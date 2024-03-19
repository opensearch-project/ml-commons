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
import org.opensearch.OpenSearchException;
import org.opensearch.OpenSearchStatusException;
import org.opensearch.action.delete.DeleteResponse;
import org.opensearch.action.get.GetResponse;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.client.Client;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.common.bytes.BytesArray;
import org.opensearch.core.rest.RestStatus;
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
    ClusterService clusterService;

    @Mock
    private ActionFilters actionFilters;

    @InjectMocks
    private DeleteAgentTransportAction deleteAgentTransportAction;

    ThreadContext threadContext;

    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);
        deleteAgentTransportAction = spy(
            new DeleteAgentTransportAction(transportService, actionFilters, client, xContentRegistry, clusterService)
        );
        Settings settings = Settings.builder().build();
        threadContext = new ThreadContext(settings);
        when(client.threadPool()).thenReturn(threadPool);
        when(clusterService.getSettings()).thenReturn(settings);
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
        GetResponse getResponse = mock(GetResponse.class);

        ActionListener<DeleteResponse> actionListener = mock(ActionListener.class);

        MLAgentDeleteRequest deleteRequest = new MLAgentDeleteRequest(agentId);
        doReturn(true).when(deleteAgentTransportAction).isSuperAdminUserWrapper(clusterService, client);

        when(getResponse.isExists()).thenReturn(true);
        when(getResponse.getSourceAsBytesRef()).thenReturn(new BytesArray("{\"is_hidden\":true, \"name\":\"agent\", \"type\":\"flow\"}")); // Mock
        // agent
        // source

        Task task = mock(Task.class);

        doAnswer(invocation -> {
            ActionListener<GetResponse> listener = invocation.getArgument(1);
            listener.onResponse(getResponse);
            return null;
        }).when(client).get(any(), any());

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
        GetResponse getResponse = mock(GetResponse.class);
        when(getResponse.isExists()).thenReturn(true);
        when(getResponse.getSourceAsBytesRef()).thenReturn(new BytesArray("{\"is_hidden\":false, \"name\":\"agent\", \"type\":\"flow\"}")); // Mock
        // agent
        // source

        ActionListener<DeleteResponse> actionListener = mock(ActionListener.class);

        MLAgentDeleteRequest deleteRequest = new MLAgentDeleteRequest(agentId);

        Task task = mock(Task.class);

        doAnswer(invocation -> {
            ActionListener<GetResponse> listener = invocation.getArgument(1);
            listener.onResponse(getResponse);
            return null;
        }).when(client).get(any(), any());

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

    @Test
    public void testDoExecute_HiddenAgentSuperAdmin() {
        String agentId = "test-agent-id";
        DeleteResponse deleteResponse = mock(DeleteResponse.class);
        GetResponse getResponse = mock(GetResponse.class);

        ActionListener<DeleteResponse> actionListener = mock(ActionListener.class);

        MLAgentDeleteRequest deleteRequest = new MLAgentDeleteRequest(agentId);

        when(getResponse.isExists()).thenReturn(true);
        when(getResponse.getSourceAsBytesRef()).thenReturn(new BytesArray("{\"is_hidden\":true, \"name\":\"agent\", \"type\":\"flow\"}")); // Mock
        // agent
        // source

        Task task = mock(Task.class);

        doAnswer(invocation -> {
            ActionListener<GetResponse> listener = invocation.getArgument(1);
            listener.onResponse(getResponse);
            return null;
        }).when(client).get(any(), any());

        doAnswer(invocation -> {
            ActionListener<DeleteResponse> listener = invocation.getArgument(1);
            listener.onResponse(deleteResponse);
            return null;
        }).when(client).delete(any(), any());

        deleteAgentTransportAction.doExecute(task, deleteRequest, actionListener);
        ArgumentCaptor<OpenSearchException> argumentCaptor = ArgumentCaptor.forClass(OpenSearchException.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
    }

    @Test
    public void testDoExecute_HiddenAgentDeletionByNonSuperAdmin() {
        String agentId = "hidden-agent-id";
        GetResponse getResponse = mock(GetResponse.class);
        when(getResponse.isExists()).thenReturn(true);
        when(getResponse.getSourceAsBytesRef())
            .thenReturn(new BytesArray("{\"is_hidden\":true, \"name\":\"hidden-agent\", \"type\":\"flow\"}"));

        ActionListener<DeleteResponse> actionListener = mock(ActionListener.class);
        MLAgentDeleteRequest deleteRequest = new MLAgentDeleteRequest(agentId);
        doReturn(false).when(deleteAgentTransportAction).isSuperAdminUserWrapper(clusterService, client);

        Task task = mock(Task.class);

        doAnswer(invocation -> {
            ActionListener<GetResponse> listener = invocation.getArgument(1);
            listener.onResponse(getResponse);
            return null;
        }).when(client).get(any(), any());

        deleteAgentTransportAction.doExecute(task, deleteRequest, actionListener);

        ArgumentCaptor<OpenSearchStatusException> argumentCaptor = ArgumentCaptor.forClass(OpenSearchStatusException.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals(RestStatus.FORBIDDEN, argumentCaptor.getValue().status());
    }

    @Test
    public void testDoExecute_NonHiddenAgentDeletionByNonSuperAdmin() {
        String agentId = "non-hidden-agent-id";
        GetResponse getResponse = mock(GetResponse.class);
        DeleteResponse deleteResponse = mock(DeleteResponse.class);

        when(getResponse.isExists()).thenReturn(true);
        when(getResponse.getSourceAsBytesRef())
            .thenReturn(new BytesArray("{\"is_hidden\":false, \"name\":\"non-hidden-agent\", \"type\":\"flow\"}"));

        ActionListener<DeleteResponse> actionListener = mock(ActionListener.class);
        MLAgentDeleteRequest deleteRequest = new MLAgentDeleteRequest(agentId);
        doReturn(false).when(deleteAgentTransportAction).isSuperAdminUserWrapper(clusterService, client);

        Task task = mock(Task.class);

        doAnswer(invocation -> {
            ActionListener<GetResponse> listener = invocation.getArgument(1);
            listener.onResponse(getResponse);
            return null;
        }).when(client).get(any(), any());

        doAnswer(invocation -> {
            ActionListener<DeleteResponse> listener = invocation.getArgument(1);
            listener.onResponse(deleteResponse);
            return null;
        }).when(client).delete(any(), any());

        deleteAgentTransportAction.doExecute(task, deleteRequest, actionListener);

        verify(actionListener).onResponse(any(DeleteResponse.class));
    }

    @Test
    public void testDoExecute_GetFails() {
        String agentId = "test-agent-id";
        ActionListener<DeleteResponse> actionListener = mock(ActionListener.class);
        MLAgentDeleteRequest deleteRequest = new MLAgentDeleteRequest(agentId);

        Task task = mock(Task.class);
        Exception expectedException = new RuntimeException("Failed to fetch agent");

        doAnswer(invocation -> {
            ActionListener<GetResponse> listener = invocation.getArgument(1);
            listener.onFailure(expectedException);
            return null;
        }).when(client).get(any(), any());

        deleteAgentTransportAction.doExecute(task, deleteRequest, actionListener);

        verify(actionListener).onFailure(any(RuntimeException.class));
    }

    @Test
    public void testDoExecute_DeleteFails() {
        String agentId = "test-agent-id";
        GetResponse getResponse = mock(GetResponse.class);
        Exception expectedException = new RuntimeException("Deletion failed");

        ActionListener<DeleteResponse> actionListener = mock(ActionListener.class);

        MLAgentDeleteRequest deleteRequest = new MLAgentDeleteRequest(agentId);

        Task task = mock(Task.class);

        // Mock the GetResponse to simulate finding the agent
        when(getResponse.isExists()).thenReturn(true);
        when(getResponse.getSourceAsBytesRef()).thenReturn(new BytesArray("{\"is_hidden\":false, \"name\":\"agent\", \"type\":\"flow\"}"));

        // Mock the client.get() call to return the mocked GetResponse
        doAnswer(invocation -> {
            ActionListener<GetResponse> listener = invocation.getArgument(1);
            listener.onResponse(getResponse);
            return null;
        }).when(client).get(any(), any());

        // Mock the client.delete() call to throw an exception
        doAnswer(invocation -> {
            ActionListener<DeleteResponse> listener = invocation.getArgument(1);
            listener.onFailure(expectedException);
            return null;
        }).when(client).delete(any(), any());

        // Execute the action
        deleteAgentTransportAction.doExecute(task, deleteRequest, actionListener);

        // Verify that actionListener.onFailure() was called with the expected exception
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("Deletion failed", argumentCaptor.getValue().getMessage());
    }
}
