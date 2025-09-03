/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.spi.memory;

import java.util.Map;

import org.opensearch.core.action.ActionListener;

/**
 * A general memory interface.
 * @param <T>
 */
public interface Memory<T extends Message> {

    /**
     * Get memory type.
     * @return
     */
    String getType();

    /**
     * Save message to id.
     * @param id memory id
     * @param message message to be saved
     */
    default void save(String id, T message) {}

    default <S> void save(String id, T message, ActionListener<S> listener) {}

    /**
     * Get messages of memory id.
     * @param id memory id
     * @return
     */
    default T[] getMessages(String id) {
        return null;
    }

    default void getMessages(String id, ActionListener<T> listener) {}

    /**
     * Clear all memory.
     */
    void clear();

    /**
     * Remove memory of specific id.
     * @param id memory id
     */
    void remove(String id);

    interface Factory<T extends Memory> {
        /**
         * Create an instance of this Memory.
         *
         * @param params Parameters for the memory
         * @param listener Action listen for the memory creation action
         */
        void create(Map<String, Object> params, ActionListener<T> listener);
    }
}
