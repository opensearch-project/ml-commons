/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.memorycontainer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.opensearch.core.xcontent.ToXContent.EMPTY_PARAMS;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.SESSION_ID_FIELD;

import java.io.IOException;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.common.xcontent.LoggingDeprecationHandler;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.xcontent.MediaTypeRegistry;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.TestHelper;

public class MLMemoryTest {

    private MLMemory memoryWithAllFields;
    private MLMemory memoryMinimal;
    private Map<String, String> testTags;
    private Instant testCreatedTime;
    private Instant testUpdatedTime;
    private float[] testEmbedding;
    private Map<String, Float> sparseEmbedding;

    @Before
    public void setUp() {
        testCreatedTime = Instant.now();
        testUpdatedTime = Instant.now().plusSeconds(60);

        testTags = new HashMap<>();
        testTags.put("topic", "machine learning");
        testTags.put("priority", "high");

        testEmbedding = new float[] { 0.1f, 0.2f, 0.3f };

        sparseEmbedding = new HashMap<>();
        sparseEmbedding.put("token1", 0.5f);
        sparseEmbedding.put("token2", 0.8f);

        // Memory with all fields
        memoryWithAllFields = MLMemory
            .builder()
            .namespace(Map.of(SESSION_ID_FIELD, "session-123", "user_id", "user-456", "agent_id", "agent-789"))
            .memory("This is a test memory content")
            .memoryType(MemoryType.SEMANTIC)
            .tags(testTags)
            .createdTime(testCreatedTime)
            .lastUpdatedTime(testUpdatedTime)
            .memoryEmbedding(testEmbedding)
            .build();

        // Minimal memory (only required fields)
        memoryMinimal = MLMemory
            .builder()
            .namespace(Map.of(SESSION_ID_FIELD, "session-minimal"))
            .memory("Minimal memory")
            .memoryType(MemoryType.SEMANTIC)
            .createdTime(testCreatedTime)
            .lastUpdatedTime(testUpdatedTime)
            .build();
    }

    @Test
    public void testBuilderWithAllFields() {
        assertNotNull(memoryWithAllFields);
        assertEquals("session-123", memoryWithAllFields.getNamespace().get(SESSION_ID_FIELD));
        assertEquals("This is a test memory content", memoryWithAllFields.getMemory());
        assertEquals(MemoryType.SEMANTIC, memoryWithAllFields.getMemoryType());
        assertEquals("user-456", memoryWithAllFields.getNamespace().get("user_id"));
        assertEquals("agent-789", memoryWithAllFields.getNamespace().get("agent_id"));
        assertEquals(testTags, memoryWithAllFields.getTags());
        assertEquals(testCreatedTime, memoryWithAllFields.getCreatedTime());
        assertEquals(testUpdatedTime, memoryWithAllFields.getLastUpdatedTime());
        assertEquals(testEmbedding, memoryWithAllFields.getMemoryEmbedding());
    }

    @Test
    public void testBuilderMinimal() {
        assertNotNull(memoryMinimal);
        assertEquals("session-minimal", memoryMinimal.getNamespace().get(SESSION_ID_FIELD));
        assertEquals("Minimal memory", memoryMinimal.getMemory());
        assertEquals(MemoryType.SEMANTIC, memoryMinimal.getMemoryType());
        assertNull(memoryMinimal.getNamespace().get("user_id"));
        assertNull(memoryMinimal.getNamespace().get("agent_id"));
        assertNull(memoryMinimal.getTags());
        assertEquals(testCreatedTime, memoryMinimal.getCreatedTime());
        assertEquals(testUpdatedTime, memoryMinimal.getLastUpdatedTime());
        assertNull(memoryMinimal.getMemoryEmbedding());
    }

