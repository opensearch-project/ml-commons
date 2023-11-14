/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.anomalylocalization;

import java.util.List;
import java.util.Map;

import org.opensearch.ml.common.output.execute.anomalylocalization.Counter;

import lombok.extern.log4j.Log4j2;

/**
 * A hybrid counter that starts with exact counting with map and switches to approximate counting with sketch as the size grows.
 */
@Log4j2
public class HybridCounter implements Counter {

    protected static int SKETCH_THRESHOLD = 10_000;

    private Counter counter = new HashMapCounter();
    private int count = 0;

    @Override
    public void increment(List<String> key, double value) {
        this.counter.increment(key, value);
        updateCount();
    }

    @Override
    public double estimate(List<String> key) {
        return this.counter.estimate(key);
    }

    private void updateCount() {
        this.count++;
        if (this.count == SKETCH_THRESHOLD) {
            Map<List<String>, Double> hashmap = ((HashMapCounter) counter).getKeyValues();
            boolean hasNegative = hashmap.values().stream().anyMatch(v -> v < 0);
            Counter newCounter;
            if (hasNegative) { // aggregate value, avg for example, of a key can be negative
                newCounter = new CountSketch();
            } else {
                newCounter = new CountMinSketch();
            }
            hashmap.forEach((k, v) -> newCounter.increment(k, v));
            this.counter = newCounter;
        }
    }
}
