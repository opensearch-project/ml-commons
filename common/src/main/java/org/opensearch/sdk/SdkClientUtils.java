/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.sdk;

import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

import org.opensearch.OpenSearchException;
import org.opensearch.common.action.ActionFuture;
import org.opensearch.common.util.concurrent.FutureUtils;
import org.opensearch.common.util.concurrent.UncategorizedExecutionException;

public class SdkClientUtils {

    /**
     * Unwraps the cause of a {@link CompletionException}. If the cause is a subclass of {@link RuntimeException}, rethrows the exception.
     * Otherwise wraps it in an {@link OpenSearchException}. Properly re-interrupts the thread on {@link InterruptedException}.
     * @param e the CompletionException
     * @return The wrapped cause of the completion exception if unchecked, otherwise an OpenSearchException wrapping it.
     */
    public static RuntimeException unwrapAndConvertToRuntime(CompletionException e) {
        Throwable cause = e.getCause();
        if (cause instanceof InterruptedException) {
            Thread.currentThread().interrupt();
        }
        // Below is the same as o.o.ExceptionsHelper.convertToRuntime but cause is a throwable
        if (cause instanceof RuntimeException) {
            return (RuntimeException) cause;
        }
        return new OpenSearchException(cause);
    }

    /**
     * Get the original exception of an {@link UncategorizedExecutionException} with two levels of cause nesting.
     * Intended to recreate the root cause of an exception thrown by {@link ActionFuture#actionGet}, which was handled by {@link FutureUtils#rethrowExecutionException}.
     * @param throwable a throwable with possibly nested causes
     * @return the root cause of an ExecutionException if it was not a RuntimeException, otherwise the original exception 
     */
    public static Throwable getRethrownExecutionExceptionRootCause(Throwable throwable) {
        if (throwable instanceof UncategorizedExecutionException && throwable.getCause() instanceof ExecutionException) {
            return throwable.getCause().getCause();
        }
        return throwable;
    }
}