    @Test
    public void testStreamInputOutput() throws IOException {
        // Test with all fields
        BytesStreamOutput out = new BytesStreamOutput();
        memoryWithAllFields.writeTo(out);
        StreamInput in = out.bytes().streamInput();
        MLMemory deserialized = new MLMemory(in);

        assertEquals(memoryWithAllFields.getNamespace().get(SESSION_ID_FIELD), deserialized.getNamespace().get(SESSION_ID_FIELD));
        assertEquals(memoryWithAllFields.getMemory(), deserialized.getMemory());
        assertEquals(memoryWithAllFields.getMemoryType(), deserialized.getMemoryType());
        assertEquals(memoryWithAllFields.getNamespace().get("user_id"), deserialized.getNamespace().get("user_id"));
        assertEquals(memoryWithAllFields.getNamespace().get("agent_id"), deserialized.getNamespace().get("agent_id"));
        assertEquals(memoryWithAllFields.getTags(), deserialized.getTags());
        assertEquals(memoryWithAllFields.getCreatedTime(), deserialized.getCreatedTime());
        assertEquals(memoryWithAllFields.getLastUpdatedTime(), deserialized.getLastUpdatedTime());
        // Note: memoryEmbedding is not serialized in StreamInput/Output
        assertNull(deserialized.getMemoryEmbedding());
    }

    @Test
    public void testStreamInputOutputMinimal() throws IOException {
        // Test with minimal fields
        BytesStreamOutput out = new BytesStreamOutput();
        memoryMinimal.writeTo(out);
        StreamInput in = out.bytes().streamInput();
        MLMemory deserialized = new MLMemory(in);

        assertEquals(memoryMinimal.getNamespace().get(SESSION_ID_FIELD), deserialized.getNamespace().get(SESSION_ID_FIELD));
        assertEquals(memoryMinimal.getMemory(), deserialized.getMemory());
        assertEquals(memoryMinimal.getMemoryType(), deserialized.getMemoryType());
        assertNull(deserialized.getNamespace().get("user_id"));
        assertNull(deserialized.getNamespace().get("agent_id"));
        assertNull(deserialized.getTags());
        assertEquals(memoryMinimal.getCreatedTime(), deserialized.getCreatedTime());
        assertEquals(memoryMinimal.getLastUpdatedTime(), deserialized.getLastUpdatedTime());
        assertNull(deserialized.getMemoryEmbedding());
    }

    @Test
    public void testStreamInputOutputEmptyTags() throws IOException {
        // Test with empty tags
        MLMemory memoryEmptyTags = MLMemory
            .builder()
            .namespace(Map.of(SESSION_ID_FIELD, "session-empty-tags"))
            .memory("Memory with empty tags")
            .memoryType(MemoryType.SEMANTIC)
            .tags(new HashMap<>())
            .createdTime(testCreatedTime)
            .lastUpdatedTime(testUpdatedTime)
            .build();

        BytesStreamOutput out = new BytesStreamOutput();
        memoryEmptyTags.writeTo(out);
        StreamInput in = out.bytes().streamInput();
        MLMemory deserialized = new MLMemory(in);

        assertNull(deserialized.getTags());
    }

    @Test
    public void testToXContent() throws IOException {
        XContentBuilder builder = MediaTypeRegistry.contentBuilder(XContentType.JSON);
        memoryWithAllFields.toXContent(builder, EMPTY_PARAMS);
        String jsonString = TestHelper.xContentBuilderToString(builder);

        assertTrue(jsonString.contains("\"session_id\":\"session-123\""));
        assertTrue(jsonString.contains("\"memory\":\"This is a test memory content\""));
        assertTrue(jsonString.contains("\"memory_type\":\"SEMANTIC\""));
        assertTrue(jsonString.contains("\"user_id\":\"user-456\""));
        assertTrue(jsonString.contains("\"agent_id\":\"agent-789\""));
        assertTrue(jsonString.contains("\"topic\":\"machine learning\""));
        assertTrue(jsonString.contains("\"priority\":\"high\""));
        assertTrue(jsonString.contains("\"created_time\":" + testCreatedTime.toEpochMilli()));
        assertTrue(jsonString.contains("\"last_updated_time\":" + testUpdatedTime.toEpochMilli()));
    }

