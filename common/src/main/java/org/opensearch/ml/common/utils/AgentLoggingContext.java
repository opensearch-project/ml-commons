/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.utils;

import org.apache.logging.log4j.Logger;
import org.opensearch.common.util.concurrent.ThreadContext;

/**
 * Utility class for managing agent execution logging context.
 * Uses OpenSearch's ThreadContext to propagate run_id and thread_id
 * across async operations and transport actions.
 *
 * When context is not set (e.g., non-agent APIs), methods return empty
 * strings or null, ensuring no impact on existing logging behavior.
 */
public final class AgentLoggingContext {

    /** ThreadContext key for agent run identifier */
    public static final String RUN_ID_KEY = "agent_run_id";

    /** ThreadContext key for agent thread identifier */
    public static final String THREAD_ID_KEY = "agent_thread_id";

    private AgentLoggingContext() {
        // Utility class - no instantiation
    }

    /**
     * Sets the agent execution context in ThreadContext.
     * Call this at the entry point of agent execution.
     *
     * @param threadContext the OpenSearch ThreadContext
     * @param runId the run identifier (can be null)
     * @param threadId the thread identifier (can be null)
     */
    public static void setContext(ThreadContext threadContext, String runId, String threadId) {
        if (threadContext == null) {
            return;
        }

        if (runId != null && !runId.isEmpty()) {
            threadContext.putTransient(RUN_ID_KEY, runId);
        }
        if (threadId != null && !threadId.isEmpty()) {
            threadContext.putTransient(THREAD_ID_KEY, threadId);
        }
    }

    /**
     * Gets the current run_id from ThreadContext.
     *
     * @param threadContext the OpenSearch ThreadContext
     * @return the run_id or null if not set
     */
    public static String getRunId(ThreadContext threadContext) {
        if (threadContext == null) {
            return null;
        }
        return threadContext.getTransient(RUN_ID_KEY);
    }

    /**
     * Gets the current thread_id from ThreadContext.
     *
     * @param threadContext the OpenSearch ThreadContext
     * @return the thread_id or null if not set
     */
    public static String getThreadId(ThreadContext threadContext) {
        if (threadContext == null) {
            return null;
        }
        return threadContext.getTransient(THREAD_ID_KEY);
    }

    /**
     * Builds the log prefix with run_id and thread_id if present.
     * Returns empty string if neither is set, ensuring no impact on other APIs.
     *
     * @param threadContext the OpenSearch ThreadContext
     * @return the log prefix string (e.g., "[run_id=xxx][thread_id=yyy] ") or empty string
     */
    public static String getLogPrefix(ThreadContext threadContext) {
        if (threadContext == null) {
            return "";
        }

        String runId = threadContext.getTransient(RUN_ID_KEY);
        String threadId = threadContext.getTransient(THREAD_ID_KEY);

        if (runId == null && threadId == null) {
            return "";
        }

        StringBuilder prefix = new StringBuilder();
        if (runId != null) {
            prefix.append("[run_id=").append(runId).append("]");
        }
        if (threadId != null) {
            prefix.append("[thread_id=").append(threadId).append("]");
        }
        prefix.append(" ");
        return prefix.toString();
    }

    /**
     * Checks if logging context is set.
     *
     * @param threadContext the OpenSearch ThreadContext
     * @return true if either run_id or thread_id is set
     */
    public static boolean hasContext(ThreadContext threadContext) {
        if (threadContext == null) {
            return false;
        }
        return threadContext.getTransient(RUN_ID_KEY) != null || threadContext.getTransient(THREAD_ID_KEY) != null;
    }

    // ========== Convenience Logging Methods ==========
    // These methods prepend the context prefix to log messages automatically.
    // When no context is set, they behave identically to standard log calls.

    /**
     * Log at INFO level with agent context prefix.
     */
    public static void info(Logger log, ThreadContext ctx, String message, Object... args) {
        log.info(getLogPrefix(ctx) + message, args);
    }

    /**
     * Log at DEBUG level with agent context prefix.
     */
    public static void debug(Logger log, ThreadContext ctx, String message, Object... args) {
        log.debug(getLogPrefix(ctx) + message, args);
    }

    /**
     * Log at WARN level with agent context prefix.
     */
    public static void warn(Logger log, ThreadContext ctx, String message, Object... args) {
        log.warn(getLogPrefix(ctx) + message, args);
    }

    /**
     * Log at ERROR level with agent context prefix.
     */
    public static void error(Logger log, ThreadContext ctx, String message, Object... args) {
        log.error(getLogPrefix(ctx) + message, args);
    }

    /**
     * Log at ERROR level with agent context prefix and throwable.
     */
    public static void errorWithException(Logger log, ThreadContext ctx, String message, Throwable t) {
        log.error(getLogPrefix(ctx) + message, t);
    }
}
