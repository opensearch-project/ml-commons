/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.memorycontainer.memory;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.opensearch.core.xcontent.ToXContent.EMPTY_PARAMS;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.SESSION_ID_FIELD;

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
import org.opensearch.ml.common.memorycontainer.WorkingMemoryType;

public class MLAddMemoriesInputTest {

    private MLAddMemoriesInput inputWithAllFields;
    private MLAddMemoriesInput inputMinimal;
    private MLAddMemoriesInput inputNoOptionals;
    private List<MessageInput> testMessages;
    private Map<String, String> testTags;
    private Map<String, Object> structuredData;
    private Map<String, String> metadata;
    private Map<String, String> namespace;

    @Before
    public void setUp() {
        testMessages = Arrays
            .asList(
                MessageInput.builder().role("user").contentText("Hello, how are you?").build(),
                MessageInput.builder().role("assistant").contentText("I'm doing well, thank you!").build(),
                MessageInput.builder().role("user").contentText("What can you help me with?").build()
            );

        testTags = new HashMap<>();
        testTags.put("topic", "greeting");
        testTags.put("priority", "low");

        structuredData = new HashMap<>();
        structuredData.put("key1", "value1");

        metadata = new HashMap<>();
        metadata.put("metadata_key1", "metadata_value1");

        namespace = new HashMap<>();
        namespace.put(SESSION_ID_FIELD, "session-456");
        namespace.put("agent_id", "agent-789");

        // Input with all fields
        inputWithAllFields = MLAddMemoriesInput
            .builder()
            .memoryContainerId("container-123")
            .memoryType(WorkingMemoryType.CONVERSATIONAL)
            .messages(testMessages)
            .binaryData("test binary data")
            .structuredData(structuredData)
            .metadata(metadata)
            .namespace(namespace)
            .infer(true)
            .tags(testTags)
            .ownerId("owner-123")
            .build();

        // Minimal input (only required fields)
        inputMinimal = MLAddMemoriesInput
            .builder()
            .memoryContainerId("container-123")
            .messages(Arrays.asList(MessageInput.builder().role("user").contentText("Single message").build()))
            .ownerId("owner-minimal")
            .build();

        // Input without optional fields
        inputNoOptionals = MLAddMemoriesInput
            .builder()
            .memoryContainerId("container-999")
            .memoryType(WorkingMemoryType.CONVERSATIONAL)
            .messages(Arrays.asList(MessageInput.builder().role("user").contentText("Test message").build()))
            .namespace(Map.of(SESSION_ID_FIELD, "session-456", "agent_id", "agent-789"))
            .infer(true)
            .tags(testTags)
            .ownerId("owner-999")
            .build();
    }

    @Test
    public void testBuilderWithAllFields() {
        assertNotNull(inputWithAllFields);
        assertEquals("container-123", inputWithAllFields.getMemoryContainerId());
        assertEquals(testMessages, inputWithAllFields.getMessages());
        assertEquals(3, inputWithAllFields.getMessages().size());
        assertEquals("session-456", inputWithAllFields.getSessionId());
        assertEquals("agent-789", inputWithAllFields.getAgentId());
        assertEquals(Boolean.TRUE, inputWithAllFields.isInfer());
        assertEquals(testTags, inputWithAllFields.getTags());
        assertEquals("owner-123", inputWithAllFields.getOwnerId());
    }

    @Test
    public void testBuilderMinimal() {
        assertNotNull(inputMinimal);
        assertEquals("container-123", inputMinimal.getMemoryContainerId());
        assertEquals(1, inputMinimal.getMessages().size());
        assertNull(inputMinimal.getSessionId());
        assertNull(inputMinimal.getAgentId());
        assertFalse(inputMinimal.isInfer());
        assertNull(inputMinimal.getTags());
        assertEquals("owner-minimal", inputMinimal.getOwnerId());
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
        assertEquals(inputWithAllFields.isInfer(), deserialized.isInfer());
        assertEquals(inputWithAllFields.getTags(), deserialized.getTags());
        // Note: ownerId is not serialized in the current implementation
    }

    @Test
    public void testStreamInputOutputMinimal() throws IOException {
        // Test with minimal fields
        BytesStreamOutput out = new BytesStreamOutput();
        inputMinimal.writeTo(out);
        StreamInput in = out.bytes().streamInput();
        MLAddMemoriesInput deserialized = new MLAddMemoriesInput(in);

        assertEquals("container-123", deserialized.getMemoryContainerId());
        assertEquals(1, deserialized.getMessages().size());
        assertNull(deserialized.getSessionId());
        assertNull(deserialized.getAgentId());
        assertFalse(deserialized.isInfer());
        assertNull(deserialized.getTags());
        // Note: ownerId is not serialized in the current implementation
    }

