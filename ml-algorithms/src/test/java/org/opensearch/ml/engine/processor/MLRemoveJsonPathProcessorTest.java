/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.processor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;

public class MLRemoveJsonPathProcessorTest {

    @Test
    public void testRemoveSimpleField() {
        Map<String, Object> config = new HashMap<>();
        config.put("path", "$.password");

        MLRemoveJsonPathProcessor processor = new MLRemoveJsonPathProcessor(config);

        Map<String, Object> input = new HashMap<>();
        input.put("username", "john");
        input.put("password", "secret");

        Object result = processor.process(input);

        assertNotNull(result);
        assertTrue(result instanceof Map);
        Map<String, Object> resultMap = (Map<String, Object>) result;
        assertEquals("john", resultMap.get("username"));
        assertFalse(resultMap.containsKey("password"));
    }

    @Test
    public void testRemoveNestedField() {
        Map<String, Object> config = new HashMap<>();
        config.put("path", "$.user.email");

        MLRemoveJsonPathProcessor processor = new MLRemoveJsonPathProcessor(config);

        Map<String, Object> user = new HashMap<>();
        user.put("name", "John");
        user.put("email", "john@example.com");

        Map<String, Object> input = new HashMap<>();
        input.put("user", user);

        Object result = processor.process(input);

        assertNotNull(result);
        assertTrue(result instanceof Map);
        Map<String, Object> resultMap = (Map<String, Object>) result;
        assertTrue(resultMap.containsKey("user"));
        Map<String, Object> userResult = (Map<String, Object>) resultMap.get("user");
        assertEquals("John", userResult.get("name"));
        assertFalse(userResult.containsKey("email"));
    }

    @Test
    public void testRemoveArrayElement() {
        Map<String, Object> config = new HashMap<>();
        config.put("path", "$.items[1]");

        MLRemoveJsonPathProcessor processor = new MLRemoveJsonPathProcessor(config);

        Map<String, Object> input = new HashMap<>();
        input.put("items", Arrays.asList("a", "b", "c"));

        Object result = processor.process(input);

        assertNotNull(result);
        assertTrue(result instanceof Map);
        Map<String, Object> resultMap = (Map<String, Object>) result;
        List<?> items = (List<?>) resultMap.get("items");
        assertEquals(2, items.size());
        assertEquals("a", items.get(0));
        assertEquals("c", items.get(1));
    }

    @Test
    public void testRemoveFirstArrayElement() {
        Map<String, Object> config = new HashMap<>();
        config.put("path", "$.items[0]");

        MLRemoveJsonPathProcessor processor = new MLRemoveJsonPathProcessor(config);

        Map<String, Object> input = new HashMap<>();
        input.put("items", Arrays.asList("first", "second", "third"));

        Object result = processor.process(input);

        assertNotNull(result);
        assertTrue(result instanceof Map);
        Map<String, Object> resultMap = (Map<String, Object>) result;
        List<?> items = (List<?>) resultMap.get("items");
        assertEquals(2, items.size());
        assertEquals("second", items.get(0));
        assertEquals("third", items.get(1));
    }

    @Test
    public void testRemoveLastArrayElement() {
        Map<String, Object> config = new HashMap<>();
        config.put("path", "$.items[-1:]");

        MLRemoveJsonPathProcessor processor = new MLRemoveJsonPathProcessor(config);

        Map<String, Object> input = new HashMap<>();
        input.put("items", Arrays.asList("first", "second", "last"));

        Object result = processor.process(input);

        assertNotNull(result);
        assertTrue(result instanceof Map);
        Map<String, Object> resultMap = (Map<String, Object>) result;
        List<?> items = (List<?>) resultMap.get("items");
        assertEquals(2, items.size());
        assertEquals("first", items.get(0));
        assertEquals("second", items.get(1));
    }

