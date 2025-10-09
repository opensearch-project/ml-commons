/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.utils.message;

import lombok.extern.log4j.Log4j2;

/**
 * Factory for creating appropriate MessageFormatter based on model's input schema.
 *
 * <p>Decision Algorithm:
 * <ol>
 *   <li>Get model's input schema (from cache or other source)</li>
 *   <li>Pass schema to {@link #getFormatter(String)}</li>
 *   <li>Check if schema contains "system_prompt" field</li>
 *   <li>If YES → Claude formatter</li>
 *   <li>If NO → OpenAI formatter</li>
 *   <li>On any error → Claude formatter (safe default)</li>
 * </ol>
 *
 * <p>The factory uses singleton formatters for performance (stateless, thread-safe).
 *
 * <p>Usage:
 * <pre>
 * // Get input schema from model cache
 * Map&lt;String, String&gt; modelInterface = modelCacheHelper.getModelInterface(modelId);
 * String inputSchema = modelInterface.get("input");
 *
 * // Get appropriate formatter
 * MessageFormatter formatter = MessageFormatterFactory.getFormatter(inputSchema);
 *
 * // Or use explicit formatters for testing
 * MessageFormatter claude = MessageFormatterFactory.getClaudeFormatter();
 * </pre>
 */
@Log4j2
public class MessageFormatterFactory {

    // Singleton formatter instances (thread-safe, stateless)
    private static final MessageFormatter CLAUDE_FORMATTER = new ClaudeMessageFormatter();
    private static final MessageFormatter OPENAI_FORMATTER = new OpenAIMessageFormatter();

    /**
     * Get appropriate formatter based on input schema JSON.
     *
     * <p>This is the core decision logic:
     * <ul>
     *   <li>Presence of "system_prompt" → Claude-style formatter</li>
     *   <li>Absence of "system_prompt" → OpenAI-style formatter</li>
     * </ul>
     *
     * <p>The detection uses simple string matching for performance and reliability.
     *
     * @param inputSchemaJson The JSON schema string from model interface
     * @return Appropriate formatter (never null, defaults to Claude)
     */
    public static MessageFormatter getFormatter(String inputSchemaJson) {
        if (inputSchemaJson == null || inputSchemaJson.isBlank()) {
            log.debug("No input schema provided, defaulting to Claude formatter");
            return CLAUDE_FORMATTER;
        }

        try {
            // Simple and fast: check if schema contains system_prompt field
            // This is more reliable than parsing JSON and navigating paths
            boolean hasSystemPromptParam = inputSchemaJson.contains("\"system_prompt\"");

            if (hasSystemPromptParam) {
                log.debug("Schema contains system_prompt parameter, using Claude formatter");
                return CLAUDE_FORMATTER;
            } else {
                log.debug("Schema lacks system_prompt parameter, using OpenAI formatter");
                return OPENAI_FORMATTER;
            }
        } catch (Exception e) {
            log.warn("Failed to analyze input schema, defaulting to Claude formatter", e);
            return CLAUDE_FORMATTER;
        }
    }

    /**
     * Get Claude formatter explicitly (for testing or explicit usage).
     *
     * @return Claude message formatter instance
     */
    public static MessageFormatter getClaudeFormatter() {
        return CLAUDE_FORMATTER;
    }

    /**
     * Get OpenAI formatter explicitly (for testing or explicit usage).
     *
     * @return OpenAI message formatter instance
     */
    public static MessageFormatter getOpenAIFormatter() {
        return OPENAI_FORMATTER;
    }
}
