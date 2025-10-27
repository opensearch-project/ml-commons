/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.contextmanager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.opensearch.ml.common.agent.MLToolSpec;
import org.opensearch.ml.common.conversation.Interaction;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Context object that contains all components of the agent execution context.
 * This object is passed to context managers for inspection and transformation.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContextManagerContext {

    /**
     * The invocation state from the hook system
     */
    private Map<String, Object> invocationState;

    /**
     * The system prompt for the LLM
     */
    private String systemPrompt;

    /**
     * The chat history as a list of interactions
     */
    @Builder.Default
    private List<Interaction> chatHistory = new ArrayList<>();

    /**
     * The current user prompt/input
     */
    private String userPrompt;

    /**
     * The tool configurations available to the agent
     */
    @Builder.Default
    private List<MLToolSpec> toolConfigs = new ArrayList<>();

    /**
     * The tool interactions/results from tool executions
     */
    @Builder.Default
    private List<Map<String, Object>> toolInteractions = new ArrayList<>();

    /**
     * Additional parameters for context processing
     */
    @Builder.Default
    private Map<String, Object> parameters = new HashMap<>();

    /**
     * Get the total token count for the current context.
     * This is a utility method that can be used by context managers.
     * @return estimated token count
     */
    public int getEstimatedTokenCount() {
        int tokenCount = 0;

        // Estimate tokens for system prompt
        if (systemPrompt != null) {
            tokenCount += estimateTokens(systemPrompt);
        }

        // Estimate tokens for user prompt
        if (userPrompt != null) {
            tokenCount += estimateTokens(userPrompt);
        }

        // Estimate tokens for chat history
        for (Interaction interaction : chatHistory) {
            if (interaction.getInput() != null) {
                tokenCount += estimateTokens(interaction.getInput());
            }
            if (interaction.getResponse() != null) {
                tokenCount += estimateTokens(interaction.getResponse());
            }
        }

        // Estimate tokens for tool interactions
        for (Map<String, Object> interaction : toolInteractions) {
            Object output = interaction.get("output");
            if (output instanceof String) {
                tokenCount += estimateTokens((String) output);
            }
        }

        return tokenCount;
    }

    /**
     * Get the message count in chat history.
     * @return number of messages in chat history
     */
    public int getMessageCount() {
        return chatHistory.size();
    }

    /**
     * Simple token estimation based on character count.
     * This is a fallback method - more sophisticated token counting should be implemented
     * in dedicated TokenCounter implementations.
     * @param text the text to estimate tokens for
     * @return estimated token count
     */
    private int estimateTokens(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        // Rough estimation: 1 token per 4 characters
        return (int) Math.ceil(text.length() / 4.0);
    }

    /**
     * Add a tool interaction to the context.
     * @param interaction the tool interaction to add
     */
    public void addToolInteraction(Map<String, Object> interaction) {
        if (toolInteractions == null) {
            toolInteractions = new ArrayList<>();
        }
        toolInteractions.add(interaction);
    }

    /**
     * Add an interaction to the chat history.
     * @param interaction the interaction to add
     */
    public void addChatHistoryInteraction(Interaction interaction) {
        if (chatHistory == null) {
            chatHistory = new ArrayList<>();
        }
        chatHistory.add(interaction);
    }

    /**
     * Clear the chat history.
     */
    public void clearChatHistory() {
        if (chatHistory != null) {
            chatHistory.clear();
        }
    }

    /**
     * Get a parameter value by key.
     * @param key the parameter key
     * @return the parameter value, or null if not found
     */
    public Object getParameter(String key) {
        return parameters != null ? parameters.get(key) : null;
    }

    /**
     * Set a parameter value.
     * @param key the parameter key
     * @param value the parameter value
     */
    public void setParameter(String key, Object value) {
        if (parameters == null) {
            parameters = new HashMap<>();
        }
        parameters.put(key, value);
    }
}
