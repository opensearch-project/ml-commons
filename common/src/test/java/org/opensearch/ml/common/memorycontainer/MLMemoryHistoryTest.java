/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.memorycontainer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
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
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.CommonValue;
import org.opensearch.ml.common.TestHelper;
import org.opensearch.ml.common.transport.memorycontainer.memory.MemoryEvent;

public class MLMemoryHistoryTest {

    private MLMemoryHistory historyWithAllFields;
    private MLMemoryHistory historyWithNullFields;
    private Instant testCreatedTime;
    private Map<String, Object> testBefore;
    private Map<String, Object> testAfter;
    private Map<String, String> testNamespace;
    private Map<String, String> testTags;

    @Before
    public void setUp() {
        testCreatedTime = Instant.ofEpochMilli(1640995200000L);

        testBefore = new HashMap<>();
        testBefore.put("memory", "old fact");

        testAfter = new HashMap<>();
        testAfter.put("memory", "new fact");

        testNamespace = new HashMap<>();
        testNamespace.put("project", "ml-project");

        testTags = new HashMap<>();
        testTags.put("topic", "testing");

        historyWithAllFields = MLMemoryHistory
            .builder()
            .ownerId("owner-123")
            .memoryContainerId("container-123")
            .memoryId("memory-456")
            .action(MemoryEvent.UPDATE)
            .before(testBefore)
            .after(testAfter)
            .namespace(testNamespace)
            .tags(testTags)
            .createdTime(testCreatedTime)
            .tenantId("tenant-789")
            .error(null)
            .pinned(true)
            .build();

        historyWithNullFields = MLMemoryHistory
            .builder()
            .ownerId(null)
            .memoryContainerId(null)
            .memoryId(null)
            .action(null)
            .before(null)
            .after(null)
            .namespace(null)
            .tags(null)
            .createdTime(null)
            .tenantId(null)
            .error(null)
            .pinned(null)
            .build();
    }

    @Test
    public void testBuilderWithAllFields() {
        assertNotNull(historyWithAllFields);
        assertEquals("owner-123", historyWithAllFields.getOwnerId());
        assertEquals("container-123", historyWithAllFields.getMemoryContainerId());
        assertEquals("memory-456", historyWithAllFields.getMemoryId());
        assertEquals(MemoryEvent.UPDATE, historyWithAllFields.getAction());
        assertEquals(testBefore, historyWithAllFields.getBefore());
        assertEquals(testAfter, historyWithAllFields.getAfter());
        assertEquals(testNamespace, historyWithAllFields.getNamespace());
        assertEquals(testTags, historyWithAllFields.getTags());
        assertEquals(testCreatedTime, historyWithAllFields.getCreatedTime());
        assertEquals("tenant-789", historyWithAllFields.getTenantId());
        assertEquals(true, historyWithAllFields.getPinned());
    }

    @Test
    public void testBuilderWithNullFields() {
        assertNotNull(historyWithNullFields);
        assertNull(historyWithNullFields.getOwnerId());
        assertNull(historyWithNullFields.getMemoryContainerId());
        assertNull(historyWithNullFields.getMemoryId());
        assertNull(historyWithNullFields.getAction());
        assertNull(historyWithNullFields.getBefore());
        assertNull(historyWithNullFields.getAfter());
        assertNull(historyWithNullFields.getNamespace());
        assertNull(historyWithNullFields.getTags());
        assertNull(historyWithNullFields.getCreatedTime());
        assertNull(historyWithNullFields.getTenantId());
        assertNull(historyWithNullFields.getPinned());
    }

    @Test
    public void testStreamInputOutputWithAllFields() throws IOException {
        BytesStreamOutput out = new BytesStreamOutput();
        historyWithAllFields.writeTo(out);

        StreamInput in = out.bytes().streamInput();
        MLMemoryHistory deserialized = new MLMemoryHistory(in);

        assertEquals(historyWithAllFields.getOwnerId(), deserialized.getOwnerId());
        assertEquals(historyWithAllFields.getMemoryContainerId(), deserialized.getMemoryContainerId());
        assertEquals(historyWithAllFields.getMemoryId(), deserialized.getMemoryId());
        assertEquals(historyWithAllFields.getAction(), deserialized.getAction());
        assertEquals(historyWithAllFields.getBefore(), deserialized.getBefore());
        assertEquals(historyWithAllFields.getAfter(), deserialized.getAfter());
        assertEquals(historyWithAllFields.getCreatedTime(), deserialized.getCreatedTime());
        assertEquals(historyWithAllFields.getTenantId(), deserialized.getTenantId());
        assertEquals(historyWithAllFields.getPinned(), deserialized.getPinned());
    }

