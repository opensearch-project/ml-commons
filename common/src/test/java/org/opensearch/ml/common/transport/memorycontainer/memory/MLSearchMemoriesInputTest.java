/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.memorycontainer.memory;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.opensearch.core.xcontent.ToXContent.EMPTY_PARAMS;

import java.io.IOException;

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

public class MLSearchMemoriesInputTest {

    private MLSearchMemoriesInput inputWithContainerId;
    private MLSearchMemoriesInput inputWithoutContainerId;

    @Before
    public void setUp() {
        inputWithContainerId = MLSearchMemoriesInput
            .builder()
            .memoryContainerId("container-123")
            .query("machine learning concepts")
            .build();

        inputWithoutContainerId = MLSearchMemoriesInput.builder().query("search without container id").build();
    }

    @Test
    public void testBuilderWithContainerId() {
        assertNotNull(inputWithContainerId);
        assertEquals("container-123", inputWithContainerId.getMemoryContainerId());
        assertEquals("machine learning concepts", inputWithContainerId.getQuery());
    }

    @Test
    public void testBuilderWithoutContainerId() {
        assertNotNull(inputWithoutContainerId);
        assertNull(inputWithoutContainerId.getMemoryContainerId());
        assertEquals("search without container id", inputWithoutContainerId.getQuery());
    }

    @Test
    public void testConstructorWithContainerId() {
        MLSearchMemoriesInput input = new MLSearchMemoriesInput("container-456", "test query");
        assertEquals("container-456", input.getMemoryContainerId());
        assertEquals("test query", input.getQuery());
    }

    @Test
    public void testConstructorWithoutContainerId() {
        MLSearchMemoriesInput input = new MLSearchMemoriesInput(null, "another query");
        assertNull(input.getMemoryContainerId());
        assertEquals("another query", input.getQuery());
    }

