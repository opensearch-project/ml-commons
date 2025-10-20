/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.processor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;

/**
 * Unit tests for {@link MLRegexCaptureProcessor}
 */
public class MLRegexCaptureProcessorTest {

    @Test
    public void testCaptureFirstGroup() {
        Map<String, Object> config = new HashMap<>();
        config.put("pattern", "([a-zA-Z0-9._%+-]+)@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}");
        config.put("groups", "1");

        MLRegexCaptureProcessor processor = new MLRegexCaptureProcessor(config);

        String input = "Contact: john.doe@example.com";
        Object result = processor.process(input);

        assertEquals("john.doe", result);
    }

    @Test
    public void testCaptureDefaultGroup() {
        Map<String, Object> config = new HashMap<>();
        config.put("pattern", "([a-zA-Z]+)@([a-zA-Z]+)\\.com");

        MLRegexCaptureProcessor processor = new MLRegexCaptureProcessor(config);

        String input = "Email: john@example.com";
        Object result = processor.process(input);

        // Default is group 1
        assertEquals("john", result);
    }

    @Test
    public void testCaptureMultipleGroups() {
        Map<String, Object> config = new HashMap<>();
        config.put("pattern", "([a-zA-Z]+)@([a-zA-Z]+)\\.([a-zA-Z]{2,})");
        config.put("groups", Arrays.asList(1, 2, 3));

        MLRegexCaptureProcessor processor = new MLRegexCaptureProcessor(config);

        String input = "Email: john@example.com";
        Object result = processor.process(input);

        assertNotNull(result);
        assertTrue(result instanceof List);
        List<?> captures = (List<?>) result;
        assertEquals(3, captures.size());
        assertEquals("john", captures.get(0));
        assertEquals("example", captures.get(1));
        assertEquals("com", captures.get(2));
    }

    @Test
    public void testCaptureEntireMatch() {
        Map<String, Object> config = new HashMap<>();
        config.put("pattern", "\\d{3}-\\d{3}-\\d{4}");
        config.put("groups", "0");

        MLRegexCaptureProcessor processor = new MLRegexCaptureProcessor(config);

        String input = "Call me at 555-123-4567";
        Object result = processor.process(input);

        assertEquals("555-123-4567", result);
    }

    @Test
    public void testCaptureDateComponents() {
        Map<String, Object> config = new HashMap<>();
        config.put("pattern", "(\\d{4})-(\\d{2})-(\\d{2})");
        config.put("groups", Arrays.asList(1, 2, 3));

        MLRegexCaptureProcessor processor = new MLRegexCaptureProcessor(config);

        String input = "Date: 2024-03-15";
        Object result = processor.process(input);

        assertNotNull(result);
        assertTrue(result instanceof List);
        List<?> captures = (List<?>) result;
        assertEquals(3, captures.size());
        assertEquals("2024", captures.get(0));
        assertEquals("03", captures.get(1));
        assertEquals("15", captures.get(2));
    }

    @Test
    public void testCapturePhoneNumber() {
        Map<String, Object> config = new HashMap<>();
        config.put("pattern", "\\(?([0-9]{3})\\)?[-. ]?([0-9]{3})[-. ]?([0-9]{4})");
        config.put("groups", Arrays.asList(1, 2, 3));

        MLRegexCaptureProcessor processor = new MLRegexCaptureProcessor(config);

        String input = "Phone: (555) 123-4567";
        Object result = processor.process(input);

        assertNotNull(result);
        assertTrue(result instanceof List);
        List<?> captures = (List<?>) result;
        assertEquals(3, captures.size());
        assertEquals("555", captures.get(0));
        assertEquals("123", captures.get(1));
        assertEquals("4567", captures.get(2));
    }

    @Test
    public void testCaptureUrl() {
        Map<String, Object> config = new HashMap<>();
        config.put("pattern", "(https?)://([^/]+)(/.*)?");
        config.put("groups", Arrays.asList(1, 2));

        MLRegexCaptureProcessor processor = new MLRegexCaptureProcessor(config);

        String input = "Visit https://example.com/path";
        Object result = processor.process(input);

        assertNotNull(result);
        assertTrue(result instanceof List);
        List<?> captures = (List<?>) result;
        assertEquals(2, captures.size());
        assertEquals("https", captures.get(0));
        assertEquals("example.com", captures.get(1));
    }

