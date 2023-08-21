/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.connector;

import org.opensearch.ml.common.output.model.MLResultDataType;
import org.opensearch.ml.common.output.model.ModelTensor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public class MLPostProcessFunction {

    public static final String COHERE_EMBEDDING = "connector.post_process.cohere.embedding";
    public static final String OPENAI_EMBEDDING = "connector.post_process.openai.embedding";

    public static final String NEURAL_SEARCH_EMBEDDING = "connector.post_process.neural_search.text_embedding";

    private static final Map<String, String> JSON_PATH_EXPRESSION = new HashMap<>();

    private static final Map<String, Function<List<List<Float>>, List<ModelTensor>>> POST_PROCESS_FUNCTIONS = new HashMap<>();


    static {
        JSON_PATH_EXPRESSION.put(OPENAI_EMBEDDING, "$.data[*].embedding");
        JSON_PATH_EXPRESSION.put(COHERE_EMBEDDING, "$.embeddings");
        JSON_PATH_EXPRESSION.put(NEURAL_SEARCH_EMBEDDING, "$[*]");
        POST_PROCESS_FUNCTIONS.put(OPENAI_EMBEDDING, buildModelTensorList());
        POST_PROCESS_FUNCTIONS.put(COHERE_EMBEDDING, buildModelTensorList());
        POST_PROCESS_FUNCTIONS.put(NEURAL_SEARCH_EMBEDDING, buildModelTensorList());
    }

    public static Function<List<List<Float>>, List<ModelTensor>> buildModelTensorList() {
        return numbersList -> {
            List<ModelTensor> modelTensors = new ArrayList<>();
            if (numbersList == null) {
                throw new IllegalArgumentException("NumbersList is null when applying build-in post process function!");
            }
            numbersList.forEach(numbers -> modelTensors.add(
                ModelTensor
                    .builder()
                    .name("sentence_embedding")
                    .dataType(MLResultDataType.FLOAT32)
                    .shape(new long[]{numbers.size()})
                    .data(numbers.toArray(new Number[0]))
                    .build()
            ));
            return modelTensors;
        };
    }

    public static String getResponseFilter(String postProcessFunction) {
        return JSON_PATH_EXPRESSION.get(postProcessFunction);
    }

    public static Function<List<List<Float>>, List<ModelTensor>> get(String postProcessFunction) {
        return POST_PROCESS_FUNCTIONS.get(postProcessFunction);
    }

    public static boolean contains(String postProcessFunction) {
        return POST_PROCESS_FUNCTIONS.containsKey(postProcessFunction);
    }
}
