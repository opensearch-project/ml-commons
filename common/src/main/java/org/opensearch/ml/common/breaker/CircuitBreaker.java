/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.breaker;

/**
 * An interface for circuit breaker.
 *
 * We use circuit breaker to protect a certain system resource like memory.
 */
public interface CircuitBreaker {

    boolean isOpen();

    String getName();
}
