/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.opensearch.searchpipelines.questionanswering.generative.llm;

/**
 * Outputs from chat completion API.
 */
public interface ChatCompletionOutput {
    String getAnswer();
}
