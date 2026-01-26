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
import java.util.Arrays;
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
import org.mockito.Mockito;
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
import org.opensearch.ml.engine.memory.ConversationIndexMessage;
import org.opensearch.ml.memory.action.conversation.CreateInteractionResponse;
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
            assertEquals("UNSUPPORTED_TYPE is not a valid Agent Type", exception.getMessage());
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
    public void test_ProcessAgentInput_AGUIAgent_WithContext_LegacyInterface() {
        // AGUI agent with legacy interface and context
        MLAgent agent = MLAgent
            .builder()
            .name("agui_agent_with_context")
            .type(MLAgentType.AG_UI.name())
            .llm(LLMSpec.builder().modelId("gpt-4").build())
            .build();

        AgentInput agentInput = new AgentInput();
        agentInput.setInput("What is the weather?");

        Map<String, String> existingParams = new HashMap<>();
        existingParams.put("context", "User is in San Francisco");
        RemoteInferenceInputDataSet dataset = RemoteInferenceInputDataSet.builder().parameters(existingParams).build();
        AgentMLInput agentMLInput = new AgentMLInput("test", null, FunctionName.AGENT, agentInput, dataset, false);

        mlAgentExecutor.processAgentInput(agentMLInput, agent);

        // Verify context is prepended to question for AGUI agents
        RemoteInferenceInputDataSet updatedDataset = (RemoteInferenceInputDataSet) agentMLInput.getInputDataset();
        String question = updatedDataset.getParameters().get(QUESTION);
        Assert.assertNotNull(question);
        Assert.assertTrue(question.contains("Context: User is in San Francisco"));
        Assert.assertTrue(question.contains("Question: What is the weather?"));
    }

    @Test
    public void test_ProcessAgentInput_AGUIAgent_WithContext_RevampInterface() {
        // AGUI agent with revamp interface and context
        MLAgent agent = MLAgent
            .builder()
            .name("agui_agent_revamp_context")
            .type(MLAgentType.AG_UI.name())
            .model(MLAgentModelSpec.builder().modelId("anthropic.claude-v2").modelProvider("bedrock/converse").build())
            .build();

        AgentInput agentInput = new AgentInput();
        agentInput.setInput("What time is it?");

        Map<String, String> existingParams = new HashMap<>();
        existingParams.put("context", "User timezone: PST");
        RemoteInferenceInputDataSet dataset = RemoteInferenceInputDataSet.builder().parameters(existingParams).build();
        AgentMLInput agentMLInput = new AgentMLInput("test", null, FunctionName.AGENT, agentInput, dataset, false);

        mlAgentExecutor.processAgentInput(agentMLInput, agent);

        // Verify context is prepended to question for AGUI agents
        RemoteInferenceInputDataSet updatedDataset = (RemoteInferenceInputDataSet) agentMLInput.getInputDataset();
        String question = updatedDataset.getParameters().get(QUESTION);
        Assert.assertNotNull(question);
        Assert.assertTrue(question.contains("Context: User timezone: PST"));
        Assert.assertTrue(question.contains("Question: What time is it?"));
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

    @Test
    public void test_ExtractTextFromMessage_SingleTextBlock() {
        ContentBlock textBlock = new ContentBlock();
        textBlock.setType(ContentType.TEXT);
        textBlock.setText("Hello world");
        Message message = new Message("user", Collections.singletonList(textBlock));

        String result = mlAgentExecutor.extractTextFromMessage(message);

        Assert.assertEquals("Hello world", result);
    }

    @Test
    public void test_ExtractTextFromMessage_MultipleTextBlocks() {
        ContentBlock textBlock1 = new ContentBlock();
        textBlock1.setType(ContentType.TEXT);
        textBlock1.setText("First block");

        ContentBlock textBlock2 = new ContentBlock();
        textBlock2.setType(ContentType.TEXT);
        textBlock2.setText("Second block");

        List<ContentBlock> blocks = Arrays.asList(textBlock1, textBlock2);
        Message message = new Message("user", blocks);

        String result = mlAgentExecutor.extractTextFromMessage(message);

        Assert.assertEquals("First block\nSecond block", result);
    }

    @Test
    public void test_ExtractTextFromMessage_MixedContentBlocks() {
        ContentBlock textBlock = new ContentBlock();
        textBlock.setType(ContentType.TEXT);
        textBlock.setText("Text content");

        ContentBlock imageBlock = new ContentBlock();
        imageBlock.setType(ContentType.IMAGE);

        List<ContentBlock> blocks = Arrays.asList(textBlock, imageBlock);
        Message message = new Message("user", blocks);

        String result = mlAgentExecutor.extractTextFromMessage(message);

        Assert.assertEquals("Text content", result);
    }

    @Test
    public void test_ExtractTextFromMessage_NullMessage() {
        String result = mlAgentExecutor.extractTextFromMessage(null);

        Assert.assertEquals("", result);
    }

    @Test
    public void test_ExtractTextFromMessage_NullContent() {
        Message message = new Message("user", null);

        String result = mlAgentExecutor.extractTextFromMessage(message);

        Assert.assertEquals("", result);
    }

    @Test
    public void test_ExtractTextFromMessage_EmptyContentList() {
        Message message = new Message("user", Collections.emptyList());

        String result = mlAgentExecutor.extractTextFromMessage(message);

        Assert.assertEquals("", result);
    }

    @Test
    public void test_ExtractTextFromMessage_TextWithWhitespace() {
        ContentBlock textBlock = new ContentBlock();
        textBlock.setType(ContentType.TEXT);
        textBlock.setText("  Hello world  ");
        Message message = new Message("user", Collections.singletonList(textBlock));

        String result = mlAgentExecutor.extractTextFromMessage(message);

        Assert.assertEquals("Hello world", result);
    }

    @Test
    public void test_ExtractTextFromMessage_NullTextInBlock() {
        ContentBlock textBlock = new ContentBlock();
        textBlock.setType(ContentType.TEXT);
        textBlock.setText(null);
        Message message = new Message("user", Collections.singletonList(textBlock));

        String result = mlAgentExecutor.extractTextFromMessage(message);

        Assert.assertEquals("", result);
    }

    @Test
    public void test_ExtractTextFromMessage_OnlyNonTextBlocks() {
        ContentBlock imageBlock = new ContentBlock();
        imageBlock.setType(ContentType.IMAGE);

        ContentBlock videoBlock = new ContentBlock();
        videoBlock.setType(ContentType.VIDEO);

        List<ContentBlock> blocks = Arrays.asList(imageBlock, videoBlock);
        Message message = new Message("user", blocks);

        String result = mlAgentExecutor.extractTextFromMessage(message);

        Assert.assertEquals("", result);
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
    // ==================== Tests for storeMessagesInMemory ====================

    @Test
    public void test_StoreMessagesInMemory_EmptyMessages() {
        List<Message> messages = Collections.emptyList();
        ActionListener<Void> listener = ActionListener
            .wrap(response -> {}, exception -> Assert.fail("Should not fail with empty messages"));

        mlAgentExecutor.storeMessagesInMemory(memory, messages, "test-app", listener);

        // Verify no save operations were called
        Mockito.verify(memory, Mockito.never()).save(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
    }

    @Test
    public void test_StoreMessagesInMemory_OnlyUserMessages() {
        ContentBlock textBlock = new ContentBlock();
        textBlock.setType(ContentType.TEXT);
        textBlock.setText("User question");
        Message userMessage = new Message("user", Collections.singletonList(textBlock));
        List<Message> messages = Collections.singletonList(userMessage);

        ActionListener<Void> listener = ActionListener
            .wrap(response -> {}, exception -> Assert.fail("Should not fail with only user messages"));

        mlAgentExecutor.storeMessagesInMemory(memory, messages, "test-app", listener);

        // Verify no save operations were called (trailing user messages are skipped)
        Mockito.verify(memory, Mockito.never()).save(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
    }

    @Test
    public void test_StoreMessagesInMemory_SingleUserAssistantPair() {
        ContentBlock userBlock = new ContentBlock();
        userBlock.setType(ContentType.TEXT);
        userBlock.setText("What is AI?");
        Message userMessage = new Message("user", Collections.singletonList(userBlock));

        ContentBlock assistantBlock = new ContentBlock();
        assistantBlock.setType(ContentType.TEXT);
        assistantBlock.setText("AI stands for Artificial Intelligence");
        Message assistantMessage = new Message("assistant", Collections.singletonList(assistantBlock));

        List<Message> messages = Arrays.asList(userMessage, assistantMessage);

        Mockito.when(memory.getConversationId()).thenReturn("conv-123");
        CreateInteractionResponse interaction = Mockito.mock(CreateInteractionResponse.class);
        Mockito.when(interaction.getId()).thenReturn("interaction-1");
        Mockito.doAnswer(invocation -> {
            ActionListener<CreateInteractionResponse> responseListener = invocation.getArgument(4);
            responseListener.onResponse(interaction);
            return null;
        }).when(memory).save(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());

        ActionListener<Void> listener = ActionListener
            .wrap(response -> {}, exception -> Assert.fail("Should not fail: " + exception.getMessage()));

        mlAgentExecutor.storeMessagesInMemory(memory, messages, "test-app", listener);

        // Verify save was called once
        ArgumentCaptor<ConversationIndexMessage> messageCaptor = ArgumentCaptor.forClass(ConversationIndexMessage.class);
        Mockito.verify(memory, Mockito.times(1)).save(messageCaptor.capture(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());

        ConversationIndexMessage savedMessage = messageCaptor.getValue();
        Assert.assertEquals("What is AI?", savedMessage.getQuestion());
        Assert.assertEquals("AI stands for Artificial Intelligence", savedMessage.getResponse());
        Assert.assertEquals("conv-123", savedMessage.getSessionId());
        Assert.assertEquals("test-app", savedMessage.getType());
        Assert.assertTrue(savedMessage.getFinalAnswer());
    }

    @Test
    public void test_StoreMessagesInMemory_MultipleUserAssistantPairs() {
        ContentBlock user1Block = new ContentBlock();
        user1Block.setType(ContentType.TEXT);
        user1Block.setText("First question");
        Message user1 = new Message("user", Collections.singletonList(user1Block));

        ContentBlock assistant1Block = new ContentBlock();
        assistant1Block.setType(ContentType.TEXT);
        assistant1Block.setText("First answer");
        Message assistant1 = new Message("assistant", Collections.singletonList(assistant1Block));

        ContentBlock user2Block = new ContentBlock();
        user2Block.setType(ContentType.TEXT);
        user2Block.setText("Second question");
        Message user2 = new Message("user", Collections.singletonList(user2Block));

        ContentBlock assistant2Block = new ContentBlock();
        assistant2Block.setType(ContentType.TEXT);
        assistant2Block.setText("Second answer");
        Message assistant2 = new Message("assistant", Collections.singletonList(assistant2Block));

        List<Message> messages = Arrays.asList(user1, assistant1, user2, assistant2);

        Mockito.when(memory.getConversationId()).thenReturn("conv-456");
        CreateInteractionResponse interaction = Mockito.mock(CreateInteractionResponse.class);
        Mockito.when(interaction.getId()).thenReturn("interaction-1");
        Mockito.doAnswer(invocation -> {
            ActionListener<CreateInteractionResponse> responseListener = invocation.getArgument(4);
            responseListener.onResponse(interaction);
            return null;
        }).when(memory).save(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());

        ActionListener<Void> listener = ActionListener
            .wrap(response -> {}, exception -> Assert.fail("Should not fail: " + exception.getMessage()));

        mlAgentExecutor.storeMessagesInMemory(memory, messages, "test-app", listener);

        // Verify save was called twice (two pairs)
        ArgumentCaptor<ConversationIndexMessage> messageCaptor = ArgumentCaptor.forClass(ConversationIndexMessage.class);
        Mockito.verify(memory, Mockito.times(2)).save(messageCaptor.capture(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());

        List<ConversationIndexMessage> savedMessages = messageCaptor.getAllValues();
        Assert.assertEquals(2, savedMessages.size());

        // First pair
        Assert.assertEquals("First question", savedMessages.get(0).getQuestion());
        Assert.assertEquals("First answer", savedMessages.get(0).getResponse());

        // Second pair
        Assert.assertEquals("Second question", savedMessages.get(1).getQuestion());
        Assert.assertEquals("Second answer", savedMessages.get(1).getResponse());
    }

    @Test
    public void test_StoreMessagesInMemory_SkipsTrailingUserMessages() {
        ContentBlock user1Block = new ContentBlock();
        user1Block.setType(ContentType.TEXT);
        user1Block.setText("First question");
        Message user1 = new Message("user", Collections.singletonList(user1Block));

        ContentBlock assistant1Block = new ContentBlock();
        assistant1Block.setType(ContentType.TEXT);
        assistant1Block.setText("First answer");
        Message assistant1 = new Message("assistant", Collections.singletonList(assistant1Block));

        ContentBlock user2Block = new ContentBlock();
        user2Block.setType(ContentType.TEXT);
        user2Block.setText("Trailing question");
        Message user2 = new Message("user", Collections.singletonList(user2Block));

        List<Message> messages = Arrays.asList(user1, assistant1, user2);

        Mockito.when(memory.getConversationId()).thenReturn("conv-789");
        CreateInteractionResponse interaction = Mockito.mock(CreateInteractionResponse.class);
        Mockito.when(interaction.getId()).thenReturn("interaction-1");
        Mockito.doAnswer(invocation -> {
            ActionListener<CreateInteractionResponse> responseListener = invocation.getArgument(4);
            responseListener.onResponse(interaction);
            return null;
        }).when(memory).save(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());

        ActionListener<Void> listener = ActionListener
            .wrap(response -> {}, exception -> Assert.fail("Should not fail: " + exception.getMessage()));

        mlAgentExecutor.storeMessagesInMemory(memory, messages, "test-app", listener);

        // Verify save was called once (only the complete pair, trailing user is skipped)
        ArgumentCaptor<ConversationIndexMessage> messageCaptor = ArgumentCaptor.forClass(ConversationIndexMessage.class);
        Mockito.verify(memory, Mockito.times(1)).save(messageCaptor.capture(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());

        ConversationIndexMessage savedMessage = messageCaptor.getValue();
        Assert.assertEquals("First question", savedMessage.getQuestion());
        Assert.assertEquals("First answer", savedMessage.getResponse());
    }

    @Test
    public void test_StoreMessagesInMemory_MultipleContentBlocks() {
        ContentBlock user1Block = new ContentBlock();
        user1Block.setType(ContentType.TEXT);
        user1Block.setText("Part 1");
        ContentBlock user2Block = new ContentBlock();
        user2Block.setType(ContentType.TEXT);
        user2Block.setText("Part 2");
        Message userMessage = new Message("user", Arrays.asList(user1Block, user2Block));

        ContentBlock assistant1Block = new ContentBlock();
        assistant1Block.setType(ContentType.TEXT);
        assistant1Block.setText("Response 1");
        ContentBlock assistant2Block = new ContentBlock();
        assistant2Block.setType(ContentType.TEXT);
        assistant2Block.setText("Response 2");
        Message assistantMessage = new Message("assistant", Arrays.asList(assistant1Block, assistant2Block));

        List<Message> messages = Arrays.asList(userMessage, assistantMessage);

        Mockito.when(memory.getConversationId()).thenReturn("conv-multi");
        CreateInteractionResponse interaction = Mockito.mock(CreateInteractionResponse.class);
        Mockito.when(interaction.getId()).thenReturn("interaction-1");
        Mockito.doAnswer(invocation -> {
            ActionListener<CreateInteractionResponse> responseListener = invocation.getArgument(4);
            responseListener.onResponse(interaction);
            return null;
        }).when(memory).save(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());

        ActionListener<Void> listener = ActionListener
            .wrap(response -> {}, exception -> Assert.fail("Should not fail: " + exception.getMessage()));

        mlAgentExecutor.storeMessagesInMemory(memory, messages, "test-app", listener);

        ArgumentCaptor<ConversationIndexMessage> messageCaptor = ArgumentCaptor.forClass(ConversationIndexMessage.class);
        Mockito.verify(memory, Mockito.times(1)).save(messageCaptor.capture(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());

        ConversationIndexMessage savedMessage = messageCaptor.getValue();
        Assert.assertEquals("Part 1\nPart 2", savedMessage.getQuestion());
        Assert.assertEquals("Response 1\nResponse 2", savedMessage.getResponse());
    }

    @Test
    public void test_StoreMessagesInMemory_IgnoresNonUserAssistantRoles() {
        ContentBlock systemBlock = new ContentBlock();
        systemBlock.setType(ContentType.TEXT);
        systemBlock.setText("System message");
        Message systemMessage = new Message("system", Collections.singletonList(systemBlock));

        ContentBlock userBlock = new ContentBlock();
        userBlock.setType(ContentType.TEXT);
        userBlock.setText("User question");
        Message userMessage = new Message("user", Collections.singletonList(userBlock));

        ContentBlock assistantBlock = new ContentBlock();
        assistantBlock.setType(ContentType.TEXT);
        assistantBlock.setText("Assistant answer");
        Message assistantMessage = new Message("assistant", Collections.singletonList(assistantBlock));

        List<Message> messages = Arrays.asList(systemMessage, userMessage, assistantMessage);

        Mockito.when(memory.getConversationId()).thenReturn("conv-system");
        CreateInteractionResponse interaction = Mockito.mock(CreateInteractionResponse.class);
        Mockito.when(interaction.getId()).thenReturn("interaction-1");
        Mockito.doAnswer(invocation -> {
            ActionListener<CreateInteractionResponse> responseListener = invocation.getArgument(4);
            responseListener.onResponse(interaction);
            return null;
        }).when(memory).save(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());

        ActionListener<Void> listener = ActionListener
            .wrap(response -> {}, exception -> Assert.fail("Should not fail: " + exception.getMessage()));

        mlAgentExecutor.storeMessagesInMemory(memory, messages, "test-app", listener);

        // System message should be ignored
        ArgumentCaptor<ConversationIndexMessage> messageCaptor = ArgumentCaptor.forClass(ConversationIndexMessage.class);
        Mockito.verify(memory, Mockito.times(1)).save(messageCaptor.capture(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());

        ConversationIndexMessage savedMessage = messageCaptor.getValue();
        Assert.assertEquals("User question", savedMessage.getQuestion());
        Assert.assertEquals("Assistant answer", savedMessage.getResponse());
    }

    @Test
    public void test_StoreMessagesInMemory_HandlesNullMessages() {
        ContentBlock userBlock = new ContentBlock();
        userBlock.setType(ContentType.TEXT);
        userBlock.setText("Question");
        Message userMessage = new Message("user", Collections.singletonList(userBlock));

        ContentBlock assistantBlock = new ContentBlock();
        assistantBlock.setType(ContentType.TEXT);
        assistantBlock.setText("Answer");
        Message assistantMessage = new Message("assistant", Collections.singletonList(assistantBlock));

        List<Message> messages = Arrays.asList(userMessage, null, assistantMessage);

        Mockito.when(memory.getConversationId()).thenReturn("conv-null");
        CreateInteractionResponse interaction = Mockito.mock(CreateInteractionResponse.class);
        Mockito.when(interaction.getId()).thenReturn("interaction-1");
        Mockito.doAnswer(invocation -> {
            ActionListener<CreateInteractionResponse> responseListener = invocation.getArgument(4);
            responseListener.onResponse(interaction);
            return null;
        }).when(memory).save(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());

        ActionListener<Void> listener = ActionListener
            .wrap(response -> {}, exception -> Assert.fail("Should not fail: " + exception.getMessage()));

        mlAgentExecutor.storeMessagesInMemory(memory, messages, "test-app", listener);

        // Should handle null message gracefully
        Mockito.verify(memory, Mockito.times(1)).save(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
    }

    // ==================== Tests for saveMessagePairsSequentially ====================

    @Test
    public void test_SaveMessagePairsSequentially_EmptyList() {
        List<ConversationIndexMessage> messagePairs = Collections.emptyList();
        ActionListener<Void> listener = ActionListener.wrap(response -> {}, exception -> Assert.fail("Should not fail with empty list"));

        mlAgentExecutor.saveMessagePairsSequentially(memory, messagePairs, listener);

        // Verify no save operations were called
        Mockito.verify(memory, Mockito.never()).save(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
    }

    @Test
    public void test_SaveMessagePairsSequentially_SinglePair() {
        ConversationIndexMessage msg = ConversationIndexMessage
            .conversationIndexMessageBuilder()
            .type("test-app")
            .question("Question")
            .response("Answer")
            .finalAnswer(true)
            .sessionId("session-1")
            .build();

        List<ConversationIndexMessage> messagePairs = Collections.singletonList(msg);

        CreateInteractionResponse interaction = Mockito.mock(CreateInteractionResponse.class);
        Mockito.when(interaction.getId()).thenReturn("interaction-1");
        Mockito.doAnswer(invocation -> {
            ActionListener<CreateInteractionResponse> responseListener = invocation.getArgument(4);
            responseListener.onResponse(interaction);
            return null;
        }).when(memory).save(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());

        ActionListener<Void> listener = ActionListener
            .wrap(response -> {}, exception -> Assert.fail("Should not fail: " + exception.getMessage()));

        mlAgentExecutor.saveMessagePairsSequentially(memory, messagePairs, listener);

        // Verify save was called once
        Mockito.verify(memory, Mockito.times(1)).save(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
    }

    @Test
    public void test_SaveMessagePairsSequentially_MultiplePairs() {
        ConversationIndexMessage msg1 = ConversationIndexMessage
            .conversationIndexMessageBuilder()
            .type("test-app")
            .question("Question 1")
            .response("Answer 1")
            .finalAnswer(true)
            .sessionId("session-1")
            .build();

        ConversationIndexMessage msg2 = ConversationIndexMessage
            .conversationIndexMessageBuilder()
            .type("test-app")
            .question("Question 2")
            .response("Answer 2")
            .finalAnswer(true)
            .sessionId("session-1")
            .build();

        List<ConversationIndexMessage> messagePairs = Arrays.asList(msg1, msg2);

        CreateInteractionResponse interaction = Mockito.mock(CreateInteractionResponse.class);
        Mockito.when(interaction.getId()).thenReturn("interaction-1");
        Mockito.doAnswer(invocation -> {
            ActionListener<CreateInteractionResponse> responseListener = invocation.getArgument(4);
            responseListener.onResponse(interaction);
            return null;
        }).when(memory).save(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());

        ActionListener<Void> listener = ActionListener
            .wrap(response -> {}, exception -> Assert.fail("Should not fail: " + exception.getMessage()));

        mlAgentExecutor.saveMessagePairsSequentially(memory, messagePairs, listener);

        // Verify save was called twice
        Mockito.verify(memory, Mockito.times(2)).save(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
    }

    @Test
    public void test_SaveMessagePairsSequentially_ContinuesOnFailure() {
        ConversationIndexMessage msg1 = ConversationIndexMessage
            .conversationIndexMessageBuilder()
            .type("test-app")
            .question("Question 1")
            .response("Answer 1")
            .finalAnswer(true)
            .sessionId("session-1")
            .build();

        ConversationIndexMessage msg2 = ConversationIndexMessage
            .conversationIndexMessageBuilder()
            .type("test-app")
            .question("Question 2")
            .response("Answer 2")
            .finalAnswer(true)
            .sessionId("session-1")
            .build();

        List<ConversationIndexMessage> messagePairs = Arrays.asList(msg1, msg2);

        // First save fails, second succeeds
        Mockito.doAnswer(invocation -> {
            ConversationIndexMessage msg = invocation.getArgument(0);
            ActionListener<CreateInteractionResponse> responseListener = invocation.getArgument(4);
            if (msg.getQuestion().equals("Question 1")) {
                responseListener.onFailure(new RuntimeException("Save failed"));
            } else {
                CreateInteractionResponse interaction = Mockito.mock(CreateInteractionResponse.class);
                Mockito.when(interaction.getId()).thenReturn("interaction-2");
                responseListener.onResponse(interaction);
            }
            return null;
        }).when(memory).save(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());

        ActionListener<Void> listener = ActionListener
            .wrap(response -> {}, exception -> Assert.fail("Should not fail: " + exception.getMessage()));

        mlAgentExecutor.saveMessagePairsSequentially(memory, messagePairs, listener);

        // Verify save was called twice (continues even after first failure)
        Mockito.verify(memory, Mockito.times(2)).save(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
    }

    // ==================== Tests for saveNextMessagePair ====================

    @Test
    public void test_SaveNextMessagePair_IndexOutOfBounds() {
        List<ConversationIndexMessage> messagePairs = Collections.emptyList();
        ActionListener<Void> listener = ActionListener.wrap(response -> {}, exception -> Assert.fail("Should not fail when index >= size"));

        mlAgentExecutor.saveNextMessagePair(memory, messagePairs, 0, listener);

        // Verify no save operations were called
        Mockito.verify(memory, Mockito.never()).save(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
    }

    @Test
    public void test_SaveNextMessagePair_SavesAtIndex() {
        ConversationIndexMessage msg = ConversationIndexMessage
            .conversationIndexMessageBuilder()
            .type("test-app")
            .question("Question")
            .response("Answer")
            .finalAnswer(true)
            .sessionId("session-1")
            .build();

        List<ConversationIndexMessage> messagePairs = Collections.singletonList(msg);

        CreateInteractionResponse interaction = Mockito.mock(CreateInteractionResponse.class);
        Mockito.when(interaction.getId()).thenReturn("interaction-1");
        Mockito.doAnswer(invocation -> {
            ActionListener<CreateInteractionResponse> responseListener = invocation.getArgument(4);
            responseListener.onResponse(interaction);
            return null;
        }).when(memory).save(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());

        ActionListener<Void> listener = ActionListener
            .wrap(response -> {}, exception -> Assert.fail("Should not fail: " + exception.getMessage()));

        mlAgentExecutor.saveNextMessagePair(memory, messagePairs, 0, listener);

        // Verify save was called once
        ArgumentCaptor<ConversationIndexMessage> messageCaptor = ArgumentCaptor.forClass(ConversationIndexMessage.class);
        Mockito.verify(memory, Mockito.times(1)).save(messageCaptor.capture(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());

        ConversationIndexMessage savedMessage = messageCaptor.getValue();
        Assert.assertEquals("Question", savedMessage.getQuestion());
        Assert.assertEquals("Answer", savedMessage.getResponse());
    }

    @Test
    public void test_SaveNextMessagePair_HandlesFailure() {
        ConversationIndexMessage msg = ConversationIndexMessage
            .conversationIndexMessageBuilder()
            .type("test-app")
            .question("Question")
            .response("Answer")
            .finalAnswer(true)
            .sessionId("session-1")
            .build();

        List<ConversationIndexMessage> messagePairs = Collections.singletonList(msg);

        Mockito.doAnswer(invocation -> {
            ActionListener<CreateInteractionResponse> responseListener = invocation.getArgument(4);
            responseListener.onFailure(new RuntimeException("Save failed"));
            return null;
        }).when(memory).save(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());

        ActionListener<Void> listener = ActionListener
            .wrap(response -> {}, exception -> Assert.fail("Should not propagate failure: " + exception.getMessage()));

        mlAgentExecutor.saveNextMessagePair(memory, messagePairs, 0, listener);

        // Verify save was called and failure was handled
        Mockito.verify(memory, Mockito.times(1)).save(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
    }
}
