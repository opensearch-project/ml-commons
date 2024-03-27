/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.agents;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.client.Client;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.core.action.ActionListener;
import org.opensearch.index.query.QueryBuilders;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.TransportService;

public class TransportSearchAgentActionTests extends OpenSearchTestCase {
    @Mock
    Client client;

    @Mock
    TransportService transportService;

    @Mock
    ActionFilters actionFilters;

    @Mock
    ActionListener<SearchResponse> actionListener;

    TransportSearchAgentAction transportSearchAgentAction;

    @Mock
    SearchResponse mockedSearchResponse;

    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);
        transportSearchAgentAction = new TransportSearchAgentAction(transportService, actionFilters, client);
        ThreadPool threadPool = mock(ThreadPool.class);
        when(client.threadPool()).thenReturn(threadPool);
        ThreadContext threadContext = new ThreadContext(Settings.EMPTY);
        when(threadPool.getThreadContext()).thenReturn(threadContext);
    }

    @Test
    public void testDoExecuteWithEmptyQuery() {
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
        SearchRequest request = new SearchRequest("my_index").source(sourceBuilder);

        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(1);
            listener.onResponse(mockedSearchResponse);
            return null;
        }).when(client).search(eq(request), any());

        transportSearchAgentAction.doExecute(null, request, actionListener);

        verify(client, times(1)).search(eq(request), any());
        verify(actionListener, times(1)).onResponse(eq(mockedSearchResponse));
    }

    @Test
    public void testDoExecuteWithNonEmptyQuery() {
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
        sourceBuilder.query(QueryBuilders.matchAllQuery());
        SearchRequest request = new SearchRequest("my_index").source(sourceBuilder);

        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(1);
            listener.onResponse(mockedSearchResponse);
            return null;
        }).when(client).search(eq(request), any());

        transportSearchAgentAction.doExecute(null, request, actionListener);

        verify(client, times(1)).search(eq(request), any());
        verify(actionListener, times(1)).onResponse(eq(mockedSearchResponse));
    }

    @Test
    public void testDoExecuteOnFailure() {
        SearchRequest request = new SearchRequest("my_index");

        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(1);
            listener.onFailure(new Exception("test exception"));
            return null;
        }).when(client).search(eq(request), any());

        transportSearchAgentAction.doExecute(null, request, actionListener);

        verify(client, times(1)).search(eq(request), any());
        verify(actionListener, times(1)).onFailure(any(Exception.class));
    }

    @Test
    public void testSearchWithHiddenField() {
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
        sourceBuilder.query(QueryBuilders.termQuery("field", "value")); // Simulate user query
        SearchRequest request = new SearchRequest("my_index").source(sourceBuilder);

        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(1);
            listener.onResponse(mockedSearchResponse);
            return null;
        }).when(client).search(eq(request), any());

        transportSearchAgentAction.doExecute(null, request, actionListener);

        verify(client, times(1)).search(eq(request), any());
        verify(actionListener, times(1)).onResponse(eq(mockedSearchResponse));
    }

    @Test
    public void testSearchException() {
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
        sourceBuilder.query(QueryBuilders.termQuery("field", "value")); // Simulate user query
        SearchRequest request = new SearchRequest("my_index").source(sourceBuilder);

        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(1);
            listener.onFailure(new Exception("failed to search the agent index"));
            return null;
        }).when(client).search(eq(request), any());

        transportSearchAgentAction.doExecute(null, request, actionListener);

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

        // Execute the action
        transportSearchAgentAction.doExecute(null, searchRequest, actionListener);

        // Verify that the actionListener's onFailure method was called
        verify(actionListener, times(1)).onFailure(any(RuntimeException.class));
    }
}
