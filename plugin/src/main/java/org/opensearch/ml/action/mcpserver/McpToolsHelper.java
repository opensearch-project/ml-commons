/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.mcpserver;

import static org.opensearch.common.xcontent.json.JsonXContent.jsonXContent;
import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.opensearch.OpenSearchException;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.common.collect.Tuple;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.common.xcontent.LoggingDeprecationHandler;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.index.IndexNotFoundException;
import org.opensearch.index.query.IdsQueryBuilder;
import org.opensearch.index.query.MatchAllQueryBuilder;
import org.opensearch.index.query.QueryBuilders;
import org.opensearch.ml.common.CommonValue;
import org.opensearch.ml.common.MLIndex;
import org.opensearch.ml.common.spi.tools.Tool;
import org.opensearch.ml.common.transport.mcpserver.requests.McpToolBaseInput;
import org.opensearch.ml.common.transport.mcpserver.requests.register.McpToolRegisterInput;
import org.opensearch.ml.common.utils.StringUtils;
import org.opensearch.ml.common.utils.ToolUtils;
import org.opensearch.ml.rest.mcpserver.ToolFactoryWrapper;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.transport.client.Client;

import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableMap;
import com.networknt.schema.Error;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SchemaRegistryConfig;
import com.networknt.schema.SpecificationVersion;

import io.modelcontextprotocol.json.jackson3.JacksonMcpJsonMapper;
import io.modelcontextprotocol.server.McpStatelessServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.extern.log4j.Log4j2;
import reactor.core.publisher.Mono;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Helper for creating stateless MCP tool specifications.
 */
@Log4j2
public class McpToolsHelper {
    public static final int MAX_TOOL_NUMBER = 1000;
    static final String OBJECT_TYPE = "object";
    static final String DEFAULT_INPUT_SCHEMA = "{\"type\":\"object\"}";
    private static final JacksonMcpJsonMapper JSON_MAPPER = new JacksonMcpJsonMapper(JsonMapper.shared());
    // The default schema loader does not fetch http, https or file $ref targets (it resolves only the bundled
    // meta-schemas and classpath resources), so a registered schema can't make the node call out. Messages use
    // Locale.ROOT so callers get the same text whatever the node's default locale.
    private static final SchemaRegistry SCHEMA_REGISTRY = SchemaRegistry
        .withDefaultDialect(
            SpecificationVersion.DRAFT_2020_12,
            builder -> builder.schemaRegistryConfig(SchemaRegistryConfig.builder().locale(Locale.ROOT).build())
        );
    // Undeclared properties are allowed, as the MCP TypeScript and Python SDKs do by default.
    private static final Set<String> IGNORED_ARGUMENT_KEYWORDS = Set.of("additionalProperties", "unevaluatedProperties");
    private static final String INPUT_ARGUMENT = "input";
    static final int MAX_REPORTED_ARGUMENT_ERRORS = 5;
    private final Client client;
    private final ToolFactoryWrapper toolFactoryWrapper;

    public McpToolsHelper(Client client, ToolFactoryWrapper toolFactoryWrapper) {
        this.client = client;
        this.toolFactoryWrapper = toolFactoryWrapper;
    }

