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
import java.util.HashMap;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.OpenSearchStatusException;
import org.opensearch.action.DocWriteResponse;
import org.opensearch.action.get.GetResponse;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.update.UpdateResponse;
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
import org.opensearch.index.get.GetResult;
import org.opensearch.ml.common.MLAgentType;
import org.opensearch.ml.common.agent.LLMSpec;
import org.opensearch.ml.common.agent.MLAgent;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.transport.agent.MLAgentUpdateInput;
import org.opensearch.ml.common.transport.agent.MLAgentUpdateRequest;
import org.opensearch.remote.metadata.client.SdkClient;
import org.opensearch.remote.metadata.client.impl.SdkClientFactory;
import org.opensearch.tasks.Task;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.TransportService;
import org.opensearch.transport.client.Client;

public class UpdateAgentTransportActionTests {

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

    @Mock
    private Task task;

    @Mock
    private ActionListener<UpdateResponse> actionListener;

    UpdateResponse updateResponse;

    @Mock
    private ActionFilters actionFilters;

    private UpdateAgentTransportAction updateAgentTransportAction;

    ThreadContext threadContext;

    @Mock
    private MLFeatureEnabledSetting mlFeatureEnabledSetting;

    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);
        sdkClient = SdkClientFactory.createSdkClient(client, NamedXContentRegistry.EMPTY, Collections.emptyMap());
        updateAgentTransportAction = spy(
            new UpdateAgentTransportAction(
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

        updateResponse = new UpdateResponse(
            new ShardId(ML_AGENT_INDEX, "_na_", 0),
            "test_agent_id",
            1,
            0,
            2,
            DocWriteResponse.Result.UPDATED
        );
    }

    @Test
    public void testConstructor() {
        assertEquals(updateAgentTransportAction.client, client);
        assertEquals(updateAgentTransportAction.xContentRegistry, xContentRegistry);
    }

    @Test
    public void testDoExecute_Success() throws IOException {
        String agentId = "test_agent_id";
        MLAgentUpdateInput mlAgentUpdateInput = MLAgentUpdateInput
            .builder()
            .agentId(agentId)
            .name("agent")
            .description("description")
            .llmModelId("model_id")
            .llmParameters(new HashMap<>())
            .build();

        GetResponse getResponse = prepareMLAgentGetResponse(agentId, false, "test_tenant_id");

        MLAgentUpdateRequest updateRequest = mock(MLAgentUpdateRequest.class);
        when(updateRequest.getMlAgentUpdateInput()).thenReturn(mlAgentUpdateInput);
        doReturn(true).when(updateAgentTransportAction).isSuperAdminUserWrapper(clusterService, client);

        doAnswer(invocation -> {
            ActionListener<GetResponse> listener = invocation.getArgument(1);
            listener.onResponse(getResponse);
            return null;
        }).when(client).get(any(), any());

        doAnswer(invocation -> {
            ActionListener<UpdateResponse> listener = invocation.getArgument(1);
            listener.onResponse(updateResponse);
            return null;
        }).when(client).update(any(), any());

        updateAgentTransportAction.doExecute(task, updateRequest, actionListener);
        ArgumentCaptor<UpdateResponse> argumentCaptor = ArgumentCaptor.forClass(UpdateResponse.class);
        verify(actionListener).onResponse(argumentCaptor.capture());
        assertEquals(DocWriteResponse.Result.UPDATED, argumentCaptor.getValue().getResult());
    }

    @Test
    public void testDoExecute_GetFailure() {
        String agentId = "test_agent_id";
        MLAgentUpdateInput mlAgentUpdateInput = MLAgentUpdateInput
            .builder()
            .agentId(agentId)
            .name("agent")
            .description("description")
            .llmModelId("model_id")
            .llmParameters(new HashMap<>())
            .build();

        MLAgentUpdateRequest updateRequest = mock(MLAgentUpdateRequest.class);
        when(updateRequest.getMlAgentUpdateInput()).thenReturn(mlAgentUpdateInput);

        doAnswer(invocation -> {
            ActionListener<GetResponse> listener = invocation.getArgument(1);
            listener.onFailure(new Exception());
            return null;
        }).when(client).get(any(), any());

        updateAgentTransportAction.doExecute(task, updateRequest, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("Failed to get data object from index .plugins-ml-agent", argumentCaptor.getValue().getMessage());
    }

    @Test
    public void testDoExecute_UpdateFailure() throws IOException {
        String agentId = "test_agent_id";
        MLAgentUpdateInput mlAgentUpdateInput = MLAgentUpdateInput
            .builder()
            .agentId(agentId)
            .name("agent")
            .description("description")
            .llmModelId("model_id")
            .llmParameters(new HashMap<>())
            .build();

        GetResponse getResponse = prepareMLAgentGetResponse(agentId, false, null);

        MLAgentUpdateRequest updateRequest = mock(MLAgentUpdateRequest.class);
        when(updateRequest.getMlAgentUpdateInput()).thenReturn(mlAgentUpdateInput);

        doAnswer(invocation -> {
            ActionListener<GetResponse> listener = invocation.getArgument(1);
            listener.onResponse(getResponse);
            return null;
        }).when(client).get(any(), any());

        doAnswer(invocation -> {
            ActionListener<UpdateResponse> listener = invocation.getArgument(1);
            listener.onFailure(new Exception());
            return null;
        }).when(client).update(any(), any());

        updateAgentTransportAction.doExecute(task, updateRequest, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("Failed to update data object in index .plugins-ml-agent", argumentCaptor.getValue().getMessage());
    }

    @Test
    public void testDoExecute_HiddenAgentSuperAdmin() throws IOException {
        String agentId = "test_agent_id";
        MLAgentUpdateInput mlAgentUpdateInput = MLAgentUpdateInput
            .builder()
            .agentId(agentId)
            .name("updated_agent")
            .description("updated description")
            .llmModelId("model_id")
            .llmParameters(new HashMap<>())
            .build();

        GetResponse getResponse = prepareMLAgentGetResponse(agentId, true, null);

        MLAgentUpdateRequest updateRequest = mock(MLAgentUpdateRequest.class);
        when(updateRequest.getMlAgentUpdateInput()).thenReturn(mlAgentUpdateInput);

        doAnswer(invocation -> {
            ActionListener<GetResponse> listener = invocation.getArgument(1);
            listener.onResponse(getResponse);
            return null;
        }).when(client).get(any(), any());

        doAnswer(invocation -> {
            ActionListener<UpdateResponse> listener = invocation.getArgument(1);
            listener.onResponse(updateResponse);
            return null;
        }).when(client).update(any(), any());

        doReturn(true).when(updateAgentTransportAction).isSuperAdminUserWrapper(clusterService, client);

        updateAgentTransportAction.doExecute(task, updateRequest, actionListener);
        ArgumentCaptor<UpdateResponse> argumentCaptor = ArgumentCaptor.forClass(UpdateResponse.class);
        verify(actionListener).onResponse(argumentCaptor.capture());
        assertEquals(DocWriteResponse.Result.UPDATED, argumentCaptor.getValue().getResult());
    }

    @Test
    public void testDoExecute_HiddenAgentNonSuperAdmin() throws IOException {
        String agentId = "test_agent_id";
        MLAgentUpdateInput mlAgentUpdateInput = MLAgentUpdateInput
            .builder()
            .agentId(agentId)
            .name("updated_agent")
            .description("updated description")
            .llmModelId("model_id")
            .llmParameters(new HashMap<>())
            .build();

        GetResponse getResponse = prepareMLAgentGetResponse(agentId, true, null);

        MLAgentUpdateRequest updateRequest = mock(MLAgentUpdateRequest.class);
        when(updateRequest.getMlAgentUpdateInput()).thenReturn(mlAgentUpdateInput);

        doAnswer(invocation -> {
            ActionListener<GetResponse> listener = invocation.getArgument(1);
            listener.onResponse(getResponse);
            return null;
        }).when(client).get(any(), any());

        doReturn(false).when(updateAgentTransportAction).isSuperAdminUserWrapper(clusterService, client);

        updateAgentTransportAction.doExecute(task, updateRequest, actionListener);
        ArgumentCaptor<OpenSearchStatusException> argumentCaptor = ArgumentCaptor.forClass(OpenSearchStatusException.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals(RestStatus.FORBIDDEN, argumentCaptor.getValue().status());
    }

    private GetResponse prepareMLAgentGetResponse(String agentId, boolean isHidden, String tenantId) throws IOException {
        MLAgent mlAgent = MLAgent
            .builder()
            .name("test")
            .type(MLAgentType.CONVERSATIONAL.name())
            .description("test description")
            .llm(LLMSpec.builder().modelId("model_id").parameters(new HashMap<>()).build())
            .isHidden(isHidden)
            .tenantId(tenantId)
            .createdTime(Instant.now())
            .build();

        XContentBuilder content = mlAgent.toXContent(XContentFactory.jsonBuilder(), ToXContent.EMPTY_PARAMS);
        BytesReference bytesReference = BytesReference.bytes(content);
        GetResult getResult = new GetResult("indexName", agentId, 111L, 111L, 111L, true, bytesReference, null, null);
        return new GetResponse(getResult);
    }
}
