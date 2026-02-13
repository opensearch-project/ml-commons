/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.agents.models;

import java.util.List;
import java.util.Map;

import org.opensearch.ml.common.MLAgentType;
import org.opensearch.ml.common.agent.MLToolSpec;
import org.opensearch.ml.common.connector.Connector;
import org.opensearch.ml.common.input.execute.agent.AgentInput;
import org.opensearch.ml.common.input.execute.agent.ContentBlock;
import org.opensearch.ml.common.input.execute.agent.InputType;
import org.opensearch.ml.common.input.execute.agent.Message;
import org.opensearch.ml.common.spi.tools.Tool;
import org.opensearch.ml.common.transport.register.MLRegisterModelInput;

/**
 * Abstract base class for model providers.
 * Handles all provider-specific operations for agents including connector creation,
 * input mapping, tool configuration, and response parsing.
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

    // Tool-related methods for agent execution

    /**
     * Formats tool configuration for this provider.
     * Generates the tool_configs parameter value that will be inserted into the request template.
     *
     * @param tools Map of tool type to Tool instance
     * @param toolSpecMap Map of tool type to MLToolSpec
     * @return JSON string to append to LLM request parameters (e.g., ",\"tools\":[...]" or ",\"toolConfig\":{...}")
     */
    public abstract String formatToolConfiguration(Map<String, Tool> tools, Map<String, MLToolSpec> toolSpecMap);

    /**
     * Formats assistant message with tool use requests.
     * Used when storing conversation history after LLM requests tools.
     *
     * @param content Content blocks (may include text and toolUse)
     * @param toolUseBlocks List of toolUse blocks extracted from content
     * @return JSON string representing the assistant message
     */
    public abstract String formatAssistantToolUseMessage(List<Map<String, Object>> content, List<Map<String, Object>> toolUseBlocks);

    /**
     * Formats tool result messages.
     * Used when storing conversation history after tools execute.
     *
     * @param toolResults List of tool results (each with toolUseId, status, content)
     * @return JSON string representing tool result message(s)
     */
    public abstract String formatToolResultMessages(List<Map<String, Object>> toolResults);

    /**
     * Parses LLM response into a unified format.
     * Normalizes provider-specific response structures.
     *
     * @param rawResponse Raw response map from LLM
     * @return Parsed response with normalized fields
     */
    public abstract ParsedLLMResponse parseResponse(Map<String, Object> rawResponse);

    /**
     * Unified parsed LLM response format.
     * Normalizes differences between provider response structures.
     */
    public static class ParsedLLMResponse {
        private String stopReason;
        private Map<String, Object> message;
        private List<Map<String, Object>> content;
        private List<Map<String, Object>> toolUseBlocks;
        private Map<String, Object> usage;

        public String getStopReason() {
            return stopReason;
        }

        public void setStopReason(String stopReason) {
            this.stopReason = stopReason;
        }

        public Map<String, Object> getMessage() {
            return message;
        }

        public void setMessage(Map<String, Object> message) {
            this.message = message;
        }

        public List<Map<String, Object>> getContent() {
            return content;
        }

        public void setContent(List<Map<String, Object>> content) {
            this.content = content;
        }

        public List<Map<String, Object>> getToolUseBlocks() {
            return toolUseBlocks;
        }

        public void setToolUseBlocks(List<Map<String, Object>> toolUseBlocks) {
            this.toolUseBlocks = toolUseBlocks;
        }

        public Map<String, Object> getUsage() {
            return usage;
        }

        public void setUsage(Map<String, Object> usage) {
            this.usage = usage;
        }
    }
}
