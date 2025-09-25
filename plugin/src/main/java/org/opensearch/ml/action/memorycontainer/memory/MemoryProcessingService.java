/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.memorycontainer.memory;

import static org.opensearch.common.xcontent.json.JsonXContent.jsonXContent;
import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.DEFAULT_UPDATE_MEMORY_PROMPT;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.MEMORY_DECISION_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.PERSONAL_INFORMATION_ORGANIZER_PROMPT;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.opensearch.OpenSearchException;
import org.opensearch.common.xcontent.LoggingDeprecationHandler;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.xcontent.NamedXContentRegistry;
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
import org.opensearch.transport.client.Client;

import lombok.extern.log4j.Log4j2;

@Log4j2
public class MemoryProcessingService {

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
        if ("semantic".equalsIgnoreCase(strategy.getType())) {// TODO: change to enum
            extractFactsFromConversation(messages, memoryConfig, listener);
        } else {
            listener.onFailure(new IllegalArgumentException("Unsupported memory strategy type: " + strategy.getType()));
        }
    }

    public void extractFactsFromConversation(
        List<MessageInput> messages,
        MemoryConfiguration memoryConfig,
        ActionListener<List<String>> listener
    ) {
        if (memoryConfig == null || memoryConfig.getLlmId() == null) {
            listener.onResponse(new ArrayList<>());
            return;
        }

        String llmModelId = memoryConfig.getLlmId();
        Map<String, String> stringParameters = new HashMap<>();
        stringParameters.put("system_prompt", PERSONAL_INFORMATION_ORGANIZER_PROMPT);

        try {
            XContentBuilder messagesBuilder = jsonXContent.contentBuilder();
            messagesBuilder.startArray();

            for (MessageInput message : messages) {
                messagesBuilder.startObject();
                messagesBuilder.field("role", message.getRole() != null ? message.getRole() : "user");
                messagesBuilder.startArray("content");
                messagesBuilder.startObject();
                messagesBuilder.field("type", "text");
                messagesBuilder.field("text", message.getContent());
                messagesBuilder.endObject();
                messagesBuilder.endArray();
                messagesBuilder.endObject();
            }
            int size = messages.size();
            if (size > 1 && messages.get(size - 1).getRole().equalsIgnoreCase("assistant")) {
                messagesBuilder.startObject();
                messagesBuilder.field("role", "user");
                messagesBuilder.startArray("content");
                messagesBuilder.startObject();
                messagesBuilder.field("type", "text");
                messagesBuilder.field("text", "Please extract information from our conversation so far");
                messagesBuilder.endObject();
                messagesBuilder.endArray();
                messagesBuilder.endObject();
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
                List<String> facts = parseFactsFromLLMResponse(mlOutput);
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

    private List<String> parseFactsFromLLMResponse(MLOutput mlOutput) {
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
            if (dataMap != null && dataMap.containsKey("content")) {
                try {
                    List<?> contentList = (List<?>) dataMap.get("content");
                    if (contentList != null && !contentList.isEmpty()) {
                        Map<String, ?> contentItem = (Map<String, ?>) contentList.get(0);
                        if (contentItem != null && contentItem.containsKey("text")) {
                            String responseStr = String.valueOf(contentItem.get("text"));

                            try (
                                XContentParser parser = jsonXContent
                                    .createParser(xContentRegistry, LoggingDeprecationHandler.INSTANCE, responseStr)
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
                            }
                        }
                    }
                } catch (Exception e) {
                    log.error("Failed to extract content from dataMap", e);
                    throw new IllegalArgumentException("Failed to extract content from LLM response", e);
                }
                break;
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
            if (responseContent.startsWith("```json") && responseContent.endsWith("```")) {
                responseContent = responseContent.substring(7, responseContent.length() - 3).trim();
            } else if (responseContent.startsWith("```") && responseContent.endsWith("```")) {
                responseContent = responseContent.substring(3, responseContent.length() - 3).trim();
            }

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
}
