/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.ml.sdkclient;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;

import java.io.IOException;

import org.junit.Before;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.OpenSearchException;
import org.opensearch.OpenSearchStatusException;
import org.opensearch.action.DocWriteResponse;
import org.opensearch.action.delete.DeleteRequest;
import org.opensearch.action.delete.DeleteResponse;
import org.opensearch.action.get.GetRequest;
import org.opensearch.action.get.GetResponse;
import org.opensearch.action.index.IndexRequest;
import org.opensearch.action.index.IndexResponse;
import org.opensearch.client.Client;
import org.opensearch.common.action.ActionFuture;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.action.ActionResponse;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.sdk.DeleteDataObjectRequest;
import org.opensearch.sdk.DeleteDataObjectResponse;
import org.opensearch.sdk.GetDataObjectRequest;
import org.opensearch.sdk.GetDataObjectResponse;
import org.opensearch.sdk.PutDataObjectRequest;
import org.opensearch.sdk.PutDataObjectResponse;
import org.opensearch.sdk.SdkClient;
import org.opensearch.test.OpenSearchTestCase;

import com.carrotsearch.randomizedtesting.annotations.ThreadLeakScope;

@ThreadLeakScope(ThreadLeakScope.Scope.NONE)
public class LocalClusterIndicesClientTests extends OpenSearchTestCase {

    private static final String TEST_ID = "123";
    private static final String TEST_INDEX = "test_index";

    @Mock
    private Client mockedClient;
    private SdkClient sdkClient;

    @Mock
    private NamedXContentRegistry xContentRegistry;

