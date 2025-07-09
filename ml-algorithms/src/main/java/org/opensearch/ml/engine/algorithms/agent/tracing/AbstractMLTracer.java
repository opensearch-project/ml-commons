/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.agent.tracing;

import java.util.Map;

import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.telemetry.tracing.Span;
import org.opensearch.telemetry.tracing.Tracer;

/**
 * Abstract base class for tracing implementations in ML Commons.
 * 
 * This class defines the common interface and shared state for all ML tracing logic,
 * such as starting and ending spans. Concrete subclasses (such as {@link MLAgentTracer})
 * implement tracing for specific ML components or workflows.
 * 
 * The intention is to allow for future extension: additional tracers can be created
 * for other ML features (e.g., connector tracing) by extending this class.
 *
 * Each call to {@link #startSpan(String, Map, Span)} returns a {@link Span} object,
 * which acts as a handle to the started span. The {@link Span} object typically contains
 * a unique identifier (span ID) that can be used for logging and debugging. When ending
 * a span, always pass the same {@link Span} object to {@link #endSpan(Span)}.
 */
public abstract class AbstractMLTracer {
    protected final Tracer tracer;
    protected final MLFeatureEnabledSetting mlFeatureEnabledSetting;

    protected AbstractMLTracer(Tracer tracer, MLFeatureEnabledSetting mlFeatureEnabledSetting) {
        this.tracer = tracer;
        this.mlFeatureEnabledSetting = mlFeatureEnabledSetting;
    }

    public abstract Span startSpan(String name, Map<String, String> attributes, Span parentSpan);

    public abstract void endSpan(Span span);
}
