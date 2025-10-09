/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.utils.message;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.opensearch.ml.common.transport.memorycontainer.memory.MessageInput;

/**
 * Comprehensive unit tests for MessageFormatter implementations.
 * Tests both ClaudeMessageFormatter and OpenAIMessageFormatter.
 */
public class MessageFormatterTests {

    private ClaudeMessageFormatter claudeFormatter;
    private OpenAIMessageFormatter openaiFormatter;

    @Before
    public void setUp() {
        claudeFormatter = new ClaudeMessageFormatter();
        openaiFormatter = new OpenAIMessageFormatter();
    }

    // ============================================================
    // ClaudeMessageFormatter Tests
    // ============================================================

    @Test
    public void testClaudeFormatter_SystemPromptInParameters() {
        String systemPrompt = "You are a helpful assistant.";
        List<MessageInput> messages = createSimpleUserMessage("Hello");

        Map<String, String> result = claudeFormatter.formatRequest(systemPrompt, messages, null);

        // Verify system_prompt parameter exists
        assertTrue("Should contain system_prompt parameter", result.containsKey("system_prompt"));
        assertEquals("System prompt should match", systemPrompt, result.get("system_prompt"));

        // Verify messages parameter exists
        assertTrue("Should contain messages parameter", result.containsKey("messages"));
    }

    @Test
    public void testClaudeFormatter_MessagesArrayStructure() {
        String systemPrompt = "You are a helpful assistant.";
        List<MessageInput> messages = createSimpleUserMessage("Hello");

        Map<String, String> result = claudeFormatter.formatRequest(systemPrompt, messages, null);

        // Parse messages JSON
        String messagesJson = result.get("messages");
        assertNotNull("Messages should not be null", messagesJson);

        JSONArray messagesArray = new JSONArray(messagesJson);
        assertEquals("Should have 1 message", 1, messagesArray.length());

        JSONObject firstMessage = messagesArray.getJSONObject(0);
        assertEquals("Role should be user", "user", firstMessage.getString("role"));

        // Verify NO system role in messages array
        for (int i = 0; i < messagesArray.length(); i++) {
            JSONObject msg = messagesArray.getJSONObject(i);
            assertNotEquals("Should NOT have system role in messages", "system", msg.getString("role"));
        }
    }

    @Test
    public void testClaudeFormatter_BlankSystemPrompt() {
        List<MessageInput> messages = createSimpleUserMessage("Hello");

        Map<String, String> result = claudeFormatter.formatRequest("", messages, null);

        // Should NOT contain system_prompt if blank
        assertFalse("Should not contain empty system_prompt", result.containsKey("system_prompt"));

        // Should still have messages
        assertTrue("Should contain messages", result.containsKey("messages"));
    }

    @Test
    public void testClaudeFormatter_NullSystemPrompt() {
        List<MessageInput> messages = createSimpleUserMessage("Hello");

        Map<String, String> result = claudeFormatter.formatRequest(null, messages, null);

        // Should NOT contain system_prompt if null
        assertFalse("Should not contain null system_prompt", result.containsKey("system_prompt"));
    }

    @Test
    public void testClaudeFormatter_ContentNormalization_StandardFormat() {
        // Content object WITH "type" field - should remain unchanged
        Map<String, Object> contentWithType = new HashMap<>();
        contentWithType.put("type", "text");
        contentWithType.put("text", "Hello world");

        List<Map<String, Object>> content = new ArrayList<>();
        content.add(contentWithType);

        List<Map<String, Object>> processed = claudeFormatter.processContent(content);

        assertEquals("Should have 1 content object", 1, processed.size());
        assertTrue("Should contain type field", processed.get(0).containsKey("type"));
        assertEquals("Type should be text", "text", processed.get(0).get("type"));
        assertEquals("Text should match", "Hello world", processed.get(0).get("text"));
    }

