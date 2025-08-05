/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.memorycontainer.memory;

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
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.xcontent.MediaTypeRegistry;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.ml.common.TestHelper;
import org.opensearch.ml.common.memorycontainer.MemoryType;

public class MemorySearchResultTest {

    private MemorySearchResult resultWithAllFields;
    private MemorySearchResult resultMinimal;
    private MemorySearchResult resultNoOptionals;
    private Map<String, String> testTags;
    private Instant testCreatedTime;
    private Instant testUpdatedTime;

    @Before
    public void setUp() {
        testCreatedTime = Instant.now();
        testUpdatedTime = Instant.now().plusSeconds(60);

        testTags = new HashMap<>();
        testTags.put("topic", "machine learning");
        testTags.put("priority", "high");

        // Result with all fields
        resultWithAllFields = MemorySearchResult
            .builder()
            .memoryId("memory-123")
            .memory("This is a test memory content")
            .score(0.95f)
            .sessionId("session-456")
            .agentId("agent-789")
            .userId("user-101")
            .memoryType(MemoryType.RAW_MESSAGE)
            .role("user")
            .tags(testTags)
            .createdTime(testCreatedTime)
            .lastUpdatedTime(testUpdatedTime)
            .build();

        // Minimal result (only required fields)
        resultMinimal = MemorySearchResult.builder().memoryId("memory-minimal").memory("Minimal memory").score(0.5f).build();

        // Result without optional fields
        resultNoOptionals = new MemorySearchResult(
            "memory-no-opt",
            "Memory without optionals",
            0.75f,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null
        );
    }

    @Test
    public void testBuilderWithAllFields() {
        assertNotNull(resultWithAllFields);
        assertEquals("memory-123", resultWithAllFields.getMemoryId());
        assertEquals("This is a test memory content", resultWithAllFields.getMemory());
        assertEquals(0.95f, resultWithAllFields.getScore(), 0.001f);
        assertEquals("session-456", resultWithAllFields.getSessionId());
        assertEquals("agent-789", resultWithAllFields.getAgentId());
        assertEquals("user-101", resultWithAllFields.getUserId());
        assertEquals(MemoryType.RAW_MESSAGE, resultWithAllFields.getMemoryType());
        assertEquals("user", resultWithAllFields.getRole());
        assertEquals(testTags, resultWithAllFields.getTags());
        assertEquals(testCreatedTime, resultWithAllFields.getCreatedTime());
        assertEquals(testUpdatedTime, resultWithAllFields.getLastUpdatedTime());
    }

    @Test
    public void testBuilderMinimal() {
        assertNotNull(resultMinimal);
        assertEquals("memory-minimal", resultMinimal.getMemoryId());
        assertEquals("Minimal memory", resultMinimal.getMemory());
        assertEquals(0.5f, resultMinimal.getScore(), 0.001f);
        assertNull(resultMinimal.getSessionId());
        assertNull(resultMinimal.getAgentId());
        assertNull(resultMinimal.getUserId());
        assertNull(resultMinimal.getMemoryType());
        assertNull(resultMinimal.getRole());
        assertNull(resultMinimal.getTags());
        assertNull(resultMinimal.getCreatedTime());
        assertNull(resultMinimal.getLastUpdatedTime());
    }

    @Test
    public void testConstructorWithAllParameters() {
        Map<String, String> tags = new HashMap<>();
        tags.put("key", "value");
        Instant now = Instant.now();

        MemorySearchResult result = new MemorySearchResult(
            "id-1",
            "memory-1",
            0.85f,
            "session-1",
            "agent-1",
            "user-1",
            MemoryType.FACT,
            "assistant",
            tags,
            now,
            now.plusSeconds(10)
        );

        assertEquals("id-1", result.getMemoryId());
        assertEquals("memory-1", result.getMemory());
        assertEquals(0.85f, result.getScore(), 0.001f);
        assertEquals("session-1", result.getSessionId());
        assertEquals("agent-1", result.getAgentId());
        assertEquals("user-1", result.getUserId());
        assertEquals(MemoryType.FACT, result.getMemoryType());
        assertEquals("assistant", result.getRole());
        assertEquals(tags, result.getTags());
        assertEquals(now, result.getCreatedTime());
        assertEquals(now.plusSeconds(10), result.getLastUpdatedTime());
    }

