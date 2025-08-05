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
        testInput = MLUpdateMemoryInput.builder().text("Updated memory content").build();

        requestNormal = MLUpdateMemoryRequest
            .builder()
            .memoryContainerId("container-123")
            .memoryId("memory-456")
            .mlUpdateMemoryInput(testInput)
            .build();

        requestWithNulls = MLUpdateMemoryRequest.builder().memoryContainerId(null).memoryId(null).mlUpdateMemoryInput(null).build();
    }

    @Test
    public void testBuilderNormal() {
        assertNotNull(requestNormal);
        assertEquals("container-123", requestNormal.getMemoryContainerId());
        assertEquals("memory-456", requestNormal.getMemoryId());
        assertNotNull(requestNormal.getMlUpdateMemoryInput());
        assertEquals("Updated memory content", requestNormal.getMlUpdateMemoryInput().getText());
    }

    @Test
    public void testBuilderWithNullValues() {
        assertNotNull(requestWithNulls);
        assertNull(requestWithNulls.getMemoryContainerId());
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
        assertEquals(requestNormal.getMemoryId(), deserialized.getMemoryId());
        assertNotNull(deserialized.getMlUpdateMemoryInput());
        assertEquals(requestNormal.getMlUpdateMemoryInput().getText(), deserialized.getMlUpdateMemoryInput().getText());
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
            .memoryId("memory-456")
            .mlUpdateMemoryInput(testInput)
            .build();

        ActionRequestValidationException exception = request.validate();
        assertNotNull(exception);
        assertEquals(1, exception.validationErrors().size());
        assertTrue(exception.validationErrors().get(0).contains("Memory container id can't be null"));
    }

    @Test
    public void testValidateWithNullMemoryId() {
        MLUpdateMemoryRequest request = MLUpdateMemoryRequest
            .builder()
            .memoryContainerId("container-123")
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
        assertEquals(3, exception.validationErrors().size());
        assertTrue(exception.validationErrors().get(0).contains("Update memory input can't be null"));
        assertTrue(exception.validationErrors().get(1).contains("Memory container id can't be null"));
        assertTrue(exception.validationErrors().get(2).contains("Memory id can't be null"));
    }

    @Test
    public void testValidateWithTwoNulls() {
        MLUpdateMemoryRequest request = MLUpdateMemoryRequest
            .builder()
            .memoryContainerId(null)
            .memoryId(null)
            .mlUpdateMemoryInput(testInput)
            .build();

        ActionRequestValidationException exception = request.validate();
        assertNotNull(exception);
        assertEquals(2, exception.validationErrors().size());
        assertTrue(exception.validationErrors().get(0).contains("Memory container id can't be null"));
        assertTrue(exception.validationErrors().get(1).contains("Memory id can't be null"));
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
                out.writeString("test-memory");
                testInput.writeTo(out);
            }
        };

        MLUpdateMemoryRequest result = MLUpdateMemoryRequest.fromActionRequest(mockRequest);
        assertNotNull(result);
        assertEquals("test-container", result.getMemoryContainerId());
        assertEquals("test-memory", result.getMemoryId());
        assertNotNull(result.getMlUpdateMemoryInput());
        assertEquals("Updated memory content", result.getMlUpdateMemoryInput().getText());
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
        MLUpdateMemoryInput newInput = MLUpdateMemoryInput.builder().text("New updated text").build();

        requestNormal.setMlUpdateMemoryInput(newInput);
        assertEquals("New updated text", requestNormal.getMlUpdateMemoryInput().getText());
    }

    @Test
    public void testSpecialCharacters() throws IOException {
        MLUpdateMemoryInput specialInput = MLUpdateMemoryInput.builder().text("Text with\n\ttabs and \"quotes\" and unicode 🚀✨").build();

        MLUpdateMemoryRequest specialRequest = MLUpdateMemoryRequest
            .builder()
            .memoryContainerId("container-with-special-chars-🌟")
            .memoryId("memory-with-unicode-💫")
            .mlUpdateMemoryInput(specialInput)
            .build();

        // Test serialization round trip
        BytesStreamOutput out = new BytesStreamOutput();
        specialRequest.writeTo(out);
        StreamInput in = out.bytes().streamInput();
        MLUpdateMemoryRequest deserialized = new MLUpdateMemoryRequest(in);

        assertEquals(specialRequest.getMemoryContainerId(), deserialized.getMemoryContainerId());
        assertEquals(specialRequest.getMemoryId(), deserialized.getMemoryId());
        assertEquals(specialRequest.getMlUpdateMemoryInput().getText(), deserialized.getMlUpdateMemoryInput().getText());
    }

    @Test
    public void testEmptyStrings() {
        MLUpdateMemoryInput emptyInput = MLUpdateMemoryInput
            .builder()
            .text("Valid text") // Text can't be empty as per MLUpdateMemoryInput validation
            .build();

        MLUpdateMemoryRequest emptyStringRequest = MLUpdateMemoryRequest
            .builder()
            .memoryContainerId("")
            .memoryId("")
            .mlUpdateMemoryInput(emptyInput)
            .build();

        assertNotNull(emptyStringRequest);
        assertEquals("", emptyStringRequest.getMemoryContainerId());
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

        MLUpdateMemoryInput longInput = MLUpdateMemoryInput.builder().text(longText.toString().trim()).build();

        MLUpdateMemoryRequest longRequest = MLUpdateMemoryRequest
            .builder()
            .memoryContainerId(longId.toString())
            .memoryId(longId.toString() + "-memory")
            .mlUpdateMemoryInput(longInput)
            .build();

        // Test serialization
        BytesStreamOutput out = new BytesStreamOutput();
        longRequest.writeTo(out);
        StreamInput in = out.bytes().streamInput();
        MLUpdateMemoryRequest deserialized = new MLUpdateMemoryRequest(in);

        assertEquals(longRequest.getMemoryContainerId(), deserialized.getMemoryContainerId());
        assertEquals(longRequest.getMemoryId(), deserialized.getMemoryId());
        assertEquals(longRequest.getMlUpdateMemoryInput().getText(), deserialized.getMlUpdateMemoryInput().getText());
    }

    @Test
    public void testMultipleInputUpdates() {
        MLUpdateMemoryInput input1 = MLUpdateMemoryInput.builder().text("First text").build();
        MLUpdateMemoryInput input2 = MLUpdateMemoryInput.builder().text("Second text").build();
        MLUpdateMemoryInput input3 = MLUpdateMemoryInput.builder().text("Third text").build();

        MLUpdateMemoryRequest request = MLUpdateMemoryRequest
            .builder()
            .memoryContainerId("container-123")
            .memoryId("memory-456")
            .mlUpdateMemoryInput(input1)
            .build();

        assertEquals("First text", request.getMlUpdateMemoryInput().getText());

        request.setMlUpdateMemoryInput(input2);
        assertEquals("Second text", request.getMlUpdateMemoryInput().getText());

        request.setMlUpdateMemoryInput(input3);
        assertEquals("Third text", request.getMlUpdateMemoryInput().getText());
    }

    @Test
    public void testValidationOrderWithMultipleNulls() {
        // Test to ensure validation errors are added in correct order
        MLUpdateMemoryRequest request = MLUpdateMemoryRequest
            .builder()
            .memoryContainerId("container-123")
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
