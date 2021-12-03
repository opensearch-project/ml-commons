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

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class HybridCounterTests {

    public void testOnCountMinSketch() {
        HybridCounter counter = new HybridCounter();
        Hashmap hash = new Hashmap();

        double sum = 0.;
        String[] keys = new String[]{"a", "b", "c"};
        for (int i = 0; i < 1_000_000; i++) {
            List<String> key = Arrays.asList(keys[(int) (keys.length * Math.random())]);
            double value = Math.random();
            sum += value;

            counter.increment(key, value);
            hash.increment(key, value);

            double estimate = counter.estimate(key);
            double truth = hash.estimate(key);

            if (i < HybridCounter.SKETCH_THRESHOLD) {
                assertEquals(truth, estimate, 1e-3);
            } else {
                assertTrue(estimate >= truth);
                assertTrue(estimate < truth + sum * (1 / CountMinSketch.INV_EPSILON));
            }
        }
    }

    public void testOnCountSketch() {
        HybridCounter counter = new HybridCounter();
        Hashmap hash = new Hashmap();

        double sum = 0.;
        String[] keys = new String[]{"a", "b", "c"};
        for (int i = 0; i < 1_000_000; i++) {
            List<String> key = Arrays.asList(keys[(int) (keys.length * Math.random())]);
            double value = Math.random() * -1;

            sum += value;

            counter.increment(key, value);
            hash.increment(key, value);

            double estimate = counter.estimate(key);
            double truth = hash.estimate(key);

            if (i < HybridCounter.SKETCH_THRESHOLD) {
                assertEquals(truth, estimate, 1e-3);
            } else {
                assertTrue(Math.abs(estimate - truth) < Math.abs(sum) * (1 / CountSketch.INV_EPSILON));
            }
        }
    }
}
