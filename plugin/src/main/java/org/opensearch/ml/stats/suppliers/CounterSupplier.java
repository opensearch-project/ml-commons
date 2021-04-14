/*
 *  Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License").
 *  You may not use this file except in compliance with the License.
 *  A copy of the License is located at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  or in the "license" file accompanying this file. This file is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 *  express or implied. See the License for the specific language governing
 *  permissions and limitations under the License.
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
