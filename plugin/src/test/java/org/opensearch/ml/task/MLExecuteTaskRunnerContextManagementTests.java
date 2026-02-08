/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.task;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.opensearch.ml.common.agent.MLAgent.CONTEXT_MANAGEMENT_NAME_FIELD;
import static org.opensearch.ml.engine.algorithms.agent.MLAgentExecutor.CONTEXT_MANAGEMENT_PROCESSED;

import java.util.HashMap;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.action.get.GetRequest;
import org.opensearch.action.get.GetResponse;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.ClusterSettings;
import org.opensearch.common.settings.Settings;
import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_ENABLE_INHOUSE_PYTHON_MODEL;
import static org.opensearch.ml.utils.TestHelper.clusterSetting;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.core.action.ActionListener;
import org.opensearch.ml.action.contextmanagement.ContextManagementTemplateService;
import org.opensearch.ml.action.contextmanagement.ContextManagerFactory;
import org.opensearch.ml.breaker.MLCircuitBreakerService;
import org.opensearch.ml.cluster.DiscoveryNodeHelper;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.agent.MLAgent;
import org.opensearch.ml.common.contextmanager.ContextManagementTemplate;
import org.opensearch.ml.common.contextmanager.ContextManager;
import org.opensearch.ml.common.contextmanager.ContextManagerConfig;
import org.opensearch.ml.common.dataset.remote.RemoteInferenceInputDataSet;
import org.opensearch.ml.common.input.execute.agent.AgentMLInput;
import org.opensearch.ml.common.output.MLOutput;
import org.opensearch.ml.common.transport.execute.MLExecuteTaskRequest;
import org.opensearch.ml.common.transport.execute.MLExecuteTaskResponse;
import org.opensearch.ml.engine.MLEngine;
import org.opensearch.ml.engine.indices.MLInputDatasetHandler;
import org.opensearch.ml.stats.MLStats;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.client.Client;

/**
 * Unit tests for context management override logic in MLExecuteTaskRunner.
 * These tests verify the decision-making logic for which context management configuration to use.
 *
 * Priority Order:
 * 1. Runtime parameter (parameters.context_management_name)
 * 2. Agent's stored template reference (agent.context_management_name)
 * 3. Agent's inline template (agent.context_management)
 *
 * The CONTEXT_MANAGEMENT_PROCESSED flag prevents double-processing when MLExecuteTaskRunner
 * has already handled context management at priority 1 or 2.
 */
public class MLExecuteTaskRunnerContextManagementTests {

    @Mock
    private ThreadPool threadPool;
    @Mock
    private ClusterService clusterService;
    @Mock
    private Client client;
    @Mock
    private MLTaskManager mlTaskManager;
    @Mock
    private MLStats mlStats;
    @Mock
    private MLInputDatasetHandler mlInputDatasetHandler;
    @Mock
    private MLTaskDispatcher mlTaskDispatcher;
    @Mock
    private MLCircuitBreakerService mlCircuitBreakerService;
    @Mock
    private DiscoveryNodeHelper nodeHelper;
    @Mock
    private MLEngine mlEngine;
    @Mock
    private ContextManagementTemplateService contextManagementTemplateService;
    @Mock
    private ContextManagerFactory contextManagerFactory;
    private ThreadContext threadContext;

