/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.anomalylocalization;

import java.util.List;
import java.util.Random;
import java.util.stream.IntStream;

import lombok.extern.log4j.Log4j2;

/**
 * Count sketch implementation.
 *
 * @see <a href="https://u.cs.biu.ac.il/~porat/2006/MDS/FrequentStream.pdf">Charikar, M., Chen, K., & Farach-Colton, M. (2002, July). Finding frequent items in data streams.</a>
 */
@Log4j2
public class CountSketch implements Counter {

    protected static final double INV_DELTOID = 1 / 0.01;
    protected static final double LOG_BASE_2 = 2;
    protected static final double INV_EPSILON = 1 / 0.001;

    private int numHashes;
    private int numBuckets;
    private double[][] counts;
    private int[] hashes;
    private int[] signHashes;

    /**
     * Constructor.
     */
    public CountSketch() {
        this.numHashes = (int) Math.ceil(Math.log(INV_DELTOID) / Math.log(LOG_BASE_2));
        this.numBuckets = (int) Math.ceil(INV_EPSILON);
        this.counts = new double[this.numHashes][this.numBuckets];
        Random random = new Random();
        this.hashes = random.ints(this.numHashes).toArray();
        this.signHashes = random.ints(this.numHashes).toArray();
        log.info("count sketch size " + this.numHashes + " * " + this.numBuckets + " = " + this.numHashes * this.numBuckets);
    }

    @Override
    public void increment(List<String> key, double value) {
        int keyHash = key.hashCode();
        for (int i = 0; i < this.numHashes; i++) {
            counts[i][getBucketIndex(keyHash, i)] += getCountSign(keyHash, i) * value;
        }
    }

    @Override
    public double estimate(List<String> key) {
        int keyHash = key.hashCode();
        double[] estimates =
                IntStream.range(0, this.numHashes).mapToDouble(i -> counts[i][getBucketIndex(keyHash, i)] * getCountSign(keyHash, i)).sorted().toArray();
        int numEstimates = estimates.length;
        return (estimates[(numEstimates - 1) / 2] + estimates[numEstimates / 2]) / 2;
    }

    private int getBucketIndex(int keyHash, int hashIndex) {
        return Math.floorMod(this.hashes[hashIndex] ^ keyHash, this.numBuckets);
    }

    private int getCountSign(int keyHash, int hashIndex) {
        return Math.floorMod(this.signHashes[hashIndex] ^ keyHash, 2) * 2 - 1;
    }
}
