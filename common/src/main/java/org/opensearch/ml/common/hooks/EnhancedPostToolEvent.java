/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.hooks;

import java.util.List;
import java.util.Map;

import org.opensearch.ml.common.contextmanager.ContextManagerContext;

/**
 * Enhanced version of PostToolEvent that includes context manager context.
 * This event is triggered after tool execution and provides access to both
 * tool results and the full context, allowing context managers to modify
 * tool outputs and other context components.
 */
public class EnhancedPostToolEvent extends PostToolEvent {
    private final ContextManagerContext context;

    /**
     * Constructor for EnhancedPostToolEvent
     * @param toolResults List of tool execution results
     * @param error Exception that occurred during tool execution, null if successful
     * @param context The context manager context containing all context components
     * @param invocationState The current state of the agent invocation
     */
    public EnhancedPostToolEvent(
        List<Map<String, Object>> toolResults,
        Exception error,
        ContextManagerContext context,
        Map<String, Object> invocationState
    ) {
        super(toolResults, error, invocationState);
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
