/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.tools;

import static org.opensearch.ml.common.CommonValue.*;
import static org.opensearch.ml.common.utils.StringUtils.PLAIN_NUMBER_GSON;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.apache.commons.lang3.StringUtils;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.common.xcontent.LoggingDeprecationHandler;
import org.opensearch.common.xcontent.XContentFactory;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.common.bytes.BytesReference;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.output.model.ModelTensor;
import org.opensearch.ml.common.output.model.ModelTensorOutput;
import org.opensearch.ml.common.output.model.ModelTensors;
import org.opensearch.ml.common.spi.tools.Parser;
import org.opensearch.ml.common.spi.tools.Tool;
import org.opensearch.ml.common.spi.tools.ToolAnnotation;
import org.opensearch.ml.common.transport.connector.MLConnectorSearchAction;
import org.opensearch.ml.common.transport.model.MLModelSearchAction;
import org.opensearch.ml.common.transport.model_group.MLModelGroupSearchAction;
import org.opensearch.ml.common.utils.ToolUtils;
import org.opensearch.ml.engine.tools.parser.ToolParser;
import org.opensearch.ml.repackage.com.google.common.annotations.VisibleForTesting;
import org.opensearch.search.SearchHit;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.transport.client.Client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;

@Getter
@Setter
@Log4j2
@ToolAnnotation(SearchIndexTool.TYPE)
public class SearchIndexTool implements Tool {

    public static final String INPUT_FIELD = "input";
    public static final String INDEX_FIELD = "index";
    public static final String QUERY_FIELD = "query";
    public static final String INPUT_SCHEMA_FIELD = "input_schema";
    public static final String STRICT_FIELD = "strict";

    public static final String TYPE = "SearchIndexTool";
    private static final String DEFAULT_DESCRIPTION =
        "Use this tool to search an index by providing two parameters: 'index' for the index name, and 'query' for the OpenSearch DSL formatted query. Only use this tool when both index name and DSL query is available. "
            + "Returns documents matching the query in the provided index.";

    public static final String DEFAULT_INPUT_SCHEMA = "{\"type\":\"object\","
        + "\"properties\":{\"index\":{\"type\":\"string\",\"description\":\"OpenSearch index name. for example: index1\"},"
        + "\"query\":{\"type\":\"object\",\"description\":\"OpenSearch search index query. You need to get index mapping to write correct search query. It must be a valid OpenSearch query."
        + " Valid value:\\n{\\\"query\\\":{\\\"match\\\":{\\\"population_description\\\":\\\"seattle 2023 population\\\"}},\\\"size\\\":2,\\\"_source\\\":\\\"population_description\\\"}\\n"
        + "Invalid value: \\n{\\\"match\\\":{\\\"population_description\\\":\\\"seattle 2023 population\\\"}}\\nThe value is invalid because the match not wrapped by \\\"query\\\".\","
        + "\"additionalProperties\":false}},\"required\":[\"index\",\"query\"],\"additionalProperties\":false}";

    private static final Gson GSON = new GsonBuilder()
        .serializeSpecialFloatingPointValues()
        .setStrictness(com.google.gson.Strictness.LENIENT)
        .create();

    public static final Map<String, Object> DEFAULT_ATTRIBUTES = Map.of(TOOL_INPUT_SCHEMA_FIELD, DEFAULT_INPUT_SCHEMA, STRICT_FIELD, false);
    public static final String RETURN_RAW_RESPONSE = "return_raw_response";

    private String name = TYPE;
    private Map<String, Object> attributes;
    private String description = DEFAULT_DESCRIPTION;

    private Client client;
    @Setter
    @Getter
    @VisibleForTesting
    private Parser outputParser;

    private NamedXContentRegistry xContentRegistry;

    public SearchIndexTool(Client client, NamedXContentRegistry xContentRegistry) {
        this.client = client;
        this.xContentRegistry = xContentRegistry;

        this.attributes = new HashMap<>();
        attributes.put(INPUT_SCHEMA_FIELD, DEFAULT_INPUT_SCHEMA);
        attributes.put(STRICT_FIELD, false);
    }

    @Override
    public String getType() {
        return TYPE;
    }

    @Override
    public String getVersion() {
        return null;
    }