    /**
     * Create stateless MCP tool specification from existing tool definition.
     */
    public McpStatelessServerFeatures.AsyncToolSpecification createToolSpecification(McpToolBaseInput tool) {
        String toolName = Optional.ofNullable(tool.getName()).orElse(tool.getType());
        Tool.Factory factory = toolFactoryWrapper.getToolsFactories().get(tool.getType());
        if (factory == null) {
            throw new RuntimeException("Failed to find tool factory for tool type: " + tool.getType());
        }

        Tool actualTool = factory.create(Optional.ofNullable(tool.getParameters()).orElse(ImmutableMap.of()));

        // Schema precedence: registered attributes, then the tool instance, then the tool type's defaults.
        String schema = Optional.ofNullable(getSchema(tool.getAttributes())).orElse(getSchema(actualTool.getAttributes()));
        McpSchema.JsonSchema inputSchema = schema != null ? toObjectSchema(schema) : defaultSchema(toolName, factory);

        String description = Optional.ofNullable(tool.getDescription()).orElse(factory.getDefaultDescription());
        // The server is rebuilt from the index for each request, so compile the schema only when the tool is called.
        Supplier<Schema> argumentSchema = Suppliers.memoize(() -> compileArgumentSchema(toolName, inputSchema));

        return new McpStatelessServerFeatures.AsyncToolSpecification(
            McpSchema.Tool.builder().name(toolName).description(String.valueOf(description)).inputSchema(inputSchema).build(),
            (ctx, request) -> Mono.create(sink -> {
                // Tools may respond with non-String outputs (e.g. SearchIndexTool returns ModelTensorOutput when
                // return_raw_response is true), so serialize the output instead of assuming it is a String.
                ActionListener<Object> actionListener = ActionListener
                    .wrap(
                        r -> sink
                            .success(
                                new McpSchema.CallToolResult(
                                    List.of(new McpSchema.TextContent(ToolUtils.parseResponse(r))),
                                    false,
                                    null,
                                    Map.of()
                                )
                            ),
                        e -> {
                            log.error("Failed to execute tool, tool name: {}", toolName, e);
                            sink.success(toolErrorResult(e));
                        }
                    );

                String invalidArguments = validateArguments(toolName, argumentSchema.get(), inputSchema, request.arguments());
                if (invalidArguments != null) {
                    sink.success(errorResult("Invalid arguments for tool " + toolName + ": " + invalidArguments));
                    return;
                }

                try {
                    actualTool.run(StringUtils.getParameterMap(request.arguments()), actionListener);
                } catch (Exception e) {
                    actionListener.onFailure(e);
                }
            })
        );
    }

    /**
     * Parses the tool's input schema. MCP requires an object schema, and MCP SDK clients (for example the Python SDK
     * 2.x and the TypeScript SDK) reject the whole tools/list response when "type" is missing or is not "object". A
     * schema without a type is treated as an object; a schema with another type is rejected.
     */
    static McpSchema.JsonSchema toObjectSchema(String schema) {
        McpSchema.JsonSchema parsed;
        try {
            parsed = JSON_MAPPER.readValue(schema, McpSchema.JsonSchema.class);
        } catch (IOException e) {
            throw new IllegalArgumentException("Invalid tool input schema: " + e.getMessage(), e);
        }
        if (parsed == null) {
            return toObjectSchema(DEFAULT_INPUT_SCHEMA);
        }
        if (parsed.type() != null) {
            if (!OBJECT_TYPE.equals(parsed.type())) {
                throw new IllegalArgumentException("Tool input schema must have type \"object\", got \"" + parsed.type() + "\"");
            }
            return parsed;
        }
        return new McpSchema.JsonSchema(
            OBJECT_TYPE,
            parsed.properties(),
            parsed.required(),
            parsed.additionalProperties(),
            parsed.defs(),
            parsed.definitions()
        );
    }

    /**
     * Schema for a tool registered without one: the tool type's default attributes, else an empty object schema.
     * Built-in and plugin factory defaults were not parsed before, so an unusable default is logged and replaced
     * instead of dropping the tool from tools/list.
     */
    private static McpSchema.JsonSchema defaultSchema(String toolName, Tool.Factory<?> factory) {
        try {
            String factorySchema = getSchema(factory.getDefaultAttributes());
            if (factorySchema != null) {
                return toObjectSchema(factorySchema);
            }
        } catch (RuntimeException e) {
            log.warn("Ignoring unusable default input schema of tool {}: {}", toolName, e.getMessage());
        }
        return toObjectSchema(DEFAULT_INPUT_SCHEMA);
    }

