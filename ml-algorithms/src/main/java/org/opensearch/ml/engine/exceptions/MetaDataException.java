/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 *
 * Modifications Copyright OpenSearch Contributors. See
 * GitHub history for details.
 *
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
