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
import static org.opensearch.core.xcontent.ToXContent.EMPTY_PARAMS;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;

import org.junit.Before;
import org.junit.Test;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.commons.authuser.User;
import org.opensearch.core.action.ActionResponse;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.TestHelper;
import org.opensearch.ml.common.memorycontainer.MLMemoryContainer;
import org.opensearch.ml.common.memorycontainer.MemoryStorageConfig;

public class MLMemoryContainerGetResponseTests {

    private MLMemoryContainerGetResponse responseWithAllFields;
    private MLMemoryContainerGetResponse responseMinimal;
    private MLMemoryContainer testMemoryContainer;
    private MLMemoryContainer minimalMemoryContainer;
    private MemoryStorageConfig testMemoryStorageConfig;
    private User testUser;
    private Instant testCreatedTime;
    private Instant testLastUpdatedTime;

    @Before
    public void setUp() {
        testUser = new User(); // Use empty User constructor
        // Use millisecond precision to avoid precision loss in JSON serialization
        testCreatedTime = Instant.ofEpochMilli(System.currentTimeMillis());
        testLastUpdatedTime = Instant.ofEpochMilli(System.currentTimeMillis() + 3600000);

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

        // Create test memory container with all fields
        testMemoryContainer = MLMemoryContainer
            .builder()
            .name("test-memory-container")
            .description("Test memory container description")
            .owner(testUser)
            .tenantId("test-tenant")
            .createdTime(testCreatedTime)
            .lastUpdatedTime(testLastUpdatedTime)
            .memoryStorageConfig(testMemoryStorageConfig)
            .build();

        // Create minimal memory container
        minimalMemoryContainer = MLMemoryContainer.builder().name("minimal-container").build();

        // Create responses
        responseWithAllFields = MLMemoryContainerGetResponse.builder().mlMemoryContainer(testMemoryContainer).build();

        responseMinimal = MLMemoryContainerGetResponse.builder().mlMemoryContainer(minimalMemoryContainer).build();
    }

    @Test
    public void testConstructorWithBuilder() {
        assertNotNull(responseWithAllFields);
        assertEquals(testMemoryContainer, responseWithAllFields.getMlMemoryContainer());

        assertNotNull(responseMinimal);
        assertEquals(minimalMemoryContainer, responseMinimal.getMlMemoryContainer());
    }

    @Test
    public void testConstructorWithMemoryContainer() {
        MLMemoryContainerGetResponse response = new MLMemoryContainerGetResponse(testMemoryContainer);

        assertNotNull(response);
        assertEquals(testMemoryContainer, response.getMlMemoryContainer());
    }

    @Test
    public void testStreamInputOutput() throws IOException {
        BytesStreamOutput bytesStreamOutput = new BytesStreamOutput();
        responseWithAllFields.writeTo(bytesStreamOutput);

        StreamInput streamInput = bytesStreamOutput.bytes().streamInput();
        MLMemoryContainerGetResponse parsedResponse = new MLMemoryContainerGetResponse(streamInput);

        assertNotNull(parsedResponse);
        assertNotNull(parsedResponse.getMlMemoryContainer());

        // Verify the memory container fields
        MLMemoryContainer originalContainer = responseWithAllFields.getMlMemoryContainer();
        MLMemoryContainer parsedContainer = parsedResponse.getMlMemoryContainer();

        assertEquals(originalContainer.getName(), parsedContainer.getName());
        assertEquals(originalContainer.getDescription(), parsedContainer.getDescription());
        assertEquals(originalContainer.getTenantId(), parsedContainer.getTenantId());
        assertEquals(originalContainer.getCreatedTime(), parsedContainer.getCreatedTime());
        assertEquals(originalContainer.getLastUpdatedTime(), parsedContainer.getLastUpdatedTime());
        assertEquals(originalContainer.getMemoryStorageConfig(), parsedContainer.getMemoryStorageConfig());
    }

    @Test
    public void testStreamInputOutputWithMinimalFields() throws IOException {
        BytesStreamOutput bytesStreamOutput = new BytesStreamOutput();
        responseMinimal.writeTo(bytesStreamOutput);

        StreamInput streamInput = bytesStreamOutput.bytes().streamInput();
        MLMemoryContainerGetResponse parsedResponse = new MLMemoryContainerGetResponse(streamInput);

        assertNotNull(parsedResponse);
        assertNotNull(parsedResponse.getMlMemoryContainer());

        MLMemoryContainer parsedContainer = parsedResponse.getMlMemoryContainer();
        assertEquals("minimal-container", parsedContainer.getName());
        assertNull(parsedContainer.getDescription());
        assertNull(parsedContainer.getTenantId());
        assertNull(parsedContainer.getCreatedTime());
        assertNull(parsedContainer.getLastUpdatedTime());
        assertNull(parsedContainer.getMemoryStorageConfig());
    }

