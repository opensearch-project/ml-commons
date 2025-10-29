/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.memorycontainer.memory;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;
import org.opensearch.OpenSearchParseException;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.xcontent.MediaTypeRegistry;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.memorycontainer.MemoryConfiguration;
import org.opensearch.ml.common.memorycontainer.MemoryStrategy;
import org.opensearch.ml.common.memorycontainer.MemoryStrategyType;

public class MLUpdateMemoryContainerInputTests {

    @Test
    public void testConstructor() {
        List<String> backendRoles = Arrays.asList("role1", "role2");
        List<MemoryStrategy> strategies = Arrays
            .asList(
                MemoryStrategy
                    .builder()
                    .id("semantic_123")
                    .enabled(true)
                    .type(MemoryStrategyType.SEMANTIC)
                    .namespace(Arrays.asList("user_id"))
                    .strategyConfig(new HashMap<>())
                    .build()
            );

        MemoryConfiguration configuration = MemoryConfiguration.builder().strategies(strategies).llmId("llm-123").build();

        MLUpdateMemoryContainerInput input = MLUpdateMemoryContainerInput
            .builder()
            .name("test container")
            .description("test description")
            .backendRoles(backendRoles)
            .configuration(configuration)
            .build();

        assertEquals("test container", input.getName());
        assertEquals("test description", input.getDescription());
        assertEquals(backendRoles, input.getBackendRoles());
        assertEquals(configuration, input.getConfiguration());
    }

    @Test
    public void testStreamSerialization() throws IOException {
        List<MemoryStrategy> strategies = Arrays
            .asList(
                MemoryStrategy
                    .builder()
                    .id("semantic_123")
                    .enabled(true)
                    .type(MemoryStrategyType.SEMANTIC)
                    .namespace(Arrays.asList("user_id"))
                    .strategyConfig(new HashMap<>())
                    .build(),
                MemoryStrategy
                    .builder()
                    .id("user_pref_456")
                    .enabled(false)
                    .type(MemoryStrategyType.USER_PREFERENCE)
                    .namespace(Arrays.asList("user_id", "session_id"))
                    .strategyConfig(new HashMap<>())
                    .build()
            );

        MemoryConfiguration configuration = MemoryConfiguration.builder().strategies(strategies).llmId("llm-model").build();

        MLUpdateMemoryContainerInput original = MLUpdateMemoryContainerInput
            .builder()
            .name("test")
            .description("desc")
            .backendRoles(Arrays.asList("role1"))
            .configuration(configuration)
            .build();

        BytesStreamOutput output = new BytesStreamOutput();
        original.writeTo(output);

        StreamInput input = output.bytes().streamInput();
        MLUpdateMemoryContainerInput deserialized = new MLUpdateMemoryContainerInput(input);

        assertEquals(original.getName(), deserialized.getName());
        assertEquals(original.getDescription(), deserialized.getDescription());
        assertEquals(original.getBackendRoles(), deserialized.getBackendRoles());
        assertNotNull(deserialized.getConfiguration());
        assertEquals(original.getConfiguration().getLlmId(), deserialized.getConfiguration().getLlmId());
        assertEquals(original.getConfiguration().getStrategies().size(), deserialized.getConfiguration().getStrategies().size());
        for (int i = 0; i < original.getConfiguration().getStrategies().size(); i++) {
            assertEquals(
                original.getConfiguration().getStrategies().get(i).getId(),
                deserialized.getConfiguration().getStrategies().get(i).getId()
            );
        }
    }

    @Test
    public void testStreamSerializationWithNullConfiguration() throws IOException {
        MLUpdateMemoryContainerInput original = MLUpdateMemoryContainerInput
            .builder()
            .name("test")
            .description("desc")
            .backendRoles(Arrays.asList("role1"))
            .configuration(null)
            .build();

        BytesStreamOutput output = new BytesStreamOutput();
        original.writeTo(output);

        StreamInput input = output.bytes().streamInput();
        MLUpdateMemoryContainerInput deserialized = new MLUpdateMemoryContainerInput(input);

        assertEquals(original.getName(), deserialized.getName());
        assertNull(deserialized.getConfiguration());
    }

