/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.stats.otel.metrics;

import lombok.Getter;

@Getter
public enum McpServerMetric {
    MCP_SERVER_REGISTERED_TOOL_COUNT("Number of tools registered to the MCP server", MetricType.COUNTER),
    MCP_SERVER_TOOL_CALL_COUNT("Number of tool calls served by the MCP server", MetricType.COUNTER),
    MCP_SERVER_TOOL_CALL_LATENCY("Latency of tool calls served by the MCP server", MetricType.HISTOGRAM),
    MCP_SERVER_REQUEST_COUNT("Number of JSON-RPC requests handled by the MCP server", MetricType.COUNTER);

    private final String description;
    private final MetricType type;

    McpServerMetric(String description, MetricType type) {
        this.description = description;
        this.type = type;
    }
}
