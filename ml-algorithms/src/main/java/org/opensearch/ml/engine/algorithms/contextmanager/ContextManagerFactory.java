/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.contextmanager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.opensearch.ml.common.contextmanager.ContextManagementTemplate;
import org.opensearch.ml.common.contextmanager.ContextManager;
import org.opensearch.ml.common.contextmanager.ContextManagerConfig;
import org.opensearch.transport.client.Client;

import lombok.extern.log4j.Log4j2;

/**
 * Factory class for creating context managers from configuration templates.
 * This class centralizes the creation logic for all context manager types.
 */
@Log4j2
public class ContextManagerFactory {

    /**
     * Create context managers from template configuration
     * 
     * @param template the context management template
     * @param client   the OpenSearch client (required for some managers)
     * @return list of created context managers
     */
    public static List<ContextManager> createContextManagers(ContextManagementTemplate template, Client client) {
        List<ContextManager> managers = new ArrayList<>();

        try {
            // Iterate through all hooks and their configurations
            for (Map.Entry<String, List<ContextManagerConfig>> entry : template.getHooks().entrySet()) {
                String hookName = entry.getKey();
                List<ContextManagerConfig> configs = entry.getValue();

                log.debug("Processing hook '{}' with {} configurations", hookName, configs.size());

                for (ContextManagerConfig config : configs) {
                    try {
                        ContextManager manager = createContextManager(config, client);
                        if (manager != null) {
                            managers.add(manager);
                            log.debug("Created context manager: {} for hook: {}", config.getType(), hookName);
                        }
                    } catch (Exception e) {
                        log
                            .error(
                                "Failed to create context manager of type '{}' for hook '{}': {}",
                                config.getType(),
                                hookName,
                                e.getMessage(),
                                e
                            );
                        // Continue processing other managers instead of failing completely
                    }
                }
            }

            log.info("Successfully created {} context managers from template '{}'", managers.size(), template.getName());
        } catch (Exception e) {
            log.error("Failed to create context managers from template '{}': {}", template.getName(), e.getMessage(), e);
        }

        return managers;
    }

    /**
     * Create a single context manager from configuration
     * 
     * @param config the context manager configuration
     * @param client the OpenSearch client (required for some managers)
     * @return the created context manager
     * @throws IllegalArgumentException if the context manager type is unknown
     */
    public static ContextManager createContextManager(ContextManagerConfig config, Client client) {
        try {
            String type = config.getType();
            Map<String, Object> managerConfig = config.getConfig();

            log.debug("Creating context manager of type: {}", type);

            // Create context manager based on type
            switch (type) {
                case ToolsOutputTruncateManager.TYPE:
                    return createToolsOutputTruncateManager(managerConfig);
                case SummarizationManager.TYPE:
                    return createSummarizationManager(managerConfig, client);
                case SlidingWindowManager.TYPE:
                    return createMemoryManager(managerConfig);
                case "ConversationManager": // Placeholder type
                    return createConversationManager(managerConfig, client);
                default:
                    throw new IllegalArgumentException("Unknown context manager type: " + type);
            }
        } catch (Exception e) {
            log.error("Failed to create context manager: {}", e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Create ToolsOutputTruncateManager
     */
    private static ContextManager createToolsOutputTruncateManager(Map<String, Object> config) {
        log.debug("Creating ToolsOutputTruncateManager with config: {}", config);
        ToolsOutputTruncateManager manager = new ToolsOutputTruncateManager();
        manager.initialize(config != null ? config : new HashMap<>());
        return manager;
    }

    /**
     * Create SummarizationManager
     */
    private static ContextManager createSummarizationManager(Map<String, Object> config, Client client) {
        log.debug("Creating SummarizationManager with config: {}", config);
        SummarizationManager manager = new SummarizationManager(client);
        manager.initialize(config != null ? config : new HashMap<>());
        return manager;
    }

    /**
     * Create SlidingWindowManager (used for MemoryManager type)
     */
    private static ContextManager createMemoryManager(Map<String, Object> config) {
        log.debug("Creating SlidingWindowManager (MemoryManager) with config: {}", config);
        SlidingWindowManager manager = new SlidingWindowManager();
        manager.initialize(config != null ? config : new HashMap<>());
        return manager;
    }

    /**
     * Create ConversationManager (placeholder - using SummarizationManager for now)
     */
    private static ContextManager createConversationManager(Map<String, Object> config, Client client) {
        log.debug("Creating ConversationManager (using SummarizationManager as placeholder) with config: {}", config);
        SummarizationManager manager = new SummarizationManager(client);
        manager.initialize(config != null ? config : new HashMap<>());
        return manager;
    }
}