    @Test
    public void testClaudeFormatter_ContentNormalization_UserDefinedObject() {
        // Content object WITHOUT "type" field - should be wrapped
        Map<String, Object> contentWithoutType = new HashMap<>();
        contentWithoutType.put("custom_field", "custom_value");
        contentWithoutType.put("another_field", 123);

        List<Map<String, Object>> content = new ArrayList<>();
        content.add(contentWithoutType);

        List<Map<String, Object>> processed = claudeFormatter.processContent(content);

        assertEquals("Should have 1 wrapped content object", 1, processed.size());
        assertTrue("Should contain type field", processed.get(0).containsKey("type"));
        assertEquals("Type should be text", "text", processed.get(0).get("type"));
        assertTrue("Should contain text field", processed.get(0).containsKey("text"));

        // The "text" field should contain JSON string of original object
        String jsonText = (String) processed.get(0).get("text");
        assertNotNull("Text should not be null", jsonText);
        assertTrue("Should contain custom_field", jsonText.contains("custom_field"));
    }

    @Test
    public void testClaudeFormatter_ContentNormalization_MixedFormat() {
        List<Map<String, Object>> content = new ArrayList<>();

        // Standard format object
        Map<String, Object> standard = new HashMap<>();
        standard.put("type", "text");
        standard.put("text", "Hello");
        content.add(standard);

        // User-defined object
        Map<String, Object> custom = new HashMap<>();
        custom.put("custom_key", "custom_value");
        content.add(custom);

        List<Map<String, Object>> processed = claudeFormatter.processContent(content);

        assertEquals("Should have 2 content objects", 2, processed.size());

        // First should be unchanged
        assertEquals("First should be unchanged", standard, processed.get(0));

        // Second should be wrapped
        assertTrue("Second should have type field", processed.get(1).containsKey("type"));
        assertEquals("Second type should be text", "text", processed.get(1).get("type"));
    }

    @Test
    public void testClaudeFormatter_EmptyMessagesList() {
        String systemPrompt = "You are helpful.";
        List<MessageInput> emptyMessages = new ArrayList<>();

        Map<String, String> result = claudeFormatter.formatRequest(systemPrompt, emptyMessages, null);

        assertTrue("Should contain system_prompt", result.containsKey("system_prompt"));
        assertTrue("Should contain messages", result.containsKey("messages"));

        JSONArray messagesArray = new JSONArray(result.get("messages"));
        assertEquals("Messages array should be empty", 0, messagesArray.length());
    }

    @Test
    public void testClaudeFormatter_MultipleMessages() {
        String systemPrompt = "You are helpful.";
        List<MessageInput> messages = new ArrayList<>();
        messages.add(createMessageInput("user", "Hello"));
        messages.add(createMessageInput("assistant", "Hi there!"));
        messages.add(createMessageInput("user", "How are you?"));

        Map<String, String> result = claudeFormatter.formatRequest(systemPrompt, messages, null);

        JSONArray messagesArray = new JSONArray(result.get("messages"));
        assertEquals("Should have 3 messages", 3, messagesArray.length());

        assertEquals("First role should be user", "user", messagesArray.getJSONObject(0).getString("role"));
        assertEquals("Second role should be assistant", "assistant", messagesArray.getJSONObject(1).getString("role"));
        assertEquals("Third role should be user", "user", messagesArray.getJSONObject(2).getString("role"));
    }

    // ============================================================
    // OpenAIMessageFormatter Tests
    // ============================================================

    @Test
    public void testOpenAIFormatter_SystemPromptAsMessage() {
        String systemPrompt = "You are a helpful assistant.";
        List<MessageInput> messages = createSimpleUserMessage("Hello");

        Map<String, String> result = openaiFormatter.formatRequest(systemPrompt, messages, null);

        // Parse messages JSON
        String messagesJson = result.get("messages");
        assertNotNull("Messages should not be null", messagesJson);

        JSONArray messagesArray = new JSONArray(messagesJson);

        // Should have 2 messages: system + user
        assertEquals("Should have 2 messages (system + user)", 2, messagesArray.length());

        // First message should be system
        JSONObject firstMessage = messagesArray.getJSONObject(0);
        assertEquals("First message should have system role", "system", firstMessage.getString("role"));

        // Verify system message content
        JSONArray systemContent = firstMessage.getJSONArray("content");
        assertEquals("System content should have 1 item", 1, systemContent.length());
        JSONObject systemContentObj = systemContent.getJSONObject(0);
        assertEquals("System content type should be text", "text", systemContentObj.getString("type"));
        assertEquals("System content text should match", systemPrompt, systemContentObj.getString("text"));

        // Second message should be user
        JSONObject secondMessage = messagesArray.getJSONObject(1);
        assertEquals("Second message should have user role", "user", secondMessage.getString("role"));
    }

