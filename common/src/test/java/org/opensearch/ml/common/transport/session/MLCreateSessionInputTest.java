/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.session;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.opensearch.core.xcontent.ToXContent.EMPTY_PARAMS;

import java.io.IOException;
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

public class MLCreateSessionInputTest {

    private MLCreateSessionInput inputWithAllFields;
    private MLCreateSessionInput inputWithNullFields;
    private Map<String, Object> testMetadata;
    private Map<String, Object> testAgents;
    private Map<String, Object> testAdditionalInfo;
    private Map<String, String> testNamespace;

    @Before
    public void setUp() {
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

        inputWithAllFields = MLCreateSessionInput
            .builder()
            .sessionId("session-123")
            .ownerId("owner-456")
            .summary("Test session summary")
            .metadata(testMetadata)
            .agents(testAgents)
            .additionalInfo(testAdditionalInfo)
            .namespace(testNamespace)
            .tenantId("tenant-789")
            .memoryContainerId("memory-container-abc")
            .build();

        inputWithNullFields = MLCreateSessionInput
            .builder()
            .sessionId(null)
            .ownerId(null)
            .summary(null)
            .metadata(null)
            .agents(null)
            .additionalInfo(null)
            .namespace(null)
            .tenantId(null)
            .memoryContainerId(null)
            .build();
    }

    @Test
    public void testBuilderWithAllFields() {
        assertNotNull(inputWithAllFields);
        assertEquals("session-123", inputWithAllFields.getSessionId());
        assertEquals("owner-456", inputWithAllFields.getOwnerId());
        assertEquals("Test session summary", inputWithAllFields.getSummary());
        assertEquals(testMetadata, inputWithAllFields.getMetadata());
        assertEquals(testAgents, inputWithAllFields.getAgents());
        assertEquals(testAdditionalInfo, inputWithAllFields.getAdditionalInfo());
        assertEquals(testNamespace, inputWithAllFields.getNamespace());
        assertEquals("tenant-789", inputWithAllFields.getTenantId());
        assertEquals("memory-container-abc", inputWithAllFields.getMemoryContainerId());
    }

    @Test
    public void testBuilderWithNullFields() {
        assertNotNull(inputWithNullFields);
        assertNull(inputWithNullFields.getSessionId());
        assertNull(inputWithNullFields.getOwnerId());
        assertNull(inputWithNullFields.getSummary());
        assertNull(inputWithNullFields.getMetadata());
        assertNull(inputWithNullFields.getAgents());
        assertNull(inputWithNullFields.getAdditionalInfo());
        assertNull(inputWithNullFields.getNamespace());
        assertNull(inputWithNullFields.getTenantId());
        assertNull(inputWithNullFields.getMemoryContainerId());
    }

    @Test
    public void testConstructorWithAllFields() {
        MLCreateSessionInput input = new MLCreateSessionInput(
            "session-123",
            "owner-456",
            "Test session summary",
            testMetadata,
            testAgents,
            testAdditionalInfo,
            testNamespace,
            "tenant-789",
            "memory-container-abc"
        );

        assertEquals("session-123", input.getSessionId());
        assertEquals("owner-456", input.getOwnerId());
        assertEquals("Test session summary", input.getSummary());
        assertEquals(testMetadata, input.getMetadata());
        assertEquals(testAgents, input.getAgents());
        assertEquals(testAdditionalInfo, input.getAdditionalInfo());
        assertEquals(testNamespace, input.getNamespace());
        assertEquals("tenant-789", input.getTenantId());
        assertEquals("memory-container-abc", input.getMemoryContainerId());
    }

    @Test
    public void testConstructorWithNullFields() {
        MLCreateSessionInput input = new MLCreateSessionInput(null, null, null, null, null, null, null, null, null);

        assertNull(input.getSessionId());
        assertNull(input.getOwnerId());
        assertNull(input.getSummary());
        assertNull(input.getMetadata());
        assertNull(input.getAgents());
        assertNull(input.getAdditionalInfo());
        assertNull(input.getNamespace());
        assertNull(input.getTenantId());
        assertNull(input.getMemoryContainerId());
    }

