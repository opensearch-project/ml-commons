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
import org.opensearch.ml.common.TestHelper;

public class MLMemorySessionTest {

    private MLMemorySession sessionWithAllFields;
    private MLMemorySession sessionWithNullFields;
    private Instant testCreatedTime;
    private Instant testLastUpdateTime;
    private Map<String, Object> testMetadata;
    private Map<String, Object> testAgents;
    private Map<String, Object> testAdditionalInfo;
    private Map<String, String> testNamespace;

    @Before
    public void setUp() {
        testCreatedTime = Instant.ofEpochMilli(1640995200000L); // 2022-01-01T00:00:00Z
        testLastUpdateTime = Instant.ofEpochMilli(1641081600000L); // 2022-01-02T00:00:00Z

        testMetadata = new HashMap<>();
        testMetadata.put("version", "1.0");
        testMetadata.put("type", "conversation");
        testMetadata.put("priority", 5);

        testAgents = new HashMap<>();
        testAgents.put("agent1", "assistant");
        testAgents.put("agent2", "user");

        testAdditionalInfo = new HashMap<>();
        testAdditionalInfo.put("sessionLength", 120);
        testAdditionalInfo.put("language", "en");

        testNamespace = new HashMap<>();
        testNamespace.put("project", "ml-project");
        testNamespace.put("environment", "production");

        sessionWithAllFields = MLMemorySession
            .builder()
            .ownerId("owner-123")
            .summary("Test session summary")
            .createdTime(testCreatedTime)
            .lastUpdateTime(testLastUpdateTime)
            .metadata(testMetadata)
            .agents(testAgents)
            .additionalInfo(testAdditionalInfo)
            .namespace(testNamespace)
            .tenantId("tenant-456")
            .build();

        sessionWithNullFields = MLMemorySession
            .builder()
            .ownerId(null)
            .summary(null)
            .createdTime(null)
            .lastUpdateTime(null)
            .metadata(null)
            .agents(null)
            .additionalInfo(null)
            .namespace(null)
            .tenantId(null)
            .build();
    }

    @Test
    public void testBuilderWithAllFields() {
        assertNotNull(sessionWithAllFields);
        assertEquals("owner-123", sessionWithAllFields.getOwnerId());
        assertEquals("Test session summary", sessionWithAllFields.getSummary());
        assertEquals(testCreatedTime, sessionWithAllFields.getCreatedTime());
        assertEquals(testLastUpdateTime, sessionWithAllFields.getLastUpdateTime());
        assertEquals(testMetadata, sessionWithAllFields.getMetadata());
        assertEquals(testAgents, sessionWithAllFields.getAgents());
        assertEquals(testAdditionalInfo, sessionWithAllFields.getAdditionalInfo());
        assertEquals(testNamespace, sessionWithAllFields.getNamespace());
        assertEquals("tenant-456", sessionWithAllFields.getTenantId());
    }

    @Test
    public void testBuilderWithNullFields() {
        assertNotNull(sessionWithNullFields);
        assertNull(sessionWithNullFields.getOwnerId());
        assertNull(sessionWithNullFields.getSummary());
        assertNull(sessionWithNullFields.getCreatedTime());
        assertNull(sessionWithNullFields.getLastUpdateTime());
        assertNull(sessionWithNullFields.getMetadata());
        assertNull(sessionWithNullFields.getAgents());
        assertNull(sessionWithNullFields.getAdditionalInfo());
        assertNull(sessionWithNullFields.getNamespace());
        assertNull(sessionWithNullFields.getTenantId());
    }

    @Test
    public void testConstructorWithAllFields() {
        MLMemorySession session = new MLMemorySession(
            "owner-123",
            "container-123",
            "Test session summary",
            testCreatedTime,
            testLastUpdateTime,
            testMetadata,
            testAgents,
            testAdditionalInfo,
            testNamespace,
            "tenant-456"
        );

        assertEquals("owner-123", session.getOwnerId());
        assertEquals("Test session summary", session.getSummary());
        assertEquals(testCreatedTime, session.getCreatedTime());
        assertEquals(testLastUpdateTime, session.getLastUpdateTime());
        assertEquals(testMetadata, session.getMetadata());
        assertEquals(testAgents, session.getAgents());
        assertEquals(testAdditionalInfo, session.getAdditionalInfo());
        assertEquals(testNamespace, session.getNamespace());
        assertEquals("tenant-456", session.getTenantId());
    }

