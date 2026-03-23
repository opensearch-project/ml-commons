/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.agent;

import static org.opensearch.ml.common.CommonValue.AGENT_ID_FIELD;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.opensearch.action.ActionRequest;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.Settings;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.agent.LLMSpec;
import org.opensearch.ml.common.agent.MLAgent;
import org.opensearch.ml.common.agent.TokenUsage;
import org.opensearch.ml.common.dataset.remote.RemoteInferenceInputDataSet;
import org.opensearch.ml.common.input.execute.agent.Message;
import org.opensearch.ml.common.input.remote.RemoteInferenceMLInput;
import org.opensearch.ml.common.model.ModelProvider;
import org.opensearch.ml.common.output.model.ModelTensorOutput;
import org.opensearch.ml.common.spi.tools.Tool;
import org.opensearch.ml.common.transport.prediction.MLPredictionTaskAction;
import org.opensearch.ml.common.transport.prediction.MLPredictionTaskRequest;
import org.opensearch.ml.engine.encryptor.Encryptor;
import org.opensearch.ml.engine.function_calling.FunctionCalling;
import org.opensearch.remote.metadata.client.SdkClient;
import org.opensearch.transport.client.Client;

import lombok.extern.log4j.Log4j2;

/**
 * V2 Chat Agent Runner - Extends AbstractV2AgentRunner for code reuse.
 *
 * Chat-specific implementation:
 * - ReAct loop: Iterative LLM → tool execution → LLM cycle
 * - Stop conditions: No tool calls (end_turn) or max iterations reached
 * - Default max iterations: 5
 */
@Log4j2
public class MLChatAgentRunnerV2 extends AbstractV2AgentRunner {

    // Chat-specific constants
    private static final String STOP_REASON_MAX_ITERATIONS = "max_iterations";
    private static final String STOP_REASON_END_TURN = "end_turn";

