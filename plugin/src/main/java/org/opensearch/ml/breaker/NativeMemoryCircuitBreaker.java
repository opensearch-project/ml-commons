/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.breaker;

import static org.opensearch.ml.settings.MLCommonsSettings.ML_COMMONS_NATIVE_MEM_THRESHOLD;

import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.Settings;
import org.opensearch.monitor.os.OsService;

/**
 * A circuit breaker for native memory usage.
 */
public class NativeMemoryCircuitBreaker extends ThresholdCircuitBreaker<Short> {
    private static final String ML_MEMORY_CB = "Native Memory Circuit Breaker";
    public static final short DEFAULT_NATIVE_MEM_USAGE_THRESHOLD = 90;
    private final OsService osService;
    private volatile Integer nativeMemThreshold = 90;

    public NativeMemoryCircuitBreaker(OsService osService, Settings settings, ClusterService clusterService) {
        super(DEFAULT_NATIVE_MEM_USAGE_THRESHOLD);
        this.osService = osService;
        this.nativeMemThreshold = ML_COMMONS_NATIVE_MEM_THRESHOLD.get(settings);
        clusterService.getClusterSettings().addSettingsUpdateConsumer(ML_COMMONS_NATIVE_MEM_THRESHOLD, it -> nativeMemThreshold = it);
    }

    public NativeMemoryCircuitBreaker(Integer threshold, OsService osService) {
        super(threshold.shortValue());
        this.nativeMemThreshold = threshold;
        this.osService = osService;
    }

    @Override
    public String getName() {
        return ML_MEMORY_CB;
    }

    @Override
    public Short getThreshold() {
        return this.nativeMemThreshold.shortValue();
    }

    @Override
    public boolean isOpen() {
        return osService.stats().getMem().getUsedPercent() > this.nativeMemThreshold.shortValue();
    }
}
