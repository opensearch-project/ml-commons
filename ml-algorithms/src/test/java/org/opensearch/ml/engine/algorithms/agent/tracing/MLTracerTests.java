package org.opensearch.ml.engine.algorithms.agent.tracing;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

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

public class MLTracerTests {
    private Tracer mockTracer;
    private MLFeatureEnabledSetting mockFeatureSetting;

    @Before
    public void setup() {
        mockTracer = mock(Tracer.class);
        mockFeatureSetting = mock(MLFeatureEnabledSetting.class);
    }

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

    @Test(expected = IllegalArgumentException.class)
    public void testEndSpanWithNullThrows() {
        MLTracer tracer = new MLTracer(mockTracer, mockFeatureSetting);
        tracer.endSpan(null);
    }

    @Test
    public void testStartSpanWithNoopTracer() {
        MLTracer tracer = new MLTracer(org.opensearch.telemetry.tracing.noop.NoopTracer.INSTANCE, mockFeatureSetting);
        // Do not mock NoopTracer.INSTANCE, just call the method
        Span span = tracer.startSpan("test", new HashMap<>());
        assertNotNull(span);
    }

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

    @Test
    public void testInjectSpanContextNoopTracer() {
        MLTracer tracer = new MLTracer(NoopTracer.INSTANCE, mockFeatureSetting);
        Map<String, String> carrier = new HashMap<>();
        tracer.injectSpanContext(null, carrier); // Should be a no-op, no exception
    }

    @Test
    public void testExtractSpanContextNoopTracer() {
        MLTracer tracer = new MLTracer(NoopTracer.INSTANCE, mockFeatureSetting);
        Map<String, String> carrier = new HashMap<>();
        assertNull(tracer.extractSpanContext(carrier));
    }

    // --- Reflection coverage helpers and tests ---
    public static class TestTracerWithReflection implements Tracer {
        public Object defaultTracer = new DefaultTracer();

        public static class DefaultTracer {
            public Object tracingTelemetry = new TracingTelemetry();
        }

        public static class TracingTelemetry {
            public Span createSpan(SpanCreationContext context, Span parent) {
                return mock(Span.class);
            }

            public Object getContextPropagator() {
                return new ContextPropagator();
            }
        }

        public static class ContextPropagator {
            public void inject(Span span, java.util.function.BiConsumer<String, String> consumer) {
                consumer.accept("key", "value");
            }

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
