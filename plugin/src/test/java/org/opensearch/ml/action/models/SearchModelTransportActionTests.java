/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.models;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.opensearch.ml.plugin.MachineLearningPlugin.GENERAL_THREAD_POOL;
import static org.opensearch.ml.plugin.MachineLearningPlugin.ML_THREAD_POOL_PREFIX;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.lucene.search.TotalHits;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.ExpectedException;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.OpenSearchStatusException;
import org.opensearch.action.LatchedActionListener;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.action.search.SearchResponse.Clusters;
import org.opensearch.action.search.SearchResponseSections;
import org.opensearch.action.search.ShardSearchFailure;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.PlainActionFuture;
import org.opensearch.client.Client;
import org.opensearch.cluster.ClusterName;
import org.opensearch.cluster.ClusterState;
import org.opensearch.cluster.metadata.Metadata;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.unit.TimeValue;
import org.opensearch.common.util.concurrent.OpenSearchExecutors;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.index.IndexNotFoundException;
import org.opensearch.index.query.QueryBuilders;
import org.opensearch.ml.action.handler.MLSearchHandler;
import org.opensearch.ml.common.exception.MLResourceNotFoundException;
import org.opensearch.ml.helper.ModelAccessControlHelper;
import org.opensearch.ml.sdkclient.SdkClientFactory;
import org.opensearch.ml.utils.TestHelper;
import org.opensearch.sdk.SdkClient;
import org.opensearch.search.SearchHit;
import org.opensearch.search.SearchHits;
import org.opensearch.search.aggregations.InternalAggregations;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.search.fetch.subphase.FetchSourceContext;
import org.opensearch.search.internal.InternalSearchResponse;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.threadpool.ScalingExecutorBuilder;
import org.opensearch.threadpool.TestThreadPool;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.TransportService;

public class SearchModelTransportActionTests extends OpenSearchTestCase {
    private static TestThreadPool testThreadPool = new TestThreadPool(
        SearchModelTransportActionTests.class.getName(),
        new ScalingExecutorBuilder(
            GENERAL_THREAD_POOL,
            1,
            Math.max(1, OpenSearchExecutors.allocatedProcessors(Settings.EMPTY) - 1),
            TimeValue.timeValueMinutes(1),
            ML_THREAD_POOL_PREFIX + GENERAL_THREAD_POOL
        )
    );
    @Mock
    Client client;

    SdkClient sdkClient;

    @Mock
    NamedXContentRegistry namedXContentRegistry;

    @Mock
    TransportService transportService;

    @Mock
    ActionFilters actionFilters;

    SearchRequest searchRequest;

    SearchResponse searchResponse;

    @Mock
    ActionListener<SearchResponse> actionListener;

    @Mock
    ThreadPool threadPool;

    private SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();

    MLSearchHandler mlSearchHandler;
    SearchModelTransportAction searchModelTransportAction;
    ThreadContext threadContext;

    @Mock
    private ModelAccessControlHelper modelAccessControlHelper;

    @Mock
    private ClusterService clusterService;

