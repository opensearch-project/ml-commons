/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.helper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.opensearch.common.inject.Inject;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.core.action.ActionListener;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.MLModel;
import org.opensearch.ml.common.dataset.TextDocsInputDataSet;
import org.opensearch.ml.common.input.MLInput;
import org.opensearch.ml.common.memorycontainer.MemoryStorageConfig;
import org.opensearch.ml.common.model.MLModelState;
import org.opensearch.ml.common.output.MLOutput;
import org.opensearch.ml.common.output.model.ModelTensor;
import org.opensearch.ml.common.output.model.ModelTensorOutput;
import org.opensearch.ml.common.output.model.ModelTensors;
import org.opensearch.ml.common.transport.prediction.MLPredictionTaskAction;
import org.opensearch.ml.common.transport.prediction.MLPredictionTaskRequest;
import org.opensearch.ml.model.MLModelManager;
import org.opensearch.transport.client.Client;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import lombok.extern.log4j.Log4j2;

/**
 * Helper class for memory embedding operations
 */
@Log4j2
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class MemoryEmbeddingHelper {

    Client client;
    MLModelManager mlModelManager;

    @Inject
    public MemoryEmbeddingHelper(Client client, MLModelManager mlModelManager) {
        this.client = client;
        this.mlModelManager = mlModelManager;
    }

    /**
     * Generate embeddings for multiple texts
     * 
     * @param texts list of texts to embed
     * @param storageConfig memory storage configuration
     * @param listener action listener for the result
     */
    public void generateEmbeddingsForMultipleTexts(
        List<String> texts,
        MemoryStorageConfig storageConfig,
        ActionListener<List<Object>> listener
    ) {
        if (texts.isEmpty()) {
            listener.onResponse(new ArrayList<>());
            return;
        }

        String embeddingModelId = storageConfig.getEmbeddingModelId();
        FunctionName embeddingModelType = storageConfig.getEmbeddingModelType();

        // Validate model state before generating embeddings
        validateEmbeddingModelState(embeddingModelId, embeddingModelType, ActionListener.wrap(isValid -> {
            // Create MLInput for text embedding with multiple documents
            MLInput mlInput = MLInput
                .builder()
                .algorithm(embeddingModelType)
                .inputDataset(TextDocsInputDataSet.builder().docs(texts).build())
                .build();

            // Create prediction request
            MLPredictionTaskRequest predictionRequest = MLPredictionTaskRequest
                .builder()
                .modelId(embeddingModelId)
                .mlInput(mlInput)
                .build();

            // Execute prediction
            client.execute(MLPredictionTaskAction.INSTANCE, predictionRequest, ActionListener.wrap(response -> {
                try {
                    MLOutput mlOutput = response.getOutput();
                    if (mlOutput instanceof ModelTensorOutput) {
                        ModelTensorOutput tensorOutput = (ModelTensorOutput) mlOutput;
                        List<Object> embeddings = new ArrayList<>();

                        if (tensorOutput.getMlModelOutputs() != null) {
                            for (ModelTensors modelTensors : tensorOutput.getMlModelOutputs()) {
                                Object embedding = null;
                                if (embeddingModelType == FunctionName.TEXT_EMBEDDING) {
                                    embedding = extractDenseEmbeddingFromModelTensors(modelTensors);
                                } else if (embeddingModelType == FunctionName.SPARSE_ENCODING) {
                                    embedding = extractSparseEmbeddingFromModelTensors(modelTensors);
                                }
                                embeddings.add(embedding);
                            }
                        }

                        listener.onResponse(embeddings);
                    } else {
                        log.error("Unexpected ML output type: {}", mlOutput.getClass().getName());
                        listener.onFailure(new IllegalStateException("Unexpected ML output type: " + mlOutput.getClass().getName()));
                    }
                } catch (Exception e) {
                    log.error("Failed to extract embeddings from ML output", e);
                    listener.onFailure(new IllegalStateException("Failed to extract embeddings from ML output", e));
                }
            }, e -> {
                log.error("Failed to generate embeddings", e);
                listener.onFailure(e);
            }));
        }, listener::onFailure));
    }

    /**
     * Generate embedding for a single text
     * 
     * @param text text to embed
     * @param storageConfig memory storage configuration
     * @param listener action listener for the result
     */
    public void generateEmbedding(String text, MemoryStorageConfig storageConfig, ActionListener<Object> listener) {
        if (storageConfig == null || !storageConfig.isSemanticStorageEnabled()) {
            listener.onResponse(null);
            return;
        }

        String embeddingModelId = storageConfig.getEmbeddingModelId();
        FunctionName embeddingModelType = storageConfig.getEmbeddingModelType();

        if (embeddingModelId == null || embeddingModelType == null) {
            log.error("Embedding model configuration is missing");
            listener.onResponse(null);
            return;
        }

        // Validate model state before generating embedding
        validateEmbeddingModelState(embeddingModelId, embeddingModelType, ActionListener.wrap(isValid -> {
            // Create MLInput for text embedding
            MLInput mlInput = MLInput
                .builder()
                .algorithm(embeddingModelType)
                .inputDataset(TextDocsInputDataSet.builder().docs(Arrays.asList(text)).build())
                .build();

            // Create prediction request
            MLPredictionTaskRequest predictionRequest = MLPredictionTaskRequest
                .builder()
                .modelId(embeddingModelId)
                .mlInput(mlInput)
                .build();

            // Execute prediction
            client.execute(MLPredictionTaskAction.INSTANCE, predictionRequest, ActionListener.wrap(response -> {
                try {
                    MLOutput mlOutput = response.getOutput();
                    if (mlOutput instanceof ModelTensorOutput) {
                        ModelTensorOutput tensorOutput = (ModelTensorOutput) mlOutput;
                        Object embedding = null;

                        if (embeddingModelType == FunctionName.TEXT_EMBEDDING) {
                            embedding = buildDenseEmbeddingFromResponse(tensorOutput);
                        } else if (embeddingModelType == FunctionName.SPARSE_ENCODING) {
                            embedding = buildSparseEmbeddingFromResponse(tensorOutput);
                        }

                        listener.onResponse(embedding);
                    } else {
                        log.error("Unexpected ML output type: {}", mlOutput.getClass().getName());
                        listener.onResponse(null);
                    }
                } catch (Exception e) {
                    log.error("Failed to extract embedding from ML output", e);
                    listener.onResponse(null);
                }
            }, e -> {
                log.error("Failed to generate embedding", e);
                listener.onResponse(null);
            }));
        }, e -> {
            log.error("Failed to validate embedding model state", e);
            listener.onResponse(null);
        }));
    }

    /**
     * Validate embedding model state
     * 
     * @param modelId embedding model ID
     * @param modelType embedding model type
     * @param listener action listener for the result
     */
    public void validateEmbeddingModelState(String modelId, FunctionName modelType, ActionListener<Boolean> listener) {
        // If model type is REMOTE, no need to check state
        if (modelType == FunctionName.REMOTE) {
            listener.onResponse(true);
            return;
        }

        // For TEXT_EMBEDDING or SPARSE_ENCODING, check if model is DEPLOYED
        try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
            ActionListener<MLModel> wrappedListener = ActionListener.runBefore(ActionListener.wrap(model -> {
                MLModelState modelState = model.getModelState();
                if (modelState != MLModelState.DEPLOYED) {
                    listener
                        .onFailure(
                            new IllegalStateException(
                                String.format("Embedding model must be in DEPLOYED state, current state: %s", modelState)
                            )
                        );
                } else {
                    listener.onResponse(true);
                }
            }, e -> {
                log.error("Failed to get embedding model: " + modelId, e);
                listener.onFailure(new IllegalStateException("Failed to validate embedding model state", e));
            }), context::restore);

            mlModelManager.getModel(modelId, wrappedListener);
        }
    }

    /**
     * Extract dense embedding from model tensors
     * 
     * @param modelTensors model tensors containing the embedding
     * @return float array representing the dense embedding
     */
    private Object extractDenseEmbeddingFromModelTensors(ModelTensors modelTensors) {
        if (modelTensors.getMlModelTensors() == null || modelTensors.getMlModelTensors().isEmpty()) {
            return null;
        }

        for (ModelTensor tensor : modelTensors.getMlModelTensors()) {
            if ("sentence_embedding".equals(tensor.getName()) && tensor.getData() != null) {
                Number[] data = tensor.getData();
                float[] floatData = new float[data.length];
                for (int i = 0; i < data.length; i++) {
                    floatData[i] = data[i].floatValue();
                }
                return floatData;
            }
        }
        return null;
    }

    /**
     * Extract sparse embedding from model tensors
     * 
     * @param modelTensors model tensors containing the embedding
     * @return map representing the sparse embedding
     */
    private Object extractSparseEmbeddingFromModelTensors(ModelTensors modelTensors) {
        if (modelTensors.getMlModelTensors() == null || modelTensors.getMlModelTensors().isEmpty()) {
            return null;
        }

        for (ModelTensor tensor : modelTensors.getMlModelTensors()) {
            Map<String, ?> dataMap = tensor.getDataAsMap();
            if (dataMap != null) {
                if (dataMap.containsKey("response") && dataMap.get("response") instanceof List) {
                    List<?> responseList = (List<?>) dataMap.get("response");
                    if (!responseList.isEmpty() && responseList.get(0) instanceof Map) {
                        return responseList.get(0);
                    }
                }
                return dataMap;
            }
        }
        return null;
    }

    /**
     * Build dense embedding from response
     * 
     * @param tensorOutput model tensor output
     * @return float array representing the dense embedding
     */
    private float[] buildDenseEmbeddingFromResponse(ModelTensorOutput tensorOutput) {
        if (tensorOutput.getMlModelOutputs() == null || tensorOutput.getMlModelOutputs().isEmpty()) {
            log.debug("No model outputs found in tensor output");
            return null;
        }

        ModelTensors modelTensors = tensorOutput.getMlModelOutputs().get(0);
        if (modelTensors.getMlModelTensors() == null || modelTensors.getMlModelTensors().isEmpty()) {
            log.debug("No model tensors found in model output");
            return null;
        }

        // For dense embeddings, look for the sentence_embedding tensor
        for (ModelTensor tensor : modelTensors.getMlModelTensors()) {
            if ("sentence_embedding".equals(tensor.getName()) && tensor.getData() != null) {
                Number[] data = tensor.getData();
                log.debug("Found sentence_embedding tensor with dimension: {}", data.length);

                // Convert Number[] to float[] for proper storage
                float[] floatData = new float[data.length];
                for (int i = 0; i < data.length; i++) {
                    floatData[i] = data[i].floatValue();
                }
                return floatData;
            }
        }

        log.error("No sentence_embedding tensor found for dense embedding");
        return null;
    }

    /**
     * Build sparse embedding from response
     * 
     * @param tensorOutput model tensor output
     * @return map representing the sparse embedding
     */
    private Map<String, ?> buildSparseEmbeddingFromResponse(ModelTensorOutput tensorOutput) {
        if (tensorOutput.getMlModelOutputs() == null || tensorOutput.getMlModelOutputs().isEmpty()) {
            log.debug("No model outputs found in tensor output");
            return null;
        }

        ModelTensors modelTensors = tensorOutput.getMlModelOutputs().get(0);
        if (modelTensors.getMlModelTensors() == null || modelTensors.getMlModelTensors().isEmpty()) {
            log.debug("No model tensors found in model output");
            return null;
        }

        // For sparse embeddings, find the tensor with dataAsMap
        for (ModelTensor tensor : modelTensors.getMlModelTensors()) {
            Map<String, ?> dataMap = tensor.getDataAsMap();
            if (dataMap != null) {
                // Check if sparse embedding is nested in a response field
                if (dataMap.containsKey("response") && dataMap.get("response") instanceof List) {
                    List<?> responseList = (List<?>) dataMap.get("response");
                    if (!responseList.isEmpty() && responseList.get(0) instanceof Map) {
                        Map<String, ?> sparseMap = (Map<String, ?>) responseList.get(0);
                        log.debug("Extracted sparse embedding from nested response with {} tokens", sparseMap.size());
                        return sparseMap;
                    }
                }
                // Otherwise return the direct map
                log.debug("Using direct sparse embedding with {} tokens", dataMap.size());
                return dataMap;
            }
        }

        log.error("No sparse embedding data found");
        return null;
    }
}
