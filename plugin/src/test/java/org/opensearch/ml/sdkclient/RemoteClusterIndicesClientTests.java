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
import static org.opensearch.ml.plugin.MachineLearningPlugin.GENERAL_THREAD_POOL;
import static org.opensearch.ml.plugin.MachineLearningPlugin.ML_THREAD_POOL_PREFIX;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;

import org.junit.AfterClass;
import org.junit.Before;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.OpenSearchStatusException;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.Result;
import org.opensearch.client.opensearch._types.ShardStatistics;
import org.opensearch.client.opensearch.core.DeleteRequest;
import org.opensearch.client.opensearch.core.DeleteResponse;
import org.opensearch.client.opensearch.core.GetRequest;
import org.opensearch.client.opensearch.core.GetResponse;
import org.opensearch.client.opensearch.core.IndexRequest;
import org.opensearch.client.opensearch.core.IndexResponse;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.unit.TimeValue;
import org.opensearch.common.util.concurrent.OpenSearchExecutors;
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

public class RemoteClusterIndicesClientTests extends OpenSearchTestCase {

    private static final String TEST_ID = "123";
    private static final String TEST_INDEX = "test_index";

    private static TestThreadPool testThreadPool = new TestThreadPool(
        RemoteClusterIndicesClientTests.class.getName(),
        new ScalingExecutorBuilder(
            GENERAL_THREAD_POOL,
            1,
            Math.max(1, OpenSearchExecutors.allocatedProcessors(Settings.EMPTY) - 1),
            TimeValue.timeValueMinutes(1),
            ML_THREAD_POOL_PREFIX + GENERAL_THREAD_POOL
        )
    );

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

    @AfterClass
    public static void cleanup() {
        ThreadPool.terminate(testThreadPool, 500, TimeUnit.MILLISECONDS);
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

        PutDataObjectResponse response = sdkClient
            .putDataObjectAsync(putRequest, testThreadPool.executor(GENERAL_THREAD_POOL))
            .toCompletableFuture()
            .join();

        assertEquals(TEST_INDEX, indexRequestCaptor.getValue().index());
        assertEquals(TEST_ID, response.id());
        assertTrue(response.created());
    }

    public void testPutDataObject_Updated() throws IOException {
        PutDataObjectRequest putRequest = new PutDataObjectRequest.Builder().index(TEST_INDEX).dataObject(testDataObject).build();

        IndexResponse indexResponse = new IndexResponse.Builder()
            .id(TEST_ID)
            .index(TEST_INDEX)
            .primaryTerm(0)
            .result(Result.Updated)
            .seqNo(0)
            .shards(new ShardStatistics.Builder().failed(0).successful(1).total(1).build())
            .version(0)
            .build();

        ArgumentCaptor<IndexRequest<?>> indexRequestCaptor = ArgumentCaptor.forClass(IndexRequest.class);
        when(mockedOpenSearchClient.index(indexRequestCaptor.capture())).thenReturn(indexResponse);

        PutDataObjectResponse response = sdkClient
            .putDataObjectAsync(putRequest, testThreadPool.executor(GENERAL_THREAD_POOL))
            .toCompletableFuture()
            .join();

        assertEquals(TEST_INDEX, indexRequestCaptor.getValue().index());
        assertEquals(TEST_ID, response.id());
        assertFalse(response.created());
    }

