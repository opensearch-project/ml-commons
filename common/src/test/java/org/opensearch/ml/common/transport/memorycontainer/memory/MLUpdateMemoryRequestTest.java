/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.memorycontainer.memory;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.opensearch.ml.common.memorycontainer.MemoryConfiguration.VALID_MEMORY_TYPES;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.MEM_CONTAINER_MEMORY_TYPE_SESSIONS;

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
        assertTrue(exception.validationErrors().get(0).contains("Memory type can't be null or empty"));
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
        assertTrue(exception.validationErrors().get(2).contains("Memory type can't be null or empty"));
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
        assertTrue(exception.validationErrors().get(1).contains("Memory type can't be null or empty"));
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

        // Empty strings should fail validation (same as null values)
        ActionRequestValidationException exception = emptyStringRequest.validate();
        assertNotNull(exception);
        assertEquals(1, exception.validationErrors().size());
        assertTrue(exception.validationErrors().get(0).contains("Memory type can't be null or empty"));
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

    @Test
    public void testValidateWithInvalidMemoryType() {
        MLUpdateMemoryRequest request = MLUpdateMemoryRequest
            .builder()
            .memoryContainerId("container-123")
            .memoryType("invalid-type")
            .memoryId("memory-456")
            .mlUpdateMemoryInput(testInput)
            .build();

        ActionRequestValidationException exception = request.validate();
        assertNotNull(exception);
        assertEquals(1, exception.validationErrors().size());
        assertTrue(exception.validationErrors().get(0).contains("Invalid memory type"));
    }

    @Test
    public void testValidateWithValidMemoryTypes() {
        // Test all valid memory types: "sessions", "working", "long-term", "history"
        for (String memoryType : VALID_MEMORY_TYPES) {
            MLUpdateMemoryRequest request = MLUpdateMemoryRequest
                .builder()
                .memoryContainerId("container-123")
                .memoryType(memoryType)
                .memoryId("memory-456")
                .mlUpdateMemoryInput(testInput)
                .build();

            ActionRequestValidationException exception = request.validate();
            assertNull("Memory type '" + memoryType + "' should be valid", exception);
        }
    }

    // Additional tests to specifically cover lines 69-73 in MLUpdateMemoryRequest.validate()
    @Test
    public void testValidateMemoryTypeNullOrEmpty_Line69() {
        // Test line 69: if (memoryType == null || memoryType.isEmpty())

        // Test null memory type
        MLUpdateMemoryRequest requestWithNull = MLUpdateMemoryRequest
            .builder()
            .memoryContainerId("container-123")
            .memoryType(null)
            .memoryId("memory-456")
            .mlUpdateMemoryInput(testInput)
            .build();

        ActionRequestValidationException exceptionNull = requestWithNull.validate();
        assertNotNull(exceptionNull);
        assertTrue(exceptionNull.validationErrors().stream().anyMatch(error -> error.contains("Memory type can't be null or empty")));

        // Test empty memory type
        MLUpdateMemoryRequest requestWithEmpty = MLUpdateMemoryRequest
            .builder()
            .memoryContainerId("container-123")
            .memoryType("")
            .memoryId("memory-456")
            .mlUpdateMemoryInput(testInput)
            .build();

        ActionRequestValidationException exceptionEmpty = requestWithEmpty.validate();
        assertNotNull(exceptionEmpty);
        assertTrue(exceptionEmpty.validationErrors().stream().anyMatch(error -> error.contains("Memory type can't be null or empty")));
    }

    @Test
    public void testValidateInvalidMemoryType_Lines71to73() {
        // Test lines 71-73: } else if (!VALID_MEMORY_TYPES.contains(memoryType)) {
        // exception = addValidationError("Invalid memory type", exception);
        // }

        String[] invalidTypes = { "invalid", "unknown", "bad-type", "LONG-TERM", "Session", "WORKING" };

        for (String invalidType : invalidTypes) {
            MLUpdateMemoryRequest request = MLUpdateMemoryRequest
                .builder()
                .memoryContainerId("container-123")
                .memoryType(invalidType)
                .memoryId("memory-456")
                .mlUpdateMemoryInput(testInput)
                .build();

            ActionRequestValidationException exception = request.validate();
            assertNotNull("Memory type '" + invalidType + "' should be invalid", exception);
            assertTrue(
                "Should contain 'Invalid memory type' error for: " + invalidType,
                exception.validationErrors().stream().anyMatch(error -> error.contains("Invalid memory type"))
            );
        }
    }

    @Test
    public void testValidateMemoryTypeValidation_BothBranches() {
        // Test that valid memory types pass through the validation without errors
        // This ensures the else-if condition on line 71 works correctly for valid types
        for (String validType : VALID_MEMORY_TYPES) {
            MLUpdateMemoryRequest request = MLUpdateMemoryRequest
                .builder()
                .memoryContainerId("container-123")
                .memoryType(validType)
                .memoryId("memory-456")
                .mlUpdateMemoryInput(testInput)
                .build();

            ActionRequestValidationException exception = request.validate();
            // Should be null since all other fields are valid
            assertNull("Valid memory type '" + validType + "' should not produce validation errors", exception);
        }
    }

    @Test
    public void testValidateMemoryTypeWhitespaceOnly() {
        // Test memory type with only whitespace (should be treated as empty)
        MLUpdateMemoryRequest requestWithWhitespace = MLUpdateMemoryRequest
            .builder()
            .memoryContainerId("container-123")
            .memoryType("   ")
            .memoryId("memory-456")
            .mlUpdateMemoryInput(testInput)
            .build();

        ActionRequestValidationException exception = requestWithWhitespace.validate();
        assertNotNull(exception);
        // Whitespace-only string should be treated as invalid memory type (not empty)
        assertTrue(exception.validationErrors().stream().anyMatch(error -> error.contains("Invalid memory type")));
    }

    // Additional tests to ensure 100% coverage

    @Test
    public void testConstructorWithStreamInput() throws IOException {
        // Test the StreamInput constructor directly
        BytesStreamOutput out = new BytesStreamOutput();
        requestNormal.writeTo(out);
        StreamInput in = out.bytes().streamInput();

        // Create new instance using StreamInput constructor
        MLUpdateMemoryRequest fromStream = new MLUpdateMemoryRequest(in);

        assertEquals(requestNormal.getMemoryContainerId(), fromStream.getMemoryContainerId());
        assertEquals(requestNormal.getMemoryType(), fromStream.getMemoryType());
        assertEquals(requestNormal.getMemoryId(), fromStream.getMemoryId());
        assertNotNull(fromStream.getMlUpdateMemoryInput());
        assertEquals(requestNormal.getMlUpdateMemoryInput().getUpdateContent(), fromStream.getMlUpdateMemoryInput().getUpdateContent());
    }

    @Test
    public void testBuilderWithAllFieldsSet() {
        // Test builder with all fields explicitly set
        Map<String, Object> content = new HashMap<>();
        content.put("key", "value");
        MLUpdateMemoryInput input = MLUpdateMemoryInput.builder().updateContent(content).build();

        MLUpdateMemoryRequest request = MLUpdateMemoryRequest
            .builder()
            .memoryContainerId("test-container")
            .memoryType(MEM_CONTAINER_MEMORY_TYPE_SESSIONS)
            .memoryId("test-memory")
            .mlUpdateMemoryInput(input)
            .build();

        assertEquals("test-container", request.getMemoryContainerId());
        assertEquals(MEM_CONTAINER_MEMORY_TYPE_SESSIONS, request.getMemoryType());
        assertEquals("test-memory", request.getMemoryId());
        assertEquals(input, request.getMlUpdateMemoryInput());
    }

    @Test
    public void testValidateWithEmptyContainerId() {
        // Test validation with empty container ID (should pass since only null is checked)
        MLUpdateMemoryRequest request = MLUpdateMemoryRequest
            .builder()
            .memoryContainerId("")
            .memoryType(MEM_CONTAINER_MEMORY_TYPE_SESSIONS)
            .memoryId("memory-456")
            .mlUpdateMemoryInput(testInput)
            .build();

        ActionRequestValidationException exception = request.validate();
        // Empty string container ID should be valid (only null is invalid)
        assertNull(exception);
    }

    @Test
    public void testValidateWithEmptyMemoryId() {
        // Test validation with empty memory ID (should pass since only null is checked)
        MLUpdateMemoryRequest request = MLUpdateMemoryRequest
            .builder()
            .memoryContainerId("container-123")
            .memoryType(MEM_CONTAINER_MEMORY_TYPE_SESSIONS)
            .memoryId("")
            .mlUpdateMemoryInput(testInput)
            .build();

        ActionRequestValidationException exception = request.validate();
        // Empty string memory ID should be valid (only null is invalid)
        assertNull(exception);
    }

    @Test
    public void testFromActionRequestWithComplexInput() throws IOException {
        // Test fromActionRequest with a more complex MLUpdateMemoryInput
        Map<String, Object> complexContent = new HashMap<>();
        complexContent.put("text", "Complex text content");
        complexContent.put("metadata", Map.of("key1", "value1", "key2", "value2"));
        complexContent.put("tags", java.util.Arrays.asList("tag1", "tag2", "tag3"));

        MLUpdateMemoryInput complexInput = MLUpdateMemoryInput.builder().updateContent(complexContent).build();

        MLUpdateMemoryRequest originalRequest = MLUpdateMemoryRequest
            .builder()
            .memoryContainerId("complex-container")
            .memoryType("working")
            .memoryId("complex-memory")
            .mlUpdateMemoryInput(complexInput)
            .build();

        // Test fromActionRequest with the complex request
        MLUpdateMemoryRequest result = MLUpdateMemoryRequest.fromActionRequest(originalRequest);
        assertEquals(originalRequest, result);

        // Verify complex content is preserved
        assertEquals(complexContent, result.getMlUpdateMemoryInput().getUpdateContent());
    }

    @Test
    public void testSerializationWithNullInput() throws IOException {
        // Test serialization when mlUpdateMemoryInput is null
        // Note: This will fail validation, but we can test serialization separately
        MLUpdateMemoryRequest requestWithNullInput = MLUpdateMemoryRequest
            .builder()
            .memoryContainerId("container-123")
            .memoryType(MEM_CONTAINER_MEMORY_TYPE_SESSIONS)
            .memoryId("memory-456")
            .mlUpdateMemoryInput(null)
            .build();

        // This should throw an exception during writeTo because mlUpdateMemoryInput.writeTo(out) will fail
        BytesStreamOutput out = new BytesStreamOutput();
        try {
            requestWithNullInput.writeTo(out);
            // If we get here, the test should fail because writeTo should have thrown an exception
            assertTrue("Expected NullPointerException when writing null mlUpdateMemoryInput", false);
        } catch (NullPointerException e) {
            // Expected behavior - writeTo fails when mlUpdateMemoryInput is null
            assertTrue(true);
        }
    }

    @Test
    public void testAllValidMemoryTypesIndividually() {
        // Test each valid memory type individually to ensure complete coverage
        for (String memoryType : VALID_MEMORY_TYPES) {
            MLUpdateMemoryRequest request = MLUpdateMemoryRequest
                .builder()
                .memoryContainerId("container-" + memoryType)
                .memoryType(memoryType)
                .memoryId("memory-" + memoryType)
                .mlUpdateMemoryInput(testInput)
                .build();

            // Test validation passes
            ActionRequestValidationException exception = request.validate();
            assertNull("Memory type '" + memoryType + "' should be valid", exception);

            // Test getters
            assertEquals("container-" + memoryType, request.getMemoryContainerId());
            assertEquals(memoryType, request.getMemoryType());
            assertEquals("memory-" + memoryType, request.getMemoryId());
            assertEquals(testInput, request.getMlUpdateMemoryInput());
        }
    }

    @Test
    public void testValidateReturnsNullForValidRequest() {
        // Explicitly test that validate() returns null for a completely valid request
        MLUpdateMemoryRequest validRequest = MLUpdateMemoryRequest
            .builder()
            .memoryContainerId("valid-container")
            .memoryType(MEM_CONTAINER_MEMORY_TYPE_SESSIONS)
            .memoryId("valid-memory")
            .mlUpdateMemoryInput(testInput)
            .build();

        ActionRequestValidationException result = validRequest.validate();
        assertNull("Valid request should return null from validate()", result);
    }

    @Test
    public void testSetterAfterConstruction() {
        // Test that setter works correctly after object construction
        Map<String, Object> originalContent = new HashMap<>();
        originalContent.put("original", "content");
        MLUpdateMemoryInput originalInput = MLUpdateMemoryInput.builder().updateContent(originalContent).build();

        Map<String, Object> newContent = new HashMap<>();
        newContent.put("new", "content");
        MLUpdateMemoryInput newInput = MLUpdateMemoryInput.builder().updateContent(newContent).build();

        MLUpdateMemoryRequest request = MLUpdateMemoryRequest
            .builder()
            .memoryContainerId("container-123")
            .memoryType(MEM_CONTAINER_MEMORY_TYPE_SESSIONS)
            .memoryId("memory-456")
            .mlUpdateMemoryInput(originalInput)
            .build();

        // Verify original input
        assertEquals(originalContent, request.getMlUpdateMemoryInput().getUpdateContent());

        // Use setter to change input
        request.setMlUpdateMemoryInput(newInput);

        // Verify new input
        assertEquals(newContent, request.getMlUpdateMemoryInput().getUpdateContent());
    }

    @Test
    public void testEdgeCaseMemoryTypes() {
        // Test memory types that are close to valid but not quite
        String[] edgeCaseTypes = {
            "Session", // Wrong case
            "WORKING", // Wrong case
            "long_term", // Underscore instead of hyphen
            "longterm", // No separator
            "work", // Partial
            " session ", // With spaces
            "session\n", // With newline
            "session\t" // With tab
        };

        for (String memoryType : edgeCaseTypes) {
            MLUpdateMemoryRequest request = MLUpdateMemoryRequest
                .builder()
                .memoryContainerId("container-123")
                .memoryType(memoryType)
                .memoryId("memory-456")
                .mlUpdateMemoryInput(testInput)
                .build();

            ActionRequestValidationException exception = request.validate();
            assertNotNull("Memory type '" + memoryType + "' should be invalid", exception);
            assertTrue(
                "Should contain 'Invalid memory type' error for: " + memoryType,
                exception.validationErrors().stream().anyMatch(error -> error.contains("Invalid memory type"))
            );
        }
    }
}
