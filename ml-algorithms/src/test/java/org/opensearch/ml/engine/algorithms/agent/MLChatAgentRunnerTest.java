/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.agent;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.opensearch.ml.engine.algorithms.agent.AgentUtils.DEFAULT_DATETIME_PREFIX;
import static org.opensearch.ml.engine.algorithms.agent.MLAgentExecutor.MESSAGE_HISTORY_LIMIT;
import static org.opensearch.ml.engine.memory.ConversationIndexMemory.LAST_N_INTERACTIONS;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.stubbing.Answer;
import org.opensearch.action.ActionRequest;
import org.opensearch.action.ActionType;
import org.opensearch.action.StepListener;
import org.opensearch.action.update.UpdateResponse;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.Settings;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.ml.common.MLAgentType;
import org.opensearch.ml.common.agent.LLMSpec;
import org.opensearch.ml.common.agent.MLAgent;
import org.opensearch.ml.common.agent.MLMemorySpec;
import org.opensearch.ml.common.agent.MLToolSpec;
import org.opensearch.ml.common.conversation.Interaction;
import org.opensearch.ml.common.output.model.ModelTensor;
import org.opensearch.ml.common.output.model.ModelTensorOutput;
import org.opensearch.ml.common.output.model.ModelTensors;
import org.opensearch.ml.common.spi.memory.Memory;
import org.opensearch.ml.common.spi.tools.Tool;
import org.opensearch.ml.common.transport.MLTaskResponse;
import org.opensearch.ml.engine.memory.ConversationIndexMemory;
import org.opensearch.ml.engine.memory.MLMemoryManager;
import org.opensearch.ml.engine.memory.bedrockagentcore.BedrockAgentCoreMemory;
import org.opensearch.ml.engine.memory.bedrockagentcore.BedrockAgentCoreMemoryRecord;
import org.opensearch.ml.memory.action.conversation.CreateInteractionResponse;
import org.opensearch.ml.repackage.com.google.common.collect.ImmutableMap;
import org.opensearch.transport.client.Client;

public class MLChatAgentRunnerTest {
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
    private MLChatAgentRunner mlChatAgentRunner;
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

    @Captor
    private ArgumentCaptor<Object> objectCaptor;

    @Captor
    private ArgumentCaptor<StepListener<Object>> nextStepListenerCaptor;

    @Captor
    private ArgumentCaptor<ActionListener<Object>> toolListenerCaptor;

    private MLMemorySpec mlMemorySpec;
    @Mock
    private ConversationIndexMemory conversationIndexMemory;
    @Mock
    private MLMemoryManager mlMemoryManager;
    @Mock
    private CreateInteractionResponse createInteractionResponse;
    @Mock
    private UpdateResponse updateResponse;

    @Mock
    private ConversationIndexMemory.Factory memoryFactory;
    @Mock
    private BedrockAgentCoreMemory.Factory bedrockMemoryFactory;
    @Mock
    private BedrockAgentCoreMemory bedrockAgentCoreMemory;
    @Captor
    private ArgumentCaptor<ActionListener<ConversationIndexMemory>> memoryFactoryCapture;
    @Captor
    private ArgumentCaptor<ActionListener<List<Interaction>>> memoryInteractionCapture;
    @Captor
    private ArgumentCaptor<Integer> messageHistoryLimitCapture;
    @Captor
    private ArgumentCaptor<ActionListener<CreateInteractionResponse>> conversationIndexMemoryCapture;
    @Captor
    private ArgumentCaptor<ActionListener<UpdateResponse>> mlMemoryManagerCapture;
    @Captor
    private ArgumentCaptor<ActionListener<BedrockAgentCoreMemory>> bedrockMemoryFactoryCapture;
    @Captor
    private ArgumentCaptor<ActionListener<List<Interaction>>> bedrockMemoryInteractionCapture;
    @Captor
    private ArgumentCaptor<Map<String, String>> toolParamsCapture;
    @Captor
    private ArgumentCaptor<Map<String, Object>> bedrockMemoryParamsCapture;

    @Before
    @SuppressWarnings("unchecked")
    public void setup() {
        MockitoAnnotations.openMocks(this);
        settings = Settings.builder().build();
        toolFactories = ImmutableMap.of(FIRST_TOOL, firstToolFactory, SECOND_TOOL, secondToolFactory);

        // memory
        mlMemorySpec = new MLMemorySpec(ConversationIndexMemory.TYPE, "uuid", 10);
        when(memoryMap.get(anyString())).thenReturn(memoryFactory);
        doAnswer(invocation -> {
            ActionListener<List<Interaction>> listener = invocation.getArgument(0);
            listener.onResponse(generateInteractions(2));
            return null;
        }).when(conversationIndexMemory).getMessages(memoryInteractionCapture.capture(), messageHistoryLimitCapture.capture());
        when(conversationIndexMemory.getConversationId()).thenReturn("conversation_id");
        when(conversationIndexMemory.getMemoryManager()).thenReturn(mlMemoryManager);
        doAnswer(invocation -> {
            ActionListener<ConversationIndexMemory> listener = invocation.getArgument(3);
            listener.onResponse(conversationIndexMemory);
            return null;
        }).when(memoryFactory).create(any(), any(), any(), memoryFactoryCapture.capture());
        when(createInteractionResponse.getId()).thenReturn("create_interaction_id");
        doAnswer(invocation -> {
            ActionListener<CreateInteractionResponse> listener = invocation.getArgument(4);
            listener.onResponse(createInteractionResponse);
            return null;
        }).when(conversationIndexMemory).save(any(), any(), any(), any(), conversationIndexMemoryCapture.capture());
        when(updateResponse.getId()).thenReturn("update_interaction_id");
        doAnswer(invocation -> {
            ActionListener<UpdateResponse> listener = invocation.getArgument(2);
            listener.onResponse(updateResponse);
            return null;
        }).when(mlMemoryManager).updateInteraction(any(), any(), mlMemoryManagerCapture.capture());

        mlChatAgentRunner = new MLChatAgentRunner(client, settings, clusterService, xContentRegistry, toolFactories, memoryMap, null, null);
        when(firstToolFactory.create(Mockito.anyMap())).thenReturn(firstTool);
        when(secondToolFactory.create(Mockito.anyMap())).thenReturn(secondTool);
        when(firstTool.getName()).thenReturn(FIRST_TOOL);
        when(firstTool.getDescription()).thenReturn("First tool description");
        when(secondTool.getName()).thenReturn(SECOND_TOOL);
        when(secondTool.getDescription()).thenReturn("Second tool description");
        when(firstTool.validate(Mockito.anyMap())).thenReturn(true);
        when(secondTool.validate(Mockito.anyMap())).thenReturn(true);
        Mockito.doAnswer(generateToolResponse("First tool response")).when(firstTool).run(Mockito.anyMap(), toolListenerCaptor.capture());
        Mockito.doAnswer(generateToolResponse("Second tool response")).when(secondTool).run(Mockito.anyMap(), toolListenerCaptor.capture());

        Mockito
            .doAnswer(getLLMAnswer(ImmutableMap.of("thought", "thought 1", "action", FIRST_TOOL)))
            .doAnswer(getLLMAnswer(ImmutableMap.of("thought", "thought 2", "action", SECOND_TOOL)))
            .doAnswer(getLLMAnswer(ImmutableMap.of("thought", "thought 3", "final_answer", "This is the final answer")))
            .when(client)
            .execute(any(ActionType.class), any(ActionRequest.class), isA(ActionListener.class));
    }

    @Test
    public void testParsingJsonBlockFromResponse() {
        // Prepare the response with JSON block
        String jsonBlock = "{\"thought\":\"parsed thought\", \"action\":\"parsed action\", "
            + "\"action_input\":\"parsed action input\", \"final_answer\":\"parsed final answer\"}";
        String responseWithJsonBlock = "Some text```json" + jsonBlock + "```More text";

        // Mock LLM response to not contain "thought" but contain "response" with JSON block
        Map<String, String> llmResponse = new HashMap<>();
        llmResponse.put("response", responseWithJsonBlock);
        doAnswer(getLLMAnswer(llmResponse))
            .when(client)
            .execute(any(ActionType.class), any(ActionRequest.class), isA(ActionListener.class));

        // Create an MLAgent and run the MLChatAgentRunner
        MLAgent mlAgent = createMLAgentWithTools();
        Map<String, String> params = new HashMap<>();
        params.put(MLAgentExecutor.PARENT_INTERACTION_ID, "parent_interaction_id");
        params.put("verbose", "true");
        mlChatAgentRunner.run(mlAgent, params, agentActionListener);

        // Capture the response passed to the listener
        ArgumentCaptor<Object> responseCaptor = ArgumentCaptor.forClass(Object.class);
        verify(agentActionListener).onResponse(responseCaptor.capture());

        // Extract the captured response
        Object capturedResponse = responseCaptor.getValue();
        assertTrue(capturedResponse instanceof ModelTensorOutput);
        ModelTensorOutput modelTensorOutput = (ModelTensorOutput) capturedResponse;

        ModelTensor parentInteractionModelTensor = modelTensorOutput.getMlModelOutputs().get(0).getMlModelTensors().get(1);
        ModelTensor modelTensor1 = modelTensorOutput.getMlModelOutputs().get(0).getMlModelTensors().get(0);
        ModelTensor modelTensor2 = modelTensorOutput.getMlModelOutputs().get(1).getMlModelTensors().get(0);

        // Verify that the parsed values from JSON block are correctly set
        assertEquals("parent_interaction_id", parentInteractionModelTensor.getResult());
        assertEquals("conversation_id", modelTensor1.getResult());
        assertEquals("parsed final answer", modelTensor2.getResult());
    }

