/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.opensearch.searchpipelines.questionanswering.generative.llm;

/**
 * Capabilities of large language models, e.g. completion, embeddings, etc.
 */
public interface Llm {

    ChatCompletionOutput createChatCompletion(ChatCompletionInput input);
}
