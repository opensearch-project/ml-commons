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

public class CohereMultiModalEmbeddingPreProcessFunction extends ConnectorPreProcessFunction {

    public CohereMultiModalEmbeddingPreProcessFunction() {
        this.returnDirectlyForRemoteInferenceInput = true;
    }

    @Override
    public void validate(MLInput mlInput) {
        validateTextDocsInput(mlInput);
        List<String> docs = ((TextDocsInputDataSet) mlInput.getInputDataset()).getDocs();
        if (docs.isEmpty() || (docs.size() == 1 && docs.getFirst() == null)) {
            throw new IllegalArgumentException("No image provided");
        }
    }

    @Override
    public RemoteInferenceInputDataSet process(MLInput mlInput) {
        TextDocsInputDataSet inputData = (TextDocsInputDataSet) mlInput.getInputDataset();
        Map<String, String> parametersMap = new HashMap<>();
        parametersMap.put("images", inputData.getDocs().getFirst());
        return RemoteInferenceInputDataSet
            .builder()
            .parameters(convertScriptStringToJsonString(Map.of("parameters", parametersMap)))
            .build();
    }
}
