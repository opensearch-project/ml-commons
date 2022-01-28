/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.stats.suppliers;

import java.util.concurrent.atomic.LongAdder;
import java.util.function.Supplier;

/**
 * CounterSupplier provides a stateful count as the value
 */
public class CounterSupplier implements Supplier<Long> {
    private LongAdder counter;

    /**
     * Constructor
     */
    public CounterSupplier() {
        this.counter = new LongAdder();
    }

    @Override
    public Long get() {
        return counter.longValue();
    }

    /**
     * Increments the value of the counter by 1
     */
    public void increment() {
        counter.increment();
    }

    /**
     * Decrease the value of the counter by 1
     */
    public void decrement() {
        counter.decrement();
    }
}
