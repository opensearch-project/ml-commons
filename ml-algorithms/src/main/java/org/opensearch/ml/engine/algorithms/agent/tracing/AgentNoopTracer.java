package org.opensearch.ml.engine.algorithms.agent.tracing;

import java.io.IOException;
import java.util.Collection;
import java.util.Map;

import org.opensearch.telemetry.tracing.ScopedSpan;
import org.opensearch.telemetry.tracing.Span;
import org.opensearch.telemetry.tracing.SpanContext;
import org.opensearch.telemetry.tracing.SpanCreationContext;
import org.opensearch.telemetry.tracing.SpanScope;
import org.opensearch.telemetry.tracing.Tracer;

/**
 * AgentNoopTracer is a no-operation implementation of the Tracer interface for agent tracing.
 * 
 * This class provides a lightweight, zero-overhead implementation that performs no actual
 * tracing operations. It is used when tracing is disabled.
 * 
 * All methods in this implementation return no-op objects or perform no operations, making
 * it safe to use in any context without any performance impact or side effects.
 * 
 * This class is thread-safe and immutable. The singleton instance can be safely shared
 * across multiple threads. All spans created by this tracer are instances of
 * {@link AgentNoopSpan}.
 * 
 * @see Tracer
 * @see AgentNoopSpan
 * @see MLAgentTracer
 */
public class AgentNoopTracer implements Tracer {
    /**
     * Singleton instance of AgentNoopTracer.
     * This instance is shared across all uses to minimize memory footprint.
     */
    public static final AgentNoopTracer INSTANCE = new AgentNoopTracer();

    /**
     * Private constructor to enforce singleton pattern.
     * This prevents external instantiation and ensures only the singleton instance is used.
     */
    private AgentNoopTracer() {}

    /**
     * Starts a new span with the given context. This is a no-op implementation.
     * @param context the span creation context (ignored)
     * @return a singleton instance of AgentNoopSpan
     */
    @Override
    public Span startSpan(SpanCreationContext context) {
        return AgentNoopSpan.NOOP_INSTANCE;
    }

    /**
     * Returns the current span context. This is a no-op implementation.
     * @return a new SpanContext wrapping the AgentNoopSpan singleton
     */
    @Override
    public SpanContext getCurrentSpan() {
        return new SpanContext(AgentNoopSpan.NOOP_INSTANCE);
    }

    /**
     * Starts a scoped span with the given context. This is a no-op implementation.
     * @param spanCreationContext the span creation context (ignored)
     * @return ScopedSpan.NO_OP, which is a no-operation scoped span
     */
    @Override
    public ScopedSpan startScopedSpan(SpanCreationContext spanCreationContext) {
        return ScopedSpan.NO_OP;
    }

    /**
     * Creates a span scope with the given span. This is a no-op implementation.
     * @param span the span to scope (ignored)
     * @return SpanScope.NO_OP, which is a no-operation span scope
     */
    @Override
    public SpanScope withSpanInScope(Span span) {
        return SpanScope.NO_OP;
    }

    /**
     * Checks if the tracer is currently recording spans. This is a no-op implementation.
     * @return false, as this tracer never records spans
     */
    @Override
    public boolean isRecording() {
        return false;
    }

    /**
     * Closes the tracer. This is a no-op implementation.
     * @throws IOException never thrown by this implementation
     */
    @Override
    public void close() throws IOException {
        // no-op
    }

    /**
     * Starts a new span with the given context and headers. This is a no-op implementation.
     * @param spanCreationContext the span creation context (ignored)
     * @param headers the headers to use for span creation (ignored)
     * @return a singleton instance of AgentNoopSpan
     */
    @Override
    public Span startSpan(SpanCreationContext spanCreationContext, Map<String, Collection<String>> headers) {
        return AgentNoopSpan.NOOP_INSTANCE;
    }
}
