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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.xcontent.MediaTypeRegistry;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.memorycontainer.MemoryStrategy;

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
                    .type("semantic")
                    .namespace(Arrays.asList("user_id"))
                    .strategyConfig(new HashMap<>())
                    .build()
            );

        MLUpdateMemoryContainerInput input = MLUpdateMemoryContainerInput
            .builder()
            .name("test container")
            .description("test description")
            .backendRoles(backendRoles)
            .strategies(strategies)
            .build();

        assertEquals("test container", input.getName());
        assertEquals("test description", input.getDescription());
        assertEquals(backendRoles, input.getBackendRoles());
        assertEquals(strategies, input.getStrategies());
    }

    @Test
    public void testStreamSerialization() throws IOException {
        List<MemoryStrategy> strategies = Arrays
            .asList(
                MemoryStrategy
                    .builder()
                    .id("semantic_123")
                    .enabled(true)
                    .type("semantic")
                    .namespace(Arrays.asList("user_id"))
                    .strategyConfig(new HashMap<>())
                    .build(),
                MemoryStrategy
                    .builder()
                    .id("user_pref_456")
                    .enabled(false)
                    .type("user_preference")
                    .namespace(Arrays.asList("user_id", "session_id"))
                    .strategyConfig(new HashMap<>())
                    .build()
            );

        MLUpdateMemoryContainerInput original = MLUpdateMemoryContainerInput
            .builder()
            .name("test")
            .description("desc")
            .backendRoles(Arrays.asList("role1"))
            .strategies(strategies)
            .build();

        BytesStreamOutput output = new BytesStreamOutput();
        original.writeTo(output);

        StreamInput input = output.bytes().streamInput();
        MLUpdateMemoryContainerInput deserialized = new MLUpdateMemoryContainerInput(input);

        assertEquals(original.getName(), deserialized.getName());
        assertEquals(original.getDescription(), deserialized.getDescription());
        assertEquals(original.getBackendRoles(), deserialized.getBackendRoles());
        assertEquals(original.getStrategies().size(), deserialized.getStrategies().size());
        assertEquals(original.getStrategies().get(0).getId(), deserialized.getStrategies().get(0).getId());
    }

    @Test
    public void testStreamSerializationWithNullStrategies() throws IOException {
        MLUpdateMemoryContainerInput original = MLUpdateMemoryContainerInput
            .builder()
            .name("test")
            .description("desc")
            .backendRoles(Arrays.asList("role1"))
            .strategies(null)
            .build();

        BytesStreamOutput output = new BytesStreamOutput();
        original.writeTo(output);

        StreamInput input = output.bytes().streamInput();
        MLUpdateMemoryContainerInput deserialized = new MLUpdateMemoryContainerInput(input);

        assertEquals(original.getName(), deserialized.getName());
        assertNull(deserialized.getStrategies());
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
                    .type("semantic")
                    .namespace(Arrays.asList("user_id"))
                    .strategyConfig(strategyConfig)
                    .build()
            );

        MLUpdateMemoryContainerInput input = MLUpdateMemoryContainerInput
            .builder()
            .name("test container")
            .description("test description")
            .backendRoles(Arrays.asList("role1", "role2"))
            .strategies(strategies)
            .build();

        XContentBuilder builder = MediaTypeRegistry.contentBuilder(XContentType.JSON);
        input.toXContent(builder, ToXContent.EMPTY_PARAMS);
        String jsonStr = builder.toString();

        assertTrue(jsonStr.contains("\"name\":\"test container\""));
        assertTrue(jsonStr.contains("\"description\":\"test description\""));
        assertTrue(jsonStr.contains("\"backend_roles\":[\"role1\",\"role2\"]"));
        assertTrue(jsonStr.contains("\"strategies\""));
        assertTrue(jsonStr.contains("\"semantic_123\""));
        assertTrue(jsonStr.contains("\"semantic\""));
    }

    @Test
    public void testToXContentWithNullStrategies() throws IOException {
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
        assertFalse(jsonStr.contains("\"strategies\""));
    }

    @Test
    public void testParse() throws IOException {
        String jsonStr = "{"
            + "\"name\":\"test\","
            + "\"description\":\"desc\","
            + "\"backend_roles\":[\"role1\"],"
            + "\"strategies\":[{"
            + "\"id\":\"semantic_123\","
            + "\"enabled\":true,"
            + "\"type\":\"semantic\","
            + "\"namespace\":[\"user_id\"],"
            + "\"strategy_config\":{}"
            + "}]"
            + "}";

        XContentParser parser = MediaTypeRegistry.JSON.xContent().createParser(null, null, jsonStr);
        parser.nextToken();

        MLUpdateMemoryContainerInput input = MLUpdateMemoryContainerInput.parse(parser);

        assertEquals("test", input.getName());
        assertEquals("desc", input.getDescription());
        assertEquals(Arrays.asList("role1"), input.getBackendRoles());
        assertNotNull(input.getStrategies());
        assertEquals(1, input.getStrategies().size());
        assertEquals("semantic_123", input.getStrategies().get(0).getId());
        assertEquals("semantic", input.getStrategies().get(0).getType());
    }

    @Test
    public void testParseWithoutStrategies() throws IOException {
        String jsonStr = "{" + "\"name\":\"test\"," + "\"description\":\"desc\"," + "\"backend_roles\":[\"role1\"]" + "}";

        XContentParser parser = MediaTypeRegistry.JSON.xContent().createParser(null, null, jsonStr);
        parser.nextToken();

        MLUpdateMemoryContainerInput input = MLUpdateMemoryContainerInput.parse(parser);

        assertEquals("test", input.getName());
        assertEquals("desc", input.getDescription());
        assertNull(input.getStrategies());
    }

    @Test
    public void testParseMultipleStrategies() throws IOException {
        String jsonStr = "{"
            + "\"name\":\"test\","
            + "\"strategies\":["
            + "{\"id\":\"semantic_123\",\"enabled\":true,\"type\":\"semantic\",\"namespace\":[\"user_id\"],\"strategy_config\":{}},"
            + "{\"id\":\"user_pref_456\",\"enabled\":false,\"type\":\"user_preference\",\"namespace\":[\"session_id\"],\"strategy_config\":{}}"
            + "]"
            + "}";

        XContentParser parser = MediaTypeRegistry.JSON.xContent().createParser(null, null, jsonStr);
        parser.nextToken();

        MLUpdateMemoryContainerInput input = MLUpdateMemoryContainerInput.parse(parser);

        assertEquals("test", input.getName());
        assertNotNull(input.getStrategies());
        assertEquals(2, input.getStrategies().size());
        assertEquals("semantic_123", input.getStrategies().get(0).getId());
        assertEquals("user_pref_456", input.getStrategies().get(1).getId());
    }

    @Test
    public void testParseWithStrategyConfig() throws IOException {
        String jsonStr = "{"
            + "\"name\":\"test\","
            + "\"strategies\":[{"
            + "\"id\":\"semantic_123\","
            + "\"enabled\":true,"
            + "\"type\":\"semantic\","
            + "\"namespace\":[\"user_id\"],"
            + "\"strategy_config\":{\"threshold\":0.8,\"max_results\":10}"
            + "}]"
            + "}";

        XContentParser parser = MediaTypeRegistry.JSON.xContent().createParser(null, null, jsonStr);
        parser.nextToken();

        MLUpdateMemoryContainerInput input = MLUpdateMemoryContainerInput.parse(parser);

        assertNotNull(input.getStrategies());
        MemoryStrategy strategy = input.getStrategies().get(0);
        assertNotNull(strategy);
        assertEquals("semantic_123", strategy.getId());
        assertEquals("semantic", strategy.getType());
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
                    .type("semantic")
                    .namespace(Arrays.asList("user_id"))
                    .strategyConfig(strategyConfig)
                    .build()
            );

        MLUpdateMemoryContainerInput input = MLUpdateMemoryContainerInput
            .builder()
            .name("name")
            .description("desc")
            .backendRoles(Arrays.asList("role"))
            .strategies(strategies)
            .build();

        assertEquals("name", input.getName());
        assertEquals("desc", input.getDescription());
        assertEquals(Arrays.asList("role"), input.getBackendRoles());
        assertEquals(1, input.getStrategies().size());
        assertEquals("test_id", input.getStrategies().get(0).getId());
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
                    .type("semantic")
                    .namespace(Arrays.asList("user_id", "agent_id"))
                    .strategyConfig(strategyConfig)
                    .build()
            );

        MLUpdateMemoryContainerInput original = MLUpdateMemoryContainerInput
            .builder()
            .name("container")
            .description("description")
            .backendRoles(Arrays.asList("role1", "role2"))
            .strategies(strategies)
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
        assertEquals(original.getStrategies().size(), deserialized.getStrategies().size());
        assertEquals(original.getStrategies().get(0).getId(), deserialized.getStrategies().get(0).getId());
        assertEquals(original.getStrategies().get(0).getType(), deserialized.getStrategies().get(0).getType());
    }
}
