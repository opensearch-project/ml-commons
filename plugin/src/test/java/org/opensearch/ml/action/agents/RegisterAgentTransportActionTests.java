/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.agents;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.opensearch.ml.common.CommonValue.ML_AGENT_INDEX;
import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_MCP_CONNECTOR_ENABLED;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Set;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.OpenSearchException;
import org.opensearch.action.index.IndexResponse;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.ClusterSettings;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.commons.ConfigConstants;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.index.shard.ShardId;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.ml.common.MLAgentType;
import org.opensearch.ml.common.agent.LLMSpec;
import org.opensearch.ml.common.agent.MLAgent;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.transport.agent.MLRegisterAgentRequest;
import org.opensearch.ml.common.transport.agent.MLRegisterAgentResponse;
import org.opensearch.ml.engine.indices.MLIndicesHandler;
import org.opensearch.remote.metadata.client.SdkClient;
import org.opensearch.remote.metadata.client.impl.SdkClientFactory;
import org.opensearch.tasks.Task;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.TransportService;
import org.opensearch.transport.client.Client;

public class RegisterAgentTransportActionTests extends OpenSearchTestCase {

    @Mock
    private Client client;

    SdkClient sdkClient;

    @Mock
    private MLIndicesHandler mlIndicesHandler;

    @Mock
    private ActionFilters actionFilters;

    @Mock
    private TransportService transportService;

    @Mock
    private ClusterService clusterService;

    @Mock
    private Task task;

    @Mock
    private ActionListener<MLRegisterAgentResponse> actionListener;

    IndexResponse indexResponse;

    @Mock
    private ThreadPool threadPool;

    private TransportRegisterAgentAction transportRegisterAgentAction;
    private Settings settings;
    private ThreadContext threadContext;

    @Mock
    private MLFeatureEnabledSetting mlFeatureEnabledSetting;

    @Before
    public void setup() throws IOException {
        MockitoAnnotations.openMocks(this);
        settings = Settings.builder().build();
        sdkClient = SdkClientFactory.createSdkClient(client, NamedXContentRegistry.EMPTY, Collections.emptyMap());
        threadContext = new ThreadContext(settings);

        threadContext.putTransient(ConfigConstants.OPENSEARCH_SECURITY_USER_INFO_THREAD_CONTEXT, "alex|IT,HR|engineering,operations");

        when(client.threadPool()).thenReturn(threadPool);
        when(threadPool.getThreadContext()).thenReturn(threadContext);
        when(clusterService.getSettings()).thenReturn(settings);
        when(this.clusterService.getClusterSettings()).thenReturn(new ClusterSettings(settings, Set.of(ML_COMMONS_MCP_CONNECTOR_ENABLED)));
        transportRegisterAgentAction = new TransportRegisterAgentAction(
            transportService,
            actionFilters,
            client,
            sdkClient,
            mlIndicesHandler,
            clusterService,
            mlFeatureEnabledSetting
        );
        indexResponse = new IndexResponse(new ShardId(ML_AGENT_INDEX, "_na_", 0), "AGENT_ID", 1, 0, 2, true);
    }

