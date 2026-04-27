/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.stats.otel.counters;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.Before;
import org.junit.Test;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.telemetry.metrics.Counter;
import org.opensearch.telemetry.metrics.Histogram;
import org.opensearch.telemetry.metrics.MetricsRegistry;

/**
 * Covers the MCP-specific counter singleton scaffolding (initialize/getInstance/reset).
 * The abstract base's emission + feature-flag behavior is exercised via call-site tests.
 */
public class MLMcpMetricsCountersTests {

    private MetricsRegistry registry;
    private MLFeatureEnabledSetting featureFlag;

    @Before
    public void setup() {
        registry = mock(MetricsRegistry.class);
        when(registry.createCounter(any(), any(), any())).thenReturn(mock(Counter.class));
        when(registry.createHistogram(any(), any(), any())).thenReturn(mock(Histogram.class));
        featureFlag = mock(MLFeatureEnabledSetting.class);
        MLMcpConnectorMetricsCounter.reset();
        MLMcpServerMetricsCounter.reset();
    }

    @Test
    public void getInstanceBeforeInitializeThrows() {
        assertThrows(IllegalStateException.class, MLMcpConnectorMetricsCounter::getInstance);
        assertThrows(IllegalStateException.class, MLMcpServerMetricsCounter::getInstance);
    }

    @Test
    public void initializeThenGetInstanceReturnsSingleton() {
        MLMcpConnectorMetricsCounter.initialize("c", registry, featureFlag);
        MLMcpServerMetricsCounter.initialize("c", registry, featureFlag);
        assertNotNull(MLMcpConnectorMetricsCounter.getInstance());
        assertNotNull(MLMcpServerMetricsCounter.getInstance());
    }
}