    @Override
    public boolean validate(Map<String, String> parameters) {
        if (parameters == null || parameters.isEmpty()) {
            return false;
        }
        boolean argumentsFromInput = parameters.containsKey(INPUT_FIELD) && !StringUtils.isEmpty(parameters.get(INPUT_FIELD));
        boolean argumentsFromParameters = parameters.containsKey(INDEX_FIELD)
            && parameters.containsKey(QUERY_FIELD)
            && !StringUtils.isEmpty(parameters.get(INDEX_FIELD))
            && !StringUtils.isEmpty(parameters.get(QUERY_FIELD));
        boolean validRequest = argumentsFromInput || argumentsFromParameters;
        if (!validRequest) {
            log.error("SearchIndexTool's two parameter: index and query are required!");
            return false;
        }
        return true;
    }

    /**
     * Normalizes a JsonElement query parameter to a proper JSON string.
     * Handles both JSON objects and malformed JSON strings from LLMs.
     */
    private String normalizeQueryParameter(JsonElement queryElement) {
        if (queryElement == null || queryElement.isJsonNull()) {
            return null;
        }
        if (queryElement.isJsonObject()) {
            return PLAIN_NUMBER_GSON.toJson(queryElement);
        } else if (queryElement.isJsonPrimitive() && queryElement.getAsJsonPrimitive().isString()) {
            String queryString = queryElement.getAsString();
            return normalizeQueryString(queryString);
        } else {
            Object queryObject = PLAIN_NUMBER_GSON.fromJson(queryElement, Object.class);
            return PLAIN_NUMBER_GSON.toJson(queryObject);
        }
    }

    /**
     * Attempts to normalize and fix common JSON string formatting issues from LLMs.
     * Provides graceful fallback for malformed query strings.
     */
    private String normalizeQueryString(String queryString) {
        if (StringUtils.isEmpty(queryString)) {
            return queryString;
        }

        try {
            Object parsed = PLAIN_NUMBER_GSON.fromJson(queryString, Object.class);
            if (parsed instanceof String s && !StringUtils.isEmpty(s)) {
                try {
                    Object reparsed = PLAIN_NUMBER_GSON.fromJson(s, Object.class);
                    // Only unwrap if the inner content is structured JSON (object/array), not another string.
                    if (!(reparsed instanceof String)) {
                        return PLAIN_NUMBER_GSON.toJson(reparsed);
                    }
                } catch (JsonSyntaxException ignored) {
                    // fall through to return the original parsed form
                }
            }
            return PLAIN_NUMBER_GSON.toJson(parsed);
        } catch (JsonSyntaxException e) {
            log.debug("Initial query parsing failed, attempting to fix common LLM formatting issues: {}", e.getMessage());
        }

        String fixedQuery = queryString;

        fixedQuery = fixedQuery.replaceAll("\\\\\\\\\"", "\\\\\"");

        fixedQuery = fixedQuery.replaceAll("\\\\\"", "\"");

        fixedQuery = fixedQuery.trim();
        if (fixedQuery.startsWith("\"") && fixedQuery.endsWith("\"") && fixedQuery.length() > 1) {
            String unwrapped = fixedQuery.substring(1, fixedQuery.length() - 1);
            try {
                Object parsed = PLAIN_NUMBER_GSON.fromJson(unwrapped, Object.class);
                log.info("Successfully fixed stringified JSON query by removing outer quotes");
                return PLAIN_NUMBER_GSON.toJson(parsed);
            } catch (JsonSyntaxException ignored) {
                // Keep original if unwrapping doesn't work
            }
        }

        fixedQuery = fixMalformedJson(fixedQuery);

        try {
            Object parsed = PLAIN_NUMBER_GSON.fromJson(fixedQuery, Object.class);
            log.info("Successfully fixed malformed query through escape correction and brace balancing");
            return PLAIN_NUMBER_GSON.toJson(parsed);
        } catch (JsonSyntaxException e) {
            log.warn("Could not auto-fix malformed query (length: {}), using original", queryString.length());
            return queryString;
        }
    }

