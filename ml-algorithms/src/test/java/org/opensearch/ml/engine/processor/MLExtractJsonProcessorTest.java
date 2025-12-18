/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.processor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;

public class MLExtractJsonProcessorTest {

    @Test
    public void testExtractJsonObjectAuto() {
        Map<String, Object> config = new HashMap<>();

        MLExtractJsonProcessor processor = new MLExtractJsonProcessor(config);

        String input = "Here is the data: {\"name\": \"John\", \"age\": 30}";
        Object result = processor.process(input);

        assertNotNull(result);
        assertTrue(result instanceof Map);
        Map<String, Object> resultMap = (Map<String, Object>) result;
        assertEquals("John", resultMap.get("name"));
        assertEquals(30, resultMap.get("age"));
    }

    @Test
    public void testExtractJsonArrayAuto() {
        Map<String, Object> config = new HashMap<>();

        MLExtractJsonProcessor processor = new MLExtractJsonProcessor(config);

        String input = "Results: [{\"id\": 1}, {\"id\": 2}] - processed successfully";
        Object result = processor.process(input);

        assertNotNull(result);
        assertTrue(result instanceof List);
        List<?> resultList = (List<?>) result;
        assertEquals(2, resultList.size());
    }

    @Test
    public void testExtractJsonObjectExplicit() {
        Map<String, Object> config = new HashMap<>();
        config.put("extract_type", "object");

        MLExtractJsonProcessor processor = new MLExtractJsonProcessor(config);

        String input = "Data: {\"key\": \"value\"}";
        Object result = processor.process(input);

        assertNotNull(result);
        assertTrue(result instanceof Map);
        Map<String, Object> resultMap = (Map<String, Object>) result;
        assertEquals("value", resultMap.get("key"));
    }

    @Test
    public void testExtractJsonArrayExplicit() {
        Map<String, Object> config = new HashMap<>();
        config.put("extract_type", "array");

        MLExtractJsonProcessor processor = new MLExtractJsonProcessor(config);

        String input = "Items: [1, 2, 3]";
        Object result = processor.process(input);

        assertNotNull(result);
        assertTrue(result instanceof List);
        List<?> resultList = (List<?>) result;
        assertEquals(3, resultList.size());
    }

    @Test
    public void testExtractJsonFromLLMResponse() {
        Map<String, Object> config = new HashMap<>();

        MLExtractJsonProcessor processor = new MLExtractJsonProcessor(config);

        String input = "Sure, here's the JSON you requested:\n{\"status\": \"success\", \"data\": {\"value\": 42}}";
        Object result = processor.process(input);

        assertNotNull(result);
        assertTrue(result instanceof Map);
        Map<String, Object> resultMap = (Map<String, Object>) result;
        assertEquals("success", resultMap.get("status"));
    }

    @Test
    public void testExtractJsonWithNestedObjects() {
        Map<String, Object> config = new HashMap<>();

        MLExtractJsonProcessor processor = new MLExtractJsonProcessor(config);

        String input = "Response: {\"user\": {\"name\": \"John\", \"profile\": {\"age\": 30}}}";
        Object result = processor.process(input);

        assertNotNull(result);
        assertTrue(result instanceof Map);
        Map<String, Object> resultMap = (Map<String, Object>) result;
        assertTrue(resultMap.get("user") instanceof Map);
    }

    @Test
    public void testExtractJsonWithArrayOfObjects() {
        Map<String, Object> config = new HashMap<>();

        MLExtractJsonProcessor processor = new MLExtractJsonProcessor(config);

        String input = "Data: [{\"id\": 1, \"name\": \"A\"}, {\"id\": 2, \"name\": \"B\"}]";
        Object result = processor.process(input);

        assertNotNull(result);
        assertTrue(result instanceof List);
        List<?> resultList = (List<?>) result;
        assertEquals(2, resultList.size());
        assertTrue(resultList.get(0) instanceof Map);
    }

    @Test
    public void testExtractJsonObjectWhenArrayExpected() {
        Map<String, Object> config = new HashMap<>();
        config.put("extract_type", "array");

        MLExtractJsonProcessor processor = new MLExtractJsonProcessor(config);

        String input = "Data: {\"key\": \"value\"}";
        Object result = processor.process(input);

        // Should return original input when wrong type found
        assertEquals(input, result);
    }

