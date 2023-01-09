/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.breaker;

import org.opensearch.monitor.os.OsService;

/**
 * A circuit breaker for native memory usage.
 */
public class NativeMemoryCircuitBreaker extends ThresholdCircuitBreaker<Short> {
    //TODO: make this value configurable as cluster setting
    private static final String ML_MEMORY_CB = "Native Memory Circuit Breaker";
    //Set the threshold as 97 to avoid IT failures.
    //TODO: make the value as dynamic setting, so we increase it for IT
    public static final short DEFAULT_NATIVE_MEM_USAGE_THRESHOLD = 97;
    private final OsService osService;

    public NativeMemoryCircuitBreaker(OsService osService) {
        super(DEFAULT_NATIVE_MEM_USAGE_THRESHOLD);
        this.osService = osService;
    }

    public NativeMemoryCircuitBreaker(short threshold, OsService osService) {
        super(threshold);
        this.osService = osService;
    }

    @Override
    public String getName() {
        return ML_MEMORY_CB;
    }

    @Override
    public boolean isOpen() {
        return osService.stats().getMem().getUsedPercent() > this.getThreshold();
    }
}
