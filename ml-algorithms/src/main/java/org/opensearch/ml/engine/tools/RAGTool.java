/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.tools;

import static org.apache.commons.text.StringEscapeUtils.escapeJson;
import static org.opensearch.ml.common.utils.StringUtils.gson;

import java.io.IOException;
import java.security.AccessController;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.opensearch.action.ActionRequest;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.client.Client;
import org.opensearch.common.xcontent.LoggingDeprecationHandler;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.dataset.remote.RemoteInferenceInputDataSet;
import org.opensearch.ml.common.input.MLInput;
import org.opensearch.ml.common.output.model.ModelTensor;
import org.opensearch.ml.common.output.model.ModelTensorOutput;
import org.opensearch.ml.common.output.model.ModelTensors;
import org.opensearch.ml.common.spi.tools.ToolAnnotation;
import org.opensearch.ml.common.transport.MLTaskResponse;
import org.opensearch.ml.common.transport.prediction.MLPredictionTaskAction;
import org.opensearch.ml.common.transport.prediction.MLPredictionTaskRequest;
import org.opensearch.search.SearchHit;
import org.opensearch.search.builder.SearchSourceBuilder;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;

/**
 * This tool supports running any ml-commons model.
 */
@Log4j2
@Setter
@Getter
@ToolAnnotation(RAGTool.TYPE)
public class RAGTool extends AbstractRetrieverTool {
    public static final String TYPE = "RAGTool";
    private static String DEFAULT_DESCRIPTION = "Use this tool to run any model.";
    public static final String MODEL_ID_FIELD = "model_id";
    public static final String EMBEDDING_MODEL_ID_FIELD = "embedding_model_id";
    public static final String EMBEDDING_FIELD = "embedding_field";
    public static final String OUTPUT_FIELD = "output_field";
    private String name = TYPE;
    private String description = DEFAULT_DESCRIPTION;
    private Client client;
    private String modelId;

    private NamedXContentRegistry xContentRegistry;
    private String index;
    private String embeddingField;
    private String[] sourceFields;
    private String embeddingModelId;
    private Integer docSize;
    private Integer k;

    @Builder
    public RAGTool(
        Client client,
        NamedXContentRegistry xContentRegistry,
        String index,
        String embeddingField,
        String[] sourceFields,
        Integer k,
        Integer docSize,
        String embeddingModelId,
        String modelId
    ) {
        super(TYPE, DEFAULT_DESCRIPTION, client, xContentRegistry, index, sourceFields, docSize);
        this.client = client;
        this.xContentRegistry = xContentRegistry;
        this.index = index;
        this.embeddingField = embeddingField;
        this.sourceFields = sourceFields;
        this.embeddingModelId = embeddingModelId;
        this.docSize = docSize == null ? DEFAULT_DOC_SIZE : docSize;
        this.k = k == null ? DEFAULT_K : k;
        this.modelId = modelId;

        this.setOutputParser(o -> {
            List<ModelTensors> mlModelOutputs = (List<ModelTensors>) o;
            return mlModelOutputs.get(0).getMlModelTensors().get(0).getDataAsMap().get("response");
        });
    }

    @Override
    protected String getQueryBody(String queryText) {
        if (StringUtils.isBlank(embeddingField) || StringUtils.isBlank(embeddingModelId)) {
            throw new IllegalArgumentException(
                "Parameter [" + EMBEDDING_FIELD + "] and [" + EMBEDDING_MODEL_ID_FIELD + "] can not be null or empty."
            );
        }
        return "{\"query\":{\"neural\":{\""
            + embeddingField
            + "\":{\"query_text\":\""
            + queryText
            + "\",\"model_id\":\""
            + embeddingModelId
            + "\",\"k\":"
            + k
            + "}}}"
            + " }";
    }

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
                T vectorDBToolOutput;

                if (hits != null && hits.length > 0) {
                    StringBuilder contextBuilder = new StringBuilder();
                    for (SearchHit hit : hits) {
                        String doc = AccessController.doPrivileged((PrivilegedExceptionAction<String>) () -> {
                            Map<String, Object> docContent = new HashMap<>();
                            docContent.put("_id", hit.getId());
                            docContent.put("_source", hit.getSourceAsMap());
                            return gson.toJson(docContent);
                        });
                        contextBuilder.append(doc).append("\n");
                    }
                    vectorDBToolOutput = (T) gson.toJson(contextBuilder.toString());
                } else {
                    vectorDBToolOutput = (T) "";
                }

