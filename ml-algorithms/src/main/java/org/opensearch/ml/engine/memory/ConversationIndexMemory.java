/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.memory;

import static org.opensearch.ml.common.CommonValue.ML_MEMORY_MESSAGE_INDEX;
import static org.opensearch.ml.common.CommonValue.ML_MEMORY_META_INDEX;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.opensearch.action.update.UpdateResponse;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.common.Strings;
import org.opensearch.ml.common.MLMemoryType;
import org.opensearch.ml.common.conversation.Interaction;
import org.opensearch.ml.common.input.execute.agent.ContentBlock;
import org.opensearch.ml.common.input.execute.agent.ContentType;
import org.opensearch.ml.common.input.execute.agent.Message;
import org.opensearch.ml.common.memory.Memory;
import org.opensearch.ml.engine.algorithms.agent.AgentUtils;
import org.opensearch.ml.engine.indices.MLIndicesHandler;
import org.opensearch.ml.memory.action.conversation.CreateConversationResponse;
import org.opensearch.ml.memory.action.conversation.CreateInteractionResponse;
import org.opensearch.transport.client.Client;

import lombok.Getter;
import lombok.extern.log4j.Log4j2;

@Log4j2
@Getter
public class ConversationIndexMemory implements Memory<org.opensearch.ml.common.memory.Message, CreateInteractionResponse, UpdateResponse> {
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
    public void save(org.opensearch.ml.common.memory.Message message, String parentId, Integer traceNum, String action) {
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
        org.opensearch.ml.common.memory.Message message,
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
    public void getStructuredMessages(ActionListener<List<Message>> listener) {
        // Retrieve text-based interactions and convert to structured Message format
        ActionListener<List<org.opensearch.ml.common.memory.Message>> getMessagesListener = ActionListener.wrap(interactions -> {
            List<Message> messages = new ArrayList<>();

            for (org.opensearch.ml.common.memory.Message interaction : interactions) {
                if (interaction instanceof Interaction) {
                    Interaction inter = (Interaction) interaction;

                    // Create user message if input exists
                    if (inter.getInput() != null && !inter.getInput().trim().isEmpty()) {
                        ContentBlock userContentBlock = new ContentBlock();
                        userContentBlock.setType(ContentType.TEXT);
                        userContentBlock.setText(inter.getInput());

                        Message userMessage = new Message();
                        userMessage.setRole("user");
                        userMessage.setContent(List.of(userContentBlock));
                        messages.add(userMessage);
                    }

                    // Create assistant message if response exists
                    if (inter.getResponse() != null && !inter.getResponse().trim().isEmpty()) {
                        ContentBlock assistantContentBlock = new ContentBlock();
                        assistantContentBlock.setType(ContentType.TEXT);
                        assistantContentBlock.setText(inter.getResponse());

                        Message assistantMessage = new Message();
                        assistantMessage.setRole("assistant");
                        assistantMessage.setContent(List.of(assistantContentBlock));
                        messages.add(assistantMessage);
                    }
                }
            }

            listener.onResponse(messages);
        }, e -> {
            log.error("Failed to retrieve messages from conversation index", e);
            listener.onFailure(e);
        });

        getMessages(Memory.MAX_MESSAGES_TO_RETRIEVE, getMessagesListener);
    }

    @Override
    public void saveStructuredMessages(List<Message> messages, ActionListener<Void> listener) {
        if (messages == null || messages.isEmpty()) {
            listener.onResponse(null);
            return;
        }

        // Extract app type from agent context if available
        String appType = null; // Default to null for now
        extractAndSaveMessagePairs(messages, appType, listener);
    }

    /**
     * Extract text-only content from messages and save as conversation pairs.
     */
    private void extractAndSaveMessagePairs(List<Message> messages, String appType, ActionListener<Void> listener) {
        List<ConversationIndexMessage> messagePairs = AgentUtils.extractMessagePairs(messages, conversationId, appType);

        // Detect multimodal content and warn
        boolean hasMultimodalContent = false;
        for (Message message : messages) {
            if (message != null && message.getContent() != null) {
                for (ContentBlock block : message.getContent()) {
                    if (block.getType() != ContentType.TEXT) {
                        hasMultimodalContent = true;
                        break;
                    }
                }
            }
            if (hasMultimodalContent)
                break;
        }

        if (hasMultimodalContent) {
            log
                .warn(
                    "Multimodal content detected in messages for ConversationIndexMemory (conversation: {}). "
                        + "Only text content will be stored. Images, documents, and other non-text content will be ignored. "
                        + "Consider using AgenticConversationMemory for full multimodal support.",
                    conversationId
                );
        }

        // If no pairs to save, complete immediately
        if (messagePairs.isEmpty()) {
            listener.onResponse(null);
            return;
        }

        // Save all pairs sequentially, tracking errors
        saveMessagePairsSequentially(messagePairs, 0, new AtomicBoolean(false), listener);
    }

    /**
     * Helper method to save message pairs sequentially.
     * Continues on individual failures but reports error to the listener if any save failed.
     */
    private void saveMessagePairsSequentially(
        List<ConversationIndexMessage> messagePairs,
        int index,
        AtomicBoolean hasError,
        ActionListener<Void> finalListener
    ) {
        if (index >= messagePairs.size()) {
            if (hasError.get()) {
                finalListener.onFailure(new RuntimeException("One or more message pairs failed to save to conversation index"));
            } else {
                finalListener.onResponse(null);
            }
            return;
        }

        ConversationIndexMessage msg = messagePairs.get(index);

        ActionListener<CreateInteractionResponse> saveListener = ActionListener.wrap(interaction -> {
            log
                .info(
                    "Stored message pair {} of {} in conversation index with interaction ID: {}",
                    index + 1,
                    messagePairs.size(),
                    interaction.getId()
                );
            saveMessagePairsSequentially(messagePairs, index + 1, hasError, finalListener);
        }, ex -> {
            log.error("Failed to store message pair {} of {} in conversation index", index + 1, messagePairs.size(), ex);
            hasError.set(true);
            saveMessagePairsSequentially(messagePairs, index + 1, hasError, finalListener);
        });

        save(msg, null, null, null, saveListener);
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
