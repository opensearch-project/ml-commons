/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.stats.otel.counters;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.stats.otel.metrics.McpConnectorMetric;
import org.opensearch.ml.stats.otel.metrics.McpServerMetric;
import org.opensearch.ml.stats.otel.metrics.MetricType;
import org.opensearch.telemetry.metrics.Counter;
import org.opensearch.telemetry.metrics.Histogram;
import org.opensearch.telemetry.metrics.MetricsRegistry;
import org.opensearch.telemetry.metrics.tags.Tags;

public class MLMcpMetricsCountersTests {

    private static final String CLUSTER_NAME = "test-cluster";

    @Mock
    private MLFeatureEnabledSetting mlFeatureEnabledSetting;

    private Counter mockCounter;
    private Histogram mockHistogram;
    private MetricsRegistry metricsRegistry;

    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);
        when(mlFeatureEnabledSetting.isMetricCollectionEnabled()).thenReturn(true);
        mockCounter = mock(Counter.class);
        mockHistogram = mock(Histogram.class);
        metricsRegistry = mock(MetricsRegistry.class);
        when(metricsRegistry.createCounter(any(), any(), any())).thenReturn(mockCounter);
        when(metricsRegistry.createHistogram(any(), any(), any())).thenReturn(mockHistogram);
        MLMcpConnectorMetricsCounter.reset();
        MLMcpServerMetricsCounter.reset();
    }

    @After
    public void tearDown() {
        MLMcpConnectorMetricsCounter.reset();
        MLMcpServerMetricsCounter.reset();
    }

    @Test
    public void testExceptionThrownForNotInitialized() {
        IllegalStateException connectorEx = assertThrows(IllegalStateException.class, MLMcpConnectorMetricsCounter::getInstance);
        assertEquals("MLMcpConnectorMetricsCounter is not initialized. Call initialize() first.", connectorEx.getMessage());

        IllegalStateException serverEx = assertThrows(IllegalStateException.class, MLMcpServerMetricsCounter::getInstance);
        assertEquals("MLMcpServerMetricsCounter is not initialized. Call initialize() first.", serverEx.getMessage());
    }

    @Test
    public void testConnectorSingletonInitializationAndIncrement() {
        MLMcpConnectorMetricsCounter.initialize(CLUSTER_NAME, metricsRegistry, mlFeatureEnabledSetting);
        MLMcpConnectorMetricsCounter instance = MLMcpConnectorMetricsCounter.getInstance();

        verify(
            metricsRegistry,
            times((int) Arrays.stream(McpConnectorMetric.values()).filter(m -> m.getType() == MetricType.COUNTER).count())
        ).createCounter(any(), any(), eq("1"));
        verify(
            metricsRegistry,
            times((int) Arrays.stream(McpConnectorMetric.values()).filter(m -> m.getType() == MetricType.HISTOGRAM).count())
        ).createHistogram(any(), any(), eq("1"));

        instance.incrementCounter(McpConnectorMetric.MCP_CONNECTOR_COUNT);
        instance.incrementCounter(McpConnectorMetric.MCP_CONNECTOR_COUNT);
        verify(mockCounter, times(2)).add(eq(1.0), any(Tags.class));

        instance.recordHistogram(McpConnectorMetric.MCP_CONNECTOR_TOOL_INVOCATION_LATENCY, 12.5);
        verify(mockHistogram, times(1)).record(eq(12.5), any(Tags.class));
    }

    @Test
    public void testServerSingletonInitializationAndIncrement() {
        MLMcpServerMetricsCounter.initialize(CLUSTER_NAME, metricsRegistry, mlFeatureEnabledSetting);
        MLMcpServerMetricsCounter instance = MLMcpServerMetricsCounter.getInstance();

        verify(metricsRegistry, times((int) Arrays.stream(McpServerMetric.values()).filter(m -> m.getType() == MetricType.COUNTER).count()))
            .createCounter(any(), any(), eq("1"));
        verify(
            metricsRegistry,
            times((int) Arrays.stream(McpServerMetric.values()).filter(m -> m.getType() == MetricType.HISTOGRAM).count())
        ).createHistogram(any(), any(), eq("1"));

        instance.incrementCounter(McpServerMetric.MCP_SERVER_REQUEST_COUNT);
        instance.incrementCounter(McpServerMetric.MCP_SERVER_REQUEST_COUNT);
        verify(mockCounter, times(2)).add(eq(1.0), any(Tags.class));

        instance.recordHistogram(McpServerMetric.MCP_SERVER_TOOL_CALL_LATENCY, 7.0);
        verify(mockHistogram, times(1)).record(eq(7.0), any(Tags.class));
    }

    @Test
    public void testMetricCollectionSettings() {
        MLMcpConnectorMetricsCounter.initialize(CLUSTER_NAME, metricsRegistry, mlFeatureEnabledSetting);
        MLMcpConnectorMetricsCounter instance = MLMcpConnectorMetricsCounter.getInstance();

        when(mlFeatureEnabledSetting.isMetricCollectionEnabled()).thenReturn(true);
        instance.incrementCounter(McpConnectorMetric.MCP_CONNECTOR_COUNT);
        instance.incrementCounter(McpConnectorMetric.MCP_CONNECTOR_COUNT);
        verify(mockCounter, times(2)).add(eq(1.0), any(Tags.class));

        when(mlFeatureEnabledSetting.isMetricCollectionEnabled()).thenReturn(false);
        instance.incrementCounter(McpConnectorMetric.MCP_CONNECTOR_COUNT);
        instance.incrementCounter(McpConnectorMetric.MCP_CONNECTOR_COUNT);
        verify(mockCounter, times(2)).add(anyDouble(), any(Tags.class));

        when(mlFeatureEnabledSetting.isMetricCollectionEnabled()).thenReturn(true);
        instance.incrementCounter(McpConnectorMetric.MCP_CONNECTOR_COUNT);
        verify(mockCounter, times(3)).add(eq(1.0), any(Tags.class));
    }
}
