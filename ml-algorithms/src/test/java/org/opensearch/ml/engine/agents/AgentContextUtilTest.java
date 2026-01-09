package org.opensearch.ml.engine.agents;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;
import org.opensearch.ml.common.agent.MLToolSpec;
import org.opensearch.ml.common.contextmanager.ContextManagerContext;
import org.opensearch.ml.common.hooks.HookRegistry;
import org.opensearch.ml.common.spi.memory.Memory;

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
}
