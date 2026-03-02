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
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.opensearch.OpenSearchParseException;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.common.xcontent.LoggingDeprecationHandler;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.TestHelper;
import org.opensearch.ml.common.memorycontainer.MemoryConfiguration;

public class MLCreateMemoryContainerInputTests {

    private MLCreateMemoryContainerInput inputWithAllFields;
    private MLCreateMemoryContainerInput inputMinimal;
    private MemoryConfiguration testMemoryStorageConfig;

    @Before
    public void setUp() {
        // Create test memory storage config
        testMemoryStorageConfig = MemoryConfiguration
            .builder()
            .indexPrefix("test-memory-index")
            .embeddingModelType(FunctionName.TEXT_EMBEDDING)
            .embeddingModelId("test-embedding-model")
            .llmId("test-llm-model")
            .dimension(768)
            .maxInferSize(8)
            .build();

        // Input with all fields
        inputWithAllFields = MLCreateMemoryContainerInput
            .builder()
            .name("test-memory-container")
            .description("Test memory container description")
            .configuration(testMemoryStorageConfig)
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
        assertEquals(testMemoryStorageConfig, inputWithAllFields.getConfiguration());
        assertEquals("test-tenant", inputWithAllFields.getTenantId());
    }

    @Test
    public void testConstructorWithBuilderMinimal() {
        assertNotNull(inputMinimal);
        assertEquals("minimal-container", inputMinimal.getName());
        assertNull(inputMinimal.getDescription());
        assertNotNull(inputMinimal.getConfiguration());
        assertNull(inputMinimal.getTenantId());
    }

    @Test
    public void testConstructorWithAllParameters() {
        MLCreateMemoryContainerInput input = MLCreateMemoryContainerInput
            .builder()
            .name("param-container")
            .description("param description")
            .configuration(testMemoryStorageConfig)
            .tenantId("param-tenant")
            .build();

        assertEquals("param-container", input.getName());
        assertEquals("param description", input.getDescription());
        assertEquals(testMemoryStorageConfig, input.getConfiguration());
        assertEquals("param-tenant", input.getTenantId());
    }

    @Test
    public void testConstructorWithNullOptionalFields() {
        MLCreateMemoryContainerInput input = MLCreateMemoryContainerInput
            .builder()
            .name("null-optional-container")
            .description(null)
            .configuration(null)
            .tenantId(null)
            .build();

        assertEquals("null-optional-container", input.getName());
        assertNull(input.getDescription());
        assertNotNull(input.getConfiguration());
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
        MLCreateMemoryContainerInput
            .builder()
            .name(null)
            .description("param description")
            .configuration(testMemoryStorageConfig)
            .tenantId("test-tenant")
            .build();
    }

    @Test
    public void testStreamInputOutput() throws IOException {
        BytesStreamOutput bytesStreamOutput = new BytesStreamOutput();
        inputWithAllFields.writeTo(bytesStreamOutput);

        StreamInput streamInput = bytesStreamOutput.bytes().streamInput();
        MLCreateMemoryContainerInput parsedInput = new MLCreateMemoryContainerInput(streamInput);

        assertEquals(inputWithAllFields.getName(), parsedInput.getName());
        assertEquals(inputWithAllFields.getDescription(), parsedInput.getDescription());
        assertEquals(inputWithAllFields.getConfiguration().getIndexPrefix(), parsedInput.getConfiguration().getIndexPrefix());
        assertEquals(inputWithAllFields.getConfiguration().getEmbeddingModelId(), parsedInput.getConfiguration().getEmbeddingModelId());
        assertEquals(inputWithAllFields.getConfiguration().getEmbeddingModelType(), parsedInput.getConfiguration().getEmbeddingModelType());
        assertEquals(inputWithAllFields.getConfiguration().getDimension(), parsedInput.getConfiguration().getDimension());
        assertEquals(inputWithAllFields.getConfiguration().getMaxInferSize(), parsedInput.getConfiguration().getMaxInferSize());
        assertEquals(inputWithAllFields.getConfiguration().getLlmId(), parsedInput.getConfiguration().getLlmId());
        assertEquals(inputWithAllFields.getConfiguration().isDisableHistory(), parsedInput.getConfiguration().isDisableHistory());
        assertEquals(inputWithAllFields.getConfiguration().isDisableSession(), parsedInput.getConfiguration().isDisableSession());
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
        assertNotNull(parsedInput.getConfiguration());
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
        assertTrue(jsonStr.contains("\"configuration\""));
        // Verify memory storage config fields are nested
        assertTrue(jsonStr.contains("\"index_prefix\":\"test-memory-index\""));
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
        assertTrue(jsonStr.contains("\"configuration\""));
        assertFalse(jsonStr.contains("\"tenant_id\""));
    }