    /**
     * Reports a tool execution failure as a tool result with isError set, as the MCP spec asks, so the calling model
     * sees the error. JSON-RPC errors are left for protocol problems such as an unknown tool.
     */
    static McpSchema.CallToolResult toolErrorResult(Exception e) {
        return errorResult(Optional.ofNullable(e.getMessage()).filter(m -> !m.isBlank()).orElse(e.getClass().getSimpleName()));
    }

    private static McpSchema.CallToolResult errorResult(String message) {
        return new McpSchema.CallToolResult(List.of(new McpSchema.TextContent(message)), true, null, Map.of());
    }

    /**
     * Compiles the advertised input schema so tools/call arguments can be checked against it, as the MCP spec asks
     * servers to do. Returns null, and so skips validation, if the schema can't be compiled: a schema that was usable
     * for listing shouldn't make the tool uncallable.
     */
    static Schema compileArgumentSchema(String toolName, McpSchema.JsonSchema inputSchema) {
        try {
            JsonNode schemaNode = JsonMapper.shared().readTree(JSON_MAPPER.writeValueAsString(inputSchema));
            return SCHEMA_REGISTRY.getSchema(schemaNode);
        } catch (Exception e) {
            log.warn("Not validating arguments of tool {}: its input schema can't be compiled: {}", toolName, e.getMessage());
            return null;
        }
    }

    // Prefixes the JSON Pointer of the failing argument, e.g. "/size: string found, integer expected". Errors on the
    // arguments object itself (such as a missing required property) have an empty pointer and keep just the message.
    private static String describe(Error error) {
        String location = String.valueOf(Optional.ofNullable(error.getInstanceLocation()).map(Object::toString).orElse(""));
        return location.isEmpty() ? error.getMessage() : location + ": " + error.getMessage();
    }

    /**
     * Checks tools/call arguments against the tool's input schema. Missing arguments are checked as an empty object.
     * Returns a description of what is wrong, or null if the arguments are valid or can't be checked.
     * <p>
     * The check is tolerant in the same ways as the MCP TypeScript and Python SDKs, so arguments that tools accept
     * today keep working: properties the schema doesn't declare are allowed, and a top-level argument sent as a JSON
     * string (or a single value where an array is expected) is checked in its parsed form. The tool still receives
     * the original arguments.
     */
    static String validateArguments(
        String toolName,
        Schema argumentSchema,
        McpSchema.JsonSchema inputSchema,
        Map<String, Object> arguments
    ) {
        if (argumentSchema == null) {
            return null;
        }
        try {
            Map<String, Object> args = Optional.ofNullable(arguments).orElse(Map.of());
            JsonNode argumentNode = JsonMapper.shared().valueToTree(args);
            if (argumentNode instanceof ObjectNode objectNode && inputSchema.properties() != null) {
                if (!unwrapInputArgument(objectNode, inputSchema.properties())) {
                    return null;
                }
                coerceTopLevelArguments(objectNode, inputSchema.properties());
            }
            List<Error> errors = argumentSchema
                .validate(argumentNode)
                .stream()
                .filter(e -> !IGNORED_ARGUMENT_KEYWORDS.contains(e.getKeyword()))
                .toList();
            if (errors.isEmpty()) {
                return null;
            }
            String message = errors
                .stream()
                .limit(MAX_REPORTED_ARGUMENT_ERRORS)
                .map(McpToolsHelper::describe)
                .collect(Collectors.joining("; "));
            return errors.size() > MAX_REPORTED_ARGUMENT_ERRORS
                ? message + " (and " + (errors.size() - MAX_REPORTED_ARGUMENT_ERRORS) + " more)"
                : message;
        } catch (Exception e) {
            log.warn("Skipping argument validation for tool {}: {}", toolName, e.getMessage());
            return null;
        }
    }

