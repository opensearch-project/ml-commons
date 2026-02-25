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
    private List<Object> content;

    BedrockMessage() {
        this("user");
    }

    BedrockMessage(String role) {
        this(role, null);
    }

    BedrockMessage(String role, List<Object> content) {
        this.role = role != null ? role : "user";
        this.content = content != null ? new ArrayList<>(content) : new ArrayList<>();
    }

    public String getResponse() {
        String roleToUse = role != null ? role : "user";
        List<Object> contentToUse = content != null ? content : new ArrayList<>();
        return StringUtils.toJson(Map.of("role", roleToUse, "content", contentToUse));
    }
}
