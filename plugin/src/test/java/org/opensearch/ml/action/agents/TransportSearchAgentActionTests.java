/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.agents;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.*;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.client.Client;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.core.action.ActionListener;
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
    SearchRequest searchRequest;

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

    public void test_DoExecute_OnResponse() {
        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(1);
            listener.onResponse(mockedSearchResponse);
            return null;
        }).when(client).search(any(), isA(ActionListener.class));
        transportSearchAgentAction.doExecute(null, searchRequest, actionListener);
        verify(client, times(1)).search(eq(searchRequest), any());
        verify(actionListener, times(1)).onResponse(eq(mockedSearchResponse));
    }

    @Test
    public void test_DoExecute_OnFailure() {
        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(1);
            listener.onFailure(new RuntimeException("runtime exception"));
            return null;
        }).when(client).search(any(), isA(ActionListener.class));
        transportSearchAgentAction.doExecute(null, searchRequest, actionListener);
        verify(client, times(1)).search(eq(searchRequest), any());
        verify(actionListener, times(1)).onFailure(any(RuntimeException.class));
    }
}
