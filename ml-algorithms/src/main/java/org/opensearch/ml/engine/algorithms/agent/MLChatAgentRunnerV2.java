/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.agent;

import static org.opensearch.ml.common.utils.StringUtils.gson;
import static org.opensearch.ml.engine.algorithms.agent.AgentUtils.createTools;
import static org.opensearch.ml.engine.algorithms.agent.AgentUtils.getMlToolSpecs;

import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.Settings;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.common.Strings;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.MLAgentType;
import org.opensearch.ml.common.agent.LLMSpec;
import org.opensearch.ml.common.agent.MLAgent;
import org.opensearch.ml.common.agent.MLToolSpec;
import org.opensearch.ml.common.dataset.remote.RemoteInferenceInputDataSet;
import org.opensearch.ml.common.hooks.HookRegistry;
import org.opensearch.ml.common.input.MLInput;
import org.opensearch.ml.common.input.execute.agent.AgentInput;
import org.opensearch.ml.common.input.execute.agent.ContentType;
import org.opensearch.ml.common.memory.Memory;
import org.opensearch.ml.common.output.model.ModelTensor;
import org.opensearch.ml.common.output.model.ModelTensorOutput;
import org.opensearch.ml.common.output.model.ModelTensors;
import org.opensearch.ml.common.spi.tools.Tool;
import org.opensearch.ml.common.transport.prediction.MLPredictionTaskAction;
import org.opensearch.ml.common.transport.prediction.MLPredictionTaskRequest;
import org.opensearch.ml.engine.encryptor.Encryptor;
import org.opensearch.ml.engine.memory.AgenticConversationMemory;
import org.opensearch.ml.engine.agents.models.ModelProvider;
import org.opensearch.ml.engine.agents.models.ModelProviderFactory;
import org.opensearch.remote.metadata.client.SdkClient;
import org.opensearch.transport.TransportChannel;
import org.opensearch.transport.client.Client;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.log4j.Log4j2;

/**
 * V2 Chat Agent Runner with unified input interface and full multi-modal support.
 *
 * Key Features:
 * - Accepts AgentInput (TEXT, CONTENT_BLOCKS, MESSAGES)
 * - Uses ModelProvider for format conversion
 * - Stores complete conversations in agentic_memory
 * - Clean separation from V1/V2 legacy logic
 */
@Log4j2
@Data
@NoArgsConstructor
public class MLChatAgentRunnerV2 implements MLAgentRunner {

    private static final String DEFAULT_SYSTEM_PROMPT = "You are a helpful assistant.";
    private static final String DEFAULT_MAX_ITERATIONS = "10";
    private static final String MAX_ITERATIONS_MESSAGE = "Agent reached maximum iterations (%d) without completing the task";

    // Parameter keys
    public static final String SESSION_ID = "session_id";
    public static final String SYSTEM_PROMPT_FIELD = "system_prompt";
    public static final String MAX_ITERATION = "max_iteration";
    public static final String VERBOSE = "verbose";

    // Message ID tracking for Strands format (per session)
    private static final Map<String, AtomicInteger> sessionMessageCounters = new ConcurrentHashMap<>();

    private Client client;
    private Settings settings;
    private ClusterService clusterService;
    private NamedXContentRegistry xContentRegistry;
    private Map<String, Tool.Factory> toolFactories;
    private Map<String, Memory.Factory> memoryFactoryMap;
    private SdkClient sdkClient;
    private Encryptor encryptor;
    private HookRegistry hookRegistry;

    public MLChatAgentRunnerV2(
        Client client,
        Settings settings,
        ClusterService clusterService,
        NamedXContentRegistry xContentRegistry,
        Map<String, Tool.Factory> toolFactories,
        Map<String, Memory.Factory> memoryFactoryMap,
        SdkClient sdkClient,
        Encryptor encryptor,
        HookRegistry hookRegistry
    ) {
        this.client = client;
        this.settings = settings;
        this.clusterService = clusterService;
        this.xContentRegistry = xContentRegistry;
        this.toolFactories = toolFactories;
        this.memoryFactoryMap = memoryFactoryMap;
        this.sdkClient = sdkClient;
        this.encryptor = encryptor;
        this.hookRegistry = hookRegistry;
    }

    @Override
    public void run(MLAgent mlAgent, Map<String, String> inputParams, ActionListener<Object> listener) {
        run(mlAgent, inputParams, listener, null);
    }

    @Override
    public void run(MLAgent mlAgent, Map<String, String> inputParams, ActionListener<Object> listener, TransportChannel channel) {
        // Parameters have already been converted by processAgentInput() in MLAgentExecutor
        // using ModelProvider.mapAgentInput()

        // Extract question text for memory storage
        String questionText = inputParams.get(MLAgentExecutor.QUESTION);
        if (Strings.isNullOrEmpty(questionText)) {
            listener.onFailure(new IllegalArgumentException("Question parameter is required for V2 agent execution"));
            return;
        }

        // Extract original AgentInput for Strands format storage
        AgentInput agentInput = null;
        String agentInputBytes = inputParams.get("__agent_input_bytes__");
        if (agentInputBytes != null) {
            try {
                // Deserialize AgentInput from Base64-encoded bytes
                byte[] decoded = Base64.getDecoder().decode(agentInputBytes);
                StreamInput in = StreamInput.wrap(decoded);
                agentInput = new AgentInput(in);
                log.debug("Deserialized AgentInput with type: {}", agentInput.getInputType());
            } catch (Exception e) {
                log.warn("Failed to deserialize AgentInput, will use text-only storage", e);
            }
        }

        // Setup memory if configured
        if (mlAgent.getMemory() != null && memoryFactoryMap != null && !memoryFactoryMap.isEmpty()) {
            setupMemoryAndRun(mlAgent, agentInput, questionText, inputParams, listener);
        } else {
            // No memory - run directly
            runAgentWithInput(mlAgent, agentInput, questionText, inputParams, listener, null);
        }
    }

    /**
     * Setup memory and run agent with conversation history
     */
    private void setupMemoryAndRun(
        MLAgent mlAgent,
        AgentInput agentInput,
        String questionText,
        Map<String, String> inputParams,
        ActionListener<Object> listener
    ) {
        String memoryType = mlAgent.getMemory().getType();
        String sessionId = inputParams.get(SESSION_ID);
        String appType = mlAgent.getAppType();

        Map<String, Object> memoryParams = new HashMap<>();
        memoryParams.put("memory_id", sessionId);
        memoryParams.put("memory_name", questionText);
        memoryParams.put("app_type", appType);
        memoryParams.put("memory_container_id", mlAgent.getMemory().getMemoryContainerId());

        // Convert memory type to uppercase for map lookup
        String memoryTypeKey = memoryType.toUpperCase(Locale.ROOT);
        Memory.Factory<?> memoryFactory = memoryFactoryMap.get(memoryTypeKey);
        if (memoryFactory == null) {
            listener.onFailure(new IllegalArgumentException("Memory factory not found for type: " + memoryType));
            return;
        }

        memoryFactory.create(memoryParams, ActionListener.wrap(mem -> {
            Memory memory = (Memory) mem;

            // Load conversation history from memory and prepend to current request
            if (memory instanceof AgenticConversationMemory) {
                AgenticConversationMemory agenticMemory = (AgenticConversationMemory) memory;

                // Load up to 100 messages from conversation history
                agenticMemory.getFullConversationHistory(100, ActionListener.wrap(history -> {
                    try {
                        // Convert history to Message objects and merge with current input
                        AgentInput enrichedInput = enrichInputWithHistory(agentInput, history, questionText);

                        // Re-process through ModelProvider to get proper format with conversation history
                        if (enrichedInput != null) {
                            String modelProviderStr = mlAgent.getModel().getModelProvider();
                            if (modelProviderStr == null && mlAgent.getParameters() != null) {
                                modelProviderStr = mlAgent.getParameters().get("model_provider");
                            }

                            if (modelProviderStr != null) {
                                ModelProvider modelProvider = ModelProviderFactory.getProvider(modelProviderStr);
                                Map<String, String> enrichedParams = modelProvider
                                    .mapAgentInput(enrichedInput, MLAgentType.CONVERSATIONAL_V2);
                                enrichedParams.put(MLAgentExecutor.QUESTION, questionText);

                                // Copy other parameters from original input
                                for (Map.Entry<String, String> entry : inputParams.entrySet()) {
                                    if (!enrichedParams.containsKey(entry.getKey())) {
                                        enrichedParams.put(entry.getKey(), entry.getValue());
                                    }
                                }

                                log.info("Loaded {} conversation turns for session {}", history.size(), sessionId);
                                runAgentWithInput(mlAgent, agentInput, questionText, enrichedParams, listener, memory);
                                return;
                            }
                        }
                    } catch (Exception e) {
                        log.warn("Failed to load conversation history, running without history", e);
                    }

                    // Fallback: run without history
                    runAgentWithInput(mlAgent, agentInput, questionText, inputParams, listener, memory);
                }, error -> {
                    log.warn("Failed to retrieve conversation history, running without history", error);
                    runAgentWithInput(mlAgent, agentInput, questionText, inputParams, listener, memory);
                }));
            } else {
                // Not AgenticConversationMemory, run without history
                runAgentWithInput(mlAgent, agentInput, questionText, inputParams, listener, memory);
            }
        }, listener::onFailure));
    }

