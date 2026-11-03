/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.memorycontainer.memory;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
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
import org.opensearch.core.xcontent.MediaTypeRegistry;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.TestHelper;

public class MLUpdateMemoryInputTest {

    private MLUpdateMemoryInput inputNormal;
    private MLUpdateMemoryInput inputWithWhitespace;

    @Before
    public void setUp() {
        Map<String, Object> normalContent = new HashMap<>();
        normalContent.put("text", "Updated memory content");
        inputNormal = MLUpdateMemoryInput.builder().updateContent(normalContent).build();

        Map<String, Object> whitespaceContent = new HashMap<>();
        whitespaceContent.put("text", "  Text with surrounding spaces  ");
        inputWithWhitespace = MLUpdateMemoryInput.builder().updateContent(whitespaceContent).build();
    }

    @Test
    public void testBuilderNormal() {
        assertNotNull(inputNormal);
        assertEquals("Updated memory content", inputNormal.getUpdateContent().get("text"));
    }

    @Test
    public void testBuilderWithWhitespace() {
        assertNotNull(inputWithWhitespace);
        assertEquals("  Text with surrounding spaces  ", inputWithWhitespace.getUpdateContent().get("text"));
    }

    @Test
    public void testConstructor() {
        Map<String, Object> content = new HashMap<>();
        content.put("text", "Test text");
        MLUpdateMemoryInput input = MLUpdateMemoryInput.builder().updateContent(content).build();
        assertEquals("Test text", input.getUpdateContent().get("text"));
    }

    @Test
    public void testConstructorWithMultipleFields() {
        Map<String, Object> content = new HashMap<>();
        content.put("text", "Test text");
        content.put("metadata", "some metadata");
        MLUpdateMemoryInput input = MLUpdateMemoryInput.builder().updateContent(content).build();
        assertEquals("Test text", input.getUpdateContent().get("text"));
        assertEquals("some metadata", input.getUpdateContent().get("metadata"));
    }

