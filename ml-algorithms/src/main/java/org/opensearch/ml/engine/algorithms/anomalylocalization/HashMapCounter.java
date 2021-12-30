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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import lombok.Data;
import lombok.extern.log4j.Log4j2;

/**
 * Hashmap-based exact counting.
 */
@Data
@Log4j2
public class HashMapCounter implements Counter {

    private Map<List<String>, Double> keyValues = new HashMap<>();

    @Override
    public void increment(List<String> key, double value) {
        keyValues.compute(key, (k, v) -> (v == null) ? value : value + v);
    }

    @Override
    public double estimate(List<String> key) {
        return keyValues.getOrDefault(key, 0.0);
    }
}
