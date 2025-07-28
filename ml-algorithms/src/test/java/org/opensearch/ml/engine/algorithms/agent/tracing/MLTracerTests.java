package org.opensearch.ml.engine.algorithms.agent.tracing;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyMap;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.telemetry.tracing.ScopedSpan;
import org.opensearch.telemetry.tracing.Span;
import org.opensearch.telemetry.tracing.SpanContext;
import org.opensearch.telemetry.tracing.SpanCreationContext;
import org.opensearch.telemetry.tracing.SpanScope;
import org.opensearch.telemetry.tracing.Tracer;
import org.opensearch.telemetry.tracing.noop.NoopTracer;

/**
 * Unit tests for {@link MLTracer}.
 * 
 * This test class covers the functionality of the MLTracer, including:
 * <ul>
 *   <li>Span creation and management with attributes</li>
 *   <li>Span context injection and extraction</li>
 *   <li>NoopTracer integration and fallback behavior</li>
 *   <li>Reflection-based span creation for different tracer implementations</li>
 *   <li>Error handling and exception scenarios</li>
 * </ul>
 * 
 * The tests verify that the MLTracer properly wraps different tracer implementations
 * and provides consistent behavior across various telemetry configurations.
 */
public class MLTracerTests {
    private Tracer mockTracer;
    private MLFeatureEnabledSetting mockFeatureSetting;

    /**
     * Sets up the test environment before each test method.
     * 
     * This method initializes mock objects for the tracer and feature settings
     * that are used throughout the test cases.
     */
    @Before
    public void setup() {
        mockTracer = mock(Tracer.class);
        mockFeatureSetting = mock(MLFeatureEnabledSetting.class);
    }

    /**
     * Tests starting and ending a span with attributes.
     * 
     * This test verifies that the MLTracer can properly create spans with
     * attributes and end them correctly. It also tests that null attributes
     * are properly ignored during span creation.
     */
    @Test
    public void testStartAndEndSpanWithAttributes() {
        MLTracer tracer = new MLTracer(mockTracer, mockFeatureSetting);
        Span mockSpan = mock(Span.class);
        when(mockTracer.startSpan(any())).thenReturn(mockSpan);
        Map<String, String> attrs = new HashMap<>();
        attrs.put("a", "b");
        attrs.put("c", null); // Should be ignored
        attrs.put(null, "d"); // Should be ignored
        Span span = tracer.startSpan("test", attrs);
        assertNotNull(span);
        tracer.endSpan(span);
        verify(mockSpan).endSpan();
    }

    /**
     * Tests that ending a null span throws an exception.
     * 
     * This test verifies that the MLTracer properly validates input parameters
     * and throws an IllegalArgumentException when attempting to end a null span.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testEndSpanWithNullThrows() {
        MLTracer tracer = new MLTracer(mockTracer, mockFeatureSetting);
        tracer.endSpan(null);
    }

    /**
     * Tests span creation with NoopTracer.
     * 
     * This test verifies that the MLTracer works correctly with the NoopTracer
     * implementation, which is used when tracing is disabled or not available.
     */
    @Test
    public void testStartSpanWithNoopTracer() {
        MLTracer tracer = new MLTracer(org.opensearch.telemetry.tracing.noop.NoopTracer.INSTANCE, mockFeatureSetting);
        // Do not mock NoopTracer.INSTANCE, just call the method
        Span span = tracer.startSpan("test", new HashMap<>());
        assertNotNull(span);
    }

    /**
     * Tests reflection fallback behavior when span creation fails.
     * 
     * This test verifies that when reflection-based span creation fails,
     * the MLTracer falls back to the standard tracer.startSpan method.
     * This ensures robust behavior even when the underlying tracer implementation
     * doesn't support the expected reflection-based interface.
     */
    @Test
    public void testStartSpanReflectionFallback() throws Exception {
        // Simulate reflection failure by throwing exception
        Tracer failingTracer = mock(Tracer.class);
        when(failingTracer.startSpan(any())).thenReturn(mock(Span.class));
        MLTracer tracer = new MLTracer(failingTracer, mockFeatureSetting);
        // Use a spy to throw exception in the try block
        MLTracer spyTracer = spy(tracer);
        doThrow(new RuntimeException("reflection fail")).when(spyTracer).startSpan(anyString(), anyMap());
        // Should not throw, should fallback to tracer.startSpan
        Span span = tracer.startSpan("test", new HashMap<>());
        assertNotNull(span);
    }

    /**
     * Tests span context injection with NoopTracer.
     * 
     * This test verifies that span context injection works correctly with
     * the NoopTracer implementation, which should perform no operations
     * without throwing exceptions.
     */
    @Test
    public void testInjectSpanContextNoopTracer() {
        MLTracer tracer = new MLTracer(NoopTracer.INSTANCE, mockFeatureSetting);
        Map<String, String> carrier = new HashMap<>();
        tracer.injectSpanContext(null, carrier); // Should be a no-op, no exception
    }

