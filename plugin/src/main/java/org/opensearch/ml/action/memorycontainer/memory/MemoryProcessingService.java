/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.memorycontainer.memory;

import static org.apache.commons.text.StringEscapeUtils.escapeJson;
import static org.opensearch.common.xcontent.json.JsonXContent.jsonXContent;
import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.DEFAULT_UPDATE_MEMORY_PROMPT;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.FACTS_FIELD;
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
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.dataset.remote.RemoteInferenceInputDataSet;
import org.opensearch.ml.common.input.MLInput;
import org.opensearch.ml.common.memorycontainer.MemoryDecision;
import org.opensearch.ml.common.memorycontainer.MemoryDecisionRequest;
import org.opensearch.ml.common.memorycontainer.MemoryStorageConfig;
import org.opensearch.ml.common.output.MLOutput;
import org.opensearch.ml.common.output.model.ModelTensorOutput;
import org.opensearch.ml.common.output.model.ModelTensors;
import org.opensearch.ml.common.transport.MLTaskResponse;
import org.opensearch.ml.common.transport.memorycontainer.memory.MessageInput;
import org.opensearch.ml.common.transport.prediction.MLPredictionTaskAction;
import org.opensearch.ml.common.transport.prediction.MLPredictionTaskRequest;
import org.opensearch.ml.common.utils.StringUtils;
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

    public void extractFactsFromConversation(
        List<MessageInput> messages,
        MemoryStorageConfig storageConfig,
        ActionListener<List<String>> listener
    ) {
        if (storageConfig == null || storageConfig.getLlmModelId() == null) {
            listener.onResponse(new ArrayList<>());
            return;
        }

        String llmModelId = storageConfig.getLlmModelId();
        Map<String, String> stringParameters = new HashMap<>();
        stringParameters.put("system_prompt", PERSONAL_INFORMATION_ORGANIZER_PROMPT);

        try {
            StringBuilder userMessages = new StringBuilder();
            for (MessageInput message : messages) {
                userMessages.append(message.getContent());
                userMessages.append(System.lineSeparator());
            }
            String messagesJson = userMessages.toString();
            stringParameters.put("messages", escapeJson(messagesJson));

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
        MemoryStorageConfig storageConfig,
        ActionListener<List<MemoryDecision>> listener
    ) {
        if (storageConfig == null || storageConfig.getLlmModelId() == null) {
            listener.onFailure(new IllegalStateException("LLM model is required for memory decisions"));
            return;
        }

        String llmModelId = storageConfig.getLlmModelId();

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
            stringParameters.put("messages", escapeJson(decisionRequestJson));
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

        // Parse the JSON response to extract facts
        try {
            Map<String, ?> dataMap = modelTensors.getMlModelTensors().get(0).getDataAsMap();
            // if LLM Response does not contain FACTS_FIELD then fact extraction failed hence throw exception
            if (dataMap == null || dataMap.get(FACTS_FIELD) == null) {
                throw new IllegalArgumentException("Failed to parse facts from LLM response");
            }
            facts = (List<String>) dataMap.get(FACTS_FIELD);
        } catch (Exception e) {
            // Should not print the user data in logs
            log.warn("Failed to parse facts from LLM response", e);
            throw new IllegalArgumentException("Failed to parse facts from LLM response", e);
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

            String responseContent = StringUtils.toJson(dataMap);

            if (responseContent == null || responseContent.isEmpty() || responseContent.equals("{}")) {
                throw new IllegalStateException("No response content found in LLM output");
            }

            // Parse memory decisions using XContentParser
            List<MemoryDecision> decisions = new ArrayList<>();
            boolean foundMemoryDecisionField = false;

            try (XContentParser parser = jsonXContent.createParser(xContentRegistry, LoggingDeprecationHandler.INSTANCE, responseContent)) {
                ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.nextToken(), parser);

                while (parser.nextToken() != XContentParser.Token.END_OBJECT) {
                    String fieldName = parser.currentName();
                    parser.nextToken();

                    if (MEMORY_DECISION_FIELD.equals(fieldName)) {
                        foundMemoryDecisionField = true;
                        ensureExpectedToken(XContentParser.Token.START_ARRAY, parser.currentToken(), parser);
                        while (parser.nextToken() != XContentParser.Token.END_ARRAY) {
                            decisions.add(MemoryDecision.parse(parser));
                        }
                    } else {
                        parser.skipChildren();
                    }
                }
            }

            // If MEMORY_DECISION_FIELD is not found in the LLM output, fail the parsing
            if (!foundMemoryDecisionField) {
                throw new IllegalStateException("LLM response does not contain required field: " + MEMORY_DECISION_FIELD);
            }

            return decisions;
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse memory decisions", e);
        }
    }
}
