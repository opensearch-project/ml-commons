/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.hooks;

import java.util.List;
import java.util.Map;

/**
 * Hook event triggered after tool execution in the agent lifecycle.
 * This event provides access to tool results and any errors that occurred.
 */
public class PostToolEvent extends HookEvent {
    private final List<Map<String, Object>> toolResults;
    private final Exception error;

    /**
     * Constructor for PostToolEvent
     * @param toolResults List of tool execution results
     * @param error Exception that occurred during tool execution, null if successful
     * @param invocationState The current state of the agent invocation
     */
    public PostToolEvent(List<Map<String, Object>> toolResults, Exception error, Map<String, Object> invocationState) {
        super(invocationState);
        this.toolResults = toolResults;
        this.error = error;
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
}
