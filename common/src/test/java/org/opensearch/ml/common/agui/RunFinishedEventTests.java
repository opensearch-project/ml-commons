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

public class RunFinishedEventTests {

    @Test
    public void testConstructor() {
        String threadId = "thread_123";
        String runId = "run_456";

        RunFinishedEvent event = new RunFinishedEvent(threadId, runId, null);

        assertNotNull("Event should not be null", event);
        assertEquals("Type should be RUN_FINISHED", "RUN_FINISHED", event.getType());
        assertEquals("Thread ID should match", threadId, event.getThreadId());
        assertEquals("Run ID should match", runId, event.getRunId());
        assertNotNull("Timestamp should be set", event.getTimestamp());
    }

    @Test
    public void testSerialization() throws IOException {
        RunFinishedEvent original = new RunFinishedEvent("thread_test", "run_test", null);

        BytesStreamOutput output = new BytesStreamOutput();
        original.writeTo(output);

        StreamInput input = output.bytes().streamInput();
        RunFinishedEvent deserialized = new RunFinishedEvent(input);

        assertEquals("Type should match", original.getType(), deserialized.getType());
        assertEquals("Thread ID should match", original.getThreadId(), deserialized.getThreadId());
        assertEquals("Run ID should match", original.getRunId(), deserialized.getRunId());
        assertEquals("Timestamp should match", original.getTimestamp(), deserialized.getTimestamp());
    }

    @Test
    public void testToXContent() throws IOException {
        RunFinishedEvent event = new RunFinishedEvent("thread_xcontent", "run_xcontent", null);

        String json = event.toJsonString();

        assertNotNull("JSON should not be null", json);
        assertTrue("JSON should contain type", json.contains("\"type\":\"RUN_FINISHED\""));
        assertTrue("JSON should contain threadId", json.contains("\"threadId\":\"thread_xcontent\""));
        assertTrue("JSON should contain runId", json.contains("\"runId\":\"run_xcontent\""));
        assertTrue("JSON should contain timestamp", json.contains("\"timestamp\""));
    }

    @Test
    public void testToJsonString() {
        RunFinishedEvent event = new RunFinishedEvent("thread_json", "run_json", null);

        String json = event.toJsonString();

        assertNotNull("JSON string should not be null", json);
        assertTrue("JSON should be valid", json.startsWith("{") && json.endsWith("}"));
    }

    @Test
    public void testTimestampGeneration() {
        long before = System.currentTimeMillis();
        RunFinishedEvent event = new RunFinishedEvent("thread_time", "run_time", null);
        long after = System.currentTimeMillis();

        assertNotNull("Timestamp should be set", event.getTimestamp());
        assertTrue("Timestamp should be in valid range", event.getTimestamp() >= before && event.getTimestamp() <= after);
    }
}
