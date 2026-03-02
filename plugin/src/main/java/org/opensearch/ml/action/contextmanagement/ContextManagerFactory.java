/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.contextmanagement;

import java.util.Map;

import org.opensearch.common.inject.Inject;
import org.opensearch.ml.common.contextmanager.ActivationRuleFactory;
import org.opensearch.ml.common.contextmanager.ContextManager;
import org.opensearch.ml.common.contextmanager.ContextManagerConfig;
import org.opensearch.ml.engine.algorithms.contextmanager.SlidingWindowManager;
import org.opensearch.ml.engine.algorithms.contextmanager.SummarizationManager;
import org.opensearch.ml.engine.algorithms.contextmanager.ToolsOutputTruncateManager;
import org.opensearch.transport.client.Client;

import lombok.extern.log4j.Log4j2;

/**
 * Factory for creating context manager instances from configuration.
 * This factory creates the appropriate context manager based on the type
 * specified in the configuration and initializes it with the provided settings.
 */
@Log4j2
public class ContextManagerFactory {

    private final ActivationRuleFactory activationRuleFactory;
    private final Client client;

    @Inject
    public ContextManagerFactory(ActivationRuleFactory activationRuleFactory, Client client) {
        this.activationRuleFactory = activationRuleFactory;
        this.client = client;
    }

    /**
     * Create a context manager instance from configuration
     * @param config The context manager configuration
     * @return The created context manager instance
     * @throws IllegalArgumentException if the manager type is not supported
     */
    public ContextManager createContextManager(ContextManagerConfig config) {
        if (config == null || config.getType() == null) {
            throw new IllegalArgumentException("Context manager configuration and type cannot be null");
        }

        String type = config.getType();
        Map<String, Object> managerConfig = config.getConfig();
        Map<String, Object> activationConfig = config.getActivation();

        log.debug("Creating context manager of type: {}", type);

        ContextManager manager;
        switch (type) {
            case "ToolsOutputTruncateManager":
                manager = createToolsOutputTruncateManager(managerConfig);
                break;
            case "SlidingWindowManager":
                manager = createSlidingWindowManager(managerConfig);
                break;
            case "SummarizationManager":
                manager = createSummarizationManager(managerConfig);
                break;
            default:
                throw new IllegalArgumentException("Unsupported context manager type: " + type);
        }

        // Initialize the manager with configuration
        try {
            // Merge activation and manager config for initialization
            Map<String, Object> fullConfig = new java.util.HashMap<>();
            if (managerConfig != null) {
                fullConfig.putAll(managerConfig);
            }
            if (activationConfig != null) {
                fullConfig.put("activation", activationConfig);
            }

            manager.initialize(fullConfig);
            log.debug("Successfully created and initialized context manager: {}", type);
            return manager;
        } catch (Exception e) {
            log.error("Failed to initialize context manager of type: {}", type, e);
            throw new RuntimeException("Failed to initialize context manager: " + type, e);
        }
    }

    /**
     * Create a ToolsOutputTruncateManager instance
     */
    private ContextManager createToolsOutputTruncateManager(Map<String, Object> config) {
        return new ToolsOutputTruncateManager();
    }

    /**
     * Create a SlidingWindowManager instance
     */
    private ContextManager createSlidingWindowManager(Map<String, Object> config) {
        return new SlidingWindowManager();
    }

    /**
     * Create a SummarizationManager instance
     */
    private ContextManager createSummarizationManager(Map<String, Object> config) {
        return new SummarizationManager(client);
    }

    // Add more factory methods for other context manager types as they are implemented

    // private ContextManager createSummarizingManager(Map<String, Object> config) {
    // return new SummarizingManager();
    // }

    // private ContextManager createSystemPromptAugmentationManager(Map<String, Object> config) {
    // return new SystemPromptAugmentationManager();
    // }
}
