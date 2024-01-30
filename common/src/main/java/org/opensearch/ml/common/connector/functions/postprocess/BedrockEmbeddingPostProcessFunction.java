/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.connector.functions.postprocess;

import org.opensearch.ml.common.output.model.MLResultDataType;
import org.opensearch.ml.common.output.model.ModelTensor;

import java.util.ArrayList;
import java.util.List;

public class BedrockEmbeddingPostProcessFunction extends ConnectorPostProcessFunction<List<Float>> {

    @Override
    public void validate(Object input) {
        if (!(input instanceof List)) {
            throw new IllegalArgumentException("Post process function input is not a List.");
        }

        List<?> outerList = (List<?>) input;

        if (!outerList.isEmpty() && !(((List<?>)input).get(0) instanceof Number)) {
            throw new IllegalArgumentException("The embedding should be a non-empty List containing Float values.");
        }
    }

    @Override
    public List<ModelTensor> process(List<Float> embedding) {
        List<ModelTensor> modelTensors = new ArrayList<>();
        modelTensors.add(
                ModelTensor
                        .builder()
                        .name("sentence_embedding")
                        .dataType(MLResultDataType.FLOAT32)
                        .shape(new long[]{embedding.size()})
                        .data(embedding.toArray(new Number[0]))
                        .build());
        return modelTensors;
    }
}