    @Test
    public void testToXContentWithAllFields() throws IOException {
        XContentBuilder builder = XContentBuilder.builder(XContentType.JSON.xContent());
        responseWithAllFields.toXContent(builder, EMPTY_PARAMS);
        String jsonStr = TestHelper.xContentBuilderToString(builder);

        assertNotNull(jsonStr);
        // Verify that all memory container fields are present in the JSON
        assertTrue(jsonStr.contains("\"name\":\"test-memory-container\""));
        assertTrue(jsonStr.contains("\"description\":\"Test memory container description\""));
        assertTrue(jsonStr.contains("\"tenant_id\":\"test-tenant\""));
        assertTrue(jsonStr.contains("\"created_time\":" + testCreatedTime.toEpochMilli()));
        assertTrue(jsonStr.contains("\"last_updated_time\":" + testLastUpdatedTime.toEpochMilli()));
        assertTrue(jsonStr.contains("\"memory_storage_config\""));

        // Verify nested memory storage config fields
        assertTrue(jsonStr.contains("\"memory_index_name\":\"test-memory-index\""));
        assertTrue(jsonStr.contains("\"embedding_model_type\":\"TEXT_EMBEDDING\""));
        assertTrue(jsonStr.contains("\"embedding_model_id\":\"test-embedding-model\""));
    }

    @Test
    public void testToXContentWithMinimalFields() throws IOException {
        XContentBuilder builder = XContentBuilder.builder(XContentType.JSON.xContent());
        responseMinimal.toXContent(builder, EMPTY_PARAMS);
        String jsonStr = TestHelper.xContentBuilderToString(builder);

        assertNotNull(jsonStr);
        // Verify only required fields are present
        assertTrue(jsonStr.contains("\"name\":\"minimal-container\""));
        // Verify optional fields are not present
        assertFalse(jsonStr.contains("\"description\""));
        assertFalse(jsonStr.contains("\"tenant_id\""));
        assertFalse(jsonStr.contains("\"created_time\""));
        assertFalse(jsonStr.contains("\"last_updated_time\""));
        assertFalse(jsonStr.contains("\"memory_storage_config\""));
    }

    @Test
    public void testGetterMethod() {
        assertEquals(testMemoryContainer, responseWithAllFields.getMlMemoryContainer());
        assertEquals(minimalMemoryContainer, responseMinimal.getMlMemoryContainer());
    }

    @Test
    public void testInheritanceFromActionResponse() {
        assertTrue(responseWithAllFields instanceof ActionResponse);
        assertTrue(responseMinimal instanceof ActionResponse);
    }

    @Test
    public void testToXContentObjectInterface() {
        assertTrue(responseWithAllFields instanceof org.opensearch.core.xcontent.ToXContentObject);
        assertTrue(responseMinimal instanceof org.opensearch.core.xcontent.ToXContentObject);
    }

    @Test
    public void testFromActionResponseWithSameType() {
        MLMemoryContainerGetResponse result = MLMemoryContainerGetResponse.fromActionResponse(responseWithAllFields);

        assertSame(responseWithAllFields, result);
    }

    @Test
    public void testFromActionResponseWithDifferentType() throws IOException {
        // Create a properly serializable ActionResponse that writes data in the expected format
        ActionResponse mockActionResponse = new ActionResponse() {
            @Override
            public void writeTo(StreamOutput out) throws IOException {
                // Write data in the same format as MLMemoryContainerGetResponse
                testMemoryContainer.writeTo(out);
            }
        };

        MLMemoryContainerGetResponse result = MLMemoryContainerGetResponse.fromActionResponse(mockActionResponse);

        assertNotNull(result);
        assertNotNull(result.getMlMemoryContainer());
        assertEquals(testMemoryContainer.getName(), result.getMlMemoryContainer().getName());
        assertEquals(testMemoryContainer.getDescription(), result.getMlMemoryContainer().getDescription());
        assertEquals(testMemoryContainer.getTenantId(), result.getMlMemoryContainer().getTenantId());
    }

    @Test(expected = UncheckedIOException.class)
    public void testFromActionResponseWithIOException() {
        // Create a mock ActionResponse that throws IOException during serialization
        ActionResponse mockActionResponse = new ActionResponse() {
            @Override
            public void writeTo(StreamOutput out) throws IOException {
                throw new IOException("Test IOException");
            }
        };

        MLMemoryContainerGetResponse.fromActionResponse(mockActionResponse);
    }

    @Test
    public void testBuilderFunctionality() {
        MLMemoryContainerGetResponse response = MLMemoryContainerGetResponse.builder().mlMemoryContainer(testMemoryContainer).build();

        assertNotNull(response);
        assertEquals(testMemoryContainer, response.getMlMemoryContainer());
    }