    @Test
    public void testToXContentMinimal() throws IOException {
        XContentBuilder builder = MediaTypeRegistry.contentBuilder(XContentType.JSON);
        memoryMinimal.toXContent(builder, EMPTY_PARAMS);
        String jsonString = TestHelper.xContentBuilderToString(builder);

        assertTrue(jsonString.contains("\"session_id\":\"session-minimal\""));
        assertTrue(jsonString.contains("\"memory\":\"Minimal memory\""));
        assertTrue(jsonString.contains("\"memory_type\":\"SEMANTIC\""));
        // Optional fields should not be present
        assertTrue(!jsonString.contains("\"user_id\""));
        assertTrue(!jsonString.contains("\"agent_id\""));
        assertTrue(!jsonString.contains("\"role\""));
        assertTrue(!jsonString.contains("\"tags\""));
        assertTrue(!jsonString.contains("\"memory_embedding\""));
    }

    @Test
    public void testParse() throws IOException {
        String jsonString = "{"
            + "\"namespace\":{\"session_id\":\"session-123\", \"user_id\":\"user-456\", \"agent_id\":\"agent-789\"},"
            + "\"memory\":\"This is a test memory content\","
            + "\"memory_type\":\"SEMANTIC\","
            + "\"role\":\"user\","
            + "\"tags\":{\"topic\":\"machine learning\",\"priority\":\"high\"},"
            + "\"created_time\":"
            + testCreatedTime.toEpochMilli()
            + ","
            + "\"last_updated_time\":"
            + testUpdatedTime.toEpochMilli()
            + ","
            + "\"memory_embedding\":{\"values\":[0.1,0.2,0.3]}"
            + "}";

        XContentParser parser = XContentType.JSON
            .xContent()
            .createParser(NamedXContentRegistry.EMPTY, LoggingDeprecationHandler.INSTANCE, jsonString);
        parser.nextToken();

        MLMemory parsed = MLMemory.parse(parser);

        assertEquals("session-123", parsed.getNamespace().get(SESSION_ID_FIELD));
        assertEquals("This is a test memory content", parsed.getMemory());
        assertEquals(MemoryType.SEMANTIC, parsed.getMemoryType());
        assertEquals("user-456", parsed.getNamespace().get("user_id"));
        assertEquals("agent-789", parsed.getNamespace().get("agent_id"));
        assertEquals(2, parsed.getTags().size());
        assertEquals("machine learning", parsed.getTags().get("topic"));
        assertEquals("high", parsed.getTags().get("priority"));
        assertEquals(testCreatedTime.toEpochMilli(), parsed.getCreatedTime().toEpochMilli());
        assertEquals(testUpdatedTime.toEpochMilli(), parsed.getLastUpdatedTime().toEpochMilli());
        assertNotNull(parsed.getMemoryEmbedding());
    }

    @Test
    public void testParseMinimal() throws IOException {
        String jsonString = "{"
            + "\"namespace\":{\"session_id\":\"session-minimal\"},"
            + "\"memory\":\"Minimal memory\","
            + "\"memory_type\":\"SEMANTIC\","
            + "\"created_time\":"
            + testCreatedTime.toEpochMilli()
            + ","
            + "\"last_updated_time\":"
            + testUpdatedTime.toEpochMilli()
            + "}";

        XContentParser parser = XContentType.JSON
            .xContent()
            .createParser(NamedXContentRegistry.EMPTY, LoggingDeprecationHandler.INSTANCE, jsonString);
        parser.nextToken();

        MLMemory parsed = MLMemory.parse(parser);

        assertEquals("session-minimal", parsed.getNamespace().get(SESSION_ID_FIELD));
        assertEquals("Minimal memory", parsed.getMemory());
        assertEquals(MemoryType.SEMANTIC, parsed.getMemoryType());
        assertNull(parsed.getNamespace().get("user_id"));
        assertNull(parsed.getNamespace().get("agent_id"));
        assertNull(parsed.getTags());
        assertEquals(testCreatedTime.toEpochMilli(), parsed.getCreatedTime().toEpochMilli());
        assertEquals(testUpdatedTime.toEpochMilli(), parsed.getLastUpdatedTime().toEpochMilli());
        assertNull(parsed.getMemoryEmbedding());
    }

