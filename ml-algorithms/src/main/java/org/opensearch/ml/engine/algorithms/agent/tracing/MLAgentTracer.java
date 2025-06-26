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

import org.opensearch.cluster.service.ClusterService;
import org.opensearch.ml.common.settings.MLCommonsSettings;
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
    public static final String AGENT_TASK_PER_SPAN = "agent.task_per";
    public static final String AGENT_TASK_CONV_SPAN = "agent.task_conv";
    public static final String AGENT_TASK_CONV_FLOW_SPAN = "agent.task_convflow";
    public static final String AGENT_TASK_FLOW_SPAN = "agent.task_flow";

    private static MLAgentTracer instance;

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
     * This is a convenience method that calls the full initialize method with a null ClusterService.
     * 
     * @param tracer The tracer implementation to use. If null or if tracing is disabled,
     *               a NoopTracer will be used instead.
     * @param mlFeatureEnabledSetting The ML feature settings that control tracing behavior.
     *                                If null, tracing will be disabled.
     */
    public static synchronized void initialize(Tracer tracer, MLFeatureEnabledSetting mlFeatureEnabledSetting) {
        initialize(tracer, mlFeatureEnabledSetting, null);
    }

    /**
     * Initializes the singleton MLAgentTracer instance with the given tracer and settings.
     * If agent tracing is disabled, a NoopTracer is used.
     * @param tracer The tracer implementation to use. If null or if tracing is disabled,
     *               a NoopTracer will be used instead.
     * @param mlFeatureEnabledSetting The ML feature settings that control tracing behavior.
     *                                If null, tracing will be disabled.
     */
    public static synchronized void initialize(
        Tracer tracer,
        MLFeatureEnabledSetting mlFeatureEnabledSetting,
        ClusterService clusterService
    ) {
        Tracer tracerToUse = (mlFeatureEnabledSetting != null
            && mlFeatureEnabledSetting.isTracingEnabled()
            && mlFeatureEnabledSetting.isAgentTracingEnabled()
            && tracer != null) ? tracer : NoopTracer.INSTANCE;

        instance = new MLAgentTracer(tracerToUse, mlFeatureEnabledSetting);
        log.info("MLAgentTracer initialized with {}", tracerToUse.getClass().getSimpleName());

        if (clusterService != null) {
            clusterService.getClusterSettings().addSettingsUpdateConsumer(MLCommonsSettings.ML_COMMONS_AGENT_TRACING_ENABLED, enabled -> {
                Tracer newTracerToUse = (mlFeatureEnabledSetting != null
                    && mlFeatureEnabledSetting.isTracingEnabled()
                    && enabled
                    && tracer != null) ? tracer : NoopTracer.INSTANCE;
                instance = new MLAgentTracer(newTracerToUse, mlFeatureEnabledSetting);
                log.info("MLAgentTracer re-initialized with {} due to setting change", newTracerToUse.getClass().getSimpleName());
            });
        }
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
        Span newSpan;
        if (name != null && name.startsWith(AGENT_TASK_SPAN) && !(tracer instanceof NoopTracer)) {
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
                log.warn("Failed to create root span for agent.task*, falling back to normal span creation", e);
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
     * Starts a new span for agent tracing with the specified name and attributes, and no parent span.
     * <p>
     * This is a convenience overload for starting a root span. It is equivalent to calling
     * {@link #startSpan(String, Map, Span)} with {@code parentSpan} set to {@code null}.
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
    public Span startSpan(String name, Map<String, String> attributes) {
        return startSpan(name, attributes, null);
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
     * Resets the singleton instance for testing purposes.
     */
    @VisibleForTesting
    static void resetForTest() {
        instance = null;
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