    @Test
    public void testStreamInputOutputEmptyTags() throws IOException {
        // Test with empty tags
        MLAddMemoriesInput inputEmptyTags = MLAddMemoriesInput
            .builder()
            .memoryContainerId("container-123")
            .messages(Arrays.asList(MessageInput.builder().role("user").contentText("Test").build()))
            .tags(new HashMap<>())
            .ownerId("owner-empty-tags")
            .build();

        BytesStreamOutput out = new BytesStreamOutput();
        inputEmptyTags.writeTo(out);
        StreamInput in = out.bytes().streamInput();
        MLAddMemoriesInput deserialized = new MLAddMemoriesInput(in);

        assertNull(deserialized.getTags());
    }

    @Test
    public void testValidateMethod() {
        // Test null memoryContainerId
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> new MLAddMemoriesInput(
                null,
                WorkingMemoryType.CONVERSATIONAL,
                testMessages,
                null,
                null,
                null,
                false,
                null,
                null,
                "owner-1"
            )
        );
        assertEquals("No memory container id provided", exception.getMessage());

        // Test null messages with infer=true
        exception = assertThrows(
            IllegalArgumentException.class,
            () -> new MLAddMemoriesInput(
                "container-1",
                WorkingMemoryType.CONVERSATIONAL,
                null,
                null,
                null,
                null,
                true,
                null,
                null,
                "owner-1"
            )
        );
        assertEquals("No messages provided when inferring memory", exception.getMessage());

        // Test empty messages with infer=true
        exception = assertThrows(
            IllegalArgumentException.class,
            () -> new MLAddMemoriesInput(
                "container-1",
                WorkingMemoryType.CONVERSATIONAL,
                new ArrayList<>(),
                null,
                null,
                null,
                true,
                null,
                null,
                "owner-1"
            )
        );
        assertEquals("No messages provided when inferring memory", exception.getMessage());

