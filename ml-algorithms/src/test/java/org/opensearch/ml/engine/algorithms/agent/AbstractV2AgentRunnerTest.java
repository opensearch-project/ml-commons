/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.agent;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.Settings;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.ml.common.MLMemoryType;
import org.opensearch.ml.common.agent.MLAgent;
import org.opensearch.ml.common.agent.MLAgentModelSpec;
import org.opensearch.ml.common.agent.TokenUsage;
import org.opensearch.ml.common.input.execute.agent.ContentBlock;
import org.opensearch.ml.common.input.execute.agent.ContentType;
import org.opensearch.ml.common.input.execute.agent.Message;
import org.opensearch.ml.common.memory.Memory;
import org.opensearch.ml.common.model.ModelProvider;
import org.opensearch.ml.common.output.execute.agent.AgentV2Output;
import org.opensearch.ml.common.output.model.ModelTensor;
import org.opensearch.ml.common.output.model.ModelTensorOutput;
import org.opensearch.ml.common.output.model.ModelTensors;
import org.opensearch.ml.common.spi.tools.Tool;
import org.opensearch.ml.engine.encryptor.Encryptor;
import org.opensearch.ml.engine.function_calling.FunctionCalling;
import org.opensearch.remote.metadata.client.SdkClient;
import org.opensearch.transport.TransportChannel;
import org.opensearch.transport.client.Client;

public class AbstractV2AgentRunnerTest {

    @Mock
    private Client client;
    private Settings settings;  // Cannot mock - final class
    @Mock
    private ClusterService clusterService;
    @Mock
    private NamedXContentRegistry xContentRegistry;
    @Mock
    private Map<String, Tool.Factory> toolFactories;
    @Mock
    private SdkClient sdkClient;
    @Mock
    private Encryptor encryptor;
    @Mock
    private Memory memory;
    @Mock
    private MLAgent mlAgent;
    @Mock
    private ModelProvider modelProvider;
    @Mock
    private FunctionCalling functionCalling;
    @Mock
    private TransportChannel channel;

    private TestV2AgentRunner runner;

    // Concrete implementation for testing
    private static class TestV2AgentRunner extends AbstractV2AgentRunner {

        private AgentLogicResult mockResult;

        public TestV2AgentRunner(
            Client client,
            Settings settings,
            ClusterService clusterService,
            NamedXContentRegistry xContentRegistry,
            Map<String, Tool.Factory> toolFactories,
            SdkClient sdkClient,
            Encryptor encryptor
        ) {
            super(client, settings, clusterService, xContentRegistry, toolFactories, sdkClient, encryptor);
        }

        public void setMockResult(AgentLogicResult result) {
            this.mockResult = result;
        }

        @Override
        protected int getDefaultMaxIterations() {
            return 3;
        }

