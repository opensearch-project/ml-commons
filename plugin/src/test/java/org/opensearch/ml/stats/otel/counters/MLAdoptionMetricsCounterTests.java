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

import java.util.Arrays;

import org.junit.Before;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.stats.otel.metrics.AdoptionMetric;
import org.opensearch.ml.stats.otel.metrics.MetricType;
import org.opensearch.telemetry.metrics.Counter;
import org.opensearch.telemetry.metrics.Histogram;
import org.opensearch.telemetry.metrics.MetricsRegistry;
import org.opensearch.telemetry.metrics.tags.Tags;
import org.opensearch.test.OpenSearchTestCase;

public class MLAdoptionMetricsCounterTests extends OpenSearchTestCase {
    private static final String CLUSTER_NAME = "test-cluster";

    @Mock
    private MLFeatureEnabledSetting mlFeatureEnabledSetting;

    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);
        when(mlFeatureEnabledSetting.isMetricCollectionEnabled()).thenReturn(true);
        MLAdoptionMetricsCounter.reset();
    }

    public void testExceptionThrownForNotInitialized() {
        IllegalStateException exception = assertThrows(IllegalStateException.class, MLAdoptionMetricsCounter::getInstance);
        assertEquals("MLAdoptionMetricsCounter is not initialized. Call initialize() first.", exception.getMessage());
    }

    public void testSingletonInitializationAndIncrement() {
        Counter mockCounter = mock(Counter.class);
        Histogram mockHistogram = mock(Histogram.class);
        MetricsRegistry metricsRegistry = mock(MetricsRegistry.class);
        // Stub the createCounter method to return the mockCounter
        when(metricsRegistry.createCounter(any(), any(), any())).thenReturn(mockCounter);
        when(metricsRegistry.createHistogram(any(), any(), any())).thenReturn(mockHistogram);

        MLAdoptionMetricsCounter.initialize(CLUSTER_NAME, metricsRegistry, mlFeatureEnabledSetting);
        MLAdoptionMetricsCounter instance = MLAdoptionMetricsCounter.getInstance();

        ArgumentCaptor<String> nameCaptor = ArgumentCaptor.forClass(String.class);
        verify(
            metricsRegistry,
            times((int) Arrays.stream(AdoptionMetric.values()).filter(type -> type.getType() == MetricType.COUNTER).count())
        ).createCounter(nameCaptor.capture(), any(), eq("1"));
        verify(
            metricsRegistry,
            times((int) Arrays.stream(AdoptionMetric.values()).filter(type -> type.getType() == MetricType.HISTOGRAM).count())
        ).createHistogram(nameCaptor.capture(), any(), eq("1"));
        assertNotNull(instance);

        instance.incrementCounter(AdoptionMetric.MODEL_COUNT);
        instance.incrementCounter(AdoptionMetric.MODEL_COUNT);
        instance.incrementCounter(AdoptionMetric.MODEL_COUNT);
        verify(mockCounter, times(3)).add(eq(1.0), any(Tags.class));
    }

    public void testMetricCollectionSettings() {
        Counter mockCounter = mock(Counter.class);
        MetricsRegistry metricsRegistry = mock(MetricsRegistry.class);
        when(metricsRegistry.createCounter(any(), any(), any())).thenReturn(mockCounter);

        MLAdoptionMetricsCounter.initialize(CLUSTER_NAME, metricsRegistry, mlFeatureEnabledSetting);
        MLAdoptionMetricsCounter instance = MLAdoptionMetricsCounter.getInstance();

        // Enable
        when(mlFeatureEnabledSetting.isMetricCollectionEnabled()).thenReturn(true);
        instance.incrementCounter(AdoptionMetric.MODEL_COUNT);
        instance.incrementCounter(AdoptionMetric.MODEL_COUNT);
        verify(mockCounter, times(2)).add(eq(1.0), any(Tags.class));

        // Disable
        when(mlFeatureEnabledSetting.isMetricCollectionEnabled()).thenReturn(false);
        instance.incrementCounter(AdoptionMetric.MODEL_COUNT);
        instance.incrementCounter(AdoptionMetric.MODEL_COUNT);
        verify(mockCounter, times(2)).add(anyDouble(), any(Tags.class));

        // Enable
        when(mlFeatureEnabledSetting.isMetricCollectionEnabled()).thenReturn(true);
        instance.incrementCounter(AdoptionMetric.MODEL_COUNT);
        instance.incrementCounter(AdoptionMetric.MODEL_COUNT);
        verify(mockCounter, times(4)).add(eq(1.0), any(Tags.class));
    }
}
