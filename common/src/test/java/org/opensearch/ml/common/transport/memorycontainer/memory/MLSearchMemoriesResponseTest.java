/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.memorycontainer.memory;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.opensearch.core.xcontent.ToXContent.EMPTY_PARAMS;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
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

public class MLSearchMemoriesResponseTest {

    private MLSearchMemoriesResponse responseWithHits;
    private MLSearchMemoriesResponse responseEmpty;
    private MLSearchMemoriesResponse responseTimedOut;
    private List<MemorySearchResult> testHits;

    @Before
    public void setUp() {
        Map<String, String> tags = new HashMap<>();
        tags.put("topic", "ML");

        testHits = Arrays
            .asList(
                MemorySearchResult
                    .builder()
                    .memoryId("mem-1")
                    .memory("Machine learning is a subset of AI")
                    .score(0.95f)
                    .sessionId("session-123")
                    .userId("user-456")
                    .memoryType(MemoryType.RAW_MESSAGE)
                    .role("assistant")
                    .tags(tags)
                    .createdTime(Instant.now())
                    .lastUpdatedTime(Instant.now())
                    .build(),
                MemorySearchResult
                    .builder()
                    .memoryId("mem-2")
                    .memory("Deep learning uses neural networks")
                    .score(0.87f)
                    .sessionId("session-123")
                    .memoryType(MemoryType.FACT)
                    .build(),
                MemorySearchResult.builder().memoryId("mem-3").memory("Neural networks have multiple layers").score(0.75f).build()
            );

        // Response with hits
        responseWithHits = MLSearchMemoriesResponse.builder().hits(testHits).totalHits(25L).maxScore(0.95f).timedOut(false).build();

        // Empty response
        responseEmpty = MLSearchMemoriesResponse.builder().hits(new ArrayList<>()).totalHits(0L).maxScore(0.0f).timedOut(false).build();

        // Timed out response
        responseTimedOut = MLSearchMemoriesResponse
            .builder()
            .hits(Arrays.asList(testHits.get(0)))
            .totalHits(1L)
            .maxScore(0.95f)
            .timedOut(true)
            .build();
    }

    @Test
    public void testBuilderWithHits() {
        assertNotNull(responseWithHits);
        assertEquals(testHits, responseWithHits.getHits());
        assertEquals(3, responseWithHits.getHits().size());
        assertEquals(25L, responseWithHits.getTotalHits());
        assertEquals(0.95f, responseWithHits.getMaxScore(), 0.001f);
        assertFalse(responseWithHits.isTimedOut());
    }

    @Test
    public void testBuilderEmpty() {
        assertNotNull(responseEmpty);
        assertEquals(0, responseEmpty.getHits().size());
        assertEquals(0L, responseEmpty.getTotalHits());
        assertEquals(0.0f, responseEmpty.getMaxScore(), 0.001f);
        assertFalse(responseEmpty.isTimedOut());
    }

    @Test
    public void testBuilderTimedOut() {
        assertNotNull(responseTimedOut);
        assertEquals(1, responseTimedOut.getHits().size());
        assertEquals(1L, responseTimedOut.getTotalHits());
        assertEquals(0.95f, responseTimedOut.getMaxScore(), 0.001f);
        assertTrue(responseTimedOut.isTimedOut());
    }

    @Test
    public void testConstructorWithNullHits() {
        MLSearchMemoriesResponse response = new MLSearchMemoriesResponse(null, 0L, 0.0f, false);
        assertNotNull(response.getHits());
        assertEquals(0, response.getHits().size());
    }

    @Test
    public void testStreamInputOutput() throws IOException {
        // Test with hits
        BytesStreamOutput out = new BytesStreamOutput();
        responseWithHits.writeTo(out);
        StreamInput in = out.bytes().streamInput();
        MLSearchMemoriesResponse deserialized = new MLSearchMemoriesResponse(in);

        assertEquals(responseWithHits.getHits().size(), deserialized.getHits().size());
        assertEquals(responseWithHits.getTotalHits(), deserialized.getTotalHits());
        assertEquals(responseWithHits.getMaxScore(), deserialized.getMaxScore(), 0.001f);
        assertEquals(responseWithHits.isTimedOut(), deserialized.isTimedOut());

        // Verify individual hits
        for (int i = 0; i < responseWithHits.getHits().size(); i++) {
            MemorySearchResult original = responseWithHits.getHits().get(i);
            MemorySearchResult deser = deserialized.getHits().get(i);
            assertEquals(original.getMemoryId(), deser.getMemoryId());
            assertEquals(original.getMemory(), deser.getMemory());
            assertEquals(original.getScore(), deser.getScore(), 0.001f);
        }
    }

