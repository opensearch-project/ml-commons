/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.remote;

import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.ExpectedException;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.action.ActionListener;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.client.Client;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.commons.ConfigConstants;
import org.opensearch.commons.authuser.User;
import org.opensearch.ml.helper.ConnectorAccessControlHelper;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.search.fetch.subphase.FetchSourceContext;
import org.opensearch.tasks.Task;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.TransportService;


import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class SearchConnectorTransportActionTests extends OpenSearchTestCase {

    @Mock
    Client client;

    @Mock
    TransportService transportService;

    @Mock
    ActionFilters actionFilters;

    @Mock
    SearchRequest searchRequest;

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

    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);
        searchConnectorTransportAction = new SearchConnectorTransportAction(transportService, actionFilters, client, connectorAccessControlHelper);

        Settings settings = Settings.builder().build();
        threadContext = new ThreadContext(settings);
        when(client.threadPool()).thenReturn(threadPool);
        when(threadPool.getThreadContext()).thenReturn(threadContext);

        searchSourceBuilder = new SearchSourceBuilder();
        searchSourceBuilder.fetchSource(fetchSourceContext);
        when(searchRequest.source()).thenReturn(searchSourceBuilder);
        when(fetchSourceContext.includes()).thenReturn(new String[]{});
        when(fetchSourceContext.excludes()).thenReturn(new String[]{});
    }

    public void test_doExecute_connectorAccessControlNotEnabled_searchSuccess() {
        String userString = "admin|role-1|all_access";
        threadContext.putTransient(ConfigConstants.OPENSEARCH_SECURITY_USER_INFO_THREAD_CONTEXT, userString);
        when(connectorAccessControlHelper.skipConnectorAccessControl(any(User.class))).thenReturn(true);
        SearchResponse searchResponse = mock(SearchResponse.class);
        doAnswer(invocation -> {
            ActionListener<SearchResponse> actionListener = invocation.getArgument(1);
            actionListener.onResponse(searchResponse);
            return null;
        }).when(client).search(any(SearchRequest.class), isA(ActionListener.class));
        searchConnectorTransportAction.doExecute(task, searchRequest, actionListener);
        verify(actionListener).onResponse(any(SearchResponse.class));
    }

    public void test_doExecute_connectorAccessControlEnabled_searchSuccess() {
        when(connectorAccessControlHelper.skipConnectorAccessControl(any(User.class))).thenReturn(false);
        SearchResponse searchResponse = mock(SearchResponse.class);
        doAnswer(invocation -> {
            ActionListener<SearchResponse> actionListener = invocation.getArgument(1);
            actionListener.onResponse(searchResponse);
            return null;
        }).when(client).search(any(SearchRequest.class), isA(ActionListener.class));
        searchConnectorTransportAction.doExecute(task, searchRequest, actionListener);
        verify(actionListener).onResponse(any(SearchResponse.class));
    }

    public void test_doExecute_exception() {
        when(searchRequest.source()).thenThrow(new RuntimeException("runtime exception"));
        searchConnectorTransportAction.doExecute(task, searchRequest, actionListener);
        verify(actionListener).onFailure(any(RuntimeException.class));
    }

   
}
