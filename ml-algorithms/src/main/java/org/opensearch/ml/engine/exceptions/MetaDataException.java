/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.exceptions;

public class MetaDataException extends RuntimeException {
    public MetaDataException() {
        super();
    }

    public MetaDataException(String message) {
        super(message);
    }

    public MetaDataException(String message, Throwable cause) {
        super(message, cause);
    }

    public MetaDataException(Throwable cause) {
        super(cause);
    }
}
