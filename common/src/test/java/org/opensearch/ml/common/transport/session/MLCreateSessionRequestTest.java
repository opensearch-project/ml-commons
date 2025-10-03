/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.session;

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

public class MLCreateSessionRequestTest {

    private MLCreateSessionRequest requestNormal;
    private MLCreateSessionRequest requestWithNullInput;
    private MLCreateSessionInput testInput;

    @Before
    public void setUp() {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("version", "1.0");
        metadata.put("type", "conversation");

        Map<String, Object> agents = new HashMap<>();
        agents.put("agent1", "assistant");
        agents.put("agent2", "user");

        // Note: Avoiding namespace field due to serialization bug in MLCreateSessionInput
        testInput = MLCreateSessionInput
            .builder()
            .sessionId("session-123")
            .ownerId("owner-456")
            .summary("Test session summary")
            .metadata(metadata)
            .agents(agents)
            .namespace(null) // Avoid namespace serialization bug
            .tenantId("tenant-789")
            .memoryContainerId("memory-container-abc")
            .build();

        requestNormal = MLCreateSessionRequest.builder().mlCreateSessionInput(testInput).build();

        requestWithNullInput = MLCreateSessionRequest.builder().mlCreateSessionInput(null).build();
    }

    @Test
    public void testBuilderWithValidInput() {
        assertNotNull(requestNormal);
        assertNotNull(requestNormal.getMlCreateSessionInput());
        assertEquals(testInput, requestNormal.getMlCreateSessionInput());
        assertEquals("session-123", requestNormal.getMlCreateSessionInput().getSessionId());
        assertEquals("owner-456", requestNormal.getMlCreateSessionInput().getOwnerId());
        assertEquals("Test session summary", requestNormal.getMlCreateSessionInput().getSummary());
        assertEquals("tenant-789", requestNormal.getMlCreateSessionInput().getTenantId());
        assertEquals("memory-container-abc", requestNormal.getMlCreateSessionInput().getMemoryContainerId());
    }

    @Test
    public void testBuilderWithNullInput() {
        assertNotNull(requestWithNullInput);
        assertNull(requestWithNullInput.getMlCreateSessionInput());
    }

    @Test
    public void testStreamInputOutput() throws IOException {
        BytesStreamOutput out = new BytesStreamOutput();
        requestNormal.writeTo(out);
        StreamInput in = out.bytes().streamInput();
        MLCreateSessionRequest deserialized = new MLCreateSessionRequest(in);

        assertNotNull(deserialized.getMlCreateSessionInput());
        assertEquals(requestNormal.getMlCreateSessionInput().getSessionId(), deserialized.getMlCreateSessionInput().getSessionId());
        assertEquals(requestNormal.getMlCreateSessionInput().getOwnerId(), deserialized.getMlCreateSessionInput().getOwnerId());
        assertEquals(requestNormal.getMlCreateSessionInput().getSummary(), deserialized.getMlCreateSessionInput().getSummary());
        assertEquals(requestNormal.getMlCreateSessionInput().getTenantId(), deserialized.getMlCreateSessionInput().getTenantId());
        assertEquals(
            requestNormal.getMlCreateSessionInput().getMemoryContainerId(),
            deserialized.getMlCreateSessionInput().getMemoryContainerId()
        );
    }

    @Test
    public void testValidateSuccess() {
        ActionRequestValidationException exception = requestNormal.validate();
        assertNull(exception);
    }

    @Test
    public void testValidateWithNullInput() {
        ActionRequestValidationException exception = requestWithNullInput.validate();
        assertNotNull(exception);
        assertEquals(1, exception.validationErrors().size());
        assertTrue(exception.validationErrors().get(0).contains("Session input can't be null"));
    }

    @Test
    public void testValidateWithNullMemoryContainerId() {
        MLCreateSessionInput inputWithNullMemoryContainer = MLCreateSessionInput
            .builder()
            .sessionId("session-123")
            .ownerId("owner-456")
            .memoryContainerId(null)
            .build();

        MLCreateSessionRequest request = MLCreateSessionRequest.builder().mlCreateSessionInput(inputWithNullMemoryContainer).build();

        ActionRequestValidationException exception = request.validate();
        assertNotNull(exception);
        assertEquals(1, exception.validationErrors().size());
        assertTrue(exception.validationErrors().get(0).contains("Memory container ID is required"));
    }

