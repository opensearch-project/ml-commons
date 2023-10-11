/*
 * Copyright 2023 Aryn
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.opensearch.searchpipelines.questionanswering.generative.llm;

import java.util.List;

import org.opensearch.ml.common.conversation.Interaction;
import org.opensearch.searchpipelines.questionanswering.generative.prompt.PromptUtil;

/**
 * Helper class for creating inputs and outputs for different implementations of LLMs.
 */
public class LlmIOUtil {

    private static final String BEDROCK_PROVIDER_PREFIX = "bedrock/";

    public static ChatCompletionInput createChatCompletionInput(
        String llmModel,
        String question,
        List<Interaction> chatHistory,
        List<String> contexts,
        int timeoutInSeconds
    ) {

        // TODO pick the right subclass based on the modelId.

        return createChatCompletionInput(
            PromptUtil.DEFAULT_SYSTEM_PROMPT,
            null,
            llmModel,
            question,
            chatHistory,
            contexts,
            timeoutInSeconds
        );
    }

    public static ChatCompletionInput createChatCompletionInput(
        String systemPrompt,
        String userInstructions,
        String llmModel,
        String question,
        List<Interaction> chatHistory,
        List<String> contexts,
        int timeoutInSeconds
    ) {
        Llm.ModelProvider provider = Llm.ModelProvider.OPENAI;
        if (llmModel != null && llmModel.startsWith(BEDROCK_PROVIDER_PREFIX)) {
            provider = Llm.ModelProvider.BEDROCK;
        }
        return new ChatCompletionInput(
            llmModel,
            question,
            chatHistory,
            contexts,
            timeoutInSeconds,
            systemPrompt,
            userInstructions,
            provider
        );
    }
}
