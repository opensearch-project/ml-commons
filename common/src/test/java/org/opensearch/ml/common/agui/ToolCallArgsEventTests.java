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
public class ToolCallArgsEventTests {

    @Test
    public void testConstructor() {
        String toolCallId = "call_123";
        String delta = "{\"query\":\"";
        
        ToolCallArgsEvent event = new ToolCallArgsEvent(toolCallId, delta);
        
        assertNotNull("Event should not be null", event);
        assertEquals("Type should be TOOL_CALL_ARGS", "TOOL_CALL_ARGS", event.getType());
        assertEquals("Tool call ID should match", toolCallId, event.getToolCallId());
        assertEquals("Delta should match", delta, event.getDelta());
        assertNotNull("Timestamp should be set", event.getTimestamp());
    }

    @Test
    public void testSerialization() throws IOException {
        ToolCallArgsEvent original = new ToolCallArgsEvent("call_test", "weather\"}");
        
        BytesStreamOutput output = new BytesStreamOutput();
        original.writeTo(output);
        
        StreamInput input = output.bytes().streamInput();
        ToolCallArgsEvent deserialized = new ToolCallArgsEvent(input);
        
        assertEquals("Type should match", original.getType(), deserialized.getType());
        assertEquals("Tool call ID should match", original.getToolCallId(), deserialized.getToolCallId());
        assertEquals("Delta should match", original.getDelta(), deserialized.getDelta());
        assertEquals("Timestamp should match", original.getTimestamp(), deserialized.getTimestamp());
    }

    @Test
    public void testToXContent() throws IOException {
        ToolCallArgsEvent event = new ToolCallArgsEvent("call_xcontent", "{\"param\":\"value\"}");
        
        String json = event.toJsonString();
        
        assertNotNull("JSON should not be null", json);
        assertTrue("JSON should contain type", json.contains("\"type\":\"TOOL_CALL_ARGS\""));
        assertTrue("JSON should contain toolCallId", json.contains("\"toolCallId\":\"call_xcontent\""));
        assertTrue("JSON should contain delta", json.contains("\"delta\""));
        assertTrue("JSON should contain timestamp", json.contains("\"timestamp\""));
    }

    @Test
    public void testToJsonString() {
        ToolCallArgsEvent event = new ToolCallArgsEvent("call_json", "args");
        
        String json = event.toJsonString();
        
        assertNotNull("JSON string should not be null", json);
        assertTrue("JSON should be valid", json.startsWith("{") && json.endsWith("}"));
    }

    @Test
    public void testTimestampGeneration() {
        long before = System.currentTimeMillis();
        ToolCallArgsEvent event = new ToolCallArgsEvent("call_time", "delta");
        long after = System.currentTimeMillis();
        
        assertNotNull("Timestamp should be set", event.getTimestamp());
        assertTrue("Timestamp should be in valid range", 
            event.getTimestamp() >= before && event.getTimestamp() <= after);
    }

    @Test
    public void testEmptyDelta() {
        ToolCallArgsEvent event = new ToolCallArgsEvent("call_empty", "");
        
        assertEquals("Empty delta should be preserved", "", event.getDelta());
        
        String json = event.toJsonString();
        assertTrue("JSON should contain empty delta", json.contains("\"delta\":\"\""));
    }

    @Test
    public void testJsonFragmentDelta() {
        String jsonFragment = "{\"query\":\"weather\",\"location\":\"";
        ToolCallArgsEvent event = new ToolCallArgsEvent("call_fragment", jsonFragment);
        
        assertEquals("JSON fragment should be preserved", jsonFragment, event.getDelta());
    }
}
