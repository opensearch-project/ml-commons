/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.agent.tracing;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

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
        assertEquals(
            "MLAgentTracer is not initialized. Call initialize() first or enable plugins.ml_commons.tracing_enabled setting.",
            exception.getMessage()
        );
    }

    @Test
    public void testInitializeWithFeatureFlagDisabled() {
        when(mockFeatureSetting.isTracingEnabled()).thenReturn(false);
        MLAgentTracer.initialize(mockTracer, mockFeatureSetting);
        assertThrows(IllegalStateException.class, MLAgentTracer::getInstance);
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
    public void testStartSpanReturnsNullIfTracerIsNull() {
        when(mockFeatureSetting.isTracingEnabled()).thenReturn(true);
        when(mockFeatureSetting.isAgentTracingEnabled()).thenReturn(true);
        MLAgentTracer.initialize(null, mockFeatureSetting);
        MLAgentTracer instance = MLAgentTracer.getInstance();
        assertNull(instance.startSpan("test", null, null));
    }

    @Test
    public void testEndSpanDoesNothingIfSpanOrTracerIsNull() {
        when(mockFeatureSetting.isTracingEnabled()).thenReturn(true);
        when(mockFeatureSetting.isAgentTracingEnabled()).thenReturn(true);
        MLAgentTracer.initialize(null, mockFeatureSetting);
        MLAgentTracer instance = MLAgentTracer.getInstance();
        instance.endSpan(null);
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
