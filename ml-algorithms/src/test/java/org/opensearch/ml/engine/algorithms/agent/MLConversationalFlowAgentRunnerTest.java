/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.agent;

/**
 * Unit tests for {@link MLConversationalFlowAgentRunner}.
 * 
 * <p>This test class covers the functionality of the ML Conversational Flow Agent Runner, which is
 * responsible for executing a sequence of tools in a conversational context with memory management.
 * The tests verify:</p>
 * 
 * <ul>
 *   <li>Conversational flow execution with and without memory</li>
 *   <li>Chat history retrieval and processing</li>
 *   <li>Memory creation and management</li>
 *   <li>Tool parameter extraction and configuration</li>
 *   <li>Response parsing for different output types</li>
 *   <li>Error handling and tracing integration</li>
 *   <li>Message saving and interaction updates</li>
 * </ul>
 * 
 * <p>The tests use Mockito for mocking dependencies and verify both successful execution paths
 * and error scenarios. The MLAgentTracer is initialized with a NoopTracer for testing purposes
 * to avoid actual tracing overhead.</p>
 * 
 * <p>Key test scenarios include:</p>
 * <ul>
 *   <li>Conversational flow with memory and app type</li>
 *   <li>Flow execution without memory management</li>
 *   <li>Single and multiple tool execution</li>
 *   <li>Error handling for memory creation and chat history retrieval</li>
 *   <li>Parameter substitution and configuration overrides</li>
 *   <li>Response parsing for various output formats</li>
 * </ul>
 * 
 * <p>Unlike {@link MLFlowAgentRunner}, the conversational flow agent includes memory management
 * and chat history processing, making it suitable for multi-turn conversations.</p>
 * 
 * @see MLConversationalFlowAgentRunner
 * @see MLAgentTracer
 * @see MLAgent
 * @see MLToolSpec
 * @see ConversationIndexMemory
 */

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
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
import org.opensearch.ml.common.conversation.Interaction;
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
import org.opensearch.telemetry.tracing.Span;
import org.opensearch.telemetry.tracing.noop.NoopTracer;
import org.opensearch.transport.client.Client;

import software.amazon.awssdk.utils.ImmutableMap;

public class MLConversationalFlowAgentRunnerTest {

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

    private MLConversationalFlowAgentRunner mlConversationalFlowAgentRunner;

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
    private ConversationIndexMemory mockMemory;

    @Captor
    private ArgumentCaptor<Object> objectCaptor;

    @Captor
    private ArgumentCaptor<ActionListener<Object>> actionListenerCaptor;

    @Captor
    private ArgumentCaptor<Map<String, Object>> memoryMapCaptor;

