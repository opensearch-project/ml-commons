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
public class TextMessageStartEventTests {

    @Test
    public void testConstructor() {
        String messageId = "msg_123";
        String role = "assistant";
        
        TextMessageStartEvent event = new TextMessageStartEvent(messageId, role);
        
        assertNotNull("Event should not be null", event);
        assertEquals("Type should be TEXT_MESSAGE_START", "TEXT_MESSAGE_START", event.getType());
        assertEquals("Message ID should match", messageId, event.getMessageId());
        assertEquals("Role should match", role, event.getRole());
        assertNotNull("Timestamp should be set", event.getTimestamp());
    }

    @Test
    public void testSerialization() throws IOException {
        TextMessageStartEvent original = new TextMessageStartEvent("msg_test", "assistant");
        
        BytesStreamOutput output = new BytesStreamOutput();
        original.writeTo(output);
        
        StreamInput input = output.bytes().streamInput();
        TextMessageStartEvent deserialized = new TextMessageStartEvent(input);
        
        assertEquals("Type should match", original.getType(), deserialized.getType());
        assertEquals("Message ID should match", original.getMessageId(), deserialized.getMessageId());
        assertEquals("Role should match", original.getRole(), deserialized.getRole());
        assertEquals("Timestamp should match", original.getTimestamp(), deserialized.getTimestamp());
    }

    @Test
    public void testToXContent() throws IOException {
        TextMessageStartEvent event = new TextMessageStartEvent("msg_xcontent", "assistant");
        
        String json = event.toJsonString();
        
        assertNotNull("JSON should not be null", json);
        assertTrue("JSON should contain type", json.contains("\"type\":\"TEXT_MESSAGE_START\""));
        assertTrue("JSON should contain messageId", json.contains("\"messageId\":\"msg_xcontent\""));
        assertTrue("JSON should contain role", json.contains("\"role\":\"assistant\""));
        assertTrue("JSON should contain timestamp", json.contains("\"timestamp\""));
    }

    @Test
    public void testToJsonString() {
        TextMessageStartEvent event = new TextMessageStartEvent("msg_json", "user");
        
        String json = event.toJsonString();
        
        assertNotNull("JSON string should not be null", json);
        assertTrue("JSON should be valid", json.startsWith("{") && json.endsWith("}"));
        assertTrue("JSON should contain messageId and role", 
            json.contains("messageId") && json.contains("role"));
    }

    @Test
    public void testTimestampGeneration() {
        long before = System.currentTimeMillis();
        TextMessageStartEvent event = new TextMessageStartEvent("msg_time", "assistant");
        long after = System.currentTimeMillis();
        
        assertNotNull("Timestamp should be set", event.getTimestamp());
        assertTrue("Timestamp should be in valid range", 
            event.getTimestamp() >= before && event.getTimestamp() <= after);
    }
}
