/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.agent;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.Settings;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.ml.common.MLAgentType;
import org.opensearch.ml.common.MLMemoryType;
import org.opensearch.ml.common.agent.LLMSpec;
import org.opensearch.ml.common.agent.MLAgent;
import org.opensearch.ml.common.agent.MLAgentModelSpec;
import org.opensearch.ml.common.agent.MLMemorySpec;
import org.opensearch.ml.common.hooks.HookRegistry;
import org.opensearch.ml.common.hooks.PreLLMEvent;
import org.opensearch.ml.common.input.execute.agent.ContentBlock;
import org.opensearch.ml.common.input.execute.agent.ContentType;
import org.opensearch.ml.common.input.execute.agent.Message;
import org.opensearch.ml.common.memory.Memory;
import org.opensearch.ml.common.spi.tools.Tool;
import org.opensearch.ml.engine.encryptor.Encryptor;
import org.opensearch.remote.metadata.client.SdkClient;
import org.opensearch.transport.TransportChannel;
import org.opensearch.transport.client.Client;

public class MLAGUIAgentRunnerTest {

    @Mock
    private Client client;

    @Mock
    private ClusterService clusterService;

    @Mock
    private NamedXContentRegistry xContentRegistry;

    @Mock
    @SuppressWarnings("rawtypes")
    private Map<String, Memory.Factory> memoryFactoryMap;

    @Mock
    private SdkClient sdkClient;

    @Mock
    private Encryptor encryptor;

    @Mock
    private ActionListener<Object> agentActionListener;

    @Mock
    private TransportChannel channel;

    @Mock
    @SuppressWarnings("rawtypes")
    private Memory.Factory memoryFactory;

    @Mock
    private Memory memory;