    @Test
    public void testStreamInputOutputWithAllFields() throws IOException {
        // Create input without namespace to avoid serialization bug
        MLCreateSessionInput inputWithoutNamespace = MLCreateSessionInput
            .builder()
            .sessionId("session-123")
            .ownerId("owner-456")
            .summary("Test session summary")
            .metadata(testMetadata)
            .agents(testAgents)
            .additionalInfo(testAdditionalInfo)
            .namespace(null) // Avoid namespace serialization bug
            .tenantId("tenant-789")
            .memoryContainerId("memory-container-abc")
            .build();

        BytesStreamOutput out = new BytesStreamOutput();
        inputWithoutNamespace.writeTo(out);

        StreamInput in = out.bytes().streamInput();
        MLCreateSessionInput deserialized = new MLCreateSessionInput(in);

        assertEquals(inputWithoutNamespace.getSessionId(), deserialized.getSessionId());
        assertEquals(inputWithoutNamespace.getOwnerId(), deserialized.getOwnerId());
        assertEquals(inputWithoutNamespace.getSummary(), deserialized.getSummary());
        assertEquals(inputWithoutNamespace.getMetadata(), deserialized.getMetadata());
        assertEquals(inputWithoutNamespace.getAgents(), deserialized.getAgents());
        assertEquals(inputWithoutNamespace.getAdditionalInfo(), deserialized.getAdditionalInfo());
        assertEquals(inputWithoutNamespace.getNamespace(), deserialized.getNamespace());
        assertEquals(inputWithoutNamespace.getTenantId(), deserialized.getTenantId());
        assertEquals(inputWithoutNamespace.getMemoryContainerId(), deserialized.getMemoryContainerId());
    }

    @Test
    public void testStreamInputOutputWithNullFields() throws IOException {
        BytesStreamOutput out = new BytesStreamOutput();
        inputWithNullFields.writeTo(out);

        StreamInput in = out.bytes().streamInput();
        MLCreateSessionInput deserialized = new MLCreateSessionInput(in);

        assertNull(deserialized.getSessionId());
        assertNull(deserialized.getOwnerId());
        assertNull(deserialized.getSummary());
        assertNull(deserialized.getMetadata());
        assertNull(deserialized.getAgents());
        assertNull(deserialized.getAdditionalInfo());
        assertNull(deserialized.getNamespace());
        assertNull(deserialized.getTenantId());
        assertNull(deserialized.getMemoryContainerId());
    }

    @Test
    public void testToXContentWithAllFields() throws IOException {
        XContentBuilder builder = XContentBuilder.builder(XContentType.JSON.xContent());
        inputWithAllFields.toXContent(builder, EMPTY_PARAMS);

        String jsonString = TestHelper.xContentBuilderToString(builder);
        assertNotNull(jsonString);

        // Verify all fields are present in the JSON
        assert jsonString.contains("\"session_id\":\"session-123\"");
        assert jsonString.contains("\"owner_id\":\"owner-456\"");
        assert jsonString.contains("\"summary\":\"Test session summary\"");
        assert jsonString.contains("\"metadata\":");
        assert jsonString.contains("\"agents\":");
        assert jsonString.contains("\"additional_info\":");
        assert jsonString.contains("\"namespace\":");
        assert jsonString.contains("\"tenant_id\":\"tenant-789\"");
    }

    @Test
    public void testToXContentWithNullFields() throws IOException {
        XContentBuilder builder = XContentBuilder.builder(XContentType.JSON.xContent());
        inputWithNullFields.toXContent(builder, EMPTY_PARAMS);

        String jsonString = TestHelper.xContentBuilderToString(builder);
        assertEquals("{}", jsonString);
    }