    @Test
    public void testOpenAIFormatter_NoSystemPromptParameter() {
        String systemPrompt = "You are helpful.";
        List<MessageInput> messages = createSimpleUserMessage("Hello");

        Map<String, String> result = openaiFormatter.formatRequest(systemPrompt, messages, null);

        // Should NOT have system_prompt parameter
        assertFalse("Should NOT contain system_prompt parameter", result.containsKey("system_prompt"));

        // Should only have messages parameter
        assertTrue("Should contain messages parameter", result.containsKey("messages"));
        assertEquals("Should only have 1 parameter", 1, result.size());
    }

    @Test
    public void testOpenAIFormatter_BlankSystemPrompt() {
        List<MessageInput> messages = createSimpleUserMessage("Hello");

        Map<String, String> result = openaiFormatter.formatRequest("", messages, null);

        // Parse messages
        JSONArray messagesArray = new JSONArray(result.get("messages"));

        // Should only have user message, NO system message
        assertEquals("Should only have 1 message (user)", 1, messagesArray.length());
        assertEquals("First message should be user", "user", messagesArray.getJSONObject(0).getString("role"));
    }

    @Test
    public void testOpenAIFormatter_NullSystemPrompt() {
        List<MessageInput> messages = createSimpleUserMessage("Hello");

        Map<String, String> result = openaiFormatter.formatRequest(null, messages, null);

        // Parse messages
        JSONArray messagesArray = new JSONArray(result.get("messages"));

        // Should only have user message
        assertEquals("Should only have 1 message (user)", 1, messagesArray.length());
        assertEquals("First message should be user", "user", messagesArray.getJSONObject(0).getString("role"));
    }

    @Test
    public void testOpenAIFormatter_ContentNormalization_StandardFormat() {
        // Same logic as Claude formatter
        Map<String, Object> contentWithType = new HashMap<>();
        contentWithType.put("type", "text");
        contentWithType.put("text", "Hello world");

        List<Map<String, Object>> content = new ArrayList<>();
        content.add(contentWithType);

        List<Map<String, Object>> processed = openaiFormatter.processContent(content);

        assertEquals("Should have 1 content object", 1, processed.size());
        assertEquals("Should be unchanged", contentWithType, processed.get(0));
    }

    @Test
    public void testOpenAIFormatter_ContentNormalization_UserDefinedObject() {
        // Same logic as Claude formatter
        Map<String, Object> contentWithoutType = new HashMap<>();
        contentWithoutType.put("custom_field", "custom_value");

        List<Map<String, Object>> content = new ArrayList<>();
        content.add(contentWithoutType);

        List<Map<String, Object>> processed = openaiFormatter.processContent(content);

        assertEquals("Should have 1 wrapped object", 1, processed.size());
        assertTrue("Should have type field", processed.get(0).containsKey("type"));
        assertEquals("Type should be text", "text", processed.get(0).get("type"));
        assertTrue("Should have text field", processed.get(0).containsKey("text"));
    }

    @Test
    public void testOpenAIFormatter_MultipleMessages() {
        String systemPrompt = "You are helpful.";
        List<MessageInput> messages = new ArrayList<>();
        messages.add(createMessageInput("user", "Hello"));
        messages.add(createMessageInput("assistant", "Hi!"));

        Map<String, String> result = openaiFormatter.formatRequest(systemPrompt, messages, null);

        JSONArray messagesArray = new JSONArray(result.get("messages"));

        // Should have system + user + assistant = 3 messages
        assertEquals("Should have 3 messages", 3, messagesArray.length());
        assertEquals("First should be system", "system", messagesArray.getJSONObject(0).getString("role"));
        assertEquals("Second should be user", "user", messagesArray.getJSONObject(1).getString("role"));
        assertEquals("Third should be assistant", "assistant", messagesArray.getJSONObject(2).getString("role"));
    }

