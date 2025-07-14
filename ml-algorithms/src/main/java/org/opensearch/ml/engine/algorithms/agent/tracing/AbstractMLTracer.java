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

    /**
     * Constructs a new AbstractMLTracer with the specified tracer and feature settings.
     * 
     * @param tracer The underlying tracer implementation to use for span operations.
     *               This may be a real tracer or a no-op tracer depending on configuration.
     * @param mlFeatureEnabledSetting The ML feature settings that control tracing behavior.
     *                                Used to determine if tracing is enabled and which features
     *                                should be traced.
     */
    protected AbstractMLTracer(Tracer tracer, MLFeatureEnabledSetting mlFeatureEnabledSetting) {
        this.tracer = tracer;
        this.mlFeatureEnabledSetting = mlFeatureEnabledSetting;
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
    public abstract Span startSpan(String name, Map<String, String> attributes);

    /**
     * Starts a new span for tracing ML operations.
     * 
     * This method creates a new span with the specified name and attributes. The span
     * can be either a root span (when parentSpan is null) or a child span of the
     * specified parent span. The returned Span object should be passed to
     * {@link #endSpan(Span)} when the operation completes.
     * 
     * @param name The name of the span.
     * @param attributes A map of key-value pairs to associate with the span. These attributes
     *                  provide additional context about the operation being traced. May be null
     *                  or empty if no attributes are needed.
     * @param parentSpan The parent span, or null if this should be a root span. Child spans
     *                  are nested under their parent spans in the trace hierarchy.
     * @return A Span object representing the started span, or null if tracing is disabled.
     *         The returned span should be passed to {@link #endSpan(Span)} when the
     *         operation completes.
     */
    public abstract Span startSpan(String name, Map<String, String> attributes, Span parentSpan);

    /**
     * Ends a previously started span.
     * 
     * This method marks the completion of a span and finalizes its timing information.
     * The span will be recorded in the trace with its start time, end time, and any
     * attributes that were set during its lifetime.
     * 
     * @param span The span to end. This should be the same Span object that was returned
     *             by a previous call to {@link #startSpan(String, Map, Span)}. If null,
     *             this method is a no-op.
     */
    public abstract void endSpan(Span span);
}
