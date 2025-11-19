/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

/**
 * Context management framework for OpenSearch ML-Commons.
 * 
 * This package provides a pluggable context management system that allows for dynamic
 * optimization of LLM context windows through configurable context managers.
 * 
 * Key components:
 * - {@link org.opensearch.ml.common.contextmanager.ContextManager}: Base interface for all context managers
 * - {@link org.opensearch.ml.common.contextmanager.ContextManagerContext}: Context object containing all agent execution state
 * - {@link org.opensearch.ml.common.contextmanager.ActivationRule}: Interface for rules that determine when managers should execute
 * - {@link org.opensearch.ml.common.contextmanager.ActivationRuleFactory}: Factory for creating activation rules from configuration
 * 
 * The system integrates with the existing hook framework to provide seamless context optimization
 * during agent execution without breaking existing functionality.
 */
package org.opensearch.ml.common.contextmanager;
