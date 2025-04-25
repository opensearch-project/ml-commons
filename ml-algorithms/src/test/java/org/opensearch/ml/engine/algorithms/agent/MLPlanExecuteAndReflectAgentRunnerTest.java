/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.agent;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
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
import org.opensearch.ml.common.transport.execute.MLExecuteTaskAction;
import org.opensearch.ml.common.transport.execute.MLExecuteTaskRequest;
import org.opensearch.ml.common.transport.execute.MLExecuteTaskResponse;
import org.opensearch.ml.common.transport.prediction.MLPredictionTaskAction;
import org.opensearch.ml.common.transport.prediction.MLPredictionTaskRequest;
import org.opensearch.ml.engine.encryptor.Encryptor;
import org.opensearch.ml.engine.memory.ConversationIndexMemory;
import org.opensearch.ml.engine.memory.MLMemoryManager;
import org.opensearch.ml.memory.action.conversation.CreateInteractionResponse;
import org.opensearch.ml.repackage.com.google.common.collect.ImmutableMap;
import org.opensearch.remote.metadata.client.SdkClient;
import org.opensearch.transport.client.Client;

public class MLPlanExecuteAndReflectAgentRunnerTest {
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
    private MLExecuteTaskResponse mlExecuteTaskResponse;
    @Mock
    private MLTaskResponse mlTaskResponse;

    @Captor
    private ArgumentCaptor<Object> objectCaptor;
    @Captor
    private ArgumentCaptor<ActionListener<ConversationIndexMemory>> memoryFactoryCapture;
    @Captor
    private ArgumentCaptor<ActionListener<List<Interaction>>> memoryInteractionCapture;
    @Captor
    private ArgumentCaptor<Map<String, String>> toolParamsCapture;

    private MLMemorySpec mlMemorySpec;

