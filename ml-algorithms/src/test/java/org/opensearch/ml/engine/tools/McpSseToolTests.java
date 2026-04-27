/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.tools;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.opensearch.ml.common.CommonValue.MCP_SYNC_CLIENT;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.core.action.ActionListener;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.spi.tools.Tool;
import org.opensearch.ml.stats.otel.counters.MLMcpConnectorMetricsCounter;
import org.opensearch.telemetry.metrics.Counter;
import org.opensearch.telemetry.metrics.Histogram;
import org.opensearch.telemetry.metrics.MetricsRegistry;
import org.opensearch.telemetry.metrics.tags.Tags;

import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;

public class McpSseToolTests {

    @Mock
    private McpSyncClient mcpSyncClient;

    @Mock
    private ActionListener<String> listener;

    private Tool tool;
    private Map<String, String> validParams;
    private Counter counter;
    private Histogram histogram;

    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);
        counter = mock(Counter.class);
        histogram = mock(Histogram.class);
        MLFeatureEnabledSetting featureFlag = mock(MLFeatureEnabledSetting.class);
        when(featureFlag.isMetricCollectionEnabled()).thenReturn(true);
        MetricsRegistry registry = mock(MetricsRegistry.class);
        when(registry.createCounter(any(), any(), any())).thenReturn(counter);
        when(registry.createHistogram(any(), any(), any())).thenReturn(histogram);
        MLMcpConnectorMetricsCounter.reset();
        MLMcpConnectorMetricsCounter.initialize("test-cluster", registry, featureFlag);

        tool = McpSseTool.Factory.getInstance().create(Map.of(MCP_SYNC_CLIENT, mcpSyncClient));
        validParams = Map.of("input", "{\"foo\":\"bar\"}");
    }

    @After
    public void tearDown() {
        MLMcpConnectorMetricsCounter.reset();
    }

    @Test
    public void testRunSuccess() {
        // create a CallToolResult wrapping a JSON string
        McpSchema.CallToolResult result = new McpSchema.CallToolResult(
            List.of(new McpSchema.TextContent("{\"foo\":\"bar\"}")),
            false,
            null,
            Map.of()
        );
        when(mcpSyncClient.callTool(any(McpSchema.CallToolRequest.class))).thenReturn(result);

        tool.run(validParams, listener);

        // Assert
        verify(listener).onResponse("[{\"text\":\"{\\\"foo\\\":\\\"bar\\\"}\"}]");
        verify(listener, never()).onFailure(any());
    }

    @Test
    public void testRunInvalidJsonInput() {
        // Passing a non-JSON string should trigger failure in parsing
        Map<String, String> badParams = Map.of("input", "not-json");
        tool.run(badParams, listener);

        verify(listener).onFailure(any(Exception.class));
        verify(listener, never()).onResponse(any());
    }

    @Test
    public void testRunClientThrows() {
        // Simulate the MCP client throwing an exception
        when(mcpSyncClient.callTool(any())).thenThrow(new RuntimeException("client error"));

        tool.run(validParams, listener);

        verify(listener).onFailure(any(RuntimeException.class));
        verify(listener, never()).onResponse(any());
    }

    @Test
    public void testRunMissingInputParam() {
        // No "input" key in parameters should be caught
        tool.run(Collections.emptyMap(), listener);

        verify(listener).onFailure(any(Exception.class));
        verify(listener, never()).onResponse(any());
    }

    @Test
    public void testValidateAndMetadata() {
        // validate
        assertTrue(tool.validate(validParams));
        assertFalse(tool.validate(Collections.emptyMap()));
        // metadata
        assertEquals(McpSseTool.TYPE, tool.getName());
        assertEquals(McpSseTool.TYPE, tool.getType());
        assertNull(tool.getVersion());
        assertEquals(McpSseTool.DEFAULT_DESCRIPTION, tool.getDescription());
    }

    @Test
    public void testFactoryDefaults() {
        McpSseTool.Factory factory = McpSseTool.Factory.getInstance();
        assertEquals(McpSseTool.DEFAULT_DESCRIPTION, factory.getDefaultDescription());
        assertEquals(McpSseTool.TYPE, factory.getDefaultType());
        assertNull(factory.getDefaultVersion());
        assertTrue(factory.getAllModelKeys().isEmpty());
    }

    @Test
    public void testMetricsEmittedOnSuccessAndFailure() {
        McpSchema.CallToolResult result = new McpSchema.CallToolResult(List.of(new McpSchema.TextContent("{}")), false, null, Map.of());
        when(mcpSyncClient.callTool(any(McpSchema.CallToolRequest.class))).thenReturn(result);
        tool.run(validParams, listener);

        when(mcpSyncClient.callTool(any(McpSchema.CallToolRequest.class))).thenThrow(new RuntimeException("boom"));
        tool.run(validParams, listener);

        ArgumentCaptor<Tags> tagsCaptor = ArgumentCaptor.forClass(Tags.class);
        verify(counter, times(2)).add(eq(1.0), tagsCaptor.capture());
        verify(histogram, times(2)).record(anyDouble(), any(Tags.class));

        Map<String, ?> first = tagsCaptor.getAllValues().get(0).getTagsMap();
        Map<String, ?> second = tagsCaptor.getAllValues().get(1).getTagsMap();
        assertEquals("mcp_sse", first.get("protocol"));
        assertEquals("success", first.get("status"));
        assertEquals("mcp_sse", second.get("protocol"));
        assertEquals("failure", second.get("status"));
    }
}
