/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.contextmanager;

import java.util.List;

import org.opensearch.ml.common.input.execute.agent.Message;

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

    /**
     * Find a safe cut point in structured messages to avoid breaking tool-call/tool-result pairs.
     *
     * In structured messages, tool pairs look like:
     *   assistant message with toolCalls [{id: "tooluse_xxx"}]  →  tool message with toolCallId "tooluse_xxx"
     * Cutting between them would cause the LLM to reject the payload.
     *
     * Starting from targetPoint, this scans forward until the cut position:
     *   1. Is NOT a tool result message (role="tool" with toolCallId)
     *   2. Is NOT immediately after an assistant message with toolCalls (which needs its tool result)
     *
     * @param messages List of structured Message objects
     * @param targetPoint The desired cut point
     * @return Safe cut point that keeps tool pairs together
     */
    public static int findSafeCutPointForStructuredMessages(List<Message> messages, int targetPoint) {
        if (targetPoint <= 0) {
            return 0;
        }
        if (targetPoint >= messages.size()) {
            return messages.size();
        }

        int safePoint = targetPoint;

        while (safePoint < messages.size()) {
            try {
                Message message = messages.get(safePoint);

                // If this message is a tool result, we can't start here — the preceding
                // assistant tool-call message would be orphaned in the summarized portion.
                boolean isToolResult = "tool".equals(message.getRole()) && message.getToolCallId() != null;

                // If the previous message is an assistant with tool calls, cutting here
                // would separate the tool-call from its result.
                boolean prevHasToolCalls = false;
                if (safePoint > 0) {
                    Message prev = messages.get(safePoint - 1);
                    prevHasToolCalls = "assistant".equals(prev.getRole()) && prev.getToolCalls() != null && !prev.getToolCalls().isEmpty();
                }

                if (isToolResult || prevHasToolCalls) {
                    safePoint++;
                } else {
                    break;
                }
            } catch (Exception e) {
                log.warn("Error checking structured message at index {}: {}", safePoint, e.getMessage());
                safePoint++;
            }
        }

        log.debug("Safe cut point for structured messages: target={}, safe={}", targetPoint, safePoint);
        return safePoint;
    }
}
