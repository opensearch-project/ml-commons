/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.exception;

/**
 * This exception is thrown when illegal arguments are found.
 */
public class MLIllegalArgumentException extends MLException {
    /**
     * Constructor with error message.
     * @param message message of the exception
     */
    public MLIllegalArgumentException(String message) {
        super(message);
    }

    /**
     * Constructor with specified cause.
     * @param cause exception cause
     */
    public MLIllegalArgumentException(Throwable cause) {
        super(cause);
    }

}