    @Test
    public void testParseWithUnknownFields() throws IOException {
        String jsonString = "{"
            + "\"namespace\": {\"session_id\":\"session-123\"},"
            + "\"memory\":\"Test memory\","
            + "\"memory_type\":\"SEMANTIC\","
            + "\"unknown_field\":\"should be ignored\","
            + "\"created_time\":"
            + testCreatedTime.toEpochMilli()
            + ","
            + "\"last_updated_time\":"
            + testUpdatedTime.toEpochMilli()
            + "}";

        XContentParser parser = XContentType.JSON
            .xContent()
            .createParser(NamedXContentRegistry.EMPTY, LoggingDeprecationHandler.INSTANCE, jsonString);
        parser.nextToken();

        MLMemory parsed = MLMemory.parse(parser);

        assertEquals("session-123", parsed.getNamespace().get(SESSION_ID_FIELD));
        assertEquals("Test memory", parsed.getMemory());
        assertEquals(MemoryType.SEMANTIC, parsed.getMemoryType());
    }

    @Test
    public void testToIndexMap() {
        Map<String, Object> indexMap = memoryWithAllFields.toIndexMap();
        Map<String, String> namespace = (Map<String, String>) indexMap.get("namespace");
        assertEquals("session-123", namespace.get(SESSION_ID_FIELD));
        assertEquals("This is a test memory content", indexMap.get("memory"));
        assertEquals("SEMANTIC", indexMap.get("memory_type"));
        assertEquals("user-456", namespace.get("user_id"));
        assertEquals("agent-789", namespace.get("agent_id"));
        assertEquals(testTags, indexMap.get("tags"));
        assertEquals(testCreatedTime.toEpochMilli(), indexMap.get("created_time"));
        assertEquals(testUpdatedTime.toEpochMilli(), indexMap.get("last_updated_time"));
        assertEquals(testEmbedding, indexMap.get("memory_embedding"));
    }

    @Test
    public void testToIndexMapMinimal() {
        Map<String, Object> indexMap = memoryMinimal.toIndexMap();
        Map<String, String> namespace = (Map<String, String>) indexMap.get("namespace");
        assertEquals("session-minimal", namespace.get("session_id"));
        assertEquals("Minimal memory", indexMap.get("memory"));
        assertEquals("SEMANTIC", indexMap.get("memory_type"));
        assertEquals(testCreatedTime.toEpochMilli(), indexMap.get("created_time"));
        assertEquals(testUpdatedTime.toEpochMilli(), indexMap.get("last_updated_time"));

        // Optional fields should not be in the map
        assertTrue(!indexMap.containsKey("user_id"));
        assertTrue(!indexMap.containsKey("agent_id"));
        assertTrue(!indexMap.containsKey("role"));
        assertTrue(!indexMap.containsKey("tags"));
        assertTrue(!indexMap.containsKey("memory_embedding"));
    }

    @Test
    public void testSettersWork() {
        Map<String, String> namespace = new HashMap<>();
        namespace.put(SESSION_ID_FIELD, "initial-session");
        MLMemory memory = MLMemory
            .builder()
            .namespace(namespace)
            .memory("initial memory")
            .memoryType(MemoryType.SEMANTIC)
            .createdTime(testCreatedTime)
            .lastUpdatedTime(testUpdatedTime)
            .build();

        // Test setters
        memory.getNamespace().put(SESSION_ID_FIELD, "new-session");
        memory.setMemory("new memory");
        memory.setMemoryType(MemoryType.SEMANTIC);
        memory.getNamespace().put("user_id", "new-user");
        memory.getNamespace().put("agent_id", "new-agent");
        memory.setTags(testTags);
        memory.setMemoryEmbedding(sparseEmbedding);

        assertEquals("new-session", memory.getNamespace().get(SESSION_ID_FIELD));
        assertEquals("new memory", memory.getMemory());
        assertEquals(MemoryType.SEMANTIC, memory.getMemoryType());
        assertEquals("new-user", memory.getNamespace().get("user_id"));
        assertEquals("new-agent", memory.getNamespace().get("agent_id"));
        assertEquals(testTags, memory.getTags());
        assertEquals(sparseEmbedding, memory.getMemoryEmbedding());
    }