    public MLChatAgentRunnerV2(
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

    @Override
    protected int getDefaultMaxIterations() {
        return 5; // Chat agents default to 5 ReAct iterations
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
        // Execute chat-specific ReAct loop
        executeReActLoop(mlAgent, params, conversationHistory, functionCalling, modelProvider, listener);
    }

    /**
     * Execute ReAct loop with function calling.
     * Sequential tool execution only (Phase 1 - no parallel tools).
     *
     * @param mlAgent the agent configuration
     * @param params execution parameters
     * @param conversationHistory the full conversation history (history + new input)
     * @param functionCalling configured function calling interface
     * @param modelProvider model provider for formatting
     * @param listener listener for AgentLogicResult containing final assistant message and stop reason
     */
    private void executeReActLoop(
        MLAgent mlAgent,
        Map<String, String> params,
        List<Message> conversationHistory,
        FunctionCalling functionCalling,
        ModelProvider modelProvider,
        ActionListener<AgentLogicResult> listener
    ) {
        String agentId = params.get(AGENT_ID_FIELD);
        int maxIterations = getMaxIterations(params);

        log
            .debug(
                "Starting ReAct loop with {} conversation messages. agentId={}, maxIterations={}",
                conversationHistory.size(),
                agentId,
                maxIterations
            );

        // Create mutable list for ReAct iterations (will append tool results)
        List<Message> messages = new ArrayList<>(conversationHistory);
        AtomicInteger iteration = new AtomicInteger(0);
        TokenUsage[] accumulatedTokenUsage = new TokenUsage[1];

        // Execute first LLM call
        executeLLMCall(
            mlAgent,
            params,
            messages,
            modelProvider,
            functionCalling,
            iteration,
            maxIterations,
            accumulatedTokenUsage,
            listener
        );
    }

    /**
     * Execute a single LLM call in the ReAct loop.
     * This method recursively calls itself to continue the ReAct loop until:
     * - No tool calls are returned (end_turn)
     * - Max iterations reached (max_iterations)
     */
    private void executeLLMCall(
        MLAgent mlAgent,
        Map<String, String> params,
        List<Message> messages,
        ModelProvider modelProvider,
        FunctionCalling functionCalling,
        AtomicInteger iteration,
        int maxIterations,
        TokenUsage[] accumulatedTokenUsage,
        ActionListener<AgentLogicResult> listener
    ) {
        String agentId = params.get(AGENT_ID_FIELD);
        String tenantId = mlAgent.getTenantId();
        int currentIteration = iteration.getAndIncrement();

        log.debug("ReAct iteration {}. agentId={}", currentIteration, agentId);

        try {
            // Build LLM request parameters using base class method
            Map<String, String> llmParams = buildLLMParams(mlAgent, params, messages, modelProvider);

            // Execute LLM call
            LLMSpec llmSpec = mlAgent.getLlm();
            ActionRequest request = new MLPredictionTaskRequest(
                llmSpec.getModelId(),
                RemoteInferenceMLInput
                    .builder()
                    .algorithm(FunctionName.REMOTE)
                    .inputDataset(RemoteInferenceInputDataSet.builder().parameters(llmParams).build())
                    .build(),
                null,
                tenantId
            );

            client.execute(MLPredictionTaskAction.INSTANCE, request, ActionListener.wrap(response -> {
                try {
                    ModelTensorOutput output = (ModelTensorOutput) response.getOutput();

                    // Extract and accumulate token usage using base class method
                    TokenUsage currentTokenUsage = extractTokenUsage(output, functionCalling);
                    if (currentTokenUsage != null) {
                        accumulatedTokenUsage[0] = accumulatedTokenUsage[0] == null
                            ? currentTokenUsage
                            : accumulatedTokenUsage[0].addTokens(currentTokenUsage);
                        log
                            .debug(
                                "Token usage for iteration {}: input={}, output={}, total={}",
                                currentIteration,
                                currentTokenUsage.getInputTokens(),
                                currentTokenUsage.getOutputTokens(),
                                currentTokenUsage.getEffectiveTotalTokens()
                            );
                    }

                    // Parse response for tool calls
                    List<Map<String, String>> toolCalls = functionCalling.handle(output, params);

                    // Extract assistant message from response using base class method
                    Message assistantMessage = extractAssistantMessage(output, toolCalls, modelProvider);
                    messages.add(assistantMessage);

                    // Check if we should continue the loop
                    if (toolCalls == null || toolCalls.isEmpty()) {
                        // No tool calls - return final answer
                        log
                            .debug(
                                "ReAct loop complete (no tool calls). agentId={}, iterations={}, totalTokens={}",
                                agentId,
                                currentIteration + 1,
                                accumulatedTokenUsage[0] != null ? accumulatedTokenUsage[0].getEffectiveTotalTokens() : 0
                            );
                        listener.onResponse(new AgentLogicResult(assistantMessage, STOP_REASON_END_TURN, accumulatedTokenUsage[0]));
                        return;
                    }

                    if (currentIteration >= maxIterations - 1) {
                        // Max iterations reached
                        log
                            .warn(
                                "ReAct loop stopped (max iterations). agentId={}, maxIterations={}, totalTokens={}",
                                agentId,
                                maxIterations,
                                accumulatedTokenUsage[0] != null ? accumulatedTokenUsage[0].getEffectiveTotalTokens() : 0
                            );
                        listener.onResponse(new AgentLogicResult(assistantMessage, STOP_REASON_MAX_ITERATIONS, accumulatedTokenUsage[0]));
                        return;
                    }

                    // Execute tools and continue loop using base class method
                    executeToolsSequentially(mlAgent, params, toolCalls, ActionListener.wrap(toolResults -> {
                        // Format tool results as messages using base class method
                        List<Message> toolResultMessages = formatToolResults(toolResults, functionCalling);
                        messages.addAll(toolResultMessages);

                        // Continue ReAct loop
                        executeLLMCall(
                            mlAgent,
                            params,
                            messages,
                            modelProvider,
                            functionCalling,
                            iteration,
                            maxIterations,
                            accumulatedTokenUsage,
                            listener
                        );
                    }, e -> {
                        log.error("Tool execution failed in ReAct loop. agentId={}", agentId, e);
                        listener.onFailure(e);
                    }));

                } catch (Exception e) {
                    log.error("Failed to process LLM response. agentId={}", agentId, e);
                    listener.onFailure(e);
                }
            }, e -> {
                log.error("LLM call failed. agentId={}, modelId={}", agentId, llmSpec.getModelId(), e);
                listener.onFailure(e);
            }));

        } catch (Exception e) {
            log.error("Failed to build LLM request. agentId={}", agentId, e);
            listener.onFailure(e);
        }
    }
}
