/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.model_group;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.opensearch.action.DocWriteResponse.Result.DELETED;
import static org.opensearch.ml.common.CommonValue.ML_MODEL_GROUP_INDEX;
import static org.opensearch.ml.plugin.MachineLearningPlugin.GENERAL_THREAD_POOL;
import static org.opensearch.ml.plugin.MachineLearningPlugin.ML_THREAD_POOL_PREFIX;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.lucene.search.TotalHits;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.OpenSearchException;
import org.opensearch.action.LatchedActionListener;
import org.opensearch.action.delete.DeleteRequest;
import org.opensearch.action.delete.DeleteResponse;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.action.search.SearchResponseSections;
import org.opensearch.action.search.ShardSearchFailure;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.PlainActionFuture;
import org.opensearch.client.Client;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.unit.TimeValue;
import org.opensearch.common.util.concurrent.OpenSearchExecutors;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.index.shard.ShardId;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.index.IndexNotFoundException;
import org.opensearch.ml.common.transport.model_group.MLModelGroupDeleteRequest;
import org.opensearch.ml.helper.ModelAccessControlHelper;
import org.opensearch.ml.sdkclient.SdkClientFactory;
import org.opensearch.ml.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.utils.TestHelper;
import org.opensearch.sdk.SdkClient;
import org.opensearch.search.SearchHit;
import org.opensearch.search.SearchHits;
import org.opensearch.search.aggregations.InternalAggregations;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.threadpool.ScalingExecutorBuilder;
import org.opensearch.threadpool.TestThreadPool;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.TransportService;

public class DeleteModelGroupTransportActionTests extends OpenSearchTestCase {
    @Mock
    ThreadPool threadPool;

    @Mock
    Client client;

    SdkClient sdkClient;

    @Mock
    TransportService transportService;

    @Mock
    ActionFilters actionFilters;

    @Mock
    ActionListener<DeleteResponse> actionListener;

    @Mock
    ClusterService clusterService;

    @Mock
    NamedXContentRegistry xContentRegistry;

    @Mock
    private MLFeatureEnabledSetting mlFeatureEnabledSetting;

    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    DeleteModelGroupTransportAction deleteModelGroupTransportAction;
    MLModelGroupDeleteRequest mlModelGroupDeleteRequest;
    ThreadContext threadContext;

    @Mock
    private ModelAccessControlHelper modelAccessControlHelper;

    private static final TestThreadPool testThreadPool = new TestThreadPool(
        DeleteModelGroupTransportActionTests.class.getName(),
        new ScalingExecutorBuilder(
            GENERAL_THREAD_POOL,
            1,
            Math.max(1, OpenSearchExecutors.allocatedProcessors(Settings.EMPTY) - 1),
            TimeValue.timeValueMinutes(1),
            ML_THREAD_POOL_PREFIX + GENERAL_THREAD_POOL
        )
    );

