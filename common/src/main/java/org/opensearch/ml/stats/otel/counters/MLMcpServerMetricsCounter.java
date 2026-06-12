/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.stats.otel.counters;

import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.stats.otel.metrics.McpServerMetric;
import org.opensearch.ml.stats.otel.metrics.MetricType;
import org.opensearch.telemetry.metrics.MetricsRegistry;

public class MLMcpServerMetricsCounter extends AbstractMLMetricsCounter<McpServerMetric> {

    private static MLMcpServerMetricsCounter instance;

    private MLMcpServerMetricsCounter(
        String clusterName,
        MetricsRegistry metricsRegistry,
        MLFeatureEnabledSetting mlFeatureEnabledSetting
    ) {
        super(clusterName, metricsRegistry, McpServerMetric.class, mlFeatureEnabledSetting);
    }

    public static synchronized void initialize(
        String clusterName,
        MetricsRegistry metricsRegistry,
        MLFeatureEnabledSetting mlFeatureEnabledSetting
    ) {
        instance = new MLMcpServerMetricsCounter(clusterName, metricsRegistry, mlFeatureEnabledSetting);
    }

    public static synchronized MLMcpServerMetricsCounter getInstance() {
        if (instance == null) {
            throw new IllegalStateException("MLMcpServerMetricsCounter is not initialized. Call initialize() first.");
        }
        return instance;
    }

    /**
     * Resets the singleton instance. This method is only for testing purposes.
     */
    public static synchronized void reset() {
        instance = null;
    }

    @Override
    protected String getMetricDescription(McpServerMetric metric) {
        return metric.getDescription();
    }

    @Override
    protected MetricType getMetricType(McpServerMetric metric) {
        return metric.getType();
    }
}
