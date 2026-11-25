/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.agent;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.opensearch.ml.common.CommonValue.MCP_CONNECTORS_FIELD;
import static org.opensearch.ml.common.CommonValue.MCP_CONNECTOR_ID_FIELD;
import static org.opensearch.ml.common.CommonValue.MCP_SYNC_CLIENT;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.function.BiConsumer;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import org.opensearch.action.ActionRequest;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.agent.LLMSpec;
import org.opensearch.ml.common.agent.MLAgent;
import org.opensearch.ml.common.agent.MLAgentModelSpec;
import org.opensearch.ml.common.agent.MLToolSpec;
import org.opensearch.ml.common.agent.TokenUsage;
import org.opensearch.ml.common.connector.Connector;
import org.opensearch.ml.common.connector.McpConnector;
import org.opensearch.ml.common.input.execute.agent.ContentBlock;
import org.opensearch.ml.common.input.execute.agent.ContentType;
import org.opensearch.ml.common.input.execute.agent.Message;
import org.opensearch.ml.common.memory.Memory;
import org.opensearch.ml.common.model.ModelProvider;
import org.opensearch.ml.common.output.model.ModelTensor;
import org.opensearch.ml.common.output.model.ModelTensorOutput;
import org.opensearch.ml.common.output.model.ModelTensors;
import org.opensearch.ml.common.spi.tools.Tool;
import org.opensearch.ml.common.transport.MLTaskResponse;
import org.opensearch.ml.common.transport.prediction.MLPredictionTaskAction;
import org.opensearch.ml.engine.MLEngineClassLoader;
import org.opensearch.ml.engine.MLStaticMockBase;
import org.opensearch.ml.engine.algorithms.remote.McpConnectorExecutor;
import org.opensearch.ml.engine.encryptor.Encryptor;
import org.opensearch.ml.engine.function_calling.FunctionCalling;
import org.opensearch.ml.engine.tools.McpSseTool;
import org.opensearch.remote.metadata.client.GetDataObjectRequest;
import org.opensearch.remote.metadata.client.GetDataObjectResponse;
import org.opensearch.remote.metadata.client.SdkClient;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.client.Client;

import io.modelcontextprotocol.client.McpSyncClient;

public class MLChatAgentRunnerV2Test extends MLStaticMockBase {

    @Mock
    private Client client;
    private Settings settings;
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
    private LLMSpec llmSpec;
    @Mock
    private ModelProvider modelProvider;
    @Mock
    private FunctionCalling functionCalling;
    @Mock
    private Tool.Factory toolFactory;
    @Mock
    private Tool tool;

    private MLChatAgentRunnerV2 runner;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        settings = Settings.builder().build();
        runner = new MLChatAgentRunnerV2(client, settings, clusterService, xContentRegistry, toolFactories, sdkClient, encryptor);

        // Setup basic agent configuration
        when(mlAgent.getType()).thenReturn("conversational_v2");
        when(mlAgent.getLlm()).thenReturn(llmSpec);
        when(llmSpec.getModelId()).thenReturn("test-model-id");
        when(mlAgent.getTenantId()).thenReturn("test-tenant");

