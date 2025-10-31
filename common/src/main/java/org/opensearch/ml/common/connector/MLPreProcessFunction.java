/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.connector;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import org.opensearch.ml.common.connector.functions.preprocess.AudioEmbeddingPreProcessFunction;
import org.opensearch.ml.common.connector.functions.preprocess.BedrockEmbeddingPreProcessFunction;
import org.opensearch.ml.common.connector.functions.preprocess.BedrockRerankPreProcessFunction;
import org.opensearch.ml.common.connector.functions.preprocess.CohereEmbeddingPreProcessFunction;
import org.opensearch.ml.common.connector.functions.preprocess.CohereMultiModalEmbeddingPreProcessFunction;
import org.opensearch.ml.common.connector.functions.preprocess.CohereRerankPreProcessFunction;
import org.opensearch.ml.common.connector.functions.preprocess.ImageEmbeddingPreProcessFunction;
import org.opensearch.ml.common.connector.functions.preprocess.MultiModalConnectorPreProcessFunction;
import org.opensearch.ml.common.connector.functions.preprocess.OpenAIEmbeddingPreProcessFunction;
import org.opensearch.ml.common.connector.functions.preprocess.VideoEmbeddingPreProcessFunction;
import org.opensearch.ml.common.dataset.remote.RemoteInferenceInputDataSet;
import org.opensearch.ml.common.input.MLInput;

public class MLPreProcessFunction {

    private static final Map<String, Function<MLInput, RemoteInferenceInputDataSet>> PRE_PROCESS_FUNCTIONS = new HashMap<>();
    public static final String TEXT_DOCS_TO_COHERE_EMBEDDING_INPUT = "connector.pre_process.cohere.embedding";
    public static final String IMAGE_TO_COHERE_MULTI_MODAL_EMBEDDING_INPUT = "connector.pre_process.cohere.multimodal_embedding";
    public static final String TEXT_DOCS_TO_OPENAI_EMBEDDING_INPUT = "connector.pre_process.openai.embedding";
    public static final String TEXT_DOCS_TO_BEDROCK_EMBEDDING_INPUT = "connector.pre_process.bedrock.embedding";
    public static final String TEXT_IMAGE_TO_BEDROCK_EMBEDDING_INPUT = "connector.pre_process.bedrock.multimodal_embedding";
    public static final String TEXT_TO_BEDROCK_NOVA_EMBEDDING_INPUT = "connector.pre_process.bedrock.nova.text_embedding";
    public static final String IMAGE_TO_BEDROCK_NOVA_EMBEDDING_INPUT = "connector.pre_process.bedrock.nova.image_embedding";
    public static final String VIDEO_TO_BEDROCK_NOVA_EMBEDDING_INPUT = "connector.pre_process.bedrock.nova.video_embedding";
    public static final String AUDIO_TO_BEDROCK_NOVA_EMBEDDING_INPUT = "connector.pre_process.bedrock.nova.audio_embedding";
    public static final String TEXT_DOCS_TO_DEFAULT_EMBEDDING_INPUT = "connector.pre_process.default.embedding";
    public static final String TEXT_SIMILARITY_TO_COHERE_RERANK_INPUT = "connector.pre_process.cohere.rerank";
    public static final String TEXT_SIMILARITY_TO_BEDROCK_RERANK_INPUT = "connector.pre_process.bedrock.rerank";
    public static final String TEXT_SIMILARITY_TO_DEFAULT_INPUT = "connector.pre_process.default.rerank";

    public static final String PROCESS_REMOTE_INFERENCE_INPUT = "pre_process_function.process_remote_inference_input";
    public static final String CONVERT_INPUT_TO_JSON_STRING = "pre_process_function.convert_input_to_json_string";

