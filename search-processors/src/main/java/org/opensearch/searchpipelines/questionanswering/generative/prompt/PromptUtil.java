/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.opensearch.searchpipelines.questionanswering.generative.prompt;

import java.util.List;

/**
 * TODO Should prompt engineering llm-specific?
 *
 */
public class PromptUtil {
    public static String getQuestionRephrasingPrompt(String originalQuestion, List<String> chatHistory) {
        return null;
    }

    public static String getChatCompletionPrompt(String question, List<String> chatHistory, List<String> passages) {
        return null;
    }
}
