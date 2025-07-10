/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.agent.tracing;

import java.util.Map;

import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.repackage.com.google.common.annotations.VisibleForTesting;
import org.opensearch.telemetry.tracing.Span;
import org.opensearch.telemetry.tracing.SpanContext;
import org.opensearch.telemetry.tracing.SpanCreationContext;
import org.opensearch.telemetry.tracing.Tracer;
import org.opensearch.telemetry.tracing.attributes.Attributes;
import org.opensearch.telemetry.tracing.noop.NoopTracer;

import lombok.extern.log4j.Log4j2;

@Log4j2
public class MLAgentTracer extends AbstractMLTracer {
    private static MLAgentTracer instance;
    private static boolean tracingFlagSet = false;

    private MLAgentTracer(Tracer tracer, MLFeatureEnabledSetting mlFeatureEnabledSetting) {
        super(tracer, mlFeatureEnabledSetting);
    }

    public static synchronized void initialize(Tracer tracer, MLFeatureEnabledSetting mlFeatureEnabledSetting) {
        if (mlFeatureEnabledSetting == null || !mlFeatureEnabledSetting.isTracingEnabled()) {
            instance = null;
            tracingFlagSet = false;
            log.info("MLAgentTracer not initialized: agent tracing feature flag is disabled.");
            return;
        }
        tracingFlagSet = true;
        Tracer tracerToUse = mlFeatureEnabledSetting.isAgentTracingEnabled() ? tracer : NoopTracer.INSTANCE;
        instance = new MLAgentTracer(tracerToUse, mlFeatureEnabledSetting);
        log.info("MLAgentTracer initialized with {}", tracerToUse.getClass().getSimpleName());
    }

    public static synchronized MLAgentTracer getInstance() {
        if (!tracingFlagSet) {
            throw new IllegalStateException(
                "MLAgentTracer is not enabled. Please set plugins.ml_commons.tracing_enabled to true in your OpenSearch configuration."
            );
        }
        if (instance == null) {
            throw new IllegalStateException("MLAgentTracer is not initialized. Call initialize() first before using getInstance().");
        }
        return instance;
    }

    @Override
    public Span startSpan(String name, Map<String, String> attributes, Span parentSpan) {
        if (tracer == null || tracer instanceof NoopTracer) {
            return null;
        }
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
        if ("agent.task".equals(name)) {
            // Force agent.task spans to be root span
            try {
                java.lang.reflect.Field defaultTracerField = tracer.getClass().getDeclaredField("defaultTracer");
                defaultTracerField.setAccessible(true);
                Object defaultTracer = defaultTracerField.get(tracer);

                java.lang.reflect.Field tracingTelemetryField = defaultTracer.getClass().getDeclaredField("tracingTelemetry");
                tracingTelemetryField.setAccessible(true);
                Object tracingTelemetry = tracingTelemetryField.get(defaultTracer);

                java.lang.reflect.Method createSpanMethod = tracingTelemetry
                    .getClass()
                    .getMethod("createSpan", SpanCreationContext.class, Span.class);
                createSpanMethod.setAccessible(true);

                newSpan = (Span) createSpanMethod.invoke(tracingTelemetry, context, null);

                newSpan.addAttribute("thread.name", Thread.currentThread().getName());
            } catch (Exception e) {
                log.warn("Failed to create root span for agent.task, falling back to normal span creation", e);
                if (parentSpan != null) {
                    context = context.parent(new SpanContext(parentSpan));
                }
                newSpan = tracer.startSpan(context);
            }
        } else {
            if (parentSpan != null) {
                context = context.parent(new SpanContext(parentSpan));
            }
            newSpan = tracer.startSpan(context);
        }

        return newSpan;
    }

    @Override
    public void endSpan(Span span) {
        if (span == null || tracer == null) {
            return;
        }
        span.endSpan();
    }

    public Tracer getTracer() {
        return tracer;
    }

    @VisibleForTesting
    static void resetForTest() {
        instance = null;
    }

    /**
     * Injects the span context into a carrier map using the TracingContextPropagator
     * @param span The span whose context to inject
     * @param carrier The map to inject context into
     */
    public void injectSpanContext(Span span, Map<String, String> carrier) {
        if (tracer == null || tracer instanceof NoopTracer) {
            return;
        }

        try {
            java.lang.reflect.Field defaultTracerField = tracer.getClass().getDeclaredField("defaultTracer");
            defaultTracerField.setAccessible(true);
            Object defaultTracer = defaultTracerField.get(tracer);

            java.lang.reflect.Field tracingTelemetryField = defaultTracer.getClass().getDeclaredField("tracingTelemetry");
            tracingTelemetryField.setAccessible(true);
            Object tracingTelemetry = tracingTelemetryField.get(defaultTracer);

            java.lang.reflect.Method getContextPropagatorMethod = tracingTelemetry.getClass().getMethod("getContextPropagator");
            Object propagator = getContextPropagatorMethod.invoke(tracingTelemetry);

            java.lang.reflect.Method injectMethod = propagator
                .getClass()
                .getMethod("inject", Span.class, java.util.function.BiConsumer.class);
            injectMethod.invoke(propagator, span, (java.util.function.BiConsumer<String, String>) carrier::put);
        } catch (Exception e) {
            log.warn("Failed to inject span context", e);
        }
    }

    /**
     * Extracts a parent span from a carrier map using the TracingContextPropagator
     * @param carrier The map containing the context
     * @return The extracted parent span, or null if not found
     */
    public Span extractSpanContext(Map<String, String> carrier) {
        if (tracer == null || tracer instanceof NoopTracer) {
            return null;
        }

        try {
            java.lang.reflect.Field defaultTracerField = tracer.getClass().getDeclaredField("defaultTracer");
            defaultTracerField.setAccessible(true);
            Object defaultTracer = defaultTracerField.get(tracer);

            java.lang.reflect.Field tracingTelemetryField = defaultTracer.getClass().getDeclaredField("tracingTelemetry");
            tracingTelemetryField.setAccessible(true);
            Object tracingTelemetry = tracingTelemetryField.get(defaultTracer);

            java.lang.reflect.Method getContextPropagatorMethod = tracingTelemetry.getClass().getMethod("getContextPropagator");
            Object propagator = getContextPropagatorMethod.invoke(tracingTelemetry);

            java.lang.reflect.Method extractMethod = propagator.getClass().getMethod("extract", Map.class);
            java.util.Optional<?> spanOpt = (java.util.Optional<?>) extractMethod.invoke(propagator, carrier);
            if (spanOpt.isPresent()) {
                return (Span) spanOpt.get();
            }
        } catch (Exception e) {
            log.warn("Failed to extract span context", e);
        }
        return null;
    }
}
