/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.tasks;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.opensearch.ml.plugin.MachineLearningPlugin.GENERAL_THREAD_POOL;
import static org.opensearch.ml.plugin.MachineLearningPlugin.ML_THREAD_POOL_PREFIX;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.lucene.search.TotalHits;
import org.junit.AfterClass;
import org.junit.Before;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
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
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.ml.common.transport.search.MLSearchActionRequest;
import org.opensearch.ml.helper.ModelAccessControlHelper;
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

public class SearchTaskTransportActionTests extends OpenSearchTestCase {
    @Mock
    Client client;
    SdkClient sdkClient;

    SearchResponse searchResponse;

    @Mock
    private NamedXContentRegistry xContentRegistry;

    @Mock
    TransportService transportService;

    @Mock
    ActionFilters actionFilters;

    @Mock
    private MLFeatureEnabledSetting mlFeatureEnabledSetting;

    @Mock
    SearchRequest searchRequest;

    @Mock
    ActionListener<SearchResponse> actionListener;

    SearchTaskTransportAction searchTaskTransportAction;

    @Mock
    private ModelAccessControlHelper modelAccessControlHelper;

    private static final TestThreadPool testThreadPool = new TestThreadPool(
        SearchTaskTransportActionTests.class.getName(),
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
        searchTaskTransportAction = new SearchTaskTransportAction(
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

    public void test_DoExecute() throws InterruptedException {
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
        SearchRequest request = new SearchRequest("my_index").source(sourceBuilder);
        MLSearchActionRequest mlSearchActionRequest = new MLSearchActionRequest(request, null);
        PlainActionFuture<SearchResponse> future = PlainActionFuture.newFuture();
        future.onResponse(searchResponse);
        when(client.search(request)).thenReturn(future);

        CountDownLatch latch = new CountDownLatch(1);
        LatchedActionListener<SearchResponse> latchedActionListener = new LatchedActionListener<>(actionListener, latch);
        searchTaskTransportAction.doExecute(null, mlSearchActionRequest, latchedActionListener);
        latch.await(500, TimeUnit.MILLISECONDS);

        verify(client, times(1)).search(eq(request));
        verify(actionListener, times(1)).onResponse(any(SearchResponse.class));
    }
}
