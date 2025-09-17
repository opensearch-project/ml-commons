/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.memorycontainer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.opensearch.core.xcontent.ToXContent.EMPTY_PARAMS;

import java.io.IOException;
import java.time.Instant;

import org.junit.Before;
import org.junit.Test;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.common.xcontent.LoggingDeprecationHandler;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.commons.authuser.User;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.TestHelper;

public class MLMemoryContainerTests {

    private MLMemoryContainer mlMemoryContainer;
    private User testUser;
    private MemoryStorageConfig testMemoryStorageConfig;
    private Instant testCreatedTime;
    private Instant testLastUpdatedTime;

    @Before
    public void setUp() {
        testUser = new User(); // Use empty User constructor like in MLModelTests
        // Use millisecond precision to avoid precision loss in JSON serialization
        testCreatedTime = Instant.ofEpochMilli(System.currentTimeMillis());
        testLastUpdatedTime = Instant.ofEpochMilli(System.currentTimeMillis() + 3600000);

        testMemoryStorageConfig = MemoryStorageConfig
            .builder()
            .memoryIndexName("test-memory-index")
            .semanticStorageEnabled(true)
            .embeddingModelType(FunctionName.TEXT_EMBEDDING)
            .embeddingModelId("test-embedding-model")
            .dimension(768)
            .maxInferSize(10) // Max allowed value is 10
            .build();

        mlMemoryContainer = MLMemoryContainer
            .builder()
            .name("test-memory-container")
            .description("Test memory container description")
            .owner(testUser)
            .tenantId("test-tenant")
            .createdTime(testCreatedTime)
            .lastUpdatedTime(testLastUpdatedTime)
            .memoryStorageConfig(testMemoryStorageConfig)
            .build();
    }

    @Test
    public void testConstructorWithBuilder() {
        assertNotNull(mlMemoryContainer);
        assertEquals("test-memory-container", mlMemoryContainer.getName());
        assertEquals("Test memory container description", mlMemoryContainer.getDescription());
        assertEquals(testUser, mlMemoryContainer.getOwner());
        assertEquals("test-tenant", mlMemoryContainer.getTenantId());
        assertEquals(testCreatedTime, mlMemoryContainer.getCreatedTime());
        assertEquals(testLastUpdatedTime, mlMemoryContainer.getLastUpdatedTime());
        assertEquals(testMemoryStorageConfig, mlMemoryContainer.getMemoryStorageConfig());
    }

    @Test
    public void testConstructorWithAllParameters() {
        MLMemoryContainer container = new MLMemoryContainer(
            "test-name",
            "test-description",
            testUser,
            "test-tenant",
            testCreatedTime,
            testLastUpdatedTime,
            testMemoryStorageConfig
        );

        assertEquals("test-name", container.getName());
        assertEquals("test-description", container.getDescription());
        assertEquals(testUser, container.getOwner());
        assertEquals("test-tenant", container.getTenantId());
        assertEquals(testCreatedTime, container.getCreatedTime());
        assertEquals(testLastUpdatedTime, container.getLastUpdatedTime());
        assertEquals(testMemoryStorageConfig, container.getMemoryStorageConfig());
    }

    @Test
    public void testConstructorWithNullValues() {
        MLMemoryContainer container = MLMemoryContainer
            .builder()
            .name(null)
            .description(null)
            .owner(null)
            .tenantId(null)
            .createdTime(null)
            .lastUpdatedTime(null)
            .memoryStorageConfig(null)
            .build();

        assertNull(container.getName());
        assertNull(container.getDescription());
        assertNull(container.getOwner());
        assertNull(container.getTenantId());
        assertNull(container.getCreatedTime());
        assertNull(container.getLastUpdatedTime());
        assertNull(container.getMemoryStorageConfig());
    }

    @Test
    public void testStreamInputOutput() throws IOException {
        BytesStreamOutput bytesStreamOutput = new BytesStreamOutput();
        mlMemoryContainer.writeTo(bytesStreamOutput);

        StreamInput streamInput = bytesStreamOutput.bytes().streamInput();
        MLMemoryContainer parsedContainer = new MLMemoryContainer(streamInput);

        assertEquals(mlMemoryContainer.getName(), parsedContainer.getName());
        assertEquals(mlMemoryContainer.getDescription(), parsedContainer.getDescription());
        assertEquals(mlMemoryContainer.getOwner(), parsedContainer.getOwner());
        assertEquals(mlMemoryContainer.getTenantId(), parsedContainer.getTenantId());
        assertEquals(mlMemoryContainer.getCreatedTime(), parsedContainer.getCreatedTime());
        assertEquals(mlMemoryContainer.getLastUpdatedTime(), parsedContainer.getLastUpdatedTime());
        assertEquals(mlMemoryContainer.getMemoryStorageConfig(), parsedContainer.getMemoryStorageConfig());
    }

