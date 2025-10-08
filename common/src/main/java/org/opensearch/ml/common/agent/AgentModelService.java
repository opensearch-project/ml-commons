/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.agent;

import java.util.Map;

import org.opensearch.ml.common.connector.Connector;
import org.opensearch.ml.common.transport.register.MLRegisterModelInput;

/**
 * Service class for handling model creation during agent registration
 */
public class AgentModelService {
    
    /**
     * Creates a model input from the simplified agent model specification
     * @param modelSpec the model specification from agent registration
     * @return MLRegisterModelInput ready for model registration
     * @throws IllegalArgumentException if model provider is not supported
     */
    public static MLRegisterModelInput createModelFromSpec(MLAgentModelSpec modelSpec) {
        validateModelSpec(modelSpec);
        // Get the appropriate model provider
        ModelProvider provider = ModelProviderFactory.getProvider(modelSpec.getModelProvider());
        
        // Create connector using the provider
        Connector connector = provider.createConnector(
            modelSpec.getModel(),
            modelSpec.getCredential(),
            modelSpec.getModelParameters()
        );
        
        // Create model input using the provider
        MLRegisterModelInput modelInput = provider.createModelInput(
            modelSpec.getModel(),
            connector,
            modelSpec.getModelParameters()
        );
        
        return modelInput;
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
            throw new IllegalArgumentException("Model specification cannot be null");
        }
        
        if (modelSpec.getModel() == null || modelSpec.getModel().trim().isEmpty()) {
            throw new IllegalArgumentException("Model name cannot be null or empty");
        }
        
        if (modelSpec.getModelProvider() == null || modelSpec.getModelProvider().trim().isEmpty()) {
            throw new IllegalArgumentException("Model provider cannot be null or empty");
        }
        
        // Validate that the provider type is supported
        try {
            ModelProviderType.from(modelSpec.getModelProvider());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unsupported model provider: " + modelSpec.getModelProvider());
        }
    }
}