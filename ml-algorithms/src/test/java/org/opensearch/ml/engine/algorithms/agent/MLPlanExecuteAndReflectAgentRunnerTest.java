/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.agent;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.opensearch.ml.engine.algorithms.agent.AgentUtils.DEFAULT_DATETIME_PREFIX;
import static org.opensearch.ml.engine.algorithms.agent.PromptTemplate.PLAN_EXECUTE_REFLECT_RESPONSE_FORMAT;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import org.opensearch.action.update.UpdateResponse;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.Settings;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.ml.common.MLAgentType;
import org.opensearch.ml.common.MLTaskState;
import org.opensearch.ml.common.agent.LLMSpec;
import org.opensearch.ml.common.agent.MLAgent;
import org.opensearch.ml.common.agent.MLMemorySpec;
import org.opensearch.ml.common.agent.MLToolSpec;
import org.opensearch.ml.common.conversation.Interaction;
import org.opensearch.ml.common.dataset.remote.RemoteInferenceInputDataSet;
import org.opensearch.ml.common.input.execute.agent.AgentMLInput;
import org.opensearch.ml.common.output.model.ModelTensor;
import org.opensearch.ml.common.output.model.ModelTensorOutput;
import org.opensearch.ml.common.output.model.ModelTensors;
import org.opensearch.ml.common.spi.memory.Memory;
import org.opensearch.ml.common.spi.tools.Tool;
import org.opensearch.ml.common.transport.MLTaskResponse;
import org.opensearch.ml.common.transport.execute.MLExecuteTaskAction;
import org.opensearch.ml.common.transport.execute.MLExecuteTaskRequest;
import org.opensearch.ml.common.transport.execute.MLExecuteTaskResponse;
import org.opensearch.ml.common.transport.prediction.MLPredictionTaskAction;
import org.opensearch.ml.common.transport.prediction.MLPredictionTaskRequest;
import org.opensearch.ml.common.utils.MLTaskUtils;
import org.opensearch.ml.engine.MLStaticMockBase;
import org.opensearch.ml.engine.encryptor.Encryptor;
import org.opensearch.ml.engine.memory.ConversationIndexMemory;
import org.opensearch.ml.engine.memory.MLMemoryManager;
import org.opensearch.ml.memory.action.conversation.CreateInteractionResponse;
import org.opensearch.remote.metadata.client.SdkClient;
import org.opensearch.transport.client.Client;

import com.google.common.collect.ImmutableMap;

public class MLPlanExecuteAndReflectAgentRunnerTest extends MLStaticMockBase {
    public static final String FIRST_TOOL = "firstTool";
    public static final String SECOND_TOOL = "secondTool";

    @Mock
    private Client client;
    private Settings settings;
    @Mock
    private ClusterService clusterService;
    @Mock
    private NamedXContentRegistry xContentRegistry;
    private Map<String, Tool.Factory> toolFactories;
    @Mock
    private Map<String, Memory.Factory> memoryMap;
    private MLPlanExecuteAndReflectAgentRunner mlPlanExecuteAndReflectAgentRunner;
    @Mock
    private Tool.Factory firstToolFactory;
    @Mock
    private Tool.Factory secondToolFactory;
    @Mock
    private Tool firstTool;
    @Mock
    private Tool secondTool;
    @Mock
    private ActionListener<Object> agentActionListener;
    @Mock
    private ConversationIndexMemory conversationIndexMemory;
    @Mock
    private MLMemoryManager mlMemoryManager;
    @Mock
    private CreateInteractionResponse createInteractionResponse;
    @Mock
    private ConversationIndexMemory.Factory memoryFactory;
    @Mock
    private SdkClient sdkClient;
    @Mock
    private Encryptor encryptor;
    @Mock
    private UpdateResponse updateResponse;
    @Mock
    private MLTaskResponse mlTaskResponse;
    @Mock
    private MLExecuteTaskResponse mlExecuteTaskResponse;

    @Captor
    private ArgumentCaptor<Object> objectCaptor;

    @Captor
    private ArgumentCaptor<ActionListener<ConversationIndexMemory>> memoryFactoryCapture;

    @Captor
    private ArgumentCaptor<ActionListener<List<Interaction>>> memoryInteractionCapture;

    private MLMemorySpec mlMemorySpec;

