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
import java.util.Random;
import java.util.stream.IntStream;

import lombok.extern.log4j.Log4j2;

/**
 * CountMin sketch implementation.
 *
 * @see <a href="https://www.cs.tufts.edu/comp/150FP/archive/graham-cormode/count-min.pdf">Cormode, G., & Muthukrishnan, S. (2005). An improved data stream summary: the count-min sketch and its applications.</a>
 */
@Log4j2
public class CountMinSketch implements Counter {

    protected static final double INV_DELTOID = 1 / 0.01;
    protected static final double LOG_BASE_2 = 2;
    protected static final double INV_EPSILON = 1 / 0.001;

    private int numHashes;
    private int numBuckets;
    private double[][] counts;
    private int[] hashes;

    /**
     * Constructor.
     */
    public CountMinSketch() {
        this.numHashes = (int) Math.ceil(Math.log(INV_DELTOID) / Math.log(LOG_BASE_2));
        this.numBuckets = (int) Math.ceil(INV_EPSILON);
        this.counts = new double[this.numHashes][this.numBuckets];
        this.hashes = new Random().ints(this.numHashes).toArray();
        log.info("count min sketch size " + this.numHashes + " * " + this.numBuckets + " = " + this.numHashes * this.numBuckets);
    }

    @Override
    public void increment(List<String> key, double value) {
        int keyHash = key.hashCode();
        for (int i = 0; i < this.numHashes; i++) {
            counts[i][getBucketIndex(keyHash, i)] += value;
        }
    }

    @Override
    public double estimate(List<String> key) {
        int keyHash = key.hashCode();
        return IntStream.range(0, this.numHashes).mapToDouble(i -> counts[i][getBucketIndex(keyHash, i)]).min().orElse(0.0);
    }

    private int getBucketIndex(int keyHash, int hashIndex) {
        return Math.floorMod(this.hashes[hashIndex] ^ keyHash, this.numBuckets);
    }
}
