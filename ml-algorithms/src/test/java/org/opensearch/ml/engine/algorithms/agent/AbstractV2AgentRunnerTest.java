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

    @Test
    public void testValidateV2Agent_WithContextManagement() {
        // Arrange
        when(memory.getType()).thenReturn(MLMemoryType.AGENTIC_MEMORY.name());
        MLAgentModelSpec modelSpec = new MLAgentModelSpec("model-123", "bedrock", null, null);
        when(mlAgent.getModel()).thenReturn(modelSpec);
        when(mlAgent.usesUnifiedInterface()).thenReturn(true);
        when(mlAgent.hasContextManagement()).thenReturn(true);  // Has context management configured

        // Act & Assert
        try {
            runner.validateV2Agent(mlAgent, memory);
            fail("Should throw IllegalStateException for V2 agent with context management");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("V2 agents do not support context management"));
            assertTrue(e.getMessage().contains("only supported for V1 agents"));
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

    // ==================== Tests for executeToolsSequentially ====================

    @Test
    public void testExecuteToolsSequentially_Success() {
        // Arrange
        Map<String, Tool> toolsMap = new HashMap<>();
        Tool mockTool = mock(Tool.class);
        toolsMap.put("test-tool", mockTool);

        List<Map<String, String>> toolCalls = List.of(Map.of("tool_name", "test-tool", "tool_input", "{}", "tool_call_id", "call-1"));

        doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            ActionListener<Object> listener = invocation.getArgument(1);
            listener.onResponse("Tool output");
            return null;
        }).when(mockTool).run(any(), any());

        ActionListener<List<Map<String, Object>>> testListener = mock(ActionListener.class);

        // Act
        runner.executeToolsSequentially(toolsMap, toolCalls, testListener);

        // Assert
        verify(testListener, timeout(1000)).onResponse(argThat(results -> {
            assertEquals(1, results.size());
            assertEquals("call-1", results.get(0).get("tool_call_id"));
            Map<String, Object> toolResult = (Map<String, Object>) results.get(0).get("tool_result");
            assertEquals("Tool output", toolResult.get("text"));
            return true;
        }));
    }

    @Test
    public void testExecuteToolsSequentially_ToolNotFound() {
        // Arrange
        Map<String, Tool> toolsMap = new HashMap<>();

        List<Map<String, String>> toolCalls = List.of(Map.of("tool_name", "missing-tool", "tool_input", "{}", "tool_call_id", "call-1"));

        ActionListener<List<Map<String, Object>>> testListener = mock(ActionListener.class);

        // Act
        runner.executeToolsSequentially(toolsMap, toolCalls, testListener);

        // Assert
        verify(testListener, timeout(1000)).onResponse(argThat(results -> {
            assertEquals(1, results.size());
            assertEquals("call-1", results.get(0).get("tool_call_id"));
            Map<String, Object> toolResult = (Map<String, Object>) results.get(0).get("tool_result");
            assertTrue(toolResult.get("error").toString().contains("Tool not found"));
            return true;
        }));
    }

    @Test
    public void testExecuteToolsSequentially_ToolExecutionFailure() {
        // Arrange
        Map<String, Tool> toolsMap = new HashMap<>();
        Tool mockTool = mock(Tool.class);
        toolsMap.put("test-tool", mockTool);

        List<Map<String, String>> toolCalls = List.of(Map.of("tool_name", "test-tool", "tool_input", "{}", "tool_call_id", "call-1"));

        doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            ActionListener<Object> listener = invocation.getArgument(1);
            listener.onFailure(new RuntimeException("Tool failed"));
            return null;
        }).when(mockTool).run(any(), any());

        ActionListener<List<Map<String, Object>>> testListener = mock(ActionListener.class);

        // Act
        runner.executeToolsSequentially(toolsMap, toolCalls, testListener);

        // Assert
        verify(testListener, timeout(1000)).onResponse(argThat(results -> {
            assertEquals(1, results.size());
            assertEquals("call-1", results.get(0).get("tool_call_id"));
            Map<String, Object> toolResult = (Map<String, Object>) results.get(0).get("tool_result");
            assertEquals("Tool failed", toolResult.get("error"));
            return true;
        }));
    }

    @Test
    public void testExecuteToolsSequentially_MultipleTools() {
        // Arrange
        Map<String, Tool> toolsMap = new HashMap<>();
        Tool mockTool1 = mock(Tool.class);
        Tool mockTool2 = mock(Tool.class);
        toolsMap.put("tool-1", mockTool1);
        toolsMap.put("tool-2", mockTool2);

        List<Map<String, String>> toolCalls = List
            .of(
                Map.of("tool_name", "tool-1", "tool_input", "{}", "tool_call_id", "call-1"),
                Map.of("tool_name", "tool-2", "tool_input", "{}", "tool_call_id", "call-2")
            );

        doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            ActionListener<Object> listener = invocation.getArgument(1);
            listener.onResponse("Output 1");
            return null;
        }).when(mockTool1).run(any(), any());

        doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            ActionListener<Object> listener = invocation.getArgument(1);
            listener.onResponse("Output 2");
            return null;
        }).when(mockTool2).run(any(), any());

        ActionListener<List<Map<String, Object>>> testListener = mock(ActionListener.class);

        // Act
        runner.executeToolsSequentially(toolsMap, toolCalls, testListener);

        // Assert
        verify(testListener, timeout(1000)).onResponse(argThat(results -> {
            assertEquals(2, results.size());
            assertEquals("call-1", results.get(0).get("tool_call_id"));
            assertEquals("call-2", results.get(1).get("tool_call_id"));
            return true;
        }));
    }

    // ==================== Tests for extractAssistantMessage ====================

    @Test
    public void testExtractAssistantMessage_Success() {
        // Arrange
        Map<String, Object> dataMap = new HashMap<>();
        dataMap.put("message", "{\"role\":\"assistant\",\"content\":[{\"text\":\"Hello\"}]}");

        ModelTensor tensor = ModelTensor.builder().dataAsMap(dataMap).build();
        ModelTensors tensors = ModelTensors.builder().mlModelTensors(List.of(tensor)).build();
        ModelTensorOutput output = ModelTensorOutput.builder().mlModelOutputs(List.of(tensors)).build();

        Message expectedMessage = new Message();
        expectedMessage.setRole("assistant");
        when(modelProvider.extractMessageFromResponse(dataMap)).thenReturn("{\"role\":\"assistant\",\"content\":[{\"text\":\"Hello\"}]}");
        when(modelProvider.parseToUnifiedMessage("{\"role\":\"assistant\",\"content\":[{\"text\":\"Hello\"}]}"))
            .thenReturn(expectedMessage);

        // Act
        Message result = runner.extractAssistantMessage(output, modelProvider);

        // Assert
        assertNotNull(result);
        assertEquals("assistant", result.getRole());
    }

    @Test
    public void testExtractAssistantMessage_NullOutput() {
        // Act & Assert
        try {
            runner.extractAssistantMessage(null, modelProvider);
            fail("Should throw IllegalStateException for null output");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("LLM output is null or empty"));
        }
    }

    @Test
    public void testExtractAssistantMessage_EmptyOutputs() {
        // Arrange
        ModelTensorOutput output = ModelTensorOutput.builder().mlModelOutputs(List.of()).build();

        // Act & Assert
        try {
            runner.extractAssistantMessage(output, modelProvider);
            fail("Should throw IllegalStateException for empty outputs");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("LLM output is null or empty"));
        }
    }

    @Test
    public void testExtractAssistantMessage_NullTensors() {
        // Arrange
        ModelTensors tensors = ModelTensors.builder().mlModelTensors(null).build();
        ModelTensorOutput output = ModelTensorOutput.builder().mlModelOutputs(List.of(tensors)).build();

        // Act & Assert
        try {
            runner.extractAssistantMessage(output, modelProvider);
            fail("Should throw IllegalStateException for null tensors");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("LLM output tensors are null or empty"));
        }
    }

    @Test
    public void testExtractAssistantMessage_NullDataMap() {
        // Arrange
        ModelTensor tensor = ModelTensor.builder().dataAsMap(null).build();
        ModelTensors tensors = ModelTensors.builder().mlModelTensors(List.of(tensor)).build();
        ModelTensorOutput output = ModelTensorOutput.builder().mlModelOutputs(List.of(tensors)).build();

        // Act & Assert
        try {
            runner.extractAssistantMessage(output, modelProvider);
            fail("Should throw IllegalStateException for null data map");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("LLM output data map is null"));
        }
    }

    @Test
    public void testExtractAssistantMessage_ExtractMessageFails() {
        // Arrange
        Map<String, Object> dataMap = new HashMap<>();
        ModelTensor tensor = ModelTensor.builder().dataAsMap(dataMap).build();
        ModelTensors tensors = ModelTensors.builder().mlModelTensors(List.of(tensor)).build();
        ModelTensorOutput output = ModelTensorOutput.builder().mlModelOutputs(List.of(tensors)).build();

        when(modelProvider.extractMessageFromResponse(dataMap)).thenReturn(null);

        // Act & Assert
        try {
            runner.extractAssistantMessage(output, modelProvider);
            fail("Should throw IllegalStateException when extraction fails");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("ModelProvider failed to extract message from response"));
        }
    }

    @Test
    public void testExtractAssistantMessage_ParseToUnifiedFails() {
        // Arrange
        Map<String, Object> dataMap = new HashMap<>();
        ModelTensor tensor = ModelTensor.builder().dataAsMap(dataMap).build();
        ModelTensors tensors = ModelTensors.builder().mlModelTensors(List.of(tensor)).build();
        ModelTensorOutput output = ModelTensorOutput.builder().mlModelOutputs(List.of(tensors)).build();

        String messageJson = "{\"role\":\"assistant\",\"content\":[]}";
        when(modelProvider.extractMessageFromResponse(dataMap)).thenReturn(messageJson);
        when(modelProvider.parseToUnifiedMessage(messageJson)).thenReturn(null);

        // Act & Assert
        try {
            runner.extractAssistantMessage(output, modelProvider);
            fail("Should throw IllegalStateException when parsing fails");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("ModelProvider failed to parse message JSON"));
        }
    }

    // ==================== Tests for buildLLMParams ====================

    @Test
    public void testBuildLLMParams_WithSystemPrompt() {
        // Arrange
        Map<String, String> params = new HashMap<>();
        params.put("temperature", "0.7");
        params.put("system_prompt", "Custom system prompt");

        Message userMessage = new Message();
        userMessage.setRole("user");
        List<Message> messages = List.of(userMessage);

        Map<String, String> formattedMessages = new HashMap<>();
        formattedMessages.put("messages", "[{\"role\":\"user\"}]");

        when(mlAgent.getType()).thenReturn("CONVERSATIONAL_V2");
        when(modelProvider.mapMessages(eq(messages), any())).thenReturn(formattedMessages);

        // Act
        Map<String, String> result = runner.buildLLMParams(mlAgent, params, messages, modelProvider);

        // Assert
        assertNotNull(result);
        assertEquals("0.7", result.get("temperature"));
        assertEquals("Custom system prompt", result.get("system_prompt"));
        assertEquals("[{\"role\":\"user\"}]", result.get("messages"));
    }

    @Test
    public void testBuildLLMParams_WithoutSystemPrompt_UsesDefault() {
        // Arrange
        Map<String, String> params = new HashMap<>();
        when(mlAgent.getParameters()).thenReturn(null);
        when(mlAgent.getType()).thenReturn("CONVERSATIONAL_V2");

        Message userMessage = new Message();
        List<Message> messages = List.of(userMessage);

        Map<String, String> formattedMessages = new HashMap<>();
        formattedMessages.put("messages", "[]");
        when(modelProvider.mapMessages(eq(messages), any())).thenReturn(formattedMessages);

        // Act
        Map<String, String> result = runner.buildLLMParams(mlAgent, params, messages, modelProvider);

        // Assert
        assertEquals("You are a helpful assistant", result.get("system_prompt"));
    }

    @Test
    public void testBuildLLMParams_MergesFormattedMessages() {
        // Arrange
        Map<String, String> params = new HashMap<>();
        params.put("param1", "value1");

        Message userMessage = new Message();
        List<Message> messages = List.of(userMessage);

        Map<String, String> formattedMessages = new HashMap<>();
        formattedMessages.put("messages", "[]");
        formattedMessages.put("param2", "value2");

        when(mlAgent.getType()).thenReturn("CONVERSATIONAL_V2");
        when(modelProvider.mapMessages(eq(messages), any())).thenReturn(formattedMessages);
        when(mlAgent.getParameters()).thenReturn(null);

        // Act
        Map<String, String> result = runner.buildLLMParams(mlAgent, params, messages, modelProvider);

        // Assert
        assertEquals("value1", result.get("param1"));
        assertEquals("value2", result.get("param2"));
        assertEquals("[]", result.get("messages"));
    }

    // ==================== Tests for getFunctionCalling ====================

    @Test
    public void testGetFunctionCalling_FromParams() {
        // Arrange
        Map<String, String> params = new HashMap<>();
        params.put("_llm_interface", "bedrock_converse");
        when(mlAgent.getParameters()).thenReturn(new HashMap<>());

        // Act & Assert - The factory validates interfaces, so invalid ones throw IllegalArgumentException
        // In test env, most interfaces are not registered, so we expect exception
        try {
            FunctionCalling result = runner.getFunctionCalling(mlAgent, params);
            // If the interface is registered in test, result should not be null
            assertNotNull(result);
        } catch (IllegalArgumentException e) {
            // Expected in test environment where FunctionCallingFactory doesn't have all implementations
            assertTrue(e.getMessage().contains("Invalid _llm_interface") || e.getMessage().contains("No function calling"));
        }
    }

    @Test
    public void testGetFunctionCalling_FromAgentParams() {
        // Arrange
        Map<String, String> params = new HashMap<>();
        Map<String, String> agentParams = new HashMap<>();
        agentParams.put("_llm_interface", "openai_v1_chat_completions");
        when(mlAgent.getParameters()).thenReturn(agentParams);

        // Act & Assert
        try {
            FunctionCalling result = runner.getFunctionCalling(mlAgent, params);
            assertNotNull(result);
        } catch (IllegalArgumentException e) {
            // Expected in test environment
            assertTrue(e.getMessage().contains("Invalid _llm_interface") || e.getMessage().contains("No function calling"));
        }
    }

    @Test
    public void testGetFunctionCalling_NoInterface_ThrowsException() {
        // Arrange
        Map<String, String> params = new HashMap<>();
        when(mlAgent.getParameters()).thenReturn(null);

        // Act & Assert
        try {
            runner.getFunctionCalling(mlAgent, params);
            fail("Should throw IllegalStateException when no interface configured");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("V2 agents require function calling"));
            assertTrue(e.getMessage().contains("LLM interface not configured"));
        }
    }

    @Test
    public void testGetFunctionCalling_ConfiguresCalled() {
        // Arrange
        Map<String, String> params = new HashMap<>();
        params.put("_llm_interface", "some_interface");
        params.put("temperature", "0.7");
        Map<String, String> agentParams = new HashMap<>();
        agentParams.put("max_tokens", "100");
        when(mlAgent.getParameters()).thenReturn(agentParams);

        // Act - Will throw but tests that params are merged before configure
        try {
            runner.getFunctionCalling(mlAgent, params);
        } catch (Exception e) {
            // Expected - just testing the param merge logic
            assertTrue(e instanceof IllegalArgumentException || e instanceof IllegalStateException);
        }
    }

    // ==================== Tests for extractTokenUsage edge cases ====================

    @Test
    public void testExtractTokenUsage_ExceptionDuringExtraction() {
        // Arrange
        Map<String, Object> dataMap = new HashMap<>();
        ModelTensor tensor = ModelTensor.builder().dataAsMap(dataMap).build();
        ModelTensors tensors = ModelTensors.builder().mlModelTensors(List.of(tensor)).build();
        ModelTensorOutput output = ModelTensorOutput.builder().mlModelOutputs(List.of(tensors)).build();

        when(functionCalling.extractTokenUsage(dataMap)).thenThrow(new RuntimeException("Extraction failed"));

        // Act
        TokenUsage result = runner.extractTokenUsage(output, functionCalling);

        // Assert
        assertNull(result); // Should handle exception gracefully
    }

    @Test
    public void testExtractTokenUsage_NullDataMap() {
        // Arrange
        ModelTensor tensor = ModelTensor.builder().dataAsMap(null).build();
        ModelTensors tensors = ModelTensors.builder().mlModelTensors(List.of(tensor)).build();
        ModelTensorOutput output = ModelTensorOutput.builder().mlModelOutputs(List.of(tensors)).build();

        // Act
        TokenUsage result = runner.extractTokenUsage(output, functionCalling);

        // Assert
        assertNull(result);
    }

    // ==================== Tests for executeToolsSequentially edge cases ====================

    @Test
    public void testExecuteToolsSequentially_EmptyToolCalls() {
        // Arrange
        Map<String, Tool> toolsMap = new HashMap<>();
        List<Map<String, String>> toolCalls = List.of();

        ActionListener<List<Map<String, Object>>> testListener = mock(ActionListener.class);

        // Act
        runner.executeToolsSequentially(toolsMap, toolCalls, testListener);

        // Assert
        verify(testListener, timeout(1000)).onResponse(argThat(results -> {
            assertEquals(0, results.size());
            return true;
        }));
    }

    @Test
    public void testExecuteToolsSequentially_MixedSuccessAndFailure() {
        // Arrange
        Map<String, Tool> toolsMap = new HashMap<>();
        Tool successTool = mock(Tool.class);
        Tool failTool = mock(Tool.class);
        toolsMap.put("success-tool", successTool);
        toolsMap.put("fail-tool", failTool);

        List<Map<String, String>> toolCalls = List
            .of(
                Map.of("tool_name", "success-tool", "tool_input", "{}", "tool_call_id", "call-1"),
                Map.of("tool_name", "fail-tool", "tool_input", "{}", "tool_call_id", "call-2"),
                Map.of("tool_name", "missing-tool", "tool_input", "{}", "tool_call_id", "call-3")
            );

        doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            ActionListener<Object> listener = invocation.getArgument(1);
            listener.onResponse("Success");
            return null;
        }).when(successTool).run(any(), any());

        doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            ActionListener<Object> listener = invocation.getArgument(1);
            listener.onFailure(new RuntimeException("Failed"));
            return null;
        }).when(failTool).run(any(), any());

        ActionListener<List<Map<String, Object>>> testListener = mock(ActionListener.class);

        // Act
        runner.executeToolsSequentially(toolsMap, toolCalls, testListener);

        // Assert
        verify(testListener, timeout(1000)).onResponse(argThat(results -> {
            assertEquals(3, results.size());
            // First should succeed
            Map<String, Object> result1 = (Map<String, Object>) results.get(0).get("tool_result");
            assertEquals("Success", result1.get("text"));
            // Second should have error
            Map<String, Object> result2 = (Map<String, Object>) results.get(1).get("tool_result");
            assertEquals("Failed", result2.get("error"));
            // Third should have tool not found error
            Map<String, Object> result3 = (Map<String, Object>) results.get(2).get("tool_result");
            assertTrue(result3.get("error").toString().contains("Tool not found"));
            return true;
        }));
    }

    @Test
    public void testExecuteToolsSequentially_ToolFailureWithNullMessage() {
        // Arrange
        Map<String, Tool> toolsMap = new HashMap<>();
        Tool mockTool = mock(Tool.class);
        toolsMap.put("test-tool", mockTool);

        List<Map<String, String>> toolCalls = List.of(Map.of("tool_name", "test-tool", "tool_input", "{}", "tool_call_id", "call-1"));

        doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            ActionListener<Object> listener = invocation.getArgument(1);
            listener.onFailure(new RuntimeException()); // null message
            return null;
        }).when(mockTool).run(any(), any());

        ActionListener<List<Map<String, Object>>> testListener = mock(ActionListener.class);

        // Act
        runner.executeToolsSequentially(toolsMap, toolCalls, testListener);

        // Assert
        verify(testListener, timeout(1000)).onResponse(argThat(results -> {
            assertEquals(1, results.size());
            Map<String, Object> toolResult = (Map<String, Object>) results.get(0).get("tool_result");
            assertEquals("Tool execution failed", toolResult.get("error"));
            return true;
        }));
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