    @Test
    public void testOpenAIFormatter_EmptyMessagesList() {
        String systemPrompt = "You are helpful.";
        List<MessageInput> emptyMessages = new ArrayList<>();

        Map<String, String> result = openaiFormatter.formatRequest(systemPrompt, emptyMessages, null);

        JSONArray messagesArray = new JSONArray(result.get("messages"));

        // Should only have system message
        assertEquals("Should have 1 message (system)", 1, messagesArray.length());
        assertEquals("Should be system message", "system", messagesArray.getJSONObject(0).getString("role"));
    }

    // ============================================================
    // Edge Cases and Error Handling
    // ============================================================

    @Test
    public void testClaudeFormatter_NullContent() {
        // Test with null content list - implementation returns null for null input
        List<Map<String, Object>> processed = claudeFormatter.processContent(null);
        assertNull("Should return null for null input", processed);
    }

    @Test
    public void testOpenAIFormatter_NullContent() {
        // Test with null content list - implementation returns null for null input
        List<Map<String, Object>> processed = openaiFormatter.processContent(null);
        assertNull("Should return null for null input", processed);
    }

    @Test
    public void testClaudeFormatter_EmptyContent() {
        List<Map<String, Object>> emptyContent = new ArrayList<>();
        List<Map<String, Object>> processed = claudeFormatter.processContent(emptyContent);

        assertNotNull("Should return non-null result", processed);
        assertEquals("Should return empty list", 0, processed.size());
    }

    @Test
    public void testClaudeFormatter_WithAdditionalConfig() {
        String systemPrompt = "You are helpful.";
        List<MessageInput> messages = createSimpleUserMessage("Hello");
        Map<String, Object> additionalConfig = new HashMap<>();
        additionalConfig.put("temperature", 0.7);
        additionalConfig.put("max_tokens", 100);

        Map<String, String> result = claudeFormatter.formatRequest(systemPrompt, messages, additionalConfig);

        // Should still have basic parameters
        assertTrue("Should contain system_prompt", result.containsKey("system_prompt"));
        assertTrue("Should contain messages", result.containsKey("messages"));
    }

    @Test
    public void testOpenAIFormatter_WithAdditionalConfig() {
        String systemPrompt = "You are helpful.";
        List<MessageInput> messages = createSimpleUserMessage("Hello");
        Map<String, Object> additionalConfig = new HashMap<>();
        additionalConfig.put("temperature", 0.7);

        Map<String, String> result = openaiFormatter.formatRequest(systemPrompt, messages, additionalConfig);

        // Should have messages parameter
        assertTrue("Should contain messages", result.containsKey("messages"));
        assertFalse("Should NOT contain system_prompt", result.containsKey("system_prompt"));
    }

    @Test
    public void testClaudeFormatter_ComplexContent() {
        // Test with complex nested content
        Map<String, Object> complexContent = new HashMap<>();
        Map<String, Object> nestedMap = new HashMap<>();
        nestedMap.put("nested_key", "nested_value");
        complexContent.put("data", nestedMap);
        complexContent.put("array", List.of("item1", "item2"));

        List<Map<String, Object>> content = new ArrayList<>();
        content.add(complexContent);

        List<Map<String, Object>> processed = claudeFormatter.processContent(content);

        assertEquals("Should have 1 wrapped object", 1, processed.size());
        assertTrue("Should have type field", processed.get(0).containsKey("type"));
        assertEquals("Type should be text", "text", processed.get(0).get("type"));
        assertTrue("Should have text field", processed.get(0).containsKey("text"));

        // Verify the wrapped JSON contains the original data
        String wrappedJson = (String) processed.get(0).get("text");
        assertTrue("Should contain nested data", wrappedJson.contains("nested_key") || wrappedJson.contains("data"));
    }

    // ============================================================
    // Helper Methods
    // ============================================================

    private List<MessageInput> createSimpleUserMessage(String text) {
        List<MessageInput> messages = new ArrayList<>();
        messages.add(createMessageInput("user", text));
        return messages;
    }

    private MessageInput createMessageInput(String role, String text) {
        List<Map<String, Object>> content = new ArrayList<>();
        Map<String, Object> textContent = new HashMap<>();
        textContent.put("type", "text");
        textContent.put("text", text);
        content.add(textContent);

        return MessageInput.builder().role(role).content(content).build();
    }
}
