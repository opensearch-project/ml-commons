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
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.OpenSearchException;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class SdkClientTests {

    private SdkClient sdkClient;

    @Mock
    private PutDataObjectRequest putRequest;
    @Mock
    private PutDataObjectResponse putResponse;
    @Mock
    private GetDataObjectRequest getRequest;
    @Mock
    private GetDataObjectResponse getResponse;
    @Mock
    private DeleteDataObjectRequest deleteRequest;
    @Mock
    private DeleteDataObjectResponse deleteResponse;

    private RuntimeException testException;
    private InterruptedException interruptedException;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        sdkClient = spy(new SdkClient() {
            @Override
            public CompletionStage<PutDataObjectResponse> putDataObjectAsync(PutDataObjectRequest request) {
                return CompletableFuture.completedFuture(putResponse);
            }

            @Override
            public CompletionStage<GetDataObjectResponse> getDataObjectAsync(GetDataObjectRequest request) {
                return CompletableFuture.completedFuture(getResponse);
            }

            @Override
            public CompletionStage<DeleteDataObjectResponse> deleteDataObjectAsync(DeleteDataObjectRequest request) {
                return CompletableFuture.completedFuture(deleteResponse);
            }
        });
        testException = new RuntimeException();
        interruptedException = new InterruptedException();
    }

    @Test
    public void testPutDataObjectSuccess() {
        assertEquals(putResponse, sdkClient.putDataObject(putRequest));
        verify(sdkClient).putDataObjectAsync(putRequest);
    }

    @Test
    public void testPutDataObjectException() {
        when(sdkClient.putDataObjectAsync(putRequest))
                .thenReturn(CompletableFuture.failedFuture(testException));

        OpenSearchException exception = assertThrows(OpenSearchException.class, () -> {
            sdkClient.putDataObject(putRequest);
        });
        assertEquals(testException, exception.getCause());
        assertFalse(Thread.interrupted());        
        verify(sdkClient).putDataObjectAsync(putRequest);
    }

    @Test
    public void testPutDataObjectInterrupted() {
        when(sdkClient.putDataObjectAsync(putRequest))
                .thenReturn(CompletableFuture.failedFuture(interruptedException));

        OpenSearchException exception = assertThrows(OpenSearchException.class, () -> {
            sdkClient.putDataObject(putRequest);
        });
        assertEquals(interruptedException, exception.getCause());
        assertTrue(Thread.interrupted());        
        verify(sdkClient).putDataObjectAsync(putRequest);
    }

    @Test
    public void testGetDataObjectSuccess() {
        assertEquals(getResponse, sdkClient.getDataObject(getRequest));
        verify(sdkClient).getDataObjectAsync(getRequest);
    }

    @Test
    public void testGetDataObjectException() {
        when(sdkClient.getDataObjectAsync(getRequest))
                .thenReturn(CompletableFuture.failedFuture(testException));

        OpenSearchException exception = assertThrows(OpenSearchException.class, () -> {
            sdkClient.getDataObject(getRequest);
        });
        assertEquals(testException, exception.getCause());
        assertFalse(Thread.interrupted());        
        verify(sdkClient).getDataObjectAsync(getRequest);
    }

    @Test
    public void testGetDataObjectInterrupted() {
        when(sdkClient.getDataObjectAsync(getRequest))
        .thenReturn(CompletableFuture.failedFuture(interruptedException));

        OpenSearchException exception = assertThrows(OpenSearchException.class, () -> {
            sdkClient.getDataObject(getRequest);
        });
        assertEquals(interruptedException, exception.getCause());
        assertTrue(Thread.interrupted());        
        verify(sdkClient).getDataObjectAsync(getRequest);
    }

    @Test
    public void testDeleteDataObjectSuccess() {
        assertEquals(deleteResponse, sdkClient.deleteDataObject(deleteRequest));
        verify(sdkClient).deleteDataObjectAsync(deleteRequest);
    }

    @Test
    public void testDeleteDataObjectException() {
        when(sdkClient.deleteDataObjectAsync(deleteRequest))
                .thenReturn(CompletableFuture.failedFuture(testException));
        OpenSearchException exception = assertThrows(OpenSearchException.class, () -> {
            sdkClient.deleteDataObject(deleteRequest);
        });
        assertEquals(testException, exception.getCause());
        assertFalse(Thread.interrupted());        
        verify(sdkClient).deleteDataObjectAsync(deleteRequest);
    }

    @Test
    public void testDeleteDataObjectInterrupted() {
        when(sdkClient.deleteDataObjectAsync(deleteRequest))
        .thenReturn(CompletableFuture.failedFuture(interruptedException));
        OpenSearchException exception = assertThrows(OpenSearchException.class, () -> {
            sdkClient.deleteDataObject(deleteRequest);
        });
        assertEquals(interruptedException, exception.getCause());
        assertTrue(Thread.interrupted());        
        verify(sdkClient).deleteDataObjectAsync(deleteRequest);
    }
}
