/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.utils;

import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.core.action.ActionListener;

/**
 * ActionListener wrapper that preserves and restores agent logging context
 * across async operations.
 *
 * Use this wrapper when passing ActionListeners to async operations to ensure
 * that run_id and thread_id are available in callback log statements.
 *
 * Example usage:
 * <pre>
 * client.execute(action, request, ContextAwareActionListener.wrap(
 *     ActionListener.wrap(
 *         response -> { ... },
 *         error -> { ... }
 *     ),
 *     threadContext
 * ));
 * </pre>
 */
public class ContextAwareActionListener<T> implements ActionListener<T> {

    private final ActionListener<T> delegate;
    private final ThreadContext threadContext;
    private final String savedRunId;
    private final String savedThreadId;

    /**
     * Creates a new ContextAwareActionListener.
     *
     * @param delegate the underlying ActionListener to delegate to
     * @param threadContext the OpenSearch ThreadContext
     */
    public ContextAwareActionListener(ActionListener<T> delegate, ThreadContext threadContext) {
        this.delegate = delegate;
        this.threadContext = threadContext;
        // Capture context at creation time (before async operation)
        this.savedRunId = AgentLoggingContext.getRunId(threadContext);
        this.savedThreadId = AgentLoggingContext.getThreadId(threadContext);
    }

    @Override
    public void onResponse(T response) {
        restoreContext();
        delegate.onResponse(response);
    }

    @Override
    public void onFailure(Exception e) {
        restoreContext();
        delegate.onFailure(e);
    }

    /**
     * Restores the saved context to ThreadContext.
     * Called before delegating to ensure context is available for logging.
     */
    private void restoreContext() {
        if (threadContext != null && (savedRunId != null || savedThreadId != null)) {
            AgentLoggingContext.setContext(threadContext, savedRunId, savedThreadId);
        }
    }

    /**
     * Factory method for wrapping ActionListeners with context preservation.
     * Only wraps if context exists; otherwise returns the original listener
     * to avoid unnecessary overhead.
     *
     * @param listener the ActionListener to wrap
     * @param threadContext the OpenSearch ThreadContext
     * @param <T> the response type
     * @return wrapped listener if context exists, original listener otherwise
     */
    public static <T> ActionListener<T> wrap(ActionListener<T> listener, ThreadContext threadContext) {
        // Only wrap if context exists to avoid overhead for non-agent operations
        if (threadContext == null || !AgentLoggingContext.hasContext(threadContext)) {
            return listener;
        }
        return new ContextAwareActionListener<>(listener, threadContext);
    }
}
