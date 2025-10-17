/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.hooks;

import java.util.List;
import java.util.Map;

public class PostToolEvent extends HookEvent {
    List<Map<String, Object>> toolResults;
    private final Exception error;

    public PostToolEvent(List<Map<String, Object>> toolResults, Exception error, Map<String, Object> invocationState) {
        super(invocationState);
        this.toolResults = toolResults;
        this.error = error;
    }

    public List<Map<String, Object>> getToolResults() {
        return toolResults;
    }

    public Exception getError() {
        return error;
    }
}
