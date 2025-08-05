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

public class MessageInputTest {

    private MessageInput messageWithRole;
    private MessageInput messageWithoutRole;

    @Before
    public void setUp() {
        messageWithRole = MessageInput.builder().role("user").content("Hello, how are you?").build();

        messageWithoutRole = MessageInput.builder().content("Just a message without role").build();
    }

    @Test
    public void testBuilderWithRole() {
        assertNotNull(messageWithRole);
        assertEquals("user", messageWithRole.getRole());
        assertEquals("Hello, how are you?", messageWithRole.getContent());
    }

    @Test
    public void testBuilderWithoutRole() {
        assertNotNull(messageWithoutRole);
        assertNull(messageWithoutRole.getRole());
        assertEquals("Just a message without role", messageWithoutRole.getContent());
    }

    @Test
    public void testConstructorWithRole() {
        MessageInput message = new MessageInput("assistant", "I'm doing well, thank you!");
        assertEquals("assistant", message.getRole());
        assertEquals("I'm doing well, thank you!", message.getContent());
    }

    @Test
    public void testConstructorWithoutRole() {
        MessageInput message = new MessageInput(null, "Message content");
        assertNull(message.getRole());
        assertEquals("Message content", message.getContent());
    }

    @Test
    public void testConstructorWithNullContent() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> new MessageInput("user", null));
        assertEquals("Content is required", exception.getMessage());
    }

    @Test
    public void testConstructorWithEmptyContent() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> new MessageInput("user", ""));
        assertEquals("Content is required", exception.getMessage());
    }

    @Test
    public void testStreamInputOutput() throws IOException {
        // Test with role
        BytesStreamOutput out = new BytesStreamOutput();
        messageWithRole.writeTo(out);
        StreamInput in = out.bytes().streamInput();
        MessageInput deserialized = new MessageInput(in);

        assertEquals(messageWithRole.getRole(), deserialized.getRole());
        assertEquals(messageWithRole.getContent(), deserialized.getContent());
    }

    @Test
    public void testStreamInputOutputWithoutRole() throws IOException {
        // Test without role
        BytesStreamOutput out = new BytesStreamOutput();
        messageWithoutRole.writeTo(out);
        StreamInput in = out.bytes().streamInput();
        MessageInput deserialized = new MessageInput(in);

        assertNull(deserialized.getRole());
        assertEquals(messageWithoutRole.getContent(), deserialized.getContent());
    }

    @Test
    public void testToXContent() throws IOException {
        XContentBuilder builder = MediaTypeRegistry.contentBuilder(XContentType.JSON);
        messageWithRole.toXContent(builder, EMPTY_PARAMS);
        String jsonString = TestHelper.xContentBuilderToString(builder);

        assertTrue(jsonString.contains("\"role\":\"user\""));
        assertTrue(jsonString.contains("\"content\":\"Hello, how are you?\""));
    }

    @Test
    public void testToXContentWithoutRole() throws IOException {
        XContentBuilder builder = MediaTypeRegistry.contentBuilder(XContentType.JSON);
        messageWithoutRole.toXContent(builder, EMPTY_PARAMS);
        String jsonString = TestHelper.xContentBuilderToString(builder);

        assertTrue(!jsonString.contains("\"role\""));
        assertTrue(jsonString.contains("\"content\":\"Just a message without role\""));
    }

    @Test
    public void testParse() throws IOException {
        String jsonString = "{\"role\":\"user\",\"content\":\"Test message\"}";

        XContentParser parser = XContentType.JSON
            .xContent()
            .createParser(NamedXContentRegistry.EMPTY, LoggingDeprecationHandler.INSTANCE, jsonString);
        parser.nextToken();

        MessageInput parsed = MessageInput.parse(parser);

        assertEquals("user", parsed.getRole());
        assertEquals("Test message", parsed.getContent());
    }

    @Test
    public void testParseWithoutRole() throws IOException {
        String jsonString = "{\"content\":\"Message without role\"}";

        XContentParser parser = XContentType.JSON
            .xContent()
            .createParser(NamedXContentRegistry.EMPTY, LoggingDeprecationHandler.INSTANCE, jsonString);
        parser.nextToken();

        MessageInput parsed = MessageInput.parse(parser);

        assertNull(parsed.getRole());
        assertEquals("Message without role", parsed.getContent());
    }

    @Test
    public void testParseWithUnknownFields() throws IOException {
        String jsonString = "{\"role\":\"assistant\",\"content\":\"Test\",\"unknown\":\"field\"}";

        XContentParser parser = XContentType.JSON
            .xContent()
            .createParser(NamedXContentRegistry.EMPTY, LoggingDeprecationHandler.INSTANCE, jsonString);
        parser.nextToken();

        MessageInput parsed = MessageInput.parse(parser);

        assertEquals("assistant", parsed.getRole());
        assertEquals("Test", parsed.getContent());
    }

    @Test
    public void testSetters() {
        MessageInput message = new MessageInput(null, "Initial content");

        message.setRole("system");
        message.setContent("Updated content");

        assertEquals("system", message.getRole());
        assertEquals("Updated content", message.getContent());
    }

    @Test
    public void testSpecialCharactersInContent() throws IOException {
        MessageInput specialMessage = new MessageInput(
            "user",
            "Content with\n\ttabs,\nnewlines, \"quotes\", 'single quotes', and unicode 🚀✨"
        );

        // Test serialization round trip
        BytesStreamOutput out = new BytesStreamOutput();
        specialMessage.writeTo(out);
        StreamInput in = out.bytes().streamInput();
        MessageInput deserialized = new MessageInput(in);

        assertEquals(specialMessage.getRole(), deserialized.getRole());
        assertEquals(specialMessage.getContent(), deserialized.getContent());

        // Test XContent
        XContentBuilder builder = MediaTypeRegistry.contentBuilder(XContentType.JSON);
        specialMessage.toXContent(builder, EMPTY_PARAMS);
        String jsonString = TestHelper.xContentBuilderToString(builder);

        assertTrue(jsonString.contains("Content with"));
        assertTrue(jsonString.contains("tabs"));
        assertTrue(jsonString.contains("quotes"));
    }

    @Test
    public void testRoleValues() throws IOException {
        String[] roles = { "user", "assistant", "system", "human", "ai", null };

        for (String role : roles) {
            MessageInput message = new MessageInput(role, "Test content");
            assertEquals(role, message.getRole());

            // Test round trip
            BytesStreamOutput out = new BytesStreamOutput();
            message.writeTo(out);
            StreamInput in = out.bytes().streamInput();
            MessageInput deserialized = new MessageInput(in);
            assertEquals(role, deserialized.getRole());
        }
    }

    @Test
    public void testXContentRoundTrip() throws IOException {
        // Convert to XContent
        XContentBuilder builder = MediaTypeRegistry.contentBuilder(XContentType.JSON);
        messageWithRole.toXContent(builder, EMPTY_PARAMS);
        String jsonString = TestHelper.xContentBuilderToString(builder);

        // Parse back
        XContentParser parser = XContentType.JSON
            .xContent()
            .createParser(NamedXContentRegistry.EMPTY, LoggingDeprecationHandler.INSTANCE, jsonString);
        parser.nextToken();
        MessageInput parsed = MessageInput.parse(parser);

        // Verify all fields match
        assertEquals(messageWithRole.getRole(), parsed.getRole());
        assertEquals(messageWithRole.getContent(), parsed.getContent());
    }
}
