/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.tools;

import org.opensearch.ml.stats.otel.counters.MLMcpConnectorMetricsCounter;
import org.opensearch.ml.stats.otel.metrics.McpConnectorMetric;
import org.opensearch.telemetry.metrics.tags.Tags;

final class McpToolMetrics {

    private McpToolMetrics() {}

    static void recordConnectorInvocation(String protocol, long startNanos, String status) {
        double latencyMs = (System.nanoTime() - startNanos) / 1_000_000.0;
        Tags tags = Tags.create().addTag("protocol", protocol).addTag("status", status);
        MLMcpConnectorMetricsCounter.getInstance().incrementCounter(McpConnectorMetric.MCP_CONNECTOR_TOOL_INVOCATION_COUNT, tags);
        MLMcpConnectorMetricsCounter
            .getInstance()
            .recordHistogram(McpConnectorMetric.MCP_CONNECTOR_TOOL_INVOCATION_LATENCY, latencyMs, tags);
    }
}
