/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.agent;

import java.util.List;
import java.util.Map;

/**
 * Enhanced template system for ChatMessage-based memory types.
 * Supports flexible templating with role-based formatting and metadata access.
 */
public interface ChatHistoryTemplateEngine {
    /**
     * Build chat history from ChatMessage list using template
     * @param messages List of ChatMessage objects
     * @param template Template string with placeholders
     * @param context Additional context variables
     * @return Formatted chat history string
     */
    String buildChatHistory(List<ChatMessage> messages, String template, Map<String, Object> context);

    /**
     * Get default template for basic chat history formatting
     * @return Default template string
     */
    default String getDefaultTemplate() {
        return "{{#each messages}}{{role}}: {{content}}\n{{/each}}";
    }

    /**
     * Get role-based template with enhanced formatting
     * @return Role-based template string
     */
    default String getRoleBasedTemplate() {
        return """
            {{#each messages}}
            {{#if (eq role 'user')}}
            Human: {{content}}
            {{else if (eq role 'assistant')}}
            Assistant: {{content}}
            {{else if (eq role 'system')}}
            System: {{content}}
            {{else if (eq role 'tool')}}
            Tool Result: {{content}}
            {{/if}}
            {{#if metadata.confidence}}
            (Confidence: {{metadata.confidence}})
            {{/if}}
            {{/each}}
            """;
    }
}
