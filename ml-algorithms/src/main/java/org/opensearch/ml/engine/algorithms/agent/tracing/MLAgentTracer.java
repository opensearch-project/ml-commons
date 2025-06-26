/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.agent.tracing;

import java.util.Map;

import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.telemetry.tracing.Span;
import org.opensearch.telemetry.tracing.SpanContext;
import org.opensearch.telemetry.tracing.SpanCreationContext;
import org.opensearch.telemetry.tracing.Tracer;
import org.opensearch.telemetry.tracing.attributes.Attributes;
import org.opensearch.telemetry.tracing.noop.NoopTracer;
import org.opensearch.ml.repackage.com.google.common.annotations.VisibleForTesting;

import lombok.extern.log4j.Log4j2;

@Log4j2
public class MLAgentTracer extends AbstractMLTracer {
    private static MLAgentTracer instance;

    private MLAgentTracer(Tracer tracer, MLFeatureEnabledSetting mlFeatureEnabledSetting) {
        super(tracer, mlFeatureEnabledSetting);
    }

    public static synchronized void initialize(Tracer tracer, MLFeatureEnabledSetting mlFeatureEnabledSetting) {
        if (mlFeatureEnabledSetting == null || !mlFeatureEnabledSetting.isAgentTracingFeatureEnabled()) {
            instance = null;
            return;
        }
        Tracer tracerToUse = mlFeatureEnabledSetting.isAgentTracingEnabled() ? tracer : NoopTracer.INSTANCE;
        instance = new MLAgentTracer(tracerToUse, mlFeatureEnabledSetting);
    }

    public static synchronized MLAgentTracer getInstance() {
        if (instance == null) {
            throw new IllegalStateException("MLAgentTracer is not initialized. Call initialize() first or enable plugins.ml_commons.agent_tracing_feature_enabled setting.");
        }
        return instance;
    }

    @Override
    public Span startSpan(String name, Map<String, String> attributes, Span parentSpan) {
        if (tracer == null) {
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
}