    @Test
    public void testCaptureWithArrayNotation() {
        Map<String, Object> config = new HashMap<>();
        config.put("pattern", "(\\w+)\\s+(\\w+)");
        config.put("groups", "[1,2]");

        MLRegexCaptureProcessor processor = new MLRegexCaptureProcessor(config);

        String input = "John Doe";
        Object result = processor.process(input);

        assertNotNull(result);
        assertTrue(result instanceof List);
        List<?> captures = (List<?>) result;
        assertEquals(2, captures.size());
        assertEquals("John", captures.get(0));
        assertEquals("Doe", captures.get(1));
    }

    @Test
    public void testCaptureNoMatch() {
        Map<String, Object> config = new HashMap<>();
        config.put("pattern", "\\d+");
        config.put("groups", "1");

        MLRegexCaptureProcessor processor = new MLRegexCaptureProcessor(config);

        String input = "No numbers here";
        Object result = processor.process(input);

        // Should return original input when no match
        assertEquals("No numbers here", result);
    }

    @Test
    public void testCaptureInvalidGroupIndex() {
        Map<String, Object> config = new HashMap<>();
        config.put("pattern", "(\\w+)");
        config.put("groups", Arrays.asList(1, 5)); // Group 5 doesn't exist

        MLRegexCaptureProcessor processor = new MLRegexCaptureProcessor(config);

        String input = "test";
        Object result = processor.process(input);

        // Should only capture valid group
        assertEquals("test", result);
    }

    @Test
    public void testCaptureMultipleGroupsWithInvalidIndex() {
        Map<String, Object> config = new HashMap<>();
        config.put("pattern", "(\\w+)\\s+(\\w+)");
        config.put("groups", Arrays.asList(1, 2, 10)); // Group 10 doesn't exist

        MLRegexCaptureProcessor processor = new MLRegexCaptureProcessor(config);

        String input = "Hello World";
        Object result = processor.process(input);

        assertNotNull(result);
        assertTrue(result instanceof List);
        List<?> captures = (List<?>) result;
        assertEquals(2, captures.size());
        assertEquals("Hello", captures.get(0));
        assertEquals("World", captures.get(1));
    }

    @Test
    public void testCaptureFromJsonInput() {
        Map<String, Object> config = new HashMap<>();
        config.put("pattern", "\"(\\w+)\"");
        config.put("groups", "1");

        MLRegexCaptureProcessor processor = new MLRegexCaptureProcessor(config);

        Map<String, Object> input = new HashMap<>();
        input.put("name", "John");

        Object result = processor.process(input);

        // Should capture field name from JSON
        assertEquals("name", result);
    }

    @Test
    public void testCaptureMultilineText() {
        Map<String, Object> config = new HashMap<>();
        config.put("pattern", "<code>(.*)</code>");
        config.put("groups", "1");

        MLRegexCaptureProcessor processor = new MLRegexCaptureProcessor(config);

        String input = "<code>line1\nline2</code>";
        Object result = processor.process(input);

        // DOTALL flag should make . match newlines
        assertEquals("line1\nline2", result);
    }

    @Test
    public void testCaptureIpAddress() {
        Map<String, Object> config = new HashMap<>();
        config.put("pattern", "(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})");
        config.put("groups", Arrays.asList(1, 2, 3, 4));

        MLRegexCaptureProcessor processor = new MLRegexCaptureProcessor(config);

        String input = "Server IP: 192.168.1.100";
        Object result = processor.process(input);

        assertNotNull(result);
        assertTrue(result instanceof List);
        List<?> captures = (List<?>) result;
        assertEquals(4, captures.size());
        assertEquals("192", captures.get(0));
        assertEquals("168", captures.get(1));
        assertEquals("1", captures.get(2));
        assertEquals("100", captures.get(3));
    }

