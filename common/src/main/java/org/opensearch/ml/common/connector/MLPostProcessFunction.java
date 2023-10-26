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
    public static final String BEDROCK_EMBEDDING = "connector.post_process.bedrock.embedding";
    public static final String DEFAULT_EMBEDDING = "connector.post_process.default.embedding";

    private static final Map<String, String> JSON_PATH_EXPRESSION = new HashMap<>();

    private static final Map<String, Function<List<?>, List<ModelTensor>>> POST_PROCESS_FUNCTIONS = new HashMap<>();


    static {
        JSON_PATH_EXPRESSION.put(OPENAI_EMBEDDING, "$.data[*].embedding");
        JSON_PATH_EXPRESSION.put(COHERE_EMBEDDING, "$.embeddings");
        JSON_PATH_EXPRESSION.put(DEFAULT_EMBEDDING, "$[*]");
        JSON_PATH_EXPRESSION.put(BEDROCK_EMBEDDING, "$.embedding");
        POST_PROCESS_FUNCTIONS.put(OPENAI_EMBEDDING, buildMultipleResultModelTensor());
        POST_PROCESS_FUNCTIONS.put(COHERE_EMBEDDING, buildMultipleResultModelTensor());
        POST_PROCESS_FUNCTIONS.put(DEFAULT_EMBEDDING, buildMultipleResultModelTensor());
        POST_PROCESS_FUNCTIONS.put(BEDROCK_EMBEDDING, buildSingleResultModelTensor());
    }

    public static Function<List<?>, List<ModelTensor>> buildSingleResultModelTensor() {
        return embedding -> {
            List<ModelTensor> modelTensors = new ArrayList<>();
            if (embedding == null) {
                throw new IllegalArgumentException("The embedding is null when using the built-in post-processing function.");
            }
            modelTensors.add(
                ModelTensor
                    .builder()
                    .name("sentence_embedding")
                    .dataType(MLResultDataType.FLOAT32)
                    .shape(new long[]{embedding.size()})
                    .data(embedding.toArray(new Number[0]))
                    .build()
            );
            return modelTensors;
        };
    }

    public static Function<List<?>, List<ModelTensor>> buildMultipleResultModelTensor() {
        return embeddings -> {
            List<ModelTensor> modelTensors = new ArrayList<>();
            if (embeddings == null) {
                throw new IllegalArgumentException("The list of embeddings is null when using the built-in post-processing function.");
            }
            embeddings.forEach(embedding -> {
                List<Number> eachEmbedding = (List<Number>) embedding;
                modelTensors.add(
                    ModelTensor
                        .builder()
                        .name("sentence_embedding")
                        .dataType(MLResultDataType.FLOAT32)
                        .shape(new long[]{eachEmbedding.size()})
                        .data(eachEmbedding.toArray(new Number[0]))
                        .build()
                );
            });
            return modelTensors;
        };
    }

    public static String getResponseFilter(String postProcessFunction) {
        return JSON_PATH_EXPRESSION.get(postProcessFunction);
    }

    public static Function<List<?>, List<ModelTensor>> get(String postProcessFunction) {
        return POST_PROCESS_FUNCTIONS.get(postProcessFunction);
    }

    public static boolean contains(String postProcessFunction) {
        return POST_PROCESS_FUNCTIONS.containsKey(postProcessFunction);
    }
}
