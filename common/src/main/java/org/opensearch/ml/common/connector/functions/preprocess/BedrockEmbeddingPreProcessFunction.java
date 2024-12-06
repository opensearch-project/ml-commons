/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.connector.functions.preprocess;

import static org.opensearch.ml.common.utils.StringUtils.convertScriptStringToJsonString;

import java.util.Map;
import java.util.Optional;

import org.apache.commons.lang3.math.NumberUtils;
import org.opensearch.ml.common.dataset.TextDocsInputDataSet;
import org.opensearch.ml.common.dataset.remote.RemoteInferenceInputDataSet;
import org.opensearch.ml.common.input.MLInput;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class BedrockEmbeddingPreProcessFunction extends ConnectorPreProcessFunction {

    public BedrockEmbeddingPreProcessFunction() {
        this.returnDirectlyForRemoteInferenceInput = true;
    }

    @Override
    public void validate(MLInput mlInput) {
        validateTextDocsInput(mlInput);
    }

    // Keep this method for robust
    @Override
    public RemoteInferenceInputDataSet process(MLInput mlInput) {
        TextDocsInputDataSet inputData = (TextDocsInputDataSet) mlInput.getInputDataset();
        Map<String, Object> processedResult = Map.of("parameters", Map.of("inputText", inputData.getDocs().get(0)));
        return RemoteInferenceInputDataSet.builder().parameters(convertScriptStringToJsonString(processedResult)).build();
    }

    @Override
    public RemoteInferenceInputDataSet process(Map<String, String> connectorParams, MLInput mlInput) {
        TextDocsInputDataSet inputData = (TextDocsInputDataSet) mlInput.getInputDataset();
        // Amazon Titan Text Embeddings V2 model: https://docs.aws.amazon.com/bedrock/latest/userguide/titan-embedding-models.html
        // Default dimension is 1024
        int dimensions = Optional.ofNullable(connectorParams.get("dimensions")).map(x -> NumberUtils.toInt(x, 1024)).orElse(1024);
        log.error("The bedrock dimensions parameter value is: {}", dimensions);
        Map<String, Object> processedResult = Map
            .of("parameters", Map.of("inputText", inputData.getDocs().get(0), "dimensions", dimensions));
        return RemoteInferenceInputDataSet.builder().parameters(convertScriptStringToJsonString(processedResult)).build();
    }
}
