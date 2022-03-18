/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.parameter;

import java.util.List;

/**
 * Counts values for keys.
 */
public interface Counter {

    /**
     * Increments the value for the key.
     *
     * @param value The value to increment.
     * @param key   The key to increment value for.
     */
    void increment(List<String> key, double value);

    /**
     * Gets the value for the key.
     *
     * @param key The key to get value for.
     * @return the (approximate/exact) value for the key
     */
    double estimate(List<String> key);
}
