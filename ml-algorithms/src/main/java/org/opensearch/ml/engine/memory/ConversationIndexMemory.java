/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.memory;

import static org.opensearch.ml.common.CommonValue.ML_MEMORY_MESSAGE_INDEX;
import static org.opensearch.ml.common.CommonValue.ML_MEMORY_META_INDEX;

import java.util.Map;

import org.opensearch.action.update.UpdateResponse;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.common.Strings;
import org.opensearch.ml.common.MLMemoryType;
import org.opensearch.ml.common.memory.Memory;
import org.opensearch.ml.common.memory.Message;
import org.opensearch.ml.engine.indices.MLIndicesHandler;
import org.opensearch.ml.memory.action.conversation.CreateConversationResponse;
import org.opensearch.ml.memory.action.conversation.CreateInteractionResponse;
import org.opensearch.transport.client.Client;

import lombok.Getter;
import lombok.extern.log4j.Log4j2;

@Log4j2
@Getter
public class ConversationIndexMemory implements Memory<Message, CreateInteractionResponse, UpdateResponse> {
    public static final String TYPE = MLMemoryType.CONVERSATION_INDEX.name();
    public static final String CONVERSATION_ID = "conversation_id";
    public static final String FINAL_ANSWER = "final_answer";
    public static final String CREATED_TIME = "created_time";
    public static final String MEMORY_NAME = "memory_name";
    public static final String MEMORY_ID = "memory_id";
    public static final String APP_TYPE = "app_type";
    public static int LAST_N_INTERACTIONS = 10;
    protected String memoryMetaIndexName;
    protected String memoryMessageIndexName;
    protected String conversationId;
    protected boolean retrieveFinalAnswer = true;
    protected final Client client;
    private final MLIndicesHandler mlIndicesHandler;
    private MLMemoryManager memoryManager;

    public ConversationIndexMemory(
        Client client,
        MLIndicesHandler mlIndicesHandler,
        String memoryMetaIndexName,
        String memoryMessageIndexName,
        String conversationId,
        MLMemoryManager memoryManager
    ) {
        this.client = client;
        this.mlIndicesHandler = mlIndicesHandler;
        this.memoryMetaIndexName = memoryMetaIndexName;
        this.memoryMessageIndexName = memoryMessageIndexName;
        this.conversationId = conversationId;
        this.memoryManager = memoryManager;
    }

    @Override
    public String getType() {
        return TYPE;
    }

    @Override
    public String getId() {
        return this.conversationId;
    }

    @Override
    public void save(Message message, String parentId, Integer traceNum, String action) {
        this.save(message, parentId, traceNum, action, ActionListener.<CreateInteractionResponse>wrap(r -> {
            log
                .info(
                    "saved message into memory {}, parent id: {}, trace number: {}, interaction id: {}",
                    conversationId,
                    parentId,
                    traceNum,
                    r.getId()
                );
        }, e -> { log.error("Failed to save interaction", e); }));
    }

    @Override
    public void save(
        Message message,
        String parentId,
        Integer traceNum,
        String action,
        ActionListener<CreateInteractionResponse> listener
    ) {
        ConversationIndexMessage msg = (ConversationIndexMessage) message;
        memoryManager
            .createInteraction(conversationId, msg.getQuestion(), null, msg.getResponse(), action, null, parentId, traceNum, listener);
    }

    @Override
    public void getMessages(int size, ActionListener listener) {
        memoryManager.getFinalInteractions(conversationId, size, listener);
    }

    @Override
    public void clear() {
        throw new RuntimeException("clear method is not supported in ConversationIndexMemory");
    }

    @Override
    public void update(String messageId, Map<String, Object> updateContent, ActionListener<UpdateResponse> updateListener) {
        getMemoryManager().updateInteraction(messageId, updateContent, updateListener);
    }

    @Override
    public void deleteInteractionAndTrace(String interactionId, ActionListener<Boolean> listener) {
        memoryManager.deleteInteractionAndTrace(interactionId, listener);
    }

    public static class Factory implements Memory.Factory<ConversationIndexMemory> {
        private Client client;
        private MLIndicesHandler mlIndicesHandler;
        private String memoryMetaIndexName = ML_MEMORY_META_INDEX;
        private String memoryMessageIndexName = ML_MEMORY_MESSAGE_INDEX;
        private MLMemoryManager memoryManager;

        public void init(Client client, MLIndicesHandler mlIndicesHandler, MLMemoryManager memoryManager) {
            this.client = client;
            this.mlIndicesHandler = mlIndicesHandler;
            this.memoryManager = memoryManager;
        }

        @Override
        public void create(Map<String, Object> map, ActionListener<ConversationIndexMemory> listener) {
            if (map == null || map.isEmpty()) {
                listener.onFailure(new IllegalArgumentException("Invalid input parameter for creating ConversationIndexMemory"));
                return;
            }

            String memoryId = (String) map.get(MEMORY_ID);
            String name = (String) map.get(MEMORY_NAME);
            String appType = (String) map.get(APP_TYPE);
            create(name, memoryId, appType, listener);
        }

        private void create(String name, String memoryId, String appType, ActionListener<ConversationIndexMemory> listener) {
            if (Strings.isEmpty(memoryId)) {
                memoryManager.createConversation(name, appType, ActionListener.<CreateConversationResponse>wrap(r -> {
                    create(r.getId(), listener);
                    log.debug("Created conversation on memory layer, conversation id: {}", r.getId());
                }, e -> {
                    log.error("Failed to save interaction", e);
                    listener.onFailure(e);
                }));
            } else {
                create(memoryId, listener);
            }
        }

        private void create(String memoryId, ActionListener<ConversationIndexMemory> listener) {
            listener
                .onResponse(
                    new ConversationIndexMemory(
                        client,
                        mlIndicesHandler,
                        memoryMetaIndexName,
                        memoryMessageIndexName,
                        memoryId,
                        memoryManager
                    )
                );
        }
    }
}