    @Test
    public void testParseFromXContentWithAllFields() throws IOException {
        String jsonStr = "{"
            + "\"name\":\"parsed-container\","
            + "\"description\":\"parsed description\","
            + "\"tenant_id\":\"parsed-tenant\","
            + "\"configuration\":{"
            + "\"index_prefix\":\"parsed-index\","
            + "\"embedding_model_type\":\"TEXT_EMBEDDING\","
            + "\"embedding_model_id\":\"parsed-embedding-model\","
            + "\"llm_id\":\"parsed-llm-model\","
            + "\"embedding_dimension\":512,"
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
        assertNotNull(parsedInput.getConfiguration());
        assertEquals("parsed-index", parsedInput.getConfiguration().getIndexPrefix());
        assertEquals(FunctionName.TEXT_EMBEDDING, parsedInput.getConfiguration().getEmbeddingModelType());
        assertEquals("parsed-embedding-model", parsedInput.getConfiguration().getEmbeddingModelId());
        assertEquals("parsed-llm-model", parsedInput.getConfiguration().getLlmId());
        assertEquals(Integer.valueOf(512), parsedInput.getConfiguration().getDimension());
        assertEquals(Integer.valueOf(7), parsedInput.getConfiguration().getMaxInferSize());
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
        assertNotNull(parsedInput.getConfiguration());
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
        assertNotNull(parsedInput.getConfiguration());
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
        assertEquals(inputWithAllFields.getConfiguration(), parsedInput.getConfiguration());
    }

    @Test
    public void testEqualsAndHashCode() {
        MLCreateMemoryContainerInput input1 = MLCreateMemoryContainerInput
            .builder()
            .name("test-container")
            .description("test description")
            .configuration(testMemoryStorageConfig)
            .tenantId("test-tenant")
            .build();

        MLCreateMemoryContainerInput input2 = MLCreateMemoryContainerInput
            .builder()
            .name("test-container")
            .description("test description")
            .configuration(testMemoryStorageConfig)
            .tenantId("test-tenant")
            .build();

        MLCreateMemoryContainerInput input3 = MLCreateMemoryContainerInput
            .builder()
            .name("different-container")
            .description("test description")
            .configuration(testMemoryStorageConfig)
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
        input.setConfiguration(testMemoryStorageConfig);
        input.setTenantId("new-tenant");

        // Test getters
        assertEquals("new-name", input.getName());
        assertEquals("new-description", input.getDescription());
        assertEquals(testMemoryStorageConfig, input.getConfiguration());
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
        assertEquals(inputWithAllFields.getConfiguration(), modifiedInput.getConfiguration());
        assertEquals(inputWithAllFields.getTenantId(), modifiedInput.getTenantId());
    }

    @Test
    public void testFieldConstants() {
        // Test that field constants are correctly defined
        assertEquals("name", MLCreateMemoryContainerInput.NAME_FIELD);
        assertEquals("description", MLCreateMemoryContainerInput.DESCRIPTION_FIELD);
        assertEquals("configuration", MLCreateMemoryContainerInput.MEMORY_CONFIG_FIELD);
    }

