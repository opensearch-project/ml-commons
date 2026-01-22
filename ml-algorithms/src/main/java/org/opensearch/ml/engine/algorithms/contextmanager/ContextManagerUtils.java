/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.contextmanager;

import java.util.List;

import lombok.extern.log4j.Log4j2;

/**
 * Utility class for context manager operations
 */
@Log4j2
public class ContextManagerUtils {

    /**
     * Find a safe point in interactions to avoid breaking tool-use/tool-result pairs
     * @param interactions List of interaction messages
     * @param targetPoint The desired point to start/cut from
     * @param isStartPoint true if finding start point, false if finding cut point
     * @return Safe point that doesn't break tool pairs
     */
    public static int findSafePoint(List<String> interactions, int targetPoint, boolean isStartPoint) {
        if (isStartPoint && targetPoint <= 0) {
            return 0;
        }
        if (!isStartPoint && targetPoint >= interactions.size()) {
            return targetPoint;
        }
        if (isStartPoint && targetPoint >= interactions.size()) {
            return interactions.size();
        }

        int safePoint = targetPoint;

        while (safePoint < interactions.size()) {
            try {
                String message = interactions.get(safePoint);

                // Check for tool result patterns
                boolean hasToolResult = message.contains("toolResult") || message.contains("tool_call_id");

                // Check for tool use and if next message is tool result
                boolean hasToolUse = message.contains("toolUse");
                boolean nextHasToolResult = false;
                if (safePoint + 1 < interactions.size()) {
                    String nextMessage = interactions.get(safePoint + 1);
                    nextHasToolResult = nextMessage.contains("toolResult") || nextMessage.contains("tool_call_id");
                }

                // Oldest message cannot be a toolResult because it needs a toolUse preceding it
                // Oldest message can be a toolUse only if a toolResult immediately follows it
                if (hasToolResult || (hasToolUse && safePoint + 1 < interactions.size() && !nextHasToolResult)) {
                    safePoint++;
                } else {
                    break;
                }

            } catch (Exception e) {
                log.warn("Error checking message at index {}: {}", safePoint, e.getMessage());
                safePoint++;
            }
        }

        return safePoint;
    }
}