    @Before
    @SuppressWarnings("unchecked")
    public void setup() {
        MockitoAnnotations.openMocks(this);
        settings = Settings.builder().build();
        toolFactories = ImmutableMap.of(FIRST_TOOL, firstToolFactory, SECOND_TOOL, secondToolFactory);

        // memory
        mlMemorySpec = new MLMemorySpec(ConversationIndexMemory.TYPE, "uuid", 10);
        when(memoryMap.get(anyString())).thenReturn(memoryFactory);
        when(conversationIndexMemory.getConversationId()).thenReturn("test_memory_id");
        when(conversationIndexMemory.getMemoryManager()).thenReturn(mlMemoryManager);
        when(createInteractionResponse.getId()).thenReturn("create_interaction_id");
        when(updateResponse.getId()).thenReturn("update_interaction_id");

        // memory factory
        doAnswer(invocation -> {
            ActionListener<ConversationIndexMemory> listener = invocation.getArgument(3);
            listener.onResponse(conversationIndexMemory);
            return null;
        }).when(memoryFactory).create(any(), any(), any(), memoryFactoryCapture.capture());

        // Setup conversation index memory
        doAnswer(invocation -> {
            ActionListener<List<Interaction>> listener = invocation.getArgument(0);
            listener.onResponse(generateInteractions());
            return null;
        }).when(conversationIndexMemory).getMessages(memoryInteractionCapture.capture(), anyInt());

        // Setup memory manager
        doAnswer(invocation -> {
            ActionListener<CreateInteractionResponse> listener = invocation.getArgument(4);
            listener.onResponse(createInteractionResponse);
            return null;
        }).when(conversationIndexMemory).save(any(), any(), any(), any(), any());

        mlPlanExecuteAndReflectAgentRunner = new MLPlanExecuteAndReflectAgentRunner(
            client,
            settings,
            clusterService,
            xContentRegistry,
            toolFactories,
            memoryMap,
            sdkClient,
            encryptor
        );

        // Setup tools
        when(firstToolFactory.create(any())).thenReturn(firstTool);
        when(secondToolFactory.create(any())).thenReturn(secondTool);
        when(firstTool.getName()).thenReturn(FIRST_TOOL);
        when(firstTool.getDescription()).thenReturn("First tool description");
        when(secondTool.getName()).thenReturn(SECOND_TOOL);
        when(secondTool.getDescription()).thenReturn("Second tool description");
        when(firstTool.validate(any())).thenReturn(true);
        when(secondTool.validate(any())).thenReturn(true);
    }

    @Test
    public void testBasicExecution() {
        MLAgent mlAgent = createMLAgentWithTools();

        // Setup LLM response for planning phase
        doAnswer(invocation -> {
            ActionListener<Object> listener = invocation.getArgument(2);
            ModelTensor modelTensor = ModelTensor
                .builder()
                .dataAsMap(ImmutableMap.of("response", "{\"steps\":[\"step1\"], \"result\":\"final result\"}"))
                .build();
            ModelTensors modelTensors = ModelTensors.builder().mlModelTensors(Arrays.asList(modelTensor)).build();
            ModelTensorOutput mlModelTensorOutput = ModelTensorOutput.builder().mlModelOutputs(Arrays.asList(modelTensors)).build();
            when(mlTaskResponse.getOutput()).thenReturn(mlModelTensorOutput);
            listener.onResponse(mlTaskResponse);
            return null;
        }).when(client).execute(eq(MLPredictionTaskAction.INSTANCE), any(MLPredictionTaskRequest.class), any());

        // Setup tool execution response
        doAnswer(invocation -> {
            ActionListener<Object> listener = invocation.getArgument(1);
            ModelTensor modelTensor = ModelTensor.builder().dataAsMap(ImmutableMap.of("response", "tool execution result")).build();
            ModelTensors modelTensors = ModelTensors.builder().mlModelTensors(Arrays.asList(modelTensor)).build();
            ModelTensorOutput mlModelTensorOutput = ModelTensorOutput.builder().mlModelOutputs(Arrays.asList(modelTensors)).build();
            when(mlExecuteTaskResponse.getOutput()).thenReturn(mlModelTensorOutput);
            listener.onResponse(mlExecuteTaskResponse);
            return null;
        }).when(client).execute(eq(MLExecuteTaskAction.INSTANCE), any(MLExecuteTaskRequest.class), any());

        // Setup memory manager update response
        doAnswer(invocation -> {
            ActionListener<UpdateResponse> listener = invocation.getArgument(2);
            listener.onResponse(updateResponse);
            return null;
        }).when(mlMemoryManager).updateInteraction(any(), any(), any());

        // Run the agent
        Map<String, String> params = new HashMap<>();
        params.put("question", "test question");
        params.put(MLAgentExecutor.PARENT_INTERACTION_ID, "test_parent_interaction_id");
        mlPlanExecuteAndReflectAgentRunner.run(mlAgent, params, agentActionListener);

        // Verify the response
        verify(agentActionListener).onResponse(objectCaptor.capture());
        Object response = objectCaptor.getValue();
        assertTrue(response instanceof ModelTensorOutput);
        ModelTensorOutput modelTensorOutput = (ModelTensorOutput) response;

        List<ModelTensors> mlModelOutputs = modelTensorOutput.getMlModelOutputs();
        assertEquals(2, mlModelOutputs.size());

        ModelTensors firstModelTensors = mlModelOutputs.get(0);
        List<ModelTensor> firstModelTensorList = firstModelTensors.getMlModelTensors();
        assertEquals(4, firstModelTensorList.size());

        ModelTensor memoryIdTensor = firstModelTensorList.get(0);
        assertEquals("memory_id", memoryIdTensor.getName());
        assertEquals("test_memory_id", memoryIdTensor.getResult());

        ModelTensor parentInteractionModelTensor = firstModelTensorList.get(1);
        assertEquals("parent_interaction_id", parentInteractionModelTensor.getName());
        assertEquals("test_parent_interaction_id", parentInteractionModelTensor.getResult());

        ModelTensors secondModelTensors = mlModelOutputs.get(1);
        List<ModelTensor> secondModelTensorList = secondModelTensors.getMlModelTensors();
        assertEquals(1, secondModelTensorList.size());

        ModelTensor responseTensor = secondModelTensorList.get(0);
        assertEquals("response", responseTensor.getName());
        assertEquals("final result", responseTensor.getDataAsMap().get("response"));
    }

