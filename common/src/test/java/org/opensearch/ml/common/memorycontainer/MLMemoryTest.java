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
            .sessionId("session-123")
            .memory("This is a test memory content")
            .memoryType(MemoryType.RAW_MESSAGE)
            .userId("user-456")
            .agentId("agent-789")
            .role("user")
            .tags(testTags)
            .createdTime(testCreatedTime)
            .lastUpdatedTime(testUpdatedTime)
            .memoryEmbedding(testEmbedding)
            .build();

        // Minimal memory (only required fields)
        memoryMinimal = MLMemory
            .builder()
            .sessionId("session-minimal")
            .memory("Minimal memory")
            .memoryType(MemoryType.FACT)
            .createdTime(testCreatedTime)
            .lastUpdatedTime(testUpdatedTime)
            .build();
    }

    @Test
    public void testBuilderWithAllFields() {
        assertNotNull(memoryWithAllFields);
        assertEquals("session-123", memoryWithAllFields.getSessionId());
        assertEquals("This is a test memory content", memoryWithAllFields.getMemory());
        assertEquals(MemoryType.RAW_MESSAGE, memoryWithAllFields.getMemoryType());
        assertEquals("user-456", memoryWithAllFields.getUserId());
        assertEquals("agent-789", memoryWithAllFields.getAgentId());
        assertEquals("user", memoryWithAllFields.getRole());
        assertEquals(testTags, memoryWithAllFields.getTags());
        assertEquals(testCreatedTime, memoryWithAllFields.getCreatedTime());
        assertEquals(testUpdatedTime, memoryWithAllFields.getLastUpdatedTime());
        assertEquals(testEmbedding, memoryWithAllFields.getMemoryEmbedding());
    }

    @Test
    public void testBuilderMinimal() {
        assertNotNull(memoryMinimal);
        assertEquals("session-minimal", memoryMinimal.getSessionId());
        assertEquals("Minimal memory", memoryMinimal.getMemory());
        assertEquals(MemoryType.FACT, memoryMinimal.getMemoryType());
        assertNull(memoryMinimal.getUserId());
        assertNull(memoryMinimal.getAgentId());
        assertNull(memoryMinimal.getRole());
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

        assertEquals(memoryWithAllFields.getSessionId(), deserialized.getSessionId());
        assertEquals(memoryWithAllFields.getMemory(), deserialized.getMemory());
        assertEquals(memoryWithAllFields.getMemoryType(), deserialized.getMemoryType());
        assertEquals(memoryWithAllFields.getUserId(), deserialized.getUserId());
        assertEquals(memoryWithAllFields.getAgentId(), deserialized.getAgentId());
        assertEquals(memoryWithAllFields.getRole(), deserialized.getRole());
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

        assertEquals(memoryMinimal.getSessionId(), deserialized.getSessionId());
        assertEquals(memoryMinimal.getMemory(), deserialized.getMemory());
        assertEquals(memoryMinimal.getMemoryType(), deserialized.getMemoryType());
        assertNull(deserialized.getUserId());
        assertNull(deserialized.getAgentId());
        assertNull(deserialized.getRole());
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
            .sessionId("session-empty-tags")
            .memory("Memory with empty tags")
            .memoryType(MemoryType.FACT)
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
        assertTrue(jsonString.contains("\"memory_type\":\"RAW_MESSAGE\""));
        assertTrue(jsonString.contains("\"user_id\":\"user-456\""));
        assertTrue(jsonString.contains("\"agent_id\":\"agent-789\""));
        assertTrue(jsonString.contains("\"role\":\"user\""));
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
        assertTrue(jsonString.contains("\"memory_type\":\"FACT\""));
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
            + "\"session_id\":\"session-123\","
            + "\"memory\":\"This is a test memory content\","
            + "\"memory_type\":\"RAW_MESSAGE\","
            + "\"user_id\":\"user-456\","
            + "\"agent_id\":\"agent-789\","
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

        assertEquals("session-123", parsed.getSessionId());
        assertEquals("This is a test memory content", parsed.getMemory());
        assertEquals(MemoryType.RAW_MESSAGE, parsed.getMemoryType());
        assertEquals("user-456", parsed.getUserId());
        assertEquals("agent-789", parsed.getAgentId());
        assertEquals("user", parsed.getRole());
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
            + "\"session_id\":\"session-minimal\","
            + "\"memory\":\"Minimal memory\","
            + "\"memory_type\":\"FACT\","
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

        assertEquals("session-minimal", parsed.getSessionId());
        assertEquals("Minimal memory", parsed.getMemory());
        assertEquals(MemoryType.FACT, parsed.getMemoryType());
        assertNull(parsed.getUserId());
        assertNull(parsed.getAgentId());
        assertNull(parsed.getRole());
        assertNull(parsed.getTags());
        assertEquals(testCreatedTime.toEpochMilli(), parsed.getCreatedTime().toEpochMilli());
        assertEquals(testUpdatedTime.toEpochMilli(), parsed.getLastUpdatedTime().toEpochMilli());
        assertNull(parsed.getMemoryEmbedding());
    }

    @Test
    public void testParseWithUnknownFields() throws IOException {
        String jsonString = "{"
            + "\"session_id\":\"session-123\","
            + "\"memory\":\"Test memory\","
            + "\"memory_type\":\"FACT\","
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

        assertEquals("session-123", parsed.getSessionId());
        assertEquals("Test memory", parsed.getMemory());
        assertEquals(MemoryType.FACT, parsed.getMemoryType());
    }

    @Test
    public void testToIndexMap() {
        Map<String, Object> indexMap = memoryWithAllFields.toIndexMap();

        assertEquals("session-123", indexMap.get("session_id"));
        assertEquals("This is a test memory content", indexMap.get("memory"));
        assertEquals("RAW_MESSAGE", indexMap.get("memory_type"));
        assertEquals("user-456", indexMap.get("user_id"));
        assertEquals("agent-789", indexMap.get("agent_id"));
        assertEquals("user", indexMap.get("role"));
        assertEquals(testTags, indexMap.get("tags"));
        assertEquals(testCreatedTime.toEpochMilli(), indexMap.get("created_time"));
        assertEquals(testUpdatedTime.toEpochMilli(), indexMap.get("last_updated_time"));
        assertEquals(testEmbedding, indexMap.get("memory_embedding"));
    }

    @Test
    public void testToIndexMapMinimal() {
        Map<String, Object> indexMap = memoryMinimal.toIndexMap();

        assertEquals("session-minimal", indexMap.get("session_id"));
        assertEquals("Minimal memory", indexMap.get("memory"));
        assertEquals("FACT", indexMap.get("memory_type"));
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
        MLMemory memory = MLMemory
            .builder()
            .sessionId("initial-session")
            .memory("initial memory")
            .memoryType(MemoryType.FACT)
            .createdTime(testCreatedTime)
            .lastUpdatedTime(testUpdatedTime)
            .build();

        // Test setters
        memory.setSessionId("new-session");
        memory.setMemory("new memory");
        memory.setMemoryType(MemoryType.RAW_MESSAGE);
        memory.setUserId("new-user");
        memory.setAgentId("new-agent");
        memory.setRole("assistant");
        memory.setTags(testTags);
        memory.setMemoryEmbedding(sparseEmbedding);

        assertEquals("new-session", memory.getSessionId());
        assertEquals("new memory", memory.getMemory());
        assertEquals(MemoryType.RAW_MESSAGE, memory.getMemoryType());
        assertEquals("new-user", memory.getUserId());
        assertEquals("new-agent", memory.getAgentId());
        assertEquals("assistant", memory.getRole());
        assertEquals(testTags, memory.getTags());
        assertEquals(sparseEmbedding, memory.getMemoryEmbedding());
    }

    @Test
    public void testXContentRoundTrip() throws IOException {
        // Use memory without embedding for round trip test since embedding parsing is complex
        MLMemory memoryNoEmbedding = MLMemory
            .builder()
            .sessionId("session-123")
            .memory("This is a test memory content")
            .memoryType(MemoryType.RAW_MESSAGE)
            .userId("user-456")
            .agentId("agent-789")
            .role("user")
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
        assertEquals(memoryNoEmbedding.getSessionId(), parsed.getSessionId());
        assertEquals(memoryNoEmbedding.getMemory(), parsed.getMemory());
        assertEquals(memoryNoEmbedding.getMemoryType(), parsed.getMemoryType());
        assertEquals(memoryNoEmbedding.getUserId(), parsed.getUserId());
        assertEquals(memoryNoEmbedding.getAgentId(), parsed.getAgentId());
        assertEquals(memoryNoEmbedding.getRole(), parsed.getRole());
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
            .sessionId("session-with-special-chars-🚀")
            .memory("Memory with\n\ttabs and\nnewlines and \"quotes\"")
            .memoryType(MemoryType.FACT)
            .role("user/assistant")
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

        assertEquals(specialMemory.getSessionId(), parsed.getSessionId());
        assertEquals(specialMemory.getMemory(), parsed.getMemory());
        assertEquals(specialMemory.getRole(), parsed.getRole());
        assertEquals(specialMemory.getTags(), parsed.getTags());
    }
}
