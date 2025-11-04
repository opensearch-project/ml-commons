/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.model_group;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.opensearch.ml.common.CommonValue.ML_MODEL_GROUP_RESOURCE_TYPE;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import org.apache.lucene.search.TotalHits;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.OpenSearchStatusException;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.action.search.ShardSearchFailure;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.commons.ConfigConstants;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.index.query.QueryBuilder;
import org.opensearch.index.query.QueryBuilders;
import org.opensearch.ml.common.CommonValue;
import org.opensearch.ml.common.ResourceSharingClientAccessor;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.transport.search.MLSearchActionRequest;
import org.opensearch.ml.helper.ModelAccessControlHelper;
import org.opensearch.remote.metadata.client.SdkClient;
import org.opensearch.remote.metadata.client.SearchDataObjectRequest;
import org.opensearch.remote.metadata.client.SearchDataObjectResponse;
import org.opensearch.search.SearchHit;
import org.opensearch.search.SearchHits;
import org.opensearch.search.aggregations.InternalAggregations;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.search.fetch.subphase.FetchSourceContext;
import org.opensearch.search.internal.InternalSearchResponse;
import org.opensearch.security.spi.resources.client.ResourceSharingClient;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.TransportService;
import org.opensearch.transport.client.Client;

public class SearchModelGroupTransportActionTests extends OpenSearchTestCase {

    @Mock
    Client client;
    @Mock
    SdkClient sdkClient;

    @Mock
    NamedXContentRegistry namedXContentRegistry;
    @Mock
    TransportService transportService;
    @Mock
    ActionFilters actionFilters;

    SearchSourceBuilder searchSourceBuilder;
    @Mock
    FetchSourceContext fetchSourceContext;

    @Mock
    ActionListener<SearchResponse> actionListener;
    @Mock
    ThreadPool threadPool;
    @Mock
    ClusterService clusterService;

    SearchModelGroupTransportAction searchModelGroupTransportAction;

    @Mock
    private ModelAccessControlHelper modelAccessControlHelper;
    @Mock
    private MLFeatureEnabledSetting mlFeatureEnabledSetting;

    ThreadContext threadContext;

    @Before
    @Override
    public void setUp() throws Exception {
        super.setUp();
        MockitoAnnotations.openMocks(this);

        searchModelGroupTransportAction = new SearchModelGroupTransportAction(
            transportService,
            actionFilters,
            client,
            sdkClient,
            clusterService,
            modelAccessControlHelper,
            mlFeatureEnabledSetting
        );

        Settings settings = Settings.builder().build();
        threadContext = new ThreadContext(settings);
        threadContext.putTransient(ConfigConstants.OPENSEARCH_SECURITY_USER_INFO_THREAD_CONTEXT, "alex|IT,HR|engineering,operations");
        when(client.threadPool()).thenReturn(threadPool);
        when(threadPool.getThreadContext()).thenReturn(threadContext);

        searchSourceBuilder = new SearchSourceBuilder();
        searchSourceBuilder.fetchSource(fetchSourceContext);
        when(fetchSourceContext.includes()).thenReturn(new String[] {});
        when(fetchSourceContext.excludes()).thenReturn(new String[] {});

        // By default, do not skip access control
        when(modelAccessControlHelper.skipModelAccessControl(any())).thenReturn(false);
        // Simplify the merged query for tests
        when(modelAccessControlHelper.mergeWithAccessFilter(any(QueryBuilder.class), any(Set.class)))
            .thenAnswer(inv -> QueryBuilders.termQuery("dummy", "value"));

        ResourceSharingClientAccessor.getInstance().setResourceSharingClient(null);
    }

    @Override
    public void tearDown() throws Exception {
        try {
            ResourceSharingClientAccessor.getInstance().setResourceSharingClient(null);
        } finally {
            super.tearDown();
        }
    }

