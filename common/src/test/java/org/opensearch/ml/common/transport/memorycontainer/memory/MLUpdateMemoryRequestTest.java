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
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.opensearch.action.ActionRequest;
import org.opensearch.action.ActionRequestValidationException;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;

public class MLUpdateMemoryRequestTest {

    private MLUpdateMemoryRequest requestNormal;
    private MLUpdateMemoryRequest requestWithNulls;
    private MLUpdateMemoryInput testInput;

    @Before
    public void setUp() {
        Map<String, Object> updateContent = new HashMap<>();
        updateContent.put("text", "Updated memory content");
        testInput = MLUpdateMemoryInput.builder().updateContent(updateContent).build();

        requestNormal = MLUpdateMemoryRequest
            .builder()
            .memoryContainerId("container-123")
            .memoryType("long-term")
            .memoryId("memory-456")
            .mlUpdateMemoryInput(testInput)
            .build();

        requestWithNulls = MLUpdateMemoryRequest
            .builder()
            .memoryContainerId(null)
            .memoryType(null)
            .memoryId(null)
            .mlUpdateMemoryInput(null)
            .build();
    }

    @Test
    public void testBuilderNormal() {
        assertNotNull(requestNormal);
        assertEquals("container-123", requestNormal.getMemoryContainerId());
        assertEquals("long-term", requestNormal.getMemoryType());
        assertEquals("memory-456", requestNormal.getMemoryId());
        assertNotNull(requestNormal.getMlUpdateMemoryInput());
        assertEquals("Updated memory content", requestNormal.getMlUpdateMemoryInput().getUpdateContent().get("text"));
    }

    @Test
    public void testBuilderWithNullValues() {
        assertNotNull(requestWithNulls);
        assertNull(requestWithNulls.getMemoryContainerId());
        assertNull(requestWithNulls.getMemoryType());
        assertNull(requestWithNulls.getMemoryId());
        assertNull(requestWithNulls.getMlUpdateMemoryInput());
    }

    @Test
    public void testStreamInputOutput() throws IOException {
        BytesStreamOutput out = new BytesStreamOutput();
        requestNormal.writeTo(out);
        StreamInput in = out.bytes().streamInput();
        MLUpdateMemoryRequest deserialized = new MLUpdateMemoryRequest(in);

        assertEquals(requestNormal.getMemoryContainerId(), deserialized.getMemoryContainerId());
        assertEquals(requestNormal.getMemoryType(), deserialized.getMemoryType());
        assertEquals(requestNormal.getMemoryId(), deserialized.getMemoryId());
        assertNotNull(deserialized.getMlUpdateMemoryInput());
        assertEquals(requestNormal.getMlUpdateMemoryInput().getUpdateContent(), deserialized.getMlUpdateMemoryInput().getUpdateContent());
    }

    @Test
    public void testValidateSuccess() {
        ActionRequestValidationException exception = requestNormal.validate();
        assertNull(exception);
    }

    @Test
    public void testValidateWithNullInput() {
        MLUpdateMemoryRequest request = MLUpdateMemoryRequest
            .builder()
            .memoryContainerId("container-123")
            .memoryType("long-term")
            .memoryId("memory-456")
            .mlUpdateMemoryInput(null)
            .build();

        ActionRequestValidationException exception = request.validate();
        assertNotNull(exception);
        assertEquals(1, exception.validationErrors().size());
        assertTrue(exception.validationErrors().get(0).contains("Update memory input can't be null"));
    }

    @Test
    public void testValidateWithNullContainerId() {
        MLUpdateMemoryRequest request = MLUpdateMemoryRequest
            .builder()
            .memoryContainerId(null)
            .memoryType("long-term")
            .memoryId("memory-456")
            .mlUpdateMemoryInput(testInput)
            .build();

        ActionRequestValidationException exception = request.validate();
        assertNotNull(exception);
        assertEquals(1, exception.validationErrors().size());
        assertTrue(exception.validationErrors().get(0).contains("Memory container id can't be null"));
    }

    @Test
    public void testValidateWithNullMemoryType() {
        MLUpdateMemoryRequest request = MLUpdateMemoryRequest
            .builder()
            .memoryContainerId("container-123")
            .memoryType(null)
            .memoryId("memory-456")
            .mlUpdateMemoryInput(testInput)
            .build();

        ActionRequestValidationException exception = request.validate();
        assertNotNull(exception);
        assertEquals(1, exception.validationErrors().size());
        assertTrue(exception.validationErrors().get(0).contains("Memory type can't be null"));
    }

    @Test
    public void testValidateWithNullMemoryId() {
        MLUpdateMemoryRequest request = MLUpdateMemoryRequest
            .builder()
            .memoryContainerId("container-123")
            .memoryType("long-term")
            .memoryId(null)
            .mlUpdateMemoryInput(testInput)
            .build();

        ActionRequestValidationException exception = request.validate();
        assertNotNull(exception);
        assertEquals(1, exception.validationErrors().size());
        assertTrue(exception.validationErrors().get(0).contains("Memory id can't be null"));
    }

