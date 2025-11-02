/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.agent;

import static org.mockito.Mockito.*;
import static org.opensearch.ml.common.CommonValue.ML_AGENT_INDEX;

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.OpenSearchStatusException;
import org.opensearch.ResourceNotFoundException;
import org.opensearch.action.get.GetResponse;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.MLAgentType;
import org.opensearch.ml.common.agent.MLAgent;
import org.opensearch.ml.common.dataset.remote.RemoteInferenceInputDataSet;
import org.opensearch.ml.common.input.execute.agent.AgentMLInput;
import org.opensearch.ml.common.output.Output;
import org.opensearch.ml.common.output.model.ModelTensorOutput;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.spi.memory.Memory;
import org.opensearch.ml.common.spi.tools.Tool;
import org.opensearch.ml.engine.encryptor.Encryptor;
import org.opensearch.ml.engine.indices.MLIndicesHandler;
import org.opensearch.remote.metadata.client.SdkClient;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.TransportChannel;
import org.opensearch.transport.client.Client;

public class MLAgentExecutorTest extends OpenSearchTestCase {

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

    @Mock
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

        when(client.threadPool()).thenReturn(threadPool);
        when(threadPool.getThreadContext()).thenReturn(threadContext);
        when(threadContext.stashContext()).thenReturn(storedContext);
        when(mlFeatureEnabledSetting.isMultiTenancyEnabled()).thenReturn(false);

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
        // Test with non-AgentMLInput
        RemoteInferenceInputDataSet dataset = RemoteInferenceInputDataSet
            .builder()
            .parameters(Collections.singletonMap("test", "value"))
            .build();

        IllegalArgumentException exception = expectThrows(IllegalArgumentException.class, () -> {
            mlAgentExecutor.execute(dataset, listener, channel);
        });

        assertEquals("wrong input", exception.getMessage());
    }

    @Test
    public void testExecuteWithNullInputDataSet() {
        AgentMLInput agentInput = new AgentMLInput("test-agent", null, FunctionName.AGENT, null);

        IllegalArgumentException exception = expectThrows(IllegalArgumentException.class, () -> {
            mlAgentExecutor.execute(agentInput, listener, channel);
        });

        assertEquals("Agent input data can not be empty.", exception.getMessage());
    }

    @Test
    public void testExecuteWithNullParameters() {
        RemoteInferenceInputDataSet dataset = RemoteInferenceInputDataSet.builder().build();
        AgentMLInput agentInput = new AgentMLInput("test-agent", null, FunctionName.AGENT, dataset);

        IllegalArgumentException exception = expectThrows(IllegalArgumentException.class, () -> {
            mlAgentExecutor.execute(agentInput, listener, channel);
        });

        assertEquals("Agent input data can not be empty.", exception.getMessage());
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
        
        OpenSearchStatusException exception = expectThrows(OpenSearchStatusException.class, () -> {
            mlAgentExecutor.execute(agentInput, listener, channel);
        });
        
        assertEquals("You don't have permission to access this resource", exception.getMessage());
        assertEquals(RestStatus.FORBIDDEN, exception.status());
    }

    @Test
    public void testExecuteWithAgentIndexNotFound() {
        Map<String, String> parameters = Collections.singletonMap("question", "test question");
        RemoteInferenceInputDataSet dataset = RemoteInferenceInputDataSet.builder().parameters(parameters).build();
        AgentMLInput agentInput = new AgentMLInput("test-agent", null, FunctionName.AGENT, dataset);

        // Mock that agent index doesn't exist
        mockStatic(MLIndicesHandler.class);
        when(MLIndicesHandler.doesMultiTenantIndexExist(clusterService, false, ML_AGENT_INDEX)).thenReturn(false);

        mlAgentExecutor.execute(agentInput, listener, channel);

        ArgumentCaptor<ResourceNotFoundException> exceptionCaptor = ArgumentCaptor.forClass(ResourceNotFoundException.class);
        verify(listener).onFailure(exceptionCaptor.capture());

        ResourceNotFoundException exception = exceptionCaptor.getValue();
        assertEquals("Agent index not found", exception.getMessage());
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
        MLAgent agent = createTestAgent("UNSUPPORTED_TYPE");

        IllegalArgumentException exception = expectThrows(
            IllegalArgumentException.class,
            () -> { mlAgentExecutor.getAgentRunner(agent, null); }
        );

        assertEquals("Unsupported agent type: UNSUPPORTED_TYPE", exception.getMessage());
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
            .llm(Collections.singletonMap("model_id", "test-model"))
            .tools(Collections.emptyList())
            .parameters(Collections.emptyMap())
            .memory(null)
            .createdTime(Instant.now())
            .lastUpdatedTime(Instant.now())
            .appType("test-app")
            .build();
    }
}
