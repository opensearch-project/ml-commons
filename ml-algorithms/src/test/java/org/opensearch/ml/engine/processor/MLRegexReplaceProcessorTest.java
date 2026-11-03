/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.processor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

/**
 * Unit tests for {@link MLRegexReplaceProcessor}
 */
public class MLRegexReplaceProcessorTest {

    @Test
    public void testReplaceSimplePattern() {
        Map<String, Object> config = new HashMap<>();
        config.put("pattern", "world");
        config.put("replacement", "universe");

        MLRegexReplaceProcessor processor = new MLRegexReplaceProcessor(config);

        String input = "hello world";
        Object result = processor.process(input);

        assertEquals("hello universe", result);
    }

    @Test
    public void testReplaceAllOccurrences() {
        Map<String, Object> config = new HashMap<>();
        config.put("pattern", "foo");
        config.put("replacement", "bar");
        config.put("replace_all", true);

        MLRegexReplaceProcessor processor = new MLRegexReplaceProcessor(config);

        String input = "foo foo foo";
        Object result = processor.process(input);

        assertEquals("bar bar bar", result);
    }

    @Test
    public void testReplaceFirstOccurrence() {
        Map<String, Object> config = new HashMap<>();
        config.put("pattern", "foo");
        config.put("replacement", "bar");
        config.put("replace_all", false);

        MLRegexReplaceProcessor processor = new MLRegexReplaceProcessor(config);

        String input = "foo foo foo";
        Object result = processor.process(input);

        assertEquals("bar foo foo", result);
    }

    @Test
    public void testRemovePattern() {
        Map<String, Object> config = new HashMap<>();
        config.put("pattern", "[^a-zA-Z0-9]");
        config.put("replacement", "");

        MLRegexReplaceProcessor processor = new MLRegexReplaceProcessor(config);

        String input = "Hello, World! 123";
        Object result = processor.process(input);

        assertEquals("HelloWorld123", result);
    }

    @Test
    public void testReplaceWithBackreference() {
        Map<String, Object> config = new HashMap<>();
        config.put("pattern", "(\\d{3})-(\\d{3})-(\\d{4})");
        config.put("replacement", "($1) $2-$3");

        MLRegexReplaceProcessor processor = new MLRegexReplaceProcessor(config);

        String input = "Phone: 555-123-4567";
        Object result = processor.process(input);

        assertEquals("Phone: (555) 123-4567", result);
    }

    @Test
    public void testReplaceSpacesWithUnderscore() {
        Map<String, Object> config = new HashMap<>();
        config.put("pattern", "\\s+");
        config.put("replacement", "_");

        MLRegexReplaceProcessor processor = new MLRegexReplaceProcessor(config);

        String input = "hello   world  test";
        Object result = processor.process(input);

        assertEquals("hello_world_test", result);
    }

    @Test
    public void testReplaceDigits() {
        Map<String, Object> config = new HashMap<>();
        config.put("pattern", "\\d+");
        config.put("replacement", "X");

        MLRegexReplaceProcessor processor = new MLRegexReplaceProcessor(config);

        String input = "Order 123 has 456 items";
        Object result = processor.process(input);

        assertEquals("Order X has X items", result);
    }

    @Test
    public void testReplaceEmailDomain() {
        Map<String, Object> config = new HashMap<>();
        config.put("pattern", "@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}");
        config.put("replacement", "@example.com");

        MLRegexReplaceProcessor processor = new MLRegexReplaceProcessor(config);

        String input = "Contact: john@test.org and jane@company.com";
        Object result = processor.process(input);

        assertEquals("Contact: john@example.com and jane@example.com", result);
    }

    @Test
    public void testReplaceWithEmptyString() {
        Map<String, Object> config = new HashMap<>();
        config.put("pattern", "remove");
        config.put("replacement", "");

        MLRegexReplaceProcessor processor = new MLRegexReplaceProcessor(config);

        String input = "keep remove this remove text";
        Object result = processor.process(input);

        assertEquals("keep  this  text", result);
    }

    @Test
    public void testDefaultReplacementIsEmpty() {
        Map<String, Object> config = new HashMap<>();
        config.put("pattern", "test");

        MLRegexReplaceProcessor processor = new MLRegexReplaceProcessor(config);

        String input = "test string";
        Object result = processor.process(input);

        assertEquals(" string", result);
    }

    @Test
    public void testDefaultReplaceAllIsTrue() {
        Map<String, Object> config = new HashMap<>();
        config.put("pattern", "a");
        config.put("replacement", "A");

        MLRegexReplaceProcessor processor = new MLRegexReplaceProcessor(config);

        String input = "banana";
        Object result = processor.process(input);

        assertEquals("bAnAnA", result);
    }

