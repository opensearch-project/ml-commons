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
    private List<Object> parts = new ArrayList<>();

    GeminiMessage() {
        this("user");
    }

    GeminiMessage(String role) {
        this(role, null);
    }

    GeminiMessage(String role, List<Object> parts) {
        this.role = role;
        if (parts != null) {
            this.parts = parts;
        }
    }

    public Object getContent() {
        return parts;
    }

    public String getResponse() {
        return StringUtils.toJson(Map.of("role", role, "parts", parts));
    }
}
