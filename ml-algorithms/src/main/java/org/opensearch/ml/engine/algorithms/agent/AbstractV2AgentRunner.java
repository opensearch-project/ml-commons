/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.agent;

import static org.opensearch.ml.common.CommonValue.AGENT_ID_FIELD;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.Settings;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.ml.common.MLAgentType;
import org.opensearch.ml.common.MLMemoryType;
import org.opensearch.ml.common.agent.MLAgent;
import org.opensearch.ml.common.agent.MLToolSpec;
import org.opensearch.ml.common.agent.TokenUsage;
import org.opensearch.ml.common.input.execute.agent.ContentBlock;
import org.opensearch.ml.common.input.execute.agent.ContentType;
import org.opensearch.ml.common.input.execute.agent.Message;
import org.opensearch.ml.common.memory.Memory;
import org.opensearch.ml.common.model.ModelProvider;
import org.opensearch.ml.common.model.ModelProviderFactory;
import org.opensearch.ml.common.output.execute.agent.AgentV2Output;
import org.opensearch.ml.common.output.model.ModelTensorOutput;
import org.opensearch.ml.common.spi.tools.Tool;
import org.opensearch.ml.engine.encryptor.Encryptor;
import org.opensearch.ml.engine.function_calling.FunctionCalling;
import org.opensearch.ml.engine.function_calling.FunctionCallingFactory;
import org.opensearch.remote.metadata.client.SdkClient;
import org.opensearch.transport.TransportChannel;
import org.opensearch.transport.client.Client;

import com.google.gson.Gson;

import lombok.extern.log4j.Log4j2;

/**
 * Abstract base class for V2 agent runners.
 *
 * Provides common functionality for all V2 agent types (CONVERSATIONAL_V2, FLOW_V2, etc.):
 * - Validation (V2 requirements: agentic memory, model field)
 * - Tool execution (sequential with error handling)
 * - Message handling (formatting for LLM, extracting responses)
 * - Output building (standardized AgentV2Output)
 * - Token tracking (usage accumulation)
 * - Saving assistant responses to memory
 *
 * Note: Memory operations (fetch history, save input, apply limits) are handled by MLAgentExecutor.
 * Runner receives complete conversation and focuses on execution logic.
 *
 * Concrete agent implementations extend this class and implement:
 * - executeAgentLogic() - Agent-specific execution (ReAct/Flow/PER/etc.)
 * - getDefaultMaxIterations() - Agent-specific iteration limit
 *
 * Template Method Pattern:
 * - runV2() orchestrates the full execution flow
 * - Concrete methods (final) enforce consistency
 * - Template methods (non-final) allow customization
 * - Abstract methods require agent-specific implementation
 */
@Log4j2
public abstract class AbstractV2AgentRunner implements MLAgentRunner {

    // Dependencies
    protected final Client client;
    protected final Settings settings;
    protected final ClusterService clusterService;
    protected final NamedXContentRegistry xContentRegistry;
    protected final Map<String, Tool.Factory> toolFactories;
    protected final SdkClient sdkClient;
    protected final Encryptor encryptor;

    /**
     * Constructor for V2 agent runners.
     *
     * @param client OpenSearch client for LLM calls
     * @param settings Cluster settings
     * @param clusterService Cluster service
     * @param xContentRegistry XContent registry
     * @param toolFactories Tool factories for creating tools
     * @param sdkClient SDK client for memory operations
     * @param encryptor Encryptor for sensitive data
     */
    protected AbstractV2AgentRunner(
        Client client,
        Settings settings,
        ClusterService clusterService,
        NamedXContentRegistry xContentRegistry,
        Map<String, Tool.Factory> toolFactories,
        SdkClient sdkClient,
        Encryptor encryptor
    ) {
        this.client = client;
        this.settings = settings;
        this.clusterService = clusterService;
        this.xContentRegistry = xContentRegistry;
        this.toolFactories = toolFactories;
        this.sdkClient = sdkClient;
        this.encryptor = encryptor;
    }

    /**
     * Execute agent-specific logic (ReAct loop, flow sequence, PER phases, etc.).
     * This is the core differentiation point between V2 agent types.
     *
     * @param mlAgent Agent configuration
     * @param params Execution parameters
     * @param conversationHistory Full message history (history + new input)
     * @param functionCalling Configured function calling interface
     * @param modelProvider Model provider for message formatting
     * @param listener Listener for AgentLogicResult containing final message and stop reason
     */
    protected abstract void executeAgentLogic(
        MLAgent mlAgent,
        Map<String, String> params,
        List<Message> conversationHistory,
        FunctionCalling functionCalling,
        ModelProvider modelProvider,
        ActionListener<AgentLogicResult> listener
    );

