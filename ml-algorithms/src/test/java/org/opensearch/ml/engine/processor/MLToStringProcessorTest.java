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
import java.util.Map;

import org.junit.Test;

public class MLToStringProcessorTest {

    @Test
    public void testProcessSimpleString() {
        Map<String, Object> config = new HashMap<>();
        MLToStringProcessor processor = new MLToStringProcessor(config);

        String input = "hello world";
        Object result = processor.process(input);

        assertEquals("hello world", result);
    }

    @Test
    public void testProcessNumber() {
        Map<String, Object> config = new HashMap<>();
        MLToStringProcessor processor = new MLToStringProcessor(config);

        Object result = processor.process(42);

        assertEquals("42", result);
    }

    @Test
    public void testProcessBoolean() {
        Map<String, Object> config = new HashMap<>();
        MLToStringProcessor processor = new MLToStringProcessor(config);

        Object result = processor.process(true);

        assertEquals("true", result);
    }

    @Test
    public void testProcessMap() {
        Map<String, Object> config = new HashMap<>();
        MLToStringProcessor processor = new MLToStringProcessor(config);

        Map<String, Object> input = new HashMap<>();
        input.put("name", "John");
        input.put("age", 30);

        Object result = processor.process(input);

        assertNotNull(result);
        assertTrue(result instanceof String);
        String jsonStr = (String) result;
        assertTrue(jsonStr.contains("\"name\""));
        assertTrue(jsonStr.contains("\"John\""));
        assertTrue(jsonStr.contains("\"age\""));
        assertTrue(jsonStr.contains("30"));
    }

    @Test
    public void testProcessList() {
        Map<String, Object> config = new HashMap<>();
        MLToStringProcessor processor = new MLToStringProcessor(config);

        Object result = processor.process(Arrays.asList(1, 2, 3));

        assertNotNull(result);
        assertTrue(result instanceof String);
        String jsonStr = (String) result;
        assertTrue(jsonStr.contains("1"));
        assertTrue(jsonStr.contains("2"));
        assertTrue(jsonStr.contains("3"));
    }

    @Test
    public void testProcessNestedObject() {
        Map<String, Object> config = new HashMap<>();
        MLToStringProcessor processor = new MLToStringProcessor(config);

        Map<String, Object> nested = new HashMap<>();
        nested.put("city", "Seattle");
        nested.put("zip", "98101");

        Map<String, Object> input = new HashMap<>();
        input.put("name", "John");
        input.put("address", nested);

        Object result = processor.process(input);

        assertNotNull(result);
        assertTrue(result instanceof String);
        String jsonStr = (String) result;
        assertTrue(jsonStr.contains("\"name\""));
        assertTrue(jsonStr.contains("\"address\""));
        assertTrue(jsonStr.contains("\"city\""));
        assertTrue(jsonStr.contains("\"Seattle\""));
    }

    @Test
    public void testProcessWithEscapeJsonFalse() {
        Map<String, Object> config = new HashMap<>();
        config.put("escape_json", false);
        MLToStringProcessor processor = new MLToStringProcessor(config);

        Map<String, Object> input = new HashMap<>();
        input.put("message", "Hello \"World\"");

        Object result = processor.process(input);

        assertNotNull(result);
        assertTrue(result instanceof String);
        String jsonStr = (String) result;
        // Should contain unescaped quotes
        assertTrue(jsonStr.contains("\""));
    }

    @Test
    public void testProcessWithEscapeJsonTrue() {
        Map<String, Object> config = new HashMap<>();
        config.put("escape_json", true);
        MLToStringProcessor processor = new MLToStringProcessor(config);

        Map<String, Object> input = new HashMap<>();
        input.put("message", "Hello \"World\"");

        Object result = processor.process(input);

        assertNotNull(result);
        assertTrue(result instanceof String);
        String jsonStr = (String) result;
        // Should contain escaped quotes
        assertTrue(jsonStr.contains("\\\""));
    }

    @Test
    public void testProcessNull() {
        Map<String, Object> config = new HashMap<>();
        MLToStringProcessor processor = new MLToStringProcessor(config);

        Object result = processor.process(null);

        assertEquals("null", result);
    }

    @Test
    public void testProcessEmptyMap() {
        Map<String, Object> config = new HashMap<>();
        MLToStringProcessor processor = new MLToStringProcessor(config);

        Object result = processor.process(new HashMap<>());

        assertNotNull(result);
        assertTrue(result instanceof String);
        assertEquals("{}", result);
    }

    @Test
    public void testProcessEmptyList() {
        Map<String, Object> config = new HashMap<>();
        MLToStringProcessor processor = new MLToStringProcessor(config);

        Object result = processor.process(Arrays.asList());

        assertNotNull(result);
        assertTrue(result instanceof String);
        assertEquals("[]", result);
    }

    @Test
    public void testProcessComplexNestedStructure() {
        Map<String, Object> config = new HashMap<>();
        MLToStringProcessor processor = new MLToStringProcessor(config);

        Map<String, Object> user = new HashMap<>();
        user.put("id", 1);
        user.put("name", "John");
        user.put("tags", Arrays.asList("admin", "user"));

        Map<String, Object> profile = new HashMap<>();
        profile.put("age", 30);
        profile.put("active", true);

        user.put("profile", profile);

        Object result = processor.process(user);

        assertNotNull(result);
        assertTrue(result instanceof String);
        String jsonStr = (String) result;
        assertTrue(jsonStr.contains("\"id\""));
        assertTrue(jsonStr.contains("\"name\""));
        assertTrue(jsonStr.contains("\"tags\""));
        assertTrue(jsonStr.contains("\"profile\""));
        assertTrue(jsonStr.contains("\"age\""));
        assertTrue(jsonStr.contains("\"active\""));
    }

    @Test
    public void testProcessWithSpecialCharacters() {
        Map<String, Object> config = new HashMap<>();
        MLToStringProcessor processor = new MLToStringProcessor(config);

        Map<String, Object> input = new HashMap<>();
        input.put("text", "Line1\nLine2\tTabbed");

        Object result = processor.process(input);

        assertNotNull(result);
        assertTrue(result instanceof String);
        // JSON should escape newlines and tabs
        String jsonStr = (String) result;
        assertTrue(jsonStr.contains("\\n") || jsonStr.contains("\\t"));
    }

    @Test
    public void testEscapeJsonWithNewlines() {
        Map<String, Object> config = new HashMap<>();
        config.put("escape_json", true);
        MLToStringProcessor processor = new MLToStringProcessor(config);

        String input = "Line1\nLine2";
        Object result = processor.process(input);

        assertNotNull(result);
        assertTrue(result instanceof String);
        String escaped = (String) result;
        // Should escape the newline
        assertTrue(escaped.contains("\\n"));
    }

    @Test
    public void testDefaultConfigurationNoEscape() {
        Map<String, Object> config = new HashMap<>();
        MLToStringProcessor processor = new MLToStringProcessor(config);

        String input = "test";
        Object result = processor.process(input);

        assertEquals("test", result);
    }
}
