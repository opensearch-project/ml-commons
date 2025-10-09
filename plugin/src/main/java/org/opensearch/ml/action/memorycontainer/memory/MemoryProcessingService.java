/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.memorycontainer.memory;

import static org.opensearch.common.xcontent.json.JsonXContent.jsonXContent;
import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.DEFAULT_UPDATE_MEMORY_PROMPT;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.JSON_ENFORCEMENT_MESSAGE;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.LLM_ID_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.MEMORY_DECISION_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.SEMANTIC_FACTS_EXTRACTION_PROMPT;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.SESSION_SUMMARY_PROMPT;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.SUMMARY_FACTS_EXTRACTION_PROMPT;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.USER_PREFERENCE_FACTS_EXTRACTION_PROMPT;
import static org.opensearch.ml.common.utils.StringUtils.getParameterMap;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.opensearch.OpenSearchException;
import org.opensearch.common.xcontent.LoggingDeprecationHandler;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.dataset.remote.RemoteInferenceInputDataSet;
import org.opensearch.ml.common.input.MLInput;
import org.opensearch.ml.common.memorycontainer.MemoryConfiguration;
import org.opensearch.ml.common.memorycontainer.MemoryDecision;
import org.opensearch.ml.common.memorycontainer.MemoryDecisionRequest;
import org.opensearch.ml.common.memorycontainer.MemoryStrategy;
import org.opensearch.ml.common.memorycontainer.MemoryStrategyType;
import org.opensearch.ml.common.output.MLOutput;
import org.opensearch.ml.common.output.model.ModelTensorOutput;
import org.opensearch.ml.common.output.model.ModelTensors;
import org.opensearch.ml.common.transport.MLTaskResponse;
import org.opensearch.ml.common.transport.memorycontainer.memory.MessageInput;
import org.opensearch.ml.common.transport.prediction.MLPredictionTaskAction;
import org.opensearch.ml.common.transport.prediction.MLPredictionTaskRequest;
import org.opensearch.ml.common.utils.LlmResultPathGenerator;
import org.opensearch.ml.common.utils.StringUtils;
import org.opensearch.ml.common.utils.message.MessageFormatter;
import org.opensearch.ml.common.utils.message.MessageFormatterFactory;
import org.opensearch.ml.engine.processor.MLProcessorType;
import org.opensearch.ml.engine.processor.ProcessorChain;
import org.opensearch.ml.model.MLModelCacheHelper;
import org.opensearch.transport.client.Client;

import com.jayway.jsonpath.JsonPath;

import lombok.extern.log4j.Log4j2;

@Log4j2
public class MemoryProcessingService {

    /**
     * Smart fallback path for Claude models when auto-generation fails.
     * This path works with all Claude models (v2, v3, Sonnet 4, etc.) that use the Messages API.
     */
    public static final String CLAUDE_SYSTEM_PROMPT_PATH = "$.content[0].text";

    private final Client client;
    private final NamedXContentRegistry xContentRegistry;
    private final ProcessorChain extractJsonProcessorChain;
    private final MLModelCacheHelper modelCacheHelper;

    public MemoryProcessingService(Client client, NamedXContentRegistry xContentRegistry, MLModelCacheHelper modelCacheHelper) {
        this.client = client;
        this.xContentRegistry = xContentRegistry;
        this.modelCacheHelper = modelCacheHelper;
        List<Map<String, Object>> processorConfigs = new ArrayList<>();
        processorConfigs.add(Map.of("type", MLProcessorType.EXTRACT_JSON.getValue(), "extract_type", "object"));
        this.extractJsonProcessorChain = new ProcessorChain(processorConfigs);
    }

