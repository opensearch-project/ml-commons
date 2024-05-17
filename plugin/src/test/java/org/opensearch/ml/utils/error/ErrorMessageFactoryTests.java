/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.utils.error;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.opensearch.OpenSearchException;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.transport.RemoteTransportException;

public class ErrorMessageFactoryTests {

    @Test
    public void openSearchExceptionWithoutNestedException() {
        Throwable openSearchThrowable = new OpenSearchException("OpenSearch Exception");
        ErrorMessage errorMessage = ErrorMessageFactory.createErrorMessage(openSearchThrowable, RestStatus.BAD_REQUEST.getStatus());
        assertTrue(errorMessage.exception instanceof OpenSearchException);
        assertEquals(RestStatus.INTERNAL_SERVER_ERROR.getStatus(), errorMessage.getStatus());
    }

    @Test
    public void openSearchExceptionWithNestedException() {
        Throwable nestedThrowable = new IllegalArgumentException("Illegal Argument Exception");
        Throwable openSearchThrowable = new RemoteTransportException("Remote Transport Exception", nestedThrowable);
        ErrorMessage errorMessage = ErrorMessageFactory
            .createErrorMessage(openSearchThrowable, RestStatus.INTERNAL_SERVER_ERROR.getStatus());
        assertTrue(errorMessage.exception instanceof IllegalArgumentException);
        assertEquals(RestStatus.BAD_REQUEST.getStatus(), errorMessage.getStatus());
    }

    @Test
    public void nonOpenSearchExceptionWithNestedException() {
        Throwable nestedThrowable = new IllegalArgumentException("Illegal Argument Exception");
        Throwable nonOpenSearchThrowable = new Exception("Remote Transport Exception", nestedThrowable);
        ErrorMessage errorMessage = ErrorMessageFactory
            .createErrorMessage(nonOpenSearchThrowable, RestStatus.INTERNAL_SERVER_ERROR.getStatus());
        assertTrue(errorMessage.exception instanceof IllegalArgumentException);
        assertEquals(RestStatus.INTERNAL_SERVER_ERROR.getStatus(), errorMessage.getStatus());
    }
}