    @Test
    public void testExtractJsonArrayWhenObjectExpected() {
        Map<String, Object> config = new HashMap<>();
        config.put("extract_type", "object");

        MLExtractJsonProcessor processor = new MLExtractJsonProcessor(config);

        String input = "Data: [1, 2, 3]";
        Object result = processor.process(input);

        // Should return original input when wrong type found
        assertEquals(input, result);
    }

    @Test
    public void testExtractJsonWithDefault() {
        Map<String, Object> config = new HashMap<>();
        config.put("default", "no_json_found");

        MLExtractJsonProcessor processor = new MLExtractJsonProcessor(config);

        String input = "No JSON here";
        Object result = processor.process(input);

        assertEquals("no_json_found", result);
    }

    @Test
    public void testExtractJsonWithoutDefault() {
        Map<String, Object> config = new HashMap<>();

        MLExtractJsonProcessor processor = new MLExtractJsonProcessor(config);

        String input = "No JSON here";
        Object result = processor.process(input);

        // Should return original input when no JSON found
        assertEquals(input, result);
    }

    @Test
    public void testExtractJsonAtBeginning() {
        Map<String, Object> config = new HashMap<>();

        MLExtractJsonProcessor processor = new MLExtractJsonProcessor(config);

        String input = "{\"first\": true} followed by text";
        Object result = processor.process(input);

        assertNotNull(result);
        assertTrue(result instanceof Map);
        Map<String, Object> resultMap = (Map<String, Object>) result;
        assertEquals(true, resultMap.get("first"));
    }

    @Test
    public void testExtractJsonAtEnd() {
        Map<String, Object> config = new HashMap<>();

        MLExtractJsonProcessor processor = new MLExtractJsonProcessor(config);

        String input = "Text before {\"last\": true}";
        Object result = processor.process(input);

        assertNotNull(result);
        assertTrue(result instanceof Map);
        Map<String, Object> resultMap = (Map<String, Object>) result;
        assertEquals(true, resultMap.get("last"));
    }

    @Test
    public void testExtractJsonWithMultipleJsonBlocks() {
        Map<String, Object> config = new HashMap<>();

        MLExtractJsonProcessor processor = new MLExtractJsonProcessor(config);

        String input = "First: {\"a\": 1} Second: {\"b\": 2}";
        Object result = processor.process(input);

        // Should extract first JSON found
        assertNotNull(result);
        assertTrue(result instanceof Map);
        Map<String, Object> resultMap = (Map<String, Object>) result;
        assertEquals(1, resultMap.get("a"));
    }

    @Test
    public void testExtractJsonWithSpecialCharacters() {
        Map<String, Object> config = new HashMap<>();

        MLExtractJsonProcessor processor = new MLExtractJsonProcessor(config);

        String input = "Data: {\"message\": \"Hello\\nWorld\", \"path\": \"C:\\\\Users\"}";
        Object result = processor.process(input);

        assertNotNull(result);
        assertTrue(result instanceof Map);
        Map<String, Object> resultMap = (Map<String, Object>) result;
        assertTrue(resultMap.containsKey("message"));
        assertTrue(resultMap.containsKey("path"));
    }

    @Test
    public void testExtractJsonWithNumbers() {
        Map<String, Object> config = new HashMap<>();

        MLExtractJsonProcessor processor = new MLExtractJsonProcessor(config);

        String input = "Stats: {\"count\": 42, \"average\": 3.14, \"negative\": -5}";
        Object result = processor.process(input);

        assertNotNull(result);
        assertTrue(result instanceof Map);
        Map<String, Object> resultMap = (Map<String, Object>) result;
        assertEquals(42, resultMap.get("count"));
        assertTrue(resultMap.get("average") instanceof Number);
        assertEquals(-5, resultMap.get("negative"));
    }

    @Test
    public void testExtractJsonWithBooleans() {
        Map<String, Object> config = new HashMap<>();

        MLExtractJsonProcessor processor = new MLExtractJsonProcessor(config);

        String input = "Flags: {\"active\": true, \"verified\": false}";
        Object result = processor.process(input);

        assertNotNull(result);
        assertTrue(result instanceof Map);
        Map<String, Object> resultMap = (Map<String, Object>) result;
        assertEquals(true, resultMap.get("active"));
        assertEquals(false, resultMap.get("verified"));
    }