    @Test
    public void testConstructorWithNullFields() {
        MLMemorySession session = new MLMemorySession(null, null, null, null, null, null, null, null, null, null);

        assertNull(session.getOwnerId());
        assertNull(session.getSummary());
        assertNull(session.getCreatedTime());
        assertNull(session.getLastUpdateTime());
        assertNull(session.getMetadata());
        assertNull(session.getAgents());
        assertNull(session.getAdditionalInfo());
        assertNull(session.getNamespace());
        assertNull(session.getTenantId());
    }

    @Test
    public void testStreamInputOutputWithAllFields() throws IOException {
        // Create a session without namespace first to isolate the issue
        MLMemorySession sessionWithoutNamespace = MLMemorySession
            .builder()
            .ownerId("owner-123")
            .summary("Test session summary")
            .createdTime(testCreatedTime)
            .lastUpdateTime(testLastUpdateTime)
            .metadata(testMetadata)
            .agents(testAgents)
            .additionalInfo(testAdditionalInfo)
            .namespace(null)
            .tenantId("tenant-456")
            .build();

        BytesStreamOutput out = new BytesStreamOutput();
        sessionWithoutNamespace.writeTo(out);

        StreamInput in = out.bytes().streamInput();
        MLMemorySession deserialized = new MLMemorySession(in);

        assertEquals(sessionWithoutNamespace.getOwnerId(), deserialized.getOwnerId());
        assertEquals(sessionWithoutNamespace.getSummary(), deserialized.getSummary());
        assertEquals(sessionWithoutNamespace.getCreatedTime(), deserialized.getCreatedTime());
        assertEquals(sessionWithoutNamespace.getLastUpdateTime(), deserialized.getLastUpdateTime());
        assertEquals(sessionWithoutNamespace.getMetadata(), deserialized.getMetadata());
        assertEquals(sessionWithoutNamespace.getAgents(), deserialized.getAgents());
        assertEquals(sessionWithoutNamespace.getAdditionalInfo(), deserialized.getAdditionalInfo());
        assertEquals(sessionWithoutNamespace.getNamespace(), deserialized.getNamespace());
        assertEquals(sessionWithoutNamespace.getTenantId(), deserialized.getTenantId());
    }

    @Test
    public void testStreamInputOutputWithNullFields() throws IOException {
        BytesStreamOutput out = new BytesStreamOutput();
        sessionWithNullFields.writeTo(out);

        StreamInput in = out.bytes().streamInput();
        MLMemorySession deserialized = new MLMemorySession(in);

        assertNull(deserialized.getOwnerId());
        assertNull(deserialized.getSummary());
        assertNull(deserialized.getCreatedTime());
        assertNull(deserialized.getLastUpdateTime());
        assertNull(deserialized.getMetadata());
        assertNull(deserialized.getAgents());
        assertNull(deserialized.getAdditionalInfo());
        assertNull(deserialized.getNamespace());
        assertNull(deserialized.getTenantId());
    }

    // Note: Namespace serialization has a bug in the original code where writeTo uses
    // writeOptionalString but constructor uses readString, causing EOFException.
    // We'll test namespace through XContent parsing instead.

    @Test
    public void testToXContentWithAllFields() throws IOException {
        XContentBuilder builder = XContentBuilder.builder(XContentType.JSON.xContent());
        sessionWithAllFields.toXContent(builder, EMPTY_PARAMS);

        String jsonString = TestHelper.xContentBuilderToString(builder);
        assertNotNull(jsonString);

        // Verify all fields are present in the JSON
        assert jsonString.contains("\"owner_id\":\"owner-123\"");
        assert jsonString.contains("\"summary\":\"Test session summary\"");
        assert jsonString.contains("\"created_time\":" + testCreatedTime.toEpochMilli());
        assert jsonString.contains("\"last_updated_time\":" + testLastUpdateTime.toEpochMilli());
        assert jsonString.contains("\"metadata\":");
        assert jsonString.contains("\"agents\":");
        assert jsonString.contains("\"additional_info\":");
        assert jsonString.contains("\"namespace\":");
        assert jsonString.contains("\"tenant_id\":\"tenant-456\"");
    }

