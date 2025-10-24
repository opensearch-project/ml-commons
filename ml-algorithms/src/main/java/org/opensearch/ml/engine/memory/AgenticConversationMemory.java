/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.memory;

import static org.opensearch.ml.engine.memory.ConversationIndexMemory.APP_TYPE;
import static org.opensearch.ml.engine.memory.ConversationIndexMemory.MEMORY_ID;
import static org.opensearch.ml.engine.memory.ConversationIndexMemory.MEMORY_NAME;

import java.util.List;
import java.util.Map;

import org.opensearch.action.update.UpdateResponse;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.common.Strings;
import org.opensearch.ml.common.memory.Memory;
import org.opensearch.ml.common.memory.Message;
import org.opensearch.ml.common.transport.session.MLCreateSessionAction;
import org.opensearch.ml.common.transport.session.MLCreateSessionInput;
import org.opensearch.ml.common.transport.session.MLCreateSessionRequest;
import org.opensearch.ml.memory.action.conversation.CreateInteractionResponse;
import org.opensearch.transport.client.Client;

import lombok.Getter;
import lombok.extern.log4j.Log4j2;

/**
 * Agentic Memory implementation that stores conversations in Memory Container
 * Uses TransportCreateSessionAction and TransportAddMemoriesAction for all operations
 */
@Log4j2
@Getter
public class AgenticConversationMemory implements Memory<Message, CreateInteractionResponse, UpdateResponse> {

    public static final String TYPE = "agentic_memory";

    public AgenticConversationMemory(Client client, String memoryId, String memoryContainerId) {}

    @Override
    public String getType() {
        return "";
    }

    @Override
    public String getId() {
        return "";
    }

    @Override
    public void save(Message message, String parentId, Integer traceNum, String action) {
        Memory.super.save(message, parentId, traceNum, action);
    }

    @Override
    public void save(
        Message message,
        String parentId,
        Integer traceNum,
        String action,
        ActionListener<CreateInteractionResponse> listener
    ) {
        Memory.super.save(message, parentId, traceNum, action, listener);
    }

    @Override
    public void update(String messageId, Map<String, Object> updateContent, ActionListener<UpdateResponse> updateListener) {
        Memory.super.update(messageId, updateContent, updateListener);
    }

    @Override
    public void getMessages(int size, ActionListener<List<Message>> listener) {
        Memory.super.getMessages(size, listener);
    }

    @Override
    public void clear() {

    }

    @Override
    public void deleteInteractionAndTrace(String regenerateInteractionId, ActionListener<Boolean> wrap) {

    }

    /**
     * Factory for creating AgenticConversationMemory instances
     */
    public static class Factory implements Memory.Factory<AgenticConversationMemory> {
        private Client client;

        public void init(Client client) {
            this.client = client;
        }

        @Override
        public void create(Map<String, Object> map, ActionListener<AgenticConversationMemory> listener) {
            if (map == null || map.isEmpty()) {
                listener.onFailure(new IllegalArgumentException("Invalid input parameter for creating AgenticConversationMemory"));
                return;
            }

            String memoryId = (String) map.get(MEMORY_ID);
            String name = (String) map.get(MEMORY_NAME);
            String appType = (String) map.get(APP_TYPE);
            String memoryContainerId = (String) map.get("memory_container_id");

            create(name, memoryId, appType, memoryContainerId, listener);
        }

        public void create(
            String name,
            String memoryId,
            String appType,
            String memoryContainerId,
            ActionListener<AgenticConversationMemory> listener
        ) {
            // Memory container ID is required for AgenticConversationMemory
            if (Strings.isNullOrEmpty(memoryContainerId)) {
                listener
                    .onFailure(
                        new IllegalArgumentException(
                            "Memory container ID is required for AgenticConversationMemory. "
                                + "Please provide 'memory_container_id' in the agent configuration."
                        )
                    );
                return;
            }

            if (Strings.isEmpty(memoryId)) {
                // Create new session using TransportCreateSessionAction
                createSessionInMemoryContainer(name, memoryContainerId, ActionListener.wrap(sessionId -> {
                    create(sessionId, memoryContainerId, listener);
                    log.debug("Created session in memory container, session id: {}", sessionId);
                }, e -> {
                    log.error("Failed to create session in memory container", e);
                    listener.onFailure(e);
                }));
            } else {
                // Use existing session/memory ID
                create(memoryId, memoryContainerId, listener);
            }
        }

        /**
         * Create a new session in the memory container using the new session API
         */
        private void createSessionInMemoryContainer(String summary, String memoryContainerId, ActionListener<String> listener) {
            MLCreateSessionInput input = MLCreateSessionInput.builder().memoryContainerId(memoryContainerId).summary(summary).build();

            MLCreateSessionRequest request = MLCreateSessionRequest.builder().mlCreateSessionInput(input).build();

            client
                .execute(
                    MLCreateSessionAction.INSTANCE,
                    request,
                    ActionListener.wrap(response -> { listener.onResponse(response.getSessionId()); }, e -> {
                        log.error("Failed to create session via TransportCreateSessionAction", e);
                        listener.onFailure(e);
                    })
                );
        }

        public void create(String memoryId, String memoryContainerId, ActionListener<AgenticConversationMemory> listener) {
            listener.onResponse(new AgenticConversationMemory(client, memoryId, memoryContainerId));
        }
    }
}
