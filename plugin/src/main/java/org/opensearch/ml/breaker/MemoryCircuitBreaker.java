/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.breaker;

import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_JVM_HEAP_MEM_THRESHOLD;

import java.util.Optional;

import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.Settings;
import org.opensearch.monitor.jvm.JvmService;

/**
 * A circuit breaker for memory usage.
 */
public class MemoryCircuitBreaker extends ThresholdCircuitBreaker<Short> {
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

    public MemoryCircuitBreaker(Settings settings, ClusterService clusterService, JvmService jvmService) {
        super(
            Optional
                .ofNullable(ML_COMMONS_JVM_HEAP_MEM_THRESHOLD.get(settings))
                .map(Integer::shortValue)
                .orElse(DEFAULT_JVM_HEAP_USAGE_THRESHOLD)
        );
        this.jvmService = jvmService;
        clusterService
            .getClusterSettings()
            .addSettingsUpdateConsumer(ML_COMMONS_JVM_HEAP_MEM_THRESHOLD, it -> super.setThreshold(it.shortValue()));
    }

    @Override
    public String getName() {
        return ML_MEMORY_CB;
    }

    @Override
    public boolean isOpen() {
        return getThreshold() < 100 && jvmService.stats().getMem().getHeapUsedPercent() > getThreshold();
    }
}
