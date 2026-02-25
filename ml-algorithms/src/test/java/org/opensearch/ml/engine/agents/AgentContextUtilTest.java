package org.opensearch.ml.engine.agents;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;
import org.opensearch.ml.common.agent.LLMSpec;
import org.opensearch.ml.common.agent.MLAgent;
import org.opensearch.ml.common.agent.MLAgentModelSpec;
import org.opensearch.ml.common.agent.MLToolSpec;
import org.opensearch.ml.common.contextmanager.ContextManagerContext;
import org.opensearch.ml.common.conversation.Interaction;
import org.opensearch.ml.common.hooks.HookRegistry;
import org.opensearch.ml.common.input.execute.agent.Message;
import org.opensearch.ml.common.memory.Memory;

public class AgentContextUtilTest {

    @Test
    public void testEmitPreLLMHookWithNullHookRegistry() {
        Map<String, String> parameters = new HashMap<>();
        parameters.put("question", "test question");

        List<String> interactions = new ArrayList<>();
        List<MLToolSpec> toolSpecs = new ArrayList<>();
        Memory memory = mock(Memory.class);

        ContextManagerContext result = AgentContextUtil.emitPreLLMHook(parameters, interactions, toolSpecs, memory, null);

        assertNotNull(result);
        assertEquals("test question", result.getUserPrompt());
    }

    @Test
    public void testEmitPreLLMHookWithValidHookRegistry() {
        Map<String, String> parameters = new HashMap<>();
        parameters.put("question", "test question");

        List<String> interactions = new ArrayList<>();
        List<MLToolSpec> toolSpecs = new ArrayList<>();
        Memory memory = mock(Memory.class);
        HookRegistry hookRegistry = mock(HookRegistry.class);

        ContextManagerContext result = AgentContextUtil.emitPreLLMHook(parameters, interactions, toolSpecs, memory, hookRegistry);

        assertNotNull(result);
        assertEquals("test question", result.getUserPrompt());
        verify(hookRegistry, times(1)).emit(any());
    }

    @Test
    public void testEmitPostMemoryHookWithNullHookRegistry() {
        Map<String, String> parameters = new HashMap<>();
        parameters.put("question", "test question");

        List<Interaction> retrievedHistory = new ArrayList<>();
        retrievedHistory.add(Interaction.builder().id("1").input("q1").response("r1").build());
        List<MLToolSpec> toolSpecs = new ArrayList<>();
        Memory memory = mock(Memory.class);

        ContextManagerContext result = AgentContextUtil.emitPostMemoryHook(parameters, retrievedHistory, toolSpecs, null);

        assertNotNull(result);
        assertEquals("test question", result.getUserPrompt());
        assertEquals(1, result.getChatHistory().size());
        assertEquals("q1", result.getChatHistory().get(0).getInput());
    }

    @Test
    public void testEmitPostMemoryHookWithValidHookRegistry() {
        Map<String, String> parameters = new HashMap<>();
        parameters.put("question", "test question");

        List<Interaction> retrievedHistory = new ArrayList<>();
        retrievedHistory.add(Interaction.builder().id("1").input("q1").response("r1").build());
        retrievedHistory.add(Interaction.builder().id("2").input("q2").response("r2").build());
        List<MLToolSpec> toolSpecs = new ArrayList<>();
        Memory memory = mock(Memory.class);
        HookRegistry hookRegistry = mock(HookRegistry.class);

        ContextManagerContext result = AgentContextUtil.emitPostMemoryHook(parameters, retrievedHistory, toolSpecs, hookRegistry);

        assertNotNull(result);
        assertEquals("test question", result.getUserPrompt());
        assertEquals(2, result.getChatHistory().size());
        verify(hookRegistry, times(1)).emit(any());
    }

    @Test
    public void testEmitPostMemoryHookWithEmptyHistory() {
        Map<String, String> parameters = new HashMap<>();
        Memory memory = mock(Memory.class);
        HookRegistry hookRegistry = mock(HookRegistry.class);

        ContextManagerContext result = AgentContextUtil.emitPostMemoryHook(parameters, new ArrayList<>(), null, hookRegistry);

        assertNotNull(result);
        assertTrue(result.getChatHistory().isEmpty());
        verify(hookRegistry, times(1)).emit(any());
    }

