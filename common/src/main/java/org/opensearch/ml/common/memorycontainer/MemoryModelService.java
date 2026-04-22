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

    /**
     * Memory LLM request body template using system_prompt/user_prompt parameters.
     * This differs from the agent template which uses body/_chat_history/_interactions.
     * The memory processing service calls the LLM with system_prompt and user_prompt.
     */
    private static final String MEMORY_LLM_REQUEST_BODY = "{\"system\":[{\"text\":\"${parameters.system_prompt}\"}],"
        + "\"messages\":[{\"role\":\"user\",\"content\":[{\"text\":\"${parameters.user_prompt}\"}]}]}";

    private static final String DEFAULT_REGION = "us-east-1";

    /**
     * Creates a model registration input from an inline model spec.
     * For embedding models, uses the standard provider.
     * For LLM models used by memory, uses a memory-specific connector template.
     */
    public static MLRegisterModelInput createModelFromSpec(MLAgentModelSpec modelSpec, boolean isMemoryLlm) {
        validateModelSpec(modelSpec);

        if (isMemoryLlm && modelSpec.getModelProvider().equalsIgnoreCase("bedrock/converse")) {
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
        Map<String, String> parameters = new HashMap<>();
        parameters.put("region", DEFAULT_REGION);
        parameters.put("service_name", "bedrock");
        parameters.put("model", modelSpec.getModelId());
        if (modelSpec.getModelParameters() != null) {
            parameters.putAll(modelSpec.getModelParameters());
        }

        ConnectorAction predictAction = ConnectorAction
            .builder()
            .actionType(ConnectorAction.ActionType.PREDICT)
            .method("POST")
            .url("https://bedrock-runtime.${parameters.region}.amazonaws.com/model/${parameters.model}/converse")
            .headers(Map.of("content-type", "application/json"))
            .requestBody(MEMORY_LLM_REQUEST_BODY)
            .build();

        ConnectorClientConfig clientConfig = new ConnectorClientConfig();
        clientConfig.setMaxRetryTimes(3);

        Connector connector = AwsConnector
            .awsConnectorBuilder()
            .name("Auto-generated Bedrock Converse connector for Memory")
            .description("Auto-generated LLM connector for memory container (uses system_prompt/user_prompt)")
            .version("1")
            .protocol(ConnectorProtocols.AWS_SIGV4)
            .parameters(parameters)
            .credential(modelSpec.getCredential() != null ? modelSpec.getCredential() : new HashMap<>())
            .actions(List.of(predictAction))
            .connectorClientConfig(clientConfig)
            .build();

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
        BedrockEmbeddingModelProvider.EmbeddingModelInfo info = BedrockEmbeddingModelProvider.getModelInfo(modelId);
        return info != null ? info.functionName : null;
    }

    /**
     * Auto-detect embedding dimension from model ID.
     * @return dimension or null if unknown
     */
    public static Integer detectEmbeddingDimension(String modelId) {
        BedrockEmbeddingModelProvider.EmbeddingModelInfo info = BedrockEmbeddingModelProvider.getModelInfo(modelId);
        return info != null ? info.dimension : null;
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