    @Test
    public void testToXContent() throws IOException {
        Map<String, Object> strategyConfig = new HashMap<>();
        strategyConfig.put("threshold", 0.8);

        List<MemoryStrategy> strategies = Arrays
            .asList(
                MemoryStrategy
                    .builder()
                    .id("semantic_123")
                    .enabled(true)
                    .type(MemoryStrategyType.SEMANTIC)
                    .namespace(Arrays.asList("user_id"))
                    .strategyConfig(strategyConfig)
                    .build()
            );

        MemoryConfiguration configuration = MemoryConfiguration.builder().strategies(strategies).llmId("llm-abc").build();

        MLUpdateMemoryContainerInput input = MLUpdateMemoryContainerInput
            .builder()
            .name("test container")
            .description("test description")
            .backendRoles(Arrays.asList("role1", "role2"))
            .configuration(configuration)
            .build();

        XContentBuilder builder = MediaTypeRegistry.contentBuilder(XContentType.JSON);
        input.toXContent(builder, ToXContent.EMPTY_PARAMS);
        String jsonStr = builder.toString();

        assertTrue(jsonStr.contains("\"name\":\"test container\""));
        assertTrue(jsonStr.contains("\"description\":\"test description\""));
        assertTrue(jsonStr.contains("\"backend_roles\":[\"role1\",\"role2\"]"));
        assertTrue(jsonStr.contains("\"configuration\""));
        assertTrue(jsonStr.contains("\"strategies\""));
    }

    @Test
    public void testToXContentWithNullConfiguration() throws IOException {
        MLUpdateMemoryContainerInput input = MLUpdateMemoryContainerInput
            .builder()
            .name("test container")
            .description("test description")
            .backendRoles(Arrays.asList("role1"))
            .build();

        XContentBuilder builder = MediaTypeRegistry.contentBuilder(XContentType.JSON);
        input.toXContent(builder, ToXContent.EMPTY_PARAMS);
        String jsonStr = builder.toString();

        assertTrue(jsonStr.contains("\"name\":\"test container\""));
        assertTrue(jsonStr.contains("\"description\":\"test description\""));
        assertFalse(jsonStr.contains("\"configuration\""));
    }

    @Test
    public void testParse() throws IOException {
        String jsonStr = "{"
            + "\"name\":\"test\","
            + "\"description\":\"desc\","
            + "\"backend_roles\":[\"role1\"],"
            + "\"configuration\":{"
            + "\"llm_id\":\"llm-model-123\","
            + "\"strategies\":[{"
            + "\"id\":\"semantic_123\","
            + "\"enabled\":true,"
            + "\"type\":\"semantic\","
            + "\"namespace\":[\"user_id\"],"
            + "\"strategy_config\":{}"
            + "}]"
            + "}"
            + "}";

        XContentParser parser = MediaTypeRegistry.JSON.xContent().createParser(null, null, jsonStr);
        parser.nextToken();

        MLUpdateMemoryContainerInput input = MLUpdateMemoryContainerInput.parse(parser);

        assertEquals("test", input.getName());
        assertEquals("desc", input.getDescription());
        assertEquals(Arrays.asList("role1"), input.getBackendRoles());
        assertNotNull(input.getConfiguration());
        assertEquals("llm-model-123", input.getConfiguration().getLlmId());
        assertEquals(1, input.getConfiguration().getStrategies().size());
    }

    @Test
    public void testParseWithoutConfiguration() throws IOException {
        String jsonStr = "{" + "\"name\":\"test\"," + "\"description\":\"desc\"," + "\"backend_roles\":[\"role1\"]" + "}";

        XContentParser parser = MediaTypeRegistry.JSON.xContent().createParser(null, null, jsonStr);
        parser.nextToken();

        MLUpdateMemoryContainerInput input = MLUpdateMemoryContainerInput.parse(parser);

        assertEquals("test", input.getName());
        assertEquals("desc", input.getDescription());
        assertNull(input.getConfiguration());
    }

    @Test
    public void testParseMultipleStrategies() throws IOException {
        String jsonStr = "{"
            + "\"name\":\"test\","
            + "\"configuration\":{"
            + "\"strategies\":["
            + "{\"id\":\"semantic_123\",\"enabled\":true,\"type\":\"semantic\",\"namespace\":[\"user_id\"],\"strategy_config\":{}},"
            + "{\"id\":\"user_pref_456\",\"enabled\":false,\"type\":\"user_preference\",\"namespace\":[\"session_id\"],\"strategy_config\":{}}"
            + "]"
            + "}"
            + "}";

        XContentParser parser = MediaTypeRegistry.JSON.xContent().createParser(null, null, jsonStr);
        parser.nextToken();

        MLUpdateMemoryContainerInput input = MLUpdateMemoryContainerInput.parse(parser);

        assertEquals("test", input.getName());
        assertNotNull(input.getConfiguration());
        assertEquals(2, input.getConfiguration().getStrategies().size());
        assertEquals("semantic_123", input.getConfiguration().getStrategies().get(0).getId());
        assertEquals("user_pref_456", input.getConfiguration().getStrategies().get(1).getId());
    }