    @Test
    public void testRemoveDeepNestedField() {
        Map<String, Object> config = new HashMap<>();
        config.put("path", "$.level1.level2.level3.secret");

        MLRemoveJsonPathProcessor processor = new MLRemoveJsonPathProcessor(config);

        Map<String, Object> level3 = new HashMap<>();
        level3.put("value", "keep");
        level3.put("secret", "remove");

        Map<String, Object> level2 = new HashMap<>();
        level2.put("level3", level3);

        Map<String, Object> level1 = new HashMap<>();
        level1.put("level2", level2);

        Map<String, Object> input = new HashMap<>();
        input.put("level1", level1);

        Object result = processor.process(input);

        assertNotNull(result);
        assertTrue(result instanceof Map);
        Map<String, Object> resultMap = (Map<String, Object>) result;
        Map<String, Object> l1 = (Map<String, Object>) resultMap.get("level1");
        Map<String, Object> l2 = (Map<String, Object>) l1.get("level2");
        Map<String, Object> l3 = (Map<String, Object>) l2.get("level3");
        assertEquals("keep", l3.get("value"));
        assertFalse(l3.containsKey("secret"));
    }

    @Test
    public void testRemoveMultipleFieldsSequentially() {
        Map<String, Object> config1 = new HashMap<>();
        config1.put("path", "$.password");

        Map<String, Object> config2 = new HashMap<>();
        config2.put("path", "$.ssn");

        MLRemoveJsonPathProcessor processor1 = new MLRemoveJsonPathProcessor(config1);
        MLRemoveJsonPathProcessor processor2 = new MLRemoveJsonPathProcessor(config2);

        Map<String, Object> input = new HashMap<>();
        input.put("username", "john");
        input.put("password", "secret");
        input.put("ssn", "123-45-6789");

        Object result = processor1.process(input);
        result = processor2.process(result);

        assertNotNull(result);
        assertTrue(result instanceof Map);
        Map<String, Object> resultMap = (Map<String, Object>) result;
        assertEquals("john", resultMap.get("username"));
        assertFalse(resultMap.containsKey("password"));
        assertFalse(resultMap.containsKey("ssn"));
    }

    @Test
    public void testRemoveFieldFromArrayOfObjects() {
        Map<String, Object> config = new HashMap<>();
        config.put("path", "$.users[0].email");

        MLRemoveJsonPathProcessor processor = new MLRemoveJsonPathProcessor(config);

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
        assertTrue(result instanceof Map);
        Map<String, Object> resultMap = (Map<String, Object>) result;
        List<?> users = (List<?>) resultMap.get("users");
        Map<String, Object> firstUser = (Map<String, Object>) users.get(0);
        Map<String, Object> secondUser = (Map<String, Object>) users.get(1);

        assertEquals("John", firstUser.get("name"));
        assertFalse(firstUser.containsKey("email"));
        assertEquals("Jane", secondUser.get("name"));
        assertEquals("jane@example.com", secondUser.get("email"));
    }

    @Test
    public void testRemoveNonExistentField() {
        Map<String, Object> config = new HashMap<>();
        config.put("path", "$.nonExistent");

        MLRemoveJsonPathProcessor processor = new MLRemoveJsonPathProcessor(config);

        Map<String, Object> input = new HashMap<>();
        input.put("existing", "value");

        Object result = processor.process(input);

        // Should return original input when path doesn't exist
        assertNotNull(result);
        assertTrue(result instanceof Map);
        Map<String, Object> resultMap = (Map<String, Object>) result;
        assertEquals("value", resultMap.get("existing"));
    }

    @Test
    public void testRemoveFromStringInput() {
        Map<String, Object> config = new HashMap<>();
        config.put("path", "$.password");

        MLRemoveJsonPathProcessor processor = new MLRemoveJsonPathProcessor(config);

        String input = "{\"username\": \"john\", \"password\": \"secret\"}";
        Object result = processor.process(input);

        assertNotNull(result);
        assertTrue(result instanceof Map);
        Map<String, Object> resultMap = (Map<String, Object>) result;
        assertEquals("john", resultMap.get("username"));
        assertFalse(resultMap.containsKey("password"));
    }

