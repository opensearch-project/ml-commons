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

    @Test(expected = IllegalArgumentException.class)
    public void testBothValueAndSourcePathProvided() {
        Map<String, Object> config = new HashMap<>();
        config.put("path", "$.field");
        config.put("value", "staticValue");
        config.put("source_path", "$.sourceField");

        new MLSetFieldProcessor(config);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testEmptySourcePath() {
        Map<String, Object> config = new HashMap<>();
        config.put("path", "$.field");
        config.put("source_path", "");

        new MLSetFieldProcessor(config);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testWhitespaceOnlySourcePath() {
        Map<String, Object> config = new HashMap<>();
        config.put("path", "$.field");
        config.put("source_path", "   ");

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

    // Tests for source_path functionality

    @Test
    public void testCopyFieldToNewLocation() {
        Map<String, Object> config = new HashMap<>();
        config.put("path", "$.c");
        config.put("source_path", "$.a");

        MLSetFieldProcessor processor = new MLSetFieldProcessor(config);

        Map<String, Object> input = new HashMap<>();
        input.put("a", 1);
        input.put("b", 2);

        Object result = processor.process(input);

        assertNotNull(result);
        Integer c = JsonPath.read(result, "$.c");
        assertEquals(Integer.valueOf(1), c);
    }

    @Test
    public void testReplaceFieldWithAnotherFieldValue() {
        Map<String, Object> config = new HashMap<>();
        config.put("path", "$.b");
        config.put("source_path", "$.a");

        MLSetFieldProcessor processor = new MLSetFieldProcessor(config);

        Map<String, Object> input = new HashMap<>();
        input.put("a", 1);
        input.put("b", 2);

        Object result = processor.process(input);

        assertNotNull(result);
        Integer b = JsonPath.read(result, "$.b");
        assertEquals(Integer.valueOf(1), b);
    }

    @Test
    public void testCopyNestedFieldForStandardization() {
        Map<String, Object> config = new HashMap<>();
        config.put("path", "$.userId");
        config.put("source_path", "$.user.id");

        MLSetFieldProcessor processor = new MLSetFieldProcessor(config);

        Map<String, Object> user = new HashMap<>();
        user.put("id", 123);
        user.put("name", "John");

        Map<String, Object> input = new HashMap<>();
        input.put("user", user);

        Object result = processor.process(input);

        assertNotNull(result);
        Integer userId = JsonPath.read(result, "$.userId");
        assertEquals(Integer.valueOf(123), userId);
    }

    @Test
    public void testCopyStringField() {
        Map<String, Object> config = new HashMap<>();
        config.put("path", "$.displayName");
        config.put("source_path", "$.name");

        MLSetFieldProcessor processor = new MLSetFieldProcessor(config);

        Map<String, Object> input = new HashMap<>();
        input.put("name", "John Doe");

        Object result = processor.process(input);

        assertNotNull(result);
        String displayName = JsonPath.read(result, "$.displayName");
        assertEquals("John Doe", displayName);
    }

    @Test
    public void testCopyObjectField() {
        Map<String, Object> config = new HashMap<>();
        config.put("path", "$.backup");
        config.put("source_path", "$.original");

        MLSetFieldProcessor processor = new MLSetFieldProcessor(config);

        Map<String, Object> original = new HashMap<>();
        original.put("key1", "value1");
        original.put("key2", "value2");

        Map<String, Object> input = new HashMap<>();
        input.put("original", original);

        Object result = processor.process(input);

        assertNotNull(result);
        Map<String, Object> backup = JsonPath.read(result, "$.backup");
        assertEquals("value1", backup.get("key1"));
        assertEquals("value2", backup.get("key2"));
    }

    @Test
    public void testCopyArrayField() {
        Map<String, Object> config = new HashMap<>();
        config.put("path", "$.tagsCopy");
        config.put("source_path", "$.tags");

        MLSetFieldProcessor processor = new MLSetFieldProcessor(config);

        Map<String, Object> input = new HashMap<>();
        input.put("tags", Arrays.asList("tag1", "tag2", "tag3"));

        Object result = processor.process(input);

        assertNotNull(result);
        Object tagsCopy = JsonPath.read(result, "$.tagsCopy");
        assertTrue(tagsCopy instanceof java.util.List);
        assertEquals(3, ((java.util.List<?>) tagsCopy).size());
    }

    @Test
    public void testSourcePathNotFound() {
        Map<String, Object> config = new HashMap<>();
        config.put("path", "$.newField");
        config.put("source_path", "$.nonExistent");

        MLSetFieldProcessor processor = new MLSetFieldProcessor(config);

        Map<String, Object> input = new HashMap<>();
        input.put("existingField", "value");

        Object result = processor.process(input);

        assertNotNull(result);
        String existingField = JsonPath.read(result, "$.existingField");
        assertEquals("value", existingField);
    }

    @Test
    public void testCopyDeepNestedField() {
        Map<String, Object> config = new HashMap<>();
        config.put("path", "$.flatValue");
        config.put("source_path", "$.level1.level2.level3.value");

        MLSetFieldProcessor processor = new MLSetFieldProcessor(config);

        Map<String, Object> level3 = new HashMap<>();
        level3.put("value", "deep");
        Map<String, Object> level2 = new HashMap<>();
        level2.put("level3", level3);
        Map<String, Object> level1 = new HashMap<>();
        level1.put("level2", level2);
        Map<String, Object> input = new HashMap<>();
        input.put("level1", level1);

        Object result = processor.process(input);

        assertNotNull(result);
        String flatValue = JsonPath.read(result, "$.flatValue");
        assertEquals("deep", flatValue);
    }

    @Test
    public void testCopyToDeepNestedPath() {
        Map<String, Object> config = new HashMap<>();
        config.put("path", "$.metadata.backup.value");
        config.put("source_path", "$.originalValue");

        MLSetFieldProcessor processor = new MLSetFieldProcessor(config);

        Map<String, Object> input = new HashMap<>();
        input.put("originalValue", "test");
        Map<String, Object> backup = new HashMap<>();
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("backup", backup);
        input.put("metadata", metadata);

        Object result = processor.process(input);

        assertNotNull(result);
        String backupValue = JsonPath.read(result, "$.metadata.backup.value");
        assertEquals("test", backupValue);
    }

    @Test
    public void testCopyFromArrayElement() {
        Map<String, Object> config = new HashMap<>();
        config.put("path", "$.firstItem");
        config.put("source_path", "$.items[0]");

        MLSetFieldProcessor processor = new MLSetFieldProcessor(config);

        Map<String, Object> input = new HashMap<>();
        input.put("items", Arrays.asList("first", "second", "third"));

        Object result = processor.process(input);

        assertNotNull(result);
        String firstItem = JsonPath.read(result, "$.firstItem");
        assertEquals("first", firstItem);
    }

    @Test
    public void testCopyToExistingFieldOverwrites() {
        Map<String, Object> config = new HashMap<>();
        config.put("path", "$.target");
        config.put("source_path", "$.source");

        MLSetFieldProcessor processor = new MLSetFieldProcessor(config);

        Map<String, Object> input = new HashMap<>();
        input.put("source", "newValue");
        input.put("target", "oldValue");

        Object result = processor.process(input);

        assertNotNull(result);
        String target = JsonPath.read(result, "$.target");
        assertEquals("newValue", target);
    }

    // Tests for default value functionality

    @Test
    public void testSourcePathWithDefaultWhenSourceExists() {
        Map<String, Object> config = new HashMap<>();
        config.put("path", "$.status");
        config.put("source_path", "$.user.status");
        config.put("default", "unknown");

        MLSetFieldProcessor processor = new MLSetFieldProcessor(config);

        Map<String, Object> user = new HashMap<>();
        user.put("status", "active");

        Map<String, Object> input = new HashMap<>();
        input.put("user", user);

        Object result = processor.process(input);

        assertNotNull(result);
        String status = JsonPath.read(result, "$.status");
        assertEquals("active", status);
    }

    @Test
    public void testSourcePathWithDefaultWhenSourceNotFound() {
        Map<String, Object> config = new HashMap<>();
        config.put("path", "$.status");
        config.put("source_path", "$.user.status");
        config.put("default", "unknown");

        MLSetFieldProcessor processor = new MLSetFieldProcessor(config);

        Map<String, Object> input = new HashMap<>();
        input.put("name", "John");

        Object result = processor.process(input);

        assertNotNull(result);
        String status = JsonPath.read(result, "$.status");
        assertEquals("unknown", status);
    }

    @Test
    public void testSourcePathWithNumericDefault() {
        Map<String, Object> config = new HashMap<>();
        config.put("path", "$.priority");
        config.put("source_path", "$.user.priority");
        config.put("default", 0);

        MLSetFieldProcessor processor = new MLSetFieldProcessor(config);

        Map<String, Object> input = new HashMap<>();
        input.put("name", "John");

        Object result = processor.process(input);

        assertNotNull(result);
        Integer priority = JsonPath.read(result, "$.priority");
        assertEquals(Integer.valueOf(0), priority);
    }

    @Test
    public void testSourcePathWithBooleanDefault() {
        Map<String, Object> config = new HashMap<>();
        config.put("path", "$.enabled");
        config.put("source_path", "$.settings.enabled");
        config.put("default", false);

        MLSetFieldProcessor processor = new MLSetFieldProcessor(config);

        Map<String, Object> input = new HashMap<>();
        input.put("name", "Test");

        Object result = processor.process(input);

        assertNotNull(result);
        Boolean enabled = JsonPath.read(result, "$.enabled");
        assertEquals(false, enabled);
    }

    @Test
    public void testSourcePathWithObjectDefault() {
        Map<String, Object> defaultValue = new HashMap<>();
        defaultValue.put("type", "guest");
        defaultValue.put("level", 1);

        Map<String, Object> config = new HashMap<>();
        config.put("path", "$.userInfo");
        config.put("source_path", "$.user.info");
        config.put("default", defaultValue);

        MLSetFieldProcessor processor = new MLSetFieldProcessor(config);

        Map<String, Object> input = new HashMap<>();
        input.put("name", "John");

        Object result = processor.process(input);

        assertNotNull(result);
        Map<String, Object> userInfo = JsonPath.read(result, "$.userInfo");
        assertEquals("guest", userInfo.get("type"));
        assertEquals(1, userInfo.get("level"));
    }

    @Test
    public void testSourcePathWithArrayDefault() {
        Map<String, Object> config = new HashMap<>();
        config.put("path", "$.tags");
        config.put("source_path", "$.user.tags");
        config.put("default", Arrays.asList("default", "tag"));

        MLSetFieldProcessor processor = new MLSetFieldProcessor(config);

        Map<String, Object> input = new HashMap<>();
        input.put("name", "John");

        Object result = processor.process(input);

        assertNotNull(result);
        Object tags = JsonPath.read(result, "$.tags");
        assertTrue(tags instanceof java.util.List);
        assertEquals(2, ((java.util.List<?>) tags).size());
    }

    @Test
    public void testSourcePathWithNullDefault() {
        Map<String, Object> config = new HashMap<>();
        config.put("path", "$.optional");
        config.put("source_path", "$.user.optional");
        config.put("default", null);

        MLSetFieldProcessor processor = new MLSetFieldProcessor(config);

        Map<String, Object> input = new HashMap<>();
        input.put("name", "John");

        Object result = processor.process(input);

        assertNotNull(result);
        // When setting null, the field is created but JsonPath may have issues reading it
        // Verify the result is a Map and contains the field
        assertTrue(result instanceof Map);
        @SuppressWarnings("unchecked")
        Map<String, Object> resultMap = (Map<String, Object>) result;
        assertTrue(resultMap.containsKey("optional"));
        assertEquals(null, resultMap.get("optional"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testDefaultWithoutSourcePath() {
        Map<String, Object> config = new HashMap<>();
        config.put("path", "$.field");
        config.put("value", "test");
        config.put("default", "fallback");

        new MLSetFieldProcessor(config);
    }

    @Test
    public void testSourcePathWithDefaultInNestedPath() {
        Map<String, Object> config = new HashMap<>();
        config.put("path", "$.metadata.version");
        config.put("source_path", "$.config.version");
        config.put("default", "1.0.0");

        MLSetFieldProcessor processor = new MLSetFieldProcessor(config);

        Map<String, Object> input = new HashMap<>();
        input.put("metadata", new HashMap<>());

        Object result = processor.process(input);

        assertNotNull(result);
        String version = JsonPath.read(result, "$.metadata.version");
        assertEquals("1.0.0", version);
    }
}