    /**
     * Fixes common JSON malformation issues in input strings.
     * Handles cases like extra closing braces that cause parsing failures.
     */
    private String fixMalformedJson(String input) {
        if (StringUtils.isEmpty(input)) {
            return input;
        }

        String fixed = input.trim();

        while (fixed.endsWith("}")) {
            int braceBalance = countBraces(fixed);
            if (braceBalance == 0) {
                break;
            } else if (braceBalance < 0) {
                fixed = fixed.substring(0, fixed.length() - 1);
                log.debug("Removed extra closing brace from malformed JSON");
            } else {
                break;
            }
        }

        return fixed;
    }

    /**
     * Counts the balance of opening vs closing braces.
     * Returns 0 for balanced, positive for more opening braces, negative for more closing braces.
     */
    private int countBraces(String json) {
        int balance = 0;
        boolean inString = false;
        boolean escaped = false;

        for (char c : json.toCharArray()) {
            if (escaped) {
                escaped = false;
                continue;
            }

            if (c == '\\') {
                escaped = true;
                continue;
            }

            if (c == '"') {
                inString = !inString;
                continue;
            }

            if (!inString) {
                if (c == '{') {
                    balance++;
                } else if (c == '}') {
                    balance--;
                }
            }
        }

        return balance;
    }

    private SearchRequest getSearchRequest(String index, String query) throws IOException {
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        XContentParser queryParser = XContentType.JSON.xContent().createParser(xContentRegistry, LoggingDeprecationHandler.INSTANCE, query);
        searchSourceBuilder.parseXContent(queryParser);
        return new SearchRequest().source(searchSourceBuilder).indices(index);
    }

    private static Map<String, Object> processResponse(SearchHit hit) {
        Map<String, Object> docContent = new HashMap<>();
        docContent.put("_index", hit.getIndex());
        docContent.put("_id", hit.getId());
        docContent.put("_score", hit.getScore());
        docContent.put("_source", hit.getSourceAsMap());
        return docContent;
    }

    /**
     * Converts a SearchResponse to a Map representation for easier processing.
     *
     * @param searchResponse The search response to convert
     * @return Map representation of the search response
     * @throws IOException if conversion fails
     */
    public Map<String, Object> convertSearchResponseToMap(SearchResponse searchResponse) throws IOException {
        XContentBuilder builder = XContentFactory.jsonBuilder();
        searchResponse.toXContent(builder, ToXContent.EMPTY_PARAMS);

        // Convert to bytes and then to map
        BytesReference bytes = BytesReference.bytes(builder);
        try (
            XContentParser parser = XContentType.JSON
                .xContent()
                .createParser(NamedXContentRegistry.EMPTY, LoggingDeprecationHandler.INSTANCE, BytesReference.toBytes(bytes))
        ) {
            return parser.map();
        }
    }

