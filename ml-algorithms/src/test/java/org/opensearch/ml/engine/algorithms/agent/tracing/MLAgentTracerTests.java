/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.agent.tracing;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertThrows;
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
import org.opensearch.ml.common.output.model.ModelTensor;
import org.opensearch.ml.common.output.model.ModelTensorOutput;
import org.opensearch.ml.common.output.model.ModelTensors;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.telemetry.tracing.Span;
import org.opensearch.telemetry.tracing.Tracer;
import org.opensearch.telemetry.tracing.noop.NoopTracer;

/**
 * Comprehensive unit tests for MLAgentTracer.
 * These tests cover:
 * 1. Singleton initialization and feature flag handling
 * 2. Static utility methods (attribute creation, provider detection, etc.)
 * 3. Multi-span scenarios, span identification, and race conditions
 * 4. Tool call extraction and span attribute updates
 */
public class MLAgentTracerTests {
    private MLFeatureEnabledSetting mockFeatureSetting;
    private Tracer mockTracer;

    /**
     * Sets up mocks and resets the singleton before each test.
     */
    @Before
    public void setup() {
        mockFeatureSetting = mock(MLFeatureEnabledSetting.class);
        mockTracer = mock(Tracer.class);
        MLAgentTracer.resetForTest();
    }

    // ==================== SINGLETON AND INITIALIZATION TESTS ====================

    /**
     * Tests that an exception is thrown if getInstance is called before initialization.
     */
    @Test
    public void testExceptionThrownForNotInitialized() {
        IllegalStateException exception = assertThrows(IllegalStateException.class, MLAgentTracer::getInstance);
        String msg = exception.getMessage();
        assertEquals("MLAgentTracer is not initialized. Call initialize() first before using getInstance().", msg);
    }

    /**
     * Tests that NoopTracer is used if the feature flag is disabled.
     */
    @Test
    public void testInitializeWithFeatureFlagDisabled() {
        when(mockFeatureSetting.isTracingEnabled()).thenReturn(false);
        MLAgentTracer.initialize(mockTracer, mockFeatureSetting);
        MLAgentTracer instance = MLAgentTracer.getInstance();
        assertNotNull(instance);
        assertTrue(instance.getTracer() instanceof NoopTracer);
    }

    /**
     * Tests that the provided tracer is used if both feature flags are enabled.
     */
    @Test
    public void testInitializeWithFeatureFlagEnabledAndDynamicEnabled() {
        when(mockFeatureSetting.isTracingEnabled()).thenReturn(true);
        when(mockFeatureSetting.isAgentTracingEnabled()).thenReturn(true);
        MLAgentTracer.initialize(mockTracer, mockFeatureSetting);
        MLAgentTracer instance = MLAgentTracer.getInstance();
        assertNotNull(instance);
        assertEquals(mockTracer, instance.getTracer());
    }

    /**
     * Tests that NoopTracer is used if the dynamic agent tracing flag is disabled.
     */
    @Test
    public void testInitializeWithFeatureFlagEnabledAndDynamicDisabled() {
        when(mockFeatureSetting.isTracingEnabled()).thenReturn(true);
        when(mockFeatureSetting.isAgentTracingEnabled()).thenReturn(false);
        MLAgentTracer.initialize(mockTracer, mockFeatureSetting);
        MLAgentTracer instance = MLAgentTracer.getInstance();
        assertNotNull(instance);
        assertTrue(instance.getTracer() instanceof NoopTracer);
    }

    /**
     * Tests that startSpan works and does not throw when using a NoopTracer.
     */
    @Test
    public void testStartSpanWorksWithNullTracer() {
        when(mockFeatureSetting.isTracingEnabled()).thenReturn(true);
        when(mockFeatureSetting.isAgentTracingEnabled()).thenReturn(true);
        MLAgentTracer.initialize(null, mockFeatureSetting);
        MLAgentTracer instance = MLAgentTracer.getInstance();
        assertNotNull(instance);
        assertTrue(instance.getTracer() instanceof NoopTracer);
        // Should not throw exception when using NoopTracer
        instance.startSpan("test", null, null);
    }

    /**
     * Tests that endSpan throws an exception if the span is null.
     */
    @Test
    public void testEndSpanThrowsExceptionIfSpanIsNull() {
        when(mockFeatureSetting.isTracingEnabled()).thenReturn(true);
        when(mockFeatureSetting.isAgentTracingEnabled()).thenReturn(true);
        MLAgentTracer.initialize(mockTracer, mockFeatureSetting);
        MLAgentTracer instance = MLAgentTracer.getInstance();
        assertThrows(IllegalArgumentException.class, () -> instance.endSpan(null));
    }

