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
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.MEM_CONTAINER_MEMORY_TYPE_SESSIONS;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.common.io.stream.NamedWriteableAwareStreamInput;
import org.opensearch.core.common.io.stream.NamedWriteableRegistry;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.xcontent.MediaTypeRegistry;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.index.query.QueryBuilders;
import org.opensearch.indices.IndicesModule;
import org.opensearch.ml.common.TestHelper;
import org.opensearch.search.SearchModule;
import org.opensearch.search.builder.SearchSourceBuilder;

public class MLSearchMemoriesInputTest {

    private MLSearchMemoriesInput inputWithContainerId;
    private MLSearchMemoriesInput inputWithoutContainerId;
    private NamedWriteableRegistry namedWriteableRegistry;

    @Before
    public void setUp() {
        IndicesModule indicesModule = new IndicesModule(Collections.emptyList());
        SearchModule searchModule = new SearchModule(Settings.EMPTY, Collections.emptyList());
        List<NamedWriteableRegistry.Entry> entries = new ArrayList<>();
        entries.addAll(indicesModule.getNamedWriteables());
        entries.addAll(searchModule.getNamedWriteables());
        namedWriteableRegistry = new NamedWriteableRegistry(entries);
        SearchSourceBuilder searchSourceBuilder1 = new SearchSourceBuilder()
            .query(QueryBuilders.matchQuery("memory", "machine learning concepts"));

        inputWithContainerId = MLSearchMemoriesInput
            .builder()
            .memoryContainerId("container-123")
            .memoryType("long-term")
            .searchSourceBuilder(searchSourceBuilder1)
            .build();

        SearchSourceBuilder searchSourceBuilder2 = new SearchSourceBuilder()
            .query(QueryBuilders.matchQuery("memory", "search without container id"));

        inputWithoutContainerId = MLSearchMemoriesInput.builder().memoryType("working").searchSourceBuilder(searchSourceBuilder2).build();
    }

    @Test
    public void testBuilderWithContainerId() {
        assertNotNull(inputWithContainerId);
        assertEquals("container-123", inputWithContainerId.getMemoryContainerId());
        assertEquals("long-term", inputWithContainerId.getMemoryType());
        assertNotNull(inputWithContainerId.getSearchSourceBuilder());
    }

    @Test
    public void testBuilderWithoutContainerId() {
        assertNotNull(inputWithoutContainerId);
        assertNull(inputWithoutContainerId.getMemoryContainerId());
        assertEquals("working", inputWithoutContainerId.getMemoryType());
        assertNotNull(inputWithoutContainerId.getSearchSourceBuilder());
    }

    @Test
    public void testConstructorWithContainerId() {
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder().query(QueryBuilders.matchQuery("memory", "test query"));
        MLSearchMemoriesInput input = new MLSearchMemoriesInput("container-456", MEM_CONTAINER_MEMORY_TYPE_SESSIONS, searchSourceBuilder);
        assertEquals("container-456", input.getMemoryContainerId());
        assertEquals(MEM_CONTAINER_MEMORY_TYPE_SESSIONS, input.getMemoryType());
        assertNotNull(input.getSearchSourceBuilder());
    }

    @Test
    public void testConstructorWithoutContainerId() {
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder().query(QueryBuilders.matchQuery("memory", "another query"));
        MLSearchMemoriesInput input = new MLSearchMemoriesInput(null, "history", searchSourceBuilder);
        assertNull(input.getMemoryContainerId());
        assertEquals("history", input.getMemoryType());
        assertNotNull(input.getSearchSourceBuilder());
    }

