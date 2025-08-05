/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.memorycontainer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
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
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.memorycontainer.MemoryStorageConfig;

public class MLCreateMemoryContainerRequestTests {

    private MLCreateMemoryContainerRequest requestWithAllFields;
    private MLCreateMemoryContainerRequest requestMinimal;
    private MLCreateMemoryContainerInput testInput;
    private MLCreateMemoryContainerInput minimalInput;
    private MemoryStorageConfig testMemoryStorageConfig;

    @Before
    public void setUp() {
        // Create test memory storage config
        testMemoryStorageConfig = MemoryStorageConfig
            .builder()
            .memoryIndexName("test-memory-index")
            .embeddingModelType(FunctionName.TEXT_EMBEDDING)
            .embeddingModelId("test-embedding-model")
            .llmModelId("test-llm-model")
            .dimension(768)
            .maxInferSize(8)
            .build();

        // Create test input with all fields
        testInput = MLCreateMemoryContainerInput
            .builder()
            .name("test-memory-container")
            .description("Test memory container description")
            .memoryStorageConfig(testMemoryStorageConfig)
            .tenantId("test-tenant")
            .build();

        // Create minimal input
        minimalInput = MLCreateMemoryContainerInput.builder().name("minimal-container").build();

        // Create requests
        requestWithAllFields = MLCreateMemoryContainerRequest.builder().mlCreateMemoryContainerInput(testInput).build();

        requestMinimal = MLCreateMemoryContainerRequest.builder().mlCreateMemoryContainerInput(minimalInput).build();
    }

    @Test
    public void testConstructorWithBuilder() {
        assertNotNull(requestWithAllFields);
        assertEquals(testInput, requestWithAllFields.getMlCreateMemoryContainerInput());

        assertNotNull(requestMinimal);
        assertEquals(minimalInput, requestMinimal.getMlCreateMemoryContainerInput());
    }

    @Test
    public void testConstructorWithInput() {
        MLCreateMemoryContainerRequest request = new MLCreateMemoryContainerRequest(testInput);

        assertNotNull(request);
        assertEquals(testInput, request.getMlCreateMemoryContainerInput());
    }

    @Test
    public void testStreamInputOutput() throws IOException {
        BytesStreamOutput bytesStreamOutput = new BytesStreamOutput();
        requestWithAllFields.writeTo(bytesStreamOutput);

        StreamInput streamInput = bytesStreamOutput.bytes().streamInput();
        MLCreateMemoryContainerRequest parsedRequest = new MLCreateMemoryContainerRequest(streamInput);

        assertNotNull(parsedRequest);
        assertNotNull(parsedRequest.getMlCreateMemoryContainerInput());

        // Verify the input fields
        MLCreateMemoryContainerInput originalInput = requestWithAllFields.getMlCreateMemoryContainerInput();
        MLCreateMemoryContainerInput parsedInput = parsedRequest.getMlCreateMemoryContainerInput();

        assertEquals(originalInput.getName(), parsedInput.getName());
        assertEquals(originalInput.getDescription(), parsedInput.getDescription());
        assertEquals(originalInput.getTenantId(), parsedInput.getTenantId());
        assertEquals(originalInput.getMemoryStorageConfig(), parsedInput.getMemoryStorageConfig());
    }

    @Test
    public void testStreamInputOutputWithMinimalFields() throws IOException {
        BytesStreamOutput bytesStreamOutput = new BytesStreamOutput();
        requestMinimal.writeTo(bytesStreamOutput);

        StreamInput streamInput = bytesStreamOutput.bytes().streamInput();
        MLCreateMemoryContainerRequest parsedRequest = new MLCreateMemoryContainerRequest(streamInput);

        assertNotNull(parsedRequest);
        assertNotNull(parsedRequest.getMlCreateMemoryContainerInput());

        MLCreateMemoryContainerInput parsedInput = parsedRequest.getMlCreateMemoryContainerInput();
        assertEquals("minimal-container", parsedInput.getName());
        assertNull(parsedInput.getDescription());
        assertNull(parsedInput.getTenantId());
        assertNull(parsedInput.getMemoryStorageConfig());
    }

