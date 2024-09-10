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

import org.opensearch.core.action.ActionListener;

/**
 * Capabilities of large language models, e.g. completion, embeddings, etc.
 */
public interface Llm {

    // TODO Ensure the current implementation works with all models supported by Bedrock.
    enum ModelProvider {
        OPENAI,
        BEDROCK,
        COHERE
    }

    void doChatCompletion(ChatCompletionInput input, ActionListener<ChatCompletionOutput> listener);
}