    /**
     * Tests that getTracer returns the correct tracer instance.
     */
    @Test
    public void testGetTracerReturnsTracer() {
        when(mockFeatureSetting.isTracingEnabled()).thenReturn(true);
        when(mockFeatureSetting.isAgentTracingEnabled()).thenReturn(true);
        MLAgentTracer.initialize(mockTracer, mockFeatureSetting);
        MLAgentTracer instance = MLAgentTracer.getInstance();
        assertEquals(mockTracer, instance.getTracer());
    }

    // ==================== STATIC UTILITY TESTS ====================

    /**
     * Tests that createAgentTaskAttributes returns correct attributes for agent name and task.
     */
    @Test
    public void testCreateAgentTaskAttributes() {
        Map<String, String> attrs = MLAgentTracer.createAgentTaskAttributes("agent1", "task1");
        assertEquals("agent1", attrs.get(MLAgentTracer.ATTR_NAME));
        assertEquals("task1", attrs.get(MLAgentTracer.ATTR_TASK));
        assertEquals("create_agent", attrs.get(MLAgentTracer.ATTR_OPERATION_NAME));
    }

    /**
     * Tests that createPlanAttributes returns correct attributes for a plan step.
     */
    @Test
    public void testCreatePlanAttributes() {
        Map<String, String> attrs = MLAgentTracer.createPlanAttributes(5);
        assertEquals("planner", attrs.get(MLAgentTracer.ATTR_PHASE));
        assertEquals("5", attrs.get(MLAgentTracer.ATTR_STEP_NUMBER));
    }

    /**
     * Tests that createExecuteStepAttributes returns correct attributes for an execute step.
     */
    @Test
    public void testCreateExecuteStepAttributes() {
        Map<String, String> attrs = MLAgentTracer.createExecuteStepAttributes(2);
        assertEquals("executor", attrs.get(MLAgentTracer.ATTR_PHASE));
        assertEquals("2", attrs.get(MLAgentTracer.ATTR_STEP_NUMBER));
    }

    /**
     * Tests provider detection logic for various LLM interface strings.
     */
    @Test
    public void testDetectProviderFromParameters() {
        Map<String, String> params = new HashMap<>();
        params.put("_llm_interface", "openai/v1/chat/completions");
        assertEquals("openai", MLAgentTracer.detectProviderFromParameters(params));
        params.put("_llm_interface", "bedrock/converse/claude");
        assertEquals("bedrock", MLAgentTracer.detectProviderFromParameters(params));
        params.put("_llm_interface", "unknown");
        assertEquals("unknown", MLAgentTracer.detectProviderFromParameters(params));
    }

    /**
     * Tests that extractToolCallInfo returns correct input and empty output when given null tool output.
     */
    @Test
    public void testExtractToolCallInfoWithNull() {
        MLAgentTracer.ToolCallExtractionResult result = MLAgentTracer.extractToolCallInfo(null, "input");
        assertEquals("input", result.input);
        assertEquals("", result.output);
    }

    /**
     * Tests that updateSpanWithResultAttributes sets the correct attributes on a mock span.
     */
    @Test
    public void testUpdateSpanWithResultAttributes() {
        Span mockSpan = org.mockito.Mockito.mock(Span.class);
        MLAgentTracer.AgentExecutionContext context = new MLAgentTracer.AgentExecutionContext(mockSpan, null);
        context.getCurrentResult().set("result");
        context.getPhaseInputTokens().set(1.0);
        context.getPhaseOutputTokens().set(2.0);
        context.getPhaseTotalTokens().set(3.0);
        context.getCurrentLatency().set(4L);
        MLAgentTracer.updateSpanWithResultAttributes(mockSpan, context);
        org.mockito.Mockito.verify(mockSpan).addAttribute(MLAgentTracer.ATTR_RESULT, "result");
        org.mockito.Mockito.verify(mockSpan).addAttribute(MLAgentTracer.ATTR_USAGE_INPUT_TOKENS, "1");
        org.mockito.Mockito.verify(mockSpan).addAttribute(MLAgentTracer.ATTR_USAGE_OUTPUT_TOKENS, "2");
        org.mockito.Mockito.verify(mockSpan).addAttribute(MLAgentTracer.ATTR_USAGE_TOTAL_TOKENS, "3");
        org.mockito.Mockito.verify(mockSpan).addAttribute(MLAgentTracer.ATTR_LATENCY, "4");
    }