    @Test
    public void testNoMatchReturnsOriginal() {
        Map<String, Object> config = new HashMap<>();
        config.put("pattern", "xyz");
        config.put("replacement", "abc");

        MLRegexReplaceProcessor processor = new MLRegexReplaceProcessor(config);

        String input = "hello world";
        Object result = processor.process(input);

        assertEquals("hello world", result);
    }

    @Test
    public void testReplaceMultilineText() {
        Map<String, Object> config = new HashMap<>();
        config.put("pattern", "\\n");
        config.put("replacement", " ");

        MLRegexReplaceProcessor processor = new MLRegexReplaceProcessor(config);

        String input = "line1\nline2\nline3";
        Object result = processor.process(input);

        assertEquals("line1 line2 line3", result);
    }

    @Test
    public void testReplaceWithDotallFlag() {
        Map<String, Object> config = new HashMap<>();
        config.put("pattern", "<.*>");
        config.put("replacement", "");

        MLRegexReplaceProcessor processor = new MLRegexReplaceProcessor(config);

        String input = "<tag>content\nmore content</tag>";
        Object result = processor.process(input);

        // DOTALL flag makes . match newlines
        assertEquals("", result);
    }

    @Test
    public void testReplaceCaseSensitivePattern() {
        Map<String, Object> config = new HashMap<>();
        config.put("pattern", "[a-z]+");
        config.put("replacement", "X");

        MLRegexReplaceProcessor processor = new MLRegexReplaceProcessor(config);

        String input = "Hello World";
        Object result = processor.process(input);

        // Pattern is case-sensitive by default, only matches lowercase
        assertEquals("HX WX", result);
    }

    @Test
    public void testReplaceSpecialCharacters() {
        Map<String, Object> config = new HashMap<>();
        config.put("pattern", "[!@#$%^&*()]");
        config.put("replacement", "");

        MLRegexReplaceProcessor processor = new MLRegexReplaceProcessor(config);

        String input = "Hello! World@2024#";
        Object result = processor.process(input);

        assertEquals("Hello World2024", result);
    }

    @Test
    public void testReplaceUrlProtocol() {
        Map<String, Object> config = new HashMap<>();
        config.put("pattern", "https?://");
        config.put("replacement", "");

        MLRegexReplaceProcessor processor = new MLRegexReplaceProcessor(config);

        String input = "Visit https://example.com or http://test.org";
        Object result = processor.process(input);

        assertEquals("Visit example.com or test.org", result);
    }

    @Test
    public void testReplaceWithComplexBackreference() {
        Map<String, Object> config = new HashMap<>();
        config.put("pattern", "(\\w+)\\s+(\\w+)");
        config.put("replacement", "$2 $1");

        MLRegexReplaceProcessor processor = new MLRegexReplaceProcessor(config);

        String input = "John Doe";
        Object result = processor.process(input);

        assertEquals("Doe John", result);
    }

    @Test
    public void testReplaceJsonInput() {
        Map<String, Object> config = new HashMap<>();
        config.put("pattern", "\"");
        config.put("replacement", "'");

        MLRegexReplaceProcessor processor = new MLRegexReplaceProcessor(config);

        Map<String, Object> input = new HashMap<>();
        input.put("name", "John");

        Object result = processor.process(input);

        assertNotNull(result);
        assertTrue(result instanceof String);
        String jsonStr = (String) result;
        assertTrue(jsonStr.contains("'name'"));
        assertTrue(jsonStr.contains("'John'"));
    }

    @Test
    public void testReplaceNumberInput() {
        Map<String, Object> config = new HashMap<>();
        config.put("pattern", "4");
        config.put("replacement", "X");

        MLRegexReplaceProcessor processor = new MLRegexReplaceProcessor(config);

        Object result = processor.process(42);

        assertEquals("X2", result);
    }

    @Test
    public void testReplaceEmptyString() {
        Map<String, Object> config = new HashMap<>();
        config.put("pattern", "test");
        config.put("replacement", "replaced");

        MLRegexReplaceProcessor processor = new MLRegexReplaceProcessor(config);

        Object result = processor.process("");

        assertEquals("", result);
    }

    @Test
    public void testReplaceWhitespaceNormalization() {
        Map<String, Object> config = new HashMap<>();
        config.put("pattern", "\\s+");
        config.put("replacement", " ");

        MLRegexReplaceProcessor processor = new MLRegexReplaceProcessor(config);

        String input = "text  with   irregular    spacing";
        Object result = processor.process(input);

        assertEquals("text with irregular spacing", result);
    }

    @Test
    public void testReplaceLeadingTrailingSpaces() {
        Map<String, Object> config = new HashMap<>();
        config.put("pattern", "^\\s+|\\s+$");
        config.put("replacement", "");

        MLRegexReplaceProcessor processor = new MLRegexReplaceProcessor(config);

        String input = "  hello world  ";
        Object result = processor.process(input);

        assertEquals("hello world", result);
    }

