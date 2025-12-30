/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.contextmanager;

import java.util.Map;

/**
 * Base interface for all context managers.
 * Context managers are pluggable components that inspect and transform
 * agent context components before they are sent to an LLM.
 */
public interface ContextManager {

    /**
     * Get the type identifier for this context manager
     * @return String identifying the manager type
     */
    String getType();

    /**
     * Initialize the context manager with configuration
     * @param config Configuration map for the manager
     */
    void initialize(Map<String, Object> config);

    /**
     * Check if this context manager should activate based on current context
     * @param context The current context manager context
     * @return true if the manager should execute, false otherwise
     */
    boolean shouldActivate(ContextManagerContext context);

    /**
     * Execute the context transformation
     * @param context The context manager context to transform
     */
    void execute(ContextManagerContext context);

}
