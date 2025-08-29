/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.memory.bedrockagentcore;

import java.util.Map;

import org.opensearch.core.action.ActionListener;
import org.opensearch.ml.common.spi.memory.Memory;

import lombok.Getter;
import lombok.extern.log4j.Log4j2;

/**
 * Bedrock AgentCore memory implementation.
 * Provides memory storage using AWS Bedrock AgentCore service.
 */
@Log4j2
@Getter
public class BedrockAgentCoreMemory implements Memory<BedrockAgentCoreMemoryRecord> {

    public static final String TYPE = "bedrock_agentcore_memory";

    private final String memoryArn;       // Bedrock AgentCore memory ARN
    private final String sessionId;       // Bedrock AgentCore session ID
    private final BedrockAgentCoreClient bedrockClient;
    private final BedrockAgentCoreAdapter adapter;

    public BedrockAgentCoreMemory(
        String memoryArn,
        String sessionId,
        BedrockAgentCoreClient bedrockClient,
        BedrockAgentCoreAdapter adapter
    ) {
        this.memoryArn = memoryArn;
        this.sessionId = sessionId;
        this.bedrockClient = bedrockClient;
        this.adapter = adapter;
    }

    /**
     * Extract memory ID from memory ARN for Bedrock AgentCore API calls
     * ARN format: arn:aws:bedrock:region:account:agent-memory/memory-id
     */
    private String getMemoryId() {
        return memoryArn.substring(memoryArn.lastIndexOf('/') + 1);
    }

    @Override
    public String getType() {
        return TYPE;
    }

    @Override
    public void save(String id, BedrockAgentCoreMemoryRecord record) {
        save(
            id,
            record,
            ActionListener
                .wrap(
                    r -> log.info("Saved memory record to Bedrock AgentCore, session id: {}", id),
                    e -> log.error("Failed to save memory record to Bedrock AgentCore", e)
                )
        );
    }

    @Override
    public void save(String id, BedrockAgentCoreMemoryRecord record, ActionListener listener) {
        // TODO: Implement using BedrockAgentCore createEvent API
        log.info("Saving memory record to Bedrock AgentCore for session: {}", id);
        // Implementation will use bedrockClient.createEvent()
        listener.onResponse("placeholder-event-id");
    }

    @Override
    public void getMessages(String id, ActionListener<BedrockAgentCoreMemoryRecord> listener) {
        // TODO: Implement using BedrockAgentCore listMemoryRecords API
        log.info("Retrieving memory records from Bedrock AgentCore for session: {}", id);
        // Implementation will use bedrockClient.listMemoryRecords()
        listener.onResponse(null);
    }

    @Override
    public void clear() {
        // TODO: Implement using BedrockAgentCore deleteMemoryRecord API
        log.info("Clearing memory records from Bedrock AgentCore");
    }

    @Override
    public void remove(String id) {
        // TODO: Implement using BedrockAgentCore deleteMemoryRecord API
        log.info("Removing memory record from Bedrock AgentCore: {}", id);
    }

    // ===== COMPATIBILITY METHODS FOR EXISTING AGENT RUNNERS =====

    /**
     * Compatibility method for existing agent runners that expect getConversationId()
     */
    public String getConversationId() {
        return sessionId;
    }

    // TODO: Add other compatibility methods as needed for ConversationIndexMemory interface

    /**
     * Factory for creating BedrockAgentCoreMemory instances
     */
    public static class Factory implements Memory.Factory<BedrockAgentCoreMemory> {

        private BedrockAgentCoreClient bedrockClient;
        private BedrockAgentCoreAdapter adapter;

        public void init(BedrockAgentCoreClient bedrockClient, BedrockAgentCoreAdapter adapter) {
            this.bedrockClient = bedrockClient;
            this.adapter = adapter;
        }

        @Override
        public void create(Map<String, Object> params, ActionListener<BedrockAgentCoreMemory> listener) {
            String memoryArn = (String) params.get("memory_arn");
            String sessionId = (String) params.get("session_id");

            if (memoryArn == null || sessionId == null) {
                listener.onFailure(new IllegalArgumentException("memory_arn and session_id are required"));
                return;
            }

            BedrockAgentCoreMemory memory = new BedrockAgentCoreMemory(memoryArn, sessionId, bedrockClient, adapter);
            listener.onResponse(memory);
        }

        // Compatibility method for existing agent runners
        public void create(String name, String memoryArn, String appType, ActionListener<BedrockAgentCoreMemory> listener) {
            if (memoryArn == null) {
                listener
                    .onFailure(
                        new IllegalArgumentException("memory_arn is required - customer must provide pre-existing Bedrock AgentCore memory")
                    );
                return;
            }

            Map<String, Object> params = Map
                .of("memory_arn", memoryArn, "session_id", generateSessionId(), "name", name, "app_type", appType);
            create(params, listener);
        }

        private String generateSessionId() {
            return "bedrock-session-" + System.currentTimeMillis();
        }
    }
}