    @Test
    public void testParseWithStrategyConfig() throws IOException {
        String jsonStr = "{"
            + "\"name\":\"test\","
            + "\"configuration\":{"
            + "\"strategies\":[{"
            + "\"id\":\"semantic_123\","
            + "\"enabled\":true,"
            + "\"type\":\"semantic\","
            + "\"namespace\":[\"user_id\"],"
            + "\"configuration\":{\"threshold\":0.8,\"max_results\":10}"
            + "}]"
            + "}"
            + "}";

        XContentParser parser = MediaTypeRegistry.JSON.xContent().createParser(null, null, jsonStr);
        parser.nextToken();

        MLUpdateMemoryContainerInput input = MLUpdateMemoryContainerInput.parse(parser);

        assertNotNull(input.getConfiguration());
        MemoryStrategy strategy = input.getConfiguration().getStrategies().get(0);
        assertNotNull(strategy);
        assertEquals("semantic_123", strategy.getId());
        assertEquals(MemoryStrategyType.SEMANTIC, strategy.getType());
        assertNotNull(strategy.getStrategyConfig());
        // Strategy config parsing is tested in MemoryStrategyTest
    }

    @Test
    public void testBuilderWithAllFields() {
        Map<String, Object> strategyConfig = new HashMap<>();
        strategyConfig.put("key", "value");

        List<MemoryStrategy> strategies = Arrays
            .asList(
                MemoryStrategy
                    .builder()
                    .id("test_id")
                    .enabled(true)
                    .type(MemoryStrategyType.SEMANTIC)
                    .namespace(Arrays.asList("user_id"))
                    .strategyConfig(strategyConfig)
                    .build()
            );

        MemoryConfiguration configuration = MemoryConfiguration.builder().strategies(strategies).llmId("llm-test").build();

        MLUpdateMemoryContainerInput input = MLUpdateMemoryContainerInput
            .builder()
            .name("name")
            .description("desc")
            .backendRoles(Arrays.asList("role"))
            .configuration(configuration)
            .build();

        assertEquals("name", input.getName());
        assertEquals("desc", input.getDescription());
        assertEquals(Arrays.asList("role"), input.getBackendRoles());
        assertEquals(configuration, input.getConfiguration());
    }

    @Test
    public void testRoundTripSerialization() throws IOException {
        Map<String, Object> strategyConfig = new HashMap<>();
        strategyConfig.put("threshold", 0.75);

        List<MemoryStrategy> strategies = Arrays
            .asList(
                MemoryStrategy
                    .builder()
                    .id("semantic_abc")
                    .enabled(true)
                    .type(MemoryStrategyType.SEMANTIC)
                    .namespace(Arrays.asList("user_id", "agent_id"))
                    .strategyConfig(strategyConfig)
                    .build()
            );

        MemoryConfiguration configuration = MemoryConfiguration.builder().strategies(strategies).llmId("llm-round-trip").build();

        MLUpdateMemoryContainerInput original = MLUpdateMemoryContainerInput
            .builder()
            .name("container")
            .description("description")
            .backendRoles(Arrays.asList("role1", "role2"))
            .configuration(configuration)
            .build();

        // Serialize to XContent
        XContentBuilder builder = MediaTypeRegistry.contentBuilder(XContentType.JSON);
        original.toXContent(builder, ToXContent.EMPTY_PARAMS);
        String jsonStr = builder.toString();

        // Deserialize from XContent
        XContentParser parser = MediaTypeRegistry.JSON.xContent().createParser(null, null, jsonStr);
        parser.nextToken();
        MLUpdateMemoryContainerInput deserialized = MLUpdateMemoryContainerInput.parse(parser);

        // Verify
        assertEquals(original.getName(), deserialized.getName());
        assertEquals(original.getDescription(), deserialized.getDescription());
        assertEquals(original.getBackendRoles(), deserialized.getBackendRoles());
        assertNotNull(deserialized.getConfiguration());
        assertEquals(original.getConfiguration().getLlmId(), deserialized.getConfiguration().getLlmId());
        assertEquals(original.getConfiguration().getStrategies().size(), deserialized.getConfiguration().getStrategies().size());
        for (int i = 0; i < original.getConfiguration().getStrategies().size(); i++) {
            assertEquals(
                original.getConfiguration().getStrategies().get(i).getId(),
                deserialized.getConfiguration().getStrategies().get(i).getId()
            );
        }
    }

