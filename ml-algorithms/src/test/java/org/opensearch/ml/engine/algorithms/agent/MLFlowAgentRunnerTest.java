/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.agent;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.opensearch.ml.engine.memory.ConversationIndexMemory.APP_TYPE;
import static org.opensearch.ml.engine.memory.ConversationIndexMemory.MEMORY_ID;
import static org.opensearch.ml.engine.memory.ConversationIndexMemory.MEMORY_NAME;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.stubbing.Answer;
import org.opensearch.action.StepListener;
import org.opensearch.client.Client;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.Settings;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.ml.common.agent.MLAgent;
import org.opensearch.ml.common.agent.MLMemorySpec;
import org.opensearch.ml.common.agent.MLToolSpec;
import org.opensearch.ml.common.output.model.ModelTensor;
import org.opensearch.ml.common.output.model.ModelTensorOutput;
import org.opensearch.ml.common.output.model.ModelTensors;
import org.opensearch.ml.common.spi.memory.Memory;
import org.opensearch.ml.common.spi.tools.Tool;
import org.opensearch.ml.engine.indices.MLIndicesHandler;
import org.opensearch.ml.engine.memory.ConversationIndexMemory;
import org.opensearch.ml.engine.memory.MLMemoryManager;

public class MLFlowAgentRunnerTest {

    public static final String FIRST_TOOL = "firstTool";
    public static final String SECOND_TOOL = "secondTool";

    public static final String FIRST_TOOL_DESC = "first tool description";
    public static final String SECOND_TOOL_DESC = "second tool description";
    public static final String FIRST_TOOL_RESPONSE = "First tool response";
    public static final String SECOND_TOOL_RESPONSE = "Second tool response";

    @Mock
    private Client client;
    @Mock
    MLIndicesHandler indicesHandler;

    @Mock
    MLMemoryManager memoryManager;

    private Settings settings;

    @Mock
    private ClusterService clusterService;

    @Mock
    private NamedXContentRegistry xContentRegistry;

    private Map<String, Tool.Factory> toolFactories;

    private Map<String, Memory.Factory> memoryMap;

    private MLFlowAgentRunner mlFlowAgentRunner;

    @Mock
    private Tool.Factory firstToolFactory;

    @Mock
    private Tool.Factory secondToolFactory;
    @Mock
    private Tool firstTool;

    @Mock
    private Tool secondTool;

    @Mock
    private ConversationIndexMemory.Factory mockMemoryFactory;

    @Mock
    private ActionListener<Object> agentActionListener;

    @Mock
    private ActionListener<ConversationIndexMemory> conversationIndexMemoryActionListener;

    @Captor
    private ArgumentCaptor<Object> objectCaptor;

    @Captor
    private ArgumentCaptor<StepListener<Object>> nextStepListenerCaptor;

    @Before
    @SuppressWarnings("unchecked")
    public void setup() {
        MockitoAnnotations.openMocks(this);
        settings = Settings.builder().build();
        toolFactories = Map.of(FIRST_TOOL, firstToolFactory, SECOND_TOOL, secondToolFactory);
        memoryMap = Map.of("memoryType", mockMemoryFactory);
        mlFlowAgentRunner = new MLFlowAgentRunner(client, settings, clusterService, xContentRegistry, toolFactories, memoryMap);
        when(firstToolFactory.create(anyMap())).thenReturn(firstTool);
        when(secondToolFactory.create(anyMap())).thenReturn(secondTool);
        when(secondTool.getDescription()).thenReturn(SECOND_TOOL_DESC);
        when(firstTool.getDescription()).thenReturn(FIRST_TOOL_DESC);
        when(firstTool.getName()).thenReturn(FIRST_TOOL);
        when(secondTool.getName()).thenReturn(SECOND_TOOL);
        doAnswer(generateToolResponse(FIRST_TOOL_RESPONSE)).when(firstTool).run(anyMap(), nextStepListenerCaptor.capture());
        doAnswer(generateToolResponse(SECOND_TOOL_RESPONSE)).when(secondTool).run(anyMap(), nextStepListenerCaptor.capture());
    }

    private Answer generateToolResponse(String response) {
        return invocation -> {
            ActionListener<Object> listener = invocation.getArgument(1);
            listener.onResponse(response);
            return null;
        };
    }