    @Test
    public void testRemoveEntireObject() {
        Map<String, Object> config = new HashMap<>();
        config.put("path", "$.metadata");

        MLRemoveJsonPathProcessor processor = new MLRemoveJsonPathProcessor(config);

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("internal", "data");

        Map<String, Object> input = new HashMap<>();
        input.put("data", "value");
        input.put("metadata", metadata);

        Object result = processor.process(input);

        assertNotNull(result);
        assertTrue(result instanceof Map);
        Map<String, Object> resultMap = (Map<String, Object>) result;
        assertEquals("value", resultMap.get("data"));
        assertFalse(resultMap.containsKey("metadata"));
    }

    @Test
    public void testRemoveEntireArray() {
        Map<String, Object> config = new HashMap<>();
        config.put("path", "$.tags");

        MLRemoveJsonPathProcessor processor = new MLRemoveJsonPathProcessor(config);

        Map<String, Object> input = new HashMap<>();
        input.put("name", "item");
        input.put("tags", Arrays.asList("tag1", "tag2"));

        Object result = processor.process(input);

        assertNotNull(result);
        assertTrue(result instanceof Map);
        Map<String, Object> resultMap = (Map<String, Object>) result;
        assertEquals("item", resultMap.get("name"));
        assertFalse(resultMap.containsKey("tags"));
    }

    @Test
    public void testRemoveWithNullInput() {
        Map<String, Object> config = new HashMap<>();
        config.put("path", "$.field");

        MLRemoveJsonPathProcessor processor = new MLRemoveJsonPathProcessor(config);

        Object result = processor.process(null);

        // Should return original input on error
        assertEquals(null, result);
    }

    @Test
    public void testRemoveWithEmptyObject() {
        Map<String, Object> config = new HashMap<>();
        config.put("path", "$.field");

        MLRemoveJsonPathProcessor processor = new MLRemoveJsonPathProcessor(config);

        Map<String, Object> input = new HashMap<>();
        Object result = processor.process(input);

        assertNotNull(result);
        assertTrue(result instanceof Map);
        Map<String, Object> resultMap = (Map<String, Object>) result;
        assertEquals(0, resultMap.size());
    }

    @Test
    public void testRemoveWithComplexNestedStructure() {
        Map<String, Object> config = new HashMap<>();
        config.put("path", "$.response.data.sensitive");

        MLRemoveJsonPathProcessor processor = new MLRemoveJsonPathProcessor(config);

        Map<String, Object> data = new HashMap<>();
        data.put("public", "info");
        data.put("sensitive", "secret");

        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("data", data);

        Map<String, Object> input = new HashMap<>();
        input.put("response", response);

        Object result = processor.process(input);

        assertNotNull(result);
        assertTrue(result instanceof Map);
        Map<String, Object> resultMap = (Map<String, Object>) result;
        Map<String, Object> resp = (Map<String, Object>) resultMap.get("response");
        Map<String, Object> dataResult = (Map<String, Object>) resp.get("data");
        assertEquals("info", dataResult.get("public"));
        assertFalse(dataResult.containsKey("sensitive"));
    }