    /**
     * Get agent-specific default maximum iterations.
     * Examples: Chat=5, Flow=1, PER=10
     *
     * @return Default max iterations for this agent type
     */
    protected abstract int getDefaultMaxIterations();

    /**
     * Validate V2 agent requirements.
     * All V2 agents must meet these criteria.
     *
     * @param mlAgent Agent to validate
     * @param memory Memory instance
     * @throws IllegalStateException if validation fails
     */
    protected final void validateV2Agent(MLAgent mlAgent, Memory memory) {
        if (memory == null) {
            throw new IllegalStateException("V2 agents require agentic memory. Memory was not provided by executor.");
        }

        String memoryType = memory.getType();
        if (!MLMemoryType.AGENTIC_MEMORY.name().equalsIgnoreCase(memoryType)
            && !MLMemoryType.REMOTE_AGENTIC_MEMORY.name().equalsIgnoreCase(memoryType)) {
            throw new IllegalStateException(
                String
                    .format(
                        "V2 agents only support agentic_memory or remote_agentic_memory. Found: %s. "
                            + "Please update your agent configuration to use agentic_memory.",
                        memoryType
                    )
            );
        }

        if (!mlAgent.usesUnifiedInterface()) {
            throw new IllegalStateException(
                "V2 agents require the 'model' field to be configured. " + "Use simplified registration with the 'model' block."
            );
        }
    }

    /**
     * Save assistant message to memory.
     *
     * @param memory Memory instance
     * @param message Assistant message to save
     * @param listener Listener for save completion
     */
    protected final void saveAssistantMessage(Memory memory, Message message, ActionListener<Void> listener) {
        memory.saveStructuredMessages(List.of(message), listener);
    }

    /**
     * Execute tools sequentially with error handling.
     * Tools are executed one by one, and errors in individual tools don't stop execution.
     *
     * @param mlAgent Agent configuration
     * @param params Execution parameters
     * @param toolCalls Tool calls to execute
     * @param listener Listener for tool results
     */
    protected final void executeToolsSequentially(
        MLAgent mlAgent,
        Map<String, String> params,
        List<Map<String, String>> toolCalls,
        ActionListener<List<Map<String, Object>>> listener
    ) {
        String agentId = params.get(AGENT_ID_FIELD);
        log.debug("Executing {} tool calls sequentially. agentId={}", toolCalls.size(), agentId);

        // Load tools from ML tool specs
        List<MLToolSpec> mlToolSpecs = AgentUtils.getMlToolSpecs(mlAgent, params);
        Map<String, Tool> toolsMap = new HashMap<>();
        Map<String, MLToolSpec> toolSpecMap = new HashMap<>();

        // Load MCP tools if configured, then create all tools
        AgentUtils.getMcpToolSpecs(mlAgent, client, sdkClient, encryptor, ActionListener.wrap(mcpTools -> {
            mlToolSpecs.addAll(mcpTools);
            AgentUtils.createTools(toolFactories, params, mlToolSpecs, toolsMap, toolSpecMap, mlAgent);

            // Execute tools one by one
            List<Map<String, Object>> results = new ArrayList<>();
            executeToolCallsSequentially(toolsMap, toolCalls, results, 0, listener);
        }, e -> {
            // Continue without MCP tools if they fail
            log.debug("MCP tools failed to load, continuing with backend tools only. agentId={}", agentId, e);
            AgentUtils.createTools(toolFactories, params, mlToolSpecs, toolsMap, toolSpecMap, mlAgent);
            List<Map<String, Object>> results = new ArrayList<>();
            executeToolCallsSequentially(toolsMap, toolCalls, results, 0, listener);
        }));
    }

