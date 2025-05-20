/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.stats.otel.metrics;

import lombok.Getter;

@Getter
public enum AdoptionMetric {
    MODEL_COUNT("Number of models created"),
    CONNECTOR_COUNT("Number of connectors created");

    private final String description;

    AdoptionMetric(String description) {
        this.description = description;
    }
}