    /** Helper: empty SDK response that can be converted to SearchResponse by the utils wrapper */
    private SearchDataObjectResponse emptySearchDataObjectResponse() {
        SearchHits hits = new SearchHits(new SearchHit[0], new TotalHits(0L, TotalHits.Relation.EQUAL_TO), Float.NaN);
        InternalSearchResponse internal = new InternalSearchResponse(hits, InternalAggregations.EMPTY, null, null, false, null, 0);
        SearchResponse osSearchResponse = new SearchResponse(
            internal,
            null,
            0,
            0,
            0,
            1,
            ShardSearchFailure.EMPTY_ARRAY,
            mock(SearchResponse.Clusters.class),
            null
        );

        SearchDataObjectResponse sdkResp = mock(SearchDataObjectResponse.class);
        try {
            when(sdkResp.searchResponse()).thenReturn(osSearchResponse);
        } catch (Throwable ignore) {}
        return sdkResp;
    }

    @Test
    public void test_DoExecute_success_callsSdkClient_andAddsBackendRoleFilter() {
        MLSearchActionRequest mlReq = new MLSearchActionRequest(new SearchRequest(new String[0], searchSourceBuilder), "tenant-x");

        CompletableFuture<SearchDataObjectResponse> future = new CompletableFuture<>();
        when(sdkClient.searchDataObjectAsync(any(SearchDataObjectRequest.class))).thenReturn(future);

        searchModelGroupTransportAction.doExecute(null, mlReq, actionListener);

        future.complete(emptySearchDataObjectResponse());

        verify(modelAccessControlHelper).addUserBackendRolesFilter(any(), any());
        verify(sdkClient).searchDataObjectAsync(any(SearchDataObjectRequest.class));
        verify(actionListener).onResponse(any(SearchResponse.class));
    }

    @Test
    public void test_DoExecute_exception_propagatesFailure() {
        MLSearchActionRequest mlReq = new MLSearchActionRequest(new SearchRequest(new String[0], searchSourceBuilder), "tenant-x");

        CompletableFuture<SearchDataObjectResponse> future = new CompletableFuture<>();
        when(sdkClient.searchDataObjectAsync(any(SearchDataObjectRequest.class))).thenReturn(future);

        searchModelGroupTransportAction.doExecute(null, mlReq, actionListener);

        future.completeExceptionally(new RuntimeException("search failed"));

        verify(modelAccessControlHelper).addUserBackendRolesFilter(any(), any());
        verify(sdkClient).searchDataObjectAsync(any(SearchDataObjectRequest.class));
        verify(actionListener).onFailure(any(RuntimeException.class));
    }

    @Test
    public void test_skipModelAccessControlTrue_stillCallsSdkClient() {
        when(modelAccessControlHelper.skipModelAccessControl(any())).thenReturn(true);

        MLSearchActionRequest mlReq = new MLSearchActionRequest(
                new SearchRequest(new String[0], searchSourceBuilder),
                "tenant-x"
        );

        CompletableFuture<SearchDataObjectResponse> future = new CompletableFuture<>();
        when(sdkClient.searchDataObjectAsync(any(SearchDataObjectRequest.class))).thenReturn(future);

        searchModelGroupTransportAction.doExecute(null, mlReq, actionListener);
        future.complete(emptySearchDataObjectResponse());

        verify(sdkClient).searchDataObjectAsync(any(SearchDataObjectRequest.class));
        verify(actionListener).onResponse(any(SearchResponse.class));
    }

    @Test
    public void test_ThreadContextError_wrappedWithMessage() {
        when(modelAccessControlHelper.skipModelAccessControl(any()))
                .thenThrow(new RuntimeException("thread context error"));

        MLSearchActionRequest mlReq = new MLSearchActionRequest(
                new SearchRequest(new String[0], searchSourceBuilder),
                "tenant-x"
        );

        searchModelGroupTransportAction.doExecute(null, mlReq, actionListener);

        ArgumentCaptor<Exception> captor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(captor.capture());
        assertEquals("Fail to search", captor.getValue().getMessage());
    }