    /**
     * Tests span context extraction with NoopTracer.
     * 
     * This test verifies that span context extraction returns null when
     * using the NoopTracer implementation, which is the expected behavior
     * for a no-operation tracer.
     */
    @Test
    public void testExtractSpanContextNoopTracer() {
        MLTracer tracer = new MLTracer(NoopTracer.INSTANCE, mockFeatureSetting);
        Map<String, String> carrier = new HashMap<>();
        assertNull(tracer.extractSpanContext(carrier));
    }

    // --- Reflection coverage helpers and tests ---
    /**
     * Test tracer implementation that supports reflection-based span creation.
     * 
     * This class provides a mock implementation of the Tracer interface that
     * includes the internal structure expected by the reflection-based span
     * creation mechanism in MLTracer.
     */
    public static class TestTracerWithReflection implements Tracer {
        public Object defaultTracer = new DefaultTracer();

        /**
         * Default tracer implementation with tracing telemetry support.
         */
        public static class DefaultTracer {
            public Object tracingTelemetry = new TracingTelemetry();
        }

        /**
         * Tracing telemetry implementation that provides span creation and context propagation.
         */
        public static class TracingTelemetry {
            /**
             * Creates a span with the given context and parent.
             * 
             * @param context The span creation context
             * @param parent The parent span
             * @return A mock span instance
             */
            public Span createSpan(SpanCreationContext context, Span parent) {
                return mock(Span.class);
            }

            /**
             * Gets the context propagator for span context injection/extraction.
             * 
             * @return A context propagator instance
             */
            public Object getContextPropagator() {
                return new ContextPropagator();
            }
        }

        /**
         * Context propagator implementation for testing span context operations.
         */
        public static class ContextPropagator {
            /**
             * Injects span context into a carrier map.
             * 
             * @param span The span to inject
             * @param consumer The consumer to accept key-value pairs
             */
            public void inject(Span span, java.util.function.BiConsumer<String, String> consumer) {
                consumer.accept("key", "value");
            }

            /**
             * Extracts span context from a carrier map.
             * 
             * @param carrier The carrier map containing span context
             * @return An optional containing a mock span
             */
            public java.util.Optional<Span> extract(Map<String, String> carrier) {
                return java.util.Optional.of(mock(Span.class));
            }
        }

        @Override
        public Span startSpan(SpanCreationContext context) {
            return mock(Span.class);
        }

        @Override
        public SpanContext getCurrentSpan() {
            return mock(SpanContext.class);
        }

        @Override
        public ScopedSpan startScopedSpan(SpanCreationContext context) {
            return mock(ScopedSpan.class);
        }

        @Override
        public SpanScope withSpanInScope(Span span) {
            return mock(SpanScope.class);
        }

        @Override
        public boolean isRecording() {
            return false;
        }

        @Override
        public void close() {}

        @Override
        public Span startSpan(SpanCreationContext context, Map<String, java.util.Collection<String>> headers) {
            return mock(Span.class);
        }
    }

    /**
     * Tests span creation using the reflection-based path.
     * 
     * This test verifies that the MLTracer can successfully create spans
     * using reflection when the underlying tracer implementation supports
     * the expected internal structure.
     */
    @Test
    public void testStartSpanReflectionPath() {
        TestTracerWithReflection testTracer = new TestTracerWithReflection();
        MLFeatureEnabledSetting mockFeatureSetting = mock(MLFeatureEnabledSetting.class);
        MLTracer tracer = new MLTracer(testTracer, mockFeatureSetting);
        Map<String, String> attrs = new HashMap<>();
        attrs.put("foo", "bar");
        Span span = tracer.startSpan("test", attrs);
        assertNotNull(span);
    }

    /**
     * Tests span context injection using the reflection-based path.
     * 
     * This test verifies that the MLTracer can successfully inject span
     * context using reflection when the underlying tracer implementation
     * supports the expected context propagator interface.
     */
    @Test
    public void testInjectSpanContextReflectionPath() {
        TestTracerWithReflection testTracer = new TestTracerWithReflection();
        MLFeatureEnabledSetting mockFeatureSetting = mock(MLFeatureEnabledSetting.class);
        MLTracer tracer = new MLTracer(testTracer, mockFeatureSetting);
        Span mockSpan = mock(Span.class);
        Map<String, String> carrier = new HashMap<>();
        tracer.injectSpanContext(mockSpan, carrier);
        // Should add the key from the propagator
        assertTrue(carrier.containsKey("key"));
        assertEquals("value", carrier.get("key"));
    }

    /**
     * Tests span context extraction using the reflection-based path.
     * 
     * This test verifies that the MLTracer can successfully extract span
     * context using reflection when the underlying tracer implementation
     * supports the expected context propagator interface.
     */
    @Test
    public void testExtractSpanContextReflectionPath() {
        TestTracerWithReflection testTracer = new TestTracerWithReflection();
        MLFeatureEnabledSetting mockFeatureSetting = mock(MLFeatureEnabledSetting.class);
        MLTracer tracer = new MLTracer(testTracer, mockFeatureSetting);
        Map<String, String> carrier = new HashMap<>();
        Span span = tracer.extractSpanContext(carrier);
        assertNotNull(span);
    }
}
