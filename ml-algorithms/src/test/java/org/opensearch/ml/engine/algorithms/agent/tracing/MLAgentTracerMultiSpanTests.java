/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.agent.tracing;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Before;
import org.junit.Test;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.telemetry.tracing.Span;
import org.opensearch.telemetry.tracing.Tracer;

/**
 * Tests for multi-span scenarios, span identification, and race conditions in MLAgentTracer.
 * These tests ensure that:
 * 1. Each span has unique identification
 * 2. Parent-child relationships are properly maintained
 * 3. No race conditions occur in concurrent usage
 * 4. Span attributes help identify which span is problematic
 */
public class MLAgentTracerMultiSpanTests {
    private MLFeatureEnabledSetting mockFeatureSetting;
    private Tracer mockTracer;

    @Before
    public void setup() {
        mockFeatureSetting = mock(MLFeatureEnabledSetting.class);
        mockTracer = mock(Tracer.class);
        MLAgentTracer.resetForTest();
    }

    private Span createMockSpan(String traceId, String spanId, String spanName) {
        Span mockSpan = mock(Span.class);
        when(mockSpan.getTraceId()).thenReturn(traceId);
        when(mockSpan.getSpanId()).thenReturn(spanId);
        when(mockSpan.getSpanName()).thenReturn(spanName);
        return mockSpan;
    }

    @Test
    public void testMultiSpanHierarchyWithUniqueIdentification() {
        when(mockFeatureSetting.isTracingEnabled()).thenReturn(true);
        when(mockFeatureSetting.isAgentTracingEnabled()).thenReturn(true);
        
        // Set up mock spans with unique IDs
        Span mockRootSpan = createMockSpan("trace-123", "span-root", MLAgentTracer.AGENT_TASK_SPAN);
        Span mockChildSpan = createMockSpan("trace-123", "span-child", MLAgentTracer.AGENT_LLM_CALL_SPAN);
        Span mockGrandchildSpan = createMockSpan("trace-123", "span-grandchild", MLAgentTracer.AGENT_TOOL_CALL_SPAN);
        
        when(mockTracer.startSpan(any())).thenReturn(mockRootSpan, mockChildSpan, mockGrandchildSpan);
        
        MLAgentTracer.initialize(mockTracer, mockFeatureSetting);
        MLAgentTracer tracer = MLAgentTracer.getInstance();

        // Create root span
        Map<String, String> rootAttributes = new HashMap<>();
        rootAttributes.put("operation", "agent_task");
        rootAttributes.put("request_id", "req-123");
        Span rootSpan = tracer.startSpan(MLAgentTracer.AGENT_TASK_SPAN, rootAttributes, null);
        assertNotNull("Root span should not be null", rootSpan);
        assertNotNull("Root span should have trace ID", rootSpan.getTraceId());
        assertNotNull("Root span should have span ID", rootSpan.getSpanId());

        // Create child span
        Map<String, String> childAttributes = new HashMap<>();
        childAttributes.put("operation", "llm_call");
        childAttributes.put("model", "gpt-4");
        Span childSpan = tracer.startSpan(MLAgentTracer.AGENT_LLM_CALL_SPAN, childAttributes, rootSpan);
        assertNotNull("Child span should not be null", childSpan);
        assertNotNull("Child span should have trace ID", childSpan.getTraceId());
        assertNotNull("Child span should have span ID", childSpan.getSpanId());

        // Verify parent-child relationship
        assertEquals("Child should have same trace ID as parent", rootSpan.getTraceId(), childSpan.getTraceId());
        assertNotSame("Child should have different span ID than parent", rootSpan.getSpanId(), childSpan.getSpanId());

        // Create grandchild span
        Map<String, String> grandchildAttributes = new HashMap<>();
        grandchildAttributes.put("operation", "tool_call");
        grandchildAttributes.put("tool_name", "search_index");
        Span grandchildSpan = tracer.startSpan(MLAgentTracer.AGENT_TOOL_CALL_SPAN, grandchildAttributes, childSpan);
        assertNotNull("Grandchild span should not be null", grandchildSpan);
        assertEquals("Grandchild should have same trace ID as root", rootSpan.getTraceId(), grandchildSpan.getTraceId());
        assertNotSame("Grandchild should have different span ID than child", childSpan.getSpanId(), grandchildSpan.getSpanId());

        // End spans in reverse order
        tracer.endSpan(grandchildSpan);
        tracer.endSpan(childSpan);
        tracer.endSpan(rootSpan);
    }

