/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.stats.otel.metrics;

import lombok.Getter;

@Getter
public enum AdoptionMetric {
    MODEL_COUNT("Number of models created", MetricType.COUNTER),
    CONNECTOR_COUNT("Number of connectors created", MetricType.COUNTER),
    AGENT_COUNT("Number of agents created", MetricType.COUNTER);

    private final String description;
    private final MetricType type;

    AdoptionMetric(String description, MetricType type) {
        this.description = description;
        this.type = type;
    }
}