    @Test
    public void testStreamInputOutput() throws IOException {
        // Test with all fields
        BytesStreamOutput out = new BytesStreamOutput();
        resultWithAllFields.writeTo(out);
        StreamInput in = out.bytes().streamInput();
        MemorySearchResult deserialized = new MemorySearchResult(in);

        assertEquals(resultWithAllFields.getMemoryId(), deserialized.getMemoryId());
        assertEquals(resultWithAllFields.getMemory(), deserialized.getMemory());
        assertEquals(resultWithAllFields.getScore(), deserialized.getScore(), 0.001f);
        assertEquals(resultWithAllFields.getSessionId(), deserialized.getSessionId());
        assertEquals(resultWithAllFields.getAgentId(), deserialized.getAgentId());
        assertEquals(resultWithAllFields.getUserId(), deserialized.getUserId());
        assertEquals(resultWithAllFields.getMemoryType(), deserialized.getMemoryType());
        assertEquals(resultWithAllFields.getRole(), deserialized.getRole());
        assertEquals(resultWithAllFields.getTags(), deserialized.getTags());
        assertEquals(resultWithAllFields.getCreatedTime(), deserialized.getCreatedTime());
        assertEquals(resultWithAllFields.getLastUpdatedTime(), deserialized.getLastUpdatedTime());
    }

    @Test
    public void testStreamInputOutputMinimal() throws IOException {
        // Test with minimal fields
        BytesStreamOutput out = new BytesStreamOutput();
        resultMinimal.writeTo(out);
        StreamInput in = out.bytes().streamInput();
        MemorySearchResult deserialized = new MemorySearchResult(in);

        assertEquals(resultMinimal.getMemoryId(), deserialized.getMemoryId());
        assertEquals(resultMinimal.getMemory(), deserialized.getMemory());
        assertEquals(resultMinimal.getScore(), deserialized.getScore(), 0.001f);
        assertNull(deserialized.getSessionId());
        assertNull(deserialized.getAgentId());
        assertNull(deserialized.getUserId());
        assertNull(deserialized.getMemoryType());
        assertNull(deserialized.getRole());
        assertNull(deserialized.getTags());
        assertNull(deserialized.getCreatedTime());
        assertNull(deserialized.getLastUpdatedTime());
    }

    @Test
    public void testStreamInputOutputEmptyTags() throws IOException {
        // Test with empty tags
        MemorySearchResult resultEmptyTags = MemorySearchResult
            .builder()
            .memoryId("memory-empty-tags")
            .memory("Memory with empty tags")
            .score(0.8f)
            .tags(new HashMap<>())
            .build();

        BytesStreamOutput out = new BytesStreamOutput();
        resultEmptyTags.writeTo(out);
        StreamInput in = out.bytes().streamInput();
        MemorySearchResult deserialized = new MemorySearchResult(in);

        assertNull(deserialized.getTags());
    }

    @Test
    public void testToXContent() throws IOException {
        XContentBuilder builder = MediaTypeRegistry.contentBuilder(XContentType.JSON);
        resultWithAllFields.toXContent(builder, EMPTY_PARAMS);
        String jsonString = TestHelper.xContentBuilderToString(builder);

        assertTrue(jsonString.contains("\"memory_id\":\"memory-123\""));
        assertTrue(jsonString.contains("\"memory\":\"This is a test memory content\""));
        assertTrue(jsonString.contains("\"_score\":0.95"));
        assertTrue(jsonString.contains("\"session_id\":\"session-456\""));
        assertTrue(jsonString.contains("\"agent_id\":\"agent-789\""));
        assertTrue(jsonString.contains("\"user_id\":\"user-101\""));
        assertTrue(jsonString.contains("\"memory_type\":\"RAW_MESSAGE\""));
        assertTrue(jsonString.contains("\"role\":\"user\""));
        assertTrue(jsonString.contains("\"topic\":\"machine learning\""));
        assertTrue(jsonString.contains("\"priority\":\"high\""));
        assertTrue(jsonString.contains("\"created_time\":" + testCreatedTime.toEpochMilli()));
        assertTrue(jsonString.contains("\"last_updated_time\":" + testUpdatedTime.toEpochMilli()));
    }

    @Test
    public void testToXContentMinimal() throws IOException {
        XContentBuilder builder = MediaTypeRegistry.contentBuilder(XContentType.JSON);
        resultMinimal.toXContent(builder, EMPTY_PARAMS);
        String jsonString = TestHelper.xContentBuilderToString(builder);

        assertTrue(jsonString.contains("\"memory_id\":\"memory-minimal\""));
        assertTrue(jsonString.contains("\"memory\":\"Minimal memory\""));
        assertTrue(jsonString.contains("\"_score\":0.5"));
        // Optional fields should not be present
        assertTrue(!jsonString.contains("\"session_id\""));
        assertTrue(!jsonString.contains("\"agent_id\""));
        assertTrue(!jsonString.contains("\"user_id\""));
        assertTrue(!jsonString.contains("\"memory_type\""));
        assertTrue(!jsonString.contains("\"role\""));
        assertTrue(!jsonString.contains("\"tags\""));
        assertTrue(!jsonString.contains("\"created_time\""));
        assertTrue(!jsonString.contains("\"last_updated_time\""));
    }