    private Answer generateToolTensorResponse() {
        ModelTensor modelTensor = ModelTensor.builder().name(FIRST_TOOL).dataAsMap(Map.of("index", "index response")).build();
        ModelTensors modelTensors = ModelTensors.builder().mlModelTensors(Arrays.asList(modelTensor)).build();
        ModelTensorOutput mlModelTensorOutput = ModelTensorOutput.builder().mlModelOutputs(Arrays.asList(modelTensors)).build();
        return invocation -> {
            ActionListener<Object> listener = invocation.getArgument(1);
            listener.onResponse(mlModelTensorOutput);
            return null;
        };
    }

    @Test
    public void testRunWithIncludeOutputNotSet() {
        final Map<String, String> params = new HashMap<>();
        params.put(MLAgentExecutor.MEMORY_ID, "memoryId");
        MLToolSpec firstToolSpec = MLToolSpec.builder().name(FIRST_TOOL).type(FIRST_TOOL).build();
        MLToolSpec secondToolSpec = MLToolSpec.builder().name(SECOND_TOOL).type(SECOND_TOOL).build();
        MLMemorySpec mlMemorySpec = MLMemorySpec.builder().type("memoryType").build();
        final MLAgent mlAgent = MLAgent
            .builder()
            .name("TestAgent")
            .memory(mlMemorySpec)
            .tools(Arrays.asList(firstToolSpec, secondToolSpec))
            .build();
        mlFlowAgentRunner.run(mlAgent, params, agentActionListener);
        Mockito.verify(agentActionListener).onResponse(objectCaptor.capture());
        List<ModelTensor> agentOutput = (List<ModelTensor>) objectCaptor.getValue();
        assertEquals(1, agentOutput.size());
        // Respond with last tool output
        assertEquals(SECOND_TOOL, agentOutput.get(0).getName());
        assertEquals(SECOND_TOOL_RESPONSE, agentOutput.get(0).getResult());
    }

    @Test()
    public void testRunWithNoToolSpec() {
        final Map<String, String> params = new HashMap<>();
        params.put(MLAgentExecutor.MEMORY_ID, "memoryId");
        MLMemorySpec mlMemorySpec = MLMemorySpec.builder().type("memoryType").build();
        final MLAgent mlAgent = MLAgent.builder().name("TestAgent").memory(mlMemorySpec).build();
        mlFlowAgentRunner.run(mlAgent, params, agentActionListener);
        ArgumentCaptor<Exception> argCaptor = ArgumentCaptor.forClass(IllegalArgumentException.class);
        verify(agentActionListener).onFailure(argCaptor.capture());
        assert (argCaptor.getValue().getMessage().equals("no tool configured"));
    }

    @Test
    public void testRunWithIncludeOutputSet() {
        final Map<String, String> params = new HashMap<>();
        params.put(MLAgentExecutor.MEMORY_ID, "memoryId");
        MLToolSpec firstToolSpec = MLToolSpec.builder().name(FIRST_TOOL).type(FIRST_TOOL).includeOutputInAgentResponse(true).build();
        MLToolSpec secondToolSpec = MLToolSpec.builder().name(SECOND_TOOL).type(SECOND_TOOL).includeOutputInAgentResponse(true).build();
        MLMemorySpec mlMemorySpec = MLMemorySpec.builder().type("memoryType").build();
        final MLAgent mlAgent = MLAgent
            .builder()
            .name("TestAgent")
            .memory(mlMemorySpec)
            .tools(Arrays.asList(firstToolSpec, secondToolSpec))
            .build();
        mlFlowAgentRunner.run(mlAgent, params, agentActionListener);
        Mockito.verify(agentActionListener).onResponse(objectCaptor.capture());
        List<ModelTensor> agentOutput = (List<ModelTensor>) objectCaptor.getValue();
        // Respond with all tool output
        assertEquals(2, agentOutput.size());
        assertEquals(FIRST_TOOL, agentOutput.get(0).getName());
        assertEquals(SECOND_TOOL, agentOutput.get(1).getName());
        assertEquals(FIRST_TOOL_RESPONSE, agentOutput.get(0).getResult());
        assertEquals(SECOND_TOOL_RESPONSE, agentOutput.get(1).getResult());
    }

