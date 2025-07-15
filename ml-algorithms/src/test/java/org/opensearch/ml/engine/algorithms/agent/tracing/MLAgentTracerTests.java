/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.agent.tracing;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.Before;
import org.junit.Test;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.telemetry.tracing.Tracer;
import org.opensearch.telemetry.tracing.noop.NoopTracer;

public class MLAgentTracerTests {
    private MLFeatureEnabledSetting mockFeatureSetting;
    private Tracer mockTracer;

    @Before
    public void setup() {
        mockFeatureSetting = mock(MLFeatureEnabledSetting.class);
        mockTracer = mock(Tracer.class);
        MLAgentTracer.resetForTest();
    }

    @Test
    public void testExceptionThrownForNotInitialized() {
        IllegalStateException exception = assertThrows(IllegalStateException.class, MLAgentTracer::getInstance);
        String msg = exception.getMessage();
        assertEquals("MLAgentTracer is not initialized. Call initialize() first before using getInstance().", msg);
    }

    @Test
    public void testInitializeWithFeatureFlagDisabled() {
        when(mockFeatureSetting.isTracingEnabled()).thenReturn(false);
        MLAgentTracer.initialize(mockTracer, mockFeatureSetting);
        MLAgentTracer instance = MLAgentTracer.getInstance();
        assertNotNull(instance);
        assertTrue(instance.getTracer() instanceof NoopTracer);
    }

    @Test
    public void testInitializeWithFeatureFlagEnabledAndDynamicEnabled() {
        when(mockFeatureSetting.isTracingEnabled()).thenReturn(true);
        when(mockFeatureSetting.isAgentTracingEnabled()).thenReturn(true);
        MLAgentTracer.initialize(mockTracer, mockFeatureSetting);
        MLAgentTracer instance = MLAgentTracer.getInstance();
        assertNotNull(instance);
        assertEquals(mockTracer, instance.getTracer());
    }

    @Test
    public void testInitializeWithFeatureFlagEnabledAndDynamicDisabled() {
        when(mockFeatureSetting.isTracingEnabled()).thenReturn(true);
        when(mockFeatureSetting.isAgentTracingEnabled()).thenReturn(false);
        MLAgentTracer.initialize(mockTracer, mockFeatureSetting);
        MLAgentTracer instance = MLAgentTracer.getInstance();
        assertNotNull(instance);
        assertTrue(instance.getTracer() instanceof NoopTracer);
    }

    @Test
    public void testStartSpanWorksWithNullTracer() {
        when(mockFeatureSetting.isTracingEnabled()).thenReturn(true);
        when(mockFeatureSetting.isAgentTracingEnabled()).thenReturn(true);
        MLAgentTracer.initialize(null, mockFeatureSetting);
        MLAgentTracer instance = MLAgentTracer.getInstance();
        assertNotNull(instance);
        assertTrue(instance.getTracer() instanceof NoopTracer);
        // Should not throw exception when using NoopTracer
        instance.startSpan("test", null, null);
    }

    @Test
    public void testEndSpanThrowsExceptionIfSpanIsNull() {
        when(mockFeatureSetting.isTracingEnabled()).thenReturn(true);
        when(mockFeatureSetting.isAgentTracingEnabled()).thenReturn(true);
        MLAgentTracer.initialize(mockTracer, mockFeatureSetting);
        MLAgentTracer instance = MLAgentTracer.getInstance();
        assertThrows(IllegalArgumentException.class, () -> instance.endSpan(null));
    }

    @Test
    public void testGetTracerReturnsTracer() {
        when(mockFeatureSetting.isTracingEnabled()).thenReturn(true);
        when(mockFeatureSetting.isAgentTracingEnabled()).thenReturn(true);
        MLAgentTracer.initialize(mockTracer, mockFeatureSetting);
        MLAgentTracer instance = MLAgentTracer.getInstance();
        assertEquals(mockTracer, instance.getTracer());
    }
}
