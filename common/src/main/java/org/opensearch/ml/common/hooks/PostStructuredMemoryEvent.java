/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.hooks;

import java.util.List;
import java.util.Map;

import org.opensearch.ml.common.contextmanager.ContextManagerContext;
import org.opensearch.ml.common.input.execute.agent.Message;

/**
 * Hook event triggered after structured memory retrieval in the agent lifecycle.
 * This event is the structured-message counterpart of {@link PostMemoryEvent},
 * fired for agents using the unified interface (structured messages).
 * It routes through the same "POST_MEMORY" hook name so existing context manager
 * configurations work without changes.
 */
public class PostStructuredMemoryEvent extends HookEvent {
    private final ContextManagerContext context;
    private final List<Message> retrievedStructuredHistory;

    /**
     * Constructor for PostStructuredMemoryEvent
     * @param context The context manager context containing all context components
     * @param retrievedStructuredHistory The structured chat history retrieved from memory
     * @param invocationState The current state of the agent invocation
     */
    public PostStructuredMemoryEvent(
        ContextManagerContext context,
        List<Message> retrievedStructuredHistory,
        Map<String, Object> invocationState
    ) {
        super(invocationState);
        this.context = context;
        this.retrievedStructuredHistory = retrievedStructuredHistory;
    }

    /**
     * Get the context manager context
     * @return ContextManagerContext containing all context components
     */
    public ContextManagerContext getContext() {
        return context;
    }

    /**
     * Get the retrieved structured chat history
     * @return List of Messages retrieved from memory
     */
    public List<Message> getRetrievedStructuredHistory() {
        return retrievedStructuredHistory;
    }
}