    @Test
    public void testExecutionWithHistory() {
        MLAgent mlAgent = createMLAgentWithTools();

        // Setup LLM response for planning phase
        doAnswer(invocation -> {
            ActionListener<Object> listener = invocation.getArgument(2);
            ModelTensor modelTensor = ModelTensor
                .builder()
                .dataAsMap(ImmutableMap.of("response", "{\"steps\":[\"step1\"], \"result\":\"final result\"}"))
                .build();
            ModelTensors modelTensors = ModelTensors.builder().mlModelTensors(Arrays.asList(modelTensor)).build();
            ModelTensorOutput mlModelTensorOutput = ModelTensorOutput.builder().mlModelOutputs(Arrays.asList(modelTensors)).build();
            when(mlTaskResponse.getOutput()).thenReturn(mlModelTensorOutput);
            listener.onResponse(mlTaskResponse);
            return null;
        }).when(client).execute(eq(MLPredictionTaskAction.INSTANCE), any(MLPredictionTaskRequest.class), any());

        // Setup tool execution response
        doAnswer(invocation -> {
            ActionListener<Object> listener = invocation.getArgument(1);
            ModelTensor modelTensor = ModelTensor.builder().dataAsMap(ImmutableMap.of("response", "tool execution result")).build();
            ModelTensors modelTensors = ModelTensors.builder().mlModelTensors(Arrays.asList(modelTensor)).build();
            ModelTensorOutput mlModelTensorOutput = ModelTensorOutput.builder().mlModelOutputs(Arrays.asList(modelTensors)).build();
            when(mlExecuteTaskResponse.getOutput()).thenReturn(mlModelTensorOutput);
            listener.onResponse(mlExecuteTaskResponse);
            return null;
        }).when(client).execute(eq(MLExecuteTaskAction.INSTANCE), any(MLExecuteTaskRequest.class), any());

        // Setup memory manager update response
        doAnswer(invocation -> {
            ActionListener<UpdateResponse> listener = invocation.getArgument(2);
            listener.onResponse(updateResponse);
            return null;
        }).when(mlMemoryManager).updateInteraction(any(), any(), any());

        // Run the agent with history
        Map<String, String> params = new HashMap<>();
        params.put("question", "test question");
        params.put("memory_id", "test_memory_id");
        params.put("parent_interaction_id", "test_parent_interaction_id");
        mlPlanExecuteAndReflectAgentRunner.run(mlAgent, params, agentActionListener);

        // Verify the response
        verify(agentActionListener).onResponse(objectCaptor.capture());
        Object response = objectCaptor.getValue();
        assertTrue(response instanceof ModelTensorOutput);
        ModelTensorOutput modelTensorOutput = (ModelTensorOutput) response;

        List<ModelTensors> mlModelOutputs = modelTensorOutput.getMlModelOutputs();
        assertEquals(2, mlModelOutputs.size());

        ModelTensors firstModelTensors = mlModelOutputs.get(0);
        List<ModelTensor> firstModelTensorList = firstModelTensors.getMlModelTensors();
        assertEquals(4, firstModelTensorList.size());

        ModelTensor memoryIdTensor = firstModelTensorList.get(0);
        assertEquals("memory_id", memoryIdTensor.getName());
        assertEquals("test_memory_id", memoryIdTensor.getResult());

        ModelTensor parentInteractionModelTensor = firstModelTensorList.get(1);
        assertEquals("parent_interaction_id", parentInteractionModelTensor.getName());
        assertEquals("test_parent_interaction_id", parentInteractionModelTensor.getResult());

        ModelTensors secondModelTensors = mlModelOutputs.get(1);
        List<ModelTensor> secondModelTensorList = secondModelTensors.getMlModelTensors();
        assertEquals(1, secondModelTensorList.size());

        ModelTensor responseTensor = secondModelTensorList.get(0);
        assertEquals("response", responseTensor.getName());
        assertEquals("final result", responseTensor.getDataAsMap().get("response"));
    }

