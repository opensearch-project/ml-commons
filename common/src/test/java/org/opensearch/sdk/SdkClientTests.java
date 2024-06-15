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
import org.opensearch.OpenSearchStatusException;
import org.opensearch.core.rest.RestStatus;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
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
    private UpdateDataObjectRequest updateRequest;
    @Mock
    private UpdateDataObjectResponse updateResponse;
    @Mock
    private DeleteDataObjectRequest deleteRequest;
    @Mock
    private DeleteDataObjectResponse deleteResponse;
    @Mock
    private SearchDataObjectRequest searchRequest;
    @Mock
    private SearchDataObjectResponse searchResponse;

    private OpenSearchStatusException testException;
    private InterruptedException interruptedException;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        sdkClient = spy(new SdkClient() {
            @Override
            public CompletionStage<PutDataObjectResponse> putDataObjectAsync(PutDataObjectRequest request, Executor executor) {
                return CompletableFuture.completedFuture(putResponse);
            }

            @Override
            public CompletionStage<GetDataObjectResponse> getDataObjectAsync(GetDataObjectRequest request, Executor executor) {
                return CompletableFuture.completedFuture(getResponse);
            }

            @Override
            public CompletionStage<UpdateDataObjectResponse> updateDataObjectAsync(UpdateDataObjectRequest request, Executor executor) {
                return CompletableFuture.completedFuture(updateResponse);
            }

            @Override
            public CompletionStage<DeleteDataObjectResponse> deleteDataObjectAsync(DeleteDataObjectRequest request, Executor executor) {
                return CompletableFuture.completedFuture(deleteResponse);
            }

            @Override
            public CompletionStage<SearchDataObjectResponse> searchDataObjectAsync(SearchDataObjectRequest request, Executor executor) {
                return CompletableFuture.completedFuture(searchResponse);
            }
        });
        testException = new OpenSearchStatusException("Test", RestStatus.BAD_REQUEST);
        interruptedException = new InterruptedException();
    }

    @Test
    public void testPutDataObjectSuccess() {
        assertEquals(putResponse, sdkClient.putDataObject(putRequest));
        verify(sdkClient).putDataObjectAsync(any(PutDataObjectRequest.class), any(Executor.class));
    }

    @Test
    public void testPutDataObjectException() {
        when(sdkClient.putDataObjectAsync(any(PutDataObjectRequest.class), any(Executor.class)))
                .thenReturn(CompletableFuture.failedFuture(testException));

        OpenSearchStatusException exception = assertThrows(OpenSearchStatusException.class, () -> {
            sdkClient.putDataObject(putRequest);
        });
        assertEquals(testException, exception);
        assertFalse(Thread.interrupted());        
        verify(sdkClient).putDataObjectAsync(any(PutDataObjectRequest.class), any(Executor.class));
    }

    @Test
    public void testPutDataObjectInterrupted() {
        when(sdkClient.putDataObjectAsync(any(PutDataObjectRequest.class), any(Executor.class)))
                .thenReturn(CompletableFuture.failedFuture(interruptedException));

        OpenSearchException exception = assertThrows(OpenSearchException.class, () -> {
            sdkClient.putDataObject(putRequest);
        });
        assertEquals(interruptedException, exception.getCause());
        assertTrue(Thread.interrupted());        
        verify(sdkClient).putDataObjectAsync(any(PutDataObjectRequest.class), any(Executor.class));
    }

    @Test
    public void testGetDataObjectSuccess() {
        assertEquals(getResponse, sdkClient.getDataObject(getRequest));
        verify(sdkClient).getDataObjectAsync(any(GetDataObjectRequest.class), any(Executor.class));
    }

    @Test
    public void testGetDataObjectException() {
        when(sdkClient.getDataObjectAsync(any(GetDataObjectRequest.class), any(Executor.class)))
                .thenReturn(CompletableFuture.failedFuture(testException));

        OpenSearchStatusException exception = assertThrows(OpenSearchStatusException.class, () -> {
            sdkClient.getDataObject(getRequest);
        });
        assertEquals(testException, exception);
        assertFalse(Thread.interrupted());        
        verify(sdkClient).getDataObjectAsync(any(GetDataObjectRequest.class), any(Executor.class));
    }

    @Test
    public void testGetDataObjectInterrupted() {
        when(sdkClient.getDataObjectAsync(any(GetDataObjectRequest.class), any(Executor.class)))
        .thenReturn(CompletableFuture.failedFuture(interruptedException));

        OpenSearchException exception = assertThrows(OpenSearchException.class, () -> {
            sdkClient.getDataObject(getRequest);
        });
        assertEquals(interruptedException, exception.getCause());
        assertTrue(Thread.interrupted());        
        verify(sdkClient).getDataObjectAsync(any(GetDataObjectRequest.class), any(Executor.class));
    }


    @Test
    public void testUpdateDataObjectSuccess() {
        assertEquals(updateResponse, sdkClient.updateDataObject(updateRequest));
        verify(sdkClient).updateDataObjectAsync(any(UpdateDataObjectRequest.class), any(Executor.class));
    }

    @Test
    public void testUpdateDataObjectException() {
        when(sdkClient.updateDataObjectAsync(any(UpdateDataObjectRequest.class), any(Executor.class)))
                .thenReturn(CompletableFuture.failedFuture(testException));
        OpenSearchStatusException exception = assertThrows(OpenSearchStatusException.class, () -> {
            sdkClient.updateDataObject(updateRequest);
        });
        assertEquals(testException, exception);
        assertFalse(Thread.interrupted());        
        verify(sdkClient).updateDataObjectAsync(any(UpdateDataObjectRequest.class), any(Executor.class));
    }

    @Test
    public void testUpdateDataObjectInterrupted() {
        when(sdkClient.updateDataObjectAsync(any(UpdateDataObjectRequest.class), any(Executor.class)))
        .thenReturn(CompletableFuture.failedFuture(interruptedException));
        OpenSearchException exception = assertThrows(OpenSearchException.class, () -> {
            sdkClient.updateDataObject(updateRequest);
        });
        assertEquals(interruptedException, exception.getCause());
        assertTrue(Thread.interrupted());        
        verify(sdkClient).updateDataObjectAsync(any(UpdateDataObjectRequest.class), any(Executor.class));
    }
    
    @Test
    public void testDeleteDataObjectSuccess() {
        assertEquals(deleteResponse, sdkClient.deleteDataObject(deleteRequest));
        verify(sdkClient).deleteDataObjectAsync(any(DeleteDataObjectRequest.class), any(Executor.class));
    }

    @Test
    public void testDeleteDataObjectException() {
        when(sdkClient.deleteDataObjectAsync(any(DeleteDataObjectRequest.class), any(Executor.class)))
                .thenReturn(CompletableFuture.failedFuture(testException));
        OpenSearchStatusException exception = assertThrows(OpenSearchStatusException.class, () -> {
            sdkClient.deleteDataObject(deleteRequest);
        });
        assertEquals(testException, exception);
        assertFalse(Thread.interrupted());        
        verify(sdkClient).deleteDataObjectAsync(any(DeleteDataObjectRequest.class), any(Executor.class));
    }

    @Test
    public void testDeleteDataObjectInterrupted() {
        when(sdkClient.deleteDataObjectAsync(any(DeleteDataObjectRequest.class), any(Executor.class)))
        .thenReturn(CompletableFuture.failedFuture(interruptedException));
        OpenSearchException exception = assertThrows(OpenSearchException.class, () -> {
            sdkClient.deleteDataObject(deleteRequest);
        });
        assertEquals(interruptedException, exception.getCause());
        assertTrue(Thread.interrupted());        
        verify(sdkClient).deleteDataObjectAsync(any(DeleteDataObjectRequest.class), any(Executor.class));
    }
    
    @Test
    public void testSearchDataObjectSuccess() {
        assertEquals(searchResponse, sdkClient.searchDataObject(searchRequest));
        verify(sdkClient).searchDataObjectAsync(any(SearchDataObjectRequest.class), any(Executor.class));
    }

    @Test
    public void testSearchDataObjectException() {
        when(sdkClient.searchDataObjectAsync(any(SearchDataObjectRequest.class), any(Executor.class)))
                .thenReturn(CompletableFuture.failedFuture(testException));
        OpenSearchStatusException exception = assertThrows(OpenSearchStatusException.class, () -> {
            sdkClient.searchDataObject(searchRequest);
        });
        assertEquals(testException, exception);
        assertFalse(Thread.interrupted());        
        verify(sdkClient).searchDataObjectAsync(any(SearchDataObjectRequest.class), any(Executor.class));
    }

    @Test
    public void testSearchDataObjectInterrupted() {
        when(sdkClient.searchDataObjectAsync(any(SearchDataObjectRequest.class), any(Executor.class)))
        .thenReturn(CompletableFuture.failedFuture(interruptedException));
        OpenSearchException exception = assertThrows(OpenSearchException.class, () -> {
            sdkClient.searchDataObject(searchRequest);
        });
        assertEquals(interruptedException, exception.getCause());
        assertTrue(Thread.interrupted());        
        verify(sdkClient).searchDataObjectAsync(any(SearchDataObjectRequest.class), any(Executor.class));
    }
}
