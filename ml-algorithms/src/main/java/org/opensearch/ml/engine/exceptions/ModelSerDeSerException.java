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