                Map<String, String> tmpParameters = new HashMap<>();
                tmpParameters.putAll(parameters);

                if (vectorDBToolOutput instanceof List
                    && !((List) vectorDBToolOutput).isEmpty()
                    && ((List) vectorDBToolOutput).get(0) instanceof ModelTensors) {
                    ModelTensors tensors = (ModelTensors) ((List) vectorDBToolOutput).get(0);
                    Object response = tensors.getMlModelTensors().get(0).getDataAsMap().get("response");
                    tmpParameters.put(OUTPUT_FIELD, response + "");
                } else if (vectorDBToolOutput instanceof ModelTensor) {
                    tmpParameters.put(OUTPUT_FIELD, escapeJson(toJson(((ModelTensor) vectorDBToolOutput).getDataAsMap())));
                } else {
                    if (vectorDBToolOutput instanceof String) {
                        tmpParameters.put(OUTPUT_FIELD, (String) vectorDBToolOutput);
                    } else {
                        tmpParameters.put(OUTPUT_FIELD, escapeJson(toJson(vectorDBToolOutput.toString())));
                    }
                }

                RemoteInferenceInputDataSet inputDataSet = RemoteInferenceInputDataSet.builder().parameters(tmpParameters).build();
                ActionRequest request = new MLPredictionTaskRequest(
                    modelId,
                    MLInput.builder().algorithm(FunctionName.REMOTE).inputDataset(inputDataSet).build()
                );
                client.execute(MLPredictionTaskAction.INSTANCE, request, ActionListener.<MLTaskResponse>wrap(resp -> {
                    ModelTensorOutput modelTensorOutput = (ModelTensorOutput) resp.getOutput();
                    modelTensorOutput.getMlModelOutputs();
                    if (this.getOutputParser() == null) {
                        listener.onResponse((T) modelTensorOutput.getMlModelOutputs());
                    } else {
                        listener.onResponse((T) this.getOutputParser().parse(modelTensorOutput.getMlModelOutputs()));
                    }
                }, e -> {
                    log.error("Failed to run model " + modelId, e);
                    listener.onFailure(e);
                }));
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
    public boolean validate(Map<String, String> parameters) {
        if (parameters == null || parameters.size() == 0) {
            return false;
        }
        String question = parameters.get(INPUT_FIELD);
        return question != null;
    }

    public static class Factory extends AbstractRetrieverTool.Factory<RAGTool> {
        private Client client;
        private NamedXContentRegistry xContentRegistry;

        private static Factory INSTANCE;

        public static Factory getInstance() {
            if (INSTANCE != null) {
                return INSTANCE;
            }
            synchronized (RAGTool.class) {
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
        public RAGTool create(Map<String, Object> params) {
            String embeddingModelId = (String) params.get(EMBEDDING_MODEL_ID_FIELD);
            String index = (String) params.get(INDEX_FIELD);
            String embeddingField = (String) params.get(EMBEDDING_FIELD);
            String[] sourceFields = gson.fromJson((String) params.get(SOURCE_FIELD), String[].class);
            String modelId = (String) params.get(MODEL_ID_FIELD);
            Integer docSize = params.containsKey(DOC_SIZE_FIELD) ? Integer.parseInt((String) params.get(DOC_SIZE_FIELD)) : 2;
            return RAGTool
                .builder()
                .client(client)
                .xContentRegistry(xContentRegistry)
                .index(index)
                .embeddingField(embeddingField)
                .sourceFields(sourceFields)
                .embeddingModelId(embeddingModelId)
                .docSize(docSize)
                .modelId(modelId)
                .build();
        }

        @Override
        public String getDefaultDescription() {
            return DEFAULT_DESCRIPTION;
        }
    }

    private String toJson(Object value) {
        return getString(value);
    }

    public static String getString(Object value) {
        try {
            return AccessController.doPrivileged((PrivilegedExceptionAction<String>) () -> {
                if (value instanceof String) {
                    return (String) value;
                } else {
                    return gson.toJson(value);
                }
            });
        } catch (PrivilegedActionException e) {
            throw new RuntimeException(e);
        }
    }
}
