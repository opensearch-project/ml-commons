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

public class MLJsonPathFilterProcessorTest {

    @Test
    public void testExtractSimpleField() {
        Map<String, Object> config = new HashMap<>();
        config.put("path", "$.name");

        MLJsonPathFilterProcessor processor = new MLJsonPathFilterProcessor(config);

        Map<String, Object> input = new HashMap<>();
        input.put("name", "John");
        input.put("age", 30);

        Object result = processor.process(input);

        assertEquals("John", result);
    }

    @Test
    public void testExtractNestedField() {
        Map<String, Object> config = new HashMap<>();
        config.put("path", "$.user.email");

        MLJsonPathFilterProcessor processor = new MLJsonPathFilterProcessor(config);

        Map<String, Object> user = new HashMap<>();
        user.put("name", "John");
        user.put("email", "john@example.com");

        Map<String, Object> input = new HashMap<>();
        input.put("user", user);

        Object result = processor.process(input);

        assertEquals("john@example.com", result);
    }

    @Test
    public void testExtractArrayElement() {
        Map<String, Object> config = new HashMap<>();
        config.put("path", "$.items[0]");

        MLJsonPathFilterProcessor processor = new MLJsonPathFilterProcessor(config);

        Map<String, Object> input = new HashMap<>();
        input.put("items", Arrays.asList("first", "second", "third"));

        Object result = processor.process(input);

        assertEquals("first", result);
    }

    @Test
    public void testExtractAllArrayElements() {
        Map<String, Object> config = new HashMap<>();
        config.put("path", "$.items[*]");

        MLJsonPathFilterProcessor processor = new MLJsonPathFilterProcessor(config);

        Map<String, Object> input = new HashMap<>();
        input.put("items", Arrays.asList("a", "b", "c"));

        Object result = processor.process(input);

        assertNotNull(result);
        assertTrue(result instanceof List);
        List<?> resultList = (List<?>) result;
        assertEquals(3, resultList.size());
        assertEquals("a", resultList.get(0));
        assertEquals("b", resultList.get(1));
        assertEquals("c", resultList.get(2));
    }

    @Test
    public void testExtractFieldFromAllArrayElements() {
        Map<String, Object> config = new HashMap<>();
        config.put("path", "$.products[*].name");

        MLJsonPathFilterProcessor processor = new MLJsonPathFilterProcessor(config);

        Map<String, Object> product1 = new HashMap<>();
        product1.put("name", "Product A");
        product1.put("price", 10);

        Map<String, Object> product2 = new HashMap<>();
        product2.put("name", "Product B");
        product2.put("price", 20);

        Map<String, Object> input = new HashMap<>();
        input.put("products", Arrays.asList(product1, product2));

        Object result = processor.process(input);

        assertNotNull(result);
        assertTrue(result instanceof List);
        List<?> resultList = (List<?>) result;
        assertEquals(2, resultList.size());
        assertEquals("Product A", resultList.get(0));
        assertEquals("Product B", resultList.get(1));
    }

    @Test
    public void testFilterArrayByCondition() {
        Map<String, Object> config = new HashMap<>();
        config.put("path", "$.items[?(@.active == true)]");

        MLJsonPathFilterProcessor processor = new MLJsonPathFilterProcessor(config);

        Map<String, Object> item1 = new HashMap<>();
        item1.put("id", 1);
        item1.put("active", true);

        Map<String, Object> item2 = new HashMap<>();
        item2.put("id", 2);
        item2.put("active", false);

        Map<String, Object> item3 = new HashMap<>();
        item3.put("id", 3);
        item3.put("active", true);

        Map<String, Object> input = new HashMap<>();
        input.put("items", Arrays.asList(item1, item2, item3));

        Object result = processor.process(input);

        assertNotNull(result);
        assertTrue(result instanceof List);
        List<?> resultList = (List<?>) result;
        assertEquals(2, resultList.size());
    }

