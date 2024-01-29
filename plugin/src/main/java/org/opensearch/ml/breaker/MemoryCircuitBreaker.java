/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.breaker;

import static org.opensearch.ml.settings.MLCommonsSettings.ML_COMMONS_JVM_HEAP_MEM_THRESHOLD;

import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.Settings;
import org.opensearch.monitor.jvm.JvmService;

/**
 * A circuit breaker for memory usage.
 */
public class MemoryCircuitBreaker extends ThresholdCircuitBreaker<Short> {
    // TODO: make this value configurable as cluster setting
    private static final String ML_MEMORY_CB = "Memory Circuit Breaker";
    public static final short DEFAULT_JVM_HEAP_USAGE_THRESHOLD = 85;
    private final JvmService jvmService;
    private volatile Integer jvmHeapMemThreshold = 85;

    public MemoryCircuitBreaker(JvmService jvmService) {
        super(DEFAULT_JVM_HEAP_USAGE_THRESHOLD);
        this.jvmService = jvmService;
    }

    public MemoryCircuitBreaker(short threshold, JvmService jvmService) {
        super(threshold);
        this.jvmService = jvmService;
    }

    public MemoryCircuitBreaker(Settings settings, ClusterService clusterService, JvmService jvmService) {
        super(DEFAULT_JVM_HEAP_USAGE_THRESHOLD);
        this.jvmService = jvmService;
        this.jvmHeapMemThreshold = ML_COMMONS_JVM_HEAP_MEM_THRESHOLD.get(settings);
        clusterService.getClusterSettings().addSettingsUpdateConsumer(ML_COMMONS_JVM_HEAP_MEM_THRESHOLD, it -> jvmHeapMemThreshold = it);
    }

    @Override
    public String getName() {
        return ML_MEMORY_CB;
    }

    @Override
    public boolean isOpen() {
        return jvmService.stats().getMem().getHeapUsedPercent() > jvmHeapMemThreshold;
    }
}