    public void runMemoryStrategy(
        MemoryStrategy strategy,
        List<MessageInput> messages,
        MemoryConfiguration memoryConfig,
        ActionListener<List<String>> listener
    ) {
        MemoryStrategyType type = strategy.getType();
        if (type == MemoryStrategyType.SEMANTIC || type == MemoryStrategyType.USER_PREFERENCE || type == MemoryStrategyType.SUMMARY) {
            extractFactsFromConversation(messages, strategy, memoryConfig, listener);
        } else {
            log.error("Unsupported memory strategy type: {}", type);
            listener.onFailure(new IllegalArgumentException("Unsupported memory strategy type: " + type));
        }
    }

    public void extractFactsFromConversation(
        List<MessageInput> messages,
        MemoryStrategy strategy,
        MemoryConfiguration memoryConfig,
        ActionListener<List<String>> listener
    ) {
        String llmModelId = getEffectiveLlmId(strategy, memoryConfig);
        if (llmModelId == null) {
            log.debug("No LLM model configured for fact extraction, skipping");
            listener.onResponse(new ArrayList<>());
            return;
        }

        boolean isOverride = strategy != null
            && strategy.getStrategyConfig() != null
            && strategy.getStrategyConfig().containsKey(LLM_ID_FIELD);
        log
            .debug(
                "Extracting long-term memory facts using LLM model: {} (strategy: {}, source: {})",
                llmModelId,
                strategy != null ? strategy.getType() : "unknown",
                isOverride ? "strategy-override" : "container-config"
            );

        // Get appropriate formatter for the model
        MessageFormatter formatter = getFormatterForModel(llmModelId);
        log.debug("Using {} for model {}", formatter.getClass().getSimpleName(), llmModelId);

        // Determine system prompt (default or custom)
        String systemPrompt = determineSystemPrompt(strategy, listener);
        if (systemPrompt == null) {
            // Validation failed, listener already called
            return;
        }

        // Build full message list with additional messages from config
        List<MessageInput> allMessages = buildFullMessageList(messages, strategy);

        // Format the request using the appropriate formatter
        Map<String, String> stringParameters = formatter.formatRequest(systemPrompt, allMessages, strategy.getStrategyConfig());

        // Continue with existing MLInput building and execution
        MLInput mlInput = MLInput
            .builder()
            .algorithm(FunctionName.REMOTE)
            .inputDataset(RemoteInferenceInputDataSet.builder().parameters(stringParameters).build())
            .build();

        MLPredictionTaskRequest predictionRequest = MLPredictionTaskRequest.builder().modelId(llmModelId).mlInput(mlInput).build();

        client.execute(MLPredictionTaskAction.INSTANCE, predictionRequest, ActionListener.wrap(response -> {
            try {
                log.debug("Received LLM response, parsing facts...");
                MLOutput mlOutput = response.getOutput();
                List<String> facts = parseFactsFromLLMResponse(strategy, mlOutput, llmModelId);
                log.debug("Extracted {} facts from LLM response", facts.size());
                listener.onResponse(facts);
            } catch (Exception e) {
                log.error("Failed to parse facts from LLM response", e);
                listener.onFailure(new IllegalArgumentException("Failed to parse facts from LLM response", e));
            }
        }, e -> {
            log.error("Failed to call LLM for fact extraction", e);
            listener.onFailure(new OpenSearchException("Failed to extract facts using LLM model: " + e.getMessage(), e));
        }));
    }

    private static MessageInput getMessageInput(String userPrompt) {
        List<Map<String, Object>> content = new ArrayList<>();
        content.add(Map.of("text", userPrompt, "type", "text"));
        return MessageInput.builder().role("user").content(content).build();
    }

