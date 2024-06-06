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

/**
 * This class provides a pre-processing function for multi-modal input data.
 * It takes an instance of {@link MLInput} as input and returns an instance of {@link RemoteInferenceInputDataSet}.
 * The input data is expected to be of type {@link TextDocsInputDataSet}, with the first document representing text input and the second document representing an image input.
 * The function validates the input data and then processes it to create a {@link RemoteInferenceInputDataSet} object.
 * If the input data is already of type {@link RemoteInferenceInputDataSet}, it is returned directly.
 */
public class MultiModalConnectorPreProcessFunction extends ConnectorPreProcessFunction {

    public MultiModalConnectorPreProcessFunction() {
        this.returnDirectlyForRemoteInferenceInput = true;
    }

    @Override
    public void validate(MLInput mlInput) {
        validateTextDocsInput(mlInput);
    }

    /**
     *  @param mlInput The input data to be processed.
     *  This method validates the input data and then processes it to create a {@link RemoteInferenceInputDataSet} object.
     *  If the input data is already of type {@link RemoteInferenceInputDataSet}, it is returned directly.
     *  The inputText will always show up in the first document, even it's null.
     */
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