    @Test
    public void testToStringFunctionality() {
        String toString = responseWithAllFields.toString();
        assertNotNull(toString);
        assertTrue(toString.length() > 0);
        // The toString should contain the class name
        assertTrue(toString.contains("MLMemoryContainerGetResponse"));
    }

    @Test
    public void testCompleteRoundTripSerialization() throws IOException {
        // Test complete serialization round trip
        BytesStreamOutput bytesStreamOutput = new BytesStreamOutput();
        responseWithAllFields.writeTo(bytesStreamOutput);

        StreamInput streamInput = bytesStreamOutput.bytes().streamInput();
        MLMemoryContainerGetResponse deserializedResponse = new MLMemoryContainerGetResponse(streamInput);

        // Verify all nested data is preserved
        MLMemoryContainer originalContainer = responseWithAllFields.getMlMemoryContainer();
        MLMemoryContainer deserializedContainer = deserializedResponse.getMlMemoryContainer();

        assertEquals(originalContainer.getName(), deserializedContainer.getName());
        assertEquals(originalContainer.getDescription(), deserializedContainer.getDescription());
        assertEquals(originalContainer.getTenantId(), deserializedContainer.getTenantId());
        assertEquals(originalContainer.getCreatedTime(), deserializedContainer.getCreatedTime());
        assertEquals(originalContainer.getLastUpdatedTime(), deserializedContainer.getLastUpdatedTime());

        // Verify nested MemoryStorageConfig
        MemoryStorageConfig originalConfig = originalContainer.getMemoryStorageConfig();
        MemoryStorageConfig deserializedConfig = deserializedContainer.getMemoryStorageConfig();

        assertEquals(originalConfig.getMemoryIndexName(), deserializedConfig.getMemoryIndexName());
        assertEquals(originalConfig.isSemanticStorageEnabled(), deserializedConfig.isSemanticStorageEnabled());
        assertEquals(originalConfig.getEmbeddingModelType(), deserializedConfig.getEmbeddingModelType());
        assertEquals(originalConfig.getEmbeddingModelId(), deserializedConfig.getEmbeddingModelId());
        assertEquals(originalConfig.getLlmModelId(), deserializedConfig.getLlmModelId());
        assertEquals(originalConfig.getDimension(), deserializedConfig.getDimension());
        assertEquals(originalConfig.getMaxInferSize(), deserializedConfig.getMaxInferSize());
    }

    @Test
    public void testXContentRoundTrip() throws IOException {
        // Test JSON serialization and verify structure
        XContentBuilder builder = XContentBuilder.builder(XContentType.JSON.xContent());
        responseWithAllFields.toXContent(builder, EMPTY_PARAMS);
        String jsonStr = TestHelper.xContentBuilderToString(builder);

        // Verify JSON structure contains expected fields
        assertTrue(jsonStr.contains("name"));
        assertTrue(jsonStr.contains("description"));
        assertTrue(jsonStr.contains("tenant_id"));
        assertTrue(jsonStr.contains("created_time"));
        assertTrue(jsonStr.contains("last_updated_time"));
        assertTrue(jsonStr.contains("memory_storage_config"));
        assertTrue(jsonStr.contains("test-memory-container"));
        assertTrue(jsonStr.contains("Test memory container description"));
    }

    @Test
    public void testWithNullMemoryContainer() throws IOException {
        MLMemoryContainerGetResponse responseWithNull = MLMemoryContainerGetResponse.builder().mlMemoryContainer(null).build();

        assertNotNull(responseWithNull);
        assertNull(responseWithNull.getMlMemoryContainer());
    }

    @Test
    public void testFromActionResponseRoundTrip() throws IOException {
        // Test that fromActionResponse can properly handle the same response type
        MLMemoryContainerGetResponse reconstructed = MLMemoryContainerGetResponse.fromActionResponse(responseWithAllFields);
        assertSame(responseWithAllFields, reconstructed);

        // Test with minimal response
        MLMemoryContainerGetResponse minimalReconstructed = MLMemoryContainerGetResponse.fromActionResponse(responseMinimal);
        assertSame(responseMinimal, minimalReconstructed);
    }

    @Test
    public void testLombokAnnotations() {
        // Test @Getter annotation
        assertNotNull(responseWithAllFields.getMlMemoryContainer());

        // Test @ToString annotation
        String toString = responseWithAllFields.toString();
        assertNotNull(toString);
        assertTrue(toString.length() > 0);

        // Test @Builder annotation
        MLMemoryContainerGetResponse builderResponse = MLMemoryContainerGetResponse
            .builder()
            .mlMemoryContainer(testMemoryContainer)
            .build();
        assertNotNull(builderResponse);
        assertEquals(testMemoryContainer, builderResponse.getMlMemoryContainer());
    }

