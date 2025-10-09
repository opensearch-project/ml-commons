/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.utils.message;

import static org.opensearch.common.xcontent.json.JsonXContent.jsonXContent;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.ml.common.transport.memorycontainer.memory.MessageInput;
import org.opensearch.ml.common.utils.StringUtils;

import lombok.extern.log4j.Log4j2;

/**
 * Message formatter for OpenAI models (and similar models without system_prompt parameter).
 *
 * <p>Format characteristics:
 * <ul>
 *   <li>System prompt: Injected as first message with role="system"</li>
 *   <li>Messages: Array including system + user/assistant messages</li>
 *   <li>Content: Normalized to have "type" field</li>
 * </ul>
 *
 * <p>Compatible with:
 * <ul>
 *   <li>GPT-4, GPT-4o, GPT-4o-mini</li>
 *   <li>GPT-3.5-turbo</li>
 *   <li>Any Chat Completions API model</li>
 *   <li>Models without system_prompt in schema</li>
 * </ul>
 *
 * <p>Example output:
 * <pre>
 * {
 *   "messages": "[{\"role\":\"system\",\"content\":[{\"type\":\"text\",\"text\":\"You are helpful\"}]},
 *                 {\"role\":\"user\",\"content\":[{\"type\":\"text\",\"text\":\"Hello\"}]}]"
 * }
 * </pre>
 */
@Log4j2
public class OpenAIMessageFormatter implements MessageFormatter {

    @Override
    public Map<String, String> formatRequest(String systemPrompt, List<MessageInput> messages, Map<String, Object> additionalConfig) {
        Map<String, String> parameters = new HashMap<>();

        // OpenAI-style: NO system_prompt parameter
        // System prompt goes as first message in array

        List<MessageInput> allMessages = new ArrayList<>();

        // Inject system prompt as first message
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            allMessages.add(createSystemMessage(systemPrompt));
            log.debug("System prompt injected as first message");
        }

        // Add user messages
        if (messages != null) {
            allMessages.addAll(messages);
        }

        // Build messages array with content processing
        try {
            String messagesJson = buildMessagesArray(allMessages, additionalConfig);
            parameters.put("messages", messagesJson);
            log.debug("Built messages array with {} messages", allMessages.size());
        } catch (IOException e) {
            log.error("Failed to build messages array", e);
            throw new RuntimeException("Failed to format OpenAI request", e);
        }

        return parameters;
    }

    @Override
    public List<Map<String, Object>> processContent(List<Map<String, Object>> content) {
        if (content == null || content.isEmpty()) {
            return content;
        }

        return content.stream().map(this::normalizeContentObject).collect(Collectors.toList());
    }

    /**
     * Create a system message from prompt text.
     *
     * <p>Creates a MessageInput with:
     * <ul>
     *   <li>role: "system"</li>
     *   <li>content: [{"type": "text", "text": prompt}]</li>
     * </ul>
     *
     * @param prompt The system prompt text
     * @return MessageInput configured as system message
     */
    private MessageInput createSystemMessage(String prompt) {
        List<Map<String, Object>> content = new ArrayList<>();
        Map<String, Object> textContent = new HashMap<>();
        textContent.put("type", "text");
        textContent.put("text", prompt);
        content.add(textContent);

        return MessageInput.builder().role("system").content(content).build();
    }

    /**
     * Normalize a single content object to ensure it has "type" field.
     *
     * <p>Rules:
     * <ul>
     *   <li>If object has "type" field → return as-is (standard LLM format)</li>
     *   <li>If object lacks "type" field → wrap as {"type": "text", "text": JSON_STRING}</li>
     * </ul>
     *
     * @param obj Content object to normalize
     * @return Normalized content object with "type" field
     */
    private Map<String, Object> normalizeContentObject(Map<String, Object> obj) {
        if (obj == null || obj.isEmpty()) {
            return obj;
        }

        // Already has type field → standard format
        if (obj.containsKey("type")) {
            return obj;
        }

        // No type field → user-defined object, wrap as text
        Map<String, Object> wrapped = new HashMap<>();
        wrapped.put("type", "text");
        String jsonText = StringUtils.toJson(obj);
        wrapped.put("text", jsonText);
        return wrapped;
    }

    /**
     * Build messages JSON array from MessageInput list.
     *
     * <p>Includes:
     * <ul>
     *   <li>Optional system_prompt_message from config (added first)</li>
     *   <li>All messages (including injected system message) with processed content</li>
     *   <li>Optional user_prompt_message from config (added last)</li>
     * </ul>
     *
     * @param messages List of ALL messages to include (including system)
     * @param additionalConfig Optional config with extra messages
     * @return JSON string representing messages array
     * @throws IOException if JSON building fails
     */
    private String buildMessagesArray(List<MessageInput> messages, Map<String, Object> additionalConfig) throws IOException {
        XContentBuilder builder = jsonXContent.contentBuilder();
        builder.startArray();

        // Optional system_prompt_message from config (rare for OpenAI)
        if (additionalConfig != null && additionalConfig.containsKey("system_prompt_message")) {
            Object systemPromptMsg = additionalConfig.get("system_prompt_message");
            if (systemPromptMsg instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> msgMap = (Map<String, Object>) systemPromptMsg;
                builder.map(msgMap);
            }
        }

        // All messages (including injected system message)
        if (messages != null) {
            for (MessageInput message : messages) {
                builder.startObject();

                if (message.getRole() != null) {
                    builder.field("role", message.getRole());
                }

                // Process content to ensure type fields
                if (message.getContent() != null) {
                    List<Map<String, Object>> processedContent = processContent(message.getContent());
                    builder.field("content", processedContent);
                }

                builder.endObject();
            }
        }

        // Optional user_prompt_message from config
        if (additionalConfig != null && additionalConfig.containsKey("user_prompt_message")) {
            Object userPromptMsg = additionalConfig.get("user_prompt_message");
            if (userPromptMsg instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> msgMap = (Map<String, Object>) userPromptMsg;
                builder.map(msgMap);
            }
        }

        builder.endArray();
        return builder.toString();
    }
}
