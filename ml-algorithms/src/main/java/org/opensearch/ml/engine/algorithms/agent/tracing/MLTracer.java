package org.opensearch.ml.engine.algorithms.agent.tracing;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;

import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.telemetry.tracing.Span;
import org.opensearch.telemetry.tracing.SpanContext;
import org.opensearch.telemetry.tracing.SpanCreationContext;
import org.opensearch.telemetry.tracing.Tracer;
import org.opensearch.telemetry.tracing.attributes.Attributes;
import org.opensearch.telemetry.tracing.noop.NoopTracer;

import lombok.extern.log4j.Log4j2;

/**
 * MLTracer provides tracing utilities for ML agent operations, supporting span creation, context propagation, and span completion.
 * It abstracts the underlying tracer implementation and provides reflection-based logic for root span creation and context injection/extraction.
 * This class is intended to be extended by concrete tracers such as MLAgentTracer.
 */
@Log4j2
public class MLTracer extends AbstractMLTracer {
    /**
     * Constructs an MLTracer with the given tracer and feature settings.
     *
     * @param tracer The tracer implementation to use (may be a real tracer or NoopTracer).
     * @param mlFeatureEnabledSetting The ML feature settings.
     */
    public MLTracer(Tracer tracer, MLFeatureEnabledSetting mlFeatureEnabledSetting) {
        super(tracer, mlFeatureEnabledSetting);
    }

    /**
     * Starts a new span for agent tracing with the specified name and attributes, and no parent span.
     * <p>
     * This is for starting a root span.
     * <p>
     * The returned span should be passed to {@link #endSpan(Span)} when the operation completes.
     *
     * @param name The name of the span. Should follow the naming convention defined by
     *             the span constants (e.g., AGENT_TASK_SPAN, AGENT_TOOL_CALL_SPAN).
     * @param attributes A map of key-value pairs to associate with the span. These
     *                  provide additional context about the operation being traced.
     *                  May be null or empty if no attributes are needed.
     * @return A Span object representing the started root span, or null if tracing is disabled.
     */
    @Override
    public Span startSpan(String name, Map<String, String> attributes) {
        Attributes attrBuilder = Attributes.create();
        if (attributes != null && !attributes.isEmpty()) {
            for (Map.Entry<String, String> entry : attributes.entrySet()) {
                String key = entry.getKey();
                String value = entry.getValue();
                if (key != null && value != null) {
                    attrBuilder.addAttribute(key, value);
                }
            }
        }
        SpanCreationContext context = SpanCreationContext.server().name(name).attributes(attrBuilder);
        Span newSpan;
        if (!(tracer instanceof NoopTracer)) {
            // Force agent.task* spans to be root span for real tracer
            try {
                Field defaultTracerField = tracer.getClass().getDeclaredField("defaultTracer");
                defaultTracerField.setAccessible(true);
                Object defaultTracer = defaultTracerField.get(tracer);

                Field tracingTelemetryField = defaultTracer.getClass().getDeclaredField("tracingTelemetry");
                tracingTelemetryField.setAccessible(true);
                Object tracingTelemetry = tracingTelemetryField.get(defaultTracer);

                Method createSpanMethod = tracingTelemetry.getClass().getMethod("createSpan", SpanCreationContext.class, Span.class);
                createSpanMethod.setAccessible(true);

                newSpan = (Span) createSpanMethod.invoke(tracingTelemetry, context, null);

                newSpan.addAttribute("thread.name", Thread.currentThread().getName());
            } catch (Exception e) {
                log.warn("Failed to create root span, falling back to normal span creation", e);
                newSpan = tracer.startSpan(context);
            }
        } else {
            newSpan = tracer.startSpan(context);
        }
        return newSpan;
    }

    /**
     * Starts a new span for agent tracing.
     *
     * This method creates a new span with the specified name and attributes. For agent.task*
     * spans, this method attempts to create them as root spans to ensure proper trace
     * grouping. If the reflection-based root span creation fails, it falls back to
     * normal span creation which might result in ghost parent span.
     *
     * The method handles both real tracers and NoopTracer instances. When using a real
     * tracer, spans are created with proper parent-child relationships. When using
     * NoopTracer, the spans are no-ops but still maintain the expected interface.
     *
     * @param name The name of the span. Should follow the naming convention defined by
     *             the span constants (e.g., AGENT_TASK_SPAN, AGENT_TOOL_CALL_SPAN).
     * @param attributes A map of key-value pairs to associate with the span. These
     *                  provide additional context about the operation being traced.
     *                  May be null or empty if no attributes are needed.
     * @param parentSpan The parent span, or null if this should be a root span.
     *                  For agent.task* spans, this parameter is ignored when using
     *                  real tracers as they are forced to be root spans.
     * @return A Span object representing the started span, or null if tracing is disabled.
     *         The returned span should be passed to {@link #endSpan(Span)} when the
     *         operation completes.
     */
    @Override
    public Span startSpan(String name, Map<String, String> attributes, Span parentSpan) {
        Attributes attrBuilder = Attributes.create();
        if (attributes != null && !attributes.isEmpty()) {
            for (Map.Entry<String, String> entry : attributes.entrySet()) {
                String key = entry.getKey();
                String value = entry.getValue();
                if (key != null && value != null) {
                    attrBuilder.addAttribute(key, value);
                }
            }
        }
        SpanCreationContext context = SpanCreationContext.server().name(name).attributes(attrBuilder);
        if (parentSpan != null) {
            context = context.parent(new SpanContext(parentSpan));
        }
        return tracer.startSpan(context);
    }

