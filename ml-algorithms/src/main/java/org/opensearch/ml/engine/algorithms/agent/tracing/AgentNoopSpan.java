package org.opensearch.ml.engine.algorithms.agent.tracing;

import org.opensearch.telemetry.tracing.Span;

public class AgentNoopSpan implements Span {
    public static final AgentNoopSpan NOOP_INSTANCE = new AgentNoopSpan();

    private AgentNoopSpan() {}

    @Override
    public void endSpan() {}

    @Override
    public Span getParentSpan() {
        return null;
    }

    @Override
    public String getSpanName() {
        return "";
    }

    @Override
    public void addAttribute(String key, String value) {}

    @Override
    public void addAttribute(String key, Long value) {}

    @Override
    public void addAttribute(String key, Double value) {}

    @Override
    public void addAttribute(String key, Boolean value) {}

    @Override
    public void setError(Exception exception) {}

    @Override
    public void addEvent(String event) {}

    @Override
    public String getTraceId() {
        return "";
    }

    @Override
    public String getSpanId() {
        return "";
    }
}
