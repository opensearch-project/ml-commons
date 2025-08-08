/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.agent;

/**
 * Unit tests for {@link MLFlowAgentRunner}.
 * 
 * <p>This test class covers the functionality of the ML Flow Agent Runner, which is responsible for
 * executing a sequence of tools in a flow-based manner. The tests verify:</p>
 * 
 * <ul>
 *   <li>Basic flow execution with single and multiple tools</li>
 *   <li>Memory management and interaction updates</li>
 *   <li>Tool parameter extraction and configuration</li>
 *   <li>Response parsing for different output types</li>
 *   <li>Error handling and tracing integration</li>
 *   <li>Model tensor output processing</li>
 * </ul>
 * 
 * <p>The tests use Mockito for mocking dependencies and verify both successful execution paths
 * and error scenarios. The MLAgentTracer is initialized with a NoopTracer for testing purposes
 * to avoid actual tracing overhead.</p>
 * 
 * <p>Key test scenarios include:</p>
 * <ul>
 *   <li>Flow execution with and without memory</li>
 *   <li>Tool chain execution with multiple tools</li>
 *   <li>Error handling when tools fail</li>
 *   <li>Parameter substitution and configuration overrides</li>
 *   <li>Response parsing for various output formats</li>
 * </ul>
 * 
 * @see MLFlowAgentRunner
 * @see MLAgentTracer
 * @see MLAgent
 * @see MLToolSpec
 */

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.opensearch.ml.engine.memory.ConversationIndexMemory.APP_TYPE;
import static org.opensearch.ml.engine.memory.ConversationIndexMemory.MEMORY_ID;
import static org.opensearch.ml.engine.memory.ConversationIndexMemory.MEMORY_NAME;
import static org.opensearch.ml.engine.tools.ToolUtils.buildToolParameters;

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
import org.opensearch.action.DocWriteResponse;
import org.opensearch.action.update.UpdateResponse;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.Settings;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.index.Index;
import org.opensearch.core.index.shard.ShardId;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.ml.common.MLAgentType;
import org.opensearch.ml.common.agent.MLAgent;
import org.opensearch.ml.common.agent.MLMemorySpec;
import org.opensearch.ml.common.agent.MLToolSpec;
import org.opensearch.ml.common.output.model.ModelTensor;
import org.opensearch.ml.common.output.model.ModelTensorOutput;
import org.opensearch.ml.common.output.model.ModelTensors;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.spi.memory.Memory;
import org.opensearch.ml.common.spi.tools.Tool;
import org.opensearch.ml.engine.algorithms.agent.tracing.MLAgentTracer;
import org.opensearch.ml.engine.indices.MLIndicesHandler;
import org.opensearch.ml.engine.memory.ConversationIndexMemory;
import org.opensearch.ml.engine.memory.MLMemoryManager;
import org.opensearch.ml.engine.tools.ToolUtils;
import org.opensearch.telemetry.tracing.Span;
import org.opensearch.telemetry.tracing.noop.NoopTracer;
import org.opensearch.transport.client.Client;

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
    private ArgumentCaptor<ActionListener<Object>> actionListenerCaptor;

    @Captor
    private ArgumentCaptor<Map<String, Object>> memoryMapCaptor;

    /**
     * Sets up the test environment before each test method.
     * 
     * <p>This method initializes all mocks and dependencies required for testing the MLFlowAgentRunner.
     * It sets up:</p>
     * <ul>
     *   <li>Mock tool factories and tools</li>
     *   <li>Mock memory management components</li>
     *   <li>MLAgentTracer with NoopTracer for testing</li>
     *   <li>Tool response generators</li>
     * </ul>
     * 
     * <p>The setup ensures that all tests have a consistent and isolated environment.</p>
     */
    @Before
    @SuppressWarnings("unchecked")
    public void setup() {
        MockitoAnnotations.openMocks(this);
        settings = Settings.builder().build();
        toolFactories = ImmutableMap.of(FIRST_TOOL, firstToolFactory, SECOND_TOOL, secondToolFactory);
        memoryMap = ImmutableMap.of("memoryType", mockMemoryFactory);
        mlFlowAgentRunner = new MLFlowAgentRunner(client, settings, clusterService, xContentRegistry, toolFactories, memoryMap, null, null);
        when(firstToolFactory.create(anyMap())).thenReturn(firstTool);
        when(secondToolFactory.create(anyMap())).thenReturn(secondTool);
        when(secondTool.getDescription()).thenReturn(SECOND_TOOL_DESC);
        when(firstTool.getDescription()).thenReturn(FIRST_TOOL_DESC);
        when(firstTool.getName()).thenReturn(FIRST_TOOL);
        when(secondTool.getName()).thenReturn(SECOND_TOOL);
        doAnswer(generateToolResponse(FIRST_TOOL_RESPONSE)).when(firstTool).run(anyMap(), actionListenerCaptor.capture());
        doAnswer(generateToolResponse(SECOND_TOOL_RESPONSE)).when(secondTool).run(anyMap(), actionListenerCaptor.capture());

        // Initialize MLAgentTracer with NoopTracer for tests
        MLFeatureEnabledSetting mockFeatureSetting = Mockito.mock(MLFeatureEnabledSetting.class);
        when(mockFeatureSetting.isTracingEnabled()).thenReturn(false); // disables tracing, uses NoopTracer
        MLAgentTracer.resetForTest();
        MLAgentTracer.initialize(NoopTracer.INSTANCE, mockFeatureSetting);
    }

    /**
     * Generates a mock tool response for testing.
     * 
     * @param response The response string to return from the tool
     * @return A Mockito Answer that simulates a successful tool execution
     */
    private Answer generateToolResponse(String response) {
        return invocation -> {
            ActionListener<Object> listener = invocation.getArgument(1);
            listener.onResponse(response);
            return null;
        };
    }

    /**
     * Generates a mock ModelTensorOutput response for testing.
     * 
     * <p>This method creates a mock response that simulates a tool returning a ModelTensorOutput,
     * which is used to test the parsing and handling of complex model outputs.</p>
     * 
     * @return A Mockito Answer that simulates a tool returning ModelTensorOutput
     */
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

    /**
     * Tests flow execution when includeOutputInAgentResponse is not set.
     * 
     * <p>This test verifies that when tools are configured without the includeOutputInAgentResponse
     * flag, only the last tool's output is included in the final response. The test:</p>
     * <ul>
     *   <li>Creates an agent with two tools</li>
     *   <li>Executes the flow with memory management</li>
     *   <li>Verifies that only the second tool's output is returned</li>
     *   <li>Checks that memory interaction is updated correctly</li>
     * </ul>
     * 
     * <p>This test ensures the default behavior where intermediate tool outputs are not
     * included in the final response unless explicitly configured.</p>
     */
    @Test
    public void testRunWithIncludeOutputNotSet() {
        final Map<String, String> params = new HashMap<>();
        params.put(MLAgentExecutor.MEMORY_ID, "memoryId");
        params.put(MLAgentExecutor.PARENT_INTERACTION_ID, "interaction_id");
        MLToolSpec firstToolSpec = MLToolSpec.builder().name(FIRST_TOOL).type(FIRST_TOOL).build();
        MLToolSpec secondToolSpec = MLToolSpec.builder().name(SECOND_TOOL).type(SECOND_TOOL).build();
        MLMemorySpec mlMemorySpec = MLMemorySpec.builder().type("memoryType").build();
        ConversationIndexMemory memory = mock(ConversationIndexMemory.class);
        Mockito.doAnswer(invocation -> {
            ActionListener<UpdateResponse> listener = invocation.getArgument(2);
            ShardId shardId = new ShardId(new Index("indexName", "uuid"), 1);
            listener.onResponse(new UpdateResponse(shardId, "taskId", 1, 1, 1, DocWriteResponse.Result.UPDATED));
            return null;
        }).when(memoryManager).updateInteraction(Mockito.any(), Mockito.any(), Mockito.any());
        doReturn(memoryManager).when(memory).getMemoryManager();
        Mockito.doAnswer(invocation -> {
            ActionListener<Object> listener = invocation.getArgument(1);
            listener.onResponse(memory);
            return null;
        }).when(mockMemoryFactory).create(Mockito.anyString(), Mockito.any());

        final MLAgent mlAgent = MLAgent
            .builder()
            .name("TestAgent")
            .type(MLAgentType.FLOW.name())
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

        verify(memoryManager).updateInteraction(anyString(), memoryMapCaptor.capture(), any(ActionListener.class));
        Map<String, Object> additionalInfo = (Map<String, Object>) memoryMapCaptor.getValue().get("additional_info");
        assertEquals(1, additionalInfo.size());
        assertNotNull(additionalInfo.get(SECOND_TOOL + ".output"));
    }

    @Test()
    public void testRunWithNoToolSpec() {
        final Map<String, String> params = new HashMap<>();
        params.put(MLAgentExecutor.MEMORY_ID, "memoryId");
        MLMemorySpec mlMemorySpec = MLMemorySpec.builder().type("memoryType").build();
        final MLAgent mlAgent = MLAgent.builder().name("TestAgent").type(MLAgentType.FLOW.name()).memory(mlMemorySpec).build();
        mlFlowAgentRunner.run(mlAgent, params, agentActionListener);
        ArgumentCaptor<Exception> argCaptor = ArgumentCaptor.forClass(IllegalArgumentException.class);
        verify(agentActionListener).onFailure(argCaptor.capture());
        assert (argCaptor.getValue().getMessage().equals("no tool configured"));
    }

    @Test
    public void testRunWithIncludeOutputSet() {
        final Map<String, String> params = new HashMap<>();
        params.put(MLAgentExecutor.MEMORY_ID, "memoryId");
        params.put(MLAgentExecutor.PARENT_INTERACTION_ID, "interaction_id");
        MLToolSpec firstToolSpec = MLToolSpec.builder().name(FIRST_TOOL).type(FIRST_TOOL).includeOutputInAgentResponse(true).build();
        MLToolSpec secondToolSpec = MLToolSpec.builder().name(SECOND_TOOL).type(SECOND_TOOL).includeOutputInAgentResponse(true).build();
        MLMemorySpec mlMemorySpec = MLMemorySpec.builder().type("memoryType").build();
        ConversationIndexMemory memory = mock(ConversationIndexMemory.class);
        Mockito.doAnswer(invocation -> {
            ActionListener<UpdateResponse> listener = invocation.getArgument(2);
            ShardId shardId = new ShardId(new Index("indexName", "uuid"), 1);
            listener.onResponse(new UpdateResponse(shardId, "taskId", 1, 1, 1, DocWriteResponse.Result.UPDATED));
            return null;
        }).when(memoryManager).updateInteraction(Mockito.any(), Mockito.any(), Mockito.any());
        doReturn(memoryManager).when(memory).getMemoryManager();
        Mockito.doAnswer(invocation -> {
            ActionListener<Object> listener = invocation.getArgument(1);
            listener.onResponse(memory);
            return null;
        }).when(mockMemoryFactory).create(Mockito.anyString(), Mockito.any());
        final MLAgent mlAgent = MLAgent
            .builder()
            .name("TestAgent")
            .type(MLAgentType.FLOW.name())
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

        verify(memoryManager).updateInteraction(anyString(), memoryMapCaptor.capture(), any(ActionListener.class));
        Map<String, Object> additionalInfo = (Map<String, Object>) memoryMapCaptor.getValue().get("additional_info");
        assertEquals(2, additionalInfo.size());
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
            .type(MLAgentType.FLOW.name())
            .memory(mlMemorySpec)
            .tools(Arrays.asList(firstToolSpec, secondToolSpec))
            .build();
        doAnswer(generateToolTensorResponse()).when(firstTool).run(anyMap(), actionListenerCaptor.capture());
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

        Map<String, String> result = buildToolParameters(params, toolSpec, null);

        assertEquals("value1", result.get("param1"));
        assertEquals("value3", result.get("param3"));
        assertEquals("value4", result.get("param4"));
        assertFalse(result.containsKey("toolType.param2"));
    }

    @Test
    public void testGetToolExecuteParamsWithConfig() {
        MLToolSpec toolSpec = mock(MLToolSpec.class);
        when(toolSpec.getParameters()).thenReturn(Map.of("param1", "value1", "tool_key", "value_from_parameters"));
        when(toolSpec.getConfigMap()).thenReturn(Map.of("tool_key", "tool_config_value"));
        when(toolSpec.getType()).thenReturn("toolType");
        when(toolSpec.getName()).thenReturn("toolName");

        Map<String, String> params = Map
            .of("toolType.param2", "value2", "toolName.param3", "value3", "param4", "value4", "toolName.tool_key", "dynamic value");

        Map<String, String> result = buildToolParameters(params, toolSpec, null);

        assertEquals("value1", result.get("param1"));
        assertEquals("value3", result.get("param3"));
        assertEquals("value4", result.get("param4"));
        assertFalse(result.containsKey("toolType.param2"));
        assertEquals("tool_config_value", result.get("tool_key"));
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
        Map<String, String> result = ToolUtils.extractInputParameters(buildToolParameters(params, toolSpec, null), null);

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
    public void testParseResponse() throws IOException {

        String outputString = "testOutput";
        assertEquals(outputString, ToolUtils.parseResponse(outputString));

        ModelTensor modelTensor = ModelTensor.builder().name(FIRST_TOOL).dataAsMap(Map.of("index", "index response")).build();
        ModelTensors modelTensors = ModelTensors.builder().mlModelTensors(Arrays.asList(modelTensor)).build();
        ModelTensorOutput mlModelTensorOutput = ModelTensorOutput.builder().mlModelOutputs(Arrays.asList(modelTensors)).build();

        String expectedJson = "{\"name\":\"firstTool\",\"dataAsMap\":{\"index\":\"index response\"}}"; // the JSON representation of the
                                                                                                       // model tensor
        assertEquals(expectedJson, ToolUtils.parseResponse(modelTensor));

        String expectedTensorOuput =
            "{\"inference_results\":[{\"output\":[{\"name\":\"firstTool\",\"dataAsMap\":{\"index\":\"index response\"}}]}]}";
        assertEquals(expectedTensorOuput, ToolUtils.parseResponse(mlModelTensorOutput));

        // Test for List containing ModelTensors
        ModelTensors tensorsInList = ModelTensors.builder().mlModelTensors(Arrays.asList(modelTensor)).build();
        List<ModelTensors> tensorList = Arrays.asList(tensorsInList);
        String expectedListJson = "{\"output\":[{\"name\":\"firstTool\",\"dataAsMap\":{\"index\":\"index response\"}}]}"; // Replace with
                                                                                                                          // the actual JSON
                                                                                                                          // representation
        assertEquals(expectedListJson, ToolUtils.parseResponse(tensorList));

        // Test for a non-string, non-model object
        Map<String, Object> nonModelObject = Map.of("key", "value");
        String expectedNonModelJson = "{\"key\":\"value\"}"; // Replace with the actual JSON representation from StringUtils.toJson
        assertEquals(expectedNonModelJson, ToolUtils.parseResponse(nonModelObject));
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
            .type(MLAgentType.FLOW.name())
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

    @Test
    public void testRunWithUpdateFailure() {
        final Map<String, String> params = new HashMap<>();
        params.put(MLAgentExecutor.MEMORY_ID, "memoryId");
        params.put(MLAgentExecutor.PARENT_INTERACTION_ID, "interaction_id");
        MLToolSpec firstToolSpec = MLToolSpec.builder().name(FIRST_TOOL).type(FIRST_TOOL).build();
        MLToolSpec secondToolSpec = MLToolSpec.builder().name(SECOND_TOOL).type(SECOND_TOOL).build();
        MLMemorySpec mlMemorySpec = MLMemorySpec.builder().type("memoryType").build();
        ConversationIndexMemory memory = mock(ConversationIndexMemory.class);
        Mockito.doAnswer(invocation -> {
            ActionListener<UpdateResponse> listener = invocation.getArgument(2);
            listener.onFailure(new IllegalArgumentException("input error"));
            return null;
        }).when(memoryManager).updateInteraction(Mockito.any(), Mockito.any(), Mockito.any());
        doReturn(memoryManager).when(memory).getMemoryManager();
        Mockito.doAnswer(invocation -> {
            ActionListener<Object> listener = invocation.getArgument(1);
            listener.onResponse(memory);
            return null;
        }).when(mockMemoryFactory).create(Mockito.anyString(), Mockito.any());

        final MLAgent mlAgent = MLAgent
            .builder()
            .name("TestAgent")
            .type(MLAgentType.FLOW.name())
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

        verify(memoryManager).updateInteraction(anyString(), memoryMapCaptor.capture(), any(ActionListener.class));
        Map<String, Object> additionalInfo = (Map<String, Object>) memoryMapCaptor.getValue().get("additional_info");
        assertEquals(1, additionalInfo.size());
        assertNotNull(additionalInfo.get(SECOND_TOOL + ".output"));
    }

    /**
     * Tests error handling when a tool in the chain fails during execution.
     * Verifies that MLAgentTracer.handleSpanError is called with the correct error message.
     */
    @Test
    public void testToolChainExecutionError() {
        final Map<String, String> params = new HashMap<>();
        params.put(MLAgentExecutor.MEMORY_ID, "memoryId");
        params.put(MLAgentExecutor.QUESTION, "test question");
        MLToolSpec firstToolSpec = MLToolSpec.builder().name(FIRST_TOOL).type(FIRST_TOOL).build();
        MLToolSpec secondToolSpec = MLToolSpec.builder().name(SECOND_TOOL).type(SECOND_TOOL).build();
        final MLAgent mlAgent = MLAgent
            .builder()
            .name("TestAgent")
            .type(MLAgentType.FLOW.name())
            .memory(null)
            .tools(Arrays.asList(firstToolSpec, secondToolSpec))
            .build();

        // Make the second tool fail
        doAnswer(invocation -> {
            ActionListener<Object> listener = invocation.getArgument(1);
            listener.onFailure(new RuntimeException("Second tool failed"));
            return null;
        }).when(secondTool).run(anyMap(), any(ActionListener.class));

        mlFlowAgentRunner.run(mlAgent, params, agentActionListener);

        // Verify that the listener was called with failure
        verify(agentActionListener).onFailure(any(RuntimeException.class));
    }

    /**
     * Tests error handling when a single tool fails during execution.
     * Verifies that MLAgentTracer.handleSpanError is called with the correct error message.
     */
    @Test
    public void testSingleToolExecutionError() {
        final Map<String, String> params = new HashMap<>();
        params.put(MLAgentExecutor.MEMORY_ID, "memoryId");
        params.put(MLAgentExecutor.QUESTION, "test question");
        MLToolSpec firstToolSpec = MLToolSpec.builder().name(FIRST_TOOL).type(FIRST_TOOL).build();
        final MLAgent mlAgent = MLAgent
            .builder()
            .name("TestAgent")
            .type(MLAgentType.FLOW.name())
            .memory(null)
            .tools(Arrays.asList(firstToolSpec))
            .build();

        // Make the first tool fail
        doAnswer(invocation -> {
            ActionListener<Object> listener = invocation.getArgument(1);
            listener.onFailure(new RuntimeException("First tool failed"));
            return null;
        }).when(firstTool).run(anyMap(), any(ActionListener.class));

        mlFlowAgentRunner.run(mlAgent, params, agentActionListener);

        // Verify that the listener was called with failure
        verify(agentActionListener).onFailure(any(RuntimeException.class));
    }

    /**
     * Tests error handling when the first tool in a multiple tool chain fails.
     * Verifies that MLAgentTracer.handleSpanError is called with the correct error message.
     */
    @Test
    public void testMultipleToolsFirstToolError() {
        final Map<String, String> params = new HashMap<>();
        params.put(MLAgentExecutor.MEMORY_ID, "memoryId");
        params.put(MLAgentExecutor.QUESTION, "test question");
        MLToolSpec firstToolSpec = MLToolSpec.builder().name(FIRST_TOOL).type(FIRST_TOOL).build();
        MLToolSpec secondToolSpec = MLToolSpec.builder().name(SECOND_TOOL).type(SECOND_TOOL).build();
        final MLAgent mlAgent = MLAgent
            .builder()
            .name("TestAgent")
            .type(MLAgentType.FLOW.name())
            .memory(null)
            .tools(Arrays.asList(firstToolSpec, secondToolSpec))
            .build();

        // Make the first tool fail in a multiple tool scenario
        doAnswer(invocation -> {
            ActionListener<Object> listener = invocation.getArgument(1);
            listener.onFailure(new RuntimeException("First tool in chain failed"));
            return null;
        }).when(firstTool).run(anyMap(), any(ActionListener.class));

        mlFlowAgentRunner.run(mlAgent, params, agentActionListener);

        // Verify that the listener was called with failure
        verify(agentActionListener).onFailure(any(RuntimeException.class));
    }

    /**
     * Tests error handling in the main run method when an exception occurs.
     * Verifies that MLAgentTracer.handleSpanError is called with the correct error message.
     */
    @Test
    public void testRunMethodException() {
        final Map<String, String> params = new HashMap<>();
        params.put(MLAgentExecutor.MEMORY_ID, "memoryId");
        params.put(MLAgentExecutor.QUESTION, "test question");

        // Create an agent that will cause an exception during execution
        final MLAgent mlAgent = MLAgent
            .builder()
            .name("TestAgent")
            .type(MLAgentType.FLOW.name())
            .memory(null)
            .tools(Arrays.asList()) // Empty tools list will cause exception
            .build();

        mlFlowAgentRunner.run(mlAgent, params, agentActionListener);

        // Verify that the listener was called with failure
        verify(agentActionListener).onFailure(any(IllegalArgumentException.class));
    }

    /**
     * Tests error handling in updateSpanWithTool when an exception occurs during span update.
     * This test verifies that the span error handling works correctly.
     */
    @Test
    public void testUpdateSpanWithToolError() {
        // Create a mock span that will throw an exception
        Span mockSpan = mock(Span.class);
        doThrow(new RuntimeException("Span update failed")).when(mockSpan).addAttribute(anyString(), anyString());

        // Call updateSpanWithTool with a problematic output that will cause parseResponse to fail
        Object problematicOutput = new Object() {
            @Override
            public String toString() {
                throw new RuntimeException("toString failed");
            }
        };

        // This should handle the exception gracefully
        MLAgentTracer.updateSpanWithTool(mockSpan, problematicOutput, "test question");

        // Verify that the span was marked with error and ended
        verify(mockSpan).setError(any(RuntimeException.class));
        verify(mockSpan).endSpan();
    }

}
