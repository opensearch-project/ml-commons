/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.memory;

import java.util.List;
import java.util.Map;

import org.opensearch.core.action.ActionListener;

/**
 * A general memory interface.
 * @param <T> Message type
 * @param <R> Save response type
 * @param <S> Update response type
 */
public interface Memory<T extends Message, R, S> {

    /**
     * Maximum number of messages to retrieve from storage.
     */
    int MAX_MESSAGES_TO_RETRIEVE = 10000;

    /**
     * Get memory type.
     * @return memory type
     */
    String getType();

    /**
     * Get memory ID.
     * @return memory ID
     */
    String getId();

    default void save(Message message, String parentId, Integer traceNum, String action) {}

    default void save(Message message, String parentId, Integer traceNum, String action, ActionListener<R> listener) {}

    default void update(String messageId, Map<String, Object> updateContent, ActionListener<S> updateListener) {}

    default void getMessages(int size, ActionListener<List<T>> listener) {}

    /**
     * Get structured messages from memory.
     *
     * @param listener Action listener that receives the messages
     */
    default void getStructuredMessages(ActionListener<List<org.opensearch.ml.common.input.execute.agent.Message>> listener) {
        listener.onFailure(new UnsupportedOperationException("getStructuredMessages is not supported by " + getType()));
    }

    /**
     * Save structured messages to memory.
     *
     * @param messages List of AgentInput messages to save (may be null or empty)
     * @param listener Action listener for save completion
     */
    default void saveStructuredMessages(
        List<org.opensearch.ml.common.input.execute.agent.Message> messages,
        ActionListener<Void> listener
    ) {
        listener.onFailure(new UnsupportedOperationException("saveStructuredMessages is not supported by " + getType()));
    }

    /**
     * Clear all memory.
     */
    void clear();

    void deleteInteractionAndTrace(String regenerateInteractionId, ActionListener<Boolean> wrap);

    interface Factory<M extends Memory> {
        /**
         * Create an instance of this Memory.
         *
         * @param params Parameters for the memory
         * @param listener Action listener for the memory creation action
         */
        void create(Map<String, Object> params, ActionListener<M> listener);
    }
}