    public void makeMemoryDecisions(
        List<String> extractedFacts,
        List<FactSearchResult> allSearchResults,
        MemoryStrategy strategy,
        MemoryConfiguration memoryConfig,
        ActionListener<List<MemoryDecision>> listener
    ) {
        String llmModelId = getEffectiveLlmId(strategy, memoryConfig);
        if (llmModelId == null) {
            log.error("LLM model is required for memory decisions but not configured");
            listener.onFailure(new IllegalStateException("LLM model is required for memory decisions"));
            return;
        }

        boolean isOverride = strategy != null
            && strategy.getStrategyConfig() != null
            && strategy.getStrategyConfig().containsKey(LLM_ID_FIELD);
        log
            .debug(
                "Making memory decisions using LLM model: {} (strategy: {}, source: {}, facts: {}, similar-memories: {})",
                llmModelId,
                strategy != null ? strategy.getType() : "unknown",
                isOverride ? "strategy-override" : "container-config",
                extractedFacts.size(),
                allSearchResults.size()
            );

        // Get appropriate formatter for the model
        MessageFormatter formatter = getFormatterForModel(llmModelId);
        log.debug("Using {} for model {}", formatter.getClass().getSimpleName(), llmModelId);

        List<MemoryDecisionRequest.OldMemory> oldMemories = new ArrayList<>();
        for (FactSearchResult result : allSearchResults) {
            oldMemories
                .add(MemoryDecisionRequest.OldMemory.builder().id(result.getId()).text(result.getText()).score(result.getScore()).build());
        }

        MemoryDecisionRequest decisionRequest = MemoryDecisionRequest
            .builder()
            .oldMemory(oldMemories)
            .retrievedFacts(extractedFacts)
            .build();

        String decisionRequestJson = decisionRequest.toJsonString();

        // Create user message with decision request JSON
        List<MessageInput> messages = new ArrayList<>();
        List<Map<String, Object>> content = new ArrayList<>();
        content.add(Map.of("type", "text", "text", decisionRequestJson));
        messages.add(MessageInput.builder().role("user").content(content).build());

        // Format the request using the appropriate formatter
        Map<String, String> stringParameters = formatter.formatRequest(DEFAULT_UPDATE_MEMORY_PROMPT, messages, null);

        log
            .debug(
                "Making memory decisions for {} extracted facts and {} existing memories",
                extractedFacts.size(),
                allSearchResults.size()
            );

        RemoteInferenceInputDataSet inputDataSet = RemoteInferenceInputDataSet.builder().parameters(stringParameters).build();
        MLInput mlInput = MLInput.builder().algorithm(FunctionName.REMOTE).inputDataset(inputDataSet).build();

        MLPredictionTaskRequest predictionRequest = MLPredictionTaskRequest.builder().modelId(llmModelId).mlInput(mlInput).build();

        client.execute(MLPredictionTaskAction.INSTANCE, predictionRequest, ActionListener.wrap(response -> {
            try {
                List<MemoryDecision> decisions = parseMemoryDecisions(response, llmModelId);
                log.debug("LLM made {} memory decisions", decisions.size());
                listener.onResponse(decisions);
            } catch (Exception e) {
                log.error("Failed to parse memory decisions from LLM response", e);
                listener.onFailure(e);
            }
        }, e -> {
            log.error("Failed to get memory decisions from LLM", e);
            listener.onFailure(e);
        }));
    }

