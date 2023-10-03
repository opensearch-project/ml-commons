/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.tasks;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.Before;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.action.ActionListener;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.client.Client;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.ml.helper.ModelAccessControlHelper;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.TransportService;

public class SearchTaskTransportActionTests extends OpenSearchTestCase {
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

    SearchTaskTransportAction searchTaskTransportAction;

    @Mock
    private ModelAccessControlHelper modelAccessControlHelper;

    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);
        searchTaskTransportAction = new SearchTaskTransportAction(transportService, actionFilters, client);
        ThreadPool threadPool = mock(ThreadPool.class);
        when(client.threadPool()).thenReturn(threadPool);
        ThreadContext threadContext = new ThreadContext(Settings.EMPTY);
        when(threadPool.getThreadContext()).thenReturn(threadContext);
    }

    public void test_DoExecute() {
        searchTaskTransportAction.doExecute(null, searchRequest, actionListener);
        verify(client).search(eq(searchRequest), any());
    }
}
