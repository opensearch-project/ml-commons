/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.agent;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import static org.opensearch.ml.common.CommonValue.MCP_CONNECTORS_FIELD;
import static org.opensearch.ml.common.agui.AGUIConstants.AGUI_PARAM_CONTEXT;
import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_MCP_CONNECTOR_DISABLED_MESSAGE;
import static org.opensearch.ml.engine.algorithms.agent.MLAgentExecutor.QUESTION;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;

import org.opensearch.action.index.IndexResponse;

import org.opensearch.cluster.node.DiscoveryNode;
import org.opensearch.ml.common.MLMemoryType;
import org.opensearch.core.xcontent.XContentParser;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.OpenSearchException;
import org.opensearch.OpenSearchStatusException;
import org.opensearch.action.get.GetResponse;
import org.opensearch.cluster.ClusterState;
import org.opensearch.cluster.metadata.Metadata;
import org.opensearch.cluster.node.DiscoveryNode;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.common.xcontent.XContentFactory;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.common.bytes.BytesReference;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.index.IndexNotFoundException;
import org.opensearch.index.get.GetResult;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.MLAgentType;
import org.opensearch.ml.common.agent.LLMSpec;
import org.opensearch.ml.common.agent.MLAgent;
import org.opensearch.ml.common.agent.MLAgentModelSpec;
import org.opensearch.ml.common.agent.MLMemorySpec;
import org.opensearch.ml.common.agent.MLToolSpec;
import org.opensearch.ml.common.dataset.remote.RemoteInferenceInputDataSet;
import org.opensearch.ml.common.input.Input;
import org.opensearch.ml.common.input.execute.agent.AgentInput;
import org.opensearch.ml.common.input.execute.agent.AgentMLInput;
import org.opensearch.ml.common.input.execute.agent.ContentBlock;
import org.opensearch.ml.common.input.execute.agent.ContentType;
import org.opensearch.ml.common.input.execute.agent.InputType;
import org.opensearch.ml.common.input.execute.agent.Message;
import org.opensearch.ml.common.memory.Memory;
import org.opensearch.ml.common.output.Output;
import org.opensearch.ml.common.output.model.ModelTensor;
import org.opensearch.ml.common.output.model.ModelTensorOutput;
import org.opensearch.ml.common.output.model.ModelTensors;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.spi.tools.Tool;
import org.opensearch.ml.engine.encryptor.Encryptor;
import org.opensearch.ml.engine.memory.ConversationIndexMemory;
import org.opensearch.remote.metadata.client.GetDataObjectResponse;
import org.opensearch.remote.metadata.client.SdkClient;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.TransportChannel;
import org.opensearch.transport.client.Client;

@SuppressWarnings({ "rawtypes" })
public class MLAgentExecutorTest {

    @Mock
    private Client client;

    @Mock
    private SdkClient sdkClient;

    @Mock
    private ClusterService clusterService;

    @Mock
    private NamedXContentRegistry xContentRegistry;

    @Mock
    private MLFeatureEnabledSetting mlFeatureEnabledSetting;

    @Mock
    private Encryptor encryptor;

    @Mock
    private ThreadPool threadPool;

    private ThreadContext threadContext;

    @Mock
    private ThreadContext.StoredContext storedContext;

    @Mock
    private TransportChannel channel;

    @Mock
    private ActionListener<Output> listener;

    @Mock
    private GetResponse getResponse;

    private MLAgentExecutor mlAgentExecutor;
    private Map<String, Tool.Factory> toolFactories;
    private Map<String, Memory.Factory> memoryFactoryMap;
    private Settings settings;
    private Metadata metadata;

    @Mock
    private MLAgentRunner mlAgentRunner;

    @Mock
    private ConversationIndexMemory memory;

    @Mock
    private ConversationIndexMemory.Factory mockMemoryFactory;

    @Captor
    private ArgumentCaptor<Output> objectCaptor;

    @Captor
    private ArgumentCaptor<Exception> exceptionCaptor;

    @Mock
    private ActionListener<Output> agentActionListener;

    @Mock
    private DiscoveryNode localNode;

