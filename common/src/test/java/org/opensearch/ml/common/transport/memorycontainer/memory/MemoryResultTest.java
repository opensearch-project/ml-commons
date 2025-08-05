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

import org.junit.Before;
import org.junit.Test;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.xcontent.MediaTypeRegistry;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.ml.common.TestHelper;

public class MemoryResultTest {

    private MemoryResult resultWithAllFields;
    private MemoryResult resultMinimal;
    private MemoryResult addResult;
    private MemoryResult updateResult;
    private MemoryResult deleteResult;
    private MemoryResult noneResult;

    @Before
    public void setUp() {
        // UPDATE result with all fields including oldMemory
        resultWithAllFields = MemoryResult
            .builder()
            .memoryId("memory-123")
            .memory("Updated memory text")
            .event(MemoryEvent.UPDATE)
            .oldMemory("Original memory text")
            .build();

        // Minimal result (no oldMemory)
        resultMinimal = MemoryResult.builder().memoryId("memory-456").memory("New memory text").event(MemoryEvent.ADD).build();

        // Different event types
        addResult = new MemoryResult("add-789", "Adding new memory", MemoryEvent.ADD, null);
        updateResult = new MemoryResult("update-101", "Updating memory", MemoryEvent.UPDATE, "Old text");
        deleteResult = new MemoryResult("delete-202", "Deleting memory", MemoryEvent.DELETE, null);
        noneResult = new MemoryResult("none-303", "No change", MemoryEvent.NONE, null);
    }

    @Test
    public void testBuilderWithAllFields() {
        assertNotNull(resultWithAllFields);
        assertEquals("memory-123", resultWithAllFields.getMemoryId());
        assertEquals("Updated memory text", resultWithAllFields.getMemory());
        assertEquals(MemoryEvent.UPDATE, resultWithAllFields.getEvent());
        assertEquals("Original memory text", resultWithAllFields.getOldMemory());
    }

    @Test
    public void testBuilderMinimal() {
        assertNotNull(resultMinimal);
        assertEquals("memory-456", resultMinimal.getMemoryId());
        assertEquals("New memory text", resultMinimal.getMemory());
        assertEquals(MemoryEvent.ADD, resultMinimal.getEvent());
        assertNull(resultMinimal.getOldMemory());
    }

    @Test
    public void testConstructorWithAllParameters() {
        MemoryResult result = new MemoryResult("id-1", "text-1", MemoryEvent.UPDATE, "old-text");
        assertEquals("id-1", result.getMemoryId());
        assertEquals("text-1", result.getMemory());
        assertEquals(MemoryEvent.UPDATE, result.getEvent());
        assertEquals("old-text", result.getOldMemory());
    }

    @Test
    public void testConstructorWithNullOldMemory() {
        MemoryResult result = new MemoryResult("id-2", "text-2", MemoryEvent.ADD, null);
        assertEquals("id-2", result.getMemoryId());
        assertEquals("text-2", result.getMemory());
        assertEquals(MemoryEvent.ADD, result.getEvent());
        assertNull(result.getOldMemory());
    }

    @Test
    public void testStreamInputOutput() throws IOException {
        // Test with all fields
        BytesStreamOutput out = new BytesStreamOutput();
        resultWithAllFields.writeTo(out);
        StreamInput in = out.bytes().streamInput();
        MemoryResult deserialized = new MemoryResult(in);

        assertEquals(resultWithAllFields.getMemoryId(), deserialized.getMemoryId());
        assertEquals(resultWithAllFields.getMemory(), deserialized.getMemory());
        assertEquals(resultWithAllFields.getEvent(), deserialized.getEvent());
        assertEquals(resultWithAllFields.getOldMemory(), deserialized.getOldMemory());
    }

    @Test
    public void testStreamInputOutputMinimal() throws IOException {
        // Test with minimal fields
        BytesStreamOutput out = new BytesStreamOutput();
        resultMinimal.writeTo(out);
        StreamInput in = out.bytes().streamInput();
        MemoryResult deserialized = new MemoryResult(in);

        assertEquals(resultMinimal.getMemoryId(), deserialized.getMemoryId());
        assertEquals(resultMinimal.getMemory(), deserialized.getMemory());
        assertEquals(resultMinimal.getEvent(), deserialized.getEvent());
        assertNull(deserialized.getOldMemory());
    }

    @Test
    public void testStreamInputOutputAllEventTypes() throws IOException {
        // Test all event types
        MemoryResult[] results = { addResult, updateResult, deleteResult, noneResult };

        for (MemoryResult original : results) {
            BytesStreamOutput out = new BytesStreamOutput();
            original.writeTo(out);
            StreamInput in = out.bytes().streamInput();
            MemoryResult deserialized = new MemoryResult(in);

            assertEquals(original.getMemoryId(), deserialized.getMemoryId());
            assertEquals(original.getMemory(), deserialized.getMemory());
            assertEquals(original.getEvent(), deserialized.getEvent());
            assertEquals(original.getOldMemory(), deserialized.getOldMemory());
        }
    }