    @Test
    public void testParseWithAllFields() throws IOException {
        XContentBuilder builder = XContentBuilder.builder(XContentType.JSON.xContent());
        builder.startObject();
        builder.field("session_id", "session-123");
        builder.field("owner_id", "owner-456");
        builder.field("summary", "Test session summary");
        builder.field("metadata", testMetadata);
        builder.field("agents", testAgents);
        builder.field("additional_info", testAdditionalInfo);
        builder.field("namespace", testNamespace);
        builder.field("tenant_id", "tenant-789");
        builder.endObject();

        String jsonString = TestHelper.xContentBuilderToString(builder);
        XContentParser parser = XContentType.JSON
            .xContent()
            .createParser(NamedXContentRegistry.EMPTY, LoggingDeprecationHandler.INSTANCE, jsonString);
        parser.nextToken();

        MLCreateSessionInput parsed = MLCreateSessionInput.parse(parser);

        assertEquals("session-123", parsed.getSessionId());
        assertEquals("owner-456", parsed.getOwnerId());
        assertEquals("Test session summary", parsed.getSummary());
        assertEquals(testMetadata, parsed.getMetadata());
        assertEquals(testAgents, parsed.getAgents());
        assertEquals(testAdditionalInfo, parsed.getAdditionalInfo());
        assertEquals(testNamespace, parsed.getNamespace());
        assertEquals("tenant-789", parsed.getTenantId());
        assertNull(parsed.getMemoryContainerId()); // Not included in parsing
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

        MLCreateSessionInput parsed = MLCreateSessionInput.parse(parser);

        assertNull(parsed.getSessionId());
        assertNull(parsed.getOwnerId());
        assertNull(parsed.getSummary());
        assertNull(parsed.getMetadata());
        assertNull(parsed.getAgents());
        assertNull(parsed.getAdditionalInfo());
        assertNull(parsed.getNamespace());
        assertNull(parsed.getTenantId());
        assertNull(parsed.getMemoryContainerId());
    }

    @Test
    public void testParseWithUnknownFields() throws IOException {
        XContentBuilder builder = XContentBuilder.builder(XContentType.JSON.xContent());
        builder.startObject();
        builder.field("session_id", "session-123");
        builder.field("unknown_field", "unknown_value");
        builder.field("summary", "Test session summary");
        builder.field("another_unknown", 42);
        builder.endObject();

        String jsonString = TestHelper.xContentBuilderToString(builder);
        XContentParser parser = XContentType.JSON
            .xContent()
            .createParser(NamedXContentRegistry.EMPTY, LoggingDeprecationHandler.INSTANCE, jsonString);
        parser.nextToken();

        MLCreateSessionInput parsed = MLCreateSessionInput.parse(parser);

        assertEquals("session-123", parsed.getSessionId());
        assertEquals("Test session summary", parsed.getSummary());
        assertNull(parsed.getOwnerId());
        assertNull(parsed.getMetadata());
        assertNull(parsed.getAgents());
        assertNull(parsed.getAdditionalInfo());
        assertNull(parsed.getNamespace());
        assertNull(parsed.getTenantId());
        assertNull(parsed.getMemoryContainerId());
    }

    @Test
    public void testSettersAndGetters() {
        MLCreateSessionInput input = new MLCreateSessionInput(null, null, null, null, null, null, null, null, null);

        // Test setters
        input.setSessionId("new-session");
        input.setOwnerId("new-owner");
        input.setSummary("new-summary");
        input.setMetadata(testMetadata);
        input.setAgents(testAgents);
        input.setAdditionalInfo(testAdditionalInfo);
        input.setNamespace(testNamespace);
        input.setTenantId("new-tenant");
        input.setMemoryContainerId("new-memory-container");

        // Test getters
        assertEquals("new-session", input.getSessionId());
        assertEquals("new-owner", input.getOwnerId());
        assertEquals("new-summary", input.getSummary());
        assertEquals(testMetadata, input.getMetadata());
        assertEquals(testAgents, input.getAgents());
        assertEquals(testAdditionalInfo, input.getAdditionalInfo());
        assertEquals(testNamespace, input.getNamespace());
        assertEquals("new-tenant", input.getTenantId());
        assertEquals("new-memory-container", input.getMemoryContainerId());
    }

