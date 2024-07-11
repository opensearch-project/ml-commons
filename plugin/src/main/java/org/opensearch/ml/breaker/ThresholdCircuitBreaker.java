/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.breaker;

import lombok.Data;

/**
 * An abstract class for all breakers with threshold.
 * @param <T> data type of threshold
 */
@Data
public abstract class ThresholdCircuitBreaker<T> implements CircuitBreaker {

    private volatile T threshold;

    public ThresholdCircuitBreaker(T threshold) {
        this.threshold = threshold;
    }

    @Override
    public abstract boolean isOpen();
}
