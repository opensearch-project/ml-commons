/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.memorycontainer.memory;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.opensearch.core.xcontent.ToXContent.EMPTY_PARAMS;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.common.xcontent.LoggingDeprecationHandler;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.xcontent.MediaTypeRegistry;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.TestHelper;

public class MLAddMemoriesInputTest {

    private MLAddMemoriesInput inputWithAllFields;
    private MLAddMemoriesInput inputMinimal;
    private MLAddMemoriesInput inputNoOptionals;
    private List<MessageInput> testMessages;
    private Map<String, String> testTags;

    @Before
    public void setUp() {
        testMessages = Arrays
            .asList(
                new MessageInput("user", "Hello, how are you?"),
                new MessageInput("assistant", "I'm doing well, thank you!"),
                new MessageInput("user", "What can you help me with?")
            );

        testTags = new HashMap<>();
        testTags.put("topic", "greeting");
        testTags.put("priority", "low");

        // Input with all fields
        inputWithAllFields = MLAddMemoriesInput
            .builder()
            .memoryContainerId("container-123")
            .messages(testMessages)
            .sessionId("session-456")
            .agentId("agent-789")
            .infer(true)
            .tags(testTags)
            .build();

        // Minimal input (only required fields)
        inputMinimal = MLAddMemoriesInput.builder().messages(Arrays.asList(new MessageInput(null, "Single message"))).build();

        // Input without optional fields
        inputNoOptionals = new MLAddMemoriesInput(
            "container-999",
            Arrays.asList(new MessageInput("user", "Test message")),
            null,
            null,
            null,
            null
        );
    }

    @Test
    public void testBuilderWithAllFields() {
        assertNotNull(inputWithAllFields);
        assertEquals("container-123", inputWithAllFields.getMemoryContainerId());
        assertEquals(testMessages, inputWithAllFields.getMessages());
        assertEquals(3, inputWithAllFields.getMessages().size());
        assertEquals("session-456", inputWithAllFields.getSessionId());
        assertEquals("agent-789", inputWithAllFields.getAgentId());
        assertEquals(Boolean.TRUE, inputWithAllFields.getInfer());
        assertEquals(testTags, inputWithAllFields.getTags());
    }

    @Test
    public void testBuilderMinimal() {
        assertNotNull(inputMinimal);
        assertNull(inputMinimal.getMemoryContainerId());
        assertEquals(1, inputMinimal.getMessages().size());
        assertNull(inputMinimal.getSessionId());
        assertNull(inputMinimal.getAgentId());
        assertNull(inputMinimal.getInfer());
        assertNull(inputMinimal.getTags());
    }

    @Test
    public void testConstructorValidation() {
        // Test null messages
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> new MLAddMemoriesInput("container-1", null, null, null, null, null)
        );
        assertEquals("Messages list cannot be empty", exception.getMessage());

        // Test empty messages
        exception = assertThrows(
            IllegalArgumentException.class,
            () -> new MLAddMemoriesInput("container-1", new ArrayList<>(), null, null, null, null)
        );
        assertEquals("Messages list cannot be empty", exception.getMessage());

