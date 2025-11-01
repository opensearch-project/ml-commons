/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.task;

import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_ENABLE_INHOUSE_PYTHON_MODEL;
import static org.opensearch.ml.plugin.MachineLearningPlugin.EXECUTE_THREAD_POOL;
import static org.opensearch.ml.plugin.MachineLearningPlugin.STREAM_EXECUTE_THREAD_POOL;

import java.io.IOException;

import org.opensearch.action.ActionListenerResponseHandler;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.ml.action.contextmanagement.ContextManagementTemplateService;
import org.opensearch.ml.action.contextmanagement.ContextManagerFactory;
import org.opensearch.ml.breaker.MLCircuitBreakerService;
import org.opensearch.ml.cluster.DiscoveryNodeHelper;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.contextmanager.ContextManagementTemplate;
import org.opensearch.ml.common.contextmanager.ContextManager;
import org.opensearch.ml.common.contextmanager.ContextManagerConfig;
import org.opensearch.ml.common.contextmanager.ContextManagerHookProvider;
import org.opensearch.ml.common.hooks.HookRegistry;
import org.opensearch.ml.common.input.Input;
import org.opensearch.ml.common.input.execute.agent.AgentMLInput;
import org.opensearch.ml.common.transport.execute.MLExecuteStreamTaskAction;
import org.opensearch.ml.common.transport.execute.MLExecuteTaskAction;
import org.opensearch.ml.common.transport.execute.MLExecuteTaskRequest;
import org.opensearch.ml.common.transport.execute.MLExecuteTaskResponse;
import org.opensearch.ml.engine.MLEngine;
import org.opensearch.ml.engine.indices.MLInputDatasetHandler;
import org.opensearch.ml.stats.ActionName;
import org.opensearch.ml.stats.MLActionLevelStat;
import org.opensearch.ml.stats.MLNodeLevelStat;
import org.opensearch.ml.stats.MLStats;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.StreamTransportResponseHandler;
import org.opensearch.transport.TransportChannel;
import org.opensearch.transport.TransportException;
import org.opensearch.transport.TransportResponseHandler;
import org.opensearch.transport.client.Client;
import org.opensearch.transport.stream.StreamTransportResponse;

import lombok.extern.log4j.Log4j2;

/**
 * MLExecuteTaskRunner is responsible for running execute tasks.
 */
@Log4j2
public class MLExecuteTaskRunner extends MLTaskRunner<MLExecuteTaskRequest, MLExecuteTaskResponse> {
    private final ThreadPool threadPool;
    private final ClusterService clusterService;
    private final Client client;
    private final MLInputDatasetHandler mlInputDatasetHandler;
    protected final DiscoveryNodeHelper nodeHelper;
    private final MLEngine mlEngine;
    private final ContextManagementTemplateService contextManagementTemplateService;
    private final ContextManagerFactory contextManagerFactory;
    private volatile Boolean isPythonModelEnabled;

    public MLExecuteTaskRunner(
        ThreadPool threadPool,
        ClusterService clusterService,
        Client client,
        MLTaskManager mlTaskManager,
        MLStats mlStats,
        MLInputDatasetHandler mlInputDatasetHandler,
        MLTaskDispatcher mlTaskDispatcher,
        MLCircuitBreakerService mlCircuitBreakerService,
        DiscoveryNodeHelper nodeHelper,
        MLEngine mlEngine,
        ContextManagementTemplateService contextManagementTemplateService,
        ContextManagerFactory contextManagerFactory
    ) {
        super(mlTaskManager, mlStats, nodeHelper, mlTaskDispatcher, mlCircuitBreakerService, clusterService);
        this.threadPool = threadPool;
        this.clusterService = clusterService;
        this.client = client;
        this.mlInputDatasetHandler = mlInputDatasetHandler;
        this.nodeHelper = nodeHelper;
        this.mlEngine = mlEngine;
        this.contextManagementTemplateService = contextManagementTemplateService;
        this.contextManagerFactory = contextManagerFactory;
        isPythonModelEnabled = ML_COMMONS_ENABLE_INHOUSE_PYTHON_MODEL.get(this.clusterService.getSettings());
        this.clusterService
            .getClusterSettings()
            .addSettingsUpdateConsumer(ML_COMMONS_ENABLE_INHOUSE_PYTHON_MODEL, it -> isPythonModelEnabled = it);
    }

    @Override
    protected String getTransportActionName() {
        return MLExecuteTaskAction.NAME;
    }

    @Override
    protected String getTransportStreamActionName() {
        return MLExecuteStreamTaskAction.NAME;
    }

    @Override
    protected boolean isStreamingRequest(MLExecuteTaskRequest request) {
        return request.getStreamingChannel() != null;
    }

