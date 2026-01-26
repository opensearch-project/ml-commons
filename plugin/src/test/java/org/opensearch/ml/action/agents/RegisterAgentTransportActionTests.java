/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.agents;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.opensearch.ml.common.CommonValue.MCP_CONNECTORS_FIELD;
import static org.opensearch.ml.common.CommonValue.ML_AGENT_INDEX;
import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_MCP_CONNECTOR_DISABLED_MESSAGE;
import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_MCP_CONNECTOR_ENABLED;
import static org.opensearch.ml.engine.algorithms.agent.MLChatAgentRunner.LLM_INTERFACE;
import static org.opensearch.ml.engine.algorithms.agent.MLPlanExecuteAndReflectAgentRunner.EXECUTOR_AGENT_ID_FIELD;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.OpenSearchException;
import org.opensearch.action.index.IndexRequest;
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
import org.opensearch.ml.action.contextmanagement.ContextManagementTemplateService;
import org.opensearch.ml.common.MLAgentType;
import org.opensearch.ml.common.agent.LLMSpec;
import org.opensearch.ml.common.agent.MLAgent;
import org.opensearch.ml.common.agent.MLAgentModelSpec;
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

    @Mock
    private ContextManagementTemplateService contextManagementTemplateService;

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
            mlFeatureEnabledSetting,
            contextManagementTemplateService
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

    @Test
    public void test_execute_registerAgent_PlanExecuteAndReflect_WithoutExecutorAgentId() {
        MLRegisterAgentRequest request = mock(MLRegisterAgentRequest.class);
        Map<String, String> parameters = new HashMap<>();
        parameters.put("tools", "[]");
        parameters.put("memory", "{}");

        LLMSpec llmSpec = new LLMSpec("test-model-id", new HashMap<>());

        MLAgent mlAgent = MLAgent
            .builder()
            .name("test_agent")
            .type(MLAgentType.PLAN_EXECUTE_AND_REFLECT.name())
            .description("Test agent for plan-execute-and-reflect")
            .parameters(parameters)
            .llm(llmSpec)
            .build();
        when(request.getMlAgent()).thenReturn(mlAgent);

        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(0);
            listener.onResponse(true);
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

        MLRegisterAgentResponse response = argumentCaptor.getValue();
        assertNotNull(response);
        assertEquals("AGENT_ID", response.getAgentId());
        verify(client, times(2)).index(any(), any());
    }

    @Test
    public void test_execute_registerAgent_PlanExecuteAndReflect_WithExecutorAgentId() {
        MLRegisterAgentRequest request = mock(MLRegisterAgentRequest.class);
        Map<String, String> parameters = new HashMap<>();
        parameters.put("tools", "[]");
        parameters.put("memory", "{}");
        parameters.put(EXECUTOR_AGENT_ID_FIELD, "existing-executor-id");

        LLMSpec llmSpec = new LLMSpec("test-model-id", new HashMap<>());

        MLAgent mlAgent = MLAgent
            .builder()
            .name("test_agent")
            .type(MLAgentType.PLAN_EXECUTE_AND_REFLECT.name())
            .description("Test agent for plan-execute-and-reflect")
            .parameters(parameters)
            .llm(llmSpec)
            .build();
        when(request.getMlAgent()).thenReturn(mlAgent);

        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(0);
            listener.onResponse(true);
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

        MLRegisterAgentResponse response = argumentCaptor.getValue();
        assertNotNull(response);
        assertEquals("AGENT_ID", response.getAgentId());

        verify(client, times(1)).index(any(), any());
    }

    @Test
    public void test_execute_registerAgent_MCPConnectorDisabled() {
        // Create an MLAgent with MCP connectors in parameters
        Map<String, String> parameters = new HashMap<>();
        parameters.put(MCP_CONNECTORS_FIELD, "[{\"connector_id\": \"test-connector\"}]");

        MLAgent mlAgent = MLAgent
            .builder()
            .name("agent")
            .type(MLAgentType.CONVERSATIONAL.name())
            .description("description")
            .llm(new LLMSpec("model_id", new HashMap<>()))
            .parameters(parameters)
            .build();

        MLRegisterAgentRequest request = mock(MLRegisterAgentRequest.class);
        when(request.getMlAgent()).thenReturn(mlAgent);

        // Disable MCP connector feature using mlFeatureEnabledSetting
        when(mlFeatureEnabledSetting.isMcpConnectorEnabled()).thenReturn(false);

        // Recreate the action with disabled MCP connector setting
        TransportRegisterAgentAction disabledAction = new TransportRegisterAgentAction(
            transportService,
            actionFilters,
            client,
            sdkClient,
            mlIndicesHandler,
            clusterService,
            mlFeatureEnabledSetting,
            contextManagementTemplateService
        );

        disabledAction.doExecute(task, request, actionListener);

        ArgumentCaptor<OpenSearchException> argumentCaptor = ArgumentCaptor.forClass(OpenSearchException.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals(ML_COMMONS_MCP_CONNECTOR_DISABLED_MESSAGE, argumentCaptor.getValue().getMessage());
    }

    @Test
    public void test_execute_registerAgent_InvalidLlmInterface() {
        Map<String, String> parameters = new HashMap<>();
        parameters.put(LLM_INTERFACE, "invalid_interface");

        MLAgent mlAgent = MLAgent
            .builder()
            .name("agent")
            .type(MLAgentType.CONVERSATIONAL.name())
            .description("description")
            .llm(new LLMSpec("model_id", new HashMap<>()))
            .parameters(parameters)
            .build();

        MLRegisterAgentRequest request = mock(MLRegisterAgentRequest.class);
        when(request.getMlAgent()).thenReturn(mlAgent);

        transportRegisterAgentAction.doExecute(task, request, actionListener);

        ArgumentCaptor<IllegalArgumentException> argumentCaptor = ArgumentCaptor.forClass(IllegalArgumentException.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertTrue(argumentCaptor.getValue().getMessage().contains("Invalid _llm_interface: invalid_interface"));
    }

    @Test
    public void test_execute_registerAgent_EmptyLlmInterface() {
        Map<String, String> parameters = new HashMap<>();
        parameters.put(LLM_INTERFACE, "");

        MLAgent mlAgent = MLAgent
            .builder()
            .name("agent")
            .type(MLAgentType.CONVERSATIONAL.name())
            .description("description")
            .llm(new LLMSpec("model_id", new HashMap<>()))
            .parameters(parameters)
            .build();

        MLRegisterAgentRequest request = mock(MLRegisterAgentRequest.class);
        when(request.getMlAgent()).thenReturn(mlAgent);

        transportRegisterAgentAction.doExecute(task, request, actionListener);

        ArgumentCaptor<IllegalArgumentException> argumentCaptor = ArgumentCaptor.forClass(IllegalArgumentException.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("_llm_interface cannot be blank or empty", argumentCaptor.getValue().getMessage());
    }

    @Test
    public void test_execute_registerAgent_BlankLlmInterface() {
        Map<String, String> parameters = new HashMap<>();
        parameters.put(LLM_INTERFACE, "   ");

        MLAgent mlAgent = MLAgent
            .builder()
            .name("agent")
            .type(MLAgentType.CONVERSATIONAL.name())
            .description("description")
            .llm(new LLMSpec("model_id", new HashMap<>()))
            .parameters(parameters)
            .build();

        MLRegisterAgentRequest request = mock(MLRegisterAgentRequest.class);
        when(request.getMlAgent()).thenReturn(mlAgent);

        transportRegisterAgentAction.doExecute(task, request, actionListener);

        ArgumentCaptor<IllegalArgumentException> argumentCaptor = ArgumentCaptor.forClass(IllegalArgumentException.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("_llm_interface cannot be blank or empty", argumentCaptor.getValue().getMessage());
    }

    @Test
    public void test_execute_registerAgent_WithModelSpec_Success() {
        // Create MLAgentModelSpec with model configuration
        Map<String, String> modelParameters = new HashMap<>();
        modelParameters.put("temperature", "0.7");
        modelParameters.put("max_tokens", "100");

        Map<String, String> credential = new HashMap<>();
        credential.put("access_key", "test_key");
        credential.put("secret_key", "test_secret");

        MLAgentModelSpec modelSpec = MLAgentModelSpec
            .builder()
            .modelProvider("bedrock/converse")
            .modelId("anthropic.claude-v2")
            .modelParameters(modelParameters)
            .credential(credential)
            .build();

        Map<String, String> agentParameters = new HashMap<>();
        agentParameters.put("tools", "[]");
        agentParameters.put("memory", "{}");

        MLAgent mlAgent = MLAgent
            .builder()
            .name("test_agent_with_model")
            .type(MLAgentType.CONVERSATIONAL.name())
            .description("Test agent with model spec")
            .model(modelSpec)
            .parameters(agentParameters)
            .build();

        MLRegisterAgentRequest request = mock(MLRegisterAgentRequest.class);
        when(request.getMlAgent()).thenReturn(mlAgent);

        // Mock model registration response
        org.opensearch.ml.common.transport.register.MLRegisterModelResponse modelResponse = mock(
            org.opensearch.ml.common.transport.register.MLRegisterModelResponse.class
        );
        when(modelResponse.getModelId()).thenReturn("created_model_id");

        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(0);
            listener.onResponse(true);
            return null;
        }).when(mlIndicesHandler).initMLAgentIndex(any());

        doAnswer(invocation -> {
            ActionListener<org.opensearch.ml.common.transport.register.MLRegisterModelResponse> al = invocation.getArgument(2);
            al.onResponse(modelResponse);
            return null;
        }).when(client).execute(eq(org.opensearch.ml.common.transport.register.MLRegisterModelAction.INSTANCE), any(), any());

        doAnswer(invocation -> {
            ActionListener<IndexResponse> al = invocation.getArgument(1);
            al.onResponse(indexResponse);
            return null;
        }).when(client).index(any(), any());

        transportRegisterAgentAction.doExecute(task, request, actionListener);

        ArgumentCaptor<MLRegisterAgentResponse> argumentCaptor = ArgumentCaptor.forClass(MLRegisterAgentResponse.class);
        verify(actionListener).onResponse(argumentCaptor.capture());

        MLRegisterAgentResponse response = argumentCaptor.getValue();
        assertNotNull(response);
        assertEquals("AGENT_ID", response.getAgentId());

        // Verify model registration was called
        verify(client, times(1)).execute(eq(org.opensearch.ml.common.transport.register.MLRegisterModelAction.INSTANCE), any(), any());

        // Verify agent was indexed with the created model ID in LLMSpec
        ArgumentCaptor<IndexRequest> indexRequestCaptor = ArgumentCaptor.forClass(IndexRequest.class);
        verify(client).index(indexRequestCaptor.capture(), any());

        IndexRequest indexRequest = indexRequestCaptor.getValue();
        assertNotNull(indexRequest);
        String source = indexRequest.source().utf8ToString();
        assertTrue("Agent source should contain created model_id", source.contains("\"model_id\":\"created_model_id\""));
        assertTrue("Agent source should contain _llm_interface", source.contains("\"_llm_interface\":\"bedrock/converse/claude\""));
        assertFalse("Agent source should not contain credential", source.contains("access_key"));
    }

    @Test
    public void test_execute_registerAgent_WithModelSpec_ModelRegistrationFailure() {
        Map<String, String> credential = new HashMap<>();
        credential.put("access_key", "test_key");
        credential.put("secret_key", "test_secret");

        MLAgentModelSpec modelSpec = MLAgentModelSpec
            .builder()
            .modelProvider("bedrock/converse")
            .modelId("anthropic.claude-v2")
            .credential(credential)
            .build();

        MLAgent mlAgent = MLAgent
            .builder()
            .name("test_agent_with_model")
            .type(MLAgentType.CONVERSATIONAL.name())
            .description("Test agent with model spec")
            .model(modelSpec)
            .build();

        MLRegisterAgentRequest request = mock(MLRegisterAgentRequest.class);
        when(request.getMlAgent()).thenReturn(mlAgent);

        doAnswer(invocation -> {
            ActionListener<org.opensearch.ml.common.transport.register.MLRegisterModelResponse> al = invocation.getArgument(2);
            al.onFailure(new RuntimeException("Model registration failed"));
            return null;
        }).when(client).execute(eq(org.opensearch.ml.common.transport.register.MLRegisterModelAction.INSTANCE), any(), any());

        transportRegisterAgentAction.doExecute(task, request, actionListener);

        ArgumentCaptor<RuntimeException> argumentCaptor = ArgumentCaptor.forClass(RuntimeException.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("Model registration failed", argumentCaptor.getValue().getMessage());
    }
}