    @Test
    public void testRemovePreservesOtherFields() {
        Map<String, Object> config = new HashMap<>();
        config.put("path", "$.remove");

        MLRemoveJsonPathProcessor processor = new MLRemoveJsonPathProcessor(config);

        Map<String, Object> input = new HashMap<>();
        input.put("keep1", "value1");
        input.put("remove", "value2");
        input.put("keep2", "value3");

        Object result = processor.process(input);

        assertNotNull(result);
        assertTrue(result instanceof Map);
        Map<String, Object> resultMap = (Map<String, Object>) result;
        assertEquals(2, resultMap.size());
        assertEquals("value1", resultMap.get("keep1"));
        assertEquals("value3", resultMap.get("keep2"));
        assertFalse(resultMap.containsKey("remove"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testMissingPathConfig() {
        Map<String, Object> config = new HashMap<>();
        new MLRemoveJsonPathProcessor(config);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testEmptyPathConfig() {
        Map<String, Object> config = new HashMap<>();
        config.put("path", "");

        new MLRemoveJsonPathProcessor(config);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testWhitespaceOnlyPath() {
        Map<String, Object> config = new HashMap<>();
        config.put("path", "   ");

        new MLRemoveJsonPathProcessor(config);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testNullPath() {
        Map<String, Object> config = new HashMap<>();
        config.put("path", null);

        new MLRemoveJsonPathProcessor(config);
    }

    @Test
    public void testRemoveWithInvalidPath() {
        Map<String, Object> config = new HashMap<>();
        config.put("path", "$.invalid..path");

        MLRemoveJsonPathProcessor processor = new MLRemoveJsonPathProcessor(config);

        Map<String, Object> input = new HashMap<>();
        input.put("data", "value");

        Object result = processor.process(input);

        // Should return original input on error
        assertNotNull(result);
        assertTrue(result instanceof Map);
        Map<String, Object> resultMap = (Map<String, Object>) result;
        assertEquals("value", resultMap.get("data"));
    }

    @Test
    public void testRemoveRootField() {
        Map<String, Object> config = new HashMap<>();
        config.put("path", "$.name");

        MLRemoveJsonPathProcessor processor = new MLRemoveJsonPathProcessor(config);

        Map<String, Object> input = new HashMap<>();
        input.put("name", "John");
        input.put("age", 30);
        input.put("city", "NYC");

        Object result = processor.process(input);

        assertNotNull(result);
        assertTrue(result instanceof Map);
        Map<String, Object> resultMap = (Map<String, Object>) result;
        assertFalse(resultMap.containsKey("name"));
        assertEquals(30, resultMap.get("age"));
        assertEquals("NYC", resultMap.get("city"));
    }

    @Test
    public void testRemoveFromEmptyArray() {
        Map<String, Object> config = new HashMap<>();
        config.put("path", "$.items[0]");

        MLRemoveJsonPathProcessor processor = new MLRemoveJsonPathProcessor(config);

        Map<String, Object> input = new HashMap<>();
        input.put("items", Arrays.asList());

        Object result = processor.process(input);

        // Should return original input when array is empty
        assertNotNull(result);
        assertTrue(result instanceof Map);
        Map<String, Object> resultMap = (Map<String, Object>) result;
        List<?> items = (List<?>) resultMap.get("items");
        assertEquals(0, items.size());
    }

    @Test
    public void testRemoveFieldWithSpecialCharacters() {
        Map<String, Object> config = new HashMap<>();
        config.put("path", "$.field-with-dash");

        MLRemoveJsonPathProcessor processor = new MLRemoveJsonPathProcessor(config);

        Map<String, Object> input = new HashMap<>();
        input.put("field-with-dash", "value");
        input.put("normal", "keep");

        Object result = processor.process(input);

        assertNotNull(result);
        assertTrue(result instanceof Map);
        Map<String, Object> resultMap = (Map<String, Object>) result;
        assertFalse(resultMap.containsKey("field-with-dash"));
        assertEquals("keep", resultMap.get("normal"));
    }

    @Test
    public void testRemoveNumericField() {
        Map<String, Object> config = new HashMap<>();
        config.put("path", "$.count");

        MLRemoveJsonPathProcessor processor = new MLRemoveJsonPathProcessor(config);

        Map<String, Object> input = new HashMap<>();
        input.put("count", 42);
        input.put("name", "test");

        Object result = processor.process(input);

        assertNotNull(result);
        assertTrue(result instanceof Map);
        Map<String, Object> resultMap = (Map<String, Object>) result;
        assertFalse(resultMap.containsKey("count"));
        assertEquals("test", resultMap.get("name"));
    }

    @Test
    public void testRemoveBooleanField() {
        Map<String, Object> config = new HashMap<>();
        config.put("path", "$.active");

        MLRemoveJsonPathProcessor processor = new MLRemoveJsonPathProcessor(config);

        Map<String, Object> input = new HashMap<>();
        input.put("active", true);
        input.put("name", "test");

        Object result = processor.process(input);

        assertNotNull(result);
        assertTrue(result instanceof Map);
        Map<String, Object> resultMap = (Map<String, Object>) result;
        assertFalse(resultMap.containsKey("active"));
        assertEquals("test", resultMap.get("name"));
    }
}
