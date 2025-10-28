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