    @Test
    public void testExtractJsonWithNullValues() {
        Map<String, Object> config = new HashMap<>();

        MLExtractJsonProcessor processor = new MLExtractJsonProcessor(config);

        String input = "Data: {\"value\": null, \"other\": \"text\"}";
        Object result = processor.process(input);

        assertNotNull(result);
        assertTrue(result instanceof Map);
        Map<String, Object> resultMap = (Map<String, Object>) result;
        assertEquals(null, resultMap.get("value"));
        assertEquals("text", resultMap.get("other"));
    }

    @Test
    public void testExtractJsonWithEmptyObject() {
        Map<String, Object> config = new HashMap<>();

        MLExtractJsonProcessor processor = new MLExtractJsonProcessor(config);

        String input = "Empty: {}";
        Object result = processor.process(input);

        assertNotNull(result);
        assertTrue(result instanceof Map);
        Map<String, Object> resultMap = (Map<String, Object>) result;
        assertEquals(0, resultMap.size());
    }

    @Test
    public void testExtractJsonWithEmptyArray() {
        Map<String, Object> config = new HashMap<>();

        MLExtractJsonProcessor processor = new MLExtractJsonProcessor(config);

        String input = "Empty: []";
        Object result = processor.process(input);

        assertNotNull(result);
        assertTrue(result instanceof List);
        List<?> resultList = (List<?>) result;
        assertEquals(0, resultList.size());
    }

    @Test
    public void testExtractJsonArrayBeforeObject() {
        Map<String, Object> config = new HashMap<>();

        MLExtractJsonProcessor processor = new MLExtractJsonProcessor(config);

        String input = "Array: [1, 2] Object: {\"key\": \"value\"}";
        Object result = processor.process(input);

        // Should extract first JSON (array)
        assertNotNull(result);
        assertTrue(result instanceof List);
    }

    @Test
    public void testExtractJsonObjectBeforeArray() {
        Map<String, Object> config = new HashMap<>();

        MLExtractJsonProcessor processor = new MLExtractJsonProcessor(config);

        String input = "Object: {\"key\": \"value\"} Array: [1, 2]";
        Object result = processor.process(input);

        // Should extract first JSON (object)
        assertNotNull(result);
        assertTrue(result instanceof Map);
    }

    @Test
    public void testExtractJsonFromNonStringInput() {
        Map<String, Object> config = new HashMap<>();

        MLExtractJsonProcessor processor = new MLExtractJsonProcessor(config);

        Map<String, Object> input = new HashMap<>();
        input.put("key", "value");

        Object result = processor.process(input);

        // Should convert to JSON string and extract
        assertNotNull(result);
        assertTrue(result instanceof Map);
    }

    @Test
    public void testExtractJsonWithInvalidJson() {
        Map<String, Object> config = new HashMap<>();
        config.put("default", "invalid");

        MLExtractJsonProcessor processor = new MLExtractJsonProcessor(config);

        String input = "Invalid: {broken json}";
        Object result = processor.process(input);

        // Should return default on parse error
        assertEquals("invalid", result);
    }

    @Test
    public void testExtractJsonWithMalformedJson() {
        Map<String, Object> config = new HashMap<>();

        MLExtractJsonProcessor processor = new MLExtractJsonProcessor(config);

        String input = "Malformed: {\"key\": }";
        Object result = processor.process(input);

        // Should return original input on parse error
        assertEquals(input, result);
    }

    @Test
    public void testExtractJsonSequentially() {
        Map<String, Object> config1 = new HashMap<>();

        Map<String, Object> config2 = new HashMap<>();
        config2.put("extract_type", "object");

        MLExtractJsonProcessor processor1 = new MLExtractJsonProcessor(config1);
        MLExtractJsonProcessor processor2 = new MLExtractJsonProcessor(config2);

        String input = "Outer: {\"inner\": \"{\\\"nested\\\": \\\"value\\\"}\"}";
        Object result = processor1.process(input);

        // First extraction gets the outer object
        assertNotNull(result);
        assertTrue(result instanceof Map);
    }

