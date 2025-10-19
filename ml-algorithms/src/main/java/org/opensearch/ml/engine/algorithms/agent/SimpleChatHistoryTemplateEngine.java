/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.agent;

import java.util.List;
import java.util.Map;

/**
 * Simple implementation of ChatHistoryTemplateEngine.
 * Provides basic template functionality for ChatMessage formatting.
 * 
 * This is a simplified implementation that supports:
 * - Role-based message formatting
 * - Basic placeholder replacement
 * - Content type awareness
 * 
 * Future versions can implement more advanced template engines (Handlebars, etc.)
 */
public class SimpleChatHistoryTemplateEngine implements ChatHistoryTemplateEngine {

    @Override
    public String buildChatHistory(List<ChatMessage> messages, String template, Map<String, Object> context) {
        if (messages == null || messages.isEmpty()) {
            return "";
        }

        // For now, use a simple approach - build chat history with role-based formatting
        StringBuilder chatHistory = new StringBuilder();

        for (ChatMessage message : messages) {
            String formattedMessage = formatMessage(message);
            chatHistory.append(formattedMessage).append("\n");
        }

        return chatHistory.toString().trim();
    }

    /**
     * Format a single ChatMessage based on its role and content type
     */
    private String formatMessage(ChatMessage message) {
        String role = message.getRole();
        String content = message.getContent();
        String contentType = message.getContentType();

        // Role-based formatting
        String prefix = switch (role) {
            case "user" -> "Human: ";
            case "assistant" -> "Assistant: ";
            case "system" -> "System: ";
            case "tool" -> "Tool Result: ";
            default -> role + ": ";
        };

        // Content type awareness
        String formattedContent = content;
        if ("image".equals(contentType)) {
            formattedContent = "[Image: " + content + "]";
        } else if ("tool_result".equals(contentType)) {
            Map<String, Object> metadata = message.getMetadata();
            if (metadata != null && metadata.containsKey("tool_name")) {
                formattedContent = "Tool " + metadata.get("tool_name") + ": " + content;
            }
        } else if ("context".equals(contentType)) {
            // Context messages (like from long-term memory) get special formatting
            formattedContent = "[Context] " + content;
        }

        return prefix + formattedContent;
    }

    /**
     * Build chat history using default simple formatting
     */
    public String buildSimpleChatHistory(List<ChatMessage> messages) {
        return buildChatHistory(messages, getDefaultTemplate(), Map.of());
    }
}