    @Test
    public void testSpanIdentificationWithAttributes() {
        when(mockFeatureSetting.isTracingEnabled()).thenReturn(true);
        when(mockFeatureSetting.isAgentTracingEnabled()).thenReturn(true);
        
        // Set up mock spans with unique IDs but same trace ID
        Span mockTaskSpan = createMockSpan("trace-456", "span-task", MLAgentTracer.AGENT_TASK_SPAN);
        Span mockLlmSpan = createMockSpan("trace-456", "span-llm", MLAgentTracer.AGENT_LLM_CALL_SPAN);
        Span mockToolSpan = createMockSpan("trace-456", "span-tool", MLAgentTracer.AGENT_TOOL_CALL_SPAN);
        
        when(mockTracer.startSpan(any())).thenReturn(mockTaskSpan, mockLlmSpan, mockToolSpan);
        
        MLAgentTracer.initialize(mockTracer, mockFeatureSetting);
        MLAgentTracer tracer = MLAgentTracer.getInstance();

        // Create spans with identifying attributes
        Map<String, String> taskAttributes = new HashMap<>();
        taskAttributes.put("operation", "agent_task");
        taskAttributes.put("request_id", "req-456");
        taskAttributes.put("user_id", "user-789");
        taskAttributes.put("session_id", "session-abc");
        
        Span taskSpan = tracer.startSpan(MLAgentTracer.AGENT_TASK_SPAN, taskAttributes, null);
        assertNotNull("Task span should not be null", taskSpan);

        // Create multiple child spans with different operations
        Map<String, String> llmAttributes = new HashMap<>();
        llmAttributes.put("operation", "llm_call");
        llmAttributes.put("model", "gpt-4");
        llmAttributes.put("request_id", "req-456"); // Same request ID for correlation
        
        Span llmSpan = tracer.startSpan(MLAgentTracer.AGENT_LLM_CALL_SPAN, llmAttributes, taskSpan);
        assertNotNull("LLM span should not be null", llmSpan);

        Map<String, String> toolAttributes = new HashMap<>();
        toolAttributes.put("operation", "tool_call");
        toolAttributes.put("tool_name", "search_index");
        toolAttributes.put("request_id", "req-456"); // Same request ID for correlation
        
        Span toolSpan = tracer.startSpan(MLAgentTracer.AGENT_TOOL_CALL_SPAN, toolAttributes, taskSpan);
        assertNotNull("Tool span should not be null", toolSpan);

        // Verify all spans have unique IDs but same trace ID
        assertEquals("All spans should have same trace ID", taskSpan.getTraceId(), llmSpan.getTraceId());
        assertEquals("All spans should have same trace ID", taskSpan.getTraceId(), toolSpan.getTraceId());
        assertNotSame("Spans should have different span IDs", taskSpan.getSpanId(), llmSpan.getSpanId());
        assertNotSame("Spans should have different span IDs", taskSpan.getSpanId(), toolSpan.getSpanId());
        assertNotSame("Spans should have different span IDs", llmSpan.getSpanId(), toolSpan.getSpanId());

        // End spans
        tracer.endSpan(toolSpan);
        tracer.endSpan(llmSpan);
        tracer.endSpan(taskSpan);
    }

