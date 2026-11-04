/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.agent;

import static org.opensearch.ml.common.CommonValue.AGENT_ID_LOG_FIELD;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
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
import org.opensearch.ml.common.agent.MLToolSpec;
import org.opensearch.ml.common.agent.TokenUsage;
import org.opensearch.ml.common.dataset.remote.RemoteInferenceInputDataSet;
import org.opensearch.ml.common.input.execute.agent.Message;
import org.opensearch.ml.common.input.execute.agent.ToolCall;
import org.opensearch.ml.common.input.remote.RemoteInferenceMLInput;
import org.opensearch.ml.common.model.ModelProvider;
import org.opensearch.ml.common.output.model.ModelTensorOutput;
import org.opensearch.ml.common.spi.tools.Tool;
import org.opensearch.ml.common.transport.prediction.MLPredictionTaskAction;
import org.opensearch.ml.common.transport.prediction.MLPredictionTaskRequest;
import org.opensearch.ml.engine.encryptor.Encryptor;
import org.opensearch.ml.engine.function_calling.FunctionCalling;
import org.opensearch.ml.engine.tools.selection.DynamicToolSelector;
import org.opensearch.ml.engine.tools.selection.SearchToolsTool;
import org.opensearch.ml.engine.tools.selection.ToolEmbeddingStore;
import org.opensearch.ml.engine.tools.selection.ToolSelectionResult;
import org.opensearch.ml.engine.tools.selection.ToolSelectionSettings;
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

    private final DynamicToolSelector dynamicToolSelector;

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
        this.dynamicToolSelector = new DynamicToolSelector(new ToolEmbeddingStore(client));
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

        // Load MCP tools asynchronously, then create all tools
        AgentUtils.getMcpToolSpecs(mlAgent, client, sdkClient, encryptor, ActionListener.wrap(mcpTools -> {
            mlToolSpecs.addAll(mcpTools);
            createToolsThenSelectAndRun(
                mlAgent,
                params,
                messages,
                modelProvider,
                functionCalling,
                iteration,
                maxIterations,
                accumulatedTokenUsage,
                mlToolSpecs,
                toolsMap,
                toolSpecMap,
                toolInteractionJsonList,
                listener
            );
        }, e -> {
            // Continue without MCP tools if they fail
            log.warn("MCP tools failed to load, continuing with backend tools only. agentId={}", agentId, e);
            createToolsThenSelectAndRun(
                mlAgent,
                params,
                messages,
                modelProvider,
                functionCalling,
                iteration,
                maxIterations,
                accumulatedTokenUsage,
                mlToolSpecs,
                toolsMap,
                toolSpecMap,
                toolInteractionJsonList,
                listener
            );
        }));
    }

    /**
     * Instantiate every configured tool, then — when tool search applies — show the LLM only the
     * pinned tools plus {@link SearchToolsTool}. Runs after the ML and MCP specs are merged and
     * after {@link AgentUtils#createTools}, so MCP tools are searchable and effective descriptions
     * are available to embed.
     */
    private void createToolsThenSelectAndRun(
        MLAgent mlAgent,
        Map<String, String> params,
        List<Message> messages,
        ModelProvider modelProvider,
        FunctionCalling functionCalling,
        AtomicInteger iteration,
        int maxIterations,
        TokenUsage[] accumulatedTokenUsage,
        List<MLToolSpec> mlToolSpecs,
        Map<String, Tool> toolsMap,
        Map<String, MLToolSpec> toolSpecMap,
        List<String> toolInteractionJsonList,
        ActionListener<AgentLogicResult> listener
    ) {
        String agentId = params.get(AGENT_ID_LOG_FIELD);
        try {
            AgentUtils.createTools(toolFactories, params, mlToolSpecs, toolsMap, toolSpecMap, mlAgent);
        } catch (Exception ex) {
            log.warn("Failed to create some tools, continuing with available tools. agentId={}", agentId, ex);
        }

        Map<String, Tool> promptToolsMap = toolsMap;
        Map<String, Tool> executionToolsMap = toolsMap;
        try {
            ToolSelectionSettings toolSelectionSettings = ToolSelectionSettings.resolve(clusterService, mlAgent);
            ToolSelectionResult plan = dynamicToolSelector.plan(toolsMap, toolSelectionSettings);
            logSelection(agentId, plan);
            if (plan.isApplied()) {
                promptToolsMap = pinnedToolsPlusSearch(mlAgent, toolsMap, plan, toolSelectionSettings, messages);
                executionToolsMap = new HashMap<>(toolsMap);
                executionToolsMap.put(SearchToolsTool.NAME, promptToolsMap.get(SearchToolsTool.NAME));
            }
        } catch (Exception e) {
            // Tool search is an optimisation; a bug in it must never take an agent request down.
            log.warn("Tool search setup failed unexpectedly; exposing all tools. agentId={}", agentId, e);
            promptToolsMap = toolsMap;
            executionToolsMap = toolsMap;
        }

        executeLLMCall(
            mlAgent,
            params,
            messages,
            modelProvider,
            functionCalling,
            iteration,
            maxIterations,
            accumulatedTokenUsage,
            promptToolsMap,
            executionToolsMap,
            toolInteractionJsonList,
            listener
        );
    }

    /**
     * Build the tool map the LLM is shown: pinned tools in configured order, the tools the
     * conversation history already called, then search_tools. Mutable because SearchToolsTool
     * appends its matches to it. Also starts the cache warm-up.
     */
    Map<String, Tool> pinnedToolsPlusSearch(
        MLAgent mlAgent,
        Map<String, Tool> toolsMap,
        ToolSelectionResult plan,
        ToolSelectionSettings toolSelectionSettings,
        List<Message> history
    ) {
        Map<String, Tool> promptToolsMap = new LinkedHashMap<>();
        for (String name : plan.getPinnedToolNames()) {
            promptToolsMap.put(name, toolsMap.get(name));
        }
        for (String name : toolsCalledInHistory(history, toolsMap, toolSelectionSettings.getMaxDynamicTools())) {
            promptToolsMap.putIfAbsent(name, toolsMap.get(name));
        }
        Map<String, Tool> hidden = new LinkedHashMap<>(toolsMap);
        hidden.keySet().removeAll(promptToolsMap.keySet());

        SearchToolsTool searchTool = new SearchToolsTool(
            dynamicToolSelector,
            toolSelectionSettings,
            mlAgent.getTenantId(),
            toolsMap,
            promptToolsMap,
            client.threadPool()
        );
        promptToolsMap.put(SearchToolsTool.NAME, searchTool);
        dynamicToolSelector.warmUp(hidden, toolSelectionSettings, mlAgent.getTenantId());
        return promptToolsMap;
    }

    /**
     * Configured tools called earlier in the conversation, most recent first, up to {@code limit}.
     * Shown again so the history's tool calls always refer to tools in the request.
     */
    static List<String> toolsCalledInHistory(List<Message> history, Map<String, Tool> toolsMap, int limit) {
        LinkedHashSet<String> names = new LinkedHashSet<>();
        if (history == null) {
            return new ArrayList<>(names);
        }
        for (int i = history.size() - 1; i >= 0 && names.size() < limit; i--) {
            List<ToolCall> toolCalls = history.get(i) == null ? null : history.get(i).getToolCalls();
            if (toolCalls == null) {
                continue;
            }
            for (int j = toolCalls.size() - 1; j >= 0 && names.size() < limit; j--) {
                ToolCall.ToolFunction function = toolCalls.get(j) == null ? null : toolCalls.get(j).getFunction();
                String name = function == null ? null : function.getName();
                if (name != null && toolsMap.containsKey(name) && !SearchToolsTool.NAME.equals(name)) {
                    names.add(name);
                }
            }
        }
        return new ArrayList<>(names);
    }

    /** Lets {@link SearchToolsTool} adopt a hidden tool the model called directly. */
    void adoptDirectCalls(Map<String, Tool> promptToolsMap, List<Map<String, String>> toolCalls) {
        if (!(promptToolsMap.get(SearchToolsTool.NAME) instanceof SearchToolsTool searchTool)) {
            return;
        }
        for (Map<String, String> toolCall : toolCalls) {
            searchTool.adoptDirectCall(toolCall.get("tool_name"));
        }
    }

    /**
     * One structured log line per agent execution. Must never throw into the execution path.
     */
    void logSelection(String agentId, ToolSelectionResult result) {
        if (result == null) {
            return;
        }
        if (result.isApplied()) {
            log
                .info(
                    "tool_selection applied=true agentId={} pool_size={} pinned={} searchable={}",
                    agentId,
                    result.getPoolSize(),
                    result.getPinnedToolNames(),
                    result.getSearchableCount()
                );
        } else {
            log
                .debug(
                    "tool_selection applied=false agentId={} pool_size={} reason={}",
                    agentId,
                    result.getPoolSize(),
                    result.getSkipReason()
                );
        }
    }

    /**
     * Execute a single LLM call in the ReAct loop.
     * This method recursively calls itself to continue the ReAct loop until:
     * - No tool calls are returned (end_turn)
     * - Max iterations reached (max_iterations)
     *
     * @param promptToolsMap the tools described to the LLM; the same instance across iterations,
     *                       so tools search_tools appends appear from the next call
     * @param executionToolsMap every tool configured on the agent, used to run tool calls; tool
     *                          search reduces prompt size, it is not an authorisation boundary
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
        Map<String, Tool> promptToolsMap,
        Map<String, Tool> executionToolsMap,
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
            if (!promptToolsMap.isEmpty()) {
                // Re-configure functionCalling with llmParams to ensure TOOL_TEMPLATE is set
                // This is needed because addToolsToPrompt checks for TOOL_TEMPLATE to determine
                // whether to use function calling format or plain text format
                functionCalling.configure(llmParams);

                List<String> toolNames = new ArrayList<>(promptToolsMap.keySet());
                AgentUtils.addToolsToFunctionCalling(promptToolsMap, llmParams, toolNames, "");
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

                    adoptDirectCalls(promptToolsMap, toolCalls);

                    // Execute tools against every configured tool, not just this turn's prompt set
                    executeToolsSequentially(executionToolsMap, toolCalls, ActionListener.wrap(toolResults -> {
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
                            promptToolsMap,
                            executionToolsMap,
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
