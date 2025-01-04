/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.connector.functions.preprocess;

import static org.opensearch.ml.common.utils.StringUtils.convertScriptStringToJsonString;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.opensearch.ml.common.dataset.TextSimilarityInputDataSet;
import org.opensearch.ml.common.dataset.remote.RemoteInferenceInputDataSet;
import org.opensearch.ml.common.input.MLInput;

public class BedrockRerankPreProcessFunction extends ConnectorPreProcessFunction {

    public BedrockRerankPreProcessFunction() {
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
        String queryText = inputData.getQueryText();
        List<String> textDocs = inputData.getTextDocs();

        List<Map<String, Object>> queries = new ArrayList<Map<String, Object>>();
        queries.add(Map.of("textQuery", Map.of("text", queryText), "type", "TEXT"));

        List<Map<String, Object>> sources = new ArrayList<Map<String, Object>>();
        inputData.getTextDocs().forEach(textDoc -> {
            sources.add(Map.of("inlineDocumentSource", Map.of("textDocument", Map.of("text", textDoc), "type", "TEXT"), "type", "INLINE"));
        });

        Map<String, Object> processedResult = Map
            .of("parameters", Map.of("queries", queries, "sources", sources, "numberOfResults", textDocs.size()));

        return RemoteInferenceInputDataSet.builder().parameters(convertScriptStringToJsonString(processedResult)).build();
    }
}
