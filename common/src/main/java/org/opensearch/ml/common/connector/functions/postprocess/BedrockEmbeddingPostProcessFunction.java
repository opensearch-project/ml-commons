/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.connector.functions.postprocess;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.opensearch.ml.common.CommonValue;
import org.opensearch.ml.common.output.model.MLResultDataType;
import org.opensearch.ml.common.output.model.ModelTensor;

import com.google.common.collect.ImmutableMap;

/**
 * Bedrock embedding post process function currently is used by bedrock titan models, for v1 model,
 * the model response is a list of float numbers, for v2 model, the model response combined by two parts:
 * 1. "embedding" which returns list of float numbers like v1.
 * 2. "embeddingByType" is a map contains all embedding type results, with embedding type as the key.
 */
public class BedrockEmbeddingPostProcessFunction implements ConnectorPostProcessFunction {

    @Override
    public void validate(Object input) {
        if (input instanceof List<?>) {
            validateEmbeddingList((List<?>) input);
        } else if (input instanceof Map) {
            for (Map.Entry<String, Object> entry : ((Map<String, Object>) input).entrySet()) {
                if (!(entry.getValue() instanceof List)) {
                    throw new IllegalArgumentException(
                        String
                            .format(
                                Locale.ROOT,
                                "Model response embedding type %s result is NOT an list type, please check the model response!",
                                entry.getKey()
                            )
                    );
                }
                validateEmbeddingList((List<?>) entry.getValue());
            }
        } else {
            throw new IllegalArgumentException("Model response is neither a list type nor a map type, please check the model response!");
        }
    }

    /**
     * The response could be list (case1: when specified concrete embedding type or case2: a v1 model specified with $.embedding)
     * or map (case3: when specified embedding by type), but since the data type is not resolved, so consider this is case2 or case3.
     * @param input the model's response: v1 model's embedding part or v2 model's embeddingByType part.
     * @return  List of ModelTensor that represent the embedding result including all different embedding types or single embedding type.
     */
    @Override
    public List<ModelTensor> process(Object input) {
        List<ModelTensor> modelTensors = new ArrayList<>();
        if (input instanceof Map) {
            modelTensors
                .add(
                    ModelTensor
                        .builder()
                        .name(CommonValue.ML_MAP_RESPONSE_KEY)
                        .dataAsMap(ImmutableMap.of(CommonValue.ML_MAP_RESPONSE_KEY, input))
                        .build()
                );
        } else {
            List<Float> embedding = (List<Float>) input;
            modelTensors
                .add(
                    ModelTensor
                        .builder()
                        .name("sentence_embedding")
                        .dataType(MLResultDataType.FLOAT32)
                        .shape(new long[] { embedding.size() })
                        .data(embedding.toArray(new Number[0]))
                        .build()
                );
        }
        return modelTensors;
    }

    /**
     * When the response is map, it means user specifies the response filter to a concrete embedding type, e.g.: $.embeddingByType.float
     * In this case we need to process the result to ModelTensor's data field as it's same as before. If user specifies the response
     * filter to embedding, e.g. $.embedding, then we need to convert the result to ModelTensor's dataAsMap field as the result is a map.
     * @param input Model's response or extracted object from the model response by response filter.
     * @param mlResultDataType The data type of the model's response.
     * @return List of ModelTensor that represent the embedding result including all different embedding types or single embedding type.
     */
    @Override
    public List<ModelTensor> process(Object input, MLResultDataType mlResultDataType) {
        List<ModelTensor> modelTensors = new ArrayList<>();
        if (input instanceof Map) {
            modelTensors
                .add(
                    ModelTensor
                        .builder()
                        .name(CommonValue.ML_MAP_RESPONSE_KEY)
                        .dataAsMap(ImmutableMap.of(CommonValue.ML_MAP_RESPONSE_KEY, input))
                        .build()
                );

        } else if (input instanceof List) {
            List<Number> embedding = (List<Number>) input;
            modelTensors
                .add(
                    ModelTensor
                        .builder()
                        .name("sentence_embedding")
                        .dataType(mlResultDataType)
                        .shape(new long[] { embedding.size() })
                        .data(embedding.toArray(new Number[0]))
                        .build()
                );
        }
        return modelTensors;
    }

    private void validateEmbeddingList(List<?> input) {
        if (input.isEmpty() || !(input.get(0) instanceof Number)) {
            throw new IllegalArgumentException(
                "Model result is NOT an non-empty List containing Number values, please check the model response!"
            );
        }
    }
}
