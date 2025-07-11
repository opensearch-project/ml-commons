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

    private MLAgentTracer(Tracer tracer, MLFeatureEnabledSetting mlFeatureEnabledSetting) {
        super(tracer, mlFeatureEnabledSetting);
    }

    public static synchronized void initialize(Tracer tracer, MLFeatureEnabledSetting mlFeatureEnabledSetting) {
        initialize(tracer, mlFeatureEnabledSetting, null);
    }

    public static synchronized void initialize(Tracer tracer, MLFeatureEnabledSetting mlFeatureEnabledSetting, ClusterService clusterService) {
        Tracer tracerToUse = (mlFeatureEnabledSetting != null && 
                             mlFeatureEnabledSetting.isTracingEnabled() && 
                             mlFeatureEnabledSetting.isAgentTracingEnabled()) ? tracer : NoopTracer.INSTANCE;
        
        instance = new MLAgentTracer(tracerToUse, mlFeatureEnabledSetting);
        log.info("MLAgentTracer initialized with {}", tracerToUse.getClass().getSimpleName());
        
        if (clusterService != null) {
            clusterService.getClusterSettings().addSettingsUpdateConsumer(MLCommonsSettings.ML_COMMONS_AGENT_TRACING_ENABLED, enabled -> {
                Tracer newTracerToUse = (mlFeatureEnabledSetting != null && 
                                        mlFeatureEnabledSetting.isTracingEnabled() && 
                                        enabled) ? tracer : NoopTracer.INSTANCE;
                instance = new MLAgentTracer(newTracerToUse, mlFeatureEnabledSetting);
                log.info("MLAgentTracer re-initialized with {} due to setting change", newTracerToUse.getClass().getSimpleName());
            });
        }
    }

    public static synchronized MLAgentTracer getInstance() {
        if (instance == null) {
            throw new IllegalStateException("MLAgentTracer is not initialized. Call initialize() first before using getInstance().");
        }
        return instance;
    }

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
        if (name != null && name.startsWith("agent.task") && !(tracer instanceof NoopTracer)) {
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

    @Override
    public void endSpan(Span span) {
        if (span == null) {
            throw new IllegalArgumentException("Span cannot be null");
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
     * Extracts a parent span from a carrier map using the TracingContextPropagator
     * @param carrier The map containing the context
     * @return The extracted parent span, or null if not found
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