    @Test
    public void testReplaceHtmlTags() {
        Map<String, Object> config = new HashMap<>();
        config.put("pattern", "<[^>]+>");
        config.put("replacement", "");

        MLRegexReplaceProcessor processor = new MLRegexReplaceProcessor(config);

        String input = "<p>Hello <b>World</b></p>";
        Object result = processor.process(input);

        assertEquals("Hello World", result);
    }

    @Test
    public void testReplaceSequentially() {
        Map<String, Object> config1 = new HashMap<>();
        config1.put("pattern", "foo");
        config1.put("replacement", "bar");

        Map<String, Object> config2 = new HashMap<>();
        config2.put("pattern", "bar");
        config2.put("replacement", "baz");

        MLRegexReplaceProcessor processor1 = new MLRegexReplaceProcessor(config1);
        MLRegexReplaceProcessor processor2 = new MLRegexReplaceProcessor(config2);

        String input = "foo test";
        Object result = processor1.process(input);
        result = processor2.process(result);

        assertEquals("baz test", result);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testMissingPatternConfig() {
        Map<String, Object> config = new HashMap<>();
        config.put("replacement", "test");

        new MLRegexReplaceProcessor(config);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testEmptyPatternConfig() {
        Map<String, Object> config = new HashMap<>();
        config.put("pattern", "");
        config.put("replacement", "test");

        new MLRegexReplaceProcessor(config);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testWhitespaceOnlyPattern() {
        Map<String, Object> config = new HashMap<>();
        config.put("pattern", "   ");
        config.put("replacement", "test");

        new MLRegexReplaceProcessor(config);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidRegexPattern() {
        Map<String, Object> config = new HashMap<>();
        config.put("pattern", "[invalid");
        config.put("replacement", "test");

        new MLRegexReplaceProcessor(config);
    }

    @Test
    public void testReplaceWithEscapedDollarSign() {
        Map<String, Object> config = new HashMap<>();
        config.put("pattern", "price");
        config.put("replacement", "\\$100");

        MLRegexReplaceProcessor processor = new MLRegexReplaceProcessor(config);

        String input = "The price is high";
        Object result = processor.process(input);

        assertEquals("The $100 is high", result);
    }

    @Test
    public void testReplacePhoneNumberFormat() {
        Map<String, Object> config = new HashMap<>();
        config.put("pattern", "\\(?([0-9]{3})\\)?[-. ]?([0-9]{3})[-. ]?([0-9]{4})");
        config.put("replacement", "$1-$2-$3");

        MLRegexReplaceProcessor processor = new MLRegexReplaceProcessor(config);

        String input = "Call (555) 123-4567 or 555.987.6543";
        Object result = processor.process(input);

        assertEquals("Call 555-123-4567 or 555-987-6543", result);
    }

    @Test
    public void testReplaceMultiplePatterns() {
        Map<String, Object> config = new HashMap<>();
        config.put("pattern", "cat|dog");
        config.put("replacement", "animal");

        MLRegexReplaceProcessor processor = new MLRegexReplaceProcessor(config);

        String input = "I have a cat and a dog";
        Object result = processor.process(input);

        assertEquals("I have a animal and a animal", result);
    }

    @Test
    public void testReplaceWordBoundary() {
        Map<String, Object> config = new HashMap<>();
        config.put("pattern", "\\btest\\b");
        config.put("replacement", "exam");

        MLRegexReplaceProcessor processor = new MLRegexReplaceProcessor(config);

        String input = "test testing tested test";
        Object result = processor.process(input);

        assertEquals("exam testing tested exam", result);
    }

    @Test
    public void testReplaceDateFormat() {
        Map<String, Object> config = new HashMap<>();
        config.put("pattern", "(\\d{4})-(\\d{2})-(\\d{2})");
        config.put("replacement", "$2/$3/$1");

        MLRegexReplaceProcessor processor = new MLRegexReplaceProcessor(config);

        String input = "Date: 2024-03-15";
        Object result = processor.process(input);

        assertEquals("Date: 03/15/2024", result);
    }

    @Test
    public void testReplaceOnlyFirstWithMultipleMatches() {
        Map<String, Object> config = new HashMap<>();
        config.put("pattern", "\\d+");
        config.put("replacement", "NUM");
        config.put("replace_all", false);

        MLRegexReplaceProcessor processor = new MLRegexReplaceProcessor(config);

        String input = "123 and 456 and 789";
        Object result = processor.process(input);

        assertEquals("NUM and 456 and 789", result);
    }

    @Test
    public void testReplaceWithNullInput() {
        Map<String, Object> config = new HashMap<>();
        config.put("pattern", "test");
        config.put("replacement", "replaced");

        MLRegexReplaceProcessor processor = new MLRegexReplaceProcessor(config);

        Object result = processor.process(null);

        assertEquals("null", result);
    }
}
