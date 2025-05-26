/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.mcpserver;

import static org.opensearch.common.xcontent.json.JsonXContent.jsonXContent;
import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;
import static org.opensearch.ml.plugin.MachineLearningPlugin.GENERAL_THREAD_POOL;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.opensearch.OpenSearchException;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.common.collect.Tuple;
import org.opensearch.common.unit.TimeValue;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.common.xcontent.LoggingDeprecationHandler;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.index.query.BoolQueryBuilder;
import org.opensearch.index.query.MatchAllQueryBuilder;
import org.opensearch.index.query.QueryBuilders;
import org.opensearch.ml.common.CommonValue;
import org.opensearch.ml.common.MLIndex;
import org.opensearch.ml.common.spi.tools.Tool;
import org.opensearch.ml.common.transport.mcpserver.requests.BaseMcpTool;
import org.opensearch.ml.common.transport.mcpserver.requests.register.RegisterMcpTool;
import org.opensearch.ml.common.utils.StringUtils;
import org.opensearch.ml.rest.mcpserver.ToolFactoryWrapper;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.client.Client;

import com.google.common.collect.ImmutableMap;

import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Slf4j
public class McpToolsHelper {
    public static final int MAX_TOOL_NUMBER = 1000;
    private static final int SYNC_MCP_TOOLS_JOB_INTERVAL = 10;

    private final Client client;
    private final ThreadPool threadPool;
    private final ToolFactoryWrapper toolFactoryWrapper;

    public McpToolsHelper(Client client, ThreadPool threadPool, ToolFactoryWrapper toolFactoryWrapper) {
        this.client = client;
        this.threadPool = threadPool;
        this.toolFactoryWrapper = toolFactoryWrapper;
    }

