/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.hooks;

import java.util.List;
import java.util.Map;

import org.opensearch.ml.common.contextmanager.ContextManagerContext;

/**
 * Hook event triggered after tool execution in the agent lifecycle.
 * This event provides access to tool results, any errors that occurred,
 * and the full context, allowing context managers to modify tool outputs
 * and other context components.
 */
public class PostToolEvent extends HookEvent {
    private final List<Map<String, Object>> toolResults;
    private final Exception error;
    private final ContextManagerContext context;

    /**
     * Constructor for PostToolEvent
     * @param toolResults List of tool execution results
     * @param error Exception that occurred during tool execution, null if successful
     * @param context The context manager context containing all context components
     * @param invocationState The current state of the agent invocation
     */
    public PostToolEvent(
        List<Map<String, Object>> toolResults,
        Exception error,
        ContextManagerContext context,
        Map<String, Object> invocationState
    ) {
        super(invocationState);
        this.toolResults = toolResults;
        this.error = error;
        this.context = context;
    }

    /**
     * Get the tool execution results
     * @return List of tool results
     */
    public List<Map<String, Object>> getToolResults() {
        return toolResults;
    }

    /**
     * Get the error that occurred during tool execution
     * @return Exception if an error occurred, null otherwise
     */
    public Exception getError() {
        return error;
    }

    /**
     * Get the context manager context
     * @return ContextManagerContext containing all context components
     */
    public ContextManagerContext getContext() {
        return context;
    }
}
