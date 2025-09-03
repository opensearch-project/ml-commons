/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.memory.bedrockagentcore;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.opensearch.core.action.ActionListener;
import org.opensearch.ml.common.conversation.Interaction;
import org.opensearch.ml.common.spi.memory.Memory;
import org.opensearch.transport.client.Client;

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
    private final String agentId;         // Agent ID to use as actorId
    private final BedrockAgentCoreClientWrapper bedrockClient;
    private final BedrockAgentCoreAdapter adapter;

    public BedrockAgentCoreMemory(
        String memoryArn,
        String sessionId,
        String agentId,
        BedrockAgentCoreClientWrapper bedrockClient,
        BedrockAgentCoreAdapter adapter
    ) {
        this.memoryArn = memoryArn;
        this.sessionId = sessionId;
        this.agentId = agentId;
        this.bedrockClient = bedrockClient;
        this.adapter = adapter;

        // Enhanced logging for session tracking
        log
            .info(
                "🔧 BEDROCK MEMORY CREATED: memoryArn={}, sessionId={}, agentId={}, memoryId={}",
                memoryArn,
                sessionId,
                agentId,
                getMemoryId()
            );
        log.info("MEMORY INSTANCE: {}", this.toString());
    }

    /**
     * Extract memory ID from memory ARN for Bedrock AgentCore API calls
     * ARN format: arn:aws:bedrock:region:account:agent-memory/memory-id
     */
    public String getMemoryId() {
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
        log
            .info(
                "💾 SAVE REQUEST START: requestedSessionId={}, recordSessionId={}, memorySessionId={}, memoryId={}",
                id,
                record != null ? record.getSessionId() : "null",
                this.sessionId,
                getMemoryId()
            );
        log
            .info(
                "💾 SAVE CONTENT: type={}, content='{}'",
                record != null ? record.getType() : "null",
                record != null ? record.getContent() : "null"
            );

        if (record == null) {
            log.error("SAVE FAILED: Memory record is null for session: {}", id);
            listener.onFailure(new IllegalArgumentException("Memory record cannot be null"));
            return;
        }

        // Use BedrockAgentCore createEvent API
        bedrockClient.createEvent(getMemoryId(), record, agentId, ActionListener.wrap(eventId -> {
            log.info("SAVE SUCCESS: eventId={}, sessionId={}, memoryId={}", eventId, id, getMemoryId());
            listener.onResponse(eventId);
        }, error -> {
            log.error("SAVE FAILED: sessionId={}, memoryId={}, error={}", id, getMemoryId(), error.getMessage());
            listener.onFailure(error);
        }));
    }

    @Override
    public void getMessages(String id, ActionListener<BedrockAgentCoreMemoryRecord> listener) {
        log.info("RETRIEVE REQUEST START: requestedSessionId={}, memorySessionId={}, memoryId={}", id, this.sessionId, getMemoryId());
        log.info("RETRIEVE STRATEGY: Using sessionId={} and actorId={} for AWS ListEvents", id, agentId);

        // Use BedrockAgentCore listMemoryRecords API with sessionId and agentId as actorId
        bedrockClient.listMemoryRecords(getMemoryId(), id, agentId, ActionListener.wrap(records -> {
            log.info("RETRIEVE RESPONSE: Found {} records for sessionId={}, memoryId={}", records.size(), id, getMemoryId());

            if (!records.isEmpty()) {
                log
                    .info(
                        "📋 FIRST RECORD DETAILS: type={}, content='{}', sessionId={}",
                        records.get(0).getType(),
                        records.get(0).getContent(),
                        records.get(0).getSessionId()
                    );
            } else {
                log
                    .warn(
                        "⚠️ NO RECORDS FOUND: sessionId={}, memoryId={} - This may indicate session mismatch or no events saved yet",
                        id,
                        getMemoryId()
                    );
            }

            // For now, return the first record or null if empty
            BedrockAgentCoreMemoryRecord result = records.isEmpty() ? null : records.get(0);
            listener.onResponse(result);
        }, error -> {
            log.error("RETRIEVE FAILED: sessionId={}, memoryId={}, error={}", id, getMemoryId(), error.getMessage());
            listener.onFailure(error);
        }));
    }

    /**
     * Get conversation history as a list of records
     */
    public void getConversationHistory(String sessionId, ActionListener<List<BedrockAgentCoreMemoryRecord>> listener) {
        log.info("GET CONVERSATION HISTORY: sessionId={}, actorId={}", sessionId, agentId);

        bedrockClient.listMemoryRecords(getMemoryId(), sessionId, agentId, ActionListener.wrap(records -> {
            log.info("Successfully retrieved {} memory records from Bedrock AgentCore", records.size());
            // Filter records by session ID if needed, or return all records
            // For now, return all records as conversation history
            listener.onResponse(records);
        }, error -> {
            log.error("Failed to retrieve conversation history from Bedrock AgentCore for session: {}", sessionId, error);
            listener.onFailure(error);
        }));
    }

    /**
     * Get messages compatible with MLChatAgentRunner - converts BedrockAgentCoreMemoryRecord to List<Interaction>
     */
    public void getMessages(ActionListener<List<Interaction>> listener) {
        log
            .info(
                "🔍 RETRIEVE FOR COMPATIBILITY START: memorySessionId={}, agentId={}, memoryId={}",
                this.sessionId,
                this.agentId,
                getMemoryId()
            );
        log.info("COMPATIBILITY STRATEGY: Using sessionId={}, actorId={} for AWS ListEvents", this.sessionId, this.agentId);

        bedrockClient.listMemoryRecords(getMemoryId(), this.sessionId, this.agentId, ActionListener.wrap(records -> {
            log
                .info(
                    "📥 COMPATIBILITY RESPONSE: Found {} records for sessionId={}, memoryId={}",
                    records.size(),
                    this.sessionId,
                    getMemoryId()
                );

            // Convert BedrockAgentCoreMemoryRecord to List<Interaction>
            List<Interaction> interactions = new ArrayList<>();
            for (BedrockAgentCoreMemoryRecord record : records) {
                log
                    .info(
                        "📋 CONVERTING RECORD: type={}, content='{}', sessionId={}",
                        record.getType(),
                        record.getContent(),
                        record.getSessionId()
                    );

                // Create Interaction from BedrockAgentCoreMemoryRecord
                // Use actual content and response from the record
                Interaction interaction = Interaction
                    .builder()
                    .conversationId(getConversationId())
                    .input(record.getContent() != null ? record.getContent() : "")
                    .response(record.getResponse() != null ? record.getResponse() : "")
                    .build();
                interactions.add(interaction);
            }

            log.info("COMPATIBILITY SUCCESS: Converted {} records to {} interactions", records.size(), interactions.size());
            listener.onResponse(interactions);
        }, error -> {
            log.error("COMPATIBILITY FAILED: sessionId={}, memoryId={}, error={}", this.sessionId, getMemoryId(), error.getMessage());
            listener.onFailure(error);
        }));
    }

    @Override
    public void clear() {
        log.info("Clearing memory records from Bedrock AgentCore");
        // Note: Bedrock AgentCore doesn't have a clear-all API, so this is a no-op
        // In production, this might need to list and delete individual records
    }

    @Override
    public void remove(String id) {
        log.info("Removing memory record from Bedrock AgentCore: {}", id);

        // Use BedrockAgentCore deleteMemoryRecord API
        bedrockClient
            .deleteMemoryRecord(
                getMemoryId(),
                id,
                ActionListener
                    .wrap(
                        result -> log.info("Successfully removed memory record from Bedrock AgentCore: {}", id),
                        error -> log.error("Failed to remove memory record from Bedrock AgentCore: {}", id, error)
                    )
            );
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
     * Factory for creating BedrockAgentCoreMemory instances.
     * 
     * Uses S3-style pattern: creates new client per request for multi-tenant efficiency.
     * Each client is auto-closeable and should be used with try-with-resources.
     */
    public static class Factory implements Memory.Factory<BedrockAgentCoreMemory> {

        private BedrockAgentCoreAdapter adapter;

        public void init(BedrockAgentCoreClientWrapper bedrockClient, BedrockAgentCoreAdapter adapter) {
            // Legacy method - now always creates new clients per request
            this.adapter = adapter;
        }

        // Plugin-compatible init method for factory registration
        public void init(
            Client client,
            org.opensearch.ml.engine.indices.MLIndicesHandler mlIndicesHandler,
            org.opensearch.ml.engine.memory.MLMemoryManager memoryManager
        ) {
            // Always create new clients per request (S3-style pattern)
            this.adapter = new BedrockAgentCoreAdapter();
        }

        @Override
        public void create(Map<String, Object> params, ActionListener<BedrockAgentCoreMemory> listener) {
            String memoryArn = (String) params.get("memory_arn");
            String sessionId = (String) params.get("session_id");
            String agentId = (String) params.get("agent_id");

            if (memoryArn == null || sessionId == null) {
                listener.onFailure(new IllegalArgumentException("memory_arn and session_id are required"));
                return;
            }

            // Use sessionId as agentId if agentId is not provided (backward compatibility)
            if (agentId == null) {
                throw new IllegalArgumentException(
                    "Agent ID is mandatory but not found in memory parameters. This indicates a configuration issue - please check agent setup."
                );
            }

            // Always create new client per request (S3-style pattern for multi-tenant efficiency)
            String region = (String) params.get("region");
            @SuppressWarnings("unchecked")
            Map<String, String> credentials = (Map<String, String>) params.get("credentials");

            if (region == null || credentials == null) {
                listener.onFailure(new IllegalArgumentException("region and credentials are required"));
                return;
            }

            // Check if credentials look expired (basic validation)
            String sessionToken = credentials.get("session_token");
            if (sessionToken != null && sessionToken.length() > 0) {
                log.warn("Using temporary AWS credentials - these may expire during long conversations");
            }

            BedrockAgentCoreClientWrapper clientWrapper;
            try {
                clientWrapper = new BedrockAgentCoreClientWrapper(region, credentials);
                log.info("Created new BedrockAgentCore client for multi-tenant request (S3-style pattern)");
            } catch (Exception e) {
                log.error("Failed to create BedrockAgentCore client - credentials may be expired", e);
                listener.onFailure(new IllegalArgumentException("Failed to create BedrockAgentCore client: " + e.getMessage(), e));
                return;
            }

            BedrockAgentCoreMemory memory = new BedrockAgentCoreMemory(memoryArn, sessionId, agentId, clientWrapper, adapter);
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

            String sessionId = generateSessionId();
            Map<String, Object> params = Map
                .of("memory_arn", memoryArn, "session_id", sessionId, "agent_id", sessionId, "name", name, "app_type", appType);
            create(params, listener);
        }

        private String generateSessionId() {
            return "bedrock-session-" + System.currentTimeMillis();
        }
    }
}