        // Test that limit is removed - should be able to create with many messages
        List<MessageInput> manyMessages = new ArrayList<>();
        for (int i = 0; i < 100; i++) { // Test with 100 messages
            manyMessages.add(new MessageInput("user", "Message " + i));
        }
        // Should not throw exception anymore
        MLAddMemoriesInput inputWithManyMessages = new MLAddMemoriesInput("container-1", manyMessages, null, null, null, null);
        assertNotNull(inputWithManyMessages);
        assertEquals(100, inputWithManyMessages.getMessages().size());
    }

    @Test
    public void testStreamInputOutput() throws IOException {
        // Test with all fields
        BytesStreamOutput out = new BytesStreamOutput();
        inputWithAllFields.writeTo(out);
        StreamInput in = out.bytes().streamInput();
        MLAddMemoriesInput deserialized = new MLAddMemoriesInput(in);

        assertEquals(inputWithAllFields.getMemoryContainerId(), deserialized.getMemoryContainerId());
        assertEquals(inputWithAllFields.getMessages().size(), deserialized.getMessages().size());
        for (int i = 0; i < inputWithAllFields.getMessages().size(); i++) {
            MessageInput original = inputWithAllFields.getMessages().get(i);
            MessageInput deser = deserialized.getMessages().get(i);
            assertEquals(original.getRole(), deser.getRole());
            assertEquals(original.getContent(), deser.getContent());
        }
        assertEquals(inputWithAllFields.getSessionId(), deserialized.getSessionId());
        assertEquals(inputWithAllFields.getAgentId(), deserialized.getAgentId());
        assertEquals(inputWithAllFields.getInfer(), deserialized.getInfer());
        assertEquals(inputWithAllFields.getTags(), deserialized.getTags());
    }

    @Test
    public void testStreamInputOutputMinimal() throws IOException {
        // Test with minimal fields
        BytesStreamOutput out = new BytesStreamOutput();
        inputMinimal.writeTo(out);
        StreamInput in = out.bytes().streamInput();
        MLAddMemoriesInput deserialized = new MLAddMemoriesInput(in);

        assertNull(deserialized.getMemoryContainerId());
        assertEquals(1, deserialized.getMessages().size());
        assertNull(deserialized.getSessionId());
        assertNull(deserialized.getAgentId());
        assertNull(deserialized.getInfer());
        assertNull(deserialized.getTags());
    }

    @Test
    public void testStreamInputOutputEmptyTags() throws IOException {
        // Test with empty tags
        MLAddMemoriesInput inputEmptyTags = MLAddMemoriesInput
            .builder()
            .messages(Arrays.asList(new MessageInput("user", "Test")))
            .tags(new HashMap<>())
            .build();

        BytesStreamOutput out = new BytesStreamOutput();
        inputEmptyTags.writeTo(out);
        StreamInput in = out.bytes().streamInput();
        MLAddMemoriesInput deserialized = new MLAddMemoriesInput(in);

        assertNull(deserialized.getTags());
    }

    @Test
    public void testToXContent() throws IOException {
        XContentBuilder builder = MediaTypeRegistry.contentBuilder(XContentType.JSON);
        inputWithAllFields.toXContent(builder, EMPTY_PARAMS);
        String jsonString = TestHelper.xContentBuilderToString(builder);

        assertTrue(jsonString.contains("\"memory_container_id\":\"container-123\""));
        assertTrue(jsonString.contains("\"messages\":["));
        assertTrue(jsonString.contains("\"role\":\"user\""));
        assertTrue(jsonString.contains("\"content\":\"Hello, how are you?\""));
        assertTrue(jsonString.contains("\"session_id\":\"session-456\""));
        assertTrue(jsonString.contains("\"agent_id\":\"agent-789\""));
        assertTrue(jsonString.contains("\"infer\":true"));
        assertTrue(jsonString.contains("\"topic\":\"greeting\""));
    }

    @Test
    public void testToXContentMinimal() throws IOException {
        XContentBuilder builder = MediaTypeRegistry.contentBuilder(XContentType.JSON);
        inputMinimal.toXContent(builder, EMPTY_PARAMS);
        String jsonString = TestHelper.xContentBuilderToString(builder);

        assertTrue(!jsonString.contains("\"memory_container_id\""));
        assertTrue(jsonString.contains("\"messages\":["));
        assertTrue(jsonString.contains("\"content\":\"Single message\""));
        assertTrue(!jsonString.contains("\"session_id\""));
        assertTrue(!jsonString.contains("\"agent_id\""));
        assertTrue(!jsonString.contains("\"infer\""));
        assertTrue(!jsonString.contains("\"tags\""));
    }

    @Test
    public void testParse() throws IOException {
        String jsonString = "{"
            + "\"memory_container_id\":\"container-123\","
            + "\"messages\":["
            + "{\"role\":\"user\",\"content\":\"Test message 1\"},"
            + "{\"role\":\"assistant\",\"content\":\"Test response\"}"
            + "],"
            + "\"session_id\":\"session-789\","
            + "\"agent_id\":\"agent-456\","
            + "\"infer\":false,"
            + "\"tags\":{\"key\":\"value\"}"
            + "}";

        XContentParser parser = XContentType.JSON
            .xContent()
            .createParser(NamedXContentRegistry.EMPTY, LoggingDeprecationHandler.INSTANCE, jsonString);
        parser.nextToken();

        MLAddMemoriesInput parsed = MLAddMemoriesInput.parse(parser);

        assertEquals("container-123", parsed.getMemoryContainerId());
        assertEquals(2, parsed.getMessages().size());
        assertEquals("user", parsed.getMessages().get(0).getRole());
        assertEquals("Test message 1", parsed.getMessages().get(0).getContent());
        assertEquals("session-789", parsed.getSessionId());
        assertEquals("agent-456", parsed.getAgentId());
        assertEquals(Boolean.FALSE, parsed.getInfer());
        assertEquals(1, parsed.getTags().size());
        assertEquals("value", parsed.getTags().get("key"));
    }

    @Test
    public void testParseMinimal() throws IOException {
        String jsonString = "{" + "\"messages\":[" + "{\"content\":\"Minimal message\"}" + "]" + "}";

        XContentParser parser = XContentType.JSON
            .xContent()
            .createParser(NamedXContentRegistry.EMPTY, LoggingDeprecationHandler.INSTANCE, jsonString);
        parser.nextToken();

        MLAddMemoriesInput parsed = MLAddMemoriesInput.parse(parser);

        assertNull(parsed.getMemoryContainerId());
        assertEquals(1, parsed.getMessages().size());
        assertEquals("Minimal message", parsed.getMessages().get(0).getContent());
        assertNull(parsed.getMessages().get(0).getRole());
    }

    @Test
    public void testParseWithUnknownFields() throws IOException {
        String jsonString = "{"
            + "\"messages\":[{\"content\":\"Test\"}],"
            + "\"unknown_field\":\"ignored\","
            + "\"another_unknown\":123"
            + "}";

        XContentParser parser = XContentType.JSON
            .xContent()
            .createParser(NamedXContentRegistry.EMPTY, LoggingDeprecationHandler.INSTANCE, jsonString);
        parser.nextToken();

        MLAddMemoriesInput parsed = MLAddMemoriesInput.parse(parser);

        assertEquals(1, parsed.getMessages().size());
        assertEquals("Test", parsed.getMessages().get(0).getContent());
    }

    @Test
    public void testSetters() {
        MLAddMemoriesInput input = MLAddMemoriesInput.builder().messages(Arrays.asList(new MessageInput("user", "Initial"))).build();

        input.setMemoryContainerId("new-container");
        input.setSessionId("new-session");
        input.setAgentId("new-agent");
        input.setInfer(true);
        input.setTags(testTags);

        assertEquals("new-container", input.getMemoryContainerId());
        assertEquals("new-session", input.getSessionId());
        assertEquals("new-agent", input.getAgentId());
        assertEquals(Boolean.TRUE, input.getInfer());
        assertEquals(testTags, input.getTags());
    }

    @Test
    public void testLargeNumberOfMessages() {
        // Test that we can handle a large number of messages now that limit is removed
        List<MessageInput> manyMessages = new ArrayList<>();
        for (int i = 0; i < 1000; i++) { // Test with 1000 messages
            manyMessages.add(new MessageInput("user", "Message " + i));
        }

        // Should succeed with large number of messages
        MLAddMemoriesInput input = new MLAddMemoriesInput("container-1", manyMessages, null, null, null, null);
        assertEquals(1000, input.getMessages().size());
    }

    @Test
    public void testSpecialCharactersInFields() throws IOException {
        Map<String, String> specialTags = new HashMap<>();
        specialTags.put("key with spaces", "value with\nnewlines");

        List<MessageInput> specialMessages = Arrays
            .asList(
                new MessageInput("user", "Message with\n\ttabs and \"quotes\""),
                new MessageInput("assistant", "Response with unicode 🚀✨")
            );

        MLAddMemoriesInput specialInput = MLAddMemoriesInput
            .builder()
            .memoryContainerId("container-with-special-chars")
            .messages(specialMessages)
            .sessionId("session-🔥")
            .tags(specialTags)
            .build();

        // Test serialization round trip
        BytesStreamOutput out = new BytesStreamOutput();
        specialInput.writeTo(out);
        StreamInput in = out.bytes().streamInput();
        MLAddMemoriesInput deserialized = new MLAddMemoriesInput(in);

        assertEquals(specialInput.getMemoryContainerId(), deserialized.getMemoryContainerId());
        assertEquals(specialInput.getSessionId(), deserialized.getSessionId());
        assertEquals(2, deserialized.getMessages().size());
    }

    @Test
    public void testXContentRoundTrip() throws IOException {
        // Convert to XContent
        XContentBuilder builder = MediaTypeRegistry.contentBuilder(XContentType.JSON);
        inputWithAllFields.toXContent(builder, EMPTY_PARAMS);
        String jsonString = TestHelper.xContentBuilderToString(builder);

        // Parse back
        XContentParser parser = XContentType.JSON
            .xContent()
            .createParser(NamedXContentRegistry.EMPTY, LoggingDeprecationHandler.INSTANCE, jsonString);
        parser.nextToken();
        MLAddMemoriesInput parsed = MLAddMemoriesInput.parse(parser);

        // Verify all fields match
        assertEquals(inputWithAllFields.getMemoryContainerId(), parsed.getMemoryContainerId());
        assertEquals(inputWithAllFields.getMessages().size(), parsed.getMessages().size());
        assertEquals(inputWithAllFields.getSessionId(), parsed.getSessionId());
        assertEquals(inputWithAllFields.getAgentId(), parsed.getAgentId());
        assertEquals(inputWithAllFields.getInfer(), parsed.getInfer());
        assertEquals(inputWithAllFields.getTags(), parsed.getTags());
    }
}
