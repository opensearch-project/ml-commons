/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.utils.error;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.opensearch.OpenSearchException;
import org.opensearch.core.rest.RestStatus;

public class ErrorMessageFactoryTests {

    private Throwable nonOpenSearchThrowable = new Throwable();
    private Throwable openSearchThrowable = new OpenSearchException(nonOpenSearchThrowable);

    @Test
    public void openSearchExceptionShouldCreateEsErrorMessage() {
        Exception exception = new OpenSearchException(nonOpenSearchThrowable);
        ErrorMessage msg = ErrorMessageFactory.createErrorMessage(exception, RestStatus.BAD_REQUEST.getStatus());
        assertTrue(msg.exception instanceof OpenSearchException);
    }

    @Test
    public void nonOpenSearchExceptionShouldCreateGenericErrorMessage() {
        Exception exception = new Exception(nonOpenSearchThrowable);
        ErrorMessage msg = ErrorMessageFactory.createErrorMessage(exception, RestStatus.BAD_REQUEST.getStatus());
        assertFalse(msg.exception instanceof OpenSearchException);
    }

    @Test
    public void nonOpenSearchExceptionWithWrappedEsExceptionCauseShouldCreateEsErrorMessage() {
        Exception exception = (Exception) openSearchThrowable;
        ErrorMessage msg = ErrorMessageFactory.createErrorMessage(exception, RestStatus.BAD_REQUEST.getStatus());
        assertTrue(msg.exception instanceof OpenSearchException);
    }

    @Test
    public void nonOpenSearchExceptionWithMultiLayerWrappedEsExceptionCauseShouldCreateEsErrorMessage() {
        Exception exception = new Exception(new Throwable(new Throwable(openSearchThrowable)));
        ErrorMessage msg = ErrorMessageFactory.createErrorMessage(exception, RestStatus.BAD_REQUEST.getStatus());
        assertTrue(msg.exception instanceof OpenSearchException);
    }
}
