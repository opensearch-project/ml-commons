/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.breaker;

import org.opensearch.monitor.jvm.JvmService;

/**
 * A circuit breaker for memory usage.
 */
public class MemoryCircuitBreaker extends ThresholdCircuitBreaker<Short> {
    // TODO: make this value configurable as cluster setting
    private static final String ML_MEMORY_CB = "Memory Circuit Breaker";
    public static final short DEFAULT_JVM_HEAP_USAGE_THRESHOLD = 85;
    private final JvmService jvmService;

    public MemoryCircuitBreaker(JvmService jvmService) {
        super(DEFAULT_JVM_HEAP_USAGE_THRESHOLD);
        this.jvmService = jvmService;
    }

    public MemoryCircuitBreaker(short threshold, JvmService jvmService) {
        super(threshold);
        this.jvmService = jvmService;
    }

    @Override
    public String getName() {
        return ML_MEMORY_CB;
    }

    @Override
    public boolean isOpen() {
        return jvmService.stats().getMem().getHeapUsedPercent() > this.getThreshold();
    }
}
