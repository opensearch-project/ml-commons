/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.agui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;

import org.junit.Test;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.core.common.io.stream.StreamInput;
public class ToolCallStartEventTests {

    @Test
    public void testConstructor() {
        String toolCallId = "call_123";
        String toolCallName = "search_web";
        
        ToolCallStartEvent event = new ToolCallStartEvent(toolCallId, toolCallName, null);
        
        assertNotNull("Event should not be null", event);
        assertEquals("Type should be TOOL_CALL_START", "TOOL_CALL_START", event.getType());
        assertEquals("Tool call ID should match", toolCallId, event.getToolCallId());
        assertEquals("Tool call name should match", toolCallName, event.getToolCallName());
        assertNull("Parent message ID should be null", event.getParentMessageId());
        assertNotNull("Timestamp should be set", event.getTimestamp());
    }

    @Test
    public void testConstructorWithParentMessageId() {
        String toolCallId = "call_123";
        String toolCallName = "search_web";
        String parentMessageId = "msg_456";
        
        ToolCallStartEvent event = new ToolCallStartEvent(toolCallId, toolCallName, parentMessageId);
        
        assertNotNull("Event should not be null", event);
        assertEquals("Tool call ID should match", toolCallId, event.getToolCallId());
        assertEquals("Tool call name should match", toolCallName, event.getToolCallName());
        assertEquals("Parent message ID should match", parentMessageId, event.getParentMessageId());
    }

    @Test
    public void testSerialization() throws IOException {
        ToolCallStartEvent original = new ToolCallStartEvent("call_test", "test_tool", "msg_parent");
        
        BytesStreamOutput output = new BytesStreamOutput();
        original.writeTo(output);
        
        StreamInput input = output.bytes().streamInput();
        ToolCallStartEvent deserialized = new ToolCallStartEvent(input);
        
        assertEquals("Type should match", original.getType(), deserialized.getType());
        assertEquals("Tool call ID should match", original.getToolCallId(), deserialized.getToolCallId());
        assertEquals("Tool call name should match", original.getToolCallName(), deserialized.getToolCallName());
        assertEquals("Parent message ID should match", original.getParentMessageId(), deserialized.getParentMessageId());
        assertEquals("Timestamp should match", original.getTimestamp(), deserialized.getTimestamp());
    }

    @Test
    public void testToXContent() throws IOException {
        ToolCallStartEvent event = new ToolCallStartEvent("call_xcontent", "xcontent_tool", "msg_parent");
        
        String json = event.toJsonString();
        
        assertNotNull("JSON should not be null", json);
        assertTrue("JSON should contain type", json.contains("\"type\":\"TOOL_CALL_START\""));
        assertTrue("JSON should contain toolCallId", json.contains("\"toolCallId\":\"call_xcontent\""));
        assertTrue("JSON should contain toolCallName", json.contains("\"toolCallName\":\"xcontent_tool\""));
        assertTrue("JSON should contain parentMessageId", json.contains("\"parentMessageId\":\"msg_parent\""));
        assertTrue("JSON should contain timestamp", json.contains("\"timestamp\""));
    }

    @Test
    public void testToJsonString() {
        ToolCallStartEvent event = new ToolCallStartEvent("call_json", "json_tool", null);
        
        String json = event.toJsonString();
        
        assertNotNull("JSON string should not be null", json);
        assertTrue("JSON should be valid", json.startsWith("{") && json.endsWith("}"));
    }

    @Test
    public void testTimestampGeneration() {
        long before = System.currentTimeMillis();
        ToolCallStartEvent event = new ToolCallStartEvent("call_time", "time_tool", null);
        long after = System.currentTimeMillis();
        
        assertNotNull("Timestamp should be set", event.getTimestamp());
        assertTrue("Timestamp should be in valid range", 
            event.getTimestamp() >= before && event.getTimestamp() <= after);
    }
}
