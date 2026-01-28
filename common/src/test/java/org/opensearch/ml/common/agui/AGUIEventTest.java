/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.agui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.core.common.io.stream.StreamInput;

public class AGUIEventTest {

    @Test
    public void testRunStartedEvent_Constructor() {
        RunStartedEvent event = new RunStartedEvent("thread-123", "run-456");

        assertEquals(RunStartedEvent.TYPE, event.getType());
        assertEquals("thread-123", event.getThreadId());
        assertEquals("run-456", event.getRunId());
        assertNotNull(event.getTimestamp());
    }

    @Test
    public void testRunStartedEvent_ToJsonString() {
        RunStartedEvent event = new RunStartedEvent("thread-123", "run-456");
        String json = event.toJsonString();

        assertNotNull(json);
        assertTrue(json.contains("RUN_STARTED"));
        assertTrue(json.contains("thread-123"));
        assertTrue(json.contains("run-456"));
    }

    @Test
    public void testRunStartedEvent_Serialization() throws IOException {
        RunStartedEvent original = new RunStartedEvent("thread-123", "run-456");

        BytesStreamOutput output = new BytesStreamOutput();
        original.writeTo(output);

        StreamInput input = output.bytes().streamInput();
        RunStartedEvent deserialized = new RunStartedEvent(input);

        assertEquals(original.getType(), deserialized.getType());
        assertEquals(original.getThreadId(), deserialized.getThreadId());
        assertEquals(original.getRunId(), deserialized.getRunId());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testRunFinishedEvent_Constructor() {
        Map<String, Object> result = new HashMap<>();
        result.put("status", "success");

        RunFinishedEvent event = new RunFinishedEvent("thread-123", "run-456", result);

        assertEquals(RunFinishedEvent.TYPE, event.getType());
        assertEquals("thread-123", event.getThreadId());
        assertEquals("run-456", event.getRunId());
        assertNotNull(event.getResult());
        assertEquals("success", ((Map<String, Object>) event.getResult()).get("status"));
    }

    @Test
    public void testRunFinishedEvent_NullResult() {
        RunFinishedEvent event = new RunFinishedEvent("thread-123", "run-456", null);

        assertEquals(RunFinishedEvent.TYPE, event.getType());
        assertEquals("thread-123", event.getThreadId());
        assertEquals("run-456", event.getRunId());
        assertEquals(null, event.getResult());
    }

    @Test
    public void testRunFinishedEvent_ToJsonString() {
        Map<String, Object> result = new HashMap<>();
        result.put("status", "success");

        RunFinishedEvent event = new RunFinishedEvent("thread-123", "run-456", result);
        String json = event.toJsonString();

        assertNotNull(json);
        assertTrue(json.contains("RUN_FINISHED"));
        assertTrue(json.contains("thread-123"));
        assertTrue(json.contains("run-456"));
    }

    @Test
    public void testRunFinishedEvent_Serialization() throws IOException {
        Map<String, Object> result = new HashMap<>();
        result.put("status", "success");

        RunFinishedEvent original = new RunFinishedEvent("thread-123", "run-456", result);

        BytesStreamOutput output = new BytesStreamOutput();
        original.writeTo(output);

        StreamInput input = output.bytes().streamInput();
        RunFinishedEvent deserialized = new RunFinishedEvent(input);

        assertEquals(original.getType(), deserialized.getType());
        assertEquals(original.getThreadId(), deserialized.getThreadId());
        assertEquals(original.getRunId(), deserialized.getRunId());
    }

    @Test
    public void testRunErrorEvent_Constructor() {
        RunErrorEvent event = new RunErrorEvent("Error occurred", "ERROR_CODE");

        assertEquals(RunErrorEvent.TYPE, event.getType());
        assertEquals("Error occurred", event.getMessage());
        assertEquals("ERROR_CODE", event.getCode());
        assertNotNull(event.getTimestamp());
    }

    @Test
    public void testRunErrorEvent_NullMessage() {
        RunErrorEvent event = new RunErrorEvent(null, "ERROR_CODE");

        assertEquals("", event.getMessage());  // Should default to empty string
        assertEquals("ERROR_CODE", event.getCode());
    }

    @Test
    public void testRunErrorEvent_NullCode() {
        RunErrorEvent event = new RunErrorEvent("Error occurred", null);

        assertEquals("Error occurred", event.getMessage());
        assertEquals(null, event.getCode());
    }

    @Test
    public void testRunErrorEvent_ToJsonString() {
        RunErrorEvent event = new RunErrorEvent("Error occurred", "ERROR_CODE");
        String json = event.toJsonString();

        assertNotNull(json);
        assertTrue(json.contains("RUN_ERROR"));
        assertTrue(json.contains("Error occurred"));
        assertTrue(json.contains("ERROR_CODE"));
    }

    @Test
    public void testRunErrorEvent_Serialization() throws IOException {
        RunErrorEvent original = new RunErrorEvent("Error occurred", "ERROR_CODE");

        BytesStreamOutput output = new BytesStreamOutput();
        original.writeTo(output);

        StreamInput input = output.bytes().streamInput();
        RunErrorEvent deserialized = new RunErrorEvent(input);

        assertEquals(original.getType(), deserialized.getType());
        assertEquals(original.getMessage(), deserialized.getMessage());
        assertEquals(original.getCode(), deserialized.getCode());
    }

    @Test
    public void testTextMessageStartEvent_Constructor() {
        TextMessageStartEvent event = new TextMessageStartEvent("msg-123", "assistant");

        assertEquals(TextMessageStartEvent.TYPE, event.getType());
        assertEquals("msg-123", event.getMessageId());
        assertEquals("assistant", event.getRole());
        assertNotNull(event.getTimestamp());
    }

    @Test
    public void testTextMessageStartEvent_NullRole() {
        TextMessageStartEvent event = new TextMessageStartEvent("msg-123", null);

        assertEquals("assistant", event.getRole());  // Should default to "assistant"
    }

    @Test
    public void testTextMessageStartEvent_ToJsonString() {
        TextMessageStartEvent event = new TextMessageStartEvent("msg-123", "assistant");
        String json = event.toJsonString();

        assertNotNull(json);
        assertTrue(json.contains("TEXT_MESSAGE_START"));
        assertTrue(json.contains("msg-123"));
        assertTrue(json.contains("assistant"));
    }

    @Test
    public void testTextMessageStartEvent_Serialization() throws IOException {
        TextMessageStartEvent original = new TextMessageStartEvent("msg-123", "assistant");

        BytesStreamOutput output = new BytesStreamOutput();
        original.writeTo(output);

        StreamInput input = output.bytes().streamInput();
        TextMessageStartEvent deserialized = new TextMessageStartEvent(input);

        assertEquals(original.getType(), deserialized.getType());
        assertEquals(original.getMessageId(), deserialized.getMessageId());
        assertEquals(original.getRole(), deserialized.getRole());
    }

    @Test
    public void testTextMessageContentEvent_Constructor() {
        TextMessageContentEvent event = new TextMessageContentEvent("msg-123", "Hello world");

        assertEquals(TextMessageContentEvent.TYPE, event.getType());
        assertEquals("msg-123", event.getMessageId());
        assertEquals("Hello world", event.getDelta());
        assertNotNull(event.getTimestamp());
    }

    @Test
    public void testTextMessageContentEvent_NullDelta() {
        TextMessageContentEvent event = new TextMessageContentEvent("msg-123", null);

        assertEquals("", event.getDelta());  // Should default to empty string
    }

    @Test
    public void testTextMessageContentEvent_ToJsonString() {
        TextMessageContentEvent event = new TextMessageContentEvent("msg-123", "Hello world");
        String json = event.toJsonString();

        assertNotNull(json);
        assertTrue(json.contains("TEXT_MESSAGE_CONTENT"));
        assertTrue(json.contains("msg-123"));
        assertTrue(json.contains("Hello world"));
    }

    @Test
    public void testTextMessageContentEvent_Serialization() throws IOException {
        TextMessageContentEvent original = new TextMessageContentEvent("msg-123", "Hello world");

        BytesStreamOutput output = new BytesStreamOutput();
        original.writeTo(output);

        StreamInput input = output.bytes().streamInput();
        TextMessageContentEvent deserialized = new TextMessageContentEvent(input);

        assertEquals(original.getType(), deserialized.getType());
        assertEquals(original.getMessageId(), deserialized.getMessageId());
        assertEquals(original.getDelta(), deserialized.getDelta());
    }

    @Test
    public void testTextMessageEndEvent_Constructor() {
        TextMessageEndEvent event = new TextMessageEndEvent("msg-123");

        assertEquals(TextMessageEndEvent.TYPE, event.getType());
        assertEquals("msg-123", event.getMessageId());
        assertNotNull(event.getTimestamp());
    }

    @Test
    public void testTextMessageEndEvent_ToJsonString() {
        TextMessageEndEvent event = new TextMessageEndEvent("msg-123");
        String json = event.toJsonString();

        assertNotNull(json);
        assertTrue(json.contains("TEXT_MESSAGE_END"));
        assertTrue(json.contains("msg-123"));
    }

    @Test
    public void testTextMessageEndEvent_Serialization() throws IOException {
        TextMessageEndEvent original = new TextMessageEndEvent("msg-123");

        BytesStreamOutput output = new BytesStreamOutput();
        original.writeTo(output);

        StreamInput input = output.bytes().streamInput();
        TextMessageEndEvent deserialized = new TextMessageEndEvent(input);

        assertEquals(original.getType(), deserialized.getType());
        assertEquals(original.getMessageId(), deserialized.getMessageId());
    }

    @Test
    public void testToolCallStartEvent_Constructor() {
        ToolCallStartEvent event = new ToolCallStartEvent("call-456", "get_weather", "msg-123");

        assertEquals(ToolCallStartEvent.TYPE, event.getType());
        assertEquals("call-456", event.getToolCallId());
        assertEquals("get_weather", event.getToolCallName());
        assertEquals("msg-123", event.getParentMessageId());
        assertNotNull(event.getTimestamp());
    }

    @Test
    public void testToolCallStartEvent_ToJsonString() {
        ToolCallStartEvent event = new ToolCallStartEvent("call-456", "get_weather", "msg-123");
        String json = event.toJsonString();

        assertNotNull(json);
        assertTrue(json.contains("TOOL_CALL_START"));
        assertTrue(json.contains("call-456"));
        assertTrue(json.contains("get_weather"));
        assertTrue(json.contains("msg-123"));
    }

    @Test
    public void testToolCallStartEvent_Serialization() throws IOException {
        ToolCallStartEvent original = new ToolCallStartEvent("call-456", "get_weather", "msg-123");

        BytesStreamOutput output = new BytesStreamOutput();
        original.writeTo(output);

        StreamInput input = output.bytes().streamInput();
        ToolCallStartEvent deserialized = new ToolCallStartEvent(input);

        assertEquals(original.getType(), deserialized.getType());
        assertEquals(original.getToolCallId(), deserialized.getToolCallId());
        assertEquals(original.getToolCallName(), deserialized.getToolCallName());
        assertEquals(original.getParentMessageId(), deserialized.getParentMessageId());
    }

    @Test
    public void testToolCallArgsEvent_Constructor() {
        ToolCallArgsEvent event = new ToolCallArgsEvent("call-456", "{\"location\":\"NYC\"}");

        assertEquals(ToolCallArgsEvent.TYPE, event.getType());
        assertEquals("call-456", event.getToolCallId());
        assertEquals("{\"location\":\"NYC\"}", event.getDelta());
        assertNotNull(event.getTimestamp());
    }

    @Test
    public void testToolCallArgsEvent_ToJsonString() {
        ToolCallArgsEvent event = new ToolCallArgsEvent("call-456", "{\"location\":\"NYC\"}");
        String json = event.toJsonString();

        assertNotNull(json);
        assertTrue(json.contains("TOOL_CALL_ARGS"));
        assertTrue(json.contains("call-456"));
    }

    @Test
    public void testToolCallArgsEvent_Serialization() throws IOException {
        ToolCallArgsEvent original = new ToolCallArgsEvent("call-456", "{\"location\":\"NYC\"}");

        BytesStreamOutput output = new BytesStreamOutput();
        original.writeTo(output);

        StreamInput input = output.bytes().streamInput();
        ToolCallArgsEvent deserialized = new ToolCallArgsEvent(input);

        assertEquals(original.getType(), deserialized.getType());
        assertEquals(original.getToolCallId(), deserialized.getToolCallId());
        assertEquals(original.getDelta(), deserialized.getDelta());
    }

    @Test
    public void testMessagesSnapshotEvent_Constructor() {
        List<Object> messages = new ArrayList<>();
        Map<String, String> message1 = new HashMap<>();
        message1.put("role", "user");
        message1.put("content", "Hello");
        messages.add(message1);

        MessagesSnapshotEvent event = new MessagesSnapshotEvent(messages);

        assertEquals(MessagesSnapshotEvent.TYPE, event.getType());
        assertNotNull(event.getMessages());
        assertEquals(1, event.getMessages().size());
        assertNotNull(event.getTimestamp());
    }

    @Test
    public void testMessagesSnapshotEvent_ToJsonString() {
        List<Object> messages = new ArrayList<>();
        Map<String, String> message1 = new HashMap<>();
        message1.put("role", "user");
        message1.put("content", "Hello");
        messages.add(message1);

        MessagesSnapshotEvent event = new MessagesSnapshotEvent(messages);
        String json = event.toJsonString();

        assertNotNull(json);
        assertTrue(json.contains("MESSAGES_SNAPSHOT"));
    }

    @Test
    public void testMessagesSnapshotEvent_Serialization() throws IOException {
        List<Object> messages = new ArrayList<>();
        Map<String, String> message1 = new HashMap<>();
        message1.put("role", "user");
        message1.put("content", "Hello");
        messages.add(message1);

        MessagesSnapshotEvent original = new MessagesSnapshotEvent(messages);

        BytesStreamOutput output = new BytesStreamOutput();
        original.writeTo(output);

        StreamInput input = output.bytes().streamInput();
        MessagesSnapshotEvent deserialized = new MessagesSnapshotEvent(input);

        assertEquals(original.getType(), deserialized.getType());
        assertNotNull(deserialized.getMessages());
        assertEquals(original.getMessages().size(), deserialized.getMessages().size());
    }
}
