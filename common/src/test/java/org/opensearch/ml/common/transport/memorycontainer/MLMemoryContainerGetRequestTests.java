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

public class MLMemoryContainerGetRequestTests {

    private MLMemoryContainerGetRequest requestWithTenant;
    private MLMemoryContainerGetRequest requestWithoutTenant;
    private MLMemoryContainerGetRequest requestWithLongId;

    @Before
    public void setUp() {
        // Request with tenant ID
        requestWithTenant = MLMemoryContainerGetRequest.builder().memoryContainerId("memory-container-123").tenantId("test-tenant").build();

        // Request without tenant ID
        requestWithoutTenant = MLMemoryContainerGetRequest.builder().memoryContainerId("memory-container-456").tenantId(null).build();

        // Request with long ID to test edge cases
        requestWithLongId = MLMemoryContainerGetRequest
            .builder()
            .memoryContainerId("memory-container-with-very-long-id-that-contains-multiple-segments-and-special-characters-789")
            .tenantId("tenant-with-long-name-and-special-characters-!@#$%")
            .build();
    }

    @Test
    public void testConstructorWithBuilder() {
        assertNotNull(requestWithTenant);
        assertEquals("memory-container-123", requestWithTenant.getMemoryContainerId());
        assertEquals("test-tenant", requestWithTenant.getTenantId());

        assertNotNull(requestWithoutTenant);
        assertEquals("memory-container-456", requestWithoutTenant.getMemoryContainerId());
        assertNull(requestWithoutTenant.getTenantId());
    }

    @Test
    public void testConstructorWithBuilderLongValues() {
        assertNotNull(requestWithLongId);
        assertEquals(
            "memory-container-with-very-long-id-that-contains-multiple-segments-and-special-characters-789",
            requestWithLongId.getMemoryContainerId()
        );
        assertEquals("tenant-with-long-name-and-special-characters-!@#$%", requestWithLongId.getTenantId());
    }

    @Test
    public void testConstructorWithParameters() {
        MLMemoryContainerGetRequest request = new MLMemoryContainerGetRequest("test-id", "test-tenant");

        assertNotNull(request);
        assertEquals("test-id", request.getMemoryContainerId());
        assertEquals("test-tenant", request.getTenantId());
    }

    @Test
    public void testConstructorWithNullTenant() {
        MLMemoryContainerGetRequest request = new MLMemoryContainerGetRequest("test-id", null);

        assertNotNull(request);
        assertEquals("test-id", request.getMemoryContainerId());
        assertNull(request.getTenantId());
    }

    @Test
    public void testStreamInputOutput() throws IOException {
        BytesStreamOutput bytesStreamOutput = new BytesStreamOutput();
        requestWithTenant.writeTo(bytesStreamOutput);

        StreamInput streamInput = bytesStreamOutput.bytes().streamInput();
        MLMemoryContainerGetRequest parsedRequest = new MLMemoryContainerGetRequest(streamInput);

        assertNotNull(parsedRequest);
        assertEquals(requestWithTenant.getMemoryContainerId(), parsedRequest.getMemoryContainerId());
        assertEquals(requestWithTenant.getTenantId(), parsedRequest.getTenantId());
    }

    @Test
    public void testStreamInputOutputWithoutTenant() throws IOException {
        BytesStreamOutput bytesStreamOutput = new BytesStreamOutput();
        requestWithoutTenant.writeTo(bytesStreamOutput);

        StreamInput streamInput = bytesStreamOutput.bytes().streamInput();
        MLMemoryContainerGetRequest parsedRequest = new MLMemoryContainerGetRequest(streamInput);

        assertNotNull(parsedRequest);
        assertEquals(requestWithoutTenant.getMemoryContainerId(), parsedRequest.getMemoryContainerId());
        assertNull(parsedRequest.getTenantId());
    }

    @Test
    public void testStreamInputOutputWithLongValues() throws IOException {
        BytesStreamOutput bytesStreamOutput = new BytesStreamOutput();
        requestWithLongId.writeTo(bytesStreamOutput);

        StreamInput streamInput = bytesStreamOutput.bytes().streamInput();
        MLMemoryContainerGetRequest parsedRequest = new MLMemoryContainerGetRequest(streamInput);

        assertNotNull(parsedRequest);
        assertEquals(requestWithLongId.getMemoryContainerId(), parsedRequest.getMemoryContainerId());
        assertEquals(requestWithLongId.getTenantId(), parsedRequest.getTenantId());
    }

    @Test
    public void testValidateWithValidRequest() {
        ActionRequestValidationException validationException = requestWithTenant.validate();
        assertNull(validationException);
    }

    @Test
    public void testValidateWithValidRequestWithoutTenant() {
        ActionRequestValidationException validationException = requestWithoutTenant.validate();
        assertNull(validationException);
    }