    @Mock
    private FetchSourceContext fetchSourceContext;

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);

        Settings settings = Settings.builder().build();
        sdkClient = SdkClientFactory.createSdkClient(client, namedXContentRegistry, settings);
        mlSearchHandler = spy(new MLSearchHandler(client, namedXContentRegistry, modelAccessControlHelper, clusterService));
        searchModelTransportAction = new SearchModelTransportAction(transportService, actionFilters, sdkClient, mlSearchHandler);

        threadContext = new ThreadContext(settings);
        when(client.threadPool()).thenReturn(threadPool);
        when(threadPool.getThreadContext()).thenReturn(threadContext);
        when(threadPool.executor(anyString())).thenReturn(testThreadPool.executor(GENERAL_THREAD_POOL));

        searchSourceBuilder = new SearchSourceBuilder();
        searchSourceBuilder.fetchSource(fetchSourceContext);
        searchRequest = new SearchRequest(new String[0], searchSourceBuilder);
        when(fetchSourceContext.includes()).thenReturn(new String[] {});
        when(fetchSourceContext.excludes()).thenReturn(new String[] {});

        when(modelAccessControlHelper.skipModelAccessControl(any())).thenReturn(false);

        Metadata metadata = mock(Metadata.class);
        when(metadata.hasIndex(anyString())).thenReturn(true);
        ClusterState testState = new ClusterState(new ClusterName("mock"), 123l, "111111", metadata, null, null, null, Map.of(), 0, false);
        when(clusterService.state()).thenReturn(testState);

        SearchHits searchHits = new SearchHits(new SearchHit[0], new TotalHits(0L, TotalHits.Relation.EQUAL_TO), Float.NaN);
        InternalSearchResponse internalSearchResponse = new InternalSearchResponse(
            searchHits,
            InternalAggregations.EMPTY,
            null,
            null,
            false,
            null,
            0
        );
        searchResponse = new SearchResponse(
            internalSearchResponse,
            null,
            0,
            0,
            0,
            1,
            ShardSearchFailure.EMPTY_ARRAY,
            mock(Clusters.class),
            null
        );
    }

    @AfterClass
    public static void cleanup() {
        ThreadPool.terminate(testThreadPool, 500, TimeUnit.MILLISECONDS);
    }

    public void test_DoExecute_admin() throws InterruptedException {
        when(modelAccessControlHelper.skipModelAccessControl(any())).thenReturn(true);
        
        PlainActionFuture<SearchResponse> future = PlainActionFuture.newFuture();
        future.onResponse(searchResponse);
        when(client.search(any(SearchRequest.class))).thenReturn(future);

        CountDownLatch latch = new CountDownLatch(1);
        LatchedActionListener<SearchResponse> latchedActionListener = new LatchedActionListener<>(actionListener, latch);
        searchModelTransportAction.doExecute(null, searchRequest, latchedActionListener);
        latch.await(500, TimeUnit.MILLISECONDS);
        
        verify(mlSearchHandler).search(sdkClient, searchRequest, latchedActionListener);
        verify(client, times(1)).search(any());
    }

    public void test_DoExecute_addBackendRoles() throws IOException, InterruptedException {
        SearchResponse searchResponse = createModelGroupSearchResponse();
        PlainActionFuture<SearchResponse> future = PlainActionFuture.newFuture();
        future.onResponse(searchResponse);
        when(client.search(any(SearchRequest.class))).thenReturn(future);

        when(modelAccessControlHelper.createSearchSourceBuilder(any())).thenReturn(searchSourceBuilder);
        CountDownLatch latch = new CountDownLatch(1);
        LatchedActionListener<SearchResponse> latchedActionListener = new LatchedActionListener<>(actionListener, latch);
        searchModelTransportAction.doExecute(null, searchRequest, latchedActionListener);
        latch.await(500, TimeUnit.MILLISECONDS);

        verify(mlSearchHandler).search(sdkClient, searchRequest, latchedActionListener);
        verify(client, times(2)).search(any());
    }

    public void test_DoExecute_addBackendRoles_without_groupIds() throws InterruptedException {
        PlainActionFuture<SearchResponse> future = PlainActionFuture.newFuture();
        future.onResponse(searchResponse);
        when(client.search(any(SearchRequest.class))).thenReturn(future);

        when(modelAccessControlHelper.createSearchSourceBuilder(any())).thenReturn(searchSourceBuilder);
        CountDownLatch latch = new CountDownLatch(1);
        LatchedActionListener<SearchResponse> latchedActionListener = new LatchedActionListener<>(actionListener, latch);
        searchModelTransportAction.doExecute(null, searchRequest, latchedActionListener);
        latch.await(500, TimeUnit.MILLISECONDS);

        verify(mlSearchHandler).search(sdkClient, searchRequest, latchedActionListener);
        verify(client, times(2)).search(any());
    }

    public void test_DoExecute_addBackendRoles_exception() throws InterruptedException {
        PlainActionFuture<SearchResponse> future = PlainActionFuture.newFuture();
        future.onFailure(new RuntimeException("runtime exception"));
        when(client.search(any(SearchRequest.class))).thenReturn(future);

        when(modelAccessControlHelper.createSearchSourceBuilder(any())).thenReturn(searchSourceBuilder);
        CountDownLatch latch = new CountDownLatch(1);
        LatchedActionListener<SearchResponse> latchedActionListener = new LatchedActionListener<>(actionListener, latch);
        searchModelTransportAction.doExecute(null, searchRequest, latchedActionListener);
        latch.await(500, TimeUnit.MILLISECONDS);

        verify(mlSearchHandler).search(sdkClient, searchRequest, latchedActionListener);
        verify(client, times(1)).search(any());
    }

    public void test_DoExecute_searchModel_before_model_creation_no_exception() throws InterruptedException {
        PlainActionFuture<SearchResponse> future = PlainActionFuture.newFuture();
        future.onFailure(new IndexNotFoundException("index not found exception"));
        when(client.search(any(SearchRequest.class))).thenReturn(future);

        when(modelAccessControlHelper.skipModelAccessControl(any())).thenReturn(true);
        CountDownLatch latch = new CountDownLatch(1);
        LatchedActionListener<SearchResponse> latchedActionListener = new LatchedActionListener<>(actionListener, latch);
        searchModelTransportAction.doExecute(null, searchRequest, latchedActionListener);
        latch.await(500, TimeUnit.MILLISECONDS);

        verify(mlSearchHandler).search(sdkClient, searchRequest, latchedActionListener);
        verify(actionListener, times(0)).onFailure(any(IndexNotFoundException.class));
    }

    public void test_DoExecute_searchModel_before_model_creation_empty_search() throws InterruptedException {
        SearchHits hits = new SearchHits(new SearchHit[] {}, new TotalHits(0, TotalHits.Relation.EQUAL_TO), Float.NaN);
        SearchResponseSections searchSections = new SearchResponseSections(hits, InternalAggregations.EMPTY, null, false, false, null, 1);
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
        PlainActionFuture<SearchResponse> future = PlainActionFuture.newFuture();
        future.onResponse(searchResponse);
        when(client.search(any(SearchRequest.class))).thenReturn(future);

        when(modelAccessControlHelper.skipModelAccessControl(any())).thenReturn(true);
        CountDownLatch latch = new CountDownLatch(1);
        LatchedActionListener<SearchResponse> latchedActionListener = new LatchedActionListener<>(actionListener, latch);
        searchModelTransportAction.doExecute(null, searchRequest, latchedActionListener);
        latch.await(500, TimeUnit.MILLISECONDS);

        verify(mlSearchHandler).search(sdkClient, searchRequest, latchedActionListener);
        verify(client, times(1)).search(any());
        verify(actionListener, times(0)).onFailure(any(IndexNotFoundException.class));
        verify(actionListener, times(1)).onResponse(any(SearchResponse.class));
    }

    public void test_DoExecute_searchModel_MLResourceNotFoundException_exception() throws InterruptedException {
        PlainActionFuture<SearchResponse> future = PlainActionFuture.newFuture();
        future.onFailure(new MLResourceNotFoundException("ml resource not found exception"));
        when(client.search(any(SearchRequest.class))).thenReturn(future);

        when(modelAccessControlHelper.skipModelAccessControl(any())).thenReturn(true);
        CountDownLatch latch = new CountDownLatch(1);
        LatchedActionListener<SearchResponse> latchedActionListener = new LatchedActionListener<>(actionListener, latch);
        searchModelTransportAction.doExecute(null, searchRequest, latchedActionListener);
        latch.await(500, TimeUnit.MILLISECONDS);

        verify(mlSearchHandler).search(sdkClient, searchRequest, latchedActionListener);
        verify(client, times(1)).search(any());
        verify(actionListener, times(1)).onFailure(any(OpenSearchStatusException.class));
    }

    public void test_DoExecute_addBackendRoles_boolQuery() throws IOException, InterruptedException {
        SearchResponse searchResponse = createModelGroupSearchResponse();
        PlainActionFuture<SearchResponse> future = PlainActionFuture.newFuture();
        future.onResponse(searchResponse);
        when(client.search(any(SearchRequest.class))).thenReturn(future);

        when(modelAccessControlHelper.createSearchSourceBuilder(any())).thenReturn(searchSourceBuilder);
        searchRequest.source().query(QueryBuilders.boolQuery().must(QueryBuilders.matchQuery("name", "model_IT")));
        CountDownLatch latch = new CountDownLatch(1);
        LatchedActionListener<SearchResponse> latchedActionListener = new LatchedActionListener<>(actionListener, latch);
        searchModelTransportAction.doExecute(null, searchRequest, latchedActionListener);
        latch.await(500, TimeUnit.MILLISECONDS);

        verify(mlSearchHandler).search(sdkClient, searchRequest, latchedActionListener);
        verify(client, times(2)).search(any());
    }

    public void test_DoExecute_addBackendRoles_termQuery() throws IOException, InterruptedException {
        SearchResponse searchResponse = createModelGroupSearchResponse();
        PlainActionFuture<SearchResponse> future = PlainActionFuture.newFuture();
        future.onResponse(searchResponse);
        when(client.search(any(SearchRequest.class))).thenReturn(future);

        when(modelAccessControlHelper.createSearchSourceBuilder(any())).thenReturn(searchSourceBuilder);
        searchRequest.source().query(QueryBuilders.termQuery("name", "model_IT"));
        CountDownLatch latch = new CountDownLatch(1);
        LatchedActionListener<SearchResponse> latchedActionListener = new LatchedActionListener<>(actionListener, latch);
        searchModelTransportAction.doExecute(null, searchRequest, latchedActionListener);
        latch.await(500, TimeUnit.MILLISECONDS);

        verify(mlSearchHandler).search(sdkClient, searchRequest, latchedActionListener);
        verify(client, times(2)).search(any());
    }

    private SearchResponse createModelGroupSearchResponse() throws IOException {
        String modelContent = "{\n"
            + "                    \"created_time\": 1684981986069,\n"
            + "                    \"access\": \"public\",\n"
            + "                    \"latest_version\": 0,\n"
            + "                    \"last_updated_time\": 1684981986069,\n"
            + "                    \"name\": \"model_group_IT\",\n"
            + "                    \"description\": \"This is an example description\"\n"
            + "                }";
        SearchHit modelGroup = SearchHit.fromXContent(TestHelper.parser(modelContent));
        SearchHits hits = new SearchHits(new SearchHit[] { modelGroup }, new TotalHits(1, TotalHits.Relation.EQUAL_TO), Float.NaN);
        InternalSearchResponse internalSearchResponse = new InternalSearchResponse(
            hits,
            InternalAggregations.EMPTY,
            null,
            null,
            false,
            null,
            0
        );
        return new SearchResponse(internalSearchResponse, null, 0, 0, 0, 1, ShardSearchFailure.EMPTY_ARRAY, mock(Clusters.class), null);
    }
}
