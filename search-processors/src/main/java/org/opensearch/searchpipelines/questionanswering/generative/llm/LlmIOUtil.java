/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.opensearch.searchpipelines.questionanswering.generative.llm;

import java.util.List;

/**
 * Helper class for creating inputs and outputs for different implementations of LLMs.
 */
public class LlmIOUtil {

    public static ChatCompletionInput createChatCompletionInput(String llmModel, String question, List<String> chatHistory, List<String> contexts) {

        // TODO pick the right subclass based on the modelId.

        return new OpenSearchChatCompletionInput(llmModel, question, chatHistory, contexts);
    }
}
