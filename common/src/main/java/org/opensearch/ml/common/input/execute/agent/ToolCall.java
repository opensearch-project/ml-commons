/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.input.execute.agent;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a tool call with ID, type, and function details.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ToolCall {
    private String id;
    private String type;
    private ToolFunction function;
    /**
     * Opaque provider token that must be sent back with this call, such as a Gemini thought
     * signature. Null for providers that don't use one.
     */
    private String thoughtSignature;

    public ToolCall(String id, String type, ToolFunction function) {
        this(id, type, function, null);
    }

    /**
     * Represents the function details within a tool call.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ToolFunction {
        private String name;
        private String arguments;
    }
}
