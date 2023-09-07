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

import org.opensearch.ml.common.conversation.Interaction;

import java.util.List;

/**
 * Helper class for creating inputs and outputs for different implementations of LLMs.
 */
public class LlmIOUtil {

    public static ChatCompletionInput createChatCompletionInput(String llmModel, String question, List<Interaction> chatHistory, List<String> contexts) {

        // TODO pick the right subclass based on the modelId.

        return new ChatCompletionInput(llmModel, question, chatHistory, contexts);
    }
}
