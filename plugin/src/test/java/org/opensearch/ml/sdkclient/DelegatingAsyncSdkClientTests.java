/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.sdkclient;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.opensearch.OpenSearchStatusException;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.remote.metadata.client.GetDataObjectRequest;
import org.opensearch.remote.metadata.client.GetDataObjectResponse;
import org.opensearch.remote.metadata.client.SdkClientDelegate;

public class DelegatingAsyncSdkClientTests {

    private SdkClientDelegate mockDelegate;
    private ExecutorService testExecutor;
    private DelegatingAsyncSdkClient wrapper;

    @Before
    public void setUp() {
        mockDelegate = mock(SdkClientDelegate.class);
        testExecutor = Executors.newFixedThreadPool(2);
        wrapper = new DelegatingAsyncSdkClient(mockDelegate, testExecutor);
    }

    @After
    public void tearDown() {
        testExecutor.shutdownNow();
    }

    @Test
    public void testSuccessPathHopsThread() throws Exception {
        // Simulate LocalClusterIndicesClient completing on "transport thread"
        CompletableFuture<GetDataObjectResponse> innerFuture = new CompletableFuture<>();
        GetDataObjectRequest request = mock(GetDataObjectRequest.class);
        when(mockDelegate.getDataObjectAsync(eq(request), any(), any())).thenReturn(innerFuture);

        // Get the wrapped stage
        var stage = wrapper.getDataObjectAsync(request, testExecutor, false);

        // Track which thread the callback runs on
        AtomicReference<String> callbackThread = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        stage.whenComplete((response, throwable) -> {
            callbackThread.set(Thread.currentThread().getName());
            latch.countDown();
        });

        // Complete on the "transport thread" (current thread)
        String transportThread = Thread.currentThread().getName();
        GetDataObjectResponse mockResponse = mock(GetDataObjectResponse.class);
        innerFuture.complete(mockResponse);

        // Wait for callback
        assertTrue("Callback timed out", latch.await(5, TimeUnit.SECONDS));

        // Verify callback did NOT run on the transport thread
        assertNotEquals("Callback must not run on the completing (transport) thread", transportThread, callbackThread.get());
    }

    @Test
    public void testFailurePathHopsThread() throws Exception {
        CompletableFuture<GetDataObjectResponse> innerFuture = new CompletableFuture<>();
        GetDataObjectRequest request = mock(GetDataObjectRequest.class);
        when(mockDelegate.getDataObjectAsync(eq(request), any(), any())).thenReturn(innerFuture);

        var stage = wrapper.getDataObjectAsync(request, testExecutor, false);

        AtomicReference<String> callbackThread = new AtomicReference<>();
        AtomicReference<Throwable> caughtError = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        stage.whenComplete((response, throwable) -> {
            callbackThread.set(Thread.currentThread().getName());
            caughtError.set(throwable);
            latch.countDown();
        });

        // Complete exceptionally on "transport thread"
        String transportThread = Thread.currentThread().getName();
        OpenSearchStatusException exception = new OpenSearchStatusException("test", RestStatus.INTERNAL_SERVER_ERROR);
        innerFuture.completeExceptionally(exception);

        assertTrue("Callback timed out", latch.await(5, TimeUnit.SECONDS));

        // Verify thread hop happened
        assertNotEquals(transportThread, callbackThread.get());

        // Verify exception is preserved (not wrapped in CompletionException)
        assertSame(exception, caughtError.get());
    }

    @Test
    public void testNullDelegateThrows() {
        assertThrows(IllegalArgumentException.class, () -> new DelegatingAsyncSdkClient(null, testExecutor));
    }

    @Test
    public void testNullExecutorThrows() {
        assertThrows(IllegalArgumentException.class, () -> new DelegatingAsyncSdkClient(mockDelegate, null));
    }

    @Test
    public void testSupportsMetadataTypeDelegates() {
        when(mockDelegate.supportsMetadataType("local")).thenReturn(true);
        assertTrue(wrapper.supportsMetadataType("local"));
        verify(mockDelegate).supportsMetadataType("local");
    }
}
