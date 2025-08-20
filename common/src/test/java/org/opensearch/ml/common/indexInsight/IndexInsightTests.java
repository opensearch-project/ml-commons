/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.indexInsight;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.time.Instant;
import java.util.Collections;

import org.junit.Test;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.xcontent.XContentFactory;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.search.SearchModule;

public class IndexInsightTests {

    @Test
    public void testConstants() {
        assertEquals("index_name", IndexInsight.INDEX_NAME_FIELD);
        assertEquals("last_updated_time", IndexInsight.LAST_UPDATE_FIELD);
        assertEquals("content", IndexInsight.CONTENT_FIELD);
        assertEquals("status", IndexInsight.STATUS_FIELD);
        assertEquals("task_type", IndexInsight.TASK_TYPE_FIELD);
    }

    @Test
    public void testBuilder() {
        Instant now = Instant.now();
        IndexInsight insight = IndexInsight
            .builder()
            .index("test-index")
            .content("test content")
            .status(IndexInsightTaskStatus.COMPLETED)
            .taskType(MLIndexInsightType.STATISTICAL_DATA)
            .lastUpdatedTime(now)
            .build();

        assertEquals("test-index", insight.getIndex());
        assertEquals("test content", insight.getContent());
        assertEquals(IndexInsightTaskStatus.COMPLETED, insight.getStatus());
        assertEquals(MLIndexInsightType.STATISTICAL_DATA, insight.getTaskType());
        assertEquals(now, insight.getLastUpdatedTime());
    }

    @Test
    public void testBuilder_WithNullValues() {
        IndexInsight insight = IndexInsight.builder().index(null).content(null).status(null).taskType(null).lastUpdatedTime(null).build();

        assertNull(insight.getIndex());
        assertNull(insight.getContent());
        assertNull(insight.getStatus());
        assertNull(insight.getTaskType());
        assertNull(insight.getLastUpdatedTime());
    }

    @Test
    public void testStreamSerialization() throws IOException {
        Instant now = Instant.now();
        IndexInsight original = IndexInsight
            .builder()
            .index("test-index")
            .content("test content")
            .status(IndexInsightTaskStatus.GENERATING)
            .taskType(MLIndexInsightType.LOG_RELATED_INDEX_CHECK)
            .lastUpdatedTime(now)
            .build();

        BytesStreamOutput output = new BytesStreamOutput();
        original.writeTo(output);

        StreamInput input = output.bytes().streamInput();
        IndexInsight deserialized = new IndexInsight(input);

        assertEquals(original.getIndex(), deserialized.getIndex());
        assertEquals(original.getContent(), deserialized.getContent());
        assertEquals(original.getStatus(), deserialized.getStatus());
        assertEquals(original.getTaskType(), deserialized.getTaskType());
        assertEquals(original.getLastUpdatedTime(), deserialized.getLastUpdatedTime());
    }

    @Test
    public void testToXContent() throws IOException {
        Instant now = Instant.now();
        IndexInsight insight = IndexInsight
            .builder()
            .index("test-index")
            .content("test content")
            .status(IndexInsightTaskStatus.FAILED)
            .taskType(MLIndexInsightType.FIELD_DESCRIPTION)
            .lastUpdatedTime(now)
            .build();

        XContentBuilder builder = XContentFactory.jsonBuilder();
        insight.toXContent(builder, null);
        String json = builder.toString();

        assertTrue(json.contains("\"index_name\":\"test-index\""));
        assertTrue(json.contains("\"content\":\"test content\""));
        assertTrue(json.contains("\"status\":\"FAILED\""));
        assertTrue(json.contains("\"task_type\":\"FIELD_DESCRIPTION\""));
        assertTrue(json.contains("\"last_updated_time\":" + now.toEpochMilli()));
    }

    @Test
    public void testToXContentWithNullValues() throws IOException {
        Instant now = Instant.now();
        IndexInsight insight = IndexInsight
            .builder()
            .index(null)
            .content("")
            .status(null)
            .taskType(MLIndexInsightType.STATISTICAL_DATA)
            .lastUpdatedTime(now)
            .build();

        XContentBuilder builder = XContentFactory.jsonBuilder();
        insight.toXContent(builder, null);
        String json = builder.toString();

        assertFalse(json.contains("index_name"));
        assertFalse(json.contains("content"));
        assertFalse(json.contains("status"));
        assertTrue(json.contains("\"task_type\":\"STATISTICAL_DATA\""));
        assertTrue(json.contains("\"last_updated_time\":" + now.toEpochMilli()));
    }

    @Test
    public void testParseFromXContent() throws IOException {
        Instant now = Instant.ofEpochMilli(System.currentTimeMillis());
        String json =
            "{\"index_name\":\"test-index\",\"content\":\"test content\",\"status\":\"COMPLETED\",\"task_type\":\"LOG_RELATED_INDEX_CHECK\",\"last_updated_time\":"
                + now.toEpochMilli()
                + "}";

        XContentParser parser = XContentType.JSON
            .xContent()
            .createParser(
                new NamedXContentRegistry(new SearchModule(Settings.EMPTY, Collections.emptyList()).getNamedXContents()),
                null,
                json
            );
        parser.nextToken();
        IndexInsight insight = IndexInsight.parse(parser);

        assertEquals("test-index", insight.getIndex());
        assertEquals("test content", insight.getContent());
        assertEquals(IndexInsightTaskStatus.COMPLETED, insight.getStatus());
        assertEquals(MLIndexInsightType.LOG_RELATED_INDEX_CHECK, insight.getTaskType());
        assertEquals(now, insight.getLastUpdatedTime());
    }

    @Test
    public void testParseFromXContent_WithMissingFields() throws IOException {
        Instant now = Instant.ofEpochMilli(System.currentTimeMillis());
        String json = "{\"task_type\":\"STATISTICAL_DATA\",\"last_updated_time\":" + now.toEpochMilli() + "}";

        XContentParser parser = XContentType.JSON
            .xContent()
            .createParser(
                new NamedXContentRegistry(new SearchModule(Settings.EMPTY, Collections.emptyList()).getNamedXContents()),
                null,
                json
            );
        parser.nextToken();
        IndexInsight insight = IndexInsight.parse(parser);

        assertNull(insight.getIndex());
        assertNull(insight.getContent());
        assertNull(insight.getStatus());
        assertEquals(MLIndexInsightType.STATISTICAL_DATA, insight.getTaskType());
        assertEquals(now, insight.getLastUpdatedTime());
    }

    @Test
    public void testEqualsAndHashCode() {
        Instant now = Instant.now();
        IndexInsight insight1 = IndexInsight
            .builder()
            .index("test-index")
            .content("test content")
            .status(IndexInsightTaskStatus.COMPLETED)
            .taskType(MLIndexInsightType.STATISTICAL_DATA)
            .lastUpdatedTime(now)
            .build();

        IndexInsight insight2 = IndexInsight
            .builder()
            .index("test-index")
            .content("test content")
            .status(IndexInsightTaskStatus.COMPLETED)
            .taskType(MLIndexInsightType.STATISTICAL_DATA)
            .lastUpdatedTime(now)
            .build();

        assertEquals(insight1, insight2);
        assertEquals(insight1.hashCode(), insight2.hashCode());
    }
}
