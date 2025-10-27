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
public class ToolCallEndEventTests {

    @Test
    public void testConstructor() {
        String toolCallId = "call_123";
        
        ToolCallEndEvent event = new ToolCallEndEvent(toolCallId);
        
        assertNotNull("Event should not be null", event);
        assertEquals("Type should be TOOL_CALL_END", "TOOL_CALL_END", event.getType());
        assertEquals("Tool call ID should match", toolCallId, event.getToolCallId());
        assertNotNull("Timestamp should be set", event.getTimestamp());
    }

    @Test
    public void testSerialization() throws IOException {
        ToolCallEndEvent original = new ToolCallEndEvent("call_test");
        
        BytesStreamOutput output = new BytesStreamOutput();
        original.writeTo(output);
        
        StreamInput input = output.bytes().streamInput();
        ToolCallEndEvent deserialized = new ToolCallEndEvent(input);
        
        assertEquals("Type should match", original.getType(), deserialized.getType());
        assertEquals("Tool call ID should match", original.getToolCallId(), deserialized.getToolCallId());
        assertEquals("Timestamp should match", original.getTimestamp(), deserialized.getTimestamp());
    }

    @Test
    public void testToXContent() throws IOException {
        ToolCallEndEvent event = new ToolCallEndEvent("call_xcontent");
        
        String json = event.toJsonString();
        
        assertNotNull("JSON should not be null", json);
        assertTrue("JSON should contain type", json.contains("\"type\":\"TOOL_CALL_END\""));
        assertTrue("JSON should contain toolCallId", json.contains("\"toolCallId\":\"call_xcontent\""));
        assertTrue("JSON should contain timestamp", json.contains("\"timestamp\""));
    }

    @Test
    public void testToJsonString() {
        ToolCallEndEvent event = new ToolCallEndEvent("call_json");
        
        String json = event.toJsonString();
        
        assertNotNull("JSON string should not be null", json);
        assertTrue("JSON should be valid", json.startsWith("{") && json.endsWith("}"));
    }

    @Test
    public void testTimestampGeneration() {
        long before = System.currentTimeMillis();
        ToolCallEndEvent event = new ToolCallEndEvent("call_time");
        long after = System.currentTimeMillis();
        
        assertNotNull("Timestamp should be set", event.getTimestamp());
        assertTrue("Timestamp should be in valid range", 
            event.getTimestamp() >= before && event.getTimestamp() <= after);
    }
}
