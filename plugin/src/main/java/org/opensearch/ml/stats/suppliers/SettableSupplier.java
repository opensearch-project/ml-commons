/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.stats.suppliers;

import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * SettableSupplier allows a user to set the value of the supplier to be returned
 */
public class SettableSupplier implements Supplier<Long> {
    protected AtomicLong value;

    /**
     * Constructor
     */
    public SettableSupplier() {
        this.value = new AtomicLong(0L);
    }

    @Override
    public Long get() {
        return value.get();
    }

    /**
     * Set value to be returned by get
     *
     * @param value to set
     */
    public void set(Long value) {
        this.value.set(value);
    }
}
