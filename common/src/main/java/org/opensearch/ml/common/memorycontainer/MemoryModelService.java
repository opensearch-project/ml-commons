/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.memorycontainer;

import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.agent.BedrockEmbeddingModelProvider;
import org.opensearch.ml.common.agent.MLAgentModelSpec;
import org.opensearch.ml.common.connector.Connector;
import org.opensearch.ml.common.model.ModelProvider;
import org.opensearch.ml.common.model.ModelProviderFactory;
import org.opensearch.ml.common.transport.register.MLRegisterModelInput;

import lombok.extern.log4j.Log4j2;

/**
 * Service for creating models during memory container creation.
 * Translates inline model specs into registered models, analogous to AgentModelService.
 */
@Log4j2
public class MemoryModelService {

    /**
     * Creates a model registration input from an inline model spec.
     */
    public static MLRegisterModelInput createModelFromSpec(MLAgentModelSpec modelSpec) {
        validateModelSpec(modelSpec);
        ModelProvider provider = ModelProviderFactory.getProvider(modelSpec.getModelProvider());
        Connector connector = provider.createConnector(modelSpec.getModelId(), modelSpec.getCredential(), modelSpec.getModelParameters());
        return provider.createModelInput(modelSpec.getModelId(), connector, modelSpec.getModelParameters());
    }

    /**
     * Auto-detect embedding model type (TEXT_EMBEDDING or SPARSE_ENCODING) from model ID.
     * @return FunctionName or null if unknown
     */
    public static FunctionName detectEmbeddingType(String modelId) {
        BedrockEmbeddingModelProvider.EmbeddingModelInfo info = BedrockEmbeddingModelProvider.getModelInfo(modelId);
        return info != null ? info.functionName : null;
    }

    /**
     * Auto-detect embedding dimension from model ID.
     * @return dimension or null if unknown
     */
    public static Integer detectEmbeddingDimension(String modelId) {
        BedrockEmbeddingModelProvider.EmbeddingModelInfo info = BedrockEmbeddingModelProvider.getModelInfo(modelId);
        return info != null ? info.dimension : null;
    }

    private static void validateModelSpec(MLAgentModelSpec modelSpec) {
        if (modelSpec == null) {
            throw new IllegalArgumentException("Model specification not found");
        }
        if (modelSpec.getModelId() == null || modelSpec.getModelId().trim().isEmpty()) {
            throw new IllegalArgumentException("model_id cannot be null or empty");
        }
        if (modelSpec.getModelProvider() == null || modelSpec.getModelProvider().trim().isEmpty()) {
            throw new IllegalArgumentException("model_provider cannot be null or empty");
        }
    }
}
