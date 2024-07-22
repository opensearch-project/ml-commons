/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.agents;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.opensearch.ml.common.CommonValue.TENANT_ID;
import static org.opensearch.ml.plugin.MachineLearningPlugin.GENERAL_THREAD_POOL;
import static org.opensearch.ml.plugin.MachineLearningPlugin.ML_THREAD_POOL_PREFIX;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.lucene.search.TotalHits;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.OpenSearchStatusException;
import org.opensearch.action.LatchedActionListener;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.action.search.ShardSearchFailure;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.PlainActionFuture;
import org.opensearch.client.Client;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.unit.TimeValue;
import org.opensearch.common.util.concurrent.OpenSearchExecutors;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.index.query.QueryBuilders;
import org.opensearch.ml.sdkclient.SdkClientFactory;
import org.opensearch.ml.settings.MLFeatureEnabledSetting;
import org.opensearch.sdk.SdkClient;
import org.opensearch.search.SearchHit;
import org.opensearch.search.SearchHits;
import org.opensearch.search.aggregations.InternalAggregations;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.search.internal.InternalSearchResponse;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.threadpool.ScalingExecutorBuilder;
import org.opensearch.threadpool.TestThreadPool;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.TransportService;

public class TransportSearchAgentActionTests extends OpenSearchTestCase {
    @Mock
    Client client;
    SdkClient sdkClient;

    SearchResponse searchResponse;

    @Mock
    TransportService transportService;

    @Mock
    ActionFilters actionFilters;

    @Mock
    private NamedXContentRegistry xContentRegistry;

    @Mock
    ActionListener<SearchResponse> actionListener;
    @Mock
    private MLFeatureEnabledSetting mlFeatureEnabledSetting;

    TransportSearchAgentAction transportSearchAgentAction;