        @Override
        protected void executeAgentLogic(
            MLAgent mlAgent,
            Map<String, String> params,
            List<Message> conversationHistory,
            FunctionCalling functionCalling,
            ModelProvider modelProvider,
            ActionListener<AgentLogicResult> listener
        ) {
            if (mockResult != null) {
                listener.onResponse(mockResult);
            } else {
                listener.onFailure(new RuntimeException("No mock result set"));
            }
        }
    }

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        settings = Settings.builder().build();  // Create real Settings instance - cannot mock final class
        runner = new TestV2AgentRunner(client, settings, clusterService, xContentRegistry, toolFactories, sdkClient, encryptor);
    }

    // ==================== Tests for validateV2Agent ====================

    @Test
    public void testValidateV2Agent_NullMemory() {
        // Act & Assert
        try {
            runner.validateV2Agent(mlAgent, null);
            fail("Should throw IllegalStateException for null memory");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("V2 agents require agentic memory"));
        }
    }

    @Test
    public void testValidateV2Agent_InvalidMemoryType() {
        // Arrange
        when(memory.getType()).thenReturn("conversation_index");

        // Act & Assert
        try {
            runner.validateV2Agent(mlAgent, memory);
            fail("Should throw IllegalStateException for invalid memory type");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("V2 agents only support agentic_memory"));
            assertTrue(e.getMessage().contains("Found: conversation_index"));
        }
    }

    @Test
    public void testValidateV2Agent_AgenticMemory_Success() {
        // Arrange
        when(memory.getType()).thenReturn(MLMemoryType.AGENTIC_MEMORY.name());
        MLAgentModelSpec modelSpec = new MLAgentModelSpec("model-123", "bedrock", null, null);
        when(mlAgent.getModel()).thenReturn(modelSpec);
        when(mlAgent.usesUnifiedInterface()).thenReturn(true);

        // Act - should not throw
        runner.validateV2Agent(mlAgent, memory);
    }

    @Test
    public void testValidateV2Agent_RemoteAgenticMemory_Success() {
        // Arrange
        when(memory.getType()).thenReturn(MLMemoryType.REMOTE_AGENTIC_MEMORY.name());
        MLAgentModelSpec modelSpec = new MLAgentModelSpec("model-123", "bedrock", null, null);
        when(mlAgent.getModel()).thenReturn(modelSpec);
        when(mlAgent.usesUnifiedInterface()).thenReturn(true);

        // Act - should not throw
        runner.validateV2Agent(mlAgent, memory);
    }

    @Test
    public void testValidateV2Agent_MissingModelField() {
        // Arrange
        when(memory.getType()).thenReturn(MLMemoryType.AGENTIC_MEMORY.name());
        when(mlAgent.usesUnifiedInterface()).thenReturn(false);  // No model field

        // Act & Assert
        try {
            runner.validateV2Agent(mlAgent, memory);
            fail("Should throw IllegalStateException for missing model field");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("V2 agents require the 'model' field"));
        }
    }

    // ==================== Tests for getMaxIterations ====================

    @Test
    public void testGetMaxIterations_FromParams() {
        // Arrange
        Map<String, String> params = new HashMap<>();
        params.put("max_iteration", "10");

        // Act
        int result = runner.getMaxIterations(params);

        // Assert
        assertEquals(10, result);
    }

    @Test
    public void testGetMaxIterations_InvalidValue_UsesDefault() {
        // Arrange
        Map<String, String> params = new HashMap<>();
        params.put("max_iteration", "invalid");

        // Act
        int result = runner.getMaxIterations(params);

        // Assert
        assertEquals(3, result); // TestV2AgentRunner default
    }

    @Test
    public void testGetMaxIterations_NoParam_UsesDefault() {
        // Arrange
        Map<String, String> params = new HashMap<>();

        // Act
        int result = runner.getMaxIterations(params);

        // Assert
        assertEquals(3, result);
    }

    // ==================== Tests for getSystemPrompt ====================

    @Test
    public void testGetSystemPrompt_FromParams() {
        // Arrange
        Map<String, String> params = new HashMap<>();
        params.put("system_prompt", "Custom system prompt");

        // Act
        String result = runner.getSystemPrompt(params, mlAgent);

        // Assert
        assertEquals("Custom system prompt", result);
    }

    @Test
    public void testGetSystemPrompt_FromAgent() {
        // Arrange
        Map<String, String> params = new HashMap<>();
        Map<String, String> agentParams = new HashMap<>();
        agentParams.put("system_prompt", "Agent system prompt");
        when(mlAgent.getParameters()).thenReturn(agentParams);

        // Act
        String result = runner.getSystemPrompt(params, mlAgent);

        // Assert
        assertEquals("Agent system prompt", result);
    }

    @Test
    public void testGetSystemPrompt_Default() {
        // Arrange
        Map<String, String> params = new HashMap<>();
        when(mlAgent.getParameters()).thenReturn(null);

        // Act
        String result = runner.getSystemPrompt(params, mlAgent);

        // Assert
        assertEquals("You are a helpful assistant", result);
    }

    @Test
    public void testGetSystemPrompt_EmptyString_UsesAgentOrDefault() {
        // Arrange
        Map<String, String> params = new HashMap<>();
        params.put("system_prompt", "");
        Map<String, String> agentParams = new HashMap<>();
        agentParams.put("system_prompt", "Agent prompt");
        when(mlAgent.getParameters()).thenReturn(agentParams);

        // Act
        String result = runner.getSystemPrompt(params, mlAgent);

        // Assert
        assertEquals("Agent prompt", result);
    }

    // ==================== Tests for extractTokenUsage ====================

    @Test
    public void testExtractTokenUsage_ValidOutput() {
        // Arrange
        Map<String, Object> dataMap = new HashMap<>();
        dataMap.put("usage", Map.of("inputTokens", 100, "outputTokens", 50));

        ModelTensor tensor = ModelTensor.builder().dataAsMap(dataMap).build();
        ModelTensors tensors = ModelTensors.builder().mlModelTensors(List.of(tensor)).build();
        ModelTensorOutput output = ModelTensorOutput.builder().mlModelOutputs(List.of(tensors)).build();

        TokenUsage expectedUsage = TokenUsage.builder().inputTokens(100L).outputTokens(50L).totalTokens(150L).build();
        when(functionCalling.extractTokenUsage(dataMap)).thenReturn(expectedUsage);

        // Act
        TokenUsage result = runner.extractTokenUsage(output, functionCalling);

        // Assert
        assertNotNull(result);
        assertEquals(Long.valueOf(100), result.getInputTokens());
        assertEquals(Long.valueOf(50), result.getOutputTokens());
    }

    @Test
    public void testExtractTokenUsage_NullOutput() {
        // Act
        TokenUsage result = runner.extractTokenUsage(null, functionCalling);

        // Assert
        assertNull(result);
    }

    @Test
    public void testExtractTokenUsage_EmptyOutputs() {
        // Arrange
        ModelTensorOutput output = ModelTensorOutput.builder().mlModelOutputs(List.of()).build();

        // Act
        TokenUsage result = runner.extractTokenUsage(output, functionCalling);

        // Assert
        assertNull(result);
    }

    // ==================== Tests for buildStandardizedOutput ====================

    @Test
    public void testBuildStandardizedOutput_WithTokenUsage() {
        // Arrange
        ContentBlock contentBlock = new ContentBlock();
        contentBlock.setType(ContentType.TEXT);
        contentBlock.setText("Response text");

        Message assistantMessage = new Message();
        assistantMessage.setRole("assistant");
        assistantMessage.setContent(List.of(contentBlock));

        TokenUsage tokenUsage = TokenUsage.builder().inputTokens(100L).outputTokens(50L).totalTokens(150L).build();
        Map<String, String> params = new HashMap<>();

        // Act
        AgentV2Output result = runner.buildStandardizedOutput(assistantMessage, "memory-123", "end_turn", tokenUsage, params);

        // Assert
        assertNotNull(result);
        assertEquals("end_turn", result.getStopReason());
        assertEquals("memory-123", result.getMemoryId());
        assertEquals(assistantMessage, result.getMessage());
        assertNotNull(result.getMetrics());

        @SuppressWarnings("unchecked")
        Map<String, Object> totalUsage = (Map<String, Object>) result.getMetrics().get("total_usage");
        assertEquals(100L, totalUsage.get("inputTokens"));
        assertEquals(50L, totalUsage.get("outputTokens"));
        assertEquals(150L, totalUsage.get("totalTokens"));
    }

    @Test
    public void testBuildStandardizedOutput_NullTokenUsage() {
        // Arrange
        Message assistantMessage = new Message();
        assistantMessage.setRole("assistant");
        Map<String, String> params = new HashMap<>();

        // Act
        AgentV2Output result = runner.buildStandardizedOutput(assistantMessage, "memory-123", "end_turn", null, params);

        // Assert
        assertNotNull(result);
        assertNotNull(result.getMetrics());
        @SuppressWarnings("unchecked")
        Map<String, Object> totalUsage = (Map<String, Object>) result.getMetrics().get("total_usage");
        assertTrue(totalUsage.isEmpty());
    }

    @Test
    public void testBuildStandardizedOutput_WithCacheMetrics() {
        // Arrange
        Message assistantMessage = new Message();
        assistantMessage.setRole("assistant");

        // TokenUsage with cache metrics (using builder)
        TokenUsage tokenUsage = TokenUsage
            .builder()
            .inputTokens(100L)
            .outputTokens(50L)
            .totalTokens(150L)
            .cacheReadInputTokens(20L)
            .cacheCreationInputTokens(30L)
            .build();

        Map<String, String> params = new HashMap<>();

        // Act
        AgentV2Output result = runner.buildStandardizedOutput(assistantMessage, "memory-123", "end_turn", tokenUsage, params);

        // Assert
        @SuppressWarnings("unchecked")
        Map<String, Object> totalUsage = (Map<String, Object>) result.getMetrics().get("total_usage");
        assertEquals(20L, totalUsage.get("cacheReadInputTokens"));
        assertEquals(30L, totalUsage.get("cacheCreationInputTokens"));
    }

    // ==================== Tests for saveAssistantMessage ====================

    @Test
    public void testSaveAssistantMessage_Success() {
        // Arrange
        Message assistantMessage = new Message();
        assistantMessage.setRole("assistant");

        doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            ActionListener<Void> listener = invocation.getArgument(1);
            listener.onResponse(null);
            return null;
        }).when(memory).saveStructuredMessages(anyList(), any());

        // Act
        ActionListener<Void> testListener = ActionListener.wrap(response -> {
            // Success
        }, e -> fail("Should not fail"));

        runner.saveAssistantMessage(memory, assistantMessage, testListener);

        // Assert
        verify(memory).saveStructuredMessages(argThat(messages -> {
            assertEquals(1, messages.size());
            assertEquals("assistant", ((Message) messages.get(0)).getRole());
            return true;
        }), any());
    }

    @Test
    public void testSaveAssistantMessage_Failure() {
        // Arrange
        Message assistantMessage = new Message();
        Exception expectedException = new RuntimeException("Save failed");

        doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            ActionListener<Void> listener = invocation.getArgument(1);
            listener.onFailure(expectedException);
            return null;
        }).when(memory).saveStructuredMessages(anyList(), any());

        // Act
        ActionListener<Void> testListener = ActionListener
            .wrap(response -> fail("Should not succeed"), e -> { assertEquals(expectedException, e); });

        runner.saveAssistantMessage(memory, assistantMessage, testListener);

        // Assert
        verify(memory).saveStructuredMessages(anyList(), any());
    }

    // ==================== Tests for unsupported V1 methods ====================

    @Test
    public void testRun_WithoutMemory_ThrowsUnsupported() {
        // Arrange
        Map<String, String> params = new HashMap<>();

        // Act & Assert
        try {
            runner.run(mlAgent, params, null, channel);
            fail("Should throw UnsupportedOperationException");
        } catch (UnsupportedOperationException e) {
            assertTrue(e.getMessage().contains("V2 agents require executor-provided memory"));
        }
    }

    @Test
    public void testRun_WithMemoryButNoMessages_ThrowsUnsupported() {
        // Arrange
        Map<String, String> params = new HashMap<>();

        // Act & Assert
        try {
            runner.run(mlAgent, params, null, channel, memory);
            fail("Should throw UnsupportedOperationException");
        } catch (UnsupportedOperationException e) {
            assertTrue(e.getMessage().contains("V2 agents require message list"));
        }
    }
}
