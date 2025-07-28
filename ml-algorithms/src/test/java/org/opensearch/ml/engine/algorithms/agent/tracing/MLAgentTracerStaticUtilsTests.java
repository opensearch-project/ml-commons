package org.opensearch.ml.engine.algorithms.agent.tracing;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;

import org.junit.Test;
import org.opensearch.ml.common.output.model.ModelTensor;
import org.opensearch.ml.common.output.model.ModelTensorOutput;
import org.opensearch.ml.common.output.model.ModelTensors;
import org.opensearch.telemetry.tracing.Span;

/**
 * Unit tests for static utility methods in MLAgentTracer.
 * These tests cover attribute creation, provider detection, tool call extraction, and span attribute updates.
 */
public class MLAgentTracerStaticUtilsTests {

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
        assertEquals("openai", MLAgentTracer.detectProviderFromParameters("openai/v1/chat/completions"));
        assertEquals("aws.bedrock", MLAgentTracer.detectProviderFromParameters("bedrock/converse/claude"));
        assertEquals("unknown", MLAgentTracer.detectProviderFromParameters("unknown"));
    }

    /**
     * Tests that extractToolCallInfo returns correct input and null output when given null tool output.
     */
    @Test
    public void testExtractToolCallInfoWithNull() {
        MLAgentTracer.ToolCallExtractionResult result = MLAgentTracer.extractToolCallInfo(null, "input");
        assertEquals("input", result.input);
        assertNull(result.output);
    }

    /**
     * Tests that updateSpanWithResultAttributes sets the correct attributes on a mock span.
     */
    @Test
    public void testUpdateSpanWithResultAttributes() {
        Span mockSpan = org.mockito.Mockito.mock(Span.class);
        MLAgentTracer.updateSpanWithResultAttributes(mockSpan, "result", 1.0, 2.0, 3.0, 4.0);
        org.mockito.Mockito.verify(mockSpan).addAttribute(MLAgentTracer.ATTR_RESULT, "result");
        org.mockito.Mockito.verify(mockSpan).addAttribute(MLAgentTracer.ATTR_USAGE_INPUT_TOKENS, "1");
        org.mockito.Mockito.verify(mockSpan).addAttribute(MLAgentTracer.ATTR_USAGE_OUTPUT_TOKENS, "2");
        org.mockito.Mockito.verify(mockSpan).addAttribute(MLAgentTracer.ATTR_USAGE_TOTAL_TOKENS, "3");
        org.mockito.Mockito.verify(mockSpan).addAttribute(MLAgentTracer.ATTR_LATENCY, "4");
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
        Map<String, String> attrs = MLAgentTracer.createLLMCallAttributes("result", 100L, mockOutput, params);
        assertEquals("aws.bedrock", attrs.get(MLAgentTracer.ATTR_SYSTEM));
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
        Map<String, String> attrs = MLAgentTracer.createLLMCallAttributes("result", 100L, mockOutput, params);
        assertEquals("openai", attrs.get(MLAgentTracer.ATTR_SYSTEM));
        assertEquals("5", attrs.get(MLAgentTracer.ATTR_USAGE_INPUT_TOKENS));
        assertEquals("7", attrs.get(MLAgentTracer.ATTR_USAGE_OUTPUT_TOKENS));
        assertEquals("12", attrs.get(MLAgentTracer.ATTR_USAGE_TOTAL_TOKENS));
    }

    /**
     * Tests provider detection for all supported LLM interface branches.
     */
    @Test
    public void testDetectProviderFromParametersAllBranches() {
        assertEquals("anthropic", MLAgentTracer.detectProviderFromParameters("claude"));
        assertEquals("anthropic", MLAgentTracer.detectProviderFromParameters("anthropic"));
        assertEquals("gcp.gemini", MLAgentTracer.detectProviderFromParameters("gemini"));
        assertEquals("gcp.gemini", MLAgentTracer.detectProviderFromParameters("google"));
        assertEquals("meta", MLAgentTracer.detectProviderFromParameters("llama"));
        assertEquals("meta", MLAgentTracer.detectProviderFromParameters("meta"));
        assertEquals("cohere", MLAgentTracer.detectProviderFromParameters("cohere"));
        assertEquals("deepseek", MLAgentTracer.detectProviderFromParameters("deepseek"));
        assertEquals("groq", MLAgentTracer.detectProviderFromParameters("groq"));
        assertEquals("mistral_ai", MLAgentTracer.detectProviderFromParameters("mistral"));
        assertEquals("perplexity", MLAgentTracer.detectProviderFromParameters("perplexity"));
        assertEquals("xai", MLAgentTracer.detectProviderFromParameters("xai"));
        assertEquals("az.ai.inference", MLAgentTracer.detectProviderFromParameters("azure"));
        assertEquals("az.ai.inference", MLAgentTracer.detectProviderFromParameters("az.ai"));
        assertEquals("ibm.watsonx.ai", MLAgentTracer.detectProviderFromParameters("ibm"));
        assertEquals("ibm.watsonx.ai", MLAgentTracer.detectProviderFromParameters("watson"));
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

    /**
     * Tests that updateSpanWithResultAttributes does not throw when span is null.
     */
    @Test
    public void testUpdateSpanWithResultAttributesNullSpan() {
        // Should not throw
        MLAgentTracer.updateSpanWithResultAttributes(null, "result", 1.0, 2.0, 3.0, 4.0);
    }
}
