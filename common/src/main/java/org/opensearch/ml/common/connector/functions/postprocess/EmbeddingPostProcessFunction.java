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
 * This is the default embedding post process function, the expected result from the model in two cases:
 * 1. A list of list of float for APIs embedding type is not enabled.
 * 2. A map of string to list of list of number for APIs that embedding type is enabled.
 * An example of enabled embedding type is cohere v2, the embedding API requires embedding_types as a mandatory field in the request body:
 * <a href="https://docs.cohere.com/reference/embed#request.body.embedding_types">...</a>
 * Currently, OpenAI Cohere and default sagemaker embedding models are using this function.
 */
public class EmbeddingPostProcessFunction implements ConnectorPostProcessFunction {

    /**
     * Validate the model's output is a List of following types:
     * float
     * int8: a signed number with eight bit, the value range is [-128, 127].
     * uint8: an unsigned number with eight bit, the value range is [0, 255].
     * binary: a binary representation of the embedding, the value range is non-deterministic.
     * ubinary: a binary representation of the embedding, the value range is non-deterministic.
     * @param input The input is the output from the model.
     */
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
     * The response could be list (case1: when specified concrete embedding type or case2: a v1 model specified with $.embeddings)
     * or map (case3: when specified embeddings in v2 model), but since the data type is not resolved, so consider this is case2 or case3.
     * v1 model's embeddings part is a list and v2 is a map.
     * @param input the model's response: v1 model's embeddings part or v2 model's embeddings part.
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
            List<List<Float>> embeddings = (List<List<Float>>) input;
            embeddings
                .forEach(
                    embedding -> modelTensors
                        .add(
                            ModelTensor
                                .builder()
                                .name("sentence_embedding")
                                .dataType(MLResultDataType.FLOAT32)
                                .shape(new long[] { embedding.size() })
                                .data(embedding.toArray(new Number[0]))
                                .build()
                        )
                );
        }
        return modelTensors;
    }

    // List<List<Number>> result
    private void validateEmbeddingList(List<?> outerList) {
        if (outerList.isEmpty() || !(outerList.get(0) instanceof List)) {
            throw new IllegalArgumentException(
                "Model result is NOT an non-empty List containing List values, please check the model response!"
            );
        }
        List<?> innerList = (List<?>) outerList.get(0);
        if (innerList.isEmpty() || !(innerList.get(0) instanceof Number)) {
            throw new IllegalArgumentException(
                "Model result is NOT an non-empty List containing List of Number values, please check the model response!"
            );
        }
    }

    /**
     * As in connector user can configure response filter to extract the result from raw model response, so we need to support different
     * cases, take cohere model as an example, the raw result looks like below:
     * {
     *     ......
     *     "embeddings": {
     *       "float": [
     *         [
     *           -0.007247925,
     *           -0.041229248,
     *           -0.023223877
     *           ......
     *         ]
     *       ],
     *       "int8": [
     *         1,
     *         2,
     *         3
     *       ]
     *     },
     *     ......
     *   }
     * 1. When response filter is set to: $.embeddings.float, then the result is a list of list<Number>.
     * 2. When response filter is set to: $.embeddings, then the result is a map of embedding type to list of list<Number>.
     * 3. When response filter is not set which is the default case, and the result is same with case2.
     * @param modelOutput the embedding result of embedding type supported models.
     * @return List of ModelTensor that represent the embedding result including all different embedding types.
     */
    @Override
    public List<ModelTensor> process(Object modelOutput, MLResultDataType mlResultDataType) {
        List<ModelTensor> modelTensors = new ArrayList<>();
        if (modelOutput instanceof Map) {
            modelTensors
                .add(
                    ModelTensor
                        .builder()
                        .name(CommonValue.ML_MAP_RESPONSE_KEY)
                        .dataAsMap(ImmutableMap.of(CommonValue.ML_MAP_RESPONSE_KEY, modelOutput))
                        .build()
                );
        } else if (modelOutput instanceof List) {
            for (Object element : (List<?>) modelOutput) {
                List<Number> singleEmbedding = (List<Number>) element;
                modelTensors
                    .add(
                        ModelTensor
                            .builder()
                            .name("embedding")
                            .shape(new long[] { singleEmbedding.size() })
                            .data(singleEmbedding.toArray(new Number[0]))
                            .dataType(mlResultDataType)
                            .build()
                    );
            }
        }
        return modelTensors;
    }
}
