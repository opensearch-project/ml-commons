/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.ml.sdkclient;

import static org.mockito.Mockito.when;
import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;

import java.io.IOException;
import java.util.Map;

import org.junit.Before;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.OpenSearchException;
import org.opensearch.client.json.JsonData;
import org.opensearch.client.json.jackson.JacksonJsonpMapper;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.Result;
import org.opensearch.client.opensearch._types.ShardStatistics;
import org.opensearch.client.opensearch.core.DeleteRequest;
import org.opensearch.client.opensearch.core.DeleteResponse;
import org.opensearch.client.opensearch.core.GetRequest;
import org.opensearch.client.opensearch.core.GetResponse;
import org.opensearch.client.opensearch.core.IndexRequest;
import org.opensearch.client.opensearch.core.IndexResponse;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.sdk.DeleteDataObjectRequest;
import org.opensearch.sdk.DeleteDataObjectResponse;
import org.opensearch.sdk.GetDataObjectRequest;
import org.opensearch.sdk.GetDataObjectResponse;
import org.opensearch.sdk.PutDataObjectRequest;
import org.opensearch.sdk.PutDataObjectResponse;
import org.opensearch.sdk.SdkClient;
import org.opensearch.test.OpenSearchTestCase;

import com.fasterxml.jackson.databind.ObjectMapper;

public class RemoteClusterIndicesClientTests extends OpenSearchTestCase {

    private static final String TEST_ID = "123";
    private static final String TEST_INDEX = "test_index";

    @Mock
    private OpenSearchClient mockedOpenSearchClient;
    private SdkClient sdkClient;