    private MLExecuteTaskRunner mlExecuteTaskRunner;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);

        // Mock cluster service settings with required ML settings
        Settings settings = Settings.builder().build();
        ClusterSettings clusterSettings = clusterSetting(settings, ML_COMMONS_ENABLE_INHOUSE_PYTHON_MODEL);
        when(clusterService.getSettings()).thenReturn(settings);
        when(clusterService.getClusterSettings()).thenReturn(clusterSettings);

        // Create a real ThreadContext instead of mocking it (ThreadContext is final)
        threadContext = new ThreadContext(settings);
        when(client.threadPool()).thenReturn(threadPool);
        when(threadPool.getThreadContext()).thenReturn(threadContext);

        // Mock thread pool executor to run tasks synchronously for testing
        java.util.concurrent.ExecutorService executorService = mock(java.util.concurrent.ExecutorService.class);
        doAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(0);
            runnable.run();
            return null;
        }).when(executorService).execute(any(Runnable.class));
        when(threadPool.executor(any())).thenReturn(executorService);

        // Mock MLStats
        org.opensearch.ml.stats.MLStat mockStat = mock(org.opensearch.ml.stats.MLStat.class);
        when(mlStats.getStat(any())).thenReturn(mockStat);
        when(mlStats.createCounterStatIfAbsent(any(), any(), any())).thenReturn(mockStat);

        // Create the task runner with mocked dependencies
        mlExecuteTaskRunner = new MLExecuteTaskRunner(
            threadPool,
            clusterService,
            client,
            mlTaskManager,
            mlStats,
            mlInputDatasetHandler,
            mlTaskDispatcher,
            mlCircuitBreakerService,
            nodeHelper,
            mlEngine,
            contextManagementTemplateService,
            contextManagerFactory
        );
    }

    /**
     * Test Priority 1: When user provides runtime context_management_name, it should take priority
     * over agent's registered context_management_name.
     *
     * Scenario:
     *   - Agent stored in index has context_management_name = "agent_default_template" (Priority 2)
     *   - User provides runtime override = "runtime_override_template" (Priority 1)
     * Expected:
     *   - Should use "runtime_override_template" (Priority 1 wins)
     *   - Agent should NOT be fetched from index (Priority 1 short-circuits the lookup)
     */
    @Test
    public void testRuntimeOverrideTakesPriority() throws Exception {
        String agentId = "test_agent_id";
        String runtimeOverride = "runtime_override_template";
        String agentDefaultTemplate = "agent_default_template";

        // Setup: User provides runtime override
        Map<String, String> parameters = new HashMap<>();
        parameters.put("question", "test question");

        RemoteInferenceInputDataSet inputDataSet = new RemoteInferenceInputDataSet(parameters);
        AgentMLInput agentInput = new AgentMLInput(agentId, null, FunctionName.AGENT, inputDataSet, false);
        // This is the runtime override - it's set on the AgentMLInput object (Priority 1)
        agentInput.setContextManagementName(runtimeOverride);

        MLExecuteTaskRequest request = new MLExecuteTaskRequest(FunctionName.AGENT, agentInput, false);

        // Mock the agent stored in the index with its own default template (Priority 2)
        // This agent has context_management_name = "agent_default_template"
        GetResponse getResponse = mock(GetResponse.class);
        when(getResponse.isExists()).thenReturn(true);
        when(getResponse.getSourceAsString()).thenReturn(
            "{\"name\":\"test_agent\",\"type\":\"CONVERSATIONAL\",\"context_management_name\":\"" + agentDefaultTemplate + "\"}"
        );
        doAnswer(invocation -> {
            ActionListener<GetResponse> listener = invocation.getArgument(1);
            listener.onResponse(getResponse);
            return null;
        }).when(client).get(any(GetRequest.class), any());

        // Mock template service for the runtime override
        ContextManagementTemplate mockTemplate = createMockTemplate(runtimeOverride);
        doAnswer(invocation -> {
            ActionListener<ContextManagementTemplate> listener = invocation.getArgument(1);
            listener.onResponse(mockTemplate);
            return null;
        }).when(contextManagementTemplateService).getTemplate(eq(runtimeOverride), any());

        // Mock context manager factory
        ContextManager mockManager = mock(ContextManager.class);
        when(contextManagerFactory.createContextManager(any())).thenReturn(mockManager);

        // Mock ML engine execution
        MLOutput mockOutput = mock(MLOutput.class);
        doAnswer(invocation -> {
            ActionListener<MLOutput> listener = invocation.getArgument(1);
            listener.onResponse(mockOutput);
            return null;
        }).when(mlEngine).execute(any(), any(), any());

        // Execute
        ActionListener<MLExecuteTaskResponse> listener = mock(ActionListener.class);
        mlExecuteTaskRunner.executeTask(request, listener);

        // Verify: Runtime override template was used (Priority 1)
        verify(contextManagementTemplateService, times(1)).getTemplate(eq(runtimeOverride), any());

        // Verify: Agent's default template was NOT used (Priority 1 short-circuits Priority 2)
        verify(contextManagementTemplateService, never()).getTemplate(eq(agentDefaultTemplate), any());

        // Verify: Agent was NOT fetched from index (Priority 1 returns immediately)
        verify(client, never()).get(any(GetRequest.class), any());

        // Verify: CONTEXT_MANAGEMENT_PROCESSED flag was set
        assertEquals("true", parameters.get(CONTEXT_MANAGEMENT_PROCESSED));

        // Verify: Success response was sent
        verify(listener, times(1)).onResponse(any());
        verify(listener, never()).onFailure(any());
    }

    /**
     * Test Priority 2: Agent with template reference, no runtime override.
     * Should fetch agent from index and use agent's context_management_name.
     *
     * Scenario: Agent has "agent_template", no runtime override
     * Expected: Should fetch agent and use "agent_template"
     */
    @Test
    public void testAgentTemplateRefWithoutOverride() throws Exception {
        String agentId = "test_agent_id";
        String agentTemplateRef = "agent_template";

        // Setup: No runtime override in parameters
        Map<String, String> parameters = new HashMap<>();
        parameters.put("question", "test question");

        RemoteInferenceInputDataSet inputDataSet = new RemoteInferenceInputDataSet(parameters);
        AgentMLInput agentInput = new AgentMLInput(agentId, null, FunctionName.AGENT, inputDataSet, false);

        MLExecuteTaskRequest request = new MLExecuteTaskRequest(FunctionName.AGENT, agentInput, false);

        // Mock agent retrieval from index - needs proper MLAgent JSON format
        // Include LLM field to make it a valid conversational agent
        String agentJson = "{"
            + "\"name\":\"test_agent\","
            + "\"type\":\"conversational\","
            + "\"context_management_name\":\"" + agentTemplateRef + "\","
            + "\"llm\":{\"model_id\":\"test-model-id\",\"parameters\":{}}"
            + "}";

        GetResponse getResponse = mock(GetResponse.class);
        when(getResponse.isExists()).thenReturn(true);
        when(getResponse.getSourceAsString()).thenReturn(agentJson);

        doAnswer(invocation -> {
            ActionListener<GetResponse> listener = invocation.getArgument(1);
            listener.onResponse(getResponse);
            return null;
        }).when(client).get(any(GetRequest.class), any());

        // Mock context management template service
        ContextManagementTemplate mockTemplate = createMockTemplate(agentTemplateRef);
        doAnswer(invocation -> {
            ActionListener<ContextManagementTemplate> listener = invocation.getArgument(1);
            listener.onResponse(mockTemplate);
            return null;
        }).when(contextManagementTemplateService).getTemplate(eq(agentTemplateRef), any());

        // Mock context manager factory
        ContextManager mockManager = mock(ContextManager.class);
        when(contextManagerFactory.createContextManager(any())).thenReturn(mockManager);

        // Mock ML engine execution
        MLOutput mockOutput = mock(MLOutput.class);
        doAnswer(invocation -> {
            ActionListener<MLOutput> listener = invocation.getArgument(1);
            listener.onResponse(mockOutput);
            return null;
        }).when(mlEngine).execute(any(), any(), any());

        // Execute
        ActionListener<MLExecuteTaskResponse> listener = mock(ActionListener.class);
        mlExecuteTaskRunner.executeTask(request, listener);

        // Verify: Agent was fetched from index
        verify(client, times(1)).get(any(GetRequest.class), any());

        // Verify: Context management template service was called with agent's template
        verify(contextManagementTemplateService, times(1)).getTemplate(eq(agentTemplateRef), any());
        // Verify: CONTEXT_MANAGEMENT_PROCESSED flag was set
        assertEquals("true", parameters.get(CONTEXT_MANAGEMENT_PROCESSED));

        // Verify: Success response was sent
        verify(listener, times(1)).onResponse(any());
        verify(listener, never()).onFailure(any());
    }

    /**
     * Test Priority 3: Agent with inline context_management object, no runtime override.
     * Should allow MLAgentExecutor to process the inline template during normal execution.
     *
     * Scenario:
     *   - Agent has inline "context_management" object (Priority 3) - not a template reference
     *   - No runtime override provided (Priority 1)
     *   - No "context_management_name" field (Priority 2)
     * Expected:
     *   - MLExecuteTaskRunner returns null (doesn't handle inline templates)
     *   - Normal execution proceeds (mlEngine.execute is called)
     *   - MLAgentExecutor will process the inline context_management during normal execution
     */
    @Test
    public void testAgentInlineTemplateWithoutOverride() throws Exception {
        String agentId = "test_agent_id";

        // Setup: No runtime override in parameters
        Map<String, String> parameters = new HashMap<>();
        parameters.put("question", "test question");

        RemoteInferenceInputDataSet inputDataSet = new RemoteInferenceInputDataSet(parameters);
        AgentMLInput agentInput = new AgentMLInput(agentId, null, FunctionName.AGENT, inputDataSet, false);

        MLExecuteTaskRequest request = new MLExecuteTaskRequest(FunctionName.AGENT, agentInput, false);

        // Mock agent retrieval - agent has inline context_management object (not context_management_name)
        // This represents an agent registered with inline context management configuration
        String agentJson = "{"
            + "\"name\":\"RAG Agent\","
            + "\"type\":\"conversational\","
            + "\"llm\":{\"model_id\":\"test-model-id\",\"parameters\":{}},"
            + "\"context_management\":{"
                + "\"name\":\"customer-service-optimizer\","
                + "\"description\":\"Optimized context management for customer service\","
                + "\"hooks\":{"
                    + "\"PRE_LLM\":[{\"type\":\"SlidingWindowManager\",\"config\":{\"max_messages\":6}}],"
                    + "\"POST_TOOL\":[{\"type\":\"ToolsOutputTruncateManager\",\"config\":{\"max_output_length\":50000}}]"
                + "}"
            + "}"
        + "}";

        GetResponse getResponse = mock(GetResponse.class);
        when(getResponse.isExists()).thenReturn(true);
        when(getResponse.getSourceAsString()).thenReturn(agentJson);

        doAnswer(invocation -> {
            ActionListener<GetResponse> listener = invocation.getArgument(1);
            listener.onResponse(getResponse);
            return null;
        }).when(client).get(any(GetRequest.class), any());

        // Mock ML engine execution for normal flow
        MLOutput mockOutput = mock(MLOutput.class);
        doAnswer(invocation -> {
            ActionListener<MLOutput> listener = invocation.getArgument(1);
            listener.onResponse(mockOutput);
            return null;
        }).when(mlEngine).execute(any(), any(), any());

        // Execute
        ActionListener<MLExecuteTaskResponse> listener = mock(ActionListener.class);
        mlExecuteTaskRunner.executeTask(request, listener);

        // Verify: Agent was fetched from index (Priority 2 check)
        verify(client, times(1)).get(any(GetRequest.class), any());

        // Verify: Context management template service was NOT called
        // (MLExecuteTaskRunner doesn't handle inline templates - only template references)
        verify(contextManagementTemplateService, never()).getTemplate(any(), any());

        // Verify: ML engine execute was called (normal execution)
        // The inline context_management will be processed by MLAgentExecutor during this execution
        verify(mlEngine, times(1)).execute(any(), any(), any());

        // Verify: Success response was sent
        verify(listener, times(1)).onResponse(any());
        verify(listener, never()).onFailure(any());

        // Note: CONTEXT_MANAGEMENT_PROCESSED flag will be set by MLAgentExecutor during mlEngine.execute()
        // Since we're mocking mlEngine.execute, we don't verify the flag here (it's tested in MLAgentExecutor tests)
    }

    /**
     * Test: When CONTEXT_MANAGEMENT_PROCESSED flag is already set, should skip template lookup.
     * This prevents double-processing of context management configuration.
     */
    @Test
    public void testProcessedFlagPreventsReprocessing() throws Exception {
        String agentId = "test_agent_id";

        Map<String, String> parameters = new HashMap<>();
        parameters.put("question", "test question");
        parameters.put(CONTEXT_MANAGEMENT_PROCESSED, "true");

        RemoteInferenceInputDataSet inputDataSet = new RemoteInferenceInputDataSet(parameters);
        AgentMLInput agentInput = new AgentMLInput(agentId, null, FunctionName.AGENT, inputDataSet, false);

        MLExecuteTaskRequest request = new MLExecuteTaskRequest(FunctionName.AGENT, agentInput, false);

        // Mock agent retrieval - agent has NO context_management_name (will check fallback)
        GetResponse getResponse = mock(GetResponse.class);
        when(getResponse.isExists()).thenReturn(true);
        when(getResponse.getSourceAsString()).thenReturn("{\"name\":\"test_agent\",\"type\":\"conversational\"}");

        doAnswer(invocation -> {
            ActionListener<GetResponse> listener = invocation.getArgument(1);
            listener.onResponse(getResponse);
            return null;
        }).when(client).get(any(GetRequest.class), any());

        // Mock ML engine execution for normal flow
        MLOutput mockOutput = mock(MLOutput.class);
        doAnswer(invocation -> {
            ActionListener<MLOutput> listener = invocation.getArgument(1);
            listener.onResponse(mockOutput);
            return null;
        }).when(mlEngine).execute(any(), any(), any());

        // Execute
        ActionListener<MLExecuteTaskResponse> listener = mock(ActionListener.class);
        mlExecuteTaskRunner.executeTask(request, listener);

        // Verify: Agent was fetched (priority 2 check)
        verify(client, times(1)).get(any(GetRequest.class), any());

        // Verify: Context management template service was NOT called (flag in fallback prevents it)
        verify(contextManagementTemplateService, never()).getTemplate(any(), any());

        // Verify: ML engine execute was called (normal execution)
        verify(mlEngine, times(1)).execute(any(), any(), any());

        // Verify: Success response was sent
        verify(listener, times(1)).onResponse(any());
    }

    /**
     * Test: Context management template not found should result in error.
     */
    @Test
    public void testContextManagementTemplateNotFound() throws Exception {
        String agentId = "test_agent_id";
        String runtimeOverride = "non_existent_template";

        Map<String, String> parameters = new HashMap<>();
        parameters.put("question", "test question");

        RemoteInferenceInputDataSet inputDataSet = new RemoteInferenceInputDataSet(parameters);
        AgentMLInput agentInput = new AgentMLInput(agentId, null, FunctionName.AGENT, inputDataSet, false);
        agentInput.setContextManagementName(runtimeOverride);

        MLExecuteTaskRequest request = new MLExecuteTaskRequest(FunctionName.AGENT, agentInput, false);

        // Mock context management template service returning null (template not found)
        doAnswer(invocation -> {
            ActionListener<ContextManagementTemplate> listener = invocation.getArgument(1);
            listener.onResponse(null);
            return null;
        }).when(contextManagementTemplateService).getTemplate(eq(runtimeOverride), any());

        // Execute
        ActionListener<MLExecuteTaskResponse> listener = mock(ActionListener.class);
        mlExecuteTaskRunner.executeTask(request, listener);

        // Verify: Error was returned
        ArgumentCaptor<Exception> exceptionCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(listener, times(1)).onFailure(exceptionCaptor.capture());

        Exception exception = exceptionCaptor.getValue();
        assertNotNull(exception);
        assertEquals("Context management template not found: " + runtimeOverride, exception.getMessage());
    }

    /**
     * Test: Agent not found should fallback to normal execution.
     */
    @Test
    public void testAgentNotFoundFallbackToNormalExecution() throws Exception {
        String agentId = "non_existent_agent";

        Map<String, String> parameters = new HashMap<>();
        parameters.put("question", "test question");

        RemoteInferenceInputDataSet inputDataSet = new RemoteInferenceInputDataSet(parameters);
        AgentMLInput agentInput = new AgentMLInput(agentId, null, FunctionName.AGENT, inputDataSet, false);

        MLExecuteTaskRequest request = new MLExecuteTaskRequest(FunctionName.AGENT, agentInput, false);

        // Mock agent retrieval - agent not found
        GetResponse getResponse = mock(GetResponse.class);
        when(getResponse.isExists()).thenReturn(false);

        doAnswer(invocation -> {
            ActionListener<GetResponse> listener = invocation.getArgument(1);
            listener.onResponse(getResponse);
            return null;
        }).when(client).get(any(GetRequest.class), any());

        // Mock ML engine execution for normal flow
        MLOutput mockOutput = mock(MLOutput.class);
        doAnswer(invocation -> {
            ActionListener<MLOutput> listener = invocation.getArgument(1);
            listener.onResponse(mockOutput);
            return null;
        }).when(mlEngine).execute(any(), any(), any());

        // Execute
        ActionListener<MLExecuteTaskResponse> listener = mock(ActionListener.class);
        mlExecuteTaskRunner.executeTask(request, listener);

        // Verify: Agent lookup was attempted
        verify(client, times(1)).get(any(GetRequest.class), any());

        // Verify: Context management was not called (agent not found)
        verify(contextManagementTemplateService, never()).getTemplate(any(), any());

        // Verify: Normal execution proceeded
        verify(mlEngine, times(1)).execute(any(), any(), any());

        // Verify: Success response was sent
        verify(listener, times(1)).onResponse(any());
    }

    /**
     * Helper method to create a mock ContextManagementTemplate
     */
    private ContextManagementTemplate createMockTemplate(String templateName) {
        ContextManagementTemplate template = mock(ContextManagementTemplate.class);
        when(template.getName()).thenReturn(templateName);

        // Create mock hooks map
        Map<String, java.util.List<ContextManagerConfig>> hooks = new HashMap<>();
        java.util.List<ContextManagerConfig> configs = new java.util.ArrayList<>();
        ContextManagerConfig config = mock(ContextManagerConfig.class);
        when(config.getType()).thenReturn("mock_type");
        configs.add(config);
        hooks.put("pre_tool", configs);

        when(template.getHooks()).thenReturn(hooks);
        return template;
    }
}
