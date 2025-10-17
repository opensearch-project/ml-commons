/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.hooks;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import lombok.extern.log4j.Log4j2;

@Log4j2
public class HookRegistry {
    private final Map<Class<? extends HookEvent>, List<HookCallback<? extends HookEvent>>> callbacks;
    private final Map<String, Long> eventCounts;

    public HookRegistry(boolean enableMetrics) {
        this.callbacks = new ConcurrentHashMap<>();
        this.eventCounts = enableMetrics ? new ConcurrentHashMap<>() : null;
    }

    public <T extends HookEvent> void addCallback(Class<T> eventType, HookCallback<T> callback) {
        callbacks.computeIfAbsent(eventType, k -> new ArrayList<>()).add(callback);
        log.debug("Added callback for event type: {}", eventType.getSimpleName());
    }

    /**
     * Add a hook provider - it registers its callbacks and then we forget about it
     */
    public HookRegistry addHook(HookProvider provider) {
        provider.registerHooks(this);
        log.debug("Completed registration for hook provider: {}", provider.getClass().getSimpleName());
        // No need to store the provider - it's done its job
        return this;
    }

    @SuppressWarnings("unchecked")
    public <T extends HookEvent> void emit(T event) {
        List<HookCallback<? extends HookEvent>> eventCallbacks = callbacks.getOrDefault(event.getClass(), Collections.emptyList());
        for (HookCallback callback : eventCallbacks) {
            callback.onEvent(event);
        }
    }

    /**
     * Get count of callbacks for an event type
     */
    public int getCallbackCount(Class<? extends HookEvent> eventType) {
        List<HookCallback<? extends HookEvent>> eventCallbacks = callbacks.get(eventType);
        return eventCallbacks != null ? eventCallbacks.size() : 0;
    }

    /**
     * Get total number of registered callbacks across all event types
     */
    public int getTotalCallbackCount() {
        return callbacks.values().stream().mapToInt(List::size).sum();
    }

    /**
     * Remove all callbacks for an event type
     */
    public HookRegistry clearCallbacks(Class<? extends HookEvent> eventType) {
        callbacks.remove(eventType);
        log.debug("Cleared all callbacks for event type: {}", eventType.getSimpleName());
        return this;
    }
}