    @Test
    public void testStreamInputOutputWithNullValues() throws IOException {
        MLMemoryContainer containerWithNulls = MLMemoryContainer
            .builder()
            .name("test-name")
            .description("test-description")
            .owner(null)
            .tenantId("test-tenant")
            .createdTime(testCreatedTime)
            .lastUpdatedTime(testLastUpdatedTime)
            .memoryStorageConfig(null)
            .build();

        BytesStreamOutput bytesStreamOutput = new BytesStreamOutput();
        containerWithNulls.writeTo(bytesStreamOutput);

        StreamInput streamInput = bytesStreamOutput.bytes().streamInput();
        MLMemoryContainer parsedContainer = new MLMemoryContainer(streamInput);

        assertEquals(containerWithNulls.getName(), parsedContainer.getName());
        assertEquals(containerWithNulls.getDescription(), parsedContainer.getDescription());
        assertNull(parsedContainer.getOwner());
        assertEquals(containerWithNulls.getTenantId(), parsedContainer.getTenantId());
        assertEquals(containerWithNulls.getCreatedTime(), parsedContainer.getCreatedTime());
        assertEquals(containerWithNulls.getLastUpdatedTime(), parsedContainer.getLastUpdatedTime());
        assertNull(parsedContainer.getMemoryStorageConfig());
    }

    @Test
    public void testToXContent() throws IOException {
        XContentBuilder builder = XContentBuilder.builder(XContentType.JSON.xContent());
        mlMemoryContainer.toXContent(builder, EMPTY_PARAMS);
        String jsonStr = TestHelper.xContentBuilderToString(builder);

        assertNotNull(jsonStr);
        // Verify that all fields are present in the JSON
        assert (jsonStr.contains("\"name\":\"test-memory-container\""));
        assert (jsonStr.contains("\"description\":\"Test memory container description\""));
        assert (jsonStr.contains("\"tenant_id\":\"test-tenant\""));
        assert (jsonStr.contains("\"created_time\":" + testCreatedTime.toEpochMilli()));
        assert (jsonStr.contains("\"last_updated_time\":" + testLastUpdatedTime.toEpochMilli()));
        assert (jsonStr.contains("\"memory_storage_config\""));
    }

    @Test
    public void testToXContentWithNullValues() throws IOException {
        MLMemoryContainer containerWithNulls = MLMemoryContainer
            .builder()
            .name("test-name")
            .description(null)
            .owner(null)
            .tenantId(null)
            .createdTime(null)
            .lastUpdatedTime(null)
            .memoryStorageConfig(null)
            .build();

        XContentBuilder builder = XContentBuilder.builder(XContentType.JSON.xContent());
        containerWithNulls.toXContent(builder, EMPTY_PARAMS);
        String jsonStr = TestHelper.xContentBuilderToString(builder);

        assertNotNull(jsonStr);
        assert (jsonStr.contains("\"name\":\"test-name\""));
        // Verify that null fields are not included in JSON
        assert (!jsonStr.contains("\"description\""));
        assert (!jsonStr.contains("\"owner\""));
        assert (!jsonStr.contains("\"tenant_id\""));
        assert (!jsonStr.contains("\"created_time\""));
        assert (!jsonStr.contains("\"last_updated_time\""));
        assert (!jsonStr.contains("\"memory_storage_config\""));
    }

    @Test
    public void testParseFromXContentWithPartialFields() throws IOException {
        String jsonStr = "{\"name\":\"partial-container\",\"description\":\"partial description\"}";

        XContentParser parser = XContentType.JSON
            .xContent()
            .createParser(NamedXContentRegistry.EMPTY, LoggingDeprecationHandler.INSTANCE, jsonStr);
        parser.nextToken();
        MLMemoryContainer parsedContainer = MLMemoryContainer.parse(parser);

        assertEquals("partial-container", parsedContainer.getName());
        assertEquals("partial description", parsedContainer.getDescription());
        assertNull(parsedContainer.getOwner());
        assertNull(parsedContainer.getTenantId());
        assertNull(parsedContainer.getCreatedTime());
        assertNull(parsedContainer.getLastUpdatedTime());
        assertNull(parsedContainer.getMemoryStorageConfig());
    }

