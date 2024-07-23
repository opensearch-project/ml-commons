/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.opensearch.ml.action.connector;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
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
import org.opensearch.action.search.SearchResponse.Clusters;
import org.opensearch.action.search.ShardSearchFailure;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.PlainActionFuture;
import org.opensearch.client.Client;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.unit.TimeValue;
import org.opensearch.common.util.concurrent.OpenSearchExecutors;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.commons.ConfigConstants;
import org.opensearch.commons.authuser.User;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.ml.common.transport.search.MLSearchActionRequest;
import org.opensearch.ml.helper.ConnectorAccessControlHelper;
import org.opensearch.ml.sdkclient.SdkClientFactory;
import org.opensearch.ml.settings.MLFeatureEnabledSetting;
import org.opensearch.sdk.SdkClient;
import org.opensearch.search.SearchHit;
import org.opensearch.search.SearchHits;
import org.opensearch.search.aggregations.InternalAggregations;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.search.fetch.subphase.FetchSourceContext;
import org.opensearch.search.internal.InternalSearchResponse;
import org.opensearch.tasks.Task;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.threadpool.ScalingExecutorBuilder;
import org.opensearch.threadpool.TestThreadPool;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.TransportService;

public class SearchConnectorTransportActionTests extends OpenSearchTestCase {

    private static TestThreadPool testThreadPool = new TestThreadPool(
        SearchConnectorTransportActionTests.class.getName(),
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
    NamedXContentRegistry xContentRegistry;

    @Mock
    TransportService transportService;

    @Mock
    ActionFilters actionFilters;

    SearchRequest searchRequest;

    MLSearchActionRequest mlSearchActionRequest;

    SearchResponse searchResponse;

    SearchSourceBuilder searchSourceBuilder;

    @Mock
    FetchSourceContext fetchSourceContext;

    @Mock
    ActionListener<SearchResponse> actionListener;

    @Mock
    ThreadPool threadPool;

    @Mock
    private Task task;
    SearchConnectorTransportAction searchConnectorTransportAction;
    ThreadContext threadContext;
    @Mock
    private ConnectorAccessControlHelper connectorAccessControlHelper;
    @Mock
    private MLFeatureEnabledSetting mlFeatureEnabledSetting;

    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);

        Settings settings = Settings.builder().build();
        sdkClient = SdkClientFactory.createSdkClient(client, xContentRegistry, settings);
        when(mlFeatureEnabledSetting.isMultiTenancyEnabled()).thenReturn(false);
        searchConnectorTransportAction = new SearchConnectorTransportAction(
            transportService,
            actionFilters,
            client,
            sdkClient,
            connectorAccessControlHelper,
            mlFeatureEnabledSetting
        );

        threadContext = new ThreadContext(settings);
        when(client.threadPool()).thenReturn(threadPool);
        when(threadPool.getThreadContext()).thenReturn(threadContext);
        when(threadPool.executor(anyString())).thenReturn(testThreadPool.executor(GENERAL_THREAD_POOL));

        searchSourceBuilder = new SearchSourceBuilder();
        searchSourceBuilder.fetchSource(fetchSourceContext);
        searchRequest = new SearchRequest(new String[0], searchSourceBuilder);

        mlSearchActionRequest = new MLSearchActionRequest(searchRequest, null);

        when(fetchSourceContext.includes()).thenReturn(new String[] {});
        when(fetchSourceContext.excludes()).thenReturn(new String[] {});

        when(connectorAccessControlHelper.addUserBackendRolesFilter(any(), any(SearchSourceBuilder.class))).thenReturn(searchSourceBuilder);

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