    @Test
    public void testMessageHistoryLimits() {
        MLAgent mlAgent = createMLAgentWithTools();

        doAnswer(invocation -> {
            ActionListener<Object> listener = invocation.getArgument(2);
            ModelTensor modelTensor = ModelTensor
                .builder()
                .dataAsMap(ImmutableMap.of("response", "{\"steps\":[\"step1\"], \"result\":\"\"}"))
                .build();
            ModelTensors modelTensors = ModelTensors.builder().mlModelTensors(Arrays.asList(modelTensor)).build();
            ModelTensorOutput mlModelTensorOutput = ModelTensorOutput.builder().mlModelOutputs(Arrays.asList(modelTensors)).build();
            when(mlTaskResponse.getOutput()).thenReturn(mlModelTensorOutput);
            listener.onResponse(mlTaskResponse);
            return null;
        }).when(client).execute(eq(MLPredictionTaskAction.INSTANCE), any(MLPredictionTaskRequest.class), any());

        doAnswer(invocation -> {
            ActionListener<Object> listener = invocation.getArgument(1);
            ModelTensor modelTensor = ModelTensor.builder().dataAsMap(ImmutableMap.of("response", "tool execution result")).build();
            ModelTensors modelTensors = ModelTensors.builder().mlModelTensors(Arrays.asList(modelTensor)).build();
            ModelTensorOutput mlModelTensorOutput = ModelTensorOutput.builder().mlModelOutputs(Arrays.asList(modelTensors)).build();
            when(mlExecuteTaskResponse.getOutput()).thenReturn(mlModelTensorOutput);
            listener.onResponse(mlExecuteTaskResponse);
            return null;
        }).when(client).execute(eq(MLExecuteTaskAction.INSTANCE), any(MLExecuteTaskRequest.class), any());

        doAnswer(invocation -> {
            ActionListener<UpdateResponse> listener = invocation.getArgument(2);
            listener.onResponse(updateResponse);
            return null;
        }).when(mlMemoryManager).updateInteraction(any(), any(), any());

        Map<String, String> params = new HashMap<>();
        params.put("question", "test question");
        params.put("memory_id", "test_memory_id");
        params.put("parent_interaction_id", "test_parent_interaction_id");
        params.put("message_history_limit", "5");
        params.put("executor_message_history_limit", "3");
        mlPlanExecuteAndReflectAgentRunner.run(mlAgent, params, agentActionListener);

        verify(conversationIndexMemory).getMessages(any(), eq(5));

        ArgumentCaptor<MLExecuteTaskRequest> executeCaptor = ArgumentCaptor.forClass(MLExecuteTaskRequest.class);
        verify(client).execute(eq(MLExecuteTaskAction.INSTANCE), executeCaptor.capture(), any());

        AgentMLInput agentInput = (AgentMLInput) executeCaptor.getValue().getInput();
        RemoteInferenceInputDataSet dataset = (RemoteInferenceInputDataSet) agentInput.getInputDataset();
        Map<String, String> executorParams = dataset.getParameters();
        assertEquals("3", executorParams.get("message_history_limit"));
    }

    // ToDo: add test case for when max steps is reached

    private MLAgent createMLAgentWithTools() {
        LLMSpec llmSpec = LLMSpec.builder().modelId("MODEL_ID").build();
        MLToolSpec firstToolSpec = MLToolSpec
            .builder()
            .name(FIRST_TOOL)
            .type(FIRST_TOOL)
            .parameters(ImmutableMap.of("key1", "value1", "key2", "value2"))
            .build();

        return MLAgent
            .builder()
            .name("TestAgent")
            .type(MLAgentType.CONVERSATIONAL.name())
            .tools(Arrays.asList(firstToolSpec))
            .memory(mlMemorySpec)
            .llm(llmSpec)
            .parameters(Collections.emptyMap())
            .build();
    }

    private List<Interaction> generateInteractions() {
        return Arrays
            .asList(
                Interaction.builder().id("interaction-1").input("input-1").response("response-1").build(),
                Interaction.builder().id("interaction-2").input("input-2").response("response-2").build()
            );
    }