    @Test
    public void testParseFromXContentWithUnknownFields() throws IOException {
        String jsonStr = "{\"name\":\"test-container\",\"unknown_field\":\"unknown_value\",\"description\":\"test description\"}";

        XContentParser parser = XContentType.JSON
            .xContent()
            .createParser(NamedXContentRegistry.EMPTY, LoggingDeprecationHandler.INSTANCE, jsonStr);
        parser.nextToken();
        MLMemoryContainer parsedContainer = MLMemoryContainer.parse(parser);

        assertEquals("test-container", parsedContainer.getName());
        assertEquals("test description", parsedContainer.getDescription());
        // Unknown fields should be ignored
        assertNull(parsedContainer.getOwner());
        assertNull(parsedContainer.getTenantId());
    }

    @Test
    public void testParseFromXContentWithTimeFields() throws IOException {
        long createdTimeMillis = testCreatedTime.toEpochMilli();
        long lastUpdatedTimeMillis = testLastUpdatedTime.toEpochMilli();

        String jsonStr = String
            .format(
                "{\"name\":\"time-test-container\",\"created_time\":%d,\"last_updated_time\":%d}",
                createdTimeMillis,
                lastUpdatedTimeMillis
            );

        XContentParser parser = XContentType.JSON
            .xContent()
            .createParser(NamedXContentRegistry.EMPTY, LoggingDeprecationHandler.INSTANCE, jsonStr);
        parser.nextToken();
        MLMemoryContainer parsedContainer = MLMemoryContainer.parse(parser);

        assertEquals("time-test-container", parsedContainer.getName());
        assertEquals(testCreatedTime, parsedContainer.getCreatedTime());
        assertEquals(testLastUpdatedTime, parsedContainer.getLastUpdatedTime());
        assertNull(parsedContainer.getOwner());
        assertNull(parsedContainer.getMemoryStorageConfig());
    }

    @Test
    public void testParseFromXContentWithMemoryStorageConfig() throws IOException {
        // Create a JSON string with memory storage config
        String jsonStr = "{\"name\":\"config-test-container\","
            + "\"memory_storage_config\":{"
            + "\"memory_index_name\":\"test-index\","
            + "\"semantic_storage_enabled\":true,"
            + "\"embedding_model_type\":\"TEXT_EMBEDDING\","
            + "\"embedding_model_id\":\"test-model\","
            + "\"dimension\":512,"
            + "\"max_infer_size\":5"
            + "}}";

        XContentParser parser = XContentType.JSON
            .xContent()
            .createParser(NamedXContentRegistry.EMPTY, LoggingDeprecationHandler.INSTANCE, jsonStr);
        parser.nextToken();
        MLMemoryContainer parsedContainer = MLMemoryContainer.parse(parser);

        assertEquals("config-test-container", parsedContainer.getName());
        assertNotNull(parsedContainer.getMemoryStorageConfig());
        assertEquals("test-index", parsedContainer.getMemoryStorageConfig().getMemoryIndexName());
        assertEquals(true, parsedContainer.getMemoryStorageConfig().isSemanticStorageEnabled());
        assertEquals(FunctionName.TEXT_EMBEDDING, parsedContainer.getMemoryStorageConfig().getEmbeddingModelType());
        assertEquals("test-model", parsedContainer.getMemoryStorageConfig().getEmbeddingModelId());
        assertEquals(Integer.valueOf(512), parsedContainer.getMemoryStorageConfig().getDimension());
        assertNull(parsedContainer.getMemoryStorageConfig().getMaxInferSize()); // No llmModelId, so maxInferSize is null
    }

