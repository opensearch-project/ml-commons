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
        inputNormal = MLUpdateMemoryInput.builder().text("Updated memory content").build();

        inputWithWhitespace = MLUpdateMemoryInput.builder().text("  Text with surrounding spaces  ").build();
    }

    @Test
    public void testBuilderNormal() {
        assertNotNull(inputNormal);
        assertEquals("Updated memory content", inputNormal.getText());
    }

    @Test
    public void testBuilderWithWhitespace() {
        assertNotNull(inputWithWhitespace);
        // Should be trimmed
        assertEquals("Text with surrounding spaces", inputWithWhitespace.getText());
    }

    @Test
    public void testConstructor() {
        MLUpdateMemoryInput input = new MLUpdateMemoryInput("Test text");
        assertEquals("Test text", input.getText());
    }

    @Test
    public void testConstructorWithTrimming() {
        MLUpdateMemoryInput input = new MLUpdateMemoryInput("  Trimmed text  ");
        assertEquals("Trimmed text", input.getText());
    }

    @Test
    public void testConstructorWithNullText() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> new MLUpdateMemoryInput((String) null));
        assertEquals("Text cannot be null or empty", exception.getMessage());
    }

    @Test
    public void testConstructorWithEmptyText() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> new MLUpdateMemoryInput(""));
        assertEquals("Text cannot be null or empty", exception.getMessage());
    }

    @Test
    public void testConstructorWithWhitespaceOnlyText() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> new MLUpdateMemoryInput("   "));
        assertEquals("Text cannot be null or empty", exception.getMessage());
    }

    @Test
    public void testStreamInputOutput() throws IOException {
        BytesStreamOutput out = new BytesStreamOutput();
        inputNormal.writeTo(out);
        StreamInput in = out.bytes().streamInput();
        MLUpdateMemoryInput deserialized = new MLUpdateMemoryInput(in);

        assertEquals(inputNormal.getText(), deserialized.getText());
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

        assertEquals("Parsed memory text", parsed.getText());
    }

    @Test
    public void testParseWithUnknownFields() throws IOException {
        String jsonString = "{\"text\":\"Valid text\",\"unknown_field\":\"ignored\",\"another\":123}";

        XContentParser parser = XContentType.JSON
            .xContent()
            .createParser(NamedXContentRegistry.EMPTY, LoggingDeprecationHandler.INSTANCE, jsonString);
        parser.nextToken();

        MLUpdateMemoryInput parsed = MLUpdateMemoryInput.parse(parser);

        assertEquals("Valid text", parsed.getText());
    }

    @Test
    public void testSetter() {
        MLUpdateMemoryInput input = new MLUpdateMemoryInput("Initial text");
        input.setText("Updated text");
        assertEquals("Updated text", input.getText());
    }

    @Test
    public void testSpecialCharactersInText() throws IOException {
        MLUpdateMemoryInput specialInput = new MLUpdateMemoryInput(
            "Text with\n\ttabs,\nnewlines, \"quotes\", 'single quotes', and unicode 🚀✨"
        );

        // Test serialization round trip
        BytesStreamOutput out = new BytesStreamOutput();
        specialInput.writeTo(out);
        StreamInput in = out.bytes().streamInput();
        MLUpdateMemoryInput deserialized = new MLUpdateMemoryInput(in);

        assertEquals(specialInput.getText(), deserialized.getText());

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

        MLUpdateMemoryInput longInput = new MLUpdateMemoryInput(longText.toString().trim());

        // Test serialization
        BytesStreamOutput out = new BytesStreamOutput();
        longInput.writeTo(out);
        StreamInput in = out.bytes().streamInput();
        MLUpdateMemoryInput deserialized = new MLUpdateMemoryInput(in);

        assertEquals(longInput.getText(), deserialized.getText());
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
        assertEquals(inputNormal.getText(), parsed.getText());
    }

    @Test
    public void testMultilineText() throws IOException {
        String multilineText = "Line 1\nLine 2\nLine 3\nWith multiple lines";
        MLUpdateMemoryInput multilineInput = new MLUpdateMemoryInput(multilineText);

        assertEquals(multilineText, multilineInput.getText());

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

        assertEquals(multilineText, parsed.getText());
    }

    @Test
    public void testSingleCharacterText() {
        MLUpdateMemoryInput singleChar = new MLUpdateMemoryInput("A");
        assertEquals("A", singleChar.getText());
    }

    @Test
    public void testTextWithOnlySpecialCharacters() {
        String specialOnly = "!@#$%^&*()_+-=[]{}|;':\",./<>?";
        MLUpdateMemoryInput specialInput = new MLUpdateMemoryInput(specialOnly);
        assertEquals(specialOnly, specialInput.getText());
    }

    @Test
    public void testParseWithMissingTextField() throws IOException {
        // Parse with missing text field should throw exception when building
        String jsonString = "{\"other_field\":\"value\"}";

        XContentParser parser = XContentType.JSON
            .xContent()
            .createParser(NamedXContentRegistry.EMPTY, LoggingDeprecationHandler.INSTANCE, jsonString);
        parser.nextToken();

        assertThrows(IllegalArgumentException.class, () -> MLUpdateMemoryInput.parse(parser));
    }

    @Test
    public void testSimpleJsonStructure() throws IOException {
        // Verify the JSON structure is simple: just {"text": "..."}
        XContentBuilder builder = MediaTypeRegistry.contentBuilder(XContentType.JSON);
        inputNormal.toXContent(builder, EMPTY_PARAMS);
        String jsonString = TestHelper.xContentBuilderToString(builder);

        // Should be a simple object with just one field
        assertTrue(jsonString.startsWith("{\"text\":"));
        assertTrue(jsonString.endsWith("\"}"));
        assertEquals(1, jsonString.split("\":").length - 1); // Only one field
    }
}
