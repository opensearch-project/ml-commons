/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.agent;

import static org.opensearch.ml.common.CommonValue.AGENT_ID_LOG_FIELD;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.opensearch.OpenSearchStatusException;
import org.opensearch.action.ActionRequest;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.Settings;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.agent.LLMSpec;
import org.opensearch.ml.common.agent.MLAgent;
import org.opensearch.ml.common.agent.MLToolSpec;
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
        String agentId = params.get(AGENT_ID_LOG_FIELD);
        // Completes the run at most once, whichever path reports first.
        ActionListener<AgentLogicResult> runListener = ActionListener.notifyOnce(listener);
        int maxIterations = getMaxIterations(params);
        // Create mutable list for ReAct iterations (will append tool results)
        List<Message> messages = new ArrayList<>(conversationHistory);
        AtomicInteger iteration = new AtomicInteger(0);
        TokenUsage[] accumulatedTokenUsage = new TokenUsage[1];
        List<String> toolInteractionJsonList = new ArrayList<>(); // Track tool interaction JSON strings for persistence (like V1)

        // Create tools once and reuse across all ReAct iterations (performance optimization)
        // Load ML tools first
        List<MLToolSpec> mlToolSpecs = AgentUtils.getMlToolSpecs(mlAgent, params);
        Map<String, Tool> toolsMap = new HashMap<>();
        Map<String, MLToolSpec> toolSpecMap = new HashMap<>();

        // Load MCP tools asynchronously, then create all tools. The result is null if MCP loading itself failed.
        Consumer<AgentUtils.McpToolLoadResult> startLoop = mcpResult -> {
            if (mcpResult != null) {
                mlToolSpecs.addAll(mcpResult.getToolSpecs());
            }
            try {
                AgentUtils.createTools(toolFactories, params, mlToolSpecs, toolsMap, toolSpecMap, mlAgent);
            } catch (Exception ex) {
                log.warn("Failed to create some tools, continuing with available tools. agentId={}", agentId, ex);
            }
            // Checked once tools are created: a configured tool that couldn't be created isn't another tool.
            Exception failure = mcpResult == null ? null : mcpLoadFailure(mcpResult, toolsMap.isEmpty(), agentId);
            if (failure != null) {
                runListener.onFailure(failure);
                return;
            }

            // Execute first LLM call with pre-created tools
            executeLLMCall(
                mlAgent,
                params,
                messages,
                modelProvider,
                functionCalling,
                iteration,
                maxIterations,
                accumulatedTokenUsage,
                toolsMap,
                toolInteractionJsonList,
                runListener
            );
        };
        // Not ActionListener.wrap: an exception from the loop would reach onFailure and call the LLM again.
        loadMcpTools(mlAgent, new ActionListener<>() {
            @Override
            public void onResponse(AgentUtils.McpToolLoadResult result) {
                runLoop(result);
            }

            @Override
            public void onFailure(Exception e) {
                // Not expected: per-connector failures arrive in the result. Kept as before.
                log.warn("MCP tools failed to load, continuing with backend tools only. agentId={}", agentId, e);
                runLoop(null);
            }

            // This runs inside the MCP loader's callback: a throw that escaped would go back to the loader,
            // which can't complete the run, so the request would hang. Throwable, not Exception: an Error
            // (e.g. NoClassDefFoundError from a tool factory) would escape the same way.
            private void runLoop(AgentUtils.McpToolLoadResult result) {
                try {
                    startLoop.accept(result);
                } catch (Throwable t) {
                    log.error("Failed to run the agent loop. agentId={}", agentId, t);
                    runListener.onFailure(t instanceof Exception e ? e : new RuntimeException("Failed to run the agent loop", t));
                }
            }
        });
    }

    /** Loads the agent's MCP tools, reporting per-connector failures. Protected so tests can stub it. */
    protected void loadMcpTools(MLAgent mlAgent, ActionListener<AgentUtils.McpToolLoadResult> listener) {
        AgentUtils.getMcpToolSpecsWithFailures(mlAgent, client, sdkClient, encryptor, listener);
    }

    /**
     * Some connectors failing is tolerated, as before. If every connector failed and the agent has
     * no other tools, the run fails instead of letting the model answer with no tools: with the failures'
     * shared 4xx status when each is a client or configuration error (such as a deleted connector), 400 if
     * their 4xx statuses differ, else 502.
     *
     * @return the exception to fail the run with, or null to continue
     */
    Exception mcpLoadFailure(AgentUtils.McpToolLoadResult result, boolean noOtherTools, String agentId) {
        Map<String, AgentUtils.McpConnectorFailure> failed = result.getFailedConnectors();
        if (failed.isEmpty()) {
            return null;
        }
        String failures = describeFailures(failed, true);
        if (result.allConnectorsFailed() && noOtherTools) {
            log
                .error(
                    "No MCP connector could be loaded and the agent has no other tools; failing the run. agentId={}, {}",
                    agentId,
                    failures
                );
            // A missing or non-MCP connector keeps its message, which names only the connector id. Causes from the
            // remote MCP call are reduced to their type and status: they can name internal hosts. The log above has them.
            return new OpenSearchStatusException(
                "None of the agent's MCP connectors could be loaded, and it has no other tools. Failed connectors: "
                    + describeFailures(failed, false),
                runFailureStatus(failed.values())
            );
        }
        log
            .warn(
                "Some MCP connectors failed to load; continuing with the tools that did. agentId={}, failed {} of {}: {}",
                agentId,
                result.getFailedCount(),
                result.getConfiguredConnectors(),
                failures
            );
        return null;
    }

    /** The failures' status if they all share one 4xx status, 400 if they are all other 4xx, else 502. */
    private static RestStatus runFailureStatus(Collection<AgentUtils.McpConnectorFailure> failures) {
        if (!failures.stream().allMatch(AgentUtils.McpConnectorFailure::isClientError)) {
            return RestStatus.BAD_GATEWAY;
        }
        Set<RestStatus> statuses = failures.stream().map(AgentUtils.McpConnectorFailure::getStatus).collect(Collectors.toSet());
        return statuses.size() == 1 ? statuses.iterator().next() : RestStatus.BAD_REQUEST;
    }

    /**
     * "id (cause)" per failed connector, sorted by id.
     *
     * @param withMessages true for the full "ExceptionType: message" cause, false for what the caller may see: the
     *                     message of a missing or non-MCP connector, else the exception type (and status for a 4xx)
     */
    private static String describeFailures(Map<String, AgentUtils.McpConnectorFailure> failedConnectors, boolean withMessages) {
        return new TreeMap<>(failedConnectors)
            .entrySet()
            .stream()
            .map(entry -> entry.getKey() + " (" + describeFailure(entry.getValue(), withMessages) + ")")
            .collect(Collectors.joining(", "));
    }

    private static String describeFailure(AgentUtils.McpConnectorFailure failure, boolean withMessage) {
        if (withMessage) {
            return failure.toString();
        }
        if (failure.isMessageReturnable()) {
            return failure.getMessage() + ", " + failure.getStatus().getStatus();
        }
        return failure.isClientError() ? failure.getType() + ", " + failure.getStatus().getStatus() : failure.getType();
    }

    /**
     * Execute a single LLM call in the ReAct loop.
     * This method recursively calls itself to continue the ReAct loop until:
     * - No tool calls are returned (end_turn)
     * - Max iterations reached (max_iterations)
     *
     * @param toolsMap Pre-created tools to reuse across iterations (performance optimization)
     * @param toolInteractionJsonList List to accumulate tool interaction JSON strings for persistence (like V1)
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
        Map<String, Tool> toolsMap,
        List<String> toolInteractionJsonList,
        ActionListener<AgentLogicResult> listener
    ) {
        String agentId = params.get(AGENT_ID_LOG_FIELD);
        String tenantId = mlAgent.getTenantId();
        int currentIteration = iteration.getAndIncrement();

        try {
            // Build LLM request parameters using base class method
            Map<String, String> llmParams = buildLLMParams(mlAgent, params, messages, modelProvider);

            // Add tools to LLM params for function calling (populates _tools parameter for connector template)
            if (!toolsMap.isEmpty()) {
                // Re-configure functionCalling with llmParams to ensure TOOL_TEMPLATE is set
                // This is needed because addToolsToPrompt checks for TOOL_TEMPLATE to determine
                // whether to use function calling format or plain text format
                functionCalling.configure(llmParams);

                List<String> toolNames = new ArrayList<>(toolsMap.keySet());
                AgentUtils.addToolsToFunctionCalling(toolsMap, llmParams, toolNames, "");
            }

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
                    }

                    // Parse response for tool calls
                    List<Map<String, String>> toolCalls = functionCalling.handle(output, params);

                    // Extract assistant message from response using base class method
                    Message assistantMessage = extractAssistantMessage(output, modelProvider);
                    messages.add(assistantMessage);

                    // Check if we should continue the loop
                    if (toolCalls == null || toolCalls.isEmpty()) {
                        // No tool calls - return final answer
                        // Parse tool interaction JSON strings to Messages for persistence
                        List<Message> toolInteractionMessages = toolInteractionJsonList.isEmpty()
                            ? null
                            : parseToolInteractionsForPersistence(toolInteractionJsonList, modelProvider);

                        listener
                            .onResponse(
                                new AgentLogicResult(
                                    assistantMessage,
                                    STOP_REASON_END_TURN,
                                    accumulatedTokenUsage[0],
                                    toolInteractionMessages
                                )
                            );
                        return;
                    }

                    // Track assistant message JSON with tool calls for persistence (like V1)
                    // Extract message JSON from LLM response
                    try {
                        var tensor = output.getMlModelOutputs().getFirst().getMlModelTensors().getFirst();
                        Map<String, ?> dataAsMap = tensor.getDataAsMap();
                        String messageJson = modelProvider.extractMessageFromResponse(dataAsMap);
                        if (messageJson != null) {
                            toolInteractionJsonList.add(messageJson);
                        }
                    } catch (Exception e) {
                        log.warn("Failed to extract message JSON for persistence. agentId={}", agentId, e);
                    }

                    if (currentIteration >= maxIterations - 1) {
                        // Max iterations reached
                        // Parse tool interaction JSON strings to Messages for persistence
                        List<Message> toolInteractionMessages = toolInteractionJsonList.isEmpty()
                            ? null
                            : parseToolInteractionsForPersistence(toolInteractionJsonList, modelProvider);

                        log
                            .warn(
                                "ReAct loop stopped (max iterations). agentId={}, maxIterations={}, totalTokens={}",
                                agentId,
                                maxIterations,
                                accumulatedTokenUsage[0] != null ? accumulatedTokenUsage[0].getEffectiveTotalTokens() : 0
                            );
                        listener
                            .onResponse(
                                new AgentLogicResult(
                                    assistantMessage,
                                    STOP_REASON_MAX_ITERATIONS,
                                    accumulatedTokenUsage[0],
                                    toolInteractionMessages
                                )
                            );
                        return;
                    }

                    // Execute tools and continue loop using base class method with pre-created tools
                    executeToolsSequentially(toolsMap, toolCalls, ActionListener.wrap(toolResults -> {
                        // Call supply() once to get LLM-formatted messages
                        var llmMessages = functionCalling.supply(toolResults);

                        // Store raw JSON strings for persistence (before parsing)
                        for (var llmMsg : llmMessages) {
                            String messageJson = llmMsg.getResponse();
                            if (messageJson != null && !messageJson.isEmpty()) {
                                toolInteractionJsonList.add(messageJson);
                            }
                        }

                        // Format tool results as parsed Messages for conversation (reuse llmMessages to avoid duplicate supply() call)
                        List<Message> toolResultMessages = formatToolResults(llmMessages, modelProvider);
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
                            toolsMap,
                            toolInteractionJsonList,
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
