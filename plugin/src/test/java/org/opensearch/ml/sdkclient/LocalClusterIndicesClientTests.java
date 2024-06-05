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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;
import static org.opensearch.ml.plugin.MachineLearningPlugin.GENERAL_THREAD_POOL;
import static org.opensearch.ml.plugin.MachineLearningPlugin.ML_THREAD_POOL_PREFIX;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;

import org.junit.AfterClass;
import org.junit.Before;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.action.DocWriteResponse;
import org.opensearch.action.delete.DeleteRequest;
import org.opensearch.action.delete.DeleteResponse;
import org.opensearch.action.get.GetRequest;
import org.opensearch.action.get.GetResponse;
import org.opensearch.action.index.IndexRequest;
import org.opensearch.action.index.IndexResponse;
import org.opensearch.action.support.PlainActionFuture;
import org.opensearch.action.support.replication.ReplicationResponse.ShardInfo;
import org.opensearch.client.Client;
import org.opensearch.common.action.ActionFuture;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.unit.TimeValue;
import org.opensearch.common.util.concurrent.OpenSearchExecutors;
import org.opensearch.common.xcontent.XContentHelper;
import org.opensearch.common.xcontent.json.JsonXContent;
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
import org.opensearch.threadpool.ScalingExecutorBuilder;
import org.opensearch.threadpool.TestThreadPool;
import org.opensearch.threadpool.ThreadPool;

public class LocalClusterIndicesClientTests extends OpenSearchTestCase {

    private static final String TEST_ID = "123";
    private static final String TEST_INDEX = "test_index";

    private static TestThreadPool testThreadPool = new TestThreadPool(
        LocalClusterIndicesClientTests.class.getName(),
        new ScalingExecutorBuilder(
            GENERAL_THREAD_POOL,
            1,
            Math.max(1, OpenSearchExecutors.allocatedProcessors(Settings.EMPTY) - 1),
            TimeValue.timeValueMinutes(1),
            ML_THREAD_POOL_PREFIX + GENERAL_THREAD_POOL
        )
    );

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

    @AfterClass
    public static void cleanup() {
        ThreadPool.terminate(testThreadPool, 500, TimeUnit.MILLISECONDS);
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

        PutDataObjectResponse response = sdkClient
            .putDataObjectAsync(putRequest, testThreadPool.executor(GENERAL_THREAD_POOL))
            .toCompletableFuture()
            .join();

        ArgumentCaptor<IndexRequest> requestCaptor = ArgumentCaptor.forClass(IndexRequest.class);
        verify(mockedClient, times(1)).index(requestCaptor.capture());
        assertEquals(TEST_INDEX, requestCaptor.getValue().index());
        assertEquals(TEST_ID, response.id());
        assertTrue(response.created());
    }

    public void testPutDataObject_Exception() throws IOException {
        PutDataObjectRequest putRequest = new PutDataObjectRequest.Builder().index(TEST_INDEX).dataObject(testDataObject).build();

        ArgumentCaptor<IndexRequest> indexRequestCaptor = ArgumentCaptor.forClass(IndexRequest.class);
        when(mockedClient.index(indexRequestCaptor.capture())).thenThrow(new UnsupportedOperationException("test"));

        CompletableFuture<PutDataObjectResponse> future = sdkClient
            .putDataObjectAsync(putRequest, testThreadPool.executor(GENERAL_THREAD_POOL))
            .toCompletableFuture();

        CompletionException ce = assertThrows(CompletionException.class, () -> future.join());
        Throwable cause = ce.getCause();
        assertEquals(UnsupportedOperationException.class, cause.getClass());
        assertEquals("test", cause.getMessage());
    }

