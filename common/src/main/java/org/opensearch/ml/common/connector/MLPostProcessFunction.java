/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.connector;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

import org.opensearch.ml.common.connector.functions.postprocess.BedrockBatchJobArnPostProcessFunction;
import org.opensearch.ml.common.connector.functions.postprocess.BedrockEmbeddingPostProcessFunction;
import org.opensearch.ml.common.connector.functions.postprocess.BedrockRerankPostProcessFunction;
import org.opensearch.ml.common.connector.functions.postprocess.CohereRerankPostProcessFunction;
import org.opensearch.ml.common.connector.functions.postprocess.EmbeddingPostProcessFunction;
import org.opensearch.ml.common.connector.functions.postprocess.RemoteMlCommonsPassthroughPostProcessFunction;
import org.opensearch.ml.common.output.model.MLResultDataType;
import org.opensearch.ml.common.output.model.ModelTensor;

public class MLPostProcessFunction {

    public static final String COHERE_EMBEDDING = "connector.post_process.cohere.embedding";
    public static final String COHERE_V2_EMBEDDING_FLOAT32 = "connector.post_process.cohere_v2.embedding.float";
    public static final String COHERE_V2_EMBEDDING_INT8 = "connector.post_process.cohere_v2.embedding.int8";
    public static final String COHERE_V2_EMBEDDING_UINT8 = "connector.post_process.cohere_v2.embedding.uint8";
    public static final String COHERE_V2_EMBEDDING_BINARY = "connector.post_process.cohere_v2.embedding.binary";
    public static final String COHERE_V2_EMBEDDING_UBINARY = "connector.post_process.cohere_v2.embedding.ubinary";
    public static final String OPENAI_EMBEDDING = "connector.post_process.openai.embedding";
    public static final String BEDROCK_EMBEDDING = "connector.post_process.bedrock.embedding";
    public static final String BEDROCK_V2_EMBEDDING_FLOAT = "connector.post_process.bedrock_v2.embedding.float";
    public static final String BEDROCK_V2_EMBEDDING_BINARY = "connector.post_process.bedrock_v2.embedding.binary";
    public static final String BEDROCK_NOVA_EMBEDDING = "connector.post_process.bedrock.nova.embedding";
    public static final String BEDROCK_BATCH_JOB_ARN = "connector.post_process.bedrock.batch_job_arn";
    public static final String COHERE_RERANK = "connector.post_process.cohere.rerank";
    public static final String BEDROCK_RERANK = "connector.post_process.bedrock.rerank";
    public static final String DEFAULT_EMBEDDING = "connector.post_process.default.embedding";
    public static final String DEFAULT_RERANK = "connector.post_process.default.rerank";
    // ML commons passthrough unwraps a remote ml-commons response and reconstructs model tensors directly based on remote inference
    public static final String ML_COMMONS_PASSTHROUGH = "connector.post_process.mlcommons.passthrough";

    private static final Map<String, String> JSON_PATH_EXPRESSION = new HashMap<>();

    private static final Map<String, BiFunction<Object, MLResultDataType, List<ModelTensor>>> POST_PROCESS_FUNCTIONS = new HashMap<>();

