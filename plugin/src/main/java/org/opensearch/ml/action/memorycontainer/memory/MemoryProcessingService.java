/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.memorycontainer.memory;

import static org.opensearch.common.xcontent.json.JsonXContent.jsonXContent;
import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.DEFAULT_UPDATE_MEMORY_PROMPT;
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
import java.util.Optional;

import org.opensearch.OpenSearchException;
import org.opensearch.common.xcontent.LoggingDeprecationHandler;
import org.opensearch.core.action.ActionListener;
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
import org.opensearch.ml.common.output.MLOutput;
import org.opensearch.ml.common.output.model.ModelTensorOutput;
import org.opensearch.ml.common.output.model.ModelTensors;
import org.opensearch.ml.common.transport.MLTaskResponse;
import org.opensearch.ml.common.transport.memorycontainer.memory.MessageInput;
import org.opensearch.ml.common.transport.prediction.MLPredictionTaskAction;
import org.opensearch.ml.common.transport.prediction.MLPredictionTaskRequest;
import org.opensearch.ml.common.utils.StringUtils;
import org.opensearch.transport.client.Client;

import com.jayway.jsonpath.JsonPath;

import lombok.extern.log4j.Log4j2;

@Log4j2
public class MemoryProcessingService {

    public static final String DEFAULT_LLM_RESULT_PATH = "$.content[0].text";
    private final Client client;
    private final NamedXContentRegistry xContentRegistry;

    public MemoryProcessingService(Client client, NamedXContentRegistry xContentRegistry) {
        this.client = client;
        this.xContentRegistry = xContentRegistry;
    }

    public void runMemoryStrategy(
        MemoryStrategy strategy,
        List<MessageInput> messages,
        MemoryConfiguration memoryConfig,
        ActionListener<List<String>> listener
    ) {
        if ("semantic".equalsIgnoreCase(strategy.getType())
            || "user_preference".equalsIgnoreCase(strategy.getType())
            || "summary".equalsIgnoreCase(strategy.getType())) {
            extractFactsFromConversation(messages, strategy, memoryConfig, listener);
        } else {
            listener.onFailure(new IllegalArgumentException("Unsupported memory strategy type: " + strategy.getType()));
        }
    }

