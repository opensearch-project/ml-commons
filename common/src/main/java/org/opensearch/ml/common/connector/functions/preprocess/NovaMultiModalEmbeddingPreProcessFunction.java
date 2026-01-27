/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.connector.functions.preprocess;

import static org.opensearch.ml.common.utils.StringUtils.convertScriptStringToJsonString;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.opensearch.ml.common.dataset.TextDocsInputDataSet;
import org.opensearch.ml.common.dataset.remote.RemoteInferenceInputDataSet;
import org.opensearch.ml.common.input.MLInput;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.log4j.Log4j2;

@Log4j2
public class NovaMultiModalEmbeddingPreProcessFunction extends ConnectorPreProcessFunction {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    public NovaMultiModalEmbeddingPreProcessFunction() {
        this.returnDirectlyForRemoteInferenceInput = true;
    }

    @Override
    public void validate(MLInput mlInput) {
        validateTextDocsInput(mlInput);
        List<String> docs = ((TextDocsInputDataSet) mlInput.getInputDataset()).getDocs();
        if (docs.size() == 0 || (docs.size() == 1 && docs.get(0) == null)) {
            throw new IllegalArgumentException("No input provided");
        }
    }

    @Override
    public RemoteInferenceInputDataSet process(MLInput mlInput) {
        TextDocsInputDataSet inputData = (TextDocsInputDataSet) mlInput.getInputDataset();
        String input = inputData.getDocs().get(0);

        Map<String, String> parametersMap = new HashMap<>();
        String parameterName = detectModalityParameter(input);
        String content = extractContent(input);
        parametersMap.put(parameterName, content);

        return RemoteInferenceInputDataSet
            .builder()
            .parameters(convertScriptStringToJsonString(Map.of("parameters", parametersMap)))
            .build();
    }

    private String extractContent(String input) {
        if (input == null || !input.startsWith("{")) {
            return input;
        }

        try {
            JsonNode node = objectMapper.readTree(input);
            JsonNode value;

            if ((value = node.get("text")) != null)
                return value.asText();
            if ((value = node.get("image")) != null)
                return value.asText();
            if ((value = node.get("audio")) != null)
                return value.asText();
            if ((value = node.get("video")) != null)
                return value.asText();

            return input;
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse JSON: {}", e.getMessage());
            return input;
        }
    }

    private String detectModalityParameter(String input) {
        try {
            JsonNode node = objectMapper.readTree(input);
            if (node.has("text"))
                return "text";
            if (node.has("image"))
                return "image";
            if (node.has("video"))
                return "video";
            if (node.has("audio"))
                return "audio";
            return "text";
        } catch (JsonProcessingException e) {
            log.warn("Failed to detect modality from input, defaulting to text: {}", e.getMessage());
            return "text";
        }
    }
}
