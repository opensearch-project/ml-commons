package org.opensearch.ml.engine.algorithms.agent.tracing;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.util.HashMap;
import java.util.Map;

import org.junit.Test;
import org.opensearch.ml.common.output.model.ModelTensor;
import org.opensearch.ml.common.output.model.ModelTensorOutput;
import org.opensearch.ml.common.output.model.ModelTensors;
import org.opensearch.telemetry.tracing.Span;

public class MLAgentTracerStaticUtilsTests {

    @Test
    public void testCreateAgentTaskAttributes() {
        Map<String, String> attrs = MLAgentTracer.createAgentTaskAttributes("agent1", "task1");
        assertEquals("agent1", attrs.get(MLAgentTracer.ATTR_NAME));
        assertEquals("task1", attrs.get(MLAgentTracer.ATTR_TASK));
        assertEquals("create_agent", attrs.get(MLAgentTracer.ATTR_OPERATION_NAME));
    }

    @Test
    public void testCreatePlanAttributes() {
        Map<String, String> attrs = MLAgentTracer.createPlanAttributes(5);
        assertEquals("planner", attrs.get(MLAgentTracer.ATTR_PHASE));
        assertEquals("5", attrs.get(MLAgentTracer.ATTR_STEP_NUMBER));
    }

    @Test
    public void testCreateExecuteStepAttributes() {
        Map<String, String> attrs = MLAgentTracer.createExecuteStepAttributes(2);
        assertEquals("executor", attrs.get(MLAgentTracer.ATTR_PHASE));
        assertEquals("2", attrs.get(MLAgentTracer.ATTR_STEP_NUMBER));
    }

    @Test
    public void testDetectProviderFromParameters() {
        Map<String, String> params = new HashMap<>();
        params.put("_llm_interface", "openai/v1/chat/completions");
        assertEquals("openai", MLAgentTracer.detectProviderFromParameters(params));
        params.put("_llm_interface", "bedrock/converse/claude");
        assertEquals("aws.bedrock", MLAgentTracer.detectProviderFromParameters(params));
        params.put("_llm_interface", "unknown");
        assertEquals("unknown", MLAgentTracer.detectProviderFromParameters(params));
    }

    @Test
    public void testExtractToolCallInfoWithNull() {
        MLAgentTracer.ToolCallExtractionResult result = MLAgentTracer.extractToolCallInfo(null, "input");
        assertEquals("input", result.input);
        assertNull(result.output);
    }

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

    @Test
    public void testDetectProviderFromParametersAllBranches() {
        Map<String, String> params = new HashMap<>();
        params.put("_llm_interface", "claude");
        assertEquals("anthropic", MLAgentTracer.detectProviderFromParameters(params));
        params.put("_llm_interface", "anthropic");
        assertEquals("anthropic", MLAgentTracer.detectProviderFromParameters(params));
        params.put("_llm_interface", "gemini");
        assertEquals("gcp.gemini", MLAgentTracer.detectProviderFromParameters(params));
        params.put("_llm_interface", "google");
        assertEquals("gcp.gemini", MLAgentTracer.detectProviderFromParameters(params));
        params.put("_llm_interface", "llama");
        assertEquals("meta", MLAgentTracer.detectProviderFromParameters(params));
        params.put("_llm_interface", "meta");
        assertEquals("meta", MLAgentTracer.detectProviderFromParameters(params));
        params.put("_llm_interface", "cohere");
        assertEquals("cohere", MLAgentTracer.detectProviderFromParameters(params));
        params.put("_llm_interface", "deepseek");
        assertEquals("deepseek", MLAgentTracer.detectProviderFromParameters(params));
        params.put("_llm_interface", "groq");
        assertEquals("groq", MLAgentTracer.detectProviderFromParameters(params));
        params.put("_llm_interface", "mistral");
        assertEquals("mistral_ai", MLAgentTracer.detectProviderFromParameters(params));
        params.put("_llm_interface", "perplexity");
        assertEquals("perplexity", MLAgentTracer.detectProviderFromParameters(params));
        params.put("_llm_interface", "xai");
        assertEquals("xai", MLAgentTracer.detectProviderFromParameters(params));
        params.put("_llm_interface", "azure");
        assertEquals("az.ai.inference", MLAgentTracer.detectProviderFromParameters(params));
        params.put("_llm_interface", "az.ai");
        assertEquals("az.ai.inference", MLAgentTracer.detectProviderFromParameters(params));
        params.put("_llm_interface", "ibm");
        assertEquals("ibm.watsonx.ai", MLAgentTracer.detectProviderFromParameters(params));
        params.put("_llm_interface", "watson");
        assertEquals("ibm.watsonx.ai", MLAgentTracer.detectProviderFromParameters(params));
    }

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

    @Test
    public void testUpdateSpanWithResultAttributesNullSpan() {
        // Should not throw
        MLAgentTracer.updateSpanWithResultAttributes(null, "result", 1.0, 2.0, 3.0, 4.0);
    }
}
