/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.memorycontainer.memory;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.opensearch.core.xcontent.ToXContent.EMPTY_PARAMS;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.xcontent.MediaTypeRegistry;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.ml.common.TestHelper;

public class MLAddMemoriesResponseTest {

    private MLAddMemoriesResponse responseWithResults;
    private MLAddMemoriesResponse responseEmpty;
    private MLAddMemoriesResponse responseMinimal;
    private List<MemoryResult> testResults;

    @Before
    public void setUp() {
        testResults = Arrays
            .asList(
                MemoryResult.builder().memoryId("mem-1").memory("User's name is John").event(MemoryEvent.ADD).build(),
                MemoryResult
                    .builder()
                    .memoryId("mem-2")
                    .memory("Lives in San Francisco")
                    .event(MemoryEvent.UPDATE)
                    .oldMemory("Lives in Boston")
                    .build(),
                MemoryResult.builder().memoryId("mem-3").memory("Works at TechCorp").event(MemoryEvent.NONE).build()
            );

        // Response with results
        responseWithResults = MLAddMemoriesResponse.builder().results(testResults).sessionId("session-123").build();

        // Empty response
        responseEmpty = MLAddMemoriesResponse.builder().results(new ArrayList<>()).sessionId("session-empty").build();

        // Minimal response (null results defaults to empty list)
        responseMinimal = MLAddMemoriesResponse.builder().sessionId("session-minimal").build();
    }

    @Test
    public void testBuilderWithResults() {
        assertNotNull(responseWithResults);
        assertEquals(testResults, responseWithResults.getResults());
        assertEquals(3, responseWithResults.getResults().size());
        assertEquals("session-123", responseWithResults.getSessionId());
    }

    @Test
    public void testBuilderEmpty() {
        assertNotNull(responseEmpty);
        assertEquals(0, responseEmpty.getResults().size());
        assertEquals("session-empty", responseEmpty.getSessionId());
    }

    @Test
    public void testBuilderMinimal() {
        assertNotNull(responseMinimal);
        assertEquals(0, responseMinimal.getResults().size());
        assertEquals("session-minimal", responseMinimal.getSessionId());
    }

    @Test
    public void testStreamInputOutput() throws IOException {
        // Test with results
        BytesStreamOutput out = new BytesStreamOutput();
        responseWithResults.writeTo(out);
        StreamInput in = out.bytes().streamInput();
        MLAddMemoriesResponse deserialized = new MLAddMemoriesResponse(in);

        assertEquals(responseWithResults.getResults().size(), deserialized.getResults().size());
        for (int i = 0; i < responseWithResults.getResults().size(); i++) {
            MemoryResult original = responseWithResults.getResults().get(i);
            MemoryResult deser = deserialized.getResults().get(i);
            assertEquals(original.getMemoryId(), deser.getMemoryId());
            assertEquals(original.getMemory(), deser.getMemory());
            assertEquals(original.getEvent(), deser.getEvent());
            assertEquals(original.getOldMemory(), deser.getOldMemory());
        }
        assertEquals(responseWithResults.getSessionId(), deserialized.getSessionId());
    }

    @Test
    public void testStreamInputOutputEmpty() throws IOException {
        // Test with empty results
        BytesStreamOutput out = new BytesStreamOutput();
        responseEmpty.writeTo(out);
        StreamInput in = out.bytes().streamInput();
        MLAddMemoriesResponse deserialized = new MLAddMemoriesResponse(in);

        assertEquals(0, deserialized.getResults().size());
        assertEquals(responseEmpty.getSessionId(), deserialized.getSessionId());
    }

    @Test
    public void testToXContent() throws IOException {
        XContentBuilder builder = MediaTypeRegistry.contentBuilder(XContentType.JSON);
        responseWithResults.toXContent(builder, EMPTY_PARAMS);
        String jsonString = TestHelper.xContentBuilderToString(builder);

        assertTrue(jsonString.contains("\"results\":["));
        assertTrue(jsonString.contains("\"id\":\"mem-1\""));
        assertTrue(jsonString.contains("\"text\":\"User's name is John\""));
        assertTrue(jsonString.contains("\"event\":\"ADD\""));
        assertTrue(jsonString.contains("\"id\":\"mem-2\""));
        assertTrue(jsonString.contains("\"text\":\"Lives in San Francisco\""));
        assertTrue(jsonString.contains("\"event\":\"UPDATE\""));
        assertTrue(jsonString.contains("\"old_memory\":\"Lives in Boston\""));
        assertTrue(jsonString.contains("\"session_id\":\"session-123\""));
    }

    @Test
    public void testToXContentEmpty() throws IOException {
        XContentBuilder builder = MediaTypeRegistry.contentBuilder(XContentType.JSON);
        responseEmpty.toXContent(builder, EMPTY_PARAMS);
        String jsonString = TestHelper.xContentBuilderToString(builder);

        assertTrue(jsonString.contains("\"results\":[]"));
        assertTrue(jsonString.contains("\"session_id\":\"session-empty\""));
    }