    @Test
    public void testFilterByNumericCondition() {
        Map<String, Object> config = new HashMap<>();
        config.put("path", "$.items[?(@.price < 15)]");

        MLJsonPathFilterProcessor processor = new MLJsonPathFilterProcessor(config);

        Map<String, Object> item1 = new HashMap<>();
        item1.put("name", "Cheap");
        item1.put("price", 10);

        Map<String, Object> item2 = new HashMap<>();
        item2.put("name", "Expensive");
        item2.put("price", 20);

        Map<String, Object> input = new HashMap<>();
        input.put("items", Arrays.asList(item1, item2));

        Object result = processor.process(input);

        assertNotNull(result);
        assertTrue(result instanceof List);
        List<?> resultList = (List<?>) result;
        assertEquals(1, resultList.size());
    }

    @Test
    public void testRecursiveSearch() {
        Map<String, Object> config = new HashMap<>();
        config.put("path", "$..id");

        MLJsonPathFilterProcessor processor = new MLJsonPathFilterProcessor(config);

        Map<String, Object> profile = new HashMap<>();
        profile.put("id", 2);

        Map<String, Object> user = new HashMap<>();
        user.put("id", 1);
        user.put("profile", profile);

        Map<String, Object> input = new HashMap<>();
        input.put("user", user);

        Object result = processor.process(input);

        assertNotNull(result);
        assertTrue(result instanceof List);
        List<?> resultList = (List<?>) result;
        assertEquals(2, resultList.size());
    }

    @Test
    public void testExtractDeepNestedField() {
        Map<String, Object> config = new HashMap<>();
        config.put("path", "$.level1.level2.level3.value");

        MLJsonPathFilterProcessor processor = new MLJsonPathFilterProcessor(config);

        Map<String, Object> level3 = new HashMap<>();
        level3.put("value", "deep");

        Map<String, Object> level2 = new HashMap<>();
        level2.put("level3", level3);

        Map<String, Object> level1 = new HashMap<>();
        level1.put("level2", level2);

        Map<String, Object> input = new HashMap<>();
        input.put("level1", level1);

        Object result = processor.process(input);

        assertEquals("deep", result);
    }

    @Test
    public void testExtractWithDefaultWhenPathNotFound() {
        Map<String, Object> config = new HashMap<>();
        config.put("path", "$.nonExistent");
        config.put("default", "not_found");

        MLJsonPathFilterProcessor processor = new MLJsonPathFilterProcessor(config);

        Map<String, Object> input = new HashMap<>();
        input.put("existing", "value");

        Object result = processor.process(input);

        assertEquals("not_found", result);
    }

    @Test
    public void testExtractWithoutDefaultWhenPathNotFound() {
        Map<String, Object> config = new HashMap<>();
        config.put("path", "$.nonExistent");

        MLJsonPathFilterProcessor processor = new MLJsonPathFilterProcessor(config);

        Map<String, Object> input = new HashMap<>();
        input.put("existing", "value");

        Object result = processor.process(input);

        // Should return original input
        assertTrue(result instanceof Map);
        Map<String, Object> resultMap = (Map<String, Object>) result;
        assertEquals("value", resultMap.get("existing"));
    }

    @Test
    public void testExtractNumber() {
        Map<String, Object> config = new HashMap<>();
        config.put("path", "$.count");

        MLJsonPathFilterProcessor processor = new MLJsonPathFilterProcessor(config);

        Map<String, Object> input = new HashMap<>();
        input.put("count", 42);

        Object result = processor.process(input);

        assertEquals(42, result);
    }

    @Test
    public void testExtractBoolean() {
        Map<String, Object> config = new HashMap<>();
        config.put("path", "$.active");

        MLJsonPathFilterProcessor processor = new MLJsonPathFilterProcessor(config);

        Map<String, Object> input = new HashMap<>();
        input.put("active", true);

        Object result = processor.process(input);

        assertEquals(true, result);
    }

    @Test
    public void testExtractArray() {
        Map<String, Object> config = new HashMap<>();
        config.put("path", "$.tags");

        MLJsonPathFilterProcessor processor = new MLJsonPathFilterProcessor(config);

        Map<String, Object> input = new HashMap<>();
        input.put("tags", Arrays.asList("tag1", "tag2", "tag3"));

        Object result = processor.process(input);

        assertNotNull(result);
        assertTrue(result instanceof List);
        List<?> resultList = (List<?>) result;
        assertEquals(3, resultList.size());
    }