    @Test
    public void testValidateWithAllNull() {
        ActionRequestValidationException exception = requestWithNulls.validate();
        assertNotNull(exception);
        assertEquals(4, exception.validationErrors().size());
        assertTrue(exception.validationErrors().get(0).contains("Update memory input can't be null"));
        assertTrue(exception.validationErrors().get(1).contains("Memory container id can't be null"));
        assertTrue(exception.validationErrors().get(2).contains("Memory type can't be null"));
        assertTrue(exception.validationErrors().get(3).contains("Memory id can't be null"));
    }

    @Test
    public void testValidateWithTwoNulls() {
        MLUpdateMemoryRequest request = MLUpdateMemoryRequest
            .builder()
            .memoryContainerId(null)
            .memoryType(null)
            .memoryId(null)
            .mlUpdateMemoryInput(testInput)
            .build();

        ActionRequestValidationException exception = request.validate();
        assertNotNull(exception);
        assertEquals(3, exception.validationErrors().size());
        assertTrue(exception.validationErrors().get(0).contains("Memory container id can't be null"));
        assertTrue(exception.validationErrors().get(1).contains("Memory type can't be null"));
        assertTrue(exception.validationErrors().get(2).contains("Memory id can't be null"));
    }

    @Test
    public void testFromActionRequestSameInstance() {
        MLUpdateMemoryRequest result = MLUpdateMemoryRequest.fromActionRequest(requestNormal);
        assertEquals(requestNormal, result);
    }

    @Test
    public void testFromActionRequestDifferentInstance() throws IOException {
        // Create a mock ActionRequest that's not MLUpdateMemoryRequest
        ActionRequest mockRequest = new ActionRequest() {
            @Override
            public ActionRequestValidationException validate() {
                return null;
            }

            @Override
            public void writeTo(StreamOutput out) throws IOException {
                super.writeTo(out);
                out.writeString("test-container");
                out.writeString("test-type");
                out.writeString("test-memory");
                testInput.writeTo(out);
            }
        };

        MLUpdateMemoryRequest result = MLUpdateMemoryRequest.fromActionRequest(mockRequest);
        assertNotNull(result);
        assertEquals("test-container", result.getMemoryContainerId());
        assertEquals("test-type", result.getMemoryType());
        assertEquals("test-memory", result.getMemoryId());
        assertNotNull(result.getMlUpdateMemoryInput());
        assertEquals("Updated memory content", result.getMlUpdateMemoryInput().getUpdateContent().get("text"));
    }

    @Test(expected = UncheckedIOException.class)
    public void testFromActionRequestIOException() {
        // Create a mock ActionRequest that throws IOException
        ActionRequest mockRequest = new ActionRequest() {
            @Override
            public ActionRequestValidationException validate() {
                return null;
            }

            @Override
            public void writeTo(StreamOutput out) throws IOException {
                throw new IOException("Test exception");
            }
        };

        MLUpdateMemoryRequest.fromActionRequest(mockRequest);
    }

    @Test
    public void testSetMlUpdateMemoryInput() {
        Map<String, Object> newContent = new HashMap<>();
        newContent.put("text", "New updated text");
        MLUpdateMemoryInput newInput = MLUpdateMemoryInput.builder().updateContent(newContent).build();

        requestNormal.setMlUpdateMemoryInput(newInput);
        assertEquals("New updated text", requestNormal.getMlUpdateMemoryInput().getUpdateContent().get("text"));
    }

    @Test
    public void testSpecialCharacters() throws IOException {
        Map<String, Object> specialContent = new HashMap<>();
        specialContent.put("text", "Text with\n\ttabs and \"quotes\" and unicode 🚀✨");
        MLUpdateMemoryInput specialInput = MLUpdateMemoryInput.builder().updateContent(specialContent).build();

        MLUpdateMemoryRequest specialRequest = MLUpdateMemoryRequest
            .builder()
            .memoryContainerId("container-with-special-chars-🌟")
            .memoryType("long-term")
            .memoryId("memory-with-unicode-💫")
            .mlUpdateMemoryInput(specialInput)
            .build();

        // Test serialization round trip
        BytesStreamOutput out = new BytesStreamOutput();
        specialRequest.writeTo(out);
        StreamInput in = out.bytes().streamInput();
        MLUpdateMemoryRequest deserialized = new MLUpdateMemoryRequest(in);

        assertEquals(specialRequest.getMemoryContainerId(), deserialized.getMemoryContainerId());
        assertEquals(specialRequest.getMemoryType(), deserialized.getMemoryType());
        assertEquals(specialRequest.getMemoryId(), deserialized.getMemoryId());
        assertEquals(specialRequest.getMlUpdateMemoryInput().getUpdateContent(), deserialized.getMlUpdateMemoryInput().getUpdateContent());
    }