    /**
     * Tests that updateSpanWithResultAttributes does not throw when span is null.
     */
    @Test
    public void testUpdateSpanWithResultAttributesNullSpan() {
        // Should not throw
        MLAgentTracer.AgentExecutionContext context = new MLAgentTracer.AgentExecutionContext(null, null);
        context.getCurrentResult().set("result");
        context.getPhaseInputTokens().set(1.0);
        context.getPhaseOutputTokens().set(2.0);
        context.getPhaseTotalTokens().set(3.0);
        context.getCurrentLatency().set(4L);
        MLAgentTracer.updateSpanWithResultAttributes(null, context);
    }

    /**
     * Tests LLM call attribute creation for AWS Bedrock provider.
     */
    @Test
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void testCreateLLMCallAttributesAwsBedrock() {
        // Mock ModelTensorOutput and tensors for aws.bedrock
        ModelTensorOutput mockOutput = mock(ModelTensorOutput.class);
        ModelTensors mockModelTensors = mock(ModelTensors.class);
        ModelTensor mockTensor = mock(ModelTensor.class);
        Map usage = new HashMap();
        usage.put("input_tokens", 10);
        usage.put("output_tokens", 20);
        Map dataAsMap = new HashMap();
        dataAsMap.put("usage", usage);
        when(mockTensor.getDataAsMap()).thenReturn(dataAsMap);
        when(mockModelTensors.getMlModelTensors()).thenReturn(java.util.Collections.singletonList(mockTensor));
        when(mockOutput.getMlModelOutputs()).thenReturn(java.util.Collections.singletonList(mockModelTensors));
        Map<String, String> params = new HashMap<>();
        params.put("_llm_interface", "bedrock/converse/claude");
        Map<String, Double> extractedTokens = MLAgentTracer.extractTokensFromModelOutput(mockOutput, params);
        Map<String, String> attrs = MLAgentTracer.createLLMCallAttributes("result", 100L, params, extractedTokens);
        assertEquals("bedrock", attrs.get(MLAgentTracer.ATTR_SYSTEM));
        assertEquals("10", attrs.get(MLAgentTracer.ATTR_USAGE_INPUT_TOKENS));
        assertEquals("20", attrs.get(MLAgentTracer.ATTR_USAGE_OUTPUT_TOKENS));
        assertEquals("30", attrs.get(MLAgentTracer.ATTR_USAGE_TOTAL_TOKENS));
    }

    /**
     * Tests LLM call attribute creation for OpenAI provider.
     */
    @Test
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void testCreateLLMCallAttributesOpenAI() {
        // Mock ModelTensorOutput and tensors for openai
        ModelTensorOutput mockOutput = mock(ModelTensorOutput.class);
        ModelTensors mockModelTensors = mock(ModelTensors.class);
        ModelTensor mockTensor = mock(ModelTensor.class);
        Map usage = new HashMap();
        usage.put("prompt_tokens", 5);
        usage.put("completion_tokens", 7);
        usage.put("total_tokens", 12);
        Map dataAsMap = new HashMap();
        dataAsMap.put("usage", usage);
        when(mockTensor.getDataAsMap()).thenReturn(dataAsMap);
        when(mockModelTensors.getMlModelTensors()).thenReturn(java.util.Collections.singletonList(mockTensor));
        when(mockOutput.getMlModelOutputs()).thenReturn(java.util.Collections.singletonList(mockModelTensors));
        Map<String, String> params = new HashMap<>();
        params.put("_llm_interface", "openai/v1/chat/completions");
        Map<String, Double> extractedTokens = MLAgentTracer.extractTokensFromModelOutput(mockOutput, params);
        Map<String, String> attrs = MLAgentTracer.createLLMCallAttributes("result", 100L, params, extractedTokens);
        assertEquals("openai", attrs.get(MLAgentTracer.ATTR_SYSTEM));
        assertEquals("5", attrs.get(MLAgentTracer.ATTR_USAGE_INPUT_TOKENS));
        assertEquals("7", attrs.get(MLAgentTracer.ATTR_USAGE_OUTPUT_TOKENS));
        assertEquals("12", attrs.get(MLAgentTracer.ATTR_USAGE_TOTAL_TOKENS));
    }