    @Test
    public void testRunWithIncludeOutputNotSet() {
        LLMSpec llmSpec = LLMSpec.builder().modelId("MODEL_ID").build();
        MLToolSpec firstToolSpec = MLToolSpec
            .builder()
            .name(FIRST_TOOL)
            .type(FIRST_TOOL)
            .parameters(ImmutableMap.of("key1", "value1", "key2", "value2"))
            .build();
        MLToolSpec secondToolSpec = MLToolSpec
            .builder()
            .name(SECOND_TOOL)
            .type(SECOND_TOOL)
            .parameters(ImmutableMap.of("key1", "value1", "key2", "value2"))
            .build();
        final MLAgent mlAgent = MLAgent
            .builder()
            .name("TestAgent")
            .type(MLAgentType.CONVERSATIONAL.name())
            .llm(llmSpec)
            .memory(mlMemorySpec)
            .tools(Arrays.asList(firstToolSpec, secondToolSpec))
            .build();
        mlChatAgentRunner.run(mlAgent, new HashMap<>(), agentActionListener);
        Mockito.verify(agentActionListener).onResponse(objectCaptor.capture());
        ModelTensorOutput modelTensorOutput = (ModelTensorOutput) objectCaptor.getValue();
        List<ModelTensor> agentOutput = modelTensorOutput.getMlModelOutputs().get(1).getMlModelTensors();
        assertEquals(1, agentOutput.size());
        // Respond with last tool output
        assertEquals("This is the final answer", agentOutput.get(0).getDataAsMap().get("response"));
    }

    @Test
    public void testRunWithIncludeOutputMLModel() {
        LLMSpec llmSpec = LLMSpec.builder().modelId("MODEL_ID").build();
        Mockito
            .doAnswer(generateToolResponseAsMLModelResult("First tool response", 1))
            .when(firstTool)
            .run(Mockito.anyMap(), toolListenerCaptor.capture());
        Mockito
            .doAnswer(generateToolResponseAsMLModelResult("Second tool response", 2))
            .when(secondTool)
            .run(Mockito.anyMap(), toolListenerCaptor.capture());
        MLToolSpec firstToolSpec = MLToolSpec.builder().name(FIRST_TOOL).type(FIRST_TOOL).build();
        MLToolSpec secondToolSpec = MLToolSpec.builder().name(SECOND_TOOL).type(SECOND_TOOL).build();
        final MLAgent mlAgent = MLAgent
            .builder()
            .name("TestAgent")
            .type(MLAgentType.CONVERSATIONAL.name())
            .llm(llmSpec)
            .memory(mlMemorySpec)
            .tools(Arrays.asList(firstToolSpec, secondToolSpec))
            .build();
        mlChatAgentRunner.run(mlAgent, new HashMap<>(), agentActionListener);
        Mockito.verify(agentActionListener).onResponse(objectCaptor.capture());
        ModelTensorOutput modelTensorOutput = (ModelTensorOutput) objectCaptor.getValue();
        List<ModelTensor> agentOutput = modelTensorOutput.getMlModelOutputs().get(1).getMlModelTensors();
        assertEquals(1, agentOutput.size());
        // Respond with last tool output
        assertEquals("This is the final answer", agentOutput.get(0).getDataAsMap().get("response"));
    }

    @Test
    public void testRunWithIncludeOutputSet() {
        LLMSpec llmSpec = LLMSpec.builder().modelId("MODEL_ID").build();
        MLToolSpec firstToolSpec = MLToolSpec
            .builder()
            .name(FIRST_TOOL)
            .type(FIRST_TOOL)
            .includeOutputInAgentResponse(false)
            .parameters(ImmutableMap.of("key1", "value1", "key2", "value2"))
            .build();
        MLToolSpec secondToolSpec = MLToolSpec
            .builder()
            .name(SECOND_TOOL)
            .type(SECOND_TOOL)
            .includeOutputInAgentResponse(true)
            .parameters(ImmutableMap.of("key1", "value1", "key2", "value2"))
            .build();
        final MLAgent mlAgent = MLAgent
            .builder()
            .name("TestAgent")
            .type(MLAgentType.CONVERSATIONAL.name())
            .memory(mlMemorySpec)
            .llm(llmSpec)
            .tools(Arrays.asList(firstToolSpec, secondToolSpec))
            .build();
        HashMap<String, String> params = new HashMap<>();
        mlChatAgentRunner.run(mlAgent, params, agentActionListener);
        Mockito.verify(agentActionListener).onResponse(objectCaptor.capture());
        ModelTensorOutput modelTensorOutput = (ModelTensorOutput) objectCaptor.getValue();
        List<ModelTensor> agentOutput = modelTensorOutput.getMlModelOutputs().get(1).getMlModelTensors();
        assertEquals(1, agentOutput.size());
        // Respond with last tool output
        assertEquals("This is the final answer", agentOutput.get(0).getDataAsMap().get("response"));
        Map<String, List<String>> additionalInfos = (Map<String, List<String>>) agentOutput.get(0).getDataAsMap().get("additional_info");
        assertEquals("Second tool response", additionalInfos.get(String.format("%s.output", SECOND_TOOL)).get(0));
    }

    // todo: chat_history is no longer added to inputParams in the runner, modify chat history test cases
    @Test
    public void testChatHistoryExcludeOngoingQuestion() {
        LLMSpec llmSpec = LLMSpec.builder().modelId("MODEL_ID").parameters(Map.of("max_iteration", "2")).build();
        MLToolSpec firstToolSpec = MLToolSpec
            .builder()
            .name(FIRST_TOOL)
            .parameters(Map.of("firsttoolspec", "firsttoolspec"))
            .description("first tool spec")
            .type(FIRST_TOOL)
            .includeOutputInAgentResponse(false)
            .build();
        MLToolSpec secondToolSpec = MLToolSpec
            .builder()
            .name(SECOND_TOOL)
            .parameters(Map.of("secondtoolspec", "secondtoolspec"))
            .description("second tool spec")
            .type(SECOND_TOOL)
            .includeOutputInAgentResponse(true)
            .build();
        final MLAgent mlAgent = MLAgent
            .builder()
            .name("TestAgent")
            .type(MLAgentType.CONVERSATIONAL.name())
            .memory(mlMemorySpec)
            .llm(llmSpec)
            .description("mlagent description")
            .tools(Arrays.asList(firstToolSpec, secondToolSpec))
            .build();

        doAnswer(invocation -> {
            ActionListener<List<Interaction>> listener = invocation.getArgument(0);
            List<Interaction> interactionList = generateInteractions(2);
            Interaction inProgressInteraction = Interaction.builder().id("interaction-99").input("input-99").response(null).build();
            interactionList.add(inProgressInteraction);
            listener.onResponse(interactionList);
            return null;
        }).when(conversationIndexMemory).getMessages(memoryInteractionCapture.capture(), messageHistoryLimitCapture.capture());

        HashMap<String, String> params = new HashMap<>();
        params.put(MESSAGE_HISTORY_LIMIT, "5");
        mlChatAgentRunner.run(mlAgent, params, agentActionListener);
        Mockito.verify(agentActionListener).onResponse(objectCaptor.capture());
        String chatHistory = params.get(MLChatAgentRunner.CHAT_HISTORY);
        Assert.assertFalse(chatHistory.contains("input-99"));
        Assert.assertEquals(5, messageHistoryLimitCapture.getValue().intValue());
    }

    @Test
    public void testChatHistoryWithVerboseMoreInteraction() {
        testInteractions("4");
    }

