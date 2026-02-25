/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.hooks;

import java.util.List;
import java.util.Map;

import org.opensearch.ml.common.contextmanager.ContextManagerContext;
import org.opensearch.ml.common.conversation.Interaction;

/**
 * Hook event triggered after memory retrieval in the agent lifecycle.
 * This event provides access to the retrieved chat history and context,
 * allowing context managers to modify the memory before it's used.
 */
public class PostMemoryEvent extends HookEvent {
    private final ContextManagerContext context;
    private final List<Interaction> retrievedHistory;

    /**
     * Constructor for PostMemoryEvent
     * @param context The context manager context containing all context components
     * @param retrievedHistory The chat history retrieved from memory
     * @param invocationState The current state of the agent invocation
     */
    public PostMemoryEvent(ContextManagerContext context, List<Interaction> retrievedHistory, Map<String, Object> invocationState) {
        super(invocationState);
        this.context = context;
        this.retrievedHistory = retrievedHistory;
    }

    /**
     * Get the context manager context
     * @return ContextManagerContext containing all context components
     */
    public ContextManagerContext getContext() {
        return context;
    }

    /**
     * Get the retrieved chat history
     * @return List of interactions retrieved from memory
     */
    public List<Interaction> getRetrievedHistory() {
        return retrievedHistory;
    }
}