    /**
     * Main agent execution - parameters already converted by MLAgentExecutor.processAgentInput()
     */
    private void runAgentWithInput(
        MLAgent mlAgent,
        AgentInput agentInput,
        String questionText,
        Map<String, String> inputParams,
        ActionListener<Object> listener,
        Memory memory
    ) {
        // Parameters have already been converted by ModelProvider.mapAgentInput() in MLAgentExecutor
        // So we can use them directly

        // Create tools if needed
        Map<String, Tool> tools = new HashMap<>();
        Map<String, MLToolSpec> toolSpecMap = new HashMap<>();
        if (mlAgent.getTools() != null && !mlAgent.getTools().isEmpty()) {
            List<MLToolSpec> mlToolSpecs = getMlToolSpecs(mlAgent, inputParams);
            log
                .info(
                    "V2 Agent - MLToolSpecs before createTools: {}",
                    mlToolSpecs
                        .stream()
                        .map(spec -> spec.getType() + " (name: " + spec.getName() + ", desc: " + spec.getDescription() + ")")
                        .collect(java.util.stream.Collectors.joining(", "))
                );
            createTools(toolFactories, inputParams, mlToolSpecs, tools, toolSpecMap, mlAgent);
            log.info("V2 Agent - Tools after createTools: {}", tools.keySet());
            for (Map.Entry<String, Tool> entry : tools.entrySet()) {
                log
                    .info(
                        "V2 Agent - Tool key: '{}', tool.getName(): '{}', tool.getDescription(): '{}'",
                        entry.getKey(),
                        entry.getValue().getName(),
                        entry.getValue().getDescription()
                    );
            }
        }

        // Use the parameters directly (they're already in model-specific format)
        Map<String, String> llmParameters = new HashMap<>(inputParams);

        // Add system prompt if configured
        String systemPrompt = inputParams
            .getOrDefault(SYSTEM_PROMPT_FIELD, mlAgent.getParameters() != null ? mlAgent.getParameters().get(SYSTEM_PROMPT_FIELD) : null);
        if (systemPrompt != null) {
            llmParameters.put("system_prompt", systemPrompt);
        }

        // Add tool configurations if tools are present
        if (!tools.isEmpty()) {
            // Get model provider to format tools appropriately
            // Try multiple sources in order of priority
            String modelProvider = getModelProvider(llmParameters, mlAgent);
            log.info("V2 Agent - Detected model provider: {}", modelProvider);
            addToolConfigurations(llmParameters, tools, toolSpecMap, modelProvider);
        }

        // Execute agent
        if (tools.isEmpty()) {
            // No tools - simple LLM call (will store user input + response)
            String modelProvider = getModelProvider(llmParameters, mlAgent);
            log.info("V2 Agent - Using model provider for simple call: {}", modelProvider);
            executeSimpleLLMCall(mlAgent.getLlm(), llmParameters, agentInput, questionText, memory, modelProvider, listener);
        } else {
            // With tools - ReAct loop
            // Store initial user input first, then tool interactions will be stored during the loop
            storeInitialUserInput(memory, agentInput, questionText);
            executeWithTools(mlAgent, agentInput, questionText, llmParameters, tools, toolSpecMap, memory, listener);
        }
    }

    /**
     * Get model provider from multiple sources with fallback detection
     */
    private String getModelProvider(Map<String, String> parameters, MLAgent mlAgent) {
        // 1. Check request parameters (highest priority)
        String modelProvider = parameters.get("model_provider");
        if (modelProvider != null && !modelProvider.trim().isEmpty()) {
            return modelProvider;
        }

        // 2. Check LLM spec parameters
        LLMSpec llmSpec = mlAgent.getLlm();
        if (llmSpec != null && llmSpec.getParameters() != null) {
            modelProvider = llmSpec.getParameters().get("model_provider");
            if (modelProvider != null && !modelProvider.trim().isEmpty()) {
                return modelProvider;
            }
        }

        // 3. Check agent parameters
        if (mlAgent.getParameters() != null) {
            modelProvider = mlAgent.getParameters().get("model_provider");
            if (modelProvider != null && !modelProvider.trim().isEmpty()) {
                return modelProvider;
            }
        }

        // 4. Fallback: Try to detect from model ID
        if (llmSpec != null && llmSpec.getModelId() != null) {
            String modelId = llmSpec.getModelId().toLowerCase();

            // OpenAI model patterns
            if (modelId.contains("gpt") || modelId.contains("o1") || modelId.contains("o3")) {
                log.info("V2 Agent - Auto-detected OpenAI provider from model ID: {}", llmSpec.getModelId());
                return "openai/v1/chat/completions";
            }

            // Bedrock model patterns
            if (modelId.contains("anthropic")
                || modelId.contains("claude")
                || modelId.contains("amazon")
                || modelId.contains("meta")
                || modelId.startsWith("us.")
                || modelId.startsWith("eu.")) {
                log.info("V2 Agent - Auto-detected Bedrock provider from model ID: {}", llmSpec.getModelId());
                return "bedrock/converse";
            }

            // Gemini model patterns
            if (modelId.contains("gemini") || modelId.contains("google")) {
                log.info("V2 Agent - Auto-detected Gemini provider from model ID: {}", llmSpec.getModelId());
                return "gemini/v1beta/generatecontent";
            }
        }

        // 5. Default to Bedrock if unable to detect
        log.warn("V2 Agent - Unable to detect model provider, defaulting to Bedrock");
        return "bedrock/converse";
    }

    /**
     * Add tool configurations to LLM parameters.
     * Serializes tool specs into JSON format expected by model providers.
     * Uses Tool objects for name/description and MLToolSpec for input schema.
     */
    private void addToolConfigurations(
        Map<String, String> parameters,
        Map<String, Tool> tools,
        Map<String, MLToolSpec> toolSpecMap,
        String modelProvider
    ) {
        if (tools == null || tools.isEmpty()) {
            return;
        }

        try {
            ModelProvider provider = ModelProviderFactory.getProvider(modelProvider);
            String toolConfigJson = provider.formatToolConfiguration(tools, toolSpecMap);

            if (toolConfigJson != null && !toolConfigJson.isEmpty()) {
                parameters.put("tool_configs", toolConfigJson);
                log.info("Added {} tool configurations to LLM request using {} provider", tools.size(), modelProvider);
            }
        } catch (Exception e) {
            log.error("Failed to serialize tool configurations", e);
        }
    }

    /**
     * Execute agent with tools using ReAct loop
     */
    private void executeWithTools(
        MLAgent mlAgent,
        AgentInput agentInput,
        String questionText,
        Map<String, String> parameters,
        Map<String, Tool> tools,
        Map<String, MLToolSpec> toolSpecMap,
        Memory memory,
        ActionListener<Object> listener
    ) {
        // Get model provider for response parsing
        String modelProvider = getModelProvider(parameters, mlAgent);
        log.info("V2 Agent - Using model provider for execution: {}", modelProvider);

        // Initialize accumulated usage tracking
        Map<String, Object> accumulatedUsage = new HashMap<>();

        // Execute LLM and handle tool use responses
        executeLLMWithToolLoop(
            mlAgent.getLlm(),
            parameters,
            agentInput,
            questionText,
            memory,
            tools,
            toolSpecMap,
            modelProvider,
            accumulatedUsage,
            listener,
            0
        );
    }

