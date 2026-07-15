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
import org.opensearch.ml.common.connector.Connector;
import org.opensearch.ml.common.connector.ConnectorAction;
import org.opensearch.ml.common.connector.ConnectorClientConfig;
import org.opensearch.ml.common.connector.ConnectorProtocols;
import org.opensearch.ml.common.connector.HttpConnector;
import org.opensearch.ml.common.input.execute.agent.ContentBlock;
import org.opensearch.ml.common.input.execute.agent.Message;
import org.opensearch.ml.common.memorycontainer.EmbeddingModelInfo;
import org.opensearch.ml.common.model.ModelProvider;
import org.opensearch.ml.common.transport.register.MLRegisterModelInput;

import lombok.extern.log4j.Log4j2;

/**
 * Model provider for OpenAI Embedding API (text-embedding-3-small, text-embedding-3-large, etc.)
 */
@Log4j2
public class OpenaiEmbeddingModelProvider extends ModelProvider {

    private static final String REQUEST_BODY = "{ \"input\": ${parameters.input}, \"model\": \"${parameters.model}\" }";

    public static final Map<String, EmbeddingModelInfo> KNOWN_MODELS = Map
        .ofEntries(
            Map.entry("text-embedding-3-small", new EmbeddingModelInfo(FunctionName.TEXT_EMBEDDING, 1536)),
            Map.entry("text-embedding-3-large", new EmbeddingModelInfo(FunctionName.TEXT_EMBEDDING, 3072)),
            Map.entry("text-embedding-ada-002", new EmbeddingModelInfo(FunctionName.TEXT_EMBEDDING, 1536))
        );

    @Override
    public Connector createConnector(String modelId, Map<String, String> credential, Map<String, String> modelParameters) {
        Map<String, String> parameters = new HashMap<>();
        parameters.put("model", modelId);
        if (modelParameters != null) {
            parameters.putAll(modelParameters);
        }

        Map<String, String> headers = new HashMap<>();
        headers.put("Authorization", "Bearer ${credential.openAI_key}");

        ConnectorAction predictAction = ConnectorAction
            .builder()
            .actionType(ConnectorAction.ActionType.PREDICT)
            .method("POST")
            .url("https://api.openai.com/v1/embeddings")
            .headers(headers)
            .requestBody(REQUEST_BODY)
            .preProcessFunction("connector.pre_process.openai.embedding")
            .postProcessFunction("connector.post_process.openai.embedding")
            .build();

        ConnectorClientConfig clientConfig = new ConnectorClientConfig();
        clientConfig.setMaxRetryTimes(3);

        return HttpConnector
            .builder()
            .name("Auto-generated OpenAI Embedding connector")
            .description("Auto-generated connector for OpenAI Embedding API")
            .version("1")
            .protocol(ConnectorProtocols.HTTP)
            .parameters(parameters)
            .credential(credential != null ? credential : new HashMap<>())
            .actions(List.of(predictAction))
            .connectorClientConfig(clientConfig)
            .build();
    }

    @Override
    public MLRegisterModelInput createModelInput(String modelName, Connector connector, Map<String, String> modelParameters) {
        return MLRegisterModelInput
            .builder()
            .functionName(FunctionName.REMOTE)
            .modelName("Auto-generated OpenAI embedding model for " + modelName)
            .description("Auto-generated embedding model for memory container")
            .connector(connector)
            .build();
    }

    public static EmbeddingModelInfo getModelInfo(String modelId) {
        return KNOWN_MODELS.get(modelId);
    }

    // Embedding providers don't support agent operations
    @Override
    public String getLLMInterface() {
        return null;
    }

    @Override
    public Map<String, String> mapTextInput(String t, MLAgentType a) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Map<String, String> mapContentBlocks(List<ContentBlock> c, MLAgentType a) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Map<String, String> mapMessages(List<Message> m, MLAgentType a) {
        throw new UnsupportedOperationException();
    }

    @Override
    public String extractMessageFromResponse(Map<String, ?> r) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Message parseToUnifiedMessage(String j) {
        throw new UnsupportedOperationException();
    }

}