    @Test
    public void testEmitPostMemoryHookWithNullHistory() {
        Map<String, String> parameters = new HashMap<>();
        Memory memory = mock(Memory.class);
        HookRegistry hookRegistry = mock(HookRegistry.class);

        ContextManagerContext result = AgentContextUtil.emitPostMemoryHook(parameters, null, null, hookRegistry);

        assertNotNull(result);
        assertTrue(result.getChatHistory().isEmpty());
        verify(hookRegistry, times(1)).emit(any());
    }

    @Test
    public void testEmitPostMemoryHookWithException() {
        Map<String, String> parameters = new HashMap<>();
        List<Interaction> retrievedHistory = new ArrayList<>();
        retrievedHistory.add(Interaction.builder().id("1").input("q1").response("r1").build());
        Memory memory = mock(Memory.class);
        HookRegistry hookRegistry = mock(HookRegistry.class);
        doThrow(new RuntimeException("hook error")).when(hookRegistry).emit(any());

        ContextManagerContext result = AgentContextUtil.emitPostMemoryHook(parameters, retrievedHistory, null, hookRegistry);

        // Should return context without throwing, fallback behavior
        assertNotNull(result);
        assertEquals(1, result.getChatHistory().size());
    }

    @Test
    public void testBuildContextManagerContextForMemory() {
        Map<String, String> parameters = new HashMap<>();
        parameters.put("system_prompt", "You are a helpful assistant");
        parameters.put("question", "What is OpenSearch?");

        List<Interaction> history = new ArrayList<>();
        history.add(Interaction.builder().id("1").input("hello").response("hi").build());
        history.add(Interaction.builder().id("2").input("how are you").response("good").build());

        List<MLToolSpec> toolSpecs = new ArrayList<>();
        toolSpecs.add(MLToolSpec.builder().name("tool1").type("type1").build());

        ContextManagerContext result = AgentContextUtil.buildContextManagerContextForMemory(parameters, history, toolSpecs);

        assertNotNull(result);
        assertEquals("You are a helpful assistant", result.getSystemPrompt());
        assertEquals("What is OpenSearch?", result.getUserPrompt());
        assertEquals(2, result.getChatHistory().size());
        assertEquals("hello", result.getChatHistory().get(0).getInput());
        assertEquals("how are you", result.getChatHistory().get(1).getInput());
        assertEquals(1, result.getToolConfigs().size());
    }

    @Test
    public void testEmitPostStructuredMemoryHookWithNullHookRegistry() {
        Map<String, String> parameters = new HashMap<>();
        parameters.put("question", "test question");

        List<Message> retrievedHistory = new ArrayList<>();
        retrievedHistory.add(new Message("user", null));
        List<MLToolSpec> toolSpecs = new ArrayList<>();
        Memory memory = mock(Memory.class);

        ContextManagerContext result = AgentContextUtil.emitPostStructuredMemoryHook(parameters, retrievedHistory, toolSpecs, null);

        assertNotNull(result);
        assertEquals("test question", result.getUserPrompt());
        assertEquals(1, result.getStructuredChatHistory().size());
        assertEquals("user", result.getStructuredChatHistory().get(0).getRole());
    }

    @Test
    public void testEmitPostStructuredMemoryHookWithValidHookRegistry() {
        Map<String, String> parameters = new HashMap<>();
        parameters.put("question", "test question");

        List<Message> retrievedHistory = new ArrayList<>();
        retrievedHistory.add(new Message("user", null));
        retrievedHistory.add(new Message("assistant", null));
        List<MLToolSpec> toolSpecs = new ArrayList<>();
        Memory memory = mock(Memory.class);
        HookRegistry hookRegistry = mock(HookRegistry.class);

        ContextManagerContext result = AgentContextUtil.emitPostStructuredMemoryHook(parameters, retrievedHistory, toolSpecs, hookRegistry);

        assertNotNull(result);
        assertEquals("test question", result.getUserPrompt());
        assertEquals(2, result.getStructuredChatHistory().size());
        verify(hookRegistry, times(1)).emit(any());
    }

