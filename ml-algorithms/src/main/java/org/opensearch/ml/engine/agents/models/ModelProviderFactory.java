/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.agents.models;

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
            case GEMINI_V1BETA_GENERATE_CONTENT -> new GeminiV1BetaGenerateContentModelProvider();
            case OPENAI_V1_CHAT_COMPLETIONS -> new OpenaiV1ChatCompletionsModelProvider();
            default -> throw new IllegalArgumentException("Unsupported model provider type");
        };
    }
}