    private void testInteractions(String maxInteraction) {
        LLMSpec llmSpec = LLMSpec.builder().modelId("MODEL_ID").parameters(Map.of("max_iteration", maxInteraction)).build();
        MLToolSpec firstToolSpec = MLToolSpec
            .builder()
            .name(FIRST_TOOL)
            .parameters(Map.of("firsttoolspec", "firsttoolspec"))
            .description("first tool spec")
            .type(FIRST_TOOL)
            .includeOutputInAgentResponse(false)
            .build();
        MLToolSpec secondToolSpec = MLToolSpec
            .builder()
            .name(SECOND_TOOL)
            .parameters(Map.of("secondtoolspec", "secondtoolspec"))
            .description("second tool spec")
            .type(SECOND_TOOL)
            .includeOutputInAgentResponse(true)
            .build();
        final MLAgent mlAgent = MLAgent
            .builder()
            .name("TestAgent")
            .type(MLAgentType.CONVERSATIONAL.name())
            .memory(mlMemorySpec)
            .llm(llmSpec)
            .description("mlagent description")
            .tools(Arrays.asList(firstToolSpec, secondToolSpec))
            .build();

        doAnswer(invocation -> {
            ActionListener<List<Interaction>> listener = invocation.getArgument(0);
            List<Interaction> interactionList = generateInteractions(2);
            Interaction inProgressInteraction = Interaction.builder().id("interaction-99").input("input-99").response(null).build();
            interactionList.add(inProgressInteraction);
            listener.onResponse(interactionList);
            return null;
        }).when(conversationIndexMemory).getMessages(memoryInteractionCapture.capture(), messageHistoryLimitCapture.capture());

        HashMap<String, String> params = new HashMap<>();
        params.put("verbose", "true");
        mlChatAgentRunner.run(mlAgent, params, agentActionListener);
        Mockito.verify(agentActionListener).onResponse(objectCaptor.capture());
        String chatHistory = params.get(MLChatAgentRunner.CHAT_HISTORY);
        Assert.assertFalse(chatHistory.contains("input-99"));
        Assert.assertEquals(LAST_N_INTERACTIONS, messageHistoryLimitCapture.getValue().intValue());
    }

    @Test
    public void testChatHistoryException() {
        LLMSpec llmSpec = LLMSpec.builder().modelId("MODEL_ID").build();
        MLToolSpec firstToolSpec = MLToolSpec.builder().name(FIRST_TOOL).type(FIRST_TOOL).includeOutputInAgentResponse(false).build();
        MLToolSpec secondToolSpec = MLToolSpec.builder().name(SECOND_TOOL).type(SECOND_TOOL).includeOutputInAgentResponse(true).build();
        final MLAgent mlAgent = MLAgent
            .builder()
            .name("TestAgent")
            .type(MLAgentType.CONVERSATIONAL.name())
            .memory(mlMemorySpec)
            .llm(llmSpec)
            .tools(Arrays.asList(firstToolSpec, secondToolSpec))
            .build();

        doAnswer(invocation -> {

            ActionListener<List<Interaction>> listener = invocation.getArgument(0);
            listener.onFailure(new RuntimeException("Test Exception"));
            return null;
        }).when(conversationIndexMemory).getMessages(memoryInteractionCapture.capture(), messageHistoryLimitCapture.capture());

        HashMap<String, String> params = new HashMap<>();
        mlChatAgentRunner.run(mlAgent, params, agentActionListener);

        // Verifying that onFailure was called
        verify(agentActionListener).onFailure(any(RuntimeException.class));
    }

    @Test
    public void testToolValidationSuccess() {
        // Mock tool validation to return true
        when(firstTool.validate(any())).thenReturn(true);

        // Create an MLAgent with tools
        MLAgent mlAgent = createMLAgentWithTools();

        // Create parameters for the agent
        Map<String, String> params = createAgentParamsWithAction(FIRST_TOOL, "someInput");

        // Run the MLChatAgentRunner
        mlChatAgentRunner.run(mlAgent, params, agentActionListener);

        // Verify that the tool's run method was called
        verify(firstTool).run(any(), any());
    }

    @Test
    public void testToolValidationFailure() {
        // Mock tool validation to return false
        when(firstTool.validate(any())).thenReturn(false);

        // Create an MLAgent with tools
        MLAgent mlAgent = createMLAgentWithTools();

        // Create parameters for the agent
        Map<String, String> params = createAgentParamsWithAction(FIRST_TOOL, "invalidInput");

        Mockito
                .doAnswer(generateToolResponse("First tool response"))
                .when(firstTool)
                .run(Mockito.anyMap(), nextStepListenerCaptor.capture());
        // Run the MLChatAgentRunner
        mlChatAgentRunner.run(mlAgent, params, agentActionListener);

        // Verify that the tool's run method was not called
        verify(firstTool, never()).run(any(), any());

        Mockito.verify(agentActionListener).onResponse(objectCaptor.capture());
        ModelTensorOutput modelTensorOutput = (ModelTensorOutput) objectCaptor.getValue();
        assertNotNull(modelTensorOutput);
    }

    @Test
    public void testToolNotFound() {
        // Create an MLAgent without tools
        LLMSpec llmSpec = LLMSpec.builder().modelId("MODEL_ID").build();
        MLAgent mlAgent = MLAgent
            .builder()
            .type(MLAgentType.CONVERSATIONAL.name())
            .memory(mlMemorySpec)
            .llm(llmSpec)
            .name("TestAgent")
            .build();

        // Create parameters for the agent with a non-existent tool
        Map<String, String> params = createAgentParamsWithAction("nonExistentTool", "someInput");

        // Run the MLChatAgentRunner
        mlChatAgentRunner.run(mlAgent, params, agentActionListener);

        // Verify that no tool's run method was called
        verify(firstTool, never()).run(any(), any());
        verify(secondTool, never()).run(any(), any());
    }

    @Test
    public void testToolFailure() {
        // Mock tool validation to return false
        when(firstTool.validate(any())).thenReturn(true);

        // Create an MLAgent with tools
        MLAgent mlAgent = createMLAgentWithTools();

        // Create parameters for the agent
        Map<String, String> params = createAgentParamsWithAction(FIRST_TOOL, "someInput");

        Mockito
                .doAnswer(generateToolFailure(new IllegalArgumentException("tool error")))
                .when(firstTool)
                .run(Mockito.anyMap(), toolListenerCaptor.capture());
        // Run the MLChatAgentRunner
        mlChatAgentRunner.run(mlAgent, params, agentActionListener);

        // Verify that the tool's run method was called
        verify(firstTool).run(any(), any());

        Mockito.verify(agentActionListener).onResponse(objectCaptor.capture());
        ModelTensorOutput modelTensorOutput = (ModelTensorOutput) objectCaptor.getValue();
        assertNotNull(modelTensorOutput);
    }

    @Test
    public void testToolThrowException() {
        // Mock tool validation to return false
        when(firstTool.validate(any())).thenReturn(true);

        // Create an MLAgent with tools
        MLAgent mlAgent = createMLAgentWithTools();

        // Create parameters for the agent
        Map<String, String> params = createAgentParamsWithAction(FIRST_TOOL, "someInput");

        Mockito
                .doThrow(new IllegalArgumentException("tool error"))
                .when(firstTool)
                .run(Mockito.anyMap(), toolListenerCaptor.capture());
        // Run the MLChatAgentRunner
        mlChatAgentRunner.run(mlAgent, params, agentActionListener);

        // Verify that the tool's run method was called
        verify(firstTool).run(any(), any());

        Mockito.verify(agentActionListener).onResponse(objectCaptor.capture());
        ModelTensorOutput modelTensorOutput = (ModelTensorOutput) objectCaptor.getValue();
        assertNotNull(modelTensorOutput);
    }

    @Test
    public void testToolParameters() {
        // Mock tool validation to return false.
        when(firstTool.validate(any())).thenReturn(true);

        // Create an MLAgent with a tool including two parameters.
        MLAgent mlAgent = createMLAgentWithTools();

        // Create parameters for the agent.
        Map<String, String> params = createAgentParamsWithAction(FIRST_TOOL, "someInput");

        // Run the MLChatAgentRunner.
        mlChatAgentRunner.run(mlAgent, params, agentActionListener);

        // Verify that the tool's run method was called.
        verify(firstTool).run(any(), any());
        // Verify the size of parameters passed in the tool run method.
        ArgumentCaptor argumentCaptor = ArgumentCaptor.forClass(Map.class);
        verify(firstTool).run((Map<String, String>) argumentCaptor.capture(), any());
        assertEquals(15, ((Map) argumentCaptor.getValue()).size());

        Mockito.verify(agentActionListener).onResponse(objectCaptor.capture());
        ModelTensorOutput modelTensorOutput = (ModelTensorOutput) objectCaptor.getValue();
        assertNotNull(modelTensorOutput);
    }

