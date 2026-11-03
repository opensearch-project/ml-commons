/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.input.execute.agent;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a message with role and content fields for conversation-style input.
 */
@Data
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Message {
    private String role; // flexible - any role allowed (user, assistant, system, etc.)
    private List<ContentBlock> content;

    private List<ToolCall> toolCalls;  // Optional, for assistant messages with tool calls
    private String toolCallId;  // Optional, for tool result messages (role "tool")

    public Message(String role, List<ContentBlock> content) {
        this.role = role;
        this.content = content;
    }
}
