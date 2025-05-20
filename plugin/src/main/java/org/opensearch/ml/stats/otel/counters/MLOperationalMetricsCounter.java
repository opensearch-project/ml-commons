/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.stats.otel.counters;

import org.opensearch.ml.stats.otel.metrics.OperationalMetric;
import org.opensearch.telemetry.metrics.MetricsRegistry;

public class MLOperationalMetricsCounter extends AbstractMLMetricsCounter<OperationalMetric> {

    private static MLOperationalMetricsCounter instance;

    private MLOperationalMetricsCounter(String clusterName, MetricsRegistry metricsRegistry) {
        super(clusterName, metricsRegistry, OperationalMetric.class);
    }

    public static synchronized void initialize(String clusterName, MetricsRegistry metricsRegistry) {
        instance = new MLOperationalMetricsCounter(clusterName, metricsRegistry);
    }

    public static synchronized MLOperationalMetricsCounter getInstance() {
        if (instance == null) {
            throw new IllegalStateException("MLOperationalMetricsCounter is not initialized. Call initialize() first.");
        }

        return instance;
    }

    @Override
    protected String getMetricDescription(OperationalMetric metric) {
        return metric.getDescription();
    }
}
