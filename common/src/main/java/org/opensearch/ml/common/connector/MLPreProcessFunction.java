/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.connector;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public class MLPreProcessFunction {

    private static final Map<String, Function<List<String>, Map<String, Object>>> PRE_PROCESS_FUNCTIONS = new HashMap<>();
    public static final String TEXT_DOCS_TO_COHERE_EMBEDDING_INPUT = "connector.pre_process.cohere.embedding";
    public static final String TEXT_DOCS_TO_OPENAI_EMBEDDING_INPUT = "connector.pre_process.openai.embedding";

    public static final String NEURAL_SEARCH_EMBEDDING_INPUT = "connector.pre_process.neural_search.text_embedding";

    private static Function<List<String>, Map<String, Object>> cohereTextEmbeddingPreProcess() {
        return inputs -> Map.of("parameters", Map.of("texts", inputs));
    }

    private static Function<List<String>, Map<String, Object>> openAiTextEmbeddingPreProcess() {
        return inputs -> Map.of("parameters", Map.of("input", inputs));
    }

    static {
        PRE_PROCESS_FUNCTIONS.put(TEXT_DOCS_TO_COHERE_EMBEDDING_INPUT, cohereTextEmbeddingPreProcess());
        PRE_PROCESS_FUNCTIONS.put(TEXT_DOCS_TO_OPENAI_EMBEDDING_INPUT, openAiTextEmbeddingPreProcess());
        PRE_PROCESS_FUNCTIONS.put(NEURAL_SEARCH_EMBEDDING_INPUT, openAiTextEmbeddingPreProcess());
    }

    public static boolean contains(String functionName) {
        return PRE_PROCESS_FUNCTIONS.containsKey(functionName);
    }

    public static Function<List<String>, Map<String, Object>> get(String postProcessFunction) {
        return PRE_PROCESS_FUNCTIONS.get(postProcessFunction);
    }
}
