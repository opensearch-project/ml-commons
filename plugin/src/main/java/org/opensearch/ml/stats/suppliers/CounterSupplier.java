/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 *
 * Modifications Copyright OpenSearch Contributors. See
 * GitHub history for details.
 *
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