    static {
        CohereEmbeddingPreProcessFunction cohereEmbeddingPreProcessFunction = new CohereEmbeddingPreProcessFunction();
        OpenAIEmbeddingPreProcessFunction openAIEmbeddingPreProcessFunction = new OpenAIEmbeddingPreProcessFunction();
        BedrockEmbeddingPreProcessFunction bedrockEmbeddingPreProcessFunction = new BedrockEmbeddingPreProcessFunction();
        CohereRerankPreProcessFunction cohereRerankPreProcessFunction = new CohereRerankPreProcessFunction();
        BedrockRerankPreProcessFunction bedrockRerankPreProcessFunction = new BedrockRerankPreProcessFunction();
        MultiModalConnectorPreProcessFunction multiModalEmbeddingPreProcessFunction = new MultiModalConnectorPreProcessFunction();
        ImageEmbeddingPreProcessFunction imageEmbeddingPreProcessFunction = new ImageEmbeddingPreProcessFunction();
        VideoEmbeddingPreProcessFunction videoEmbeddingPreProcessFunction = new VideoEmbeddingPreProcessFunction();
        AudioEmbeddingPreProcessFunction audioEmbeddingPreProcessFunction = new AudioEmbeddingPreProcessFunction();
        CohereMultiModalEmbeddingPreProcessFunction cohereMultiModalEmbeddingPreProcessFunction =
            new CohereMultiModalEmbeddingPreProcessFunction();
        PRE_PROCESS_FUNCTIONS.put(TEXT_DOCS_TO_COHERE_EMBEDDING_INPUT, cohereEmbeddingPreProcessFunction);
        PRE_PROCESS_FUNCTIONS.put(IMAGE_TO_COHERE_MULTI_MODAL_EMBEDDING_INPUT, cohereMultiModalEmbeddingPreProcessFunction);
        PRE_PROCESS_FUNCTIONS.put(TEXT_IMAGE_TO_BEDROCK_EMBEDDING_INPUT, multiModalEmbeddingPreProcessFunction);
        PRE_PROCESS_FUNCTIONS.put(TEXT_TO_BEDROCK_NOVA_EMBEDDING_INPUT, bedrockEmbeddingPreProcessFunction);
        PRE_PROCESS_FUNCTIONS.put(IMAGE_TO_BEDROCK_NOVA_EMBEDDING_INPUT, imageEmbeddingPreProcessFunction);
        PRE_PROCESS_FUNCTIONS.put(VIDEO_TO_BEDROCK_NOVA_EMBEDDING_INPUT, videoEmbeddingPreProcessFunction);
        PRE_PROCESS_FUNCTIONS.put(AUDIO_TO_BEDROCK_NOVA_EMBEDDING_INPUT, audioEmbeddingPreProcessFunction);
        PRE_PROCESS_FUNCTIONS.put(TEXT_DOCS_TO_OPENAI_EMBEDDING_INPUT, openAIEmbeddingPreProcessFunction);
        PRE_PROCESS_FUNCTIONS.put(TEXT_DOCS_TO_DEFAULT_EMBEDDING_INPUT, openAIEmbeddingPreProcessFunction);
        PRE_PROCESS_FUNCTIONS.put(TEXT_DOCS_TO_BEDROCK_EMBEDDING_INPUT, bedrockEmbeddingPreProcessFunction);
        PRE_PROCESS_FUNCTIONS.put(TEXT_SIMILARITY_TO_DEFAULT_INPUT, cohereRerankPreProcessFunction);
        PRE_PROCESS_FUNCTIONS.put(TEXT_SIMILARITY_TO_COHERE_RERANK_INPUT, cohereRerankPreProcessFunction);
        PRE_PROCESS_FUNCTIONS.put(TEXT_SIMILARITY_TO_BEDROCK_RERANK_INPUT, bedrockRerankPreProcessFunction);
    }

    public static boolean contains(String functionName) {
        return PRE_PROCESS_FUNCTIONS.containsKey(functionName);
    }

    public static Function<MLInput, RemoteInferenceInputDataSet> get(String postProcessFunction) {
        return PRE_PROCESS_FUNCTIONS.get(postProcessFunction);
    }
}