    /**
     * Ends the given span.
     *
     * This method marks the completion of a span and finalizes its timing information.
     * The span will be recorded in the trace with its start time, end time, and any
     * attributes that were set during its lifetime.
     *
     * @param span The span to end. This should be the same Span object that was returned
     *             by a previous call to {@link #startSpan(String, Map, Span)}. If null,
     *             an IllegalArgumentException is thrown.
     * @throws IllegalArgumentException if the span parameter is null.
     */
    @Override
    public void endSpan(Span span) {
        if (span == null) {
            throw new IllegalArgumentException("Span cannot be null");
        }
        span.endSpan();
    }

    /**
     * Returns the underlying tracer implementation.
     *
     * This method provides access to the tracer instance that is currently being used
     * by this MLAgentTracer. The returned tracer may be either a real tracer implementation
     * or a NoopTracer, depending on the current configuration and feature settings.
     *
     * @return The tracer instance currently in use. This may be a real tracer or
     *         NoopTracer.INSTANCE if tracing is disabled.
     */
    public Tracer getTracer() {
        return tracer;
    }

    /**
     * Injects the span context into a carrier map using the TracingContextPropagator.
     *
     * This method serializes the span context into a map that can be transmitted
     * across process boundaries (e.g., in HTTP headers, message queues, etc.).
     * The injected context can later be extracted using {@link #extractSpanContext(Map)}
     * to continue the trace in another process or thread.
     *
     * The method uses reflection to access the underlying tracing telemetry components,
     * as the OpenSearch tracing API doesn't provide direct access to context propagation.
     * If the reflection fails, the method logs a warning but doesn't throw an exception.
     *
     * @param span The span whose context to inject. If null, this method is a no-op.
     * @param carrier The map to inject context into. The span context will be added
     *               as key-value pairs to this map. Must not be null.
     */
    public void injectSpanContext(Span span, Map<String, String> carrier) {
        if (tracer instanceof NoopTracer) {
            return;
        }
        try {
            Field defaultTracerField = tracer.getClass().getDeclaredField("defaultTracer");
            defaultTracerField.setAccessible(true);
            Object defaultTracer = defaultTracerField.get(tracer);

            Field tracingTelemetryField = defaultTracer.getClass().getDeclaredField("tracingTelemetry");
            tracingTelemetryField.setAccessible(true);
            Object tracingTelemetry = tracingTelemetryField.get(defaultTracer);

            Method getContextPropagatorMethod = tracingTelemetry.getClass().getMethod("getContextPropagator");
            Object propagator = getContextPropagatorMethod.invoke(tracingTelemetry);

            Method injectMethod = propagator.getClass().getMethod("inject", Span.class, BiConsumer.class);
            injectMethod.invoke(propagator, span, (BiConsumer<String, String>) carrier::put);
        } catch (Exception e) {
            log.warn("Failed to inject span context", e);
        }
    }

    /**
     * Extracts a parent span from a carrier map using the TracingContextPropagator.
     *
     * This method deserializes a span context from a map that was previously created
     * by {@link #injectSpanContext(Span, Map)}. The extracted context can be used
     * as a parent span to continue a trace across process boundaries.
     *
     * The method uses reflection to access the underlying tracing telemetry components,
     * as the OpenSearch tracing API doesn't provide direct access to context propagation.
     * If the reflection fails or no context is found, the method returns null and logs
     * a warning.
     *
     * @param carrier The map containing the context. This should be the same map that
     *               was populated by a previous call to {@link #injectSpanContext(Span, Map)}.
     *               May be null or empty, in which case null is returned.
     * @return The extracted parent span, or null if no context is found, the carrier
     *         is null/empty, or tracing is disabled (NoopTracer is being used).
     */
    public Span extractSpanContext(Map<String, String> carrier) {
        if (tracer instanceof NoopTracer) {
            return null;
        }
        try {
            Field defaultTracerField = tracer.getClass().getDeclaredField("defaultTracer");
            defaultTracerField.setAccessible(true);
            Object defaultTracer = defaultTracerField.get(tracer);

            Field tracingTelemetryField = defaultTracer.getClass().getDeclaredField("tracingTelemetry");
            tracingTelemetryField.setAccessible(true);
            Object tracingTelemetry = tracingTelemetryField.get(defaultTracer);

            Method getContextPropagatorMethod = tracingTelemetry.getClass().getMethod("getContextPropagator");
            Object propagator = getContextPropagatorMethod.invoke(tracingTelemetry);

            Method extractMethod = propagator.getClass().getMethod("extract", Map.class);
            Optional<?> spanOpt = (Optional<?>) extractMethod.invoke(propagator, carrier);
            if (spanOpt.isPresent()) {
                return (Span) spanOpt.get();
            }
        } catch (Exception e) {
            log.warn("Failed to extract span context", e);
        }
        return null;
    }
}