    @Test
    public void testSetupPromptParameters() {
        Map<String, String> testParams = new HashMap<>();
        testParams.put(MLPlanExecuteAndReflectAgentRunner.QUESTION_FIELD, "test question");
        testParams.put(MLPlanExecuteAndReflectAgentRunner.SYSTEM_PROMPT_FIELD, "custom system prompt");

        mlPlanExecuteAndReflectAgentRunner.setupPromptParameters(testParams);

        assertEquals("test question", testParams.get(MLPlanExecuteAndReflectAgentRunner.USER_PROMPT_FIELD));
        assertTrue(testParams.get(MLPlanExecuteAndReflectAgentRunner.SYSTEM_PROMPT_FIELD).contains("custom system prompt"));
        assertNotNull(testParams.get(MLPlanExecuteAndReflectAgentRunner.PLANNER_PROMPT_FIELD));
        assertNotNull(testParams.get(MLPlanExecuteAndReflectAgentRunner.REFLECT_PROMPT_FIELD));
        assertEquals(
            PLAN_EXECUTE_REFLECT_RESPONSE_FORMAT,
            testParams.get(MLPlanExecuteAndReflectAgentRunner.PLAN_EXECUTE_REFLECT_RESPONSE_FORMAT_FIELD)
        );
    }

    @Test
    public void testSetupPromptParametersWithDateInjection() {
        Map<String, String> testParams = new HashMap<>();
        testParams.put(MLPlanExecuteAndReflectAgentRunner.QUESTION_FIELD, "test question");
        testParams.put(MLPlanExecuteAndReflectAgentRunner.INJECT_DATETIME_FIELD, "true");

        mlPlanExecuteAndReflectAgentRunner.setupPromptParameters(testParams);

        assertEquals("test question", testParams.get(MLPlanExecuteAndReflectAgentRunner.USER_PROMPT_FIELD));

        // Verify planner system prompt contains date/time
        String plannerSystemPrompt = testParams.get(MLPlanExecuteAndReflectAgentRunner.SYSTEM_PROMPT_FIELD);
        assertTrue(plannerSystemPrompt.contains(DEFAULT_DATETIME_PREFIX));
        assertTrue(plannerSystemPrompt.contains(MLPlanExecuteAndReflectAgentRunner.DEFAULT_PLANNER_SYSTEM_PROMPT));

        // Verify executor system prompt contains date/time
        String executorSystemPrompt = testParams.get(MLPlanExecuteAndReflectAgentRunner.EXECUTOR_SYSTEM_PROMPT_FIELD);
        assertTrue(executorSystemPrompt.contains(DEFAULT_DATETIME_PREFIX));
        assertTrue(executorSystemPrompt.contains(MLPlanExecuteAndReflectAgentRunner.DEFAULT_EXECUTOR_SYSTEM_PROMPT));

        assertNotNull(testParams.get(MLPlanExecuteAndReflectAgentRunner.PLANNER_PROMPT_FIELD));
        assertNotNull(testParams.get(MLPlanExecuteAndReflectAgentRunner.REFLECT_PROMPT_FIELD));
        assertEquals(
            PLAN_EXECUTE_REFLECT_RESPONSE_FORMAT,
            testParams.get(MLPlanExecuteAndReflectAgentRunner.PLAN_EXECUTE_REFLECT_RESPONSE_FORMAT_FIELD)
        );
    }

    @Test
    public void testSetupPromptParametersWithoutDateInjection() {
        Map<String, String> testParams = new HashMap<>();
        testParams.put(MLPlanExecuteAndReflectAgentRunner.QUESTION_FIELD, "test question");
        testParams.put(MLPlanExecuteAndReflectAgentRunner.INJECT_DATETIME_FIELD, "false");

        mlPlanExecuteAndReflectAgentRunner.setupPromptParameters(testParams);

        assertEquals("test question", testParams.get(MLPlanExecuteAndReflectAgentRunner.USER_PROMPT_FIELD));

        // Verify planner system prompt does NOT contain date/time
        String plannerSystemPrompt = testParams.get(MLPlanExecuteAndReflectAgentRunner.SYSTEM_PROMPT_FIELD);
        assertFalse(plannerSystemPrompt.contains(DEFAULT_DATETIME_PREFIX));
        assertEquals(MLPlanExecuteAndReflectAgentRunner.DEFAULT_PLANNER_SYSTEM_PROMPT, plannerSystemPrompt);

        // Verify executor system prompt does NOT contain date/time
        String executorSystemPrompt = testParams.get(MLPlanExecuteAndReflectAgentRunner.EXECUTOR_SYSTEM_PROMPT_FIELD);
        assertFalse(executorSystemPrompt.contains(DEFAULT_DATETIME_PREFIX));
        assertEquals(MLPlanExecuteAndReflectAgentRunner.DEFAULT_EXECUTOR_SYSTEM_PROMPT, executorSystemPrompt);
    }

