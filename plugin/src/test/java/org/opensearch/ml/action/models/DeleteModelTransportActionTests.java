/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.models;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.io.IOException;

import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.ExpectedException;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.action.ActionListener;
import org.opensearch.action.delete.DeleteResponse;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.client.Client;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.ml.common.transport.model.MLModelDeleteRequest;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.TransportService;

public class DeleteModelTransportActionTests extends OpenSearchTestCase {
    @Mock
    ThreadPool threadPool;

    @Mock
    Client client;

    @Mock
    TransportService transportService;

    @Mock
    ActionFilters actionFilters;

    @Mock
    ActionListener<DeleteResponse> actionListener;

    @Mock
    DeleteResponse deleteResponse;

    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    DeleteModelTransportAction deleteModelTransportAction;
    MLModelDeleteRequest mlModelDeleteRequest;
    ThreadContext threadContext;

    @Before
    public void setup() throws IOException {
        MockitoAnnotations.openMocks(this);

        mlModelDeleteRequest = MLModelDeleteRequest.builder().modelId("test_id").build();
        deleteModelTransportAction = spy(new DeleteModelTransportAction(transportService, actionFilters, client));

        Settings settings = Settings.builder().build();
        threadContext = new ThreadContext(settings);
        when(client.threadPool()).thenReturn(threadPool);
        when(threadPool.getThreadContext()).thenReturn(threadContext);
    }

    public void testDeleteModel_Success() {
        doAnswer(invocation -> {
            ActionListener<DeleteResponse> listener = invocation.getArgument(1);
            listener.onResponse(deleteResponse);
            return null;
        }).when(client).delete(any(), any());

        deleteModelTransportAction.doExecute(null, mlModelDeleteRequest, actionListener);
        verify(actionListener).onResponse(deleteResponse);
    }

    public void testDeleteModel_RuntimeException() {
        doAnswer(invocation -> {
            ActionListener<DeleteResponse> listener = invocation.getArgument(1);
            listener.onFailure(new RuntimeException("errorMessage"));
            return null;
        }).when(client).delete(any(), any());

        deleteModelTransportAction.doExecute(null, mlModelDeleteRequest, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("errorMessage", argumentCaptor.getValue().getMessage());
    }

    public void testDeleteModel_ThreadContextError() {
        when(threadPool.getThreadContext()).thenThrow(new RuntimeException("thread context error"));
        deleteModelTransportAction.doExecute(null, mlModelDeleteRequest, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("thread context error", argumentCaptor.getValue().getMessage());
    }
}