    /**
     * Tools also accept their arguments wrapped in "input", as a JSON object or a JSON string, as in the agent execute
     * API (ToolUtils.extractInputParameters). When the schema doesn't declare "input", check the wrapped arguments in
     * the validation copy; keys inside "input" win over top-level keys, as they do when the tool unpacks it.
     * Returns false if "input" is a string Jackson can't parse (tools parse it leniently with Gson), meaning the
     * arguments can't be checked reliably.
     */
    private static boolean unwrapInputArgument(ObjectNode arguments, Map<String, Object> properties) {
        JsonNode input = arguments.get(INPUT_ARGUMENT);
        if (input == null || properties.containsKey(INPUT_ARGUMENT)) {
            return true;
        }
        JsonNode parsed = input;
        if (input.isString()) {
            try {
                parsed = JsonMapper.shared().readTree(input.asString());
            } catch (Exception e) {
                return false;
            }
        }
        if (parsed instanceof ObjectNode wrapped) {
            arguments.remove(INPUT_ARGUMENT);
            for (Map.Entry<String, JsonNode> entry : wrapped.properties()) {
                arguments.set(entry.getKey(), entry.getValue());
            }
        }
        return true;
    }

    /**
     * For each top-level string argument whose declared type doesn't allow a string, checks its parsed form instead:
     * LLM clients often send objects, arrays and numbers as JSON strings, and ml-commons tools parse them. A string
     * that isn't JSON is checked as a one-element array when the property expects an array, following how
     * IndexMappingTool reads a single index name; a tool that can't read a single value still reports its own error.
     */
    private static void coerceTopLevelArguments(ObjectNode arguments, Map<String, Object> properties) {
        for (Map.Entry<String, Object> property : properties.entrySet()) {
            JsonNode value = arguments.get(property.getKey());
            if (value == null || !value.isString() || !(property.getValue() instanceof Map<?, ?> propertySchema)) {
                continue;
            }
            Set<String> types = declaredTypes(propertySchema.get("type"));
            if (types.isEmpty() || types.contains("string")) {
                continue;
            }
            JsonNode parsed;
            try {
                parsed = JsonMapper.shared().readTree(value.asString());
            } catch (Exception e) {
                parsed = null;
            }
            if (parsed != null && !parsed.isMissingNode()) {
                arguments.set(property.getKey(), parsed);
            } else if (types.contains("array")) {
                arguments.set(property.getKey(), JsonMapper.shared().createArrayNode().add(value));
            }
        }
    }

    private static Set<String> declaredTypes(Object type) {
        if (type instanceof String single) {
            return Set.of(single);
        }
        if (type instanceof List<?> list) {
            return list.stream().map(String::valueOf).collect(Collectors.toSet());
        }
        return Set.of();
    }

    private static String getSchema(Map<String, Object> attrs) {
        if (attrs == null || attrs.isEmpty())
            return null;

        Object inputSchema = attrs.get(CommonValue.TOOL_INPUT_SCHEMA_FIELD);
        if (inputSchema == null)
            return null;

        // Pass through JSON strings as-is (avoid double-encoding)
        if (inputSchema instanceof String inputSchemaString) {
            inputSchemaString = inputSchemaString.trim();
            if (inputSchemaString.isEmpty())
                return null;
            return inputSchemaString;
        }

        // If it’s a JSON tree, serialize it
        if (inputSchema instanceof com.google.gson.JsonElement jsonElement) {
            return StringUtils.gson.toJson(jsonElement);
        }

        // If it’s a Map/POJO, serialize to JSON
        return StringUtils.gson.toJson(inputSchema);
    }

