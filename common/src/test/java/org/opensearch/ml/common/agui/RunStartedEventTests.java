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
public class RunStartedEventTests {

    @Test
    public void testConstructor() {
        String threadId = "thread_123";
        String runId = "run_456";
        
        RunStartedEvent event = new RunStartedEvent(threadId, runId);
        
        assertNotNull("Event should not be null", event);
        assertEquals("Type should be RUN_STARTED", "RUN_STARTED", event.getType());
        assertEquals("Thread ID should match", threadId, event.getThreadId());
        assertEquals("Run ID should match", runId, event.getRunId());
        assertNotNull("Timestamp should be set", event.getTimestamp());
        assertTrue("Timestamp should be recent", event.getTimestamp() > 0);
    }

    @Test
    public void testSerialization() throws IOException {
        RunStartedEvent original = new RunStartedEvent("thread_test", "run_test");
        
        BytesStreamOutput output = new BytesStreamOutput();
        original.writeTo(output);
        
        StreamInput input = output.bytes().streamInput();
        RunStartedEvent deserialized = new RunStartedEvent(input);
        
        assertEquals("Type should match", original.getType(), deserialized.getType());
        assertEquals("Thread ID should match", original.getThreadId(), deserialized.getThreadId());
        assertEquals("Run ID should match", original.getRunId(), deserialized.getRunId());
        assertEquals("Timestamp should match", original.getTimestamp(), deserialized.getTimestamp());
    }

    @Test
    public void testToXContent() throws IOException {
        RunStartedEvent event = new RunStartedEvent("thread_xcontent", "run_xcontent");
        
        String json = event.toJsonString();
        
        assertNotNull("JSON should not be null", json);
        assertTrue("JSON should contain type", json.contains("\"type\":\"RUN_STARTED\""));
        assertTrue("JSON should contain threadId", json.contains("\"threadId\":\"thread_xcontent\""));
        assertTrue("JSON should contain runId", json.contains("\"runId\":\"run_xcontent\""));
        assertTrue("JSON should contain timestamp", json.contains("\"timestamp\""));
    }

    @Test
    public void testToJsonString() {
        RunStartedEvent event = new RunStartedEvent("thread_json", "run_json");
        
        String json = event.toJsonString();
        
        assertNotNull("JSON string should not be null", json);
        assertTrue("JSON should be valid", json.startsWith("{") && json.endsWith("}"));
        assertTrue("JSON should contain all fields", 
            json.contains("type") && json.contains("threadId") && json.contains("runId"));
    }

    @Test
    public void testTimestampGeneration() throws InterruptedException {
        long before = System.currentTimeMillis();
        Thread.sleep(10); // Small delay to ensure different timestamps
        
        RunStartedEvent event = new RunStartedEvent("thread_time", "run_time");
        
        Thread.sleep(10);
        long after = System.currentTimeMillis();
        
        assertNotNull("Timestamp should be set", event.getTimestamp());
        assertTrue("Timestamp should be after 'before'", event.getTimestamp() >= before);
        assertTrue("Timestamp should be before 'after'", event.getTimestamp() <= after);
    }
}