    @Test
    public void testParseFromXContentCompleteRoundTrip() throws IOException {
        // Test complete round trip: object -> JSON -> parse -> compare
        // Use container without User to avoid parsing issues, but test all other fields
        MLMemoryContainer originalContainer = MLMemoryContainer
            .builder()
            .name("roundtrip-container")
            .description("roundtrip description")
            .owner(null) // Skip User for now due to parsing complexity
            .tenantId("roundtrip-tenant")
            .createdTime(testCreatedTime)
            .lastUpdatedTime(testLastUpdatedTime)
            .memoryStorageConfig(testMemoryStorageConfig)
            .build();

        // Convert to JSON
        XContentBuilder builder = XContentBuilder.builder(XContentType.JSON.xContent());
        originalContainer.toXContent(builder, EMPTY_PARAMS);
        String jsonStr = TestHelper.xContentBuilderToString(builder);

        // Parse back from JSON
        XContentParser parser = XContentType.JSON
            .xContent()
            .createParser(NamedXContentRegistry.EMPTY, LoggingDeprecationHandler.INSTANCE, jsonStr);
        parser.nextToken();
        MLMemoryContainer parsedContainer = MLMemoryContainer.parse(parser);

        // Verify all fields match
        assertEquals(originalContainer.getName(), parsedContainer.getName());
        assertEquals(originalContainer.getDescription(), parsedContainer.getDescription());
        assertEquals(originalContainer.getTenantId(), parsedContainer.getTenantId());
        assertEquals(originalContainer.getCreatedTime(), parsedContainer.getCreatedTime());
        assertEquals(originalContainer.getLastUpdatedTime(), parsedContainer.getLastUpdatedTime());
        assertEquals(originalContainer.getMemoryStorageConfig(), parsedContainer.getMemoryStorageConfig());
    }

    @Test
    public void testParseFromXContentWithUserField() throws IOException {
        // Test parsing with User field using the same approach as other OpenSearch tests
        // Create a container with User and serialize it to see the expected JSON format
        MLMemoryContainer containerWithUser = MLMemoryContainer
            .builder()
            .name("user-test-container")
            .description("test with user")
            .owner(testUser)
            .build();

        // Convert to JSON to see the format
        XContentBuilder builder = XContentBuilder.builder(XContentType.JSON.xContent());
        containerWithUser.toXContent(builder, EMPTY_PARAMS);
        String jsonStr = TestHelper.xContentBuilderToString(builder);

        // Parse back from JSON - this tests the User.parse() call in line 154
        XContentParser parser = XContentType.JSON
            .xContent()
            .createParser(NamedXContentRegistry.EMPTY, LoggingDeprecationHandler.INSTANCE, jsonStr);
        parser.nextToken();
        MLMemoryContainer parsedContainer = MLMemoryContainer.parse(parser);

        // Verify the container was parsed correctly
        assertEquals("user-test-container", parsedContainer.getName());
        assertEquals("test with user", parsedContainer.getDescription());
        assertEquals(testUser, parsedContainer.getOwner());
    }

    @Test
    public void testEqualsAndHashCode() {
        MLMemoryContainer container1 = MLMemoryContainer
            .builder()
            .name("test-container")
            .description("test description")
            .owner(testUser)
            .tenantId("test-tenant")
            .createdTime(testCreatedTime)
            .lastUpdatedTime(testLastUpdatedTime)
            .memoryStorageConfig(testMemoryStorageConfig)
            .build();

        MLMemoryContainer container2 = MLMemoryContainer
            .builder()
            .name("test-container")
            .description("test description")
            .owner(testUser)
            .tenantId("test-tenant")
            .createdTime(testCreatedTime)
            .lastUpdatedTime(testLastUpdatedTime)
            .memoryStorageConfig(testMemoryStorageConfig)
            .build();

        MLMemoryContainer container3 = MLMemoryContainer
            .builder()
            .name("different-container")
            .description("test description")
            .owner(testUser)
            .tenantId("test-tenant")
            .createdTime(testCreatedTime)
            .lastUpdatedTime(testLastUpdatedTime)
            .memoryStorageConfig(testMemoryStorageConfig)
            .build();

        assertEquals(container1, container2);
        assertEquals(container1.hashCode(), container2.hashCode());
        assert (!container1.equals(container3));
        assert (container1.hashCode() != container3.hashCode());
    }

    @Test
    public void testSettersAndGetters() {
        MLMemoryContainer container = new MLMemoryContainer(null, null, null, null, null, null, null);

        container.setName("new-name");
        container.setDescription("new-description");
        container.setOwner(testUser);
        container.setTenantId("new-tenant");
        container.setCreatedTime(testCreatedTime);
        container.setLastUpdatedTime(testLastUpdatedTime);
        container.setMemoryStorageConfig(testMemoryStorageConfig);

        assertEquals("new-name", container.getName());
        assertEquals("new-description", container.getDescription());
        assertEquals(testUser, container.getOwner());
        assertEquals("new-tenant", container.getTenantId());
        assertEquals(testCreatedTime, container.getCreatedTime());
        assertEquals(testLastUpdatedTime, container.getLastUpdatedTime());
        assertEquals(testMemoryStorageConfig, container.getMemoryStorageConfig());
    }
}