    @Test
    public void testConstructorWithLlmId() {
        MemoryConfiguration config = MemoryConfiguration.builder().llmId("llm-model-123").build();
        MLUpdateMemoryContainerInput input = MLUpdateMemoryContainerInput.builder().name("test").configuration(config).build();

        assertEquals("test", input.getName());
        assertEquals("llm-model-123", input.getConfiguration().getLlmId());
    }

    @Test
    public void testStreamSerializationWithLlmId() throws IOException {
        MemoryConfiguration config = MemoryConfiguration.builder().llmId("llm-model-456").build();
        MLUpdateMemoryContainerInput original = MLUpdateMemoryContainerInput
            .builder()
            .name("test")
            .description("desc")
            .configuration(config)
            .build();

        BytesStreamOutput output = new BytesStreamOutput();
        original.writeTo(output);

        StreamInput input = output.bytes().streamInput();
        MLUpdateMemoryContainerInput deserialized = new MLUpdateMemoryContainerInput(input);

        assertEquals(original.getName(), deserialized.getName());
        assertEquals(original.getDescription(), deserialized.getDescription());
        assertEquals("llm-model-456", deserialized.getConfiguration().getLlmId());
    }

    @Test
    public void testStreamSerializationWithNullLlmId() throws IOException {
        MemoryConfiguration config = MemoryConfiguration.builder().llmId(null).build();
        MLUpdateMemoryContainerInput original = MLUpdateMemoryContainerInput.builder().name("test").configuration(config).build();

        BytesStreamOutput output = new BytesStreamOutput();
        original.writeTo(output);

        StreamInput input = output.bytes().streamInput();
        MLUpdateMemoryContainerInput deserialized = new MLUpdateMemoryContainerInput(input);

        assertEquals(original.getName(), deserialized.getName());
        assertNull(deserialized.getConfiguration().getLlmId());
    }

    @Test
    public void testToXContentWithLlmId() throws IOException {
        MemoryConfiguration config = MemoryConfiguration.builder().llmId("llm-789").build();
        MLUpdateMemoryContainerInput input = MLUpdateMemoryContainerInput.builder().name("test container").configuration(config).build();

        XContentBuilder builder = MediaTypeRegistry.contentBuilder(XContentType.JSON);
        input.toXContent(builder, ToXContent.EMPTY_PARAMS);
        String jsonStr = builder.toString();

        assertTrue(jsonStr.contains("\"name\":\"test container\""));
        assertTrue(jsonStr.contains("\"configuration\""));
        assertTrue(jsonStr.contains("\"llm_id\":\"llm-789\""));
    }

    @Test
    public void testToXContentWithNullLlmId() throws IOException {
        MLUpdateMemoryContainerInput input = MLUpdateMemoryContainerInput.builder().name("test container").build();

        XContentBuilder builder = MediaTypeRegistry.contentBuilder(XContentType.JSON);
        input.toXContent(builder, ToXContent.EMPTY_PARAMS);
        String jsonStr = builder.toString();

        assertTrue(jsonStr.contains("\"name\":\"test container\""));
        assertFalse(jsonStr.contains("\"llm_id\""));
    }

    @Test
    public void testParseWithLlmId() throws IOException {
        String jsonStr = "{"
            + "\"name\":\"test\","
            + "\"description\":\"desc\","
            + "\"configuration\":{\"llm_id\":\"llm-model-xyz\"}"
            + "}";

        XContentParser parser = MediaTypeRegistry.JSON.xContent().createParser(null, null, jsonStr);
        parser.nextToken();

        MLUpdateMemoryContainerInput input = MLUpdateMemoryContainerInput.parse(parser);

        assertEquals("test", input.getName());
        assertEquals("desc", input.getDescription());
        assertEquals("llm-model-xyz", input.getConfiguration().getLlmId());
    }

    @Test
    public void testParseWithoutLlmId() throws IOException {
        String jsonStr = "{" + "\"name\":\"test\"," + "\"description\":\"desc\"" + "}";

        XContentParser parser = MediaTypeRegistry.JSON.xContent().createParser(null, null, jsonStr);
        parser.nextToken();

        MLUpdateMemoryContainerInput input = MLUpdateMemoryContainerInput.parse(parser);

        assertEquals("test", input.getName());
        assertEquals("desc", input.getDescription());
        assertNull(input.getConfiguration());
    }