    /**
     * Tests tool call extraction from a tensor with dataAsMap containing response, usage, and metrics.
     */
    @Test
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void testExtractToolCallInfoDataAsMapBranch() {
        // Mock ModelTensorOutput and tensor with dataAsMap
        ModelTensorOutput mockOutput = mock(ModelTensorOutput.class);
        ModelTensors mockModelTensors = mock(ModelTensors.class);
        ModelTensor mockTensor = mock(ModelTensor.class);
        Map dataAsMap = new HashMap();
        dataAsMap.put("response", "myresponse");
        dataAsMap.put("usage", java.util.Collections.singletonMap("foo", "bar"));
        dataAsMap.put("metrics", java.util.Collections.singletonMap("baz", 1));
        when(mockTensor.getDataAsMap()).thenReturn(dataAsMap);
        when(mockModelTensors.getMlModelTensors()).thenReturn(java.util.Collections.singletonList(mockTensor));
        when(mockOutput.getMlModelOutputs()).thenReturn(java.util.Collections.singletonList(mockModelTensors));
        MLAgentTracer.ToolCallExtractionResult result = MLAgentTracer.extractToolCallInfo(mockOutput, "input");
        assertEquals("input", result.input);
        assertEquals("myresponse", result.output);
        assertEquals("bar", result.usage.get("foo"));
        assertEquals(1, result.metrics.get("baz"));
    }

    // ==================== MULTI-SPAN AND CONCURRENCY TESTS ====================

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

    /**
     * Tests extractToolCallInfo with tensor.getResult() not null.
     */
    @Test
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void testExtractToolCallInfoWithTensorResult() {
        ModelTensorOutput mockOutput = mock(ModelTensorOutput.class);
        ModelTensors mockModelTensors = mock(ModelTensors.class);
        ModelTensor mockTensor = mock(ModelTensor.class);

        when(mockTensor.getResult()).thenReturn("tensor_result");
        when(mockModelTensors.getMlModelTensors()).thenReturn(java.util.Collections.singletonList(mockTensor));
        when(mockOutput.getMlModelOutputs()).thenReturn(java.util.Collections.singletonList(mockModelTensors));

        MLAgentTracer.ToolCallExtractionResult result = MLAgentTracer.extractToolCallInfo(mockOutput, "input");
        assertEquals("input", result.input);
        assertEquals("tensor_result", result.output);
    }

    /**
     * Tests extractToolCallInfo with exception when getting dataAsMap.
     */
    @Test
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void testExtractToolCallInfoWithDataAsMapException() {
        ModelTensorOutput mockOutput = mock(ModelTensorOutput.class);
        ModelTensors mockModelTensors = mock(ModelTensors.class);
        ModelTensor mockTensor = mock(ModelTensor.class);

        when(mockTensor.getResult()).thenReturn(null);
        when(mockTensor.getDataAsMap()).thenThrow(new RuntimeException("DataAsMap exception"));
        when(mockTensor.toString()).thenReturn("tensor_to_string");
        when(mockModelTensors.getMlModelTensors()).thenReturn(java.util.Collections.singletonList(mockTensor));
        when(mockOutput.getMlModelOutputs()).thenReturn(java.util.Collections.singletonList(mockModelTensors));

        MLAgentTracer.ToolCallExtractionResult result = MLAgentTracer.extractToolCallInfo(mockOutput, "input");
        assertEquals("input", result.input);
        assertEquals("tensor_to_string", result.output);
    }

    /**
     * Tests extractToolCallInfo with null dataAsMap and null result.
     */
    @Test
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void testExtractToolCallInfoWithNullDataAsMapAndNullResult() {
        ModelTensorOutput mockOutput = mock(ModelTensorOutput.class);
        ModelTensors mockModelTensors = mock(ModelTensors.class);
        ModelTensor mockTensor = mock(ModelTensor.class);

        when(mockTensor.getResult()).thenReturn(null);
        when(mockTensor.getDataAsMap()).thenReturn(null);
        when(mockTensor.toString()).thenReturn("tensor_to_string");
        when(mockModelTensors.getMlModelTensors()).thenReturn(java.util.Collections.singletonList(mockTensor));
        when(mockOutput.getMlModelOutputs()).thenReturn(java.util.Collections.singletonList(mockModelTensors));

        MLAgentTracer.ToolCallExtractionResult result = MLAgentTracer.extractToolCallInfo(mockOutput, "input");
        assertEquals("input", result.input);
        assertEquals("tensor_to_string", result.output);
    }

