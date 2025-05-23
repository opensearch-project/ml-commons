/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.stats.otel.counters;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.Before;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.stats.otel.metrics.OperationalMetric;
import org.opensearch.telemetry.metrics.Counter;
import org.opensearch.telemetry.metrics.MetricsRegistry;
import org.opensearch.telemetry.metrics.tags.Tags;
import org.opensearch.test.OpenSearchTestCase;

/**
 * Unit tests for the {@link MLOperationalMetricsCounterTests} class.
 */
public class MLOperationalMetricsCounterTests extends OpenSearchTestCase {
    private static final String CLUSTER_NAME = "test-cluster";

    @Mock
    private MLFeatureEnabledSetting mlFeatureEnabledSetting;

    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);
        when(mlFeatureEnabledSetting.isMetricCollectionEnabled()).thenReturn(true);
    }

    public void testSingletonInitializationAndIncrement() {
        Counter mockCounter = mock(Counter.class);
        MetricsRegistry metricsRegistry = mock(MetricsRegistry.class);
        // Stub the createCounter method to return the mockCounter
        when(metricsRegistry.createCounter(any(), any(), any())).thenReturn(mockCounter);

        MLOperationalMetricsCounter.initialize(CLUSTER_NAME, metricsRegistry, mlFeatureEnabledSetting);
        MLOperationalMetricsCounter instance = MLOperationalMetricsCounter.getInstance();

        ArgumentCaptor<String> nameCaptor = ArgumentCaptor.forClass(String.class);
        verify(metricsRegistry, times(OperationalMetric.values().length)).createCounter(nameCaptor.capture(), any(), eq("1"));
        assertNotNull(instance);

        instance.incrementCounter(OperationalMetric.MODEL_PREDICT_COUNT);
        instance.incrementCounter(OperationalMetric.MODEL_PREDICT_COUNT);
        instance.incrementCounter(OperationalMetric.MODEL_PREDICT_COUNT);
        verify(mockCounter, times(3)).add(eq(1.0), any(Tags.class));
    }

    public void testMetricCollectionSettings() {
        Counter mockCounter = mock(Counter.class);
        MetricsRegistry metricsRegistry = mock(MetricsRegistry.class);
        when(metricsRegistry.createCounter(any(), any(), any())).thenReturn(mockCounter);

        MLOperationalMetricsCounter.initialize(CLUSTER_NAME, metricsRegistry, mlFeatureEnabledSetting);
        MLOperationalMetricsCounter instance = MLOperationalMetricsCounter.getInstance();

        // Enable
        when(mlFeatureEnabledSetting.isMetricCollectionEnabled()).thenReturn(true);
        instance.incrementCounter(OperationalMetric.MODEL_PREDICT_COUNT);
        instance.incrementCounter(OperationalMetric.MODEL_PREDICT_COUNT);
        verify(mockCounter, times(2)).add(eq(1.0), any(Tags.class));

        // Disable
        when(mlFeatureEnabledSetting.isMetricCollectionEnabled()).thenReturn(false);
        instance.incrementCounter(OperationalMetric.MODEL_PREDICT_COUNT);
        instance.incrementCounter(OperationalMetric.MODEL_PREDICT_COUNT);
        verify(mockCounter, times(2)).add(anyDouble(), any(Tags.class));

        // Enable
        when(mlFeatureEnabledSetting.isMetricCollectionEnabled()).thenReturn(true);
        instance.incrementCounter(OperationalMetric.MODEL_PREDICT_COUNT);
        instance.incrementCounter(OperationalMetric.MODEL_PREDICT_COUNT);
        verify(mockCounter, times(4)).add(eq(1.0), any(Tags.class));
    }
}
