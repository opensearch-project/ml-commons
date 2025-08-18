/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.memorycontainer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
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
import org.opensearch.ml.common.transport.memorycontainer.memory.MemoryEvent;

public class MemoryDecisionTest {

    private MemoryDecision decisionWithAllFields;
    private MemoryDecision decisionMinimal;
    private MemoryDecision addDecision;
    private MemoryDecision deleteDecision;
    private MemoryDecision noneDecision;

    @Before
    public void setUp() {
        // UPDATE decision with all fields
        decisionWithAllFields = MemoryDecision
            .builder()
            .id("memory-123")
            .text("Updated memory text")
            .event(MemoryEvent.UPDATE)
            .oldMemory("Original memory text")
            .build();

        // Minimal decision (no oldMemory)
        decisionMinimal = MemoryDecision.builder().id("memory-456").text("New memory text").event(MemoryEvent.ADD).build();

        // Different event types
        addDecision = MemoryDecision.builder().id("add-memory-789").text("Adding new memory").event(MemoryEvent.ADD).build();

        deleteDecision = MemoryDecision.builder().id("delete-memory-101").text("Memory to delete").event(MemoryEvent.DELETE).build();

        noneDecision = MemoryDecision.builder().id("none-memory-202").text("No change needed").event(MemoryEvent.NONE).build();
    }

    @Test
    public void testBuilderWithAllFields() {
        assertNotNull(decisionWithAllFields);
        assertEquals("memory-123", decisionWithAllFields.getId());
        assertEquals("Updated memory text", decisionWithAllFields.getText());
        assertEquals(MemoryEvent.UPDATE, decisionWithAllFields.getEvent());
        assertEquals("Original memory text", decisionWithAllFields.getOldMemory());
    }

    @Test
    public void testBuilderMinimal() {
        assertNotNull(decisionMinimal);
        assertEquals("memory-456", decisionMinimal.getId());
        assertEquals("New memory text", decisionMinimal.getText());
        assertEquals(MemoryEvent.ADD, decisionMinimal.getEvent());
        assertNull(decisionMinimal.getOldMemory());
    }

    @Test
    public void testConstructorWithAllParameters() {
        MemoryDecision decision = new MemoryDecision("id-1", "text-1", MemoryEvent.UPDATE, "old-text");
        assertEquals("id-1", decision.getId());
        assertEquals("text-1", decision.getText());
        assertEquals(MemoryEvent.UPDATE, decision.getEvent());
        assertEquals("old-text", decision.getOldMemory());
    }

    @Test
    public void testConstructorWithNullOldMemory() {
        MemoryDecision decision = new MemoryDecision("id-2", "text-2", MemoryEvent.ADD, null);
        assertEquals("id-2", decision.getId());
        assertEquals("text-2", decision.getText());
        assertEquals(MemoryEvent.ADD, decision.getEvent());
        assertNull(decision.getOldMemory());
    }

    @Test
    public void testStreamInputOutput() throws IOException {
        // Test with all fields
        BytesStreamOutput out = new BytesStreamOutput();
        decisionWithAllFields.writeTo(out);
        StreamInput in = out.bytes().streamInput();
        MemoryDecision deserialized = new MemoryDecision(in);

        assertEquals(decisionWithAllFields.getId(), deserialized.getId());
        assertEquals(decisionWithAllFields.getText(), deserialized.getText());
        assertEquals(decisionWithAllFields.getEvent(), deserialized.getEvent());
        assertEquals(decisionWithAllFields.getOldMemory(), deserialized.getOldMemory());
    }

    @Test
    public void testStreamInputOutputMinimal() throws IOException {
        // Test with minimal fields
        BytesStreamOutput out = new BytesStreamOutput();
        decisionMinimal.writeTo(out);
        StreamInput in = out.bytes().streamInput();
        MemoryDecision deserialized = new MemoryDecision(in);

        assertEquals(decisionMinimal.getId(), deserialized.getId());
        assertEquals(decisionMinimal.getText(), deserialized.getText());
        assertEquals(decisionMinimal.getEvent(), deserialized.getEvent());
        assertNull(deserialized.getOldMemory());
    }