    @Test
    public void testExtractObject() {
        Map<String, Object> config = new HashMap<>();
        config.put("path", "$.metadata");

        MLJsonPathFilterProcessor processor = new MLJsonPathFilterProcessor(config);

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("key", "value");

        Map<String, Object> input = new HashMap<>();
        input.put("metadata", metadata);

        Object result = processor.process(input);

        assertNotNull(result);
        assertTrue(result instanceof Map);
        Map<String, Object> resultMap = (Map<String, Object>) result;
        assertEquals("value", resultMap.get("key"));
    }

    @Test
    public void testExtractNullValue() {
        Map<String, Object> config = new HashMap<>();
        config.put("path", "$.nullField");

        MLJsonPathFilterProcessor processor = new MLJsonPathFilterProcessor(config);

        Map<String, Object> input = new HashMap<>();
        input.put("nullField", null);

        Object result = processor.process(input);

        assertEquals(input, result);
    }

    @Test
    public void testExtractFromStringInput() {
        Map<String, Object> config = new HashMap<>();
        config.put("path", "$.name");

        MLJsonPathFilterProcessor processor = new MLJsonPathFilterProcessor(config);

        String input = "{\"name\": \"John\", \"age\": 30}";
        Object result = processor.process(input);

        // Should work with string JSON input
        assertEquals("John", result);
    }

    @Test
    public void testExtractMultipleFieldsWithWildcard() {
        Map<String, Object> config = new HashMap<>();
        config.put("path", "$.users[*].email");

        MLJsonPathFilterProcessor processor = new MLJsonPathFilterProcessor(config);

        Map<String, Object> user1 = new HashMap<>();
        user1.put("name", "John");
        user1.put("email", "john@example.com");

        Map<String, Object> user2 = new HashMap<>();
        user2.put("name", "Jane");
        user2.put("email", "jane@example.com");

        Map<String, Object> input = new HashMap<>();
        input.put("users", Arrays.asList(user1, user2));

        Object result = processor.process(input);

        assertNotNull(result);
        assertTrue(result instanceof List);
        List<?> resultList = (List<?>) result;
        assertEquals(2, resultList.size());
        assertEquals("john@example.com", resultList.get(0));
        assertEquals("jane@example.com", resultList.get(1));
    }

    @Test
    public void testExtractWithComplexFilter() {
        Map<String, Object> config = new HashMap<>();
        config.put("path", "$.items[?(@.price > 10 && @.active == true)]");

        MLJsonPathFilterProcessor processor = new MLJsonPathFilterProcessor(config);

        Map<String, Object> item1 = new HashMap<>();
        item1.put("name", "Item1");
        item1.put("price", 15);
        item1.put("active", true);

        Map<String, Object> item2 = new HashMap<>();
        item2.put("name", "Item2");
        item2.put("price", 5);
        item2.put("active", true);

        Map<String, Object> item3 = new HashMap<>();
        item3.put("name", "Item3");
        item3.put("price", 20);
        item3.put("active", false);

        Map<String, Object> input = new HashMap<>();
        input.put("items", Arrays.asList(item1, item2, item3));

        Object result = processor.process(input);

        assertNotNull(result);
        assertTrue(result instanceof List);
        List<?> resultList = (List<?>) result;
        assertEquals(1, resultList.size());
    }