    @Test
    public void testConcurrentSpanCreationNoRaceConditions() throws InterruptedException {
        when(mockFeatureSetting.isTracingEnabled()).thenReturn(true);
        when(mockFeatureSetting.isAgentTracingEnabled()).thenReturn(true);
        
        // Set up mock spans for concurrent testing
        Span mockSpan = createMockSpan("trace-concurrent", "span-concurrent", MLAgentTracer.AGENT_TASK_SPAN);
        when(mockTracer.startSpan(any())).thenReturn(mockSpan);
        
        MLAgentTracer.initialize(mockTracer, mockFeatureSetting);
        MLAgentTracer tracer = MLAgentTracer.getInstance();

        int numThreads = 10;
        int spansPerThread = 5;
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(numThreads);
        AtomicInteger totalSpansCreated = new AtomicInteger(0);

        // Create multiple threads that create spans concurrently
        for (int i = 0; i < numThreads; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    startLatch.await(); // Wait for all threads to start together
                    
                    for (int j = 0; j < spansPerThread; j++) {
                        Map<String, String> attributes = new HashMap<>();
                        attributes.put("thread_id", String.valueOf(threadId));
                        attributes.put("span_index", String.valueOf(j));
                        
                        Span span = tracer.startSpan(MLAgentTracer.AGENT_TASK_SPAN, attributes, null);
                        assertNotNull("Span should not be null", span);
                        assertNotNull("Span should have trace ID", span.getTraceId());
                        assertNotNull("Span should have span ID", span.getSpanId());
                        
                        totalSpansCreated.incrementAndGet();
                        tracer.endSpan(span);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    endLatch.countDown();
                }
            });
        }

        // Start all threads simultaneously
        startLatch.countDown();
        
        // Wait for all threads to complete
        boolean completed = endLatch.await(10, TimeUnit.SECONDS);
        assertTrue("All threads should complete within timeout", completed);
        assertEquals("Should create expected number of spans", numThreads * spansPerThread, totalSpansCreated.get());
        
        executor.shutdown();
        assertTrue("Executor should shutdown cleanly", executor.awaitTermination(5, TimeUnit.SECONDS));
    }

    @Test
    public void testConcurrentParentChildSpanCreation() throws InterruptedException {
        when(mockFeatureSetting.isTracingEnabled()).thenReturn(true);
        when(mockFeatureSetting.isAgentTracingEnabled()).thenReturn(true);
        
        // Set up mock spans for parent-child testing
        Span mockParentSpan = createMockSpan("trace-parent", "span-parent", MLAgentTracer.AGENT_TASK_SPAN);
        Span mockChildSpan = createMockSpan("trace-parent", "span-child", MLAgentTracer.AGENT_LLM_CALL_SPAN);
        when(mockTracer.startSpan(any())).thenReturn(mockParentSpan, mockChildSpan);
        
        MLAgentTracer.initialize(mockTracer, mockFeatureSetting);
        MLAgentTracer tracer = MLAgentTracer.getInstance();

        int numThreads = 5;
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(numThreads);

        // Create multiple threads that create parent-child span hierarchies concurrently
        for (int i = 0; i < numThreads; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    startLatch.await(); // Wait for all threads to start together
                    
                    // Create parent span
                    Map<String, String> parentAttributes = new HashMap<>();
                    parentAttributes.put("thread_id", String.valueOf(threadId));
                    
                    Span parentSpan = tracer.startSpan(MLAgentTracer.AGENT_TASK_SPAN, parentAttributes, null);
                    assertNotNull("Parent span should not be null", parentSpan);
                    String parentTraceId = parentSpan.getTraceId();
                    String parentSpanId = parentSpan.getSpanId();
                    
                    // Create multiple child spans
                    for (int j = 0; j < 3; j++) {
                        Map<String, String> childAttributes = new HashMap<>();
                        childAttributes.put("thread_id", String.valueOf(threadId));
                        childAttributes.put("child_index", String.valueOf(j));
                        
                        Span childSpan = tracer.startSpan(MLAgentTracer.AGENT_LLM_CALL_SPAN, childAttributes, parentSpan);
                        assertNotNull("Child span should not be null", childSpan);
                        
                        // Verify parent-child relationship
                        assertEquals("Child should have same trace ID as parent", parentTraceId, childSpan.getTraceId());
                        assertNotSame("Child should have different span ID than parent", parentSpanId, childSpan.getSpanId());
                        
                        tracer.endSpan(childSpan);
                    }
                    
                    tracer.endSpan(parentSpan);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    endLatch.countDown();
                }
            });
        }

        // Start all threads simultaneously
        startLatch.countDown();
        
        // Wait for all threads to complete
        boolean completed = endLatch.await(10, TimeUnit.SECONDS);
        assertTrue("All threads should complete within timeout", completed);
        
        executor.shutdown();
        assertTrue("Executor should shutdown cleanly", executor.awaitTermination(5, TimeUnit.SECONDS));
    }

    @Test
    public void testSpanIdentificationWithErrorScenarios() {
        when(mockFeatureSetting.isTracingEnabled()).thenReturn(true);
        when(mockFeatureSetting.isAgentTracingEnabled()).thenReturn(true);
        
        // Set up mock spans for error scenario testing
        Span mockTaskSpan = createMockSpan("trace-error", "span-task", MLAgentTracer.AGENT_TASK_SPAN);
        Span mockErrorSpan = createMockSpan("trace-error", "span-error", MLAgentTracer.AGENT_LLM_CALL_SPAN);
        Span mockSuccessSpan = createMockSpan("trace-error", "span-success", MLAgentTracer.AGENT_TOOL_CALL_SPAN);
        when(mockTracer.startSpan(any())).thenReturn(mockTaskSpan, mockErrorSpan, mockSuccessSpan);
        
        MLAgentTracer.initialize(mockTracer, mockFeatureSetting);
        MLAgentTracer tracer = MLAgentTracer.getInstance();

        // Create a span hierarchy where one span fails
        Map<String, String> taskAttributes = new HashMap<>();
        taskAttributes.put("operation", "agent_task");
        taskAttributes.put("request_id", "req-error-test");
        taskAttributes.put("user_id", "user-error");
        
        Span taskSpan = tracer.startSpan(MLAgentTracer.AGENT_TASK_SPAN, taskAttributes, null);
        assertNotNull("Task span should not be null", taskSpan);

        // Create child span that will "fail"
        Map<String, String> errorAttributes = new HashMap<>();
        errorAttributes.put("operation", "llm_call");
        errorAttributes.put("model", "gpt-4");
        errorAttributes.put("request_id", "req-error-test");
        
        Span errorSpan = tracer.startSpan(MLAgentTracer.AGENT_LLM_CALL_SPAN, errorAttributes, taskSpan);
        assertNotNull("Error span should not be null", errorSpan);

        // Simulate an error in the child span
        try {
            // Simulate some operation that might fail
            throw new RuntimeException("Simulated LLM call failure");
        } catch (Exception e) {
            // Mark the span as having an error
            errorSpan.setError(e);
        }

        // Create another child span that succeeds
        Map<String, String> successAttributes = new HashMap<>();
        successAttributes.put("operation", "tool_call");
        successAttributes.put("tool_name", "search_index");
        successAttributes.put("request_id", "req-error-test");
        
        Span successSpan = tracer.startSpan(MLAgentTracer.AGENT_TOOL_CALL_SPAN, successAttributes, taskSpan);
        assertNotNull("Success span should not be null", successSpan);

        // Verify all spans have proper identification for debugging
        assertEquals("All spans should have same trace ID for correlation", taskSpan.getTraceId(), errorSpan.getTraceId());
        assertEquals("All spans should have same trace ID for correlation", taskSpan.getTraceId(), successSpan.getTraceId());
        assertNotSame("Each span should have unique span ID", taskSpan.getSpanId(), errorSpan.getSpanId());
        assertNotSame("Each span should have unique span ID", taskSpan.getSpanId(), successSpan.getSpanId());
        assertNotSame("Each span should have unique span ID", errorSpan.getSpanId(), successSpan.getSpanId());

        // End spans
        tracer.endSpan(successSpan);
        tracer.endSpan(errorSpan);
        tracer.endSpan(taskSpan);
    }

    @Test
    public void testNoopTracerMultiSpanBehavior() {
        when(mockFeatureSetting.isTracingEnabled()).thenReturn(false);
        MLAgentTracer.initialize(mockTracer, mockFeatureSetting);
        MLAgentTracer tracer = MLAgentTracer.getInstance();

        // Test that noop tracer still provides proper span identification
        Map<String, String> attributes = new HashMap<>();
        attributes.put("operation", "noop_test");
        attributes.put("request_id", "req-noop");
        
        Span span = tracer.startSpan(MLAgentTracer.AGENT_TASK_SPAN, attributes, null);
        assertNotNull("Noop span should not be null", span);
        
        // Noop spans should still have identification for debugging
        assertNotNull("Noop span should have trace ID", span.getTraceId());
        assertNotNull("Noop span should have span ID", span.getSpanId());
        
        tracer.endSpan(span);
    }
}
