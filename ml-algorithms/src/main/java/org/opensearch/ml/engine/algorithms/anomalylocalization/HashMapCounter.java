/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.anomalylocalization;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.opensearch.ml.common.output.execute.anomalylocalization.Counter;

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