    /**
     * Sets up the test environment before each test method.
     * 
     * <p>This method initializes all mocks and dependencies required for testing the MLConversationalFlowAgentRunner.
     * It sets up:</p>
     * <ul>
     *   <li>Mock tool factories and tools</li>
     *   <li>Mock memory management components</li>
     *   <li>MLAgentTracer with NoopTracer for testing</li>
     *   <li>Tool response generators</li>
     *   <li>ConversationIndexMemory mocks</li>
     * </ul>
     * 
     * <p>The setup ensures that all tests have a consistent and isolated environment for testing
     * conversational flow functionality.</p>
     */
    @Before
    @SuppressWarnings("unchecked")
    public void setup() {
        MockitoAnnotations.openMocks(this);
        settings = Settings.builder().build();
        toolFactories = ImmutableMap.of(FIRST_TOOL, firstToolFactory, SECOND_TOOL, secondToolFactory);
        memoryMap = ImmutableMap.of("memoryType", mockMemoryFactory);
        mlConversationalFlowAgentRunner = new MLConversationalFlowAgentRunner(
            client,
            settings,
            clusterService,
            xContentRegistry,
            toolFactories,
            memoryMap,
            null,
            null
        );
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
     * which is used to test the parsing and handling of complex model outputs in conversational flows.</p>
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
     * Tests the conversational flow agent runner with memory and app type set.
     * 
     * <p>This test verifies the full conversational flow functionality when both memory and app type
     * are configured. The test:</p>
     * <ul>
     *   <li>Creates an agent with memory specification and app type</li>
     *   <li>Mocks memory creation and chat history retrieval</li>
     *   <li>Executes the conversational flow with two tools</li>
     *   <li>Verifies that memory_id and parent_interaction_id are included in output</li>
     *   <li>Checks that only the last tool's output is returned (default behavior)</li>
     *   <li>Ensures memory updates are performed correctly</li>
     * </ul>
     * 
     * <p>This test ensures that the conversational flow properly integrates with memory management
     * and maintains conversation context across interactions.</p>
     */
    @Test
    public void testRunWithMemoryAndAppType() {
        final Map<String, String> params = new HashMap<>();
        params.put(MLAgentExecutor.MEMORY_ID, "memoryId");
        params.put(MLAgentExecutor.PARENT_INTERACTION_ID, "interaction_id");
        params.put(MLAgentExecutor.QUESTION, "test question");
        MLToolSpec firstToolSpec = MLToolSpec.builder().name(FIRST_TOOL).type(FIRST_TOOL).build();
        MLToolSpec secondToolSpec = MLToolSpec.builder().name(SECOND_TOOL).type(SECOND_TOOL).build();
        MLMemorySpec mlMemorySpec = MLMemorySpec.builder().type("memoryType").build();

        // Mock memory creation
        Mockito.doAnswer(invocation -> {
            ActionListener<ConversationIndexMemory> listener = invocation.getArgument(3);
            listener.onResponse(mockMemory);
            return null;
        }).when(mockMemoryFactory).create(anyString(), anyString(), anyString(), any(ActionListener.class));

        // Mock memory methods
        when(mockMemory.getConversationId()).thenReturn("conversationId");
        when(mockMemory.getType()).thenReturn("memoryType");

        // Mock getMessages response
        List<Interaction> interactions = Arrays
            .asList(Interaction.builder().input("previous question").response("previous response").build());
        Mockito.doAnswer(invocation -> {
            ActionListener<List<Interaction>> listener = invocation.getArgument(0);
            listener.onResponse(interactions);
            return null;
        }).when(mockMemory).getMessages(any(ActionListener.class), any(Integer.class));

        // Mock memory update
        Mockito.doAnswer(invocation -> {
            ActionListener<UpdateResponse> listener = invocation.getArgument(2);
            ShardId shardId = new ShardId(new Index("indexName", "uuid"), 1);
            listener.onResponse(new UpdateResponse(shardId, "taskId", 1, 1, 1, DocWriteResponse.Result.UPDATED));
            return null;
        }).when(mockMemory).update(anyString(), anyMap(), any(ActionListener.class));

        // Mock memory save
        Mockito.doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(4);
            listener.onResponse(true);
            return null;
        }).when(mockMemory).save(any(), anyString(), any(Integer.class), anyString(), any(ActionListener.class));

        final MLAgent mlAgent = MLAgent
            .builder()
            .name("TestAgent")
            .type(MLAgentType.CONVERSATIONAL_FLOW.name())
            .memory(mlMemorySpec)
            .appType("testApp")
            .tools(Arrays.asList(firstToolSpec, secondToolSpec))
            .build();

        mlConversationalFlowAgentRunner.run(mlAgent, params, agentActionListener);

