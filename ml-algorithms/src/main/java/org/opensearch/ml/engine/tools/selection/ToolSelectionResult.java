/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.tools.selection;

import java.util.List;

import lombok.Getter;

/**
 * Whether tool search applies to one agent execution and, if so, which tools stay visible.
 *
 * {@code applied == false} is the caller's instruction to expose the full tool set, exactly as
 * without this feature; {@link #getSkipReason()} says why, for the log line.
 */
@Getter
public class ToolSelectionResult {

    private final boolean applied;
    /** Pinned tools that exist on this agent, in configured order. Always visible to the model. */
    private final List<String> pinnedToolNames;
    private final int poolSize;
    /** Tools hidden behind search_tools: the pool minus the pinned tools. */
    private final int searchableCount;
    private final String skipReason;

    private ToolSelectionResult(boolean applied, List<String> pinnedToolNames, int poolSize, int searchableCount, String skipReason) {
        this.applied = applied;
        this.pinnedToolNames = pinnedToolNames;
        this.poolSize = poolSize;
        this.searchableCount = searchableCount;
        this.skipReason = skipReason;
    }

    /** Search does not apply: the caller exposes every tool. */
    public static ToolSelectionResult notApplied(int poolSize, String reason) {
        return new ToolSelectionResult(false, List.of(), poolSize, 0, reason);
    }

    public static ToolSelectionResult applied(List<String> pinnedToolNames, int poolSize) {
        return new ToolSelectionResult(true, List.copyOf(pinnedToolNames), poolSize, poolSize - pinnedToolNames.size(), null);
    }
}
