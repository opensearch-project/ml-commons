/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.utils.message;

import java.util.List;
import java.util.Map;

import org.opensearch.ml.common.transport.memorycontainer.memory.MessageInput;

/**
 * Strategy interface for formatting LLM requests based on model requirements.
 *
 * <p>Each formatter implementation handles:
 * <ul>
 *   <li>System prompt placement (parameter vs message)</li>
 *   <li>Content object normalization (type field enforcement)</li>
 *   <li>Message array construction</li>
 * </ul>
 *
 * <p>Implementations:
 * <ul>
 *   <li>{@link ClaudeMessageFormatter}: Uses system_prompt parameter (Claude, Bedrock models)</li>
 *   <li>{@link OpenAIMessageFormatter}: Injects system message in array (OpenAI, GPT models)</li>
 * </ul>
 *
 * <p>Usage:
 * <pre>
 * MessageFormatter formatter = MessageFormatterFactory.getFormatterForModel(modelId, cache);
 * Map&lt;String, String&gt; params = formatter.formatRequest(systemPrompt, messages, config);
 * </pre>
 */
public interface MessageFormatter {

    /**
     * Format a complete LLM request with proper system prompt placement.
     *
     * <p>This method handles:
     * <ul>
     *   <li>Placing system prompt in correct location (parameter or message)</li>
     *   <li>Building messages array with content processing</li>
     *   <li>Incorporating additional config messages (system_prompt_message, user_prompt_message)</li>
     * </ul>
     *
     * @param systemPrompt The system prompt to inject (may be null or blank)
     * @param messages User/assistant messages to include in the request
     * @param additionalConfig Optional configuration containing:
     *                         <ul>
     *                           <li>system_prompt_message: Additional system-level message</li>
     *                           <li>user_prompt_message: Additional user message</li>
     *                         </ul>
     * @return Map of request parameters ready for MLInput, typically containing:
     *         <ul>
     *           <li>"messages": JSON array of messages</li>
     *           <li>"system_prompt": System prompt (Claude-style only)</li>
     *         </ul>
     * @throws RuntimeException if message array building fails
     */
    Map<String, String> formatRequest(String systemPrompt, List<MessageInput> messages, Map<String, Object> additionalConfig);

    /**
     * Process message content objects to ensure LLM compatibility.
     *
     * <p>Content processing rules:
     * <ul>
     *   <li>Objects WITH "type" field → keep as-is (standard LLM format like
     *       {"type": "text", "text": "..."} or {"type": "image_url", "image_url": {...}})</li>
     *   <li>Objects WITHOUT "type" field → wrap as {"type": "text", "text": JSON_STRING}
     *       where JSON_STRING is the serialized user-defined object</li>
     * </ul>
     *
     * <p>This ensures that user-defined content objects are properly formatted
     * for LLM consumption without breaking standard multimodal content.
     *
     * @param content List of content objects from message (may be null or empty)
     * @return Processed content list with all objects having "type" field
     */
    List<Map<String, Object>> processContent(List<Map<String, Object>> content);
}