    @Test
    public void testConstructorWithNullContent() {
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> MLUpdateMemoryInput.builder().updateContent(null).build()
        );
        assertEquals("Text cannot be null or empty", exception.getMessage());
    }

    @Test
    public void testConstructorWithEmptyContent() {
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> MLUpdateMemoryInput.builder().updateContent(new HashMap<>()).build()
        );
        assertEquals("Text cannot be null or empty", exception.getMessage());
    }

    @Test
    public void testStreamInputOutput() throws IOException {
        BytesStreamOutput out = new BytesStreamOutput();
        inputNormal.writeTo(out);
        StreamInput in = out.bytes().streamInput();
        MLUpdateMemoryInput deserialized = new MLUpdateMemoryInput(in);

        assertEquals(inputNormal.getUpdateContent(), deserialized.getUpdateContent());
    }

    @Test
    public void testToXContent() throws IOException {
        XContentBuilder builder = MediaTypeRegistry.contentBuilder(XContentType.JSON);
        inputNormal.toXContent(builder, EMPTY_PARAMS);
        String jsonString = TestHelper.xContentBuilderToString(builder);

        assertTrue(jsonString.contains("\"text\":\"Updated memory content\""));
    }

    @Test
    public void testParse() throws IOException {
        String jsonString = "{\"text\":\"Parsed memory text\"}";

        XContentParser parser = XContentType.JSON
            .xContent()
            .createParser(NamedXContentRegistry.EMPTY, LoggingDeprecationHandler.INSTANCE, jsonString);
        parser.nextToken();

        MLUpdateMemoryInput parsed = MLUpdateMemoryInput.parse(parser);

        assertEquals("Parsed memory text", parsed.getUpdateContent().get("text"));
    }

    @Test
    public void testParseWithUnknownFields() throws IOException {
        String jsonString = "{\"text\":\"Valid text\",\"unknown_field\":\"ignored\",\"another\":123}";

        XContentParser parser = XContentType.JSON
            .xContent()
            .createParser(NamedXContentRegistry.EMPTY, LoggingDeprecationHandler.INSTANCE, jsonString);
        parser.nextToken();

        MLUpdateMemoryInput parsed = MLUpdateMemoryInput.parse(parser);

        assertEquals("Valid text", parsed.getUpdateContent().get("text"));
    }

    @Test
    public void testSetter() {
        Map<String, Object> initialContent = new HashMap<>();
        initialContent.put("text", "Initial text");
        MLUpdateMemoryInput input = MLUpdateMemoryInput.builder().updateContent(initialContent).build();

        Map<String, Object> updatedContent = new HashMap<>();
        updatedContent.put("text", "Updated text");
        input.setUpdateContent(updatedContent);

        assertEquals("Updated text", input.getUpdateContent().get("text"));
    }

    @Test
    public void testSpecialCharactersInText() throws IOException {
        Map<String, Object> specialContent = new HashMap<>();
        specialContent.put("text", "Text with\n\ttabs,\nnewlines, \"quotes\", 'single quotes', and unicode 🚀✨");
        MLUpdateMemoryInput specialInput = MLUpdateMemoryInput.builder().updateContent(specialContent).build();

        // Test serialization round trip
        BytesStreamOutput out = new BytesStreamOutput();
        specialInput.writeTo(out);
        StreamInput in = out.bytes().streamInput();
        MLUpdateMemoryInput deserialized = new MLUpdateMemoryInput(in);

        assertEquals(specialInput.getUpdateContent(), deserialized.getUpdateContent());

        // Test XContent
        XContentBuilder builder = MediaTypeRegistry.contentBuilder(XContentType.JSON);
        specialInput.toXContent(builder, EMPTY_PARAMS);
        String jsonString = TestHelper.xContentBuilderToString(builder);

        assertTrue(jsonString.contains("Text with"));
        assertTrue(jsonString.contains("tabs"));
        assertTrue(jsonString.contains("quotes"));
    }

    @Test
    public void testLongText() throws IOException {
        // Test with very long text
        StringBuilder longText = new StringBuilder();
        for (int i = 0; i < 1000; i++) {
            longText.append("This is sentence ").append(i).append(". ");
        }

        Map<String, Object> longContent = new HashMap<>();
        longContent.put("text", longText.toString().trim());
        MLUpdateMemoryInput longInput = MLUpdateMemoryInput.builder().updateContent(longContent).build();

        // Test serialization
        BytesStreamOutput out = new BytesStreamOutput();
        longInput.writeTo(out);
        StreamInput in = out.bytes().streamInput();
        MLUpdateMemoryInput deserialized = new MLUpdateMemoryInput(in);

        assertEquals(longInput.getUpdateContent(), deserialized.getUpdateContent());
    }

    @Test
    public void testXContentRoundTrip() throws IOException {
        // Convert to XContent
        XContentBuilder builder = MediaTypeRegistry.contentBuilder(XContentType.JSON);
        inputNormal.toXContent(builder, EMPTY_PARAMS);
        String jsonString = TestHelper.xContentBuilderToString(builder);

        // Parse back
        XContentParser parser = XContentType.JSON
            .xContent()
            .createParser(NamedXContentRegistry.EMPTY, LoggingDeprecationHandler.INSTANCE, jsonString);
        parser.nextToken();
        MLUpdateMemoryInput parsed = MLUpdateMemoryInput.parse(parser);

        // Verify field matches
        assertEquals(inputNormal.getUpdateContent(), parsed.getUpdateContent());
    }

    @Test
    public void testMultilineText() throws IOException {
        String multilineText = "Line 1\nLine 2\nLine 3\nWith multiple lines";
        Map<String, Object> multilineContent = new HashMap<>();
        multilineContent.put("text", multilineText);
        MLUpdateMemoryInput multilineInput = MLUpdateMemoryInput.builder().updateContent(multilineContent).build();

        assertEquals(multilineText, multilineInput.getUpdateContent().get("text"));

        // Test XContent handling
        XContentBuilder builder = MediaTypeRegistry.contentBuilder(XContentType.JSON);
        multilineInput.toXContent(builder, EMPTY_PARAMS);
        String jsonString = TestHelper.xContentBuilderToString(builder);

        // Parse back
        XContentParser parser = XContentType.JSON
            .xContent()
            .createParser(NamedXContentRegistry.EMPTY, LoggingDeprecationHandler.INSTANCE, jsonString);
        parser.nextToken();
        MLUpdateMemoryInput parsed = MLUpdateMemoryInput.parse(parser);

        assertEquals(multilineText, parsed.getUpdateContent().get("text"));
    }

    @Test
    public void testSingleCharacterText() {
        Map<String, Object> singleCharContent = new HashMap<>();
        singleCharContent.put("text", "A");
        MLUpdateMemoryInput singleChar = MLUpdateMemoryInput.builder().updateContent(singleCharContent).build();
        assertEquals("A", singleChar.getUpdateContent().get("text"));
    }

    @Test
    public void testTextWithOnlySpecialCharacters() {
        String specialOnly = "!@#$%^&*()_+-=[]{}|;':\",./<>?";
        Map<String, Object> specialContent = new HashMap<>();
        specialContent.put("text", specialOnly);
        MLUpdateMemoryInput specialInput = MLUpdateMemoryInput.builder().updateContent(specialContent).build();
        assertEquals(specialOnly, specialInput.getUpdateContent().get("text"));
    }

    @Test
    public void testParseWithEmptyContent() throws IOException {
        // Parse with empty content should throw exception when building
        String jsonString = "{}";

        XContentParser parser = XContentType.JSON
            .xContent()
            .createParser(NamedXContentRegistry.EMPTY, LoggingDeprecationHandler.INSTANCE, jsonString);
        parser.nextToken();

        assertThrows(IllegalArgumentException.class, () -> MLUpdateMemoryInput.parse(parser));
    }

    @Test
    public void testComplexUpdateContent() throws IOException {
        // Test with multiple fields in update content (avoiding nested Maps for XContent compatibility)
        Map<String, Object> complexContent = new HashMap<>();
        complexContent.put("text", "Updated text");
        complexContent.put("metadata", "some metadata value");
        complexContent.put("tags", "important");

        MLUpdateMemoryInput complexInput = MLUpdateMemoryInput.builder().updateContent(complexContent).build();

        // Test serialization
        BytesStreamOutput out = new BytesStreamOutput();
        complexInput.writeTo(out);
        StreamInput in = out.bytes().streamInput();
        MLUpdateMemoryInput deserialized = new MLUpdateMemoryInput(in);

        assertEquals(complexContent, deserialized.getUpdateContent());

        // Test XContent
        XContentBuilder builder = MediaTypeRegistry.contentBuilder(XContentType.JSON);
        complexInput.toXContent(builder, EMPTY_PARAMS);
        String jsonString = TestHelper.xContentBuilderToString(builder);

        assertTrue(jsonString.contains("\"text\":\"Updated text\""));
        assertTrue(jsonString.contains("\"metadata\""));
        assertTrue(jsonString.contains("\"tags\""));
    }
}
