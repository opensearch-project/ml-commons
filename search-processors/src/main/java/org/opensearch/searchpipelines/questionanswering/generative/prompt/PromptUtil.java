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
package org.opensearch.searchpipelines.questionanswering.generative.prompt;

import com.google.common.annotations.VisibleForTesting;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.apache.commons.text.StringEscapeUtils;

import java.util.List;
import java.util.Locale;

/**
 * TODO Should prompt engineering llm-specific?
 *
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class PromptUtil {

    private static final String roleUser = "user";

    public static String getQuestionRephrasingPrompt(String originalQuestion, List<String> chatHistory) {
        return null;
    }

    public static String getChatCompletionPrompt(String question, List<String> chatHistory, List<String> contexts) {
        return buildMessageParameter(question, chatHistory, contexts);
    }

    @VisibleForTesting
    static String buildMessageParameter(String question, List<String> chatHistory, List<String> contexts) {
        // TODO better prompt template management is needed here.
        String instructions = "Generate a concise and informative answer in less than 100 words for the given question, taking into context: "
            + "- An enumerated list of search results"
            + "- A rephrase of the question that was used to generate the search results"
            + "- The conversation history"
            + "Cite search results using [${number}] notation."
            + "Do not repeat yourself, and NEVER repeat anything in the chat history."
            + "If there are any necessary steps or procedures in your answer, enumerate them.";
        StringBuffer sb = new StringBuffer();
        sb.append("[\n");
        sb.append(formatMessage(roleUser, instructions));
        sb.append(",\n");
        for (String result : contexts) {
            sb.append(formatMessage(roleUser, "SEARCH RESULTS: " + result));
            sb.append(",\n");
        }
        sb.append(formatMessage(roleUser, "QUESTION: " + question));
        sb.append(",\n");
        sb.append(formatMessage(roleUser, "ANSWER:"));
        sb.append("\n");
        sb.append("]");
        return sb.toString();
    }

    private static String formatMessage(String role, String content) {
        return String.format(Locale.ROOT, "{\"role\": \"%s\", \"content\": \"%s\"}", role, StringEscapeUtils.escapeJson(content));
    }
}
