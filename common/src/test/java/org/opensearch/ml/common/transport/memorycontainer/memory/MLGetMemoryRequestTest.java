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
import org.opensearch.ml.common.memorycontainer.MemoryType;

public class MLGetMemoryRequestTest {

    private MLGetMemoryRequest requestNormal;
    private MLGetMemoryRequest requestWithNulls;

    @Before
    public void setUp() {
        requestNormal = MLGetMemoryRequest
            .builder()
            .memoryContainerId("container-123")
            .memoryType(MemoryType.LONG_TERM)
            .memoryId("memory-456")
            .tenantId("tenant-789")
            .build();

        requestWithNulls = MLGetMemoryRequest.builder().memoryContainerId(null).memoryType(null).memoryId(null).tenantId(null).build();
    }

    @Test
    public void testBuilderNormal() {
        assertNotNull(requestNormal);
        assertEquals("container-123", requestNormal.getMemoryContainerId());
        assertEquals(MemoryType.LONG_TERM, requestNormal.getMemoryType());
        assertEquals("memory-456", requestNormal.getMemoryId());
    }

    @Test
    public void testStreamInputOutput() throws IOException {
        BytesStreamOutput out = new BytesStreamOutput();
        requestNormal.writeTo(out);
        StreamInput in = out.bytes().streamInput();
        MLGetMemoryRequest deserialized = new MLGetMemoryRequest(in);

        assertEquals(requestNormal.getMemoryContainerId(), deserialized.getMemoryContainerId());
        assertEquals(requestNormal.getMemoryType(), deserialized.getMemoryType());
        assertEquals(requestNormal.getMemoryId(), deserialized.getMemoryId());
    }

    @Test
    public void testValidateSuccess() {
        ActionRequestValidationException exception = requestNormal.validate();
        assertNull(exception);
    }

    @Test
    public void testValidateWithNullValues() {
        ActionRequestValidationException exception = requestWithNulls.validate();
        assertNotNull(exception);
        assertEquals(2, exception.validationErrors().size());
        assertTrue(exception.validationErrors().get(0).contains("memoryContainerId and memoryId id can not be null"));
        assertTrue(exception.validationErrors().get(1).contains("Memory type can not be null"));
    }

    @Test
    public void testFromActionRequestSameInstance() {
        MLGetMemoryRequest result = MLGetMemoryRequest.fromActionRequest(requestNormal);
        assertEquals(requestNormal, result);
    }

    @Test
    public void testFromActionRequestDifferentInstance() throws IOException {
        // Create a mock ActionRequest that's not MLGetMemoryRequest
        ActionRequest mockRequest = new ActionRequest() {
            @Override
            public ActionRequestValidationException validate() {
                return null;
            }

            @Override
            public void writeTo(StreamOutput out) throws IOException {
                super.writeTo(out);
                out.writeString("test-container");
                out.writeEnum(MemoryType.SESSIONS);
                out.writeString("test-memory");
                out.writeOptionalString("test-tenant");
            }
        };

        MLGetMemoryRequest result = MLGetMemoryRequest.fromActionRequest(mockRequest);
        assertNotNull(result);
        assertEquals("test-container", result.getMemoryContainerId());
        assertEquals(MemoryType.SESSIONS, result.getMemoryType());
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

        MLGetMemoryRequest.fromActionRequest(mockRequest);
    }
}