    @Test
    public void testToXContent() throws IOException {
        XContentBuilder builder = MediaTypeRegistry.contentBuilder(XContentType.JSON);
        resultWithAllFields.toXContent(builder, EMPTY_PARAMS);
        String jsonString = TestHelper.xContentBuilderToString(builder);

        // Uses "id" and "text" fields instead of "memory_id" and "memory"
        assertTrue(jsonString.contains("\"id\":\"memory-123\""));
        assertTrue(jsonString.contains("\"text\":\"Updated memory text\""));
        assertTrue(jsonString.contains("\"event\":\"UPDATE\""));
        assertTrue(jsonString.contains("\"old_memory\":\"Original memory text\""));
    }

    @Test
    public void testToXContentMinimal() throws IOException {
        XContentBuilder builder = MediaTypeRegistry.contentBuilder(XContentType.JSON);
        resultMinimal.toXContent(builder, EMPTY_PARAMS);
        String jsonString = TestHelper.xContentBuilderToString(builder);

        assertTrue(jsonString.contains("\"id\":\"memory-456\""));
        assertTrue(jsonString.contains("\"text\":\"New memory text\""));
        assertTrue(jsonString.contains("\"event\":\"ADD\""));
        // old_memory should not be present
        assertTrue(!jsonString.contains("\"old_memory\""));
    }

    @Test
    public void testToXContentDifferentEvents() throws IOException {
        // Test ADD event
        XContentBuilder addBuilder = MediaTypeRegistry.contentBuilder(XContentType.JSON);
        addResult.toXContent(addBuilder, EMPTY_PARAMS);
        String addJson = TestHelper.xContentBuilderToString(addBuilder);
        assertTrue(addJson.contains("\"event\":\"ADD\""));
        assertTrue(!addJson.contains("\"old_memory\""));

        // Test UPDATE event
        XContentBuilder updateBuilder = MediaTypeRegistry.contentBuilder(XContentType.JSON);
        updateResult.toXContent(updateBuilder, EMPTY_PARAMS);
        String updateJson = TestHelper.xContentBuilderToString(updateBuilder);
        assertTrue(updateJson.contains("\"event\":\"UPDATE\""));
        assertTrue(updateJson.contains("\"old_memory\":\"Old text\""));

        // Test DELETE event
        XContentBuilder deleteBuilder = MediaTypeRegistry.contentBuilder(XContentType.JSON);
        deleteResult.toXContent(deleteBuilder, EMPTY_PARAMS);
        String deleteJson = TestHelper.xContentBuilderToString(deleteBuilder);
        assertTrue(deleteJson.contains("\"event\":\"DELETE\""));

        // Test NONE event
        XContentBuilder noneBuilder = MediaTypeRegistry.contentBuilder(XContentType.JSON);
        noneResult.toXContent(noneBuilder, EMPTY_PARAMS);
        String noneJson = TestHelper.xContentBuilderToString(noneBuilder);
        assertTrue(noneJson.contains("\"event\":\"NONE\""));
    }

    @Test
    public void testToString() {
        String str = resultWithAllFields.toString();
        assertNotNull(str);
        assertTrue(str.contains("memory-123"));
        assertTrue(str.contains("Updated memory text"));
        assertTrue(str.contains("UPDATE"));
        assertTrue(str.contains("Original memory text"));
    }

    @Test
    public void testSpecialCharactersInFields() throws IOException {
        MemoryResult specialResult = MemoryResult
            .builder()
            .memoryId("id-with-special-chars-🚀")
            .memory("Text with\n\ttabs and\nnewlines and \"quotes\"")
            .event(MemoryEvent.UPDATE)
            .oldMemory("Old text with 'single quotes' and \\backslashes\\")
            .build();

        // Test serialization round trip
        BytesStreamOutput out = new BytesStreamOutput();
        specialResult.writeTo(out);
        StreamInput in = out.bytes().streamInput();
        MemoryResult deserialized = new MemoryResult(in);

        assertEquals(specialResult.getMemoryId(), deserialized.getMemoryId());
        assertEquals(specialResult.getMemory(), deserialized.getMemory());
        assertEquals(specialResult.getOldMemory(), deserialized.getOldMemory());

        // Test XContent
        XContentBuilder builder = MediaTypeRegistry.contentBuilder(XContentType.JSON);
        specialResult.toXContent(builder, EMPTY_PARAMS);
        String jsonString = TestHelper.xContentBuilderToString(builder);

        assertTrue(jsonString.contains("id-with-special-chars-"));
        assertTrue(jsonString.contains("Text with"));
        assertTrue(jsonString.contains("tabs"));
    }

    @Test
    public void testEmptyStrings() throws IOException {
        MemoryResult emptyResult = new MemoryResult("", "", MemoryEvent.NONE, "");

        // Test serialization
        BytesStreamOutput out = new BytesStreamOutput();
        emptyResult.writeTo(out);
        StreamInput in = out.bytes().streamInput();
        MemoryResult deserialized = new MemoryResult(in);

        assertEquals("", deserialized.getMemoryId());
        assertEquals("", deserialized.getMemory());
        assertEquals(MemoryEvent.NONE, deserialized.getEvent());
        assertEquals("", deserialized.getOldMemory());
    }

    @Test
    public void testBuilderDefaults() {
        // Test builder with only required fields
        MemoryResult result = MemoryResult.builder().memoryId("test-id").memory("test memory").event(MemoryEvent.ADD).build();

        assertEquals("test-id", result.getMemoryId());
        assertEquals("test memory", result.getMemory());
        assertEquals(MemoryEvent.ADD, result.getEvent());
        assertNull(result.getOldMemory());
    }
}