    static {
        EmbeddingPostProcessFunction embeddingPostProcessFunction = new EmbeddingPostProcessFunction();
        BedrockEmbeddingPostProcessFunction bedrockEmbeddingPostProcessFunction = new BedrockEmbeddingPostProcessFunction();
        BedrockBatchJobArnPostProcessFunction batchJobArnPostProcessFunction = new BedrockBatchJobArnPostProcessFunction();
        CohereRerankPostProcessFunction cohereRerankPostProcessFunction = new CohereRerankPostProcessFunction();
        BedrockRerankPostProcessFunction bedrockRerankPostProcessFunction = new BedrockRerankPostProcessFunction();
        RemoteMlCommonsPassthroughPostProcessFunction remoteMlCommonsPassthroughPostProcessFunction =
            new RemoteMlCommonsPassthroughPostProcessFunction();
        JSON_PATH_EXPRESSION.put(OPENAI_EMBEDDING, "$.data[*].embedding");
        JSON_PATH_EXPRESSION.put(COHERE_EMBEDDING, "$.embeddings");
        JSON_PATH_EXPRESSION.put(COHERE_V2_EMBEDDING_FLOAT32, "$.embeddings.float");
        JSON_PATH_EXPRESSION.put(COHERE_V2_EMBEDDING_INT8, "$.embeddings.int8");
        JSON_PATH_EXPRESSION.put(COHERE_V2_EMBEDDING_UINT8, "$.embeddings.uint8");
        JSON_PATH_EXPRESSION.put(COHERE_V2_EMBEDDING_BINARY, "$.embeddings.binary");
        JSON_PATH_EXPRESSION.put(COHERE_V2_EMBEDDING_UBINARY, "$.embeddings.ubinary");
        JSON_PATH_EXPRESSION.put(DEFAULT_EMBEDDING, "$[*]");
        JSON_PATH_EXPRESSION.put(BEDROCK_EMBEDDING, "$.embedding");
        JSON_PATH_EXPRESSION.put(BEDROCK_V2_EMBEDDING_FLOAT, "$.embeddingsByType.float");
        JSON_PATH_EXPRESSION.put(BEDROCK_V2_EMBEDDING_BINARY, "$.embeddingsByType.binary");
        JSON_PATH_EXPRESSION.put(BEDROCK_NOVA_EMBEDDING, "$.embeddings[*].embedding");
        JSON_PATH_EXPRESSION.put(BEDROCK_BATCH_JOB_ARN, "$");
        JSON_PATH_EXPRESSION.put(COHERE_RERANK, "$.results");
        JSON_PATH_EXPRESSION.put(BEDROCK_RERANK, "$.results");
        JSON_PATH_EXPRESSION.put(DEFAULT_RERANK, "$[*]");
        JSON_PATH_EXPRESSION.put(ML_COMMONS_PASSTHROUGH, "$");  // Get the entire response
        POST_PROCESS_FUNCTIONS.put(OPENAI_EMBEDDING, embeddingPostProcessFunction);
        POST_PROCESS_FUNCTIONS.put(COHERE_EMBEDDING, embeddingPostProcessFunction);
        POST_PROCESS_FUNCTIONS.put(COHERE_V2_EMBEDDING_FLOAT32, embeddingPostProcessFunction);
        POST_PROCESS_FUNCTIONS.put(COHERE_V2_EMBEDDING_INT8, embeddingPostProcessFunction);
        POST_PROCESS_FUNCTIONS.put(COHERE_V2_EMBEDDING_UINT8, embeddingPostProcessFunction);
        POST_PROCESS_FUNCTIONS.put(COHERE_V2_EMBEDDING_BINARY, embeddingPostProcessFunction);
        POST_PROCESS_FUNCTIONS.put(COHERE_V2_EMBEDDING_UBINARY, embeddingPostProcessFunction);
        POST_PROCESS_FUNCTIONS.put(DEFAULT_EMBEDDING, embeddingPostProcessFunction);
        POST_PROCESS_FUNCTIONS.put(BEDROCK_EMBEDDING, bedrockEmbeddingPostProcessFunction);
        POST_PROCESS_FUNCTIONS.put(BEDROCK_V2_EMBEDDING_FLOAT, bedrockEmbeddingPostProcessFunction);
        POST_PROCESS_FUNCTIONS.put(BEDROCK_V2_EMBEDDING_BINARY, bedrockEmbeddingPostProcessFunction);
        POST_PROCESS_FUNCTIONS.put(BEDROCK_NOVA_EMBEDDING, embeddingPostProcessFunction);
        POST_PROCESS_FUNCTIONS.put(BEDROCK_BATCH_JOB_ARN, batchJobArnPostProcessFunction);
        POST_PROCESS_FUNCTIONS.put(COHERE_RERANK, cohereRerankPostProcessFunction);
        POST_PROCESS_FUNCTIONS.put(BEDROCK_RERANK, bedrockRerankPostProcessFunction);
        POST_PROCESS_FUNCTIONS.put(DEFAULT_RERANK, cohereRerankPostProcessFunction);
        POST_PROCESS_FUNCTIONS.put(ML_COMMONS_PASSTHROUGH, remoteMlCommonsPassthroughPostProcessFunction);
    }

    public static String getResponseFilter(String postProcessFunction) {
        return JSON_PATH_EXPRESSION.get(postProcessFunction);
    }

    public static BiFunction<Object, MLResultDataType, List<ModelTensor>> get(String postProcessFunction) {
        return POST_PROCESS_FUNCTIONS.get(postProcessFunction);
    }

    public static boolean contains(String postProcessFunction) {
        return POST_PROCESS_FUNCTIONS.containsKey(postProcessFunction);
    }
}
