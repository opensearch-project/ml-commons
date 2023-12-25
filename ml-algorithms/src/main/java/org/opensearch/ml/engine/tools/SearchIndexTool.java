/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.tools;

import static org.opensearch.ml.common.CommonValue.ML_CONNECTOR_INDEX;
import static org.opensearch.ml.common.CommonValue.ML_MODEL_INDEX;

import java.security.AccessController;
import java.security.PrivilegedExceptionAction;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.client.Client;
import org.opensearch.common.xcontent.LoggingDeprecationHandler;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.spi.tools.Tool;
import org.opensearch.ml.common.spi.tools.ToolAnnotation;
import org.opensearch.ml.common.transport.connector.MLConnectorSearchAction;
import org.opensearch.ml.common.transport.model.MLModelSearchAction;
import org.opensearch.ml.common.utils.StringUtils;
import org.opensearch.search.SearchHit;
import org.opensearch.search.builder.SearchSourceBuilder;

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

    public static final String TYPE = "SearchIndexTool";
    private static final String DEFAULT_DESCRIPTION =
        "Use this tool to search index with a query. You should pass in two parameters: index and query. Index is the index name and the query is an OpenSearch DSL query.";

    private String name = TYPE;

    private String description = DEFAULT_DESCRIPTION;

    private Client client;

    private NamedXContentRegistry xContentRegistry;

    public SearchIndexTool(Client client, NamedXContentRegistry xContentRegistry) {
        this.client = client;
        this.xContentRegistry = xContentRegistry;
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

    @Override
    public <T> void run(Map<String, String> parameters, ActionListener<T> listener) {
        try {
            String input = parameters.get(INPUT_FIELD);
            String index = "";
            String query = "";

            JsonObject jsonObject = StringUtils.gson.fromJson(input, JsonObject.class);
            index = jsonObject.get(INDEX_FIELD).getAsString();
            query = jsonObject.get(QUERY_FIELD).toString();
            query = "{\"query\": " + query + "}";

            SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
            XContentParser queryParser = XContentType.JSON
                .xContent()
                .createParser(xContentRegistry, LoggingDeprecationHandler.INSTANCE, query);
            searchSourceBuilder.parseXContent(queryParser);
            SearchRequest searchRequest = new SearchRequest().source(searchSourceBuilder).indices(index);

            ActionListener actionListener = ActionListener.<SearchResponse>wrap(r -> {
                SearchHit[] hits = r.getHits().getHits();

                if (hits != null && hits.length > 0) {
                    StringBuilder contextBuilder = new StringBuilder();
                    for (int i = 0; i < hits.length; i++) {
                        SearchHit hit = hits[i];
                        String doc = AccessController.doPrivileged((PrivilegedExceptionAction<String>) () -> {
                            Map<String, Object> docContent = new HashMap<>();
                            docContent.put("_index", hit.getIndex());
                            docContent.put("_id", hit.getId());
                            docContent.put("_score", hit.getScore());
                            docContent.put("_source", hit.getSourceAsMap());
                            return StringUtils.gson.toJson(docContent);
                        });
                        contextBuilder.append(doc).append("\n");
                    }
                    listener.onResponse((T) StringUtils.gson.toJson(contextBuilder.toString()));
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
            } else {
                client.search(searchRequest, actionListener);
            }
        } catch (Exception e) {
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
            synchronized (MLModelTool.class) {
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
    }
}
