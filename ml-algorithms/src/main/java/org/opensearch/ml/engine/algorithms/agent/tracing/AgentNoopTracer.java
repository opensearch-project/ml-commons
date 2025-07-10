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

public class AgentNoopTracer implements Tracer {
    public static final AgentNoopTracer INSTANCE = new AgentNoopTracer();

    private AgentNoopTracer() {}

    @Override
    public Span startSpan(SpanCreationContext context) {
        return AgentNoopSpan.NOOP_INSTANCE;
    }

    @Override
    public SpanContext getCurrentSpan() {
        return new SpanContext(AgentNoopSpan.NOOP_INSTANCE);
    }

    @Override
    public ScopedSpan startScopedSpan(SpanCreationContext spanCreationContext) {
        return ScopedSpan.NO_OP;
    }

    @Override
    public SpanScope withSpanInScope(Span span) {
        return SpanScope.NO_OP;
    }

    @Override
    public boolean isRecording() {
        return false;
    }

    @Override
    public void close() throws IOException {
        // no-op
    }

    @Override
    public Span startSpan(SpanCreationContext spanCreationContext, Map<String, Collection<String>> headers) {
        return AgentNoopSpan.NOOP_INSTANCE;
    }
}