    private Settings settings;
    @SuppressWarnings("rawtypes")
    private Map<String, Tool.Factory> toolFactories;
    private MLAGUIAgentRunner aguiAgentRunner;

    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);
        settings = Settings.builder().build();
        toolFactories = new HashMap<>();

        aguiAgentRunner = new MLAGUIAgentRunner(
            client,
            settings,
            clusterService,
            xContentRegistry,
            toolFactories,
            memoryFactoryMap,
            sdkClient,
            encryptor
        );
    }

    @Test
    public void testRun_WithLegacyLLMInterface() {
        // Arrange - agent uses legacy LLM interface
        MLAgent mlAgent = MLAgent
            .builder()
            .name("TestAGUIAgent")
            .type(MLAgentType.AG_UI.name())
            .llm(LLMSpec.builder().modelId("test-model-id").build())
            .build();

        Map<String, String> params = new HashMap<>();

        // Act
        aguiAgentRunner.run(mlAgent, params, agentActionListener, channel);

        // Assert - verify agent_type parameter is set
        assertEquals("ag_ui", params.get("agent_type"));
    }

    @Test
    public void testRun_WithRevampedModelInterface() {
        // Arrange - agent uses revamped model interface
        MLAgent mlAgent = MLAgent
            .builder()
            .name("TestAGUIAgent")
            .type(MLAgentType.AG_UI.name())
            .model(MLAgentModelSpec.builder().modelId("anthropic.claude-v2").modelProvider("bedrock/converse").build())
            .build();

        Map<String, String> params = new HashMap<>();

        // Act
        aguiAgentRunner.run(mlAgent, params, agentActionListener, channel);

        // Assert - verify agent_type parameter is set with revamped interface
        assertEquals("ag_ui", params.get("agent_type"));
    }

    @Test
    public void testRun_WithLLMInterfaceInAgentParameters() {
        // Arrange - agent has llm_interface in its parameters
        Map<String, String> agentParams = new HashMap<>();
        agentParams.put("llm_interface", "bedrock/converse/claude");

        MLAgent mlAgent = MLAgent
            .builder()
            .name("TestAGUIAgent")
            .type(MLAgentType.AG_UI.name())
            .llm(LLMSpec.builder().modelId("test-model-id").build())
            .parameters(agentParams)
            .build();

        Map<String, String> params = new HashMap<>();

        // Act
        aguiAgentRunner.run(mlAgent, params, agentActionListener, channel);

        // Assert - agent_type is set even when llm_interface comes from agent
        assertEquals("ag_ui", params.get("agent_type"));
    }

    @Test
    public void testConstructor_WithHookRegistry() {
        // Arrange
        HookRegistry hookRegistry = new HookRegistry();

        // Add a callback to the hook registry to verify it's functional
        final boolean[] callbackInvoked = { false };
        hookRegistry.addCallback(PreLLMEvent.class, event -> { callbackInvoked[0] = true; });

        // Act
        MLAGUIAgentRunner runnerWithHooks = new MLAGUIAgentRunner(
            client,
            settings,
            clusterService,
            xContentRegistry,
            toolFactories,
            memoryFactoryMap,
            sdkClient,
            encryptor,
            hookRegistry
        );

        // Assert - runner is created with hook registry
        assertNotNull(runnerWithHooks);

        // Verify hook registry has the callback registered
        assertEquals(1, hookRegistry.getCallbackCount(PreLLMEvent.class));

        // Verify runner can execute (hook registry is passed to MLChatAgentRunner)
        MLAgent mlAgent = MLAgent
            .builder()
            .name("TestAGUIAgent")
            .type(MLAgentType.AG_UI.name())
            .llm(LLMSpec.builder().modelId("test-model-id").build())
            .build();

        Map<String, String> params = new HashMap<>();
        runnerWithHooks.run(mlAgent, params, agentActionListener, channel);
        assertEquals("ag_ui", params.get("agent_type"));
    }

    // ==================== Tests for handleHistoryLoad ====================

    @Test
    @SuppressWarnings("unchecked")
    public void testHistoryLoad_NoMemoryConfigured_SendsEmptySnapshot() {
        // Agent has no memory configured
        MLAgent mlAgent = MLAgent
            .builder()
            .name("TestAGUIAgent")
            .type(MLAgentType.AG_UI.name())
            .llm(LLMSpec.builder().modelId("test-model-id").build())
            .build();

        Map<String, String> params = new HashMap<>();
        params.put("agui_load_chat_history", "true");

        aguiAgentRunner.run(mlAgent, params, agentActionListener, channel);

        // With no memory, sendMessagesSnapshot is called with empty list.
        // StreamingWrapper checks isStreaming (from channel), which is false for mock,
        // so no event is actually sent — but the code path is exercised without error.
        verify(agentActionListener, never()).onFailure(any());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testHistoryLoad_WithMessages_SendsSnapshot() {
        // Set up memory with structured messages
        MLMemorySpec memorySpec = MLMemorySpec.builder().type(MLMemoryType.AGENTIC_MEMORY.name()).build();
        MLAgent mlAgent = MLAgent
            .builder()
            .name("TestAGUIAgent")
            .type(MLAgentType.AG_UI.name())
            .llm(LLMSpec.builder().modelId("test-model-id").build())
            .memory(memorySpec)
            .build();

        List<Message> historyMessages = new ArrayList<>();
        historyMessages.add(createTextMessage("user", "Hello"));
        historyMessages.add(createTextMessage("assistant", "Hi there!"));

        when(memoryFactoryMap.isEmpty()).thenReturn(false);
        when(memoryFactoryMap.get(anyString())).thenReturn(memoryFactory);
        doAnswer(invocation -> {
            ActionListener<Memory> listener = invocation.getArgument(1);
            listener.onResponse(memory);
            return null;
        }).when(memoryFactory).create(any(), any(ActionListener.class));

        doAnswer(invocation -> {
            ActionListener<List<Message>> listener = invocation.getArgument(0);
            listener.onResponse(historyMessages);
            return null;
        }).when(memory).getStructuredMessages(any(ActionListener.class));

        when(memory.getId()).thenReturn("test-session-id");

        Map<String, String> params = new HashMap<>();
        params.put("agui_load_chat_history", "true");
        params.put("memory_id", "test-session-id");

        aguiAgentRunner.run(mlAgent, params, agentActionListener, channel);

        // Verify memory was created and structured messages were retrieved
        verify(memoryFactory).create(any(), any(ActionListener.class));
        verify(memory).getStructuredMessages(any(ActionListener.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testHistoryLoad_GetStructuredMessagesFails_PropagatesError() {
        MLMemorySpec memorySpec = MLMemorySpec.builder().type(MLMemoryType.AGENTIC_MEMORY.name()).build();
        MLAgent mlAgent = MLAgent
            .builder()
            .name("TestAGUIAgent")
            .type(MLAgentType.AG_UI.name())
            .llm(LLMSpec.builder().modelId("test-model-id").build())
            .memory(memorySpec)
            .build();

        RuntimeException expectedError = new RuntimeException("Failed to get messages");

        when(memoryFactoryMap.isEmpty()).thenReturn(false);
        when(memoryFactoryMap.get(anyString())).thenReturn(memoryFactory);
        doAnswer(invocation -> {
            ActionListener<Memory> listener = invocation.getArgument(1);
            listener.onResponse(memory);
            return null;
        }).when(memoryFactory).create(any(), any(ActionListener.class));

        doAnswer(invocation -> {
            ActionListener<List<Message>> listener = invocation.getArgument(0);
            listener.onFailure(expectedError);
            return null;
        }).when(memory).getStructuredMessages(any(ActionListener.class));

        Map<String, String> params = new HashMap<>();
        params.put("agui_load_chat_history", "true");
        params.put("memory_id", "test-session-id");

        aguiAgentRunner.run(mlAgent, params, agentActionListener, channel);

        ArgumentCaptor<Exception> errorCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(agentActionListener).onFailure(errorCaptor.capture());
        assertEquals("Failed to get messages", errorCaptor.getValue().getMessage());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testHistoryLoad_MemoryFactoryCreateFails_PropagatesError() {
        MLMemorySpec memorySpec = MLMemorySpec.builder().type(MLMemoryType.AGENTIC_MEMORY.name()).build();
        MLAgent mlAgent = MLAgent
            .builder()
            .name("TestAGUIAgent")
            .type(MLAgentType.AG_UI.name())
            .llm(LLMSpec.builder().modelId("test-model-id").build())
            .memory(memorySpec)
            .build();

        RuntimeException expectedError = new RuntimeException("Memory creation failed");

        when(memoryFactoryMap.isEmpty()).thenReturn(false);
        when(memoryFactoryMap.get(anyString())).thenReturn(memoryFactory);
        doAnswer(invocation -> {
            ActionListener<Memory> listener = invocation.getArgument(1);
            listener.onFailure(expectedError);
            return null;
        }).when(memoryFactory).create(any(), any(ActionListener.class));

        Map<String, String> params = new HashMap<>();
        params.put("agui_load_chat_history", "true");
        params.put("memory_id", "test-session-id");

        aguiAgentRunner.run(mlAgent, params, agentActionListener, channel);

        ArgumentCaptor<Exception> errorCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(agentActionListener).onFailure(errorCaptor.capture());
        assertEquals("Memory creation failed", errorCaptor.getValue().getMessage());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testHistoryLoad_MemoryFactoryNotFound_PropagatesError() {
        MLMemorySpec memorySpec = MLMemorySpec.builder().type(MLMemoryType.AGENTIC_MEMORY.name()).build();
        MLAgent mlAgent = MLAgent
            .builder()
            .name("TestAGUIAgent")
            .type(MLAgentType.AG_UI.name())
            .llm(LLMSpec.builder().modelId("test-model-id").build())
            .memory(memorySpec)
            .build();

        when(memoryFactoryMap.isEmpty()).thenReturn(false);
        when(memoryFactoryMap.get(anyString())).thenReturn(null);

        Map<String, String> params = new HashMap<>();
        params.put("agui_load_chat_history", "true");
        params.put("memory_id", "test-session-id");

        aguiAgentRunner.run(mlAgent, params, agentActionListener, channel);

        ArgumentCaptor<Exception> errorCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(agentActionListener).onFailure(errorCaptor.capture());
        assertEquals("Memory factory not found for type: AGENTIC_MEMORY", errorCaptor.getValue().getMessage());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testHistoryLoad_WithHistoryLimit_AppliesLimit() {
        MLMemorySpec memorySpec = MLMemorySpec.builder().type(MLMemoryType.AGENTIC_MEMORY.name()).build();
        MLAgent mlAgent = MLAgent
            .builder()
            .name("TestAGUIAgent")
            .type(MLAgentType.AG_UI.name())
            .llm(LLMSpec.builder().modelId("test-model-id").build())
            .memory(memorySpec)
            .build();

        // Create 4 messages
        List<Message> historyMessages = new ArrayList<>();
        historyMessages.add(createTextMessage("user", "First question"));
        historyMessages.add(createTextMessage("assistant", "First answer"));
        historyMessages.add(createTextMessage("user", "Second question"));
        historyMessages.add(createTextMessage("assistant", "Second answer"));

        when(memoryFactoryMap.isEmpty()).thenReturn(false);
        when(memoryFactoryMap.get(anyString())).thenReturn(memoryFactory);
        doAnswer(invocation -> {
            ActionListener<Memory> listener = invocation.getArgument(1);
            listener.onResponse(memory);
            return null;
        }).when(memoryFactory).create(any(), any(ActionListener.class));

        doAnswer(invocation -> {
            ActionListener<List<Message>> listener = invocation.getArgument(0);
            listener.onResponse(historyMessages);
            return null;
        }).when(memory).getStructuredMessages(any(ActionListener.class));

        when(memory.getId()).thenReturn("test-session-id");

        Map<String, String> params = new HashMap<>();
        params.put("agui_load_chat_history", "true");
        params.put("memory_id", "test-session-id");
        params.put("message_history_limit", "2"); // Limit to 2 most recent messages

        aguiAgentRunner.run(mlAgent, params, agentActionListener, channel);

        // Verify the history-load path was taken
        verify(memory).getStructuredMessages(any(ActionListener.class));
    }

    private static Message createTextMessage(String role, String text) {
        ContentBlock block = new ContentBlock();
        block.setType(ContentType.TEXT);
        block.setText(text);
        Message msg = new Message();
        msg.setRole(role);
        msg.setContent(List.of(block));
        return msg;
    }
}