    @Test
    public void testRunWithModelTensorOutput() {
        final Map<String, String> params = new HashMap<>();
        params.put(MLAgentExecutor.MEMORY_ID, "memoryId");
        MLToolSpec firstToolSpec = MLToolSpec.builder().name(null).type(FIRST_TOOL).includeOutputInAgentResponse(true).build();
        MLToolSpec secondToolSpec = MLToolSpec.builder().name(SECOND_TOOL).type(SECOND_TOOL).includeOutputInAgentResponse(true).build();
        MLMemorySpec mlMemorySpec = MLMemorySpec.builder().type("memoryType").build();
        final MLAgent mlAgent = MLAgent
            .builder()
            .name("TestAgent")
            .memory(mlMemorySpec)
            .tools(Arrays.asList(firstToolSpec, secondToolSpec))
            .build();
        doAnswer(generateToolTensorResponse()).when(firstTool).run(anyMap(), nextStepListenerCaptor.capture());
        mlFlowAgentRunner.run(mlAgent, params, agentActionListener);
        Mockito.verify(agentActionListener).onResponse(objectCaptor.capture());
        List<ModelTensor> agentOutput = (List<ModelTensor>) objectCaptor.getValue();
        // Respond with all tool output
        assertEquals(2, agentOutput.size());
        assertEquals(FIRST_TOOL, agentOutput.get(0).getName());
        assertEquals(SECOND_TOOL, agentOutput.get(1).getName());
        assertEquals("index response", agentOutput.get(0).getDataAsMap().get("index"));
        assertEquals(SECOND_TOOL_RESPONSE, agentOutput.get(1).getResult());
    }

    @Test
    public void testGetToolExecuteParams() {
        MLToolSpec toolSpec = mock(MLToolSpec.class);
        when(toolSpec.getParameters()).thenReturn(Map.of("param1", "value1"));
        when(toolSpec.getType()).thenReturn("toolType");
        when(toolSpec.getName()).thenReturn("toolName");

        Map<String, String> params = Map.of("toolType.param2", "value2", "toolName.param3", "value3", "param4", "value4");

        Map<String, String> result = mlFlowAgentRunner.getToolExecuteParams(toolSpec, params);

        assertEquals("value1", result.get("param1"));
        assertEquals("value3", result.get("param3"));
        assertEquals("value4", result.get("param4"));
        assertFalse(result.containsKey("toolType.param2"));
    }

    @Test
    public void testGetToolExecuteParamsWithInputSubstitution() {
        // Setup ToolSpec with parameters
        MLToolSpec toolSpec = mock(MLToolSpec.class);
        when(toolSpec.getParameters()).thenReturn(Map.of("param1", "value1"));
        when(toolSpec.getType()).thenReturn("toolType");
        when(toolSpec.getName()).thenReturn("toolName");

        // Setup params with a special 'input' key for substitution
        Map<String, String> params = Map
            .of(
                "toolType.param2",
                "value2",
                "toolName.param3",
                "value3",
                "param4",
                "value4",
                "input",
                "Input contains ${parameters.param1}, ${parameters.param4}"
            );

        // Execute the method
        Map<String, String> result = mlFlowAgentRunner.getToolExecuteParams(toolSpec, params);

        // Assertions
        assertEquals("value1", result.get("param1"));
        assertEquals("value3", result.get("param3"));
        assertEquals("value4", result.get("param4"));
        assertFalse(result.containsKey("toolType.param2"));

        // Asserting substitution in 'input'
        String expectedInput = "Input contains value1, value4";
        assertEquals(expectedInput, result.get("input"));
    }

    @Test
    public void testCreateTool() {
        MLToolSpec firstToolSpec = MLToolSpec.builder().name(FIRST_TOOL).description("description").type(FIRST_TOOL).build();
        Tool result = mlFlowAgentRunner.createTool(firstToolSpec);

        assertNotNull(result);
        assertEquals(FIRST_TOOL, result.getName());
        assertEquals(FIRST_TOOL_DESC, result.getDescription());
    }

