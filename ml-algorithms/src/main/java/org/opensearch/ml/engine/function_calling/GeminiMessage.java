/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.function_calling;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.opensearch.ml.common.utils.StringUtils;

import lombok.Data;

@Data
public class GeminiMessage implements LLMMessage {

    private String role;
    private List<Object> content;

    GeminiMessage() {
        this("user");
    }

    GeminiMessage(String role) {
        this(role, null);
    }

    GeminiMessage(String role, List<Object> content) {
        this.role = role != null ? role : "user";
        this.content = content != null ? new ArrayList<>(content) : new ArrayList<>();
    }

    public String getResponse() {
        String roleToUse = role != null ? role : "user";
        List<Object> contentToUse = content != null ? content : new ArrayList<>();
        // Gemini API uses "parts" in JSON structure, not "content"
        return StringUtils.toJson(Map.of("role", roleToUse, "parts", contentToUse));
    }
}
