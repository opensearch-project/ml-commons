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
import org.opensearch.OpenSearchException;
import org.opensearch.OpenSearchStatusException;
import org.opensearch.action.support.PlainActionFuture;
import org.opensearch.core.rest.RestStatus;

import java.io.IOException;
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
    public void testUnwrapAndConvertToRuntime() {
        CompletionException ce = new CompletionException(testException);
        RuntimeException rte = SdkClientUtils.unwrapAndConvertToRuntime(ce);
        assertSame(testException, rte);

        ce = new CompletionException(interruptedException);
        rte = SdkClientUtils.unwrapAndConvertToRuntime(ce); // sets interrupted
        assertTrue(Thread.interrupted()); // tests and resets interrupted
        assertTrue(rte instanceof OpenSearchException);
        assertSame(interruptedException, rte.getCause());

        ce = new CompletionException(ioException);
        rte = SdkClientUtils.unwrapAndConvertToRuntime(ce);
        assertFalse(Thread.currentThread().isInterrupted());
        assertTrue(rte instanceof OpenSearchException);
        assertSame(ioException, rte.getCause());
    }
    
    @Test
    public void testGetRethrownExecutionException_Unwrapped() {
        PlainActionFuture<Object> future = new PlainActionFuture<>();
        future.onFailure(testException);
        RuntimeException e = assertThrows(RuntimeException.class, () -> future.actionGet());
        Throwable notWrapped = SdkClientUtils.getRethrownExecutionExceptionRootCause(e);
        assertSame(testException, notWrapped);
    }

    @Test
    public void testGetRethrownExecutionException_Wrapped() {
        PlainActionFuture<Object> future = new PlainActionFuture<>();
        future.onFailure(ioException);
        RuntimeException e = assertThrows(RuntimeException.class, () -> future.actionGet());
        Throwable wrapped = SdkClientUtils.getRethrownExecutionExceptionRootCause(e);
        assertSame(ioException, wrapped);        
    }
}
