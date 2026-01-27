/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.remote.streaming;

import static org.opensearch.ml.engine.algorithms.agent.AgentUtils.LLM_INTERFACE_BEDROCK_CONVERSE;
import static org.opensearch.ml.engine.algorithms.agent.AgentUtils.LLM_INTERFACE_BEDROCK_CONVERSE_CLAUDE;
import static org.opensearch.ml.engine.algorithms.agent.AgentUtils.LLM_INTERFACE_OPENAI_V1_CHAT_COMPLETIONS;

import java.lang.reflect.Constructor;
import java.util.Locale;
import java.util.Map;

import org.opensearch.ml.common.connector.Connector;
import org.opensearch.ml.common.connector.ConnectorClientConfig;
import org.opensearch.ml.common.exception.MLException;

import software.amazon.awssdk.http.async.SdkAsyncHttpClient;

public class StreamingHandlerFactory {

    public static StreamingHandler createHandler(
        String llmInterface,
        Connector connector,
        SdkAsyncHttpClient httpClient,
        ConnectorClientConfig connectorClientConfig
    ) {
        return createHandler(llmInterface, connector, httpClient, connectorClientConfig, null);
    }

    public static StreamingHandler createHandler(
        String llmInterface,
        Connector connector,
        SdkAsyncHttpClient httpClient,
        ConnectorClientConfig connectorClientConfig,
        Map<String, String> parameters
    ) {
        switch (llmInterface.toLowerCase(Locale.ROOT)) {
            case LLM_INTERFACE_BEDROCK_CONVERSE:
            case LLM_INTERFACE_BEDROCK_CONVERSE_CLAUDE:
                return createBedrockHandler(httpClient, connector, parameters);
            case LLM_INTERFACE_OPENAI_V1_CHAT_COMPLETIONS:
                return createHttpHandler(llmInterface, connector, connectorClientConfig, parameters);
            default:
                throw new IllegalArgumentException("Unsupported LLM interface: " + llmInterface);
        }
    }

    private static StreamingHandler createBedrockHandler(
        SdkAsyncHttpClient httpClient,
        Connector connector,
        Map<String, String> parameters
    ) {
        try {
            // Use reflection to avoid hard dependency
            Class<?> handlerClass = Class.forName("org.opensearch.ml.engine.algorithms.remote.streaming.BedrockStreamingHandler");
            Constructor<?> constructor = handlerClass
                .getConstructor(SdkAsyncHttpClient.class, Class.forName("org.opensearch.ml.common.connector.AwsConnector"), Map.class);
            return (StreamingHandler) constructor.newInstance(httpClient, connector, parameters);
        } catch (ClassNotFoundException e) {
            throw new MLException("Bedrock streaming not available - Bedrock SDK not found", e);
        } catch (Exception e) {
            throw new MLException("Failed to initialize Bedrock streaming handler", e);
        }
    }

    private static StreamingHandler createHttpHandler(
        String llmInterface,
        Connector connector,
        ConnectorClientConfig connectorClientConfig,
        Map<String, String> parameters
    ) {
        return new HttpStreamingHandler(llmInterface, connector, connectorClientConfig, parameters);
    }
}