    @Test
    public void testStreamInputOutputEmpty() throws IOException {
        // Test with empty hits
        BytesStreamOutput out = new BytesStreamOutput();
        responseEmpty.writeTo(out);
        StreamInput in = out.bytes().streamInput();
        MLSearchMemoriesResponse deserialized = new MLSearchMemoriesResponse(in);

        assertEquals(0, deserialized.getHits().size());
        assertEquals(0L, deserialized.getTotalHits());
        assertEquals(0.0f, deserialized.getMaxScore(), 0.001f);
        assertFalse(deserialized.isTimedOut());
    }

    @Test
    public void testToXContent() throws IOException {
        XContentBuilder builder = MediaTypeRegistry.contentBuilder(XContentType.JSON);
        responseWithHits.toXContent(builder, EMPTY_PARAMS);
        String jsonString = TestHelper.xContentBuilderToString(builder);

        // Check structure
        assertTrue(jsonString.contains("\"timed_out\":false"));
        assertTrue(jsonString.contains("\"hits\":{"));
        assertTrue(jsonString.contains("\"total\":25"));
        assertTrue(jsonString.contains("\"max_score\":0.95"));
        assertTrue(jsonString.contains("\"hits\":["));

        // Check individual hits
        assertTrue(jsonString.contains("\"memory_id\":\"mem-1\""));
        assertTrue(jsonString.contains("\"memory\":\"Machine learning is a subset of AI\""));
        assertTrue(jsonString.contains("\"_score\":0.95"));
        assertTrue(jsonString.contains("\"session_id\":\"session-123\""));
    }

    @Test
    public void testToXContentEmpty() throws IOException {
        XContentBuilder builder = MediaTypeRegistry.contentBuilder(XContentType.JSON);
        responseEmpty.toXContent(builder, EMPTY_PARAMS);
        String jsonString = TestHelper.xContentBuilderToString(builder);

        assertTrue(jsonString.contains("\"timed_out\":false"));
        assertTrue(jsonString.contains("\"total\":0"));
        assertTrue(jsonString.contains("\"max_score\":0.0"));
        assertTrue(jsonString.contains("\"hits\":[]"));
    }

    @Test
    public void testToXContentTimedOut() throws IOException {
        XContentBuilder builder = MediaTypeRegistry.contentBuilder(XContentType.JSON);
        responseTimedOut.toXContent(builder, EMPTY_PARAMS);
        String jsonString = TestHelper.xContentBuilderToString(builder);

        assertTrue(jsonString.contains("\"timed_out\":true"));
        assertTrue(jsonString.contains("\"total\":1"));
        assertTrue(jsonString.contains("\"max_score\":0.95"));
        assertEquals(1, jsonString.split("\"memory_id\"").length - 1); // Only one hit
    }

    @Test
    public void testLargeResponse() throws IOException {
        // Test with many hits
        List<MemorySearchResult> manyHits = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            manyHits.add(MemorySearchResult.builder().memoryId("mem-" + i).memory("Memory content " + i).score(1.0f - (i * 0.01f)).build());
        }

        MLSearchMemoriesResponse largeResponse = MLSearchMemoriesResponse
            .builder()
            .hits(manyHits)
            .totalHits(1000L)
            .maxScore(1.0f)
            .timedOut(false)
            .build();

        // Test serialization
        BytesStreamOutput out = new BytesStreamOutput();
        largeResponse.writeTo(out);
        StreamInput in = out.bytes().streamInput();
        MLSearchMemoriesResponse deserialized = new MLSearchMemoriesResponse(in);

