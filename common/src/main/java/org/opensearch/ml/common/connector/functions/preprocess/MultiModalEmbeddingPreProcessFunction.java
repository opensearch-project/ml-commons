/*
 *
 *  * Copyright OpenSearch Contributors
 *  * SPDX-License-Identifier: Apache-2.0
 *
 */

package org.opensearch.ml.common.connector.functions.preprocess;

import org.opensearch.ml.common.dataset.TextDocsInputDataSet;
import org.opensearch.ml.common.dataset.remote.RemoteInferenceInputDataSet;
import org.opensearch.ml.common.input.MLInput;

import java.util.Map;

import static org.opensearch.ml.common.utils.StringUtils.convertScriptStringToJsonString;

public class MultiModalEmbeddingPreProcessFunction extends ConnectorPreProcessFunction {

    public MultiModalEmbeddingPreProcessFunction() {
        this.returnDirectlyForRemoteInferenceInput = true;
    }

    @Override
    public void validate(MLInput mlInput) {
        validateTextDocsInput(mlInput);
    }

    // The input will must have inputText even it's null, input image is optional.
    @Override
    public RemoteInferenceInputDataSet process(MLInput mlInput) {
        TextDocsInputDataSet inputData = (TextDocsInputDataSet) mlInput.getInputDataset();
        if (inputData.getDocs().size() == 1) {
            return RemoteInferenceInputDataSet.builder().parameters(convertScriptStringToJsonString(Map.of("parameters", Map.of("inputText", inputData.getDocs().get(0))))).build();
        } else {
            return RemoteInferenceInputDataSet.builder().parameters(convertScriptStringToJsonString(Map.of("parameters", Map.of("inputText", inputData.getDocs().get(0), "inputImage", inputData.getDocs().get(1))))).build();
        }
    }
}