    /**
     * Parsed LLM response in provider-agnostic format
     */
    /**
     * Parse raw LLM response based on provider format (delegated to provider)
     */
    private ModelProvider.ParsedLLMResponse parseLLMResponse(Map<String, Object> rawMap, String modelProvider) {
        ModelProvider provider = ModelProviderFactory.getProvider(modelProvider);
        return provider.parseResponse(rawMap);
    }

    /**
     * Execute LLM in a loop, handling tool_use responses until final answer
     */
    private void executeLLMWithToolLoop(
        LLMSpec llmSpec,
        Map<String, String> parameters,
        AgentInput agentInput,
        String questionText,
        Memory memory,
        Map<String, Tool> tools,
        Map<String, MLToolSpec> toolSpecMap,
        String modelProvider,
        Map<String, Object> accumulatedUsage,
        ActionListener<Object> listener,
        int iteration
    ) {
        // Safety limit on iterations
        final int MAX_ITERATIONS = 10;
        if (iteration >= MAX_ITERATIONS) {
            log.warn("V2 Agent - Reached maximum iterations ({}), stopping tool loop", MAX_ITERATIONS);
            listener.onFailure(new RuntimeException("Maximum tool execution iterations reached"));
            return;
        }

        // Execute LLM call and get raw response
        executeLLMRaw(llmSpec, parameters, ActionListener.wrap(rawResponse -> {
            try {
                // Parse raw response (handles both Bedrock and OpenAI formats)
                Map<String, Object> rawMap = (Map<String, Object>) rawResponse;
                ModelProvider.ParsedLLMResponse parsed = parseLLMResponse(rawMap, modelProvider);

                String stopReason = parsed.getStopReason();
                Map<String, Object> message = parsed.getMessage();
                List<Map<String, Object>> content = parsed.getContent();

                // Accumulate usage from this iteration
                if (parsed.getUsage() != null) {
                    accumulateUsage(accumulatedUsage, parsed.getUsage());
                    log.info("V2 Agent - Accumulated usage after iteration {}: {}", iteration, accumulatedUsage);
                }

                log.info("V2 Agent - Raw LLM response stopReason: {}", stopReason);

                if ("tool_use".equals(stopReason)) {
                    List<Map<String, Object>> toolUseBlocks = parsed.getToolUseBlocks();

                    if (toolUseBlocks.isEmpty()) {
                        log.warn("V2 Agent - stopReason is tool_use but no toolUse blocks found");
                        // Build Strands response with metrics and return
                        Map<String, Object> strandsResponse = new HashMap<>();
                        strandsResponse.put("message", message);
                        strandsResponse.put("stop_reason", stopReason);

                        // Add metrics with accumulated usage
                        Map<String, Object> metrics = new HashMap<>();
                        metrics.put("accumulated_usage", accumulatedUsage);
                        strandsResponse.put("metrics", metrics);

                        listener.onResponse(strandsResponse);
                        return;
                    }

                    log.info("V2 Agent - Found {} tool use requests", toolUseBlocks.size());

                    // Store assistant's tool_use message in memory
                    if (memory instanceof AgenticConversationMemory) {
                        AgenticConversationMemory agenticMemory = (AgenticConversationMemory) memory;
                        String sessionId = memory.getId();
                        int messageId = getNextMessageId(sessionId);

                        // Convert content to proper format for storage
                        List<Map<String, Object>> contentForStorage = new ArrayList<>();
                        for (Map<String, Object> block : content) {
                            contentForStorage.add(block);
                        }

                        agenticMemory
                            .saveMessageInStrandsFormat(
                                "assistant",
                                contentForStorage,
                                messageId,
                                ActionListener
                                    .wrap(
                                        response -> log
                                            .info(
                                                "V2 Agent - Stored assistant tool_use message (id: {}) for session {}",
                                                messageId,
                                                sessionId
                                            ),
                                        error -> log
                                            .error("V2 Agent - Failed to store assistant tool_use message for session {}", sessionId, error)
                                    )
                            );

                        log.info("V2 Agent - Stored assistant tool_use message to memory");
                    }

                    // Add assistant's tool_use message to interactions for conversation continuity
                    String assistantMessage = buildAssistantToolUseMessage(content, toolUseBlocks, modelProvider);
                    String currentInteractions = parameters.get("_interactions");
                    if (currentInteractions == null || currentInteractions.isEmpty()) {
                        parameters.put("_interactions", "," + assistantMessage);
                    } else {
                        parameters.put("_interactions", currentInteractions + "," + assistantMessage);
                    }
                    log.info("V2 Agent - Added assistant tool_use message to conversation history (format: {})", modelProvider);

                    // Execute tools and collect results
                    AtomicInteger completedTools = new AtomicInteger(0);
                    List<Map<String, Object>> toolResults = new CopyOnWriteArrayList<>();
                    AtomicReference<Exception> executionError = new AtomicReference<>();

                    for (Map<String, Object> toolUse : toolUseBlocks) {
                        String toolName = (String) toolUse.get("name");
                        String toolUseId = (String) toolUse.get("toolUseId");
                        Map<String, Object> toolInput = (Map<String, Object>) toolUse.get("input");

                        log.info("V2 Agent - Executing tool: {} with input: {}", toolName, toolInput);

                        Tool tool = tools.get(toolName);
                        if (tool == null) {
                            log.error("V2 Agent - Tool not found: {}", toolName);
                            // Add error result
                            Map<String, Object> errorResult = new HashMap<>();
                            errorResult.put("toolUseId", toolUseId);
                            errorResult.put("status", "error");
                            errorResult.put("content", Collections.singletonList(Map.of("text", "Tool not found: " + toolName)));
                            toolResults.add(errorResult);

                            if (completedTools.incrementAndGet() == toolUseBlocks.size()) {
                                continueWithToolResults(
                                    llmSpec,
                                    parameters,
                                    agentInput,
                                    questionText,
                                    memory,
                                    tools,
                                    toolSpecMap,
                                    toolResults,
                                    modelProvider,
                                    accumulatedUsage,
                                    listener,
                                    iteration + 1
                                );
                            }
                            continue;
                        }

                        // Convert tool input to parameters map
                        Map<String, String> toolParams = new HashMap<>();
                        if (toolInput != null) {
                            for (Map.Entry<String, Object> entry : toolInput.entrySet()) {
                                Object value = entry.getValue();
                                // For primitive types (String, Number, Boolean), use toString()
                                // For complex types (Map, List), use gson.toJson()
                                String paramValue;
                                if (value instanceof String) {
                                    paramValue = (String) value;
                                } else if (value instanceof Number || value instanceof Boolean) {
                                    paramValue = value.toString();
                                } else if (value == null) {
                                    paramValue = null;
                                } else {
                                    // Complex object (Map, List, etc.) - serialize to JSON
                                    paramValue = gson.toJson(value);
                                }
                                toolParams.put(entry.getKey(), paramValue);
                            }
                        }

                        // Execute tool
                        tool.run(toolParams, ActionListener.wrap(toolOutput -> {
                            log.info("V2 Agent - Tool {} completed successfully", toolName);

                            // Build tool result
                            Map<String, Object> toolResult = new HashMap<>();
                            toolResult.put("toolUseId", toolUseId);
                            toolResult.put("status", "success");

                            // Format tool output as text content
                            String outputText = toolOutput instanceof String ? (String) toolOutput : gson.toJson(toolOutput);
                            toolResult.put("content", Collections.singletonList(Map.of("text", outputText)));
                            toolResults.add(toolResult);

                            // Check if all tools completed
                            if (completedTools.incrementAndGet() == toolUseBlocks.size()) {
                                // Continue with all results (including any errors)
                                continueWithToolResults(
                                    llmSpec,
                                    parameters,
                                    agentInput,
                                    questionText,
                                    memory,
                                    tools,
                                    toolSpecMap,
                                    toolResults,
                                    modelProvider,
                                    accumulatedUsage,
                                    listener,
                                    iteration + 1
                                );
                            }
                        }, error -> {
                            log.error("V2 Agent - Tool {} execution failed", toolName, error);
                            // Track that at least one tool failed, but don't fail the entire execution
                            executionError.compareAndSet(null, new RuntimeException("Tool execution failed: " + toolName, error));

                            // Add error result - LLM will handle this gracefully
                            Map<String, Object> errorResult = new HashMap<>();
                            errorResult.put("toolUseId", toolUseId);
                            errorResult.put("status", "error");
                            errorResult.put("content", Collections.singletonList(Map.of("text", "Error: " + error.getMessage())));
                            toolResults.add(errorResult);

                            // Check if all tools completed
                            if (completedTools.incrementAndGet() == toolUseBlocks.size()) {
                                // Continue with all results (including errors) - let LLM handle it
                                continueWithToolResults(
                                    llmSpec,
                                    parameters,
                                    agentInput,
                                    questionText,
                                    memory,
                                    tools,
                                    toolSpecMap,
                                    toolResults,
                                    modelProvider,
                                    accumulatedUsage,
                                    listener,
                                    iteration + 1
                                );
                            }
                        }));
                    }
                } else {
                    // Final answer (stopReason is "end_turn" or other)
                    log.info("V2 Agent - Received final answer (stopReason: {}), storing conversation", stopReason);

                    // Store final assistant response in memory
                    if (memory instanceof AgenticConversationMemory) {
                        AgenticConversationMemory agenticMemory = (AgenticConversationMemory) memory;
                        String sessionId = memory.getId();
                        int messageId = getNextMessageId(sessionId);

                        // Convert message content to proper format
                        List<Map<String, Object>> contentForStorage = new ArrayList<>();
                        for (Map<String, Object> block : content) {
                            contentForStorage.add(block);
                        }

                        agenticMemory
                            .saveMessageInStrandsFormat(
                                "assistant",
                                contentForStorage,
                                messageId,
                                ActionListener
                                    .wrap(
                                        response -> log
                                            .info("V2 Agent - Stored assistant final answer (id: {}) for session {}", messageId, sessionId),
                                        error -> log
                                            .error("V2 Agent - Failed to store assistant final answer for session {}", sessionId, error)
                                    )
                            );

                        log.info("V2 Agent - Stored assistant final answer to memory");
                    }

                    // Build Strands response with metrics
                    Map<String, Object> strandsResponse = new HashMap<>();
                    strandsResponse.put("message", message);
                    strandsResponse.put("stop_reason", stopReason);

                    // Add metrics with accumulated usage
                    Map<String, Object> metrics = new HashMap<>();
                    metrics.put("accumulated_usage", accumulatedUsage);
                    strandsResponse.put("metrics", metrics);

                    listener.onResponse(strandsResponse);
                }
            } catch (Exception e) {
                log.error("V2 Agent - Error processing LLM response", e);
                listener.onFailure(e);
            }
        }, listener::onFailure));
    }

