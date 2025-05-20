/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.stats.otel.metrics;

import lombok.Getter;

@Getter
public enum OperationalMetric {
    MODEL_PREDICT_COUNT("Total number of predict calls made"),
    MODEL_PREDICT_LATENCY("Latency for model predict");

    private final String description;

    OperationalMetric(String description) {
        this.description = description;
    }
}
