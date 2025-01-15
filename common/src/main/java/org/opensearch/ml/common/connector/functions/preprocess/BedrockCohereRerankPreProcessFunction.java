/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.connector.functions.preprocess;

import static org.opensearch.ml.common.utils.StringUtils.convertScriptStringToJsonString;

import java.util.Map;

import org.opensearch.ml.common.dataset.TextSimilarityInputDataSet;
import org.opensearch.ml.common.dataset.remote.RemoteInferenceInputDataSet;
import org.opensearch.ml.common.input.MLInput;

public class BedrockCohereRerankPreProcessFunction extends ConnectorPreProcessFunction {

    public BedrockCohereRerankPreProcessFunction() {
        this.returnDirectlyForRemoteInferenceInput = true;
    }

    @Override
    public void validate(MLInput mlInput) {
        if (!(mlInput.getInputDataset() instanceof TextSimilarityInputDataSet)) {
            throw new IllegalArgumentException("This pre_process_function can only support TextSimilarityInputDataSet");
        }
    }

    @Override
    public RemoteInferenceInputDataSet process(MLInput mlInput) {
        TextSimilarityInputDataSet inputData = (TextSimilarityInputDataSet) mlInput.getInputDataset();
        var textDocs = inputData.getTextDocs();

        StringBuilder docsBuilder = new StringBuilder();
        docsBuilder.append("[");
        for (int i = 0; i < textDocs.size(); i++) {
            docsBuilder.append("\"").append(textDocs.get(i)).append("\"");
            if (i < textDocs.size() - 1) {
                docsBuilder.append(",");
            }
        }
        docsBuilder.append("]");

        Map<String, Object> processedResult = Map
            .of("parameters", Map.of("query", inputData.getQueryText(), "documents", docsBuilder.toString()));

        return RemoteInferenceInputDataSet.builder().parameters(convertScriptStringToJsonString(processedResult)).build();
    }
}