    @Test
    public void testToolUseOriginalInput() {
        // Mock tool validation to return false.
        when(firstTool.validate(any())).thenReturn(true);

        // Create an MLAgent with a tool including two parameters.
        MLAgent mlAgent = createMLAgentWithTools();

        // Create parameters for the agent.
        Map<String, String> params = createAgentParamsWithAction(FIRST_TOOL, "someInput");
        params.put("question", "raw input");
        doReturn(true).when(firstTool).useOriginalInput();

        // Run the MLChatAgentRunner.
        mlChatAgentRunner.run(mlAgent, params, agentActionListener);

        // Verify that the tool's run method was called.
        verify(firstTool).run(any(), any());
        // Verify the size of parameters passed in the tool run method.
        ArgumentCaptor argumentCaptor = ArgumentCaptor.forClass(Map.class);
        verify(firstTool).run((Map<String, String>) argumentCaptor.capture(), any());
        assertEquals(16, ((Map) argumentCaptor.getValue()).size());
        assertEquals("raw input", ((Map<?, ?>) argumentCaptor.getValue()).get("input"));

        Mockito.verify(agentActionListener).onResponse(objectCaptor.capture());
        ModelTensorOutput modelTensorOutput = (ModelTensorOutput) objectCaptor.getValue();
        assertNotNull(modelTensorOutput);
    }

    @Test
    public void testToolConfig() {
        // Mock tool validation to return false.
        when(firstTool.validate(any())).thenReturn(true);

        // Create an MLAgent with a tool including two parameters.
        MLAgent mlAgent = createMLAgentWithToolsConfig(ImmutableMap.of("input", "config_value"));

        // Create parameters for the agent.
        Map<String, String> params = createAgentParamsWithAction(FIRST_TOOL, "someInput");
        params.put("question", "raw input");
        doReturn(false).when(firstTool).useOriginalInput();

        // Run the MLChatAgentRunner.
        mlChatAgentRunner.run(mlAgent, params, agentActionListener);

        // Verify that the tool's run method was called.
        verify(firstTool).run(any(), any());
        // Verify the size of parameters passed in the tool run method.
        ArgumentCaptor argumentCaptor = ArgumentCaptor.forClass(Map.class);
        verify(firstTool).run((Map<String, String>) argumentCaptor.capture(), any());
        assertEquals(16, ((Map) argumentCaptor.getValue()).size());
        // The value of input should be "config_value".
        assertEquals("config_value", ((Map<?, ?>) argumentCaptor.getValue()).get("input"));

        Mockito.verify(agentActionListener).onResponse(objectCaptor.capture());
        ModelTensorOutput modelTensorOutput = (ModelTensorOutput) objectCaptor.getValue();
        assertNotNull(modelTensorOutput);
    }

    @Test
    public void testToolConfigWithInputPlaceholder() {
        // Mock tool validation to return false.
        when(firstTool.validate(any())).thenReturn(true);

        // Create an MLAgent with a tool including two parameters.
        MLAgent mlAgent = createMLAgentWithToolsConfig(ImmutableMap.of("input", "${parameters.key2}"));

        // Create parameters for the agent.
        Map<String, String> params = createAgentParamsWithAction(FIRST_TOOL, "someInput");
        params.put("question", "raw input");
        doReturn(false).when(firstTool).useOriginalInput();

        // Run the MLChatAgentRunner.
        mlChatAgentRunner.run(mlAgent, params, agentActionListener);

        // Verify that the tool's run method was called.
        verify(firstTool).run(any(), any());
        // Verify the size of parameters passed in the tool run method.
        ArgumentCaptor argumentCaptor = ArgumentCaptor.forClass(Map.class);
        verify(firstTool).run((Map<String, String>) argumentCaptor.capture(), any());
        assertEquals(16, ((Map) argumentCaptor.getValue()).size());
        // The value of input should be replaced with the value associated with the key "key2" of the first tool.
        assertEquals("value2", ((Map<?, ?>) argumentCaptor.getValue()).get("input"));

        Mockito.verify(agentActionListener).onResponse(objectCaptor.capture());
        ModelTensorOutput modelTensorOutput = (ModelTensorOutput) objectCaptor.getValue();
        assertNotNull(modelTensorOutput);
    }

    @Test
    public void testSaveLastTraceFailure() {
        // Mock tool validation to return true.
        when(firstTool.validate(any())).thenReturn(true);

        // Create an MLAgent with tools
        MLAgent mlAgent = createMLAgentWithTools();

        // Create parameters for the agent
        Map<String, String> params = createAgentParamsWithAction(FIRST_TOOL, "someInput");

        doAnswer(invocation -> {
            ActionListener<CreateInteractionResponse> listener = invocation.getArgument(4);
            listener.onFailure(new IllegalArgumentException());
            return null;
        }).when(conversationIndexMemory).save(any(), any(), any(), any(), conversationIndexMemoryCapture.capture());
        // Run the MLChatAgentRunner
        mlChatAgentRunner.run(mlAgent, params, agentActionListener);

        // Verify that the tool's run method was called
        verify(firstTool).run(any(), any());

        Mockito.verify(agentActionListener).onFailure(any(IllegalArgumentException.class));
    }

    @Test
    public void testToolExecutionWithChatHistoryParameter() {
        LLMSpec llmSpec = LLMSpec.builder().modelId("MODEL_ID").parameters(Map.of("max_iteration", "2")).build();
        MLToolSpec firstToolSpec = MLToolSpec
            .builder()
            .name(FIRST_TOOL)
            .parameters(Map.of("firsttoolspec", "firsttoolspec"))
            .description("first tool spec")
            .type(FIRST_TOOL)
            .includeOutputInAgentResponse(false)
            .build();
        MLToolSpec secondToolSpec = MLToolSpec
            .builder()
            .name(SECOND_TOOL)
            .parameters(Map.of("secondtoolspec", "secondtoolspec"))
            .description("second tool spec")
            .type(SECOND_TOOL)
            .includeOutputInAgentResponse(true)
            .build();
        final MLAgent mlAgent = MLAgent
            .builder()
            .name("TestAgent")
            .type(MLAgentType.CONVERSATIONAL.name())
            .memory(mlMemorySpec)
            .llm(llmSpec)
            .description("mlagent description")
            .tools(Arrays.asList(firstToolSpec, secondToolSpec))
            .build();

        doAnswer(invocation -> {
            ActionListener<List<Interaction>> listener = invocation.getArgument(0);
            List<Interaction> interactionList = generateInteractions(2);
            Interaction inProgressInteraction = Interaction.builder().id("interaction-99").input("input-99").response(null).build();
            interactionList.add(inProgressInteraction);
            listener.onResponse(interactionList);
            return null;
        }).when(conversationIndexMemory).getMessages(memoryInteractionCapture.capture(), messageHistoryLimitCapture.capture());

        doAnswer(generateToolResponse("First tool response"))
            .when(firstTool)
            .run(toolParamsCapture.capture(), toolListenerCaptor.capture());

        HashMap<String, String> params = new HashMap<>();
        params.put(MESSAGE_HISTORY_LIMIT, "5");
        mlChatAgentRunner.run(mlAgent, params, agentActionListener);
        Mockito.verify(agentActionListener).onResponse(objectCaptor.capture());
        String chatHistory = params.get(MLChatAgentRunner.CHAT_HISTORY);
        Assert.assertFalse(chatHistory.contains("input-99"));
        Assert.assertEquals(5, messageHistoryLimitCapture.getValue().intValue());
        Assert.assertTrue(toolParamsCapture.getValue().containsKey(MLChatAgentRunner.CHAT_HISTORY));
    }

