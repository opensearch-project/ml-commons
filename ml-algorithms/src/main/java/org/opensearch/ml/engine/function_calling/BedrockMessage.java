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
public class BedrockMessage implements LLMMessage {

    private String role;
    private List<Object> content = new ArrayList<>();

    BedrockMessage() {
        this("user");
    }

    BedrockMessage(String role) {
        this(role, null);
    }

    BedrockMessage(String role, List<Object> content) {
        this.role = role;
        if (content != null) {
            this.content = content;
        }
    }

    public String getResponse() {
        return StringUtils.toJson(Map.of("role", role, "content", content));
    }
}
