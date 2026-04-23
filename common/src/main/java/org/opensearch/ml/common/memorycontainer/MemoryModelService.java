/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.memorycontainer;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.agent.BedrockEmbeddingModelProvider;
import org.opensearch.ml.common.agent.MLAgentModelSpec;
import org.opensearch.ml.common.agent.OpenaiEmbeddingModelProvider;
import org.opensearch.ml.common.connector.AwsConnector;
import org.opensearch.ml.common.connector.Connector;
import org.opensearch.ml.common.connector.ConnectorAction;
import org.opensearch.ml.common.connector.ConnectorClientConfig;
import org.opensearch.ml.common.connector.ConnectorProtocols;
import org.opensearch.ml.common.model.ModelProvider;
import org.opensearch.ml.common.model.ModelProviderFactory;
import org.opensearch.ml.common.transport.register.MLRegisterModelInput;

import lombok.extern.log4j.Log4j2;

/**
 * Service for creating models during memory container creation.
 * Translates inline model specs into registered models, analogous to AgentModelService.
 */
@Log4j2
public class MemoryModelService {

    // Memory LLM templates — use system_prompt/user_prompt (not agent-style body/_chat_history)
    private static final String BEDROCK_CONVERSE_MEMORY_TEMPLATE = "{\"system\":[{\"text\":\"${parameters.system_prompt}\"}],"
        + "\"messages\":[{\"role\":\"user\",\"content\":[{\"text\":\"${parameters.user_prompt}\"}]}]}";

    private static final String OPENAI_MEMORY_TEMPLATE =
        "{\"model\":\"${parameters.model}\",\"messages\":[{\"role\":\"system\",\"content\":\"${parameters.system_prompt}\"},"
            + "{\"role\":\"user\",\"content\":\"${parameters.user_prompt}\"}]}";

    private static final String GEMINI_MEMORY_TEMPLATE = "{\"system_instruction\":{\"parts\":[{\"text\":\"${parameters.system_prompt}\"}]},"
        + "\"contents\":[{\"role\":\"user\",\"parts\":[{\"text\":\"${parameters.user_prompt}\"}]}]}";

    private static final String DEFAULT_REGION = MemoryContainerConstants.DEFAULT_AWS_REGION;
    private static final java.util.regex.Pattern REGION_PATTERN = MemoryContainerConstants.AWS_REGION_PATTERN;

    /**
     * Creates a model registration input from an inline model spec.
     * For embedding models, uses the standard provider.
     * For LLM models used by memory, uses a memory-specific connector template.
     */
    public static MLRegisterModelInput createModelFromSpec(MLAgentModelSpec modelSpec, boolean isMemoryLlm) {
        validateModelSpec(modelSpec);

        if (isMemoryLlm) {
            return createMemoryLlmInput(modelSpec);
        }

        ModelProvider provider = ModelProviderFactory.getProvider(modelSpec.getModelProvider());
        Connector connector = provider.createConnector(modelSpec.getModelId(), modelSpec.getCredential(), modelSpec.getModelParameters());
        return provider.createModelInput(modelSpec.getModelId(), connector, modelSpec.getModelParameters());
    }

    /** Backward-compatible overload for embedding models. */
    public static MLRegisterModelInput createModelFromSpec(MLAgentModelSpec modelSpec) {
        return createModelFromSpec(modelSpec, false);
    }