    @Test
    public void testConstructorWithNullQuery() {
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> new MLSearchMemoriesInput("container-1", null)
        );
        assertEquals("Query cannot be null or empty", exception.getMessage());
    }

    @Test
    public void testConstructorWithEmptyQuery() {
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> new MLSearchMemoriesInput("container-1", "")
        );
        assertEquals("Query cannot be null or empty", exception.getMessage());
    }

    @Test
    public void testConstructorWithWhitespaceQuery() {
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> new MLSearchMemoriesInput("container-1", "   ")
        );
        assertEquals("Query cannot be null or empty", exception.getMessage());
    }

    @Test
    public void testQueryTrimming() {
        MLSearchMemoriesInput input = new MLSearchMemoriesInput("container-1", "  query with spaces  ");
        assertEquals("query with spaces", input.getQuery());
    }

    @Test
    public void testStreamInputOutput() throws IOException {
        // Test with container ID
        BytesStreamOutput out = new BytesStreamOutput();
        inputWithContainerId.writeTo(out);
        StreamInput in = out.bytes().streamInput();
        MLSearchMemoriesInput deserialized = new MLSearchMemoriesInput(in);

        assertEquals(inputWithContainerId.getMemoryContainerId(), deserialized.getMemoryContainerId());
        assertEquals(inputWithContainerId.getQuery(), deserialized.getQuery());
    }

    @Test
    public void testStreamInputOutputWithoutContainerId() throws IOException {
        // Test without container ID
        BytesStreamOutput out = new BytesStreamOutput();
        inputWithoutContainerId.writeTo(out);
        StreamInput in = out.bytes().streamInput();
        MLSearchMemoriesInput deserialized = new MLSearchMemoriesInput(in);

        assertNull(deserialized.getMemoryContainerId());
        assertEquals(inputWithoutContainerId.getQuery(), deserialized.getQuery());
    }

    @Test
    public void testToXContent() throws IOException {
        XContentBuilder builder = MediaTypeRegistry.contentBuilder(XContentType.JSON);
        inputWithContainerId.toXContent(builder, EMPTY_PARAMS);
        String jsonString = TestHelper.xContentBuilderToString(builder);

        assertTrue(jsonString.contains("\"memory_container_id\":\"container-123\""));
        assertTrue(jsonString.contains("\"query\":\"machine learning concepts\""));
    }

    @Test
    public void testToXContentWithoutContainerId() throws IOException {
        XContentBuilder builder = MediaTypeRegistry.contentBuilder(XContentType.JSON);
        inputWithoutContainerId.toXContent(builder, EMPTY_PARAMS);
        String jsonString = TestHelper.xContentBuilderToString(builder);

        assertTrue(!jsonString.contains("\"memory_container_id\""));
        assertTrue(jsonString.contains("\"query\":\"search without container id\""));
    }

    @Test
    public void testParse() throws IOException {
        String jsonString = "{\"memory_container_id\":\"container-789\",\"query\":\"neural networks\"}";

        XContentParser parser = XContentType.JSON
            .xContent()
            .createParser(NamedXContentRegistry.EMPTY, LoggingDeprecationHandler.INSTANCE, jsonString);
        parser.nextToken();

        MLSearchMemoriesInput parsed = MLSearchMemoriesInput.parse(parser);

        assertEquals("container-789", parsed.getMemoryContainerId());
        assertEquals("neural networks", parsed.getQuery());
    }

    @Test
    public void testParseWithoutContainerId() throws IOException {
        String jsonString = "{\"query\":\"deep learning\"}";

        XContentParser parser = XContentType.JSON
            .xContent()
            .createParser(NamedXContentRegistry.EMPTY, LoggingDeprecationHandler.INSTANCE, jsonString);
        parser.nextToken();

        MLSearchMemoriesInput parsed = MLSearchMemoriesInput.parse(parser);

        assertNull(parsed.getMemoryContainerId());
        assertEquals("deep learning", parsed.getQuery());
    }

    @Test
    public void testParseWithUnknownFields() throws IOException {
        String jsonString = "{\"query\":\"test query\",\"unknown_field\":\"ignored\",\"another\":123}";

        XContentParser parser = XContentType.JSON
            .xContent()
            .createParser(NamedXContentRegistry.EMPTY, LoggingDeprecationHandler.INSTANCE, jsonString);
        parser.nextToken();

        MLSearchMemoriesInput parsed = MLSearchMemoriesInput.parse(parser);

        assertEquals("test query", parsed.getQuery());
    }

    @Test
    public void testSetters() {
        MLSearchMemoriesInput input = new MLSearchMemoriesInput(null, "initial query");

        input.setMemoryContainerId("new-container");
        input.setQuery("updated query");

        assertEquals("new-container", input.getMemoryContainerId());
        assertEquals("updated query", input.getQuery());
    }

    @Test
    public void testSpecialCharactersInQuery() throws IOException {
        MLSearchMemoriesInput specialInput = new MLSearchMemoriesInput(
            "container-special",
            "Query with\n\ttabs,\nnewlines, \"quotes\", 'single quotes', and unicode 🚀✨"
        );

        // Test serialization round trip
        BytesStreamOutput out = new BytesStreamOutput();
        specialInput.writeTo(out);
        StreamInput in = out.bytes().streamInput();
        MLSearchMemoriesInput deserialized = new MLSearchMemoriesInput(in);

        assertEquals(specialInput.getMemoryContainerId(), deserialized.getMemoryContainerId());
        assertEquals(specialInput.getQuery(), deserialized.getQuery());

        // Test XContent
        XContentBuilder builder = MediaTypeRegistry.contentBuilder(XContentType.JSON);
        specialInput.toXContent(builder, EMPTY_PARAMS);
        String jsonString = TestHelper.xContentBuilderToString(builder);

        assertTrue(jsonString.contains("Query with"));
        assertTrue(jsonString.contains("tabs"));
        assertTrue(jsonString.contains("quotes"));
    }

    @Test
    public void testLongQuery() throws IOException {
        // Test with a very long query
        StringBuilder longQuery = new StringBuilder();
        for (int i = 0; i < 1000; i++) {
            longQuery.append("word").append(i).append(" ");
        }

        MLSearchMemoriesInput longInput = new MLSearchMemoriesInput("container-1", longQuery.toString().trim());

        // Test serialization
        BytesStreamOutput out = new BytesStreamOutput();
        longInput.writeTo(out);
        StreamInput in = out.bytes().streamInput();
        MLSearchMemoriesInput deserialized = new MLSearchMemoriesInput(in);

        assertEquals(longInput.getQuery(), deserialized.getQuery());
    }

    @Test
    public void testXContentRoundTrip() throws IOException {
        // Convert to XContent
        XContentBuilder builder = MediaTypeRegistry.contentBuilder(XContentType.JSON);
        inputWithContainerId.toXContent(builder, EMPTY_PARAMS);
        String jsonString = TestHelper.xContentBuilderToString(builder);

        // Parse back
        XContentParser parser = XContentType.JSON
            .xContent()
            .createParser(NamedXContentRegistry.EMPTY, LoggingDeprecationHandler.INSTANCE, jsonString);
        parser.nextToken();
        MLSearchMemoriesInput parsed = MLSearchMemoriesInput.parse(parser);

        // Verify all fields match
        assertEquals(inputWithContainerId.getMemoryContainerId(), parsed.getMemoryContainerId());
        assertEquals(inputWithContainerId.getQuery(), parsed.getQuery());
    }

    @Test
    public void testComplexQueries() {
        // Test various complex query patterns
        String[] queries = {
            "machine learning AND deep learning",
            "\"exact phrase matching\"",
            "wildcard* search?",
            "field:value AND (nested OR query)",
            "fuzzy~2 search",
            "+required -excluded" };

        for (String query : queries) {
            MLSearchMemoriesInput input = new MLSearchMemoriesInput("container-1", query);
            assertEquals(query, input.getQuery());
        }
    }
}