    @Test
    public void testCaptureFirstMatchOnly() {
        Map<String, Object> config = new HashMap<>();
        config.put("pattern", "(\\d+)");
        config.put("groups", "1");

        MLRegexCaptureProcessor processor = new MLRegexCaptureProcessor(config);

        String input = "Numbers: 123, 456, 789";
        Object result = processor.process(input);

        // Should only capture first match
        assertEquals("123", result);
    }

    @Test
    public void testCaptureWithNestedGroups() {
        Map<String, Object> config = new HashMap<>();
        config.put("pattern", "((\\w+)@(\\w+))");
        config.put("groups", Arrays.asList(1, 2, 3));

        MLRegexCaptureProcessor processor = new MLRegexCaptureProcessor(config);

        String input = "Email: john@example";
        Object result = processor.process(input);

        assertNotNull(result);
        assertTrue(result instanceof List);
        List<?> captures = (List<?>) result;
        assertEquals(3, captures.size());
        assertEquals("john@example", captures.get(0));
        assertEquals("john", captures.get(1));
        assertEquals("example", captures.get(2));
    }

    @Test
    public void testCaptureEmptyString() {
        Map<String, Object> config = new HashMap<>();
        config.put("pattern", "(\\w+)");
        config.put("groups", "1");

        MLRegexCaptureProcessor processor = new MLRegexCaptureProcessor(config);

        Object result = processor.process("");

        // No match in empty string
        assertEquals("", result);
    }

    @Test
    public void testCaptureNumberInput() {
        Map<String, Object> config = new HashMap<>();
        config.put("pattern", "(\\d)(\\d)");
        config.put("groups", Arrays.asList(1, 2));

        MLRegexCaptureProcessor processor = new MLRegexCaptureProcessor(config);

        Object result = processor.process(42);

        assertNotNull(result);
        assertTrue(result instanceof List);
        List<?> captures = (List<?>) result;
        assertEquals(2, captures.size());
        assertEquals("4", captures.get(0));
        assertEquals("2", captures.get(1));
    }

    @Test
    public void testCaptureWithSpacesInArrayNotation() {
        Map<String, Object> config = new HashMap<>();
        config.put("pattern", "(\\w+)\\s+(\\w+)");
        config.put("groups", "[ 1 , 2 ]");

        MLRegexCaptureProcessor processor = new MLRegexCaptureProcessor(config);

        String input = "Hello World";
        Object result = processor.process(input);

        assertNotNull(result);
        assertTrue(result instanceof List);
        List<?> captures = (List<?>) result;
        assertEquals(2, captures.size());
        assertEquals("Hello", captures.get(0));
        assertEquals("World", captures.get(1));
    }

    @Test
    public void testCaptureGroup0AndGroup1() {
        Map<String, Object> config = new HashMap<>();
        config.put("pattern", "hello (\\w+)");
        config.put("groups", Arrays.asList(0, 1));

        MLRegexCaptureProcessor processor = new MLRegexCaptureProcessor(config);

        String input = "Say hello world";
        Object result = processor.process(input);

        assertNotNull(result);
        assertTrue(result instanceof List);
        List<?> captures = (List<?>) result;
        assertEquals(2, captures.size());
        assertEquals("hello world", captures.get(0)); // Group 0 is entire match
        assertEquals("world", captures.get(1)); // Group 1 is capture group
    }

    @Test
    public void testCaptureSingleGroupReturnString() {
        Map<String, Object> config = new HashMap<>();
        config.put("pattern", "test (\\w+)");
        config.put("groups", "1");

        MLRegexCaptureProcessor processor = new MLRegexCaptureProcessor(config);

        String input = "This is test data";
        Object result = processor.process(input);

        // Single capture should return string, not list
        assertTrue(result instanceof String);
        assertEquals("data", result);
    }

