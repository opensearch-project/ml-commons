/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.connector;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.opensearch.ml.common.connector.functions.postprocess.BedrockBatchJobArnPostProcessFunction;
import org.opensearch.ml.common.connector.functions.postprocess.BedrockEmbeddingPostProcessFunction;
import org.opensearch.ml.common.connector.functions.postprocess.CohereRerankPostProcessFunction;
import org.opensearch.ml.common.connector.functions.postprocess.EmbeddingPostProcessFunction;
import org.opensearch.ml.common.output.model.ModelTensor;

public class MLPostProcessFunction {

    public static final String COHERE_EMBEDDING = "connector.post_process.cohere.embedding";
    public static final String OPENAI_EMBEDDING = "connector.post_process.openai.embedding";
    public static final String BEDROCK_EMBEDDING = "connector.post_process.bedrock.embedding";
    public static final String BEDROCK_BATCH_JOB_ARN = "connector.post_process.bedrock.batch_job_arn";
    public static final String COHERE_RERANK = "connector.post_process.cohere.rerank";
    public static final String DEFAULT_EMBEDDING = "connector.post_process.default.embedding";
    public static final String DEFAULT_RERANK = "connector.post_process.default.rerank";

    private static final Map<String, String> JSON_PATH_EXPRESSION = new HashMap<>();

    private static final Map<String, Function<Object, List<ModelTensor>>> POST_PROCESS_FUNCTIONS = new HashMap<>();

    static {
        EmbeddingPostProcessFunction embeddingPostProcessFunction = new EmbeddingPostProcessFunction();
        BedrockEmbeddingPostProcessFunction bedrockEmbeddingPostProcessFunction = new BedrockEmbeddingPostProcessFunction();
        BedrockBatchJobArnPostProcessFunction batchJobArnPostProcessFunction = new BedrockBatchJobArnPostProcessFunction();
        CohereRerankPostProcessFunction cohereRerankPostProcessFunction = new CohereRerankPostProcessFunction();
        JSON_PATH_EXPRESSION.put(OPENAI_EMBEDDING, "$.data[*].embedding");
        JSON_PATH_EXPRESSION.put(COHERE_EMBEDDING, "$.embeddings");
        JSON_PATH_EXPRESSION.put(DEFAULT_EMBEDDING, "$[*]");
        JSON_PATH_EXPRESSION.put(BEDROCK_EMBEDDING, "$.embedding");
        JSON_PATH_EXPRESSION.put(BEDROCK_BATCH_JOB_ARN, "$");
        JSON_PATH_EXPRESSION.put(COHERE_RERANK, "$.results");
        JSON_PATH_EXPRESSION.put(DEFAULT_RERANK, "$[*]");
        POST_PROCESS_FUNCTIONS.put(OPENAI_EMBEDDING, embeddingPostProcessFunction);
        POST_PROCESS_FUNCTIONS.put(COHERE_EMBEDDING, embeddingPostProcessFunction);
        POST_PROCESS_FUNCTIONS.put(DEFAULT_EMBEDDING, embeddingPostProcessFunction);
        POST_PROCESS_FUNCTIONS.put(BEDROCK_EMBEDDING, bedrockEmbeddingPostProcessFunction);
        POST_PROCESS_FUNCTIONS.put(BEDROCK_BATCH_JOB_ARN, batchJobArnPostProcessFunction);
        POST_PROCESS_FUNCTIONS.put(COHERE_RERANK, cohereRerankPostProcessFunction);
        POST_PROCESS_FUNCTIONS.put(DEFAULT_RERANK, cohereRerankPostProcessFunction);
    }

    public static String getResponseFilter(String postProcessFunction) {
        return JSON_PATH_EXPRESSION.get(postProcessFunction);
    }

    public static Function<Object, List<ModelTensor>> get(String postProcessFunction) {
        return POST_PROCESS_FUNCTIONS.get(postProcessFunction);
    }

    public static boolean contains(String postProcessFunction) {
        return POST_PROCESS_FUNCTIONS.containsKey(postProcessFunction);
    }
}
