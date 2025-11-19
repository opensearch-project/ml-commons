/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.opensearch.ml.action.agents;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.opensearch.ml.common.CommonValue.ML_AGENT_INDEX;

import java.io.IOException;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.opensearch.OpenSearchException;
import org.opensearch.OpenSearchStatusException;
import org.opensearch.action.delete.DeleteResponse;
import org.opensearch.action.get.GetResponse;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.common.xcontent.XContentFactory;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.common.bytes.BytesReference;
import org.opensearch.core.index.shard.ShardId;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.index.IndexNotFoundException;
import org.opensearch.index.get.GetResult;
import org.opensearch.ml.common.MLAgentType;
import org.opensearch.ml.common.agent.LLMSpec;
import org.opensearch.ml.common.agent.MLAgent;
import org.opensearch.ml.common.agent.MLMemorySpec;
import org.opensearch.ml.common.agent.MLToolSpec;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.transport.agent.MLAgentDeleteRequest;
import org.opensearch.remote.metadata.client.SdkClient;
import org.opensearch.remote.metadata.client.impl.SdkClientFactory;
import org.opensearch.tasks.Task;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.TransportService;
import org.opensearch.transport.client.Client;

public class DeleteAgentTransportActionTests {

    @Mock
    private Client client;
    SdkClient sdkClient;
    @Mock
    ThreadPool threadPool;
    @Mock
    private NamedXContentRegistry xContentRegistry;

    @Mock
    private TransportService transportService;

    @Mock
    ClusterService clusterService;

    DeleteResponse deleteResponse;

    @Mock
    private ActionFilters actionFilters;

    @InjectMocks
    private DeleteAgentTransportAction deleteAgentTransportAction;

    ThreadContext threadContext;

