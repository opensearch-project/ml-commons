/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.exception;

/**
 * This exception is thrown when validation failed.
 */
public class MLValidationException extends MLException {

    /**
     * Constructor with error message.
     * @param message message of the exception
     */
    public MLValidationException(String message) {
        super(message);
    }

    /**
     * Constructor with specified cause.
     * @param cause exception cause
     */
    public MLValidationException(Throwable cause) {
        super(cause);
    }
}