    @Test
    public void testStreamInputOutputWithNullFields() throws IOException {
        BytesStreamOutput out = new BytesStreamOutput();
        historyWithNullFields.writeTo(out);

        StreamInput in = out.bytes().streamInput();
        MLMemoryHistory deserialized = new MLMemoryHistory(in);

        assertNull(deserialized.getOwnerId());
        assertNull(deserialized.getMemoryContainerId());
        assertNull(deserialized.getMemoryId());
        assertNull(deserialized.getAction());
        assertNull(deserialized.getBefore());
        assertNull(deserialized.getAfter());
        assertNull(deserialized.getCreatedTime());
        assertNull(deserialized.getTenantId());
        assertNull(deserialized.getPinned());
    }

    @Test
    public void testPinnedFieldStreamRoundTrip() throws IOException {
        MLMemoryHistory pinnedHistory = MLMemoryHistory
            .builder()
            .ownerId("owner-123")
            .memoryId("memory-456")
            .action(MemoryEvent.ADD)
            .createdTime(testCreatedTime)
            .pinned(true)
            .build();

        BytesStreamOutput out = new BytesStreamOutput();
        pinnedHistory.writeTo(out);

        StreamInput in = out.bytes().streamInput();
        MLMemoryHistory deserialized = new MLMemoryHistory(in);

        assertEquals(true, deserialized.getPinned());
    }

    @Test
    public void testPinnedFieldStreamRoundTripNull() throws IOException {
        MLMemoryHistory history = MLMemoryHistory
            .builder()
            .ownerId("owner-123")
            .action(MemoryEvent.ADD)
            .createdTime(testCreatedTime)
            .pinned(null)
            .build();

        BytesStreamOutput out = new BytesStreamOutput();
        history.writeTo(out);

        StreamInput in = out.bytes().streamInput();
        MLMemoryHistory deserialized = new MLMemoryHistory(in);

        assertNull(deserialized.getPinned());
    }

    @Test
    public void testToXContentWithAllFields() throws IOException {
        XContentBuilder builder = XContentBuilder.builder(XContentType.JSON.xContent());
        historyWithAllFields.toXContent(builder, EMPTY_PARAMS);

        String jsonString = TestHelper.xContentBuilderToString(builder);
        assertNotNull(jsonString);

        assert jsonString.contains("\"owner_id\":\"owner-123\"");
        assert jsonString.contains("\"memory_container_id\":\"container-123\"");
        assert jsonString.contains("\"memory_id\":\"memory-456\"");
        assert jsonString.contains("\"pinned\":true");
    }

    @Test
    public void testToXContentWithNullFields() throws IOException {
        XContentBuilder builder = XContentBuilder.builder(XContentType.JSON.xContent());
        historyWithNullFields.toXContent(builder, EMPTY_PARAMS);

        String jsonString = TestHelper.xContentBuilderToString(builder);
        assertEquals("{}", jsonString);
    }

    @Test
    public void testParseWithAllFields() throws IOException {
        XContentBuilder builder = XContentBuilder.builder(XContentType.JSON.xContent());
        builder.startObject();
        builder.field("owner_id", "owner-123");
        builder.field("memory_container_id", "container-123");
        builder.field("memory_id", "memory-456");
        builder.field("action", "UPDATE");
        builder.field("before", testBefore);
        builder.field("after", testAfter);
        builder.field("namespace", testNamespace);
        builder.field("tags", testTags);
        builder.field("created_time", testCreatedTime.toEpochMilli());
        builder.field("tenant_id", "tenant-789");
        builder.field("pinned", true);
        builder.endObject();

        String jsonString = TestHelper.xContentBuilderToString(builder);
        XContentParser parser = XContentType.JSON
            .xContent()
            .createParser(NamedXContentRegistry.EMPTY, LoggingDeprecationHandler.INSTANCE, jsonString);
        parser.nextToken();

        MLMemoryHistory parsed = MLMemoryHistory.parse(parser);

        assertEquals("owner-123", parsed.getOwnerId());
        assertEquals("container-123", parsed.getMemoryContainerId());
        assertEquals("memory-456", parsed.getMemoryId());
        assertEquals(MemoryEvent.UPDATE, parsed.getAction());
        assertEquals(testBefore, parsed.getBefore());
        assertEquals(testAfter, parsed.getAfter());
        assertEquals(testNamespace, parsed.getNamespace());
        assertEquals(testTags, parsed.getTags());
        assertEquals(testCreatedTime, parsed.getCreatedTime());
        assertEquals("tenant-789", parsed.getTenantId());
        assertEquals(true, parsed.getPinned());
    }

