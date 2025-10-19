/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.agent;

import java.time.Instant;
import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * Enhanced memory message for chat agents - designed for extensibility.
 * Supports multiple memory types: Agentic, Remote Agentic, Bedrock AgentCore, etc.
 * 
 * Design Philosophy:
 * - Text-first with rich metadata (hybrid approach)
 * - Extensible for future multimodal content
 * - Memory-type agnostic interface
 * - Role-based message support
 */
@Builder
@AllArgsConstructor
@Getter
public class ChatMessage {
    private String id;
    private Instant timestamp;
    private String sessionId;
    private String role;              // "user", "assistant", "system", "tool"
    private String content;           // Primary text content
    private String contentType;       // "text", "image", "tool_result", etc. (metadata)
    private String origin;            // "agentic_memory", "remote_agentic", "bedrock_agentcore", etc.
    private Map<String, Object> metadata; // Rich content details and memory-specific data
}