    public void testGetDataObject() throws IOException {
        GetDataObjectRequest getRequest = new GetDataObjectRequest.Builder().index(TEST_INDEX).id(TEST_ID).build();

        GetResponse getResponse = mock(GetResponse.class);
        when(getResponse.isExists()).thenReturn(true);
        when(getResponse.getId()).thenReturn(TEST_ID);
        String json = testDataObject.toJson();
        when(getResponse.getSourceAsString()).thenReturn(json);
        when(getResponse.getSource()).thenReturn(XContentHelper.convertToMap(JsonXContent.jsonXContent, json, false));
        @SuppressWarnings("unchecked")
        ActionFuture<GetResponse> future = mock(ActionFuture.class);
        when(mockedClient.get(any(GetRequest.class))).thenReturn(future);
        when(future.actionGet()).thenReturn(getResponse);

        GetDataObjectResponse response = sdkClient
            .getDataObjectAsync(getRequest, testThreadPool.executor(GENERAL_THREAD_POOL))
            .toCompletableFuture()
            .join();

        ArgumentCaptor<GetRequest> requestCaptor = ArgumentCaptor.forClass(GetRequest.class);
        verify(mockedClient, times(1)).get(requestCaptor.capture());
        assertEquals(TEST_INDEX, requestCaptor.getValue().index());
        assertEquals(TEST_ID, response.id());
        assertEquals("foo", response.source().get("data"));
        assertTrue(response.parser().isPresent());
        XContentParser parser = response.parser().get();
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

        GetDataObjectResponse response = sdkClient
            .getDataObjectAsync(getRequest, testThreadPool.executor(GENERAL_THREAD_POOL))
            .toCompletableFuture()
            .join();

        ArgumentCaptor<GetRequest> requestCaptor = ArgumentCaptor.forClass(GetRequest.class);
        verify(mockedClient, times(1)).get(requestCaptor.capture());
        assertEquals(TEST_INDEX, requestCaptor.getValue().index());
        assertEquals(TEST_ID, response.id());
        assertFalse(response.parser().isPresent());
    }

    public void testGetDataObject_Exception() throws IOException {
        GetDataObjectRequest getRequest = new GetDataObjectRequest.Builder().index(TEST_INDEX).id(TEST_ID).build();

        ArgumentCaptor<GetRequest> getRequestCaptor = ArgumentCaptor.forClass(GetRequest.class);
        when(mockedClient.get(getRequestCaptor.capture())).thenThrow(new UnsupportedOperationException("test"));

        CompletableFuture<GetDataObjectResponse> future = sdkClient
            .getDataObjectAsync(getRequest, testThreadPool.executor(GENERAL_THREAD_POOL))
            .toCompletableFuture();

        CompletionException ce = assertThrows(CompletionException.class, () -> future.join());
        Throwable cause = ce.getCause();
        assertEquals(UnsupportedOperationException.class, cause.getClass());
        assertEquals("test", cause.getMessage());
    }

    public void testDeleteDataObject() throws IOException {
        DeleteDataObjectRequest deleteRequest = new DeleteDataObjectRequest.Builder().index(TEST_INDEX).id(TEST_ID).build();

        DeleteResponse deleteResponse = mock(DeleteResponse.class);
        when(deleteResponse.getId()).thenReturn(TEST_ID);
        when(deleteResponse.getResult()).thenReturn(DocWriteResponse.Result.DELETED);
        when(deleteResponse.getShardInfo()).thenReturn(new ShardInfo(2, 2));
        PlainActionFuture<DeleteResponse> future = PlainActionFuture.newFuture();
        future.onResponse(deleteResponse);
        when(mockedClient.delete(any(DeleteRequest.class))).thenReturn(future);

        DeleteDataObjectResponse response = sdkClient
            .deleteDataObjectAsync(deleteRequest, testThreadPool.executor(GENERAL_THREAD_POOL))
            .toCompletableFuture()
            .join();

        ArgumentCaptor<DeleteRequest> requestCaptor = ArgumentCaptor.forClass(DeleteRequest.class);
        verify(mockedClient, times(1)).delete(requestCaptor.capture());
        assertEquals(TEST_INDEX, requestCaptor.getValue().index());
        assertEquals(TEST_ID, response.id());
        assertEquals(2, response.shardInfo().getTotal());
        assertEquals(2, response.shardInfo().getSuccessful());
        assertEquals(0, response.shardInfo().getFailed());
    }

    public void testDeleteDataObject_Exception() throws IOException {
        DeleteDataObjectRequest deleteRequest = new DeleteDataObjectRequest.Builder().index(TEST_INDEX).id(TEST_ID).build();

        ArgumentCaptor<DeleteRequest> deleteRequestCaptor = ArgumentCaptor.forClass(DeleteRequest.class);
        when(mockedClient.delete(deleteRequestCaptor.capture())).thenThrow(new UnsupportedOperationException("test"));

        CompletableFuture<DeleteDataObjectResponse> future = sdkClient
            .deleteDataObjectAsync(deleteRequest, testThreadPool.executor(GENERAL_THREAD_POOL))
            .toCompletableFuture();

        CompletionException ce = assertThrows(CompletionException.class, () -> future.join());
        Throwable cause = ce.getCause();
        assertEquals(UnsupportedOperationException.class, cause.getClass());
        assertEquals("test", cause.getMessage());
    }
}
