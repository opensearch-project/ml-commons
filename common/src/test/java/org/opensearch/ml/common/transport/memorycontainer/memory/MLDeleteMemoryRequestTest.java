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

public class MLDeleteMemoryRequestTest {

    private MLDeleteMemoryRequest requestNormal;
    private MLDeleteMemoryRequest requestEmpty;

    @Before
    public void setUp() {
        requestNormal = MLDeleteMemoryRequest.builder().memoryContainerId("container-123").memoryId("memory-456").build();

        requestEmpty = MLDeleteMemoryRequest.builder().memoryContainerId(null).memoryId(null).build();
    }

    @Test
    public void testBuilderNormal() {
        assertNotNull(requestNormal);
        assertEquals("container-123", requestNormal.getMemoryContainerId());
        assertEquals("memory-456", requestNormal.getMemoryId());
    }

    @Test
    public void testBuilderWithNullValues() {
        assertNotNull(requestEmpty);
        assertNull(requestEmpty.getMemoryContainerId());
        assertNull(requestEmpty.getMemoryId());
    }

    @Test
    public void testStreamInputOutput() throws IOException {
        BytesStreamOutput out = new BytesStreamOutput();
        requestNormal.writeTo(out);
        StreamInput in = out.bytes().streamInput();
        MLDeleteMemoryRequest deserialized = new MLDeleteMemoryRequest(in);

        assertEquals(requestNormal.getMemoryContainerId(), deserialized.getMemoryContainerId());
        assertEquals(requestNormal.getMemoryId(), deserialized.getMemoryId());
    }

    @Test
    public void testValidateSuccess() {
        ActionRequestValidationException exception = requestNormal.validate();
        assertNull(exception);
    }

    @Test
    public void testValidateWithNullContainerId() {
        MLDeleteMemoryRequest request = MLDeleteMemoryRequest.builder().memoryContainerId(null).memoryId("memory-123").build();

        ActionRequestValidationException exception = request.validate();
        assertNotNull(exception);
        assertEquals(1, exception.validationErrors().size());
        assertTrue(exception.validationErrors().get(0).contains("Memory container id can't be null"));
    }

    @Test
    public void testValidateWithNullMemoryId() {
        MLDeleteMemoryRequest request = MLDeleteMemoryRequest.builder().memoryContainerId("container-123").memoryId(null).build();

        ActionRequestValidationException exception = request.validate();
        assertNotNull(exception);
        assertEquals(1, exception.validationErrors().size());
        assertTrue(exception.validationErrors().get(0).contains("Memory id can't be null"));
    }

    @Test
    public void testValidateWithBothNull() {
        ActionRequestValidationException exception = requestEmpty.validate();
        assertNotNull(exception);
        assertEquals(2, exception.validationErrors().size());
        assertTrue(exception.validationErrors().get(0).contains("Memory container id can't be null"));
        assertTrue(exception.validationErrors().get(1).contains("Memory id can't be null"));
    }

    @Test
    public void testFromActionRequestSameInstance() {
        MLDeleteMemoryRequest result = MLDeleteMemoryRequest.fromActionRequest(requestNormal);
        assertEquals(requestNormal, result);
    }

    @Test
    public void testFromActionRequestDifferentInstance() throws IOException {
        // Create a mock ActionRequest that's not MLDeleteMemoryRequest
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
            }
        };

        MLDeleteMemoryRequest result = MLDeleteMemoryRequest.fromActionRequest(mockRequest);
        assertNotNull(result);
        assertEquals("test-container", result.getMemoryContainerId());
        assertEquals("test-memory", result.getMemoryId());
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

        MLDeleteMemoryRequest.fromActionRequest(mockRequest);
    }

    @Test
    public void testSpecialCharacters() throws IOException {
        MLDeleteMemoryRequest specialRequest = MLDeleteMemoryRequest
            .builder()
            .memoryContainerId("container-with-special-chars-🚀")
            .memoryId("memory-with-\n\ttabs-and-\"quotes\"")
            .build();

        // Test serialization round trip
        BytesStreamOutput out = new BytesStreamOutput();
        specialRequest.writeTo(out);
        StreamInput in = out.bytes().streamInput();
        MLDeleteMemoryRequest deserialized = new MLDeleteMemoryRequest(in);

        assertEquals(specialRequest.getMemoryContainerId(), deserialized.getMemoryContainerId());
        assertEquals(specialRequest.getMemoryId(), deserialized.getMemoryId());
    }

    @Test
    public void testEmptyStrings() {
        MLDeleteMemoryRequest emptyStringRequest = MLDeleteMemoryRequest.builder().memoryContainerId("").memoryId("").build();

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

        MLDeleteMemoryRequest longRequest = MLDeleteMemoryRequest
            .builder()
            .memoryContainerId(longId.toString())
            .memoryId(longId.toString() + "-memory")
            .build();

        // Test serialization
        BytesStreamOutput out = new BytesStreamOutput();
        longRequest.writeTo(out);
        StreamInput in = out.bytes().streamInput();
        MLDeleteMemoryRequest deserialized = new MLDeleteMemoryRequest(in);

        assertEquals(longRequest.getMemoryContainerId(), deserialized.getMemoryContainerId());
        assertEquals(longRequest.getMemoryId(), deserialized.getMemoryId());
    }
}
