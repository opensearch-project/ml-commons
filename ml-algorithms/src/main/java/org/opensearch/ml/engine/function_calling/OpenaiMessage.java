/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.function_calling;

import java.util.Map;

import org.opensearch.ml.common.utils.StringUtils;

import lombok.Data;

@Data
public class OpenaiMessage implements LLMMessage {

    private String role;
    private String content;
    private String toolCallId;

    OpenaiMessage() {
        this("tool");
    }

    OpenaiMessage(String role) {
        this(role, null);
    }

    OpenaiMessage(String role, Object content) {
        this.role = role != null ? role : "tool";
        this.content = (String) content;
    }

    public String getResponse() {
        String roleToUse = role != null ? role : "tool";
        String contentToUse = content != null ? content : "";
        return StringUtils.toJson(Map.of("role", roleToUse, "tool_call_id", toolCallId, "content", contentToUse));
    }
}