    // When a crashed or new node join the cluster, we automatically reload all the mcp tools.
    public void autoLoadAllMcpTools(ActionListener<Boolean> listener) {
        try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
            ActionListener<Boolean> restoreListener = ActionListener.runBefore(listener, context::restore);
            ActionListener<Map<String, Tuple<RegisterMcpTool, Long>>> searchListener = ActionListener.wrap(r -> {
                r.forEach((key, value) -> {
                    if (!McpAsyncServerHolder.IN_MEMORY_MCP_TOOLS.containsKey(key)) {
                        McpAsyncServerHolder
                                .getMcpAsyncServerInstance()
                                .addTool(createToolSpecification(value.v1()))
                                .doOnSuccess(y -> McpAsyncServerHolder.IN_MEMORY_MCP_TOOLS.put(key, value.v2()))
                                .subscribe();
                    } else if (McpAsyncServerHolder.IN_MEMORY_MCP_TOOLS.get(key) < value.v2()) {
                        McpAsyncServerHolder.getMcpAsyncServerInstance().removeTool(key).onErrorResume(e -> Mono.empty()).subscribe();
                        McpAsyncServerHolder
                                .getMcpAsyncServerInstance()
                                .addTool(createToolSpecification(value.v1()))
                                .doOnSuccess(x -> McpAsyncServerHolder.IN_MEMORY_MCP_TOOLS.put(key, value.v2()))
                                .subscribe();
                    }
                });
                startSyncMcpToolsJob();
                restoreListener.onResponse(true);
            }, e -> {
                log.error("Failed to auto load all MCP tools to MCP server", e);
                restoreListener.onFailure(e);
            });
            searchAllToolsWithVersion(searchListener);
        } catch (Exception e) {
            log.error("Failed to auto load all MCP tools to MCP server", e);
            listener.onFailure(e);
        }
    }

    public void startSyncMcpToolsJob() {
        ActionListener<Boolean> listener = ActionListener
            .wrap(r -> { log.debug("Auto reload mcp tools schedule job run successfully!"); }, e -> {
                log.error(e.getMessage());
            });
        threadPool
            .schedule(() -> autoLoadAllMcpTools(listener), TimeValue.timeValueSeconds(SYNC_MCP_TOOLS_JOB_INTERVAL), GENERAL_THREAD_POOL);
    }

    public McpServerFeatures.AsyncToolSpecification createToolSpecification(BaseMcpTool tool) {
        String toolName = Optional.ofNullable(tool.getName()).orElse(tool.getType());
        Tool.Factory factory = toolFactoryWrapper.getToolsFactories().get(tool.getType());
        if (factory == null) {
            throw new OpenSearchException("Failed to find tool factory for tool type: " + tool.getType());
        }
        Tool actualTool = factory.create(Optional.ofNullable(tool.getParameters()).orElse(ImmutableMap.of()));
        // MCP server doesn't allow null schema.
        String schema = Optional
            .ofNullable(tool.getAttributes())
            .map(x -> StringUtils.gson.toJson(x.get(CommonValue.TOOL_INPUT_SCHEMA_FIELD)))
            .orElse(
                Optional.ofNullable(actualTool.getAttributes()).map(x -> (String) x.get(CommonValue.TOOL_INPUT_SCHEMA_FIELD)).orElse("{}")
            );
        String description = Optional.ofNullable(tool.getDescription()).orElse(factory.getDefaultDescription());
        return new McpServerFeatures.AsyncToolSpecification(
            new McpSchema.Tool(toolName, String.valueOf(description), schema),
            (exchange, arguments) -> Mono.create(sink -> {
                ActionListener<String> actionListener = ActionListener
                    .wrap(r -> sink.success(new McpSchema.CallToolResult(List.of(new McpSchema.TextContent(r)), false)), e -> {
                        log.error("Failed to execute tool, tool name: {}", toolName, e);
                        sink.error(e);
                    });
                actualTool.run(StringUtils.getParameterMap(arguments), actionListener);
            })
        );
    }

    public void searchToolsWithVersion(List<String> toolNames, ActionListener<List<RegisterMcpTool>> listener) {
        ActionListener<SearchResponse> actionListener = createSearchResponseListener(listener);
        SearchRequest searchRequest = buildSearchRequest(toolNames);
        searchRequest.source().version(true);
        client.search(searchRequest, actionListener);
    }

    public void searchToolsWithPrimaryTermAndSeqNo(List<String> toolNames, ActionListener<SearchResponse> listener) {
        SearchRequest searchRequest = buildSearchRequest(toolNames);
        searchRequest.source().seqNoAndPrimaryTerm(true);
        client.search(searchRequest, listener);
    }

    private SearchRequest buildSearchRequest(List<String> toolNames) {
        SearchRequest searchRequest = new SearchRequest();
        searchRequest.indices(MLIndex.MCP_TOOLS.getIndexName());

        BoolQueryBuilder queryBuilder = QueryBuilders.boolQuery();
        toolNames.forEach(toolName -> queryBuilder.should(QueryBuilders.matchQuery("name", toolName)));
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        searchSourceBuilder.query(queryBuilder);
        searchRequest.source(searchSourceBuilder);
        return searchRequest;
    }

    public void searchAllToolsWithVersion(ActionListener<Map<String, Tuple<RegisterMcpTool, Long>>> listener) {
        try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
            ActionListener<Map<String, Tuple<RegisterMcpTool, Long>>> restoreListener = ActionListener
                .runBefore(listener, context::restore);
            ActionListener<SearchResponse> actionListener = ActionListener.wrap(r -> {
                Map<String, Tuple<RegisterMcpTool, Long>> mcpTools = new HashMap<>();
                Arrays.stream(Objects.requireNonNull(r.getHits().getHits())).forEach(x -> {
                    long version = x.getVersion();
                    try {
                        RegisterMcpTool mcpTool = parseMcpTool(x.getSourceAsString());
                        mcpTools.put(mcpTool.getName(), Tuple.tuple(mcpTool, version));
                    } catch (IOException e) {
                        restoreListener.onFailure(e);
                    }
                });
                restoreListener.onResponse(mcpTools);
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

    public void searchAllTools(ActionListener<List<RegisterMcpTool>> listener) {
        try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
            ActionListener<List<RegisterMcpTool>> restoreListener = ActionListener.runBefore(listener, context::restore);
            ActionListener<SearchResponse> actionListener = ActionListener.wrap(r -> {
                List<RegisterMcpTool> mcpTools = new ArrayList<>();
                Arrays.stream(Objects.requireNonNull(r.getHits().getHits())).forEach(x -> {
                    try {
                        RegisterMcpTool mcpTool = parseMcpTool(x.getSourceAsString());
                        mcpTools.add(mcpTool);
                    } catch (IOException e) {
                        listener.onFailure(e);
                    }
                });
                restoreListener.onResponse(mcpTools);
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

    private ActionListener<SearchResponse> createSearchResponseListener(ActionListener<List<RegisterMcpTool>> listener) {
        return ActionListener.wrap(r -> {
            List<RegisterMcpTool> mcpTools = new ArrayList<>();
            Arrays.stream(Objects.requireNonNull(r.getHits().getHits())).forEach(x -> {
                try {
                    RegisterMcpTool mcpTool = parseMcpTool(x.getSourceAsString());
                    mcpTools.add(mcpTool);
                } catch (IOException e) {
                    listener.onFailure(e);
                }
            });
            listener.onResponse(mcpTools);
        }, e -> {
            String errMsg = String.format(Locale.ROOT, "Failed to search mcp tools index with error: %s", e.getMessage());
            log.error(errMsg, e);
            listener.onFailure(new OpenSearchException(errMsg));
        });
    }

    private RegisterMcpTool parseMcpTool(String input) throws IOException {
        try (
            XContentParser parser = jsonXContent
                .createParser(NamedXContentRegistry.EMPTY, LoggingDeprecationHandler.INSTANCE, input)
        ) {
            ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.nextToken(), parser);
            return RegisterMcpTool.parse(parser);
        } catch (IOException e) {
            log.error("Failed to parse mcp tools configuration");
            throw e;
        }
    }

}
