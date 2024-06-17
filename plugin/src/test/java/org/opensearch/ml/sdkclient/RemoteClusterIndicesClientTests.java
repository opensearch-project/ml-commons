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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;
import static org.opensearch.ml.plugin.MachineLearningPlugin.GENERAL_THREAD_POOL;
import static org.opensearch.ml.plugin.MachineLearningPlugin.ML_THREAD_POOL_PREFIX;

import java.io.IOException;
import java.util.Collections;
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
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.opensearch.client.opensearch.core.UpdateRequest;
import org.opensearch.client.opensearch.core.UpdateResponse;
import org.opensearch.client.opensearch.core.search.HitsMetadata;
import org.opensearch.client.opensearch.core.search.TotalHits;
import org.opensearch.client.opensearch.core.search.TotalHitsRelation;
import org.opensearch.client.transport.OpenSearchTransport;
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
import org.opensearch.sdk.SearchDataObjectRequest;
import org.opensearch.sdk.SearchDataObjectResponse;
import org.opensearch.sdk.UpdateDataObjectRequest;
import org.opensearch.sdk.UpdateDataObjectResponse;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.threadpool.ScalingExecutorBuilder;
import org.opensearch.threadpool.TestThreadPool;
import org.opensearch.threadpool.ThreadPool;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;

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

    @Mock
    private OpenSearchTransport transport;

    private TestDataObject testDataObject;

    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);

        when(mockedOpenSearchClient._transport()).thenReturn(transport);
        when(transport.jsonpMapper())
            .thenReturn(
                new JacksonJsonpMapper(
                    new ObjectMapper()
                        .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                        .setSerializationInclusion(JsonInclude.Include.NON_NULL)
                )
            );

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

    public void testUpdateDataObject() throws IOException {
        UpdateDataObjectRequest updateRequest = new UpdateDataObjectRequest.Builder()
            .index(TEST_INDEX)
            .id(TEST_ID)
            .dataObject(testDataObject)
            .build();

        UpdateResponse<Map<String, Object>> updateResponse = new UpdateResponse.Builder<Map<String, Object>>()
            .id(TEST_ID)
            .index(TEST_INDEX)
            .primaryTerm(0)
            .result(Result.Updated)
            .seqNo(0)
            .shards(new ShardStatistics.Builder().failed(0).successful(1).total(1).build())
            .version(0)
            .build();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<UpdateRequest<Map<String, Object>, ?>> updateRequestCaptor = ArgumentCaptor.forClass(UpdateRequest.class);
        when(mockedOpenSearchClient.update(updateRequestCaptor.capture(), any())).thenReturn(updateResponse);

        UpdateDataObjectResponse response = sdkClient
            .updateDataObjectAsync(updateRequest, testThreadPool.executor(GENERAL_THREAD_POOL))
            .toCompletableFuture()
            .join();

        assertEquals(TEST_INDEX, updateRequestCaptor.getValue().index());
        assertEquals(TEST_ID, response.id());
        assertTrue(response.updated());
    }

    public void testUpdateDataObject_NotFound() throws IOException {
        UpdateDataObjectRequest updateRequest = new UpdateDataObjectRequest.Builder()
            .index(TEST_INDEX)
            .id(TEST_ID)
            .dataObject(testDataObject)
            .build();

        UpdateResponse<Map<String, Object>> updateResponse = new UpdateResponse.Builder<Map<String, Object>>()
            .id(TEST_ID)
            .index(TEST_INDEX)
            .primaryTerm(0)
            .result(Result.Created)
            .seqNo(0)
            .shards(new ShardStatistics.Builder().failed(0).successful(1).total(1).build())
            .version(0)
            .build();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<UpdateRequest<Map<String, Object>, ?>> updateRequestCaptor = ArgumentCaptor.forClass(UpdateRequest.class);
        when(mockedOpenSearchClient.update(updateRequestCaptor.capture(), any())).thenReturn(updateResponse);

        UpdateDataObjectResponse response = sdkClient
            .updateDataObjectAsync(updateRequest, testThreadPool.executor(GENERAL_THREAD_POOL))
            .toCompletableFuture()
            .join();

        assertEquals(TEST_INDEX, updateRequestCaptor.getValue().index());
        assertEquals(TEST_ID, response.id());
        assertFalse(response.updated());
    }

    public void testtUpdateDataObject_Exception() throws IOException {
        UpdateDataObjectRequest updateRequest = new UpdateDataObjectRequest.Builder()
            .index(TEST_INDEX)
            .id(TEST_ID)
            .dataObject(testDataObject)
            .build();

        ArgumentCaptor<UpdateRequest<?, ?>> updateRequestCaptor = ArgumentCaptor.forClass(UpdateRequest.class);
        when(mockedOpenSearchClient.update(updateRequestCaptor.capture(), any())).thenThrow(new IOException("test"));

        CompletableFuture<UpdateDataObjectResponse> future = sdkClient
            .updateDataObjectAsync(updateRequest, testThreadPool.executor(GENERAL_THREAD_POOL))
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

    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void testSearchDataObject() throws IOException {
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        SearchDataObjectRequest searchRequest = new SearchDataObjectRequest.Builder()
            .indices(TEST_INDEX)
            .searchSourceBuilder(searchSourceBuilder)
            .build();

        TotalHits totalHits = new TotalHits.Builder().value(0).relation(TotalHitsRelation.Eq).build();
        HitsMetadata<Object> hits = new HitsMetadata.Builder<>().hits(Collections.emptyList()).total(totalHits).build();
        ShardStatistics shards = new ShardStatistics.Builder().failed(0).successful(1).total(1).build();
        SearchResponse<?> searchResponse = new SearchResponse.Builder<>().hits(hits).took(1).timedOut(false).shards(shards).build();

        ArgumentCaptor<SearchRequest> getRequestCaptor = ArgumentCaptor.forClass(SearchRequest.class);
        ArgumentCaptor<Class<Map>> mapClassCaptor = ArgumentCaptor.forClass(Class.class);
        when(mockedOpenSearchClient.search(getRequestCaptor.capture(), mapClassCaptor.capture()))
            .thenReturn((SearchResponse<Map>) searchResponse);

        SearchDataObjectResponse response = sdkClient
            .searchDataObjectAsync(searchRequest, testThreadPool.executor(GENERAL_THREAD_POOL))
            .toCompletableFuture()
            .join();

        ArgumentCaptor<SearchRequest> requestCaptor = ArgumentCaptor.forClass(SearchRequest.class);
        verify(mockedOpenSearchClient, times(1)).search(requestCaptor.capture(), any());
        assertEquals(1, requestCaptor.getValue().index().size());
        assertEquals(TEST_INDEX, requestCaptor.getValue().index().get(0));

        org.opensearch.action.search.SearchResponse searchActionResponse = org.opensearch.action.search.SearchResponse
            .fromXContent(response.parser());
        assertEquals(TimeValue.timeValueMillis(1), searchActionResponse.getTook());
        assertFalse(searchActionResponse.isTimedOut());
        assertEquals(0, searchActionResponse.getFailedShards());
        assertEquals(1, searchActionResponse.getSuccessfulShards());
        assertEquals(1, searchActionResponse.getTotalShards());
    }

    public void testSearchDataObject_Exception() throws IOException {
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        SearchDataObjectRequest searchRequest = new SearchDataObjectRequest.Builder()
            .indices(TEST_INDEX)
            .searchSourceBuilder(searchSourceBuilder)
            .build();

        ArgumentCaptor<SearchRequest> searchRequestCaptor = ArgumentCaptor.forClass(SearchRequest.class);
        when(mockedOpenSearchClient.search(searchRequestCaptor.capture(), any())).thenThrow(new UnsupportedOperationException("test"));
        CompletableFuture<SearchDataObjectResponse> future = sdkClient
            .searchDataObjectAsync(searchRequest, testThreadPool.executor(GENERAL_THREAD_POOL))
            .toCompletableFuture();

        CompletionException ce = assertThrows(CompletionException.class, () -> future.join());
        Throwable cause = ce.getCause();
        assertEquals(UnsupportedOperationException.class, cause.getClass());
        assertEquals("test", cause.getMessage());
    }
}