    @Test
    public void testStreamInputOutputAllEventTypes() throws IOException {
        // Test all event types
        MemoryDecision[] decisions = { addDecision, deleteDecision, noneDecision, decisionWithAllFields };

        for (MemoryDecision original : decisions) {
            BytesStreamOutput out = new BytesStreamOutput();
            original.writeTo(out);
            StreamInput in = out.bytes().streamInput();
            MemoryDecision deserialized = new MemoryDecision(in);

            assertEquals(original.getId(), deserialized.getId());
            assertEquals(original.getText(), deserialized.getText());
            assertEquals(original.getEvent(), deserialized.getEvent());
            assertEquals(original.getOldMemory(), deserialized.getOldMemory());
        }
    }

    @Test
    public void testToXContent() throws IOException {
        XContentBuilder builder = MediaTypeRegistry.contentBuilder(XContentType.JSON);
        decisionWithAllFields.toXContent(builder, EMPTY_PARAMS);
        String jsonString = TestHelper.xContentBuilderToString(builder);

        assertTrue(jsonString.contains("\"memory_id\":\"memory-123\""));
        assertTrue(jsonString.contains("\"text\":\"Updated memory text\""));
        assertTrue(jsonString.contains("\"event\":\"UPDATE\""));
        assertTrue(jsonString.contains("\"old_memory\":\"Original memory text\""));
    }

    @Test
    public void testToXContentMinimal() throws IOException {
        XContentBuilder builder = MediaTypeRegistry.contentBuilder(XContentType.JSON);
        decisionMinimal.toXContent(builder, EMPTY_PARAMS);
        String jsonString = TestHelper.xContentBuilderToString(builder);

        assertTrue(jsonString.contains("\"memory_id\":\"memory-456\""));
        assertTrue(jsonString.contains("\"text\":\"New memory text\""));
        assertTrue(jsonString.contains("\"event\":\"ADD\""));
        assertTrue(!jsonString.contains("\"old_memory\""));
    }

    @Test
    public void testParse() throws IOException {
        String jsonString = "{"
            + "\"memory_id\":\"memory-123\","
            + "\"text\":\"Updated memory text\","
            + "\"event\":\"UPDATE\","
            + "\"old_memory\":\"Original memory text\""
            + "}";

        XContentParser parser = XContentType.JSON
            .xContent()
            .createParser(NamedXContentRegistry.EMPTY, LoggingDeprecationHandler.INSTANCE, jsonString);
        parser.nextToken();

        MemoryDecision parsed = MemoryDecision.parse(parser);

        assertEquals("memory-123", parsed.getId());
        assertEquals("Updated memory text", parsed.getText());
        assertEquals(MemoryEvent.UPDATE, parsed.getEvent());
        assertEquals("Original memory text", parsed.getOldMemory());
    }

    @Test
    public void testParseMinimal() throws IOException {
        String jsonString = "{" + "\"memory_id\":\"memory-456\"," + "\"text\":\"New memory text\"," + "\"event\":\"ADD\"" + "}";

        XContentParser parser = XContentType.JSON
            .xContent()
            .createParser(NamedXContentRegistry.EMPTY, LoggingDeprecationHandler.INSTANCE, jsonString);
        parser.nextToken();

        MemoryDecision parsed = MemoryDecision.parse(parser);

        assertEquals("memory-456", parsed.getId());
        assertEquals("New memory text", parsed.getText());
        assertEquals(MemoryEvent.ADD, parsed.getEvent());
        assertNull(parsed.getOldMemory());
    }

    @Test
    public void testParseWithAlternativeIdField() throws IOException {
        // Test parsing with "id" field instead of "memory_id"
        String jsonString = "{" + "\"id\":\"memory-789\"," + "\"text\":\"Test memory\"," + "\"event\":\"DELETE\"" + "}";

        XContentParser parser = XContentType.JSON
            .xContent()
            .createParser(NamedXContentRegistry.EMPTY, LoggingDeprecationHandler.INSTANCE, jsonString);
        parser.nextToken();

        MemoryDecision parsed = MemoryDecision.parse(parser);

        assertEquals("memory-789", parsed.getId());
        assertEquals("Test memory", parsed.getText());
        assertEquals(MemoryEvent.DELETE, parsed.getEvent());
    }