    @Test
    public void testMultipleInstancesIndependence() {
        // Test that multiple instances don't interfere with each other
        MLMemoryContainer container1 = MLMemoryContainer.builder().name("container1").description("description1").build();

        MLMemoryContainer container2 = MLMemoryContainer.builder().name("container2").description("description2").build();

        MLMemoryContainerGetResponse response1 = MLMemoryContainerGetResponse.builder().mlMemoryContainer(container1).build();

        MLMemoryContainerGetResponse response2 = MLMemoryContainerGetResponse.builder().mlMemoryContainer(container2).build();

        assertEquals("container1", response1.getMlMemoryContainer().getName());
        assertEquals("description1", response1.getMlMemoryContainer().getDescription());
        assertEquals("container2", response2.getMlMemoryContainer().getName());
        assertEquals("description2", response2.getMlMemoryContainer().getDescription());

        // Verify they don't affect each other
        assertNotEquals(response1.getMlMemoryContainer().getName(), response2.getMlMemoryContainer().getName());
        assertNotEquals(response1.getMlMemoryContainer().getDescription(), response2.getMlMemoryContainer().getDescription());
    }

    @Test
    public void testDelegationToMLMemoryContainer() throws IOException {
        // Test that the response properly delegates to the wrapped MLMemoryContainer

        // Test toXContent delegation
        XContentBuilder responseBuilder = XContentBuilder.builder(XContentType.JSON.xContent());
        responseWithAllFields.toXContent(responseBuilder, EMPTY_PARAMS);
        String responseJson = TestHelper.xContentBuilderToString(responseBuilder);

        XContentBuilder containerBuilder = XContentBuilder.builder(XContentType.JSON.xContent());
        testMemoryContainer.toXContent(containerBuilder, EMPTY_PARAMS);
        String containerJson = TestHelper.xContentBuilderToString(containerBuilder);

        // The JSON output should be identical since response delegates to container
        assertEquals(containerJson, responseJson);
    }

    @Test
    public void testSerializationWithComplexMemoryContainer() throws IOException {
        // Create a memory container with complex nested structure
        MemoryStorageConfig complexConfig = MemoryStorageConfig
            .builder()
            .memoryIndexName("complex-memory-index-with-long-name")
            .embeddingModelType(FunctionName.SPARSE_ENCODING)
            .embeddingModelId("complex-sparse-encoding-model-id")
            .llmModelId("complex-llm-model-id")
            .maxInferSize(10)
            .build();

        MLMemoryContainer complexContainer = MLMemoryContainer
            .builder()
            .name("complex-memory-container-with-special-chars-!@#$%")
            .description("Complex description with\nnewlines and\ttabs and special chars: !@#$%^&*()")
            .owner(testUser)
            .tenantId("complex-tenant-id-with-special-chars")
            .createdTime(testCreatedTime)
            .lastUpdatedTime(testLastUpdatedTime)
            .memoryStorageConfig(complexConfig)
            .build();

        MLMemoryContainerGetResponse complexResponse = MLMemoryContainerGetResponse.builder().mlMemoryContainer(complexContainer).build();

        // Test serialization
        BytesStreamOutput bytesStreamOutput = new BytesStreamOutput();
        complexResponse.writeTo(bytesStreamOutput);

        StreamInput streamInput = bytesStreamOutput.bytes().streamInput();
        MLMemoryContainerGetResponse parsedResponse = new MLMemoryContainerGetResponse(streamInput);

        // Verify complex data is preserved
        MLMemoryContainer parsedContainer = parsedResponse.getMlMemoryContainer();
        assertEquals(complexContainer.getName(), parsedContainer.getName());
        assertEquals(complexContainer.getDescription(), parsedContainer.getDescription());
        assertEquals(complexContainer.getTenantId(), parsedContainer.getTenantId());
        assertEquals(complexContainer.getMemoryStorageConfig(), parsedContainer.getMemoryStorageConfig());
    }

    @Test
    public void testActionResponseIntegration() {
        // Test that the response properly integrates with ActionResponse framework
        assertTrue(responseWithAllFields instanceof ActionResponse);

        // Test that it can be treated as an ActionResponse
        ActionResponse genericResponse = responseWithAllFields;
        assertNotNull(genericResponse);

        // Test conversion back
        MLMemoryContainerGetResponse convertedBack = MLMemoryContainerGetResponse.fromActionResponse(genericResponse);
        assertSame(responseWithAllFields, convertedBack);
    }

    // Helper method for assertions
    private void assertNotEquals(Object obj1, Object obj2) {
        org.junit.Assert.assertNotEquals(obj1, obj2);
    }

    private void assertFalse(boolean condition) {
        org.junit.Assert.assertFalse(condition);
    }
}