    /**
     * Recursively execute tool calls one by one.
     * Private helper for executeToolsSequentially.
     */
    private void executeToolCallsSequentially(
        Map<String, Tool> toolsMap,
        List<Map<String, String>> toolCalls,
        List<Map<String, Object>> results,
        int index,
        ActionListener<List<Map<String, Object>>> listener
    ) {
        if (index >= toolCalls.size()) {
            listener.onResponse(results);
            return;
        }

        Map<String, String> toolCall = toolCalls.get(index);
        String toolName = toolCall.get("name");
        String toolInput = toolCall.get("arguments");
        String toolCallId = toolCall.get("id");

        Tool tool = toolsMap.get(toolName);
        if (tool == null) {
            log.error("Tool not found: {}", toolName);
            Map<String, Object> errorResult = new HashMap<>();
            errorResult.put("tool_call_id", toolCallId);
            errorResult.put("tool_result", Map.of("error", "Tool not found: " + toolName));
            results.add(errorResult);
            executeToolCallsSequentially(toolsMap, toolCalls, results, index + 1, listener);
            return;
        }

        Map<String, String> toolParams = new HashMap<>();
        toolParams.put("input", toolInput);

        tool.run(toolParams, ActionListener.wrap(output -> {
            Map<String, Object> result = new HashMap<>();
            result.put("tool_call_id", toolCallId);
            result.put("tool_result", output);
            results.add(result);
            executeToolCallsSequentially(toolsMap, toolCalls, results, index + 1, listener);
        }, e -> {
            log.error("Tool execution failed. tool={}", toolName, e);
            Map<String, Object> errorResult = new HashMap<>();
            errorResult.put("tool_call_id", toolCallId);
            errorResult.put("tool_result", Map.of("error", e.getMessage() != null ? e.getMessage() : "Tool execution failed"));
            results.add(errorResult);
            executeToolCallsSequentially(toolsMap, toolCalls, results, index + 1, listener);
        }));
    }

    /**
     * Build standardized V2 output with metrics.
     *
     * @param assistantMessage Final assistant message
     * @param memoryId Memory/conversation ID
     * @param stopReason Stop reason (end_turn, max_iterations, etc.)
     * @param tokenUsage Accumulated token usage (may be null)
     * @param params Execution parameters
     * @return Standardized AgentV2Output
     */
    protected final AgentV2Output buildStandardizedOutput(
        Message assistantMessage,
        String memoryId,
        String stopReason,
        TokenUsage tokenUsage,
        Map<String, String> params
    ) {
        Map<String, Object> metrics = new HashMap<>();

        if (tokenUsage != null) {
            Map<String, Object> totalUsage = new HashMap<>();
            totalUsage.put("inputTokens", tokenUsage.getInputTokens());
            totalUsage.put("outputTokens", tokenUsage.getOutputTokens());
            totalUsage.put("totalTokens", tokenUsage.getEffectiveTotalTokens());
            if (tokenUsage.getCacheReadInputTokens() != null && tokenUsage.getCacheReadInputTokens() > 0) {
                totalUsage.put("cacheReadInputTokens", tokenUsage.getCacheReadInputTokens());
            }
            if (tokenUsage.getCacheCreationInputTokens() != null && tokenUsage.getCacheCreationInputTokens() > 0) {
                totalUsage.put("cacheCreationInputTokens", tokenUsage.getCacheCreationInputTokens());
            }
            metrics.put("total_usage", totalUsage);
        } else {
            metrics.put("total_usage", new HashMap<>());
        }

        return AgentV2Output.builder().stopReason(stopReason).message(assistantMessage).memoryId(memoryId).metrics(metrics).build();
    }

    /**
     * Extract token usage from LLM response.
     *
     * @param output LLM output
     * @param functionCalling Function calling interface for provider-specific extraction
     * @return TokenUsage or null if extraction fails
     */
    protected final TokenUsage extractTokenUsage(ModelTensorOutput output, FunctionCalling functionCalling) {
        try {
            if (output == null || output.getMlModelOutputs() == null || output.getMlModelOutputs().isEmpty()) {
                return null;
            }

            var tensors = output.getMlModelOutputs().getFirst();
            if (tensors == null || tensors.getMlModelTensors() == null || tensors.getMlModelTensors().isEmpty()) {
                return null;
            }

            var tensor = tensors.getMlModelTensors().getFirst();
            Map<String, ?> dataAsMap = tensor.getDataAsMap();

            if (dataAsMap != null) {
                return functionCalling.extractTokenUsage(dataAsMap);
            }
        } catch (Exception e) {
            log.warn("Failed to extract token usage", e);
        }
        return null;
    }