    @Test
    public void testToString() {
        String str = responseWithResults.toString();
        assertNotNull(str);
        assertTrue(str.contains("session-123"));
        assertTrue(str.contains("results"));
    }

    @Test
    public void testDifferentEventTypes() throws IOException {
        List<MemoryResult> mixedResults = Arrays
            .asList(
                new MemoryResult("add-1", "New fact", MemoryEvent.ADD, null),
                new MemoryResult("update-1", "Updated fact", MemoryEvent.UPDATE, "Old fact"),
                new MemoryResult("delete-1", "Deleted fact", MemoryEvent.DELETE, null),
                new MemoryResult("none-1", "Unchanged fact", MemoryEvent.NONE, null)
            );

        MLAddMemoriesResponse mixedResponse = MLAddMemoriesResponse.builder().results(mixedResults).sessionId("session-mixed").build();

        // Test serialization
        BytesStreamOutput out = new BytesStreamOutput();
        mixedResponse.writeTo(out);
        StreamInput in = out.bytes().streamInput();
        MLAddMemoriesResponse deserialized = new MLAddMemoriesResponse(in);

        assertEquals(4, deserialized.getResults().size());
        assertEquals(MemoryEvent.ADD, deserialized.getResults().get(0).getEvent());
        assertEquals(MemoryEvent.UPDATE, deserialized.getResults().get(1).getEvent());
        assertEquals(MemoryEvent.DELETE, deserialized.getResults().get(2).getEvent());
        assertEquals(MemoryEvent.NONE, deserialized.getResults().get(3).getEvent());
    }

    @Test
    public void testLargeResponse() throws IOException {
        // Test with many results
        List<MemoryResult> manyResults = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            manyResults
                .add(
                    MemoryResult
                        .builder()
                        .memoryId("mem-" + i)
                        .memory("Memory content " + i)
                        .event(i % 2 == 0 ? MemoryEvent.ADD : MemoryEvent.UPDATE)
                        .oldMemory(i % 2 == 0 ? null : "Old memory " + i)
                        .build()
                );
        }

        MLAddMemoriesResponse largeResponse = MLAddMemoriesResponse.builder().results(manyResults).sessionId("session-large").build();

        // Test serialization
        BytesStreamOutput out = new BytesStreamOutput();
        largeResponse.writeTo(out);
        StreamInput in = out.bytes().streamInput();
        MLAddMemoriesResponse deserialized = new MLAddMemoriesResponse(in);

        assertEquals(100, deserialized.getResults().size());
        assertEquals("session-large", deserialized.getSessionId());
    }

    @Test
    public void testSpecialCharactersInResponse() throws IOException {
        List<MemoryResult> specialResults = Arrays
            .asList(
                MemoryResult
                    .builder()
                    .memoryId("mem-special-🚀")
                    .memory("Memory with\n\ttabs and \"quotes\"")
                    .event(MemoryEvent.ADD)
                    .build(),
                MemoryResult
                    .builder()
                    .memoryId("mem-unicode-✨")
                    .memory("Memory with unicode characters")
                    .event(MemoryEvent.UPDATE)
                    .oldMemory("Old memory with 'single quotes'")
                    .build()
            );

        MLAddMemoriesResponse specialResponse = MLAddMemoriesResponse
            .builder()
            .results(specialResults)
            .sessionId("session-special-chars")
            .build();

        // Test XContent
        XContentBuilder builder = MediaTypeRegistry.contentBuilder(XContentType.JSON);
        specialResponse.toXContent(builder, EMPTY_PARAMS);
        String jsonString = TestHelper.xContentBuilderToString(builder);

        assertTrue(jsonString.contains("mem-special-"));
        assertTrue(jsonString.contains("Memory with"));
        assertTrue(jsonString.contains("tabs"));
        assertTrue(jsonString.contains("quotes"));
    }

    @Test
    public void testConstructorWithNullResults() {
        MLAddMemoriesResponse response = new MLAddMemoriesResponse(null, "session-null");
        assertNotNull(response.getResults());
        assertEquals(0, response.getResults().size());
        assertEquals("session-null", response.getSessionId());
    }

    @Test
    public void testResultsOrder() throws IOException {
        // Verify results maintain their order
        BytesStreamOutput out = new BytesStreamOutput();
        responseWithResults.writeTo(out);
        StreamInput in = out.bytes().streamInput();
        MLAddMemoriesResponse deserialized = new MLAddMemoriesResponse(in);

        for (int i = 0; i < testResults.size(); i++) {
            assertEquals(testResults.get(i).getMemoryId(), deserialized.getResults().get(i).getMemoryId());
        }
    }
}
