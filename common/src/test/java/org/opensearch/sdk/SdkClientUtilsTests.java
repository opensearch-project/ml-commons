/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.sdk;

import org.junit.Before;
import org.junit.Test;
import org.opensearch.OpenSearchStatusException;
import org.opensearch.action.support.PlainActionFuture;
import org.opensearch.core.rest.RestStatus;

import java.io.IOException;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionException;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class SdkClientUtilsTests {

    private OpenSearchStatusException testException;
    private InterruptedException interruptedException;
    private IOException ioException;

    @Before
    public void setUp() {
        testException = new OpenSearchStatusException("Test", RestStatus.BAD_REQUEST);
        interruptedException = new InterruptedException();
        ioException = new IOException();
    }

    @Test
    public void testUnwrapAndConvertToException_CompletionException() {
        CompletionException ce = new CompletionException(testException);
        Exception e = SdkClientUtils.unwrapAndConvertToException(ce);
        assertSame(testException, e);

        ce = new CompletionException(interruptedException);
        e = SdkClientUtils.unwrapAndConvertToException(ce); // sets interrupted
        assertTrue(Thread.interrupted()); // tests and resets interrupted
        assertSame(interruptedException, e);

        ce = new CompletionException(ioException);
        e = SdkClientUtils.unwrapAndConvertToException(ce);
        assertFalse(Thread.currentThread().isInterrupted());
        assertSame(ioException, e);

        PlainActionFuture<Object> future = PlainActionFuture.newFuture();
        future.onFailure(ioException);
        e = assertThrows(RuntimeException.class, () -> future.actionGet());
        e = SdkClientUtils.unwrapAndConvertToException(e);
        assertSame(ioException, e);
    }

    @Test
    public void testUnwrapAndConvertToException_Unwrapped() {
        CancellationException ce = new CancellationException();
        Exception e = SdkClientUtils.unwrapAndConvertToException(ce);
        assertSame(ce, e);

        e = SdkClientUtils.unwrapAndConvertToException(ioException);
        assertSame(ioException, e);
    }

    @Test
    public void testGetRethrownExecutionException_Unwrapped() {
        PlainActionFuture<Object> future = PlainActionFuture.newFuture();
        future.onFailure(testException);
        RuntimeException e = assertThrows(RuntimeException.class, () -> future.actionGet());
        Throwable notWrapped = SdkClientUtils.getRethrownExecutionExceptionRootCause(e);
        assertSame(testException, notWrapped);
    }

    @Test
    public void testGetRethrownExecutionException_Wrapped() {
        PlainActionFuture<Object> future = PlainActionFuture.newFuture();
        future.onFailure(ioException);
        RuntimeException e = assertThrows(RuntimeException.class, () -> future.actionGet());
        Throwable wrapped = SdkClientUtils.getRethrownExecutionExceptionRootCause(e);
        assertSame(ioException, wrapped);        
    }
}