    @Test
    public void testParseFromXContentWithPartialMemoryStorageConfig() throws IOException {
        String jsonStr = "{"
            + "\"name\":\"partial-config-container\","
            + "\"description\":\"test with partial config\","
            + "\"configuration\":{"
            + "\"index_prefix\":\"partial-index\","
            + "\"llm_id\":\"partial-llm-model\""
            + "}"
            + "}";

        XContentParser parser = XContentType.JSON
            .xContent()
            .createParser(NamedXContentRegistry.EMPTY, LoggingDeprecationHandler.INSTANCE, jsonStr);
        parser.nextToken();
        MLCreateMemoryContainerInput parsedInput = MLCreateMemoryContainerInput.parse(parser);

        assertEquals("partial-config-container", parsedInput.getName());
        assertEquals("test with partial config", parsedInput.getDescription());
        assertNotNull(parsedInput.getConfiguration());
        assertEquals("partial-index", parsedInput.getConfiguration().getIndexPrefix());
        assertEquals("partial-llm-model", parsedInput.getConfiguration().getLlmId());
        // Semantic storage should be disabled due to missing embedding config
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

    // Backend Roles Validation Tests

    @Test
    public void testValidateBackendRoles_ValidRoles() {
        // Test various valid backend roles
        List<String> validRoles = Arrays
            .asList(
                "admin",
                "user123",
                "team:developers",
                "role+test",
                "email@domain.com",
                "path/to/resource",
                "key=value",
                "role-name_test",
                "config.property",
                "complex:role+name@test.com/path_to-resource=value"
            );

        MLCreateMemoryContainerInput input = MLCreateMemoryContainerInput.builder().name("test-container").backendRoles(validRoles).build();

        assertNotNull(input);
        assertEquals(validRoles, input.getBackendRoles());
    }

    @Test
    public void testValidateBackendRoles_UnicodeCharacters() {
        // Test Unicode alphanumeric characters
        List<String> unicodeRoles = Arrays.asList("用户角色", "роль", "角色123");

        MLCreateMemoryContainerInput input = MLCreateMemoryContainerInput
            .builder()
            .name("test-container")
            .backendRoles(unicodeRoles)
            .build();

        assertNotNull(input);
        assertEquals(unicodeRoles, input.getBackendRoles());
    }

    @Test
    public void testValidateBackendRoles_NullList() {
        // Null list should be allowed
        MLCreateMemoryContainerInput input = MLCreateMemoryContainerInput.builder().name("test-container").backendRoles(null).build();

        assertNotNull(input);
        assertNull(input.getBackendRoles());
    }

    @Test
    public void testValidateBackendRoles_EmptyList() {
        // Empty list should be allowed
        List<String> emptyList = Collections.emptyList();

        MLCreateMemoryContainerInput input = MLCreateMemoryContainerInput.builder().name("test-container").backendRoles(emptyList).build();

        assertNotNull(input);
        assertEquals(emptyList, input.getBackendRoles());
    }

    @Test
    public void testValidateBackendRoles_Exactly128Characters() {
        // Edge case: exactly 128 characters should be valid
        String exactly128 = "a".repeat(128);
        List<String> roles = Arrays.asList(exactly128);

        MLCreateMemoryContainerInput input = MLCreateMemoryContainerInput.builder().name("test-container").backendRoles(roles).build();

        assertNotNull(input);
        assertEquals(roles, input.getBackendRoles());
    }

    @Test(expected = OpenSearchParseException.class)
    public void testValidateBackendRoles_TooLong() {
        // 129 characters - should fail
        String tooLong = "a".repeat(129);
        List<String> roles = Arrays.asList(tooLong);

        MLCreateMemoryContainerInput.builder().name("test-container").backendRoles(roles).build();
    }

    @Test(expected = OpenSearchParseException.class)
    public void testValidateBackendRoles_WithSpaces() {
        // Spaces are not allowed
        List<String> roles = Arrays.asList("role with spaces");

        MLCreateMemoryContainerInput.builder().name("test-container").backendRoles(roles).build();
    }

    @Test(expected = OpenSearchParseException.class)
    public void testValidateBackendRoles_WithTabs() {
        // Tabs are not allowed
        List<String> roles = Arrays.asList("role\twith\ttabs");

        MLCreateMemoryContainerInput.builder().name("test-container").backendRoles(roles).build();
    }

    @Test(expected = OpenSearchParseException.class)
    public void testValidateBackendRoles_WithSemicolon() {
        // Semicolon is not allowed
        List<String> roles = Arrays.asList("role;semicolon");

        MLCreateMemoryContainerInput.builder().name("test-container").backendRoles(roles).build();
    }

    @Test(expected = OpenSearchParseException.class)
    public void testValidateBackendRoles_WithPipe() {
        // Pipe is not allowed
        List<String> roles = Arrays.asList("role|pipe");

        MLCreateMemoryContainerInput.builder().name("test-container").backendRoles(roles).build();
    }

    @Test(expected = OpenSearchParseException.class)
    public void testValidateBackendRoles_WithBackslash() {
        // Backslash is not allowed
        List<String> roles = Arrays.asList("role\\backslash");

        MLCreateMemoryContainerInput.builder().name("test-container").backendRoles(roles).build();
    }

    @Test(expected = OpenSearchParseException.class)
    public void testValidateBackendRoles_WithHash() {
        // Hash is not allowed
        List<String> roles = Arrays.asList("role#hash");

        MLCreateMemoryContainerInput.builder().name("test-container").backendRoles(roles).build();
    }

    @Test(expected = OpenSearchParseException.class)
    public void testValidateBackendRoles_EmptyString() {
        // Empty string should be rejected
        List<String> roles = Arrays.asList("");

        MLCreateMemoryContainerInput.builder().name("test-container").backendRoles(roles).build();
    }

    @Test(expected = OpenSearchParseException.class)
    public void testValidateBackendRoles_BlankString() {
        // Blank string (only whitespace) should be rejected
        List<String> roles = Arrays.asList("   ");

        MLCreateMemoryContainerInput.builder().name("test-container").backendRoles(roles).build();
    }

    @Test(expected = OpenSearchParseException.class)
    public void testValidateBackendRoles_NullElement() {
        // Null element in list should be rejected
        List<String> roles = Arrays.asList("valid-role", null, "another-role");

        MLCreateMemoryContainerInput.builder().name("test-container").backendRoles(roles).build();
    }

    @Test(expected = OpenSearchParseException.class)
    public void testValidateBackendRoles_WithAsterisk() {
        // Asterisk is not allowed
        List<String> roles = Arrays.asList("role*wildcard");

        MLCreateMemoryContainerInput.builder().name("test-container").backendRoles(roles).build();
    }

    @Test(expected = OpenSearchParseException.class)
    public void testValidateBackendRoles_WithParentheses() {
        // Parentheses are not allowed
        List<String> roles = Arrays.asList("role(parens)");

        MLCreateMemoryContainerInput.builder().name("test-container").backendRoles(roles).build();
    }

    @Test
    public void testValidateBackendRoles_MixedValidRoles() {
        // Test combination of different valid patterns
        List<String> mixedRoles = Arrays
            .asList("admin", "user:123", "team+dev", "email@test.com", "path/to/role", "key=val", "name-test_role", "config.yml");

        MLCreateMemoryContainerInput input = MLCreateMemoryContainerInput.builder().name("test-container").backendRoles(mixedRoles).build();

        assertNotNull(input);
        assertEquals(mixedRoles, input.getBackendRoles());
    }
}
