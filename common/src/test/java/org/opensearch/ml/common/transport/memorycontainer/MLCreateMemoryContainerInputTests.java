/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.memorycontainer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.opensearch.core.xcontent.ToXContent.EMPTY_PARAMS;

import java.io.IOException;

import org.junit.Before;
import org.junit.Test;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.common.xcontent.LoggingDeprecationHandler;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.TestHelper;
import org.opensearch.ml.common.memorycontainer.MemoryStorageConfig;

public class MLCreateMemoryContainerInputTests {

    private MLCreateMemoryContainerInput inputWithAllFields;
    private MLCreateMemoryContainerInput inputMinimal;
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

        // Input with all fields
        inputWithAllFields = MLCreateMemoryContainerInput
            .builder()
            .name("test-memory-container")
            .description("Test memory container description")
            .memoryStorageConfig(testMemoryStorageConfig)
            .tenantId("test-tenant")
            .build();

        // Minimal input (only required fields)
        inputMinimal = MLCreateMemoryContainerInput.builder().name("minimal-container").build();
    }

    @Test
    public void testConstructorWithBuilder() {
        assertNotNull(inputWithAllFields);
        assertEquals("test-memory-container", inputWithAllFields.getName());
        assertEquals("Test memory container description", inputWithAllFields.getDescription());
        assertEquals(testMemoryStorageConfig, inputWithAllFields.getMemoryStorageConfig());
        assertEquals("test-tenant", inputWithAllFields.getTenantId());
    }

    @Test
    public void testConstructorWithBuilderMinimal() {
        assertNotNull(inputMinimal);
        assertEquals("minimal-container", inputMinimal.getName());
        assertNull(inputMinimal.getDescription());
        assertNull(inputMinimal.getMemoryStorageConfig());
        assertNull(inputMinimal.getTenantId());
    }

    @Test
    public void testConstructorWithAllParameters() {
        MLCreateMemoryContainerInput input = new MLCreateMemoryContainerInput(
            "param-container",
            "param description",
            testMemoryStorageConfig,
            "param-tenant"
        );

        assertEquals("param-container", input.getName());
        assertEquals("param description", input.getDescription());
        assertEquals(testMemoryStorageConfig, input.getMemoryStorageConfig());
        assertEquals("param-tenant", input.getTenantId());
    }

    @Test
    public void testConstructorWithNullOptionalFields() {
        MLCreateMemoryContainerInput input = new MLCreateMemoryContainerInput("null-optional-container", null, null, null);

        assertEquals("null-optional-container", input.getName());
        assertNull(input.getDescription());
        assertNull(input.getMemoryStorageConfig());
        assertNull(input.getTenantId());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testConstructorWithNullName() {
        MLCreateMemoryContainerInput
            .builder()
            .name(null) // This should throw IllegalArgumentException
            .description("test description")
            .build();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testConstructorWithNullNameDirectConstructor() {
        new MLCreateMemoryContainerInput(
            null, // This should throw IllegalArgumentException
            "test description",
            testMemoryStorageConfig,
            "test-tenant"
        );
    }

    @Test
    public void testStreamInputOutput() throws IOException {
        BytesStreamOutput bytesStreamOutput = new BytesStreamOutput();
        inputWithAllFields.writeTo(bytesStreamOutput);

        StreamInput streamInput = bytesStreamOutput.bytes().streamInput();
        MLCreateMemoryContainerInput parsedInput = new MLCreateMemoryContainerInput(streamInput);

        assertEquals(inputWithAllFields.getName(), parsedInput.getName());
        assertEquals(inputWithAllFields.getDescription(), parsedInput.getDescription());
        assertEquals(inputWithAllFields.getMemoryStorageConfig(), parsedInput.getMemoryStorageConfig());
        assertEquals(inputWithAllFields.getTenantId(), parsedInput.getTenantId());
    }

    @Test
    public void testStreamInputOutputWithNullValues() throws IOException {
        BytesStreamOutput bytesStreamOutput = new BytesStreamOutput();
        inputMinimal.writeTo(bytesStreamOutput);

        StreamInput streamInput = bytesStreamOutput.bytes().streamInput();
        MLCreateMemoryContainerInput parsedInput = new MLCreateMemoryContainerInput(streamInput);

        assertEquals(inputMinimal.getName(), parsedInput.getName());
        assertNull(parsedInput.getDescription());
        assertNull(parsedInput.getMemoryStorageConfig());
        assertNull(parsedInput.getTenantId());
    }

    @Test
    public void testToXContentWithAllFields() throws IOException {
        XContentBuilder builder = XContentBuilder.builder(XContentType.JSON.xContent());
        inputWithAllFields.toXContent(builder, EMPTY_PARAMS);
        String jsonStr = TestHelper.xContentBuilderToString(builder);

        assertNotNull(jsonStr);
        // Verify all fields are present in the JSON
        assertTrue(jsonStr.contains("\"name\":\"test-memory-container\""));
        assertTrue(jsonStr.contains("\"description\":\"Test memory container description\""));
        assertTrue(jsonStr.contains("\"tenant_id\":\"test-tenant\""));
        assertTrue(jsonStr.contains("\"memory_storage_config\""));
        // Verify memory storage config fields are nested
        assertTrue(jsonStr.contains("\"memory_index_name\":\"test-memory-index\""));
        assertTrue(jsonStr.contains("\"embedding_model_type\":\"TEXT_EMBEDDING\""));
    }

    @Test
    public void testToXContentWithMinimalFields() throws IOException {
        XContentBuilder builder = XContentBuilder.builder(XContentType.JSON.xContent());
        inputMinimal.toXContent(builder, EMPTY_PARAMS);
        String jsonStr = TestHelper.xContentBuilderToString(builder);

        assertNotNull(jsonStr);
        // Verify only required fields are present
        assertTrue(jsonStr.contains("\"name\":\"minimal-container\""));
        // Verify optional fields are not present
        assertFalse(jsonStr.contains("\"description\""));
        assertFalse(jsonStr.contains("\"memory_storage_config\""));
        assertFalse(jsonStr.contains("\"tenant_id\""));
    }

    @Test
    public void testParseFromXContentWithAllFields() throws IOException {
        String jsonStr = "{"
            + "\"name\":\"parsed-container\","
            + "\"description\":\"parsed description\","
            + "\"tenant_id\":\"parsed-tenant\","
            + "\"memory_storage_config\":{"
            + "\"memory_index_name\":\"parsed-index\","
            + "\"embedding_model_type\":\"TEXT_EMBEDDING\","
            + "\"embedding_model_id\":\"parsed-embedding-model\","
            + "\"llm_model_id\":\"parsed-llm-model\","
            + "\"dimension\":512,"
            + "\"max_infer_size\":7"
            + "}"
            + "}";

        XContentParser parser = XContentType.JSON
            .xContent()
            .createParser(NamedXContentRegistry.EMPTY, LoggingDeprecationHandler.INSTANCE, jsonStr);
        parser.nextToken();
        MLCreateMemoryContainerInput parsedInput = MLCreateMemoryContainerInput.parse(parser);

        assertEquals("parsed-container", parsedInput.getName());
        assertEquals("parsed description", parsedInput.getDescription());
        assertEquals("parsed-tenant", parsedInput.getTenantId());
        assertNotNull(parsedInput.getMemoryStorageConfig());
        assertEquals("parsed-index", parsedInput.getMemoryStorageConfig().getMemoryIndexName());
        assertEquals(FunctionName.TEXT_EMBEDDING, parsedInput.getMemoryStorageConfig().getEmbeddingModelType());
        assertEquals("parsed-embedding-model", parsedInput.getMemoryStorageConfig().getEmbeddingModelId());
        assertEquals("parsed-llm-model", parsedInput.getMemoryStorageConfig().getLlmModelId());
        assertEquals(Integer.valueOf(512), parsedInput.getMemoryStorageConfig().getDimension());
        assertEquals(Integer.valueOf(7), parsedInput.getMemoryStorageConfig().getMaxInferSize());
    }

    @Test
    public void testParseFromXContentWithMinimalFields() throws IOException {
        String jsonStr = "{\"name\":\"minimal-parsed-container\"}";

        XContentParser parser = XContentType.JSON
            .xContent()
            .createParser(NamedXContentRegistry.EMPTY, LoggingDeprecationHandler.INSTANCE, jsonStr);
        parser.nextToken();
        MLCreateMemoryContainerInput parsedInput = MLCreateMemoryContainerInput.parse(parser);

        assertEquals("minimal-parsed-container", parsedInput.getName());
        assertNull(parsedInput.getDescription());
        assertNull(parsedInput.getMemoryStorageConfig());
        assertNull(parsedInput.getTenantId());
    }

    @Test
    public void testParseFromXContentWithUnknownFields() throws IOException {
        String jsonStr = "{"
            + "\"name\":\"unknown-fields-container\","
            + "\"unknown_field\":\"unknown_value\","
            + "\"description\":\"test description\","
            + "\"another_unknown\":123"
            + "}";

        XContentParser parser = XContentType.JSON
            .xContent()
            .createParser(NamedXContentRegistry.EMPTY, LoggingDeprecationHandler.INSTANCE, jsonStr);
        parser.nextToken();
        MLCreateMemoryContainerInput parsedInput = MLCreateMemoryContainerInput.parse(parser);

        assertEquals("unknown-fields-container", parsedInput.getName());
        assertEquals("test description", parsedInput.getDescription());
        // Unknown fields should be ignored
        assertNull(parsedInput.getMemoryStorageConfig());
        assertNull(parsedInput.getTenantId());
    }

    @Test
    public void testCompleteRoundTrip() throws IOException {
        // Test complete round trip: object -> JSON -> parse -> compare
        XContentBuilder builder = XContentBuilder.builder(XContentType.JSON.xContent());
        inputWithAllFields.toXContent(builder, EMPTY_PARAMS);
        String jsonStr = TestHelper.xContentBuilderToString(builder);

        XContentParser parser = XContentType.JSON
            .xContent()
            .createParser(NamedXContentRegistry.EMPTY, LoggingDeprecationHandler.INSTANCE, jsonStr);
        parser.nextToken();
        MLCreateMemoryContainerInput parsedInput = MLCreateMemoryContainerInput.parse(parser);

        assertEquals(inputWithAllFields.getName(), parsedInput.getName());
        assertEquals(inputWithAllFields.getDescription(), parsedInput.getDescription());
        assertEquals(inputWithAllFields.getTenantId(), parsedInput.getTenantId());
        assertEquals(inputWithAllFields.getMemoryStorageConfig(), parsedInput.getMemoryStorageConfig());
    }

    @Test
    public void testEqualsAndHashCode() {
        MLCreateMemoryContainerInput input1 = MLCreateMemoryContainerInput
            .builder()
            .name("test-container")
            .description("test description")
            .memoryStorageConfig(testMemoryStorageConfig)
            .tenantId("test-tenant")
            .build();

        MLCreateMemoryContainerInput input2 = MLCreateMemoryContainerInput
            .builder()
            .name("test-container")
            .description("test description")
            .memoryStorageConfig(testMemoryStorageConfig)
            .tenantId("test-tenant")
            .build();

        MLCreateMemoryContainerInput input3 = MLCreateMemoryContainerInput
            .builder()
            .name("different-container")
            .description("test description")
            .memoryStorageConfig(testMemoryStorageConfig)
            .tenantId("test-tenant")
            .build();

        assertEquals(input1, input2);
        assertEquals(input1.hashCode(), input2.hashCode());
        assertFalse(input1.equals(input3));
        assertTrue(input1.hashCode() != input3.hashCode());
    }

    @Test
    public void testSettersAndGetters() {
        MLCreateMemoryContainerInput input = MLCreateMemoryContainerInput.builder().name("initial-name").build();

        // Test setters
        input.setName("new-name");
        input.setDescription("new-description");
        input.setMemoryStorageConfig(testMemoryStorageConfig);
        input.setTenantId("new-tenant");

        // Test getters
        assertEquals("new-name", input.getName());
        assertEquals("new-description", input.getDescription());
        assertEquals(testMemoryStorageConfig, input.getMemoryStorageConfig());
        assertEquals("new-tenant", input.getTenantId());
    }

    @Test
    public void testToBuilder() {
        MLCreateMemoryContainerInput modifiedInput = inputWithAllFields
            .toBuilder()
            .name("modified-name")
            .description("modified description")
            .build();

        assertEquals("modified-name", modifiedInput.getName());
        assertEquals("modified description", modifiedInput.getDescription());
        // Other fields should remain the same
        assertEquals(inputWithAllFields.getMemoryStorageConfig(), modifiedInput.getMemoryStorageConfig());
        assertEquals(inputWithAllFields.getTenantId(), modifiedInput.getTenantId());
    }

    @Test
    public void testFieldConstants() {
        // Test that field constants are correctly defined
        assertEquals("name", MLCreateMemoryContainerInput.NAME_FIELD);
        assertEquals("description", MLCreateMemoryContainerInput.DESCRIPTION_FIELD);
        assertEquals("memory_storage_config", MLCreateMemoryContainerInput.MEMORY_STORAGE_CONFIG_FIELD);
    }

    @Test
    public void testParseFromXContentWithPartialMemoryStorageConfig() throws IOException {
        String jsonStr = "{"
            + "\"name\":\"partial-config-container\","
            + "\"description\":\"test with partial config\","
            + "\"memory_storage_config\":{"
            + "\"memory_index_name\":\"partial-index\","
            + "\"llm_model_id\":\"partial-llm-model\""
            + "}"
            + "}";

        XContentParser parser = XContentType.JSON
            .xContent()
            .createParser(NamedXContentRegistry.EMPTY, LoggingDeprecationHandler.INSTANCE, jsonStr);
        parser.nextToken();
        MLCreateMemoryContainerInput parsedInput = MLCreateMemoryContainerInput.parse(parser);

        assertEquals("partial-config-container", parsedInput.getName());
        assertEquals("test with partial config", parsedInput.getDescription());
        assertNotNull(parsedInput.getMemoryStorageConfig());
        assertEquals("partial-index", parsedInput.getMemoryStorageConfig().getMemoryIndexName());
        assertEquals("partial-llm-model", parsedInput.getMemoryStorageConfig().getLlmModelId());
        // Semantic storage should be disabled due to missing embedding config
        assertFalse(parsedInput.getMemoryStorageConfig().isSemanticStorageEnabled());
    }

    @Test
    public void testDataAnnotationFunctionality() {
        // Test that @Data annotation provides toString, equals, hashCode
        String toString = inputWithAllFields.toString();
        assertNotNull(toString);
        assertTrue(toString.contains("test-memory-container"));
        assertTrue(toString.contains("Test memory container description"));
        assertTrue(toString.contains("test-tenant"));
    }

    // Helper method for assertions
    private void assertTrue(boolean condition) {
        org.junit.Assert.assertTrue(condition);
    }

    private void assertFalse(boolean condition) {
        org.junit.Assert.assertFalse(condition);
    }
}