    /**
     * Build LLM request parameters.
     * Default implementation uses ModelProvider for message formatting.
     * Override if agent needs custom parameter handling.
     *
     * @param mlAgent Agent configuration
     * @param params Execution parameters
     * @param messages Messages to format
     * @param modelProvider Model provider
     * @return LLM request parameters
     */
    protected Map<String, String> buildLLMParams(
        MLAgent mlAgent,
        Map<String, String> params,
        List<Message> messages,
        ModelProvider modelProvider
    ) {
        Map<String, String> llmParams = new HashMap<>(params);
        MLAgentType agentType = MLAgentType.CONVERSATIONAL_V2;
        Map<String, String> formatted = modelProvider.mapMessages(messages, agentType);

        llmParams.putAll(formatted);
        String systemPrompt = getSystemPrompt(params, mlAgent);
        llmParams.put("system_prompt", systemPrompt);

        return llmParams;
    }

    /**
     * Get system prompt with fallback.
     * Default implementation checks params → agent params → default.
     * Override if agent needs custom system prompt logic.
     *
     * @param params Execution parameters
     * @param mlAgent Agent configuration
     * @return System prompt
     */
    protected String getSystemPrompt(Map<String, String> params, MLAgent mlAgent) {
        String systemPrompt = params.get("system_prompt");
        if (systemPrompt == null || systemPrompt.isEmpty()) {
            if (mlAgent.getParameters() != null) {
                systemPrompt = mlAgent.getParameters().get("system_prompt");
            }
        }

        if (systemPrompt == null || systemPrompt.isEmpty()) {
            systemPrompt = "You are a helpful assistant";
        }

        return systemPrompt;
    }

    /**
     * Extract assistant message from LLM response.
     * Delegates to ModelProvider to handle provider-specific response formats.
     * Override if agent needs custom message extraction logic.
     *
     * @param output LLM output
     * @param toolCalls Tool calls (may be null)
     * @param modelProvider Model provider that knows its own response format
     * @return Assistant message
     * @throws IllegalStateException if message cannot be extracted from valid LLM output
     */
    protected Message extractAssistantMessage(ModelTensorOutput output, List<Map<String, String>> toolCalls, ModelProvider modelProvider) {
        if (output == null || output.getMlModelOutputs() == null || output.getMlModelOutputs().isEmpty()) {
            throw new IllegalStateException("LLM output is null or empty - cannot extract assistant message");
        }

        var tensors = output.getMlModelOutputs().getFirst();
        if (tensors == null || tensors.getMlModelTensors() == null || tensors.getMlModelTensors().isEmpty()) {
            throw new IllegalStateException("LLM output tensors are null or empty - cannot extract assistant message");
        }

        var tensor = tensors.getMlModelTensors().getFirst();
        Map<String, ?> dataAsMap = tensor.getDataAsMap();

        if (dataAsMap == null) {
            throw new IllegalStateException("LLM output data map is null - cannot extract assistant message");
        }

        // Delegate to ModelProvider - it knows its own response format
        // First extract the message portion from the full response
        String messageJson = modelProvider.extractMessageFromResponse(dataAsMap);
        if (messageJson == null) {
            throw new IllegalStateException(
                "ModelProvider failed to extract message from response. Provider: "
                    + modelProvider.getClass().getSimpleName()
                    + ". This may indicate an unexpected response format."
            );
        }

        // Then parse the message to unified format
        Message message = modelProvider.parseToUnifiedMessage(messageJson);
        if (message != null) {
            return message;
        }

        // If provider parsing failed, throw with context for debugging
        throw new IllegalStateException(
            "ModelProvider failed to parse message JSON. Provider: "
                + modelProvider.getClass().getSimpleName()
                + ". This may indicate an unexpected message format."
        );
    }

    /**
     * Format tool results as messages.
     * Default implementation uses FunctionCalling.supply().
     * Override if agent needs custom tool result formatting.
     *
     * @param toolResults Tool execution results
     * @param functionCalling Function calling interface
     * @return Messages representing tool results
     */
    protected List<Message> formatToolResults(List<Map<String, Object>> toolResults, FunctionCalling functionCalling) {
        var llmMessages = functionCalling.supply(toolResults);

        List<Message> messages = new ArrayList<>();
        for (var llmMsg : llmMessages) {
            Message message = new Message();
            message.setRole(llmMsg.getRole());

            // Convert content to ContentBlocks
            // LLMMessage.getResponse() provides the formatted text content
            String content = llmMsg.getResponse();
            if (content != null && !content.isEmpty()) {
                ContentBlock contentBlock = new ContentBlock();
                contentBlock.setType(ContentType.TEXT);
                contentBlock.setText(content);
                message.setContent(List.of(contentBlock));
            } else {
                message.setContent(List.of());
            }

            messages.add(message);
        }

        return messages;
    }