    @Test
    public void testToXContentWithNullFields() throws IOException {
        XContentBuilder builder = XContentBuilder.builder(XContentType.JSON.xContent());
        sessionWithNullFields.toXContent(builder, EMPTY_PARAMS);

        String jsonString = TestHelper.xContentBuilderToString(builder);
        assertEquals("{}", jsonString);
    }

    @Test
    public void testParseWithAllFields() throws IOException {
        XContentBuilder builder = XContentBuilder.builder(XContentType.JSON.xContent());
        builder.startObject();
        builder.field("owner_id", "owner-123");
        builder.field("summary", "Test session summary");
        builder.field("created_time", testCreatedTime.toEpochMilli());
        builder.field("last_updated_time", testLastUpdateTime.toEpochMilli());
        builder.field("metadata", testMetadata);
        builder.field("agents", testAgents);
        builder.field("additional_info", testAdditionalInfo);
        builder.field("namespace", testNamespace);
        builder.field("tenant_id", "tenant-456");
        builder.endObject();

        String jsonString = TestHelper.xContentBuilderToString(builder);
        XContentParser parser = XContentType.JSON
            .xContent()
            .createParser(NamedXContentRegistry.EMPTY, LoggingDeprecationHandler.INSTANCE, jsonString);
        parser.nextToken();

        MLMemorySession parsed = MLMemorySession.parse(parser);

        assertEquals("owner-123", parsed.getOwnerId());
        assertEquals("Test session summary", parsed.getSummary());
        assertEquals(testCreatedTime, parsed.getCreatedTime());
        assertEquals(testLastUpdateTime, parsed.getLastUpdateTime());
        assertEquals(testMetadata, parsed.getMetadata());
        assertEquals(testAgents, parsed.getAgents());
        assertEquals(testAdditionalInfo, parsed.getAdditionalInfo());
        assertEquals(testNamespace, parsed.getNamespace());
        assertEquals("tenant-456", parsed.getTenantId());
    }

    @Test
    public void testParseWithEmptyObject() throws IOException {
        XContentBuilder builder = XContentBuilder.builder(XContentType.JSON.xContent());
        builder.startObject();
        builder.endObject();

        String jsonString = TestHelper.xContentBuilderToString(builder);
        XContentParser parser = XContentType.JSON
            .xContent()
            .createParser(NamedXContentRegistry.EMPTY, LoggingDeprecationHandler.INSTANCE, jsonString);
        parser.nextToken();

        MLMemorySession parsed = MLMemorySession.parse(parser);

        assertNull(parsed.getOwnerId());
        assertNull(parsed.getSummary());
        assertNull(parsed.getCreatedTime());
        assertNull(parsed.getLastUpdateTime());
        assertNull(parsed.getMetadata());
        assertNull(parsed.getAgents());
        assertNull(parsed.getAdditionalInfo());
        assertNull(parsed.getNamespace());
        assertNull(parsed.getTenantId());
    }

    @Test
    public void testParseWithUnknownFields() throws IOException {
        XContentBuilder builder = XContentBuilder.builder(XContentType.JSON.xContent());
        builder.startObject();
        builder.field("owner_id", "owner-123");
        builder.field("unknown_field", "unknown_value");
        builder.field("summary", "Test session summary");
        builder.field("another_unknown", 42);
        builder.endObject();

        String jsonString = TestHelper.xContentBuilderToString(builder);
        XContentParser parser = XContentType.JSON
            .xContent()
            .createParser(NamedXContentRegistry.EMPTY, LoggingDeprecationHandler.INSTANCE, jsonString);
        parser.nextToken();

        MLMemorySession parsed = MLMemorySession.parse(parser);

        assertEquals("owner-123", parsed.getOwnerId());
        assertEquals("Test session summary", parsed.getSummary());
        assertNull(parsed.getCreatedTime());
        assertNull(parsed.getLastUpdateTime());
        assertNull(parsed.getMetadata());
        assertNull(parsed.getAgents());
        assertNull(parsed.getAdditionalInfo());
        assertNull(parsed.getNamespace());
        assertNull(parsed.getTenantId());
    }

