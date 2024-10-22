/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.sdk.client;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;

import java.io.IOException;
import java.util.EnumSet;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.OpenSearchStatusException;
import org.opensearch.action.DocWriteRequest.OpType;
import org.opensearch.action.DocWriteResponse;
import org.opensearch.action.DocWriteResponse.Result;
import org.opensearch.action.delete.DeleteRequest;
import org.opensearch.action.delete.DeleteResponse;
import org.opensearch.action.get.GetRequest;
import org.opensearch.action.get.GetResponse;
import org.opensearch.action.index.IndexRequest;
import org.opensearch.action.index.IndexResponse;
import org.opensearch.action.search.SearchPhaseName;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.action.search.ShardSearchFailure;
import org.opensearch.action.support.PlainActionFuture;
import org.opensearch.action.support.replication.ReplicationResponse.ShardInfo;
import org.opensearch.action.update.UpdateRequest;
import org.opensearch.action.update.UpdateResponse;
import org.opensearch.client.Client;
import org.opensearch.common.action.ActionFuture;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.unit.TimeValue;
import org.opensearch.common.util.concurrent.OpenSearchExecutors;
import org.opensearch.common.xcontent.LoggingDeprecationHandler;
import org.opensearch.common.xcontent.XContentHelper;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.common.bytes.BytesArray;
import org.opensearch.core.index.shard.ShardId;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.index.engine.VersionConflictEngineException;
import org.opensearch.index.get.GetResult;
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
import org.opensearch.search.internal.InternalSearchResponse;
import org.opensearch.threadpool.ScalingExecutorBuilder;
import org.opensearch.threadpool.TestThreadPool;
import org.opensearch.threadpool.ThreadPool;

public class LocalClusterIndicesClientTests {

    // Copied constants from MachineLearningPlugin.java
    private static final String ML_THREAD_POOL_PREFIX = "thread_pool.ml_commons.";
    private static final String GENERAL_THREAD_POOL = "opensearch_ml_general";

    private static final String TEST_ID = "123";
    private static final String TEST_INDEX = "test_index";
    private static final String TEST_TENANT_ID = "xyz";

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

        sdkClient = new SdkClient(new LocalClusterIndicesClient(mockedClient, xContentRegistry), true);