    /**
     * Result container for agent logic execution.
     */
    protected static class AgentLogicResult {
        public final Message assistantMessage;
        public final String stopReason;
        public final TokenUsage tokenUsage;

        public AgentLogicResult(Message assistantMessage, String stopReason, TokenUsage tokenUsage) {
            this.assistantMessage = assistantMessage;
            this.stopReason = stopReason;
            this.tokenUsage = tokenUsage;
        }
    }

    protected int getMaxIterations(Map<String, String> params) {
        String maxIterStr = params.get("max_iteration");
        if (maxIterStr != null) {
            try {
                return Integer.parseInt(maxIterStr);
            } catch (NumberFormatException e) {
                log.warn("Invalid max_iteration value: {}", maxIterStr);
            }
        }

        return getDefaultMaxIterations();
    }

    protected FunctionCalling getFunctionCalling(MLAgent mlAgent, Map<String, String> params) {
        String llmInterface = params.get(MLChatAgentRunner.LLM_INTERFACE);
        if (llmInterface == null && mlAgent.getParameters() != null) {
            llmInterface = mlAgent.getParameters().get(MLChatAgentRunner.LLM_INTERFACE);
        }

        if (llmInterface == null) {
            throw new IllegalStateException(
                "V2 agents require function calling. LLM interface not configured. "
                    + "Ensure _llm_interface parameter is set in agent configuration."
            );
        }

        FunctionCalling functionCalling = FunctionCallingFactory.create(llmInterface);
        if (functionCalling == null) {
            throw new IllegalStateException(String.format("No function calling implementation found for interface: %s", llmInterface));
        }

        Map<String, String> configParams = new HashMap<>();
        if (mlAgent.getParameters() != null) {
            configParams.putAll(mlAgent.getParameters());
        }
        configParams.putAll(params);

        functionCalling.configure(configParams);
        return functionCalling;
    }

    /**
     * Main orchestration method for V2 agent execution.
     * This template method coordinates the complete flow:
     * 1. Validate agent
     * 2. Setup function calling and model provider
     * 3. Execute agent-specific logic (abstract method)
     * 4. Save assistant message to memory
     * 5. Build standardized output
     *
     * Note: Memory operations (fetch history, save input) are handled by executor.
     * Runner receives the complete conversation and focuses on execution logic.
     */
    @Override
    public final void runV2(
        MLAgent mlAgent,
        Map<String, String> params,
        ActionListener<Object> listener,
        TransportChannel channel,
        Memory memory,
        List<Message> fullConversation
    ) {
        String agentId = params.get(AGENT_ID_FIELD);
        String tenantId = mlAgent.getTenantId();
        log.info("Starting Agent with {} messages. agentId={}, tenantId={}", fullConversation.size(), agentId, tenantId);

        try {
            // Step 1: Validate V2 requirements
            validateV2Agent(mlAgent, memory);

            // Step 2: Get function calling and model provider
            FunctionCalling functionCalling = getFunctionCalling(mlAgent, params);
            ModelProvider modelProvider = ModelProviderFactory.getProvider(mlAgent.getModel().getModelProvider());

            // Step 3: Execute agent-specific logic (ABSTRACT METHOD)
            executeAgentLogic(mlAgent, params, fullConversation, functionCalling, modelProvider, ActionListener.wrap(result -> {

                // Step 4: Save assistant message to memory
                saveAssistantMessage(memory, result.assistantMessage, ActionListener.wrap(saveV -> {

                    // Step 5: Build and return standardized output
                    AgentV2Output output = buildStandardizedOutput(
                        result.assistantMessage,
                        memory.getId(),
                        result.stopReason,
                        result.tokenUsage,
                        params
                    );

                    listener.onResponse(output);
                }, listener::onFailure));
            }, listener::onFailure));

        } catch (Exception e) {
            log.error("V2 agent execution failed. agentId={}, tenantId={}", agentId, tenantId, e);
            listener.onFailure(e);
        }
    }

    // V1 methods not supported
    @Override
    public void run(MLAgent mlAgent, Map<String, String> params, ActionListener<Object> listener, TransportChannel channel) {
        throw new UnsupportedOperationException("V2 agents require executor-provided memory. Use runV2() instead.");
    }

    @Override
    public void run(MLAgent mlAgent, Map<String, String> params, ActionListener<Object> listener, TransportChannel channel, Memory memory) {
        throw new UnsupportedOperationException("V2 agents require message list. Use runV2() instead.");
    }
}
