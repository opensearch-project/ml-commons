/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.model;

import java.util.List;
import java.util.Map;

import org.opensearch.ml.common.MLAgentType;
import org.opensearch.ml.common.connector.Connector;
import org.opensearch.ml.common.input.execute.agent.AgentInput;
import org.opensearch.ml.common.input.execute.agent.ContentBlock;
import org.opensearch.ml.common.input.execute.agent.InputType;
import org.opensearch.ml.common.input.execute.agent.Message;
import org.opensearch.ml.common.transport.register.MLRegisterModelInput;

/**
 * Abstract base class for model providers
 */
public abstract class ModelProvider {

    /**
     * Creates a connector for this model provider
     * @param modelName the model name (e.g., "us.anthropic.claude-3-7-sonnet-20250219-v1:0")
     * @param credential credential map for the connector
     * @param modelParameters additional model parameters
     * @return configured Connector
     */
    public abstract Connector createConnector(String modelName, Map<String, String> credential, Map<String, String> modelParameters);

    /**
     * Creates MLRegisterModelInput for this model provider
     * @param modelName the model name
     * @param connector the connector to use
     * @param modelParameters additional model parameters
     * @return configured MLRegisterModelInput
     */
    public abstract MLRegisterModelInput createModelInput(String modelName, Connector connector, Map<String, String> modelParameters);

    /**
     * Gets the LLM interface for function calling
     * @return the LLM interface string, or null if not supported
     */
    public abstract String getLLMInterface();

    // Input mapping methods for converting standardized AgentInput to provider-specific parameters

    /**
     * Maps simple text input to provider-specific request body parameters.
     * Each provider implements this to convert text to their specific format.
     *
     * @param text the text input
     * @return Map of parameters for the provider's request body template
     */
    public abstract Map<String, String> mapTextInput(String text, MLAgentType type);

    /**
     * Maps multi-modal content blocks to provider-specific request body parameters.
     * Each provider implements this to convert content blocks to their specific format.
     *
     * @param contentBlocks the list of content blocks
     * @return Map of parameters for the provider's request body template
     */
    public abstract Map<String, String> mapContentBlocks(List<ContentBlock> contentBlocks, MLAgentType type);

    /**
     * Maps message-based conversation to provider-specific request body parameters.
     * Each provider implements this to convert messages to their specific format.
     *
     * @param messages the list of messages
     * @return Map of parameters for the provider's request body template
     */
    public abstract Map<String, String> mapMessages(List<Message> messages, MLAgentType type);

    /**
     * Parses an provider-specific format response message JSON string into a unified Message object.
     *
     * @param json JSON string in the provider's native message format
     * @return a Message with content blocks
     */
    public abstract Message parseResponseMessage(String json);

    /**
     * Maps standardized AgentInput to provider-specific request body parameters.
     * This is the main entry point that delegates to the specific mapping methods.
     *
     * @param agentInput the standardized agent input
     * @return Map of parameters for the provider's request body template
     * @throws IllegalArgumentException if input type is unsupported
     */
    public Map<String, String> mapAgentInput(AgentInput agentInput, MLAgentType type) {
        if (agentInput == null || agentInput.getInput() == null) {
            throw new IllegalArgumentException("AgentInput and its input field cannot be null");
        }

        InputType inputType = agentInput.getInputType();
        return switch (inputType) {
            case TEXT -> mapTextInput((String) agentInput.getInput(), type);
            case CONTENT_BLOCKS -> {
                @SuppressWarnings("unchecked")
                List<ContentBlock> blocks = (List<ContentBlock>) agentInput.getInput();
                yield mapContentBlocks(blocks, type);
            }
            case MESSAGES -> {
                @SuppressWarnings("unchecked")
                List<Message> messages = (List<Message>) agentInput.getInput();
                yield mapMessages(messages, type);
            }
            default -> throw new IllegalArgumentException("Unsupported input type: " + inputType);
        };
    }
}
