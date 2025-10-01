/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.memorycontainer.memory;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.opensearch.ml.common.TestHelper.createTestContent;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.SESSION_ID_FIELD;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.opensearch.action.ActionRequestValidationException;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.ml.common.memorycontainer.WorkingMemoryType;

public class MLAddMemoriesRequestTest {

    private MLAddMemoriesInput testInput;
    private MLAddMemoriesRequest request;

    @Before
    public void setUp() {
        MessageInput message = MessageInput.builder().role("user").content(createTestContent("Test message content")).build();
        testInput = MLAddMemoriesInput
            .builder()
            .messages(Arrays.asList(message))
            .memoryContainerId("container-123")
            .namespace(Map.of(SESSION_ID_FIELD, "session-456", "agent_id", "agent-789"))
            .infer(true)
            .build();

        request = MLAddMemoriesRequest.builder().mlAddMemoryInput(testInput).build();
    }

    @Test
    public void testBuilder() {
        assertNotNull(request);
        assertNotNull(request.getMlAddMemoryInput());
        assertEquals(testInput, request.getMlAddMemoryInput());
        assertEquals("container-123", request.getMlAddMemoryInput().getMemoryContainerId());
    }

    @Test
    public void testStreamInputOutput() throws IOException {
        BytesStreamOutput out = new BytesStreamOutput();
        request.writeTo(out);

        StreamInput in = out.bytes().streamInput();
        MLAddMemoriesRequest deserialized = new MLAddMemoriesRequest(in);

        assertNotNull(deserialized.getMlAddMemoryInput());
        assertEquals(request.getMlAddMemoryInput().getMemoryContainerId(), deserialized.getMlAddMemoryInput().getMemoryContainerId());
        assertEquals(request.getMlAddMemoryInput().getSessionId(), deserialized.getMlAddMemoryInput().getSessionId());
        assertEquals(request.getMlAddMemoryInput().getAgentId(), deserialized.getMlAddMemoryInput().getAgentId());
        assertEquals(request.getMlAddMemoryInput().isInfer(), deserialized.getMlAddMemoryInput().isInfer());
        assertEquals(request.getMlAddMemoryInput().getMessages().size(), deserialized.getMlAddMemoryInput().getMessages().size());
    }

    @Test
    public void testValidateSuccess() {
        ActionRequestValidationException exception = request.validate();
        assertNull(exception);
    }

    @Test
    public void testValidateWithNullInput() {
        MLAddMemoriesRequest invalidRequest = MLAddMemoriesRequest.builder().mlAddMemoryInput(null).build();

        ActionRequestValidationException exception = invalidRequest.validate();
        assertNotNull(exception);
        assertEquals(1, exception.validationErrors().size());
        assertTrue(exception.validationErrors().get(0).contains("ML add memory input can't be null"));
    }

    @Test
    public void testToString() {
        String toString = request.toString();
        assertNotNull(toString);
        assertTrue(toString.contains("MLAddMemoriesRequest"));
        assertTrue(toString.contains("mlAddMemoryInput"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testWithEmptyMessages() {
        // Empty messages list is not allowed - should throw exception
        MLAddMemoriesInput
            .builder()
            .messages(Collections.emptyList())
            .memoryContainerId("container-empty")
            .memoryType(WorkingMemoryType.CONVERSATIONAL)
            .infer(true)
            .build();
    }

    @Test
    public void testWithMultipleMessages() throws IOException {
        MessageInput msg1 = MessageInput.builder().role("user").content(createTestContent("First message")).build();
        MessageInput msg2 = MessageInput.builder().role("assistant").content(createTestContent("Second message")).build();
        MessageInput msg3 = MessageInput.builder().role("user").content(createTestContent("Third message")).build();

        MLAddMemoriesInput multiInput = MLAddMemoriesInput
            .builder()
            .messages(Arrays.asList(msg1, msg2, msg3))
            .memoryContainerId("container-multi")
            .namespace(Map.of(SESSION_ID_FIELD, "session-multi"))
            .build();

        MLAddMemoriesRequest multiRequest = MLAddMemoriesRequest.builder().mlAddMemoryInput(multiInput).build();

        // Test serialization
        BytesStreamOutput out = new BytesStreamOutput();
        multiRequest.writeTo(out);

        StreamInput in = out.bytes().streamInput();
        MLAddMemoriesRequest deserialized = new MLAddMemoriesRequest(in);

        assertEquals(3, deserialized.getMlAddMemoryInput().getMessages().size());
        assertEquals("First message", deserialized.getMlAddMemoryInput().getMessages().get(0).getContent().get(0).get("text"));
        assertEquals("Second message", deserialized.getMlAddMemoryInput().getMessages().get(1).getContent().get(0).get("text"));
        assertEquals("Third message", deserialized.getMlAddMemoryInput().getMessages().get(2).getContent().get(0).get("text"));
    }

    @Test
    public void testWithMinimalInput() throws IOException {
        MessageInput message = MessageInput.builder().role("user").content(createTestContent("Minimal message")).build();
        MLAddMemoriesInput minimalInput = MLAddMemoriesInput
            .builder()
            .messages(Arrays.asList(message))
            .memoryContainerId("container-minimal")
            .build();

        MLAddMemoriesRequest minimalRequest = MLAddMemoriesRequest.builder().mlAddMemoryInput(minimalInput).build();

        assertNull(minimalRequest.validate());

        // Test serialization
        BytesStreamOutput out = new BytesStreamOutput();
        minimalRequest.writeTo(out);

        StreamInput in = out.bytes().streamInput();
        MLAddMemoriesRequest deserialized = new MLAddMemoriesRequest(in);

        assertEquals("container-minimal", deserialized.getMlAddMemoryInput().getMemoryContainerId());
        assertNull(deserialized.getMlAddMemoryInput().getSessionId());
        assertNull(deserialized.getMlAddMemoryInput().getAgentId());
        assertFalse(deserialized.getMlAddMemoryInput().isInfer()); // Null when not set
    }

    @Test
    public void testWithComplexTags() throws IOException {
        MessageInput message = MessageInput.builder().role("user").content(createTestContent("Tagged message")).build();
        MLAddMemoriesInput taggedInput = MLAddMemoriesInput
            .builder()
            .messages(Arrays.asList(message))
            .memoryContainerId("container-tags")
            .namespace(Map.of(SESSION_ID_FIELD, "session-tags", "agent_id", "agent-tags"))
            .tags(java.util.Map.of("category", "technical", "priority", "high", "timestamp", "2024-01-01"))
            .infer(false)
            .build();

        MLAddMemoriesRequest taggedRequest = MLAddMemoriesRequest.builder().mlAddMemoryInput(taggedInput).build();

        // Test serialization
        BytesStreamOutput out = new BytesStreamOutput();
        taggedRequest.writeTo(out);

        StreamInput in = out.bytes().streamInput();
        MLAddMemoriesRequest deserialized = new MLAddMemoriesRequest(in);

        assertEquals(3, deserialized.getMlAddMemoryInput().getTags().size());
        assertEquals("technical", deserialized.getMlAddMemoryInput().getTags().get("category"));
        assertEquals("high", deserialized.getMlAddMemoryInput().getTags().get("priority"));
        assertEquals("2024-01-01", deserialized.getMlAddMemoryInput().getTags().get("timestamp"));
    }
}
