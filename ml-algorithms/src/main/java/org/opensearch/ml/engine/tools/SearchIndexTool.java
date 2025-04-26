/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.tools;

import static org.opensearch.ml.common.CommonValue.*;

import java.io.IOException;
import java.security.AccessController;
import java.security.PrivilegedExceptionAction;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.common.xcontent.LoggingDeprecationHandler;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.spi.tools.Tool;
import org.opensearch.ml.common.spi.tools.ToolAnnotation;
import org.opensearch.ml.common.transport.connector.MLConnectorSearchAction;
import org.opensearch.ml.common.transport.model.MLModelSearchAction;
import org.opensearch.ml.common.transport.model_group.MLModelGroupSearchAction;
import org.opensearch.search.SearchHit;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.transport.client.Client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

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
        "Use this tool to search an index by providing two parameters: 'index' for the index name, and 'query' for the OpenSearch DSL formatted query. Only use this tool when both index name and DSL query is available.";

    public static final String DEFAULT_INPUT_SCHEMA = "{\"type\":\"object\","
        + "\"properties\":{\"index\":{\"type\":\"string\",\"description\":\"OpenSearch index name. for example: index1\"},"
        + "\"query\":{\"type\":\"object\",\"description\":\"OpenSearch search index query. You need to get index mapping to write correct search query. It must be a valid OpenSearch query."
        + " Valid value:\\n{\\\"query\\\":{\\\"match\\\":{\\\"population_description\\\":\\\"seattle 2023 population\\\"}},\\\"size\\\":2,\\\"_source\\\":\\\"population_description\\\"}\\n"
        + "Invalid value: \\n{\\\"match\\\":{\\\"population_description\\\":\\\"seattle 2023 population\\\"}}\\nThe value is invalid because the match not wrapped by \\\"query\\\".\","
        + "\"additionalProperties\":false}},\"required\":[\"index\",\"query\"],\"additionalProperties\":false}";

    private static final Gson GSON = new GsonBuilder().serializeSpecialFloatingPointValues().create();

    private String name = TYPE;
    private Map<String, Object> attributes;
    private String description = DEFAULT_DESCRIPTION;

    private Client client;

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
        return parameters != null && parameters.containsKey(INPUT_FIELD) && parameters.get(INPUT_FIELD) != null;
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

    @Override
    public <T> void run(Map<String, String> parameters, ActionListener<T> listener) {
        try {
            String input = parameters.get(INPUT_FIELD);
            JsonObject jsonObject = GSON.fromJson(input, JsonObject.class);
            String index = Optional.ofNullable(jsonObject).map(x -> x.get(INDEX_FIELD)).map(JsonElement::getAsString).orElse(null);
            String query = Optional.ofNullable(jsonObject).map(x -> x.get(QUERY_FIELD)).map(JsonElement::toString).orElse(null);
            if (index == null || query == null) {
                listener.onFailure(new IllegalArgumentException("SearchIndexTool's two parameter: index and query are required!"));
                return;
            }
            SearchRequest searchRequest = getSearchRequest(index, query);

            ActionListener<SearchResponse> actionListener = ActionListener.<SearchResponse>wrap(r -> {
                SearchHit[] hits = r.getHits().getHits();

                if (hits != null && hits.length > 0) {
                    StringBuilder contextBuilder = new StringBuilder();
                    for (SearchHit hit : hits) {
                        String doc = AccessController.doPrivileged((PrivilegedExceptionAction<String>) () -> {
                            Map<String, Object> docContent = processResponse(hit);
                            return GSON.toJson(docContent);
                        });
                        contextBuilder.append(doc).append("\n");
                    }
                    listener.onResponse((T) contextBuilder.toString());
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
            log.error("Failed to search index", e);
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
            return new SearchIndexTool(client, xContentRegistry);
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
    }
}