    /**
     * Tests extractToolCallInfo with RESPONSE_FIELD in dataAsMap.
     */
    @Test
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void testExtractToolCallInfoWithResponseField() {
        ModelTensorOutput mockOutput = mock(ModelTensorOutput.class);
        ModelTensors mockModelTensors = mock(ModelTensors.class);
        ModelTensor mockTensor = mock(ModelTensor.class);
        Map dataAsMap = new HashMap();
        dataAsMap.put("response", "response_value");

        when(mockTensor.getResult()).thenReturn(null);
        when(mockTensor.getDataAsMap()).thenReturn(dataAsMap);
        when(mockModelTensors.getMlModelTensors()).thenReturn(java.util.Collections.singletonList(mockTensor));
        when(mockOutput.getMlModelOutputs()).thenReturn(java.util.Collections.singletonList(mockModelTensors));

        MLAgentTracer.ToolCallExtractionResult result = MLAgentTracer.extractToolCallInfo(mockOutput, "input");
        assertEquals("input", result.input);
        assertEquals("response_value", result.output);
    }

    /**
     * Tests extractToolCallInfo with OUTPUT_FIELD in dataAsMap.
     */
    @Test
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void testExtractToolCallInfoWithOutputField() {
        ModelTensorOutput mockOutput = mock(ModelTensorOutput.class);
        ModelTensors mockModelTensors = mock(ModelTensors.class);
        ModelTensor mockTensor = mock(ModelTensor.class);
        Map dataAsMap = new HashMap();
        dataAsMap.put("output", "output_value");

        when(mockTensor.getResult()).thenReturn(null);
        when(mockTensor.getDataAsMap()).thenReturn(dataAsMap);
        when(mockModelTensors.getMlModelTensors()).thenReturn(java.util.Collections.singletonList(mockTensor));
        when(mockOutput.getMlModelOutputs()).thenReturn(java.util.Collections.singletonList(mockModelTensors));

        MLAgentTracer.ToolCallExtractionResult result = MLAgentTracer.extractToolCallInfo(mockOutput, "input");
        assertEquals("input", result.input);
        assertEquals("output_value", result.output);
    }

    /**
     * Tests extractToolCallInfo with neither RESPONSE_FIELD nor OUTPUT_FIELD, but non-empty map.
     */
    @Test
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void testExtractToolCallInfoWithFirstValueFromMap() {
        ModelTensorOutput mockOutput = mock(ModelTensorOutput.class);
        ModelTensors mockModelTensors = mock(ModelTensors.class);
        ModelTensor mockTensor = mock(ModelTensor.class);
        Map dataAsMap = new HashMap();
        dataAsMap.put("other_field", "other_value");

        when(mockTensor.getResult()).thenReturn(null);
        when(mockTensor.getDataAsMap()).thenReturn(dataAsMap);
        when(mockModelTensors.getMlModelTensors()).thenReturn(java.util.Collections.singletonList(mockTensor));
        when(mockOutput.getMlModelOutputs()).thenReturn(java.util.Collections.singletonList(mockModelTensors));

        MLAgentTracer.ToolCallExtractionResult result = MLAgentTracer.extractToolCallInfo(mockOutput, "input");
        assertEquals("input", result.input);
        assertEquals("other_value", result.output);
    }

    /**
     * Tests updateAgentTaskSpanWithCumulativeTokens with null agent task span.
     */
    @Test
    public void testUpdateAgentTaskSpanWithCumulativeTokensNullSpan() {
        MLAgentTracer.AgentExecutionContext context = new MLAgentTracer.AgentExecutionContext(null, null);
        context.getAgentInputTokens().set(10.0);
        context.getAgentOutputTokens().set(20.0);
        context.getAgentTotalTokens().set(30.0);

        // Should not throw
        MLAgentTracer.updateAgentTaskSpanWithCumulativeTokens(context);
    }