        // Test valid case - null messages with infer=false should pass
        MLAddMemoriesInput validInput = new MLAddMemoriesInput(
            "container-1",
            WorkingMemoryType.CONVERSATIONAL,
            null,
            null,
            null,
            null,
            false,
            null,
            null,
            "owner-1"
        );
        assertNotNull(validInput);
    }

    @Test
    public void testToXContent() throws IOException {
        XContentBuilder builder = MediaTypeRegistry.contentBuilder(XContentType.JSON);
        inputWithAllFields.toXContent(builder, EMPTY_PARAMS);
        String jsonString = TestHelper.xContentBuilderToString(builder);

        assertTrue(jsonString.contains("\"memory_container_id\":\"container-123\""));
        assertTrue(jsonString.contains("\"messages\":["));
        assertTrue(jsonString.contains("\"role\":\"user\""));
        assertTrue(jsonString.contains("\"content_text\":\"Hello, how are you?\""));
        assertTrue(jsonString.contains("\"session_id\":\"session-456\""));
        assertTrue(jsonString.contains("\"agent_id\":\"agent-789\""));
        assertTrue(jsonString.contains("\"infer\":true"));
        assertTrue(jsonString.contains("\"topic\":\"greeting\""));
        assertTrue(jsonString.contains("\"owner_id\":\"owner-123\""));
    }

    @Test
    public void testToXContentMinimal() throws IOException {
        XContentBuilder builder = MediaTypeRegistry.contentBuilder(XContentType.JSON);
        inputMinimal.toXContent(builder, EMPTY_PARAMS);
        String jsonString = TestHelper.xContentBuilderToString(builder);

        assertTrue(jsonString.contains("\"memory_container_id\""));
        assertTrue(jsonString.contains("\"messages\":["));
        assertTrue(jsonString.contains("\"content_text\":\"Single message\""));
        assertTrue(!jsonString.contains("\"session_id\""));
        assertTrue(!jsonString.contains("\"agent_id\""));
        assertTrue(jsonString.contains("\"infer\":false"));
        assertTrue(!jsonString.contains("\"tags\""));
        assertTrue(jsonString.contains("\"owner_id\":\"owner-minimal\""));
    }

    @Test
    public void testParse() throws IOException {
        String jsonString = "{"
            + "\"memory_container_id\":\"container-123\","
            + "\"messages\":["
            + "{\"role\":\"user\",\"content\":\"Test message 1\"},"
            + "{\"role\":\"assistant\",\"content\":\"Test response\"}"
            + "],"
            + "\"namespace\": {\"session_id\":\"session-789\", \"agent_id\":\"agent-456\"},"
            + "\"infer\":false,"
            + "\"tags\":{\"key\":\"value\"}"
            + "}";

        XContentParser parser = XContentType.JSON
            .xContent()
            .createParser(NamedXContentRegistry.EMPTY, LoggingDeprecationHandler.INSTANCE, jsonString);
        parser.nextToken();

        MLAddMemoriesInput parsed = MLAddMemoriesInput.parse(parser, "container-123");

        assertEquals("container-123", parsed.getMemoryContainerId());
        assertEquals(2, parsed.getMessages().size());
        assertEquals("user", parsed.getMessages().get(0).getRole());
        assertEquals("Test message 1", parsed.getMessages().get(0).getContent());
        assertEquals("session-789", parsed.getSessionId());
        assertEquals("agent-456", parsed.getAgentId());
        assertEquals(Boolean.FALSE, parsed.isInfer());
        assertEquals(1, parsed.getTags().size());
        assertEquals("value", parsed.getTags().get("key"));
    }

    @Test
    public void testParseMinimal() throws IOException {
        String jsonString = "{"
            + "\"memory_container_id\":\"container-123\","
            + "\"messages\":["
            + "{\"role\":\"user\", \"content\":\"Minimal message\"}"
            + "]"
            + "}";

        XContentParser parser = XContentType.JSON
            .xContent()
            .createParser(NamedXContentRegistry.EMPTY, LoggingDeprecationHandler.INSTANCE, jsonString);
        parser.nextToken();

        MLAddMemoriesInput parsed = MLAddMemoriesInput.parse(parser, null);

        assertNotNull(parsed.getMemoryContainerId());
        assertEquals(1, parsed.getMessages().size());
        assertEquals("Minimal message", parsed.getMessages().get(0).getContent());
        assertEquals("user", parsed.getMessages().get(0).getRole());
    }

    @Test
    public void testParseWithUnknownFields() throws IOException {
        String jsonString = "{"
            + "\"messages\":[{\"role\":\"user\", \"content\":\"Test\"}],"
            + "\"memory_container_id\":\"container-123\","
            + "\"unknown_field\":\"ignored\","
            + "\"another_unknown\":123"
            + "}";

        XContentParser parser = XContentType.JSON
            .xContent()
            .createParser(NamedXContentRegistry.EMPTY, LoggingDeprecationHandler.INSTANCE, jsonString);
        parser.nextToken();

        MLAddMemoriesInput parsed = MLAddMemoriesInput.parse(parser, null);

        assertEquals(1, parsed.getMessages().size());
        assertEquals("Test", parsed.getMessages().get(0).getContent());
    }

    @Test
    public void testSetters() {
        MLAddMemoriesInput input = MLAddMemoriesInput
            .builder()
            .memoryContainerId("container-123")
            .messages(Arrays.asList(MessageInput.builder().role("user").contentText("Initial").build()))
            .ownerId("owner-setters")
            .build();

        input.setMemoryContainerId("new-container");
        input.setNamespace(Map.of(SESSION_ID_FIELD, "new-session", "agent_id", "new-agent"));
        input.setInfer(true);
        input.setTags(testTags);
        input.setOwnerId("new-owner");

        assertEquals("new-container", input.getMemoryContainerId());
        assertEquals("new-session", input.getSessionId());
        assertEquals("new-agent", input.getAgentId());
        assertEquals(Boolean.TRUE, input.isInfer());
        assertEquals(testTags, input.getTags());
        assertEquals("new-owner", input.getOwnerId());
    }

    @Test
    public void testLargeNumberOfMessages() {
        // Test that we can handle a large number of messages now that limit is removed
        List<MessageInput> manyMessages = new ArrayList<>();
        for (int i = 0; i < 1000; i++) { // Test with 1000 messages
            manyMessages.add(MessageInput.builder().role("user").contentText("Message " + i).build());
        }

        // Should succeed with large number of messages
        MLAddMemoriesInput input = MLAddMemoriesInput
            .builder()
            .memoryContainerId("container-1")
            .messages(manyMessages)
            .ownerId("owner-large")
            .build();
        assertEquals(1000, input.getMessages().size());
    }

    @Test
    public void testSpecialCharactersInFields() throws IOException {
        Map<String, String> specialTags = new HashMap<>();
        specialTags.put("key with spaces", "value with\nnewlines");

        List<MessageInput> specialMessages = Arrays
            .asList(
                MessageInput.builder().role("user").contentText("Message with\n\ttabs and \"quotes\"").build(),
                MessageInput.builder().role("assistant").contentText("Response with unicode 🚀✨").build()
            );

        MLAddMemoriesInput specialInput = MLAddMemoriesInput
            .builder()
            .memoryContainerId("container-with-special-chars")
            .messages(specialMessages)
            .namespace(Map.of(SESSION_ID_FIELD, "session-🔥"))
            .tags(specialTags)
            .ownerId("owner-special")
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
        MLAddMemoriesInput parsed = MLAddMemoriesInput.parse(parser, null);

        // Verify all fields match
        assertEquals(inputWithAllFields.getMemoryContainerId(), parsed.getMemoryContainerId());
        assertEquals(inputWithAllFields.getMessages().size(), parsed.getMessages().size());
        assertEquals(inputWithAllFields.getSessionId(), parsed.getSessionId());
        assertEquals(inputWithAllFields.getAgentId(), parsed.getAgentId());
        assertEquals(inputWithAllFields.isInfer(), parsed.isInfer());
        assertEquals(inputWithAllFields.getTags(), parsed.getTags());
    }
}