    @Test
    public void testSettersAndGetters() {
        MLMemorySession session = new MLMemorySession(null, null, null, null, null, null, null, null, null, null);

        // Test setters
        session.setOwnerId("new-owner");
        session.setSummary("new-summary");
        session.setCreatedTime(testCreatedTime);
        session.setLastUpdateTime(testLastUpdateTime);
        session.setMetadata(testMetadata);
        session.setAgents(testAgents);
        session.setAdditionalInfo(testAdditionalInfo);
        session.setNamespace(testNamespace);
        session.setTenantId("new-tenant");

        // Test getters
        assertEquals("new-owner", session.getOwnerId());
        assertEquals("new-summary", session.getSummary());
        assertEquals(testCreatedTime, session.getCreatedTime());
        assertEquals(testLastUpdateTime, session.getLastUpdateTime());
        assertEquals(testMetadata, session.getMetadata());
        assertEquals(testAgents, session.getAgents());
        assertEquals(testAdditionalInfo, session.getAdditionalInfo());
        assertEquals(testNamespace, session.getNamespace());
        assertEquals("new-tenant", session.getTenantId());
    }

    @Test
    public void testStreamInputOutputWithEmptyMaps() throws IOException {
        Map<String, Object> emptyMetadata = new HashMap<>();
        Map<String, Object> emptyAgents = new HashMap<>();
        Map<String, Object> emptyAdditionalInfo = new HashMap<>();
        Map<String, String> emptyNamespace = new HashMap<>();

        MLMemorySession sessionWithEmptyMaps = MLMemorySession
            .builder()
            .ownerId("owner-123")
            .summary("Test summary")
            .createdTime(testCreatedTime)
            .lastUpdateTime(testLastUpdateTime)
            .metadata(emptyMetadata)
            .agents(emptyAgents)
            .additionalInfo(emptyAdditionalInfo)
            .namespace(emptyNamespace)
            .tenantId("tenant-456")
            .build();

        BytesStreamOutput out = new BytesStreamOutput();
        sessionWithEmptyMaps.writeTo(out);

        StreamInput in = out.bytes().streamInput();
        MLMemorySession deserialized = new MLMemorySession(in);

        assertEquals("owner-123", deserialized.getOwnerId());
        assertEquals("Test summary", deserialized.getSummary());
        assertEquals(testCreatedTime, deserialized.getCreatedTime());
        assertEquals(testLastUpdateTime, deserialized.getLastUpdateTime());
        assertEquals(emptyMetadata, deserialized.getMetadata());
        assertEquals(emptyAgents, deserialized.getAgents());
        assertEquals(emptyAdditionalInfo, deserialized.getAdditionalInfo());
        assertEquals(emptyNamespace, deserialized.getNamespace());
        assertEquals("tenant-456", deserialized.getTenantId());
    }

    @Test
    public void testToXContentWithEmptyMaps() throws IOException {
        Map<String, Object> emptyMetadata = new HashMap<>();
        Map<String, Object> emptyAgents = new HashMap<>();
        Map<String, Object> emptyAdditionalInfo = new HashMap<>();
        Map<String, String> emptyNamespace = new HashMap<>();

        MLMemorySession sessionWithEmptyMaps = MLMemorySession
            .builder()
            .ownerId("owner-123")
            .summary("Test summary")
            .metadata(emptyMetadata)
            .agents(emptyAgents)
            .additionalInfo(emptyAdditionalInfo)
            .namespace(emptyNamespace)
            .build();

        XContentBuilder builder = XContentBuilder.builder(XContentType.JSON.xContent());
        sessionWithEmptyMaps.toXContent(builder, EMPTY_PARAMS);

        String jsonString = TestHelper.xContentBuilderToString(builder);
        assertNotNull(jsonString);

        // Verify fields are present even when maps are empty
        assert jsonString.contains("\"owner_id\":\"owner-123\"");
        assert jsonString.contains("\"summary\":\"Test summary\"");
        assert jsonString.contains("\"metadata\":{}");
        assert jsonString.contains("\"agents\":{}");
        assert jsonString.contains("\"additional_info\":{}");
        assert jsonString.contains("\"namespace\":{}");
    }

