/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.function_calling;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.opensearch.ml.common.utils.StringUtils;

import lombok.Data;

/**
 * An Anthropic Messages API message, used to send tool results back to the model.
 */
@Data
public class AnthropicMessage implements LLMMessage {

    private String role;
    private List<Object> content;

    AnthropicMessage() {
        this("user", null);
    }

    AnthropicMessage(String role, List<Object> content) {
        this.role = role != null ? role : "user";
        this.content = content != null ? new ArrayList<>(content) : new ArrayList<>();
    }

    public String getResponse() {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("role", role != null ? role : "user");
        message.put("content", content != null ? content : new ArrayList<>());
        return StringUtils.toJson(message);
    }
}
