/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.model;

import org.opensearch.ml.common.agent.BedrockConverseModelProvider;
import org.opensearch.ml.common.agent.GeminiGenerateContentModelProvider;
import org.opensearch.ml.common.input.execute.agent.ModelProviderType;

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
        return switch (type) {
            case BEDROCK_CONVERSE -> new BedrockConverseModelProvider();
            case GEMINI_GENERATE_CONTENT -> new GeminiGenerateContentModelProvider();
            default -> throw new IllegalArgumentException("Unsupported model provider type: " + providerType);
        };
    }
}