    @Test
    public void testPinnedFieldXContentRoundTrip() throws IOException {
        MLMemoryHistory pinnedHistory = MLMemoryHistory
            .builder()
            .ownerId("owner-123")
            .memoryId("memory-456")
            .action(MemoryEvent.ADD)
            .createdTime(testCreatedTime)
            .pinned(true)
            .build();

        XContentBuilder builder = XContentBuilder.builder(XContentType.JSON.xContent());
        pinnedHistory.toXContent(builder, EMPTY_PARAMS);
        String jsonString = TestHelper.xContentBuilderToString(builder);

        assert jsonString.contains("\"pinned\":true");

        XContentParser parser = XContentType.JSON
            .xContent()
            .createParser(NamedXContentRegistry.EMPTY, LoggingDeprecationHandler.INSTANCE, jsonString);
        parser.nextToken();

        MLMemoryHistory parsed = MLMemoryHistory.parse(parser);
        assertEquals(true, parsed.getPinned());
    }

    @Test
    public void testPinnedFieldDefaultIsNull() throws IOException {
        XContentBuilder builder = XContentBuilder.builder(XContentType.JSON.xContent());
        builder.startObject();
        builder.field("owner_id", "owner-123");
        builder.field("action", "ADD");
        builder.field("created_time", testCreatedTime.toEpochMilli());
        builder.endObject();

        String jsonString = TestHelper.xContentBuilderToString(builder);
        XContentParser parser = XContentType.JSON
            .xContent()
            .createParser(NamedXContentRegistry.EMPTY, LoggingDeprecationHandler.INSTANCE, jsonString);
        parser.nextToken();

        MLMemoryHistory parsed = MLMemoryHistory.parse(parser);
        assertNull(parsed.getPinned());
    }

    @Test
    public void testPinnedFieldFalse() throws IOException {
        MLMemoryHistory unpinnedHistory = MLMemoryHistory
            .builder()
            .ownerId("owner-123")
            .action(MemoryEvent.ADD)
            .createdTime(testCreatedTime)
            .pinned(false)
            .build();

        XContentBuilder builder = XContentBuilder.builder(XContentType.JSON.xContent());
        unpinnedHistory.toXContent(builder, EMPTY_PARAMS);
        String jsonString = TestHelper.xContentBuilderToString(builder);

        assert jsonString.contains("\"pinned\":false");

        XContentParser parser = XContentType.JSON
            .xContent()
            .createParser(NamedXContentRegistry.EMPTY, LoggingDeprecationHandler.INSTANCE, jsonString);
        parser.nextToken();

        MLMemoryHistory parsed = MLMemoryHistory.parse(parser);
        assertEquals(false, parsed.getPinned());
    }

    @Test
    public void testParseWithUnknownFields() throws IOException {
        XContentBuilder builder = XContentBuilder.builder(XContentType.JSON.xContent());
        builder.startObject();
        builder.field("owner_id", "owner-123");
        builder.field("unknown_field", "unknown_value");
        builder.field("action", "ADD");
        builder.endObject();

        String jsonString = TestHelper.xContentBuilderToString(builder);
        XContentParser parser = XContentType.JSON
            .xContent()
            .createParser(NamedXContentRegistry.EMPTY, LoggingDeprecationHandler.INSTANCE, jsonString);
        parser.nextToken();

        MLMemoryHistory parsed = MLMemoryHistory.parse(parser);

        assertEquals("owner-123", parsed.getOwnerId());
        assertEquals(MemoryEvent.ADD, parsed.getAction());
        assertNull(parsed.getPinned());
    }

    @Test
    public void testBackwardCompatStreamFromOldNode() throws IOException {
        MLMemoryHistory history = MLMemoryHistory
            .builder()
            .ownerId("owner-123")
            .memoryId("memory-456")
            .action(MemoryEvent.UPDATE)
            .createdTime(testCreatedTime)
            .pinned(true)
            .build();

        BytesStreamOutput out = new BytesStreamOutput();
        out.setVersion(CommonValue.VERSION_3_5_0);
        history.writeTo(out);

        StreamInput in = out.bytes().streamInput();
        in.setVersion(CommonValue.VERSION_3_5_0);
        MLMemoryHistory deserialized = new MLMemoryHistory(in);

        assertEquals("owner-123", deserialized.getOwnerId());
        assertEquals("memory-456", deserialized.getMemoryId());
        assertEquals(MemoryEvent.UPDATE, deserialized.getAction());
        assertEquals(testCreatedTime, deserialized.getCreatedTime());
        assertNull(deserialized.getPinned());
    }
}
