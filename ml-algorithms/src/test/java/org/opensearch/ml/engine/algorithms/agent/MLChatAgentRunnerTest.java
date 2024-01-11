/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.agent;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
import org.opensearch.client.Client;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.Settings;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.xcontent.NamedXContentRegistry;
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

    private MLMemorySpec mlMemorySpec;
    @Mock
    private ConversationIndexMemory conversationIndexMemory;
    @Mock
    private MLMemoryManager mlMemoryManager;

    @Mock
    private ConversationIndexMemory.Factory memoryFactory;
    @Captor
    private ArgumentCaptor<ActionListener<ConversationIndexMemory>> memoryFactoryCapture;
    @Captor
    private ArgumentCaptor<ActionListener<List<Interaction>>> memoryInteractionCapture;

    @Before
    @SuppressWarnings("unchecked")
    public void setup() {
        MockitoAnnotations.openMocks(this);
        settings = Settings.builder().build();
        toolFactories = Map.of(FIRST_TOOL, firstToolFactory, SECOND_TOOL, secondToolFactory);

        // memory
        mlMemorySpec = new MLMemorySpec(ConversationIndexMemory.TYPE, "uuid", 10);
        when(memoryMap.get(anyString())).thenReturn(memoryFactory);
        doAnswer(invocation -> {
            ActionListener<List<Interaction>> listener = invocation.getArgument(0);
            listener.onResponse(generateInteractions(2));
            return null;
        }).when(conversationIndexMemory).getMessages(memoryInteractionCapture.capture());
        when(conversationIndexMemory.getConversationId()).thenReturn("conversation_id");
        when(conversationIndexMemory.getMemoryManager()).thenReturn(mlMemoryManager);
        doAnswer(invocation -> {
            ActionListener<ConversationIndexMemory> listener = invocation.getArgument(3);
            listener.onResponse(conversationIndexMemory);
            return null;
        }).when(memoryFactory).create(any(), any(), any(), memoryFactoryCapture.capture());

        mlChatAgentRunner = new MLChatAgentRunner(client, settings, clusterService, xContentRegistry, toolFactories, memoryMap);
        when(firstToolFactory.create(Mockito.anyMap())).thenReturn(firstTool);
        when(secondToolFactory.create(Mockito.anyMap())).thenReturn(secondTool);
        when(firstTool.getName()).thenReturn(FIRST_TOOL);
        when(firstTool.getDescription()).thenReturn("First tool description");
        when(secondTool.getName()).thenReturn(SECOND_TOOL);
        when(secondTool.getDescription()).thenReturn("Second tool description");
        when(firstTool.validate(Mockito.anyMap())).thenReturn(true);
        when(secondTool.validate(Mockito.anyMap())).thenReturn(true);
        Mockito
            .doAnswer(generateToolResponse("First tool response"))
            .when(firstTool)
            .run(Mockito.anyMap(), nextStepListenerCaptor.capture());
        Mockito
            .doAnswer(generateToolResponse("Second tool response"))
            .when(secondTool)
            .run(Mockito.anyMap(), nextStepListenerCaptor.capture());

        Mockito
            .doAnswer(getLLMAnswer(Map.of("thought", "thought 1", "action", FIRST_TOOL)))
            .doAnswer(getLLMAnswer(Map.of("thought", "thought 2", "action", SECOND_TOOL)))
            .doAnswer(getLLMAnswer(Map.of("thought", "thought 3", "final_answer", "This is the final answer")))
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
        ModelTensor modelTensor1 = modelTensorOutput.getMlModelOutputs().get(1).getMlModelTensors().get(0);
        ModelTensor modelTensor2 = modelTensorOutput.getMlModelOutputs().get(2).getMlModelTensors().get(0);

        // Verify that the parsed values from JSON block are correctly set
        assertEquals("parent_interaction_id", parentInteractionModelTensor.getResult());
        assertEquals("Thought: parsed thought", modelTensor1.getResult());
        assertEquals("parsed final answer", modelTensor2.getResult());
    }

    @Test
    public void testRunWithIncludeOutputNotSet() {
        LLMSpec llmSpec = LLMSpec.builder().modelId("MODEL_ID").build();
        MLToolSpec firstToolSpec = MLToolSpec.builder().name(FIRST_TOOL).type(FIRST_TOOL).build();
        MLToolSpec secondToolSpec = MLToolSpec.builder().name(SECOND_TOOL).type(SECOND_TOOL).build();
        final MLAgent mlAgent = MLAgent
            .builder()
            .name("TestAgent")
            .llm(llmSpec)
            .memory(mlMemorySpec)
            .tools(Arrays.asList(firstToolSpec, secondToolSpec))
            .build();
        mlChatAgentRunner.run(mlAgent, new HashMap<>(), agentActionListener);
        Mockito.verify(agentActionListener).onResponse(objectCaptor.capture());
        ModelTensorOutput modelTensorOutput = (ModelTensorOutput) objectCaptor.getValue();
        List<ModelTensor> agentOutput = modelTensorOutput.getMlModelOutputs().get(0).getMlModelTensors();
        assertEquals(1, agentOutput.size());
        // Respond with last tool output
        assertEquals("This is the final answer", agentOutput.get(0).getDataAsMap().get("response"));
    }

    @Test
    public void testRunWithIncludeOutputSet() {
        LLMSpec llmSpec = LLMSpec.builder().modelId("MODEL_ID").build();
        MLToolSpec firstToolSpec = MLToolSpec.builder().name(FIRST_TOOL).type(FIRST_TOOL).includeOutputInAgentResponse(false).build();
        MLToolSpec secondToolSpec = MLToolSpec.builder().name(SECOND_TOOL).type(SECOND_TOOL).includeOutputInAgentResponse(true).build();
        final MLAgent mlAgent = MLAgent
            .builder()
            .name("TestAgent")
            .memory(mlMemorySpec)
            .llm(llmSpec)
            .tools(Arrays.asList(firstToolSpec, secondToolSpec))
            .build();
        HashMap<String, String> params = new HashMap<>();
        mlChatAgentRunner.run(mlAgent, params, agentActionListener);
        Mockito.verify(agentActionListener).onResponse(objectCaptor.capture());
        ModelTensorOutput modelTensorOutput = (ModelTensorOutput) objectCaptor.getValue();
        List<ModelTensor> agentOutput = modelTensorOutput.getMlModelOutputs().get(0).getMlModelTensors();
        assertEquals(1, agentOutput.size());
        // Respond with last tool output
        assertEquals("This is the final answer", agentOutput.get(0).getDataAsMap().get("response"));
        Map<String, List<String>> additionalInfos = (Map<String, List<String>>) agentOutput.get(0).getDataAsMap().get("additional_info");
        assertEquals("Second tool response", additionalInfos.get(String.format("%s.output", SECOND_TOOL)).get(0));
    }

    @Test
    public void testChatHistoryExcludeOngoingQuestion() {
        LLMSpec llmSpec = LLMSpec.builder().modelId("MODEL_ID").parameters(Map.of("max_iteration", "1")).build();
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
        }).when(conversationIndexMemory).getMessages(memoryInteractionCapture.capture());

        HashMap<String, String> params = new HashMap<>();
        mlChatAgentRunner.run(mlAgent, params, agentActionListener);
        Mockito.verify(agentActionListener).onResponse(objectCaptor.capture());
        String chatHistory = params.get(MLChatAgentRunner.CHAT_HISTORY);
        Assert.assertFalse(chatHistory.contains("input-99"));
    }

    @Test
    public void testChatHistoryWithVerboseMoreInteraction() {
        testInteractions("4");
    }

    @Test
    public void testChatHistoryWithVerboseLessInteraction() {
        testInteractions("2");
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
        }).when(conversationIndexMemory).getMessages(memoryInteractionCapture.capture());

        HashMap<String, String> params = new HashMap<>();
        params.put("verbose", "true");
        mlChatAgentRunner.run(mlAgent, params, agentActionListener);
        Mockito.verify(agentActionListener).onResponse(objectCaptor.capture());
        String chatHistory = params.get(MLChatAgentRunner.CHAT_HISTORY);
        Assert.assertFalse(chatHistory.contains("input-99"));
    }

    @Test
    public void testChatHistoryException() {
        LLMSpec llmSpec = LLMSpec.builder().modelId("MODEL_ID").build();
        MLToolSpec firstToolSpec = MLToolSpec.builder().name(FIRST_TOOL).type(FIRST_TOOL).includeOutputInAgentResponse(false).build();
        MLToolSpec secondToolSpec = MLToolSpec.builder().name(SECOND_TOOL).type(SECOND_TOOL).includeOutputInAgentResponse(true).build();
        final MLAgent mlAgent = MLAgent
            .builder()
            .name("TestAgent")
            .memory(mlMemorySpec)
            .llm(llmSpec)
            .tools(Arrays.asList(firstToolSpec, secondToolSpec))
            .build();

        doAnswer(invocation -> {

            ActionListener<List<Interaction>> listener = invocation.getArgument(0);
            listener.onFailure(new RuntimeException("Test Exception"));
            return null;
        }).when(conversationIndexMemory).getMessages(memoryInteractionCapture.capture());

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
        MLAgent mlAgent = MLAgent.builder().memory(mlMemorySpec).llm(llmSpec).name("TestAgent").build();

        // Create parameters for the agent with a non-existent tool
        Map<String, String> params = createAgentParamsWithAction("nonExistentTool", "someInput");

        // Run the MLChatAgentRunner
        mlChatAgentRunner.run(mlAgent, params, agentActionListener);

        // Verify that no tool's run method was called
        verify(firstTool, never()).run(any(), any());
        verify(secondTool, never()).run(any(), any());
    }

    // Helper methods to create MLAgent and parameters
    private MLAgent createMLAgentWithTools() {
        LLMSpec llmSpec = LLMSpec.builder().modelId("MODEL_ID").build();
        MLToolSpec firstToolSpec = MLToolSpec.builder().name(FIRST_TOOL).type(FIRST_TOOL).build();
        return MLAgent.builder().name("TestAgent").tools(Arrays.asList(firstToolSpec)).memory(mlMemorySpec).llm(llmSpec).build();
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

}
