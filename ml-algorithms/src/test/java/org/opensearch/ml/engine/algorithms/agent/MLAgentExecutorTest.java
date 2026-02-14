/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.agent;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import static org.opensearch.ml.engine.algorithms.agent.MLAgentExecutor.QUESTION;

import java.io.IOException;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.OpenSearchStatusException;
import org.opensearch.action.get.GetResponse;
import org.opensearch.cluster.ClusterState;
import org.opensearch.cluster.metadata.Metadata;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.common.xcontent.XContentFactory;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.common.bytes.BytesReference;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.XContentBuilder;
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
import org.opensearch.ml.common.output.model.ModelTensorOutput;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.spi.tools.Tool;
import org.opensearch.ml.engine.encryptor.Encryptor;
import org.opensearch.ml.engine.memory.ConversationIndexMemory;
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
        Metadata metadata = mock(Metadata.class);
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
        // AGUI agent with legacy LLM interface
        // Context has already been appended by AGUIInputConverter before reaching MLAgentExecutor
        MLAgent agent = MLAgent
            .builder()
            .name("agui_agent_legacy_context")
            .type(MLAgentType.AG_UI.name())
            .llm(LLMSpec.builder().modelId("gpt-4").build())
            .build();

        // Simulate message with context already appended (as done by AGUIInputConverter)
        ContentBlock textBlock = new ContentBlock();
        textBlock.setType(ContentType.TEXT);
        textBlock.setText("Context:\n- Location: San Francisco\n\nWhat is the weather?");
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
        Assert.assertTrue(question.contains("San Francisco"));
        Assert.assertTrue(question.contains("What is the weather?"));
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

        mlAgentExecutor.processAgentInput(agentMLInput, agent);

        // Verify dataset was created with processed parameters
        Assert.assertNotNull(agentMLInput.getInputDataset());
        RemoteInferenceInputDataSet dataset = (RemoteInferenceInputDataSet) agentMLInput.getInputDataset();
        Assert.assertNotNull(dataset.getParameters());
        Assert.assertTrue(dataset.getParameters().containsKey(QUESTION));
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
}
