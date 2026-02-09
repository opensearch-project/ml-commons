/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.agent;

import org.opensearch.ml.common.connector.Connector;
import org.opensearch.ml.common.input.execute.agent.ModelProviderType;
import org.opensearch.ml.common.model.ModelProvider;
import org.opensearch.ml.common.model.ModelProviderFactory;
import org.opensearch.ml.common.transport.register.MLRegisterModelInput;

import lombok.extern.log4j.Log4j2;

/**
 * Service class for handling model creation during agent registration
 */
@Log4j2
public class AgentModelService {

    /**
     * Creates a model input from the agent model specification
     * @param modelSpec the model specification from agent registration
     * @return MLRegisterModelInput ready for model registration
     * @throws IllegalArgumentException if model provider is not supported
     */
    public static MLRegisterModelInput createModelFromSpec(MLAgentModelSpec modelSpec) {
        validateModelSpec(modelSpec);
        ModelProvider provider = ModelProviderFactory.getProvider(modelSpec.getModelProvider());

        Connector connector = provider.createConnector(modelSpec.getModelId(), modelSpec.getCredential(), modelSpec.getModelParameters());

        return provider.createModelInput(modelSpec.getModelId(), connector, modelSpec.getModelParameters());
    }

    /**
     * Infers the LLM interface from model provider for function calling
     * @param modelProvider the model provider string
     * @return the corresponding LLM interface string, or null if not supported
     */
    public static String inferLLMInterface(String modelProvider) {
        if (modelProvider == null) {
            return null;
        }

        try {
            ModelProvider provider = ModelProviderFactory.getProvider(modelProvider);
            return provider.getLLMInterface();
        } catch (Exception e) {
            log.error("Failed to infer LLM interface", e);
            return null;
        }
    }

    /**
     * Validates that the model specification is complete and valid
     * @param modelSpec the model specification to validate
     * @throws IllegalArgumentException if validation fails
     */
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

        // Validate that the provider type is supported
        // Will Re-throw the original exception from ModelProviderType
        ModelProviderType.from(modelSpec.getModelProvider());
    }
}
