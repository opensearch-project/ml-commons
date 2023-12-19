package org.opensearch.ml.engine.algorithms.agent;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import org.opensearch.ml.engine.memory.ConversationIndexMemory;
import org.opensearch.ml.engine.memory.MLMemoryManager;

import software.amazon.awssdk.utils.ImmutableMap;

public class MLFlowAgentRunnerTest {

    public static final String FIRST_TOOL = "firstTool";
    public static final String SECOND_TOOL = "secondTool";

    public static final String FIRST_TOOL_DESC = "first tool description";
    public static final String SECOND_TOOL_DESC = "second tool description";
    public static final String FIRST_TOOL_RESPONSE = "First tool response";
    public static final String SECOND_TOOL_RESPONSE = "Second tool response";

    @Mock
    private Client client;

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

    @Captor
    private ArgumentCaptor<Object> objectCaptor;

    @Captor
    private ArgumentCaptor<StepListener<Object>> nextStepListenerCaptor;

    @Before
    @SuppressWarnings("unchecked")
    public void setup() {
        MockitoAnnotations.openMocks(this);
        settings = Settings.builder().build();
        toolFactories = ImmutableMap.of(FIRST_TOOL, firstToolFactory, SECOND_TOOL, secondToolFactory);
        memoryMap = ImmutableMap.of("memoryType", mockMemoryFactory);
        mlFlowAgentRunner = new MLFlowAgentRunner(client, settings, clusterService, xContentRegistry, toolFactories, memoryMap);
        when(firstToolFactory.create(anyMap())).thenReturn(firstTool);
        when(secondToolFactory.create(anyMap())).thenReturn(secondTool);
        when(secondTool.getDescription()).thenReturn(SECOND_TOOL_DESC);
        when(firstTool.getDescription()).thenReturn(FIRST_TOOL_DESC);
        when(firstTool.getName()).thenReturn(FIRST_TOOL);
        when(secondTool.getName()).thenReturn(SECOND_TOOL);
        Mockito.doAnswer(generateToolResponse(FIRST_TOOL_RESPONSE)).when(firstTool).run(anyMap(), nextStepListenerCaptor.capture());
        Mockito.doAnswer(generateToolResponse(SECOND_TOOL_RESPONSE)).when(secondTool).run(anyMap(), nextStepListenerCaptor.capture());
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
        Mockito.doAnswer(generateToolTensorResponse()).when(firstTool).run(anyMap(), nextStepListenerCaptor.capture());
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
            "{\"inference_results\":[{\"output\":[{\"name\":\"firstTool\",\"dataAsMap\":{\"index\":\"index response\"}}]}]}"; // the JSON
                                                                                                                              // representation
                                                                                                                              // of the
                                                                                                                              // model
                                                                                                                              // tensor
        assertEquals(expectedTensorOuput, mlFlowAgentRunner.parseResponse(mlModelTensorOutput));
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
}