    @Test
    public void testRoundTripSerializationWithLlmIdAndStrategies() throws IOException {
        List<MemoryStrategy> strategies = Arrays
            .asList(
                MemoryStrategy
                    .builder()
                    .id("semantic_123")
                    .enabled(true)
                    .type(MemoryStrategyType.SEMANTIC)
                    .namespace(Arrays.asList("user_id"))
                    .strategyConfig(new HashMap<>())
                    .build()
            );

        MemoryConfiguration config = MemoryConfiguration.builder().strategies(strategies).llmId("llm-combined-test").build();

        MLUpdateMemoryContainerInput original = MLUpdateMemoryContainerInput
            .builder()
            .name("container")
            .description("description")
            .backendRoles(Arrays.asList("role1"))
            .configuration(config)
            .build();

        // Serialize to XContent
        XContentBuilder builder = MediaTypeRegistry.contentBuilder(XContentType.JSON);
        original.toXContent(builder, ToXContent.EMPTY_PARAMS);
        String jsonStr = builder.toString();

        // Deserialize from XContent
        XContentParser parser = MediaTypeRegistry.JSON.xContent().createParser(null, null, jsonStr);
        parser.nextToken();
        MLUpdateMemoryContainerInput deserialized = MLUpdateMemoryContainerInput.parse(parser);

        // Verify
        assertEquals(original.getName(), deserialized.getName());
        assertEquals(original.getDescription(), deserialized.getDescription());
        assertEquals(original.getBackendRoles(), deserialized.getBackendRoles());
        assertNotNull(deserialized.getConfiguration());
        assertEquals(original.getConfiguration().getLlmId(), deserialized.getConfiguration().getLlmId());
        assertEquals(original.getConfiguration().getStrategies().size(), deserialized.getConfiguration().getStrategies().size());
        for (int i = 0; i < original.getConfiguration().getStrategies().size(); i++) {
            assertEquals(
                original.getConfiguration().getStrategies().get(i).getId(),
                deserialized.getConfiguration().getStrategies().get(i).getId()
            );
        }
    }

    // Backend Roles Validation Tests

    @Test
    public void testValidateBackendRoles_ValidRoles() {
        // Test various valid backend roles
        List<String> validRoles = Arrays
            .asList("admin", "user123", "team:developers", "role+test", "email@domain.com", "path/to/resource", "key=value");

        MLUpdateMemoryContainerInput input = MLUpdateMemoryContainerInput.builder().name("test-update").backendRoles(validRoles).build();

        assertNotNull(input);
        assertEquals(validRoles, input.getBackendRoles());
    }

    @Test
    public void testValidateBackendRoles_NullList() {
        // Null list should be allowed
        MLUpdateMemoryContainerInput input = MLUpdateMemoryContainerInput.builder().name("test-update").backendRoles(null).build();

        assertNotNull(input);
        assertNull(input.getBackendRoles());
    }

    @Test
    public void testValidateBackendRoles_EmptyList() {
        // Empty list should be allowed
        List<String> emptyList = Collections.emptyList();

        MLUpdateMemoryContainerInput input = MLUpdateMemoryContainerInput.builder().name("test-update").backendRoles(emptyList).build();

        assertNotNull(input);
        assertEquals(emptyList, input.getBackendRoles());
    }

    @Test(expected = OpenSearchParseException.class)
    public void testValidateBackendRoles_TooLong() {
        // 129 characters - should fail
        String tooLong = "a".repeat(129);
        List<String> roles = Arrays.asList(tooLong);

        MLUpdateMemoryContainerInput.builder().name("test-update").backendRoles(roles).build();
    }

    @Test(expected = OpenSearchParseException.class)
    public void testValidateBackendRoles_InvalidCharacters() {
        // Spaces are not allowed
        List<String> roles = Arrays.asList("role with spaces");

        MLUpdateMemoryContainerInput.builder().name("test-update").backendRoles(roles).build();
    }

    @Test(expected = OpenSearchParseException.class)
    public void testValidateBackendRoles_EmptyString() {
        // Empty string should be rejected
        List<String> roles = Arrays.asList("");

        MLUpdateMemoryContainerInput.builder().name("test-update").backendRoles(roles).build();
    }

    @Test(expected = OpenSearchParseException.class)
    public void testValidateBackendRoles_NullElement() {
        // Null element in list should be rejected
        List<String> roles = Arrays.asList("valid-role", null);

        MLUpdateMemoryContainerInput.builder().name("test-update").backendRoles(roles).build();
    }
}
