/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.contextmanager;

/**
 * Interface for activation rules that determine when a context manager should execute.
 * Activation rules evaluate runtime conditions based on the current context state.
 */
public interface ActivationRule {

    /**
     * Evaluate whether the activation condition is met.
     * @param context the current context state
     * @return true if the condition is met and the manager should activate, false otherwise
     */
    boolean evaluate(ContextManagerContext context);

    /**
     * Get a description of this activation rule for logging and debugging.
     * @return a human-readable description of the rule
     */
    String getDescription();
}