    // Helper methods to create MLAgent and parameters
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
            .build();
    }

    private MLAgent createMLAgentWithToolsConfig(Map<String, String> configMap) {
        LLMSpec llmSpec = LLMSpec.builder().modelId("MODEL_ID").build();
        MLToolSpec firstToolSpec = MLToolSpec
            .builder()
            .name(FIRST_TOOL)
            .type(FIRST_TOOL)
            .parameters(ImmutableMap.of("key1", "value1", "key2", "value2"))
            .configMap(configMap)
            .build();
        return MLAgent
            .builder()
            .name("TestAgent")
            .type(MLAgentType.CONVERSATIONAL.name())
            .tools(Arrays.asList(firstToolSpec))
            .memory(mlMemorySpec)
            .llm(llmSpec)
            .build();
    }

    private Map<String, String> createAgentParamsWithAction(String action, String actionInput) {
        Map<String, String> params = new HashMap<>();
        params.put("action", action);
        params.put("action_input", actionInput);
        return params;
    }

    private List<Interaction> generateInteractions(int size) {
        return IntStream
            .range(1, size + 1)
            .mapToObj(i -> Interaction.builder().id("interaction-" + i).input("input-" + i).response("response-" + i).build())
            .collect(Collectors.toList());
    }

    private Answer getLLMAnswer(Map<String, String> llmResponse) {
        return invocation -> {
            ActionListener<Object> listener = invocation.getArgument(2);
            ModelTensor modelTensor = ModelTensor.builder().dataAsMap(llmResponse).build();
            ModelTensors modelTensors = ModelTensors.builder().mlModelTensors(Arrays.asList(modelTensor)).build();
            ModelTensorOutput mlModelTensorOutput = ModelTensorOutput.builder().mlModelOutputs(Arrays.asList(modelTensors)).build();
            MLTaskResponse mlTaskResponse = MLTaskResponse.builder().output(mlModelTensorOutput).build();
            listener.onResponse(mlTaskResponse);
            return null;
        };
    }

    private Answer generateToolResponse(String response) {
        return invocation -> {
            ActionListener<Object> listener = invocation.getArgument(1);
            listener.onResponse(response);
            return null;
        };
    }

    private Answer generateToolResponseAsMLModelResult(String response, int type) {
        ModelTensor modelTensor;
        if (type == 1) {
            modelTensor = ModelTensor.builder().dataAsMap(ImmutableMap.of("return", response)).build();
        } else {
            modelTensor = ModelTensor.builder().result(response).build();
        }
        ModelTensors modelTensors = ModelTensors.builder().mlModelTensors(Arrays.asList(modelTensor)).build();
        ModelTensorOutput mlModelTensorOutput = ModelTensorOutput.builder().mlModelOutputs(Arrays.asList(modelTensors)).build();

        return invocation -> {
            ActionListener<Object> listener = invocation.getArgument(1);
            listener.onResponse(mlModelTensorOutput);
            return null;
        };
    }

    private Answer generateToolFailure(Exception e) {
        return invocation -> {
            ActionListener<Object> listener = invocation.getArgument(1);
            listener.onFailure(e);
            return null;
        };
    }

    @Test
    public void testMaxIterationsReached() {
        // Create LLM spec with max_iteration = 1 to force max iterations
        LLMSpec llmSpec = LLMSpec.builder().modelId("MODEL_ID").parameters(Map.of("max_iteration", "1")).build();
        MLToolSpec firstToolSpec = MLToolSpec.builder().name(FIRST_TOOL).type(FIRST_TOOL).build();
        final MLAgent mlAgent = MLAgent
            .builder()
            .name("TestAgent")
            .type(MLAgentType.CONVERSATIONAL.name())
            .llm(llmSpec)
            .memory(mlMemorySpec)
            .tools(Arrays.asList(firstToolSpec))
            .build();

        // Mock LLM response that doesn't contain final_answer to force max iterations
        Mockito
            .doAnswer(getLLMAnswer(ImmutableMap.of("thought", "", "action", FIRST_TOOL)))
            .when(client)
            .execute(any(ActionType.class), any(ActionRequest.class), isA(ActionListener.class));

        Map<String, String> params = new HashMap<>();
        params.put(MLAgentExecutor.PARENT_INTERACTION_ID, "parent_interaction_id");

        mlChatAgentRunner.run(mlAgent, params, agentActionListener);

        // Verify response is captured
        verify(agentActionListener).onResponse(objectCaptor.capture());
        Object capturedResponse = objectCaptor.getValue();
        assertTrue(capturedResponse instanceof ModelTensorOutput);

        ModelTensorOutput modelTensorOutput = (ModelTensorOutput) capturedResponse;
        List<ModelTensor> agentOutput = modelTensorOutput.getMlModelOutputs().get(1).getMlModelTensors();
        assertEquals(1, agentOutput.size());

        // Verify the response contains max iterations message
        String response = (String) agentOutput.get(0).getDataAsMap().get("response");
        assertEquals("Agent reached maximum iterations (1) without completing the task", response);
    }

    @Test
    public void testMaxIterationsReachedWithValidThought() {
        // Create LLM spec with max_iteration = 1 to force max iterations
        LLMSpec llmSpec = LLMSpec.builder().modelId("MODEL_ID").parameters(Map.of("max_iteration", "1")).build();
        MLToolSpec firstToolSpec = MLToolSpec.builder().name(FIRST_TOOL).type(FIRST_TOOL).build();
        final MLAgent mlAgent = MLAgent
            .builder()
            .name("TestAgent")
            .type(MLAgentType.CONVERSATIONAL.name())
            .llm(llmSpec)
            .memory(mlMemorySpec)
            .tools(Arrays.asList(firstToolSpec))
            .build();

        // Mock LLM response with valid thought
        Mockito
            .doAnswer(getLLMAnswer(ImmutableMap.of("thought", "I need to use the first tool", "action", FIRST_TOOL)))
            .when(client)
            .execute(any(ActionType.class), any(ActionRequest.class), isA(ActionListener.class));

        Map<String, String> params = new HashMap<>();
        params.put(MLAgentExecutor.PARENT_INTERACTION_ID, "parent_interaction_id");

        mlChatAgentRunner.run(mlAgent, params, agentActionListener);

        // Verify response is captured
        verify(agentActionListener).onResponse(objectCaptor.capture());
        Object capturedResponse = objectCaptor.getValue();
        assertTrue(capturedResponse instanceof ModelTensorOutput);

        ModelTensorOutput modelTensorOutput = (ModelTensorOutput) capturedResponse;
        List<ModelTensor> agentOutput = modelTensorOutput.getMlModelOutputs().get(1).getMlModelTensors();
        assertEquals(1, agentOutput.size());

        // Verify the response contains the last valid thought instead of max iterations message
        String response = (String) agentOutput.get(0).getDataAsMap().get("response");
        assertEquals(
            "Agent reached maximum iterations (1) without completing the task. Last thought: I need to use the first tool",
            response
        );
    }

    @Test
    public void testConstructLLMParams_WithSystemPromptAndDateTimeInjection() {
        LLMSpec llmSpec = LLMSpec.builder().modelId("MODEL_ID").build();
        Map<String, String> parameters = new HashMap<>();
        parameters.put(MLChatAgentRunner.SYSTEM_PROMPT_FIELD, "You are a helpful assistant.");
        parameters.put(MLChatAgentRunner.INJECT_DATETIME_FIELD, "true");

        Map<String, String> result = MLChatAgentRunner.constructLLMParams(llmSpec, parameters);

        Assert.assertNotNull(result);
        Assert.assertTrue(result.containsKey(MLChatAgentRunner.SYSTEM_PROMPT_FIELD));
        String systemPrompt = result.get(MLChatAgentRunner.SYSTEM_PROMPT_FIELD);
        Assert.assertTrue(systemPrompt.startsWith("You are a helpful assistant."));
        Assert.assertTrue(systemPrompt.contains(DEFAULT_DATETIME_PREFIX));
    }

    @Test
    public void testConstructLLMParams_WithoutSystemPromptAndDateTimeInjection() {
        LLMSpec llmSpec = LLMSpec.builder().modelId("MODEL_ID").build();
        Map<String, String> parameters = new HashMap<>();
        parameters.put(MLChatAgentRunner.INJECT_DATETIME_FIELD, "true");

        Map<String, String> result = MLChatAgentRunner.constructLLMParams(llmSpec, parameters);

        Assert.assertNotNull(result);
        Assert.assertTrue(result.containsKey(AgentUtils.PROMPT_PREFIX));
        String promptPrefix = result.get(AgentUtils.PROMPT_PREFIX);
        Assert.assertTrue(promptPrefix.contains(DEFAULT_DATETIME_PREFIX));
    }

    @Test
    public void testConstructLLMParams_DateTimeInjectionDisabled() {
        LLMSpec llmSpec = LLMSpec.builder().modelId("MODEL_ID").build();
        Map<String, String> parameters = new HashMap<>();
        parameters.put(MLChatAgentRunner.INJECT_DATETIME_FIELD, "false");
        parameters.put(MLChatAgentRunner.SYSTEM_PROMPT_FIELD, "You are a helpful assistant.");

        Map<String, String> result = MLChatAgentRunner.constructLLMParams(llmSpec, parameters);

        Assert.assertNotNull(result);
        Assert.assertTrue(result.containsKey(MLChatAgentRunner.SYSTEM_PROMPT_FIELD));
        String systemPrompt = result.get(MLChatAgentRunner.SYSTEM_PROMPT_FIELD);
        Assert.assertEquals("You are a helpful assistant.", systemPrompt);
        Assert.assertFalse(systemPrompt.contains(DEFAULT_DATETIME_PREFIX));
    }

    @Test
    public void testConstructLLMParams_DefaultValues() {
        LLMSpec llmSpec = LLMSpec.builder().modelId("MODEL_ID").build();
        Map<String, String> parameters = new HashMap<>();

        Map<String, String> result = MLChatAgentRunner.constructLLMParams(llmSpec, parameters);

        Assert.assertNotNull(result);
        Assert.assertTrue(result.containsKey(AgentUtils.PROMPT_PREFIX));
        Assert.assertTrue(result.containsKey(AgentUtils.PROMPT_SUFFIX));
        Assert.assertTrue(result.containsKey(AgentUtils.RESPONSE_FORMAT_INSTRUCTION));
        Assert.assertTrue(result.containsKey(AgentUtils.TOOL_RESPONSE));
    }

    // Tests for BedrockAgentCoreMemory integration - simplified to test specific branches
    @Test
    public void testRunWithBedrockAgentCoreMemory() {
        // This test verifies that the BedrockAgentCoreMemory branch is executed
        // Setup BedrockAgentCoreMemory
        MLMemorySpec bedrockMemorySpec = new MLMemorySpec("bedrock_agentcore_memory", "memory-id", 10);
        when(memoryMap.get("bedrock_agentcore_memory")).thenReturn(bedrockMemoryFactory);

        // Mock the factory to fail immediately so we can verify the branch was taken
        doAnswer(invocation -> {
            ActionListener<BedrockAgentCoreMemory> listener = invocation.getArgument(1);
            listener.onFailure(new RuntimeException("BedrockAgentCoreMemory branch executed"));
            return null;
        }).when(bedrockMemoryFactory).create(any(), any());

        // Create MLAgent with BedrockAgentCoreMemory
        LLMSpec llmSpec = LLMSpec.builder().modelId("MODEL_ID").build();
        MLAgent mlAgent = MLAgent
            .builder()
            .name("TestAgent")
            .type(MLAgentType.CONVERSATIONAL.name())
            .llm(llmSpec)
            .memory(bedrockMemorySpec)
            .build();

        Map<String, String> params = new HashMap<>();
        params.put("memory_type", "bedrock_agentcore_memory");
        params.put("memory_arn", "arn:aws:bedrock:us-east-1:123456789012:memory/test-memory");
        params.put("memory_region", "us-east-1");
        params.put("agent_id", "test-agent-id");
        params.put(MLAgentExecutor.MEMORY_ID, "test-session-id");

        mlChatAgentRunner.run(mlAgent, params, agentActionListener);

        // Verify that the BedrockAgentCoreMemory branch was executed
        verify(agentActionListener).onFailure(any(RuntimeException.class));
    }

    @Test
    public void testSaveTraceDataWithBedrockAgentCoreMemory() {
        // Create a mock BedrockAgentCoreMemory
        BedrockAgentCoreMemory mockMemory = Mockito.mock(BedrockAgentCoreMemory.class);

        doAnswer(invocation -> {
            ActionListener<String> listener = invocation.getArgument(2);
            listener.onResponse("saved-event-id");
            return null;
        }).when(mockMemory).save(anyString(), any(BedrockAgentCoreMemoryRecord.class), any(ActionListener.class));

        // Test saveTraceData static method
        MLChatAgentRunner
            .saveTraceData(
                mockMemory,
                "bedrock_agentcore_memory",
                "test question",
                "test response",
                "test-session-id",
                false, // traceDisabled = false
                "parent-interaction-id",
                new AtomicInteger(1),
                "LLM"
            );

        // Verify save was called
        verify(mockMemory).save(eq("test-session-id"), any(BedrockAgentCoreMemoryRecord.class), any(ActionListener.class));
    }

    @Test
    public void testSaveTraceDataWithBedrockAgentCoreMemoryTraceDisabled() {
        // Create a mock BedrockAgentCoreMemory
        BedrockAgentCoreMemory mockMemory = Mockito.mock(BedrockAgentCoreMemory.class);

        // Test saveTraceData static method with trace disabled
        MLChatAgentRunner
            .saveTraceData(
                mockMemory,
                "bedrock_agentcore_memory",
                "test question",
                "test response",
                "test-session-id",
                true, // traceDisabled = true
                "parent-interaction-id",
                new AtomicInteger(1),
                "LLM"
            );

        // Verify save was NOT called when trace is disabled
        verify(mockMemory, never()).save(anyString(), any(BedrockAgentCoreMemoryRecord.class), any(ActionListener.class));
    }

    @Test
    public void testBedrockAgentCoreMemoryFactoryCreationFailure() {
        // Test lambda$run$5 (factory error handling)
        MLMemorySpec bedrockMemorySpec = new MLMemorySpec("bedrock_agentcore_memory", "memory-id", 10);
        when(memoryMap.get("bedrock_agentcore_memory")).thenReturn(bedrockMemoryFactory);

        // Make factory creation fail to trigger error handling lambda
        doAnswer(invocation -> {
            ActionListener<BedrockAgentCoreMemory> listener = invocation.getArgument(1);
            listener.onFailure(new RuntimeException("Factory creation failed"));
            return null;
        }).when(bedrockMemoryFactory).create(any(), any());

        // Create MLAgent with BedrockAgentCoreMemory
        LLMSpec llmSpec = LLMSpec.builder().modelId("MODEL_ID").build();
        MLAgent mlAgent = MLAgent
            .builder()
            .name("TestAgent")
            .type(MLAgentType.CONVERSATIONAL.name())
            .llm(llmSpec)
            .memory(bedrockMemorySpec)
            .build();

        Map<String, String> params = new HashMap<>();
        params.put("memory_type", "bedrock_agentcore_memory");
        params.put("memory_arn", "arn:aws:bedrock:us-east-1:123456789012:memory/test-memory");
        params.put("memory_region", "us-east-1");
        params.put("agent_id", "test-agent-id");

        mlChatAgentRunner.run(mlAgent, params, agentActionListener);

        // Verify failure is propagated through the error handling lambda
        verify(agentActionListener).onFailure(any(RuntimeException.class));
    }

    @Test
    public void testRestoreBedrockMemoryConfigWithCachedConfig() {
        // Test restoreBedrockMemoryConfig method with cached configuration
        MLMemorySpec bedrockMemorySpec = new MLMemorySpec("bedrock_agentcore_memory", "memory-id", 10);

        // First, cache some configuration by running with parameters
        Map<String, String> initialParams = new HashMap<>();
        initialParams.put("memory_type", "bedrock_agentcore_memory");
        initialParams.put("memory_arn", "arn:aws:bedrock:us-east-1:123456789012:memory/test-memory");
        initialParams.put("memory_region", "us-east-1");
        initialParams.put("agent_id", "test-agent-id");

        LLMSpec llmSpec = LLMSpec.builder().modelId("MODEL_ID").build();
        MLAgent mlAgent = MLAgent
            .builder()
            .name("TestAgentForCache")
            .type(MLAgentType.CONVERSATIONAL.name())
            .llm(llmSpec)
            .memory(bedrockMemorySpec)
            .build();

        when(memoryMap.get("bedrock_agentcore_memory")).thenReturn(bedrockMemoryFactory);
        doAnswer(invocation -> {
            ActionListener<BedrockAgentCoreMemory> listener = invocation.getArgument(1);
            listener.onResponse(bedrockAgentCoreMemory);
            return null;
        }).when(bedrockMemoryFactory).create(any(), any());

        // First call to cache the configuration
        mlChatAgentRunner.run(mlAgent, initialParams, agentActionListener);

        // Now test restore scenario - agent has bedrock memory type but no parameters
        Map<String, String> emptyParams = new HashMap<>();
        emptyParams.put("agent_id", "test-agent-id"); // Still need agent_id

        // This should trigger restoreBedrockMemoryConfig
        mlChatAgentRunner.run(mlAgent, emptyParams, agentActionListener);

        // Verify the method was called (indirectly by checking factory was called again)
        verify(bedrockMemoryFactory, Mockito.atLeast(2)).create(any(), any());
    }

    @Test
    public void testRestoreBedrockMemoryConfigWithNoCachedConfig() {
        // Test restoreBedrockMemoryConfig method with no cached configuration
        MLMemorySpec bedrockMemorySpec = new MLMemorySpec("bedrock_agentcore_memory", "memory-id", 10);

        LLMSpec llmSpec = LLMSpec.builder().modelId("MODEL_ID").build();
        MLAgent mlAgent = MLAgent
            .builder()
            .name("TestAgentNoCache")
            .type(MLAgentType.CONVERSATIONAL.name())
            .llm(llmSpec)
            .memory(bedrockMemorySpec)
            .build();

        when(memoryMap.get("bedrock_agentcore_memory")).thenReturn(bedrockMemoryFactory);

        Map<String, String> emptyParams = new HashMap<>();
        emptyParams.put("agent_id", "test-agent-id");

        // This should trigger restoreBedrockMemoryConfig with no cached config
        mlChatAgentRunner.run(mlAgent, emptyParams, agentActionListener);

        // Should still attempt to create factory even with missing parameters
        verify(bedrockMemoryFactory).create(any(), any());
    }

    @Test
    public void testConversationIndexMemoryGetMessagesFailure() {
        // Test lambda$run$3 (conversation memory error handling) - simplified version
        when(memoryMap.get("conversation_index")).thenReturn(memoryFactory);
        
        // Make factory creation fail to trigger error handling
        doAnswer(invocation -> {
            ActionListener<ConversationIndexMemory> listener = invocation.getArgument(3);
            listener.onFailure(new RuntimeException("Factory creation failed"));
            return null;
        }).when(memoryFactory).create(any(), any(), any(), any());

        LLMSpec llmSpec = LLMSpec.builder().modelId("MODEL_ID").build();
        MLMemorySpec memorySpec = new MLMemorySpec("conversation_index", "memory-id", 10);
        MLAgent mlAgent = MLAgent
            .builder()
            .name("TestAgent")
            .type(MLAgentType.CONVERSATIONAL.name())
            .llm(llmSpec)
            .memory(memorySpec)
            .build();

        Map<String, String> params = new HashMap<>();
        params.put(MLAgentExecutor.QUESTION, "test question");

        mlChatAgentRunner.run(mlAgent, params, agentActionListener);

        // Verify failure is propagated through the error handling lambda
        verify(agentActionListener).onFailure(any(RuntimeException.class));
    }

    @Test
    public void testUnsupportedMemoryFactoryType() {
        // Test unsupported memory factory type error handling
        Memory.Factory unsupportedFactory = Mockito.mock(Memory.Factory.class); // Mock the interface instead
        when(memoryMap.get("unsupported_memory")).thenReturn(unsupportedFactory);

        LLMSpec llmSpec = LLMSpec.builder().modelId("MODEL_ID").build();
        MLMemorySpec memorySpec = new MLMemorySpec("unsupported_memory", "memory-id", 10);
        MLAgent mlAgent = MLAgent
            .builder()
            .name("TestAgent")
            .type(MLAgentType.CONVERSATIONAL.name())
            .llm(llmSpec)
            .memory(memorySpec)
            .build();

        Map<String, String> params = new HashMap<>();
        params.put(MLAgentExecutor.QUESTION, "test question");

        mlChatAgentRunner.run(mlAgent, params, agentActionListener);

        // Verify failure with unsupported memory factory type
        ArgumentCaptor<Exception> exceptionCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(agentActionListener).onFailure(exceptionCaptor.capture());
        assertTrue(exceptionCaptor.getValue() instanceof IllegalArgumentException);
        assertTrue(exceptionCaptor.getValue().getMessage().contains("Unsupported memory factory type"));
    }

    @Test
    public void testBedrockMemoryWithExecutorMemoryId() {
        // Test branch: executor_memory_id != null (line 256)
        MLMemorySpec bedrockMemorySpec = new MLMemorySpec("bedrock_agentcore_memory", "memory-id", 10);
        when(memoryMap.get("bedrock_agentcore_memory")).thenReturn(bedrockMemoryFactory);

        doAnswer(invocation -> {
            ActionListener<BedrockAgentCoreMemory> listener = invocation.getArgument(1);
            listener.onResponse(bedrockAgentCoreMemory);
            return null;
        }).when(bedrockMemoryFactory).create(any(), any());

        LLMSpec llmSpec = LLMSpec.builder().modelId("MODEL_ID").build();
        MLAgent mlAgent = MLAgent
            .builder()
            .name("TestAgent")
            .type(MLAgentType.CONVERSATIONAL.name())
            .llm(llmSpec)
            .memory(bedrockMemorySpec)
            .build();

        Map<String, String> params = new HashMap<>();
        params.put("memory_type", "bedrock_agentcore_memory");
        params.put("memory_arn", "arn:aws:bedrock:us-east-1:123456789012:memory/test-memory");
        params.put("memory_region", "us-east-1");
        params.put("agent_id", "test-agent-id");
        params.put("executor_memory_id", "executor-session-123"); // This should be used instead of memoryId

        mlChatAgentRunner.run(mlAgent, params, agentActionListener);

        // Verify factory was called
        verify(bedrockMemoryFactory).create(any(), any());
    }

    @Test
    public void testBedrockMemoryWithAllCredentials() {
        // Test all credential branches (lines 279, 282, 285, 288)
        MLMemorySpec bedrockMemorySpec = new MLMemorySpec("bedrock_agentcore_memory", "memory-id", 10);
        when(memoryMap.get("bedrock_agentcore_memory")).thenReturn(bedrockMemoryFactory);

        doAnswer(invocation -> {
            ActionListener<BedrockAgentCoreMemory> listener = invocation.getArgument(1);
            listener.onResponse(bedrockAgentCoreMemory);
            return null;
        }).when(bedrockMemoryFactory).create(any(), any());

        LLMSpec llmSpec = LLMSpec.builder().modelId("MODEL_ID").build();
        MLAgent mlAgent = MLAgent
            .builder()
            .name("TestAgent")
            .type(MLAgentType.CONVERSATIONAL.name())
            .llm(llmSpec)
            .memory(bedrockMemorySpec)
            .build();

        Map<String, String> params = new HashMap<>();
        params.put("memory_type", "bedrock_agentcore_memory");
        params.put("memory_arn", "arn:aws:bedrock:us-east-1:123456789012:memory/test-memory");
        params.put("memory_region", "us-east-1");
        params.put("agent_id", "test-agent-id");
        params.put("memory_access_key", "test-access-key");
        params.put("memory_secret_key", "test-secret-key");
        params.put("memory_session_token", "test-session-token");

        mlChatAgentRunner.run(mlAgent, params, agentActionListener);

        // Verify factory was called - this covers the credentials branches
        verify(bedrockMemoryFactory).create(any(), any());
    }

    @Test
    public void testBedrockMemoryWithNullAgentId() {
        // Test branch: agentIdToUse == null (line 269)
        MLMemorySpec bedrockMemorySpec = new MLMemorySpec("bedrock_agentcore_memory", "memory-id", 10);
        when(memoryMap.get("bedrock_agentcore_memory")).thenReturn(bedrockMemoryFactory);

        LLMSpec llmSpec = LLMSpec.builder().modelId("MODEL_ID").build();
        MLAgent mlAgent = MLAgent
            .builder()
            .name("TestAgent")
            .type(MLAgentType.CONVERSATIONAL.name())
            .llm(llmSpec)
            .memory(bedrockMemorySpec)
            .build();

        Map<String, String> params = new HashMap<>();
        params.put("memory_type", "bedrock_agentcore_memory");
        params.put("memory_arn", "arn:aws:bedrock:us-east-1:123456789012:memory/test-memory");
        params.put("memory_region", "us-east-1");
        // No agent_id parameter - should trigger the null check

        // Expect IllegalArgumentException to be thrown directly
        assertThrows(IllegalArgumentException.class, () -> { mlChatAgentRunner.run(mlAgent, params, agentActionListener); });
    }

    @Test
    public void testAgentWithParameters() {
        // Test branch: mlAgent.getParameters() != null (line 364)
        when(memoryMap.get("conversation_index")).thenReturn(memoryFactory);
        
        doAnswer(invocation -> {
            ActionListener<ConversationIndexMemory> listener = invocation.getArgument(3);
            listener.onResponse(conversationIndexMemory);
            return null;
        }).when(memoryFactory).create(any(), any(), any(), any());

        LLMSpec llmSpec = LLMSpec.builder().modelId("MODEL_ID").build();
        MLMemorySpec memorySpec = new MLMemorySpec("conversation_index", "memory-id", 10);
        
        // Create agent with parameters
        Map<String, String> agentParams = new HashMap<>();
        agentParams.put("_test_param", "test_value");
        agentParams.put("normal_param", "normal_value");
        
        MLAgent mlAgent = MLAgent
            .builder()
            .name("TestAgent")
            .type(MLAgentType.CONVERSATIONAL.name())
            .llm(llmSpec)
            .memory(memorySpec)
            .parameters(agentParams)
            .build();

        Map<String, String> params = new HashMap<>();
        params.put(MLAgentExecutor.QUESTION, "test question");

        mlChatAgentRunner.run(mlAgent, params, agentActionListener);

        verify(memoryFactory).create(any(), any(), any(), any());
    }

    @Test
    public void testBedrockMemoryWithCredentials() {
        // Test BedrockAgentCoreMemory with credentials parameters
        MLMemorySpec bedrockMemorySpec = new MLMemorySpec("bedrock_agentcore_memory", "memory-id", 10);
        when(memoryMap.get("bedrock_agentcore_memory")).thenReturn(bedrockMemoryFactory);

        doAnswer(invocation -> {
            ActionListener<BedrockAgentCoreMemory> listener = invocation.getArgument(1);
            listener.onResponse(bedrockAgentCoreMemory);
            return null;
        }).when(bedrockMemoryFactory).create(any(), any());

        LLMSpec llmSpec = LLMSpec.builder().modelId("MODEL_ID").build();
        MLAgent mlAgent = MLAgent
            .builder()
            .name("TestAgent")
            .type(MLAgentType.CONVERSATIONAL.name())
            .llm(llmSpec)
            .memory(bedrockMemorySpec)
            .build();

        Map<String, String> params = new HashMap<>();
        params.put("memory_type", "bedrock_agentcore_memory");
        params.put("memory_arn", "arn:aws:bedrock:us-east-1:123456789012:memory/test-memory");
        params.put("memory_region", "us-east-1");
        params.put("agent_id", "test-agent-id");
        params.put("memory_access_key", "test-access-key");
        params.put("memory_secret_key", "test-secret-key");
        params.put("memory_session_token", "test-session-token");

        mlChatAgentRunner.run(mlAgent, params, agentActionListener);

        // Verify factory was called with credentials
        ArgumentCaptor<Map<String, Object>> paramsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(bedrockMemoryFactory).create(paramsCaptor.capture(), any());

        Map<String, Object> capturedParams = paramsCaptor.getValue();
        assertNotNull(capturedParams.get("credentials"));
        Map<String, String> credentials = (Map<String, String>) capturedParams.get("credentials");
        assertEquals("test-access-key", credentials.get("access_key"));
        assertEquals("test-secret-key", credentials.get("secret_key"));
        assertEquals("test-session-token", credentials.get("session_token"));
    }

    @Test
    public void testConversationMemoryWithTemplatesAndMessages() {
        // Target uncovered branches in lines 217-218 (chatHistoryQuestionTemplate != null path)
        when(memoryMap.get("conversation_index")).thenReturn(memoryFactory);
        
        doAnswer(invocation -> {
            ActionListener<ConversationIndexMemory> listener = invocation.getArgument(3);
            ConversationIndexMemory mockMemory = Mockito.mock(ConversationIndexMemory.class);
            when(mockMemory.getConversationId()).thenReturn("test-conversation-id");
            
            doAnswer(msgInvocation -> {
                ActionListener<List<Interaction>> msgListener = msgInvocation.getArgument(0);
                List<Interaction> interactions = Arrays.asList(
                    Interaction.builder().id("interaction-1").input("test question").response("test response").build()
                );
                msgListener.onResponse(interactions);
                return null;
            }).when(mockMemory).getMessages(any(), any(Integer.class));
            
            listener.onResponse(mockMemory);
            return null;
        }).when(memoryFactory).create(any(), any(), any(), any());

        LLMSpec llmSpec = LLMSpec.builder().modelId("MODEL_ID").build();
        MLMemorySpec memorySpec = new MLMemorySpec("conversation_index", "memory-id", 10);
        MLAgent mlAgent = MLAgent
            .builder()
            .name("TestAgent")
            .type(MLAgentType.CONVERSATIONAL.name())
            .llm(llmSpec)
            .memory(memorySpec)
            .build();

        Map<String, String> params = new HashMap<>();
        params.put(MLAgentExecutor.QUESTION, "test question");
        params.put(MLChatAgentRunner.CHAT_HISTORY_QUESTION_TEMPLATE, "Q: ${_chat_history.message.question}");
        params.put(MLChatAgentRunner.CHAT_HISTORY_RESPONSE_TEMPLATE, "A: ${_chat_history.message.response}");

        mlChatAgentRunner.run(mlAgent, params, agentActionListener);

        verify(memoryFactory).create(any(), any(), any(), any());
    }

    @Test
    public void testAgentParametersWithUnderscoreKeys() {
        // Target uncovered branches in lines 366-367 (parameter key iteration and underscore check)
        when(memoryMap.get("conversation_index")).thenReturn(memoryFactory);
        
        doAnswer(invocation -> {
            ActionListener<ConversationIndexMemory> listener = invocation.getArgument(3);
            listener.onResponse(conversationIndexMemory);
            return null;
        }).when(memoryFactory).create(any(), any(), any(), any());

        LLMSpec llmSpec = LLMSpec.builder().modelId("MODEL_ID").build();
        MLMemorySpec memorySpec = new MLMemorySpec("conversation_index", "memory-id", 10);
        
        // Create agent with parameters including underscore keys
        Map<String, String> agentParams = new HashMap<>();
        agentParams.put("_underscore_param", "underscore_value");
        agentParams.put("normal_param", "normal_value");
        
        MLAgent mlAgent = MLAgent
            .builder()
            .name("TestAgent")
            .type(MLAgentType.CONVERSATIONAL.name())
            .llm(llmSpec)
            .memory(memorySpec)
            .parameters(agentParams)
            .build();

        Map<String, String> params = new HashMap<>();
        params.put(MLAgentExecutor.QUESTION, "test question");

        mlChatAgentRunner.run(mlAgent, params, agentActionListener);

        verify(memoryFactory).create(any(), any(), any(), any());
    }

    @Test
    public void testUnsupportedMemoryFactoryWithNullCheck() {
        // Target uncovered branch in line 356 (null check in error message)
        when(memoryMap.get("unsupported_memory")).thenReturn(null);
        
        LLMSpec llmSpec = LLMSpec.builder().modelId("MODEL_ID").build();
        MLMemorySpec memorySpec = new MLMemorySpec("unsupported_memory", "memory-id", 10);
        MLAgent mlAgent = MLAgent
            .builder()
            .name("TestAgent")
            .type(MLAgentType.CONVERSATIONAL.name())
            .llm(llmSpec)
            .memory(memorySpec)
            .build();

        Map<String, String> params = new HashMap<>();
        params.put(MLAgentExecutor.QUESTION, "test question");

        mlChatAgentRunner.run(mlAgent, params, agentActionListener);

        verify(agentActionListener).onFailure(any(IllegalArgumentException.class));
    }

    @Test
    public void testBedrockMemoryWithInteractionsProcessing() {
        // Target uncovered branches in BedrockAgentCoreMemory message processing (lines 296, 300, 316, 317, 319)
        MLMemorySpec bedrockMemorySpec = new MLMemorySpec("bedrock_agentcore_memory", "memory-id", 10);
        when(memoryMap.get("bedrock_agentcore_memory")).thenReturn(bedrockMemoryFactory);

        doAnswer(invocation -> {
            ActionListener<BedrockAgentCoreMemory> listener = invocation.getArgument(1);
            BedrockAgentCoreMemory mockMemory = Mockito.mock(BedrockAgentCoreMemory.class);
            when(mockMemory.getConversationId()).thenReturn("bedrock-session-id");

            doAnswer(msgInvocation -> {
                ActionListener<List<Interaction>> msgListener = msgInvocation.getArgument(0);
                List<Interaction> interactions = Arrays
                    .asList(
                        Interaction.builder().id("interaction-1").input("bedrock question").response("bedrock response").build(),
                        Interaction.builder().id("interaction-2").input("empty response question").response("").build() // This triggers
                                                                                                                        // empty response
                                                                                                                        // branch
                    );
                msgListener.onResponse(interactions);
                return null;
            }).when(mockMemory).getMessages(any(ActionListener.class));

            listener.onResponse(mockMemory);
            return null;
        }).when(bedrockMemoryFactory).create(any(), any());

        LLMSpec llmSpec = LLMSpec.builder().modelId("MODEL_ID").build();
        MLAgent mlAgent = MLAgent
            .builder()
            .name("TestAgent")
            .type(MLAgentType.CONVERSATIONAL.name())
            .llm(llmSpec)
            .memory(bedrockMemorySpec)
            .build();

        Map<String, String> params = new HashMap<>();
        params.put("memory_type", "bedrock_agentcore_memory");
        params.put("memory_arn", "arn:aws:bedrock:us-east-1:123456789012:memory/test-memory");
        params.put("memory_region", "us-east-1");
        params.put("agent_id", "test-agent-id");

        mlChatAgentRunner.run(mlAgent, params, agentActionListener);

        verify(bedrockMemoryFactory).create(any(), any());
    }

    @Test
    public void testRestoreBedrockMemoryConfigBranches() {
        // Target uncovered branch in line 422 (cachedConfig != null)
        MLMemorySpec bedrockMemorySpec = new MLMemorySpec("bedrock_agentcore_memory", "memory-id", 10);

        // First cache some config
        Map<String, String> initialParams = new HashMap<>();
        initialParams.put("memory_type", "bedrock_agentcore_memory");
        initialParams.put("memory_arn", "arn:aws:bedrock:us-east-1:123456789012:memory/test-memory");
        initialParams.put("memory_region", "us-east-1");
        initialParams.put("agent_id", "test-agent-id");

        LLMSpec llmSpec = LLMSpec.builder().modelId("MODEL_ID").build();
        MLAgent mlAgent = MLAgent
            .builder()
            .name("TestAgentCache")
            .type(MLAgentType.CONVERSATIONAL.name())
            .llm(llmSpec)
            .memory(bedrockMemorySpec)
            .build();

        when(memoryMap.get("bedrock_agentcore_memory")).thenReturn(bedrockMemoryFactory);
        doAnswer(invocation -> {
            ActionListener<BedrockAgentCoreMemory> listener = invocation.getArgument(1);
            listener.onResponse(bedrockAgentCoreMemory);
            return null;
        }).when(bedrockMemoryFactory).create(any(), any());

        // First call to cache config
        mlChatAgentRunner.run(mlAgent, initialParams, agentActionListener);

        // Second call should trigger restoreBedrockMemoryConfig with cached config
        Map<String, String> emptyParams = new HashMap<>();
        emptyParams.put("agent_id", "test-agent-id");

        mlChatAgentRunner.run(mlAgent, emptyParams, agentActionListener);

        verify(bedrockMemoryFactory, Mockito.atLeast(2)).create(any(), any());
    }
}