    /**
     * Creates a Bedrock Converse LLM model with memory-compatible request body.
     * Uses system_prompt/user_prompt instead of agent-style body/_chat_history.
     */
    private static MLRegisterModelInput createMemoryLlmInput(MLAgentModelSpec modelSpec) {
        String provider = modelSpec.getModelProvider().toLowerCase();
        String url;
        String requestBody;
        String protocol;
        Map<String, String> headers = new HashMap<>(Map.of("content-type", "application/json"));

        Map<String, String> parameters = new HashMap<>();
        parameters.put("model", modelSpec.getModelId());
        if (modelSpec.getModelParameters() != null) {
            parameters.putAll(modelSpec.getModelParameters());
        }

        if (provider.equals("bedrock/converse")) {
            parameters.putIfAbsent("region", DEFAULT_REGION);
            parameters.put("service_name", "bedrock");
            // Validate region to prevent SSRF via URL injection
            String region = parameters.get("region");
            if (!REGION_PATTERN.matcher(region).matches()) {
                throw new IllegalArgumentException("Invalid AWS region format: " + region);
            }
            url = "https://bedrock-runtime.${parameters.region}.amazonaws.com/model/${parameters.model}/converse";
            requestBody = BEDROCK_CONVERSE_MEMORY_TEMPLATE;
            protocol = ConnectorProtocols.AWS_SIGV4;
        } else if (provider.equals("openai/v1/chat/completions")) {
            url = "https://api.openai.com/v1/chat/completions";
            requestBody = OPENAI_MEMORY_TEMPLATE;
            protocol = ConnectorProtocols.HTTP;
            headers.put("Authorization", "Bearer ${credential.openAI_key}");
        } else if (provider.equals("gemini/v1beta/generatecontent")) {
            url = "https://generativelanguage.googleapis.com/v1beta/models/${parameters.model}:generateContent";
            requestBody = GEMINI_MEMORY_TEMPLATE;
            protocol = ConnectorProtocols.HTTP;
            headers.put("x-goog-api-key", "${credential.gemini_api_key}");
        } else {
            throw new IllegalArgumentException("Unsupported LLM provider for memory: " + modelSpec.getModelProvider());
        }

        ConnectorAction predictAction = ConnectorAction
            .builder()
            .actionType(ConnectorAction.ActionType.PREDICT)
            .method("POST")
            .url(url)
            .headers(headers)
            .requestBody(requestBody)
            .build();

        ConnectorClientConfig clientConfig = new ConnectorClientConfig();
        clientConfig.setMaxRetryTimes(3);

        Connector connector;
        if (protocol.equals(ConnectorProtocols.AWS_SIGV4)) {
            connector = AwsConnector
                .awsConnectorBuilder()
                .name("Auto-generated " + modelSpec.getModelProvider() + " connector for Memory")
                .description("Auto-generated LLM connector for memory container")
                .version("1")
                .protocol(protocol)
                .parameters(parameters)
                .credential(modelSpec.getCredential() != null ? modelSpec.getCredential() : new HashMap<>())
                .actions(List.of(predictAction))
                .connectorClientConfig(clientConfig)
                .build();
        } else {
            connector = org.opensearch.ml.common.connector.HttpConnector
                .builder()
                .name("Auto-generated " + modelSpec.getModelProvider() + " connector for Memory")
                .description("Auto-generated LLM connector for memory container")
                .version("1")
                .protocol(protocol)
                .parameters(parameters)
                .credential(modelSpec.getCredential() != null ? modelSpec.getCredential() : new HashMap<>())
                .actions(List.of(predictAction))
                .connectorClientConfig(clientConfig)
                .build();
        }

        return MLRegisterModelInput
            .builder()
            .functionName(FunctionName.REMOTE)
            .modelName("Auto-generated LLM for memory: " + modelSpec.getModelId())
            .description("Auto-generated LLM for memory container")
            .connector(connector)
            .build();
    }

    /**
     * Auto-detect embedding model type (TEXT_EMBEDDING or SPARSE_ENCODING) from model ID.
     * @return FunctionName or null if unknown
     */
    public static FunctionName detectEmbeddingType(String modelId) {
        EmbeddingModelInfo info = BedrockEmbeddingModelProvider.getModelInfo(modelId);
        if (info != null)
            return info.functionName;
        EmbeddingModelInfo openaiInfo = OpenaiEmbeddingModelProvider.getModelInfo(modelId);
        if (openaiInfo != null)
            return openaiInfo.functionName;
        return null;
    }

    public static Integer detectEmbeddingDimension(String modelId) {
        EmbeddingModelInfo info = BedrockEmbeddingModelProvider.getModelInfo(modelId);
        if (info != null)
            return info.dimension;
        EmbeddingModelInfo openaiInfo = OpenaiEmbeddingModelProvider.getModelInfo(modelId);
        if (openaiInfo != null)
            return openaiInfo.dimension;
        return null;
    }

    /**
     * Returns the correct llm_result_path for a given LLM provider.
     */
    public static String getLlmResultPath(String modelProvider) {
        if (modelProvider == null)
            return null;
        String provider = modelProvider.toLowerCase();
        if (provider.equals("bedrock/converse")) {
            return "$.output.message.content[0].text";
        } else if (provider.equals("openai/v1/chat/completions")) {
            return "$.choices[0].message.content";
        } else if (provider.equals("gemini/v1beta/generatecontent")) {
            return "$.candidates[0].content.parts[0].text";
        }
        return null;
    }

    private static void validateModelSpec(MLAgentModelSpec modelSpec) {
        if (modelSpec == null) {
            throw new IllegalArgumentException("Model specification not found");
        }
        if (modelSpec.getModelId() == null || modelSpec.getModelId().trim().isEmpty()) {
            throw new IllegalArgumentException("model_id cannot be null or empty");
        }
        if (modelSpec.getModelProvider() == null || modelSpec.getModelProvider().trim().isEmpty()) {
            throw new IllegalArgumentException("model_provider cannot be null or empty");
        }
    }
}