    @Test
    public void testXContentRoundTrip() throws IOException {
        // Use memory without embedding for round trip test since embedding parsing is complex
        MLMemory memoryNoEmbedding = MLMemory
            .builder()
            .namespace(Map.of(SESSION_ID_FIELD, "session-123", "user_id", "user-456", "agent_id", "agent-789"))
            .memory("This is a test memory content")
            .memoryType(MemoryType.SEMANTIC)
            .tags(testTags)
            .createdTime(testCreatedTime)
            .lastUpdatedTime(testUpdatedTime)
            .build();

        // Convert to XContent
        XContentBuilder builder = MediaTypeRegistry.contentBuilder(XContentType.JSON);
        memoryNoEmbedding.toXContent(builder, EMPTY_PARAMS);
        String jsonString = TestHelper.xContentBuilderToString(builder);

        // Parse back
        XContentParser parser = XContentType.JSON
            .xContent()
            .createParser(NamedXContentRegistry.EMPTY, LoggingDeprecationHandler.INSTANCE, jsonString);
        parser.nextToken();
        MLMemory parsed = MLMemory.parse(parser);

        // Verify all fields match
        assertEquals(memoryNoEmbedding.getNamespace().get(SESSION_ID_FIELD), parsed.getNamespace().get(SESSION_ID_FIELD));
        assertEquals(memoryNoEmbedding.getMemory(), parsed.getMemory());
        assertEquals(memoryNoEmbedding.getMemoryType(), parsed.getMemoryType());
        assertEquals(memoryNoEmbedding.getNamespace().get("user_id"), parsed.getNamespace().get("user_id"));
        assertEquals(memoryNoEmbedding.getNamespace().get("agent_id"), parsed.getNamespace().get("agent_id"));
        assertEquals(memoryNoEmbedding.getTags(), parsed.getTags());
        assertEquals(memoryNoEmbedding.getCreatedTime().toEpochMilli(), parsed.getCreatedTime().toEpochMilli());
        assertEquals(memoryNoEmbedding.getLastUpdatedTime().toEpochMilli(), parsed.getLastUpdatedTime().toEpochMilli());
    }

    @Test
    public void testSpecialCharactersInFields() throws IOException {
        Map<String, String> specialTags = new HashMap<>();
        specialTags.put("key with spaces", "value with\nnewlines");
        specialTags.put("unicode_key_🔥", "unicode_value_✨");

        MLMemory specialMemory = MLMemory
            .builder()
            .namespace(Map.of(SESSION_ID_FIELD, "session-with-special-chars-🚀"))
            .memory("Memory with\n\ttabs and\nnewlines and \"quotes\"")
            .memoryType(MemoryType.SEMANTIC)
            .tags(specialTags)
            .createdTime(testCreatedTime)
            .lastUpdatedTime(testUpdatedTime)
            .build();

        // Test XContent round trip
        XContentBuilder builder = MediaTypeRegistry.contentBuilder(XContentType.JSON);
        specialMemory.toXContent(builder, EMPTY_PARAMS);
        String jsonString = TestHelper.xContentBuilderToString(builder);

        XContentParser parser = XContentType.JSON
            .xContent()
            .createParser(NamedXContentRegistry.EMPTY, LoggingDeprecationHandler.INSTANCE, jsonString);
        parser.nextToken();
        MLMemory parsed = MLMemory.parse(parser);

        assertEquals(specialMemory.getNamespace().get(SESSION_ID_FIELD), parsed.getNamespace().get(SESSION_ID_FIELD));
        assertEquals(specialMemory.getMemory(), parsed.getMemory());
        assertEquals(specialMemory.getTags(), parsed.getTags());
    }
}
