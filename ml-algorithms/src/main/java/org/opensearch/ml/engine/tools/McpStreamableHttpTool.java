/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.tools;

import static org.opensearch.ml.common.CommonValue.MCP_SYNC_CLIENT;
import static org.opensearch.ml.common.connector.ConnectorProtocols.MCP_STREAMABLE_HTTP;

import java.util.List;
import java.util.Map;

import org.opensearch.core.action.ActionListener;
import org.opensearch.ml.common.spi.tools.Parser;
import org.opensearch.ml.common.spi.tools.ToolAnnotation;
import org.opensearch.ml.common.spi.tools.WithModelTool;
import org.opensearch.ml.common.utils.StringUtils;
import org.opensearch.ml.common.utils.ToolUtils;
import org.opensearch.ml.stats.otel.counters.MLMcpConnectorMetricsCounter;
import org.opensearch.ml.stats.otel.metrics.McpConnectorMetric;
import org.opensearch.telemetry.metrics.tags.Tags;

import com.google.common.annotations.VisibleForTesting;

import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;

/**
 * This tool supports running any MCP streamable HTTP model.
 */
@Log4j2
@ToolAnnotation(McpStreamableHttpTool.TYPE)
public class McpStreamableHttpTool implements WithModelTool {
    public static final String TYPE = "McpStreamableHttpTool";

    @Setter
    @Getter
    private String name = TYPE;
    @Getter
    @Setter
    private Map<String, Object> attributes;
    @VisibleForTesting
    public static String DEFAULT_DESCRIPTION = "A tool from MCP Streamable HTTP Server";
    @Getter
    @Setter
    private String description = DEFAULT_DESCRIPTION;
    @Getter
    private McpSyncClient mcpSyncClient;
    @Setter
    @Getter
    @VisibleForTesting
    private Parser<?, ?> outputParser;

    public McpStreamableHttpTool(McpSyncClient mcpSyncClient) {
        this.mcpSyncClient = mcpSyncClient;
    }

    @Override
    public <T> void run(Map<String, String> originalParameters, ActionListener<T> listener) {
        long startNanos = System.nanoTime();
        try {
            Map<String, String> parameters = ToolUtils.extractInputParameters(originalParameters, attributes);
            String input = parameters.get("input");
            Map<String, Object> inputArgs = StringUtils.fromJson(input, "input");
            McpSchema.CallToolResult result = mcpSyncClient.callTool(new McpSchema.CallToolRequest(this.name, inputArgs));
            String resultJson = StringUtils.toJson(result.content());
            @SuppressWarnings("unchecked")
            T response = (T) resultJson;
            recordInvocation(startNanos, "success");
            listener.onResponse(response);
        } catch (Exception e) {
            recordInvocation(startNanos, "failure");
            log.error("Failed to call MCP streamable HTTP tool: {}", this.getName(), e);
            listener.onFailure(e);
        }
    }

    private void recordInvocation(long startNanos, String status) {
        double latencyMs = (System.nanoTime() - startNanos) / 1_000_000.0;
        Tags tags = Tags.create().addTag("protocol", MCP_STREAMABLE_HTTP).addTag("status", status);
        MLMcpConnectorMetricsCounter.getInstance().incrementCounter(McpConnectorMetric.MCP_CONNECTOR_TOOL_INVOCATION_COUNT, tags);
        MLMcpConnectorMetricsCounter
            .getInstance()
            .recordHistogram(
                McpConnectorMetric.MCP_CONNECTOR_TOOL_INVOCATION_LATENCY,
                latencyMs,
                Tags.create().addTag("protocol", MCP_STREAMABLE_HTTP)
            );
    }

    @Override
    public String getType() {
        return TYPE;
    }

    @Override
    public String getVersion() {
        return null;
    }

    @Override
    public String getName() {
        return this.name;
    }

    @Override
    public void setName(String s) {
        this.name = s;
    }

    @Override
    public boolean validate(Map<String, String> parameters) {
        if (parameters == null || parameters.size() == 0) {
            return false;
        }
        return true;
    }

    public static class Factory implements WithModelTool.Factory<McpStreamableHttpTool> {
        private static Factory INSTANCE;

        public static Factory getInstance() {
            if (INSTANCE != null) {
                return INSTANCE;
            }
            synchronized (McpStreamableHttpTool.class) {
                if (INSTANCE != null) {
                    return INSTANCE;
                }
                INSTANCE = new Factory();
                return INSTANCE;
            }
        }

        public void init() {}

        @Override
        public McpStreamableHttpTool create(Map<String, Object> map) {
            return new McpStreamableHttpTool((McpSyncClient) map.get(MCP_SYNC_CLIENT));
        }

        @Override
        public String getDefaultDescription() {
            return DEFAULT_DESCRIPTION;
        }

        @Override
        public String getDefaultType() {
            return TYPE;
        }

        @Override
        public String getDefaultVersion() {
            return null;
        }

        @Override
        public List<String> getAllModelKeys() {
            return List.of();
        }
    }
}
