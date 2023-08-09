/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.opensearch.searchpipelines.questionanswering.generative.llm;

import org.opensearch.client.Client;

/**
 * Helper class for wiring LLMs based on the model ID.
 *
 * TODO Should we extend this use case beyond HttpConnectors/Remote Inference?
 */
public class ModelLocator {

    public static Llm getRemoteLlm(String openSearchModelId, Client client) {
        return new OpenSearchChatConnector(openSearchModelId, client);
    }

    // For testing locally
    static class LocalLlm implements Llm {

        @Override
        public ChatCompletionOutput createChatCompletion(ChatCompletionInput input) {
            return new ChatCompletionOutput() {
                @Override
                public String getAnswer() {
                    return "dummy";
                }
            };
        }
    }
}