        assertEquals(100, deserialized.getHits().size());
        assertEquals(1000L, deserialized.getTotalHits());
        assertEquals(1.0f, deserialized.getMaxScore(), 0.001f);
    }

    @Test
    public void testDifferentScoreValues() {
        // Test various score configurations
        MLSearchMemoriesResponse response1 = MLSearchMemoriesResponse
            .builder()
            .hits(new ArrayList<>())
            .totalHits(0L)
            .maxScore(Float.NaN)
            .timedOut(false)
            .build();

        MLSearchMemoriesResponse response2 = MLSearchMemoriesResponse
            .builder()
            .hits(testHits)
            .totalHits(100L)
            .maxScore(Float.POSITIVE_INFINITY)
            .timedOut(false)
            .build();

        assertEquals(Float.NaN, response1.getMaxScore(), 0.001f);
        assertEquals(Float.POSITIVE_INFINITY, response2.getMaxScore(), 0.001f);
    }

    @Test
    public void testHitsOrdering() throws IOException {
        // Verify hits maintain their order
        BytesStreamOutput out = new BytesStreamOutput();
        responseWithHits.writeTo(out);
        StreamInput in = out.bytes().streamInput();
        MLSearchMemoriesResponse deserialized = new MLSearchMemoriesResponse(in);

        for (int i = 0; i < responseWithHits.getHits().size(); i++) {
            assertEquals(responseWithHits.getHits().get(i).getMemoryId(), deserialized.getHits().get(i).getMemoryId());
            assertEquals(responseWithHits.getHits().get(i).getScore(), deserialized.getHits().get(i).getScore(), 0.001f);
        }
    }

    @Test
    public void testResponseStructure() throws IOException {
        // Test the nested JSON structure matches OpenSearch conventions
        XContentBuilder builder = MediaTypeRegistry.contentBuilder(XContentType.JSON);
        responseWithHits.toXContent(builder, EMPTY_PARAMS);
        String jsonString = TestHelper.xContentBuilderToString(builder);

        // Verify structure: { "timed_out": ..., "hits": { "total": ..., "max_score": ..., "hits": [...] } }
        assertTrue(jsonString.startsWith("{\"timed_out\":"));
        assertTrue(jsonString.contains(",\"hits\":{\"total\":"));
        assertTrue(jsonString.contains(",\"max_score\":"));
        assertTrue(jsonString.contains(",\"hits\":["));
        assertTrue(jsonString.endsWith("}}"));
    }

    @Test
    public void testPartialResults() {
        // Test response with partial results (timed out but has some hits)
        MLSearchMemoriesResponse partialResponse = MLSearchMemoriesResponse
            .builder()
            .hits(Arrays.asList(testHits.get(0), testHits.get(1)))
            .totalHits(50L) // More than returned hits
            .maxScore(0.95f)
            .timedOut(true)
            .build();

        assertEquals(2, partialResponse.getHits().size());
        assertEquals(50L, partialResponse.getTotalHits());
        assertTrue(partialResponse.isTimedOut());
        assertTrue(partialResponse.getTotalHits() > partialResponse.getHits().size());
    }

    @Test
    public void testSpecialCharactersInHits() throws IOException {
        List<MemorySearchResult> specialHits = Arrays
            .asList(
                MemorySearchResult
                    .builder()
                    .memoryId("mem-special-🚀")
                    .memory("Memory with\n\ttabs and \"quotes\"")
                    .score(0.9f)
                    .sessionId("session-✨")
                    .build()
            );

        MLSearchMemoriesResponse specialResponse = MLSearchMemoriesResponse
            .builder()
            .hits(specialHits)
            .totalHits(1L)
            .maxScore(0.9f)
            .timedOut(false)
            .build();

        // Test XContent
        XContentBuilder builder = MediaTypeRegistry.contentBuilder(XContentType.JSON);
        specialResponse.toXContent(builder, EMPTY_PARAMS);
        String jsonString = TestHelper.xContentBuilderToString(builder);

        assertTrue(jsonString.contains("mem-special-"));
        assertTrue(jsonString.contains("Memory with"));
        assertTrue(jsonString.contains("tabs"));
    }

    @Test
    public void testZeroMaxScore() throws IOException {
        // Test when all hits have 0 score
        List<MemorySearchResult> zeroScoreHits = Arrays
            .asList(MemorySearchResult.builder().memoryId("mem-zero-1").memory("Memory with zero score").score(0.0f).build());

        MLSearchMemoriesResponse zeroScoreResponse = MLSearchMemoriesResponse
            .builder()
            .hits(zeroScoreHits)
            .totalHits(1L)
            .maxScore(0.0f)
            .timedOut(false)
            .build();

        assertEquals(0.0f, zeroScoreResponse.getMaxScore(), 0.001f);
        assertEquals(0.0f, zeroScoreResponse.getHits().get(0).getScore(), 0.001f);
    }
}
