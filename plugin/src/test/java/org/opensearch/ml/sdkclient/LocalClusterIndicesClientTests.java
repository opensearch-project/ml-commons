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
import static org.mockito.Mockito.doThrow;
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
import org.opensearch.action.DocWriteResponse;
import org.opensearch.action.delete.DeleteRequest;
import org.opensearch.action.delete.DeleteResponse;
import org.opensearch.action.get.GetRequest;
import org.opensearch.action.get.GetResponse;
import org.opensearch.action.index.IndexRequest;
import org.opensearch.action.index.IndexResponse;
import org.opensearch.client.Client;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.action.ActionResponse;
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

        doAnswer(invocation -> {
            ActionListener<ActionResponse> listener = invocation.getArgument(1);

            IndexResponse response = mock(IndexResponse.class);
            when(response.getId()).thenReturn(TEST_ID);
            when(response.getResult()).thenReturn(DocWriteResponse.Result.CREATED);
            listener.onResponse(response);
            return null;
        }).when(mockedClient).index(any(IndexRequest.class), any());

        PutDataObjectResponse response = sdkClient.putDataObject(putRequest);

        ArgumentCaptor<IndexRequest> requestCaptor = ArgumentCaptor.forClass(IndexRequest.class);
        verify(mockedClient, times(1)).index(requestCaptor.capture(), any());
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
        assertEquals(IOException.class, ose.getCause().getClass());
    }

    public void testPutDataObject_OuterException() throws IOException {
        PutDataObjectRequest putRequest = new PutDataObjectRequest.Builder().index(TEST_INDEX).dataObject(testDataObject).build();

        doThrow(new NullPointerException("test")).when(mockedClient).index(any(IndexRequest.class), any());

        OpenSearchException ose = assertThrows(OpenSearchException.class, () -> sdkClient.putDataObject(putRequest));
        assertEquals(NullPointerException.class, ose.getCause().getClass());
    }

    public void testGetDataObject() throws IOException {
        GetDataObjectRequest getRequest = new GetDataObjectRequest.Builder().index(TEST_INDEX).id(TEST_ID).build();

        doAnswer(invocation -> {
            ActionListener<ActionResponse> listener = invocation.getArgument(1);

            GetResponse response = mock(GetResponse.class);
            when(response.getId()).thenReturn(TEST_ID);
            when(response.getSourceAsString()).thenReturn(testDataObject.toJson());
            listener.onResponse(response);
            return null;
        }).when(mockedClient).get(any(GetRequest.class), any());

        GetDataObjectResponse response = sdkClient.getDataObject(getRequest);

        ArgumentCaptor<GetRequest> requestCaptor = ArgumentCaptor.forClass(GetRequest.class);
        verify(mockedClient, times(1)).get(requestCaptor.capture(), any());
        assertEquals(TEST_INDEX, requestCaptor.getValue().index());
        assertEquals(TEST_ID, response.id());
        XContentParser parser = response.parser();
        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.nextToken(), parser);
        assertEquals("foo", TestDataObject.parse(parser).data());
    }

    public void testGetDataObject_Exception() throws IOException {
        GetDataObjectRequest getRequest = new GetDataObjectRequest.Builder().index(TEST_INDEX).id(TEST_ID).build();

        doAnswer(invocation -> {
            ActionListener<ActionResponse> listener = invocation.getArgument(1);
            listener.onFailure(new IOException("test"));
            return null;
        }).when(mockedClient).get(any(GetRequest.class), any());

        OpenSearchException ose = assertThrows(OpenSearchException.class, () -> sdkClient.getDataObject(getRequest));
        assertEquals(IOException.class, ose.getCause().getClass());
    }

    public void testGetDataObject_OuterException() throws IOException {
        GetDataObjectRequest getRequest = new GetDataObjectRequest.Builder().index(TEST_INDEX).id(TEST_ID).build();

        doThrow(new NullPointerException("test")).when(mockedClient).get(any(GetRequest.class), any());

        OpenSearchException ose = assertThrows(OpenSearchException.class, () -> sdkClient.getDataObject(getRequest));
        assertEquals(NullPointerException.class, ose.getCause().getClass());
    }

    public void testDeleteDataObject() throws IOException {
        DeleteDataObjectRequest deleteRequest = new DeleteDataObjectRequest.Builder().index(TEST_INDEX).id(TEST_ID).build();

        doAnswer(invocation -> {
            ActionListener<ActionResponse> listener = invocation.getArgument(1);

            DeleteResponse response = mock(DeleteResponse.class);
            when(response.getId()).thenReturn(TEST_ID);
            when(response.getResult()).thenReturn(DocWriteResponse.Result.DELETED);
            listener.onResponse(response);
            return null;
        }).when(mockedClient).delete(any(DeleteRequest.class), any());

        DeleteDataObjectResponse response = sdkClient.deleteDataObject(deleteRequest);

        ArgumentCaptor<DeleteRequest> requestCaptor = ArgumentCaptor.forClass(DeleteRequest.class);
        verify(mockedClient, times(1)).delete(requestCaptor.capture(), any());
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
        assertEquals(IOException.class, ose.getCause().getClass());
    }

    public void testDeleteDataObject_OuterException() throws IOException {
        DeleteDataObjectRequest deleteRequest = new DeleteDataObjectRequest.Builder().index(TEST_INDEX).id(TEST_ID).build();
        doThrow(new NullPointerException("test")).when(mockedClient).delete(any(DeleteRequest.class), any());

        OpenSearchException ose = assertThrows(OpenSearchException.class, () -> sdkClient.deleteDataObject(deleteRequest));
        assertEquals(NullPointerException.class, ose.getCause().getClass());
    }
}