    @Test
    public void testValidateWithEmptyMemoryContainerId() {
        MLCreateSessionInput inputWithEmptyMemoryContainer = MLCreateSessionInput
            .builder()
            .sessionId("session-123")
            .ownerId("owner-456")
            .memoryContainerId("")
            .build();

        MLCreateSessionRequest request = MLCreateSessionRequest.builder().mlCreateSessionInput(inputWithEmptyMemoryContainer).build();

        ActionRequestValidationException exception = request.validate();
        assertNotNull(exception);
        assertEquals(1, exception.validationErrors().size());
        assertTrue(exception.validationErrors().get(0).contains("Memory container ID is required"));
    }

    @Test
    public void testValidateWithWhitespaceOnlyMemoryContainerId() {
        MLCreateSessionInput inputWithWhitespaceMemoryContainer = MLCreateSessionInput
            .builder()
            .sessionId("session-123")
            .ownerId("owner-456")
            .memoryContainerId("   ")
            .build();

        MLCreateSessionRequest request = MLCreateSessionRequest.builder().mlCreateSessionInput(inputWithWhitespaceMemoryContainer).build();

        ActionRequestValidationException exception = request.validate();
        assertNotNull(exception);
        assertEquals(1, exception.validationErrors().size());
        assertTrue(exception.validationErrors().get(0).contains("Memory container ID is required"));
    }

    @Test
    public void testFromActionRequestSameInstance() {
        MLCreateSessionRequest result = MLCreateSessionRequest.fromActionRequest(requestNormal);
        assertEquals(requestNormal, result);
    }

    @Test
    public void testFromActionRequestDifferentInstance() throws IOException {
        // Create a mock ActionRequest that's not MLCreateSessionRequest
        ActionRequest mockRequest = new ActionRequest() {
            @Override
            public ActionRequestValidationException validate() {
                return null;
            }

            @Override
            public void writeTo(StreamOutput out) throws IOException {
                super.writeTo(out);
                testInput.writeTo(out);
            }
        };

        MLCreateSessionRequest result = MLCreateSessionRequest.fromActionRequest(mockRequest);
        assertNotNull(result);
        assertNotNull(result.getMlCreateSessionInput());
        assertEquals("session-123", result.getMlCreateSessionInput().getSessionId());
        assertEquals("owner-456", result.getMlCreateSessionInput().getOwnerId());
        assertEquals("Test session summary", result.getMlCreateSessionInput().getSummary());
        assertEquals("memory-container-abc", result.getMlCreateSessionInput().getMemoryContainerId());
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

        MLCreateSessionRequest.fromActionRequest(mockRequest);
    }

    @Test
    public void testConstructorWithStreamInput() throws IOException {
        // Test the StreamInput constructor directly
        BytesStreamOutput out = new BytesStreamOutput();
        requestNormal.writeTo(out);
        StreamInput in = out.bytes().streamInput();

        // Create new instance using StreamInput constructor
        MLCreateSessionRequest fromStream = new MLCreateSessionRequest(in);

        assertNotNull(fromStream.getMlCreateSessionInput());
        assertEquals(requestNormal.getMlCreateSessionInput().getSessionId(), fromStream.getMlCreateSessionInput().getSessionId());
        assertEquals(requestNormal.getMlCreateSessionInput().getOwnerId(), fromStream.getMlCreateSessionInput().getOwnerId());
        assertEquals(requestNormal.getMlCreateSessionInput().getSummary(), fromStream.getMlCreateSessionInput().getSummary());
        assertEquals(
            requestNormal.getMlCreateSessionInput().getMemoryContainerId(),
            fromStream.getMlCreateSessionInput().getMemoryContainerId()
        );
    }