    @Before
    public void setup() throws IOException {
        MockitoAnnotations.openMocks(this);

        Settings settings = Settings.builder().build();
        sdkClient = SdkClientFactory.createSdkClient(client, xContentRegistry, settings);
        mlModelGroupDeleteRequest = MLModelGroupDeleteRequest.builder().modelGroupId("test_id").build();
        deleteModelGroupTransportAction = spy(
            new DeleteModelGroupTransportAction(
                transportService,
                actionFilters,
                client,
                sdkClient,
                xContentRegistry,
                clusterService,
                modelAccessControlHelper,
                mlFeatureEnabledSetting
            )
        );

        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(3);
            listener.onResponse(true);
            return null;
        }).when(modelAccessControlHelper).validateModelGroupAccess(any(), any(), any(), any());

        threadContext = new ThreadContext(settings);
        when(client.threadPool()).thenReturn(threadPool);
        when(threadPool.getThreadContext()).thenReturn(threadContext);
        when(threadPool.executor(anyString())).thenReturn(testThreadPool.executor(GENERAL_THREAD_POOL));
    }

    @AfterClass
    public static void cleanup() {
        ThreadPool.terminate(testThreadPool, 500, TimeUnit.MILLISECONDS);
    }

    @Test
    public void testDeleteModelGroup_Success() throws InterruptedException {

        SearchResponse searchResponse = getEmptySearchResponse();

        PlainActionFuture<SearchResponse> future = PlainActionFuture.newFuture();
        future.onResponse(searchResponse);
        when(client.search(any(SearchRequest.class))).thenReturn(future);

        DeleteResponse deleteResponse = new DeleteResponse(new ShardId(ML_MODEL_GROUP_INDEX, "_na_", 0), "test_id", 1, 0, 2, true);
        PlainActionFuture<DeleteResponse> deleteFuture = PlainActionFuture.newFuture();
        deleteFuture.onResponse(deleteResponse);
        when(client.delete(any(DeleteRequest.class))).thenReturn(deleteFuture);

        CountDownLatch latch = new CountDownLatch(1);
        LatchedActionListener<DeleteResponse> latchedActionListener = new LatchedActionListener<>(actionListener, latch);
        deleteModelGroupTransportAction.doExecute(null, mlModelGroupDeleteRequest, latchedActionListener);
        latch.await(500, TimeUnit.MILLISECONDS);
        ArgumentCaptor<DeleteResponse> captor = ArgumentCaptor.forClass(DeleteResponse.class);
        verify(actionListener).onResponse(captor.capture());
        assertEquals("test_id", captor.getValue().getId());
        assertEquals(DELETED, captor.getValue().getResult());
    }

    @Test
    public void test_AssociatedModelsExistException() throws IOException, InterruptedException {

        SearchResponse searchResponse = getNonEmptySearchResponse();
        PlainActionFuture<SearchResponse> future = PlainActionFuture.newFuture();
        future.onResponse(searchResponse);
        when(client.search(any(SearchRequest.class))).thenReturn(future);

        CountDownLatch latch = new CountDownLatch(1);
        LatchedActionListener<DeleteResponse> latchedActionListener = new LatchedActionListener<>(actionListener, latch);
        deleteModelGroupTransportAction.doExecute(null, mlModelGroupDeleteRequest, latchedActionListener);
        latch.await(500, TimeUnit.MILLISECONDS);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("Cannot delete the model group when it has associated model versions", argumentCaptor.getValue().getMessage());

    }

    @Test
    public void test_IndexNotFoundExceptionDuringSearch() throws IOException, InterruptedException {
        PlainActionFuture<SearchResponse> future = PlainActionFuture.newFuture();
        future.onFailure(new IndexNotFoundException("index_not_found"));
        when(client.search(any(SearchRequest.class))).thenReturn(future);

        DeleteResponse deleteResponse = new DeleteResponse(new ShardId(ML_MODEL_GROUP_INDEX, "_na_", 0), "test_id", 1, 0, 2, true);
        PlainActionFuture<DeleteResponse> deleteFuture = PlainActionFuture.newFuture();
        deleteFuture.onResponse(deleteResponse);
        when(client.delete(any(DeleteRequest.class))).thenReturn(deleteFuture);

        CountDownLatch latch = new CountDownLatch(1);
        LatchedActionListener<DeleteResponse> latchedActionListener = new LatchedActionListener<>(actionListener, latch);
        deleteModelGroupTransportAction.doExecute(null, mlModelGroupDeleteRequest, latchedActionListener);
        latch.await(500, TimeUnit.MILLISECONDS);

        ArgumentCaptor<DeleteResponse> captor = ArgumentCaptor.forClass(DeleteResponse.class);
        verify(actionListener).onResponse(captor.capture());
        assertEquals("test_id", captor.getValue().getId());
        assertEquals(DELETED, captor.getValue().getResult());
    }

    @Test
    public void test_SearchResponseParsingFailure() throws InterruptedException {
        // Simulate a search response that causes a parsing failure
        PlainActionFuture<SearchResponse> future = PlainActionFuture.newFuture();
        future.onFailure(new IOException("Failed to parse search response"));
        when(client.search(any(SearchRequest.class))).thenReturn(future);

        CountDownLatch latch = new CountDownLatch(1);
        LatchedActionListener<DeleteResponse> latchedActionListener = new LatchedActionListener<>(actionListener, latch);
        deleteModelGroupTransportAction.doExecute(null, mlModelGroupDeleteRequest, latchedActionListener);
        latch.await(500, TimeUnit.MILLISECONDS);

        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("Failed to parse search response", argumentCaptor.getValue().getMessage());
    }

    @Test
    public void test_DeleteRequestInternalServerError() throws InterruptedException {
        SearchResponse searchResponse = getEmptySearchResponse();
        PlainActionFuture<SearchResponse> future = PlainActionFuture.newFuture();
        future.onResponse(searchResponse);
        when(client.search(any(SearchRequest.class))).thenReturn(future);

        PlainActionFuture<DeleteResponse> deleteFuture = PlainActionFuture.newFuture();
        deleteFuture.onFailure(new OpenSearchException("Internal Server Error", RestStatus.INTERNAL_SERVER_ERROR));
        when(client.delete(any(DeleteRequest.class))).thenReturn(deleteFuture);

        CountDownLatch latch = new CountDownLatch(1);
        LatchedActionListener<DeleteResponse> latchedActionListener = new LatchedActionListener<>(actionListener, latch);
        deleteModelGroupTransportAction.doExecute(null, mlModelGroupDeleteRequest, latchedActionListener);
        latch.await(500, TimeUnit.MILLISECONDS);

        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("Internal Server Error", argumentCaptor.getValue().getMessage());
    }

    @Test
    public void test_UserHasNoAccessException() throws IOException {
        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(3);
            listener.onResponse(false);
            return null;
        }).when(modelAccessControlHelper).validateModelGroupAccess(any(), any(), any(), any());

        deleteModelGroupTransportAction.doExecute(null, mlModelGroupDeleteRequest, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("User doesn't have privilege to delete this model group", argumentCaptor.getValue().getMessage());
    }

    @Test
    public void test_ValidationFailedException() {
        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(3);
            listener.onFailure(new Exception("Failed to validate access"));
            return null;
        }).when(modelAccessControlHelper).validateModelGroupAccess(any(), any(), any(), any());

        deleteModelGroupTransportAction.doExecute(null, mlModelGroupDeleteRequest, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("Failed to validate access", argumentCaptor.getValue().getMessage());
    }

    public void testDeleteModelGroup_Failure() throws IOException, InterruptedException {
        SearchResponse searchResponse = getEmptySearchResponse();

        PlainActionFuture<SearchResponse> future = PlainActionFuture.newFuture();
        future.onResponse(searchResponse);
        when(client.search(any(SearchRequest.class))).thenReturn(future);

        PlainActionFuture<DeleteResponse> deleteFuture = PlainActionFuture.newFuture();
        deleteFuture.onFailure(new Exception("errorMessage"));
        when(client.delete(any(DeleteRequest.class))).thenReturn(deleteFuture);

        CountDownLatch latch = new CountDownLatch(1);
        LatchedActionListener<DeleteResponse> latchedActionListener = new LatchedActionListener<>(actionListener, latch);
        deleteModelGroupTransportAction.doExecute(null, mlModelGroupDeleteRequest, latchedActionListener);
        latch.await(500, TimeUnit.MILLISECONDS);

        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("errorMessage", argumentCaptor.getValue().getMessage());
    }

    private SearchResponse getEmptySearchResponse() {
        SearchHits hits = new SearchHits(new SearchHit[0], null, Float.NaN);
        SearchResponseSections searchSections = new SearchResponseSections(hits, InternalAggregations.EMPTY, null, true, false, null, 1);
        SearchResponse searchResponse = new SearchResponse(
            searchSections,
            null,
            1,
            1,
            0,
            11,
            ShardSearchFailure.EMPTY_ARRAY,
            SearchResponse.Clusters.EMPTY
        );
        return searchResponse;
    }

    private SearchResponse getNonEmptySearchResponse() throws IOException {
        SearchHit[] hits = new SearchHit[1];
        String modelContent = "{\n"
            + "                    \"created_time\": 1684981986069,\n"
            + "                    \"last_updated_time\": 1684981986069,\n"
            + "                    \"_id\": \"model_ID\",\n"
            + "                    \"name\": \"test_model\",\n"
            + "                    \"description\": \"This is an example description\"\n"
            + "                }";
        SearchHit model = SearchHit.fromXContent(TestHelper.parser(modelContent));
        hits[0] = model;
        SearchHits searchHits = new SearchHits(hits, new TotalHits(1, TotalHits.Relation.EQUAL_TO), 1.0f);
        SearchResponseSections searchSections = new SearchResponseSections(
            searchHits,
            InternalAggregations.EMPTY,
            null,
            true,
            false,
            null,
            1
        );
        SearchResponse searchResponse = new SearchResponse(
            searchSections,
            null,
            1,
            1,
            0,
            11,
            ShardSearchFailure.EMPTY_ARRAY,
            SearchResponse.Clusters.EMPTY
        );
        return searchResponse;
    }
}