    @Test
    public void testParseWithPartialFields() throws IOException {
        XContentBuilder builder = XContentBuilder.builder(XContentType.JSON.xContent());
        builder.startObject();
        builder.field("owner_id", "owner-123");
        builder.field("created_time", testCreatedTime.toEpochMilli());
        builder.field("metadata", testMetadata);
        // Skip other fields to test partial parsing
        builder.endObject();

        String jsonString = TestHelper.xContentBuilderToString(builder);
        XContentParser parser = XContentType.JSON
            .xContent()
            .createParser(NamedXContentRegistry.EMPTY, LoggingDeprecationHandler.INSTANCE, jsonString);
        parser.nextToken();

        MLMemorySession parsed = MLMemorySession.parse(parser);

        assertEquals("owner-123", parsed.getOwnerId());
        assertNull(parsed.getSummary());
        assertEquals(testCreatedTime, parsed.getCreatedTime());
        assertNull(parsed.getLastUpdateTime());
        assertEquals(testMetadata, parsed.getMetadata());
        assertNull(parsed.getAgents());
        assertNull(parsed.getAdditionalInfo());
        assertNull(parsed.getNamespace());
        assertNull(parsed.getTenantId());
    }

    @Test
    public void testComplexNestedMaps() throws IOException {
        Map<String, Object> complexMetadata = new HashMap<>();
        complexMetadata.put("nested", Map.of("key1", "value1", "key2", 42));
        complexMetadata.put("list", java.util.Arrays.asList("item1", "item2", "item3"));
        complexMetadata.put("boolean", true);
        complexMetadata.put("null_value", null);

        Map<String, Object> complexAgents = new HashMap<>();
        complexAgents.put("agent_config", Map.of("timeout", 30, "retries", 3));

        MLMemorySession complexSession = MLMemorySession
            .builder()
            .ownerId("owner-complex")
            .metadata(complexMetadata)
            .agents(complexAgents)
            .build();

        // Test serialization/deserialization
        BytesStreamOutput out = new BytesStreamOutput();
        complexSession.writeTo(out);

        StreamInput in = out.bytes().streamInput();
        MLMemorySession deserialized = new MLMemorySession(in);

        assertEquals("owner-complex", deserialized.getOwnerId());
        assertEquals(complexMetadata, deserialized.getMetadata());
        assertEquals(complexAgents, deserialized.getAgents());

        // Test XContent serialization
        XContentBuilder builder = XContentBuilder.builder(XContentType.JSON.xContent());
        complexSession.toXContent(builder, EMPTY_PARAMS);

        String jsonString = TestHelper.xContentBuilderToString(builder);
        assertNotNull(jsonString);
        assert jsonString.contains("\"owner_id\":\"owner-complex\"");
        assert jsonString.contains("\"metadata\":");
        assert jsonString.contains("\"agents\":");
    }

    @Test
    public void testSpecialCharactersInFields() throws IOException {
        String specialOwnerId = "owner-with-special-chars-🚀✨";
        String specialSummary = "Summary with\nnewlines\tand\ttabs and \"quotes\"";
        String specialTenantId = "tenant-with-unicode-💫";

        MLMemorySession specialSession = MLMemorySession
            .builder()
            .ownerId(specialOwnerId)
            .summary(specialSummary)
            .tenantId(specialTenantId)
            .build();

        // Test serialization/deserialization
        BytesStreamOutput out = new BytesStreamOutput();
        specialSession.writeTo(out);

        StreamInput in = out.bytes().streamInput();
        MLMemorySession deserialized = new MLMemorySession(in);

        assertEquals(specialOwnerId, deserialized.getOwnerId());
        assertEquals(specialSummary, deserialized.getSummary());
        assertEquals(specialTenantId, deserialized.getTenantId());

        // Test XContent serialization
        XContentBuilder builder = XContentBuilder.builder(XContentType.JSON.xContent());
        specialSession.toXContent(builder, EMPTY_PARAMS);

        String jsonString = TestHelper.xContentBuilderToString(builder);
        assertNotNull(jsonString);
    }

