/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.utils.error;

import org.opensearch.OpenSearchException;
import org.opensearch.ml.utils.MLExceptionUtils;

import lombok.experimental.UtilityClass;

@UtilityClass
public class ErrorMessageFactory {
    /**
     * Create error message based on the exception type.
     *
     * @param e exception to create error message
     * @param status exception status code
     * @return error message
     */
    public static ErrorMessage createErrorMessage(Throwable e, int status) {
        Throwable t = e;
        int st = status;
        if (t instanceof OpenSearchException) {
            st = ((OpenSearchException) t).status().getStatus();
        }
        t = MLExceptionUtils.getRootCause(t);

        return new ErrorMessage(t, st);
    }
}
