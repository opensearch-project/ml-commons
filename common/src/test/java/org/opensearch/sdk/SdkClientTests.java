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
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class SdkClientTests {

    private SdkClient sdkClient;
    private SdkClientDelegate sdkClientImpl;

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
        sdkClientImpl = spy(new SdkClientDelegate() {
            @Override
            public CompletionStage<PutDataObjectResponse> putDataObjectAsync(
                PutDataObjectRequest request,
                Executor executor,
                Boolean isMultiTenancyEnabled
            ) {
                return CompletableFuture.completedFuture(putResponse);
            }

            @Override
            public CompletionStage<GetDataObjectResponse> getDataObjectAsync(
                GetDataObjectRequest request,
                Executor executor,
                Boolean isMultiTenancyEnabled
            ) {
                return CompletableFuture.completedFuture(getResponse);
            }

            @Override
            public CompletionStage<UpdateDataObjectResponse> updateDataObjectAsync(
                UpdateDataObjectRequest request,
                Executor executor,
                Boolean isMultiTenancyEnabled
            ) {
                return CompletableFuture.completedFuture(updateResponse);
            }

            @Override
            public CompletionStage<DeleteDataObjectResponse> deleteDataObjectAsync(
                DeleteDataObjectRequest request,
                Executor executor,
                Boolean isMultiTenancyEnabled
            ) {
                return CompletableFuture.completedFuture(deleteResponse);
            }

            @Override
            public CompletionStage<SearchDataObjectResponse> searchDataObjectAsync(
                SearchDataObjectRequest request,
                Executor executor,
                Boolean isMultiTenancyEnabled
            ) {
                return CompletableFuture.completedFuture(searchResponse);
            }
        });
        sdkClient = new SdkClient(sdkClientImpl);
        sdkClient.onMultiTenancyEnabledChanged(true);
        testException = new OpenSearchStatusException("Test", RestStatus.BAD_REQUEST);
        interruptedException = new InterruptedException();
    }

    @Test
    public void testPutDataObjectSuccess() {
        assertEquals(putResponse, sdkClient.putDataObject(putRequest));
        verify(sdkClientImpl).putDataObjectAsync(any(PutDataObjectRequest.class), any(Executor.class), anyBoolean());
    }

    @Test
    public void testPutDataObjectException() {
        when(sdkClientImpl.putDataObjectAsync(any(PutDataObjectRequest.class), any(Executor.class), anyBoolean()))
                .thenReturn(CompletableFuture.failedFuture(testException));

        OpenSearchStatusException exception = assertThrows(OpenSearchStatusException.class, () -> {
            sdkClient.putDataObject(putRequest);
        });
        assertEquals(testException, exception);
        assertFalse(Thread.interrupted());        
        verify(sdkClientImpl).putDataObjectAsync(any(PutDataObjectRequest.class), any(Executor.class), anyBoolean());
    }

    @Test
    public void testPutDataObjectInterrupted() {
        when(sdkClientImpl.putDataObjectAsync(any(PutDataObjectRequest.class), any(Executor.class), anyBoolean()))
                .thenReturn(CompletableFuture.failedFuture(interruptedException));

        OpenSearchException exception = assertThrows(OpenSearchException.class, () -> {
            sdkClient.putDataObject(putRequest);
        });
        assertEquals(interruptedException, exception.getCause());
        assertTrue(Thread.interrupted());        
        verify(sdkClientImpl).putDataObjectAsync(any(PutDataObjectRequest.class), any(Executor.class), anyBoolean());
    }

    @Test
    public void testGetDataObjectSuccess() {
        assertEquals(getResponse, sdkClient.getDataObject(getRequest));
        verify(sdkClientImpl).getDataObjectAsync(any(GetDataObjectRequest.class), any(Executor.class), anyBoolean());
    }

    @Test
    public void testGetDataObjectException() {
        when(sdkClientImpl.getDataObjectAsync(any(GetDataObjectRequest.class), any(Executor.class), anyBoolean()))
                .thenReturn(CompletableFuture.failedFuture(testException));

        OpenSearchStatusException exception = assertThrows(OpenSearchStatusException.class, () -> {
            sdkClient.getDataObject(getRequest);
        });
        assertEquals(testException, exception);
        assertFalse(Thread.interrupted());        
        verify(sdkClientImpl).getDataObjectAsync(any(GetDataObjectRequest.class), any(Executor.class), anyBoolean());
    }

    @Test
    public void testGetDataObjectInterrupted() {
        when(sdkClientImpl.getDataObjectAsync(any(GetDataObjectRequest.class), any(Executor.class), anyBoolean()))
        .thenReturn(CompletableFuture.failedFuture(interruptedException));

        OpenSearchException exception = assertThrows(OpenSearchException.class, () -> {
            sdkClient.getDataObject(getRequest);
        });
        assertEquals(interruptedException, exception.getCause());
        assertTrue(Thread.interrupted());        
        verify(sdkClientImpl).getDataObjectAsync(any(GetDataObjectRequest.class), any(Executor.class), anyBoolean());
    }

    @Test
    public void testUpdateDataObjectSuccess() {
        assertEquals(updateResponse, sdkClient.updateDataObject(updateRequest));
        verify(sdkClientImpl).updateDataObjectAsync(any(UpdateDataObjectRequest.class), any(Executor.class), anyBoolean());
    }

    @Test
    public void testUpdateDataObjectException() {
        when(sdkClientImpl.updateDataObjectAsync(any(UpdateDataObjectRequest.class), any(Executor.class), anyBoolean()))
                .thenReturn(CompletableFuture.failedFuture(testException));
        OpenSearchStatusException exception = assertThrows(OpenSearchStatusException.class, () -> {
            sdkClient.updateDataObject(updateRequest);
        });
        assertEquals(testException, exception);
        assertFalse(Thread.interrupted());        
        verify(sdkClientImpl).updateDataObjectAsync(any(UpdateDataObjectRequest.class), any(Executor.class), anyBoolean());
    }

    @Test
    public void testUpdateDataObjectInterrupted() {
        when(sdkClientImpl.updateDataObjectAsync(any(UpdateDataObjectRequest.class), any(Executor.class), anyBoolean()))
        .thenReturn(CompletableFuture.failedFuture(interruptedException));
        OpenSearchException exception = assertThrows(OpenSearchException.class, () -> {
            sdkClient.updateDataObject(updateRequest);
        });
        assertEquals(interruptedException, exception.getCause());
        assertTrue(Thread.interrupted());        
        verify(sdkClientImpl).updateDataObjectAsync(any(UpdateDataObjectRequest.class), any(Executor.class), anyBoolean());
    }

    @Test
    public void testDeleteDataObjectSuccess() {
        assertEquals(deleteResponse, sdkClient.deleteDataObject(deleteRequest));
        verify(sdkClientImpl).deleteDataObjectAsync(any(DeleteDataObjectRequest.class), any(Executor.class), anyBoolean());
    }

    @Test
    public void testDeleteDataObjectException() {
        when(sdkClientImpl.deleteDataObjectAsync(any(DeleteDataObjectRequest.class), any(Executor.class), anyBoolean()))
                .thenReturn(CompletableFuture.failedFuture(testException));
        OpenSearchStatusException exception = assertThrows(OpenSearchStatusException.class, () -> {
            sdkClient.deleteDataObject(deleteRequest);
        });
        assertEquals(testException, exception);
        assertFalse(Thread.interrupted());        
        verify(sdkClientImpl).deleteDataObjectAsync(any(DeleteDataObjectRequest.class), any(Executor.class), anyBoolean());
    }

    @Test
    public void testDeleteDataObjectInterrupted() {
        when(sdkClientImpl.deleteDataObjectAsync(any(DeleteDataObjectRequest.class), any(Executor.class), anyBoolean()))
        .thenReturn(CompletableFuture.failedFuture(interruptedException));
        OpenSearchException exception = assertThrows(OpenSearchException.class, () -> {
            sdkClient.deleteDataObject(deleteRequest);
        });
        assertEquals(interruptedException, exception.getCause());
        assertTrue(Thread.interrupted());        
        verify(sdkClientImpl).deleteDataObjectAsync(any(DeleteDataObjectRequest.class), any(Executor.class), anyBoolean());
    }

    @Test
    public void testSearchDataObjectSuccess() {
        assertEquals(searchResponse, sdkClient.searchDataObject(searchRequest));
        verify(sdkClientImpl).searchDataObjectAsync(any(SearchDataObjectRequest.class), any(Executor.class), anyBoolean());
    }

    @Test
    public void testSearchDataObjectException() {
        when(sdkClientImpl.searchDataObjectAsync(any(SearchDataObjectRequest.class), any(Executor.class), anyBoolean()))
                .thenReturn(CompletableFuture.failedFuture(testException));
        OpenSearchStatusException exception = assertThrows(OpenSearchStatusException.class, () -> {
            sdkClient.searchDataObject(searchRequest);
        });
        assertEquals(testException, exception);
        assertFalse(Thread.interrupted());        
        verify(sdkClientImpl).searchDataObjectAsync(any(SearchDataObjectRequest.class), any(Executor.class), anyBoolean());
    }

    @Test
    public void testSearchDataObjectInterrupted() {
        when(sdkClientImpl.searchDataObjectAsync(any(SearchDataObjectRequest.class), any(Executor.class), anyBoolean()))
        .thenReturn(CompletableFuture.failedFuture(interruptedException));
        OpenSearchException exception = assertThrows(OpenSearchException.class, () -> {
            sdkClient.searchDataObject(searchRequest);
        });
        assertEquals(interruptedException, exception.getCause());
        assertTrue(Thread.interrupted());        
        verify(sdkClientImpl).searchDataObjectAsync(any(SearchDataObjectRequest.class), any(Executor.class), anyBoolean());
    }
}
