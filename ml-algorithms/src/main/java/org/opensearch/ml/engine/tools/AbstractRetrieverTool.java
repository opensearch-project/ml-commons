/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.tools;

import static org.opensearch.ml.common.utils.StringUtils.gson;

import java.io.IOException;
import java.security.AccessController;
import java.security.PrivilegedExceptionAction;
import java.util.HashMap;
import java.util.Map;

import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.client.Client;
import org.opensearch.common.xcontent.LoggingDeprecationHandler;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.spi.tools.Tool;
import org.opensearch.search.SearchHit;
import org.opensearch.search.builder.SearchSourceBuilder;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;

/**
 * Abstract tool supports search paradigms in neural-search plugin.
 */
@Log4j2
public abstract class AbstractRetrieverTool implements Tool {
    protected static String DEFAULT_DESCRIPTION = "Use this tool to search data in OpenSearch index.";
    @Getter
    @Setter
    protected String description = DEFAULT_DESCRIPTION;

    protected Client client;
    protected NamedXContentRegistry xContentRegistry;
    protected String index;
    protected String embeddingField;
    protected String[] sourceFields;
    protected String modelId;
    protected Integer docSize;

    protected AbstractRetrieverTool(
        Client client,
        NamedXContentRegistry xContentRegistry,
        String index,
        String embeddingField,
        String[] sourceFields,
        Integer docSize,
        String modelId
    ) {
        this.client = client;
        this.xContentRegistry = xContentRegistry;
        this.index = index;
        this.embeddingField = embeddingField;
        this.sourceFields = sourceFields;
        this.modelId = modelId;
        this.docSize = docSize == null ? 2 : docSize;
    }

    protected abstract String getQueryBody(String queryText);

    @Override
    public <T> void run(Map<String, String> parameters, ActionListener<T> listener) {
        try {
            String question = parameters.get("input");
            try {
                question = gson.fromJson(question, String.class);
            } catch (Exception e) {
                // throw new IllegalArgumentException("wrong input");
            }
            String query = getQueryBody(question);

            SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
            XContentParser queryParser = XContentType.JSON
                .xContent()
                .createParser(xContentRegistry, LoggingDeprecationHandler.INSTANCE, query);
            searchSourceBuilder.parseXContent(queryParser);
            searchSourceBuilder.fetchSource(sourceFields, null);
            searchSourceBuilder.size(docSize);
            SearchRequest searchRequest = new SearchRequest().source(searchSourceBuilder).indices(index);
            ActionListener actionListener = ActionListener.<SearchResponse>wrap(r -> {
                SearchHit[] hits = r.getHits().getHits();

                if (hits != null && hits.length > 0) {
                    StringBuilder contextBuilder = new StringBuilder();
                    for (int i = 0; i < hits.length; i++) {
                        SearchHit hit = hits[i];
                        String doc = AccessController.doPrivileged((PrivilegedExceptionAction<String>) () -> {
                            Map<String, Object> docContent = new HashMap<>();
                            docContent.put("_id", hit.getId());
                            docContent.put("_source", hit.getSourceAsMap());
                            return gson.toJson(docContent);
                        });
                        contextBuilder.append(doc).append("\n");
                    }
                    listener.onResponse((T) gson.toJson(contextBuilder.toString()));
                } else {
                    listener.onResponse((T) "");
                }
            }, e -> {
                log.error("Failed to search index", e);
                listener.onFailure(e);
            });
            client.search(searchRequest, actionListener);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public String getVersion() {
        return null;
    }

    @Override
    public boolean validate(Map<String, String> parameters) {
        if (parameters == null || parameters.size() == 0) {
            return false;
        }
        String question = parameters.get("input");
        return question != null;
    }

    public void setClient(Client client) {
        this.client = client;
    }

    public static abstract class Factory<T extends Tool> implements Tool.Factory<T> {
        protected Client client;
        protected NamedXContentRegistry xContentRegistry;

        public void init(Client client, NamedXContentRegistry xContentRegistry) {
            this.client = client;
            this.xContentRegistry = xContentRegistry;
        }

        @Override
        public String getDefaultDescription() {
            return DEFAULT_DESCRIPTION;
        }
    }
}