    /**
     * Execute LLM call and return raw response without conversion
     */
    private void executeLLMRaw(LLMSpec llmSpec, Map<String, String> parameters, ActionListener<Object> listener) {
        // Build remote inference input
        RemoteInferenceInputDataSet dataSet = RemoteInferenceInputDataSet.builder().parameters(parameters).build();
        MLInput mlInput = MLInput.builder().algorithm(FunctionName.REMOTE).inputDataset(dataSet).build();

        MLPredictionTaskRequest request = new MLPredictionTaskRequest(llmSpec.getModelId(), mlInput);

        client.execute(MLPredictionTaskAction.INSTANCE, request, ActionListener.wrap(response -> {
            ModelTensorOutput modelOutput = (ModelTensorOutput) response.getOutput();

            // Parse raw response from ModelTensorOutput
            // The response is in the first ModelTensors, first ModelTensor, dataAsMap
            if (modelOutput.getMlModelOutputs() != null && !modelOutput.getMlModelOutputs().isEmpty()) {
                ModelTensors tensors = modelOutput.getMlModelOutputs().get(0);
                if (tensors.getMlModelTensors() != null && !tensors.getMlModelTensors().isEmpty()) {
                    ModelTensor tensor = tensors.getMlModelTensors().get(0);
                    @SuppressWarnings("unchecked")
                    Map<String, Object> dataAsMap = (Map<String, Object>) tensor.getDataAsMap();

                    log.info("V2 Agent - Raw LLM response keys: {}", dataAsMap.keySet());
                    listener.onResponse(dataAsMap);
                    return;
                }
            }

            log.error("V2 Agent - No tensors in model output");
            listener.onFailure(new RuntimeException("No tensors in model output"));
        }, listener::onFailure));
    }

    /**
     * Continue the tool loop by sending tool results back to LLM
     */
    private void continueWithToolResults(
        LLMSpec llmSpec,
        Map<String, String> parameters,
        AgentInput originalInput,
        String questionText,
        Memory memory,
        Map<String, Tool> tools,
        Map<String, MLToolSpec> toolSpecMap,
        List<Map<String, Object>> toolResults,
        String modelProvider,
        Map<String, Object> accumulatedUsage,
        ActionListener<Object> listener,
        int nextIteration
    ) {
        log.info("V2 Agent - Continuing with {} tool results (provider: {})", toolResults.size(), modelProvider);

        try {

            // Build tool result content blocks for memory storage
            List<Map<String, Object>> toolResultContentBlocks = new ArrayList<>();
            for (Map<String, Object> toolResult : toolResults) {
                String toolUseId = (String) toolResult.get("toolUseId");
                String status = (String) toolResult.get("status");
                List<Map<String, Object>> content = (List<Map<String, Object>>) toolResult.get("content");

                Map<String, Object> toolResultBlock = new HashMap<>();
                Map<String, Object> toolResultData = new HashMap<>();
                toolResultData.put("toolUseId", toolUseId);
                toolResultData.put("status", status);
                toolResultData.put("content", content);
                toolResultBlock.put("toolResult", toolResultData);
                toolResultContentBlocks.add(toolResultBlock);
            }

            // Store tool_result message in memory
            if (memory instanceof AgenticConversationMemory) {
                AgenticConversationMemory agenticMemory = (AgenticConversationMemory) memory;
                String sessionId = memory.getId();
                int messageId = getNextMessageId(sessionId);

                agenticMemory
                    .saveMessageInStrandsFormat(
                        "user",
                        toolResultContentBlocks,
                        messageId,
                        ActionListener
                            .wrap(
                                response -> log
                                    .info("V2 Agent - Stored user tool_result message (id: {}) for session {}", messageId, sessionId),
                                error -> log.error("V2 Agent - Failed to store user tool_result message for session {}", sessionId, error)
                            )
                    );

                log.info("V2 Agent - Stored user tool_result message to memory");
            }

            // Add tool result message to conversation in provider-specific format
            // The _interactions parameter is used to append messages to the conversation
            String toolResultMessages = buildToolResultMessages(toolResults, modelProvider);

            // Get current _interactions parameter (may contain tool use from previous response)
            String currentInteractions = parameters.get("_interactions");
            if (currentInteractions == null || currentInteractions.isEmpty()) {
                parameters.put("_interactions", "," + toolResultMessages);
            } else {
                // Append to existing interactions
                parameters.put("_interactions", currentInteractions + "," + toolResultMessages);
            }

            log
                .info(
                    "V2 Agent - Added tool result message(s) to interactions (format: {}), continuing to LLM (iteration {})",
                    modelProvider,
                    nextIteration
                );

            // Continue the loop with updated parameters
            executeLLMWithToolLoop(
                llmSpec,
                parameters,
                originalInput,
                questionText,
                memory,
                tools,
                toolSpecMap,
                modelProvider,
                accumulatedUsage,
                listener,
                nextIteration
            );

        } catch (Exception e) {
            log.error("V2 Agent - Error building tool result message", e);
            listener.onFailure(e);
        }
    }