    @Test
    public void testEmitPostStructuredMemoryHookWithEmptyHistory() {
        Map<String, String> parameters = new HashMap<>();
        Memory memory = mock(Memory.class);
        HookRegistry hookRegistry = mock(HookRegistry.class);

        ContextManagerContext result = AgentContextUtil.emitPostStructuredMemoryHook(parameters, new ArrayList<>(), null, hookRegistry);

        assertNotNull(result);
        assertTrue(result.getStructuredChatHistory().isEmpty());
        verify(hookRegistry, times(1)).emit(any());
    }

    @Test
    public void testEmitPostStructuredMemoryHookWithNullHistory() {
        Map<String, String> parameters = new HashMap<>();
        Memory memory = mock(Memory.class);
        HookRegistry hookRegistry = mock(HookRegistry.class);

        ContextManagerContext result = AgentContextUtil.emitPostStructuredMemoryHook(parameters, null, null, hookRegistry);

        assertNotNull(result);
        assertTrue(result.getStructuredChatHistory().isEmpty());
        verify(hookRegistry, times(1)).emit(any());
    }

    @Test
    public void testEmitPostStructuredMemoryHookWithException() {
        Map<String, String> parameters = new HashMap<>();
        List<Message> retrievedHistory = new ArrayList<>();
        retrievedHistory.add(new Message("user", null));
        Memory memory = mock(Memory.class);
        HookRegistry hookRegistry = mock(HookRegistry.class);
        doThrow(new RuntimeException("hook error")).when(hookRegistry).emit(any());

        ContextManagerContext result = AgentContextUtil.emitPostStructuredMemoryHook(parameters, retrievedHistory, null, hookRegistry);

        // Should return context without throwing, fallback behavior
        assertNotNull(result);
        assertEquals(1, result.getStructuredChatHistory().size());
    }

    @Test
    public void testBuildContextManagerContextForStructuredMemory() {
        Map<String, String> parameters = new HashMap<>();
        parameters.put("system_prompt", "You are a helpful assistant");
        parameters.put("question", "What is OpenSearch?");

        List<Message> history = new ArrayList<>();
        history.add(new Message("user", null));
        history.add(new Message("assistant", null));

        List<MLToolSpec> toolSpecs = new ArrayList<>();
        toolSpecs.add(MLToolSpec.builder().name("tool1").type("type1").build());

        ContextManagerContext result = AgentContextUtil.buildContextManagerContextForStructuredMemory(parameters, history, toolSpecs);

        assertNotNull(result);
        assertEquals("You are a helpful assistant", result.getSystemPrompt());
        assertEquals("What is OpenSearch?", result.getUserPrompt());
        assertEquals(2, result.getStructuredChatHistory().size());
        assertEquals("user", result.getStructuredChatHistory().get(0).getRole());
        assertEquals("assistant", result.getStructuredChatHistory().get(1).getRole());
        assertEquals(1, result.getToolConfigs().size());
    }

    @Test
    public void testBuildContextManagerContextForToolOutput() {
        Map<String, String> parameters = new HashMap<>();
        parameters.put("question", "test question");

        List<MLToolSpec> toolSpecs = new ArrayList<>();
        Memory memory = mock(Memory.class);

        ContextManagerContext result = AgentContextUtil
            .buildContextManagerContextForToolOutput("tool output text", parameters, toolSpecs, memory);

        assertNotNull(result);
        assertEquals("test question", result.getUserPrompt());
        assertEquals("tool output text", result.getParameters().get("_current_tool_output"));
    }

    @Test
    public void testExtractProcessedToolOutput() {
        Map<String, String> params = new HashMap<>();
        params.put("_current_tool_output", "processed output");
        ContextManagerContext context = ContextManagerContext.builder().parameters(params).build();

        Object result = AgentContextUtil.extractProcessedToolOutput(context);
        assertEquals("processed output", result);
    }

    @Test
    public void testExtractProcessedToolOutputNullParams() {
        ContextManagerContext context = ContextManagerContext.builder().parameters(null).build();
        Object result = AgentContextUtil.extractProcessedToolOutput(context);
        assertNull(result);
    }

    @Test
    public void testExtractFromContext() {
        Map<String, String> params = new HashMap<>();
        params.put("key1", "value1");
        ContextManagerContext context = ContextManagerContext.builder().parameters(params).build();

        assertEquals("value1", AgentContextUtil.extractFromContext(context, "key1"));
        assertNull(AgentContextUtil.extractFromContext(context, "nonexistent"));
    }