    @Test
    public void testEmptyStrings() {
        Map<String, Object> validContent = new HashMap<>();
        validContent.put("text", "Valid text"); // Content can't be empty as per MLUpdateMemoryInput validation
        MLUpdateMemoryInput emptyInput = MLUpdateMemoryInput.builder().updateContent(validContent).build();

        MLUpdateMemoryRequest emptyStringRequest = MLUpdateMemoryRequest
            .builder()
            .memoryContainerId("")
            .memoryType("")
            .memoryId("")
            .mlUpdateMemoryInput(emptyInput)
            .build();

        assertNotNull(emptyStringRequest);
        assertEquals("", emptyStringRequest.getMemoryContainerId());
        assertEquals("", emptyStringRequest.getMemoryType());
        assertEquals("", emptyStringRequest.getMemoryId());

        // Empty strings should pass validation (only null check in validate method)
        ActionRequestValidationException exception = emptyStringRequest.validate();
        assertNull(exception);
    }

    @Test
    public void testLongIds() throws IOException {
        // Test with very long IDs
        StringBuilder longId = new StringBuilder();
        for (int i = 0; i < 1000; i++) {
            longId.append("a");
        }

        StringBuilder longText = new StringBuilder();
        for (int i = 0; i < 1000; i++) {
            longText.append("This is sentence ").append(i).append(". ");
        }

        Map<String, Object> longContent = new HashMap<>();
        longContent.put("text", longText.toString().trim());
        MLUpdateMemoryInput longInput = MLUpdateMemoryInput.builder().updateContent(longContent).build();

        MLUpdateMemoryRequest longRequest = MLUpdateMemoryRequest
            .builder()
            .memoryContainerId(longId.toString())
            .memoryType("long-term")
            .memoryId(longId.toString() + "-memory")
            .mlUpdateMemoryInput(longInput)
            .build();

        // Test serialization
        BytesStreamOutput out = new BytesStreamOutput();
        longRequest.writeTo(out);
        StreamInput in = out.bytes().streamInput();
        MLUpdateMemoryRequest deserialized = new MLUpdateMemoryRequest(in);

        assertEquals(longRequest.getMemoryContainerId(), deserialized.getMemoryContainerId());
        assertEquals(longRequest.getMemoryType(), deserialized.getMemoryType());
        assertEquals(longRequest.getMemoryId(), deserialized.getMemoryId());
        assertEquals(longRequest.getMlUpdateMemoryInput().getUpdateContent(), deserialized.getMlUpdateMemoryInput().getUpdateContent());
    }

    @Test
    public void testMultipleInputUpdates() {
        Map<String, Object> content1 = new HashMap<>();
        content1.put("text", "First text");
        MLUpdateMemoryInput input1 = MLUpdateMemoryInput.builder().updateContent(content1).build();

        Map<String, Object> content2 = new HashMap<>();
        content2.put("text", "Second text");
        MLUpdateMemoryInput input2 = MLUpdateMemoryInput.builder().updateContent(content2).build();

        Map<String, Object> content3 = new HashMap<>();
        content3.put("text", "Third text");
        MLUpdateMemoryInput input3 = MLUpdateMemoryInput.builder().updateContent(content3).build();

        MLUpdateMemoryRequest request = MLUpdateMemoryRequest
            .builder()
            .memoryContainerId("container-123")
            .memoryType("long-term")
            .memoryId("memory-456")
            .mlUpdateMemoryInput(input1)
            .build();

        assertEquals("First text", request.getMlUpdateMemoryInput().getUpdateContent().get("text"));

        request.setMlUpdateMemoryInput(input2);
        assertEquals("Second text", request.getMlUpdateMemoryInput().getUpdateContent().get("text"));

        request.setMlUpdateMemoryInput(input3);
        assertEquals("Third text", request.getMlUpdateMemoryInput().getUpdateContent().get("text"));
    }

    @Test
    public void testValidationOrderWithMultipleNulls() {
        // Test to ensure validation errors are added in correct order
        MLUpdateMemoryRequest request = MLUpdateMemoryRequest
            .builder()
            .memoryContainerId("container-123")
            .memoryType("long-term")
            .memoryId(null)
            .mlUpdateMemoryInput(null)
            .build();

        ActionRequestValidationException exception = request.validate();
        assertNotNull(exception);
        assertEquals(2, exception.validationErrors().size());
        // Input validation comes first
        assertTrue(exception.validationErrors().get(0).contains("Update memory input can't be null"));
        assertTrue(exception.validationErrors().get(1).contains("Memory id can't be null"));
    }
}