    /**
     * Accumulate usage metrics from multiple LLM iterations.
     * Handles inputTokens, outputTokens, and totalTokens.
     * Gracefully handles null values.
     *
     * @param accumulated The accumulated usage map to update (modified in place)
     * @param newUsage The new usage to add to the accumulated totals
     */
    private void accumulateUsage(Map<String, Object> accumulated, Map<String, Object> newUsage) {
        if (newUsage == null || newUsage.isEmpty()) {
            return;
        }

        // Accumulate inputTokens
        if (newUsage.containsKey("inputTokens")) {
            Object inputTokens = newUsage.get("inputTokens");
            if (inputTokens instanceof Number) {
                int currentInput = accumulated.containsKey("inputTokens")
                    ? ((Number) accumulated.get("inputTokens")).intValue()
                    : 0;
                accumulated.put("inputTokens", currentInput + ((Number) inputTokens).intValue());
            }
        }

        // Accumulate outputTokens
        if (newUsage.containsKey("outputTokens")) {
            Object outputTokens = newUsage.get("outputTokens");
            if (outputTokens instanceof Number) {
                int currentOutput = accumulated.containsKey("outputTokens")
                    ? ((Number) accumulated.get("outputTokens")).intValue()
                    : 0;
                accumulated.put("outputTokens", currentOutput + ((Number) outputTokens).intValue());
            }
        }

        // Accumulate totalTokens
        if (newUsage.containsKey("totalTokens")) {
            Object totalTokens = newUsage.get("totalTokens");
            if (totalTokens instanceof Number) {
                int currentTotal = accumulated.containsKey("totalTokens")
                    ? ((Number) accumulated.get("totalTokens")).intValue()
                    : 0;
                accumulated.put("totalTokens", currentTotal + ((Number) totalTokens).intValue());
            }
        }
    }

    /**
     * Extract text content from response map
     */
    private String extractTextFromResponse(Map<String, Object> responseMap) {
        try {
            Map<String, Object> message = (Map<String, Object>) responseMap.get("message");
            if (message == null)
                return "";

            List<Map<String, Object>> content = (List<Map<String, Object>>) message.get("content");
            if (content == null)
                return "";

            StringBuilder text = new StringBuilder();
            for (Map<String, Object> block : content) {
                if (block.containsKey("text")) {
                    text.append(block.get("text"));
                }
            }
            return text.toString();
        } catch (Exception e) {
            log.error("V2 Agent - Error extracting text from response", e);
            return "";
        }
    }

    /**
     * Build assistant message with tool use in provider-specific format (delegated to provider)
     */
    private String buildAssistantToolUseMessage(
        List<Map<String, Object>> content,
        List<Map<String, Object>> toolUseBlocks,
        String modelProvider
    ) {
        ModelProvider provider = ModelProviderFactory.getProvider(modelProvider);
        return provider.formatAssistantToolUseMessage(content, toolUseBlocks);
    }

    /**
     * Build tool result messages in provider-specific format
     */
    private String buildToolResultMessages(List<Map<String, Object>> toolResults, String modelProvider) {
        ModelProvider provider = ModelProviderFactory.getProvider(modelProvider);
        return provider.formatToolResultMessages(toolResults);
    }

    /**
     * Execute a simple LLM call without tools
     */
    private void executeSimpleLLMCall(
        LLMSpec llmSpec,
        Map<String, String> parameters,
        AgentInput agentInput,
        String questionText,
        Memory memory,
        String modelProvider,
        ActionListener<Object> listener
    ) {
        // Use executeLLMRaw to get the actual stopReason from raw response
        executeLLMRaw(llmSpec, parameters, ActionListener.wrap(rawResponse -> {
            // Parse raw response (handles both Bedrock and OpenAI formats)
            Map<String, Object> rawMap = (Map<String, Object>) rawResponse;
            ModelProvider.ParsedLLMResponse parsed = parseLLMResponse(rawMap, modelProvider);

            String stopReason = parsed.getStopReason();
            Map<String, Object> message = parsed.getMessage();
            List<Map<String, Object>> content = parsed.getContent();

            // Extract text from content
            StringBuilder answerText = new StringBuilder();
            for (Map<String, Object> block : content) {
                if (block.containsKey("text")) {
                    answerText.append(block.get("text"));
                }
            }

            // Store conversation in memory if configured
            if (memory != null) {
                storeConversation(memory, agentInput, questionText, new ArrayList<>(), answerText.toString());
            }

            // Build Strands response with actual stopReason and metrics
            Map<String, Object> result = new HashMap<>();
            result.put("message", message);
            result.put("stop_reason", stopReason);

            // Add metrics with usage if available
            if (parsed.getUsage() != null) {
                Map<String, Object> metrics = new HashMap<>();
                metrics.put("accumulated_usage", parsed.getUsage());
                result.put("metrics", metrics);
            }

            listener.onResponse(result);
        }, listener::onFailure));
    }

    /**
     * Build response in Strands message format.
     *
     * Format:
     * {
     *   "message": {
     *     "role": "assistant",
     *     "content": [
     *       {"text": "The response text"}
     *     ]
     *   },
     *   "stop_reason": "end_turn"
     * }
     */
    private Map<String, Object> buildStrandsMessageResponse(String answerText, String stopReason) {
        // Build content array
        List<Map<String, Object>> content = new ArrayList<>();
        Map<String, Object> textBlock = new HashMap<>();
        textBlock.put("text", answerText);
        content.add(textBlock);

        // Build message
        Map<String, Object> message = new HashMap<>();
        message.put("role", "assistant");
        message.put("content", content);

        // Build result
        Map<String, Object> result = new HashMap<>();
        result.put("message", message);
        result.put("stop_reason", stopReason);

        return result;
    }

    /**
     * Store initial user input at the beginning of a conversation.
     * Used for tool-enabled agents where tool interactions will be stored separately.
     */
    private void storeInitialUserInput(Memory memory, AgentInput agentInput, String questionText) {
        if (!(memory instanceof AgenticConversationMemory)) {
            log.warn("Memory is not AgenticConversationMemory, skipping structured storage");
            return;
        }

        AgenticConversationMemory agenticMemory = (AgenticConversationMemory) memory;
        String sessionId = memory.getId();

        // Convert AgentInput to Strands messages
        List<StrandsMessage> inputMessages = convertAgentInputToStrandsMessages(agentInput);

        // Fallback: if no messages converted (e.g., agentInput was null), create a text message from questionText
        if (inputMessages.isEmpty() && questionText != null && !questionText.isEmpty()) {
            log.warn("No input messages from AgentInput, falling back to text-only message for session {}", sessionId);
            inputMessages.add(createTextMessage("user", questionText));
        }

        log.info("V2 Agent - Storing {} initial user input message(s) for session {}", inputMessages.size(), sessionId);

        // Store each input message with sequential message_id
        for (StrandsMessage msg : inputMessages) {
            int messageId = getNextMessageId(sessionId);
            agenticMemory
                .saveMessageInStrandsFormat(
                    msg.getRole(),
                    msg.getContent(),
                    messageId,
                    ActionListener
                        .wrap(
                            response -> log
                                .info(
                                    "V2 Agent - Stored initial user input message {} (role: {}) for session {}",
                                    messageId,
                                    msg.getRole(),
                                    sessionId
                                ),
                            error -> log
                                .error(
                                    "V2 Agent - Failed to store initial user input message {} for session {}",
                                    messageId,
                                    sessionId,
                                    error
                                )
                        )
                );
        }
    }

