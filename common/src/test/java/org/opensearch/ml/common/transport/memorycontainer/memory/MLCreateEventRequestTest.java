/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.memorycontainer.memory;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;

import org.junit.Before;
import org.junit.Test;
import org.opensearch.action.ActionRequestValidationException;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.core.common.io.stream.StreamInput;

public class MLCreateEventRequestTest {

    private MLCreateEventInput testInput;
    private MLCreateEventRequest request;

    @Before
    public void setUp() {
        MessageInput message = new MessageInput("user", "Test message content");
        testInput = MLCreateEventInput
            .builder()
            .messages(Arrays.asList(message))
            .memoryContainerId("container-123")
            .sessionId("session-456")
            .agentId("agent-789")
            .infer(true)
            .build();

        request = MLCreateEventRequest.builder().mlCreateEventInput(testInput).build();
    }

    @Test
    public void testBuilder() {
        assertNotNull(request);
        assertNotNull(request.getMlCreateEventInput());
        assertEquals(testInput, request.getMlCreateEventInput());
        assertEquals("container-123", request.getMlCreateEventInput().getMemoryContainerId());
    }

    @Test
    public void testStreamInputOutput() throws IOException {
        BytesStreamOutput out = new BytesStreamOutput();
        request.writeTo(out);

        StreamInput in = out.bytes().streamInput();
        MLCreateEventRequest deserialized = new MLCreateEventRequest(in);

        assertNotNull(deserialized.getMlCreateEventInput());
        assertEquals(request.getMlCreateEventInput().getMemoryContainerId(), deserialized.getMlCreateEventInput().getMemoryContainerId());
        assertEquals(request.getMlCreateEventInput().getSessionId(), deserialized.getMlCreateEventInput().getSessionId());
        assertEquals(request.getMlCreateEventInput().getAgentId(), deserialized.getMlCreateEventInput().getAgentId());
        assertEquals(request.getMlCreateEventInput().getInfer(), deserialized.getMlCreateEventInput().getInfer());
        assertEquals(request.getMlCreateEventInput().getMessages().size(), deserialized.getMlCreateEventInput().getMessages().size());
    }

    @Test
    public void testValidateSuccess() {
        ActionRequestValidationException exception = request.validate();
        assertNull(exception);
    }

    @Test
    public void testValidateWithNullInput() {
        MLCreateEventRequest invalidRequest = MLCreateEventRequest.builder().mlCreateEventInput(null).build();

        ActionRequestValidationException exception = invalidRequest.validate();
        assertNotNull(exception);
        assertEquals(1, exception.validationErrors().size());
        assertTrue(exception.validationErrors().get(0).contains("ML create event input can't be null"));
    }

    @Test
    public void testToString() {
        String toString = request.toString();
        assertNotNull(toString);
        assertTrue(toString.contains("MLCreateEventRequest"));
        assertTrue(toString.contains("mlCreateEventInput"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testWithEmptyMessages() {
        // Empty messages list is not allowed - should throw exception
        MLCreateEventInput.builder().messages(Collections.emptyList()).memoryContainerId("container-empty").build();
    }

    @Test
    public void testWithMultipleMessages() throws IOException {
        MessageInput msg1 = new MessageInput("user", "First message");
        MessageInput msg2 = new MessageInput("assistant", "Second message");
        MessageInput msg3 = new MessageInput("user", "Third message");

        MLCreateEventInput multiInput = MLCreateEventInput
            .builder()
            .messages(Arrays.asList(msg1, msg2, msg3))
            .memoryContainerId("container-multi")
            .sessionId("session-multi")
            .build();

        MLCreateEventRequest multiRequest = MLCreateEventRequest.builder().mlCreateEventInput(multiInput).build();

        // Test serialization
        BytesStreamOutput out = new BytesStreamOutput();
        multiRequest.writeTo(out);

        StreamInput in = out.bytes().streamInput();
        MLCreateEventRequest deserialized = new MLCreateEventRequest(in);

        assertEquals(3, deserialized.getMlCreateEventInput().getMessages().size());
        assertEquals("First message", deserialized.getMlCreateEventInput().getMessages().get(0).getContent());
        assertEquals("Second message", deserialized.getMlCreateEventInput().getMessages().get(1).getContent());
        assertEquals("Third message", deserialized.getMlCreateEventInput().getMessages().get(2).getContent());
    }

    @Test
    public void testWithMinimalInput() throws IOException {
        MessageInput message = new MessageInput(null, "Minimal message");
        MLCreateEventInput minimalInput = MLCreateEventInput
            .builder()
            .messages(Arrays.asList(message))
            .memoryContainerId("container-minimal")
            .build();

        MLCreateEventRequest minimalRequest = MLCreateEventRequest.builder().mlCreateEventInput(minimalInput).build();

        assertNull(minimalRequest.validate());

        // Test serialization
        BytesStreamOutput out = new BytesStreamOutput();
        minimalRequest.writeTo(out);

        StreamInput in = out.bytes().streamInput();
        MLCreateEventRequest deserialized = new MLCreateEventRequest(in);

        assertEquals("container-minimal", deserialized.getMlCreateEventInput().getMemoryContainerId());
        assertNull(deserialized.getMlCreateEventInput().getSessionId());
        assertNull(deserialized.getMlCreateEventInput().getAgentId());
        assertNull(deserialized.getMlCreateEventInput().getInfer()); // Null when not set
    }

    @Test
    public void testWithComplexTags() throws IOException {
        MessageInput message = new MessageInput("user", "Tagged message");
        MLCreateEventInput taggedInput = MLCreateEventInput
            .builder()
            .messages(Arrays.asList(message))
            .memoryContainerId("container-tags")
            .sessionId("session-tags")
            .agentId("agent-tags")
            .tags(java.util.Map.of("category", "technical", "priority", "high", "timestamp", "2024-01-01"))
            .infer(false)
            .build();

        MLCreateEventRequest taggedRequest = MLCreateEventRequest.builder().mlCreateEventInput(taggedInput).build();

        // Test serialization
        BytesStreamOutput out = new BytesStreamOutput();
        taggedRequest.writeTo(out);

        StreamInput in = out.bytes().streamInput();
        MLCreateEventRequest deserialized = new MLCreateEventRequest(in);

        assertEquals(3, deserialized.getMlCreateEventInput().getTags().size());
        assertEquals("technical", deserialized.getMlCreateEventInput().getTags().get("category"));
        assertEquals("high", deserialized.getMlCreateEventInput().getTags().get("priority"));
        assertEquals("2024-01-01", deserialized.getMlCreateEventInput().getTags().get("timestamp"));
    }
}