    private static final TestThreadPool testThreadPool = new TestThreadPool(
        TransportSearchAgentActionTests.class.getName(),
        new ScalingExecutorBuilder(
            GENERAL_THREAD_POOL,
            1,
            Math.max(1, OpenSearchExecutors.allocatedProcessors(Settings.EMPTY) - 1),
            TimeValue.timeValueMinutes(1),
            ML_THREAD_POOL_PREFIX + GENERAL_THREAD_POOL
        )
    );

    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);
        Settings settings = Settings.builder().build();
        sdkClient = SdkClientFactory.createSdkClient(client, xContentRegistry, settings);
        when(mlFeatureEnabledSetting.isMultiTenancyEnabled()).thenReturn(false);
        transportSearchAgentAction = new TransportSearchAgentAction(
            transportService,
            actionFilters,
            client,
            sdkClient,
            mlFeatureEnabledSetting
        );
        ThreadPool threadPool = mock(ThreadPool.class);
        when(client.threadPool()).thenReturn(threadPool);
        ThreadContext threadContext = new ThreadContext(Settings.EMPTY);
        when(threadPool.getThreadContext()).thenReturn(threadContext);
        when(threadPool.executor(anyString())).thenReturn(testThreadPool.executor(GENERAL_THREAD_POOL));

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
            mock(SearchResponse.Clusters.class),
            null
        );
    }

    @AfterClass
    public static void cleanup() {
        ThreadPool.terminate(testThreadPool, 500, TimeUnit.MILLISECONDS);
    }

    @Test
    public void testDoExecuteWithEmptyQuery() throws InterruptedException {
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
        SearchRequest request = new SearchRequest("my_index").source(sourceBuilder);

        PlainActionFuture<SearchResponse> future = PlainActionFuture.newFuture();
        future.onResponse(searchResponse);
        when(client.search(request)).thenReturn(future);

        CountDownLatch latch = new CountDownLatch(1);
        LatchedActionListener<SearchResponse> latchedActionListener = new LatchedActionListener<>(actionListener, latch);
        transportSearchAgentAction.doExecute(null, request, latchedActionListener);
        latch.await(500, TimeUnit.MILLISECONDS);

        verify(client, times(1)).search(eq(request));
        verify(actionListener, times(1)).onResponse(any(SearchResponse.class));
    }

    @Test
    public void testDoExecuteWithNonEmptyQuery() throws InterruptedException {
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
        sourceBuilder.query(QueryBuilders.matchAllQuery());
        SearchRequest request = new SearchRequest("my_index").source(sourceBuilder);

        PlainActionFuture<SearchResponse> future = PlainActionFuture.newFuture();
        future.onResponse(searchResponse);
        when(client.search(request)).thenReturn(future);

        CountDownLatch latch = new CountDownLatch(1);
        LatchedActionListener<SearchResponse> latchedActionListener = new LatchedActionListener<>(actionListener, latch);
        transportSearchAgentAction.doExecute(null, request, latchedActionListener);
        latch.await(500, TimeUnit.MILLISECONDS);

        verify(client, times(1)).search(eq(request));
        verify(actionListener, times(1)).onResponse(any(SearchResponse.class));
    }

    @Test
    public void testDoExecuteOnFailure() throws InterruptedException {
        SearchRequest request = new SearchRequest("my_index");
        PlainActionFuture<SearchResponse> future = PlainActionFuture.newFuture();
        future.onFailure(new Exception("test exception"));
        when(client.search(request)).thenReturn(future);

        CountDownLatch latch = new CountDownLatch(1);
        LatchedActionListener<SearchResponse> latchedActionListener = new LatchedActionListener<>(actionListener, latch);
        transportSearchAgentAction.doExecute(null, request, latchedActionListener);
        latch.await(500, TimeUnit.MILLISECONDS);

        verify(client, times(1)).search(eq(request));
        verify(actionListener, times(1)).onFailure(any(Exception.class));
    }

    @Test
    public void testSearchWithHiddenField() throws InterruptedException {
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
        sourceBuilder.query(QueryBuilders.termQuery("field", "value")); // Simulate user query
        SearchRequest request = new SearchRequest("my_index").source(sourceBuilder);

        PlainActionFuture<SearchResponse> future = PlainActionFuture.newFuture();
        future.onResponse(searchResponse);
        when(client.search(request)).thenReturn(future);

        CountDownLatch latch = new CountDownLatch(1);
        LatchedActionListener<SearchResponse> latchedActionListener = new LatchedActionListener<>(actionListener, latch);
        transportSearchAgentAction.doExecute(null, request, latchedActionListener);
        latch.await(500, TimeUnit.MILLISECONDS);

        verify(client, times(1)).search(eq(request));
        verify(actionListener, times(1)).onResponse(any(SearchResponse.class));
    }

    @Test
    public void testSearchException() throws InterruptedException {
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
        sourceBuilder.query(QueryBuilders.termQuery("field", "value")); // Simulate user query
        SearchRequest request = new SearchRequest("my_index").source(sourceBuilder);

        PlainActionFuture<SearchResponse> future = PlainActionFuture.newFuture();
        future.onFailure(new Exception("failed to search the agent index"));
        when(client.search(request)).thenReturn(future);

        CountDownLatch latch = new CountDownLatch(1);
        LatchedActionListener<SearchResponse> latchedActionListener = new LatchedActionListener<>(actionListener, latch);
        transportSearchAgentAction.doExecute(null, request, latchedActionListener);
        latch.await(500, TimeUnit.MILLISECONDS);

        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("Fail to search agent", argumentCaptor.getValue().getMessage());
    }

    @Test
    public void testSearchThrowsException() {
        // Mock the client to throw an exception when the search method is called
        doThrow(new RuntimeException("Search failed")).when(client).search(any(SearchRequest.class), any());

        // Create a search request
        SearchRequest searchRequest = new SearchRequest();

        // Execute the action
        transportSearchAgentAction.doExecute(null, searchRequest, actionListener);

        // Verify that the actionListener's onFailure method was called
        verify(actionListener, times(1)).onFailure(any(RuntimeException.class));
    }

    @Test
    public void testDoExecute_MultiTenancyEnabled_TenantFilteringNotEnabled() throws InterruptedException {
        when(mlFeatureEnabledSetting.isMultiTenancyEnabled()).thenReturn(true);

        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
        sourceBuilder.query(QueryBuilders.termQuery("field", "value")); // Simulate user query
        SearchRequest request = new SearchRequest("my_index").source(sourceBuilder);

        CountDownLatch latch = new CountDownLatch(1);
        LatchedActionListener<SearchResponse> latchedActionListener = new LatchedActionListener<>(actionListener, latch);
        transportSearchAgentAction.doExecute(null, request, latchedActionListener);
        latch.await(500, TimeUnit.MILLISECONDS);

        ArgumentCaptor<OpenSearchStatusException> captor = ArgumentCaptor.forClass(OpenSearchStatusException.class);
        verify(actionListener).onFailure(captor.capture());
        OpenSearchStatusException exception = captor.getValue();
        assertEquals(RestStatus.INTERNAL_SERVER_ERROR, exception.status());
        assertEquals("Failed to get the tenant ID from the search request", exception.getMessage());
    }

    @Test
    public void testDoExecute_MultiTenancyEnabled_TenantFilteringEnabled() throws InterruptedException {
        when(mlFeatureEnabledSetting.isMultiTenancyEnabled()).thenReturn(true);

        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
        sourceBuilder.query(QueryBuilders.termQuery("field", "value")); // Simulate user query
        SearchRequest request = new SearchRequest("my_index").source(sourceBuilder);
        sourceBuilder.query(QueryBuilders.termQuery(TENANT_ID, "123456"));

        PlainActionFuture<SearchResponse> future = PlainActionFuture.newFuture();
        future.onResponse(searchResponse);
        when(client.search(any(SearchRequest.class))).thenReturn(future);

        CountDownLatch latch = new CountDownLatch(1);
        LatchedActionListener<SearchResponse> latchedActionListener = new LatchedActionListener<>(actionListener, latch);
        transportSearchAgentAction.doExecute(null, request, latchedActionListener);
        latch.await(500, TimeUnit.MILLISECONDS);
        verify(actionListener).onResponse(any(SearchResponse.class));
    }
}