    @Test
    public void testToXContentNoOptionals() throws IOException {
        XContentBuilder builder = MediaTypeRegistry.contentBuilder(XContentType.JSON);
        resultNoOptionals.toXContent(builder, EMPTY_PARAMS);
        String jsonString = TestHelper.xContentBuilderToString(builder);

        assertTrue(jsonString.contains("\"memory_id\":\"memory-no-opt\""));
        assertTrue(jsonString.contains("\"memory\":\"Memory without optionals\""));
        assertTrue(jsonString.contains("\"_score\":0.75"));
        // All optional fields should be absent
        assertTrue(!jsonString.contains("\"session_id\""));
        assertTrue(!jsonString.contains("\"tags\""));
        assertTrue(!jsonString.contains("\"created_time\""));
    }

    @Test
    public void testToString() {
        String str = resultWithAllFields.toString();
        assertNotNull(str);
        assertTrue(str.contains("memory-123"));
        assertTrue(str.contains("This is a test memory content"));
        assertTrue(str.contains("0.95"));
        assertTrue(str.contains("session-456"));
    }

    @Test
    public void testDifferentMemoryTypes() throws IOException {
        // Test with FACT type
        MemorySearchResult factResult = MemorySearchResult
            .builder()
            .memoryId("fact-123")
            .memory("User's name is John")
            .score(0.9f)
            .memoryType(MemoryType.FACT)
            .build();

        BytesStreamOutput out = new BytesStreamOutput();
        factResult.writeTo(out);
        StreamInput in = out.bytes().streamInput();
        MemorySearchResult deserialized = new MemorySearchResult(in);

        assertEquals(MemoryType.FACT, deserialized.getMemoryType());

        // Test XContent
        XContentBuilder builder = MediaTypeRegistry.contentBuilder(XContentType.JSON);
        factResult.toXContent(builder, EMPTY_PARAMS);
        String jsonString = TestHelper.xContentBuilderToString(builder);
        assertTrue(jsonString.contains("\"memory_type\":\"FACT\""));
    }

    @Test
    public void testScoreValues() {
        // Test various score values
        float[] scores = { 0.0f, 0.5f, 0.999f, 1.0f, 100.0f };

        for (float score : scores) {
            MemorySearchResult result = MemorySearchResult.builder().memoryId("id-" + score).memory("memory-" + score).score(score).build();

            assertEquals(score, result.getScore(), 0.001f);
        }
    }

    @Test
    public void testSpecialCharactersInFields() throws IOException {
        Map<String, String> specialTags = new HashMap<>();
        specialTags.put("key with spaces", "value with\nnewlines");
        specialTags.put("unicode_key_🔥", "unicode_value_✨");

        MemorySearchResult specialResult = MemorySearchResult
            .builder()
            .memoryId("id-with-special-chars-🚀")
            .memory("Memory with\n\ttabs and\nnewlines and \"quotes\"")
            .score(0.99f)
            .sessionId("session-with-special-chars")
            .role("user/assistant")
            .tags(specialTags)
            .createdTime(testCreatedTime)
            .lastUpdatedTime(testUpdatedTime)
            .build();

        // Test serialization round trip
        BytesStreamOutput out = new BytesStreamOutput();
        specialResult.writeTo(out);
        StreamInput in = out.bytes().streamInput();
        MemorySearchResult deserialized = new MemorySearchResult(in);

        assertEquals(specialResult.getMemoryId(), deserialized.getMemoryId());
        assertEquals(specialResult.getMemory(), deserialized.getMemory());
        assertEquals(specialResult.getRole(), deserialized.getRole());
        assertEquals(specialResult.getTags(), deserialized.getTags());
    }

    @Test
    public void testNullHandling() throws IOException {
        // Create result with explicit nulls
        MemorySearchResult nullResult = new MemorySearchResult(
            "id-null",
            "memory-null",
            0.1f,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null
        );

        // Test serialization
        BytesStreamOutput out = new BytesStreamOutput();
        nullResult.writeTo(out);
        StreamInput in = out.bytes().streamInput();
        MemorySearchResult deserialized = new MemorySearchResult(in);

        assertEquals("id-null", deserialized.getMemoryId());
        assertEquals("memory-null", deserialized.getMemory());
        assertEquals(0.1f, deserialized.getScore(), 0.001f);
        assertNull(deserialized.getSessionId());
        assertNull(deserialized.getAgentId());
        assertNull(deserialized.getUserId());
        assertNull(deserialized.getMemoryType());
        assertNull(deserialized.getRole());
        assertNull(deserialized.getTags());
        assertNull(deserialized.getCreatedTime());
        assertNull(deserialized.getLastUpdatedTime());
    }
}
