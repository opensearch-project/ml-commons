/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.tools;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;
import org.opensearch.action.LatchedActionListener;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.client.Client;
import org.opensearch.common.xcontent.LoggingDeprecationHandler;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.exception.MLException;
import org.opensearch.ml.common.spi.tools.Tool;
import org.opensearch.ml.common.spi.tools.ToolAnnotation;
import org.opensearch.search.SearchHit;
import org.opensearch.search.builder.SearchSourceBuilder;

import java.io.IOException;
import java.security.AccessController;
import java.security.PrivilegedExceptionAction;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.opensearch.ml.common.utils.StringUtils.gson;

/**
 * This tool supports neural search with embedding models and knn index.
 */
@Log4j2
@ToolAnnotation(VectorDBTool.NAME)
public class VectorDBTool implements Tool {
    public static final String NAME = "VectorDBTool";
    @Setter @Getter
    private String alias;
    private static String DEFAULT_DESCRIPTION = "Use this tool to search data in OpenSearch index.";
    @Getter @Setter
    private String description = DEFAULT_DESCRIPTION;

    private Client client;
    private NamedXContentRegistry xContentRegistry;
    private String index;
    private String embeddingField;
    private String sourceField;
    private String modelId;
    private Integer docSize ;
    private Integer k;

    @Builder
    public VectorDBTool(Client client, NamedXContentRegistry xContentRegistry, String index, String embeddingField, String sourceField, Integer k, Integer docSize, String modelId) {
        this.client = client;
        this.xContentRegistry = xContentRegistry;
        this.index = index;
        this.embeddingField = embeddingField;
        this.sourceField = sourceField;
        this.modelId = modelId;
        this.docSize = docSize == null? 2 : docSize;
        this.k = k == null? 10 : k;
    }

    @Override
    public <T> T run(Map<String, String> parameters) {
        try {
            String question = parameters.get("input");
            try {
                question = gson.fromJson(question, String.class);
            } catch (Exception e) {
                throw new IllegalArgumentException("wrong input");
            }
            String query = "{\"query\":{\"neural\":{\""+ embeddingField +"\":{\"query_text\":\"" + question + "\",\"model_id\":\""
                           + modelId + "\",\"k\":" + k + "}}},\"size\":\"" + docSize
                           + "\",\"_source\":[\"" + sourceField + "\"]}";
            AtomicReference<String> contextRef = new AtomicReference<>("");
            AtomicReference<Exception> exceptionRef = new AtomicReference<>(null);

            SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
            XContentParser queryParser = XContentType.JSON.xContent().createParser(xContentRegistry, LoggingDeprecationHandler.INSTANCE, query);
            searchSourceBuilder.parseXContent(queryParser);
            SearchRequest searchRequest = new SearchRequest().source(searchSourceBuilder).indices(index);
            CountDownLatch latch = new CountDownLatch(1);
            LatchedActionListener listener = new LatchedActionListener<SearchResponse>(ActionListener.wrap(r -> {
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
                    contextRef.set(gson.toJson(contextBuilder.toString()));
                }
            }, e -> {
                log.error("Failed to search index", e);
                exceptionRef.set(e);
            }), latch);
            client.search(searchRequest, listener);

            try {
                latch.await(50, SECONDS);
            } catch (InterruptedException e) {
                throw new IllegalStateException(e);
            }
            if (exceptionRef.get() != null) {
                throw new MLException(exceptionRef.get());
            }
            return (T)contextRef.get();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public <T> void run(Map<String, String> parameters, ActionListener<T> listener) {
        try {
            String question = parameters.get("input");
            try {
                question = gson.fromJson(question, String.class);
            } catch (Exception e) {
                //throw new IllegalArgumentException("wrong input");
            }
            String query = "{\"query\":{\"neural\":{\""+ embeddingField +"\":{\"query_text\":\"" + question + "\",\"model_id\":\""
                           + modelId + "\",\"k\":" + k + "}}},\"size\":\"" + docSize
                           + "\",\"_source\":[\"" + sourceField + "\"]}";

            SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
            XContentParser queryParser = XContentType.JSON.xContent().createParser(xContentRegistry, LoggingDeprecationHandler.INSTANCE, query);
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
                            docContent.put("_id", hit.getId());
                            docContent.put("_source", hit.getSourceAsMap());
                            return gson.toJson(docContent);
                        });
                        contextBuilder.append(doc).append("\n");
                    }
                    listener.onResponse((T)gson.toJson(contextBuilder.toString()));
                } else {
                    listener.onResponse((T)"");
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
    public String getName() {
        return NAME;
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

    public static class Factory implements Tool.Factory<VectorDBTool> {
        private Client client;
        private NamedXContentRegistry xContentRegistry;

        private static Factory INSTANCE;
        public static Factory getInstance() {
            if (INSTANCE != null) {
                return INSTANCE;
            }
            synchronized (VectorDBTool.class) {
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
        public VectorDBTool create(Map<String, Object> params) {
            String index = (String)params.get("index");
            String embeddingField = (String)params.get("embedding_field");
            String sourceField = (String)params.get("source_field");
            String modelId = (String)params.get("model_id");
            Integer docSize = params.containsKey("doc_size")? Integer.parseInt((String)params.get("doc_size")) : 2;
            return VectorDBTool.builder()
                    .client(client)
                    .xContentRegistry(xContentRegistry)
                    .index(index)
                    .embeddingField(embeddingField)
                    .sourceField(sourceField)
                    .modelId(modelId)
                    .docSize(docSize)
                    .build();
        }

        @Override
        public String getDefaultDescription() {
            return DEFAULT_DESCRIPTION;
        }
    }

}