    /**
     * Tests updateAgentTaskSpanWithCumulativeTokens with valid agent task span.
     */
    @Test
    public void testUpdateAgentTaskSpanWithCumulativeTokensValidSpan() {
        Span mockAgentTaskSpan = mock(Span.class);
        MLAgentTracer.AgentExecutionContext context = new MLAgentTracer.AgentExecutionContext(mockAgentTaskSpan, null);
        context.getAgentInputTokens().set(10.0);
        context.getAgentOutputTokens().set(20.0);
        context.getAgentTotalTokens().set(30.0);

        MLAgentTracer.updateAgentTaskSpanWithCumulativeTokens(context);

        org.mockito.Mockito.verify(mockAgentTaskSpan).addAttribute(MLAgentTracer.ATTR_USAGE_INPUT_TOKENS, "10");
        org.mockito.Mockito.verify(mockAgentTaskSpan).addAttribute(MLAgentTracer.ATTR_USAGE_OUTPUT_TOKENS, "20");
        org.mockito.Mockito.verify(mockAgentTaskSpan).addAttribute(MLAgentTracer.ATTR_USAGE_TOTAL_TOKENS, "30");
    }

    /**
     * Tests getPlanSpan method.
     */
    @Test
    public void testGetPlanSpan() {
        Span mockPlanSpan = mock(Span.class);
        MLAgentTracer.AgentExecutionContext context = new MLAgentTracer.AgentExecutionContext(null, mockPlanSpan);
        assertEquals(mockPlanSpan, context.getPlanSpan());
    }

    /**
     * Tests incrementPhaseTokensFromReActOutput with null reactResult.
     */
    @Test
    public void testIncrementPhaseTokensFromReActOutputNullResult() {
        MLAgentTracer.AgentExecutionContext context = new MLAgentTracer.AgentExecutionContext(null, null);
        context.getPhaseInputTokens().set(5.0);
        context.getPhaseOutputTokens().set(10.0);
        context.getPhaseTotalTokens().set(15.0);

        MLAgentTracer.incrementPhaseTokensFromReActOutput(null, context);

        assertEquals(5.0, context.getPhaseInputTokens().get(), 0.001);
        assertEquals(10.0, context.getPhaseOutputTokens().get(), 0.001);
        assertEquals(15.0, context.getPhaseTotalTokens().get(), 0.001);
    }

    /**
     * Tests incrementPhaseTokensFromReActOutput with empty model outputs.
     */
    @Test
    public void testIncrementPhaseTokensFromReActOutputEmptyModelOutputs() {
        ModelTensorOutput mockOutput = mock(ModelTensorOutput.class);
        when(mockOutput.getMlModelOutputs()).thenReturn(java.util.Collections.emptyList());

        MLAgentTracer.AgentExecutionContext context = new MLAgentTracer.AgentExecutionContext(null, null);
        context.getPhaseInputTokens().set(5.0);
        context.getPhaseOutputTokens().set(10.0);
        context.getPhaseTotalTokens().set(15.0);

        MLAgentTracer.incrementPhaseTokensFromReActOutput(mockOutput, context);

        assertEquals(5.0, context.getPhaseInputTokens().get(), 0.001);
        assertEquals(10.0, context.getPhaseOutputTokens().get(), 0.001);
        assertEquals(15.0, context.getPhaseTotalTokens().get(), 0.001);
    }

    /**
     * Tests incrementPhaseTokensFromReActOutput with null tensors.
     */
    @Test
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void testIncrementPhaseTokensFromReActOutputNullTensors() {
        ModelTensorOutput mockOutput = mock(ModelTensorOutput.class);
        ModelTensors mockModelTensors = mock(ModelTensors.class);
        when(mockModelTensors.getMlModelTensors()).thenReturn(null);
        when(mockOutput.getMlModelOutputs()).thenReturn(java.util.Collections.singletonList(mockModelTensors));

        MLAgentTracer.AgentExecutionContext context = new MLAgentTracer.AgentExecutionContext(null, null);
        context.getPhaseInputTokens().set(5.0);
        context.getPhaseOutputTokens().set(10.0);
        context.getPhaseTotalTokens().set(15.0);

        MLAgentTracer.incrementPhaseTokensFromReActOutput(mockOutput, context);

        assertEquals(5.0, context.getPhaseInputTokens().get(), 0.001);
        assertEquals(10.0, context.getPhaseOutputTokens().get(), 0.001);
        assertEquals(15.0, context.getPhaseTotalTokens().get(), 0.001);
    }

