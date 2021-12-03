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

package org.opensearch.ml.engine.algorithms.anomalylocalization;

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
