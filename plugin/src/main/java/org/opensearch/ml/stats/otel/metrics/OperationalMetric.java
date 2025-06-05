/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.stats.otel.metrics;

import lombok.Getter;

@Getter
public enum OperationalMetric {
    MODEL_PREDICT_COUNT("Total number of predict calls made", MetricType.COUNTER),
    MODEL_PREDICT_LATENCY("Latency for model predict", MetricType.HISTOGRAM);

    private final String description;
    private final MetricType type;

    OperationalMetric(String description, MetricType type) {
        this.description = description;
        this.type = type;
    }
}