    @Test
    public void testExtractFromContextNullParams() {
        ContextManagerContext context = ContextManagerContext.builder().parameters(null).build();
        assertNull(AgentContextUtil.extractFromContext(context, "key1"));
    }

    @Test
    public void testEmitPostToolHookWithNullHookRegistry() {
        Map<String, String> parameters = new HashMap<>();
        Memory memory = mock(Memory.class);

        Object result = AgentContextUtil.emitPostToolHook("tool output", parameters, null, memory, null);
        assertEquals("tool output", result);
    }

    @Test
    public void testEmitPostToolHookWithNullToolOutput() {
        Map<String, String> parameters = new HashMap<>();
        Memory memory = mock(Memory.class);
        HookRegistry hookRegistry = mock(HookRegistry.class);

        Object result = AgentContextUtil.emitPostToolHook(null, parameters, null, memory, hookRegistry);
        assertNull(result);
    }

    @Test
    public void testEmitPostToolHookWithValidHookRegistry() {
        Map<String, String> parameters = new HashMap<>();
        Memory memory = mock(Memory.class);
        HookRegistry hookRegistry = mock(HookRegistry.class);

        Object result = AgentContextUtil.emitPostToolHook("tool output", parameters, null, memory, hookRegistry);
        assertNotNull(result);
        verify(hookRegistry, times(1)).emit(any());
    }

    @Test
    public void testEmitPostToolHookWithException() {
        Map<String, String> parameters = new HashMap<>();
        Memory memory = mock(Memory.class);
        HookRegistry hookRegistry = mock(HookRegistry.class);
        doThrow(new RuntimeException("hook error")).when(hookRegistry).emit(any());

        Object result = AgentContextUtil.emitPostToolHook("tool output", parameters, null, memory, hookRegistry);
        assertEquals("tool output", result);
    }

    @Test
    public void testEnsureLlmModelIdFromLlm() {
        LLMSpec llmSpec = LLMSpec.builder().modelId("llm-model-id").build();
        MLAgent mlAgent = MLAgent.builder().name("test").type("conversational").llm(llmSpec).build();
        Map<String, String> params = new HashMap<>();

        AgentContextUtil.ensureLlmModelId(mlAgent, params);
        assertEquals("llm-model-id", params.get("_llm_model_id"));
    }

    @Test
    public void testEnsureLlmModelIdFromModel() {
        MLAgentModelSpec modelSpec = MLAgentModelSpec.builder().modelId("model-spec-id").modelProvider("test").build();
        MLAgent mlAgent = MLAgent.builder().name("test").type("flow").model(modelSpec).build();
        Map<String, String> params = new HashMap<>();

        AgentContextUtil.ensureLlmModelId(mlAgent, params);
        assertEquals("model-spec-id", params.get("_llm_model_id"));
    }

    @Test
    public void testEnsureLlmModelIdDoesNotOverwrite() {
        LLMSpec llmSpec = LLMSpec.builder().modelId("llm-model-id").build();
        MLAgent mlAgent = MLAgent.builder().name("test").type("conversational").llm(llmSpec).build();
        Map<String, String> params = new HashMap<>();
        params.put("_llm_model_id", "existing-id");

        AgentContextUtil.ensureLlmModelId(mlAgent, params);
        assertEquals("existing-id", params.get("_llm_model_id"));
    }

    @Test
    public void testUpdateParametersFromContext() {
        Map<String, String> parameters = new HashMap<>();
        ContextManagerContext context = ContextManagerContext
            .builder()
            .systemPrompt("updated system prompt")
            .userPrompt("updated question")
            .toolInteractions(List.of("interaction1", "interaction2"))
            .parameters(Map.of("extra_key", "extra_value"))
            .build();

        AgentContextUtil.updateParametersFromContext(parameters, context);

        assertEquals("updated system prompt", parameters.get("system_prompt"));
        assertEquals("updated question", parameters.get("question"));
        assertTrue(parameters.get("_interactions").contains("interaction1"));
        assertEquals("extra_value", parameters.get("extra_key"));
    }
}
