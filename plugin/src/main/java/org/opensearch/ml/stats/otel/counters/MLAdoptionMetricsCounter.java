/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.stats.otel.counters;

import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.stats.otel.metrics.AdoptionMetric;
import org.opensearch.ml.stats.otel.metrics.MetricType;
import org.opensearch.telemetry.metrics.MetricsRegistry;

public class MLAdoptionMetricsCounter extends AbstractMLMetricsCounter<AdoptionMetric> {

    private static MLAdoptionMetricsCounter instance;

    private MLAdoptionMetricsCounter(String clusterName, MetricsRegistry metricsRegistry, MLFeatureEnabledSetting mlFeatureEnabledSetting) {
        super(clusterName, metricsRegistry, AdoptionMetric.class, mlFeatureEnabledSetting);
    }

    public static synchronized void initialize(
        String clusterName,
        MetricsRegistry metricsRegistry,
        MLFeatureEnabledSetting mlFeatureEnabledSetting
    ) {
        instance = new MLAdoptionMetricsCounter(clusterName, metricsRegistry, mlFeatureEnabledSetting);
    }

    public static synchronized MLAdoptionMetricsCounter getInstance() {
        if (instance == null) {
            throw new IllegalStateException("MLAdoptionMetricsCounter is not initialized. Call initialize() first.");
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
    protected String getMetricDescription(AdoptionMetric metric) {
        return metric.getDescription();
    }

    @Override
    protected MetricType getMetricType(AdoptionMetric metric) {
        return metric.getType();
    }
}
