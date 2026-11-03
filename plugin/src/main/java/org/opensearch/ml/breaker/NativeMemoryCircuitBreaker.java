/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.breaker;

import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_NATIVE_MEM_THRESHOLD;

import java.util.Optional;

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

    public NativeMemoryCircuitBreaker(OsService osService, Settings settings, ClusterService clusterService) {
        super(
            Optional
                .ofNullable(ML_COMMONS_NATIVE_MEM_THRESHOLD.get(settings))
                .map(Integer::shortValue)
                .orElse(DEFAULT_NATIVE_MEM_USAGE_THRESHOLD)
        );
        this.osService = osService;
        clusterService
            .getClusterSettings()
            .addSettingsUpdateConsumer(ML_COMMONS_NATIVE_MEM_THRESHOLD, it -> super.setThreshold(it.shortValue()));
    }

    public NativeMemoryCircuitBreaker(Integer threshold, OsService osService) {
        super(threshold.shortValue());
        this.osService = osService;
    }

    @Override
    public String getName() {
        return ML_MEMORY_CB;
    }

    @Override
    public boolean isOpen() {
        return osService.stats().getMem().getUsedPercent() > getThreshold();
    }
}