    private TestDataObject testDataObject;

    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);

        sdkClient = new LocalClusterIndicesClient(mockedClient, xContentRegistry);
        testDataObject = new TestDataObject("foo");
    }

    public void testPutDataObject() throws IOException {
        PutDataObjectRequest putRequest = new PutDataObjectRequest.Builder().index(TEST_INDEX).dataObject(testDataObject).build();

        IndexResponse indexResponse = mock(IndexResponse.class);
        when(indexResponse.getId()).thenReturn(TEST_ID);
        when(indexResponse.getResult()).thenReturn(DocWriteResponse.Result.CREATED);
        @SuppressWarnings("unchecked")
        ActionFuture<IndexResponse> future = mock(ActionFuture.class);
        when(mockedClient.index(any(IndexRequest.class))).thenReturn(future);
        when(future.actionGet()).thenReturn(indexResponse);

        PutDataObjectResponse response = sdkClient.putDataObject(putRequest);

        ArgumentCaptor<IndexRequest> requestCaptor = ArgumentCaptor.forClass(IndexRequest.class);
        verify(mockedClient, times(1)).index(requestCaptor.capture());
        assertEquals(TEST_INDEX, requestCaptor.getValue().index());
        assertEquals(TEST_ID, response.id());
        assertTrue(response.created());
    }

    public void testPutDataObject_Exception() throws IOException {
        PutDataObjectRequest putRequest = new PutDataObjectRequest.Builder().index(TEST_INDEX).dataObject(testDataObject).build();

        doAnswer(invocation -> {
            ActionListener<ActionResponse> listener = invocation.getArgument(1);
            listener.onFailure(new IOException("test"));
            return null;
        }).when(mockedClient).index(any(IndexRequest.class), any());

        OpenSearchException ose = assertThrows(OpenSearchException.class, () -> sdkClient.putDataObject(putRequest));
        assertEquals(OpenSearchException.class, ose.getCause().getClass());
    }

    public void testGetDataObject() throws IOException {
        GetDataObjectRequest getRequest = new GetDataObjectRequest.Builder().index(TEST_INDEX).id(TEST_ID).build();

        GetResponse getResponse = mock(GetResponse.class);
        when(getResponse.isExists()).thenReturn(true);
        when(getResponse.getId()).thenReturn(TEST_ID);
        when(getResponse.getSourceAsString()).thenReturn(testDataObject.toJson());
        @SuppressWarnings("unchecked")
        ActionFuture<GetResponse> future = mock(ActionFuture.class);
        when(mockedClient.get(any(GetRequest.class))).thenReturn(future);
        when(future.actionGet()).thenReturn(getResponse);

        GetDataObjectResponse response = sdkClient.getDataObject(getRequest);

        ArgumentCaptor<GetRequest> requestCaptor = ArgumentCaptor.forClass(GetRequest.class);
        verify(mockedClient, times(1)).get(requestCaptor.capture());
        assertEquals(TEST_INDEX, requestCaptor.getValue().index());
        assertEquals(TEST_ID, response.id());
        XContentParser parser = response.parser();
        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.nextToken(), parser);
        assertEquals("foo", TestDataObject.parse(parser).data());
    }

    public void testGetDataObject_NotFound() throws IOException {
        GetDataObjectRequest getRequest = new GetDataObjectRequest.Builder().index(TEST_INDEX).id(TEST_ID).build();

        GetResponse getResponse = mock(GetResponse.class);
        when(getResponse.isExists()).thenReturn(false);
        @SuppressWarnings("unchecked")
        ActionFuture<GetResponse> future = mock(ActionFuture.class);
        when(mockedClient.get(any(GetRequest.class))).thenReturn(future);
        when(future.actionGet()).thenReturn(getResponse);

        OpenSearchException ose = assertThrows(OpenSearchException.class, () -> sdkClient.getDataObject(getRequest));
        assertEquals(OpenSearchStatusException.class, ose.getCause().getClass());
        assertEquals(RestStatus.NOT_FOUND, ((OpenSearchStatusException) ose.getCause()).status());
    }

    public void testGetDataObject_Exception() throws IOException {
        GetDataObjectRequest getRequest = new GetDataObjectRequest.Builder().index(TEST_INDEX).id(TEST_ID).build();

        doAnswer(invocation -> {
            ActionListener<ActionResponse> listener = invocation.getArgument(1);
            listener.onFailure(new IOException("test"));
            return null;
        }).when(mockedClient).get(any(GetRequest.class), any());

        OpenSearchException ose = assertThrows(OpenSearchException.class, () -> sdkClient.getDataObject(getRequest));
        assertEquals(OpenSearchException.class, ose.getCause().getClass());
    }

    public void testDeleteDataObject() throws IOException {
        DeleteDataObjectRequest deleteRequest = new DeleteDataObjectRequest.Builder().index(TEST_INDEX).id(TEST_ID).build();

        DeleteResponse deleteResponse = mock(DeleteResponse.class);
        when(deleteResponse.getId()).thenReturn(TEST_ID);
        when(deleteResponse.getResult()).thenReturn(DocWriteResponse.Result.DELETED);
        @SuppressWarnings("unchecked")
        ActionFuture<DeleteResponse> future = mock(ActionFuture.class);
        when(mockedClient.delete(any(DeleteRequest.class))).thenReturn(future);
        when(future.actionGet()).thenReturn(deleteResponse);

        DeleteDataObjectResponse response = sdkClient.deleteDataObject(deleteRequest);

        ArgumentCaptor<DeleteRequest> requestCaptor = ArgumentCaptor.forClass(DeleteRequest.class);
        verify(mockedClient, times(1)).delete(requestCaptor.capture());
        assertEquals(TEST_INDEX, requestCaptor.getValue().index());
        assertEquals(TEST_ID, response.id());
    }

    public void testDeleteDataObject_Exception() throws IOException {
        DeleteDataObjectRequest deleteRequest = new DeleteDataObjectRequest.Builder().index(TEST_INDEX).id(TEST_ID).build();

        doAnswer(invocation -> {
            ActionListener<ActionResponse> listener = invocation.getArgument(1);
            listener.onFailure(new IOException("test"));
            return null;
        }).when(mockedClient).delete(any(DeleteRequest.class), any());

        OpenSearchException ose = assertThrows(OpenSearchException.class, () -> sdkClient.deleteDataObject(deleteRequest));
        assertEquals(OpenSearchException.class, ose.getCause().getClass());
    }
}
