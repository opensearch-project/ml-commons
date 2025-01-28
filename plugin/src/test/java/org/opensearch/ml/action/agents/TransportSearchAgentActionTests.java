/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.agents;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.opensearch.ml.common.CommonValue.TENANT_ID_FIELD;

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
import org.opensearch.client.Client;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.index.query.QueryBuilders;
import org.opensearch.ml.common.transport.search.MLSearchActionRequest;
import org.opensearch.ml.settings.MLFeatureEnabledSetting;
import org.opensearch.remote.metadata.client.SdkClient;
import org.opensearch.remote.metadata.client.impl.SdkClientFactory;
import org.opensearch.search.SearchHit;
import org.opensearch.search.SearchHits;
import org.opensearch.search.aggregations.InternalAggregations;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.search.internal.InternalSearchResponse;
import org.opensearch.test.OpenSearchTestCase;
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
    private MLFeatureEnabledSetting mlFeatureEnabledSetting;

    @Mock
    ActionListener<SearchResponse> actionListener;

    TransportSearchAgentAction transportSearchAgentAction;

    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);
        sdkClient = SdkClientFactory.createSdkClient(client, NamedXContentRegistry.EMPTY, Collections.emptyMap());
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

    @Test
    public void testDoExecuteWithEmptyQuery() {
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
        SearchRequest request = new SearchRequest("my_index").source(sourceBuilder);

        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(1);
            listener.onResponse(searchResponse);
            return null;
        }).when(client).search(eq(request), any());
        MLSearchActionRequest mlSearchActionRequest = new MLSearchActionRequest(request, null);

        transportSearchAgentAction.doExecute(null, mlSearchActionRequest, actionListener);
        verify(client, times(1)).search(eq(request), any());
        // Use ArgumentCaptor to capture the SearchResponse
        ArgumentCaptor<SearchResponse> responseCaptor = ArgumentCaptor.forClass(SearchResponse.class);
        // Capture the response passed to actionListener.onResponse
        verify(actionListener, times(1)).onResponse(responseCaptor.capture());
        // Assert that the captured response matches the expected values
        SearchResponse capturedResponse = responseCaptor.getValue();
        assertEquals(searchResponse.getHits().getTotalHits(), capturedResponse.getHits().getTotalHits());
        assertEquals(searchResponse.getHits().getHits().length, capturedResponse.getHits().getHits().length);
        assertEquals(searchResponse.status(), capturedResponse.status());

    }

    @Test
    public void testDoExecuteWithNonEmptyQuery() {
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
        sourceBuilder.query(QueryBuilders.matchAllQuery());
        SearchRequest request = new SearchRequest("my_index").source(sourceBuilder);
        MLSearchActionRequest mlSearchActionRequest = new MLSearchActionRequest(request, null);

        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(1);
            listener.onResponse(searchResponse);
            return null;
        }).when(client).search(eq(request), any());

        transportSearchAgentAction.doExecute(null, mlSearchActionRequest, actionListener);

        verify(client, times(1)).search(eq(request), any());
        // Use ArgumentCaptor to capture the SearchResponse
        ArgumentCaptor<SearchResponse> responseCaptor = ArgumentCaptor.forClass(SearchResponse.class);
        // Capture the response passed to actionListener.onResponse
        verify(actionListener, times(1)).onResponse(responseCaptor.capture());
        // Assert that the captured response matches the expected values
        SearchResponse capturedResponse = responseCaptor.getValue();
        assertEquals(searchResponse.getHits().getTotalHits(), capturedResponse.getHits().getTotalHits());
        assertEquals(searchResponse.getHits().getHits().length, capturedResponse.getHits().getHits().length);
        assertEquals(searchResponse.status(), capturedResponse.status());
    }

    @Test
    public void testDoExecuteOnFailure() {
        SearchRequest request = new SearchRequest("my_index");
        MLSearchActionRequest mlSearchActionRequest = new MLSearchActionRequest(request, null);
        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(1);
            listener.onFailure(new Exception("test exception"));
            return null;
        }).when(client).search(eq(request), any());

        transportSearchAgentAction.doExecute(null, mlSearchActionRequest, actionListener);

        verify(client, times(1)).search(eq(request), any());
        verify(actionListener, times(1)).onFailure(any(Exception.class));
    }

    @Test
    public void testSearchWithHiddenField() {
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
        sourceBuilder.query(QueryBuilders.termQuery("field", "value")); // Simulate user query
        SearchRequest request = new SearchRequest("my_index").source(sourceBuilder);
        MLSearchActionRequest mlSearchActionRequest = new MLSearchActionRequest(request, null);
        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(1);
            listener.onResponse(searchResponse);
            return null;
        }).when(client).search(eq(request), any());

        transportSearchAgentAction.doExecute(null, mlSearchActionRequest, actionListener);

        verify(client, times(1)).search(eq(request), any());
        // Use ArgumentCaptor to capture the SearchResponse
        ArgumentCaptor<SearchResponse> responseCaptor = ArgumentCaptor.forClass(SearchResponse.class);
        // Capture the response passed to actionListener.onResponse
        verify(actionListener, times(1)).onResponse(responseCaptor.capture());
        // Assert that the captured response matches the expected values
        SearchResponse capturedResponse = responseCaptor.getValue();
        assertEquals(searchResponse.getHits().getTotalHits(), capturedResponse.getHits().getTotalHits());
        assertEquals(searchResponse.getHits().getHits().length, capturedResponse.getHits().getHits().length);
        assertEquals(searchResponse.status(), capturedResponse.status());
    }

    @Test
    public void testSearchException() {
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
        sourceBuilder.query(QueryBuilders.termQuery("field", "value")); // Simulate user query
        SearchRequest request = new SearchRequest("my_index").source(sourceBuilder);
        MLSearchActionRequest mlSearchActionRequest = new MLSearchActionRequest(request, null);
        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(1);
            listener.onFailure(new Exception("failed to search the agent index"));
            return null;
        }).when(client).search(eq(request), any());

        transportSearchAgentAction.doExecute(null, mlSearchActionRequest, actionListener);

        ArgumentCaptor<RuntimeException> argumentCaptor = ArgumentCaptor.forClass(RuntimeException.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("Fail to search agent", argumentCaptor.getValue().getMessage());
    }

    @Test
    public void testSearchThrowsException() {
        // Mock the client to throw an exception when the search method is called
        doThrow(new RuntimeException("Search failed")).when(client).search(any(SearchRequest.class), any());

        // Create a search request
        SearchRequest searchRequest = new SearchRequest();
        MLSearchActionRequest mlSearchActionRequest = new MLSearchActionRequest(searchRequest, null);

        // Execute the action
        transportSearchAgentAction.doExecute(null, mlSearchActionRequest, actionListener);

        // Verify that the actionListener's onFailure method was called
        verify(actionListener, times(1)).onFailure(any(RuntimeException.class));
    }

    @Test
    public void testDoExecute_MultiTenancyEnabled_TenantFilteringNotEnabled() throws InterruptedException {
        when(mlFeatureEnabledSetting.isMultiTenancyEnabled()).thenReturn(true);

        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
        sourceBuilder.query(QueryBuilders.termQuery("field", "value")); // Simulate user query
        SearchRequest request = new SearchRequest("my_index").source(sourceBuilder);

        MLSearchActionRequest mlSearchActionRequest = new MLSearchActionRequest(request, null);

        // Execute the action
        transportSearchAgentAction.doExecute(null, mlSearchActionRequest, actionListener);

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
        sourceBuilder.query(QueryBuilders.termQuery(TENANT_ID_FIELD, "123456"));
        MLSearchActionRequest mlSearchActionRequest = new MLSearchActionRequest(request, "123456");

        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(1);
            listener.onResponse(searchResponse);
            return null;
        }).when(client).search(any(), any());

        // Execute the action
        transportSearchAgentAction.doExecute(null, mlSearchActionRequest, actionListener);
        verify(actionListener).onResponse(any(SearchResponse.class));
    }
}
