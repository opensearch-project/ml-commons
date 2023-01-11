/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.breaker;

/**
 * An abstract class for all breakers with threshold.
 * @param <T> data type of threshold
 */
public abstract class ThresholdCircuitBreaker<T> implements CircuitBreaker {

    private T threshold;

    public ThresholdCircuitBreaker(T threshold) {
        this.threshold = threshold;
    }

    public T getThreshold() {
        return threshold;
    }

    @Override
    public abstract boolean isOpen();
}