    /**
     * Store conversation in memory in Strands format with sequential message_id.
     * Used for non-tool scenarios where user input + final answer are stored together.
     */
    private void storeConversation(
        Memory memory,
        AgentInput agentInput,
        String questionText,
        List<Map<String, Object>> toolInteractions,
        String finalAnswer
    ) {
        if (!(memory instanceof AgenticConversationMemory)) {
            log.warn("Memory is not AgenticConversationMemory, skipping structured storage");
            return;
        }

        AgenticConversationMemory agenticMemory = (AgenticConversationMemory) memory;
        String sessionId = memory.getId();

        // Convert AgentInput to Strands messages
        List<StrandsMessage> inputMessages = convertAgentInputToStrandsMessages(agentInput);

        // Fallback: if no messages converted (e.g., agentInput was null), create a text message from questionText
        if (inputMessages.isEmpty() && questionText != null && !questionText.isEmpty()) {
            log.warn("No input messages from AgentInput, falling back to text-only message for session {}", sessionId);
            inputMessages.add(createTextMessage("user", questionText));
        }

        log.info("V2 Agent - Storing {} input message(s) for session {}", inputMessages.size(), sessionId);

        // Store each input message with sequential message_id
        for (StrandsMessage msg : inputMessages) {
            int messageId = getNextMessageId(sessionId);
            agenticMemory
                .saveMessageInStrandsFormat(
                    msg.getRole(),
                    msg.getContent(),
                    messageId,
                    ActionListener
                        .wrap(
                            response -> log.info("Stored input message {} (role: {}) for session {}", messageId, msg.getRole(), sessionId),
                            error -> log.error("Failed to store input message {} for session {}", messageId, sessionId, error)
                        )
                );
        }

        // Store assistant response as a message
        List<Map<String, Object>> responseContent = new ArrayList<>();
        Map<String, Object> textBlock = new HashMap<>();
        textBlock.put("text", finalAnswer);
        responseContent.add(textBlock);

        int responseMessageId = getNextMessageId(sessionId);
        agenticMemory
            .saveMessageInStrandsFormat(
                "assistant",
                responseContent,
                responseMessageId,
                ActionListener
                    .wrap(
                        response -> log.info("Stored assistant response message {} for session {}", responseMessageId, sessionId),
                        error -> log
                            .error("Failed to store assistant response message {} for session {}", responseMessageId, sessionId, error)
                    )
            );

        log.info("Stored V2 conversation in Strands format: session={}, total_messages={}", sessionId, responseMessageId + 1);
    }

    /**
     * Get the next message_id for a session.
     * Message IDs are sequential starting from 0 within each session.
     */
    private int getNextMessageId(String sessionId) {
        return sessionMessageCounters.computeIfAbsent(sessionId, k -> new AtomicInteger(0)).getAndIncrement();
    }