    @Test
    public void testUsePlannerPromptTemplate() {
        Map<String, String> testParams = new HashMap<>();
        mlPlanExecuteAndReflectAgentRunner.usePlannerPromptTemplate(testParams);
        assertNotNull(testParams.get(MLPlanExecuteAndReflectAgentRunner.PROMPT_TEMPLATE_FIELD));
        assertNotNull(testParams.get(MLPlanExecuteAndReflectAgentRunner.PROMPT_FIELD));
    }

    @Test
    public void testUseReflectPromptTemplate() {
        Map<String, String> testParams = new HashMap<>();
        mlPlanExecuteAndReflectAgentRunner.useReflectPromptTemplate(testParams);
        assertNotNull(testParams.get(MLPlanExecuteAndReflectAgentRunner.PROMPT_TEMPLATE_FIELD));
        assertNotNull(testParams.get(MLPlanExecuteAndReflectAgentRunner.PROMPT_FIELD));
    }

    @Test
    public void testUsePlannerWithHistoryPromptTemplate() {
        Map<String, String> testParams = new HashMap<>();
        mlPlanExecuteAndReflectAgentRunner.usePlannerWithHistoryPromptTemplate(testParams);
        assertNotNull(testParams.get(MLPlanExecuteAndReflectAgentRunner.PROMPT_TEMPLATE_FIELD));
        assertNotNull(testParams.get(MLPlanExecuteAndReflectAgentRunner.PROMPT_FIELD));
    }

    @Test
    public void testPopulatePrompt() {
        Map<String, String> testParams = new HashMap<>();
        testParams.put(MLPlanExecuteAndReflectAgentRunner.PROMPT_TEMPLATE_FIELD, "Hello ${parameters.name}!");
        testParams.put("name", "World");

        mlPlanExecuteAndReflectAgentRunner.populatePrompt(testParams);

        assertEquals("Hello World!", testParams.get(MLPlanExecuteAndReflectAgentRunner.PROMPT_FIELD));
    }

    @Test
    public void testParseLLMOutput() {
        Map<String, String> allParams = new HashMap<>();
        ModelTensor modelTensor = ModelTensor
            .builder()
            .dataAsMap(
                Map.of(MLPlanExecuteAndReflectAgentRunner.RESPONSE_FIELD, "{\"steps\":[\"step1\",\"step2\"],\"result\":\"final result\"}")
            )
            .build();
        ModelTensors modelTensors = ModelTensors.builder().mlModelTensors(Arrays.asList(modelTensor)).build();
        ModelTensorOutput modelTensorOutput = ModelTensorOutput.builder().mlModelOutputs(Arrays.asList(modelTensors)).build();

        Map<String, String> result = mlPlanExecuteAndReflectAgentRunner.parseLLMOutput(allParams, modelTensorOutput);

        assertEquals("step1, step2", result.get(MLPlanExecuteAndReflectAgentRunner.STEPS_FIELD));
        assertEquals("final result", result.get(MLPlanExecuteAndReflectAgentRunner.RESULT_FIELD));

        modelTensor = ModelTensor.builder().dataAsMap(Map.of(MLPlanExecuteAndReflectAgentRunner.RESPONSE_FIELD, "random response")).build();
        modelTensors = ModelTensors.builder().mlModelTensors(Arrays.asList(modelTensor)).build();
        final ModelTensorOutput modelTensorOutput2 = ModelTensorOutput.builder().mlModelOutputs(Arrays.asList(modelTensors)).build();

        assertThrows(IllegalStateException.class, () -> mlPlanExecuteAndReflectAgentRunner.parseLLMOutput(allParams, modelTensorOutput2));

        modelTensor = ModelTensor
            .builder()
            .dataAsMap(Map.of(MLPlanExecuteAndReflectAgentRunner.RESPONSE_FIELD, "{ \"random\": \"random response\"}"))
            .build();
        modelTensors = ModelTensors.builder().mlModelTensors(Arrays.asList(modelTensor)).build();
        final ModelTensorOutput modelTensorOutput3 = ModelTensorOutput.builder().mlModelOutputs(Arrays.asList(modelTensors)).build();

        assertThrows(
            IllegalArgumentException.class,
            () -> mlPlanExecuteAndReflectAgentRunner.parseLLMOutput(allParams, modelTensorOutput3)
        );
    }

    @Test
    public void testExtractJsonFromMarkdown() {
        String markdown = "```json\n{\"key\":\"value\"}\n```";
        String result = mlPlanExecuteAndReflectAgentRunner.extractJsonFromMarkdown(markdown);
        assertEquals("{\"key\":\"value\"}", result);
    }