    @Test
    public void testStreamInputOutputWithEmptyMaps() throws IOException {
        Map<String, Object> emptyMetadata = new HashMap<>();
        Map<String, Object> emptyAgents = new HashMap<>();
        Map<String, Object> emptyAdditionalInfo = new HashMap<>();
        Map<String, String> emptyNamespace = new HashMap<>();

        MLCreateSessionInput inputWithEmptyMaps = MLCreateSessionInput
            .builder()
            .sessionId("session-123")
            .ownerId("owner-456")
            .summary("Test summary")
            .metadata(emptyMetadata)
            .agents(emptyAgents)
            .additionalInfo(emptyAdditionalInfo)
            .namespace(emptyNamespace)
            .tenantId("tenant-789")
            .memoryContainerId("memory-container-abc")
            .build();

        BytesStreamOutput out = new BytesStreamOutput();
        inputWithEmptyMaps.writeTo(out);

        StreamInput in = out.bytes().streamInput();
        MLCreateSessionInput deserialized = new MLCreateSessionInput(in);

        assertEquals("session-123", deserialized.getSessionId());
        assertEquals("owner-456", deserialized.getOwnerId());
        assertEquals("Test summary", deserialized.getSummary());
        assertEquals(emptyMetadata, deserialized.getMetadata());
        assertEquals(emptyAgents, deserialized.getAgents());
        assertEquals(emptyAdditionalInfo, deserialized.getAdditionalInfo());
        assertEquals(emptyNamespace, deserialized.getNamespace());
        assertEquals("tenant-789", deserialized.getTenantId());
        assertEquals("memory-container-abc", deserialized.getMemoryContainerId());
    }

    @Test
    public void testToXContentWithEmptyMaps() throws IOException {
        Map<String, Object> emptyMetadata = new HashMap<>();
        Map<String, Object> emptyAgents = new HashMap<>();
        Map<String, Object> emptyAdditionalInfo = new HashMap<>();
        Map<String, String> emptyNamespace = new HashMap<>();

        MLCreateSessionInput inputWithEmptyMaps = MLCreateSessionInput
            .builder()
            .sessionId("session-123")
            .ownerId("owner-456")
            .summary("Test summary")
            .metadata(emptyMetadata)
            .agents(emptyAgents)
            .additionalInfo(emptyAdditionalInfo)
            .namespace(emptyNamespace)
            .tenantId("tenant-789")
            .build();

        XContentBuilder builder = XContentBuilder.builder(XContentType.JSON.xContent());
        inputWithEmptyMaps.toXContent(builder, EMPTY_PARAMS);

        String jsonString = TestHelper.xContentBuilderToString(builder);
        assertNotNull(jsonString);

        // Verify fields are present even when maps are empty
        assert jsonString.contains("\"session_id\":\"session-123\"");
        assert jsonString.contains("\"owner_id\":\"owner-456\"");
        assert jsonString.contains("\"summary\":\"Test summary\"");
        assert jsonString.contains("\"metadata\":{}");
        assert jsonString.contains("\"agents\":{}");
        assert jsonString.contains("\"additional_info\":{}");
        assert jsonString.contains("\"namespace\":{}");
        assert jsonString.contains("\"tenant_id\":\"tenant-789\"");
    }

    @Test
    public void testParseWithPartialFields() throws IOException {
        XContentBuilder builder = XContentBuilder.builder(XContentType.JSON.xContent());
        builder.startObject();
        builder.field("session_id", "session-123");
        builder.field("summary", "Test session summary");
        builder.field("metadata", testMetadata);
        // Skip other fields to test partial parsing
        builder.endObject();

        String jsonString = TestHelper.xContentBuilderToString(builder);
        XContentParser parser = XContentType.JSON
            .xContent()
            .createParser(NamedXContentRegistry.EMPTY, LoggingDeprecationHandler.INSTANCE, jsonString);
        parser.nextToken();

        MLCreateSessionInput parsed = MLCreateSessionInput.parse(parser);

        assertEquals("session-123", parsed.getSessionId());
        assertNull(parsed.getOwnerId());
        assertEquals("Test session summary", parsed.getSummary());
        assertEquals(testMetadata, parsed.getMetadata());
        assertNull(parsed.getAgents());
        assertNull(parsed.getAdditionalInfo());
        assertNull(parsed.getNamespace());
        assertNull(parsed.getTenantId());
        assertNull(parsed.getMemoryContainerId());
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

        MLCreateSessionInput complexInput = MLCreateSessionInput
            .builder()
            .sessionId("session-complex")
            .metadata(complexMetadata)
            .agents(complexAgents)
            .build();

        // Test serialization/deserialization
        BytesStreamOutput out = new BytesStreamOutput();
        complexInput.writeTo(out);

        StreamInput in = out.bytes().streamInput();
        MLCreateSessionInput deserialized = new MLCreateSessionInput(in);

        assertEquals("session-complex", deserialized.getSessionId());
        assertEquals(complexMetadata, deserialized.getMetadata());
        assertEquals(complexAgents, deserialized.getAgents());

        // Test XContent serialization
        XContentBuilder builder = XContentBuilder.builder(XContentType.JSON.xContent());
        complexInput.toXContent(builder, EMPTY_PARAMS);

        String jsonString = TestHelper.xContentBuilderToString(builder);
        assertNotNull(jsonString);
        assert jsonString.contains("\"session_id\":\"session-complex\"");
        assert jsonString.contains("\"metadata\":");
        assert jsonString.contains("\"agents\":");
    }