    @Test
    public void testExtractJsonWithWhitespace() {
        Map<String, Object> config = new HashMap<>();

        MLExtractJsonProcessor processor = new MLExtractJsonProcessor(config);

        String input = "Data:   {  \"key\"  :  \"value\"  }  ";
        Object result = processor.process(input);

        assertNotNull(result);
        assertTrue(result instanceof Map);
        Map<String, Object> resultMap = (Map<String, Object>) result;
        assertEquals("value", resultMap.get("key"));
    }

    @Test
    public void testExtractJsonWithNewlines() {
        Map<String, Object> config = new HashMap<>();

        MLExtractJsonProcessor processor = new MLExtractJsonProcessor(config);

        String input = "Data:\n{\n  \"key\": \"value\",\n  \"number\": 42\n}";
        Object result = processor.process(input);

        assertNotNull(result);
        assertTrue(result instanceof Map);
        Map<String, Object> resultMap = (Map<String, Object>) result;
        assertEquals("value", resultMap.get("key"));
        assertEquals(42, resultMap.get("number"));
    }

    @Test
    public void testExtractJsonEmptyString() {
        Map<String, Object> config = new HashMap<>();
        config.put("default", "empty");

        MLExtractJsonProcessor processor = new MLExtractJsonProcessor(config);

        Object result = processor.process("");

        assertEquals("empty", result);
    }

    @Test
    public void testExtractJsonWhitespaceOnly() {
        Map<String, Object> config = new HashMap<>();
        config.put("default", "whitespace");

        MLExtractJsonProcessor processor = new MLExtractJsonProcessor(config);

        Object result = processor.process("   ");

        assertEquals("whitespace", result);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidExtractType() {
        Map<String, Object> config = new HashMap<>();
        config.put("extract_type", "invalid");

        new MLExtractJsonProcessor(config);
    }

    @Test
    public void testExtractTypeAuto() {
        Map<String, Object> config = new HashMap<>();
        config.put("extract_type", "auto");

        MLExtractJsonProcessor processor = new MLExtractJsonProcessor(config);

        String input = "{\"key\": \"value\"}";
        Object result = processor.process(input);

        assertNotNull(result);
        assertTrue(result instanceof Map);
    }

    @Test
    public void testExtractTypeCaseInsensitive() {
        Map<String, Object> config = new HashMap<>();
        config.put("extract_type", "OBJECT");

        MLExtractJsonProcessor processor = new MLExtractJsonProcessor(config);

        String input = "{\"key\": \"value\"}";
        Object result = processor.process(input);

        assertNotNull(result);
        assertTrue(result instanceof Map);
    }

    @Test
    public void testExtractJsonWithUnicodeCharacters() {
        Map<String, Object> config = new HashMap<>();

        MLExtractJsonProcessor processor = new MLExtractJsonProcessor(config);

        String input = "Data: {\"message\": \"Hello 世界\", \"emoji\": \"😀\"}";
        Object result = processor.process(input);

        assertNotNull(result);
        assertTrue(result instanceof Map);
        Map<String, Object> resultMap = (Map<String, Object>) result;
        assertTrue(resultMap.containsKey("message"));
        assertTrue(resultMap.containsKey("emoji"));
    }

    @Test
    public void testExtractJsonWithLargeNumbers() {
        Map<String, Object> config = new HashMap<>();

        MLExtractJsonProcessor processor = new MLExtractJsonProcessor(config);

        String input = "Data: {\"big\": 9223372036854775807, \"small\": 0.0000001}";
        Object result = processor.process(input);

        assertNotNull(result);
        assertTrue(result instanceof Map);
        Map<String, Object> resultMap = (Map<String, Object>) result;
        assertTrue(resultMap.containsKey("big"));
        assertTrue(resultMap.containsKey("small"));
    }

    @Test
    public void testExtractJsonWithNullInput() {
        Map<String, Object> config = new HashMap<>();

        MLExtractJsonProcessor processor = new MLExtractJsonProcessor(config);

        Object result = processor.process(null);

        // When input is null, StringUtils.toJson converts it to "null" string
        // which has no JSON, so it returns the input (which becomes "null")
        assertNull(result);
    }
}