    @Test
    public void test_execute_registerAgent_success() {
        MLRegisterAgentRequest request = mock(MLRegisterAgentRequest.class);
        MLAgent mlAgent = MLAgent
            .builder()
            .name("agent")
            .type(MLAgentType.CONVERSATIONAL.name())
            .description("description")
            .llm(new LLMSpec("model_id", new HashMap<>()))
            .build();
        when(request.getMlAgent()).thenReturn(mlAgent);

        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(0);
            listener.onResponse(true);
            return null;
        }).when(mlIndicesHandler).initMLAgentIndex(any());

        doAnswer(invocation -> {
            ActionListener<IndexResponse> al = invocation.getArgument(1);
            IndexResponse indexResponse = new IndexResponse(new ShardId("test", "test", 1), "test", 1l, 1l, 1l, true);
            al.onResponse(indexResponse);
            return null;
        }).when(client).index(any(), any());

        transportRegisterAgentAction.doExecute(task, request, actionListener);
        ArgumentCaptor<MLRegisterAgentResponse> argumentCaptor = ArgumentCaptor.forClass(MLRegisterAgentResponse.class);
        verify(actionListener).onResponse(argumentCaptor.capture());
    }

    @Test
    public void test_execute_registerAgent_AgentIndexNotInitialized() {
        MLRegisterAgentRequest request = mock(MLRegisterAgentRequest.class);
        MLAgent mlAgent = MLAgent
            .builder()
            .name("agent")
            .type(MLAgentType.CONVERSATIONAL.name())
            .description("description")
            .llm(new LLMSpec("model_id", new HashMap<>()))
            .build();
        when(request.getMlAgent()).thenReturn(mlAgent);

        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(0);
            listener.onResponse(false);
            return null;
        }).when(mlIndicesHandler).initMLAgentIndex(any());

        transportRegisterAgentAction.doExecute(task, request, actionListener);
        ArgumentCaptor<OpenSearchException> argumentCaptor = ArgumentCaptor.forClass(OpenSearchException.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("Failed to create ML agent index", argumentCaptor.getValue().getMessage());
    }

    @Test
    public void test_execute_registerAgent_IndexFailure() {
        MLRegisterAgentRequest request = mock(MLRegisterAgentRequest.class);
        MLAgent mlAgent = MLAgent
            .builder()
            .name("agent")
            .type(MLAgentType.CONVERSATIONAL.name())
            .description("description")
            .llm(new LLMSpec("model_id", new HashMap<>()))
            .build();
        when(request.getMlAgent()).thenReturn(mlAgent);

        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(0);
            listener.onResponse(true);
            return null;
        }).when(mlIndicesHandler).initMLAgentIndex(any());

        doAnswer(invocation -> {
            ActionListener<IndexResponse> al = invocation.getArgument(1);
            al.onFailure(new RuntimeException("index failure"));
            return null;
        }).when(client).index(any(), any());

        transportRegisterAgentAction.doExecute(task, request, actionListener);
        ArgumentCaptor<RuntimeException> argumentCaptor = ArgumentCaptor.forClass(RuntimeException.class);
        verify(actionListener).onFailure(argumentCaptor.capture());

        assertEquals("Failed to put data object in index .plugins-ml-agent", argumentCaptor.getValue().getMessage());
    }

    @Test
    public void test_execute_registerAgent_InitAgentIndexFailure() {
        MLRegisterAgentRequest request = mock(MLRegisterAgentRequest.class);
        MLAgent mlAgent = MLAgent
            .builder()
            .name("agent")
            .type(MLAgentType.CONVERSATIONAL.name())
            .description("description")
            .llm(new LLMSpec("model_id", new HashMap<>()))
            .build();
        when(request.getMlAgent()).thenReturn(mlAgent);

        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(0);
            listener.onFailure(new RuntimeException("agent index initialization failed"));
            return null;
        }).when(mlIndicesHandler).initMLAgentIndex(any());

        transportRegisterAgentAction.doExecute(task, request, actionListener);
        ArgumentCaptor<RuntimeException> argumentCaptor = ArgumentCaptor.forClass(RuntimeException.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("agent index initialization failed", argumentCaptor.getValue().getMessage());
    }

    @Test
    public void test_execute_registerAgent_ModelNotHidden() {
        MLRegisterAgentRequest request = mock(MLRegisterAgentRequest.class);
        MLAgent mlAgent = MLAgent
            .builder()
            .name("agent")
            .type(MLAgentType.CONVERSATIONAL.name())
            .description("description")
            .llm(new LLMSpec("model_id", new HashMap<>()))
            .build();
        when(request.getMlAgent()).thenReturn(mlAgent);

        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(0);
            listener.onResponse(true); // Simulate successful index initialization
            return null;
        }).when(mlIndicesHandler).initMLAgentIndex(any());

        doAnswer(invocation -> {
            ActionListener<IndexResponse> al = invocation.getArgument(1);
            al.onResponse(indexResponse); // Simulating successful indexing
            return null;
        }).when(client).index(any(), any());

        transportRegisterAgentAction.doExecute(task, request, actionListener);

        ArgumentCaptor<MLRegisterAgentResponse> argumentCaptor = ArgumentCaptor.forClass(MLRegisterAgentResponse.class);
        verify(actionListener).onResponse(argumentCaptor.capture());

        assertNotNull(argumentCaptor.getValue());
    }

    @Test
    public void test_execute_registerAgent_Othertype() {
        MLRegisterAgentRequest request = mock(MLRegisterAgentRequest.class);
        MLAgent mlAgent = MLAgent.builder().name("agent").type(MLAgentType.FLOW.name()).description("description").build();
        when(request.getMlAgent()).thenReturn(mlAgent);

        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(0);
            listener.onResponse(true); // Simulate successful index initialization
            return null;
        }).when(mlIndicesHandler).initMLAgentIndex(any());

        doAnswer(invocation -> {
            ActionListener<IndexResponse> al = invocation.getArgument(1);
            al.onResponse(indexResponse);
            return null;
        }).when(client).index(any(), any());

        transportRegisterAgentAction.doExecute(task, request, actionListener);

        ArgumentCaptor<MLRegisterAgentResponse> argumentCaptor = ArgumentCaptor.forClass(MLRegisterAgentResponse.class);
        verify(actionListener).onResponse(argumentCaptor.capture());

        assertNotNull(argumentCaptor.getValue());
    }

}