    @Override
    public <T> void run(Map<String, String> originalParameters, ActionListener<T> listener) {
        try {
            Map<String, String> parameters = ToolUtils.extractInputParameters(originalParameters, attributes);
            String input = parameters.get(INPUT_FIELD);
            String index = null;
            String query = null;
            boolean returnFullResponse = Boolean.parseBoolean(parameters.getOrDefault(RETURN_RAW_RESPONSE, "false"));

            if (!StringUtils.isEmpty(input)) {
                try {
                    String fixedInput = fixMalformedJson(input);
                    JsonObject jsonObject = GSON.fromJson(fixedInput, JsonObject.class);
                    if (jsonObject != null && jsonObject.has(INDEX_FIELD) && jsonObject.has(QUERY_FIELD)) {
                        index = jsonObject.get(INDEX_FIELD).getAsString();
                        JsonElement queryElement = jsonObject.get(QUERY_FIELD);

                        if (queryElement != null) {
                            query = normalizeQueryParameter(queryElement);
                        }
                    }
                } catch (JsonSyntaxException e) {
                    log.error("Invalid JSON input: {}", input, e);
                }
            }

            if (StringUtils.isEmpty(index)) {
                index = parameters.get(INDEX_FIELD);
            }

            if (StringUtils.isEmpty(query)) {
                String rawQuery = parameters.get(QUERY_FIELD);
                if (!StringUtils.isEmpty(rawQuery)) {
                    query = normalizeQueryString(rawQuery);
                }
            }

            if (StringUtils.isEmpty(index) || StringUtils.isEmpty(query)) {
                listener
                    .onFailure(
                        new IllegalArgumentException(
                            "SearchIndexTool's two parameters: index and query are required and should be in valid format"
                        )
                    );
                return;
            }

            SearchRequest searchRequest;
            try {
                searchRequest = getSearchRequest(index, query);
            } catch (Exception e) {
                log.error("Failed to parse search query. Index: {}", index, e);
                listener
                    .onFailure(
                        new IllegalArgumentException("Invalid query format. Expected valid OpenSearch DSL query. Error: " + e.getMessage())
                    );
                return;
            }

            ActionListener<SearchResponse> actionListener = ActionListener.<SearchResponse>wrap(r -> {
                SearchHit[] hits = r.getHits().getHits();
                if (returnFullResponse) {
                    List<ModelTensors> outputs = new ArrayList<>();
                    List<ModelTensor> tensors = new ArrayList<>();
                    tensors.add(ModelTensor.builder().name(name).dataAsMap(convertSearchResponseToMap(r)).build());
                    outputs.add(ModelTensors.builder().mlModelTensors(tensors).build());
                    ModelTensorOutput output = ModelTensorOutput.builder().mlModelOutputs(outputs).build();
                    if (outputParser != null) {
                        listener.onResponse((T) outputParser.parse(output));
                    } else {
                        listener.onResponse((T) output);
                    }
                    return;
                }
                if (hits != null && hits.length > 0) {
                    StringBuilder contextBuilder = new StringBuilder();
                    for (SearchHit hit : hits) {
                        Map<String, Object> docContent = processResponse(hit);
                        String doc = GSON.toJson(docContent);
                        contextBuilder.append(doc).append("\n");
                    }
                    if (outputParser != null) {
                        listener.onResponse((T) outputParser.parse(contextBuilder.toString()));
                    } else {
                        listener.onResponse((T) contextBuilder.toString());
                    }
                } else {
                    listener.onResponse((T) "");
                }
            }, e -> {
                log.error("Failed to search index", e);
                listener.onFailure(e);
            });

            // since searching connector and model needs access control, we need
            // to forward the request corresponding transport action
            if (Objects.equals(index, ML_CONNECTOR_INDEX)) {
                client.execute(MLConnectorSearchAction.INSTANCE, searchRequest, actionListener);
            } else if (Objects.equals(index, ML_MODEL_INDEX)) {
                client.execute(MLModelSearchAction.INSTANCE, searchRequest, actionListener);
            } else if (Objects.equals(index, ML_MODEL_GROUP_INDEX)) {
                client.execute(MLModelGroupSearchAction.INSTANCE, searchRequest, actionListener);
            } else {
                client.search(searchRequest, actionListener);
            }
        } catch (Exception e) {
            log.error("Failed to run SearchIndexTool", e);
            listener.onFailure(e);
        }
    }

    public static class Factory implements Tool.Factory<SearchIndexTool> {

        private Client client;
        private static Factory INSTANCE;

        private NamedXContentRegistry xContentRegistry;

        /**
         * Create or return the singleton factory instance
         */
        public static Factory getInstance() {
            if (INSTANCE != null) {
                return INSTANCE;
            }
            synchronized (SearchIndexTool.class) {
                if (INSTANCE != null) {
                    return INSTANCE;
                }
                INSTANCE = new Factory();
                return INSTANCE;
            }
        }

        public void init(Client client, NamedXContentRegistry xContentRegistry) {
            this.client = client;
            this.xContentRegistry = xContentRegistry;
        }

        @Override
        public SearchIndexTool create(Map<String, Object> params) {
            SearchIndexTool tool = new SearchIndexTool(client, xContentRegistry);
            // Enhance the output parser with processors if configured
            tool.setOutputParser(ToolParser.createFromToolParams(params));
            return tool;
        }

        @Override
        public String getDefaultDescription() {
            return DEFAULT_DESCRIPTION;
        }

        @Override
        public String getDefaultType() {
            return TYPE;
        }

        @Override
        public String getDefaultVersion() {
            return null;
        }

        @Override
        public Map<String, Object> getDefaultAttributes() {
            return DEFAULT_ATTRIBUTES;
        }
    }
}
