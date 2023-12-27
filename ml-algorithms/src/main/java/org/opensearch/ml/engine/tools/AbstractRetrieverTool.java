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

import org.apache.commons.lang3.StringUtils;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.client.Client;
import org.opensearch.common.xcontent.LoggingDeprecationHandler;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.spi.tools.AbstractTool;
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
@Getter
@Setter
public abstract class AbstractRetrieverTool extends AbstractTool {
    public static final String DEFAULT_DESCRIPTION = "Use this tool to search data in OpenSearch index.";
    public static final String INPUT_FIELD = "input";
    public static final String INDEX_FIELD = "index";
    public static final String SOURCE_FIELD = "source_field";
    public static final String DOC_SIZE_FIELD = "doc_size";
    public static final int DEFAULT_DOC_SIZE = 2;
    public static final int DEFAULT_K = 10;
    protected String description = DEFAULT_DESCRIPTION;
    protected Client client;
    protected NamedXContentRegistry xContentRegistry;
    protected String index;
    protected String[] sourceFields;
    protected Integer docSize;

    protected AbstractRetrieverTool(
        String type,
        String description,
        Client client,
        NamedXContentRegistry xContentRegistry,
        String index,
        String[] sourceFields,
        Integer docSize
    ) {
        super(type, description);
        this.client = client;
        this.xContentRegistry = xContentRegistry;
        this.index = index;
        this.sourceFields = sourceFields;
        this.docSize = docSize == null ? DEFAULT_DOC_SIZE : docSize;
    }

    protected abstract String getQueryBody(String queryText);

    @Override
    public <T> void run(Map<String, String> parameters, ActionListener<T> listener) {
        try {
            String question = parameters.get(INPUT_FIELD);
            try {
                question = gson.fromJson(question, String.class);
            } catch (Exception e) {
                // throw new IllegalArgumentException("wrong input");
            }
            String query = getQueryBody(question);
            if (StringUtils.isBlank(query)) {
                throw new IllegalArgumentException("[" + INPUT_FIELD + "] is null or empty, can not process it.");
            }

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
                            docContent.put("_index", hit.getIndex());
                            docContent.put("_id", hit.getId());
                            docContent.put("_score", hit.getScore());
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

    protected static abstract class Factory<T extends Tool> implements Tool.Factory<T> {
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
