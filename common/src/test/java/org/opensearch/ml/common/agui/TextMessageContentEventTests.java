/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.agui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;

import org.junit.Test;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.core.common.io.stream.StreamInput;

public class TextMessageContentEventTests {

    @Test
    public void testConstructor() {
        String messageId = "msg_123";
        String delta = "Hello, ";

        TextMessageContentEvent event = new TextMessageContentEvent(messageId, delta);

        assertNotNull("Event should not be null", event);
        assertEquals("Type should be TEXT_MESSAGE_CONTENT", "TEXT_MESSAGE_CONTENT", event.getType());
        assertEquals("Message ID should match", messageId, event.getMessageId());
        assertEquals("Delta should match", delta, event.getDelta());
        assertNotNull("Timestamp should be set", event.getTimestamp());
    }

    @Test
    public void testSerialization() throws IOException {
        TextMessageContentEvent original = new TextMessageContentEvent("msg_test", "world!");

        BytesStreamOutput output = new BytesStreamOutput();
        original.writeTo(output);

        StreamInput input = output.bytes().streamInput();
        TextMessageContentEvent deserialized = new TextMessageContentEvent(input);

        assertEquals("Type should match", original.getType(), deserialized.getType());
        assertEquals("Message ID should match", original.getMessageId(), deserialized.getMessageId());
        assertEquals("Delta should match", original.getDelta(), deserialized.getDelta());
        assertEquals("Timestamp should match", original.getTimestamp(), deserialized.getTimestamp());
    }

    @Test
    public void testToXContent() throws IOException {
        TextMessageContentEvent event = new TextMessageContentEvent("msg_xcontent", "test content");

        String json = event.toJsonString();

        assertNotNull("JSON should not be null", json);
        assertTrue("JSON should contain type", json.contains("\"type\":\"TEXT_MESSAGE_CONTENT\""));
        assertTrue("JSON should contain messageId", json.contains("\"messageId\":\"msg_xcontent\""));
        assertTrue("JSON should contain delta", json.contains("\"delta\":\"test content\""));
        assertTrue("JSON should contain timestamp", json.contains("\"timestamp\""));
    }

    @Test
    public void testToJsonString() {
        TextMessageContentEvent event = new TextMessageContentEvent("msg_json", "content");

        String json = event.toJsonString();

        assertNotNull("JSON string should not be null", json);
        assertTrue("JSON should be valid", json.startsWith("{") && json.endsWith("}"));
    }

    @Test
    public void testTimestampGeneration() {
        long before = System.currentTimeMillis();
        TextMessageContentEvent event = new TextMessageContentEvent("msg_time", "delta");
        long after = System.currentTimeMillis();

        assertNotNull("Timestamp should be set", event.getTimestamp());
        assertTrue("Timestamp should be in valid range", event.getTimestamp() >= before && event.getTimestamp() <= after);
    }

    @Test
    public void testEmptyDelta() {
        TextMessageContentEvent event = new TextMessageContentEvent("msg_empty", "");

        assertEquals("Empty delta should be preserved", "", event.getDelta());

        String json = event.toJsonString();
        assertTrue("JSON should contain empty delta", json.contains("\"delta\":\"\""));
    }

    @Test
    public void testSpecialCharactersInDelta() {
        String specialChars = "Hello \"world\" with\nnewlines\tand\ttabs";
        TextMessageContentEvent event = new TextMessageContentEvent("msg_special", specialChars);

        assertEquals("Special characters should be preserved", specialChars, event.getDelta());
    }
}