        testDataObject = new TestDataObject("foo");
    }

    @AfterClass
    public static void cleanup() {
        ThreadPool.terminate(testThreadPool, 500, TimeUnit.MILLISECONDS);
    }

    @Test
    public void testPutDataObject() throws IOException {
        PutDataObjectRequest putRequest = PutDataObjectRequest
            .builder()
            .index(TEST_INDEX)
            .id(TEST_ID).tenantId(TEST_TENANT_ID)
            .overwriteIfExists(false)
            .dataObject(testDataObject)
            .build();

        IndexResponse indexResponse = new IndexResponse(new ShardId(TEST_INDEX, "_na_", 0), TEST_ID, 1, 0, 2, true);
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
        assertEquals(TEST_ID, requestCaptor.getValue().id());
        assertEquals(OpType.CREATE, requestCaptor.getValue().opType());

        assertEquals(TEST_ID, response.id());

        IndexResponse indexActionResponse = IndexResponse.fromXContent(response.parser());
        assertEquals(TEST_ID, indexActionResponse.getId());
        assertEquals(DocWriteResponse.Result.CREATED, indexActionResponse.getResult());
    }

    @Test
    public void testPutDataObject_Exception() throws IOException {
        PutDataObjectRequest putRequest = PutDataObjectRequest.builder().index(TEST_INDEX).tenantId(TEST_TENANT_ID).dataObject(testDataObject).build();

        when(mockedClient.index(any(IndexRequest.class))).thenThrow(new UnsupportedOperationException("test"));

        CompletableFuture<PutDataObjectResponse> future = sdkClient
            .putDataObjectAsync(putRequest, testThreadPool.executor(GENERAL_THREAD_POOL))
            .toCompletableFuture();

        CompletionException ce = assertThrows(CompletionException.class, () -> future.join());
        Throwable cause = ce.getCause();
        assertEquals(UnsupportedOperationException.class, cause.getClass());
        assertEquals("test", cause.getMessage());
    }

    @Test
    public void testPutDataObject_IOException() throws IOException {
        ToXContentObject badDataObject = new ToXContentObject() {
            @Override
            public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
                throw new IOException("test");
            }
        };
        PutDataObjectRequest putRequest = PutDataObjectRequest.builder().index(TEST_INDEX).tenantId(TEST_TENANT_ID).dataObject(badDataObject).build();

        CompletableFuture<PutDataObjectResponse> future = sdkClient
            .putDataObjectAsync(putRequest, testThreadPool.executor(GENERAL_THREAD_POOL))
            .toCompletableFuture();

        CompletionException ce = assertThrows(CompletionException.class, () -> future.join());
        Throwable cause = ce.getCause();
        assertEquals(OpenSearchStatusException.class, cause.getClass());
        assertEquals(RestStatus.BAD_REQUEST, ((OpenSearchStatusException) cause).status());
    }

    @Test
    public void testGetDataObject() throws IOException {
        GetDataObjectRequest getRequest = GetDataObjectRequest.builder().index(TEST_INDEX).id(TEST_ID).tenantId(TEST_TENANT_ID).build();

        String json = testDataObject.toJson();
        GetResponse getResponse = new GetResponse(new GetResult(TEST_INDEX, TEST_ID, -2, 0, 1, true, new BytesArray(json), null, null));
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
        XContentParser parser = response.parser();
        XContentParser dataParser = XContentHelper
            .createParser(
                NamedXContentRegistry.EMPTY,
                LoggingDeprecationHandler.INSTANCE,
                GetResponse.fromXContent(parser).getSourceAsBytesRef(),
                XContentType.JSON
            );
        ensureExpectedToken(XContentParser.Token.START_OBJECT, dataParser.nextToken(), dataParser);
        assertEquals("foo", TestDataObject.parse(dataParser).data());
    }

    @Test
    public void testGetDataObject_NullResponse() throws IOException {
        GetDataObjectRequest getRequest = GetDataObjectRequest.builder().index(TEST_INDEX).id(TEST_ID).tenantId(TEST_TENANT_ID).build();

        @SuppressWarnings("unchecked")
        ActionFuture<GetResponse> future = mock(ActionFuture.class);
        when(mockedClient.get(any(GetRequest.class))).thenReturn(future);
        when(future.actionGet()).thenReturn(null);

        GetDataObjectResponse response = sdkClient
            .getDataObjectAsync(getRequest, testThreadPool.executor(GENERAL_THREAD_POOL))
            .toCompletableFuture()
            .join();

        ArgumentCaptor<GetRequest> requestCaptor = ArgumentCaptor.forClass(GetRequest.class);
        verify(mockedClient, times(1)).get(requestCaptor.capture());
        assertEquals(TEST_INDEX, requestCaptor.getValue().index());
        assertEquals(TEST_ID, response.id());
        assertNull(response.parser());
        assertTrue(response.source().isEmpty());
    }

    @Test
    public void testGetDataObject_NotFound() throws IOException {
        GetDataObjectRequest getRequest = GetDataObjectRequest.builder().index(TEST_INDEX).id(TEST_ID).tenantId(TEST_TENANT_ID).build();
        GetResponse getResponse = new GetResponse(new GetResult(TEST_INDEX, TEST_ID, -2, 0, 1, false, null, null, null));

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
        assertTrue(response.source().isEmpty());
        assertFalse(GetResponse.fromXContent(response.parser()).isExists());
    }

    @Test
    public void testGetDataObject_Exception() throws IOException {
        GetDataObjectRequest getRequest = GetDataObjectRequest.builder().index(TEST_INDEX).id(TEST_ID).tenantId(TEST_TENANT_ID).build();

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

    @Test
    public void testUpdateDataObject() throws IOException {
        UpdateDataObjectRequest updateRequest = UpdateDataObjectRequest
            .builder()
            .index(TEST_INDEX)
            .id(TEST_ID).tenantId(TEST_TENANT_ID)
            .retryOnConflict(3)
            .dataObject(testDataObject)
            .build();

        UpdateResponse updateResponse = new UpdateResponse(
            new ShardInfo(1, 1),
            new ShardId(TEST_INDEX, "_na_", 0),
            TEST_ID,
            1,
            0,
            2,
            Result.UPDATED
        );

        @SuppressWarnings("unchecked")
        ActionFuture<UpdateResponse> future = mock(ActionFuture.class);
        when(mockedClient.update(any(UpdateRequest.class))).thenReturn(future);
        when(future.actionGet()).thenReturn(updateResponse);

        UpdateDataObjectResponse response = sdkClient
            .updateDataObjectAsync(updateRequest, testThreadPool.executor(GENERAL_THREAD_POOL))
            .toCompletableFuture()
            .join();

        ArgumentCaptor<UpdateRequest> requestCaptor = ArgumentCaptor.forClass(UpdateRequest.class);
        verify(mockedClient, times(1)).update(requestCaptor.capture());
        assertEquals(TEST_INDEX, requestCaptor.getValue().index());
        assertEquals(3, requestCaptor.getValue().retryOnConflict());
        assertEquals(TEST_ID, response.id());

        UpdateResponse updateActionResponse = UpdateResponse.fromXContent(response.parser());
        assertEquals(TEST_ID, updateActionResponse.getId());
        assertEquals(DocWriteResponse.Result.UPDATED, updateActionResponse.getResult());
        assertEquals(0, updateActionResponse.getShardInfo().getFailed());
        assertEquals(1, updateActionResponse.getShardInfo().getSuccessful());
        assertEquals(1, updateActionResponse.getShardInfo().getTotal());
    }

    @Test
    public void testUpdateDataObjectWithMap() throws IOException {
        UpdateDataObjectRequest updateRequest = UpdateDataObjectRequest
            .builder()
            .index(TEST_INDEX)
            .id(TEST_ID).tenantId(TEST_TENANT_ID)
            .dataObject(Map.of("foo", "bar"))
            .build();

        UpdateResponse updateResponse = new UpdateResponse(
            new ShardInfo(1, 1),
            new ShardId(TEST_INDEX, "_na_", 0),
            TEST_ID,
            1,
            0,
            2,
            Result.UPDATED
        );

        @SuppressWarnings("unchecked")
        ActionFuture<UpdateResponse> future = mock(ActionFuture.class);
        when(mockedClient.update(any(UpdateRequest.class))).thenReturn(future);
        when(future.actionGet()).thenReturn(updateResponse);

        sdkClient.updateDataObjectAsync(updateRequest, testThreadPool.executor(GENERAL_THREAD_POOL)).toCompletableFuture().join();

        ArgumentCaptor<UpdateRequest> requestCaptor = ArgumentCaptor.forClass(UpdateRequest.class);
        verify(mockedClient, times(1)).update(requestCaptor.capture());
        assertEquals(TEST_INDEX, requestCaptor.getValue().index());
        assertEquals(TEST_ID, requestCaptor.getValue().id());
        assertEquals("bar", requestCaptor.getValue().doc().sourceAsMap().get("foo"));
    }

    @Test
    public void testUpdateDataObject_NotFound() throws IOException {
        UpdateDataObjectRequest updateRequest = UpdateDataObjectRequest
            .builder()
            .index(TEST_INDEX)
            .id(TEST_ID).tenantId(TEST_TENANT_ID)
            .dataObject(testDataObject)
            .build();

        UpdateResponse updateResponse = new UpdateResponse(
            new ShardInfo(1, 1),
            new ShardId(TEST_INDEX, "_na_", 0),
            TEST_ID,
            1,
            0,
            2,
            Result.CREATED
        );

        @SuppressWarnings("unchecked")
        ActionFuture<UpdateResponse> future = mock(ActionFuture.class);
        when(mockedClient.update(any(UpdateRequest.class))).thenReturn(future);
        when(future.actionGet()).thenReturn(updateResponse);

        UpdateDataObjectResponse response = sdkClient
            .updateDataObjectAsync(updateRequest, testThreadPool.executor(GENERAL_THREAD_POOL))
            .toCompletableFuture()
            .join();

        ArgumentCaptor<UpdateRequest> requestCaptor = ArgumentCaptor.forClass(UpdateRequest.class);
        verify(mockedClient, times(1)).update(requestCaptor.capture());
        assertEquals(TEST_INDEX, requestCaptor.getValue().index());
        assertEquals(TEST_ID, response.id());

        UpdateResponse updateActionResponse = UpdateResponse.fromXContent(response.parser());
        assertEquals(TEST_ID, updateActionResponse.getId());
        assertEquals(DocWriteResponse.Result.CREATED, updateActionResponse.getResult());
        assertEquals(0, updateActionResponse.getShardInfo().getFailed());
        assertEquals(1, updateActionResponse.getShardInfo().getSuccessful());
        assertEquals(1, updateActionResponse.getShardInfo().getTotal());
    }

    @Test
    public void testUpdateDataObject_Null() throws IOException {
        UpdateDataObjectRequest updateRequest = UpdateDataObjectRequest
            .builder()
            .index(TEST_INDEX)
            .id(TEST_ID).tenantId(TEST_TENANT_ID)
            .dataObject(testDataObject)
            .build();

        @SuppressWarnings("unchecked")
        ActionFuture<UpdateResponse> future = mock(ActionFuture.class);
        when(mockedClient.update(any(UpdateRequest.class))).thenReturn(future);
        when(future.actionGet()).thenReturn(null);

        UpdateDataObjectResponse response = sdkClient
            .updateDataObjectAsync(updateRequest, testThreadPool.executor(GENERAL_THREAD_POOL))
            .toCompletableFuture()
            .join();

        ArgumentCaptor<UpdateRequest> requestCaptor = ArgumentCaptor.forClass(UpdateRequest.class);
        verify(mockedClient, times(1)).update(requestCaptor.capture());
        assertEquals(TEST_INDEX, requestCaptor.getValue().index());
        assertEquals(TEST_ID, response.id());
        assertNull(response.parser());
    }

    @Test
    public void testUpdateDataObject_Exception() throws IOException {
        UpdateDataObjectRequest updateRequest = UpdateDataObjectRequest
            .builder()
            .index(TEST_INDEX)
            .id(TEST_ID).tenantId(TEST_TENANT_ID)
            .dataObject(testDataObject)
            .build();

        ArgumentCaptor<UpdateRequest> updateRequestCaptor = ArgumentCaptor.forClass(UpdateRequest.class);
        when(mockedClient.update(updateRequestCaptor.capture())).thenThrow(new UnsupportedOperationException("test"));

        CompletableFuture<UpdateDataObjectResponse> future = sdkClient
            .updateDataObjectAsync(updateRequest, testThreadPool.executor(GENERAL_THREAD_POOL))
            .toCompletableFuture();

        CompletionException ce = assertThrows(CompletionException.class, () -> future.join());
        Throwable cause = ce.getCause();
        assertEquals(UnsupportedOperationException.class, cause.getClass());
        assertEquals("test", cause.getMessage());
    }

    @Test
    public void testUpdateDataObject_VersionCheck() throws IOException {
        UpdateDataObjectRequest updateRequest = UpdateDataObjectRequest
            .builder()
            .index(TEST_INDEX)
            .id(TEST_ID).tenantId(TEST_TENANT_ID)
            .dataObject(testDataObject)
            .ifSeqNo(5)
            .ifPrimaryTerm(2)
            .build();

        ArgumentCaptor<UpdateRequest> updateRequestCaptor = ArgumentCaptor.forClass(UpdateRequest.class);
        VersionConflictEngineException conflictException = new VersionConflictEngineException(
            new ShardId(TEST_INDEX, "_na_", 0),
            TEST_ID,
            "test"
        );
        when(mockedClient.update(updateRequestCaptor.capture())).thenThrow(conflictException);

        CompletableFuture<UpdateDataObjectResponse> future = sdkClient
            .updateDataObjectAsync(updateRequest, testThreadPool.executor(GENERAL_THREAD_POOL))
            .toCompletableFuture();

        CompletionException ce = assertThrows(CompletionException.class, () -> future.join());
        Throwable cause = ce.getCause();
        assertEquals(OpenSearchStatusException.class, cause.getClass());
        assertEquals(RestStatus.CONFLICT, ((OpenSearchStatusException) cause).status());
    }

    @Test
    public void testDeleteDataObject() throws IOException {
        DeleteDataObjectRequest deleteRequest = DeleteDataObjectRequest.builder().index(TEST_INDEX).id(TEST_ID).tenantId(TEST_TENANT_ID).build();

        DeleteResponse deleteResponse = new DeleteResponse(new ShardId(TEST_INDEX, "_na_", 0), TEST_ID, 1, 0, 2, true);
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

        DeleteResponse deleteActionResponse = DeleteResponse.fromXContent(response.parser());
        assertEquals(TEST_ID, deleteActionResponse.getId());
        assertEquals(DocWriteResponse.Result.DELETED, deleteActionResponse.getResult());
    }

    @Test
    public void testDeleteDataObject_Exception() throws IOException {
        DeleteDataObjectRequest deleteRequest = DeleteDataObjectRequest.builder().index(TEST_INDEX).id(TEST_ID).tenantId(TEST_TENANT_ID).build();

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

    @Test
    public void testSearchDataObjectNotTenantAware() throws IOException {
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        SearchDataObjectRequest searchRequest = SearchDataObjectRequest
            .builder()
            .indices(TEST_INDEX)
            .tenantId(TEST_TENANT_ID)
            .searchSourceBuilder(searchSourceBuilder)
            .build();

        SearchResponse searchResponse = new SearchResponse(
            InternalSearchResponse.empty(),
            null,
            1,
            1,
            0,
            123,
            new SearchResponse.PhaseTook(
                EnumSet.allOf(SearchPhaseName.class).stream().collect(Collectors.toMap(SearchPhaseName::getName, e -> (long) e.ordinal()))
            ),
            new ShardSearchFailure[0],
            SearchResponse.Clusters.EMPTY,
            null
        );
        @SuppressWarnings("unchecked")
        ActionFuture<SearchResponse> future = mock(ActionFuture.class);
        when(mockedClient.search(any(SearchRequest.class))).thenReturn(future);
        when(future.actionGet()).thenReturn(searchResponse);

        SdkClient sdkClientNoTenant = new SdkClient(new LocalClusterIndicesClient(mockedClient, xContentRegistry), false);
        SearchDataObjectResponse response = sdkClientNoTenant
            .searchDataObjectAsync(searchRequest, testThreadPool.executor(GENERAL_THREAD_POOL))
            .toCompletableFuture()
            .join();

        ArgumentCaptor<SearchRequest> requestCaptor = ArgumentCaptor.forClass(SearchRequest.class);
        verify(mockedClient, times(1)).search(requestCaptor.capture());
        assertEquals(1, requestCaptor.getValue().indices().length);
        assertEquals(TEST_INDEX, requestCaptor.getValue().indices()[0]);
        assertEquals("{}", requestCaptor.getValue().source().toString());

        SearchResponse searchActionResponse = SearchResponse.fromXContent(response.parser());
        assertEquals(0, searchActionResponse.getFailedShards());
        assertEquals(0, searchActionResponse.getSkippedShards());
        assertEquals(1, searchActionResponse.getSuccessfulShards());
        assertEquals(1, searchActionResponse.getTotalShards());
        assertEquals(0, searchActionResponse.getHits().getTotalHits().value);
    }

    @Test
    public void testSearchDataObjectTenantAware() throws IOException {
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        SearchDataObjectRequest searchRequest = SearchDataObjectRequest
            .builder()
            .indices(TEST_INDEX)
            .tenantId(TEST_TENANT_ID)
            .searchSourceBuilder(searchSourceBuilder)
            .build();

        SearchResponse searchResponse = new SearchResponse(
            InternalSearchResponse.empty(),
            null,
            1,
            1,
            0,
            123,
            new SearchResponse.PhaseTook(
                EnumSet.allOf(SearchPhaseName.class).stream().collect(Collectors.toMap(SearchPhaseName::getName, e -> (long) e.ordinal()))
            ),
            new ShardSearchFailure[0],
            SearchResponse.Clusters.EMPTY,
            null
        );
        @SuppressWarnings("unchecked")
        ActionFuture<SearchResponse> future = mock(ActionFuture.class);
        when(mockedClient.search(any(SearchRequest.class))).thenReturn(future);
        when(future.actionGet()).thenReturn(searchResponse);

        SearchDataObjectResponse response = sdkClient
            .searchDataObjectAsync(searchRequest, testThreadPool.executor(GENERAL_THREAD_POOL))
            .toCompletableFuture()
            .join();

        ArgumentCaptor<SearchRequest> requestCaptor = ArgumentCaptor.forClass(SearchRequest.class);
        verify(mockedClient, times(1)).search(requestCaptor.capture());
        assertEquals(1, requestCaptor.getValue().indices().length);
        assertEquals(TEST_INDEX, requestCaptor.getValue().indices()[0]);
        assertTrue(requestCaptor.getValue().source().toString().contains("{\"term\":{\"tenant_id\":{\"value\":\"xyz\""));

        SearchResponse searchActionResponse = SearchResponse.fromXContent(response.parser());
        assertEquals(0, searchActionResponse.getFailedShards());
        assertEquals(0, searchActionResponse.getSkippedShards());
        assertEquals(1, searchActionResponse.getSuccessfulShards());
        assertEquals(1, searchActionResponse.getTotalShards());
        assertEquals(0, searchActionResponse.getHits().getTotalHits().value);
    }

    @Test
    public void testSearchDataObject_Exception() throws IOException {
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        SearchDataObjectRequest searchRequest = SearchDataObjectRequest
            .builder()
            .indices(TEST_INDEX)
            .tenantId(TEST_TENANT_ID)
            .searchSourceBuilder(searchSourceBuilder)
            .build();

        PlainActionFuture<SearchResponse> exceptionalFuture = PlainActionFuture.newFuture();
        exceptionalFuture.onFailure(new UnsupportedOperationException("test"));
        when(mockedClient.search(any(SearchRequest.class))).thenReturn(exceptionalFuture);
        
        CompletableFuture<SearchDataObjectResponse> future = sdkClient
            .searchDataObjectAsync(searchRequest, testThreadPool.executor(GENERAL_THREAD_POOL))
            .toCompletableFuture();

        CompletionException ce = assertThrows(CompletionException.class, () -> future.join());
        Throwable cause = ce.getCause();
        assertEquals(UnsupportedOperationException.class, cause.getClass());
        assertEquals("test", cause.getMessage());
    }
    
    @Test
    public void testSearchDataObject_NullTenantNoMultitenancy() throws IOException {
        // Tests no status exception if multitenancy not enabled
        SdkClient sdkClientNoTenant = new SdkClient(new LocalClusterIndicesClient(mockedClient, xContentRegistry), false);

        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        SearchDataObjectRequest searchRequest = SearchDataObjectRequest
            .builder()
            .indices(TEST_INDEX)
            // null tenant Id
            .searchSourceBuilder(searchSourceBuilder)
            .build();

        PlainActionFuture<SearchResponse> exceptionalFuture = PlainActionFuture.newFuture();
        exceptionalFuture.onFailure(new UnsupportedOperationException("test"));
        when(mockedClient.search(any(SearchRequest.class))).thenReturn(exceptionalFuture);

        CompletableFuture<SearchDataObjectResponse> future = sdkClientNoTenant
            .searchDataObjectAsync(searchRequest, testThreadPool.executor(GENERAL_THREAD_POOL))
            .toCompletableFuture();

        CompletionException ce = assertThrows(CompletionException.class, () -> future.join());
        Throwable cause = ce.getCause();
        assertEquals(UnsupportedOperationException.class, cause.getClass());
        assertEquals("test", cause.getMessage());
    }
}