    private List<String> parseFactsFromLLMResponse(MemoryStrategy strategy, MLOutput mlOutput, String modelId) {
        List<String> facts = new ArrayList<>();

        if (!(mlOutput instanceof ModelTensorOutput)) {
            log.warn("Unexpected ML output type for LLM response: {}", mlOutput != null ? mlOutput.getClass().getName() : "null");
            return facts;
        }

        ModelTensorOutput tensorOutput = (ModelTensorOutput) mlOutput;
        if (tensorOutput.getMlModelOutputs() == null || tensorOutput.getMlModelOutputs().isEmpty()) {
            log.warn("No model outputs found in LLM response");
            return facts;
        }

        ModelTensors modelTensors = tensorOutput.getMlModelOutputs().get(0);
        if (modelTensors.getMlModelTensors() == null || modelTensors.getMlModelTensors().isEmpty()) {
            log.warn("No model tensors found in LLM response");
            return facts;
        }

        for (int i = 0; i < modelTensors.getMlModelTensors().size(); i++) {
            Map<String, ?> dataMap = modelTensors.getMlModelTensors().get(i).getDataAsMap();

            // Auto-generate the result path based on model's output schema
            String llmResultPath = getAutoGeneratedLlmResultPath(modelId);
            log.debug("Using llm_result_path: {}", llmResultPath);

            // Gracefully handle malformed responses
            Object filterdResult = null;
            try {
                filterdResult = JsonPath.read(dataMap, llmResultPath);
            } catch (Exception e) {
                log.warn("Failed to extract LLM result from path '{}': {}. Skipping tensor {}", llmResultPath, e.getMessage(), i);
                continue;
            }

            String llmResult = null;
            if (filterdResult != null) {
                llmResult = StringUtils.toJson(filterdResult);
                llmResult = cleanMarkdownFromJson(llmResult);
            }
            if (llmResult != null) {
                llmResult = StringUtils.toJson(extractJsonProcessorChain.process(llmResult));
                try (XContentParser parser = jsonXContent.createParser(xContentRegistry, LoggingDeprecationHandler.INSTANCE, llmResult)) {
                    ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.nextToken(), parser);
                    while (parser.nextToken() != XContentParser.Token.END_OBJECT) {
                        String fieldName = parser.currentName();
                        if ("facts".equals(fieldName)) {
                            ensureExpectedToken(XContentParser.Token.START_ARRAY, parser.nextToken(), parser);
                            while (parser.nextToken() != XContentParser.Token.END_ARRAY) {
                                String fact = parser.text();
                                facts.add(fact);
                            }
                        } else {
                            parser.skipChildren();
                        }
                    }
                } catch (IOException e) {
                    log.error("Failed to extract content from dataMap", e);
                    throw new IllegalArgumentException("Failed to extract content from LLM response", e);
                }
            }
        }