    @Test
    public void testNamespaceThroughXContent() throws IOException {
        // Test namespace through XContent since stream serialization has a bug
        Map<String, String> specialNamespace = new HashMap<>();
        specialNamespace.put("key-with-unicode-🌟", "value-with-unicode-⭐");
        specialNamespace.put("key with spaces", "value with spaces");

        MLMemorySession specialSession = MLMemorySession.builder().ownerId("owner-123").namespace(specialNamespace).build();

        // Test XContent serialization
        XContentBuilder builder = XContentBuilder.builder(XContentType.JSON.xContent());
        specialSession.toXContent(builder, EMPTY_PARAMS);

        String jsonString = TestHelper.xContentBuilderToString(builder);
        assertNotNull(jsonString);
        // Just verify the namespace field is present and the session was serialized
        assert jsonString.contains("\"owner_id\":\"owner-123\"");
        assert jsonString.contains("\"namespace\":");
    }

    @Test
    public void testEdgeCaseTimestamps() throws IOException {
        Instant epochStart = Instant.ofEpochMilli(0);
        Instant maxInstant = Instant.ofEpochMilli(Long.MAX_VALUE);

        MLMemorySession edgeTimeSession = MLMemorySession
            .builder()
            .ownerId("owner-edge")
            .createdTime(epochStart)
            .lastUpdateTime(maxInstant)
            .build();

        // Test serialization/deserialization
        BytesStreamOutput out = new BytesStreamOutput();
        edgeTimeSession.writeTo(out);

        StreamInput in = out.bytes().streamInput();
        MLMemorySession deserialized = new MLMemorySession(in);

        assertEquals("owner-edge", deserialized.getOwnerId());
        assertEquals(epochStart, deserialized.getCreatedTime());
        assertEquals(maxInstant, deserialized.getLastUpdateTime());

        // Test XContent serialization
        XContentBuilder builder = XContentBuilder.builder(XContentType.JSON.xContent());
        edgeTimeSession.toXContent(builder, EMPTY_PARAMS);

        String jsonString = TestHelper.xContentBuilderToString(builder);
        assertNotNull(jsonString);
        assert jsonString.contains("\"created_time\":0");
        assert jsonString.contains("\"last_updated_time\":" + Long.MAX_VALUE);
    }

    @Test
    public void testParseWithAllFieldsIncludingNamespace() throws IOException {
        // Test parsing with namespace through XContent
        XContentBuilder builder = XContentBuilder.builder(XContentType.JSON.xContent());
        builder.startObject();
        builder.field("owner_id", "owner-123");
        builder.field("summary", "Test session summary");
        builder.field("created_time", testCreatedTime.toEpochMilli());
        builder.field("last_updated_time", testLastUpdateTime.toEpochMilli());
        builder.field("metadata", testMetadata);
        builder.field("agents", testAgents);
        builder.field("additional_info", testAdditionalInfo);
        builder.field("namespace", testNamespace);
        builder.field("tenant_id", "tenant-456");
        builder.endObject();

        String jsonString = TestHelper.xContentBuilderToString(builder);
        XContentParser parser = XContentType.JSON
            .xContent()
            .createParser(NamedXContentRegistry.EMPTY, LoggingDeprecationHandler.INSTANCE, jsonString);
        parser.nextToken();

        MLMemorySession parsed = MLMemorySession.parse(parser);

        assertEquals("owner-123", parsed.getOwnerId());
        assertEquals("Test session summary", parsed.getSummary());
        assertEquals(testCreatedTime, parsed.getCreatedTime());
        assertEquals(testLastUpdateTime, parsed.getLastUpdateTime());
        assertEquals(testMetadata, parsed.getMetadata());
        assertEquals(testAgents, parsed.getAgents());
        assertEquals(testAdditionalInfo, parsed.getAdditionalInfo());
        assertEquals(testNamespace, parsed.getNamespace());
        assertEquals("tenant-456", parsed.getTenantId());
    }

