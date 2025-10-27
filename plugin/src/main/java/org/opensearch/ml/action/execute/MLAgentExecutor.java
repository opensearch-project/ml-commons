/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.execute;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.opensearch.core.action.ActionListener;
import org.opensearch.ml.action.contextmanagement.ContextManagementTemplateService;
import org.opensearch.ml.action.contextmanagement.ContextManagerFactory;
import org.opensearch.ml.common.contextmanager.ContextManagementTemplate;
import org.opensearch.ml.common.contextmanager.ContextManager;
import org.opensearch.ml.common.contextmanager.ContextManagerConfig;
import org.opensearch.ml.common.contextmanager.ContextManagerHookProvider;
import org.opensearch.ml.common.hooks.HookRegistry;
import org.opensearch.ml.common.input.execute.agent.AgentMLInput;
import org.opensearch.ml.common.transport.execute.MLExecuteTaskRequest;
import org.opensearch.ml.common.transport.execute.MLExecuteTaskResponse;
import org.opensearch.ml.engine.MLEngine;
import org.opensearch.transport.TransportService;

import lombok.extern.log4j.Log4j2;

/**
 * MLAgentExecutor is responsible for executing ML agents with optional context management.
 * It creates HookRegistry instances with context managers and passes them to agent runners
 * to enable dynamic context optimization during agent execution.
 */
@Log4j2
public class MLAgentExecutor {
    private final MLEngine mlEngine;
    private final ContextManagementTemplateService contextManagementTemplateService;
    private final ContextManagerFactory contextManagerFactory;

    /**
     * Constructor for MLAgentExecutor
     * @param mlEngine The ML engine for executing agents
     * @param contextManagementTemplateService Service for managing context management templates
     * @param contextManagerFactory Factory for creating context managers
     */
    public MLAgentExecutor(
        MLEngine mlEngine,
        ContextManagementTemplateService contextManagementTemplateService,
        ContextManagerFactory contextManagerFactory
    ) {
        this.mlEngine = mlEngine;
        this.contextManagementTemplateService = contextManagementTemplateService;
        this.contextManagerFactory = contextManagerFactory;
    }

    /**
     * Execute an agent with optional context management
     * @param request The ML execute task request
     * @param contextManagementName Optional context management template name
     * @param transportService The transport service
     * @param listener Action listener for the response
     */
    public void executeAgent(
        MLExecuteTaskRequest request,
        String contextManagementName,
        TransportService transportService,
        ActionListener<MLExecuteTaskResponse> listener
    ) {
        if (contextManagementName != null && !contextManagementName.trim().isEmpty()) {
            log.debug("Executing agent with context management: {}", contextManagementName);
            executeWithContextManagement(request, contextManagementName, transportService, listener);
        } else {
            log.debug("Executing agent without context management");
            executeWithoutContextManagement(request, transportService, listener);
        }
    }

    /**
     * Execute agent with context management template
     */
    private void executeWithContextManagement(
        MLExecuteTaskRequest request,
        String contextManagementName,
        TransportService transportService,
        ActionListener<MLExecuteTaskResponse> listener
    ) {
        // Lookup context management template
        contextManagementTemplateService.getTemplate(contextManagementName, ActionListener.wrap(template -> {
            if (template == null) {
                listener.onFailure(new IllegalArgumentException("Context management template not found: " + contextManagementName));
                return;
            }

            try {
                // Create context managers from template
                List<ContextManager> contextManagers = createContextManagers(template);

                // Create HookRegistry with context managers
                HookRegistry hookRegistry = createHookRegistry(contextManagers, template);

                // Execute agent with hook registry
                executeAgentWithHooks(request, hookRegistry, transportService, listener);

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
     * Execute agent without context management (backward compatibility)
     */
    private void executeWithoutContextManagement(
        MLExecuteTaskRequest request,
        TransportService transportService,
        ActionListener<MLExecuteTaskResponse> listener
    ) {
        // Execute with empty hook registry for backward compatibility
        HookRegistry hookRegistry = new HookRegistry();
        executeAgentWithHooks(request, hookRegistry, transportService, listener);
    }

    /**
     * Create context managers from template configuration
     */
    private List<ContextManager> createContextManagers(ContextManagementTemplate template) {
        List<ContextManager> contextManagers = new ArrayList<>();

        // Iterate through all hooks in the template
        for (Map.Entry<String, List<ContextManagerConfig>> entry : template.getHooks().entrySet()) {
            String hookName = entry.getKey();
            List<ContextManagerConfig> configs = entry.getValue();

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
    private HookRegistry createHookRegistry(List<ContextManager> contextManagers, ContextManagementTemplate template) {
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

    /**
     * Execute agent with hook registry
     * This method integrates with the existing agent execution pipeline
     */
    private void executeAgentWithHooks(
        MLExecuteTaskRequest request,
        HookRegistry hookRegistry,
        TransportService transportService,
        ActionListener<MLExecuteTaskResponse> listener
    ) {
        try {
            // Extract agent input
            AgentMLInput agentInput = (AgentMLInput) request.getInput();

            // Set hook registry in agent input so agent runners can access it
            agentInput.setHookRegistry(hookRegistry);

            // Execute through the ML engine with the enhanced request
            mlEngine.execute(request.getInput(), ActionListener.wrap(output -> {
                MLExecuteTaskResponse response = new MLExecuteTaskResponse(request.getFunctionName(), output);
                listener.onResponse(response);
            }, error -> {
                log.error("Agent execution failed", error);
                listener.onFailure(error);
            }), null);

        } catch (Exception e) {
            log.error("Failed to execute agent with hooks", e);
            listener.onFailure(e);
        }
    }
}
