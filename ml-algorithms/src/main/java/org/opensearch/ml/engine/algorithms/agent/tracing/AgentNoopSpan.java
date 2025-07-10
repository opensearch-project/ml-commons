package org.opensearch.ml.engine.algorithms.agent.tracing;

import org.opensearch.telemetry.tracing.Span;

/**
 * AgentNoopSpan is a no-operation implementation of the Span interface for agent tracing.
 * 
 * This class provides a lightweight, zero-overhead implementation that performs no actual
 * tracing operations. It is used when tracing is disabled.
 * 
 * All methods in this implementation are no-ops, making it safe to use in any context
 * without any performance impact or side effects.
 * 
 * This class is thread-safe and immutable. The singleton instance can be safely shared
 * across multiple threads.
 * 
 * @see Span
 * @see AgentNoopTracer
 */
public class AgentNoopSpan implements Span {
    /**
     * Singleton instance of AgentNoopSpan.
     * This instance is shared across all uses to minimize memory footprint.
     */
    public static final AgentNoopSpan NOOP_INSTANCE = new AgentNoopSpan();

    /**
     * Private constructor to enforce singleton pattern.
     * This prevents external instantiation and ensures only the singleton instance is used.
     */
    private AgentNoopSpan() {}

    /**
     * Ends the span. This is a no-op implementation.
     * No actual tracing operations are performed.
     */
    @Override
    public void endSpan() {}

    /**
     * Returns the parent span. This is a no-op implementation.
     * @return null, as this is a no-op span with no parent
     */
    @Override
    public Span getParentSpan() {
        return null;
    }

    /**
     * Returns the span name. This is a no-op implementation.
     * @return no-op span
     */
    @Override
    public String getSpanName() {
        return "noop-span";
    }

    /**
     * Adds a string attribute to the span. This is a no-op implementation.
     * @param key the attribute key (ignored)
     * @param value the attribute value (ignored)
     */
    @Override
    public void addAttribute(String key, String value) {}

    /**
     * Adds a long attribute to the span. This is a no-op implementation.
     * @param key the attribute key (ignored)
     * @param value the attribute value (ignored)
     */
    @Override
    public void addAttribute(String key, Long value) {}

    /**
     * Adds a double attribute to the span. This is a no-op implementation.
     * @param key the attribute key (ignored)
     * @param value the attribute value (ignored)
     */
    @Override
    public void addAttribute(String key, Double value) {}

    /**
     * Adds a boolean attribute to the span. This is a no-op implementation.
     * @param key the attribute key (ignored)
     * @param value the attribute value (ignored)
     */
    @Override
    public void addAttribute(String key, Boolean value) {}

    /**
     * Sets an error on the span. This is a no-op implementation.
     * @param exception the exception to set (ignored)
     */
    @Override
    public void setError(Exception exception) {}

    /**
     * Adds an event to the span. This is a no-op implementation.
     * @param event the event to add (ignored)
     */
    @Override
    public void addEvent(String event) {}

    /**
     * Returns the trace ID. This is a no-op implementation.
     * @return no-op trace id
     */
    @Override
    public String getTraceId() {
        return "noop-trace-id";
    }

    /**
     * Returns the span ID. This is a no-op implementation.
     * @return no-op span id
     */
    @Override
    public String getSpanId() {
        return "noop-span-id";
    }
}