    public void extractFactsFromConversation(
        List<MessageInput> messages,
        MemoryStrategy strategy,
        MemoryConfiguration memoryConfig,
        ActionListener<List<String>> listener
    ) {
        if (memoryConfig == null || memoryConfig.getLlmId() == null) {
            listener.onResponse(new ArrayList<>());
            return;
        }

        String llmModelId = memoryConfig.getLlmId();
        Map<String, String> stringParameters = new HashMap<>();

        // Determine default prompt based on strategy type
        String defaultPrompt;
        if ("user_preference".equalsIgnoreCase(strategy.getType())) {
            defaultPrompt = USER_PREFERENCE_FACTS_EXTRACTION_PROMPT;
        } else if ("summary".equalsIgnoreCase(strategy.getType())) {
            defaultPrompt = SUMMARY_FACTS_EXTRACTION_PROMPT;
        } else {
            defaultPrompt = SEMANTIC_FACTS_EXTRACTION_PROMPT;
        }

        if (strategy.getStrategyConfig() == null || strategy.getStrategyConfig().isEmpty()) {
            stringParameters.put("system_prompt", defaultPrompt);
        } else {
            Object customPrompt = strategy.getStrategyConfig().get("system_prompt");
            if (customPrompt == null || customPrompt.toString().trim().isEmpty()) {
                stringParameters.put("system_prompt", defaultPrompt);
            } else if (!validatePromptFormat(customPrompt.toString())) {
                listener.onFailure(new IllegalArgumentException("Custom prompt must specify JSON response format with 'facts' array"));
                return;
            } else {
                stringParameters.put("system_prompt", customPrompt.toString()); // use custom strategy
            }
        }

        try {
            XContentBuilder messagesBuilder = jsonXContent.contentBuilder();
            messagesBuilder.startArray();
            Map<String, Object> strategyConfig = strategy.getStrategyConfig();
            if (strategyConfig.containsKey("system_prompt_message")) {
                Object systemPromptMsg = strategyConfig.get("system_prompt_message");
                if (systemPromptMsg != null && systemPromptMsg instanceof Map) {
                    messagesBuilder.map((Map) systemPromptMsg);
                }
            }
            for (MessageInput message : messages) {
                message.toXContent(messagesBuilder, ToXContent.EMPTY_PARAMS);
            }
            if (strategyConfig.containsKey("user_prompt_message")) {
                Object userPromptMsg = strategyConfig.get("user_prompt_message");
                if (userPromptMsg != null && userPromptMsg instanceof Map) {
                    messagesBuilder.map((Map) userPromptMsg);
                }
            } else { // Add default user prompt
                MessageInput message = getMessageInput("Please extract information from our conversation so far");
                message.toXContent(messagesBuilder, ToXContent.EMPTY_PARAMS);
            }
            messagesBuilder.endArray();
            String messagesJson = messagesBuilder.toString();
            stringParameters.put("messages", messagesJson);

            log.debug("LLM request - processing {} messages", messages.size());
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
                List<String> facts = parseFactsFromLLMResponse(strategy, mlOutput);
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
        MemoryConfiguration memoryConfig,
        ActionListener<List<MemoryDecision>> listener
    ) {
        if (memoryConfig == null || memoryConfig.getLlmId() == null) {
            listener.onFailure(new IllegalStateException("LLM model is required for memory decisions"));
            return;
        }

        String llmModelId = memoryConfig.getLlmId();

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

        try {
            XContentBuilder messagesBuilder = jsonXContent.contentBuilder();
            messagesBuilder.startArray();
            messagesBuilder.startObject();
            messagesBuilder.field("role", "user");
            messagesBuilder.startArray("content");
            messagesBuilder.startObject();
            messagesBuilder.field("type", "text");
            messagesBuilder.field("text", decisionRequestJson);
            messagesBuilder.endObject();
            messagesBuilder.endArray();
            messagesBuilder.endObject();
            messagesBuilder.endArray();

            String messagesJson = messagesBuilder.toString();
            stringParameters.put("messages", messagesJson);

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
                    List<MemoryDecision> decisions = parseMemoryDecisions(response);
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

    private List<String> parseFactsFromLLMResponse(MemoryStrategy strategy, MLOutput mlOutput) {
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
            String llmResultPath = Optional
                .ofNullable(strategy.getStrategyConfig().get("llm_result_path"))
                .map(Object::toString)
                .orElse(DEFAULT_LLM_RESULT_PATH);
            Object filterdResult = JsonPath.read(dataMap, llmResultPath);
            String llmResult = null;
            if (filterdResult != null) {
                llmResult = StringUtils.toJson(filterdResult);
                llmResult = cleanMarkdownFromJson(llmResult);
            }
            if (llmResult != null) {
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

    private List<MemoryDecision> parseMemoryDecisions(MLTaskResponse response) {
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

            String responseContent = null;
            if (dataMap.containsKey("response")) {
                responseContent = (String) dataMap.get("response");
            } else if (dataMap.containsKey("content")) {
                List<Map<String, Object>> contentList = (List<Map<String, Object>>) dataMap.get("content");
                if (contentList != null && !contentList.isEmpty()) {
                    Map<String, Object> firstContent = contentList.get(0);
                    responseContent = (String) firstContent.get("text");
                }
            }

            if (responseContent == null) {
                throw new IllegalStateException("No response content found in LLM output");
            }

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
        } else {
            Map<String, String> stringParameters = new HashMap<>();
            Map<String, Object> memoryParams = configuration.getParameters();
            String llmResultPath = (String) memoryParams.getOrDefault("llm_result_path", DEFAULT_LLM_RESULT_PATH);
            Map<String, String> sessionParams = (Map<String, String>) memoryParams.getOrDefault("session", new HashMap<>());
            stringParameters.put("system_prompt", SESSION_SUMMARY_PROMPT);
            stringParameters.putAll(getParameterMap(sessionParams));
            stringParameters.putIfAbsent("max_summary_size", "10");

            try {
                XContentBuilder messagesBuilder = jsonXContent.contentBuilder();
                messagesBuilder.startArray();
                for (MessageInput message : messages) {
                    message.toXContent(messagesBuilder, ToXContent.EMPTY_PARAMS);
                }
                if (sessionParams.containsKey("user_prompt_message")) {
                    Object userPromptMsg = sessionParams.get("user_prompt_message");
                    if (userPromptMsg != null && userPromptMsg instanceof Map) {
                        messagesBuilder.map((Map) userPromptMsg);
                    }
                } else {
                    MessageInput message = getMessageInput(
                        "Please summarize our conversation, not exceed " + stringParameters.get("max_summary_size") + " words"
                    );
                    message.toXContent(messagesBuilder, ToXContent.EMPTY_PARAMS);
                }
                messagesBuilder.endArray();
                stringParameters.put("messages", messagesBuilder.toString());

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
        Object filterdResult = JsonPath.read(dataAsMap, llmResultPath);
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
}