    @Test
    public void testExtractJsonFromMarkdownWithoutJsonPrefix() {
        String markdown = "This is the json output {\"key\":\"value\"}\n";
        String result = mlPlanExecuteAndReflectAgentRunner.extractJsonFromMarkdown(markdown);
        assertEquals("{\"key\":\"value\"}", result);
    }

    @Test
    public void testAddToolsToPrompt() {
        Map<String, String> testParams = new HashMap<>();
        Map<String, Tool> tools = new HashMap<>();
        Tool tool1 = mock(Tool.class);
        when(tool1.getName()).thenReturn("tool1");
        when(tool1.getDescription()).thenReturn("description1");
        tools.put("tool1", tool1);

        mlPlanExecuteAndReflectAgentRunner.addToolsToPrompt(tools, testParams);

        assertEquals(
            "In this environment, the executor agent only has access to the below tools. You must choose from only the following tools — no other tools are available. Do not use tools not listed here. \n"
                + "Tool 1 - tool1: description1\n"
                + "\n"
                + "No other tools are available. Do not invent tools.\n\n",
            testParams.get(MLPlanExecuteAndReflectAgentRunner.DEFAULT_PROMPT_TOOLS_FIELD)
        );
    }

    @Test
    public void testAddSteps() {
        Map<String, String> testParams = new HashMap<>();
        List<String> steps = Arrays.asList("step1", "step2");
        String field = "test_field";

        mlPlanExecuteAndReflectAgentRunner.addSteps(steps, testParams, field);

        assertEquals("step1, step2", testParams.get(field));
    }

    @Test
    public void testCreateModelTensors() {
        String sessionId = "test_session";
        String parentInteractionId = "test_parent";
        String executorMemoryId = "test_executor_mem_id";
        String executorParentId = "test_executor_parent_id";

        List<ModelTensors> result = MLPlanExecuteAndReflectAgentRunner
            .createModelTensors(sessionId, parentInteractionId, executorMemoryId, executorParentId);

        assertNotNull(result);
        assertEquals(1, result.size());
        ModelTensors tensors = result.get(0);
        assertEquals(4, tensors.getMlModelTensors().size());
        assertEquals(sessionId, tensors.getMlModelTensors().get(0).getResult());
        assertEquals(parentInteractionId, tensors.getMlModelTensors().get(1).getResult());
        assertEquals(executorMemoryId, tensors.getMlModelTensors().get(2).getResult());
        assertEquals(executorParentId, tensors.getMlModelTensors().get(3).getResult());
    }

    @Test
    public void testSaveAndReturnFinalResult() {
        String parentInteractionId = "test_parent_id";
        String finalResult = "test final result";
        String input = "test input";
        String conversationId = "test_conversation_id";
        String executorMemoryId = "test_executor_mem_id";
        String executorParentId = "test_executor_parent_id";

        when(conversationIndexMemory.getConversationId()).thenReturn(conversationId);
        when(conversationIndexMemory.getMemoryManager()).thenReturn(mlMemoryManager);

        doAnswer(invocation -> {
            ActionListener<UpdateResponse> listener = invocation.getArgument(2);
            listener.onResponse(updateResponse);
            return null;
        }).when(mlMemoryManager).updateInteraction(eq(parentInteractionId), any(), any());

        mlPlanExecuteAndReflectAgentRunner
            .saveAndReturnFinalResult(
                conversationIndexMemory,
                parentInteractionId,
                executorMemoryId,
                executorParentId,
                finalResult,
                input,
                agentActionListener
            );

        verify(agentActionListener).onResponse(objectCaptor.capture());
        Object response = objectCaptor.getValue();
        assertTrue(response instanceof ModelTensorOutput);
        ModelTensorOutput modelTensorOutput = (ModelTensorOutput) response;

        List<ModelTensors> mlModelOutputs = modelTensorOutput.getMlModelOutputs();
        assertEquals(2, mlModelOutputs.size());

        ModelTensors firstModelTensors = mlModelOutputs.get(0);
        List<ModelTensor> firstModelTensorList = firstModelTensors.getMlModelTensors();
        assertEquals(4, firstModelTensorList.size());
        assertEquals(conversationId, firstModelTensorList.get(0).getResult());
        assertEquals(parentInteractionId, firstModelTensorList.get(1).getResult());
        assertEquals(executorMemoryId, firstModelTensorList.get(2).getResult());
        assertEquals(executorParentId, firstModelTensorList.get(3).getResult());

        ModelTensors secondModelTensors = mlModelOutputs.get(1);
        List<ModelTensor> secondModelTensorList = secondModelTensors.getMlModelTensors();
        assertEquals(1, secondModelTensorList.size());
        assertEquals(finalResult, secondModelTensorList.get(0).getDataAsMap().get("response"));
    }