    @Test
    public void testExtractSequentially() {
        Map<String, Object> config1 = new HashMap<>();
        config1.put("path", "$.user");

        Map<String, Object> config2 = new HashMap<>();
        config2.put("path", "$.email");

        MLJsonPathFilterProcessor processor1 = new MLJsonPathFilterProcessor(config1);
        MLJsonPathFilterProcessor processor2 = new MLJsonPathFilterProcessor(config2);

        Map<String, Object> user = new HashMap<>();
        user.put("name", "John");
        user.put("email", "john@example.com");

        Map<String, Object> input = new HashMap<>();
        input.put("user", user);

        Object result = processor1.process(input);
        result = processor2.process(result);

        assertEquals("john@example.com", result);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testMissingPathConfig() {
        Map<String, Object> config = new HashMap<>();
        new MLJsonPathFilterProcessor(config);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testEmptyPathConfig() {
        Map<String, Object> config = new HashMap<>();
        config.put("path", "");

        new MLJsonPathFilterProcessor(config);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testWhitespaceOnlyPath() {
        Map<String, Object> config = new HashMap<>();
        config.put("path", "   ");

        new MLJsonPathFilterProcessor(config);
    }

    @Test
    public void testExtractWithDefaultOnError() {
        Map<String, Object> config = new HashMap<>();
        config.put("path", "$.invalid..path");
        config.put("default", "error_default");

        MLJsonPathFilterProcessor processor = new MLJsonPathFilterProcessor(config);

        Map<String, Object> input = new HashMap<>();
        input.put("data", "test");

        Object result = processor.process(input);

        // Should return default on error
        assertEquals("error_default", result);
    }

    @Test
    public void testExtractRootElement() {
        Map<String, Object> config = new HashMap<>();
        config.put("path", "$");

        MLJsonPathFilterProcessor processor = new MLJsonPathFilterProcessor(config);

        Map<String, Object> input = new HashMap<>();
        input.put("name", "John");
        input.put("age", 30);

        Object result = processor.process(input);

        assertNotNull(result);
        assertTrue(result instanceof Map);
        Map<String, Object> resultMap = (Map<String, Object>) result;
        assertEquals("John", resultMap.get("name"));
        assertEquals(30, resultMap.get("age"));
    }

    @Test
    public void testExtractFromEmptyArray() {
        Map<String, Object> config = new HashMap<>();
        config.put("path", "$.items[0]");
        config.put("default", "empty");

        MLJsonPathFilterProcessor processor = new MLJsonPathFilterProcessor(config);

        Map<String, Object> input = new HashMap<>();
        input.put("items", Arrays.asList());

        Object result = processor.process(input);

        assertEquals("empty", result);
    }

    @Test
    public void testExtractWithNullInput() {
        Map<String, Object> config = new HashMap<>();
        config.put("path", "$.field");

        MLJsonPathFilterProcessor processor = new MLJsonPathFilterProcessor(config);

        Object input = null;
        Object result = processor.process(input);

        // Should return original input
        assertEquals(input, result);
    }

    @Test
    public void testExtractArraySlice() {
        Map<String, Object> config = new HashMap<>();
        config.put("path", "$.items[0:2]");

        MLJsonPathFilterProcessor processor = new MLJsonPathFilterProcessor(config);

        Map<String, Object> input = new HashMap<>();
        input.put("items", Arrays.asList("a", "b", "c", "d"));

        Object result = processor.process(input);

        assertNotNull(result);
        assertTrue(result instanceof List);
        List<?> resultList = (List<?>) result;
        assertEquals(2, resultList.size());
        assertEquals("a", resultList.get(0));
        assertEquals("b", resultList.get(1));
    }

    @Test
    public void testExtractLastArrayElement() {
        Map<String, Object> config = new HashMap<>();
        config.put("path", "$.items[-1]");

        MLJsonPathFilterProcessor processor = new MLJsonPathFilterProcessor(config);

        Map<String, Object> input = new HashMap<>();
        input.put("items", Arrays.asList("first", "second", "last"));

        Object result = processor.process(input);

        assertEquals("last", result);
    }

    @Test
    public void testExtractWithRegexFilter() {
        Map<String, Object> config = new HashMap<>();
        config.put("path", "$.items[?(@.name =~ /.*test.*/i)]");

        MLJsonPathFilterProcessor processor = new MLJsonPathFilterProcessor(config);

        Map<String, Object> item1 = new HashMap<>();
        item1.put("name", "Test Item");

        Map<String, Object> item2 = new HashMap<>();
        item2.put("name", "Other Item");

        Map<String, Object> item3 = new HashMap<>();
        item3.put("name", "testing");

        Map<String, Object> input = new HashMap<>();
        input.put("items", Arrays.asList(item1, item2, item3));

        Object result = processor.process(input);

        assertNotNull(result);
        assertTrue(result instanceof List);
        List<?> resultList = (List<?>) result;
        assertEquals(2, resultList.size());
    }

    @Test
    public void testExtractArrayLength() {
        Map<String, Object> config = new HashMap<>();
        config.put("path", "$.items.length()");

        MLJsonPathFilterProcessor processor = new MLJsonPathFilterProcessor(config);

        Map<String, Object> input = new HashMap<>();
        input.put("items", Arrays.asList("a", "b", "c"));

        Object result = processor.process(input);

        assertEquals(3, result);
    }
}