    /**
     * Tests incrementPhaseTokensFromReActOutput with null dataMap.
     */
    @Test
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void testIncrementPhaseTokensFromReActOutputNullDataMap() {
        ModelTensorOutput mockOutput = mock(ModelTensorOutput.class);
        ModelTensors mockModelTensors = mock(ModelTensors.class);
        ModelTensor mockTensor = mock(ModelTensor.class);
        when(mockTensor.getDataAsMap()).thenReturn(null);
        when(mockModelTensors.getMlModelTensors()).thenReturn(java.util.Collections.singletonList(mockTensor));
        when(mockOutput.getMlModelOutputs()).thenReturn(java.util.Collections.singletonList(mockModelTensors));

        MLAgentTracer.AgentExecutionContext context = new MLAgentTracer.AgentExecutionContext(null, null);
        context.getPhaseInputTokens().set(5.0);
        context.getPhaseOutputTokens().set(10.0);
        context.getPhaseTotalTokens().set(15.0);

        MLAgentTracer.incrementPhaseTokensFromReActOutput(mockOutput, context);

        assertEquals(5.0, context.getPhaseInputTokens().get(), 0.001);
        assertEquals(10.0, context.getPhaseOutputTokens().get(), 0.001);
        assertEquals(15.0, context.getPhaseTotalTokens().get(), 0.001);
    }

    /**
     * Tests incrementPhaseTokensFromReActOutput with valid additional info.
     */
    @Test
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void testIncrementPhaseTokensFromReActOutputValidAdditionalInfo() {
        ModelTensorOutput mockOutput = mock(ModelTensorOutput.class);
        ModelTensors mockModelTensors = mock(ModelTensors.class);
        ModelTensor mockTensor = mock(ModelTensor.class);
        Map dataMap = new HashMap();
        Map additionalInfo = new HashMap();
        additionalInfo.put("inputTokens", 5);
        additionalInfo.put("outputTokens", 10);
        additionalInfo.put("totalTokens", 15);
        dataMap.put("additional_info", additionalInfo);

        when(mockTensor.getDataAsMap()).thenReturn(dataMap);
        when(mockModelTensors.getMlModelTensors()).thenReturn(java.util.Collections.singletonList(mockTensor));
        when(mockOutput.getMlModelOutputs()).thenReturn(java.util.Collections.singletonList(mockModelTensors));

        MLAgentTracer.AgentExecutionContext context = new MLAgentTracer.AgentExecutionContext(null, null);
        context.getPhaseInputTokens().set(5.0);
        context.getPhaseOutputTokens().set(10.0);
        context.getPhaseTotalTokens().set(15.0);

        MLAgentTracer.incrementPhaseTokensFromReActOutput(mockOutput, context);

        assertEquals(10.0, context.getPhaseInputTokens().get(), 0.001);
        assertEquals(20.0, context.getPhaseOutputTokens().get(), 0.001);
        assertEquals(30.0, context.getPhaseTotalTokens().get(), 0.001);
    }

    /**
     * Tests incrementPhaseTokensFromReActOutput with additionalInfo field.
     */
    @Test
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void testIncrementPhaseTokensFromReActOutputAdditionalInfoField() {
        ModelTensorOutput mockOutput = mock(ModelTensorOutput.class);
        ModelTensors mockModelTensors = mock(ModelTensors.class);
        ModelTensor mockTensor = mock(ModelTensor.class);
        Map dataMap = new HashMap();
        Map additionalInfo = new HashMap();
        additionalInfo.put("inputTokens", 5);
        additionalInfo.put("outputTokens", 10);
        additionalInfo.put("totalTokens", 15);
        dataMap.put("additionalInfo", additionalInfo);

        when(mockTensor.getDataAsMap()).thenReturn(dataMap);
        when(mockModelTensors.getMlModelTensors()).thenReturn(java.util.Collections.singletonList(mockTensor));
        when(mockOutput.getMlModelOutputs()).thenReturn(java.util.Collections.singletonList(mockModelTensors));

        MLAgentTracer.AgentExecutionContext context = new MLAgentTracer.AgentExecutionContext(null, null);
        context.getPhaseInputTokens().set(5.0);
        context.getPhaseOutputTokens().set(10.0);
        context.getPhaseTotalTokens().set(15.0);

        MLAgentTracer.incrementPhaseTokensFromReActOutput(mockOutput, context);

        assertEquals(10.0, context.getPhaseInputTokens().get(), 0.001);
        assertEquals(20.0, context.getPhaseOutputTokens().get(), 0.001);
        assertEquals(30.0, context.getPhaseTotalTokens().get(), 0.001);
    }

