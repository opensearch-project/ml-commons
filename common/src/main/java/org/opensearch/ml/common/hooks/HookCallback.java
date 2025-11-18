/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.hooks;

/**
 * Functional interface for handling specific hook events.
 * Implementations of this interface define the behavior to execute
 * when a particular hook event is triggered.
 *
 * @param <T> The type of HookEvent this callback handles
 */
@FunctionalInterface
public interface HookCallback<T extends HookEvent> {

    /**
     * Handle the hook event
     * @param event The hook event to handle
     */
    void handle(T event);
}