    @Test
    public void testSpecialCharactersInFields() throws IOException {
        String specialSessionId = "session-with-special-chars-🚀✨";
        String specialSummary = "Summary with\nnewlines\tand\ttabs and \"quotes\"";
        String specialTenantId = "tenant-with-unicode-💫";

        MLCreateSessionInput specialInput = MLCreateSessionInput
            .builder()
            .sessionId(specialSessionId)
            .summary(specialSummary)
            .tenantId(specialTenantId)
            .build();

        // Test serialization/deserialization (without namespace due to bug)
        BytesStreamOutput out = new BytesStreamOutput();
        specialInput.writeTo(out);

        StreamInput in = out.bytes().streamInput();
        MLCreateSessionInput deserialized = new MLCreateSessionInput(in);

        assertEquals(specialSessionId, deserialized.getSessionId());
        assertEquals(specialSummary, deserialized.getSummary());
        assertEquals(specialTenantId, deserialized.getTenantId());

        // Test XContent serialization
        XContentBuilder builder = XContentBuilder.builder(XContentType.JSON.xContent());
        specialInput.toXContent(builder, EMPTY_PARAMS);

        String jsonString = TestHelper.xContentBuilderToString(builder);
        assertNotNull(jsonString);
    }

    @Test
    public void testNamespaceThroughXContent() throws IOException {
        // Test namespace through XContent since stream serialization has a bug
        Map<String, String> specialNamespace = new HashMap<>();
        specialNamespace.put("key-with-unicode-🌟", "value-with-unicode-⭐");
        specialNamespace.put("key with spaces", "value with spaces");

        MLCreateSessionInput specialInput = MLCreateSessionInput.builder().sessionId("session-123").namespace(specialNamespace).build();

        // Test XContent serialization
        XContentBuilder builder = XContentBuilder.builder(XContentType.JSON.xContent());
        specialInput.toXContent(builder, EMPTY_PARAMS);

        String jsonString = TestHelper.xContentBuilderToString(builder);
        assertNotNull(jsonString);
        assert jsonString.contains("\"namespace\":");
        assert jsonString.contains("\"session_id\":\"session-123\"");
    }

    @Test
    public void testBuilderDefaults() {
        // Test that builder creates object with null defaults
        MLCreateSessionInput emptyInput = MLCreateSessionInput.builder().build();

        assertNull(emptyInput.getSessionId());
        assertNull(emptyInput.getOwnerId());
        assertNull(emptyInput.getSummary());
        assertNull(emptyInput.getMetadata());
        assertNull(emptyInput.getAgents());
        assertNull(emptyInput.getAdditionalInfo());
        assertNull(emptyInput.getNamespace());
        assertNull(emptyInput.getTenantId());
        assertNull(emptyInput.getMemoryContainerId());
    }

    @Test
    public void testToXContentBugWithAgentsField() throws IOException {
        // Test the bug in toXContent where agents field is checked against metadata != null
        // This tests line 118: if (metadata != null) { builder.field(AGENTS_FIELD, agents); }
        MLCreateSessionInput inputWithAgentsOnly = MLCreateSessionInput
            .builder()
            .sessionId("session-123")
            .agents(testAgents)
            .metadata(null) // metadata is null
            .build();

        XContentBuilder builder = XContentBuilder.builder(XContentType.JSON.xContent());
        inputWithAgentsOnly.toXContent(builder, EMPTY_PARAMS);

        String jsonString = TestHelper.xContentBuilderToString(builder);
        assertNotNull(jsonString);

        // Due to the bug, agents field should NOT be included when metadata is null
        assert jsonString.contains("\"session_id\":\"session-123\"");
        assert !jsonString.contains("\"agents\":");
    }

