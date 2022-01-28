/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.exception;

/**
 * This exception is thrown when a resource is not found.
 * Won't count this exception in stats.
 */
public class MLResourceNotFoundException extends MLException {

    /**
     * Constructor with error message.
     * @param message message of the exception
     */
    public MLResourceNotFoundException(String message) {
        super(message);
        countedInStats(false);// don't count resource not found exception in stats
    }

    /**
     * Constructor with specified cause.
     * @param cause exception cause
     */
    public MLResourceNotFoundException(Throwable cause) {
        super(cause);
        countedInStats(false);
    }
}
