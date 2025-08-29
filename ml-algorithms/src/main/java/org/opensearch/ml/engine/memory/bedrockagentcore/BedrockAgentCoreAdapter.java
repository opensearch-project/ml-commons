/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.memory.bedrockagentcore;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.opensearch.ml.common.conversation.Interaction;
import org.opensearch.ml.engine.memory.ConversationIndexMessage;

import lombok.extern.log4j.Log4j2;

/**
 * Adapter for converting between Bedrock AgentCore memory format and OpenSearch ML Commons format.
 * Handles compatibility with existing agent runners.
 */
@Log4j2
public class BedrockAgentCoreAdapter {

    /**
     * Convert ConversationIndexMessage to BedrockAgentCoreMemoryRecord
     */
    public BedrockAgentCoreMemoryRecord convertToBedrockRecord(ConversationIndexMessage message) {
        return BedrockAgentCoreMemoryRecord
            .bedrockAgentCoreMemoryRecordBuilder()
            .type(message.getType())
            .sessionId(message.getSessionId())
            .content(message.getQuestion())
            .response(message.getResponse())
            .metadata(Map.of("type", message.getType(), "finalAnswer", message.getFinalAnswer() != null ? message.getFinalAnswer() : true))
            .timestamp(Instant.now())
            .build();
    }

    /**
     * Convert BedrockAgentCoreMemoryRecord to ConversationIndexMessage
     */
    public ConversationIndexMessage convertFromBedrockRecord(BedrockAgentCoreMemoryRecord record) {
        return ConversationIndexMessage
            .conversationIndexMessageBuilder()
            .type(record.getType())
            .sessionId(record.getSessionId())
            .question(record.getContent())
            .response(record.getResponse())
            .finalAnswer((Boolean) record.getMetadata().getOrDefault("finalAnswer", true))
            .build();
    }

    /**
     * Convert list of BedrockAgentCoreMemoryRecord to Interaction list (for PER agent)
     */
    public List<Interaction> convertToInteractions(List<BedrockAgentCoreMemoryRecord> records) {
        return records
            .stream()
            .map(
                record -> Interaction
                    .builder()
                    .conversationId(record.getSessionId())
                    .input(record.getContent())
                    .response(record.getResponse())
                    .createTime(record.getTimestamp() != null ? record.getTimestamp() : Instant.now())
                    .origin("bedrock-agentcore")
                    .build()
            )
            .collect(Collectors.toList());
    }

    /**
     * Convert Interaction to BedrockAgentCoreMemoryRecord
     */
    public BedrockAgentCoreMemoryRecord convertFromInteraction(Interaction interaction) {
        return BedrockAgentCoreMemoryRecord
            .bedrockAgentCoreMemoryRecordBuilder()
            .sessionId(interaction.getConversationId())
            .content(interaction.getInput())
            .response(interaction.getResponse())
            .timestamp(interaction.getCreateTime())
            .metadata(Map.of("source", "interaction", "finalAnswer", true))
            .build();
    }

    /**
     * Convert BedrockAgentCoreMemoryRecord to Bedrock event data format
     */
    public Map<String, Object> convertToEventData(BedrockAgentCoreMemoryRecord record) {
        // TODO: Define proper Bedrock event data structure
        return Map
            .of(
                "content",
                record.getContent(),
                "response",
                record.getResponse(),
                "sessionId",
                record.getSessionId(),
                "timestamp",
                record.getTimestamp() != null ? record.getTimestamp().toString() : Instant.now().toString(),
                "metadata",
                record.getMetadata() != null ? record.getMetadata() : Map.of()
            );
    }

    /**
     * Convert Bedrock event data to BedrockAgentCoreMemoryRecord
     */
    public BedrockAgentCoreMemoryRecord convertFromEventData(Map<String, Object> eventData, String eventId) {
        return BedrockAgentCoreMemoryRecord
            .bedrockAgentCoreMemoryRecordBuilder()
            .eventId(eventId)
            .content((String) eventData.get("content"))
            .response((String) eventData.get("response"))
            .sessionId((String) eventData.get("sessionId"))
            .timestamp(Instant.parse((String) eventData.get("timestamp")))
            .metadata((Map<String, Object>) eventData.get("metadata"))
            .build();
    }
}