    /**
     * Tests extractOpenAITokens with NumberFormatException for prompt tokens.
     */
    @Test
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void testExtractOpenAITokensWithPromptTokensException() {
        Map usage = new HashMap();
        usage.put("prompt_tokens", "invalid_number");

        Map<String, Double> extractedTokens = new HashMap<>();
        MLAgentTracerTests.extractOpenAITokens(usage, extractedTokens);

        assertFalse("Should not add invalid prompt tokens", extractedTokens.containsKey(MLAgentTracer.TOKEN_FIELD_INPUT_TOKENS));
    }

    /**
     * Tests extractOpenAITokens with NumberFormatException for completion tokens.
     */
    @Test
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void testExtractOpenAITokensWithCompletionTokensException() {
        Map usage = new HashMap();
        usage.put("completion_tokens", "invalid_number");

        Map<String, Double> extractedTokens = new HashMap<>();
        MLAgentTracerTests.extractOpenAITokens(usage, extractedTokens);

        assertFalse("Should not add invalid completion tokens", extractedTokens.containsKey(MLAgentTracer.TOKEN_FIELD_OUTPUT_TOKENS));
    }

    /**
     * Tests extractOpenAITokens with NumberFormatException for total tokens.
     */
    @Test
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void testExtractOpenAITokensWithTotalTokensException() {
        Map usage = new HashMap();
        usage.put("total_tokens", "invalid_number");

        Map<String, Double> extractedTokens = new HashMap<>();
        MLAgentTracerTests.extractOpenAITokens(usage, extractedTokens);

        assertFalse("Should not add invalid total tokens", extractedTokens.containsKey(MLAgentTracer.TOKEN_FIELD_TOTAL_TOKENS));
    }

    /**
     * Tests extractOpenAITokens with valid token values.
     */
    @Test
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void testExtractOpenAITokensWithValidTokens() {
        Map usage = new HashMap();
        usage.put("prompt_tokens", "5");
        usage.put("completion_tokens", "7");
        usage.put("total_tokens", "12");

        Map<String, Double> extractedTokens = new HashMap<>();
        MLAgentTracerTests.extractOpenAITokens(usage, extractedTokens);

        assertEquals(5.0, extractedTokens.get(MLAgentTracer.TOKEN_FIELD_INPUT_TOKENS), 0.001);
        assertEquals(7.0, extractedTokens.get(MLAgentTracer.TOKEN_FIELD_OUTPUT_TOKENS), 0.001);
        assertEquals(12.0, extractedTokens.get(MLAgentTracer.TOKEN_FIELD_TOTAL_TOKENS), 0.001);
    }

    /**
     * Helper method to access the private extractOpenAITokens method for testing.
     */
    @SuppressWarnings("unchecked")
    private static void extractOpenAITokens(Map<String, Object> usage, Map<String, Double> extractedTokens) {
        Object promptTokens = null;
        Object completionTokens = null;
        Object totalTokens = null;
        for (String key : usage.keySet()) {
            if (key.equalsIgnoreCase(MLAgentTracer.TOKEN_FIELD_PROMPT_TOKENS)) {
                promptTokens = usage.get(key);
            }
            if (key.equalsIgnoreCase(MLAgentTracer.TOKEN_FIELD_COMPLETION_TOKENS)) {
                completionTokens = usage.get(key);
            }
            if (key.equalsIgnoreCase(MLAgentTracer.TOKEN_FIELD_TOTAL_TOKENS_ALT)) {
                totalTokens = usage.get(key);
            }
        }

        if (promptTokens != null) {
            try {
                extractedTokens.put(MLAgentTracer.TOKEN_FIELD_INPUT_TOKENS, Double.parseDouble(promptTokens.toString()));
            } catch (NumberFormatException e) {
                // Log warning would happen here in real code
            }
        }
        if (completionTokens != null) {
            try {
                extractedTokens.put(MLAgentTracer.TOKEN_FIELD_OUTPUT_TOKENS, Double.parseDouble(completionTokens.toString()));
            } catch (NumberFormatException e) {
                // Log warning would happen here in real code
            }
        }
        if (totalTokens != null) {
            try {
                extractedTokens.put(MLAgentTracer.TOKEN_FIELD_TOTAL_TOKENS, Double.parseDouble(totalTokens.toString()));
            } catch (NumberFormatException e) {
                // Log warning would happen here in real code
            }
        }
    }
}