    private TestDataObject testDataObject;

    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);

        sdkClient = new RemoteClusterIndicesClient(mockedOpenSearchClient);
        testDataObject = new TestDataObject("foo");
    }

    public void testPutDataObject() throws IOException {
        PutDataObjectRequest putRequest = new PutDataObjectRequest.Builder().index(TEST_INDEX).dataObject(testDataObject).build();

        IndexResponse indexResponse = new IndexResponse.Builder()
            .id(TEST_ID)
            .index(TEST_INDEX)
            .primaryTerm(0)
            .result(Result.Created)
            .seqNo(0)
            .shards(new ShardStatistics.Builder().failed(0).successful(1).total(1).build())
            .version(0)
            .build();

        ArgumentCaptor<IndexRequest<?>> indexRequestCaptor = ArgumentCaptor.forClass(IndexRequest.class);
        when(mockedOpenSearchClient.index(indexRequestCaptor.capture())).thenReturn(indexResponse);

        PutDataObjectResponse response = sdkClient.putDataObject(putRequest);

        assertEquals(TEST_INDEX, indexRequestCaptor.getValue().index());
        assertEquals(TEST_ID, response.id());
        assertTrue(response.created());
    }

    public void testPutDataObject_Exception() throws IOException {
        PutDataObjectRequest putRequest = new PutDataObjectRequest.Builder().index(TEST_INDEX).dataObject(testDataObject).build();

        ArgumentCaptor<IndexRequest<?>> indexRequestCaptor = ArgumentCaptor.forClass(IndexRequest.class);
        when(mockedOpenSearchClient.index(indexRequestCaptor.capture())).thenThrow(new IOException("test"));

        OpenSearchException ose = assertThrows(OpenSearchException.class, () -> sdkClient.putDataObject(putRequest));
        assertEquals(IOException.class, ose.getCause().getClass());
    }

    public void testPutDataObject_InnerException() throws IOException {
        PutDataObjectRequest putRequest = new PutDataObjectRequest.Builder().index(TEST_INDEX).dataObject(testDataObject).build();

        ArgumentCaptor<IndexRequest<?>> indexRequestCaptor = ArgumentCaptor.forClass(IndexRequest.class);
        when(mockedOpenSearchClient.index(indexRequestCaptor.capture())).thenReturn(null);

        OpenSearchException ose = assertThrows(OpenSearchException.class, () -> sdkClient.putDataObject(putRequest));
        assertEquals(NullPointerException.class, ose.getCause().getClass());
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void testGetDataObject() throws IOException {
        GetDataObjectRequest getRequest = new GetDataObjectRequest.Builder().index(TEST_INDEX).id(TEST_ID).build();

        GetResponse<?> getResponse = new GetResponse.Builder<>()
            .index(TEST_INDEX)
            .fields(Map.of("_source", JsonData.of(Map.of("data", "foo"), new JacksonJsonpMapper(new ObjectMapper()))))
            .found(true)
            .id(TEST_ID)
            .build();

        ArgumentCaptor<GetRequest> getRequestCaptor = ArgumentCaptor.forClass(GetRequest.class);
        ArgumentCaptor<Class<Map>> mapClassCaptor = ArgumentCaptor.forClass(Class.class);
        when(mockedOpenSearchClient.get(getRequestCaptor.capture(), mapClassCaptor.capture())).thenReturn((GetResponse<Map>) getResponse);

        GetDataObjectResponse response = sdkClient.getDataObject(getRequest);

        assertEquals(TEST_INDEX, getRequestCaptor.getValue().index());
        assertEquals(TEST_ID, response.id());
        XContentParser parser = response.parser();
        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.nextToken(), parser);
        assertEquals("foo", TestDataObject.parse(parser).data());
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void testGetDataObject_Exception() throws IOException {
        GetDataObjectRequest getRequest = new GetDataObjectRequest.Builder().index(TEST_INDEX).id(TEST_ID).build();

        ArgumentCaptor<GetRequest> getRequestCaptor = ArgumentCaptor.forClass(GetRequest.class);
        ArgumentCaptor<Class<Map>> mapClassCaptor = ArgumentCaptor.forClass(Class.class);
        when(mockedOpenSearchClient.get(getRequestCaptor.capture(), mapClassCaptor.capture())).thenThrow(new IOException("test"));

        OpenSearchException ose = assertThrows(OpenSearchException.class, () -> sdkClient.getDataObject(getRequest));
        assertEquals(IOException.class, ose.getCause().getClass());
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void testGetDataObject_InnerException() throws IOException {
        GetDataObjectRequest getRequest = new GetDataObjectRequest.Builder().index(TEST_INDEX).id(TEST_ID).build();

        ArgumentCaptor<GetRequest> getRequestCaptor = ArgumentCaptor.forClass(GetRequest.class);
        ArgumentCaptor<Class<Map>> mapClassCaptor = ArgumentCaptor.forClass(Class.class);
        when(mockedOpenSearchClient.get(getRequestCaptor.capture(), mapClassCaptor.capture())).thenReturn(null);

        OpenSearchException ose = assertThrows(OpenSearchException.class, () -> sdkClient.getDataObject(getRequest));
        assertEquals(NullPointerException.class, ose.getCause().getClass());
    }

    public void testDeleteDataObject() throws IOException {
        DeleteDataObjectRequest deleteRequest = new DeleteDataObjectRequest.Builder().index(TEST_INDEX).id(TEST_ID).build();

        DeleteResponse deleteResponse = new DeleteResponse.Builder()
            .id(TEST_ID)
            .index(TEST_INDEX)
            .primaryTerm(0)
            .result(Result.Deleted)
            .seqNo(0)
            .shards(new ShardStatistics.Builder().failed(0).successful(1).total(1).build())
            .version(0)
            .build();

        ArgumentCaptor<DeleteRequest> deleteRequestCaptor = ArgumentCaptor.forClass(DeleteRequest.class);
        when(mockedOpenSearchClient.delete(deleteRequestCaptor.capture())).thenReturn(deleteResponse);

        DeleteDataObjectResponse response = sdkClient.deleteDataObject(deleteRequest);

        assertEquals(TEST_INDEX, deleteRequestCaptor.getValue().index());
        assertEquals(TEST_ID, response.id());
    }

    public void testDeleteDataObject_Exception() throws IOException {
        DeleteDataObjectRequest deleteRequest = new DeleteDataObjectRequest.Builder().index(TEST_INDEX).id(TEST_ID).build();

        ArgumentCaptor<DeleteRequest> deleteRequestCaptor = ArgumentCaptor.forClass(DeleteRequest.class);
        when(mockedOpenSearchClient.delete(deleteRequestCaptor.capture())).thenThrow(new IOException("test"));

        OpenSearchException ose = assertThrows(OpenSearchException.class, () -> sdkClient.deleteDataObject(deleteRequest));
        assertEquals(IOException.class, ose.getCause().getClass());
    }

    public void testDeleteDataObject_InnerException() throws IOException {
        DeleteDataObjectRequest deleteRequest = new DeleteDataObjectRequest.Builder().index(TEST_INDEX).id(TEST_ID).build();

        ArgumentCaptor<DeleteRequest> deleteRequestCaptor = ArgumentCaptor.forClass(DeleteRequest.class);
        when(mockedOpenSearchClient.delete(deleteRequestCaptor.capture())).thenReturn(null);

        OpenSearchException ose = assertThrows(OpenSearchException.class, () -> sdkClient.deleteDataObject(deleteRequest));
        assertEquals(NullPointerException.class, ose.getCause().getClass());
    }
}
