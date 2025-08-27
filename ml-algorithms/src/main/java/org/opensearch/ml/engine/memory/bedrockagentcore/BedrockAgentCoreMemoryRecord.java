/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.memory.bedrockagentcore;

import java.time.Instant;
import java.util.Map;

import org.opensearch.ml.engine.memory.BaseMessage;

import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Memory record for Bedrock AgentCore memory integration.
 * Represents individual memory records stored in Bedrock AgentCore.
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class BedrockAgentCoreMemoryRecord extends BaseMessage {

    private String content;           // User input/question
    private String response;          // Agent response
    private String sessionId;         // Bedrock AgentCore session identifier
    private String memoryId;          // Bedrock AgentCore memory container ID
    private Map<String, Object> metadata; // Bedrock AgentCore-specific fields

    // Bedrock-specific fields
    private String eventId;           // Bedrock event identifier
    private String traceId;           // Bedrock trace identifier
    private Instant timestamp;        // Event timestamp

    @Builder(builderMethodName = "bedrockAgentCoreMemoryRecordBuilder")
    public BedrockAgentCoreMemoryRecord(
        String type,
        String sessionId,
        String content,
        String response,
        String memoryId,
        Map<String, Object> metadata,
        String eventId,
        String traceId,
        Instant timestamp
    ) {
        super(type, sessionId);
        this.content = content;
        this.response = response;
        this.sessionId = sessionId;
        this.memoryId = memoryId;
        this.metadata = metadata;
        this.eventId = eventId;
        this.traceId = traceId;
        this.timestamp = timestamp;
    }

    // Default constructor for serialization
    public BedrockAgentCoreMemoryRecord() {
        super(null, null);
    }
}
