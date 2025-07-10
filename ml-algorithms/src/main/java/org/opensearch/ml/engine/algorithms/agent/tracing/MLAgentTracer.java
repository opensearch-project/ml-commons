/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.agent.tracing;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;

import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.repackage.com.google.common.annotations.VisibleForTesting;
import org.opensearch.telemetry.tracing.Span;
import org.opensearch.telemetry.tracing.SpanContext;
import org.opensearch.telemetry.tracing.SpanCreationContext;
import org.opensearch.telemetry.tracing.Tracer;
import org.opensearch.telemetry.tracing.attributes.Attributes;
import org.opensearch.telemetry.tracing.noop.NoopTracer;

import lombok.extern.log4j.Log4j2;

/**
 * MLAgentTracer is a concrete implementation of AbstractMLTracer for agent tracing in ML Commons.
 * It manages the lifecycle of agent-related spans, including creation, context propagation, and completion.
 * 
 * This class is implemented as a singleton to ensure that only one tracer is active
 * for agent tracing at any time. This design provides consistent management of tracing state and configuration,
 * and avoids issues with multiple tracers being active at once.
 * The singleton can be dynamically enabled or disabled based on cluster settings.
 * 
 * This class is thread-safe: multiple threads can use the singleton instance to start and end spans concurrently.
 * Each call to {@link #startSpan(String, Map, Span)} creates a new, independent span.
 */
@Log4j2
public class MLAgentTracer extends AbstractMLTracer {
    public static final String AGENT_TASK_SPAN = "agent.task";
    public static final String AGENT_CONV_TASK_SPAN = "agent.conv_task";
    public static final String AGENT_LLM_CALL_SPAN = "agent.llm_call";
    public static final String AGENT_TOOL_CALL_SPAN = "agent.tool_call";
    public static final String AGENT_PLAN_SPAN = "agent.plan";
    public static final String AGENT_EXECUTE_STEP_SPAN = "agent.execute_step";
    public static final String AGENT_REFLECT_STEP_SPAN = "agent.reflect_step";

    private static MLAgentTracer instance;
    private static boolean tracingFlagSet = false;

    /**
     * Private constructor for MLAgentTracer.
     * @param tracer The tracer implementation to use (may be a real tracer or NoopTracer).
     * @param mlFeatureEnabledSetting The ML feature settings.
     */
    private MLAgentTracer(Tracer tracer, MLFeatureEnabledSetting mlFeatureEnabledSetting) {
        super(tracer, mlFeatureEnabledSetting);
    }

    /**
     * Initializes the singleton MLAgentTracer instance with the given tracer and settings.
     * If agent tracing is disabled, a NoopTracer is used.
     * @param tracer The tracer implementation to use.
     * @param mlFeatureEnabledSetting The ML feature settings.
     */
    public static synchronized void initialize(Tracer tracer, MLFeatureEnabledSetting mlFeatureEnabledSetting) {
        if (mlFeatureEnabledSetting == null || !mlFeatureEnabledSetting.isTracingEnabled()) {
            instance = new MLAgentTracer(AgentNoopTracer.INSTANCE, mlFeatureEnabledSetting);
            log.info("MLAgentTracer not initialized: agent tracing feature flag is disabled. Using AgentNoopTracer for all spans.");
            return;
        }
        Tracer tracerToUse;
        if (mlFeatureEnabledSetting.isAgentTracingEnabled() && tracer != null) {
            tracerToUse = tracer;
        } else {
            tracerToUse = NoopTracer.INSTANCE;
        }
        instance = new MLAgentTracer(tracerToUse, mlFeatureEnabledSetting);
    }

    /**
     * Returns the singleton MLAgentTracer instance.
     * @return The MLAgentTracer instance.
     * @throws IllegalStateException if the tracer is not initialized.
     */
    public static synchronized MLAgentTracer getInstance() {
        if (instance == null) {
            throw new IllegalStateException("MLAgentTracer is not initialized. Call initialize() first before using getInstance().");
        }
        return instance;
    }

    /**
     * Starts a new span for agent tracing.
     * @param name The name of the span.
     * @param attributes Attributes to associate with the span.
     * @param parentSpan The parent span, or null if this is a root span.
     * @return The started Span object, or null if tracing is disabled.
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
        Span newSpan;
        if (AGENT_TASK_SPAN.equals(name) && !(tracer instanceof AgentNoopTracer) && !(tracer instanceof NoopTracer)) {
            // Force agent.task spans to be root span
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

    /**
     * Ends the given span.
     * @param span The span to end. If null or tracing is disabled, this is a no-op.
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
     * @return The tracer instance.
     */
    public Tracer getTracer() {
        return tracer;
    }

    /**
     * Resets the singleton instance for testing purposes.
     */
    @VisibleForTesting
    static void resetForTest() {
        instance = null;
    }

    /**
     * Injects the span context into a carrier map using the TracingContextPropagator.
     * @param span The span whose context to inject.
     * @param carrier The map to inject context into.
     */
    public void injectSpanContext(Span span, Map<String, String> carrier) {
        if (tracer instanceof AgentNoopTracer || tracer instanceof NoopTracer) {
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
     * @param carrier The map containing the context.
     * @return The extracted parent span, or null if not found or tracing is disabled.
     */
    public Span extractSpanContext(Map<String, String> carrier) {
        if (tracer instanceof AgentNoopTracer || tracer instanceof NoopTracer) {
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