    @Test
    public void testValidateWithValidInput() {
        ActionRequestValidationException validationException = requestWithAllFields.validate();
        assertNull(validationException);
    }

    @Test
    public void testValidateWithMinimalValidInput() {
        ActionRequestValidationException validationException = requestMinimal.validate();
        assertNull(validationException);
    }

    @Test
    public void testValidateWithNullInput() {
        MLCreateMemoryContainerRequest requestWithNullInput = MLCreateMemoryContainerRequest
            .builder()
            .mlCreateMemoryContainerInput(null)
            .build();

        ActionRequestValidationException validationException = requestWithNullInput.validate();

        assertNotNull(validationException);
        assertTrue(validationException.validationErrors().contains("Memory container input can't be null"));
    }

    @Test
    public void testFromActionRequestWithSameType() {
        MLCreateMemoryContainerRequest result = MLCreateMemoryContainerRequest.fromActionRequest(requestWithAllFields);

        assertSame(requestWithAllFields, result);
    }

    @Test
    public void testFromActionRequestWithDifferentType() throws IOException {
        // Create a properly serializable ActionRequest that writes data in the expected format
        ActionRequest mockActionRequest = new ActionRequest() {
            @Override
            public ActionRequestValidationException validate() {
                return null;
            }

            @Override
            public void writeTo(StreamOutput out) throws IOException {
                // Write data in the same format as MLCreateMemoryContainerRequest
                super.writeTo(out); // Write ActionRequest base data
                testInput.writeTo(out); // Write the MLCreateMemoryContainerInput data
            }
        };

        MLCreateMemoryContainerRequest result = MLCreateMemoryContainerRequest.fromActionRequest(mockActionRequest);

        assertNotNull(result);
        assertNotNull(result.getMlCreateMemoryContainerInput());
        assertEquals(testInput.getName(), result.getMlCreateMemoryContainerInput().getName());
        assertEquals(testInput.getDescription(), result.getMlCreateMemoryContainerInput().getDescription());
        assertEquals(testInput.getTenantId(), result.getMlCreateMemoryContainerInput().getTenantId());
    }

    @Test(expected = UncheckedIOException.class)
    public void testFromActionRequestWithIOException() {
        // Create a mock ActionRequest that throws IOException during serialization
        ActionRequest mockActionRequest = new ActionRequest() {
            @Override
            public ActionRequestValidationException validate() {
                return null;
            }

            @Override
            public void writeTo(StreamOutput out) throws IOException {
                throw new IOException("Test IOException");
            }
        };

        MLCreateMemoryContainerRequest.fromActionRequest(mockActionRequest);
    }

    @Test
    public void testGetterFunctionality() {
        assertEquals(testInput, requestWithAllFields.getMlCreateMemoryContainerInput());
        assertEquals(minimalInput, requestMinimal.getMlCreateMemoryContainerInput());
    }

    @Test
    public void testBuilderFunctionality() {
        MLCreateMemoryContainerRequest request = MLCreateMemoryContainerRequest.builder().mlCreateMemoryContainerInput(testInput).build();

        assertNotNull(request);
        assertEquals(testInput, request.getMlCreateMemoryContainerInput());
    }

    @Test
    public void testInheritanceFromActionRequest() {
        assertTrue(requestWithAllFields instanceof ActionRequest);
        assertTrue(requestMinimal instanceof ActionRequest);
    }

