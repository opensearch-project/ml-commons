/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.connector.functions.postprocess;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.opensearch.ml.common.output.model.MLResultDataType;
import org.opensearch.ml.common.output.model.ModelTensor;

public class BedrockRerankPostProcessFunction extends ConnectorPostProcessFunction<List<Map<String, Object>>> {

    @Override
    public void validate(Object input) {
        if (!(input instanceof List)) {
            throw new IllegalArgumentException("Post process function input is not a List.");
        }
        List<?> outerList = (List<?>) input;
        if (!outerList.isEmpty()) {
            if (!(outerList.get(0) instanceof Map)) {
                throw new IllegalArgumentException("Post process function input is not a List of Map.");
            }
            Map innerMap = (Map) outerList.get(0);

            if (innerMap.isEmpty() || !innerMap.containsKey("index") || !innerMap.containsKey("relevanceScore")) {
                throw new IllegalArgumentException("The rerank result should contain index and relevanceScore.");
            }
        }
    }

    @Override
    public List<ModelTensor> process(List<Map<String, Object>> rerankResults) {
        List<ModelTensor> modelTensors = new ArrayList<>();

        if (rerankResults.size() > 0) {
            Double[] scores = new Double[rerankResults.size()];
            for (int i = 0; i < rerankResults.size(); i++) {
                Integer index = (Integer) rerankResults.get(i).get("index");
                Object relevanceScore = rerankResults.get(i).get("relevanceScore");
                scores[index] = switch (relevanceScore) {
                    case BigDecimal bd -> bd.doubleValue();
                    case Double d -> d;
                    case null -> throw new IllegalArgumentException("relevanceScore is null");
                    default -> throw new IllegalArgumentException("Unexpected type for relevanceScore: " +
                            relevanceScore.getClass().getName());
                };
            }

            for (int i = 0; i < scores.length; i++) {
                modelTensors
                    .add(
                        ModelTensor
                            .builder()
                            .name("similarity")
                            .shape(new long[] { 1 })
                            .data(new Number[] { scores[i] })
                            .dataType(MLResultDataType.FLOAT32)
                            .build()
                    );
            }
        }
        return modelTensors;
    }
}