    @Test
    public void testParseWithUnknownFields() throws IOException {
        String jsonString = "{"
            + "\"memory_id\":\"memory-123\","
            + "\"text\":\"Test memory\","
            + "\"event\":\"NONE\","
            + "\"unknown_field\":\"should be ignored\","
            + "\"another_unknown\":123"
            + "}";

        XContentParser parser = XContentType.JSON
            .xContent()
            .createParser(NamedXContentRegistry.EMPTY, LoggingDeprecationHandler.INSTANCE, jsonString);
        parser.nextToken();

        MemoryDecision parsed = MemoryDecision.parse(parser);

        assertEquals("memory-123", parsed.getId());
        assertEquals("Test memory", parsed.getText());
        assertEquals(MemoryEvent.NONE, parsed.getEvent());
    }

    @Test
    public void testXContentRoundTrip() throws IOException {
        // Convert to XContent
        XContentBuilder builder = MediaTypeRegistry.contentBuilder(XContentType.JSON);
        decisionWithAllFields.toXContent(builder, EMPTY_PARAMS);
        String jsonString = TestHelper.xContentBuilderToString(builder);

        // Parse back
        XContentParser parser = XContentType.JSON
            .xContent()
            .createParser(NamedXContentRegistry.EMPTY, LoggingDeprecationHandler.INSTANCE, jsonString);
        parser.nextToken();
        MemoryDecision parsed = MemoryDecision.parse(parser);

        // Verify all fields match
        assertEquals(decisionWithAllFields.getId(), parsed.getId());
        assertEquals(decisionWithAllFields.getText(), parsed.getText());
        assertEquals(decisionWithAllFields.getEvent(), parsed.getEvent());
        assertEquals(decisionWithAllFields.getOldMemory(), parsed.getOldMemory());
    }

    @Test
    public void testDataAnnotationMethods() {
        // Test @Data generated methods
        MemoryDecision decision1 = MemoryDecision.builder().id("id-1").text("text-1").event(MemoryEvent.ADD).build();

        MemoryDecision decision2 = MemoryDecision.builder().id("id-1").text("text-1").event(MemoryEvent.ADD).build();

        // Test equals
        assertEquals(decision1, decision2);
        assertEquals(decision1.hashCode(), decision2.hashCode());

        // Test setters
        decision1.setId("new-id");
        decision1.setText("new-text");
        decision1.setEvent(MemoryEvent.UPDATE);
        decision1.setOldMemory("old-text");

        assertEquals("new-id", decision1.getId());
        assertEquals("new-text", decision1.getText());
        assertEquals(MemoryEvent.UPDATE, decision1.getEvent());
        assertEquals("old-text", decision1.getOldMemory());

        // Test toString
        String str = decision1.toString();
        assertTrue(str.contains("new-id"));
        assertTrue(str.contains("new-text"));
        assertTrue(str.contains("UPDATE"));
        assertTrue(str.contains("old-text"));
    }

    @Test
    public void testSpecialCharactersInFields() throws IOException {
        MemoryDecision specialDecision = MemoryDecision
            .builder()
            .id("id-with-special-chars-🚀")
            .text("Text with\n\ttabs and\nnewlines and \"quotes\"")
            .event(MemoryEvent.UPDATE)
            .oldMemory("Old text with 'single quotes' and \\backslashes\\")
            .build();

        // Test XContent round trip
        XContentBuilder builder = MediaTypeRegistry.contentBuilder(XContentType.JSON);
        specialDecision.toXContent(builder, EMPTY_PARAMS);
        String jsonString = TestHelper.xContentBuilderToString(builder);

        XContentParser parser = XContentType.JSON
            .xContent()
            .createParser(NamedXContentRegistry.EMPTY, LoggingDeprecationHandler.INSTANCE, jsonString);
        parser.nextToken();
        MemoryDecision parsed = MemoryDecision.parse(parser);

        assertEquals(specialDecision.getId(), parsed.getId());
        assertEquals(specialDecision.getText(), parsed.getText());
        assertEquals(specialDecision.getOldMemory(), parsed.getOldMemory());
    }
}
