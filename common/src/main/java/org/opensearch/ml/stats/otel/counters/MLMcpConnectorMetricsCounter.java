/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.stats.otel.counters;

import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.stats.otel.metrics.McpConnectorMetric;
import org.opensearch.ml.stats.otel.metrics.MetricType;
import org.opensearch.telemetry.metrics.MetricsRegistry;

public class MLMcpConnectorMetricsCounter extends AbstractMLMetricsCounter<McpConnectorMetric> {

    private static MLMcpConnectorMetricsCounter instance;

    private MLMcpConnectorMetricsCounter(
        String clusterName,
        MetricsRegistry metricsRegistry,
        MLFeatureEnabledSetting mlFeatureEnabledSetting
    ) {
        super(clusterName, metricsRegistry, McpConnectorMetric.class, mlFeatureEnabledSetting);
    }

    public static synchronized void initialize(
        String clusterName,
        MetricsRegistry metricsRegistry,
        MLFeatureEnabledSetting mlFeatureEnabledSetting
    ) {
        instance = new MLMcpConnectorMetricsCounter(clusterName, metricsRegistry, mlFeatureEnabledSetting);
    }

    public static synchronized MLMcpConnectorMetricsCounter getInstance() {
        if (instance == null) {
            throw new IllegalStateException("MLMcpConnectorMetricsCounter is not initialized. Call initialize() first.");
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
    protected String getMetricDescription(McpConnectorMetric metric) {
        return metric.getDescription();
    }

    @Override
    protected MetricType getMetricType(McpConnectorMetric metric) {
        return metric.getType();
    }
}
