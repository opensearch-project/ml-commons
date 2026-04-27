/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.stats.otel.metrics;

import lombok.Getter;

@Getter
public enum McpConnectorMetric {
    MCP_CONNECTOR_COUNT("Number of MCP connectors created", MetricType.COUNTER),
    MCP_CONNECTOR_TOOL_INVOCATION_COUNT("Number of tool invocations through MCP connectors", MetricType.COUNTER),
    MCP_CONNECTOR_TOOL_INVOCATION_LATENCY("Latency of tool invocations through MCP connectors", MetricType.HISTOGRAM);

    private final String description;
    private final MetricType type;

    McpConnectorMetric(String description, MetricType type) {
        this.description = description;
        this.type = type;
    }
}
