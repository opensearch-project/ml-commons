/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.model_group;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.Before;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.client.Client;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.commons.ConfigConstants;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.ml.helper.ModelAccessControlHelper;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.TransportService;

public class SearchModelGroupTransportActionTests extends OpenSearchTestCase {
    @Mock
    Client client;

    @Mock
    NamedXContentRegistry namedXContentRegistry;

    @Mock
    TransportService transportService;

    @Mock
    ActionFilters actionFilters;

    @Mock
    SearchRequest searchRequest;

    @Mock
    ActionListener<SearchResponse> actionListener;

    @Mock
    ThreadPool threadPool;

    @Mock
    ClusterService clusterService;
    SearchModelGroupTransportAction searchModelGroupTransportAction;

    @Mock
    private ModelAccessControlHelper modelAccessControlHelper;
    ThreadContext threadContext;

    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);
        searchModelGroupTransportAction = new SearchModelGroupTransportAction(
            transportService,
            actionFilters,
            client,
            clusterService,
            modelAccessControlHelper
        );

        Settings settings = Settings.builder().build();
        threadContext = new ThreadContext(settings);
        threadContext.putTransient(ConfigConstants.OPENSEARCH_SECURITY_USER_INFO_THREAD_CONTEXT, "alex|IT,HR|engineering,operations");
        when(client.threadPool()).thenReturn(threadPool);
        when(threadPool.getThreadContext()).thenReturn(threadContext);
    }

    public void test_DoExecute() {
        when(modelAccessControlHelper.skipModelAccessControl(any())).thenReturn(false);
        searchModelGroupTransportAction.doExecute(null, searchRequest, actionListener);

        verify(modelAccessControlHelper).addUserBackendRolesFilter(any(), any());
        verify(client).search(any(), any());
    }

    public void test_skipModelAccessControlTrue() {
        when(modelAccessControlHelper.skipModelAccessControl(any())).thenReturn(true);
        searchModelGroupTransportAction.doExecute(null, searchRequest, actionListener);

        verify(client).search(any(), any());
    }

    public void test_ThreadContextError() {
        when(modelAccessControlHelper.skipModelAccessControl(any())).thenThrow(new RuntimeException("thread context error"));

        searchModelGroupTransportAction.doExecute(null, searchRequest, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("Fail to search", argumentCaptor.getValue().getMessage());
    }
}