    @Test
    public void testBuilderWithAllFieldsSet() {
        // Test builder with comprehensive input
        Map<String, Object> complexMetadata = new HashMap<>();
        complexMetadata.put("nested", Map.of("key1", "value1", "key2", 42));
        complexMetadata.put("array", java.util.Arrays.asList("item1", "item2", "item3"));

        MLCreateSessionInput complexInput = MLCreateSessionInput
            .builder()
            .sessionId("complex-session")
            .ownerId("complex-owner")
            .summary("Complex session summary")
            .metadata(complexMetadata)
            .tenantId("complex-tenant")
            .memoryContainerId("complex-memory-container")
            .build();

        MLCreateSessionRequest request = MLCreateSessionRequest.builder().mlCreateSessionInput(complexInput).build();

        assertEquals(complexInput, request.getMlCreateSessionInput());
        assertEquals("complex-session", request.getMlCreateSessionInput().getSessionId());
        assertEquals("complex-owner", request.getMlCreateSessionInput().getOwnerId());
        assertEquals("Complex session summary", request.getMlCreateSessionInput().getSummary());
        assertEquals("complex-tenant", request.getMlCreateSessionInput().getTenantId());
        assertEquals("complex-memory-container", request.getMlCreateSessionInput().getMemoryContainerId());
    }

    @Test
    public void testValidateWithValidMemoryContainerId() {
        // Test various valid memory container IDs
        String[] validIds = {
            "memory-container-123",
            "a",
            "container_with_underscores",
            "container-with-hyphens",
            "ContainerWithCamelCase",
            "container123",
            "very-long-container-id-with-many-characters-and-numbers-123456789" };

        for (String containerId : validIds) {
            MLCreateSessionInput input = MLCreateSessionInput.builder().sessionId("session-123").memoryContainerId(containerId).build();

            MLCreateSessionRequest request = MLCreateSessionRequest.builder().mlCreateSessionInput(input).build();

            ActionRequestValidationException exception = request.validate();
            assertNull("Memory container ID '" + containerId + "' should be valid", exception);
        }
    }

    @Test
    public void testSerializationWithComplexInput() throws IOException {
        // Test serialization with complex nested data
        Map<String, Object> complexMetadata = new HashMap<>();
        complexMetadata.put("nested_object", Map.of("inner_key", "inner_value", "inner_number", 42));
        complexMetadata.put("array_field", java.util.Arrays.asList("item1", "item2", "item3"));
        complexMetadata.put("boolean_field", true);
        complexMetadata.put("null_field", null);

        Map<String, Object> complexAgents = new HashMap<>();
        complexAgents.put("agent_config", Map.of("timeout", 30, "retries", 3));

        MLCreateSessionInput complexInput = MLCreateSessionInput
            .builder()
            .sessionId("complex-session")
            .ownerId("complex-owner")
            .summary("Complex session with nested data")
            .metadata(complexMetadata)
            .agents(complexAgents)
            .namespace(null) // Avoid namespace serialization bug
            .memoryContainerId("complex-memory-container")
            .build();

        MLCreateSessionRequest originalRequest = MLCreateSessionRequest.builder().mlCreateSessionInput(complexInput).build();

        // Test serialization round trip
        BytesStreamOutput out = new BytesStreamOutput();
        originalRequest.writeTo(out);
        StreamInput in = out.bytes().streamInput();
        MLCreateSessionRequest deserializedRequest = new MLCreateSessionRequest(in);

        assertEquals(
            originalRequest.getMlCreateSessionInput().getSessionId(),
            deserializedRequest.getMlCreateSessionInput().getSessionId()
        );
        assertEquals(originalRequest.getMlCreateSessionInput().getOwnerId(), deserializedRequest.getMlCreateSessionInput().getOwnerId());
        assertEquals(originalRequest.getMlCreateSessionInput().getSummary(), deserializedRequest.getMlCreateSessionInput().getSummary());
        assertEquals(
            originalRequest.getMlCreateSessionInput().getMemoryContainerId(),
            deserializedRequest.getMlCreateSessionInput().getMemoryContainerId()
        );
        assertEquals(originalRequest.getMlCreateSessionInput().getMetadata(), deserializedRequest.getMlCreateSessionInput().getMetadata());
        assertEquals(originalRequest.getMlCreateSessionInput().getAgents(), deserializedRequest.getMlCreateSessionInput().getAgents());
    }