    @Test
    public void testDoExecute_MultiTenancyEnabled_TenantFilteringNotEnabled() {
        when(mlFeatureEnabledSetting.isMultiTenancyEnabled()).thenReturn(true);

        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
        sourceBuilder.query(QueryBuilders.termQuery("field", "value"));
        SearchRequest request =
                new SearchRequest("my_index").source(sourceBuilder);

        MLSearchActionRequest mlReq = new MLSearchActionRequest(request, null);

        searchModelGroupTransportAction.doExecute(null, mlReq, actionListener);

        ArgumentCaptor<OpenSearchStatusException> captor = ArgumentCaptor.forClass(OpenSearchStatusException.class);
        verify(actionListener).onFailure(captor.capture());
        OpenSearchStatusException exception = captor.getValue();
        assertEquals(RestStatus.FORBIDDEN, exception.status());
        assertEquals("You don't have permission to access this resource", exception.getMessage());
    }

    @Test
    public void testDoExecute_MultiTenancyEnabled_TenantFilteringEnabled() {
        when(mlFeatureEnabledSetting.isMultiTenancyEnabled()).thenReturn(true);

        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
        sourceBuilder.query(QueryBuilders.termQuery("field", "value"));
        SearchRequest request =
                new SearchRequest("my_index").source(sourceBuilder);
        MLSearchActionRequest mlReq = new MLSearchActionRequest(request, "123456");

        CompletableFuture<SearchDataObjectResponse> future = new CompletableFuture<>();
        when(sdkClient.searchDataObjectAsync(any(SearchDataObjectRequest.class))).thenReturn(future);

        searchModelGroupTransportAction.doExecute(null, mlReq, actionListener);
        future.complete(emptySearchDataObjectResponse());

        verify(actionListener).onResponse(any(SearchResponse.class));
    }

    @Test
    public void testResourceSharingEnabled_successPath_filtersByAccessibleIds_andCallsSdkClient() {
        ResourceSharingClient rsc = mock(ResourceSharingClient.class);
        ResourceSharingClientAccessor.getInstance().setResourceSharingClient(rsc);
        when(rsc.isFeatureEnabledForType(any())).thenReturn(true);

        ArgumentCaptor<ActionListener<Set<String>>> rscListenerCaptor = ArgumentCaptor.forClass(ActionListener.class);

        CompletableFuture<SearchDataObjectResponse> future = new CompletableFuture<>();
        when(sdkClient.searchDataObjectAsync(any(SearchDataObjectRequest.class))).thenReturn(future);

        SearchSourceBuilder ssb = new SearchSourceBuilder();
        SearchRequest sr = new SearchRequest(new String[] { CommonValue.ML_MODEL_GROUP_INDEX }, ssb);
        MLSearchActionRequest req = new MLSearchActionRequest(sr, "tenant-1");

        searchModelGroupTransportAction.doExecute(null, req, actionListener);

        verify(rsc).getAccessibleResourceIds(eq(ML_MODEL_GROUP_RESOURCE_TYPE), rscListenerCaptor.capture());
        rscListenerCaptor.getValue().onResponse(Set.of("idA", "idB"));
        future.complete(emptySearchDataObjectResponse());

        verify(modelAccessControlHelper, atLeastOnce()).mergeWithAccessFilter(any(), eq(Set.of("idA", "idB")));

        ArgumentCaptor<SearchDataObjectRequest> sreq = ArgumentCaptor.forClass(SearchDataObjectRequest.class);
        verify(sdkClient).searchDataObjectAsync(sreq.capture());
        SearchDataObjectRequest sent = sreq.getValue();

        // Adjust these getters if your SDK uses record-style accessors
        assertArrayEquals(new String[] { CommonValue.ML_MODEL_GROUP_INDEX }, sent.indices());
        assertEquals("tenant-1", sent.tenantId());

        verify(actionListener).onResponse(any(SearchResponse.class));
    }