    /**
     * Extract answer text from model output.
     * Handles different response structures from various LLM providers:
     * - OpenAI: choices[0].message.content
     * - Bedrock: output.message.content[0].text
     * - Others: response, content, text, or message.content
     */
    private String extractAnswerFromOutput(ModelTensorOutput output) {
        if (output == null || output.getMlModelOutputs() == null || output.getMlModelOutputs().isEmpty()) {
            return "";
        }

        ModelTensors tensors = output.getMlModelOutputs().get(0);
        if (tensors.getMlModelTensors() == null || tensors.getMlModelTensors().isEmpty()) {
            return "";
        }

        ModelTensor tensor = tensors.getMlModelTensors().get(0);
        Map<String, ?> dataAsMap = tensor.getDataAsMap();

        // Try to extract from OpenAI format: choices[0].message.content
        if (dataAsMap.containsKey("choices")) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> choices = (List<Map<String, Object>>) dataAsMap.get("choices");
            if (choices != null && !choices.isEmpty()) {
                Map<String, Object> firstChoice = choices.get(0);
                if (firstChoice != null && firstChoice.containsKey("message")) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> messageMap = (Map<String, Object>) firstChoice.get("message");
                    if (messageMap != null && messageMap.containsKey("content")) {
                        Object content = messageMap.get("content");
                        if (content instanceof String) {
                            return (String) content;
                        }
                        // Handle content as array (shouldn't happen for OpenAI responses, but just in case)
                        if (content instanceof List) {
                            @SuppressWarnings("unchecked")
                            List<Map<String, Object>> contentList = (List<Map<String, Object>>) content;
                            if (!contentList.isEmpty() && contentList.get(0).containsKey("text")) {
                                return (String) contentList.get(0).get("text");
                            }
                        }
                    }
                }
            }
        }

        // Try to extract from nested structure: output.message.content[0].text (Bedrock format)
        if (dataAsMap.containsKey("output")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> outputMap = (Map<String, Object>) dataAsMap.get("output");
            if (outputMap != null && outputMap.containsKey("message")) {
                @SuppressWarnings("unchecked")
                Map<String, Object> messageMap = (Map<String, Object>) outputMap.get("message");
                if (messageMap != null && messageMap.containsKey("content")) {
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> contentList = (List<Map<String, Object>>) messageMap.get("content");
                    if (contentList != null && !contentList.isEmpty()) {
                        Map<String, Object> firstContent = contentList.get(0);
                        if (firstContent != null && firstContent.containsKey("text")) {
                            return (String) firstContent.get("text");
                        }
                    }
                }
            }
        }

        // Try direct response key (OpenAI format)
        if (dataAsMap.containsKey("response")) {
            Object response = dataAsMap.get("response");
            return response != null ? response.toString() : "";
        }

        // Try direct content key
        if (dataAsMap.containsKey("content")) {
            Object content = dataAsMap.get("content");
            // If content is a list, extract first text
            if (content instanceof List) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> contentList = (List<Map<String, Object>>) content;
                if (!contentList.isEmpty() && contentList.get(0).containsKey("text")) {
                    return (String) contentList.get(0).get("text");
                }
            }
            return content != null ? content.toString() : "";
        }

        // Try direct text key
        if (dataAsMap.containsKey("text")) {
            Object text = dataAsMap.get("text");
            return text != null ? text.toString() : "";
        }

        // Try message.content[0].text (alternative structure)
        if (dataAsMap.containsKey("message")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> messageMap = (Map<String, Object>) dataAsMap.get("message");
            if (messageMap != null && messageMap.containsKey("content")) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> contentList = (List<Map<String, Object>>) messageMap.get("content");
                if (contentList != null && !contentList.isEmpty()) {
                    Map<String, Object> firstContent = contentList.get(0);
                    if (firstContent != null && firstContent.containsKey("text")) {
                        return (String) firstContent.get("text");
                    }
                }
            }
        }

        // Fallback: log warning and return JSON representation
        log.warn("Unable to extract clean text from LLM response, using full JSON. Keys available: {}", dataAsMap.keySet());
        return gson.toJson(dataAsMap);
    }

    /**
     * Enrich current input with conversation history from memory.
     * Converts Strands format messages back to AgentInput Message objects.
     */
    private AgentInput enrichInputWithHistory(AgentInput currentInput, List<Map<String, Object>> history, String questionText) {
        try {
            List<org.opensearch.ml.common.input.execute.agent.Message> allMessages = new ArrayList<>();

            // Convert history messages from Strands format
            for (Map<String, Object> turn : history) {
                @SuppressWarnings("unchecked")
                Map<String, Object> structuredData = (Map<String, Object>) turn.get("structured_data");
                if (structuredData == null) {
                    continue;
                }

                @SuppressWarnings("unchecked")
                Map<String, Object> messageData = (Map<String, Object>) structuredData.get("message");
                if (messageData == null) {
                    continue;
                }

                String role = (String) messageData.get("role");
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> contentList = (List<Map<String, Object>>) messageData.get("content");

                if (role != null && contentList != null && !contentList.isEmpty()) {
                    // Convert to ContentBlocks first
                    List<org.opensearch.ml.common.input.execute.agent.ContentBlock> contentBlocks = convertStrandsContentToBlocks(
                        contentList
                    );

                    // Check if this message has tool interactions that need special handling
                    List<org.opensearch.ml.common.input.execute.agent.Message> convertedMessages = convertToolInteractionsToMessages(
                        role,
                        contentBlocks,
                        contentList
                    );
                    allMessages.addAll(convertedMessages);
                }
            }

            // Append current input as new user message
            if (currentInput != null) {
                switch (currentInput.getInputType()) {
                    case TEXT:
                        String text = (String) currentInput.getInput();
                        allMessages.add(createUserMessage(text));
                        break;

                    case CONTENT_BLOCKS:
                        @SuppressWarnings("unchecked")
                        List<org.opensearch.ml.common.input.execute.agent.ContentBlock> blocks =
                            (List<org.opensearch.ml.common.input.execute.agent.ContentBlock>) currentInput.getInput();
                        allMessages.add(new org.opensearch.ml.common.input.execute.agent.Message("user", blocks));
                        break;

                    case MESSAGES:
                        // Already has messages - add only the new messages that aren't in history
                        @SuppressWarnings("unchecked")
                        List<org.opensearch.ml.common.input.execute.agent.Message> inputMessages =
                            (List<org.opensearch.ml.common.input.execute.agent.Message>) currentInput.getInput();
                        allMessages.addAll(inputMessages);
                        break;
                }
            } else if (questionText != null && !questionText.isEmpty()) {
                // Fallback to question text
                allMessages.add(createUserMessage(questionText));
            }

            // Return as MESSAGES type AgentInput
            return new AgentInput(allMessages);
        } catch (Exception e) {
            log.error("Failed to enrich input with conversation history", e);
            return currentInput;
        }
    }

    /**
     * Convert messages with tool interactions to proper Message format
     * Handles assistant messages with toolUse blocks and user messages with toolResult blocks
     */
    private List<org.opensearch.ml.common.input.execute.agent.Message> convertToolInteractionsToMessages(
        String role,
        List<org.opensearch.ml.common.input.execute.agent.ContentBlock> contentBlocks,
        List<Map<String, Object>> originalContent
    ) {
        List<org.opensearch.ml.common.input.execute.agent.Message> messages = new ArrayList<>();

        // Check for TOOL_USE blocks (assistant messages)
        boolean hasToolUse = contentBlocks.stream().anyMatch(b -> b.getType() == ContentType.TOOL_USE);

        // Check for TOOL_RESULT blocks (user messages that should be tool role)
        boolean hasToolResult = contentBlocks.stream().anyMatch(b -> b.getType() == ContentType.TOOL_RESULT);

        if ("assistant".equalsIgnoreCase(role) && hasToolUse) {
            // Assistant message with tool use - create Message with toolCalls
            List<org.opensearch.ml.common.input.execute.agent.ToolCall> toolCalls = new ArrayList<>();
            List<org.opensearch.ml.common.input.execute.agent.ContentBlock> regularContent = new ArrayList<>();

            for (org.opensearch.ml.common.input.execute.agent.ContentBlock block : contentBlocks) {
                if (block.getType() == ContentType.TOOL_USE) {
                    // Convert to ToolCall
                    Map<String, Object> toolUse = block.getToolUse();
                    String toolUseId = (String) toolUse.get("toolUseId");
                    String name = (String) toolUse.get("name");
                    @SuppressWarnings("unchecked")
                    Map<String, Object> input = (Map<String, Object>) toolUse.get("input");

                    org.opensearch.ml.common.input.execute.agent.ToolCall toolCall =
                        new org.opensearch.ml.common.input.execute.agent.ToolCall();
                    toolCall.setId(toolUseId);
                    toolCall.setType("function");
                    org.opensearch.ml.common.input.execute.agent.ToolCall.ToolFunction function =
                        new org.opensearch.ml.common.input.execute.agent.ToolCall.ToolFunction();
                    function.setName(name);
                    function.setArguments(gson.toJson(input));  // Arguments as JSON string
                    toolCall.setFunction(function);
                    toolCalls.add(toolCall);
                } else {
                    // Regular content (text, etc.)
                    regularContent.add(block);
                }
            }

            org.opensearch.ml.common.input.execute.agent.Message message = new org.opensearch.ml.common.input.execute.agent.Message(
                role,
                regularContent
            );
            message.setToolCalls(toolCalls);
            messages.add(message);

        } else if ("user".equalsIgnoreCase(role) && hasToolResult) {
            // User message with tool results - create separate tool role messages
            for (org.opensearch.ml.common.input.execute.agent.ContentBlock block : contentBlocks) {
                if (block.getType() == ContentType.TOOL_RESULT) {
                    Map<String, Object> toolResult = block.getToolResult();
                    String toolUseId = (String) toolResult.get("toolUseId");
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> contentList = (List<Map<String, Object>>) toolResult.get("content");

                    // Extract text from content
                    StringBuilder contentText = new StringBuilder();
                    for (Map<String, Object> contentItem : contentList) {
                        if (contentItem.containsKey("text")) {
                            contentText.append(contentItem.get("text"));
                        }
                    }

                    // Create tool role message with text content
                    org.opensearch.ml.common.input.execute.agent.ContentBlock textBlock =
                        new org.opensearch.ml.common.input.execute.agent.ContentBlock();
                    textBlock.setType(ContentType.TEXT);
                    textBlock.setText(contentText.toString());

                    org.opensearch.ml.common.input.execute.agent.Message message = new org.opensearch.ml.common.input.execute.agent.Message(
                        "tool",
                        List.of(textBlock)
                    );
                    message.setToolCallId(toolUseId);
                    messages.add(message);
                }
            }

        } else {
            // Regular message without tool interactions
            messages.add(new org.opensearch.ml.common.input.execute.agent.Message(role, contentBlocks));
        }

        return messages;
    }

    /**
     * Convert Strands content format back to ContentBlock objects
     */
    private List<org.opensearch.ml.common.input.execute.agent.ContentBlock> convertStrandsContentToBlocks(
        List<Map<String, Object>> strandsContent
    ) {
        List<org.opensearch.ml.common.input.execute.agent.ContentBlock> blocks = new ArrayList<>();

        for (Map<String, Object> contentItem : strandsContent) {
            if (contentItem.containsKey("text")) {
                // Text block
                String text = (String) contentItem.get("text");
                org.opensearch.ml.common.input.execute.agent.ContentBlock block =
                    new org.opensearch.ml.common.input.execute.agent.ContentBlock();
                block.setType(ContentType.TEXT);
                block.setText(text);
                blocks.add(block);
            } else if (contentItem.containsKey("image")) {
                // Image block
                @SuppressWarnings("unchecked")
                Map<String, Object> imageData = (Map<String, Object>) contentItem.get("image");
                String format = (String) imageData.get("format");

                @SuppressWarnings("unchecked")
                Map<String, Object> source = (Map<String, Object>) imageData.get("source");

                org.opensearch.ml.common.input.execute.agent.ImageContent imageContent =
                    new org.opensearch.ml.common.input.execute.agent.ImageContent();
                imageContent.setFormat(format);

                if (source.containsKey("bytes")) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> bytes = (Map<String, Object>) source.get("bytes");
                    String data = (String) bytes.get("data");
                    imageContent.setType(org.opensearch.ml.common.input.execute.agent.SourceType.BASE64);
                    imageContent.setData(data);
                } else if (source.containsKey("url")) {
                    String url = (String) source.get("url");
                    imageContent.setType(org.opensearch.ml.common.input.execute.agent.SourceType.URL);
                    imageContent.setData(url);
                }

                org.opensearch.ml.common.input.execute.agent.ContentBlock block =
                    new org.opensearch.ml.common.input.execute.agent.ContentBlock();
                block.setType(ContentType.IMAGE);
                block.setImage(imageContent);
                blocks.add(block);
            } else if (contentItem.containsKey("toolUse")) {
                // Tool use block
                @SuppressWarnings("unchecked")
                Map<String, Object> toolUseData = (Map<String, Object>) contentItem.get("toolUse");

                org.opensearch.ml.common.input.execute.agent.ContentBlock block =
                    new org.opensearch.ml.common.input.execute.agent.ContentBlock();
                block.setType(ContentType.TOOL_USE);
                block.setToolUse(toolUseData);
                blocks.add(block);
            } else if (contentItem.containsKey("toolResult")) {
                // Tool result block
                @SuppressWarnings("unchecked")
                Map<String, Object> toolResultData = (Map<String, Object>) contentItem.get("toolResult");

                org.opensearch.ml.common.input.execute.agent.ContentBlock block =
                    new org.opensearch.ml.common.input.execute.agent.ContentBlock();
                block.setType(ContentType.TOOL_RESULT);
                block.setToolResult(toolResultData);
                blocks.add(block);
            }
            // Add support for document and video if needed
        }

        return blocks;
    }

    /**
     * Create a simple user message with text content
     */
    private org.opensearch.ml.common.input.execute.agent.Message createUserMessage(String text) {
        org.opensearch.ml.common.input.execute.agent.ContentBlock textBlock =
            new org.opensearch.ml.common.input.execute.agent.ContentBlock();
        textBlock.setType(ContentType.TEXT);
        textBlock.setText(text);

        List<org.opensearch.ml.common.input.execute.agent.ContentBlock> blocks = new ArrayList<>();
        blocks.add(textBlock);

        return new org.opensearch.ml.common.input.execute.agent.Message("user", blocks);
    }

    /**
     * Convert AgentInput to Strands message format.
     * Returns list of messages to be stored sequentially with message_id.
     * If agentInput is null, returns empty list (caller should handle this case).
     */
    private List<StrandsMessage> convertAgentInputToStrandsMessages(AgentInput agentInput) {
        List<StrandsMessage> messages = new ArrayList<>();

        if (agentInput == null) {
            log.debug("AgentInput is null, cannot convert to Strands messages");
            return messages;
        }

        switch (agentInput.getInputType()) {
            case TEXT:
                // Convert text to single user message
                String text = (String) agentInput.getInput();
                messages.add(createTextMessage("user", text));
                break;

            case CONTENT_BLOCKS:
                // Convert content blocks to single user message
                @SuppressWarnings("unchecked")
                List<org.opensearch.ml.common.input.execute.agent.ContentBlock> blocks =
                    (List<org.opensearch.ml.common.input.execute.agent.ContentBlock>) agentInput.getInput();
                messages.add(createContentBlocksMessage("user", blocks));
                break;

            case MESSAGES:
                // Already in messages format - convert each message
                @SuppressWarnings("unchecked")
                List<org.opensearch.ml.common.input.execute.agent.Message> inputMessages =
                    (List<org.opensearch.ml.common.input.execute.agent.Message>) agentInput.getInput();
                for (org.opensearch.ml.common.input.execute.agent.Message msg : inputMessages) {
                    messages.add(convertMessageToStrands(msg));
                }
                break;
        }

        return messages;
    }

    /**
     * Create a text-only message in Strands format
     */
    private StrandsMessage createTextMessage(String role, String text) {
        List<Map<String, Object>> content = new ArrayList<>();
        Map<String, Object> textBlock = new HashMap<>();
        textBlock.put("text", text);
        content.add(textBlock);

        return new StrandsMessage(role, content);
    }

    /**
     * Convert ContentBlock list to Strands format message
     */
    private StrandsMessage createContentBlocksMessage(String role, List<org.opensearch.ml.common.input.execute.agent.ContentBlock> blocks) {
        List<Map<String, Object>> content = new ArrayList<>();

        for (org.opensearch.ml.common.input.execute.agent.ContentBlock block : blocks) {
            Map<String, Object> contentBlock = new HashMap<>();

            switch (block.getType()) {
                case TEXT:
                    contentBlock.put("text", block.getText());
                    break;

                case IMAGE:
                    Map<String, Object> image = new HashMap<>();
                    org.opensearch.ml.common.input.execute.agent.ImageContent img = block.getImage();
                    image.put("format", img.getFormat());

                    Map<String, Object> source = new HashMap<>();
                    if (img.getType() == org.opensearch.ml.common.input.execute.agent.SourceType.BASE64) {
                        Map<String, Object> bytes = new HashMap<>();
                        bytes.put("__bytes_encoded__", true);
                        bytes.put("data", img.getData());
                        source.put("bytes", bytes);
                    } else if (img.getType() == org.opensearch.ml.common.input.execute.agent.SourceType.URL) {
                        source.put("url", img.getData());
                    }
                    image.put("source", source);

                    contentBlock.put("image", image);
                    break;

                case DOCUMENT:
                    Map<String, Object> document = new HashMap<>();
                    org.opensearch.ml.common.input.execute.agent.DocumentContent doc = block.getDocument();
                    document.put("format", doc.getFormat());

                    Map<String, Object> docSource = new HashMap<>();
                    if (doc.getType() == org.opensearch.ml.common.input.execute.agent.SourceType.BASE64) {
                        Map<String, Object> bytes = new HashMap<>();
                        bytes.put("__bytes_encoded__", true);
                        bytes.put("data", doc.getData());
                        docSource.put("bytes", bytes);
                    } else if (doc.getType() == org.opensearch.ml.common.input.execute.agent.SourceType.URL) {
                        docSource.put("url", doc.getData());
                    }
                    document.put("source", docSource);

                    contentBlock.put("document", document);
                    break;

                case VIDEO:
                    Map<String, Object> video = new HashMap<>();
                    org.opensearch.ml.common.input.execute.agent.VideoContent vid = block.getVideo();
                    video.put("format", vid.getFormat());

                    Map<String, Object> vidSource = new HashMap<>();
                    if (vid.getType() == org.opensearch.ml.common.input.execute.agent.SourceType.BASE64) {
                        Map<String, Object> bytes = new HashMap<>();
                        bytes.put("__bytes_encoded__", true);
                        bytes.put("data", vid.getData());
                        vidSource.put("bytes", bytes);
                    } else if (vid.getType() == org.opensearch.ml.common.input.execute.agent.SourceType.URL) {
                        vidSource.put("url", vid.getData());
                    }
                    video.put("source", vidSource);

                    contentBlock.put("video", video);
                    break;
            }

            content.add(contentBlock);
        }

        return new StrandsMessage(role, content);
    }

    /**
     * Convert Message to Strands format
     */
    private StrandsMessage convertMessageToStrands(org.opensearch.ml.common.input.execute.agent.Message message) {
        List<Map<String, Object>> content = new ArrayList<>();

        for (org.opensearch.ml.common.input.execute.agent.ContentBlock block : message.getContent()) {
            Map<String, Object> contentBlock = new HashMap<>();

            switch (block.getType()) {
                case TEXT:
                    contentBlock.put("text", block.getText());
                    break;

                case IMAGE:
                    Map<String, Object> image = new HashMap<>();
                    org.opensearch.ml.common.input.execute.agent.ImageContent img = block.getImage();
                    image.put("format", img.getFormat());

                    Map<String, Object> source = new HashMap<>();
                    if (img.getType() == org.opensearch.ml.common.input.execute.agent.SourceType.BASE64) {
                        Map<String, Object> bytes = new HashMap<>();
                        bytes.put("__bytes_encoded__", true);
                        bytes.put("data", img.getData());
                        source.put("bytes", bytes);
                    } else if (img.getType() == org.opensearch.ml.common.input.execute.agent.SourceType.URL) {
                        source.put("url", img.getData());
                    }
                    image.put("source", source);

                    contentBlock.put("image", image);
                    break;

                case DOCUMENT:
                    Map<String, Object> document = new HashMap<>();
                    org.opensearch.ml.common.input.execute.agent.DocumentContent doc = block.getDocument();
                    document.put("format", doc.getFormat());

                    Map<String, Object> docSource = new HashMap<>();
                    if (doc.getType() == org.opensearch.ml.common.input.execute.agent.SourceType.BASE64) {
                        Map<String, Object> bytes = new HashMap<>();
                        bytes.put("__bytes_encoded__", true);
                        bytes.put("data", doc.getData());
                        docSource.put("bytes", bytes);
                    } else if (doc.getType() == org.opensearch.ml.common.input.execute.agent.SourceType.URL) {
                        docSource.put("url", doc.getData());
                    }
                    document.put("source", docSource);

                    contentBlock.put("document", document);
                    break;

                case VIDEO:
                    Map<String, Object> video = new HashMap<>();
                    org.opensearch.ml.common.input.execute.agent.VideoContent vid = block.getVideo();
                    video.put("format", vid.getFormat());

                    Map<String, Object> vidSource = new HashMap<>();
                    if (vid.getType() == org.opensearch.ml.common.input.execute.agent.SourceType.BASE64) {
                        Map<String, Object> bytes = new HashMap<>();
                        bytes.put("__bytes_encoded__", true);
                        bytes.put("data", vid.getData());
                        vidSource.put("bytes", bytes);
                    } else if (vid.getType() == org.opensearch.ml.common.input.execute.agent.SourceType.URL) {
                        vidSource.put("url", vid.getData());
                    }
                    video.put("source", vidSource);

                    contentBlock.put("video", video);
                    break;
            }

            content.add(contentBlock);
        }

        return new StrandsMessage(message.getRole(), content);
    }

    /**
     * Helper class for Strands message structure
     */
    @Data
    private static class StrandsMessage {
        private final String role;
        private final List<Map<String, Object>> content;

        public StrandsMessage(String role, List<Map<String, Object>> content) {
            this.role = role;
            this.content = content;
        }
    }
}
