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

public class MLSearchMemoriesRequestTest {

    private MLSearchMemoriesInput testInput;
    private MLSearchMemoriesRequest request;

    @Before
    public void setUp() {
        testInput = MLSearchMemoriesInput.builder().memoryContainerId("container-123").query("machine learning concepts").build();

        request = MLSearchMemoriesRequest.builder().mlSearchMemoriesInput(testInput).tenantId("tenant-456").build();
    }

    @Test
    public void testBuilder() {
        assertNotNull(request);
        assertNotNull(request.getMlSearchMemoriesInput());
        assertEquals(testInput, request.getMlSearchMemoriesInput());
        assertEquals("tenant-456", request.getTenantId());
    }

    @Test
    public void testConstructor() {
        MLSearchMemoriesRequest constructedRequest = new MLSearchMemoriesRequest(testInput, "tenant-789");
        assertNotNull(constructedRequest);
        assertEquals(testInput, constructedRequest.getMlSearchMemoriesInput());
        assertEquals("tenant-789", constructedRequest.getTenantId());
    }

    @Test
    public void testStreamInputOutput() throws IOException {
        BytesStreamOutput out = new BytesStreamOutput();
        request.writeTo(out);

        StreamInput in = out.bytes().streamInput();
        MLSearchMemoriesRequest deserialized = new MLSearchMemoriesRequest(in);

        assertNotNull(deserialized.getMlSearchMemoriesInput());
        assertEquals(
            request.getMlSearchMemoriesInput().getMemoryContainerId(),
            deserialized.getMlSearchMemoriesInput().getMemoryContainerId()
        );
        assertEquals(request.getMlSearchMemoriesInput().getQuery(), deserialized.getMlSearchMemoriesInput().getQuery());
        assertEquals(request.getTenantId(), deserialized.getTenantId());
    }

    @Test
    public void testStreamInputOutputWithNullTenant() throws IOException {
        MLSearchMemoriesRequest requestNoTenant = MLSearchMemoriesRequest.builder().mlSearchMemoriesInput(testInput).tenantId(null).build();

        BytesStreamOutput out = new BytesStreamOutput();
        requestNoTenant.writeTo(out);

        StreamInput in = out.bytes().streamInput();
        MLSearchMemoriesRequest deserialized = new MLSearchMemoriesRequest(in);

        assertNotNull(deserialized.getMlSearchMemoriesInput());
        assertNull(deserialized.getTenantId());
    }

    @Test
    public void testValidateSuccess() {
        ActionRequestValidationException exception = request.validate();
        assertNull(exception);
    }

    @Test
    public void testValidateWithNullInput() {
        MLSearchMemoriesRequest invalidRequest = MLSearchMemoriesRequest
            .builder()
            .mlSearchMemoriesInput(null)
            .tenantId("tenant-123")
            .build();

        ActionRequestValidationException exception = invalidRequest.validate();
        assertNotNull(exception);
        assertEquals(1, exception.validationErrors().size());
        assertTrue(exception.validationErrors().get(0).contains("Search memories input can't be null"));
    }

    @Test
    public void testFromActionRequestSameInstance() {
        MLSearchMemoriesRequest result = MLSearchMemoriesRequest.fromActionRequest(request);
        assertEquals(request, result);
    }

    @Test
    public void testFromActionRequestDifferentInstance() throws IOException {
        // Create a mock ActionRequest that's not MLSearchMemoriesRequest
        ActionRequest mockRequest = new ActionRequest() {
            @Override
            public ActionRequestValidationException validate() {
                return null;
            }

            @Override
            public void writeTo(StreamOutput out) throws IOException {
                super.writeTo(out);
                testInput.writeTo(out);
                out.writeOptionalString("mock-tenant");
            }
        };

        MLSearchMemoriesRequest result = MLSearchMemoriesRequest.fromActionRequest(mockRequest);
        assertNotNull(result);
        assertEquals("container-123", result.getMlSearchMemoriesInput().getMemoryContainerId());
        assertEquals("machine learning concepts", result.getMlSearchMemoriesInput().getQuery());
        assertEquals("mock-tenant", result.getTenantId());
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

        MLSearchMemoriesRequest.fromActionRequest(mockRequest);
    }

    @Test
    public void testSetters() {
        MLSearchMemoriesRequest mutableRequest = new MLSearchMemoriesRequest(testInput, "initial-tenant");

        // Test setMlSearchMemoriesInput
        MLSearchMemoriesInput newInput = MLSearchMemoriesInput.builder().memoryContainerId("new-container").query("new query").build();
        mutableRequest.setMlSearchMemoriesInput(newInput);
        assertEquals(newInput, mutableRequest.getMlSearchMemoriesInput());

        // Test setTenantId
        mutableRequest.setTenantId("new-tenant");
        assertEquals("new-tenant", mutableRequest.getTenantId());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testWithEmptyQuery() {
        // Empty query is not allowed - should throw exception
        MLSearchMemoriesInput.builder().memoryContainerId("container-empty").query("").build();
    }

    @Test
    public void testWithSpecialCharacters() throws IOException {
        MLSearchMemoriesInput specialInput = MLSearchMemoriesInput
            .builder()
            .memoryContainerId("container-with-special-chars-🚀")
            .query("Query with \"quotes\" and\n\ttabs and unicode 🔥")
            .build();

        MLSearchMemoriesRequest specialRequest = MLSearchMemoriesRequest
            .builder()
            .mlSearchMemoriesInput(specialInput)
            .tenantId("tenant-特殊文字")
            .build();

        // Test serialization
        BytesStreamOutput out = new BytesStreamOutput();
        specialRequest.writeTo(out);

        StreamInput in = out.bytes().streamInput();
        MLSearchMemoriesRequest deserialized = new MLSearchMemoriesRequest(in);

        assertEquals("container-with-special-chars-🚀", deserialized.getMlSearchMemoriesInput().getMemoryContainerId());
        assertTrue(deserialized.getMlSearchMemoriesInput().getQuery().contains("quotes"));
        assertTrue(deserialized.getMlSearchMemoriesInput().getQuery().contains("tabs"));
        assertEquals("tenant-特殊文字", deserialized.getTenantId());
    }

    @Test
    public void testWithLongQuery() throws IOException {
        StringBuilder longQuery = new StringBuilder();
        for (int i = 0; i < 1000; i++) {
            longQuery.append("word").append(i).append(" ");
        }

        MLSearchMemoriesInput longInput = MLSearchMemoriesInput
            .builder()
            .memoryContainerId("container-long")
            .query(longQuery.toString().trim())
            .build();

        MLSearchMemoriesRequest longRequest = MLSearchMemoriesRequest
            .builder()
            .mlSearchMemoriesInput(longInput)
            .tenantId("tenant-long")
            .build();

        // Test serialization
        BytesStreamOutput out = new BytesStreamOutput();
        longRequest.writeTo(out);

        StreamInput in = out.bytes().streamInput();
        MLSearchMemoriesRequest deserialized = new MLSearchMemoriesRequest(in);

        assertEquals(longQuery.toString().trim(), deserialized.getMlSearchMemoriesInput().getQuery());
    }
}
