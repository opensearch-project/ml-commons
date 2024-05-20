/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.tools;

import com.google.gson.annotations.SerializedName;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

public class PromptHandler {

    boolean apply(String prompt) {
        return prompt.contains(llmThought().getQuestion());
    }

    LLMThought llmThought() {
        return new LLMThought();
    }

    String response(String prompt) {
        if (prompt.contains("Human: TOOL RESPONSE ")) {
            return "```json{\n"
                + "    \"thought\": \"Thought: Now I know the final answer\",\n"
                + "    \"final_answer\": \"final answer\"\n"
                + "}```";
        } else {
            return "```json{\n"
                + "    \"thought\": \"Thought: Let me use tool to figure out\",\n"
                + "    \"action\": \""
                + this.llmThought().getAction()
                + "\",\n"
                + "    \"action_input\": \""
                + this.llmThought().getActionInput()
                + "\"\n"
                + "}```";
        }
    }

    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Data
    static class LLMThought {
        String question;
        String action;
        String actionInput;
    }

    @Data
    static class LLMResponse {
        String completion;
        @SerializedName("stop_reason")
        String stopReason = "stop_sequence";
        String stop = "\\n\\nHuman:";
    }
}