    @Before
    @SuppressWarnings("unchecked")
    public void setup() {
        MockitoAnnotations.openMocks(this);
        settings = Settings.builder().build();
        toolFactories = ImmutableMap.of(FIRST_TOOL, firstToolFactory, SECOND_TOOL, secondToolFactory);

        // Setup memory
        mlMemorySpec = new MLMemorySpec(ConversationIndexMemory.TYPE, "uuid", 10);
        when(memoryMap.get(anyString())).thenReturn(memoryFactory);
        when(conversationIndexMemory.getConversationId()).thenReturn("conversation_id");
        when(conversationIndexMemory.getMemoryManager()).thenReturn(mlMemoryManager);
        when(createInteractionResponse.getId()).thenReturn("create_interaction_id");
        when(updateResponse.getId()).thenReturn("update_interaction_id");

        // Setup memory factory
        doAnswer(invocation -> {
            ActionListener<ConversationIndexMemory> listener = invocation.getArgument(3);
            listener.onResponse(conversationIndexMemory);
            return null;
        }).when(memoryFactory).create(any(), any(), any(), memoryFactoryCapture.capture());

        // Setup conversation index memory
        doAnswer(invocation -> {
            ActionListener<List<Interaction>> listener = invocation.getArgument(0);
            listener.onResponse(generateInteractions(2));
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
        // Create MLAgent with tools and parameters
        Map<String, String> agentParams = new HashMap<>();
        agentParams.put("system_prompt", "You are a helpful assistant");
        agentParams.put("max_steps", "10");
        
        MLAgent mlAgent = createMLAgentWithTools(agentParams);

        // Setup LLM response for planning phase
        doAnswer(invocation -> {
            ActionListener<Object> listener = invocation.getArgument(2);
            ModelTensor modelTensor = ModelTensor.builder()
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
            ModelTensor modelTensor = ModelTensor.builder()
                .dataAsMap(ImmutableMap.of("response", "tool execution result"))
                .build();
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
        mlPlanExecuteAndReflectAgentRunner.run(mlAgent, params, agentActionListener);

        // Verify the response
        verify(agentActionListener).onResponse(objectCaptor.capture());
        Object response = objectCaptor.getValue();
        assertTrue(response instanceof ModelTensorOutput);
        ModelTensorOutput modelTensorOutput = (ModelTensorOutput) response;
        assertNotNull(modelTensorOutput);
    }

    @Test
    public void testExecutionWithHistory() {
        // Create MLAgent with tools and parameters
        Map<String, String> agentParams = new HashMap<>();
        agentParams.put("system_prompt", "You are a helpful assistant");
        agentParams.put("max_steps", "10");
        
        MLAgent mlAgent = createMLAgentWithTools(agentParams);

        // Setup LLM response for planning phase
        doAnswer(invocation -> {
            ActionListener<Object> listener = invocation.getArgument(2);
            ModelTensor modelTensor = ModelTensor.builder()
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
            ModelTensor modelTensor = ModelTensor.builder()
                .dataAsMap(ImmutableMap.of("response", "tool execution result"))
                .build();
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
        mlPlanExecuteAndReflectAgentRunner.run(mlAgent, params, agentActionListener);

        // Verify the response
        verify(agentActionListener).onResponse(objectCaptor.capture());
        Object response = objectCaptor.getValue();
        assertTrue(response instanceof ModelTensorOutput);
        ModelTensorOutput modelTensorOutput = (ModelTensorOutput) response;
        assertNotNull(modelTensorOutput);
    }

    @Test
    public void testExecutionWithMaxSteps() {
        // Create MLAgent with tools and parameters
        Map<String, String> agentParams = new HashMap<>();
        agentParams.put("system_prompt", "You are a helpful assistant");
        agentParams.put("max_steps", "10");
        
        MLAgent mlAgent = createMLAgentWithTools(agentParams);

        // Setup LLM response for planning phase
        doAnswer(invocation -> {
            ActionListener<Object> listener = invocation.getArgument(2);
            ModelTensor modelTensor = ModelTensor.builder()
                .dataAsMap(ImmutableMap.of("response", "{\"steps\":[\"step1\", \"step2\", \"step3\"], \"result\":\"\"}"))
                .build();
            ModelTensors modelTensors = ModelTensors.builder().mlModelTensors(Arrays.asList(modelTensor)).build();
            ModelTensorOutput mlModelTensorOutput = ModelTensorOutput.builder().mlModelOutputs(Arrays.asList(modelTensors)).build();
            when(mlTaskResponse.getOutput()).thenReturn(mlModelTensorOutput);
            listener.onResponse(mlTaskResponse);
            return null;
        }).when(client).execute(eq(MLPredictionTaskAction.INSTANCE), any(MLPredictionTaskRequest.class), any());

        // Setup tool execution response
        doAnswer(invocation -> {
            ActionListener<Object> listener = invocation.getArgument(2);
            ModelTensor modelTensor = ModelTensor.builder()
                .dataAsMap(ImmutableMap.of("response", "tool execution result"))
                .build();
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

        // Run the agent with max steps
        Map<String, String> params = new HashMap<>();
        params.put("question", "test question");
        params.put("max_steps", "2");
        mlPlanExecuteAndReflectAgentRunner.run(mlAgent, params, agentActionListener);

        // Verify the response
        verify(agentActionListener).onResponse(objectCaptor.capture());
        Object response = objectCaptor.getValue();
        assertTrue(response instanceof ModelTensorOutput);
        ModelTensorOutput modelTensorOutput = (ModelTensorOutput) response;
        assertNotNull(modelTensorOutput);
    }

    // Helper methods
    private MLAgent createMLAgentWithTools(Map<String, String> parameters) {
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
            .parameters(parameters)
            .build();
    }

    private List<Interaction> generateInteractions(int size) {
        return Arrays.asList(
            Interaction.builder().id("interaction-1").input("input-1").response("response-1").build(),
            Interaction.builder().id("interaction-2").input("input-2").response("response-2").build()
        );
    }
} 