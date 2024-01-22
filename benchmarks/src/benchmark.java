/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.TearDown;

public class Benchmark {
    @Setup
    public void setup() {
        //external data or test dataset
    }

    @Benchmark
    public int benchmarkMethod() {
        //benchmark model method on dataset
    }

    @TearDown
    public void tearDown() {
        //release dataset
    }
}