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

import com.jayway.jsonpath.JsonPath;

public class MLSetFieldProcessorTest {

    @Test
    public void testSetSimpleField() {
        Map<String, Object> config = new HashMap<>();
        config.put("path", "$.status");
        config.put("value", "active");

        MLSetFieldProcessor processor = new MLSetFieldProcessor(config);

        Map<String, Object> input = new HashMap<>();
        input.put("name", "John");

        Object result = processor.process(input);

        assertNotNull(result);
        String status = JsonPath.read(result, "$.status");
        assertEquals("active", status);
    }

    @Test
    public void testSetNestedField() {
        Map<String, Object> config = new HashMap<>();
        config.put("path", "$.metadata.processed");
        config.put("value", true);

        MLSetFieldProcessor processor = new MLSetFieldProcessor(config);

        Map<String, Object> metadata = new HashMap<>();
        Map<String, Object> input = new HashMap<>();
        input.put("name", "John");
        input.put("metadata", metadata);

        Object result = processor.process(input);

        assertNotNull(result);
        Boolean processed = JsonPath.read(result, "$.metadata.processed");
        assertEquals(true, processed);
    }

    @Test
    public void testSetBooleanValue() {
        Map<String, Object> config = new HashMap<>();
        config.put("path", "$.active");
        config.put("value", true);

        MLSetFieldProcessor processor = new MLSetFieldProcessor(config);

        Map<String, Object> input = new HashMap<>();
        Object result = processor.process(input);

        assertNotNull(result);
        Boolean active = JsonPath.read(result, "$.active");
        assertEquals(true, active);
    }

    @Test
    public void testSetNumericValue() {
        Map<String, Object> config = new HashMap<>();
        config.put("path", "$.priority");
        config.put("value", 5);

        MLSetFieldProcessor processor = new MLSetFieldProcessor(config);

        Map<String, Object> input = new HashMap<>();
        Object result = processor.process(input);

        assertNotNull(result);
        Integer priority = JsonPath.read(result, "$.priority");
        assertEquals(Integer.valueOf(5), priority);
    }

    @Test
    public void testSetObjectValue() {
        Map<String, Object> configValue = new HashMap<>();
        configValue.put("enabled", true);
        configValue.put("timeout", 30);

        Map<String, Object> config = new HashMap<>();
        config.put("path", "$.settings");
        config.put("value", configValue);

        MLSetFieldProcessor processor = new MLSetFieldProcessor(config);

        Map<String, Object> input = new HashMap<>();
        Object result = processor.process(input);

        assertNotNull(result);
        Map<String, Object> settings = JsonPath.read(result, "$.settings");
        assertEquals(true, settings.get("enabled"));
        assertEquals(30, settings.get("timeout"));
    }

    @Test
    public void testSetArrayValue() {
        Map<String, Object> config = new HashMap<>();
        config.put("path", "$.tags");
        config.put("value", Arrays.asList("important", "reviewed"));

        MLSetFieldProcessor processor = new MLSetFieldProcessor(config);

        Map<String, Object> input = new HashMap<>();
        Object result = processor.process(input);

        assertNotNull(result);
        Object tags = JsonPath.read(result, "$.tags");
        assertTrue(tags instanceof java.util.List);
        assertEquals(2, ((java.util.List<?>) tags).size());
    }

    @Test
    public void testUpdateExistingField() {
        Map<String, Object> config = new HashMap<>();
        config.put("path", "$.status");
        config.put("value", "updated");

        MLSetFieldProcessor processor = new MLSetFieldProcessor(config);

        Map<String, Object> input = new HashMap<>();
        input.put("status", "original");
        input.put("name", "John");

        Object result = processor.process(input);

        assertNotNull(result);
        String status = JsonPath.read(result, "$.status");
        assertEquals("updated", status);
        String name = JsonPath.read(result, "$.name");
        assertEquals("John", name);
    }

    @Test
    public void testCreateNewNestedPath() {
        Map<String, Object> config = new HashMap<>();
        config.put("path", "$.metadata.version");
        config.put("value", "1.0");

        MLSetFieldProcessor processor = new MLSetFieldProcessor(config);

        Map<String, Object> input = new HashMap<>();
        input.put("metadata", new HashMap<>());

        Object result = processor.process(input);

        assertNotNull(result);
        String version = JsonPath.read(result, "$.metadata.version");
        assertEquals("1.0", version);
    }

    @Test
    public void testSetWithJsonPathExpression() {
        Map<String, Object> config = new HashMap<>();
        config.put("path", "$.data.result");
        config.put("value", "success");

        MLSetFieldProcessor processor = new MLSetFieldProcessor(config);

        Map<String, Object> data = new HashMap<>();
        Map<String, Object> input = new HashMap<>();
        input.put("data", data);

        Object result = processor.process(input);

        assertNotNull(result);
        String resultValue = JsonPath.read(result, "$.data.result");
        assertEquals("success", resultValue);
    }

    @Test
    public void testSetNullValue() {
        Map<String, Object> config = new HashMap<>();
        config.put("path", "$.optional");
        config.put("value", null);

        MLSetFieldProcessor processor = new MLSetFieldProcessor(config);

        Map<String, Object> input = new HashMap<>();
        Object result = processor.process(input);

        assertNotNull(result);
        // JsonPath returns null for null values
        Object optional = JsonPath.read(result, "$.optional");
        assertEquals(null, optional);
    }