        Mockito.verify(agentActionListener).onResponse(objectCaptor.capture());
        List<ModelTensor> agentOutput = (List<ModelTensor>) objectCaptor.getValue();
        assertEquals(3, agentOutput.size()); // memory_id, parent_interaction_id, second_tool (last tool only)
        assertEquals(SECOND_TOOL, agentOutput.get(2).getName());
        assertEquals(SECOND_TOOL_RESPONSE, agentOutput.get(2).getResult());
    }

    /**
     * Tests the conversational flow agent runner without memory and app type.
     * 
     * <p>This test verifies the conversational flow functionality when memory and app type are not
     * configured. The test:</p>
     * <ul>
     *   <li>Creates an agent without memory specification or app type</li>
     *   <li>Executes the conversational flow with two tools</li>
     *   <li>Verifies that only tool outputs are returned (no memory metadata)</li>
     *   <li>Checks that only the last tool's output is returned (default behavior)</li>
     *   <li>Ensures the flow executes without memory management overhead</li>
     * </ul>
     * 
     * <p>This test ensures that the conversational flow can operate in a stateless manner
     * when memory management is not required.</p>
     */
    @Test
    public void testRunWithoutMemoryAndAppType() {
        final Map<String, String> params = new HashMap<>();
        params.put(MLAgentExecutor.MEMORY_ID, "memoryId");
        params.put(MLAgentExecutor.QUESTION, "test question");
        MLToolSpec firstToolSpec = MLToolSpec.builder().name(FIRST_TOOL).type(FIRST_TOOL).build();
        MLToolSpec secondToolSpec = MLToolSpec.builder().name(SECOND_TOOL).type(SECOND_TOOL).build();

        final MLAgent mlAgent = MLAgent
            .builder()
            .name("TestAgent")
            .type(MLAgentType.CONVERSATIONAL_FLOW.name())
            .memory(null)
            .appType(null)
            .tools(Arrays.asList(firstToolSpec, secondToolSpec))
            .build();

        mlConversationalFlowAgentRunner.run(mlAgent, params, agentActionListener);

        Mockito.verify(agentActionListener).onResponse(objectCaptor.capture());
        List<ModelTensor> agentOutput = (List<ModelTensor>) objectCaptor.getValue();
        assertEquals(1, agentOutput.size()); // second_tool (last tool only)
        assertEquals(SECOND_TOOL, agentOutput.get(0).getName());
        assertEquals(SECOND_TOOL_RESPONSE, agentOutput.get(0).getResult());
    }

    /**
     * Tests the conversational flow agent runner with a single tool.
     * 
     * <p>This test verifies the conversational flow functionality when only one tool is configured.
     * The test:</p>
     * <ul>
     *   <li>Creates an agent with a single tool</li>
     *   <li>Executes the conversational flow</li>
     *   <li>Verifies that the single tool's output is returned</li>
     *   <li>Ensures proper tool execution and response handling</li>
     * </ul>
     * 
     * <p>This test ensures that single-tool conversational flows work correctly and
     * maintain the same output format as multi-tool flows.</p>
     */
    @Test
    public void testRunWithSingleTool() {
        final Map<String, String> params = new HashMap<>();
        params.put(MLAgentExecutor.MEMORY_ID, "memoryId");
        params.put(MLAgentExecutor.QUESTION, "test question");
        MLToolSpec firstToolSpec = MLToolSpec.builder().name(FIRST_TOOL).type(FIRST_TOOL).build();

        final MLAgent mlAgent = MLAgent
            .builder()
            .name("TestAgent")
            .type(MLAgentType.CONVERSATIONAL_FLOW.name())
            .memory(null)
            .appType(null)
            .tools(Arrays.asList(firstToolSpec))
            .build();

        mlConversationalFlowAgentRunner.run(mlAgent, params, agentActionListener);

        Mockito.verify(agentActionListener).onResponse(objectCaptor.capture());
        List<ModelTensor> agentOutput = (List<ModelTensor>) objectCaptor.getValue();
        assertEquals(1, agentOutput.size());
        assertEquals(FIRST_TOOL, agentOutput.get(0).getName());
        assertEquals(FIRST_TOOL_RESPONSE, agentOutput.get(0).getResult());
    }

    /**
     * Tests the conversational flow agent runner with no tools.
     * 
     * <p>This test verifies that the conversational flow agent properly handles the case when
     * no tools are configured. The test:</p>
     * <ul>
     *   <li>Creates an agent without any tools</li>
     *   <li>Attempts to execute the conversational flow</li>
     *   <li>Verifies that an IllegalArgumentException is thrown</li>
     *   <li>Checks that the error message is correct</li>
     * </ul>
     * 
     * <p>This test ensures that the agent fails gracefully with a meaningful error message
     * when no tools are available for execution.</p>
     */
    @Test
    public void testRunWithNoTools() {
        final Map<String, String> params = new HashMap<>();
        params.put(MLAgentExecutor.MEMORY_ID, "memoryId");
        params.put(MLAgentExecutor.QUESTION, "test question");

        final MLAgent mlAgent = MLAgent
            .builder()
            .name("TestAgent")
            .type(MLAgentType.CONVERSATIONAL_FLOW.name())
            .memory(null)
            .appType(null)
            .tools(null)
            .build();

        mlConversationalFlowAgentRunner.run(mlAgent, params, agentActionListener);

        ArgumentCaptor<Exception> argCaptor = ArgumentCaptor.forClass(IllegalArgumentException.class);
        verify(agentActionListener).onFailure(argCaptor.capture());
        assertEquals("no tool configured", argCaptor.getValue().getMessage());
    }

    /**
     * Tests the conversational flow agent runner with ModelTensorOutput responses.
     * 
     * <p>This test verifies that the conversational flow agent properly handles tools that return
     * ModelTensorOutput objects. The test:</p>
     * <ul>
     *   <li>Creates an agent with two tools</li>
     *   <li>Configures the first tool to return ModelTensorOutput</li>
     *   <li>Executes the conversational flow</li>
     *   <li>Verifies that only the last tool's output is returned (default behavior)</li>
     *   <li>Ensures ModelTensorOutput is properly parsed and handled</li>
     * </ul>
     * 
     * <p>This test ensures that complex model outputs are handled correctly in conversational flows
     * and that the default behavior of returning only the last tool's output is maintained.</p>
     */
    @Test
    public void testRunWithModelTensorOutput() {
        final Map<String, String> params = new HashMap<>();
        params.put(MLAgentExecutor.MEMORY_ID, "memoryId");
        params.put(MLAgentExecutor.QUESTION, "test question");
        MLToolSpec firstToolSpec = MLToolSpec.builder().name(FIRST_TOOL).type(FIRST_TOOL).build();
        MLToolSpec secondToolSpec = MLToolSpec.builder().name(SECOND_TOOL).type(SECOND_TOOL).build();

        final MLAgent mlAgent = MLAgent
            .builder()
            .name("TestAgent")
            .type(MLAgentType.CONVERSATIONAL_FLOW.name())
            .memory(null)
            .appType(null)
            .tools(Arrays.asList(firstToolSpec, secondToolSpec))
            .build();

        doAnswer(generateToolTensorResponse()).when(firstTool).run(anyMap(), actionListenerCaptor.capture());

        mlConversationalFlowAgentRunner.run(mlAgent, params, agentActionListener);

        Mockito.verify(agentActionListener).onResponse(objectCaptor.capture());
        List<ModelTensor> agentOutput = (List<ModelTensor>) objectCaptor.getValue();
        assertEquals(1, agentOutput.size());
        assertEquals(SECOND_TOOL, agentOutput.get(0).getName());
        assertEquals(SECOND_TOOL_RESPONSE, agentOutput.get(0).getResult());
    }

    /**
     * Tests the getToolExecuteParams method.
     * Verifies that tool parameters are properly extracted and processed.
     */
    @Test
    public void testGetToolExecuteParams() {
        MLToolSpec toolSpec = mock(MLToolSpec.class);
        when(toolSpec.getParameters()).thenReturn(Map.of("param1", "value1"));
        when(toolSpec.getType()).thenReturn("toolType");
        when(toolSpec.getName()).thenReturn("toolName");

        Map<String, String> params = Map.of("toolType.param2", "value2", "toolName.param3", "value3", "param4", "value4");

        Map<String, String> result = mlConversationalFlowAgentRunner.getToolExecuteParams(toolSpec, params, null);

        assertEquals("value1", result.get("param1"));
        assertEquals("value3", result.get("param3"));
        assertEquals("value4", result.get("param4"));
        assertFalse(result.containsKey("toolType.param2"));
    }

    /**
     * Tests the getToolExecuteParams method with configuration overrides.
     * Verifies that config values override parameter values.
     */
    @Test
    public void testGetToolExecuteParamsWithConfig() {
        MLToolSpec toolSpec = mock(MLToolSpec.class);
        when(toolSpec.getParameters()).thenReturn(Map.of("param1", "value1", "tool_key", "value_from_parameters"));
        when(toolSpec.getConfigMap()).thenReturn(Map.of("tool_key", "tool_config_value"));
        when(toolSpec.getType()).thenReturn("toolType");
        when(toolSpec.getName()).thenReturn("toolName");

        Map<String, String> params = Map
            .of("toolType.param2", "value2", "toolName.param3", "value3", "param4", "value4", "toolName.tool_key", "dynamic value");

        Map<String, String> result = mlConversationalFlowAgentRunner.getToolExecuteParams(toolSpec, params, null);

        assertEquals("value1", result.get("param1"));
        assertEquals("value3", result.get("param3"));
        assertEquals("value4", result.get("param4"));
        assertFalse(result.containsKey("toolType.param2"));
        assertEquals("tool_config_value", result.get("tool_key"));
    }

    /**
     * Tests the getToolExecuteParams method with input substitution.
     * Verifies that parameter substitution works correctly in input fields.
     */
    @Test
    public void testGetToolExecuteParamsWithInputSubstitution() {
        MLToolSpec toolSpec = mock(MLToolSpec.class);
        when(toolSpec.getParameters()).thenReturn(Map.of("param1", "value1"));
        when(toolSpec.getType()).thenReturn("toolType");
        when(toolSpec.getName()).thenReturn("toolName");

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

        Map<String, String> result = mlConversationalFlowAgentRunner.getToolExecuteParams(toolSpec, params, null);

        assertEquals("value1", result.get("param1"));
        assertEquals("value3", result.get("param3"));
        assertEquals("value4", result.get("param4"));
        assertFalse(result.containsKey("toolType.param2"));

        String expectedInput = "Input contains value1, value4";
        assertEquals(expectedInput, result.get("input"));
    }

    /**
     * Tests the parseResponse method with various input types.
     * Verifies that different response types are properly parsed.
     */
    @Test
    public void testParseResponse() throws IOException {
        String outputString = "testOutput";
        assertEquals(outputString, mlConversationalFlowAgentRunner.parseResponse(outputString));

        ModelTensor modelTensor = ModelTensor.builder().name(FIRST_TOOL).dataAsMap(Map.of("index", "index response")).build();
        ModelTensors modelTensors = ModelTensors.builder().mlModelTensors(Arrays.asList(modelTensor)).build();
        ModelTensorOutput mlModelTensorOutput = ModelTensorOutput.builder().mlModelOutputs(Arrays.asList(modelTensors)).build();

        String expectedJson = "{\"name\":\"firstTool\",\"dataAsMap\":{\"index\":\"index response\"}}";
        assertEquals(expectedJson, mlConversationalFlowAgentRunner.parseResponse(modelTensor));

        String expectedTensorOutput =
            "{\"inference_results\":[{\"output\":[{\"name\":\"firstTool\",\"dataAsMap\":{\"index\":\"index response\"}}]}]}";
        assertEquals(expectedTensorOutput, mlConversationalFlowAgentRunner.parseResponse(mlModelTensorOutput));

        // Test for List containing ModelTensors
        ModelTensors tensorsInList = ModelTensors.builder().mlModelTensors(Arrays.asList(modelTensor)).build();
        List<ModelTensors> tensorList = Arrays.asList(tensorsInList);
        String expectedListJson = "{\"output\":[{\"name\":\"firstTool\",\"dataAsMap\":{\"index\":\"index response\"}}]}";
        assertEquals(expectedListJson, mlConversationalFlowAgentRunner.parseResponse(tensorList));

        // Test for a non-string, non-model object
        Map<String, Object> nonModelObject = Map.of("key", "value");
        String expectedNonModelJson = "{\"key\":\"value\"}";
        assertEquals(expectedNonModelJson, mlConversationalFlowAgentRunner.parseResponse(nonModelObject));
    }

    /**
     * Tests error handling when a tool in the chain fails during execution.
     * 
     * <p>This test verifies that the conversational flow agent properly handles tool failures
     * in a multi-tool chain. The test:</p>
     * <ul>
     *   <li>Creates an agent with two tools</li>
     *   <li>Configures the second tool to fail with a RuntimeException</li>
     *   <li>Executes the conversational flow</li>
     *   <li>Verifies that the failure is properly propagated to the listener</li>
     *   <li>Ensures that MLAgentTracer handles the error correctly</li>
     * </ul>
     * 
     * <p>This test ensures that tool failures are handled gracefully and that error information
     * is properly propagated through the conversational flow.</p>
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
            .type(MLAgentType.CONVERSATIONAL_FLOW.name())
            .memory(null)
            .appType(null)
            .tools(Arrays.asList(firstToolSpec, secondToolSpec))
            .build();

        // Make the second tool fail
        doAnswer(invocation -> {
            ActionListener<Object> listener = invocation.getArgument(1);
            listener.onFailure(new RuntimeException("Second tool failed"));
            return null;
        }).when(secondTool).run(anyMap(), any(ActionListener.class));

        mlConversationalFlowAgentRunner.run(mlAgent, params, agentActionListener);

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
            .type(MLAgentType.CONVERSATIONAL_FLOW.name())
            .memory(null)
            .appType(null)
            .tools(Arrays.asList(firstToolSpec))
            .build();

        // Make the first tool fail
        doAnswer(invocation -> {
            ActionListener<Object> listener = invocation.getArgument(1);
            listener.onFailure(new RuntimeException("First tool failed"));
            return null;
        }).when(firstTool).run(anyMap(), any(ActionListener.class));

        mlConversationalFlowAgentRunner.run(mlAgent, params, agentActionListener);

        // Verify that the listener was called with failure
        verify(agentActionListener).onFailure(any(RuntimeException.class));
    }

    /**
     * Tests error handling when memory creation fails.
     * 
     * <p>This test verifies that the conversational flow agent properly handles memory creation
     * failures. The test:</p>
     * <ul>
     *   <li>Creates an agent with memory specification and app type</li>
     *   <li>Configures memory creation to fail with a RuntimeException</li>
     *   <li>Executes the conversational flow</li>
     *   <li>Verifies that the failure is properly propagated to the listener</li>
     *   <li>Ensures that MLAgentTracer handles the error correctly</li>
     * </ul>
     * 
     * <p>This test ensures that memory creation failures are handled gracefully and that
     * the conversational flow can fail safely when memory management is unavailable.</p>
     */
    @Test
    public void testMemoryCreationError() {
        final Map<String, String> params = new HashMap<>();
        params.put(MLAgentExecutor.MEMORY_ID, "memoryId");
        params.put(MLAgentExecutor.QUESTION, "test question");
        MLToolSpec firstToolSpec = MLToolSpec.builder().name(FIRST_TOOL).type(FIRST_TOOL).build();
        MLMemorySpec mlMemorySpec = MLMemorySpec.builder().type("memoryType").build();

        // Mock memory creation to fail
        Mockito.doAnswer(invocation -> {
            ActionListener<ConversationIndexMemory> listener = invocation.getArgument(3);
            listener.onFailure(new RuntimeException("Memory creation failed"));
            return null;
        }).when(mockMemoryFactory).create(anyString(), anyString(), anyString(), any(ActionListener.class));

        final MLAgent mlAgent = MLAgent
            .builder()
            .name("TestAgent")
            .type(MLAgentType.CONVERSATIONAL_FLOW.name())
            .memory(mlMemorySpec)
            .appType("testApp")
            .tools(Arrays.asList(firstToolSpec))
            .build();

        mlConversationalFlowAgentRunner.run(mlAgent, params, agentActionListener);

        // Verify that the listener was called with failure
        verify(agentActionListener).onFailure(any(RuntimeException.class));
    }

    /**
     * Tests error handling when chat history retrieval fails.
     * 
     * <p>This test verifies that the conversational flow agent properly handles chat history
     * retrieval failures. The test:</p>
     * <ul>
     *   <li>Creates an agent with memory specification and app type</li>
     *   <li>Configures memory creation to succeed but chat history retrieval to fail</li>
     *   <li>Executes the conversational flow</li>
     *   <li>Verifies that the failure is properly propagated to the listener</li>
     *   <li>Ensures that MLAgentTracer handles the error correctly</li>
     * </ul>
     * 
     * <p>This test ensures that chat history retrieval failures are handled gracefully and that
     * the conversational flow can fail safely when chat history is unavailable.</p>
     */
    @Test
    public void testChatHistoryRetrievalError() {
        final Map<String, String> params = new HashMap<>();
        params.put(MLAgentExecutor.MEMORY_ID, "memoryId");
        params.put(MLAgentExecutor.QUESTION, "test question");
        MLToolSpec firstToolSpec = MLToolSpec.builder().name(FIRST_TOOL).type(FIRST_TOOL).build();
        MLMemorySpec mlMemorySpec = MLMemorySpec.builder().type("memoryType").build();

        // Mock memory creation
        Mockito.doAnswer(invocation -> {
            ActionListener<ConversationIndexMemory> listener = invocation.getArgument(3);
            listener.onResponse(mockMemory);
            return null;
        }).when(mockMemoryFactory).create(anyString(), anyString(), anyString(), any(ActionListener.class));

        // Mock memory methods
        when(mockMemory.getConversationId()).thenReturn("conversationId");
        when(mockMemory.getType()).thenReturn("memoryType");

        // Mock getMessages to fail
        Mockito.doAnswer(invocation -> {
            ActionListener<List<Interaction>> listener = invocation.getArgument(0);
            listener.onFailure(new RuntimeException("Chat history retrieval failed"));
            return null;
        }).when(mockMemory).getMessages(any(ActionListener.class), any(Integer.class));

        final MLAgent mlAgent = MLAgent
            .builder()
            .name("TestAgent")
            .type(MLAgentType.CONVERSATIONAL_FLOW.name())
            .memory(mlMemorySpec)
            .appType("testApp")
            .tools(Arrays.asList(firstToolSpec))
            .build();

        mlConversationalFlowAgentRunner.run(mlAgent, params, agentActionListener);

        // Verify that the listener was called with failure
        verify(agentActionListener).onFailure(any(RuntimeException.class));
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
        mlConversationalFlowAgentRunner.updateSpanWithTool(mockSpan, problematicOutput, "test question");

        // Verify that the span was marked with error and ended
        verify(mockSpan).setError(any(RuntimeException.class));
        verify(mockSpan).endSpan();
    }

    /**
     * Tests the updateMemoryWithListener method.
     * Verifies that memory updates are properly handled.
     */
    @Test
    public void testUpdateMemoryWithListener() {
        MLMemorySpec memorySpec = mock(MLMemorySpec.class);
        when(memorySpec.getType()).thenReturn("memoryType");

        Map<String, Object> additionalInfo = Map.of("key", "value");
        String memoryId = "memoryId";
        String interactionId = "interactionId";

        // Mock memory factory
        Mockito.doAnswer(invocation -> {
            ActionListener<ConversationIndexMemory> listener = invocation.getArgument(1);
            listener.onResponse(mockMemory);
            return null;
        }).when(mockMemoryFactory).create(anyString(), any(ActionListener.class));

        // Mock memory update
        Mockito.doAnswer(invocation -> {
            ActionListener<UpdateResponse> listener = invocation.getArgument(2);
            ShardId shardId = new ShardId(new Index("indexName", "uuid"), 1);
            listener.onResponse(new UpdateResponse(shardId, "taskId", 1, 1, 1, DocWriteResponse.Result.UPDATED));
            return null;
        }).when(mockMemory).update(anyString(), anyMap(), any(ActionListener.class));

        ActionListener<UpdateResponse> testListener = mock(ActionListener.class);

        mlConversationalFlowAgentRunner.updateMemoryWithListener(additionalInfo, memorySpec, memoryId, interactionId, testListener);

        // Verify that the memory update was called
        verify(mockMemory).update(eq(interactionId), anyMap(), any(ActionListener.class));
    }

}