    @Test
    public void testCaptureMultipleGroupsReturnList() {
        Map<String, Object> config = new HashMap<>();
        config.put("pattern", "(\\w+) (\\w+)");
        config.put("groups", Arrays.asList(1, 2));

        MLRegexCaptureProcessor processor = new MLRegexCaptureProcessor(config);

        String input = "Hello World";
        Object result = processor.process(input);

        // Multiple captures should return list
        assertTrue(result instanceof List);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testMissingPatternConfig() {
        Map<String, Object> config = new HashMap<>();
        config.put("groups", "1");

        new MLRegexCaptureProcessor(config);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testEmptyPatternConfig() {
        Map<String, Object> config = new HashMap<>();
        config.put("pattern", "");
        config.put("groups", "1");

        new MLRegexCaptureProcessor(config);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testWhitespaceOnlyPattern() {
        Map<String, Object> config = new HashMap<>();
        config.put("pattern", "   ");
        config.put("groups", "1");

        new MLRegexCaptureProcessor(config);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidRegexPattern() {
        Map<String, Object> config = new HashMap<>();
        config.put("pattern", "[invalid");
        config.put("groups", "1");

        new MLRegexCaptureProcessor(config);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidGroupsFormat() {
        Map<String, Object> config = new HashMap<>();
        config.put("pattern", "(\\w+)");
        config.put("groups", "not_a_number");

        new MLRegexCaptureProcessor(config);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testEmptyGroupsArray() {
        Map<String, Object> config = new HashMap<>();
        config.put("pattern", "(\\w+)");
        config.put("groups", "[]");

        new MLRegexCaptureProcessor(config);
    }

    @Test
    public void testCaptureWithNegativeGroupIndex() {
        Map<String, Object> config = new HashMap<>();
        config.put("pattern", "(\\w+)");
        config.put("groups", Arrays.asList(-1, 1));

        MLRegexCaptureProcessor processor = new MLRegexCaptureProcessor(config);

        String input = "test";
        Object result = processor.process(input);

        // Should skip negative index and only capture valid group
        assertEquals("test", result);
    }

    @Test
    public void testCaptureAllValidGroupsWhenSomeInvalid() {
        Map<String, Object> config = new HashMap<>();
        config.put("pattern", "(\\w+)\\s+(\\w+)\\s+(\\w+)");
        config.put("groups", Arrays.asList(1, 10, 2, -5, 3));

        MLRegexCaptureProcessor processor = new MLRegexCaptureProcessor(config);

        String input = "one two three";
        Object result = processor.process(input);

        assertNotNull(result);
        assertTrue(result instanceof List);
        List<?> captures = (List<?>) result;
        assertEquals(3, captures.size());
        assertEquals("one", captures.get(0));
        assertEquals("two", captures.get(1));
        assertEquals("three", captures.get(2));
    }

    @Test
    public void testCaptureWithListGroups() {
        Map<String, Object> config = new HashMap<>();
        config.put("pattern", "(\\w+)\\s+(\\w+)");
        config.put("groups", Arrays.asList(1, 2));

        MLRegexCaptureProcessor processor = new MLRegexCaptureProcessor(config);

        String input = "Hello World";
        Object result = processor.process(input);

        assertNotNull(result);
        assertTrue(result instanceof List);
        List<?> captures = (List<?>) result;
        assertEquals(2, captures.size());
    }

    @Test
    public void testCaptureSequentially() {
        Map<String, Object> config1 = new HashMap<>();
        config1.put("pattern", "Email: ([\\w.]+@[\\w.]+)");
        config1.put("groups", "1");

        Map<String, Object> config2 = new HashMap<>();
        config2.put("pattern", "([\\w.]+)@");
        config2.put("groups", "1");

        MLRegexCaptureProcessor processor1 = new MLRegexCaptureProcessor(config1);
        MLRegexCaptureProcessor processor2 = new MLRegexCaptureProcessor(config2);

        String input = "Email: john.doe@example.com";
        Object result = processor1.process(input);
        result = processor2.process(result);

        assertEquals("john.doe", result);
    }

    @Test
    public void testCaptureWithNullInput() {
        Map<String, Object> config = new HashMap<>();
        config.put("pattern", "(\\w+)");
        config.put("groups", "1");

        MLRegexCaptureProcessor processor = new MLRegexCaptureProcessor(config);

        Object result = processor.process(null);

        // Should return original input (null converted to "null")
        assertEquals("null", result);
    }
}
