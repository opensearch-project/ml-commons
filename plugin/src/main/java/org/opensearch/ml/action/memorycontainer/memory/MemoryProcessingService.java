/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.memorycontainer.memory;

import static org.opensearch.common.xcontent.json.JsonXContent.jsonXContent;
import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.DEFAULT_LLM_RESULT_PATH;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.DEFAULT_UPDATE_MEMORY_PROMPT;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.JSON_ENFORCEMENT_MESSAGE;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.LLM_ID_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.LLM_RESULT_PATH_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.MEMORY_DECISION_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.SEMANTIC_FACTS_EXTRACTION_PROMPT;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.SESSION_SUMMARY_PROMPT;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.SUMMARY_FACTS_EXTRACTION_PROMPT;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.USER_PREFERENCE_FACTS_EXTRACTION_PROMPT;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.USER_PREFERENCE_JSON_ENFORCEMENT_MESSAGE;
import static org.opensearch.ml.common.utils.StringUtils.getParameterMap;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.opensearch.OpenSearchException;
import org.opensearch.OpenSearchStatusException;
import org.opensearch.common.xcontent.LoggingDeprecationHandler;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.XContentBuilder;
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
import org.opensearch.ml.common.utils.StringUtils;
import org.opensearch.ml.engine.processor.MLProcessorType;
import org.opensearch.ml.engine.processor.ProcessorChain;
import org.opensearch.ml.helper.MemoryContainerHelper;
import org.opensearch.transport.client.Client;

import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.PathNotFoundException;

import lombok.extern.log4j.Log4j2;

@Log4j2
public class MemoryProcessingService {

    private final Client client;
    private final NamedXContentRegistry xContentRegistry;
    private final ProcessorChain extractJsonProcessorChain;
    private final MemoryContainerHelper memoryContainerHelper;

