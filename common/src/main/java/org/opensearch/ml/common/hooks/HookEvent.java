/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.hooks;

import java.util.Map;

/**
 * Base class for all hook events in the ML agent lifecycle.
 * Hook events are strongly-typed events that carry context information
 * for different stages of agent execution.
 */
public abstract class HookEvent {
    private final Map<String, Object> invocationState;

    /**
     * Constructor for HookEvent
     * @param invocationState The current state of the agent invocation
     */
    protected HookEvent(Map<String, Object> invocationState) {
        this.invocationState = invocationState;
    }

    /**
     * Get the invocation state
     * @return Map containing the current invocation state
     */
    public Map<String, Object> getInvocationState() {
        return invocationState;
    }
}