        MLAgentModelSpec modelSpec = new MLAgentModelSpec("model-123", "bedrock", null, null);
        when(mlAgent.getModel()).thenReturn(modelSpec);
    }

    // ==================== Tests for getDefaultMaxIterations ====================

    @Test
    public void testGetDefaultMaxIterations() {
        // Act
        int result = runner.getDefaultMaxIterations();

        // Assert
        assertEquals(5, result);
    }

    // ==================== Tests for executeAgentLogic (ReAct loop) ====================

    @Test
    public void testExecuteAgentLogic_NoToolCalls_EndTurn() {
        // Arrange
        Map<String, String> params = new HashMap<>();
        params.put("agent_id", "test-agent");

        List<Message> conversationHistory = createConversationHistory();

        // Mock LLM response with no tool calls (end_turn)
        mockLLMResponseWithNoTools();

        // Mock ModelProvider
        when(modelProvider.mapMessages(anyList(), any())).thenReturn(Map.of("body", "test body"));

        ActionListener<AbstractV2AgentRunner.AgentLogicResult> listener = mock(ActionListener.class);

        // Act
        runner.executeAgentLogic(mlAgent, params, conversationHistory, functionCalling, modelProvider, listener);

        // Assert
        ArgumentCaptor<AbstractV2AgentRunner.AgentLogicResult> resultCaptor = ArgumentCaptor
            .forClass(AbstractV2AgentRunner.AgentLogicResult.class);
        verify(listener, timeout(5000)).onResponse(resultCaptor.capture());

        AbstractV2AgentRunner.AgentLogicResult result = resultCaptor.getValue();
        assertNotNull(result);
        assertEquals("end_turn", result.stopReason);
        assertNotNull(result.assistantMessage);
        assertEquals("assistant", result.assistantMessage.getRole());
    }

    // Note: Multi-step ReAct loop tests (tool call -> LLM -> tool call -> LLM) are complex
    // to test in unit tests due to recursive async nature. These scenarios are better
    // covered by integration tests. The basic ReAct flow is covered by the simple tests above.

    @Test
    public void testExecuteAgentLogic_TokenUsageAccumulation() {
        // Arrange
        Map<String, String> params = new HashMap<>();
        params.put("agent_id", "test-agent");

        List<Message> conversationHistory = createConversationHistory();

        // Mock LLM response with token usage
        TokenUsage tokenUsage = TokenUsage.builder().inputTokens(100L).outputTokens(50L).totalTokens(150L).build();
        mockLLMResponseWithTokenUsage(tokenUsage);

        // Mock ModelProvider
        when(modelProvider.mapMessages(anyList(), any())).thenReturn(Map.of("body", "test body"));

        ActionListener<AbstractV2AgentRunner.AgentLogicResult> listener = mock(ActionListener.class);

        // Act
        runner.executeAgentLogic(mlAgent, params, conversationHistory, functionCalling, modelProvider, listener);

        // Assert
        ArgumentCaptor<AbstractV2AgentRunner.AgentLogicResult> resultCaptor = ArgumentCaptor
            .forClass(AbstractV2AgentRunner.AgentLogicResult.class);
        verify(listener, timeout(5000)).onResponse(resultCaptor.capture());

        AbstractV2AgentRunner.AgentLogicResult result = resultCaptor.getValue();
        assertNotNull(result);
        assertNotNull(result.tokenUsage);
        assertEquals(Long.valueOf(100L), result.tokenUsage.getInputTokens());
        assertEquals(Long.valueOf(50L), result.tokenUsage.getOutputTokens());
    }

    @Test
    public void testExecuteAgentLogic_LLMCallFailure() {
        // Arrange
        Map<String, String> params = new HashMap<>();
        params.put("agent_id", "test-agent");

        List<Message> conversationHistory = createConversationHistory();

        // Mock LLM failure
        Exception expectedException = new RuntimeException("LLM call failed");
        doAnswer(invocation -> {
            ActionListener<MLTaskResponse> listener = invocation.getArgument(2);
            listener.onFailure(expectedException);
            return null;
        }).when(client).execute(eq(MLPredictionTaskAction.INSTANCE), any(ActionRequest.class), any());

        // Mock ModelProvider
        when(modelProvider.mapMessages(anyList(), any())).thenReturn(Map.of("body", "test body"));

        ActionListener<AbstractV2AgentRunner.AgentLogicResult> listener = mock(ActionListener.class);

        // Act
        runner.executeAgentLogic(mlAgent, params, conversationHistory, functionCalling, modelProvider, listener);

        // Assert
        verify(listener, timeout(5000)).onFailure(any(Exception.class));
    }

    @Test
    public void testExecuteAgentLogic_TracksToolInteractions() {
        // Arrange
        Map<String, String> params = new HashMap<>();
        params.put("agent_id", "test-agent");

        List<Message> conversationHistory = createConversationHistory();

        // Setup tool
        MLToolSpec toolSpec = new MLToolSpec("test-tool", "test-tool", "test tool", Map.of(), Map.of(), false, Map.of(), null, null);
        when(mlAgent.getTools()).thenReturn(List.of(toolSpec));
        when(toolFactories.get("test-tool")).thenReturn(toolFactory);
        when(toolFactory.create(anyMap())).thenReturn(tool);

        // Mock tool execution
        doAnswer(invocation -> {
            ActionListener<String> listener = invocation.getArgument(1);
            listener.onResponse("Tool output");
            return null;
        }).when(tool).run(anyMap(), any());

        // Mock two LLM calls: first with tool call, second without (final answer)
        mockLLMResponseWithToolCallThenFinalAnswer();

        // Mock ModelProvider
        when(modelProvider.mapMessages(anyList(), any())).thenReturn(Map.of("body", "test body"));
        when(modelProvider.extractMessageFromResponse(any())).thenReturn("{\"role\":\"assistant\",\"content\":[]}");

        ActionListener<AbstractV2AgentRunner.AgentLogicResult> listener = mock(ActionListener.class);

        // Act
        runner.executeAgentLogic(mlAgent, params, conversationHistory, functionCalling, modelProvider, listener);

        // Assert
        ArgumentCaptor<AbstractV2AgentRunner.AgentLogicResult> resultCaptor = ArgumentCaptor
            .forClass(AbstractV2AgentRunner.AgentLogicResult.class);
        verify(listener, timeout(5000)).onResponse(resultCaptor.capture());

        AbstractV2AgentRunner.AgentLogicResult result = resultCaptor.getValue();
        assertNotNull(result);
        assertNotNull(result.toolInteractionMessages);
        assertTrue(result.toolInteractionMessages.size() > 0); // Should have tracked tool interactions
    }

    // ==================== Tests for MCP client cleanup ====================

    @Test
    public void testExecuteAgentLogic_ClosesMcpClientAfterSuccess() {
        Map<String, String> params = new HashMap<>();
        params.put("agent_id", "test-agent");
        McpSyncClient mcpSyncClient = mock(McpSyncClient.class);
        mockLLMResponseWithNoTools();
        when(modelProvider.mapMessages(anyList(), any())).thenReturn(Map.of("body", "test body"));
        ActionListener<AbstractV2AgentRunner.AgentLogicResult> listener = mock(ActionListener.class);

        try (
            MockedStatic<Connector> connStatic = mockStatic(Connector.class);
            MockedStatic<MLEngineClassLoader> loadStatic = mockStatic(MLEngineClassLoader.class)
        ) {
            mockMcpConnector(connStatic, loadStatic, mcpSyncClient);
            runner.executeAgentLogic(mlAgent, params, createConversationHistory(), functionCalling, modelProvider, listener);

            verify(listener, timeout(5000)).onResponse(any());
            verify(mcpSyncClient, timeout(5000)).closeGracefully();
        }
    }

    @Test
    public void testExecuteAgentLogic_ClosesMcpClientAfterLLMCallFailure() {
        Map<String, String> params = new HashMap<>();
        params.put("agent_id", "test-agent");
        McpSyncClient mcpSyncClient = mock(McpSyncClient.class);
        doAnswer(invocation -> {
            ActionListener<MLTaskResponse> llmListener = invocation.getArgument(2);
            llmListener.onFailure(new RuntimeException("LLM call failed"));
            return null;
        }).when(client).execute(eq(MLPredictionTaskAction.INSTANCE), any(ActionRequest.class), any());
        when(modelProvider.mapMessages(anyList(), any())).thenReturn(Map.of("body", "test body"));
        ActionListener<AbstractV2AgentRunner.AgentLogicResult> listener = mock(ActionListener.class);

        try (
            MockedStatic<Connector> connStatic = mockStatic(Connector.class);
            MockedStatic<MLEngineClassLoader> loadStatic = mockStatic(MLEngineClassLoader.class)
        ) {
            mockMcpConnector(connStatic, loadStatic, mcpSyncClient);
            runner.executeAgentLogic(mlAgent, params, createConversationHistory(), functionCalling, modelProvider, listener);

            verify(listener, timeout(5000)).onFailure(any(Exception.class));
            verify(mcpSyncClient, timeout(5000)).closeGracefully();
        }
    }

    // ==================== Helper Methods ====================

    /**
     * Configures the agent with one MCP connector whose executor returns a single tool backed by the given client.
     */
    private void mockMcpConnector(
        MockedStatic<Connector> connStatic,
        MockedStatic<MLEngineClassLoader> loadStatic,
        McpSyncClient mcpSyncClient
    ) {
        when(mlAgent.getParameters()).thenReturn(Map.of(MCP_CONNECTORS_FIELD, "[{\"" + MCP_CONNECTOR_ID_FIELD + "\":\"c1\"}]"));
        ThreadPool threadPool = mock(ThreadPool.class);
        when(client.threadPool()).thenReturn(threadPool);
        when(threadPool.getThreadContext()).thenReturn(new ThreadContext(Settings.builder().build()));
        when(sdkClient.getDataObjectAsync(any(GetDataObjectRequest.class))).thenAnswer(inv -> {
            String json = "{\"_index\":\"i\",\"_id\":\"j\",\"found\":true,\"_source\":{}}";
            CompletionStage<GetDataObjectResponse> stage = mock(CompletionStage.class);
            when(stage.whenComplete(any())).thenAnswer(cbInv -> {
                try (XContentParser parser = XContentType.JSON.xContent().createParser(NamedXContentRegistry.EMPTY, null, json)) {
                    GetDataObjectResponse resp = mock(GetDataObjectResponse.class);
                    when(resp.parser()).thenReturn(parser);
                    BiConsumer<GetDataObjectResponse, Throwable> cb = cbInv.getArgument(0);
                    cb.accept(resp, null);
                }
                return stage;
            });
            return stage;
        });

        McpConnector mockConnector = mock(McpConnector.class);
        when(mockConnector.getProtocol()).thenReturn("mcp_sse");
        doAnswer(invocation -> {
            ActionListener<Boolean> decryptListener = invocation.getArgument(3);
            decryptListener.onResponse(true);
            return null;
        }).when(mockConnector).decrypt(anyString(), any(), anyString(), any(ActionListener.class));
        connStatic.when(() -> Connector.createConnector(any(XContentParser.class))).thenReturn(mockConnector);

        MLToolSpec mcpToolSpec = MLToolSpec.builder().name("mcp_tool").type(McpSseTool.TYPE).build();
        mcpToolSpec.addRuntimeResource(MCP_SYNC_CLIENT, mcpSyncClient);
        McpConnectorExecutor exec = mock(McpConnectorExecutor.class);
        when(exec.getMcpToolSpecs()).thenReturn(List.of(mcpToolSpec));
        loadStatic.when(() -> MLEngineClassLoader.initInstance(anyString(), any(), any())).thenReturn(exec);
    }

    private List<Message> createConversationHistory() {
        List<Message> messages = new ArrayList<>();

        Message userMessage = new Message();
        userMessage.setRole("user");
        ContentBlock userBlock = new ContentBlock();
        userBlock.setType(ContentType.TEXT);
        userBlock.setText("What is the weather?");
        userMessage.setContent(List.of(userBlock));

        messages.add(userMessage);
        return messages;
    }

    private void mockLLMResponseWithNoTools() {
        doAnswer(invocation -> {
            ActionListener<MLTaskResponse> listener = invocation.getArgument(2);

            // Create assistant message
            Message assistantMessage = new Message();
            assistantMessage.setRole("assistant");
            ContentBlock block = new ContentBlock();
            block.setType(ContentType.TEXT);
            block.setText("The weather is sunny.");
            assistantMessage.setContent(List.of(block));

            // Mock model provider to return this message
            String messageJson = "{\"role\":\"assistant\",\"content\":[{\"text\":\"The weather is sunny.\"}]}";
            when(modelProvider.extractMessageFromResponse(any())).thenReturn(messageJson);
            when(modelProvider.parseToUnifiedMessage(messageJson)).thenReturn(assistantMessage);

            // Mock function calling to return no tool calls
            when(functionCalling.handle(any(ModelTensorOutput.class), anyMap())).thenReturn(null);

            ModelTensor tensor = ModelTensor.builder().dataAsMap(Map.of("response", "test")).build();
            ModelTensors tensors = ModelTensors.builder().mlModelTensors(List.of(tensor)).build();
            ModelTensorOutput output = ModelTensorOutput.builder().mlModelOutputs(List.of(tensors)).build();
            MLTaskResponse response = MLTaskResponse.builder().output(output).build();

            listener.onResponse(response);
            return null;
        }).when(client).execute(eq(MLPredictionTaskAction.INSTANCE), any(ActionRequest.class), any());
    }

    private void mockLLMResponseWithToolCall() {
        doAnswer(invocation -> {
            ActionListener<MLTaskResponse> listener = invocation.getArgument(2);

            // Create assistant message with tool call
            Message assistantMessage = new Message();
            assistantMessage.setRole("assistant");
            ContentBlock block = new ContentBlock();
            block.setType(ContentType.TEXT);
            block.setText("Let me check the weather.");
            assistantMessage.setContent(List.of(block));

            // Mock model provider
            String messageJson = "{\"role\":\"assistant\",\"content\":[{\"text\":\"Let me check the weather.\"}]}";
            when(modelProvider.extractMessageFromResponse(any())).thenReturn(messageJson);
            when(modelProvider.parseToUnifiedMessage(messageJson)).thenReturn(assistantMessage);

            // Mock function calling to return tool call
            List<Map<String, String>> toolCalls = List.of(Map.of("id", "call-123", "name", "test-tool", "arguments", "{}"));
            when(functionCalling.handle(any(ModelTensorOutput.class), anyMap())).thenReturn(toolCalls);

            ModelTensor tensor = ModelTensor.builder().dataAsMap(Map.of("response", "test")).build();
            ModelTensors tensors = ModelTensors.builder().mlModelTensors(List.of(tensor)).build();
            ModelTensorOutput output = ModelTensorOutput.builder().mlModelOutputs(List.of(tensors)).build();
            MLTaskResponse response = MLTaskResponse.builder().output(output).build();

            listener.onResponse(response);
            return null;
        }).when(client).execute(eq(MLPredictionTaskAction.INSTANCE), any(ActionRequest.class), any());
    }

    private void mockLLMResponseWithTokenUsage(TokenUsage tokenUsage) {
        doAnswer(invocation -> {
            ActionListener<MLTaskResponse> listener = invocation.getArgument(2);

            Message assistantMessage = new Message();
            assistantMessage.setRole("assistant");
            ContentBlock block = new ContentBlock();
            block.setType(ContentType.TEXT);
            block.setText("Response with token usage");
            assistantMessage.setContent(List.of(block));

            String messageJson = "{\"role\":\"assistant\",\"content\":[{\"text\":\"Response with token usage\"}]}";
            when(modelProvider.extractMessageFromResponse(any())).thenReturn(messageJson);
            when(modelProvider.parseToUnifiedMessage(messageJson)).thenReturn(assistantMessage);

            // Mock function calling
            when(functionCalling.handle(any(ModelTensorOutput.class), anyMap())).thenReturn(null);
            when(functionCalling.extractTokenUsage(anyMap())).thenReturn(tokenUsage);

            ModelTensor tensor = ModelTensor.builder().dataAsMap(Map.of("response", "test")).build();
            ModelTensors tensors = ModelTensors.builder().mlModelTensors(List.of(tensor)).build();
            ModelTensorOutput output = ModelTensorOutput.builder().mlModelOutputs(List.of(tensors)).build();
            MLTaskResponse response = MLTaskResponse.builder().output(output).build();

            listener.onResponse(response);
            return null;
        }).when(client).execute(eq(MLPredictionTaskAction.INSTANCE), any(ActionRequest.class), any());
    }

    private void mockLLMResponseWithToolCallThenFinalAnswer() {
        final boolean[] firstCall = { true };

        doAnswer(invocation -> {
            ActionListener<MLTaskResponse> listener = invocation.getArgument(2);

            if (firstCall[0]) {
                // First call: return tool call
                firstCall[0] = false;

                Message assistantMessage = new Message();
                assistantMessage.setRole("assistant");
                ContentBlock block = new ContentBlock();
                block.setType(ContentType.TEXT);
                block.setText("Let me check that.");
                assistantMessage.setContent(List.of(block));

                String messageJson = "{\"role\":\"assistant\",\"content\":[{\"toolUse\":{\"name\":\"test-tool\"}}]}";
                when(modelProvider.extractMessageFromResponse(any())).thenReturn(messageJson);
                when(modelProvider.parseToUnifiedMessage(messageJson)).thenReturn(assistantMessage);

                // Mock function calling to return tool call
                List<Map<String, String>> toolCalls = List
                    .of(Map.of("tool_name", "test-tool", "tool_input", "{}", "tool_call_id", "call-1"));
                when(functionCalling.handle(any(ModelTensorOutput.class), anyMap())).thenReturn(toolCalls);

                // Mock supply for tool results
                org.opensearch.ml.engine.function_calling.LLMMessage llmMsg = mock(
                    org.opensearch.ml.engine.function_calling.LLMMessage.class
                );
                when(llmMsg.getRole()).thenReturn("user");
                when(llmMsg.getResponse()).thenReturn("{\"role\":\"user\",\"content\":[{\"toolResult\":{}}]}");
                when(functionCalling.supply(anyList())).thenReturn(List.of(llmMsg));

                Message toolResultMsg = new Message();
                toolResultMsg.setRole("user");
                when(modelProvider.parseToUnifiedMessage("{\"role\":\"user\",\"content\":[{\"toolResult\":{}}]}"))
                    .thenReturn(toolResultMsg);

                ModelTensor tensor = ModelTensor.builder().dataAsMap(Map.of("response", "test")).build();
                ModelTensors tensors = ModelTensors.builder().mlModelTensors(List.of(tensor)).build();
                ModelTensorOutput output = ModelTensorOutput.builder().mlModelOutputs(List.of(tensors)).build();
                MLTaskResponse response = MLTaskResponse.builder().output(output).build();

                listener.onResponse(response);
            } else {
                // Second call: return final answer with no tool calls
                Message assistantMessage = new Message();
                assistantMessage.setRole("assistant");
                ContentBlock block = new ContentBlock();
                block.setType(ContentType.TEXT);
                block.setText("The answer is...");
                assistantMessage.setContent(List.of(block));

                String messageJson = "{\"role\":\"assistant\",\"content\":[{\"text\":\"The answer is...\"}]}";
                when(modelProvider.extractMessageFromResponse(any())).thenReturn(messageJson);
                when(modelProvider.parseToUnifiedMessage(messageJson)).thenReturn(assistantMessage);

                // No tool calls this time
                when(functionCalling.handle(any(ModelTensorOutput.class), anyMap())).thenReturn(null);

                ModelTensor tensor = ModelTensor.builder().dataAsMap(Map.of("response", "test")).build();
                ModelTensors tensors = ModelTensors.builder().mlModelTensors(List.of(tensor)).build();
                ModelTensorOutput output = ModelTensorOutput.builder().mlModelOutputs(List.of(tensors)).build();
                MLTaskResponse response = MLTaskResponse.builder().output(output).build();

                listener.onResponse(response);
            }
            return null;
        }).when(client).execute(eq(MLPredictionTaskAction.INSTANCE), any(ActionRequest.class), any());
    }
}