    @Test
    public void test_doExecute_connectorAccessControlNotEnabled_searchSuccess() throws InterruptedException {
        String userString = "admin|role-1|all_access";
        threadContext.putTransient(ConfigConstants.OPENSEARCH_SECURITY_USER_INFO_THREAD_CONTEXT, userString);
        when(connectorAccessControlHelper.skipConnectorAccessControl(any(User.class))).thenReturn(true);

        PlainActionFuture<SearchResponse> future = PlainActionFuture.newFuture();
        future.onResponse(searchResponse);
        when(client.search(any(SearchRequest.class))).thenReturn(future);

        CountDownLatch latch = new CountDownLatch(1);
        LatchedActionListener<SearchResponse> latchedActionListener = new LatchedActionListener<>(actionListener, latch);
        searchConnectorTransportAction.doExecute(task, mlSearchActionRequest, latchedActionListener);
        latch.await(500, TimeUnit.MILLISECONDS);

        verify(actionListener).onResponse(any(SearchResponse.class));
    }

    @Test
    public void test_doExecute_connectorAccessControlEnabled_searchSuccess() throws InterruptedException {
        when(connectorAccessControlHelper.skipConnectorAccessControl(any(User.class))).thenReturn(false);

        PlainActionFuture<SearchResponse> future = PlainActionFuture.newFuture();
        future.onResponse(searchResponse);
        when(client.search(any(SearchRequest.class))).thenReturn(future);

        CountDownLatch latch = new CountDownLatch(1);
        LatchedActionListener<SearchResponse> latchedActionListener = new LatchedActionListener<>(actionListener, latch);
        searchConnectorTransportAction.doExecute(task, mlSearchActionRequest, latchedActionListener);
        latch.await(500, TimeUnit.MILLISECONDS);

        verify(actionListener).onResponse(any(SearchResponse.class));
    }

    @Test
    public void test_doExecute_exception() throws InterruptedException {
        PlainActionFuture<SearchResponse> future = PlainActionFuture.newFuture();
        future.onFailure(new RuntimeException("runtime exception"));
        when(client.search(any(SearchRequest.class))).thenReturn(future);

        CountDownLatch latch = new CountDownLatch(1);
        LatchedActionListener<SearchResponse> latchedActionListener = new LatchedActionListener<>(actionListener, latch);
        searchConnectorTransportAction.doExecute(task, mlSearchActionRequest, latchedActionListener);
        latch.await(500, TimeUnit.MILLISECONDS);

        verify(actionListener).onFailure(any(RuntimeException.class));
    }

    @Test
    public void testDoExecute_MultiTenancyEnabled_TenantFilteringNotEnabled() throws InterruptedException {
        when(mlFeatureEnabledSetting.isMultiTenancyEnabled()).thenReturn(true);

        CountDownLatch latch = new CountDownLatch(1);
        LatchedActionListener<SearchResponse> latchedActionListener = new LatchedActionListener<>(actionListener, latch);
        searchConnectorTransportAction.doExecute(task, mlSearchActionRequest, latchedActionListener);
        latch.await(500, TimeUnit.MILLISECONDS);

        ArgumentCaptor<OpenSearchStatusException> captor = ArgumentCaptor.forClass(OpenSearchStatusException.class);
        verify(actionListener).onFailure(captor.capture());
        OpenSearchStatusException exception = captor.getValue();
        assertEquals(RestStatus.FORBIDDEN, exception.status());
        assertEquals("You don't have permission to access this resource", exception.getMessage());
    }

    @Test
    public void testDoExecute_MultiTenancyEnabled_TenantFilteringEnabled() throws InterruptedException {
        when(mlFeatureEnabledSetting.isMultiTenancyEnabled()).thenReturn(true);
        mlSearchActionRequest = new MLSearchActionRequest(searchRequest, "123456");
        PlainActionFuture<SearchResponse> future = PlainActionFuture.newFuture();
        future.onResponse(searchResponse);
        when(client.search(any(SearchRequest.class))).thenReturn(future);

        CountDownLatch latch = new CountDownLatch(1);
        LatchedActionListener<SearchResponse> latchedActionListener = new LatchedActionListener<>(actionListener, latch);
        searchConnectorTransportAction.doExecute(task, mlSearchActionRequest, latchedActionListener);
        latch.await(500, TimeUnit.MILLISECONDS);
        verify(actionListener).onResponse(any(SearchResponse.class));
    }
}