    public void testPutDataObject_Exception() throws IOException {
        PutDataObjectRequest putRequest = new PutDataObjectRequest.Builder().index(TEST_INDEX).dataObject(testDataObject).build();

        ArgumentCaptor<IndexRequest<?>> indexRequestCaptor = ArgumentCaptor.forClass(IndexRequest.class);
        when(mockedOpenSearchClient.index(indexRequestCaptor.capture())).thenThrow(new IOException("test"));

        CompletableFuture<PutDataObjectResponse> future = sdkClient
            .putDataObjectAsync(putRequest, testThreadPool.executor(GENERAL_THREAD_POOL))
            .toCompletableFuture();

        CompletionException ce = assertThrows(CompletionException.class, () -> future.join());
        assertEquals(OpenSearchStatusException.class, ce.getCause().getClass());
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void testGetDataObject() throws IOException {
        GetDataObjectRequest getRequest = new GetDataObjectRequest.Builder().index(TEST_INDEX).id(TEST_ID).build();

        GetResponse<?> getResponse = new GetResponse.Builder<>()
            .index(TEST_INDEX)
            .id(TEST_ID)
            .found(true)
            .source(Map.of("data", "foo"))
            .build();

        ArgumentCaptor<GetRequest> getRequestCaptor = ArgumentCaptor.forClass(GetRequest.class);
        ArgumentCaptor<Class<Map>> mapClassCaptor = ArgumentCaptor.forClass(Class.class);
        when(mockedOpenSearchClient.get(getRequestCaptor.capture(), mapClassCaptor.capture())).thenReturn((GetResponse<Map>) getResponse);

        GetDataObjectResponse response = sdkClient
            .getDataObjectAsync(getRequest, testThreadPool.executor(GENERAL_THREAD_POOL))
            .toCompletableFuture()
            .join();

        assertEquals(TEST_INDEX, getRequestCaptor.getValue().index());
        assertEquals(TEST_ID, response.id());
        assertEquals("foo", response.source().get("data"));
        assertTrue(response.parser().isPresent());
        XContentParser parser = response.parser().get();
        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.nextToken(), parser);
        assertEquals("foo", TestDataObject.parse(parser).data());
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void testGetDataObject_NotFound() throws IOException {
        GetDataObjectRequest getRequest = new GetDataObjectRequest.Builder().index(TEST_INDEX).id(TEST_ID).build();

        GetResponse<?> getResponse = new GetResponse.Builder<>().index(TEST_INDEX).id(TEST_ID).found(false).build();

        ArgumentCaptor<GetRequest> getRequestCaptor = ArgumentCaptor.forClass(GetRequest.class);
        ArgumentCaptor<Class<Map>> mapClassCaptor = ArgumentCaptor.forClass(Class.class);
        when(mockedOpenSearchClient.get(getRequestCaptor.capture(), mapClassCaptor.capture())).thenReturn((GetResponse<Map>) getResponse);

        GetDataObjectResponse response = sdkClient
            .getDataObjectAsync(getRequest, testThreadPool.executor(GENERAL_THREAD_POOL))
            .toCompletableFuture()
            .join();

        assertEquals(TEST_INDEX, getRequestCaptor.getValue().index());
        assertEquals(TEST_ID, response.id());
        assertFalse(response.parser().isPresent());
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void testGetDataObject_Exception() throws IOException {
        GetDataObjectRequest getRequest = new GetDataObjectRequest.Builder().index(TEST_INDEX).id(TEST_ID).build();

        ArgumentCaptor<GetRequest> getRequestCaptor = ArgumentCaptor.forClass(GetRequest.class);
        ArgumentCaptor<Class<Map>> mapClassCaptor = ArgumentCaptor.forClass(Class.class);
        when(mockedOpenSearchClient.get(getRequestCaptor.capture(), mapClassCaptor.capture())).thenThrow(new IOException("test"));

        CompletableFuture<GetDataObjectResponse> future = sdkClient
            .getDataObjectAsync(getRequest, testThreadPool.executor(GENERAL_THREAD_POOL))
            .toCompletableFuture();

        CompletionException ce = assertThrows(CompletionException.class, () -> future.join());
        assertEquals(OpenSearchStatusException.class, ce.getCause().getClass());
    }

    public void testDeleteDataObject() throws IOException {
        DeleteDataObjectRequest deleteRequest = new DeleteDataObjectRequest.Builder().index(TEST_INDEX).id(TEST_ID).build();

        DeleteResponse deleteResponse = new DeleteResponse.Builder()
            .id(TEST_ID)
            .index(TEST_INDEX)
            .primaryTerm(0)
            .result(Result.Deleted)
            .seqNo(0)
            .shards(new ShardStatistics.Builder().failed(0).successful(2).total(2).build())
            .version(0)
            .build();

        ArgumentCaptor<DeleteRequest> deleteRequestCaptor = ArgumentCaptor.forClass(DeleteRequest.class);
        when(mockedOpenSearchClient.delete(deleteRequestCaptor.capture())).thenReturn(deleteResponse);

        DeleteDataObjectResponse response = sdkClient
            .deleteDataObjectAsync(deleteRequest, testThreadPool.executor(GENERAL_THREAD_POOL))
            .toCompletableFuture()
            .join();

        assertEquals(TEST_INDEX, deleteRequestCaptor.getValue().index());
        assertEquals(TEST_ID, response.id());
        assertEquals(2, response.shardInfo().getTotal());
        assertEquals(2, response.shardInfo().getSuccessful());
        assertEquals(0, response.shardInfo().getFailed());
        assertTrue(response.deleted());
    }

    public void testDeleteDataObject_NotFound() throws IOException {
        DeleteDataObjectRequest deleteRequest = new DeleteDataObjectRequest.Builder().index(TEST_INDEX).id(TEST_ID).build();

        DeleteResponse deleteResponse = new DeleteResponse.Builder()
            .id(TEST_ID)
            .index(TEST_INDEX)
            .primaryTerm(0)
            .result(Result.NotFound)
            .seqNo(0)
            .shards(new ShardStatistics.Builder().failed(0).successful(2).total(2).build())
            .version(0)
            .build();

        ArgumentCaptor<DeleteRequest> deleteRequestCaptor = ArgumentCaptor.forClass(DeleteRequest.class);
        when(mockedOpenSearchClient.delete(deleteRequestCaptor.capture())).thenReturn(deleteResponse);

        DeleteDataObjectResponse response = sdkClient
            .deleteDataObjectAsync(deleteRequest, testThreadPool.executor(GENERAL_THREAD_POOL))
            .toCompletableFuture()
            .join();

        assertEquals(TEST_INDEX, deleteRequestCaptor.getValue().index());
        assertEquals(TEST_ID, response.id());
        assertEquals(2, response.shardInfo().getTotal());
        assertEquals(2, response.shardInfo().getSuccessful());
        assertEquals(0, response.shardInfo().getFailed());
        assertFalse(response.deleted());
    }

    public void testDeleteDataObject_Exception() throws IOException {
        DeleteDataObjectRequest deleteRequest = new DeleteDataObjectRequest.Builder().index(TEST_INDEX).id(TEST_ID).build();

        ArgumentCaptor<DeleteRequest> deleteRequestCaptor = ArgumentCaptor.forClass(DeleteRequest.class);
        when(mockedOpenSearchClient.delete(deleteRequestCaptor.capture())).thenThrow(new IOException("test"));

        CompletableFuture<DeleteDataObjectResponse> future = sdkClient
            .deleteDataObjectAsync(deleteRequest, testThreadPool.executor(GENERAL_THREAD_POOL))
            .toCompletableFuture();

        CompletionException ce = assertThrows(CompletionException.class, () -> future.join());
        assertEquals(OpenSearchStatusException.class, ce.getCause().getClass());
    }
}