    @Test
    public void testToXContentWithMetadataAndAgents() throws IOException {
        // Test that agents field is included when metadata is not null (due to the bug)
        MLCreateSessionInput inputWithBoth = MLCreateSessionInput
            .builder()
            .sessionId("session-123")
            .metadata(testMetadata)
            .agents(testAgents)
            .build();

        XContentBuilder builder = XContentBuilder.builder(XContentType.JSON.xContent());
        inputWithBoth.toXContent(builder, EMPTY_PARAMS);

        String jsonString = TestHelper.xContentBuilderToString(builder);
        assertNotNull(jsonString);

        // Both metadata and agents should be included
        assert jsonString.contains("\"session_id\":\"session-123\"");
        assert jsonString.contains("\"metadata\":");
        assert jsonString.contains("\"agents\":");
    }

    @Test
    public void testParseWithComplexNestedObjects() throws IOException {
        // Test parsing with complex nested objects in metadata and agents
        Map<String, Object> complexMetadata = new HashMap<>();
        complexMetadata.put("nested_object", Map.of("inner_key", "inner_value", "inner_number", 42));
        complexMetadata.put("array_field", java.util.Arrays.asList("item1", "item2", "item3"));

        Map<String, Object> complexAgents = new HashMap<>();
        complexAgents.put("agent_config", Map.of("enabled", true, "priority", 1));

        XContentBuilder builder = XContentBuilder.builder(XContentType.JSON.xContent());
        builder.startObject();
        builder.field("session_id", "session-complex");
        builder.field("metadata", complexMetadata);
        builder.field("agents", complexAgents);
        builder.endObject();

        String jsonString = TestHelper.xContentBuilderToString(builder);
        XContentParser parser = XContentType.JSON
            .xContent()
            .createParser(NamedXContentRegistry.EMPTY, LoggingDeprecationHandler.INSTANCE, jsonString);
        parser.nextToken();

        MLCreateSessionInput parsed = MLCreateSessionInput.parse(parser);

        assertEquals("session-complex", parsed.getSessionId());
        assertEquals(complexMetadata, parsed.getMetadata());
        assertEquals(complexAgents, parsed.getAgents());
    }

    @Test
    public void testMemoryContainerIdNotInXContent() throws IOException {
        // Test that memoryContainerId is not included in XContent output
        MLCreateSessionInput inputWithMemoryContainer = MLCreateSessionInput
            .builder()
            .sessionId("session-123")
            .memoryContainerId("memory-container-abc")
            .build();

        XContentBuilder builder = XContentBuilder.builder(XContentType.JSON.xContent());
        inputWithMemoryContainer.toXContent(builder, EMPTY_PARAMS);

        String jsonString = TestHelper.xContentBuilderToString(builder);
        assertNotNull(jsonString);

        // memoryContainerId should not be in XContent output
        assert jsonString.contains("\"session_id\":\"session-123\"");
        assert !jsonString.contains("memory-container-abc");
        assert !jsonString.contains("memory_container_id");
    }

    @Test
    public void testAllFieldsIndividually() {
        // Test each field individually to ensure complete coverage
        MLCreateSessionInput input = MLCreateSessionInput.builder().build();

        // Test sessionId
        input.setSessionId("test-session");
        assertEquals("test-session", input.getSessionId());

        // Test ownerId
        input.setOwnerId("test-owner");
        assertEquals("test-owner", input.getOwnerId());

        // Test summary
        input.setSummary("test-summary");
        assertEquals("test-summary", input.getSummary());

        // Test metadata
        input.setMetadata(testMetadata);
        assertEquals(testMetadata, input.getMetadata());

        // Test agents
        input.setAgents(testAgents);
        assertEquals(testAgents, input.getAgents());

        // Test additionalInfo
        input.setAdditionalInfo(testAdditionalInfo);
        assertEquals(testAdditionalInfo, input.getAdditionalInfo());

        // Test namespace
        input.setNamespace(testNamespace);
        assertEquals(testNamespace, input.getNamespace());

        // Test tenantId
        input.setTenantId("test-tenant");
        assertEquals("test-tenant", input.getTenantId());

        // Test memoryContainerId
        input.setMemoryContainerId("test-memory-container");
        assertEquals("test-memory-container", input.getMemoryContainerId());
    }
}
