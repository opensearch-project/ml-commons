/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.models;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.isA;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

import org.apache.lucene.search.TotalHits;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.OpenSearchStatusException;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.action.search.SearchResponseSections;
import org.opensearch.action.search.ShardSearchFailure;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.cluster.ClusterName;
import org.opensearch.cluster.ClusterState;
import org.opensearch.cluster.metadata.Metadata;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.commons.ConfigConstants;
import org.opensearch.commons.authuser.User;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.index.IndexNotFoundException;
import org.opensearch.index.query.QueryBuilders;
import org.opensearch.ml.action.handler.MLSearchHandler;
import org.opensearch.ml.common.CommonValue;
import org.opensearch.ml.common.ResourceSharingClientAccessor;
import org.opensearch.ml.common.exception.MLResourceNotFoundException;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.transport.search.MLSearchActionRequest;
import org.opensearch.ml.helper.ModelAccessControlHelper;
import org.opensearch.ml.utils.TestHelper;
import org.opensearch.remote.metadata.client.SdkClient;
import org.opensearch.remote.metadata.client.impl.SdkClientFactory;
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

public class SearchModelTransportActionTests extends OpenSearchTestCase {
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

    MLSearchActionRequest mlSearchActionRequest;

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

    @Mock
    private MLFeatureEnabledSetting mlFeatureEnabledSetting;

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);
        sdkClient = SdkClientFactory.createSdkClient(client, NamedXContentRegistry.EMPTY, Collections.emptyMap());
        mlSearchHandler = spy(
            new MLSearchHandler(client, namedXContentRegistry, modelAccessControlHelper, clusterService, mlFeatureEnabledSetting)
        );
        searchModelTransportAction = new SearchModelTransportAction(
            transportService,
            actionFilters,
            sdkClient,
            mlSearchHandler,
            mlFeatureEnabledSetting
        );

        Settings settings = Settings.builder().build();
        threadContext = new ThreadContext(settings);
        when(client.threadPool()).thenReturn(threadPool);
        when(threadPool.getThreadContext()).thenReturn(threadContext);

        searchSourceBuilder = new SearchSourceBuilder();
        searchSourceBuilder.fetchSource(fetchSourceContext);
        searchRequest = new SearchRequest(new String[0], searchSourceBuilder);
        mlSearchActionRequest = new MLSearchActionRequest(searchRequest, null);
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
            mock(SearchResponse.Clusters.class),
            null
        );
        ResourceSharingClientAccessor.getInstance().setResourceSharingClient(null);
    }

    public void test_DoExecute_admin() {
        when(modelAccessControlHelper.skipModelAccessControl(any())).thenReturn(true);
        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(1);
            listener.onResponse(searchResponse);
            return null;
        }).when(client).search(any(), any());
        searchModelTransportAction.doExecute(null, mlSearchActionRequest, actionListener);
        verify(mlSearchHandler).search(sdkClient, mlSearchActionRequest, null,  actionListener);
        verify(client, times(1)).search(any(), any());
    }

    public void test_DoExecute_addBackendRoles() throws IOException {
        SearchResponse searchResponse = createModelGroupSearchResponse();
        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(1);
            listener.onResponse(searchResponse);
            return null;
        }).when(client).search(any(), any());
        when(modelAccessControlHelper.createSearchSourceBuilder(any())).thenReturn(searchSourceBuilder);
        searchModelTransportAction.doExecute(null, mlSearchActionRequest, actionListener);
        verify(mlSearchHandler).search(sdkClient, mlSearchActionRequest, null, actionListener);
        verify(client, times(2)).search(any(), any());
    }

    public void test_DoExecute_addBackendRoles_without_groupIds() {
        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(1);
            listener.onResponse(searchResponse);
            return null;
        }).when(client).search(any(), isA(ActionListener.class));
        when(modelAccessControlHelper.createSearchSourceBuilder(any())).thenReturn(searchSourceBuilder);
        searchModelTransportAction.doExecute(null, mlSearchActionRequest, actionListener);
        verify(mlSearchHandler).search(sdkClient, mlSearchActionRequest, null, actionListener);
        verify(client, times(2)).search(any(), any());
    }

    public void test_DoExecute_addBackendRoles_exception() {
        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(1);
            listener.onFailure(new RuntimeException("runtime exception"));
            return null;
        }).when(client).search(any(), isA(ActionListener.class));
        when(modelAccessControlHelper.createSearchSourceBuilder(any())).thenReturn(searchSourceBuilder);
        searchModelTransportAction.doExecute(null, mlSearchActionRequest, actionListener);
        verify(mlSearchHandler).search(sdkClient, mlSearchActionRequest, null, actionListener);
        verify(client, times(1)).search(any(), any());
    }

    public void test_DoExecute_searchModel_before_model_creation_no_exception() {
        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(1);
            listener.onFailure(new IndexNotFoundException("index not found exception"));
            return null;
        }).when(client).search(any(), isA(ActionListener.class));
        when(modelAccessControlHelper.skipModelAccessControl(any())).thenReturn(true);
        searchModelTransportAction.doExecute(null, mlSearchActionRequest, actionListener);
        verify(mlSearchHandler).search(sdkClient, mlSearchActionRequest, null, actionListener);
        verify(client, times(1)).search(any(), any());
        verify(actionListener, times(0)).onFailure(any(IndexNotFoundException.class));
    }

    public void test_DoExecute_searchModel_before_model_creation_empty_search() {
        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(1);
            SearchHits hits = new SearchHits(new SearchHit[] {}, new TotalHits(0, TotalHits.Relation.EQUAL_TO), Float.NaN);
            SearchResponseSections searchSections = new SearchResponseSections(
                hits,
                InternalAggregations.EMPTY,
                null,
                false,
                false,
                null,
                1
            );
            final SearchResponse searchResponse = new SearchResponse(
                searchSections,
                null,
                1,
                1,
                0,
                11,
                ShardSearchFailure.EMPTY_ARRAY,
                SearchResponse.Clusters.EMPTY
            );
            listener.onResponse(searchResponse);
            return null;
        }).when(client).search(any(), isA(ActionListener.class));
        when(modelAccessControlHelper.skipModelAccessControl(any())).thenReturn(true);
        searchModelTransportAction.doExecute(null, mlSearchActionRequest, actionListener);
        verify(mlSearchHandler).search(sdkClient, mlSearchActionRequest, null, actionListener);
        verify(client, times(1)).search(any(), any());
        verify(actionListener, times(0)).onFailure(any(IndexNotFoundException.class));
        verify(actionListener, times(1)).onResponse(any(SearchResponse.class));
    }

    public void test_DoExecute_searchModel_MLResourceNotFoundException_exception() {
        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(1);
            listener.onFailure(new MLResourceNotFoundException("ml resource not found exception"));
            return null;
        }).when(client).search(any(), isA(ActionListener.class));
        when(modelAccessControlHelper.skipModelAccessControl(any())).thenReturn(true);
        searchModelTransportAction.doExecute(null, mlSearchActionRequest, actionListener);
        verify(mlSearchHandler).search(sdkClient, mlSearchActionRequest, null, actionListener);
        verify(client, times(1)).search(any(), any());
        verify(actionListener, times(1)).onFailure(any(OpenSearchStatusException.class));
    }

    public void test_DoExecute_addBackendRoles_boolQuery() throws IOException {
        SearchResponse searchResponse = createModelGroupSearchResponse();
        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(1);
            listener.onResponse(searchResponse);
            return null;
        }).when(client).search(any(), isA(ActionListener.class));
        when(modelAccessControlHelper.createSearchSourceBuilder(any())).thenReturn(searchSourceBuilder);
        searchRequest.source().query(QueryBuilders.boolQuery().must(QueryBuilders.matchQuery("name", "model_IT")));
        searchModelTransportAction.doExecute(null, mlSearchActionRequest, actionListener);
        verify(mlSearchHandler).search(sdkClient, mlSearchActionRequest, null, actionListener);
        verify(client, times(2)).search(any(), any());
    }

    public void test_DoExecute_addBackendRoles_termQuery() throws IOException {
        SearchResponse searchResponse = createModelGroupSearchResponse();
        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(1);
            listener.onResponse(searchResponse);
            return null;
        }).when(client).search(any(), isA(ActionListener.class));
        when(modelAccessControlHelper.createSearchSourceBuilder(any())).thenReturn(searchSourceBuilder);
        searchRequest.source().query(QueryBuilders.termQuery("name", "model_IT"));
        searchModelTransportAction.doExecute(null, mlSearchActionRequest, actionListener);
        verify(mlSearchHandler).search(sdkClient, mlSearchActionRequest, null, actionListener);
        verify(client, times(2)).search(any(), any());
    }

    @Test
    public void testDoExecute_MultiTenancyEnabled_TenantFilteringNotEnabled() throws InterruptedException {
        when(mlFeatureEnabledSetting.isMultiTenancyEnabled()).thenReturn(true);

        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
        sourceBuilder.query(QueryBuilders.termQuery("field", "value")); // Simulate user query
        SearchRequest request = new SearchRequest("my_index").source(sourceBuilder);
        mlSearchActionRequest = new MLSearchActionRequest(request, null);

        searchModelTransportAction.doExecute(null, mlSearchActionRequest, actionListener);

        ArgumentCaptor<OpenSearchStatusException> captor = ArgumentCaptor.forClass(OpenSearchStatusException.class);
        verify(actionListener).onFailure(captor.capture());
        OpenSearchStatusException exception = captor.getValue();
        assertEquals(RestStatus.FORBIDDEN, exception.status());
        assertEquals("You don't have permission to access this resource", exception.getMessage());
    }

    @Test
    public void testDoExecute_MultiTenancyEnabled_TenantFilteringEnabled() throws InterruptedException, IOException {
        when(mlFeatureEnabledSetting.isMultiTenancyEnabled()).thenReturn(true);

        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(1);
            listener.onResponse(searchResponse);
            return null;
        }).when(client).search(any(), any());

        when(modelAccessControlHelper.createSearchSourceBuilder(any())).thenReturn(searchSourceBuilder);
        searchRequest.source().query(QueryBuilders.termQuery("name", "model_IT"));
        mlSearchActionRequest = new MLSearchActionRequest(searchRequest, "123456");

        searchModelTransportAction.doExecute(null, mlSearchActionRequest, actionListener);

        verify(mlSearchHandler).search(sdkClient, mlSearchActionRequest, "123456", actionListener);
        verify(client, times(2)).search(any(), any());
    }

    @Test
    public void test_RSC_featureEnabled_typeEnabled_callsGetAccessibleIds() throws Exception {
        ResourceSharingClient rsc = mock(ResourceSharingClient.class);
        ResourceSharingClientAccessor.getInstance().setResourceSharingClient(rsc);
        // Feature enabled for this type => apply resource sharing
        when(rsc.isFeatureEnabledForType(any())).thenReturn(true);

        var user = new User();

        threadContext.putTransient(ConfigConstants.OPENSEARCH_SECURITY_USER_INFO_THREAD_CONTEXT, user.toString());
        // model access control enabled & index exists
        when(modelAccessControlHelper.modelAccessControlEnabled()).thenReturn(true);

        // When RSC asks for accessible IDs, capture listener and respond
        ArgumentCaptor<ActionListener<Set<String>>> rscListenerCaptor = ArgumentCaptor.forClass(ActionListener.class);
        doAnswer(inv -> {
            ActionListener<Set<String>> l = inv.getArgument(1);
            // Simulate async success with some accessible IDs
            l.onResponse(Set.of("model_group_IT"));
            return null;
        }).when(rsc).getAccessibleResourceIds(eq(CommonValue.ML_MODEL_GROUP_RESOURCE_TYPE), rscListenerCaptor.capture());

        // The final remote search goes through client.search(...); return a normal response
        doAnswer(inv -> {
            ActionListener<SearchResponse> l = inv.getArgument(1);
            l.onResponse(searchResponse);
            return null;
        }).when(client).search(any(), any());

        SearchRequest req = new SearchRequest(new String[] { "ml_model" }, new SearchSourceBuilder());
        mlSearchActionRequest = new MLSearchActionRequest(req, "tenant-1");

        searchModelTransportAction.doExecute(null, mlSearchActionRequest, actionListener);

        // Verify RSC path was taken
        verify(rsc, times(1)).getAccessibleResourceIds(eq(CommonValue.ML_MODEL_GROUP_RESOURCE_TYPE), any());

        // Verify we executed the final search and returned
        verify(client, times(1)).search(any(), any());
        verify(actionListener, times(1)).onResponse(any(SearchResponse.class));

    }

    @Test
    public void test_RSC_featureEnabled_typeDisabled_skipsRSC() throws Exception {
        // Feature enabled globally but TYPE disabled → shouldUseResourceAuthz = false
        ResourceSharingClient rsc = mock(ResourceSharingClient.class);
        ResourceSharingClientAccessor.getInstance().setResourceSharingClient(rsc);
        when(rsc.isFeatureEnabledForType(any())).thenReturn(false);

        // Legacy path will query model-group index then models → 2 searches
        when(modelAccessControlHelper.modelAccessControlEnabled()).thenReturn(true);
        when(modelAccessControlHelper.createSearchSourceBuilder(any())).thenReturn(searchSourceBuilder);

        // First search (model-groups) returns one group to force the legacy gate path
        SearchResponse mgResponse = createModelGroupSearchResponse();
        doAnswer(inv -> {
            ActionListener<SearchResponse> l = inv.getArgument(1);
            l.onResponse(mgResponse);
            return null;
        }).doAnswer(inv -> { // second call: final models search
            ActionListener<SearchResponse> l = inv.getArgument(1);
            l.onResponse(searchResponse);
            return null;
        }).when(client).search(any(), any());

        searchModelTransportAction.doExecute(null, mlSearchActionRequest, actionListener);

        // RSC should not be called at all
        verify(rsc, times(0)).getAccessibleResourceIds(any(), any());

        // Legacy path did two searches
        verify(client, times(2)).search(any(), any());
        verify(actionListener, times(1)).onResponse(any(SearchResponse.class));
    }

    @Test
    public void test_RSC_featureDisabled_skipsRSC_entirely() throws Exception {
        // Entire feature disabled → skip resource sharing
        ResourceSharingClient rsc = mock(ResourceSharingClient.class);
        ResourceSharingClientAccessor.getInstance().setResourceSharingClient(rsc);
        when(rsc.isFeatureEnabledForType(any())).thenReturn(true);

        // With feature disabled, we go to legacy path. Make it return zero model-groups, then proceed.
        when(modelAccessControlHelper.modelAccessControlEnabled()).thenReturn(true);
        when(modelAccessControlHelper.createSearchSourceBuilder(any())).thenReturn(searchSourceBuilder);

        // First search returns 0 hits; second search still executes with rewritten "missing only"
        doAnswer(inv -> {
            ActionListener<SearchResponse> l = inv.getArgument(1);
            l.onResponse(searchResponse /* 0 hits already configured in setup() */);
            return null;
        }).doAnswer(inv -> {
            ActionListener<SearchResponse> l = inv.getArgument(1);
            l.onResponse(searchResponse);
            return null;
        }).when(client).search(any(), any());

        searchModelTransportAction.doExecute(null, mlSearchActionRequest, actionListener);

        // Verify RSC is never invoked
        verify(rsc, times(0)).getAccessibleResourceIds(any(), any());

        // Legacy path still performed searches
        verify(client, times(2)).search(any(), any());
        verify(actionListener, times(1)).onResponse(any(SearchResponse.class));
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
        return new SearchResponse(
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
}
