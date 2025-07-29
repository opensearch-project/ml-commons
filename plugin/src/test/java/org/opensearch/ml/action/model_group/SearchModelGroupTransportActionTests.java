/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.model_group;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;

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
import org.opensearch.index.query.QueryBuilders;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.transport.search.MLSearchActionRequest;
import org.opensearch.ml.helper.ModelAccessControlHelper;
import org.opensearch.remote.metadata.client.SdkClient;
import org.opensearch.remote.metadata.client.impl.SdkClientFactory;
import org.opensearch.search.SearchHit;
import org.opensearch.search.SearchHits;
import org.opensearch.search.aggregations.InternalAggregations;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.search.fetch.subphase.FetchSourceContext;
import org.opensearch.search.internal.InternalSearchResponse;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.TransportService;
import org.opensearch.transport.client.Client;

public class SearchModelGroupTransportActionTests extends OpenSearchTestCase {
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

    SearchSourceBuilder searchSourceBuilder;

    MLSearchActionRequest mlSearchActionRequest;

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
    public void setup() {
        MockitoAnnotations.openMocks(this);
        sdkClient = SdkClientFactory.createSdkClient(client, NamedXContentRegistry.EMPTY, Collections.emptyMap());
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
        searchRequest = new SearchRequest(new String[0], searchSourceBuilder);
        mlSearchActionRequest = new MLSearchActionRequest(searchRequest, null);
        when(fetchSourceContext.includes()).thenReturn(new String[] {});
        when(fetchSourceContext.excludes()).thenReturn(new String[] {});

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

        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(1);
            listener.onResponse(searchResponse);
            return null;
        }).when(client).search(any(), any());
    }

    @Test
    public void test_DoExecute() {
        when(modelAccessControlHelper.skipModelAccessControl(any())).thenReturn(false);
        searchModelGroupTransportAction.doExecute(null, mlSearchActionRequest, actionListener);

        verify(modelAccessControlHelper).addUserBackendRolesFilter(any(), any());
        verify(client).search(any(), any());
    }

    @Test
    public void test_DoExecute_Exception() throws InterruptedException {

        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(1);
            listener.onFailure(new RuntimeException("search failed"));
            return null;
        }).when(client).search(any(), any());

        when(modelAccessControlHelper.skipModelAccessControl(any())).thenReturn(false);
        searchModelGroupTransportAction.doExecute(null, mlSearchActionRequest, actionListener);

        verify(modelAccessControlHelper).addUserBackendRolesFilter(any(), any());
        verify(client).search(any(), any());
        verify(actionListener).onFailure(any(RuntimeException.class));
    }

    @Test
    public void test_skipModelAccessControlTrue() {
        when(modelAccessControlHelper.skipModelAccessControl(any())).thenReturn(true);
        searchModelGroupTransportAction.doExecute(null, mlSearchActionRequest, actionListener);

        verify(client).search(any(), any());
    }

    @Test
    public void test_ThreadContextError() {
        when(modelAccessControlHelper.skipModelAccessControl(any())).thenThrow(new RuntimeException("thread context error"));

        searchModelGroupTransportAction.doExecute(null, mlSearchActionRequest, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("Fail to search", argumentCaptor.getValue().getMessage());
    }

    @Test
    public void testDoExecute_MultiTenancyEnabled_TenantFilteringNotEnabled() throws InterruptedException {
        when(mlFeatureEnabledSetting.isMultiTenancyEnabled()).thenReturn(true);

        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
        sourceBuilder.query(QueryBuilders.termQuery("field", "value")); // Simulate user query
        SearchRequest request = new SearchRequest("my_index").source(sourceBuilder);

        mlSearchActionRequest = new MLSearchActionRequest(request, null);

        searchModelGroupTransportAction.doExecute(null, mlSearchActionRequest, actionListener);

        ArgumentCaptor<OpenSearchStatusException> captor = ArgumentCaptor.forClass(OpenSearchStatusException.class);
        verify(actionListener).onFailure(captor.capture());
        OpenSearchStatusException exception = captor.getValue();
        assertEquals(RestStatus.FORBIDDEN, exception.status());
        assertEquals("You don't have permission to access this resource", exception.getMessage());
    }

    @Test
    public void testDoExecute_MultiTenancyEnabled_TenantFilteringEnabled() throws InterruptedException {
        when(mlFeatureEnabledSetting.isMultiTenancyEnabled()).thenReturn(true);

        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
        sourceBuilder.query(QueryBuilders.termQuery("field", "value")); // Simulate user query
        SearchRequest request = new SearchRequest("my_index").source(sourceBuilder);
        mlSearchActionRequest = new MLSearchActionRequest(request, "123456");

        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(1);
            listener.onResponse(searchResponse);
            return null;
        }).when(client).search(any(), any());

        searchModelGroupTransportAction.doExecute(null, mlSearchActionRequest, actionListener);
        verify(actionListener).onResponse(any(SearchResponse.class));
    }
}
