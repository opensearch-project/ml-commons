/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.exception;

/**
 * This exception is thrown when a some limit is exceeded.
 * Won't count this exception in stats.
 */
public class MLLimitExceededException extends MLException {

    /**
     * Constructor with error message.
     * @param message message of the exception
     */
    public MLLimitExceededException(String message) {
        super(message);
        countedInStats(false);// don't count limit exceeded exception in stats
    }

    /**
     * Constructor with specified cause.
     * @param cause exception cause
     */
    public MLLimitExceededException(Throwable cause) {
        super(cause);
        countedInStats(false);
    }
}