    @Test
    public void testResourceSharingEnabled_failSafePath_usesEmptySet_andCallsSdkClient() {
        ResourceSharingClient rsc = mock(ResourceSharingClient.class);
        ResourceSharingClientAccessor.getInstance().setResourceSharingClient(rsc);
        when(rsc.isFeatureEnabledForType(any())).thenReturn(true);

        ArgumentCaptor<ActionListener<Set<String>>> rscListenerCaptor = ArgumentCaptor.forClass(ActionListener.class);

        CompletableFuture<SearchDataObjectResponse> future = new CompletableFuture<>();
        when(sdkClient.searchDataObjectAsync(any(SearchDataObjectRequest.class))).thenReturn(future);

        SearchRequest sr = new SearchRequest(new String[] { CommonValue.ML_MODEL_GROUP_INDEX }, new SearchSourceBuilder());
        MLSearchActionRequest req = new MLSearchActionRequest(sr, "tenant-2");

        searchModelGroupTransportAction.doExecute(null, req, actionListener);

        // Simulate failure -> deny-all (empty set)
        verify(rsc).getAccessibleResourceIds(eq(ML_MODEL_GROUP_RESOURCE_TYPE), rscListenerCaptor.capture());
        rscListenerCaptor.getValue().onFailure(new RuntimeException("boom"));

        future.complete(emptySearchDataObjectResponse());

        verify(modelAccessControlHelper, atLeastOnce()).mergeWithAccessFilter(any(), eq(Collections.emptySet()));

        verify(sdkClient).searchDataObjectAsync(any(SearchDataObjectRequest.class));
        verify(actionListener).onResponse(any(SearchResponse.class));
    }

    @Test
    public void testResourceSharingEnabled_notMarkedAsProtectedType_skipsEvaluation() {
        ResourceSharingClient rsc = mock(ResourceSharingClient.class);
        ResourceSharingClientAccessor.getInstance().setResourceSharingClient(rsc);

        // Feature disabled for this type => skip resource sharing
        when(rsc.isFeatureEnabledForType(any())).thenReturn(false);

        CompletableFuture<SearchDataObjectResponse> future = new CompletableFuture<>();
        when(sdkClient.searchDataObjectAsync(any(SearchDataObjectRequest.class))).thenReturn(future);

        SearchRequest sr = new SearchRequest(new String[] { CommonValue.ML_MODEL_GROUP_INDEX }, new SearchSourceBuilder());
        MLSearchActionRequest req = new MLSearchActionRequest(sr, "tenant-2");

        searchModelGroupTransportAction.doExecute(null, req, actionListener);

        // Complete the search as if it returned normally
        future.complete(emptySearchDataObjectResponse());

        // Verify RSC is NOT called
        verify(rsc, never()).getAccessibleResourceIds(any(), any());

        // Verify we executed normal flow (i.e., used SDK and returned a response)
        verify(sdkClient).searchDataObjectAsync(any(SearchDataObjectRequest.class));
        verify(actionListener).onResponse(any(SearchResponse.class));
    }

    @Test
    public void testThreadContext_isRestored_afterExecution() {
        String key = "test-header";
        threadContext.putHeader(key, "original");

        SearchRequest sr = new SearchRequest(new String[] { CommonValue.ML_MODEL_GROUP_INDEX }, new SearchSourceBuilder());
        MLSearchActionRequest req = new MLSearchActionRequest(sr, "tenant-4");

        ResourceSharingClientAccessor.getInstance().setResourceSharingClient(null);

        CompletableFuture<SearchDataObjectResponse> future = new CompletableFuture<>();
        when(sdkClient.searchDataObjectAsync(any(SearchDataObjectRequest.class))).thenReturn(future);

        searchModelGroupTransportAction.doExecute(null, req, actionListener);
        future.complete(emptySearchDataObjectResponse());

        assertEquals("original", threadContext.getHeader(key));
        verify(actionListener).onResponse(any(SearchResponse.class));
    }
}