    @Test
    public void testSpecialCharactersInFields() throws IOException {
        // Test with special characters and Unicode
        MLCreateSessionInput specialInput = MLCreateSessionInput
            .builder()
            .sessionId("session-with-special-chars-🚀✨")
            .ownerId("owner-with-unicode-💫")
            .summary("Summary with\nnewlines\tand\ttabs and \"quotes\"")
            .tenantId("tenant-with-special-chars-🌟")
            .namespace(null) // Avoid namespace serialization bug
            .memoryContainerId("memory-container-with-unicode-⭐")
            .build();

        MLCreateSessionRequest specialRequest = MLCreateSessionRequest.builder().mlCreateSessionInput(specialInput).build();

        // Test validation passes
        ActionRequestValidationException exception = specialRequest.validate();
        assertNull(exception);

        // Test serialization round trip
        BytesStreamOutput out = new BytesStreamOutput();
        specialRequest.writeTo(out);
        StreamInput in = out.bytes().streamInput();
        MLCreateSessionRequest deserializedRequest = new MLCreateSessionRequest(in);

        assertEquals(specialRequest.getMlCreateSessionInput().getSessionId(), deserializedRequest.getMlCreateSessionInput().getSessionId());
        assertEquals(specialRequest.getMlCreateSessionInput().getOwnerId(), deserializedRequest.getMlCreateSessionInput().getOwnerId());
        assertEquals(specialRequest.getMlCreateSessionInput().getSummary(), deserializedRequest.getMlCreateSessionInput().getSummary());
        assertEquals(specialRequest.getMlCreateSessionInput().getTenantId(), deserializedRequest.getMlCreateSessionInput().getTenantId());
        assertEquals(
            specialRequest.getMlCreateSessionInput().getMemoryContainerId(),
            deserializedRequest.getMlCreateSessionInput().getMemoryContainerId()
        );
    }

    @Test
    public void testFromActionRequestWithComplexInput() throws IOException {
        // Test fromActionRequest with complex input data
        Map<String, Object> complexMetadata = new HashMap<>();
        complexMetadata.put("text", "Complex text content");
        complexMetadata.put("metadata", Map.of("key1", "value1", "key2", "value2"));
        complexMetadata.put("tags", java.util.Arrays.asList("tag1", "tag2", "tag3"));

        MLCreateSessionInput complexInput = MLCreateSessionInput
            .builder()
            .sessionId("complex-session")
            .ownerId("complex-owner")
            .summary("Complex session summary")
            .metadata(complexMetadata)
            .namespace(null) // Avoid namespace serialization bug
            .memoryContainerId("complex-memory-container")
            .build();

        MLCreateSessionRequest originalRequest = MLCreateSessionRequest.builder().mlCreateSessionInput(complexInput).build();

        // Test fromActionRequest with the complex request
        MLCreateSessionRequest result = MLCreateSessionRequest.fromActionRequest(originalRequest);
        assertEquals(originalRequest, result);

        // Verify complex content is preserved
        assertEquals(complexMetadata, result.getMlCreateSessionInput().getMetadata());
    }

    @Test
    public void testValidateReturnsNullForValidRequest() {
        // Explicitly test that validate() returns null for a completely valid request
        MLCreateSessionInput validInput = MLCreateSessionInput
            .builder()
            .sessionId("valid-session")
            .ownerId("valid-owner")
            .summary("Valid session summary")
            .memoryContainerId("valid-memory-container")
            .build();

        MLCreateSessionRequest validRequest = MLCreateSessionRequest.builder().mlCreateSessionInput(validInput).build();

        ActionRequestValidationException result = validRequest.validate();
        assertNull("Valid request should return null from validate()", result);
    }

    @Test
    public void testGetterMethod() {
        // Test that getter method works correctly
        assertEquals(testInput, requestNormal.getMlCreateSessionInput());
        assertNull(requestWithNullInput.getMlCreateSessionInput());
    }

    @Test
    public void testEdgeCaseMemoryContainerIds() {
        // Test memory container IDs that are edge cases but should be valid
        String[] edgeCaseIds = {
            "a", // Single character
            "1", // Single digit
            "container-with-many-hyphens-and-numbers-123-456-789",
            "UPPERCASE_CONTAINER",
            "mixedCaseContainer123",
            "container.with.dots",
            "container@with@symbols" };

        for (String containerId : edgeCaseIds) {
            MLCreateSessionInput input = MLCreateSessionInput.builder().sessionId("session-123").memoryContainerId(containerId).build();

            MLCreateSessionRequest request = MLCreateSessionRequest.builder().mlCreateSessionInput(input).build();

            ActionRequestValidationException exception = request.validate();
            assertNull("Memory container ID '" + containerId + "' should be valid", exception);
        }
    }
}