    @Override
    protected TransportResponseHandler<MLExecuteTaskResponse> getResponseHandler(ActionListener<MLExecuteTaskResponse> listener) {
        return new ActionListenerResponseHandler<>(listener, MLExecuteTaskResponse::new);
    }

    @Override
    protected TransportResponseHandler<MLExecuteTaskResponse> getResponseStreamHandler(MLExecuteTaskRequest request) {
        TransportChannel channel = request.getStreamingChannel();
        return new StreamTransportResponseHandler<MLExecuteTaskResponse>() {
            @Override
            public void handleStreamResponse(StreamTransportResponse<MLExecuteTaskResponse> streamResponse) {
                try {
                    MLExecuteTaskResponse response;
                    while ((response = streamResponse.nextResponse()) != null) {
                        channel.sendResponseBatch(response);
                    }
                    channel.completeStream();
                    streamResponse.close();
                } catch (Exception e) {
                    streamResponse.cancel("Stream error", e);
                }
            }

            @Override
            public void handleException(TransportException exp) {
                try {
                    channel.sendResponse(exp);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }

            @Override
            public String executor() {
                return ThreadPool.Names.SAME;
            }

            @Override
            public MLExecuteTaskResponse read(StreamInput in) throws IOException {
                return new MLExecuteTaskResponse(in);
            }
        };
    }

    /**
     * Execute algorithm and return result.
     * @param request MLExecuteTaskRequest
     * @param listener Action listener
     */
    @Override
    protected void executeTask(MLExecuteTaskRequest request, ActionListener<MLExecuteTaskResponse> listener) {
        TransportChannel channel = request.getStreamingChannel();
        String threadPoolName = (channel != null) ? STREAM_EXECUTE_THREAD_POOL : EXECUTE_THREAD_POOL;
        threadPool.executor(threadPoolName).execute(() -> {
            try {
                mlStats.getStat(MLNodeLevelStat.ML_EXECUTING_TASK_COUNT).increment();
                mlStats.getStat(MLNodeLevelStat.ML_REQUEST_COUNT).increment();
                mlStats
                    .createCounterStatIfAbsent(request.getFunctionName(), ActionName.EXECUTE, MLActionLevelStat.ML_ACTION_REQUEST_COUNT)
                    .increment();

                Input input = request.getInput();
                FunctionName functionName = request.getFunctionName();

                // Handle agent execution with context management
                if (FunctionName.AGENT.equals(functionName) && input instanceof AgentMLInput) {
                    AgentMLInput agentInput = (AgentMLInput) input;
                    String contextManagementName = getEffectiveContextManagementName(agentInput);

                    if (contextManagementName != null && !contextManagementName.trim().isEmpty()) {
                        // Execute agent with context management
                        executeAgentWithContextManagement(request, contextManagementName, channel, listener);
                        return;
                    }
                }

                if (FunctionName.METRICS_CORRELATION.equals(functionName)) {
                    if (!isPythonModelEnabled) {
                        Exception exception = new IllegalArgumentException("This algorithm is not enabled from settings");
                        listener.onFailure(exception);
                        return;
                    }
                }

                // Default execution for all functions (including agents without context management)
                try {
                    mlEngine.execute(input, ActionListener.wrap(output -> {
                        MLExecuteTaskResponse response = new MLExecuteTaskResponse(functionName, output);
                        listener.onResponse(response);
                    }, e -> { listener.onFailure(e); }), channel);
                } catch (Exception e) {
                    log.error("Failed to execute ML function", e);
                    listener.onFailure(e);
                }
            } catch (Exception e) {
                mlStats
                    .createCounterStatIfAbsent(request.getFunctionName(), ActionName.EXECUTE, MLActionLevelStat.ML_ACTION_FAILURE_COUNT)
                    .increment();
                listener.onFailure(e);
            } finally {
                mlStats.getStat(MLNodeLevelStat.ML_EXECUTING_TASK_COUNT).decrement();
            }
        });
    }

    /**
     * Execute agent with context management
     */
    private void executeAgentWithContextManagement(
        MLExecuteTaskRequest request,
        String contextManagementName,
        TransportChannel channel,
        ActionListener<MLExecuteTaskResponse> listener
    ) {
        log.debug("Executing agent with context management: {}", contextManagementName);

        // Lookup context management template
        contextManagementTemplateService.getTemplate(contextManagementName, ActionListener.wrap(template -> {
            if (template == null) {
                listener.onFailure(new IllegalArgumentException("Context management template not found: " + contextManagementName));
                return;
            }

            try {
                // Create context managers from template
                java.util.List<ContextManager> contextManagers = createContextManagers(template);

                // Create HookRegistry with context managers
                HookRegistry hookRegistry = createHookRegistry(contextManagers, template);

                // Set hook registry in agent input
                AgentMLInput agentInput = (AgentMLInput) request.getInput();
                agentInput.setHookRegistry(hookRegistry);

                log
                    .info(
                        "Executing agent with context management template: {} using {} context managers",
                        contextManagementName,
                        contextManagers.size()
                    );

                // Execute agent with hook registry
                try {
                    mlEngine.execute(request.getInput(), ActionListener.wrap(output -> {
                        log.info("Agent execution completed successfully with context management");
                        MLExecuteTaskResponse response = new MLExecuteTaskResponse(request.getFunctionName(), output);
                        listener.onResponse(response);
                    }, error -> {
                        log.error("Agent execution failed with context management", error);
                        listener.onFailure(error);
                    }), channel);
                } catch (Exception e) {
                    log.error("Failed to execute agent with context management", e);
                    listener.onFailure(e);
                }

            } catch (Exception e) {
                log.error("Failed to create context managers from template: {}", contextManagementName, e);
                listener.onFailure(e);
            }
        }, error -> {
            log.error("Failed to retrieve context management template: {}", contextManagementName, error);
            listener.onFailure(error);
        }));
    }

    /**
     * Gets the effective context management name for an agent.
     * Priority: 1) Runtime parameter from execution request, 2) Agent's stored configuration (set by MLAgentExecutor)
     * This follows the same pattern as MCP connectors.
     * 
     * @param agentInput the agent ML input
     * @return the effective context management name, or null if none configured
     */
    private String getEffectiveContextManagementName(AgentMLInput agentInput) {
        // Priority 1: Runtime parameter from execution request (user override)
        String runtimeContextManagementName = agentInput.getContextManagementName();
        if (runtimeContextManagementName != null && !runtimeContextManagementName.trim().isEmpty()) {
            log.debug("Using runtime context management name: {}", runtimeContextManagementName);
            return runtimeContextManagementName;
        }

        // Priority 2: Agent's stored configuration (set by MLAgentExecutor in input parameters)
        if (agentInput.getInputDataset() instanceof org.opensearch.ml.common.dataset.remote.RemoteInferenceInputDataSet) {
            org.opensearch.ml.common.dataset.remote.RemoteInferenceInputDataSet dataset =
                (org.opensearch.ml.common.dataset.remote.RemoteInferenceInputDataSet) agentInput.getInputDataset();

            // Check if context management has already been processed by MLAgentExecutor (for inline templates)
            String contextManagementProcessed = dataset.getParameters().get("context_management_processed");
            if ("true".equals(contextManagementProcessed)) {
                log.debug("Context management already processed by MLAgentExecutor, skipping MLExecuteTaskRunner processing");
                return null; // Skip processing in MLExecuteTaskRunner
            }

            // Handle template references (not processed by MLAgentExecutor)
            String agentContextManagementName = dataset.getParameters().get("context_management");
            if (agentContextManagementName != null && !agentContextManagementName.trim().isEmpty()) {
                log.debug("Using agent-level context management template reference: {}", agentContextManagementName);
                return agentContextManagementName;
            }
        }

        return null;
    }

    /**
     * Create context managers from template configuration
     */
    private java.util.List<ContextManager> createContextManagers(ContextManagementTemplate template) {
        java.util.List<ContextManager> contextManagers = new java.util.ArrayList<>();

        // Iterate through all hooks in the template
        for (java.util.Map.Entry<String, java.util.List<ContextManagerConfig>> entry : template.getHooks().entrySet()) {
            String hookName = entry.getKey();
            java.util.List<ContextManagerConfig> configs = entry.getValue();

            for (ContextManagerConfig config : configs) {
                try {
                    ContextManager manager = contextManagerFactory.createContextManager(config);
                    if (manager != null) {
                        contextManagers.add(manager);
                        log.debug("Created context manager: {} for hook: {}", config.getType(), hookName);
                    } else {
                        log.warn("Failed to create context manager of type: {}", config.getType());
                    }
                } catch (Exception e) {
                    log.error("Error creating context manager of type: {}", config.getType(), e);
                    // Continue with other managers
                }
            }
        }

        log.info("Created {} context managers from template: {}", contextManagers.size(), template.getName());
        return contextManagers;
    }

    /**
     * Create HookRegistry with context managers
     */
    private HookRegistry createHookRegistry(java.util.List<ContextManager> contextManagers, ContextManagementTemplate template) {
        HookRegistry hookRegistry = new HookRegistry();

        if (!contextManagers.isEmpty()) {
            // Create context manager hook provider
            ContextManagerHookProvider hookProvider = new ContextManagerHookProvider(contextManagers);

            // Update hook configuration based on template
            hookProvider.updateHookConfiguration(template.getHooks());

            // Register hooks
            hookProvider.registerHooks(hookRegistry);

            log.debug("Registered context manager hooks for {} managers", contextManagers.size());
        }

        return hookRegistry;
    }

}
