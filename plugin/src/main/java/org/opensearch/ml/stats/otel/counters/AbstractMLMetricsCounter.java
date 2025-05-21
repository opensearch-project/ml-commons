/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.stats.otel.counters;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.telemetry.metrics.Counter;
import org.opensearch.telemetry.metrics.MetricsRegistry;
import org.opensearch.telemetry.metrics.tags.Tags;

import lombok.extern.log4j.Log4j2;

@Log4j2
public abstract class AbstractMLMetricsCounter<T extends Enum<T>> {
    private static final String PREFIX = "ml.commons.";
    private static final String UNIT = "1";
    private static final String CLUSTER_NAME_TAG = "cluster_name";

    private final MLFeatureEnabledSetting mlFeatureEnabledSetting;

    protected final String clusterName;
    protected final MetricsRegistry metricsRegistry;
    protected final Map<T, Counter> metricCounterMap;

    protected AbstractMLMetricsCounter(
        String clusterName,
        MetricsRegistry metricsRegistry,
        Class<T> metricClass,
        MLFeatureEnabledSetting mlFeatureEnabledSetting
    ) {
        this.clusterName = clusterName;
        this.metricsRegistry = metricsRegistry;
        this.metricCounterMap = new ConcurrentHashMap<>();
        this.mlFeatureEnabledSetting = mlFeatureEnabledSetting;
        Stream.of(metricClass.getEnumConstants()).forEach(metric -> metricCounterMap.computeIfAbsent(metric, this::createMetricCounter));
    }

    public void incrementCounter(T metric, Tags customTags) {
        if (!mlFeatureEnabledSetting.isMetricCollectionEnabled()) {
            return;
        }

        Counter counter = metricCounterMap.computeIfAbsent(metric, this::createMetricCounter);
        Tags metricsTags = (customTags == null ? Tags.create() : customTags).addTag(CLUSTER_NAME_TAG, clusterName);
        counter.add(1, metricsTags);
    }

    private Counter createMetricCounter(T metric) {
        return metricsRegistry.createCounter(PREFIX + metric.name(), getMetricDescription(metric), UNIT);
    }

    protected abstract String getMetricDescription(T metric);
}
