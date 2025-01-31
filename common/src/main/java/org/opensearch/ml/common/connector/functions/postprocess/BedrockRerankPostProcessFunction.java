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

        if (outerList.isEmpty()) {
            throw new IllegalArgumentException("Post process function input is empty.");
        }

        for (Object item : outerList) {
            if (!(item instanceof Map)) {
                throw new IllegalArgumentException("Rerank result is not a Map.");
            }

            Map<?, ?> innerMap = (Map<?, ?>) item;

            if (innerMap.isEmpty()) {
                throw new IllegalArgumentException("Rerank result is empty.");
            }

            if (!innerMap.containsKey("index") || !innerMap.containsKey("relevanceScore")) {
                throw new IllegalArgumentException("Rerank result should have both index and relevanceScore.");
            }

            if (!(innerMap.get("relevanceScore") instanceof BigDecimal || innerMap.get("relevanceScore") instanceof Double)) {
                throw new IllegalArgumentException("relevanceScore is not BigDecimal or Double.");
            }
        }
    }

    @Override
    public List<ModelTensor> process(List<Map<String, Object>> rerankResults) {
        List<ModelTensor> modelTensors = new ArrayList<>();

        if (!rerankResults.isEmpty()) {
            Double[] scores = new Double[rerankResults.size()];
            for (Map rerankResult : rerankResults) {
                Integer index = (Integer) rerankResult.get("index");
                Object relevanceScore = rerankResult.get("relevanceScore");
                if (relevanceScore instanceof BigDecimal) {
                    scores[index] = ((BigDecimal) relevanceScore).doubleValue();
                } else if (relevanceScore instanceof Double) {
                    scores[index] = (Double) relevanceScore;
                }
            }
            for (Double score : scores) {
                modelTensors
                    .add(
                        ModelTensor
                            .builder()
                            .name("similarity")
                            .shape(new long[] { 1 })
                            .data(new Number[] { score })
                            .dataType(MLResultDataType.FLOAT32)
                            .build()
                    );
            }
        }
        return modelTensors;
    }
}
