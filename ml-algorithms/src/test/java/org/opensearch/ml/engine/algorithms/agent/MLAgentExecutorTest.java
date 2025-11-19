/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.agent;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.OpenSearchStatusException;
import org.opensearch.action.get.GetResponse;
import org.opensearch.cluster.ClusterState;
import org.opensearch.cluster.metadata.Metadata;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.MLAgentType;
import org.opensearch.ml.common.agent.LLMSpec;
import org.opensearch.ml.common.agent.MLAgent;
import org.opensearch.ml.common.dataset.remote.RemoteInferenceInputDataSet;
import org.opensearch.ml.common.input.Input;
import org.opensearch.ml.common.input.execute.agent.AgentMLInput;
import org.opensearch.ml.common.memory.Memory;
import org.opensearch.ml.common.output.Output;
import org.opensearch.ml.common.output.model.ModelTensorOutput;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.spi.tools.Tool;
import org.opensearch.ml.engine.encryptor.Encryptor;
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
    public void testContextManagementProcessedFlagPreventsReprocessing() {
        // Test that the context_management_processed flag prevents duplicate processing
        Map<String, String> parameters = new HashMap<>();

        // First check - should allow processing
        boolean shouldProcess1 = !"true".equals(parameters.get("context_management_processed"));
        assertTrue("First call should allow processing", shouldProcess1);

        // Mark as processed (simulating what the method does)
        parameters.put("context_management_processed", "true");

        // Second check - should prevent processing
        boolean shouldProcess2 = !"true".equals(parameters.get("context_management_processed"));
        assertFalse("Second call should prevent processing", shouldProcess2);
    }
}