    @Test
    public void testConstructorWithNullSearchSourceBuilder() {
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> new MLSearchMemoriesInput("container-1", "long-term", null)
        );
        assertEquals("Query cannot be null", exception.getMessage());
    }

    @Test
    public void testValidSearchSourceBuilder() {
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder().query(QueryBuilders.matchQuery("memory", "query with spaces"));
        MLSearchMemoriesInput input = new MLSearchMemoriesInput("container-1", "long-term", searchSourceBuilder);
        assertEquals("container-1", input.getMemoryContainerId());
        assertEquals("long-term", input.getMemoryType());
        assertNotNull(input.getSearchSourceBuilder());
    }

    @Test
    public void testStreamInputOutput() throws IOException {
        // Test with container ID
        BytesStreamOutput out = new BytesStreamOutput();
        inputWithContainerId.writeTo(out);
        StreamInput in = new NamedWriteableAwareStreamInput(out.bytes().streamInput(), namedWriteableRegistry);
        MLSearchMemoriesInput deserialized = new MLSearchMemoriesInput(in);

        assertEquals(inputWithContainerId.getMemoryContainerId(), deserialized.getMemoryContainerId());
        assertEquals(inputWithContainerId.getMemoryType(), deserialized.getMemoryType());
        assertNotNull(deserialized.getSearchSourceBuilder());
    }

    @Test
    public void testStreamInputOutputWithoutContainerId() throws IOException {
        // Test without container ID
        BytesStreamOutput out = new BytesStreamOutput();
        inputWithoutContainerId.writeTo(out);
        StreamInput in = new NamedWriteableAwareStreamInput(out.bytes().streamInput(), namedWriteableRegistry);
        MLSearchMemoriesInput deserialized = new MLSearchMemoriesInput(in);

        assertNull(deserialized.getMemoryContainerId());
        assertEquals(inputWithoutContainerId.getMemoryType(), deserialized.getMemoryType());
        assertNotNull(deserialized.getSearchSourceBuilder());
    }

    @Test
    public void testToXContent() throws IOException {
        XContentBuilder builder = MediaTypeRegistry.contentBuilder(XContentType.JSON);
        inputWithContainerId.toXContent(builder, EMPTY_PARAMS);
        String jsonString = TestHelper.xContentBuilderToString(builder);

        assertTrue(jsonString.contains("\"memory_container_id\":\"container-123\""));
        assertTrue(jsonString.contains("\"memory_type\":\"long-term\""));
        assertTrue(jsonString.contains("\"query\""));
    }

    @Test
    public void testToXContentWithoutContainerId() throws IOException {
        XContentBuilder builder = MediaTypeRegistry.contentBuilder(XContentType.JSON);
        inputWithoutContainerId.toXContent(builder, EMPTY_PARAMS);
        String jsonString = TestHelper.xContentBuilderToString(builder);

        assertTrue(!jsonString.contains("\"memory_container_id\""));
        assertTrue(jsonString.contains("\"memory_type\":\"working\""));
        assertTrue(jsonString.contains("\"query\""));
    }

    @Test
    public void testToXContentContainsAllFields() throws IOException {
        XContentBuilder builder = MediaTypeRegistry.contentBuilder(XContentType.JSON);
        inputWithContainerId.toXContent(builder, EMPTY_PARAMS);
        String jsonString = TestHelper.xContentBuilderToString(builder);

        assertTrue(jsonString.contains("\"memory_container_id\":\"container-123\""));
        assertTrue(jsonString.contains("\"memory_type\":\"long-term\""));
        assertTrue(jsonString.contains("\"query\""));
    }

    @Test
    public void testSetters() {
        SearchSourceBuilder initialSearchSourceBuilder = new SearchSourceBuilder()
            .query(QueryBuilders.matchQuery("memory", "initial query"));
        MLSearchMemoriesInput input = new MLSearchMemoriesInput(null, "working", initialSearchSourceBuilder);

        input.setMemoryContainerId("new-container");
        input.setMemoryType(MEM_CONTAINER_MEMORY_TYPE_SESSIONS);

        SearchSourceBuilder newSearchSourceBuilder = new SearchSourceBuilder().query(QueryBuilders.matchQuery("memory", "updated query"));
        input.setSearchSourceBuilder(newSearchSourceBuilder);

        assertEquals("new-container", input.getMemoryContainerId());
        assertEquals(MEM_CONTAINER_MEMORY_TYPE_SESSIONS, input.getMemoryType());
        assertNotNull(input.getSearchSourceBuilder());
    }

    @Test
    public void testSpecialCharactersInQuery() throws IOException {
        SearchSourceBuilder specialSearchSourceBuilder = new SearchSourceBuilder()
            .query(QueryBuilders.matchQuery("memory", "Query with\n\ttabs,\nnewlines, \"quotes\", 'single quotes', and unicode 🚀✨"));

        MLSearchMemoriesInput specialInput = new MLSearchMemoriesInput("container-special", "long-term", specialSearchSourceBuilder);

        // Test serialization round trip
        BytesStreamOutput out = new BytesStreamOutput();
        specialInput.writeTo(out);
        StreamInput in = new NamedWriteableAwareStreamInput(out.bytes().streamInput(), namedWriteableRegistry);
        MLSearchMemoriesInput deserialized = new MLSearchMemoriesInput(in);

        assertEquals(specialInput.getMemoryContainerId(), deserialized.getMemoryContainerId());
        assertEquals(specialInput.getMemoryType(), deserialized.getMemoryType());
        assertNotNull(deserialized.getSearchSourceBuilder());

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

        SearchSourceBuilder longSearchSourceBuilder = new SearchSourceBuilder()
            .query(QueryBuilders.matchQuery("memory", longQuery.toString().trim()));

        MLSearchMemoriesInput longInput = new MLSearchMemoriesInput("container-1", "history", longSearchSourceBuilder);

        // Test serialization
        BytesStreamOutput out = new BytesStreamOutput();
        longInput.writeTo(out);
        StreamInput in = new NamedWriteableAwareStreamInput(out.bytes().streamInput(), namedWriteableRegistry);
        MLSearchMemoriesInput deserialized = new MLSearchMemoriesInput(in);

        assertEquals(longInput.getMemoryContainerId(), deserialized.getMemoryContainerId());
        assertEquals(longInput.getMemoryType(), deserialized.getMemoryType());
        assertNotNull(deserialized.getSearchSourceBuilder());
    }

    @Test
    public void testXContentGeneration() throws IOException {
        // Convert to XContent
        XContentBuilder builder = MediaTypeRegistry.contentBuilder(XContentType.JSON);
        inputWithContainerId.toXContent(builder, EMPTY_PARAMS);
        String jsonString = TestHelper.xContentBuilderToString(builder);

        // Verify JSON contains expected fields
        assertTrue(jsonString.contains("\"memory_container_id\":\"container-123\""));
        assertTrue(jsonString.contains("\"memory_type\":\"long-term\""));
        assertTrue(jsonString.contains("\"query\""));
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

        for (int i = 0; i < queries.length; i++) {
            SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder().query(QueryBuilders.queryStringQuery(queries[i]));
            MLSearchMemoriesInput input = new MLSearchMemoriesInput("container-" + i, "long-term", searchSourceBuilder);
            assertEquals("container-" + i, input.getMemoryContainerId());
            assertEquals("long-term", input.getMemoryType());
            assertNotNull(input.getSearchSourceBuilder());
        }
    }
}
