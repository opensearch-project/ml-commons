/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.models;

import static org.mockito.Mockito.*;

import org.junit.Before;
import org.junit.Ignore;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.OpenSearchStatusException;
import org.opensearch.action.ActionListener;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.client.Client;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.index.IndexNotFoundException;
import org.opensearch.ml.action.handler.MLSearchHandler;
import org.opensearch.ml.common.exception.MLException;
import org.opensearch.ml.helper.ModelAccessControlHelper;
import org.opensearch.rest.RestStatus;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.TransportService;

public class SearchModelTransportActionTests extends OpenSearchTestCase {
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

    MLSearchHandler mlSearchHandler;
    SearchModelTransportAction searchModelTransportAction;
    ThreadContext threadContext;

    @Mock
    private ModelAccessControlHelper modelAccessControlHelper;

    @Ignore
    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);
        mlSearchHandler = spy(new MLSearchHandler(client, namedXContentRegistry, modelAccessControlHelper));
        searchModelTransportAction = new SearchModelTransportAction(transportService, actionFilters, mlSearchHandler);

        Settings settings = Settings.builder().build();
        threadContext = new ThreadContext(settings);
        when(client.threadPool()).thenReturn(threadPool);
        when(threadPool.getThreadContext()).thenReturn(threadContext);
    }

    @Ignore
    public void test_DoExecute() {
        searchModelTransportAction.doExecute(null, searchRequest, actionListener);
        verify(mlSearchHandler).search(searchRequest, actionListener);
        verify(client).search(any(), any());
    }

    @Ignore
    public void test_IndexNotFoundException() {
        setupSearchMocks(new IndexNotFoundException("index not found"));

        searchModelTransportAction.doExecute(null, searchRequest, actionListener);
        verify(mlSearchHandler).search(searchRequest, actionListener);
        verify(client).search(any(), any());
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals(IndexNotFoundException.class, argumentCaptor.getValue().getClass());
    }

    @Ignore
    public void test_IllegalArgumentException() {
        setupSearchMocks(new IllegalArgumentException("illegal arguments"));

        searchModelTransportAction.doExecute(null, searchRequest, actionListener);
        verify(mlSearchHandler).search(searchRequest, actionListener);
        verify(client).search(any(), any());
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals(OpenSearchStatusException.class, argumentCaptor.getValue().getClass());
    }

    @Ignore
    public void test_OpenSearchStatusException() {
        setupSearchMocks(new OpenSearchStatusException("test error", RestStatus.CONFLICT, "args"));

        searchModelTransportAction.doExecute(null, searchRequest, actionListener);
        verify(mlSearchHandler).search(searchRequest, actionListener);
        verify(client).search(any(), any());
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals(OpenSearchStatusException.class, argumentCaptor.getValue().getClass());
    }

    @Ignore
    public void test_CauseByMLException() {
        Exception exception = new Exception();
        exception.initCause(new MLException("ml exception"));
        setupSearchMocks(exception);

        searchModelTransportAction.doExecute(null, searchRequest, actionListener);
        verify(mlSearchHandler).search(searchRequest, actionListener);
        verify(client).search(any(), any());
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals(OpenSearchStatusException.class, argumentCaptor.getValue().getClass());
    }

    @Ignore
    public void test_CauseByInvalidIndexNameException() {
        Exception exception = new Exception();
        exception.initCause(new IndexNotFoundException("Index not Found"));
        setupSearchMocks(exception);

        searchModelTransportAction.doExecute(null, searchRequest, actionListener);
        verify(mlSearchHandler).search(searchRequest, actionListener);
        verify(client).search(any(), any());
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals(IndexNotFoundException.class, argumentCaptor.getValue().getClass());
    }

    @Ignore
    private void setupSearchMocks(Exception exception) {
        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(1);
            listener.onFailure(exception);
            return null;
        }).when(client).search(any(), any());
    }
}