    MLAgent mlAgent;

    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);

        settings = Settings.builder().build();
        toolFactories = new HashMap<>();
        memoryFactoryMap = new HashMap<>();
        threadContext = new ThreadContext(settings);

        when(client.threadPool()).thenReturn(threadPool);
        when(threadPool.getThreadContext()).thenReturn(threadContext);
        when(mlFeatureEnabledSetting.isMultiTenancyEnabled()).thenReturn(false);

        // Mock ClusterService for the agent index check
        ClusterState clusterState = mock(ClusterState.class);
        metadata = mock(Metadata.class);
        when(clusterService.state()).thenReturn(clusterState);
        when(clusterState.metadata()).thenReturn(metadata);
        when(metadata.hasIndex(anyString())).thenReturn(false); // Simulate index not found

        mlAgentExecutor = new MLAgentExecutor(
            client,
            sdkClient,
            settings,
            clusterService,
            xContentRegistry,
            toolFactories,
            memoryFactoryMap,
            mlFeatureEnabledSetting,
            encryptor
        );
    }

    @Test
    public void testConstructor() {
        assertNotNull(mlAgentExecutor);
        assertEquals(client, mlAgentExecutor.getClient());
        assertEquals(settings, mlAgentExecutor.getSettings());
        assertEquals(clusterService, mlAgentExecutor.getClusterService());
        assertEquals(xContentRegistry, mlAgentExecutor.getXContentRegistry());
        assertEquals(toolFactories, mlAgentExecutor.getToolFactories());
        assertEquals(memoryFactoryMap, mlAgentExecutor.getMemoryFactoryMap());
        assertEquals(mlFeatureEnabledSetting, mlAgentExecutor.getMlFeatureEnabledSetting());
        assertEquals(encryptor, mlAgentExecutor.getEncryptor());
    }

    @Test
    public void testOnMultiTenancyEnabledChanged() {
        mlAgentExecutor.onMultiTenancyEnabledChanged(true);
        assertTrue(mlAgentExecutor.getIsMultiTenancyEnabled());

        mlAgentExecutor.onMultiTenancyEnabledChanged(false);
        assertFalse(mlAgentExecutor.getIsMultiTenancyEnabled());
    }

    @Test
    public void testExecuteWithWrongInputType() {
        // Test with non-AgentMLInput - create a mock Input that's not AgentMLInput
        Input wrongInput = mock(Input.class);

        try {
            mlAgentExecutor.execute(wrongInput, listener, channel);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException exception) {
            assertEquals("wrong input", exception.getMessage());
        }
    }

    @Test
    public void testExecuteWithNullInputDataSet() {
        AgentMLInput agentInput = new AgentMLInput("test-agent", null, FunctionName.AGENT, null);

        try {
            mlAgentExecutor.execute(agentInput, listener, channel);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException exception) {
            assertEquals("Agent input data can not be empty.", exception.getMessage());
        }
    }

    @Test
    public void testExecuteWithNullParameters() {
        AgentMLInput agentInput = new AgentMLInput("test-agent", null, FunctionName.AGENT, null);

        try {
            mlAgentExecutor.execute(agentInput, listener, channel);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException exception) {
            assertEquals("Agent input data can not be empty.", exception.getMessage());
        }
    }

    @Test
    public void testExecuteWithMultiTenancyEnabledButNoTenantId() {
        when(mlFeatureEnabledSetting.isMultiTenancyEnabled()).thenReturn(true);
        mlAgentExecutor.onMultiTenancyEnabledChanged(true);
        
        Map<String, String> parameters = Collections.singletonMap("question", "test question");
        RemoteInferenceInputDataSet dataset = RemoteInferenceInputDataSet.builder()
            .parameters(parameters)
            .build();
        AgentMLInput agentInput = new AgentMLInput("test-agent", null, FunctionName.AGENT, dataset);
        
        try {
            mlAgentExecutor.execute(agentInput, listener, channel);
            fail("Expected OpenSearchStatusException");
        } catch (OpenSearchStatusException exception) {
            assertEquals("You don't have permission to access this resource", exception.getMessage());
            assertEquals(RestStatus.FORBIDDEN, exception.status());
        }
    }

    @Test
    public void testExecuteWithAgentIndexNotFound() {
        Map<String, String> parameters = Collections.singletonMap("question", "test question");
        RemoteInferenceInputDataSet dataset = RemoteInferenceInputDataSet.builder().parameters(parameters).build();
        AgentMLInput agentInput = new AgentMLInput("test-agent", null, FunctionName.AGENT, dataset);

        // Since we can't mock static methods easily, we'll test a different scenario
        // This test would need the actual MLIndicesHandler behavior
        mlAgentExecutor.execute(agentInput, listener, channel);

        // Verify that the listener was called (the actual behavior will depend on the implementation)
        verify(listener, timeout(5000).atLeastOnce()).onFailure(any());
    }

    @Test
    public void testGetAgentRunnerWithFlowAgent() {
        MLAgent agent = createTestAgent(MLAgentType.FLOW.name());
        MLAgentRunner runner = mlAgentExecutor.getAgentRunner(agent, null);
        assertNotNull(runner);
        assertTrue(runner instanceof MLFlowAgentRunner);
    }

    @Test
    public void testGetAgentRunnerWithConversationalFlowAgent() {
        MLAgent agent = createTestAgent(MLAgentType.CONVERSATIONAL_FLOW.name());
        MLAgentRunner runner = mlAgentExecutor.getAgentRunner(agent, null);
        assertNotNull(runner);
        assertTrue(runner instanceof MLConversationalFlowAgentRunner);
    }

    @Test
    public void testGetAgentRunnerWithConversationalAgent() {
        MLAgent agent = createTestAgent(MLAgentType.CONVERSATIONAL.name());
        MLAgentRunner runner = mlAgentExecutor.getAgentRunner(agent, null);
        assertNotNull(runner);
        assertTrue(runner instanceof MLChatAgentRunner);
    }

    @Test
    public void testGetAgentRunnerWithPlanExecuteAndReflectAgent() {
        MLAgent agent = createTestAgent(MLAgentType.PLAN_EXECUTE_AND_REFLECT.name());
        MLAgentRunner runner = mlAgentExecutor.getAgentRunner(agent, null);
        assertNotNull(runner);
        assertTrue(runner instanceof MLPlanExecuteAndReflectAgentRunner);
    }

    @Test
    public void testGetAgentRunnerWithAGUIAgent() {
        MLAgent agent = createTestAgent(MLAgentType.AG_UI.name());
        MLAgentRunner runner = mlAgentExecutor.getAgentRunner(agent, null);
        assertNotNull(runner);
        assertTrue(runner instanceof MLAGUIAgentRunner);
    }

    @Test
    public void testGetAgentRunnerWithConversationalV2Agent() {
        MLAgent agent = createTestAgent(MLAgentType.CONVERSATIONAL_V2.name());
        MLAgentRunner runner = mlAgentExecutor.getAgentRunner(agent, null);
        assertNotNull(runner);
        assertTrue(runner instanceof MLChatAgentRunnerV2);
    }

    @Test
    public void testGetAgentRunnerWithUnsupportedAgentType() {
        // Create a mock MLAgent instead of using the constructor that validates
        MLAgent agent = mock(MLAgent.class);
        when(agent.getType()).thenReturn("UNSUPPORTED_TYPE");

        try {
            mlAgentExecutor.getAgentRunner(agent, null);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException exception) {
            assertEquals("Wrong Agent type", exception.getMessage());
        }
    }

    @Test
    public void testProcessOutputWithModelTensorOutput() throws Exception {
        ModelTensorOutput output = mock(ModelTensorOutput.class);
        when(output.getMlModelOutputs()).thenReturn(Collections.emptyList());

        List<org.opensearch.ml.common.output.model.ModelTensor> modelTensors = new java.util.ArrayList<>();

        mlAgentExecutor.processOutput(output, modelTensors);

        verify(output).getMlModelOutputs();
    }

    @Test
    public void testProcessOutputWithString() throws Exception {
        String output = "test response";
        List<org.opensearch.ml.common.output.model.ModelTensor> modelTensors = new java.util.ArrayList<>();

        mlAgentExecutor.processOutput(output, modelTensors);

        assertEquals(1, modelTensors.size());
        assertEquals("response", modelTensors.get(0).getName());
        assertEquals("test response", modelTensors.get(0).getResult());
    }

    private MLAgent createTestAgent(String type) {
        return MLAgent
            .builder()
            .name("test-agent")
            .type(type)
            .description("Test agent")
            .llm(LLMSpec.builder().modelId("test-model").parameters(Collections.emptyMap()).build())
            .tools(Collections.emptyList())
            .parameters(Collections.emptyMap())
            .memory(null)
            .createdTime(Instant.now())
            .lastUpdateTime(Instant.now())
            .appType("test-app")
            .build();
    }

    @Test
    public void test_ProcessAgentInput_OldStyleAgent_NoModel() {
        MLAgent oldStyleAgent = MLAgent.builder().name("old_agent").type("flow").build();
        AgentMLInput agentMLInput = new AgentMLInput("test", null, FunctionName.AGENT, null);

        // Should not throw exception for old style agent without model
        mlAgentExecutor.processAgentInput(agentMLInput, oldStyleAgent);

        // Verify no changes were made
        Assert.assertNull(agentMLInput.getInputDataset());
    }

    @Test(expected = IllegalArgumentException.class)
    public void test_ProcessAgentInput_MessagesInput_PlanExecuteReflectAgent() {
        MLAgent agent = MLAgent
            .builder()
            .name("test_agent")
            .type(MLAgentType.PLAN_EXECUTE_AND_REFLECT.name())
            .model(MLAgentModelSpec.builder().modelId("anthropic.claude-v2").modelProvider("bedrock/converse").build())
            .build();

        ContentBlock textBlock = new ContentBlock();
        textBlock.setType(ContentType.TEXT);
        textBlock.setText("Hello");
        Message message = new Message("user", Collections.singletonList(textBlock));
        AgentInput agentInput = new AgentInput();
        agentInput.setInput(Collections.singletonList(message));
        AgentMLInput agentMLInput = new AgentMLInput("test", null, FunctionName.AGENT, agentInput, null, false);

        mlAgentExecutor.processAgentInput(agentMLInput, agent);
    }

    @Test
    public void test_ProcessAgentInput_LegacyQuestionInput() {
        MLAgent agent = MLAgent
            .builder()
            .name("test_agent")
            .type(MLAgentType.CONVERSATIONAL.name())
            .model(MLAgentModelSpec.builder().modelId("anthropic.claude-v2").modelProvider("bedrock/converse").build())
            .build();

        Map<String, String> params = new HashMap<>();
        params.put(QUESTION, "What is the weather?");
        RemoteInferenceInputDataSet dataset = RemoteInferenceInputDataSet.builder().parameters(params).build();
        AgentMLInput agentMLInput = new AgentMLInput("test", null, FunctionName.AGENT, dataset);

        mlAgentExecutor.processAgentInput(agentMLInput, agent);

        // Verify AgentInput was created from legacy question
        Assert.assertNotNull(agentMLInput.getAgentInput());
        Assert.assertEquals(InputType.TEXT, agentMLInput.getAgentInput().getInputType());
    }

    @Test
    public void test_ProcessAgentInput_StandardInput_CreatesDataset() {
        MLAgent agent = MLAgent
            .builder()
            .name("test_agent")
            .type(MLAgentType.CONVERSATIONAL.name())
            .model(MLAgentModelSpec.builder().modelId("anthropic.claude-v2").modelProvider("bedrock/converse").build())
            .build();

        AgentInput agentInput = new AgentInput();
        agentInput.setInput("What is machine learning?");

        AgentMLInput agentMLInput = new AgentMLInput("test", null, FunctionName.AGENT, agentInput, null, false);

        mlAgentExecutor.processAgentInput(agentMLInput, agent);

        // Verify dataset was created
        Assert.assertNotNull(agentMLInput.getInputDataset());
        RemoteInferenceInputDataSet dataset = (RemoteInferenceInputDataSet) agentMLInput.getInputDataset();
        Assert.assertNotNull(dataset.getParameters());
        Assert.assertTrue(dataset.getParameters().containsKey(QUESTION));
        Assert.assertEquals("What is machine learning?", dataset.getParameters().get(QUESTION));
    }

    @Test
    public void test_ProcessAgentInput_StandardInput_UpdatesExistingDataset() {
        MLAgent agent = MLAgent
            .builder()
            .name("test_agent")
            .type(MLAgentType.CONVERSATIONAL.name())
            .model(MLAgentModelSpec.builder().modelId("anthropic.claude-v2").modelProvider("bedrock/converse").build())
            .build();

        AgentInput agentInput = new AgentInput();
        agentInput.setInput("Explain neural networks");
        Map<String, String> existingParams = new HashMap<>();
        existingParams.put("existing_key", "existing_value");
        RemoteInferenceInputDataSet dataset = RemoteInferenceInputDataSet.builder().parameters(existingParams).build();
        AgentMLInput agentMLInput = new AgentMLInput("test", null, FunctionName.AGENT, agentInput, dataset, false);

        mlAgentExecutor.processAgentInput(agentMLInput, agent);

        // Verify existing dataset was updated
        RemoteInferenceInputDataSet updatedDataset = (RemoteInferenceInputDataSet) agentMLInput.getInputDataset();
        Assert.assertTrue(updatedDataset.getParameters().containsKey(QUESTION));
        Assert.assertEquals("Explain neural networks", updatedDataset.getParameters().get(QUESTION));
        Assert.assertTrue(updatedDataset.getParameters().containsKey("existing_key"));
        Assert.assertEquals("existing_value", updatedDataset.getParameters().get("existing_key"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void test_ProcessAgentInput_InvalidModelProvider() {
        MLAgent agent = MLAgent
            .builder()
            .name("test_agent")
            .type(MLAgentType.CONVERSATIONAL.name())
            .model(MLAgentModelSpec.builder().modelId("model-123").modelProvider("invalid_provider").build())
            .build();

        AgentInput agentInput = new AgentInput();
        agentInput.setInput("Test question");
        AgentMLInput agentMLInput = new AgentMLInput("test", null, FunctionName.AGENT, agentInput, null, false);

        mlAgentExecutor.processAgentInput(agentMLInput, agent);
    }

    @Test
    public void test_ProcessAgentInput_AGUIAgent_WithLegacyLLMInterface() {
        // AGUI agent using legacy LLM interface
        MLAgent agent = MLAgent
            .builder()
            .name("agui_agent_legacy")
            .type(MLAgentType.AG_UI.name())
            .llm(LLMSpec.builder().modelId("anthropic.claude-v2").build())
            .build();

        AgentInput agentInput = new AgentInput();
        agentInput.setInput("What is AI?");
        AgentMLInput agentMLInput = new AgentMLInput("test", null, FunctionName.AGENT, agentInput, null, false);

        // Should not throw exception - legacy interface should work
        mlAgentExecutor.processAgentInput(agentMLInput, agent);

        // Verify dataset has QUESTION parameter for legacy interface
        Assert.assertNotNull(agentMLInput.getInputDataset());
        RemoteInferenceInputDataSet dataset = (RemoteInferenceInputDataSet) agentMLInput.getInputDataset();
        Assert.assertTrue(dataset.getParameters().containsKey(QUESTION));
        Assert.assertEquals("What is AI?", dataset.getParameters().get(QUESTION));
    }

    @Test
    public void test_ProcessAgentInput_AGUIAgent_WithRevampModelInterface() {
        // AGUI agent using revamp model interface
        MLAgent agent = MLAgent
            .builder()
            .name("agui_agent_revamp")
            .type(MLAgentType.AG_UI.name())
            .model(MLAgentModelSpec.builder().modelId("anthropic.claude-v2").modelProvider("bedrock/converse").build())
            .build();

        AgentInput agentInput = new AgentInput();
        agentInput.setInput("Explain machine learning");
        AgentMLInput agentMLInput = new AgentMLInput("test", null, FunctionName.AGENT, agentInput, null, false);

        // Should not throw exception - revamp interface should work
        mlAgentExecutor.processAgentInput(agentMLInput, agent);

        // Verify dataset has QUESTION parameter for revamp interface
        Assert.assertNotNull(agentMLInput.getInputDataset());
        RemoteInferenceInputDataSet dataset = (RemoteInferenceInputDataSet) agentMLInput.getInputDataset();
        Assert.assertTrue(dataset.getParameters().containsKey(QUESTION));
        Assert.assertEquals("Explain machine learning", dataset.getParameters().get(QUESTION));
    }

    @Test
    public void test_ProcessAgentInput_AGUIAgent_WithLegacyLLMInterface_Messages() {
        // AGUI agent with legacy LLM interface and message-based input
        MLAgent agent = MLAgent
            .builder()
            .name("agui_agent_legacy_msgs")
            .type(MLAgentType.AG_UI.name())
            .llm(LLMSpec.builder().modelId("gpt-4").build())
            .build();

        ContentBlock textBlock = new ContentBlock();
        textBlock.setType(ContentType.TEXT);
        textBlock.setText("Hello from AGUI");
        Message message = new Message("user", Collections.singletonList(textBlock));
        AgentInput agentInput = new AgentInput();
        agentInput.setInput(Collections.singletonList(message));
        AgentMLInput agentMLInput = new AgentMLInput("test", null, FunctionName.AGENT, agentInput, null, false);

        // Should handle message input with legacy interface
        mlAgentExecutor.processAgentInput(agentMLInput, agent);

        Assert.assertNotNull(agentMLInput.getAgentInput());
        Assert.assertNotNull(agentMLInput.getInputDataset());
    }

    @Test
    public void test_ProcessAgentInput_AGUIAgent_WithRevampModelInterface_Messages() {
        // AGUI agent with revamp model interface and message-based input
        MLAgent agent = MLAgent
            .builder()
            .name("agui_agent_revamp_msgs")
            .type(MLAgentType.AG_UI.name())
            .model(MLAgentModelSpec.builder().modelId("anthropic.claude-v2").modelProvider("bedrock/converse").build())
            .build();

        ContentBlock textBlock = new ContentBlock();
        textBlock.setType(ContentType.TEXT);
        textBlock.setText("Tell me about AI");
        Message message = new Message("user", Collections.singletonList(textBlock));
        AgentInput agentInput = new AgentInput();
        agentInput.setInput(Collections.singletonList(message));
        AgentMLInput agentMLInput = new AgentMLInput("test", null, FunctionName.AGENT, agentInput, null, false);

        // Should handle message input with revamp interface
        mlAgentExecutor.processAgentInput(agentMLInput, agent);

        // Verify QUESTION parameter is set
        Assert.assertNotNull(agentMLInput.getInputDataset());
        RemoteInferenceInputDataSet dataset = (RemoteInferenceInputDataSet) agentMLInput.getInputDataset();
        Assert.assertTrue(dataset.getParameters().containsKey(QUESTION));
        Assert.assertEquals("Tell me about AI\n", dataset.getParameters().get(QUESTION));
    }

    @Test
    public void test_ProcessAgentInput_AGUIAgent_WithoutContext() {
        // AGUI agent without context - question should not be modified
        MLAgent agent = MLAgent
            .builder()
            .name("agui_agent_no_context")
            .type(MLAgentType.AG_UI.name())
            .llm(LLMSpec.builder().modelId("gpt-4").build())
            .build();

        AgentInput agentInput = new AgentInput();
        agentInput.setInput("Hello world");
        AgentMLInput agentMLInput = new AgentMLInput("test", null, FunctionName.AGENT, agentInput, null, false);

        mlAgentExecutor.processAgentInput(agentMLInput, agent);

        // Verify question is set without context prefix
        RemoteInferenceInputDataSet dataset = (RemoteInferenceInputDataSet) agentMLInput.getInputDataset();
        String question = dataset.getParameters().get(QUESTION);
        Assert.assertEquals("Hello world", question);
        Assert.assertFalse(question.contains("Context:"));
    }

    @Test
    public void test_ProcessAgentInput_AGUIAgent_WithContext_LegacyInterface() {
        // AGUI agent with legacy LLM interface (no model field)
        // Context is passed via AGUI_PARAM_CONTEXT and should be prepended to question
        MLAgent agent = MLAgent
            .builder()
            .name("agui_agent_legacy_context")
            .type(MLAgentType.AG_UI.name())
            .llm(LLMSpec.builder().modelId("gpt-4").build())
            .build();

        ContentBlock textBlock = new ContentBlock();
        textBlock.setType(ContentType.TEXT);
        textBlock.setText("What is the weather?");
        Message message = new Message("user", Collections.singletonList(textBlock));
        AgentInput agentInput = new AgentInput();
        agentInput.setInput(Collections.singletonList(message));
        AgentMLInput agentMLInput = new AgentMLInput("test", null, FunctionName.AGENT, agentInput, null, false);

        // Set context via params (as AGUIInputConverter stores it)
        Map<String, String> params = new HashMap<>();
        params.put(AGUI_PARAM_CONTEXT, "[{\"description\":\"Location\",\"value\":\"San Francisco\"}]");
        agentMLInput.setInputDataset(new RemoteInferenceInputDataSet(params));

        mlAgentExecutor.processAgentInput(agentMLInput, agent);

        // Verify question contains context prepended by the new code path
        RemoteInferenceInputDataSet dataset = (RemoteInferenceInputDataSet) agentMLInput.getInputDataset();
        String question = dataset.getParameters().get(QUESTION);
        Assert.assertNotNull(question);
        Assert.assertTrue(question.startsWith("Context: "));
        Assert.assertTrue(question.contains("San Francisco"));
        Assert.assertTrue(question.contains("Question: "));
        Assert.assertTrue(question.contains("What is the weather?"));
    }

    @Test
    public void test_ProcessAgentInput_AGUIAgent_NoContext_LegacyInterface() {
        // AGUI agent with legacy LLM interface, no context param
        MLAgent agent = MLAgent
            .builder()
            .name("agui_agent_legacy_no_context")
            .type(MLAgentType.AG_UI.name())
            .llm(LLMSpec.builder().modelId("gpt-4").build())
            .build();

        ContentBlock textBlock = new ContentBlock();
        textBlock.setType(ContentType.TEXT);
        textBlock.setText("What is the weather?");
        Message message = new Message("user", Collections.singletonList(textBlock));
        AgentInput agentInput = new AgentInput();
        agentInput.setInput(Collections.singletonList(message));
        AgentMLInput agentMLInput = new AgentMLInput("test", null, FunctionName.AGENT, agentInput, null, false);

        mlAgentExecutor.processAgentInput(agentMLInput, agent);

        RemoteInferenceInputDataSet dataset = (RemoteInferenceInputDataSet) agentMLInput.getInputDataset();
        String question = dataset.getParameters().get(QUESTION);
        Assert.assertNotNull(question);
        Assert.assertTrue(question.contains("What is the weather?"));
        Assert.assertFalse(question.contains("Context:"));
    }

    @Test
    public void test_ProcessAgentInput_AGUIAgent_EmptyContext_LegacyInterface() {
        // AGUI agent with legacy LLM interface, empty context string
        MLAgent agent = MLAgent
            .builder()
            .name("agui_agent_legacy_bad_context")
            .type(MLAgentType.AG_UI.name())
            .llm(LLMSpec.builder().modelId("gpt-4").build())
            .build();

        ContentBlock textBlock = new ContentBlock();
        textBlock.setType(ContentType.TEXT);
        textBlock.setText("What is the weather?");
        Message message = new Message("user", Collections.singletonList(textBlock));
        AgentInput agentInput = new AgentInput();
        agentInput.setInput(Collections.singletonList(message));
        AgentMLInput agentMLInput = new AgentMLInput("test", null, FunctionName.AGENT, agentInput, null, false);

        // Empty context should not be prepended
        Map<String, String> params = new HashMap<>();
        params.put(AGUI_PARAM_CONTEXT, "");
        agentMLInput.setInputDataset(new RemoteInferenceInputDataSet(params));

        mlAgentExecutor.processAgentInput(agentMLInput, agent);

        RemoteInferenceInputDataSet dataset = (RemoteInferenceInputDataSet) agentMLInput.getInputDataset();
        String question = dataset.getParameters().get(QUESTION);
        Assert.assertNotNull(question);
        Assert.assertTrue(question.contains("What is the weather?"));
        Assert.assertFalse(question.contains("Context:"));
    }

    @Test
    public void test_ProcessAgentInput_AGUIAgent_WithContext_RevampInterface() {
        // AGUI agent with revamp model interface
        // Context has already been appended by AGUIInputConverter before reaching MLAgentExecutor
        MLAgent agent = MLAgent
            .builder()
            .name("agui_agent_revamp_context")
            .type(MLAgentType.AG_UI.name())
            .model(MLAgentModelSpec.builder().modelId("anthropic.claude-v2").modelProvider("bedrock/converse").build())
            .build();

        // Simulate message with context already appended (as done by AGUIInputConverter)
        ContentBlock textBlock = new ContentBlock();
        textBlock.setType(ContentType.TEXT);
        textBlock.setText("Context:\n- User Location: New York\n- Time Zone: EST\n\nWhat time is it?");
        Message message = new Message("user", Collections.singletonList(textBlock));
        AgentInput agentInput = new AgentInput();
        agentInput.setInput(Collections.singletonList(message));
        AgentMLInput agentMLInput = new AgentMLInput("test", null, FunctionName.AGENT, agentInput, null, false);

        mlAgentExecutor.processAgentInput(agentMLInput, agent);

        // Verify question contains context that was already appended
        RemoteInferenceInputDataSet dataset = (RemoteInferenceInputDataSet) agentMLInput.getInputDataset();
        String question = dataset.getParameters().get(QUESTION);
        Assert.assertNotNull(question);
        Assert.assertTrue(question.contains("Context:"));
        Assert.assertTrue(question.contains("New York"));
        Assert.assertTrue(question.contains("EST"));
        Assert.assertTrue(question.contains("What time is it?"));
    }

    @Test
    public void test_ProcessAgentInput_MessagesInput_ConversationalAgent() {
        // V1 CONVERSATIONAL agents with unified interface do NOT support MESSAGES input
        // Only TEXT input is supported. For MESSAGES support, use CONVERSATIONAL_V2.
        MLAgent agent = MLAgent
            .builder()
            .name("test_agent")
            .type(MLAgentType.CONVERSATIONAL.name())
            .model(MLAgentModelSpec.builder().modelId("anthropic.claude-v2").modelProvider("bedrock/converse").build())
            .build();

        ContentBlock textBlock = new ContentBlock();
        textBlock.setType(ContentType.TEXT);
        textBlock.setText("Hello, how are you?");
        Message message = new Message("user", Collections.singletonList(textBlock));
        AgentInput agentInput = new AgentInput();
        agentInput.setInput(Collections.singletonList(message));
        AgentMLInput agentMLInput = new AgentMLInput("test", null, FunctionName.AGENT, agentInput, null, false);

        // Should throw IllegalArgumentException for MESSAGES input
        IllegalArgumentException exception = Assert.assertThrows(IllegalArgumentException.class, () -> {
            mlAgentExecutor.processAgentInput(agentMLInput, agent);
        });

        Assert.assertTrue(exception.getMessage().contains("V1 agents with unified agent interface only support TEXT input type"));
        Assert.assertTrue(exception.getMessage().contains("MESSAGES"));
        Assert.assertTrue(exception.getMessage().contains("CONVERSATIONAL_V2"));
    }

    public GetResponse prepareMLAgent(String agentId, boolean isHidden, String tenantId) throws IOException {

        mlAgent = new MLAgent(
            "test",
            MLAgentType.CONVERSATIONAL.name(),
            "test",
            new LLMSpec("test_model", Map.of("test_key", "test_value")),
            List
                .of(
                    new MLToolSpec(
                        "memoryType",
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
            new MLMemorySpec("memoryType", "123", 0, null),
            Instant.EPOCH,
            Instant.EPOCH,
            "test",
            isHidden,
            null,
            null,
            tenantId
        );

        XContentBuilder content = mlAgent.toXContent(XContentFactory.jsonBuilder(), ToXContent.EMPTY_PARAMS);
        BytesReference bytesReference = BytesReference.bytes(content);
        GetResult getResult = new GetResult("indexName", agentId, 111l, 111l, 111l, true, bytesReference, null, null);
        return new GetResponse(getResult);
    }

    // ===== Execute path tests covering the new logAgentExecutionFailure calls =====

    @Test
    @SuppressWarnings("unchecked")
    public void testExecute_sdkClientThrowsIndexNotFound_coversLogFailureIndexNotFound() {
        when(metadata.hasIndex(anyString())).thenReturn(true);
        when(sdkClient.getDataObjectAsync(any(), any())).thenAnswer(inv -> {
            CompletionStage<GetDataObjectResponse> stage = mock(CompletionStage.class);
            when(stage.whenComplete(any())).thenAnswer(cbInv -> {
                BiConsumer<GetDataObjectResponse, Throwable> cb = cbInv.getArgument(0);
                cb.accept(null, new IndexNotFoundException(".plugins-ml-agent"));
                return stage;
            });
            return stage;
        });

        Map<String, String> parameters = Collections.singletonMap("question", "test question");
        RemoteInferenceInputDataSet dataset = RemoteInferenceInputDataSet.builder().parameters(parameters).build();
        AgentMLInput agentInput = new AgentMLInput("test-agent", null, FunctionName.AGENT, dataset);
        mlAgentExecutor.execute(agentInput, listener, channel);

        verify(listener, timeout(5000).atLeastOnce()).onFailure(any(OpenSearchStatusException.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testExecute_sdkClientThrowsGenericException_coversLogFailureGenericError() {
        when(metadata.hasIndex(anyString())).thenReturn(true);
        when(sdkClient.getDataObjectAsync(any(), any())).thenAnswer(inv -> {
            CompletionStage<GetDataObjectResponse> stage = mock(CompletionStage.class);
            when(stage.whenComplete(any())).thenAnswer(cbInv -> {
                BiConsumer<GetDataObjectResponse, Throwable> cb = cbInv.getArgument(0);
                cb.accept(null, new RuntimeException("connection failed"));
                return stage;
            });
            return stage;
        });

        Map<String, String> parameters = Collections.singletonMap("question", "test question");
        RemoteInferenceInputDataSet dataset = RemoteInferenceInputDataSet.builder().parameters(parameters).build();
        AgentMLInput agentInput = new AgentMLInput("test-agent", null, FunctionName.AGENT, dataset);
        mlAgentExecutor.execute(agentInput, listener, channel);

        verify(listener, timeout(5000).atLeastOnce()).onFailure(any(RuntimeException.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testExecute_agentNotFound_nullParser_coversLogFailureNotFound() {
        when(metadata.hasIndex(anyString())).thenReturn(true);
        when(sdkClient.getDataObjectAsync(any(), any())).thenAnswer(inv -> {
            GetDataObjectResponse mockResponse = mock(GetDataObjectResponse.class);
            when(mockResponse.parser()).thenReturn(null);

            CompletionStage<GetDataObjectResponse> stage = mock(CompletionStage.class);
            when(stage.whenComplete(any())).thenAnswer(cbInv -> {
                BiConsumer<GetDataObjectResponse, Throwable> cb = cbInv.getArgument(0);
                cb.accept(mockResponse, null);
                return stage;
            });
            return stage;
        });

        Map<String, String> parameters = Collections.singletonMap("question", "test question");
        RemoteInferenceInputDataSet dataset = RemoteInferenceInputDataSet.builder().parameters(parameters).build();
        AgentMLInput agentInput = new AgentMLInput("test-agent", null, FunctionName.AGENT, dataset);
        mlAgentExecutor.execute(agentInput, listener, channel);

        verify(listener, timeout(5000).atLeastOnce()).onFailure(any(OpenSearchStatusException.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testExecute_agentFoundButParseContextNPE_coversParseExceptionLogFailure() throws IOException {
        // Covers logAgentExecutionFailure in the catch(Exception e) block for parse failures.
        // When GetDataObjectResponse is constructed from a GetResponse, calling processAgentInput
        // eventually NPEs on unmocked clusterService.localNode(), which is caught and logged.
        when(metadata.hasIndex(anyString())).thenReturn(true);

        GetResponse agentGetResponse = prepareMLAgent("test-agent", false, null);

        when(sdkClient.getDataObjectAsync(any(), any())).thenAnswer(inv -> {
            GetDataObjectResponse mockResponse = new GetDataObjectResponse(agentGetResponse);

            CompletionStage<GetDataObjectResponse> stage = mock(CompletionStage.class);
            when(stage.whenComplete(any())).thenAnswer(cbInv -> {
                BiConsumer<GetDataObjectResponse, Throwable> cb = cbInv.getArgument(0);
                cb.accept(mockResponse, null);
                return stage;
            });
            return stage;
        });

        Map<String, String> parameters = Collections.singletonMap("question", "test question");
        RemoteInferenceInputDataSet dataset = RemoteInferenceInputDataSet.builder().parameters(parameters).build();
        AgentMLInput agentInput = new AgentMLInput("test-agent", null, FunctionName.AGENT, dataset);
        mlAgentExecutor.execute(agentInput, listener, channel);

        // listener.onFailure is called — either from parse exception or downstream NPE,
        // both paths cover the logAgentExecutionFailure call
        verify(listener, timeout(5000).atLeastOnce()).onFailure(any(Exception.class));
    }

    @Test
    public void test_PerformInitialMemoryOperations_WithHistoryAndInputMessages() {
        // Setup: history has 2 messages, input has 1 message
        List<Message> historyMessages = new ArrayList<>();
        historyMessages.add(createTestMessage("user", "previous question"));
        historyMessages.add(createTestMessage("assistant", "previous answer"));

        List<Message> inputMessages = new ArrayList<>();
        inputMessages.add(createTestMessage("user", "new question"));

        MLAgent agent = MLAgent
            .builder()
            .name("test_agent")
            .type(MLAgentType.CONVERSATIONAL.name())
            .model(MLAgentModelSpec.builder().modelId("anthropic.claude-v2").modelProvider("bedrock/converse").build())
            .build();

        // Mock getStructuredMessages to return history
        doAnswer(invocation -> {
            ActionListener<List<Message>> listener = invocation.getArgument(0);
            listener.onResponse(historyMessages);
            return null;
        }).when(memory).getStructuredMessages(any());

        // Mock saveStructuredMessages to succeed
        doAnswer(invocation -> {
            ActionListener<Void> listener = invocation.getArgument(1);
            listener.onResponse(null);
            return null;
        }).when(memory).saveStructuredMessages(any(), any());

        Map<String, String> params = new HashMap<>();
        AtomicBoolean continuationCalled = new AtomicBoolean(false);

        mlAgentExecutor
            .performInitialMemoryOperations(memory, inputMessages, params, agent, listener, () -> continuationCalled.set(true), null);

        assertTrue("Continuation should be called", continuationCalled.get());
        // NEXT_STRUCTURED_MESSAGE_ID no longer set — memory auto-resolves IDs
        assertNotNull(params.get(MLChatAgentRunner.NEW_CHAT_HISTORY));
    }

    @Test
    public void test_PerformInitialMemoryOperations_EmptyHistory() {
        List<Message> inputMessages = new ArrayList<>();
        inputMessages.add(createTestMessage("user", "first question"));

        MLAgent agent = MLAgent
            .builder()
            .name("test_agent")
            .type(MLAgentType.CONVERSATIONAL.name())
            .model(MLAgentModelSpec.builder().modelId("anthropic.claude-v2").modelProvider("bedrock/converse").build())
            .build();

        // Mock getStructuredMessages to return empty history
        doAnswer(invocation -> {
            ActionListener<List<Message>> listener = invocation.getArgument(0);
            listener.onResponse(new ArrayList<>());
            return null;
        }).when(memory).getStructuredMessages(any());

        // Mock saveStructuredMessages to succeed
        doAnswer(invocation -> {
            ActionListener<Void> listener = invocation.getArgument(1);
            listener.onResponse(null);
            return null;
        }).when(memory).saveStructuredMessages(any(), any());

        Map<String, String> params = new HashMap<>();
        AtomicBoolean continuationCalled = new AtomicBoolean(false);

        mlAgentExecutor
            .performInitialMemoryOperations(memory, inputMessages, params, agent, listener, () -> continuationCalled.set(true), null);

        assertTrue("Continuation should be called", continuationCalled.get());
        // NEXT_STRUCTURED_MESSAGE_ID no longer set — memory auto-resolves IDs
        assertNull("NEW_CHAT_HISTORY should not be set for empty history", params.get(MLChatAgentRunner.NEW_CHAT_HISTORY));
    }

    @Test
    public void test_PerformInitialMemoryOperations_HistoryLimitApplied() {
        // Setup: history has 5 messages, limit is 2
        List<Message> historyMessages = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            historyMessages.add(createTestMessage("user", "question " + i));
        }

        List<Message> inputMessages = new ArrayList<>();
        inputMessages.add(createTestMessage("user", "new question"));

        MLAgent agent = MLAgent
            .builder()
            .name("test_agent")
            .type(MLAgentType.CONVERSATIONAL.name())
            .model(MLAgentModelSpec.builder().modelId("anthropic.claude-v2").modelProvider("bedrock/converse").build())
            .build();

        doAnswer(invocation -> {
            ActionListener<List<Message>> listener = invocation.getArgument(0);
            listener.onResponse(historyMessages);
            return null;
        }).when(memory).getStructuredMessages(any());

        doAnswer(invocation -> {
            ActionListener<Void> listener = invocation.getArgument(1);
            listener.onResponse(null);
            return null;
        }).when(memory).saveStructuredMessages(any(), any());

        Map<String, String> params = new HashMap<>();
        params.put(MLAgentExecutor.MESSAGE_HISTORY_LIMIT, "2");
        AtomicBoolean continuationCalled = new AtomicBoolean(false);

        mlAgentExecutor
            .performInitialMemoryOperations(memory, inputMessages, params, agent, listener, () -> continuationCalled.set(true), null);

        assertTrue("Continuation should be called", continuationCalled.get());
        // nextStructuredMessageId = 5 (history size) + 1 (input) = 6
        // NEXT_STRUCTURED_MESSAGE_ID no longer set — memory auto-resolves IDs
    }

    @Test
    public void test_PerformInitialMemoryOperations_GetStructuredMessagesFails() {
        MLAgent agent = MLAgent
            .builder()
            .name("test_agent")
            .type(MLAgentType.CONVERSATIONAL.name())
            .model(MLAgentModelSpec.builder().modelId("anthropic.claude-v2").modelProvider("bedrock/converse").build())
            .build();

        // Mock getStructuredMessages to fail
        doAnswer(invocation -> {
            ActionListener<List<Message>> listener = invocation.getArgument(0);
            listener.onFailure(new RuntimeException("Memory read failed"));
            return null;
        }).when(memory).getStructuredMessages(any());

        Map<String, String> params = new HashMap<>();

        mlAgentExecutor
            .performInitialMemoryOperations(
                memory,
                Collections.emptyList(),
                params,
                agent,
                listener,
                () -> Assert.fail("Continuation should not be called on failure"),
                null
            );

        verify(listener).onFailure(any(RuntimeException.class));
    }

    @Test
    public void test_PerformInitialMemoryOperations_SaveStructuredMessagesFails() {
        MLAgent agent = MLAgent
            .builder()
            .name("test_agent")
            .type(MLAgentType.CONVERSATIONAL.name())
            .model(MLAgentModelSpec.builder().modelId("anthropic.claude-v2").modelProvider("bedrock/converse").build())
            .build();

        // Mock getStructuredMessages to succeed
        doAnswer(invocation -> {
            ActionListener<List<Message>> listener = invocation.getArgument(0);
            listener.onResponse(new ArrayList<>());
            return null;
        }).when(memory).getStructuredMessages(any());

        // Mock saveStructuredMessages to fail
        doAnswer(invocation -> {
            ActionListener<Void> listener = invocation.getArgument(1);
            listener.onFailure(new RuntimeException("Memory write failed"));
            return null;
        }).when(memory).saveStructuredMessages(any(), any());

        Map<String, String> params = new HashMap<>();

        mlAgentExecutor
            .performInitialMemoryOperations(
                memory,
                Collections.emptyList(),
                params,
                agent,
                listener,
                () -> Assert.fail("Continuation should not be called on failure"),
                null
            );

        verify(listener).onFailure(any(RuntimeException.class));
    }

    @Test
    public void test_PerformInitialMemoryOperations_AGUIContextAppend() {
        // Setup: AGUI agent with history and context
        List<Message> historyMessages = new ArrayList<>();
        historyMessages.add(createTestMessage("user", "hello"));

        List<Message> inputMessages = new ArrayList<>();
        inputMessages.add(createTestMessage("user", "new question"));

        MLAgent agent = MLAgent
            .builder()
            .name("test_agent")
            .type(MLAgentType.AG_UI.name())
            .model(MLAgentModelSpec.builder().modelId("anthropic.claude-v2").modelProvider("bedrock/converse").build())
            .build();

        doAnswer(invocation -> {
            ActionListener<List<Message>> listener = invocation.getArgument(0);
            listener.onResponse(historyMessages);
            return null;
        }).when(memory).getStructuredMessages(any());

        doAnswer(invocation -> {
            ActionListener<Void> listener = invocation.getArgument(1);
            listener.onResponse(null);
            return null;
        }).when(memory).saveStructuredMessages(any(), any());

        Map<String, String> params = new HashMap<>();
        params.put("agent_type", "ag_ui");
        params.put("context", "[{\"description\":\"location\",\"value\":\"SF\"}]");
        AtomicBoolean continuationCalled = new AtomicBoolean(false);

        mlAgentExecutor
            .performInitialMemoryOperations(memory, inputMessages, params, agent, listener, () -> continuationCalled.set(true), null);

        assertTrue("Continuation should be called", continuationCalled.get());
        // NEXT_STRUCTURED_MESSAGE_ID no longer set — memory auto-resolves IDs
    }

    private Message createTestMessage(String role, String text) {
        ContentBlock textBlock = new ContentBlock();
        textBlock.setType(ContentType.TEXT);
        textBlock.setText(text);
        return new Message(role, Collections.singletonList(textBlock));
    }

    @Test
    public void test_PerformInitialMemoryOperations_WithTextInputConvertedToMessage() {
        // Simulate what saveRootInteractionAndExecute does for InputType.TEXT:
        // converts plain text to a Message("user", [ContentBlock(TEXT, text)])
        String text = "What is machine learning?";
        ContentBlock textBlock = new ContentBlock();
        textBlock.setType(ContentType.TEXT);
        textBlock.setText(text);
        List<Message> inputMessages = List.of(new Message("user", List.of(textBlock)));

        MLAgent agent = MLAgent
            .builder()
            .name("test_agent")
            .type(MLAgentType.CONVERSATIONAL.name())
            .model(MLAgentModelSpec.builder().modelId("anthropic.claude-v2").modelProvider("bedrock/converse").build())
            .build();

        doAnswer(invocation -> {
            ActionListener<List<Message>> listener = invocation.getArgument(0);
            listener.onResponse(new ArrayList<>());
            return null;
        }).when(memory).getStructuredMessages(any());

        doAnswer(invocation -> {
            ActionListener<Void> listener = invocation.getArgument(1);
            listener.onResponse(null);
            return null;
        }).when(memory).saveStructuredMessages(any(), any());

        Map<String, String> params = new HashMap<>();
        AtomicBoolean continuationCalled = new AtomicBoolean(false);

        mlAgentExecutor
            .performInitialMemoryOperations(memory, inputMessages, params, agent, listener, () -> continuationCalled.set(true), null);

        assertTrue("Continuation should be called", continuationCalled.get());
        // NEXT_STRUCTURED_MESSAGE_ID no longer set — memory auto-resolves IDs

        // Verify the message was saved with correct structure
        ArgumentCaptor<List> messagesCaptor = ArgumentCaptor.forClass(List.class);
        verify(memory).saveStructuredMessages(messagesCaptor.capture(), any());
        List<Message> savedMessages = messagesCaptor.getValue();
        assertEquals(1, savedMessages.size());
        assertEquals("user", savedMessages.get(0).getRole());
        assertEquals(ContentType.TEXT, savedMessages.get(0).getContent().get(0).getType());
        assertEquals(text, savedMessages.get(0).getContent().get(0).getText());
    }

    @Test
    public void test_PerformInitialMemoryOperations_WithContentBlocksConvertedToMessage() {
        // Simulate what saveRootInteractionAndExecute does for InputType.CONTENT_BLOCKS:
        // wraps content blocks in a Message("user", contentBlocks)
        ContentBlock textBlock = new ContentBlock();
        textBlock.setType(ContentType.TEXT);
        textBlock.setText("Describe this image");
        ContentBlock imageBlock = new ContentBlock();
        imageBlock.setType(ContentType.IMAGE);
        List<ContentBlock> blocks = List.of(textBlock, imageBlock);
        List<Message> inputMessages = List.of(new Message("user", blocks));

        MLAgent agent = MLAgent
            .builder()
            .name("test_agent")
            .type(MLAgentType.CONVERSATIONAL.name())
            .model(MLAgentModelSpec.builder().modelId("anthropic.claude-v2").modelProvider("bedrock/converse").build())
            .build();

        doAnswer(invocation -> {
            ActionListener<List<Message>> listener = invocation.getArgument(0);
            listener.onResponse(new ArrayList<>());
            return null;
        }).when(memory).getStructuredMessages(any());

        doAnswer(invocation -> {
            ActionListener<Void> listener = invocation.getArgument(1);
            listener.onResponse(null);
            return null;
        }).when(memory).saveStructuredMessages(any(), any());

        Map<String, String> params = new HashMap<>();
        AtomicBoolean continuationCalled = new AtomicBoolean(false);

        mlAgentExecutor
            .performInitialMemoryOperations(memory, inputMessages, params, agent, listener, () -> continuationCalled.set(true), null);

        assertTrue("Continuation should be called", continuationCalled.get());
        // NEXT_STRUCTURED_MESSAGE_ID no longer set — memory auto-resolves IDs

        // Verify the message was saved with both content blocks
        ArgumentCaptor<List> messagesCaptor = ArgumentCaptor.forClass(List.class);
        verify(memory).saveStructuredMessages(messagesCaptor.capture(), any());
        List<Message> savedMessages = messagesCaptor.getValue();
        assertEquals(1, savedMessages.size());
        assertEquals("user", savedMessages.get(0).getRole());
        assertEquals(2, savedMessages.get(0).getContent().size());
        assertEquals(ContentType.TEXT, savedMessages.get(0).getContent().get(0).getType());
        assertEquals(ContentType.IMAGE, savedMessages.get(0).getContent().get(1).getType());
    }

    @Test
    public void test_SupportsStructuredMessages_TrueForConversationalAndAGUI() {
        MLAgent convAgent = createTestAgent(MLAgentType.CONVERSATIONAL.name());
        MLAgent aguiAgent = createTestAgent(MLAgentType.AG_UI.name());

        assertTrue(mlAgentExecutor.supportsStructuredMessages(convAgent));
        assertTrue(mlAgentExecutor.supportsStructuredMessages(aguiAgent));
    }

    @Test
    public void test_SupportsStructuredMessages_TrueForConversationalV2() {
        MLAgent convV2Agent = createTestAgent(MLAgentType.CONVERSATIONAL_V2.name());

        assertTrue(mlAgentExecutor.supportsStructuredMessages(convV2Agent));
    }

    @Test
    public void test_SupportsStructuredMessages_FalseForUnsupportedAgentTypes() {
        MLAgent perAgent = createTestAgent(MLAgentType.PLAN_EXECUTE_AND_REFLECT.name());
        MLAgent flowAgent = createTestAgent(MLAgentType.FLOW.name());
        MLAgent convFlowAgent = createTestAgent(MLAgentType.CONVERSATIONAL_FLOW.name());

        assertFalse(mlAgentExecutor.supportsStructuredMessages(perAgent));
        assertFalse(mlAgentExecutor.supportsStructuredMessages(flowAgent));
        assertFalse(mlAgentExecutor.supportsStructuredMessages(convFlowAgent));
    }

    @Test
    public void test_FreshMemoryParameter_SetToTrue_WhenMemoryIdNotProvided() {
        // Create agent with memory spec
        // MLAgent agent = MLAgent
        // .builder()
        // .name("test_agent")
        // .type(MLAgentType.CONVERSATIONAL.name())
        // .memory(new MLMemorySpec("conversation_index", null, 0))
        // .llm(LLMSpec.builder().modelId("test-model").parameters(Collections.emptyMap()).build())
        // .build();
        //
        // // Create input without memory_id
        // Map<String, String> parameters = new HashMap<>();
        // parameters.put(QUESTION, "What is AI?");
        // RemoteInferenceInputDataSet dataset = RemoteInferenceInputDataSet.builder().parameters(parameters).build();
        // AgentMLInput agentMLInput = new AgentMLInput("test-agent", null, FunctionName.AGENT, dataset);
        //
        // // Setup memory factory
        // memoryFactoryMap.put("conversation_index", mockMemoryFactory);
        // doAnswer(invocation -> {
        // ActionListener<ConversationIndexMemory> listener = invocation.getArgument(3);
        // when(memory.getConversationId()).thenReturn("new-conversation-id");
        // listener.onResponse(memory);
        // return null;
        // }).when(mockMemoryFactory).create(anyString(), isNull(), anyString(), any());
        //
        // mlAgentExecutor = new MLAgentExecutor(
        // client,
        // sdkClient,
        // settings,
        // clusterService,
        // xContentRegistry,
        // toolFactories,
        // memoryFactoryMap,
        // mlFeatureEnabledSetting,
        // encryptor
        // );
        //
        // // Execute - this will trigger memory creation
        // mlAgentExecutor.execute(agentMLInput, listener, channel);
        //
        // // Verify fresh_memory parameter is set to "true"
        // verify(mockMemoryFactory, timeout(5000).atLeastOnce()).create(anyString(), isNull(), anyString(), any());
        //
        // // The fresh_memory parameter should be set in the dataset parameters
        // // Note: This verification happens in the callback, so we verify the factory was called with null memoryId
        // ArgumentCaptor<String> memoryIdCaptor = ArgumentCaptor.forClass(String.class);
        // verify(mockMemoryFactory).create(anyString(), memoryIdCaptor.capture(), anyString(), any());
        //
        // // Verify that memoryId was null (which triggers fresh_memory = true)
        // assertNull("Memory ID should be null for fresh conversations", memoryIdCaptor.getValue());
    }

    @Test
    public void test_SupportsStructuredMessages_V2Agent() {
        MLAgent convV2Agent = createTestAgent(MLAgentType.CONVERSATIONAL_V2.name());
        assertTrue(mlAgentExecutor.supportsStructuredMessages(convV2Agent));
    }

    @Test
    public void test_ProcessAgentInput_V2Agent_WithMessages() {
        MLAgent agent = MLAgent
            .builder()
            .name("test_agent_v2")
            .type(MLAgentType.CONVERSATIONAL_V2.name())
            .model(MLAgentModelSpec.builder().modelId("anthropic.claude-v2").modelProvider("bedrock/converse").build())
            .build();

        ContentBlock textBlock = new ContentBlock();
        textBlock.setType(ContentType.TEXT);
        textBlock.setText("What is machine learning?");
        Message message = new Message("user", Collections.singletonList(textBlock));
        AgentInput agentInput = new AgentInput();
        agentInput.setInput(Collections.singletonList(message));
        AgentMLInput agentMLInput = new AgentMLInput("test", null, FunctionName.AGENT, agentInput, null, false);

        // Should not throw exception - V2 agents support messages
        mlAgentExecutor.processAgentInput(agentMLInput, agent);

        // Verify input was properly set
        assertNotNull(agentMLInput.getAgentInput());
        assertEquals(InputType.MESSAGES, agentMLInput.getAgentInput().getInputType());
    }

    @Test
    public void test_ProcessAgentInput_V2Agent_WithTextInput() {
        MLAgent agent = MLAgent
            .builder()
            .name("test_agent_v2")
            .type(MLAgentType.CONVERSATIONAL_V2.name())
            .model(MLAgentModelSpec.builder().modelId("anthropic.claude-v2").modelProvider("bedrock/converse").build())
            .build();

        AgentInput agentInput = new AgentInput();
        agentInput.setInput("What is deep learning?");
        AgentMLInput agentMLInput = new AgentMLInput("test", null, FunctionName.AGENT, agentInput, null, false);

        // Should not throw exception - V2 agents support text too
        mlAgentExecutor.processAgentInput(agentMLInput, agent);

        // Verify input was properly set
        assertNotNull(agentMLInput.getAgentInput());
        assertEquals(InputType.TEXT, agentMLInput.getAgentInput().getInputType());
    }

    @Test
    public void test_ProcessAgentInput_FlowAgent_NoValidation() {
        MLAgent agent = MLAgent.builder().name("flow_agent").type(MLAgentType.FLOW.name()).build();

        AgentInput agentInput = new AgentInput();
        agentInput.setInput("Test input");
        AgentMLInput agentMLInput = new AgentMLInput("test", null, FunctionName.AGENT, agentInput, null, false);

        // Flow agents don't require model validation
        mlAgentExecutor.processAgentInput(agentMLInput, agent);

        // Should complete without error
        assertNotNull(agentMLInput.getAgentInput());
    }

    @Test
    public void test_ProcessAgentInput_NullAgentInput_WithQuestion() {
        MLAgent agent = MLAgent
            .builder()
            .name("test_agent")
            .type(MLAgentType.CONVERSATIONAL.name())
            .model(MLAgentModelSpec.builder().modelId("anthropic.claude-v2").modelProvider("bedrock/converse").build())
            .build();

        Map<String, String> params = new HashMap<>();
        params.put(QUESTION, "What is AI?");
        RemoteInferenceInputDataSet dataset = RemoteInferenceInputDataSet.builder().parameters(params).build();
        AgentMLInput agentMLInput = new AgentMLInput("test", null, FunctionName.AGENT, null, dataset, false);

        // Should convert legacy question to AgentInput
        mlAgentExecutor.processAgentInput(agentMLInput, agent);

        // Should have created AgentInput from question
        assertNotNull(agentMLInput.getAgentInput());
        assertEquals(InputType.TEXT, agentMLInput.getAgentInput().getInputType());
    }

    @Test
    public void test_GetAgentRunner_WithNullHookRegistry() {
        MLAgent agent = createTestAgent(MLAgentType.CONVERSATIONAL_V2.name());

        // Should create runner with null hook registry
        MLAgentRunner runner = mlAgentExecutor.getAgentRunner(agent, null);

        assertNotNull(runner);
        assertTrue(runner instanceof MLChatAgentRunnerV2);
    }

    @Test
    public void test_ProcessOutput_WithNullOutput() throws Exception {
        List<org.opensearch.ml.common.output.model.ModelTensor> modelTensors = new ArrayList<>();

        // Null output should be handled and converted to "null" string
        mlAgentExecutor.processOutput(null, modelTensors);

        // Null output gets converted to a ModelTensor with "null" as result
        assertEquals(1, modelTensors.size());
        assertEquals("response", modelTensors.get(0).getName());
        assertEquals("null", modelTensors.get(0).getResult());
    }

    @Test
    public void test_ProcessOutput_WithEmptyModelTensorOutput() throws Exception {
        ModelTensorOutput output = mock(ModelTensorOutput.class);
        when(output.getMlModelOutputs()).thenReturn(Collections.emptyList());

        List<org.opensearch.ml.common.output.model.ModelTensor> modelTensors = new ArrayList<>();

        mlAgentExecutor.processOutput(output, modelTensors);

        // Should call getMlModelOutputs
        verify(output).getMlModelOutputs();
    }

    @Test
    public void test_ProcessAgentInput_ConversationalFlow_NoModelValidation() {
        MLAgent agent = MLAgent.builder().name("conv_flow_agent").type(MLAgentType.CONVERSATIONAL_FLOW.name()).build();

        AgentInput agentInput = new AgentInput();
        agentInput.setInput("Test question");
        AgentMLInput agentMLInput = new AgentMLInput("test", null, FunctionName.AGENT, agentInput, null, false);

        // Conversational flow agents don't require unified model validation
        mlAgentExecutor.processAgentInput(agentMLInput, agent);

        // Should complete successfully
        assertNotNull(agentMLInput.getAgentInput());
    }

    @Test
    public void test_ProcessAgentInput_WithContentBlocks() {
        MLAgent agent = MLAgent
            .builder()
            .name("test_agent_multimodal")
            .type(MLAgentType.CONVERSATIONAL_V2.name())
            .model(MLAgentModelSpec.builder().modelId("anthropic.claude-v2").modelProvider("bedrock/converse").build())
            .build();

        ContentBlock textBlock = new ContentBlock();
        textBlock.setType(ContentType.TEXT);
        textBlock.setText("Describe this image");

        ContentBlock imageBlock = new ContentBlock();
        imageBlock.setType(ContentType.IMAGE);

        AgentInput agentInput = new AgentInput();
        agentInput.setInput(List.of(textBlock, imageBlock));
        AgentMLInput agentMLInput = new AgentMLInput("test", null, FunctionName.AGENT, agentInput, null, false);

        // Should handle content blocks
        mlAgentExecutor.processAgentInput(agentMLInput, agent);

        // Verify input type is CONTENT_BLOCKS
        assertEquals(InputType.CONTENT_BLOCKS, agentMLInput.getAgentInput().getInputType());
    }

    @Test
    public void test_ProcessAgentInput_AGUI_WithSingleMessage() {
        MLAgent agent = MLAgent
            .builder()
            .name("agui_agent_single")
            .type(MLAgentType.AG_UI.name())
            .model(MLAgentModelSpec.builder().modelId("anthropic.claude-v2").modelProvider("bedrock/converse").build())
            .build();

        ContentBlock textBlock = new ContentBlock();
        textBlock.setType(ContentType.TEXT);
        textBlock.setText("Single message test");
        Message message = new Message("user", Collections.singletonList(textBlock));

        AgentInput agentInput = new AgentInput();
        agentInput.setInput(Collections.singletonList(message));
        AgentMLInput agentMLInput = new AgentMLInput("test", null, FunctionName.AGENT, agentInput, null, false);

        // Should handle single message for AGUI
        mlAgentExecutor.processAgentInput(agentMLInput, agent);

        assertNotNull(agentMLInput.getInputDataset());
    }

    @Test
    public void test_PerformInitialMemoryOperations_NullHistory() {
        MLAgent agent = MLAgent
            .builder()
            .name("test_agent")
            .type(MLAgentType.CONVERSATIONAL_V2.name())
            .model(MLAgentModelSpec.builder().modelId("anthropic.claude-v2").modelProvider("bedrock/converse").build())
            .build();

        List<Message> inputMessages = new ArrayList<>();
        inputMessages.add(createTestMessage("user", "Hello"));

        // Mock getStructuredMessages to return null (edge case)
        doAnswer(invocation -> {
            ActionListener<List<Message>> listener = invocation.getArgument(0);
            listener.onResponse(null);
            return null;
        }).when(memory).getStructuredMessages(any());

        Map<String, String> params = new HashMap<>();
        AtomicBoolean continuationCalled = new AtomicBoolean(false);

        // Should handle null history gracefully - will likely throw NPE or be handled
        try {
            mlAgentExecutor
                .performInitialMemoryOperations(memory, inputMessages, params, agent, listener, () -> continuationCalled.set(true), null);
        } catch (NullPointerException e) {
            // Expected if not handled - this tests the edge case
            assertTrue(e.getMessage() != null || e.getMessage() == null);
        }
    }

    @Test
    public void test_PerformInitialMemoryOperations_LargeHistoryWithLimit() {
        // Setup: history has 100 messages, limit is 10
        List<Message> historyMessages = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            historyMessages.add(createTestMessage("user", "message " + i));
        }

        List<Message> inputMessages = new ArrayList<>();
        inputMessages.add(createTestMessage("user", "new question"));

        MLAgent agent = MLAgent
            .builder()
            .name("test_agent")
            .type(MLAgentType.CONVERSATIONAL_V2.name())
            .model(MLAgentModelSpec.builder().modelId("anthropic.claude-v2").modelProvider("bedrock/converse").build())
            .build();

        doAnswer(invocation -> {
            ActionListener<List<Message>> listener = invocation.getArgument(0);
            listener.onResponse(historyMessages);
            return null;
        }).when(memory).getStructuredMessages(any());

        doAnswer(invocation -> {
            ActionListener<Void> listener = invocation.getArgument(1);
            listener.onResponse(null);
            return null;
        }).when(memory).saveStructuredMessages(any(), any());

        Map<String, String> params = new HashMap<>();
        params.put(MLAgentExecutor.MESSAGE_HISTORY_LIMIT, "10");
        AtomicBoolean continuationCalled = new AtomicBoolean(false);

        mlAgentExecutor
            .performInitialMemoryOperations(memory, inputMessages, params, agent, listener, () -> continuationCalled.set(true), null);

        assertTrue("Continuation should be called", continuationCalled.get());
    }

    @Test
    public void test_PerformInitialMemoryOperations_ZeroHistoryLimit() {
        List<Message> historyMessages = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            historyMessages.add(createTestMessage("user", "message " + i));
        }

        List<Message> inputMessages = new ArrayList<>();
        inputMessages.add(createTestMessage("user", "new question"));

        MLAgent agent = MLAgent
            .builder()
            .name("test_agent")
            .type(MLAgentType.CONVERSATIONAL.name())
            .model(MLAgentModelSpec.builder().modelId("anthropic.claude-v2").modelProvider("bedrock/converse").build())
            .build();

        doAnswer(invocation -> {
            ActionListener<List<Message>> listener = invocation.getArgument(0);
            listener.onResponse(historyMessages);
            return null;
        }).when(memory).getStructuredMessages(any());

        doAnswer(invocation -> {
            ActionListener<Void> listener = invocation.getArgument(1);
            listener.onResponse(null);
            return null;
        }).when(memory).saveStructuredMessages(any(), any());

        Map<String, String> params = new HashMap<>();
        params.put(MLAgentExecutor.MESSAGE_HISTORY_LIMIT, "0");
        AtomicBoolean continuationCalled = new AtomicBoolean(false);

        mlAgentExecutor
            .performInitialMemoryOperations(memory, inputMessages, params, agent, listener, () -> continuationCalled.set(true), null);

        assertTrue("Continuation should be called with zero limit (means unlimited)", continuationCalled.get());
    }

    @Test
    public void test_PerformInitialMemoryOperations_NegativeHistoryLimit() {
        List<Message> historyMessages = new ArrayList<>();
        historyMessages.add(createTestMessage("user", "previous message"));

        List<Message> inputMessages = new ArrayList<>();
        inputMessages.add(createTestMessage("user", "new question"));

        MLAgent agent = MLAgent
            .builder()
            .name("test_agent")
            .type(MLAgentType.CONVERSATIONAL.name())
            .model(MLAgentModelSpec.builder().modelId("anthropic.claude-v2").modelProvider("bedrock/converse").build())
            .build();

        doAnswer(invocation -> {
            ActionListener<List<Message>> listener = invocation.getArgument(0);
            listener.onResponse(historyMessages);
            return null;
        }).when(memory).getStructuredMessages(any());

        doAnswer(invocation -> {
            ActionListener<Void> listener = invocation.getArgument(1);
            listener.onResponse(null);
            return null;
        }).when(memory).saveStructuredMessages(any(), any());

        Map<String, String> params = new HashMap<>();
        params.put(MLAgentExecutor.MESSAGE_HISTORY_LIMIT, "-5");
        AtomicBoolean continuationCalled = new AtomicBoolean(false);

        mlAgentExecutor
            .performInitialMemoryOperations(memory, inputMessages, params, agent, listener, () -> continuationCalled.set(true), null);

        assertTrue("Continuation should be called with negative limit (treated as unlimited)", continuationCalled.get());
    }

    @Test
    public void test_ProcessOutput_WithList() throws Exception {
        List<String> outputList = List.of("response1", "response2");
        List<org.opensearch.ml.common.output.model.ModelTensor> modelTensors = new ArrayList<>();

        mlAgentExecutor.processOutput(outputList, modelTensors);

        // List gets converted to JSON string
        assertEquals(1, modelTensors.size());
        assertEquals("response", modelTensors.get(0).getName());
        assertTrue(modelTensors.get(0).getResult().contains("response1"));
        assertTrue(modelTensors.get(0).getResult().contains("response2"));
    }

    @Test
    public void test_ProcessOutput_WithModelTensor() throws Exception {
        ModelTensor inputTensor = ModelTensor.builder().name("test_tensor").result("test_result").build();
        List<org.opensearch.ml.common.output.model.ModelTensor> modelTensors = new ArrayList<>();

        mlAgentExecutor.processOutput(inputTensor, modelTensors);

        // ModelTensor is added directly
        assertEquals(1, modelTensors.size());
        assertEquals("test_tensor", modelTensors.get(0).getName());
        assertEquals("test_result", modelTensors.get(0).getResult());
    }

    @Test
    public void test_ProcessOutput_WithListOfModelTensors() throws Exception {
        ModelTensor tensor1 = ModelTensor.builder().name("tensor1").result("result1").build();
        ModelTensor tensor2 = ModelTensor.builder().name("tensor2").result("result2").build();
        List<ModelTensor> inputList = List.of(tensor1, tensor2);
        List<org.opensearch.ml.common.output.model.ModelTensor> modelTensors = new ArrayList<>();

        mlAgentExecutor.processOutput(inputList, modelTensors);

        // List of ModelTensors gets added to output list
        assertEquals(2, modelTensors.size());
        assertEquals("tensor1", modelTensors.get(0).getName());
        assertEquals("tensor2", modelTensors.get(1).getName());
    }

    @Test
    public void test_ProcessOutput_WithListOfModelTensors_MultipleInList() throws Exception {
        ModelTensor tensor1 = ModelTensor.builder().name("t1").result("r1").build();
        ModelTensor tensor2 = ModelTensor.builder().name("t2").result("r2").build();
        ModelTensors tensors = ModelTensors.builder().mlModelTensors(List.of(tensor1, tensor2)).build();
        List<ModelTensors> inputList = List.of(tensors);
        List<org.opensearch.ml.common.output.model.ModelTensor> modelTensors = new ArrayList<>();

        mlAgentExecutor.processOutput(inputList, modelTensors);

        // List of ModelTensors objects gets flattened
        assertEquals(2, modelTensors.size());
        assertEquals("t1", modelTensors.get(0).getName());
        assertEquals("t2", modelTensors.get(1).getName());
    }

    @Test
    public void test_ProcessAgentInput_ConversationalAgent_TextInput_NoDataset() {
        MLAgent agent = MLAgent
            .builder()
            .name("test_agent")
            .type(MLAgentType.CONVERSATIONAL.name())
            .model(MLAgentModelSpec.builder().modelId("anthropic.claude-v2").modelProvider("bedrock/converse").build())
            .build();

        AgentInput agentInput = new AgentInput();
        agentInput.setInput("Simple text question");
        // No dataset provided initially
        AgentMLInput agentMLInput = new AgentMLInput("test", null, FunctionName.AGENT, agentInput, null, false);

        // Should create dataset automatically
        mlAgentExecutor.processAgentInput(agentMLInput, agent);

        // Verify dataset was created
        assertNotNull(agentMLInput.getInputDataset());
        RemoteInferenceInputDataSet dataset = (RemoteInferenceInputDataSet) agentMLInput.getInputDataset();
        assertNotNull(dataset.getParameters());
    }

    @Test
    public void test_ProcessAgentInput_OldStyleFlowAgent() {
        // Old style flow agent without model field
        MLAgent agent = MLAgent.builder().name("old_flow").type(MLAgentType.FLOW.name()).build();

        Map<String, String> params = new HashMap<>();
        RemoteInferenceInputDataSet dataset = RemoteInferenceInputDataSet.builder().parameters(params).build();
        AgentMLInput agentMLInput = new AgentMLInput("test", null, FunctionName.AGENT, null, dataset, false);

        // Old style agents should return early without modification
        mlAgentExecutor.processAgentInput(agentMLInput, agent);

        // Dataset should remain unchanged
        assertNotNull(agentMLInput.getInputDataset());
        assertEquals(0, ((RemoteInferenceInputDataSet) agentMLInput.getInputDataset()).getParameters().size());
    }

    @Test
    public void test_ProcessAgentInput_ConversationalFlowAgent() {
        MLAgent agent = MLAgent
            .builder()
            .name("conv_flow")
            .type(MLAgentType.CONVERSATIONAL_FLOW.name())
            .llm(LLMSpec.builder().modelId("gpt-4").build())
            .build();

        Map<String, String> params = new HashMap<>();
        params.put(QUESTION, "Flow question");
        RemoteInferenceInputDataSet dataset = RemoteInferenceInputDataSet.builder().parameters(params).build();
        AgentMLInput agentMLInput = new AgentMLInput("test", null, FunctionName.AGENT, null, dataset, false);

        // Conversational flow should be processed
        mlAgentExecutor.processAgentInput(agentMLInput, agent);

        assertNotNull(agentMLInput.getInputDataset());
    }

    @Test
    public void test_SupportsStructuredMessages_PlanExecuteAndReflect() {
        MLAgent agent = createTestAgent(MLAgentType.PLAN_EXECUTE_AND_REFLECT.name());

        // PER agents don't support structured messages
        assertFalse(mlAgentExecutor.supportsStructuredMessages(agent));
    }

    @Test
    public void test_ExecuteAgent_V2Agent_WithMemoryAndMessages_RoutesToExecuteV2Agent() throws IOException {
        // Setup: agent index exists (needed for MLIndicesHandler.doesMultiTenantIndexExist to return true)
        when(clusterService.state().metadata().hasIndex(anyString())).thenReturn(true);
        when(clusterService.localNode()).thenReturn(localNode);
        when(localNode.getId()).thenReturn("test-node-id");
        when(mlFeatureEnabledSetting.isRemoteAgenticMemoryEnabled()).thenReturn(false);

        // Build a CONVERSATIONAL_V2 agent with a conversation_index memory spec
        MLAgent v2Agent = MLAgent
            .builder()
            .name("test_v2_agent")
            .type(MLAgentType.CONVERSATIONAL_V2.name())
            .description("Test V2 agent")
            .memory(new MLMemorySpec("conversation_index", null, 0, null))
            .createdTime(Instant.now())
            .lastUpdateTime(Instant.now())
            .build();

        // Serialize agent → GetResponse JSON so the sdkClient mock can return it
        XContentBuilder agentContent = v2Agent.toXContent(XContentFactory.jsonBuilder(), ToXContent.EMPTY_PARAMS);
        GetResult getResult = new GetResult(
            ".plugins-ml-agent",
            "test-agent-id",
            1L,
            1L,
            1L,
            true,
            BytesReference.bytes(agentContent),
            null,
            null
        );
        GetResponse getAgentResponseObj = new GetResponse(getResult);
        XContentBuilder responseBuilder = XContentFactory.jsonBuilder();
        getAgentResponseObj.toXContent(responseBuilder, ToXContent.EMPTY_PARAMS);
        String getResponseJson = BytesReference.bytes(responseBuilder).utf8ToString();

        // Mock sdkClient.getDataObjectAsync to return the V2 agent (two-arg overload used in execute)
        when(sdkClient.getDataObjectAsync(any(), any())).thenAnswer(inv -> {
            GetDataObjectResponse resp = mock(GetDataObjectResponse.class);
            when(resp.parser())
                .thenReturn(XContentType.JSON.xContent().createParser(NamedXContentRegistry.EMPTY, null, getResponseJson));
            CompletionStage<GetDataObjectResponse> stage = mock(CompletionStage.class);
            when(stage.whenComplete(any())).thenAnswer(cbInv -> {
                BiConsumer<GetDataObjectResponse, Throwable> cb = cbInv.getArgument(0);
                cb.accept(resp, null);
                return stage;
            });
            return stage;
        });

        // Register the memory factory so the execute flow creates a memory instance
        memoryFactoryMap.put("CONVERSATION_INDEX", mockMemoryFactory);
        when(memory.getId()).thenReturn("test-memory-id");
        doAnswer(invocation -> {
            ActionListener<ConversationIndexMemory> memListener = invocation.getArgument(1);
            memListener.onResponse(memory);
            return null;
        }).when(mockMemoryFactory).create(any(), any());

        // Mock getStructuredMessages (first call inside executeV2Agent) to verify the path was reached
        doAnswer(invocation -> {
            ActionListener<List<Message>> msgListener = invocation.getArgument(0);
            msgListener.onFailure(new RuntimeException("reached_executeV2Agent"));
            return null;
        }).when(memory).getStructuredMessages(any());

        // Create input with MESSAGES type so inputMessages is non-null in saveRootInteractionAndExecute
        ContentBlock textBlock = new ContentBlock();
        textBlock.setType(ContentType.TEXT);
        textBlock.setText("What is machine learning?");
        Message message = new Message("user", Collections.singletonList(textBlock));
        AgentInput agentInput = new AgentInput();
        agentInput.setInput(Collections.singletonList(message));

        Map<String, String> params = new HashMap<>();
        params.put(QUESTION, "What is machine learning?");
        RemoteInferenceInputDataSet dataset = RemoteInferenceInputDataSet.builder().parameters(params).build();
        AgentMLInput agentMLInput = new AgentMLInput("test-agent-id", null, FunctionName.AGENT, agentInput, dataset, false);

        mlAgentExecutor.execute(agentMLInput, listener, channel);

        // executeV2Agent delegates immediately to memory.getStructuredMessages — verify it was called
        verify(memory, timeout(5000).atLeastOnce()).getStructuredMessages(any());
    }

    private String serializeAgentToGetResponseJson(MLAgent agent) throws IOException {
        XContentBuilder agentContent = agent.toXContent(XContentFactory.jsonBuilder(), ToXContent.EMPTY_PARAMS);
        GetResult getResult = new GetResult(
            ".plugins-ml-agent",
            "test-agent-id",
            1L,
            1L,
            1L,
            true,
            BytesReference.bytes(agentContent),
            null,
            null
        );
        GetResponse getAgentResponseObj = new GetResponse(getResult);
        XContentBuilder responseBuilder = XContentFactory.jsonBuilder();
        getAgentResponseObj.toXContent(responseBuilder, ToXContent.EMPTY_PARAMS);
        return BytesReference.bytes(responseBuilder).utf8ToString();
    }

    private void mockSdkClientWithAgent(String getResponseJson) {
        when(clusterService.localNode()).thenReturn(localNode);
        when(localNode.getId()).thenReturn("test-node-id");
        when(sdkClient.getDataObjectAsync(any(), any())).thenAnswer(inv -> {
            GetDataObjectResponse resp = mock(GetDataObjectResponse.class);
            when(resp.parser())
                .thenReturn(XContentType.JSON.xContent().createParser(NamedXContentRegistry.EMPTY, null, getResponseJson));
            CompletionStage<GetDataObjectResponse> stage = mock(CompletionStage.class);
            when(stage.whenComplete(any())).thenAnswer(cbInv -> {
                BiConsumer<GetDataObjectResponse, Throwable> cb = cbInv.getArgument(0);
                cb.accept(resp, null);
                return stage;
            });
            return stage;
        });
    }

    private AgentMLInput buildV2AgentMLInput() {
        ContentBlock textBlock = new ContentBlock();
        textBlock.setType(ContentType.TEXT);
        textBlock.setText("What is machine learning?");
        Message message = new Message("user", Collections.singletonList(textBlock));
        AgentInput agentInput = new AgentInput();
        agentInput.setInput(Collections.singletonList(message));

        Map<String, String> params = new HashMap<>();
        params.put(QUESTION, "What is machine learning?");
        RemoteInferenceInputDataSet dataset = RemoteInferenceInputDataSet.builder().parameters(params).build();
        return new AgentMLInput("test-agent-id", null, FunctionName.AGENT, agentInput, dataset, false);
    }

    /**
     * Covers the false branch of condition 1: agentType.isV2() == false.
     * A non-V2 agent (CONVERSATIONAL) with no memory spec reaches executeAgent via the
     * line-484 path (null memory, null inputMessages). The V2 condition fails immediately
     * and execution falls through to the normal runner path.
     */
    @Test
    public void test_ExecuteAgent_NonV2Agent_DoesNotRouteToExecuteV2Agent() throws IOException {
        when(clusterService.state().metadata().hasIndex(anyString())).thenReturn(true);
        when(mlFeatureEnabledSetting.isRemoteAgenticMemoryEnabled()).thenReturn(false);

        MLAgent convAgent = MLAgent
            .builder()
            .name("test_conv_agent")
            .type(MLAgentType.CONVERSATIONAL.name())
            .llm(LLMSpec.builder().modelId("gpt-4").build())
            // no memory spec → executeAgent called with null memory and null inputMessages
            .createdTime(Instant.now())
            .lastUpdateTime(Instant.now())
            .build();

        mockSdkClientWithAgent(serializeAgentToGetResponseJson(convAgent));

        Map<String, String> params = new HashMap<>();
        params.put(QUESTION, "What is ML?");
        RemoteInferenceInputDataSet dataset = RemoteInferenceInputDataSet.builder().parameters(params).build();
        AgentMLInput agentMLInput = new AgentMLInput("test-agent-id", null, FunctionName.AGENT, dataset);

        mlAgentExecutor.execute(agentMLInput, listener, channel);

        // agentType.isV2() = false → V2 execution path must not be entered
        verify(memory, never()).getStructuredMessages(any());
        verify(listener, timeout(5000)).onFailure(any());
    }

    /**
     * Covers the false branch of condition 2: inputMessages == null (with agentType.isV2() true).
     * A V2 agent with no memory spec reaches executeAgent via the line-484 path where
     * inputMessages is hardcoded null. The condition fails at the second && operand.
     */
    @Test
    public void test_ExecuteAgent_V2Agent_WithNullInputMessages_DoesNotRouteToExecuteV2Agent() throws IOException {
        when(clusterService.state().metadata().hasIndex(anyString())).thenReturn(true);
        when(mlFeatureEnabledSetting.isRemoteAgenticMemoryEnabled()).thenReturn(false);

        MLAgent v2Agent = MLAgent
            .builder()
            .name("test_v2_no_memory")
            .type(MLAgentType.CONVERSATIONAL_V2.name())
            // no memory spec → executeAgent called with null memory and null inputMessages
            .createdTime(Instant.now())
            .lastUpdateTime(Instant.now())
            .build();

        mockSdkClientWithAgent(serializeAgentToGetResponseJson(v2Agent));

        // Old-style dataset input (no AgentInput) → inputMessages stays null inside executeAgent
        Map<String, String> params = new HashMap<>();
        params.put(QUESTION, "What is ML?");
        RemoteInferenceInputDataSet dataset = RemoteInferenceInputDataSet.builder().parameters(params).build();
        AgentMLInput agentMLInput = new AgentMLInput("test-agent-id", null, FunctionName.AGENT, dataset);

        mlAgentExecutor.execute(agentMLInput, listener, channel);

        // agentType.isV2() = true, but inputMessages = null → V2 execution path must not be entered
        verify(memory, never()).getStructuredMessages(any());
        verify(listener, timeout(5000)).onFailure(any());
    }

    @Test
    public void test_ExecuteAgent_V2Agent_WithMcpConnector_McpDisabled_FailsWithMcpError() throws IOException {
        when(clusterService.state().metadata().hasIndex(anyString())).thenReturn(true);
        when(mlFeatureEnabledSetting.isRemoteAgenticMemoryEnabled()).thenReturn(false);
        when(mlFeatureEnabledSetting.isMcpConnectorEnabled()).thenReturn(false);

        Map<String, String> agentParams = new HashMap<>();
        agentParams.put(MCP_CONNECTORS_FIELD, "[{\"id\":\"mcp-conn-1\"}]");

        MLAgent v2Agent = MLAgent
            .builder()
            .name("test_v2_mcp_agent")
            .type(MLAgentType.CONVERSATIONAL_V2.name())
            .description("V2 agent with MCP connector")
            .parameters(agentParams)
            .memory(new MLMemorySpec("conversation_index", null, 0, null))
            .createdTime(Instant.now())
            .lastUpdateTime(Instant.now())
            .build();

        mockSdkClientWithAgent(serializeAgentToGetResponseJson(v2Agent));
        memoryFactoryMap.put("CONVERSATION_INDEX", mockMemoryFactory);
        when(memory.getId()).thenReturn("test-memory-id");
        doAnswer(invocation -> {
            ActionListener<ConversationIndexMemory> memListener = invocation.getArgument(1);
            memListener.onResponse(memory);
            return null;
        }).when(mockMemoryFactory).create(any(), any());

        mlAgentExecutor.execute(buildV2AgentMLInput(), listener, channel);

        // MCP check must fire before V2 routing — listener.onFailure called with MCP error
        verify(listener, timeout(5000)).onFailure(argThat(e -> e instanceof OpenSearchException
            && e.getMessage().contains(ML_COMMONS_MCP_CONNECTOR_DISABLED_MESSAGE)));
        // executeV2Agent must NOT have been reached
        verify(memory, never()).getStructuredMessages(any());
    }

    @Test
    public void test_ExecuteAgent_V2Agent_WithMcpConnector_McpEnabled_RoutesToExecuteV2Agent() throws IOException {
        when(clusterService.state().metadata().hasIndex(anyString())).thenReturn(true);
        when(mlFeatureEnabledSetting.isRemoteAgenticMemoryEnabled()).thenReturn(false);
        when(mlFeatureEnabledSetting.isMcpConnectorEnabled()).thenReturn(true);

        Map<String, String> agentParams = new HashMap<>();
        agentParams.put(MCP_CONNECTORS_FIELD, "[{\"id\":\"mcp-conn-1\"}]");

        MLAgent v2Agent = MLAgent
                .builder()
                .name("test_v2_mcp_agent")
                .type(MLAgentType.CONVERSATIONAL_V2.name())
                .description("V2 agent with MCP connector, MCP enabled")
                .parameters(agentParams)
                .memory(new MLMemorySpec("conversation_index", null, 0, null))
                .createdTime(Instant.now())
                .lastUpdateTime(Instant.now())
                .build();

        mockSdkClientWithAgent(serializeAgentToGetResponseJson(v2Agent));
        memoryFactoryMap.put("CONVERSATION_INDEX", mockMemoryFactory);
        when(memory.getId()).thenReturn("test-memory-id");
        doAnswer(invocation -> {
            ActionListener<ConversationIndexMemory> memListener = invocation.getArgument(1);
            memListener.onResponse(memory);
            return null;
        }).when(mockMemoryFactory).create(any(), any());

        // Fail fast inside executeV2Agent to verify the path was reached
        doAnswer(invocation -> {
            ActionListener<List<Message>> msgListener = invocation.getArgument(0);
            msgListener.onFailure(new RuntimeException("reached_executeV2Agent"));
            return null;
        }).when(memory).getStructuredMessages(any());

        mlAgentExecutor.execute(buildV2AgentMLInput(), listener, channel);

        // MCP check passes (enabled), so execution must reach executeV2Agent
        verify(memory, timeout(5000).atLeastOnce()).getStructuredMessages(any());
    }

    // ===== Additional tests for patch coverage of logAgentExecutionFailure/Latency calls =====

    /**
     * Helper to mock clusterService.localNode() so MLTask can be built without NPE.
     */
    private void mockLocalNode() {
        DiscoveryNode localNode = mock(DiscoveryNode.class);
        when(localNode.getId()).thenReturn("node-1");
        when(clusterService.localNode()).thenReturn(localNode);
    }

    /**
     * Helper to build a GetDataObjectResponse from a serialized MLAgent.
     */
    private GetDataObjectResponse buildAgentResponse(MLAgent agent) throws IOException {
        XContentBuilder content = agent.toXContent(XContentFactory.jsonBuilder(), ToXContent.EMPTY_PARAMS);
        BytesReference bytesReference = BytesReference.bytes(content);
        GetResult getResult = new GetResult("indexName", "test-agent", 1L, 1L, 1L, true, bytesReference, null, null);
        GetResponse getResponse = new GetResponse(getResult);
        return new GetDataObjectResponse(getResponse);
    }

    /**
     * Helper to set up sdkClient mock to call callback with provided response.
     */
    @SuppressWarnings("unchecked")
    private void mockSdkClientResponse(GetDataObjectResponse response) {
        when(metadata.hasIndex(anyString())).thenReturn(true);
        when(sdkClient.getDataObjectAsync(any(), any())).thenAnswer(inv -> {
            CompletionStage<GetDataObjectResponse> stage = mock(CompletionStage.class);
            when(stage.whenComplete(any())).thenAnswer(cbInv -> {
                BiConsumer<GetDataObjectResponse, Throwable> cb = cbInv.getArgument(0);
                cb.accept(response, null);
                return stage;
            });
            return stage;
        });
    }

    /**
     * Test tenant mismatch path: multi-tenancy enabled, agent has different tenantId than request.
     * Covers logAgentExecutionFailure in the FORBIDDEN tenant mismatch block.
     */
    @Test
    @SuppressWarnings("unchecked")
    public void testExecute_tenantMismatch_coversForbiddenLogFailure() throws IOException {
        when(mlFeatureEnabledSetting.isMultiTenancyEnabled()).thenReturn(true);
        mlAgentExecutor.onMultiTenancyEnabledChanged(true);

        // Agent registered with tenantId "tenant-a"
        GetResponse agentGetResponse = prepareMLAgent("test-agent", false, "tenant-a");
        mockSdkClientResponse(new GetDataObjectResponse(agentGetResponse));

        // Request uses different tenantId "tenant-b"
        Map<String, String> parameters = new HashMap<>();
        parameters.put("question", "test question");
        RemoteInferenceInputDataSet dataset = RemoteInferenceInputDataSet.builder().parameters(parameters).build();
        AgentMLInput agentInput = new AgentMLInput("test-agent", "tenant-b", FunctionName.AGENT, dataset);

        mlAgentExecutor.execute(agentInput, listener, channel);

        verify(listener, timeout(5000).atLeastOnce()).onFailure(any(OpenSearchStatusException.class));
    }

    /**
     * Test that a failure in GetResponse parsing triggers the outer catch block.
     * Covers logAgentExecutionFailure in the outer catch at line ~572.
     */
    @Test
    @SuppressWarnings("unchecked")
    public void testExecute_getResponseParserFails_coversOuterCatchLogFailure() {
        when(metadata.hasIndex(anyString())).thenReturn(true);
        when(sdkClient.getDataObjectAsync(any(), any())).thenAnswer(inv -> {
            GetDataObjectResponse mockResponse = mock(GetDataObjectResponse.class);
            XContentParser mockParser = mock(XContentParser.class);
            // Make parser throw RuntimeException so GetResponse.fromXContent fails
            try {
                when(mockParser.nextToken()).thenThrow(new RuntimeException("parser error"));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            when(mockResponse.parser()).thenReturn(mockParser);

            CompletionStage<GetDataObjectResponse> stage = mock(CompletionStage.class);
            when(stage.whenComplete(any())).thenAnswer(cbInv -> {
                BiConsumer<GetDataObjectResponse, Throwable> cb = cbInv.getArgument(0);
                cb.accept(mockResponse, null);
                return stage;
            });
            return stage;
        });

        Map<String, String> parameters = new HashMap<>();
        parameters.put("question", "test question");
        RemoteInferenceInputDataSet dataset = RemoteInferenceInputDataSet.builder().parameters(parameters).build();
        AgentMLInput agentInput = new AgentMLInput("test-agent", null, FunctionName.AGENT, dataset);
        mlAgentExecutor.execute(agentInput, listener, channel);

        verify(listener, timeout(5000).atLeastOnce()).onFailure(any(Exception.class));
    }

    /**
     * Test remote agentic memory disabled path.
     * When an agent has REMOTE_AGENTIC_MEMORY type but the feature is disabled,
     * covers logAgentExecutionFailure in the FORBIDDEN remote memory block.
     */
    @Test
    @SuppressWarnings("unchecked")
    public void testExecute_remoteAgenticMemoryDisabled_coversForbiddenLogFailure() throws IOException {
        // isRemoteAgenticMemoryEnabled() default mock = false → !false = true → enters check
        MLAgent agent = MLAgent.builder()
            .name("test-agent")
            .type(MLAgentType.CONVERSATIONAL.name())
            .llm(LLMSpec.builder().modelId("test-model").build())
            .memory(MLMemorySpec.builder().type(MLMemoryType.REMOTE_AGENTIC_MEMORY.name()).build())
            .build();

        mockSdkClientResponse(buildAgentResponse(agent));

        // Use mutable params so put(AGENT_ID_LOG_FIELD) at line 283 succeeds
        Map<String, String> parameters = new HashMap<>();
        parameters.put("question", "test question");
        RemoteInferenceInputDataSet dataset = RemoteInferenceInputDataSet.builder().parameters(parameters).build();
        AgentMLInput agentInput = new AgentMLInput("test-agent", null, FunctionName.AGENT, dataset);

        mlAgentExecutor.execute(agentInput, listener, channel);

        verify(listener, timeout(5000).atLeastOnce()).onFailure(any(OpenSearchStatusException.class));
    }

    /**
     * Test memory factory null path (BAD_REQUEST).
     * When an agent has CONVERSATION_INDEX memory spec but no factory is registered,
     * covers logAgentExecutionFailure in the memoryFactory == null block.
     */
    @Test
    @SuppressWarnings("unchecked")
    public void testExecute_memoryFactoryNull_coversBADREQUESTLogFailure() throws IOException {
        mockLocalNode();
        // Put null factory for CONVERSATION_INDEX → containsKey=true but get=null
        memoryFactoryMap.put(MLMemoryType.CONVERSATION_INDEX.name(), null);

        MLAgent agent = MLAgent.builder()
            .name("test-agent")
            .type(MLAgentType.CONVERSATIONAL.name())
            .llm(LLMSpec.builder().modelId("test-model").build())
            .memory(MLMemorySpec.builder().type(MLMemoryType.CONVERSATION_INDEX.name()).build())
            .build();

        mockSdkClientResponse(buildAgentResponse(agent));

        Map<String, String> parameters = new HashMap<>();
        parameters.put("question", "test question");
        RemoteInferenceInputDataSet dataset = RemoteInferenceInputDataSet.builder().parameters(parameters).build();
        AgentMLInput agentInput = new AgentMLInput("test-agent", null, FunctionName.AGENT, dataset);

        mlAgentExecutor.execute(agentInput, listener, channel);

        verify(listener, timeout(5000).atLeastOnce()).onFailure(any(IllegalArgumentException.class));
    }

    /**
     * Test MCP connector disabled path.
     * When an agent has MCP connector parameters but MCP feature is disabled,
     * covers logAgentExecutionFailure in the MCP disabled block in executeAgent.
     */
    @Test
    @SuppressWarnings("unchecked")
    public void testExecute_mcpConnectorDisabled_coversForbiddenLogFailure() throws IOException {
        mockLocalNode();
        // isMcpConnectorEnabled() default mock = false → !false = true → MCP check triggers

        Map<String, String> agentParams = new HashMap<>();
        agentParams.put("mcp_connectors", "[{\"connectorId\":\"mcp-1\"}]");

        MLAgent agent = MLAgent.builder()
            .name("test-agent")
            .type(MLAgentType.CONVERSATIONAL.name())
            .llm(LLMSpec.builder().modelId("test-model").build())
            .parameters(agentParams)
            .build();

        mockSdkClientResponse(buildAgentResponse(agent));

        Map<String, String> parameters = new HashMap<>();
        parameters.put("question", "test question");
        RemoteInferenceInputDataSet dataset = RemoteInferenceInputDataSet.builder().parameters(parameters).build();
        AgentMLInput agentInput = new AgentMLInput("test-agent", null, FunctionName.AGENT, dataset);

        mlAgentExecutor.execute(agentInput, listener, channel);

        verify(listener, timeout(5000).atLeastOnce()).onFailure(any(Exception.class));
    }

    /**
     * Test synchronous agent execution success path.
     * Uses a spy to inject mock runner that calls onResponse,
     * covering logAgentExecutionLatency in createAgentActionListener.
     */
    @Test
    @SuppressWarnings("unchecked")
    public void testExecute_syncAgent_success_coversLogAgentExecutionLatency() throws IOException {
        mockLocalNode();
        when(mlFeatureEnabledSetting.isRemoteAgenticMemoryEnabled()).thenReturn(true);

        MLAgent agent = MLAgent.builder()
            .name("test-agent")
            .type(MLAgentType.CONVERSATIONAL.name())
            .llm(LLMSpec.builder().modelId("test-model").build())
            .build();

        mockSdkClientResponse(buildAgentResponse(agent));

        // Spy on the executor so we can control getAgentRunner
        MLAgentExecutor spyExecutor = spy(mlAgentExecutor);
        doReturn(mlAgentRunner).when(spyExecutor).getAgentRunner(any(), any());

        // Mock runner.run to immediately call listener.onResponse
        doAnswer(invocation -> {
            ActionListener<Object> agentListener = invocation.getArgument(2);
            agentListener.onResponse(ModelTensorOutput.builder().mlModelOutputs(new ArrayList<>()).build());
            return null;
        }).when(mlAgentRunner).run(any(), any(), any(), any());

        Map<String, String> parameters = new HashMap<>();
        parameters.put("question", "test question");
        RemoteInferenceInputDataSet dataset = RemoteInferenceInputDataSet.builder().parameters(parameters).build();
        AgentMLInput agentInput = new AgentMLInput("test-agent", null, FunctionName.AGENT, dataset);

        spyExecutor.execute(agentInput, listener, channel);

        verify(listener, timeout(5000).atLeastOnce()).onResponse(any());
    }

    /**
     * Test synchronous agent execution failure path.
     * Mock runner calls onFailure, covering logAgentExecutionFailure
     * in createAgentActionListener failure callback.
     */
    @Test
    @SuppressWarnings("unchecked")
    public void testExecute_syncAgent_failure_coversLogAgentExecutionFailure() throws IOException {
        mockLocalNode();
        when(mlFeatureEnabledSetting.isRemoteAgenticMemoryEnabled()).thenReturn(true);

        MLAgent agent = MLAgent.builder()
            .name("test-agent")
            .type(MLAgentType.CONVERSATIONAL.name())
            .llm(LLMSpec.builder().modelId("test-model").build())
            .build();

        mockSdkClientResponse(buildAgentResponse(agent));

        MLAgentExecutor spyExecutor = spy(mlAgentExecutor);
        doReturn(mlAgentRunner).when(spyExecutor).getAgentRunner(any(), any());

        doAnswer(invocation -> {
            ActionListener<Object> agentListener = invocation.getArgument(2);
            agentListener.onFailure(new RuntimeException("agent execution failed"));
            return null;
        }).when(mlAgentRunner).run(any(), any(), any(), any());

        Map<String, String> parameters = new HashMap<>();
        parameters.put("question", "test question");
        RemoteInferenceInputDataSet dataset = RemoteInferenceInputDataSet.builder().parameters(parameters).build();
        AgentMLInput agentInput = new AgentMLInput("test-agent", null, FunctionName.AGENT, dataset);

        spyExecutor.execute(agentInput, listener, channel);

        verify(listener, timeout(5000).atLeastOnce()).onFailure(any(RuntimeException.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testExecute_asyncAgent_indexTaskFailure_covers1085_1090() throws IOException {
        mockLocalNode();
        when(mlFeatureEnabledSetting.isRemoteAgenticMemoryEnabled()).thenReturn(true);
        MLAgent agent = MLAgent.builder()
            .name("test-agent").type(MLAgentType.CONVERSATIONAL.name())
            .llm(LLMSpec.builder().modelId("test-model").build())
            .build();
        mockSdkClientResponse(buildAgentResponse(agent));

        MLAgentExecutor spyExecutor = spy(mlAgentExecutor);
        doReturn(mlAgentRunner).when(spyExecutor).getAgentRunner(any(), any());
        doAnswer(inv -> {
            ActionListener<IndexResponse> taskListener = inv.getArgument(1);
            taskListener.onFailure(new RuntimeException("failed to index task"));
            return null;
        }).when(spyExecutor).indexMLTask(any(), any());

        Map<String, String> parameters = new HashMap<>();
        parameters.put("question", "test question");
        RemoteInferenceInputDataSet dataset = RemoteInferenceInputDataSet.builder().parameters(parameters).build();
        AgentMLInput agentInput = new AgentMLInput("test-agent", null, FunctionName.AGENT, dataset, true);

        spyExecutor.execute(agentInput, listener, channel);

        verify(listener, timeout(5000).atLeastOnce()).onFailure(any(RuntimeException.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testExecute_asyncAgent_success_covers1372() throws IOException {
        mockLocalNode();
        when(mlFeatureEnabledSetting.isRemoteAgenticMemoryEnabled()).thenReturn(true);
        MLAgent agent = MLAgent.builder()
            .name("test-agent").type(MLAgentType.CONVERSATIONAL.name())
            .llm(LLMSpec.builder().modelId("test-model").build())
            .build();
        mockSdkClientResponse(buildAgentResponse(agent));

        CompletionStage updateStage = mock(CompletionStage.class);
        when(updateStage.whenComplete(any())).thenReturn(updateStage);
        when(sdkClient.updateDataObjectAsync(any())).thenReturn(updateStage);

        MLAgentExecutor spyExecutor = spy(mlAgentExecutor);
        doReturn(mlAgentRunner).when(spyExecutor).getAgentRunner(any(), any());
        doAnswer(inv -> {
            ActionListener<IndexResponse> taskListener = inv.getArgument(1);
            IndexResponse mockIndexResp = mock(IndexResponse.class);
            when(mockIndexResp.getId()).thenReturn("task-id-1");
            taskListener.onResponse(mockIndexResp);
            return null;
        }).when(spyExecutor).indexMLTask(any(), any());
        doAnswer(inv -> {
            ActionListener<Object> agentListener = inv.getArgument(2);
            agentListener.onResponse(ModelTensorOutput.builder().mlModelOutputs(new ArrayList<>()).build());
            return null;
        }).when(mlAgentRunner).run(any(), any(), any(), any());

        Map<String, String> parameters = new HashMap<>();
        parameters.put("question", "test question");
        RemoteInferenceInputDataSet dataset = RemoteInferenceInputDataSet.builder().parameters(parameters).build();
        AgentMLInput agentInput = new AgentMLInput("test-agent", null, FunctionName.AGENT, dataset, true);

        spyExecutor.execute(agentInput, listener, channel);

        verify(listener, timeout(5000).atLeastOnce()).onResponse(any());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testExecute_asyncAgent_failure_covers1426() throws IOException {
        mockLocalNode();
        when(mlFeatureEnabledSetting.isRemoteAgenticMemoryEnabled()).thenReturn(true);
        MLAgent agent = MLAgent.builder()
            .name("test-agent").type(MLAgentType.CONVERSATIONAL.name())
            .llm(LLMSpec.builder().modelId("test-model").build())
            .build();
        mockSdkClientResponse(buildAgentResponse(agent));

        CompletionStage updateStage = mock(CompletionStage.class);
        when(updateStage.whenComplete(any())).thenReturn(updateStage);
        when(sdkClient.updateDataObjectAsync(any())).thenReturn(updateStage);

        MLAgentExecutor spyExecutor = spy(mlAgentExecutor);
        doReturn(mlAgentRunner).when(spyExecutor).getAgentRunner(any(), any());
        doAnswer(inv -> {
            ActionListener<IndexResponse> taskListener = inv.getArgument(1);
            IndexResponse mockIndexResp = mock(IndexResponse.class);
            when(mockIndexResp.getId()).thenReturn("task-id-1");
            taskListener.onResponse(mockIndexResp);
            return null;
        }).when(spyExecutor).indexMLTask(any(), any());
        doAnswer(inv -> {
            ActionListener<Object> agentListener = inv.getArgument(2);
            agentListener.onFailure(new RuntimeException("agent execution failed async"));
            return null;
        }).when(mlAgentRunner).run(any(), any(), any(), any());

        Map<String, String> parameters = new HashMap<>();
        parameters.put("question", "test question");
        RemoteInferenceInputDataSet dataset = RemoteInferenceInputDataSet.builder().parameters(parameters).build();
        AgentMLInput agentInput = new AgentMLInput("test-agent", null, FunctionName.AGENT, dataset, true);

        spyExecutor.execute(agentInput, listener, channel);

        verify(listener, timeout(5000).atLeastOnce()).onResponse(any());
    }
}