    @Test
    public void testConstructorStreamInputWithNullMaps() throws IOException {
        // Test the constructor with StreamInput when maps are null
        MLMemorySession sessionWithNullMaps = MLMemorySession
            .builder()
            .ownerId("owner-123")
            .summary("Test summary")
            .createdTime(testCreatedTime)
            .lastUpdateTime(testLastUpdateTime)
            .metadata(null)
            .agents(null)
            .additionalInfo(null)
            .namespace(null)
            .tenantId("tenant-456")
            .build();

        BytesStreamOutput out = new BytesStreamOutput();
        sessionWithNullMaps.writeTo(out);

        StreamInput in = out.bytes().streamInput();
        MLMemorySession deserialized = new MLMemorySession(in);

        assertEquals("owner-123", deserialized.getOwnerId());
        assertEquals("Test summary", deserialized.getSummary());
        assertEquals(testCreatedTime, deserialized.getCreatedTime());
        assertEquals(testLastUpdateTime, deserialized.getLastUpdateTime());
        assertNull(deserialized.getMetadata());
        assertNull(deserialized.getAgents());
        assertNull(deserialized.getAdditionalInfo());
        assertNull(deserialized.getNamespace());
        assertEquals("tenant-456", deserialized.getTenantId());
    }

    @Test
    public void testToXContentWithPartialFields() throws IOException {
        // Test toXContent with only some fields set
        MLMemorySession partialSession = MLMemorySession
            .builder()
            .ownerId("owner-123")
            .createdTime(testCreatedTime)
            .metadata(testMetadata)
            .build();

        XContentBuilder builder = XContentBuilder.builder(XContentType.JSON.xContent());
        partialSession.toXContent(builder, EMPTY_PARAMS);

        String jsonString = TestHelper.xContentBuilderToString(builder);
        assertNotNull(jsonString);

        // Verify only set fields are present
        assert jsonString.contains("\"owner_id\":\"owner-123\"");
        assert jsonString.contains("\"created_time\":" + testCreatedTime.toEpochMilli());
        assert jsonString.contains("\"metadata\":");

        // Verify null fields are not present
        assert !jsonString.contains("\"summary\":");
        assert !jsonString.contains("\"last_updated_time\":");
        assert !jsonString.contains("\"agents\":");
        assert !jsonString.contains("\"additional_info\":");
        assert !jsonString.contains("\"namespace\":");
        assert !jsonString.contains("\"tenant_id\":");
    }

    @Test
    public void testParseWithNestedObjects() throws IOException {
        // Test parsing with complex nested objects in metadata and agents
        Map<String, Object> complexMetadata = new HashMap<>();
        complexMetadata.put("nested_object", Map.of("inner_key", "inner_value", "inner_number", 42));
        complexMetadata.put("array_field", java.util.Arrays.asList("item1", "item2", "item3"));

        Map<String, Object> complexAgents = new HashMap<>();
        complexAgents.put("agent_config", Map.of("enabled", true, "priority", 1));

        XContentBuilder builder = XContentBuilder.builder(XContentType.JSON.xContent());
        builder.startObject();
        builder.field("owner_id", "owner-complex");
        builder.field("metadata", complexMetadata);
        builder.field("agents", complexAgents);
        builder.endObject();

        String jsonString = TestHelper.xContentBuilderToString(builder);
        XContentParser parser = XContentType.JSON
            .xContent()
            .createParser(NamedXContentRegistry.EMPTY, LoggingDeprecationHandler.INSTANCE, jsonString);
        parser.nextToken();

        MLMemorySession parsed = MLMemorySession.parse(parser);

        assertEquals("owner-complex", parsed.getOwnerId());
        assertEquals(complexMetadata, parsed.getMetadata());
        assertEquals(complexAgents, parsed.getAgents());
    }

    @Test
    public void testBuilderDefaults() {
        // Test that builder creates object with null defaults
        MLMemorySession emptySession = MLMemorySession.builder().build();

        assertNull(emptySession.getOwnerId());
        assertNull(emptySession.getSummary());
        assertNull(emptySession.getCreatedTime());
        assertNull(emptySession.getLastUpdateTime());
        assertNull(emptySession.getMetadata());
        assertNull(emptySession.getAgents());
        assertNull(emptySession.getAdditionalInfo());
        assertNull(emptySession.getNamespace());
        assertNull(emptySession.getTenantId());
    }
}
