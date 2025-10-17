/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.hooks;

/**
 * Functional interface for hook callbacks.
 * Implementations will be called when their registered event type occurs.
 *
 * @param <T> The type of HookEvent this callback handles
 */
@FunctionalInterface
public interface HookCallback<T extends HookEvent> {
    /**
     * Called when an event occurs.
     *
     * @param event The event that occurred
     */
    void onEvent(T event);
}