    @Mock
    private MLFeatureEnabledSetting mlFeatureEnabledSetting;

    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);
        sdkClient = SdkClientFactory.createSdkClient(client, NamedXContentRegistry.EMPTY, Collections.emptyMap());
        deleteAgentTransportAction = spy(
            new DeleteAgentTransportAction(
                transportService,
                actionFilters,
                client,
                sdkClient,
                xContentRegistry,
                clusterService,
                mlFeatureEnabledSetting
            )
        );
        Settings settings = Settings.builder().build();
        threadContext = new ThreadContext(settings);
        when(client.threadPool()).thenReturn(threadPool);
        when(clusterService.getSettings()).thenReturn(settings);
        when(threadPool.getThreadContext()).thenReturn(threadContext);

        deleteResponse = new DeleteResponse(new ShardId(ML_AGENT_INDEX, "_na_", 0), "AGENT_ID", 1, 0, 2, true);
    }

    @Test
    public void testConstructor() {
        // Verify that the dependencies were correctly injected
        assertEquals(deleteAgentTransportAction.client, client);
        assertEquals(deleteAgentTransportAction.xContentRegistry, xContentRegistry);
    }

    @Test
    public void testDoExecute_Success() throws IOException {
        String agentId = "test-agent-id";
        GetResponse getResponse = prepareMLAgent("AGENT_ID", false, null);
        MLAgentDeleteRequest deleteRequest = new MLAgentDeleteRequest(agentId, null);
        doReturn(true).when(deleteAgentTransportAction).isSuperAdminUserWrapper(clusterService, client);
        Task task = mock(Task.class);

        Mockito.doAnswer(invocation -> {
            // Extract the ActionListener argument from the method invocation
            ActionListener<GetResponse> listener = invocation.getArgument(1);
            // Trigger the onResponse method of the ActionListener with the mock response
            listener.onResponse(getResponse);
            return null;
        }).when(client).get(any(), any());

        Mockito.doAnswer(invocation -> {
            // Extract the ActionListener argument from the method invocation
            ActionListener<DeleteResponse> listener = invocation.getArgument(1);
            // Trigger the onResponse method of the ActionListener with the mock response
            listener.onResponse(deleteResponse);
            return null;
        }).when(client).delete(any(), any());

        ActionListener<DeleteResponse> actionListener = mock(ActionListener.class);

        deleteAgentTransportAction.doExecute(task, deleteRequest, actionListener);
        ArgumentCaptor<DeleteResponse> argumentCaptor = ArgumentCaptor.forClass(DeleteResponse.class);
        verify(actionListener).onResponse(argumentCaptor.capture());
    }

    @Test
    public void testDoExecute_Failure() throws IOException {
        String agentId = "test-non-existed-agent-id";
        GetResponse getResponse = prepareMLAgent(agentId, false, null);
        ActionListener<DeleteResponse> actionListener = mock(ActionListener.class);

        MLAgentDeleteRequest deleteRequest = new MLAgentDeleteRequest(agentId, null);

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
        assertEquals("Failed to delete data object from index .plugins-ml-agent", argumentCaptor.getValue().getMessage());
    }

    @Test
    public void testDoExecute_HiddenAgentSuperAdmin() throws IOException {
        String agentId = "test-agent-id";
        GetResponse getResponse = prepareMLAgent(agentId, true, null);
        ActionListener<DeleteResponse> actionListener = mock(ActionListener.class);
        MLAgentDeleteRequest deleteRequest = new MLAgentDeleteRequest(agentId, null);

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
    public void testDoExecute_HiddenAgentDeletionByNonSuperAdmin() throws IOException {
        String agentId = "hidden-agent-id";
        GetResponse getResponse = prepareMLAgent(agentId, true, null);
        ActionListener<DeleteResponse> actionListener = mock(ActionListener.class);
        MLAgentDeleteRequest deleteRequest = new MLAgentDeleteRequest(agentId, null);
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
    public void testDoExecute_NonHiddenAgentDeletionByNonSuperAdmin() throws IOException {
        String agentId = "non-hidden-agent-id";
        GetResponse getResponse = prepareMLAgent(agentId, false, null);

        ActionListener<DeleteResponse> actionListener = mock(ActionListener.class);
        MLAgentDeleteRequest deleteRequest = new MLAgentDeleteRequest(agentId, null);
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
        MLAgentDeleteRequest deleteRequest = new MLAgentDeleteRequest(agentId, null);

        Task task = mock(Task.class);
        Exception expectedException = new RuntimeException("Failed to fetch agent");

        doAnswer(invocation -> {
            ActionListener<GetResponse> listener = invocation.getArgument(1);
            listener.onFailure(expectedException);
            return null;
        }).when(client).get(any(), any());

        deleteAgentTransportAction.doExecute(task, deleteRequest, actionListener);

        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("Failed to get data object from index .plugins-ml-agent", argumentCaptor.getValue().getMessage());
    }

    @Test
    public void testDoExecute_DeleteFails() throws IOException {
        String agentId = "test-agent-id";
        GetResponse getResponse = prepareMLAgent(agentId, false, null);
        Exception expectedException = new RuntimeException("Deletion failed");

        ActionListener<DeleteResponse> actionListener = mock(ActionListener.class);

        MLAgentDeleteRequest deleteRequest = new MLAgentDeleteRequest(agentId, null);

        Task task = mock(Task.class);

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
        assertEquals("Failed to delete data object from index .plugins-ml-agent", argumentCaptor.getValue().getMessage());
    }

    @Test
    public void testDoExecute_GetIndexNotFoundFails() throws InterruptedException {
        String agentId = "test-agent-id";
        Task task = mock(Task.class);
        MLAgentDeleteRequest deleteRequest = new MLAgentDeleteRequest(agentId, null);
        ActionListener<DeleteResponse> actionListener = mock(ActionListener.class);
        Exception expectedException = new IndexNotFoundException("no agent index");
        doAnswer(invocation -> {
            ActionListener<GetResponse> listener = invocation.getArgument(1);
            listener.onFailure(expectedException);
            return null;
        }).when(client).get(any(), any());
        deleteAgentTransportAction.doExecute(task, deleteRequest, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("Failed to get agent index", argumentCaptor.getValue().getMessage());
    }

    private GetResponse prepareMLAgent(String agentId, boolean isHidden, String tenantId) throws IOException {

        MLAgent mlAgent = new MLAgent(
            "test",
            MLAgentType.CONVERSATIONAL.name(),
            "test",
            new LLMSpec("test_model", Map.of("test_key", "test_value")),
            List
                .of(
                    new MLToolSpec(
                        "test",
                        "test",
                        "test",
                        Collections.emptyMap(),
                        Collections.emptyMap(),
                        false,
                        Collections.emptyMap(),
                        null,
                        null
                    )
                ),
            Map.of("test", "test"),
            new MLMemorySpec("test", "123", 0, null),
            Instant.EPOCH,
            Instant.EPOCH,
            "test",
            isHidden,
            null, // contextManagementName
            null, // contextManagement
            tenantId
        );

        XContentBuilder content = mlAgent.toXContent(XContentFactory.jsonBuilder(), ToXContent.EMPTY_PARAMS);
        BytesReference bytesReference = BytesReference.bytes(content);
        GetResult getResult = new GetResult("indexName", agentId, 111l, 111l, 111l, true, bytesReference, null, null);
        return new GetResponse(getResult);
    }
}