    @Test
    public void testUpdateTaskWithExecutorAgentInfo() {
        MLAgent mlAgent = createMLAgentWithTools();
        String taskId = "test-task-id";
        // to ensure second call to prediction returns a result
        AtomicInteger callCount = new AtomicInteger(0);

        try (MockedStatic<MLTaskUtils> mlTaskUtilsMockedStatic = mockStatic(MLTaskUtils.class)) {
            mlTaskUtilsMockedStatic
                .when(() -> MLTaskUtils.updateMLTaskDirectly(anyString(), any(), any(), any()))
                .thenAnswer(invocation -> {
                    ActionListener<UpdateResponse> listener = invocation.getArgument(3);
                    listener.onResponse(updateResponse);
                    return null;
                });

            doAnswer(invocation -> {
                ActionListener<Object> listener = invocation.getArgument(2);
                ModelTensor modelTensor;
                if (callCount.getAndIncrement() == 0) {
                    modelTensor = ModelTensor
                        .builder()
                        .dataAsMap(ImmutableMap.of("response", "{\"steps\":[\"step1\", \"step2\"], \"result\":\"\"}"))
                        .build();
                } else {
                    modelTensor = ModelTensor
                        .builder()
                        .dataAsMap(ImmutableMap.of("response", "{\"steps\":[\"step1\", \"step2\"], \"result\":\"final result\"}"))
                        .build();
                }

                ModelTensors modelTensors = ModelTensors.builder().mlModelTensors(Arrays.asList(modelTensor)).build();
                ModelTensorOutput mlModelTensorOutput = ModelTensorOutput.builder().mlModelOutputs(Arrays.asList(modelTensors)).build();
                when(mlTaskResponse.getOutput()).thenReturn(mlModelTensorOutput);
                listener.onResponse(mlTaskResponse);
                return null;
            }).when(client).execute(eq(MLPredictionTaskAction.INSTANCE), any(MLPredictionTaskRequest.class), any());

            doAnswer(invocation -> {
                ActionListener<Object> listener = invocation.getArgument(2);
                ModelTensor memoryIdTensor = ModelTensor
                    .builder()
                    .name(MLAgentExecutor.MEMORY_ID)
                    .result("test_executor_memory_id")
                    .build();
                ModelTensor parentIdTensor = ModelTensor
                    .builder()
                    .name(MLAgentExecutor.PARENT_INTERACTION_ID)
                    .result("test_executor_parent_id")
                    .build();
                ModelTensor responseTensor = ModelTensor
                    .builder()
                    .name("response")
                    .dataAsMap(ImmutableMap.of("response", "tool execution result"))
                    .build();
                ModelTensors modelTensors = ModelTensors
                    .builder()
                    .mlModelTensors(Arrays.asList(memoryIdTensor, parentIdTensor, responseTensor))
                    .build();
                ModelTensorOutput mlModelTensorOutput = ModelTensorOutput.builder().mlModelOutputs(Arrays.asList(modelTensors)).build();
                when(mlExecuteTaskResponse.getOutput()).thenReturn(mlModelTensorOutput);
                listener.onResponse(mlExecuteTaskResponse);
                return null;
            }).when(client).execute(eq(MLExecuteTaskAction.INSTANCE), any(MLExecuteTaskRequest.class), any());

            doAnswer(invocation -> {
                ActionListener<UpdateResponse> listener = invocation.getArgument(2);
                listener.onResponse(updateResponse);
                return null;
            }).when(mlMemoryManager).updateInteraction(any(), any(), any());

            Map<String, String> params = new HashMap<>();
            params.put("question", "test question");
            params.put("memory_id", "test_memory_id");
            params.put("parent_interaction_id", "test_parent_interaction_id");
            params.put("task_id", taskId);
            mlPlanExecuteAndReflectAgentRunner.run(mlAgent, params, agentActionListener);

            Map<String, Object> taskUpdates = mlPlanExecuteAndReflectAgentRunner.getTaskUpdates();
            assertEquals(MLTaskState.RUNNING, taskUpdates.get("state"));

            Map<String, Object> response = (Map<String, Object>) taskUpdates.get("response");
            assertEquals("test_executor_memory_id", response.get("executor_agent_memory_id"));
            assertEquals("test_executor_parent_id", response.get("executor_agent_parent_interaction_id"));

            mlTaskUtilsMockedStatic.verify(() -> MLTaskUtils.updateMLTaskDirectly(eq(taskId), eq(taskUpdates), eq(client), any()));
        }
    }
}
