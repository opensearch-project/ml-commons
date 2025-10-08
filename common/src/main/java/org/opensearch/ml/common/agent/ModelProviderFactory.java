/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.agent;

/**
 * Factory class for creating model providers
 */
// ToDo: modify this to a map that is automatically created using the enum
    // Have the enum define the provider class
public class ModelProviderFactory {
    
    /**
     * Get model provider instance based on provider type
     * @param providerType the provider type string
     * @return ModelProvider instance
     * @throws IllegalArgumentException if provider type is not supported
     */
    public static ModelProvider getProvider(String providerType) {
        ModelProviderType type = ModelProviderType.from(providerType);
        
        switch (type) {
            case BEDROCK_CONVERSE:
                return new BedrockConverseModelProvider();
            case OPENAI:
                // TODO: Implement OpenAI provider
                throw new UnsupportedOperationException("OpenAI provider not yet implemented");
            case AZURE_OPENAI:
                // TODO: Implement Azure OpenAI provider
                throw new UnsupportedOperationException("Azure OpenAI provider not yet implemented");
            default:
                throw new IllegalArgumentException("Unsupported model provider type: " + providerType);
        }
    }
}