/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.hooks;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import lombok.extern.log4j.Log4j2;

/**
 * Registry for managing hook callbacks and event emission.
 * This class manages the registration of callbacks for different hook event types
 * and provides methods to emit events to registered callbacks.
 */
@Log4j2
public class HookRegistry {
    private final Map<Class<? extends HookEvent>, List<HookCallback<? extends HookEvent>>> callbacks;

    /**
     * Constructor for HookRegistry
     */
    public HookRegistry() {
        this.callbacks = new ConcurrentHashMap<>();
    }

    /**
     * Add a callback for a specific hook event type
     * @param eventType The class of the hook event
     * @param callback The callback to execute when the event is emitted
     * @param <T> The type of hook event
     */
    public <T extends HookEvent> void addCallback(Class<T> eventType, HookCallback<T> callback) {
        callbacks.computeIfAbsent(eventType, k -> new ArrayList<>()).add(callback);
        log.debug("Registered callback for event type: {}", eventType.getSimpleName());
    }

    /**
     * Emit an event to all registered callbacks for that event type
     * @param event The hook event to emit
     * @param <T> The type of hook event
     */
    @SuppressWarnings("unchecked")
    public <T extends HookEvent> void emit(T event) {
        Class<? extends HookEvent> eventType = event.getClass();
        List<HookCallback<? extends HookEvent>> eventCallbacks = callbacks.get(eventType);

        log
            .info(
                "HookRegistry.emit() called for event type: {}, callbacks available: {}",
                eventType.getSimpleName(),
                eventCallbacks != null ? eventCallbacks.size() : 0
            );

        if (eventCallbacks != null) {
            log.info("Emitting {} event to {} callbacks", eventType.getSimpleName(), eventCallbacks.size());

            for (HookCallback<? extends HookEvent> callback : eventCallbacks) {
                try {
                    log.info("Executing callback: {}", callback.getClass().getSimpleName());
                    ((HookCallback<T>) callback).handle(event);
                } catch (Exception e) {
                    log.error("Error executing hook callback for event type {}: {}", eventType.getSimpleName(), e.getMessage(), e);
                    // Continue with other callbacks even if one fails
                }
            }
        } else {
            log.warn("No callbacks registered for event type: {}", eventType.getSimpleName());
        }
    }

    /**
     * Get the number of registered callbacks for a specific event type
     * @param eventType The class of the hook event
     * @return Number of registered callbacks
     */
    public int getCallbackCount(Class<? extends HookEvent> eventType) {
        List<HookCallback<? extends HookEvent>> eventCallbacks = callbacks.get(eventType);
        return eventCallbacks != null ? eventCallbacks.size() : 0;
    }

    /**
     * Clear all registered callbacks
     */
    public void clear() {
        callbacks.clear();
        log.debug("Cleared all hook callbacks");
    }
}