    @Test
    public void testSetMultipleFieldsSequentially() {
        Map<String, Object> config1 = new HashMap<>();
        config1.put("path", "$.field1");
        config1.put("value", "value1");

        Map<String, Object> config2 = new HashMap<>();
        config2.put("path", "$.field2");
        config2.put("value", "value2");

        MLSetFieldProcessor processor1 = new MLSetFieldProcessor(config1);
        MLSetFieldProcessor processor2 = new MLSetFieldProcessor(config2);

        Map<String, Object> input = new HashMap<>();
        Object result = processor1.process(input);
        result = processor2.process(result);

        assertNotNull(result);
        String field1 = JsonPath.read(result, "$.field1");
        String field2 = JsonPath.read(result, "$.field2");
        assertEquals("value1", field1);
        assertEquals("value2", field2);
    }

    @Test
    public void testSetDeepNestedPath() {
        Map<String, Object> config = new HashMap<>();
        config.put("path", "$.level1.level2.level3.value");
        config.put("value", "deep");

        MLSetFieldProcessor processor = new MLSetFieldProcessor(config);

        Map<String, Object> level3 = new HashMap<>();
        Map<String, Object> level2 = new HashMap<>();
        level2.put("level3", level3);
        Map<String, Object> level1 = new HashMap<>();
        level1.put("level2", level2);
        Map<String, Object> input = new HashMap<>();
        input.put("level1", level1);

        Object result = processor.process(input);

        assertNotNull(result);
        String value = JsonPath.read(result, "$.level1.level2.level3.value");
        assertEquals("deep", value);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testMissingPathConfig() {
        Map<String, Object> config = new HashMap<>();
        config.put("value", "test");

        new MLSetFieldProcessor(config);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testEmptyPathConfig() {
        Map<String, Object> config = new HashMap<>();
        config.put("path", "");
        config.put("value", "test");

        new MLSetFieldProcessor(config);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testWhitespaceOnlyPath() {
        Map<String, Object> config = new HashMap<>();
        config.put("path", "   ");
        config.put("value", "test");

        new MLSetFieldProcessor(config);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testMissingValueConfig() {
        Map<String, Object> config = new HashMap<>();
        config.put("path", "$.field");

        new MLSetFieldProcessor(config);
    }

    @Test
    public void testSetFieldOnNonMapInput() {
        Map<String, Object> config = new HashMap<>();
        config.put("path", "$.field");
        config.put("value", "test");

        MLSetFieldProcessor processor = new MLSetFieldProcessor(config);

        // Process a string input (will be converted to JSON)
        Object result = processor.process("simple string");

        // Should return original input on failure
        assertEquals("simple string", result);
    }

    @Test
    public void testSetFieldWithComplexValue() {
        Map<String, Object> complexValue = new HashMap<>();
        complexValue.put("name", "John");
        complexValue.put("age", 30);
        complexValue.put("tags", Arrays.asList("admin", "user"));

        Map<String, Object> config = new HashMap<>();
        config.put("path", "$.user");
        config.put("value", complexValue);

        MLSetFieldProcessor processor = new MLSetFieldProcessor(config);

        Map<String, Object> input = new HashMap<>();
        Object result = processor.process(input);

        assertNotNull(result);
        Map<String, Object> user = JsonPath.read(result, "$.user");
        assertEquals("John", user.get("name"));
        assertEquals(30, user.get("age"));
        assertTrue(user.get("tags") instanceof java.util.List);
    }

    @Test
    public void testSetFieldPreservesOtherFields() {
        Map<String, Object> config = new HashMap<>();
        config.put("path", "$.newField");
        config.put("value", "newValue");

        MLSetFieldProcessor processor = new MLSetFieldProcessor(config);

        Map<String, Object> input = new HashMap<>();
        input.put("existingField1", "value1");
        input.put("existingField2", "value2");

        Object result = processor.process(input);

        assertNotNull(result);
        String newField = JsonPath.read(result, "$.newField");
        String existing1 = JsonPath.read(result, "$.existingField1");
        String existing2 = JsonPath.read(result, "$.existingField2");

        assertEquals("newValue", newField);
        assertEquals("value1", existing1);
        assertEquals("value2", existing2);
    }

    @Test
    public void testSetFieldWithStringValue() {
        Map<String, Object> config = new HashMap<>();
        config.put("path", "$.timestamp");
        config.put("value", "2024-03-15T10:30:00Z");

        MLSetFieldProcessor processor = new MLSetFieldProcessor(config);

        Map<String, Object> input = new HashMap<>();
        Object result = processor.process(input);

        assertNotNull(result);
        String timestamp = JsonPath.read(result, "$.timestamp");
        assertEquals("2024-03-15T10:30:00Z", timestamp);
    }

    @Test
    public void testSetFieldWithDoubleValue() {
        Map<String, Object> config = new HashMap<>();
        config.put("path", "$.price");
        config.put("value", 19.99);

        MLSetFieldProcessor processor = new MLSetFieldProcessor(config);

        Map<String, Object> input = new HashMap<>();
        Object result = processor.process(input);

        assertNotNull(result);
        Double price = JsonPath.read(result, "$.price");
        assertEquals(Double.valueOf(19.99), price);
    }
}
