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

/**
 * This class provides a pre-processing function for Bedrock Nova video input data.
 * It takes an instance of {@link MLInput} as input and returns an instance of {@link RemoteInferenceInputDataSet}.
 * The input data is expected to be of type {@link TextDocsInputDataSet}, with document representing a video.
 * The function validates the input data and then processes it to create a {@link RemoteInferenceInputDataSet} object.
 * If the input data is already of type {@link RemoteInferenceInputDataSet}, it is returned directly.
 */
public class VideoEmbeddingPreProcessFunction extends ConnectorPreProcessFunction {

    public VideoEmbeddingPreProcessFunction() {
        this.returnDirectlyForRemoteInferenceInput = true;
    }

    @Override
    public void validate(MLInput mlInput) {
        validateTextDocsInput(mlInput);
        List<String> docs = ((TextDocsInputDataSet) mlInput.getInputDataset()).getDocs();
        if (docs.size() == 0) {
            throw new IllegalArgumentException("No input video provided");
        }
    }

    /**
     *  @param mlInput The input data to be processed.
     *  This method validates the input data and then processes it to create a {@link RemoteInferenceInputDataSet} object.
     *  If the input data is already of type {@link RemoteInferenceInputDataSet}, it is returned directly.
     */
    @Override
    public RemoteInferenceInputDataSet process(MLInput mlInput) {
        TextDocsInputDataSet inputData = (TextDocsInputDataSet) mlInput.getInputDataset();
        Map<String, String> parametersMap = new HashMap<>();
        parametersMap.put("inputVideo", inputData.getDocs().get(0));
        return RemoteInferenceInputDataSet
            .builder()
            .parameters(convertScriptStringToJsonString(Map.of("parameters", parametersMap)))
            .build();

    }
}