    public MemoryProcessingService(Client client, NamedXContentRegistry xContentRegistry, MemoryContainerHelper memoryContainerHelper) {
        this.client = client;
        this.xContentRegistry = xContentRegistry;
        List<Map<String, Object>> processorConfigs = new ArrayList<>();
        processorConfigs.add(Map.of("type", MLProcessorType.EXTRACT_JSON.getValue(), "extract_type", "object"));
        this.extractJsonProcessorChain = new ProcessorChain(processorConfigs);
        this.memoryContainerHelper = memoryContainerHelper;
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

        Map<String, String> stringParameters = new HashMap<>();

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

        if (strategy.getStrategyConfig() == null || strategy.getStrategyConfig().isEmpty()) {
            stringParameters.put("system_prompt", defaultPrompt);
        } else {
            Object customPrompt = strategy.getStrategyConfig().get("system_prompt");
            if (customPrompt == null || customPrompt.toString().isBlank()) {
                stringParameters.put("system_prompt", defaultPrompt);
            } else if (!validatePromptFormat(customPrompt.toString())) {
                log.error("Invalid custom prompt format - must specify JSON response format with 'facts' array");
                listener.onFailure(new IllegalArgumentException("Custom prompt must specify JSON response format with 'facts' array"));
                return;
            } else {
                stringParameters.put("system_prompt", customPrompt.toString()); // use custom strategy
            }
        }

        try {
            // Always add JSON enforcement message for fact extraction
            String enforcementMsg = (strategy.getType() == MemoryStrategyType.USER_PREFERENCE)
                ? USER_PREFERENCE_JSON_ENFORCEMENT_MESSAGE
                : JSON_ENFORCEMENT_MESSAGE;
            MessageInput enforcementMessage = getMessageInput(enforcementMsg);
            // Create mutable copy to avoid UnsupportedOperationException
            List<MessageInput> mutableMessages = new ArrayList<>(messages);
            mutableMessages.add(enforcementMessage);
            String conversationJson = serializeMessagesToJson(mutableMessages);
            String userPrompt = "Analyze the following conversation and extract information:\n```json\n" + conversationJson + "\n```";
            stringParameters.put("user_prompt", userPrompt);
        } catch (Exception e) {
            log.error("Failed to build messages JSON", e);
            listener.onResponse(new ArrayList<>());
            return;
        }

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
                List<String> facts = parseFactsFromLLMResponse(strategy, memoryConfig, mlOutput);
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

        Map<String, String> stringParameters = new HashMap<>();
        stringParameters.put("system_prompt", DEFAULT_UPDATE_MEMORY_PROMPT);

        String decisionRequestJson = decisionRequest.toJsonString();
        String userPrompt = "Analyze the following old memories and newly extracted facts, then make memory decisions:\n```json\n"
            + decisionRequestJson
            + "\n```";
        stringParameters.put("user_prompt", userPrompt);

        try {

            log
                .debug(
                    "Making memory decisions for {} extracted facts and {} existing memories",
                    extractedFacts.size(),
                    allSearchResults.size()
                );

            RemoteInferenceInputDataSet inputDataSet = RemoteInferenceInputDataSet.builder().parameters(stringParameters).build();
            MLInput mlInput = MLInput.builder().algorithm(FunctionName.REMOTE).inputDataset(inputDataSet).build();

            MLPredictionTaskRequest predictionRequest = MLPredictionTaskRequest.builder().modelId(llmModelId).mlInput(mlInput).build();

            String llmResultPath = memoryContainerHelper.getLlmResultPath(strategy, memoryConfig);

            client.execute(MLPredictionTaskAction.INSTANCE, predictionRequest, ActionListener.wrap(response -> {
                try {
                    List<MemoryDecision> decisions = parseMemoryDecisions(llmResultPath, response);
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
        } catch (Exception e) {
            log.error("Failed to build memory decision request", e);
            listener.onFailure(e);
        }
    }

    private List<String> parseFactsFromLLMResponse(MemoryStrategy strategy, MemoryConfiguration memoryConfig, MLOutput mlOutput) {
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
            String llmResultPath = memoryContainerHelper.getLlmResultPath(strategy, memoryConfig);
            try {
                Object filterdResult = JsonPath.read(dataMap, llmResultPath);
                String llmResult = null;
                if (filterdResult != null) {
                    llmResult = StringUtils.toJson(filterdResult);
                }
                if (llmResult != null) {
                    llmResult = StringUtils.toJson(extractJsonProcessorChain.process(llmResult));
                    try (
                        XContentParser parser = jsonXContent.createParser(xContentRegistry, LoggingDeprecationHandler.INSTANCE, llmResult)
                    ) {
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
                        log.error("Failed to extract content from dataMap");
                        throw new IllegalArgumentException("Failed to extract content from LLM response", e);
                    }
                }
            } catch (PathNotFoundException e) {
                String reason = extractFirstSentence(e.getMessage());
                log.error("Failed to extract LLM result using path {}: {}", llmResultPath, reason);
                throw new OpenSearchStatusException(
                    "LLM predict result cannot be extracted with current llm_result_path with reason: "
                        + reason
                        + ". Please check either your llm configuration or your llm_result_path setting in memory container configuration",
                    RestStatus.NOT_FOUND
                );
            }
        }

        return facts;
    }

    private List<MemoryDecision> parseMemoryDecisions(String llmResultPath, MLTaskResponse response) {
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

            Map<String, ?> dataAsMap = tensors.get(0).getMlModelTensors().get(0).getDataAsMap();
            try {
                Object filterdResult = JsonPath.read(dataAsMap, llmResultPath);
                if (filterdResult == null) {
                    throw new IllegalStateException("No response content found in LLM output");
                }
                String responseContent = StringUtils.toJson(filterdResult);

                // Clean response content
                responseContent = StringUtils.toJson(extractJsonProcessorChain.process(responseContent));

                List<MemoryDecision> decisions = new ArrayList<>();
                try (
                    XContentParser parser = jsonXContent.createParser(xContentRegistry, LoggingDeprecationHandler.INSTANCE, responseContent)
                ) {
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
            } catch (PathNotFoundException e) {
                String reason = extractFirstSentence(e.getMessage());
                log.error("Failed to extract LLM result using path {}: {}", llmResultPath, reason);
                throw new OpenSearchStatusException(
                    "LLM predict result cannot be extracted with current llm_result_path with reason: "
                        + reason
                        + ". Please check either your llm configuration or your llm_result_path setting in memory container configuration",
                    RestStatus.NOT_FOUND
                );
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse memory decisions", e);
        }
    }

    public void summarizeMessages(MemoryConfiguration configuration, List<MessageInput> messages, ActionListener<String> listener) {
        if (messages == null || messages.isEmpty()) {
            listener.onResponse("");
        } else {
            Map<String, String> stringParameters = new HashMap<>();
            Map<String, Object> memoryParams = configuration.getParameters();
            String llmResultPath = (String) memoryParams.getOrDefault(LLM_RESULT_PATH_FIELD, DEFAULT_LLM_RESULT_PATH);
            Map<String, String> sessionParams = (Map<String, String>) memoryParams.getOrDefault("session", new HashMap<>());
            stringParameters.put("system_prompt", SESSION_SUMMARY_PROMPT);
            stringParameters.putAll(getParameterMap(sessionParams));
            stringParameters.putIfAbsent("max_summary_size", "10");

            try {
                String conversationJson = serializeMessagesToJson(messages);
                String userPrompt = "Summarize the following conversation in no more than "
                    + stringParameters.get("max_summary_size")
                    + " words:\n```json\n"
                    + conversationJson
                    + "\n```";
                stringParameters.put("user_prompt", userPrompt);

                RemoteInferenceInputDataSet inputDataSet = RemoteInferenceInputDataSet.builder().parameters(stringParameters).build();
                MLInput mlInput = MLInput.builder().algorithm(FunctionName.REMOTE).inputDataset(inputDataSet).build();
                MLPredictionTaskRequest predictionRequest = MLPredictionTaskRequest
                    .builder()
                    .modelId(configuration.getLlmId())
                    .mlInput(mlInput)
                    .build();

                client.execute(MLPredictionTaskAction.INSTANCE, predictionRequest, ActionListener.wrap(response -> {
                    try {
                        String summary = parseSessionSummary((ModelTensorOutput) response.getOutput(), llmResultPath);
                        listener.onResponse(summary);
                    } catch (Exception e) {
                        log.error("Failed to parse memory decisions from LLM response", e);
                        listener.onFailure(e);
                    }
                }, e -> {
                    log.error("Failed to get memory decisions from LLM", e);
                    listener.onFailure(e);
                }));
            } catch (Exception e) {
                listener.onFailure(e);
            }
        }
    }

    private String parseSessionSummary(ModelTensorOutput output, String llmResultPath) {
        Map<String, ?> dataAsMap = output.getMlModelOutputs().get(0).getMlModelTensors().get(0).getDataAsMap();
        try {
            Object filterdResult = JsonPath.read(dataAsMap, llmResultPath);
            String sessionSummary = null;
            if (filterdResult != null) {
                sessionSummary = StringUtils.toJson(filterdResult);
            }
            return sessionSummary;
        } catch (PathNotFoundException e) {
            String reason = extractFirstSentence(e.getMessage());
            log.error("Failed to extract LLM result using path {}: {}", llmResultPath, reason);
            throw new OpenSearchStatusException(
                "LLM predict result cannot be extracted with current llm_result_path with reason: "
                    + reason
                    + ". Please check either your llm configuration or your llm_result_path setting in memory container configuration",
                RestStatus.NOT_FOUND
            );
        }
    }

    private boolean validatePromptFormat(String prompt) {
        if (!prompt.contains("\"facts\"") || !prompt.contains("JSON")) {
            return false;
        }
        return true;
    }

    private String serializeMessagesToJson(List<MessageInput> messages) throws IOException {
        XContentBuilder builder = jsonXContent.contentBuilder();
        builder.startArray();
        for (MessageInput message : messages) {
            message.toXContent(builder, ToXContent.EMPTY_PARAMS);
        }
        builder.endArray();
        return builder.toString();
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
     * Extracts the first sentence from an exception message without the trailing period.
     * Uses ". " (period + space) as the sentence boundary to avoid splitting on periods
     * within JSON paths like "$.data.output".
     *
     * @param message The exception message to extract from
     * @return The first sentence without trailing period, or the whole message if no sentence boundary found
     */
    private String extractFirstSentence(String message) {
        if (message == null) {
            return "";
        }
        // Look for ". " (period + space) which indicates sentence boundary
        // This avoids splitting on periods within JSON paths like "$.data.output"
        int sentenceEnd = message.indexOf(". ");
        if (sentenceEnd >= 0) {
            return message.substring(0, sentenceEnd); // Exclude the period
        }
        return message; // No sentence boundary found, use whole message
    }
}