    @Test
    public void testValidationDelegation() {
        // Test that validation is properly delegated to the input object
        // The request itself only validates that input is not null
        // All other validation is handled by MLCreateMemoryContainerInput and MemoryStorageConfig

        // Valid input should pass validation
        ActionRequestValidationException validationException = requestWithAllFields.validate();
        assertNull(validationException);

        // Null input should fail validation
        MLCreateMemoryContainerRequest nullInputRequest = MLCreateMemoryContainerRequest
            .builder()
            .mlCreateMemoryContainerInput(null)
            .build();

        ActionRequestValidationException nullValidationException = nullInputRequest.validate();
        assertNotNull(nullValidationException);
        assertEquals(1, nullValidationException.validationErrors().size());
        assertTrue(nullValidationException.validationErrors().get(0).contains("Memory container input can't be null"));
    }

    @Test
    public void testCompleteRoundTripSerialization() throws IOException {
        // Test complete serialization round trip
        BytesStreamOutput bytesStreamOutput = new BytesStreamOutput();
        requestWithAllFields.writeTo(bytesStreamOutput);

        StreamInput streamInput = bytesStreamOutput.bytes().streamInput();
        MLCreateMemoryContainerRequest deserializedRequest = new MLCreateMemoryContainerRequest(streamInput);

        // Verify all nested data is preserved
        MLCreateMemoryContainerInput originalInput = requestWithAllFields.getMlCreateMemoryContainerInput();
        MLCreateMemoryContainerInput deserializedInput = deserializedRequest.getMlCreateMemoryContainerInput();

        assertEquals(originalInput.getName(), deserializedInput.getName());
        assertEquals(originalInput.getDescription(), deserializedInput.getDescription());
        assertEquals(originalInput.getTenantId(), deserializedInput.getTenantId());

        // Verify nested MemoryStorageConfig
        MemoryStorageConfig originalConfig = originalInput.getMemoryStorageConfig();
        MemoryStorageConfig deserializedConfig = deserializedInput.getMemoryStorageConfig();

        assertEquals(originalConfig.getMemoryIndexName(), deserializedConfig.getMemoryIndexName());
        assertEquals(originalConfig.isSemanticStorageEnabled(), deserializedConfig.isSemanticStorageEnabled());
        assertEquals(originalConfig.getEmbeddingModelType(), deserializedConfig.getEmbeddingModelType());
        assertEquals(originalConfig.getEmbeddingModelId(), deserializedConfig.getEmbeddingModelId());
        assertEquals(originalConfig.getLlmModelId(), deserializedConfig.getLlmModelId());
        assertEquals(originalConfig.getDimension(), deserializedConfig.getDimension());
        assertEquals(originalConfig.getMaxInferSize(), deserializedConfig.getMaxInferSize());
    }

    @Test
    public void testFromActionRequestRoundTrip() throws IOException {
        // Test that fromActionRequest can properly handle the same request type
        MLCreateMemoryContainerRequest reconstructed = MLCreateMemoryContainerRequest.fromActionRequest(requestWithAllFields);
        assertSame(requestWithAllFields, reconstructed);

        // Test with minimal request
        MLCreateMemoryContainerRequest minimalReconstructed = MLCreateMemoryContainerRequest.fromActionRequest(requestMinimal);
        assertSame(requestMinimal, minimalReconstructed);
    }

    @Test
    public void testNullInputHandling() {
        MLCreateMemoryContainerRequest requestWithNull = MLCreateMemoryContainerRequest
            .builder()
            .mlCreateMemoryContainerInput(null)
            .build();

        assertNotNull(requestWithNull);
        assertNull(requestWithNull.getMlCreateMemoryContainerInput());

        // Validation should catch this
        ActionRequestValidationException validationException = requestWithNull.validate();
        assertNotNull(validationException);
        assertTrue(validationException.validationErrors().contains("Memory container input can't be null"));
    }

    @Test
    public void testBuilderWithNullInput() {
        MLCreateMemoryContainerRequest request = MLCreateMemoryContainerRequest.builder().mlCreateMemoryContainerInput(null).build();

        assertNotNull(request);
        assertNull(request.getMlCreateMemoryContainerInput());
    }
}
