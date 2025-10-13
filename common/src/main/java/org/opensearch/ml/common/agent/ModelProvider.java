/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.agent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.opensearch.ml.common.connector.Connector;
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
     * Get the protocol for this provider
     */
    public abstract String getProtocol();
    
    /**
     * Get the service name for this provider
     */
    public abstract String getServiceName();
    
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
    public abstract Map<String, Object> mapTextInput(String text);
    
    /**
     * Maps multi-modal content blocks to provider-specific request body parameters.
     * Each provider implements this to convert content blocks to their specific format.
     * 
     * @param contentBlocks the list of content blocks
     * @return Map of parameters for the provider's request body template
     */
    public abstract Map<String, Object> mapContentBlocks(List<ContentBlock> contentBlocks);
    
    /**
     * Maps message-based conversation to provider-specific request body parameters.
     * Each provider implements this to convert messages to their specific format.
     * 
     * @param messages the list of messages
     * @return Map of parameters for the provider's request body template
     */
    public abstract Map<String, Object> mapMessages(List<Message> messages);
    
    /**
     * Maps standardized AgentInput to provider-specific request body parameters.
     * This is the main entry point that delegates to the specific mapping methods.
     * 
     * @param agentInput the standardized agent input
     * @return Map of parameters for the provider's request body template
     * @throws IllegalArgumentException if input type is unsupported
     */
    public Map<String, Object> mapAgentInput(AgentInput agentInput) {
        if (agentInput == null || agentInput.getInput() == null) {
            throw new IllegalArgumentException("AgentInput and its input field cannot be null");
        }
        
        InputType inputType = agentInput.getInputType();
        
        switch (inputType) {
            case TEXT:
                return mapTextInput((String) agentInput.getInput());
            case CONTENT_BLOCKS:
                @SuppressWarnings("unchecked")
                List<ContentBlock> blocks = (List<ContentBlock>) agentInput.getInput();
                return mapContentBlocks(blocks);
            case MESSAGES:
                @SuppressWarnings("unchecked")
                List<Message> messages = (List<Message>) agentInput.getInput();
                return mapMessages(messages);
            default:
                throw new IllegalArgumentException("Unsupported input type: " + inputType);
        }
    }
    
    /**
     * Creates default parameters map for backward compatibility.
     * Providers can use this as a starting point for their parameter maps.
     * 
     * @return empty parameters map
     */
    protected Map<String, Object> createDefaultParameters() {
        return new HashMap<>();
    }
}