    @Test
    public void testParseResponse() throws IOException {

        String outputString = "testOutput";
        assertEquals(outputString, mlFlowAgentRunner.parseResponse(outputString));

        ModelTensor modelTensor = ModelTensor.builder().name(FIRST_TOOL).dataAsMap(Map.of("index", "index response")).build();
        ModelTensors modelTensors = ModelTensors.builder().mlModelTensors(Arrays.asList(modelTensor)).build();
        ModelTensorOutput mlModelTensorOutput = ModelTensorOutput.builder().mlModelOutputs(Arrays.asList(modelTensors)).build();

        String expectedJson = "{\"name\":\"firstTool\",\"dataAsMap\":{\"index\":\"index response\"}}"; // the JSON representation of the
                                                                                                       // model tensor
        assertEquals(expectedJson, mlFlowAgentRunner.parseResponse(modelTensor));

        String expectedTensorOuput =
            "{\"inference_results\":[{\"output\":[{\"name\":\"firstTool\",\"dataAsMap\":{\"index\":\"index response\"}}]}]}";
        assertEquals(expectedTensorOuput, mlFlowAgentRunner.parseResponse(mlModelTensorOutput));

        // Test for List containing ModelTensors
        ModelTensors tensorsInList = ModelTensors.builder().mlModelTensors(Arrays.asList(modelTensor)).build();
        List<ModelTensors> tensorList = Arrays.asList(tensorsInList);
        String expectedListJson = "{\"output\":[{\"name\":\"firstTool\",\"dataAsMap\":{\"index\":\"index response\"}}]}"; // Replace with
                                                                                                                          // the actual JSON
                                                                                                                          // representation
        assertEquals(expectedListJson, mlFlowAgentRunner.parseResponse(tensorList));

        // Test for a non-string, non-model object
        Map<String, Object> nonModelObject = Map.of("key", "value");
        String expectedNonModelJson = "{\"key\":\"value\"}"; // Replace with the actual JSON representation from StringUtils.toJson
        assertEquals(expectedNonModelJson, mlFlowAgentRunner.parseResponse(nonModelObject));
    }

    @Test
    public void testUpdateInteraction() {
        String interactionId = "interactionId";
        ConversationIndexMemory memory = mock(ConversationIndexMemory.class);
        MLMemoryManager memoryManager = mock(MLMemoryManager.class);
        when(memory.getMemoryManager()).thenReturn(memoryManager);
        Map<String, Object> additionalInfo = new HashMap<>();

        mlFlowAgentRunner.updateInteraction(additionalInfo, interactionId, memory);
        verify(memoryManager).updateInteraction(eq(interactionId), anyMap(), any());
    }

    @Test
    public void testWithMemoryNotSet() {
        final Map<String, String> params = new HashMap<>();
        params.put(MLAgentExecutor.MEMORY_ID, "memoryId");
        MLToolSpec firstToolSpec = MLToolSpec.builder().name(FIRST_TOOL).type(FIRST_TOOL).build();
        MLToolSpec secondToolSpec = MLToolSpec.builder().name(SECOND_TOOL).type(SECOND_TOOL).build();
        final MLAgent mlAgent = MLAgent
            .builder()
            .name("TestAgent")
            .memory(null)
            .tools(Arrays.asList(firstToolSpec, secondToolSpec))
            .build();
        mlFlowAgentRunner.run(mlAgent, params, agentActionListener);
        Mockito.verify(agentActionListener).onResponse(objectCaptor.capture());
        List<ModelTensor> agentOutput = (List<ModelTensor>) objectCaptor.getValue();
        assertEquals(1, agentOutput.size());
        // Respond with last tool output
        assertEquals(SECOND_TOOL, agentOutput.get(0).getName());
        assertEquals(SECOND_TOOL_RESPONSE, agentOutput.get(0).getResult());
    }

    @Test
    public void testUpdateMemory() {
        // Mocking MLMemorySpec
        MLMemorySpec memorySpec = mock(MLMemorySpec.class);
        when(memorySpec.getType()).thenReturn("memoryType");

        // Mocking Memory Factory and Memory

        ConversationIndexMemory.Factory memoryFactory = new ConversationIndexMemory.Factory();
        memoryFactory.init(client, indicesHandler, memoryManager);
        ActionListener<ConversationIndexMemory> listener = mock(ActionListener.class);
        memoryFactory.create(Map.of(MEMORY_ID, "123", MEMORY_NAME, "name", APP_TYPE, "app"), listener);

        verify(listener).onResponse(isA(ConversationIndexMemory.class));

        Map<String, Memory.Factory> memoryFactoryMap = new HashMap<>();
        memoryFactoryMap.put("memoryType", memoryFactory);
        mlFlowAgentRunner.setMemoryFactoryMap(memoryFactoryMap);

        // Execute the method under test
        mlFlowAgentRunner.updateMemory(new HashMap<>(), memorySpec, "memoryId", "interactionId");

        // Asserting that the Memory Manager's updateInteraction method was called
        verify(memoryManager).updateInteraction(anyString(), anyMap(), any(ActionListener.class));
    }

}
