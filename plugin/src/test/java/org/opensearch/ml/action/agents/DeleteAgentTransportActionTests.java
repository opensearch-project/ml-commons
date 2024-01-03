/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.opensearch.ml.action.agents;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.action.delete.DeleteResponse;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.client.Client;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.ml.common.transport.agent.MLAgentDeleteRequest;
import org.opensearch.tasks.Task;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.TransportService;

public class DeleteAgentTransportActionTests {

    @Mock
    private Client client;
    @Mock
    ThreadPool threadPool;
    @Mock
    private NamedXContentRegistry xContentRegistry;

    @Mock
    private TransportService transportService;

    @Mock
    private ActionFilters actionFilters;

    @InjectMocks
    private DeleteAgentTransportAction deleteAgentTransportAction;

    ThreadContext threadContext;

    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);
        deleteAgentTransportAction = new DeleteAgentTransportAction(transportService, actionFilters, client, xContentRegistry);
        Settings settings = Settings.builder().build();
        threadContext = new ThreadContext(settings);
        when(client.threadPool()).thenReturn(threadPool);
        when(threadPool.getThreadContext()).thenReturn(threadContext);
    }

    @Test
    public void testConstructor() {
        // Verify that the dependencies were correctly injected
        assertEquals(deleteAgentTransportAction.client, client);
        assertEquals(deleteAgentTransportAction.xContentRegistry, xContentRegistry);
    }

    @Test
    public void testDoExecute_Success() {
        String agentId = "test-agent-id";
        DeleteResponse deleteResponse = mock(DeleteResponse.class);

        ActionListener<DeleteResponse> actionListener = mock(ActionListener.class);

        MLAgentDeleteRequest deleteRequest = new MLAgentDeleteRequest(agentId);

        Task task = mock(Task.class);

        doAnswer(invocation -> {
            ActionListener<DeleteResponse> listener = invocation.getArgument(1);
            listener.onResponse(deleteResponse);
            return null;
        }).when(client).delete(any(), any());

        deleteAgentTransportAction.doExecute(task, deleteRequest, actionListener);
        ArgumentCaptor<DeleteResponse> argumentCaptor = ArgumentCaptor.forClass(DeleteResponse.class);
        verify(actionListener).onResponse(argumentCaptor.capture());
    }

    @Test
    public void testDoExecute_Failure() {
        String agentId = "test-non-existed-agent-id";

        ActionListener<DeleteResponse> actionListener = mock(ActionListener.class);

        MLAgentDeleteRequest deleteRequest = new MLAgentDeleteRequest(agentId);

        Task task = mock(Task.class);

        doAnswer(invocation -> {
            ActionListener<DeleteResponse> listener = invocation.getArgument(1);
            NullPointerException NullPointerException = new NullPointerException("Failed to delete ML Agent " + agentId);
            listener.onFailure(NullPointerException);
            return null;
        }).when(client).delete(any(), any());

        deleteAgentTransportAction.doExecute(task, deleteRequest, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("Failed to delete ML Agent " + agentId, argumentCaptor.getValue().getMessage());

    }

}