    @Test
    public void testValidateWithNullMemoryContainerId() {
        MLMemoryContainerGetRequest requestWithNullId = MLMemoryContainerGetRequest
            .builder()
            .memoryContainerId(null)
            .tenantId("test-tenant")
            .build();

        ActionRequestValidationException validationException = requestWithNullId.validate();

        assertNotNull(validationException);
        assertTrue(validationException.validationErrors().contains("Memory container id can't be null"));
    }

    @Test
    public void testValidateWithEmptyMemoryContainerId() {
        // Empty string is considered valid (not null)
        MLMemoryContainerGetRequest requestWithEmptyId = MLMemoryContainerGetRequest
            .builder()
            .memoryContainerId("")
            .tenantId("test-tenant")
            .build();

        ActionRequestValidationException validationException = requestWithEmptyId.validate();
        assertNull(validationException); // Empty string should be valid
    }

    @Test
    public void testFromActionRequestWithSameType() {
        MLMemoryContainerGetRequest result = MLMemoryContainerGetRequest.fromActionRequest(requestWithTenant);

        assertSame(requestWithTenant, result);
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
                // Write data in the same format as MLMemoryContainerGetRequest
                super.writeTo(out); // Write ActionRequest base data
                out.writeString("test-memory-container-id");
                out.writeOptionalString("test-tenant-id");
            }
        };

        MLMemoryContainerGetRequest result = MLMemoryContainerGetRequest.fromActionRequest(mockActionRequest);

        assertNotNull(result);
        assertEquals("test-memory-container-id", result.getMemoryContainerId());
        assertEquals("test-tenant-id", result.getTenantId());
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

        MLMemoryContainerGetRequest.fromActionRequest(mockActionRequest);
    }

    @Test
    public void testGetterMethods() {
        assertEquals("memory-container-123", requestWithTenant.getMemoryContainerId());
        assertEquals("test-tenant", requestWithTenant.getTenantId());

        assertEquals("memory-container-456", requestWithoutTenant.getMemoryContainerId());
        assertNull(requestWithoutTenant.getTenantId());
    }

    @Test
    public void testBuilderFunctionality() {
        MLMemoryContainerGetRequest request = MLMemoryContainerGetRequest
            .builder()
            .memoryContainerId("builder-test-id")
            .tenantId("builder-test-tenant")
            .build();

        assertNotNull(request);
        assertEquals("builder-test-id", request.getMemoryContainerId());
        assertEquals("builder-test-tenant", request.getTenantId());
    }

    @Test
    public void testInheritanceFromActionRequest() {
        assertTrue(requestWithTenant instanceof ActionRequest);
        assertTrue(requestWithoutTenant instanceof ActionRequest);
        assertTrue(requestWithLongId instanceof ActionRequest);
    }

    @Test
    public void testFieldsAreFinal() {
        // Test that fields are final (immutable) - this is enforced by Lombok @FieldDefaults
        // We can't directly test final fields, but we can test that there are no setters
        try {
            // Try to find setter methods - should not exist due to final fields
            requestWithTenant.getClass().getMethod("setMemoryContainerId", String.class);
            org.junit.Assert.fail("Setter method should not exist for final field");
        } catch (NoSuchMethodException e) {
            // Expected - no setter should exist
        }

        try {
            requestWithTenant.getClass().getMethod("setTenantId", String.class);
            org.junit.Assert.fail("Setter method should not exist for final field");
        } catch (NoSuchMethodException e) {
            // Expected - no setter should exist
        }
    }

    @Test
    public void testToStringFunctionality() {
        String toString = requestWithTenant.toString();
        assertNotNull(toString);
        assertTrue(toString.contains("memory-container-123"));
        assertTrue(toString.contains("test-tenant"));
        assertTrue(toString.contains("MLMemoryContainerGetRequest"));
    }

    @Test
    public void testCompleteRoundTripSerialization() throws IOException {
        // Test complete serialization round trip
        BytesStreamOutput bytesStreamOutput = new BytesStreamOutput();
        requestWithTenant.writeTo(bytesStreamOutput);

        StreamInput streamInput = bytesStreamOutput.bytes().streamInput();
        MLMemoryContainerGetRequest deserializedRequest = new MLMemoryContainerGetRequest(streamInput);

        // Verify all data is preserved
        assertEquals(requestWithTenant.getMemoryContainerId(), deserializedRequest.getMemoryContainerId());
        assertEquals(requestWithTenant.getTenantId(), deserializedRequest.getTenantId());

        // Test that the deserialized request can be serialized again
        BytesStreamOutput secondOutput = new BytesStreamOutput();
        deserializedRequest.writeTo(secondOutput);

        StreamInput secondInput = secondOutput.bytes().streamInput();
        MLMemoryContainerGetRequest secondDeserialized = new MLMemoryContainerGetRequest(secondInput);

        assertEquals(requestWithTenant.getMemoryContainerId(), secondDeserialized.getMemoryContainerId());
        assertEquals(requestWithTenant.getTenantId(), secondDeserialized.getTenantId());
    }

    @Test
    public void testValidationWithMultipleErrors() {
        // Create a request that would have multiple validation errors if we had more validation rules
        MLMemoryContainerGetRequest requestWithNullId = MLMemoryContainerGetRequest
            .builder()
            .memoryContainerId(null)
            .tenantId("test-tenant")
            .build();

        ActionRequestValidationException validationException = requestWithNullId.validate();

        assertNotNull(validationException);
        assertEquals(1, validationException.validationErrors().size());
        assertTrue(validationException.validationErrors().get(0).contains("Memory container id can't be null"));
    }

    @Test
    public void testWithSpecialCharacters() throws IOException {
        MLMemoryContainerGetRequest requestWithSpecialChars = MLMemoryContainerGetRequest
            .builder()
            .memoryContainerId("memory-container-with-special-chars-!@#$%^&*()_+-=[]{}|;':\",./<>?")
            .tenantId("tenant-with-special-chars-!@#$%")
            .build();

        assertEquals("memory-container-with-special-chars-!@#$%^&*()_+-=[]{}|;':\",./<>?", requestWithSpecialChars.getMemoryContainerId());
        assertEquals("tenant-with-special-chars-!@#$%", requestWithSpecialChars.getTenantId());

        // Test serialization with special characters
        BytesStreamOutput bytesStreamOutput = new BytesStreamOutput();
        requestWithSpecialChars.writeTo(bytesStreamOutput);

        StreamInput streamInput = bytesStreamOutput.bytes().streamInput();
        MLMemoryContainerGetRequest parsedRequest = new MLMemoryContainerGetRequest(streamInput);

        assertEquals(requestWithSpecialChars.getMemoryContainerId(), parsedRequest.getMemoryContainerId());
        assertEquals(requestWithSpecialChars.getTenantId(), parsedRequest.getTenantId());
    }

    @Test
    public void testWithEmptyStrings() throws IOException {
        MLMemoryContainerGetRequest requestWithEmptyStrings = MLMemoryContainerGetRequest
            .builder()
            .memoryContainerId("")
            .tenantId("")
            .build();

        assertEquals("", requestWithEmptyStrings.getMemoryContainerId());
        assertEquals("", requestWithEmptyStrings.getTenantId());

        // Test serialization with empty strings
        BytesStreamOutput bytesStreamOutput = new BytesStreamOutput();
        requestWithEmptyStrings.writeTo(bytesStreamOutput);

        StreamInput streamInput = bytesStreamOutput.bytes().streamInput();
        MLMemoryContainerGetRequest parsedRequest = new MLMemoryContainerGetRequest(streamInput);

        assertEquals("", parsedRequest.getMemoryContainerId());
        assertEquals("", parsedRequest.getTenantId());

        // Validation should pass with empty string (not null)
        ActionRequestValidationException validationException = requestWithEmptyStrings.validate();
        assertNull(validationException);
    }

    @Test
    public void testFromActionRequestRoundTrip() throws IOException {
        // Test that fromActionRequest can properly handle the same request type
        MLMemoryContainerGetRequest reconstructed = MLMemoryContainerGetRequest.fromActionRequest(requestWithTenant);
        assertSame(requestWithTenant, reconstructed);

        // Test with request without tenant
        MLMemoryContainerGetRequest minimalReconstructed = MLMemoryContainerGetRequest.fromActionRequest(requestWithoutTenant);
        assertSame(requestWithoutTenant, minimalReconstructed);
    }

    @Test
    public void testLombokAnnotations() {
        // Test @Getter annotation
        assertNotNull(requestWithTenant.getMemoryContainerId());
        assertNotNull(requestWithTenant.getTenantId());

        // Test @ToString annotation
        String toString = requestWithTenant.toString();
        assertNotNull(toString);
        assertTrue(toString.length() > 0);

        // Test @FieldDefaults (final fields) - no setters should exist
        try {
            requestWithTenant.getClass().getMethod("setMemoryContainerId", String.class);
            org.junit.Assert.fail("Should not have setter for final field");
        } catch (NoSuchMethodException e) {
            // Expected
        }
    }

    @Test
    public void testMultipleInstancesIndependence() {
        // Test that multiple instances don't interfere with each other
        MLMemoryContainerGetRequest request1 = MLMemoryContainerGetRequest.builder().memoryContainerId("id1").tenantId("tenant1").build();

        MLMemoryContainerGetRequest request2 = MLMemoryContainerGetRequest.builder().memoryContainerId("id2").tenantId("tenant2").build();

        assertEquals("id1", request1.getMemoryContainerId());
        assertEquals("tenant1", request1.getTenantId());
        assertEquals("id2", request2.getMemoryContainerId());
        assertEquals("tenant2", request2.getTenantId());

        // Verify they don't affect each other
        assertNotEquals(request1.getMemoryContainerId(), request2.getMemoryContainerId());
        assertNotEquals(request1.getTenantId(), request2.getTenantId());
    }

    // Helper method for assertions
    private void assertNotEquals(Object obj1, Object obj2) {
        org.junit.Assert.assertNotEquals(obj1, obj2);
    }
}
