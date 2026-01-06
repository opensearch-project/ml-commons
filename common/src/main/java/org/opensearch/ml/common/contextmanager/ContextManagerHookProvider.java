/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.contextmanager;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.opensearch.ml.common.hooks.EnhancedPostToolEvent;
import org.opensearch.ml.common.hooks.HookProvider;
import org.opensearch.ml.common.hooks.HookRegistry;
import org.opensearch.ml.common.hooks.PostMemoryEvent;
import org.opensearch.ml.common.hooks.PreLLMEvent;

import lombok.extern.log4j.Log4j2;

/**
 * Hook provider that integrates context managers with the hook registry.
 * This class manages the execution of context managers based on hook events.
 */
@Log4j2
public class ContextManagerHookProvider implements HookProvider {
    private final List<ContextManager> contextManagers;
    private final Map<String, List<ContextManager>> hookToManagersMap;

    /**
     * Constructor for ContextManagerHookProvider
     * @param contextManagers List of context managers to register
     */
    public ContextManagerHookProvider(List<ContextManager> contextManagers) {
        this.contextManagers = new ArrayList<>(contextManagers);
        this.hookToManagersMap = new ConcurrentHashMap<>();
        // Note: Hook organization will be done via updateHookConfiguration() 
        // when template configuration is available
    }

    /**
     * Constructor for ContextManagerHookProvider with initial configuration
     * @param contextManagers List of context managers to register
     * @param hookConfiguration Map of hook names to manager configurations
     */
    public ContextManagerHookProvider(List<ContextManager> contextManagers, Map<String, List<ContextManagerConfig>> hookConfiguration) {
        this.contextManagers = new ArrayList<>(contextManagers);
        this.hookToManagersMap = new ConcurrentHashMap<>();
        
        if (hookConfiguration != null && !hookConfiguration.isEmpty()) {
            organizeManagersByHookConfiguration(hookConfiguration);
        }
    }

    /**
     * Register hook callbacks with the provided registry
     * @param registry The HookRegistry to register callbacks with
     */
    @Override
    public void registerHooks(HookRegistry registry) {
        // Register callbacks for each hook type
        registry.addCallback(PreLLMEvent.class, this::handlePreLLM);
        registry.addCallback(EnhancedPostToolEvent.class, this::handlePostTool);
        registry.addCallback(PostMemoryEvent.class, this::handlePostMemory);

        log.info("Registered context manager hooks for {} managers", contextManagers.size());
    }

    /**
     * Handle PreLLM hook events
     * @param event The PreLLM event
     */
    private void handlePreLLM(PreLLMEvent event) {
        log.debug("Handling PreLLM event");
        executeManagersForHook("PRE_LLM", event.getContext());
    }

    /**
     * Handle PostTool hook events
     * @param event The EnhancedPostTool event
     */
    private void handlePostTool(EnhancedPostToolEvent event) {
        log.debug("Handling PostTool event");
        executeManagersForHook("POST_TOOL", event.getContext());
    }

    /**
     * Handle PostMemory hook events
     * @param event The PostMemory event
     */
    private void handlePostMemory(PostMemoryEvent event) {
        log.debug("Handling PostMemory event");
        executeManagersForHook("POST_MEMORY", event.getContext());
    }

    /**
     * Execute context managers for a specific hook
     * @param hookName The name of the hook
     * @param context The context manager context
     */
    private void executeManagersForHook(String hookName, ContextManagerContext context) {
        List<ContextManager> managers = hookToManagersMap.get(hookName);
        if (managers != null && !managers.isEmpty()) {
            log.debug("Executing {} context managers for hook: {}", managers.size(), hookName);

            for (ContextManager manager : managers) {
                try {
                    if (manager.shouldActivate(context)) {
                        log.debug("Executing context manager: {}", manager.getType());
                        manager.execute(context);
                        log.debug("Successfully executed context manager: {}", manager.getType());
                    } else {
                        log.debug("Context manager {} activation conditions not met, skipping", manager.getType());
                    }
                } catch (Exception e) {
                    log.error("Context manager {} failed: {}", manager.getType(), e.getMessage(), e);
                    // Continue with other managers even if one fails
                }
            }
        } else {
            log.debug("No context managers registered for hook: {}", hookName);
        }
    }

    /**
     * Organize managers by hook configuration
     * @param hookConfiguration Map of hook names to manager configurations
     */
    private void organizeManagersByHookConfiguration(Map<String, List<ContextManagerConfig>> hookConfiguration) {
        hookToManagersMap.clear();
        
        for (Map.Entry<String, List<ContextManagerConfig>> entry : hookConfiguration.entrySet()) {
            String hookName = entry.getKey();
            List<ContextManagerConfig> configs = entry.getValue();

            for (ContextManagerConfig config : configs) {
                // Find the corresponding context manager
                ContextManager manager = findManagerByType(config.getType());
                if (manager != null) {
                    addManagerToHook(hookName, manager);
                } else {
                    log.warn("Context manager of type {} not found for hook {}", config.getType(), hookName);
                }
            }
        }

        log.info("Organized {} context managers across {} hooks from configuration", 
                contextManagers.size(), hookConfiguration.size());
    }

    /**
     * Add a manager to a specific hook
     * @param hookName The hook name
     * @param manager The context manager
     */
    private void addManagerToHook(String hookName, ContextManager manager) {
        hookToManagersMap.computeIfAbsent(hookName, k -> new ArrayList<>()).add(manager);
        log.debug("Added manager {} to hook {}", manager.getType(), hookName);
    }

    /**
     * Update the hook-to-managers mapping based on template configuration
     * @param hookConfiguration Map of hook names to manager configurations
     */
    public void updateHookConfiguration(Map<String, List<ContextManagerConfig>> hookConfiguration) {
        organizeManagersByHookConfiguration(hookConfiguration);
        log.info("Updated hook configuration with {} hooks", hookConfiguration.size());
    }

    /**
     * Find a context manager by its type
     * @param type The manager type
     * @return The context manager or null if not found
     */
    private ContextManager findManagerByType(String type) {
        return contextManagers.stream().filter(manager -> type.equals(manager.getType())).findFirst().orElse(null);
    }

    /**
     * Get the number of managers registered for a specific hook
     * @param hookName The hook name
     * @return Number of managers
     */
    public int getManagerCount(String hookName) {
        List<ContextManager> managers = hookToManagersMap.get(hookName);
        return managers != null ? managers.size() : 0;
    }
}
