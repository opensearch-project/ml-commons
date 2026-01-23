/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.hooks;

import java.util.Map;

import org.opensearch.ml.common.contextmanager.ContextManagerContext;

/**
 * Hook event triggered before LLM invocation in the agent lifecycle.
 * This event provides access to the context that will be sent to the LLM,
 * allowing context managers to modify it before the LLM call.
 */
public class PreLLMEvent extends HookEvent {
    private final ContextManagerContext context;

    /**
     * Constructor for PreLLMEvent
     * @param context The context manager context containing all context components
     * @param invocationState The current state of the agent invocation
     */
    public PreLLMEvent(ContextManagerContext context, Map<String, Object> invocationState) {
        super(invocationState);
        this.context = context;
    }

    /**
     * Get the context manager context
     * @return ContextManagerContext containing all context components
     */
    public ContextManagerContext getContext() {
        return context;
    }
}
