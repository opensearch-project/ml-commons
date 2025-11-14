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

import lombok.extern.log4j.Log4j2;

@Log4j2
public class NovaMultiModalEmbeddingPreProcessFunction extends ConnectorPreProcessFunction {

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
        parametersMap.put(parameterName, input);

        return RemoteInferenceInputDataSet
            .builder()
            .parameters(convertScriptStringToJsonString(Map.of("parameters", parametersMap)))
            .build();
    }

    private String detectModalityParameter(String input) {
        try {
            if (input.contains("\"text\"")) {
                return "inputText";
            }
            if (input.contains("\"image\"")) {
                return "inputImage";
            }
            if (input.contains("\"video\"")) {
                return "inputVideo";
            }
            if (input.contains("\"audio\"")) {
                return "inputAudio";
            }
            return "inputText";
        } catch (Exception e) {
            log.warn("Failed to detect modality from input, defaulting to text: {}", e.getMessage());
            return "inputText";
        }
    }
}
