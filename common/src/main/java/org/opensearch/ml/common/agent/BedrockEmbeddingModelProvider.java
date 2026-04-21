/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.agent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.MLAgentType;
import org.opensearch.ml.common.connector.AwsConnector;
import org.opensearch.ml.common.connector.Connector;
import org.opensearch.ml.common.connector.ConnectorAction;
import org.opensearch.ml.common.connector.ConnectorClientConfig;
import org.opensearch.ml.common.connector.ConnectorProtocols;
import org.opensearch.ml.common.input.execute.agent.ContentBlock;
import org.opensearch.ml.common.input.execute.agent.Message;
import org.opensearch.ml.common.model.ModelProvider;
import org.opensearch.ml.common.transport.register.MLRegisterModelInput;

/**
 * Model provider for Bedrock Embedding API (Titan, Cohere).
 * Uses the /invoke endpoint (not /converse) with inputText-based request body.
 */
public class BedrockEmbeddingModelProvider extends ModelProvider {

    private static final String DEFAULT_REGION = "us-east-1";

    private static final String REQUEST_BODY_TEMPLATE =
        "{ \"inputText\": \"${parameters.inputText}\", \"dimensions\": ${parameters.dimensions},"
            + " \"normalize\": ${parameters.normalize}, \"embeddingTypes\": ${parameters.embeddingTypes} }";

    /**
     * Known Bedrock embedding models with their type and default dimension.
     */
    public static final Map<String, EmbeddingModelInfo> KNOWN_MODELS = Map.of(
        "amazon.titan-embed-text-v2:0", new EmbeddingModelInfo(FunctionName.TEXT_EMBEDDING, 1024),
        "amazon.titan-embed-text-v1", new EmbeddingModelInfo(FunctionName.TEXT_EMBEDDING, 1536),
        "amazon.titan-embed-image-v1", new EmbeddingModelInfo(FunctionName.TEXT_EMBEDDING, 1024),
        "cohere.embed-english-v3", new EmbeddingModelInfo(FunctionName.TEXT_EMBEDDING, 1024),
        "cohere.embed-multilingual-v3", new EmbeddingModelInfo(FunctionName.TEXT_EMBEDDING, 1024)
    );

    @Override
    public Connector createConnector(String modelId, Map<String, String> credential, Map<String, String> modelParameters) {
        EmbeddingModelInfo info = KNOWN_MODELS.get(modelId);

        Map<String, String> parameters = new HashMap<>();
        parameters.put("region", DEFAULT_REGION);
        parameters.put("service_name", "bedrock");
        parameters.put("model", modelId);
        parameters.put("dimensions", String.valueOf(info != null ? info.dimension : 1024));
        parameters.put("normalize", "true");
        parameters.put("embeddingTypes", "[\"float\"]");

        if (modelParameters != null) {
            parameters.putAll(modelParameters);
        }

        Map<String, String> headers = new HashMap<>();
        headers.put("content-type", "application/json");
        headers.put("x-amz-content-sha256", "required");

        ConnectorAction predictAction = ConnectorAction
            .builder()
            .actionType(ConnectorAction.ActionType.PREDICT)
            .method("POST")
            .url("https://bedrock-runtime.${parameters.region}.amazonaws.com/model/${parameters.model}/invoke")
            .headers(headers)
            .requestBody(REQUEST_BODY_TEMPLATE)
            .preProcessFunction("connector.pre_process.bedrock.embedding")
            .postProcessFunction("connector.post_process.bedrock.embedding")
            .build();

        ConnectorClientConfig connectorClientConfig = new ConnectorClientConfig();
        connectorClientConfig.setMaxRetryTimes(3);

        return AwsConnector
            .awsConnectorBuilder()
            .name("Auto-generated Bedrock Embedding connector")
            .description("Auto-generated connector for Bedrock Embedding API")
            .version("1")
            .protocol(ConnectorProtocols.AWS_SIGV4)
            .parameters(parameters)
            .credential(credential != null ? credential : new HashMap<>())
            .actions(List.of(predictAction))
            .connectorClientConfig(connectorClientConfig)
            .build();
    }

    @Override
    public MLRegisterModelInput createModelInput(String modelName, Connector connector, Map<String, String> modelParameters) {
        return MLRegisterModelInput
            .builder()
            .functionName(FunctionName.REMOTE)
            .modelName("Auto-generated embedding model for " + modelName)
            .description("Auto-generated embedding model for memory container")
            .connector(connector)
            .build();
    }

    @Override
    public String getLLMInterface() {
        return null; // Embedding models don't have an LLM interface
    }

    // Embedding models don't support agent input mapping — these are LLM-only operations
    @Override
    public Map<String, String> mapTextInput(String text, MLAgentType type) {
        throw new UnsupportedOperationException("Embedding model providers do not support agent input mapping");
    }

    @Override
    public Map<String, String> mapContentBlocks(List<ContentBlock> contentBlocks, MLAgentType type) {
        throw new UnsupportedOperationException("Embedding model providers do not support agent input mapping");
    }

    @Override
    public Map<String, String> mapMessages(List<Message> messages, MLAgentType type) {
        throw new UnsupportedOperationException("Embedding model providers do not support agent input mapping");
    }

    @Override
    public String extractMessageFromResponse(Map<String, ?> responseData) {
        throw new UnsupportedOperationException("Embedding model providers do not support response extraction");
    }

    @Override
    public Message parseToUnifiedMessage(String json) {
        throw new UnsupportedOperationException("Embedding model providers do not support message parsing");
    }

    /**
     * Look up embedding model info from known models registry.
     * @param modelId the Bedrock model ID
     * @return EmbeddingModelInfo if known, null otherwise
     */
    public static EmbeddingModelInfo getModelInfo(String modelId) {
        return KNOWN_MODELS.get(modelId);
    }

    /**
     * Info about a known embedding model.
     */
    public static class EmbeddingModelInfo {
        public final FunctionName functionName;
        public final int dimension;

        public EmbeddingModelInfo(FunctionName functionName, int dimension) {
            this.functionName = functionName;
            this.dimension = dimension;
        }
    }
}
