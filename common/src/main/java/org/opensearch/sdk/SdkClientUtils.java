/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.sdk;

import java.util.Locale;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.opensearch.OpenSearchException;
import org.opensearch.common.action.ActionFuture;
import org.opensearch.common.util.concurrent.FutureUtils;
import org.opensearch.common.util.concurrent.UncategorizedExecutionException;

public class SdkClientUtils {

    /**
     * Unwraps the cause of a {@link CompletionException}. If the cause is an {@link Exception}, rethrows the exception.
     * Otherwise wraps it in an {@link OpenSearchException}. Properly re-interrupts the thread on {@link InterruptedException}.
     * @param throwable a throwable, expected to be a {@link CompletionException} or {@link CancellationException}.
     * @return the cause of the completion exception or the throwable, directly if an {@link Exception} or wrapped in an OpenSearchException otherwise.
     */
    public static Exception unwrapAndConvertToException(Throwable throwable) {
        // Unwrap completion exception or pass through other exceptions
        Throwable cause = throwable instanceof CompletionException ? throwable.getCause() : throwable;
        // Double-unwrap checked exceptions wrapped in ExecutionException
        cause = getRethrownExecutionExceptionRootCause(cause);
        if (cause instanceof InterruptedException) {
            Thread.currentThread().interrupt();
        }
        if (cause instanceof Exception) {
            return (Exception) cause;
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

    /**
     * If an internal variable name matches a field name not assigned to that field, Jackson's ObjectMapper can parse the wrong type. This method removes quotes around numeric fields to work around these cases.
     * @param field The JSON field to remove the quotes from its value
     * @param json The full JSON to process
     * @return The JSON with the quotes removed from a numeric value in the specified field
     */
    public static String unwrapQuotedInteger(String field, String json) {
        String regex = "(\"" + Pattern.quote(field) + "\"):\"(\\d+)\"";
        return json.replaceAll(regex, "$1:$2");
    }

    /**
     * If an internal variable is an enum represented by all upper case, the Remote client may have it mapped in lower case. This method lowercases these enum values 
     * @param field The JSON field to lowercase the value
     * @param json The full JSON to process
     * @return The JSON with the value lowercased
     */
    public static String lowerCaseEnumValues(String field, String json) {
        // Use a matcher to find and replace the field value in lowercase
        Matcher matcher = Pattern.compile("(\"" + Pattern.quote(field) + "\"):(\"[A-Z_]+\")").matcher(json);
        StringBuffer sb = new StringBuffer();        
        while (matcher.find()) {
            matcher.appendReplacement(sb, matcher.group(1) + ":" + matcher.group(2).toLowerCase(Locale.ROOT));
        }
        matcher.appendTail(sb);        
        return sb.toString();
    }
}
