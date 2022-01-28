/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.exceptions;

public class ModelSerDeSerException extends RuntimeException {
    public ModelSerDeSerException() {
        super();
    }

    public ModelSerDeSerException(String message) {
        super(message);
    }

    public ModelSerDeSerException(String message, Throwable cause) {
        super(message, cause);
    }

    public ModelSerDeSerException(Throwable cause) {
        super(cause);
    }
}