        return facts;
    }

    private List<MemoryDecision> parseMemoryDecisions(MLTaskResponse response, String modelId) {
        try {
            MLOutput mlOutput = response.getOutput();
            if (!(mlOutput instanceof ModelTensorOutput)) {
                throw new IllegalStateException("Expected ModelTensorOutput but got: " + mlOutput.getClass().getSimpleName());
            }

            ModelTensorOutput tensorOutput = (ModelTensorOutput) mlOutput;
            List<ModelTensors> tensors = tensorOutput.getMlModelOutputs();
            if (tensors == null || tensors.isEmpty()) {
                throw new IllegalStateException("No model output tensors found");
            }

            Map<String, ?> dataMap = tensors.get(0).getMlModelTensors().get(0).getDataAsMap();

            // Auto-generate the result path based on model's output schema (same as fact extraction)
            String llmResultPath = getAutoGeneratedLlmResultPath(modelId);
            log.debug("Using llm_result_path: {}", llmResultPath);

            // Extract response content using the auto-generated path
            Object filteredResult = null;
            try {
                filteredResult = JsonPath.read(dataMap, llmResultPath);
            } catch (Exception e) {
                log
                    .error(
                        "Failed to extract response from path '{}': {}. Available keys: {}",
                        llmResultPath,
                        e.getMessage(),
                        dataMap.keySet()
                    );
                throw new IllegalStateException("Failed to extract response content from LLM output using path: " + llmResultPath, e);
            }

            if (filteredResult == null) {
                throw new IllegalStateException("Extracted null content from LLM output using path: " + llmResultPath);
            }

            // Convert to JSON string
            String responseContent = StringUtils.toJson(filteredResult);

            // Clean response content
            responseContent = cleanMarkdownFromJson(responseContent);

            List<MemoryDecision> decisions = new ArrayList<>();
            try (XContentParser parser = jsonXContent.createParser(xContentRegistry, LoggingDeprecationHandler.INSTANCE, responseContent)) {
                ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.nextToken(), parser);

                while (parser.nextToken() != XContentParser.Token.END_OBJECT) {
                    String fieldName = parser.currentName();
                    parser.nextToken();

                    if (MEMORY_DECISION_FIELD.equals(fieldName)) {
                        ensureExpectedToken(XContentParser.Token.START_ARRAY, parser.currentToken(), parser);
                        while (parser.nextToken() != XContentParser.Token.END_ARRAY) {
                            decisions.add(MemoryDecision.parse(parser));
                        }
                    } else {
                        parser.skipChildren();
                    }
                }
            }

            return decisions;
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse memory decisions", e);
        }
    }

    public void summarizeMessages(MemoryConfiguration configuration, List<MessageInput> messages, ActionListener<String> listener) {
        if (messages == null || messages.isEmpty()) {
            listener.onResponse("");
            return;
        }

        String llmModelId = configuration.getLlmId();
        if (llmModelId == null) {
            log.error("LLM model is required for summarization but not configured");
            listener.onFailure(new IllegalStateException("LLM model is required for summarization"));
            return;
        }

        // Get appropriate formatter for the model
        MessageFormatter formatter = getFormatterForModel(llmModelId);
        log.debug("Using {} for model {}", formatter.getClass().getSimpleName(), llmModelId);

        Map<String, Object> memoryParams = configuration.getParameters();
        Map<String, String> sessionParams = (Map<String, String>) memoryParams.getOrDefault("session", new HashMap<>());

        // Prepare additional parameters (non-formatter params)
        Map<String, String> additionalParams = new HashMap<>();
        additionalParams.putAll(getParameterMap(sessionParams));
        additionalParams.putIfAbsent("max_summary_size", "10");

        // Build message list
        List<MessageInput> allMessages = new ArrayList<>(messages);

        // Add user prompt if not in config
        if (!sessionParams.containsKey("user_prompt_message")) {
            MessageInput summaryPrompt = getMessageInput(
                "Please summarize our conversation, not exceed " + additionalParams.get("max_summary_size") + " words"
            );
            allMessages.add(summaryPrompt);
        }

        // Format the request using the appropriate formatter
        Map<String, String> formatterParams = formatter.formatRequest(SESSION_SUMMARY_PROMPT, allMessages, null);

        // Combine formatter params with additional params
        Map<String, String> stringParameters = new HashMap<>();
        stringParameters.putAll(formatterParams); // system_prompt and messages from formatter
        stringParameters.putAll(additionalParams); // max_summary_size, etc.

        RemoteInferenceInputDataSet inputDataSet = RemoteInferenceInputDataSet.builder().parameters(stringParameters).build();
        MLInput mlInput = MLInput.builder().algorithm(FunctionName.REMOTE).inputDataset(inputDataSet).build();
        MLPredictionTaskRequest predictionRequest = MLPredictionTaskRequest.builder().modelId(llmModelId).mlInput(mlInput).build();

        client.execute(MLPredictionTaskAction.INSTANCE, predictionRequest, ActionListener.wrap(response -> {
            try {
                String summary = parseSessionSummary((ModelTensorOutput) response.getOutput(), llmModelId);
                listener.onResponse(summary);
            } catch (Exception e) {
                log.error("Failed to parse session summary from LLM response", e);
                listener.onFailure(e);
            }
        }, e -> {
            log.error("Failed to get session summary from LLM", e);
            listener.onFailure(e);
        }));
    }

    private String parseSessionSummary(ModelTensorOutput output, String modelId) {
        Map<String, ?> dataAsMap = output.getMlModelOutputs().get(0).getMlModelTensors().get(0).getDataAsMap();
        // Auto-generate the result path based on model's output schema
        String llmResultPath = getAutoGeneratedLlmResultPath(modelId);

        // Gracefully handle malformed responses
        Object filterdResult = null;
        try {
            filterdResult = JsonPath.read(dataAsMap, llmResultPath);
        } catch (Exception e) {
            log.warn("Failed to extract session summary from path '{}': {}. Returning empty summary", llmResultPath, e.getMessage());
            return "";
        }

        String sessionSummary = null;
        if (filterdResult != null) {
            sessionSummary = StringUtils.toJson(filterdResult);
        }
        return sessionSummary;
    }

    private boolean validatePromptFormat(String prompt) {
        if (!prompt.contains("\"facts\"") || !prompt.contains("JSON")) {
            return false;
        }
        return true;
    }

    /**
     * Utility method to clean markdown formatting from JSON responses.
     * Strips ```json...``` and ```...``` wrappers that LLMs commonly add.
     */
    private String cleanMarkdownFromJson(String response) {
        if (response == null) {
            return null;
        }

        response = response.trim();

        // Remove ```json...``` wrapper
        if (response.startsWith("```json") && response.endsWith("```")) {
            response = response.substring(7, response.length() - 3).trim();
        }
        // Remove ```...``` wrapper
        else if (response.startsWith("```") && response.endsWith("```")) {
            response = response.substring(3, response.length() - 3).trim();
        }

        return response;
    }

    /**
     * Get appropriate message formatter for a model based on its input schema.
     *
     * @param modelId The model ID to get formatter for
     * @return MessageFormatter instance (never null, defaults to Claude)
     */
    private MessageFormatter getFormatterForModel(String modelId) {
        if (modelId == null) {
            log.debug("No model ID provided, using default Claude formatter");
            return MessageFormatterFactory.getClaudeFormatter();
        }

        try {
            Map<String, String> modelInterface = modelCacheHelper.getModelInterface(modelId);

            if (modelInterface != null && modelInterface.containsKey("input")) {
                String inputSchema = modelInterface.get("input");
                log.debug("Retrieved input schema for model {}", modelId);
                return MessageFormatterFactory.getFormatter(inputSchema);
            }

            log.debug("No input schema found for model {}, using default Claude formatter", modelId);
        } catch (Exception e) {
            log.warn("Failed to get formatter for model {}, using default: {}", modelId, e.getMessage());
        }

        return MessageFormatterFactory.getClaudeFormatter();
    }

    /**
     * Determine the system prompt to use (default or custom).
     *
     * @param strategy The memory strategy that may contain custom prompt
     * @param listener The listener to call on validation failure
     * @return The system prompt to use, or null if validation failed (listener already called)
     */
    private String determineSystemPrompt(MemoryStrategy strategy, ActionListener<List<String>> listener) {
        // Determine default prompt based on strategy type
        String defaultPrompt;
        MemoryStrategyType type = strategy.getType();
        if (type == MemoryStrategyType.USER_PREFERENCE) {
            defaultPrompt = USER_PREFERENCE_FACTS_EXTRACTION_PROMPT;
        } else if (type == MemoryStrategyType.SUMMARY) {
            defaultPrompt = SUMMARY_FACTS_EXTRACTION_PROMPT;
        } else {
            defaultPrompt = SEMANTIC_FACTS_EXTRACTION_PROMPT;
        }

        // Check for custom prompt in strategy config
        if (strategy.getStrategyConfig() == null || strategy.getStrategyConfig().isEmpty()) {
            return defaultPrompt;
        }

        Object customPrompt = strategy.getStrategyConfig().get("system_prompt");
        if (customPrompt == null || customPrompt.toString().isBlank()) {
            return defaultPrompt;
        }

        // Validate custom prompt format
        if (!validatePromptFormat(customPrompt.toString())) {
            log.error("Invalid custom prompt format - must specify JSON response format with 'facts' array");
            listener.onFailure(new IllegalArgumentException("Custom prompt must specify JSON response format with 'facts' array"));
            return null;
        }

        return customPrompt.toString();
    }

    /**
     * Build full message list including additional messages from config.
     *
     * @param messages Original messages from conversation
     * @param strategy Memory strategy that may contain additional messages
     * @return Full list of messages including any additional config messages
     */
    private List<MessageInput> buildFullMessageList(List<MessageInput> messages, MemoryStrategy strategy) {
        List<MessageInput> allMessages = new ArrayList<>(messages);

        Map<String, Object> strategyConfig = strategy.getStrategyConfig();

        // Add user prompt message if not in config
        if (strategyConfig == null || !strategyConfig.containsKey("user_prompt_message")) {
            MessageInput userPrompt = getMessageInput("Please extract information from our conversation so far");
            allMessages.add(userPrompt);
        }

        // Always add JSON enforcement message
        MessageInput enforcementMessage = getMessageInput(JSON_ENFORCEMENT_MESSAGE);
        allMessages.add(enforcementMessage);

        return allMessages;
    }

    /**
     * Get the effective LLM model ID, checking strategy override first, then falling back to memory config.
     *
     * @param strategy The memory strategy that may contain an llm_id override
     * @param memoryConfig The memory configuration with default llm_id
     * @return The effective LLM ID to use, or null if neither provides one
     */
    private String getEffectiveLlmId(MemoryStrategy strategy, MemoryConfiguration memoryConfig) {
        // Check strategy config for override
        if (strategy != null && strategy.getStrategyConfig() != null) {
            Object strategyLlmId = strategy.getStrategyConfig().get(LLM_ID_FIELD);
            if (strategyLlmId != null && !strategyLlmId.toString().isBlank()) {
                return strategyLlmId.toString();
            }
        }

        // Fall back to memory config
        return memoryConfig != null ? memoryConfig.getLlmId() : null;
    }

    /**
     * Auto-generates the JSONPath for extracting LLM output from model responses.
     * Uses the model's output schema to intelligently locate the text field.
     *
     * Algorithm:
     * 1. If modelId is null → return Claude default path
     * 2. Retrieve model's output schema from cache
     * 3. Use LlmResultPathGenerator to find x-llm-output marked field
     * 4. On any failure → return Claude default path (smart fallback)
     *
     * Examples:
     * - OpenAI models: $.choices[0].message.content
     * - Claude models: $.content[0].text
     * - Unknown/missing schema: $.content[0].text (safe default)
     *
     * @param modelId The model ID to generate path for (can be null)
     * @return JSONPath string for extracting LLM text output
     */
    private String getAutoGeneratedLlmResultPath(String modelId) {
        // No model specified → use Claude default
        if (modelId == null) {
            log.debug("No modelId provided, using Claude default path: {}", CLAUDE_SYSTEM_PROMPT_PATH);
            return CLAUDE_SYSTEM_PROMPT_PATH;
        }

        try {
            // Try to get model's output schema from cache
            Map<String, String> modelInterface = modelCacheHelper.getModelInterface(modelId);

            if (modelInterface != null && modelInterface.containsKey("output")) {
                String outputSchema = modelInterface.get("output");

                // Use LlmResultPathGenerator to find the LLM output field
                String generatedPath = LlmResultPathGenerator.generate(outputSchema);

                if (generatedPath != null && !generatedPath.isBlank()) {
                    log.debug("Auto-generated llm_result_path for model {}: {}", modelId, generatedPath);
                    return generatedPath;
                }
            }

            log
                .debug(
                    "Could not auto-generate path for model {} (schema not found or generation failed), using Claude default: {}",
                    modelId,
                    CLAUDE_SYSTEM_PROMPT_PATH
                );
        } catch (Exception e) {
            log
                .warn(
                    "Failed to auto-generate llm_result_path for model {}: {}. Using Claude default: {}",
                    modelId,
                    e.getMessage(),
                    CLAUDE_SYSTEM_PROMPT_PATH
                );
        }

        // Smart fallback: Claude path works for most models
        return CLAUDE_SYSTEM_PROMPT_PATH;
    }
}
