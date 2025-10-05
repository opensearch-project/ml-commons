/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.memorycontainer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.opensearch.ml.common.memorycontainer.MemoryStrategy.generateStrategyId;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;

public class MemoryStrategyTest {

    private MemoryStrategy strategy;
    private List<String> namespace;
    private Map<String, Object> strategyConfig;

    @Before
    public void setUp() {
        namespace = Arrays.asList("user_id", "session_id");
        strategyConfig = new HashMap<>();
        strategyConfig.put("prompt", "test prompt");
    }

    @Test
    public void testGenerateStrategyId_WithSemanticType() {
        String id = generateStrategyId("semantic");
        assertNotNull(id);
        assertTrue("ID should start with semantic_", id.startsWith("semantic_"));
        assertEquals("ID should have correct length", "semantic_".length() + 8, id.length());
    }

    @Test
    public void testGenerateStrategyId_WithUserPreferenceType() {
        String id = generateStrategyId("user_preference");
        assertNotNull(id);
        assertTrue("ID should start with user_preference_", id.startsWith("user_preference_"));
        assertEquals("ID should have correct length", "user_preference_".length() + 8, id.length());
    }

    @Test
    public void testGenerateStrategyId_WithSummaryType() {
        String id = generateStrategyId("summary");
        assertNotNull(id);
        assertTrue("ID should start with summary_", id.startsWith("summary_"));
        assertEquals("ID should have correct length", "summary_".length() + 8, id.length());
    }

    @Test
    public void testGenerateStrategyId_WithCustomType() {
        String id = generateStrategyId("custom_type");
        assertNotNull(id);
        assertTrue("ID should start with custom_type_", id.startsWith("custom_type_"));
    }

    @Test
    public void testGenerateStrategyId_WithSpacesInType() {
        String id = generateStrategyId("custom type with spaces");
        assertNotNull(id);
        assertTrue("ID should replace spaces with underscores", id.startsWith("custom_type_with_spaces_"));
    }

    @Test
    public void testGenerateStrategyId_WithNullType() {
        String id = generateStrategyId(null);
        assertNotNull(id);
        assertTrue("ID should start with strategy_ for null type", id.startsWith("strategy_"));
    }

    @Test
    public void testGenerateStrategyId_WithEmptyType() {
        String id = generateStrategyId("");
        assertNotNull(id);
        assertTrue("ID should start with strategy_ for empty type", id.startsWith("strategy_"));
    }

    @Test
    public void testGenerateStrategyId_WithWhitespaceType() {
        String id = generateStrategyId("   ");
        assertNotNull(id);
        assertTrue("ID should start with strategy_ for whitespace type", id.startsWith("strategy_"));
    }

    @Test
    public void testConstructor_WithNullId_KeepsNull() {
        MemoryStrategy strategy = new MemoryStrategy(null, true, "semantic", namespace, strategyConfig);
        assertEquals(null, strategy.getId());
        assertEquals("semantic", strategy.getType());
    }

    @Test
    public void testConstructor_WithEmptyId_KeepsEmpty() {
        MemoryStrategy strategy = new MemoryStrategy("", true, "user_preference", namespace, strategyConfig);
        assertEquals("", strategy.getId());
        assertEquals("user_preference", strategy.getType());
    }

    @Test
    public void testConstructor_WithSummaryType_NoAutoGeneration() {
        MemoryStrategy strategy = new MemoryStrategy(null, true, "summary", namespace, strategyConfig);
        assertEquals(null, strategy.getId());
        assertEquals("summary", strategy.getType());
    }

    @Test
    public void testConstructor_WithProvidedId_KeepsId() {
        String customId = "custom_id_123";
        MemoryStrategy strategy = new MemoryStrategy(customId, true, "semantic", namespace, strategyConfig);
        assertEquals(customId, strategy.getId());
    }

    @Test
    public void testParse_WithoutId_KeepsNull() throws IOException {
        String jsonContent = "{\"type\":\"semantic\",\"enabled\":true,\"namespace\":[\"user_id\"]}";
        XContentParser parser = XContentType.JSON.xContent().createParser(null, null, jsonContent);
        parser.nextToken(); // Start parsing

        MemoryStrategy strategy = MemoryStrategy.parse(parser);
        assertEquals(null, strategy.getId());
        assertEquals("semantic", strategy.getType());
    }

    @Test
    public void testParse_WithId_KeepsProvidedId() throws IOException {
        String customId = "my_custom_id";
        String jsonContent = String.format("{\"id\":\"%s\",\"type\":\"semantic\",\"enabled\":true,\"namespace\":[\"user_id\"]}", customId);
        XContentParser parser = XContentType.JSON.xContent().createParser(null, null, jsonContent);
        parser.nextToken(); // Start parsing

        MemoryStrategy strategy = MemoryStrategy.parse(parser);
        assertEquals(customId, strategy.getId());
    }

    @Test
    public void testSerialization_PreservesId() throws IOException {
        String customId = "semantic_abc123";
        MemoryStrategy original = new MemoryStrategy(customId, true, "semantic", namespace, strategyConfig);
        assertEquals(customId, original.getId());

        // Write to stream
        BytesStreamOutput output = new BytesStreamOutput();
        original.writeTo(output);

        // Read from stream
        StreamInput input = output.bytes().streamInput();
        MemoryStrategy deserialized = new MemoryStrategy(input);

        assertEquals(customId, deserialized.getId());
        assertEquals(original.getType(), deserialized.getType());
        assertEquals(original.isEnabled(), deserialized.isEnabled());
    }

    @Test
    public void testToXContent_IncludesId() throws IOException {
        String customId = "user_preference_xyz789";
        MemoryStrategy strategy = new MemoryStrategy(customId, true, "user_preference", namespace, strategyConfig);
        XContentBuilder builder = XContentBuilder.builder(XContentType.JSON.xContent());
        strategy.toXContent(builder, ToXContent.EMPTY_PARAMS);
        String jsonString = builder.toString();

        assertTrue("JSON should contain id field", jsonString.contains("\"id\":"));
        assertTrue("ID should match custom id", jsonString.contains(customId));
    }

    @Test
    public void testIdGeneration_UniqueForEachCall() {
        String id1 = generateStrategyId("semantic");
        String id2 = generateStrategyId("semantic");

        assertNotNull(id1);
        assertNotNull(id2);
        assertTrue("Both IDs should start with semantic_", id1.startsWith("semantic_") && id2.startsWith("semantic_"));
        assertTrue("IDs should be unique", !id1.equals(id2));
    }

    @Test
    public void testBuilder_WithoutId_KeepsNull() {
        MemoryStrategy strategy = MemoryStrategy
            .builder()
            .type("semantic")
            .enabled(true)
            .namespace(namespace)
            .strategyConfig(strategyConfig)
            .build();

        assertEquals(null, strategy.getId());
        assertEquals("semantic", strategy.getType());
    }

    @Test
    public void testBuilder_WithId_KeepsProvidedId() {
        String customId = "my_builder_id";
        MemoryStrategy strategy = MemoryStrategy
            .builder()
            .id(customId)
            .type("semantic")
            .enabled(true)
            .namespace(namespace)
            .strategyConfig(strategyConfig)
            .build();

        assertEquals(customId, strategy.getId());
    }
}