    /**
     * Search all tools from the MCP tools index
     */
    public void searchAllTools(ActionListener<List<McpToolRegisterInput>> listener) {
        try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
            ActionListener<List<McpToolRegisterInput>> restoreListener = ActionListener.runBefore(listener, context::restore);
            ActionListener<SearchResponse> actionListener = ActionListener.wrap(r -> {
                List<McpToolRegisterInput> mcpTools = new ArrayList<>();
                List<IOException> errors = new ArrayList<>();

                Arrays.stream(Objects.requireNonNull(r.getHits().getHits())).forEach(x -> {
                    try {
                        McpToolRegisterInput mcpTool = parseMcpTool(x.getSourceAsString());
                        mcpTools.add(mcpTool);
                    } catch (IOException e) {
                        errors.add(e); // Collect the error instead of calling listener.onFailure
                    }
                });

                if (!errors.isEmpty()) {
                    // Create a composite exception with all errors
                    String errorMessage = String
                        .format("Failed to parse %d out of %d MCP tools", errors.size(), r.getHits().getHits().length);
                    OpenSearchException compositeException = new OpenSearchException(errorMessage);

                    // Add all errors as suppressed exceptions
                    for (IOException error : errors) {
                        compositeException.addSuppressed(error);
                    }

                    log.error("Multiple parsing errors occurred: {}", errorMessage);
                    restoreListener.onFailure(compositeException);
                } else {
                    restoreListener.onResponse(mcpTools);
                }
            }, e -> {
                if (e instanceof IndexNotFoundException) {
                    restoreListener.onResponse(new ArrayList<>());
                    return;
                }
                String errMsg = String.format(Locale.ROOT, "Failed to search mcp tools index with error: %s", e.getMessage());
                log.error(errMsg, e);
                restoreListener.onFailure(new OpenSearchException(errMsg));
            });

            client.search(buildSearchRequest(), actionListener);
        } catch (Exception e) {
            log.error("Failed to search mcp tools index", e);
            listener.onFailure(e);
        }
    }

    public void searchToolsWithVersion(List<String> toolNames, ActionListener<List<McpToolRegisterInput>> listener) {
        ActionListener<SearchResponse> actionListener = createSearchResponseListener(listener);
        SearchRequest searchRequest = buildSearchRequest(toolNames);
        searchRequest.source().version(true);
        client.search(searchRequest, actionListener);
    }

    private ActionListener<SearchResponse> createSearchResponseListener(ActionListener<List<McpToolRegisterInput>> listener) {
        return ActionListener.wrap(r -> {
            List<McpToolRegisterInput> mcpTools = new ArrayList<>();
            List<IOException> errors = new ArrayList<>();

            Arrays.stream(Objects.requireNonNull(r.getHits().getHits())).forEach(x -> {
                try {
                    McpToolRegisterInput mcpTool = parseMcpTool(x.getSourceAsString());
                    mcpTools.add(mcpTool);
                } catch (IOException e) {
                    errors.add(e);
                }
            });

            if (!errors.isEmpty()) {
                // Create a composite exception with all errors
                String errorMessage = String.format("Failed to parse %d out of %d MCP tools", errors.size(), r.getHits().getHits().length);
                OpenSearchException compositeException = new OpenSearchException(errorMessage);

                // Add all errors as suppressed exceptions
                for (IOException error : errors) {
                    compositeException.addSuppressed(error);
                }

                log.error("Multiple parsing errors occurred: {}", errorMessage);
                listener.onFailure(compositeException);
            } else {
                listener.onResponse(mcpTools);
            }
        }, e -> {
            String errMsg = String.format(Locale.ROOT, "Failed to search mcp tools index with error: %s", e.getMessage());
            log.error(errMsg, e);
            listener.onFailure(new OpenSearchException(errMsg));
        });
    }

    public void searchToolsWithPrimaryTermAndSeqNo(List<String> toolNames, ActionListener<SearchResponse> listener) {
        SearchRequest searchRequest = buildSearchRequest(toolNames);
        searchRequest.source().seqNoAndPrimaryTerm(true);
        searchRequest.source().size(MAX_TOOL_NUMBER);
        client.search(searchRequest, listener);
    }

    public void searchAllToolsWithVersion(ActionListener<Map<String, Tuple<McpToolRegisterInput, Long>>> listener) {
        try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
            ActionListener<Map<String, Tuple<McpToolRegisterInput, Long>>> restoreListener = ActionListener
                .runBefore(listener, context::restore);
            ActionListener<SearchResponse> actionListener = ActionListener.wrap(r -> {
                Map<String, Tuple<McpToolRegisterInput, Long>> mcpTools = new HashMap<>();
                List<IOException> errors = new ArrayList<>();

                Arrays.stream(Objects.requireNonNull(r.getHits().getHits())).forEach(x -> {
                    long version = x.getVersion();
                    try {
                        McpToolRegisterInput mcpTool = parseMcpTool(x.getSourceAsString());
                        mcpTools.put(mcpTool.getName(), Tuple.tuple(mcpTool, version));
                    } catch (IOException e) {
                        errors.add(e); // Collect the error instead of calling restoreListener.onFailure
                    }
                });

                if (!errors.isEmpty()) {
                    // Create a composite exception with all errors
                    String errorMessage = String
                        .format("Failed to parse %d out of %d MCP tools", errors.size(), r.getHits().getHits().length);
                    OpenSearchException compositeException = new OpenSearchException(errorMessage);

                    // Add all errors as suppressed exceptions
                    for (IOException error : errors) {
                        compositeException.addSuppressed(error);
                    }

                    log.error("Multiple parsing errors occurred: {}", errorMessage);
                    restoreListener.onFailure(compositeException);
                } else {
                    restoreListener.onResponse(mcpTools);
                }
            }, e -> {
                String errMsg = String.format(Locale.ROOT, "Failed to search mcp tools index with error: %s", e.getMessage());
                log.error(errMsg, e);
                restoreListener.onFailure(new OpenSearchException(errMsg));
            });
            client.search(buildSearchRequest(), actionListener);
        } catch (Exception e) {
            log.error("Failed to search mcp tools index", e);
            listener.onFailure(e);
        }
    }

    private SearchRequest buildSearchRequest() {
        SearchRequest searchRequest = new SearchRequest();
        searchRequest.indices(MLIndex.MCP_TOOLS.getIndexName());

        MatchAllQueryBuilder queryBuilder = QueryBuilders.matchAllQuery();
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        searchSourceBuilder.version(true);
        searchSourceBuilder.query(queryBuilder);
        searchRequest.source(searchSourceBuilder);
        searchRequest.source().size(MAX_TOOL_NUMBER);
        return searchRequest;
    }

    private SearchRequest buildSearchRequest(List<String> toolNames) {
        SearchRequest searchRequest = new SearchRequest();
        searchRequest.indices(MLIndex.MCP_TOOLS.getIndexName());

        // Tool documents are always indexed, updated and deleted with the tool name as the document id, so look the
        // tools up by document id. An analysed match query on the `name` text field splits on '-' and lowercases, so it
        // returned every tool sharing a single token, which made `_remove` delete unrelated tools. (Note '_' does not
        // split: it is Word_Break=ExtendNumLet in UAX #29, so `a_b` stays one token. Only '-' breaks.)
        //
        // Callers must pass a non-empty list: an ids query with no ids matches nothing, whereas the bool/should query
        // this replaced matched everything when it had no clauses. Every caller reaches here via a request whose
        // validate() rejects an empty tool list, so this is a precondition rather than a case to handle.
        IdsQueryBuilder queryBuilder = QueryBuilders.idsQuery().addIds(toolNames.toArray(new String[0]));
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        searchSourceBuilder.query(queryBuilder);
        searchRequest.source(searchSourceBuilder);
        searchRequest.source().size(MAX_TOOL_NUMBER);
        return searchRequest;
    }

    private McpToolRegisterInput parseMcpTool(String input) throws IOException {
        try (XContentParser parser = jsonXContent.createParser(NamedXContentRegistry.EMPTY, LoggingDeprecationHandler.INSTANCE, input)) {
            ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.nextToken(), parser);
            return McpToolRegisterInput.parse(parser);
        } catch (IOException e) {
            log.error("Failed to parse mcp tools configuration: {}", input);
            throw e;
        }
    }
}
