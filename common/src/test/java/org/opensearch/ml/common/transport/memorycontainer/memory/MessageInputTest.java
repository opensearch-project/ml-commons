/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.memorycontainer.memory;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.opensearch.core.xcontent.ToXContent.EMPTY_PARAMS;

import java.io.IOException;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
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
    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    @Before
    public void setUp() {
        messageWithRole = MessageInput.builder().role("user").contentText("Hello, how are you?").build();
    }

    @Test
    public void testBuilderWithRole() {
        assertNotNull(messageWithRole);
        assertEquals("user", messageWithRole.getRole());
        assertEquals("Hello, how are you?", messageWithRole.getContent());
    }

    @Test
    public void testBuilderWithoutRole() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("Message must have role and content");
        MessageInput.builder().contentText("Hello, how are you?").build();
    }

    @Test
    public void testConstructorWithRole() {
        MessageInput message = MessageInput.builder().role("assistant").contentText("I'm doing well, thank you!").build();
        assertEquals("assistant", message.getRole());
        assertEquals("I'm doing well, thank you!", message.getContent());
    }

    @Test
    public void testConstructorWithNullContent() {
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> MessageInput.builder().role("user").build()
        );
        assertEquals("Message must have role and content", exception.getMessage());
    }

    @Test
    public void testConstructorWithEmptyContent() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("Message must have role and content");
        MessageInput.builder().role("user").contentText(null).build();
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
    public void testToXContent() throws IOException {
        XContentBuilder builder = MediaTypeRegistry.contentBuilder(XContentType.JSON);
        messageWithRole.toXContent(builder, EMPTY_PARAMS);
        String jsonString = TestHelper.xContentBuilderToString(builder);

        assertTrue(jsonString.contains("\"role\":\"user\""));
        assertTrue(jsonString.contains("\"content_text\":\"Hello, how are you?\""));
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
        MessageInput message = MessageInput.builder().role("user").contentText("Initial content").build();

        message.setRole("system");
        message.setContentText("Updated content");

        assertEquals("system", message.getRole());
        assertEquals("Updated content", message.getContent());
    }

    @Test
    public void testSpecialCharactersInContent() throws IOException {
        MessageInput specialMessage = MessageInput
            .builder()
            .role("user")
            .contentText("Content with\n\ttabs,\nnewlines, \"quotes\", 'single quotes', and unicode 🚀✨")
            .build();

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
        String[] roles = { "user", "assistant", "system", "human", "ai" };

        for (String role : roles) {
            MessageInput message = MessageInput.builder().role(role).contentText("Test content").build();
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
