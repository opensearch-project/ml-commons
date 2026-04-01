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
import static org.opensearch.ml.common.TestHelper.createTestContent;
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
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.memorycontainer.PayloadType;

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
                MessageInput.builder().role("user").content(createTestContent("Hello, how are you?")).build(),
                MessageInput.builder().role("assistant").content(createTestContent("I'm doing well, thank you!")).build(),
                MessageInput.builder().role("user").content(createTestContent("What can you help me with?")).build()
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
            .payloadType(PayloadType.CONVERSATIONAL)
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
            .messages(Arrays.asList(MessageInput.builder().role("user").content(createTestContent("Single message")).build()))
            .ownerId("owner-minimal")
            .build();

        // Input without optional fields
        inputNoOptionals = MLAddMemoriesInput
            .builder()
            .memoryContainerId("container-999")
            .payloadType(PayloadType.CONVERSATIONAL)
            .messages(Arrays.asList(MessageInput.builder().role("user").content(createTestContent("Test message")).build()))
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
            .messages(Arrays.asList(MessageInput.builder().role("user").content(createTestContent("Test")).build()))
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
            () -> MLAddMemoriesInput
                .builder()
                .memoryContainerId(null)
                .payloadType(PayloadType.CONVERSATIONAL)
                .messages(testMessages)
                .ownerId("owner-1")
                .build()
        );
        assertEquals("No memory container id provided", exception.getMessage());

        // Test null messages with infer=true
        exception = assertThrows(
            IllegalArgumentException.class,
            () -> MLAddMemoriesInput
                .builder()
                .memoryContainerId("container-1")
                .payloadType(PayloadType.CONVERSATIONAL)
                .messages(null)
                .infer(true)
                .ownerId("owner-1")
                .build()
        );
        assertEquals("No messages provided when inferring memory", exception.getMessage());

        // Test empty messages with infer=true
        exception = assertThrows(
            IllegalArgumentException.class,
            () -> MLAddMemoriesInput
                .builder()
                .memoryContainerId("container-1")
                .payloadType(PayloadType.CONVERSATIONAL)
                .messages(new ArrayList<>())
                .infer(true)
                .ownerId("owner-1")
                .build()
        );
        assertEquals("No messages provided when inferring memory", exception.getMessage());

        // Test valid case - null messages with infer=false should pass
        MLAddMemoriesInput validInput = MLAddMemoriesInput
            .builder()
            .memoryContainerId("container-1")
            .payloadType(PayloadType.CONVERSATIONAL)
            .messages(null)
            .infer(false)
            .ownerId("owner-1")
            .build();
        assertNotNull(validInput);
    }

    @Test
    public void testParse() throws IOException {
        String jsonString = "{"
            + "\"memory_container_id\":\"container-123\","
            + "\"messages\":["
            + "{\"role\":\"user\",\"content\":[{\"type\":\"text\", \"text\": \"Test message 1\"}]},"
            + "{\"role\":\"assistant\",\"content\":[{\"type\":\"text\", \"text\": \"Test response\"}]}"
            + "],"
            + "\"namespace\": {\"session_id\":\"session-789\", \"agent_id\":\"agent-456\"},"
            + "\"infer\":false,"
            + "\"tags\":{\"key\":\"value\"}"
            + "}";

        XContentParser parser = XContentType.JSON
            .xContent()
            .createParser(NamedXContentRegistry.EMPTY, LoggingDeprecationHandler.INSTANCE, jsonString);
        parser.nextToken();

        MLAddMemoriesInput parsed = MLAddMemoriesInput.parse(parser, "container-123", null);

        assertEquals("container-123", parsed.getMemoryContainerId());
        assertEquals(2, parsed.getMessages().size());
        assertEquals("user", parsed.getMessages().get(0).getRole());
        assertEquals("Test message 1", parsed.getMessages().get(0).getContent().get(0).get("text"));
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
            + "{\"role\":\"user\", \"content\":[{\"type\":\"text\", \"text\": \"Minimal message\"}]}"
            + "]"
            + "}";

        XContentParser parser = XContentType.JSON
            .xContent()
            .createParser(NamedXContentRegistry.EMPTY, LoggingDeprecationHandler.INSTANCE, jsonString);
        parser.nextToken();

        MLAddMemoriesInput parsed = MLAddMemoriesInput.parse(parser, null, null);

        assertNotNull(parsed.getMemoryContainerId());
        assertEquals(1, parsed.getMessages().size());
        assertEquals("Minimal message", parsed.getMessages().get(0).getContent().get(0).get("text"));
        assertEquals("user", parsed.getMessages().get(0).getRole());
    }

    @Test
    public void testParseWithUnknownFields() throws IOException {
        String jsonString = "{"
            + "\"messages\":[{\"role\":\"user\", \"content\":[{\"type\":\"text\", \"text\": \"Test\"}]}],"
            + "\"memory_container_id\":\"container-123\","
            + "\"unknown_field\":\"ignored\","
            + "\"another_unknown\":123"
            + "}";

        XContentParser parser = XContentType.JSON
            .xContent()
            .createParser(NamedXContentRegistry.EMPTY, LoggingDeprecationHandler.INSTANCE, jsonString);
        parser.nextToken();

        MLAddMemoriesInput parsed = MLAddMemoriesInput.parse(parser, null, null);

        assertEquals(1, parsed.getMessages().size());
        assertEquals("Test", parsed.getMessages().get(0).getContent().get(0).get("text"));
    }

    @Test
    public void testSetters() {
        MLAddMemoriesInput input = MLAddMemoriesInput
            .builder()
            .memoryContainerId("container-123")
            .messages(Arrays.asList(MessageInput.builder().role("user").content(createTestContent("Initial")).build()))
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
            manyMessages.add(MessageInput.builder().role("user").content(createTestContent("Message " + i)).build());
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
                MessageInput.builder().role("user").content(createTestContent("Message with\n\ttabs and \"quotes\"")).build(),
                MessageInput.builder().role("assistant").content(createTestContent("Response with unicode 🚀✨")).build()
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

    // Additional tests to achieve 100% coverage

    @Test
    public void testConstructorWithNullMemoryType() {
        // Test that null memoryType defaults to CONVERSATIONAL
        MLAddMemoriesInput input = MLAddMemoriesInput
            .builder()
            .memoryContainerId("container-123")
            .payloadType(null) // null memoryType
            .messages(testMessages)
            .ownerId("owner-123")
            .build();
        assertEquals(PayloadType.CONVERSATIONAL, input.getPayloadType());
    }

    @Test
    public void testConstructorWithAllMemoryTypes() {
        // Test with CONVERSATIONAL type
        MLAddMemoriesInput conversationalInput = MLAddMemoriesInput
            .builder()
            .memoryContainerId("container-123")
            .payloadType(PayloadType.CONVERSATIONAL)
            .messages(testMessages)
            .ownerId("owner-123")
            .build();
        assertEquals(PayloadType.CONVERSATIONAL, conversationalInput.getPayloadType());

        // Test with DATA type
        MLAddMemoriesInput dataInput = MLAddMemoriesInput
            .builder()
            .memoryContainerId("container-123")
            .payloadType(PayloadType.DATA)
            .messages(testMessages)
            .ownerId("owner-123")
            .build();
        assertEquals(PayloadType.DATA, dataInput.getPayloadType());
    }

    @Test
    public void testConstructorWithNullParameters() {
        // Test that null parameters creates empty map
        MLAddMemoriesInput input = MLAddMemoriesInput
            .builder()
            .memoryContainerId("container-123")
            .payloadType(PayloadType.CONVERSATIONAL)
            .messages(testMessages)
            .parameters(null) // null parameters
            .ownerId("owner-123")
            .build();
        assertNotNull(input.getParameters());
        assertTrue(input.getParameters().isEmpty());
    }

    @Test
    public void testConstructorWithEmptyParameters() {
        // Test that empty parameters creates empty map
        Map<String, Object> emptyParams = new HashMap<>();
        MLAddMemoriesInput input = MLAddMemoriesInput
            .builder()
            .memoryContainerId("container-123")
            .payloadType(PayloadType.CONVERSATIONAL)
            .messages(testMessages)
            .parameters(emptyParams)
            .ownerId("owner-123")
            .build();
        assertNotNull(input.getParameters());
        assertTrue(input.getParameters().isEmpty());
    }

    @Test
    public void testConstructorWithParameters() {
        // Test that parameters are copied correctly
        Map<String, Object> params = new HashMap<>();
        params.put("param1", "value1");
        params.put("param2", 42);

        MLAddMemoriesInput input = MLAddMemoriesInput
            .builder()
            .memoryContainerId("container-123")
            .payloadType(PayloadType.CONVERSATIONAL)
            .messages(testMessages)
            .parameters(params)
            .ownerId("owner-123")
            .build();
        assertNotNull(input.getParameters());
        assertEquals(2, input.getParameters().size());
        assertEquals("value1", input.getParameters().get("param1"));
        assertEquals(42, input.getParameters().get("param2"));
    }

    @Test
    public void testStreamInputOutputWithAllFieldTypes() throws IOException {
        // Test with all possible field combinations including messageId, binaryData, structuredData
        Map<String, Object> testStructuredData = new HashMap<>();
        testStructuredData.put("key1", "value1");
        testStructuredData.put("key2", 42);
        testStructuredData.put("key3", true);

        Map<String, Object> testParameters = new HashMap<>();
        testParameters.put("param1", "paramValue1");
        testParameters.put("param2", 123);

        MLAddMemoriesInput fullInput = MLAddMemoriesInput
            .builder()
            .memoryContainerId("container-full")
            .payloadType(PayloadType.DATA)
            .messages(testMessages)
            .messageId(42)
            .binaryData("binary-data-test")
            .structuredData(testStructuredData)
            .namespace(namespace)
            .infer(true)
            .metadata(metadata)
            .tags(testTags)
            .parameters(testParameters)
            .ownerId("owner-full")
            .build();

        BytesStreamOutput out = new BytesStreamOutput();
        fullInput.writeTo(out);
        StreamInput in = out.bytes().streamInput();
        MLAddMemoriesInput deserialized = new MLAddMemoriesInput(in);

        assertEquals(fullInput.getMemoryContainerId(), deserialized.getMemoryContainerId());
        assertEquals(fullInput.getPayloadType(), deserialized.getPayloadType());
        assertEquals(fullInput.getMessages().size(), deserialized.getMessages().size());
        assertEquals(fullInput.getMessageId(), deserialized.getMessageId());
        assertEquals(fullInput.getBinaryData(), deserialized.getBinaryData());
        assertEquals(fullInput.getStructuredData(), deserialized.getStructuredData());
        assertEquals(fullInput.getNamespace(), deserialized.getNamespace());
        assertEquals(fullInput.isInfer(), deserialized.isInfer());
        assertEquals(fullInput.getMetadata(), deserialized.getMetadata());
        assertEquals(fullInput.getTags(), deserialized.getTags());
        assertEquals(fullInput.getParameters(), deserialized.getParameters());
        assertEquals(fullInput.getOwnerId(), deserialized.getOwnerId());
    }

    @Test
    public void testStreamInputOutputWithNullFields() throws IOException {
        // Test serialization with all optional fields as null
        // Use builder with minimal fields
        MLAddMemoriesInput nullFieldsInput = MLAddMemoriesInput
            .builder()
            .memoryContainerId("container-null")
            .payloadType(PayloadType.CONVERSATIONAL)
            .messages(null) // null messages is OK when infer=false
            .infer(false) // infer=false allows null messages
            .build();

        BytesStreamOutput out = new BytesStreamOutput();
        nullFieldsInput.writeTo(out);
        StreamInput in = out.bytes().streamInput();
        MLAddMemoriesInput deserialized = new MLAddMemoriesInput(in);

        assertEquals("container-null", deserialized.getMemoryContainerId());
        assertEquals(PayloadType.CONVERSATIONAL, deserialized.getPayloadType());
        assertNull(deserialized.getMessages());
        assertNull(deserialized.getMessageId());
        assertNull(deserialized.getBinaryData());
        assertNull(deserialized.getStructuredData());
        assertNull(deserialized.getNamespace());
        assertFalse(deserialized.isInfer());
        assertNull(deserialized.getMetadata());
        assertNull(deserialized.getTags());
        assertNotNull(deserialized.getParameters()); // Constructor creates empty map for null parameters
        assertTrue(deserialized.getParameters().isEmpty());
        assertNull(deserialized.getOwnerId());
    }

    @Test
    public void testParseWithAllFields() throws IOException {
        // Test parsing with all possible fields (memory_container_id removed - comes from URL path)
        String jsonString = "{"
            + "\"payload_type\":\"data\","
            + "\"messages\":["
            + "{\"role\":\"user\",\"content\":[{\"type\":\"text\", \"text\": \"Test message\"}]}"
            + "],"
            + "\"message_id\":123,"
            + "\"binary_data\":\"test-binary\","
            + "\"structured_data\":{\"key\":\"value\"},"
            + "\"namespace\":{\"session_id\":\"session-123\", \"agent_id\":\"agent-456\"},"
            + "\"infer\":true,"
            + "\"metadata\":{\"meta_key\":\"meta_value\"},"
            + "\"tags\":{\"tag_key\":\"tag_value\"},"
            + "\"parameters\":{\"param_key\":\"param_value\"},"
            + "\"owner_id\":\"owner-parse\""
            + "}";

        XContentParser parser = XContentType.JSON
            .xContent()
            .createParser(NamedXContentRegistry.EMPTY, LoggingDeprecationHandler.INSTANCE, jsonString);
        parser.nextToken();

        MLAddMemoriesInput parsed = MLAddMemoriesInput.parse(parser, "override-container", null);

        assertEquals("override-container", parsed.getMemoryContainerId()); // Should use parameter value (security: no parsing from body)
        assertEquals(PayloadType.DATA, parsed.getPayloadType());
        assertEquals(1, parsed.getMessages().size());
        assertEquals(Integer.valueOf(123), parsed.getMessageId());
        assertEquals("test-binary", parsed.getBinaryData());
        assertNotNull(parsed.getStructuredData());
        assertEquals("value", parsed.getStructuredData().get("key"));
        assertEquals("session-123", parsed.getSessionId());
        assertEquals("agent-456", parsed.getAgentId());
        assertTrue(parsed.isInfer());
        assertEquals("meta_value", parsed.getMetadata().get("meta_key"));
        assertEquals("tag_value", parsed.getTags().get("tag_key"));
        assertEquals("param_value", parsed.getParameters().get("param_key"));
        assertEquals("owner-parse", parsed.getOwnerId());
    }

    @Test
    public void testParseWithNullMemoryType() throws IOException {
        // Test parsing without memory type (should default to CONVERSATIONAL)
        String jsonString = "{"
            + "\"memory_container_id\":\"container-123\","
            + "\"messages\":["
            + "{\"role\":\"user\",\"content\":[{\"type\":\"text\", \"text\": \"Test\"}]}"
            + "]"
            + "}";

        XContentParser parser = XContentType.JSON
            .xContent()
            .createParser(NamedXContentRegistry.EMPTY, LoggingDeprecationHandler.INSTANCE, jsonString);
        parser.nextToken();

        MLAddMemoriesInput parsed = MLAddMemoriesInput.parse(parser, "test-container", null);

        assertEquals(PayloadType.CONVERSATIONAL, parsed.getPayloadType());
    }

    @Test
    public void testParseWithOverrideContainerId() throws IOException {
        // Test parsing with container ID override
        String jsonString = "{"
            + "\"messages\":["
            + "{\"role\":\"user\",\"content\":[{\"type\":\"text\", \"text\": \"Test\"}]}"
            + "]"
            + "}";

        XContentParser parser = XContentType.JSON
            .xContent()
            .createParser(NamedXContentRegistry.EMPTY, LoggingDeprecationHandler.INSTANCE, jsonString);
        parser.nextToken();

        MLAddMemoriesInput parsed = MLAddMemoriesInput.parse(parser, "override-container-id", null);

        assertEquals("override-container-id", parsed.getMemoryContainerId());
    }

    @Test
    public void testGetSessionIdAndAgentIdWithNullNamespace() {
        // Test getSessionId and getAgentId when namespace is null
        MLAddMemoriesInput input = MLAddMemoriesInput.builder().memoryContainerId("container-123").namespace(null).build();

        assertNull(input.getSessionId());
        assertNull(input.getAgentId());
    }

    @Test
    public void testGetSessionIdAndAgentIdWithEmptyNamespace() {
        // Test getSessionId and getAgentId when namespace is empty
        MLAddMemoriesInput input = MLAddMemoriesInput.builder().memoryContainerId("container-123").namespace(new HashMap<>()).build();

        assertNull(input.getSessionId());
        assertNull(input.getAgentId());
    }

    @Test
    public void testAllSettersAndGetters() {
        // Test all setters and getters for complete coverage
        // Start with a valid input to avoid validation issues
        MLAddMemoriesInput input = MLAddMemoriesInput
            .builder()
            .memoryContainerId("initial-container")
            .messages(testMessages) // Start with valid messages
            .build();

        // Test all setters
        input.setMemoryContainerId("test-container");
        input.setPayloadType(PayloadType.DATA);
        input.setMessages(testMessages);
        input.setMessageId(999);
        input.setBinaryData("test-binary");
        input.setStructuredData(structuredData);
        input.setNamespace(namespace);
        input.setInfer(true);
        input.setMetadata(metadata);
        input.setTags(testTags);
        input.setParameters(Map.of("key", "value"));
        input.setOwnerId("test-owner");

        // Test all getters
        assertEquals("test-container", input.getMemoryContainerId());
        assertEquals(PayloadType.DATA, input.getPayloadType());
        assertEquals(testMessages, input.getMessages());
        assertEquals(Integer.valueOf(999), input.getMessageId());
        assertEquals("test-binary", input.getBinaryData());
        assertEquals(structuredData, input.getStructuredData());
        assertEquals(namespace, input.getNamespace());
        assertTrue(input.isInfer());
        assertEquals(metadata, input.getMetadata());
        assertEquals(testTags, input.getTags());
        assertEquals("value", input.getParameters().get("key"));
        assertEquals("test-owner", input.getOwnerId());
        assertEquals("session-456", input.getSessionId());
        assertEquals("agent-789", input.getAgentId());
    }

    @Test
    public void testBuilderDefaults() {
        // Test that builder creates object with proper defaults
        // Note: Builder calls constructor which sets defaults and validates
        MLAddMemoriesInput input = MLAddMemoriesInput.builder().memoryContainerId("test-container").messages(testMessages).build();

        assertEquals("test-container", input.getMemoryContainerId());
        assertEquals(testMessages, input.getMessages());
        assertEquals(PayloadType.CONVERSATIONAL, input.getPayloadType()); // Constructor sets default
        assertNull(input.getMessageId());
        assertNull(input.getBinaryData());
        assertNull(input.getStructuredData());
        assertNull(input.getNamespace());
        assertFalse(input.isInfer()); // Default is false
        assertNull(input.getMetadata());
        assertNull(input.getTags());
        assertNotNull(input.getParameters()); // Constructor creates empty map
        assertTrue(input.getParameters().isEmpty());
        assertNull(input.getOwnerId());
    }

    @Test
    public void testStreamInputOutputWithEmptyMaps() throws IOException {
        // Test serialization with empty maps
        Map<String, String> emptyNamespace = new HashMap<>();
        Map<String, String> emptyMetadata = new HashMap<>();
        Map<String, String> emptyTags = new HashMap<>();
        Map<String, Object> emptyParameters = new HashMap<>();

        MLAddMemoriesInput inputEmptyMaps = MLAddMemoriesInput
            .builder()
            .memoryContainerId("container-empty-maps")
            .payloadType(PayloadType.DATA)
            .messages(testMessages)
            .namespace(emptyNamespace)
            .infer(true)
            .metadata(emptyMetadata)
            .tags(emptyTags)
            .parameters(emptyParameters)
            .ownerId("owner-empty")
            .build();

        BytesStreamOutput out = new BytesStreamOutput();
        inputEmptyMaps.writeTo(out);
        StreamInput in = out.bytes().streamInput();
        MLAddMemoriesInput deserialized = new MLAddMemoriesInput(in);

        assertEquals("container-empty-maps", deserialized.getMemoryContainerId());
        assertEquals(PayloadType.DATA, deserialized.getPayloadType());
        assertTrue(deserialized.isInfer());

        // Empty maps should be null after deserialization (due to writeTo logic)
        assertNull(deserialized.getNamespace());
        assertNull(deserialized.getMetadata());
        assertNull(deserialized.getTags());
        // Parameters is always serialized (even when empty) because writeTo only checks != null
        assertNotNull(deserialized.getParameters());
        assertTrue(deserialized.getParameters().isEmpty());
    }

    @Test
    public void testValidateWithEmptyMessagesAndInferFalse() {
        // Test that empty messages with infer=false is valid
        MLAddMemoriesInput validInput = MLAddMemoriesInput
            .builder()
            .memoryContainerId("container-123")
            .payloadType(PayloadType.CONVERSATIONAL)
            .messages(new ArrayList<>()) // empty messages
            .infer(false) // infer=false
            .ownerId("owner-123")
            .build();

        // Should not throw exception
        assertNotNull(validInput);
        assertEquals("container-123", validInput.getMemoryContainerId());
        assertFalse(validInput.isInfer());
        assertTrue(validInput.getMessages().isEmpty());
    }

    @Test
    public void testParseWithInvalidPayloadType() throws IOException {
        // Test parsing with invalid memory type (should throw exception)
        String jsonString = "{"
            + "\"memory_container_id\":\"container-123\","
            + "\"payload_type\":\"invalid_type\","
            + "\"messages\":["
            + "{\"role\":\"user\",\"content\":[{\"type\":\"text\", \"text\": \"Test\"}]}"
            + "]"
            + "}";

        XContentParser parser = XContentType.JSON
            .xContent()
            .createParser(NamedXContentRegistry.EMPTY, LoggingDeprecationHandler.INSTANCE, jsonString);
        parser.nextToken();

        // Should throw IllegalArgumentException due to invalid memory type
        assertThrows(IllegalArgumentException.class, () -> { MLAddMemoriesInput.parse(parser, null, null); });
    }

    @Test
    public void testCheckpointIdField() {
        // Test with checkpoint_id field
        MLAddMemoriesInput input = MLAddMemoriesInput
            .builder()
            .memoryContainerId("container-123")
            .messages(testMessages)
            .checkpointId("checkpoint-123")
            .ownerId("owner-checkpoint")
            .build();

        assertEquals("checkpoint-123", input.getCheckpointId());
    }

    @Test
    public void testCheckpointIdSerialization() throws IOException {
        // Test serialization with checkpoint_id field
        MLAddMemoriesInput input = MLAddMemoriesInput
            .builder()
            .memoryContainerId("container-123")
            .messages(testMessages)
            .checkpointId("checkpoint-456")
            .ownerId("owner-checkpoint-ser")
            .build();

        BytesStreamOutput out = new BytesStreamOutput();
        input.writeTo(out);
        StreamInput in = out.bytes().streamInput();
        MLAddMemoriesInput deserialized = new MLAddMemoriesInput(in);

        assertEquals(input.getCheckpointId(), deserialized.getCheckpointId());
    }

    @Test
    public void testParseWithCheckpointId() throws IOException {
        // Test parsing with checkpoint_id field
        String jsonString = "{"
            + "\"memory_container_id\":\"container-123\","
            + "\"messages\":["
            + "{\"role\":\"user\",\"content\":[{\"type\":\"text\", \"text\": \"Test message\"}]}"
            + "],"
            + "\"checkpoint_id\":\"checkpoint-789\""
            + "}";

        XContentParser parser = XContentType.JSON
            .xContent()
            .createParser(NamedXContentRegistry.EMPTY, LoggingDeprecationHandler.INSTANCE, jsonString);
        parser.nextToken();

        MLAddMemoriesInput parsed = MLAddMemoriesInput.parse(parser, "container-123", null);

        assertEquals("checkpoint-789", parsed.getCheckpointId());
    }

    @Test
    public void testCheckpointIdSetter() {
        // Test setter for checkpoint_id field
        MLAddMemoriesInput input = MLAddMemoriesInput
            .builder()
            .memoryContainerId("container-123")
            .messages(testMessages)
            .ownerId("owner-setters")
            .build();

        input.setCheckpointId("new-checkpoint-id");

        assertEquals("new-checkpoint-id", input.getCheckpointId());
    }

}
