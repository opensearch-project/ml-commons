/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.processor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;

/**
 * Unit tests for MLForEachProcessor.
 */
public class MLForEachProcessorTest {

    private Map<String, Object> baseConfig;

    @Before
    public void setUp() {
        baseConfig = new HashMap<>();
        baseConfig.put("path", "$.items[*]");
        baseConfig.put("processors", List.of(Map.of("type", "set_field", "path", "$.processed", "value", true)));
    }

    @Test
    public void testConstructor_Success() {
        MLForEachProcessor processor = new MLForEachProcessor(baseConfig);
        assertNotNull(processor);
    }

    @Test
    public void testConstructor_MissingPath() {
        Map<String, Object> config = new HashMap<>();
        config.put("processors", List.of(Map.of("type", "to_string")));

        try {
            new MLForEachProcessor(config);
            fail("Expected IllegalArgumentException for missing 'path'");
        } catch (IllegalArgumentException e) {
            assertEquals("'path' is required for for_each processor", e.getMessage());
        }
    }

    @Test
    public void testConstructor_EmptyPath() {
        Map<String, Object> config = new HashMap<>();
        config.put("path", "");
        config.put("processors", List.of(Map.of("type", "to_string")));

        try {
            new MLForEachProcessor(config);
            fail("Expected IllegalArgumentException for empty 'path'");
        } catch (IllegalArgumentException e) {
            assertEquals("'path' cannot be empty for for_each processor", e.getMessage());
        }
    }

    @Test
    public void testConstructor_NullPath() {
        Map<String, Object> config = new HashMap<>();
        config.put("path", null);
        config.put("processors", List.of(Map.of("type", "to_string")));

        try {
            new MLForEachProcessor(config);
            fail("Expected IllegalArgumentException for null 'path'");
        } catch (IllegalArgumentException e) {
            assertEquals("'path' cannot be empty for for_each processor", e.getMessage());
        }
    }

    @Test
    public void testConstructor_MissingProcessors() {
        Map<String, Object> config = new HashMap<>();
        config.put("path", "$.items[*]");

        try {
            new MLForEachProcessor(config);
            fail("Expected IllegalArgumentException for missing 'processors'");
        } catch (IllegalArgumentException e) {
            assertEquals("'processors' is required for for_each processor", e.getMessage());
        }
    }

    @Test
    public void testConstructor_NullProcessors() {
        Map<String, Object> config = new HashMap<>();
        config.put("path", "$.items[*]");
        config.put("processors", null);

        try {
            new MLForEachProcessor(config);
            fail("Expected IllegalArgumentException for null 'processors'");
        } catch (IllegalArgumentException e) {
            assertEquals("'processors' list cannot be empty for for_each processor", e.getMessage());
        }
    }

    @Test
    public void testConstructor_EmptyProcessors() {
        Map<String, Object> config = new HashMap<>();
        config.put("path", "$.items[*]");
        config.put("processors", List.of());

        try {
            new MLForEachProcessor(config);
            fail("Expected IllegalArgumentException for empty 'processors'");
        } catch (IllegalArgumentException e) {
            assertEquals("'processors' list cannot be empty for for_each processor", e.getMessage());
        }
    }

    @Test
    public void testProcess_SimpleArray() {
        Map<String, Object> config = Map
            .of("path", "$.items[*]", "processors", List.of(Map.of("type", "set_field", "path", "$.processed", "value", true)));

        MLForEachProcessor processor = new MLForEachProcessor(config);

        Map<String, Object> input = Map.of("items", List.of(Map.of("name", "item1"), Map.of("name", "item2"), Map.of("name", "item3")));

        Object result = processor.process(input);
        assertNotNull(result);
        assertTrue(result instanceof Map);

        Map<String, Object> resultMap = (Map<String, Object>) result;
        List<Map<String, Object>> items = (List<Map<String, Object>>) resultMap.get("items");
        assertEquals(3, items.size());

        for (Map<String, Object> item : items) {
            assertTrue(item.containsKey("processed"));
            assertEquals(true, item.get("processed"));
            assertTrue(item.containsKey("name"));
        }
    }

    @Test
    public void testProcess_AddMissingTypeField() {
        Map<String, Object> config = Map
            .of(
                "path",
                "$.messages[*].content[*]",
                "processors",
                List
                    .of(
                        Map
                            .of(
                                "type",
                                "conditional",
                                "path",
                                "$.type",
                                "routes",
                                List
                                    .of(
                                        Map
                                            .of(
                                                "not_exists",
                                                List
                                                    .of(
                                                        Map
                                                            .of(
                                                                "type",
                                                                "conditional",
                                                                "path",
                                                                "$.text",
                                                                "routes",
                                                                List
                                                                    .of(
                                                                        Map
                                                                            .of(
                                                                                "exists",
                                                                                List
                                                                                    .of(
                                                                                        Map
                                                                                            .of(
                                                                                                "type",
                                                                                                "set_field",
                                                                                                "path",
                                                                                                "$.type",
                                                                                                "value",
                                                                                                "text"
                                                                                            )
                                                                                    )
                                                                            )
                                                                    )
                                                            )
                                                    )
                                            )
                                    )
                            )
                    )
            );

        MLForEachProcessor processor = new MLForEachProcessor(config);

        Map<String, Object> input = Map
            .of(
                "messages",
                List
                    .of(
                        Map
                            .of(
                                "role",
                                "assistant",
                                "content",
                                List
                                    .of(
                                        Map.of("text", "Hello"),  // Missing type
                                        Map.of("text", "World", "type", "text")  // Has type
                                    )
                            ),
                        Map
                            .of(
                                "role",
                                "user",
                                "content",
                                List
                                    .of(
                                        Map.of("text", "Hi there")  // Missing type
                                    )
                            )
                    )
            );

        Object result = processor.process(input);
        assertNotNull(result);
        assertTrue(result instanceof Map);

        Map<String, Object> resultMap = (Map<String, Object>) result;
        List<Map<String, Object>> messages = (List<Map<String, Object>>) resultMap.get("messages");

        // Check first message, first content
        List<Map<String, Object>> content1 = (List<Map<String, Object>>) messages.get(0).get("content");
        assertEquals("text", content1.get(0).get("type"));

        // Check first message, second content (already had type)
        assertEquals("text", content1.get(1).get("type"));

        // Check second message, first content
        List<Map<String, Object>> content2 = (List<Map<String, Object>>) messages.get(1).get("content");
        assertEquals("text", content2.get(0).get("type"));
    }

    @Test
    public void testProcess_MultipleProcessors() {
        Map<String, Object> config = Map
            .of(
                "path",
                "$.items[*]",
                "processors",
                List
                    .of(
                        Map.of("type", "set_field", "path", "$.processed", "value", true),
                        Map.of("type", "set_field", "path", "$.version", "value", 2),
                        Map.of("type", "remove_jsonpath", "paths", List.of("$.internal_id"))
                    )
            );

        MLForEachProcessor processor = new MLForEachProcessor(config);

        Map<String, Object> input = Map
            .of("items", List.of(Map.of("name", "item1", "internal_id", "abc123"), Map.of("name", "item2", "internal_id", "def456")));

        Object result = processor.process(input);
        assertNotNull(result);
        assertTrue(result instanceof Map);

        Map<String, Object> resultMap = (Map<String, Object>) result;
        List<Map<String, Object>> items = (List<Map<String, Object>>) resultMap.get("items");
        assertEquals(2, items.size());

        for (Map<String, Object> item : items) {
            assertEquals(true, item.get("processed"));
            assertEquals(2, item.get("version"));
            assertTrue(!item.containsKey("internal_id"));
            assertTrue(item.containsKey("name"));
        }
    }

    @Test
    public void testProcess_EmptyArray() {
        MLForEachProcessor processor = new MLForEachProcessor(baseConfig);

        Map<String, Object> input = Map.of("items", List.of());

        Object result = processor.process(input);
        assertNotNull(result);
        assertTrue(result instanceof Map);

        Map<String, Object> resultMap = (Map<String, Object>) result;
        List<?> items = (List<?>) resultMap.get("items");
        assertEquals(0, items.size());
    }

    @Test
    public void testProcess_PathNotFound() {
        MLForEachProcessor processor = new MLForEachProcessor(baseConfig);

        Map<String, Object> input = Map.of("other_field", "value");

        Object result = processor.process(input);
        assertNotNull(result);

        // Should return original input unchanged
        assertEquals(input, result);
    }

    @Test
    public void testProcess_PathNotArray() {
        Map<String, Object> config = Map
            .of("path", "$.items", "processors", List.of(Map.of("type", "set_field", "path", "$.processed", "value", true)));

        MLForEachProcessor processor = new MLForEachProcessor(config);

        Map<String, Object> input = Map.of("items", "not an array");

        Object result = processor.process(input);
        assertNotNull(result);

        // Should return original input unchanged
        assertEquals(input, result);
    }

    @Test
    public void testProcess_NestedArrays() {
        // For nested arrays, process each level separately
        // First process the users array to add processors to each user's tags
        Map<String, Object> config = Map
            .of(
                "path",
                "$.users[*]",
                "processors",
                List
                    .of(
                        Map
                            .of(
                                "type",
                                "for_each",
                                "path",
                                "$.tags[*]",
                                "processors",
                                List.of(Map.of("type", "set_field", "path", "$.active", "value", true))
                            )
                    )
            );

        MLForEachProcessor processor = new MLForEachProcessor(config);

        Map<String, Object> input = Map
            .of(
                "users",
                List
                    .of(
                        Map.of("name", "user1", "tags", List.of(Map.of("label", "admin"), Map.of("label", "developer"))),
                        Map.of("name", "user2", "tags", List.of(Map.of("label", "user")))
                    )
            );

        Object result = processor.process(input);
        assertNotNull(result);
        assertTrue(result instanceof Map);

        Map<String, Object> resultMap = (Map<String, Object>) result;
        List<Map<String, Object>> users = (List<Map<String, Object>>) resultMap.get("users");

        // Check all tags have active field
        int tagCount = 0;
        for (Map<String, Object> user : users) {
            List<Map<String, Object>> tags = (List<Map<String, Object>>) user.get("tags");
            for (Map<String, Object> tag : tags) {
                assertEquals(true, tag.get("active"));
                assertTrue(tag.containsKey("label"));
                tagCount++;
            }
        }
        assertEquals(3, tagCount);
    }

    @Test
    public void testProcess_WithConditionalProcessor() {
        Map<String, Object> config = Map
            .of(
                "path",
                "$.items[*]",
                "processors",
                List
                    .of(
                        Map
                            .of(
                                "type",
                                "conditional",
                                "path",
                                "$.status",
                                "routes",
                                List
                                    .of(
                                        Map.of("active", List.of(Map.of("type", "set_field", "path", "$.priority", "value", "high"))),
                                        Map.of("inactive", List.of(Map.of("type", "set_field", "path", "$.priority", "value", "low")))
                                    )
                            )
                    )
            );

        MLForEachProcessor processor = new MLForEachProcessor(config);

        Map<String, Object> input = Map
            .of(
                "items",
                List
                    .of(
                        Map.of("name", "item1", "status", "active"),
                        Map.of("name", "item2", "status", "inactive"),
                        Map.of("name", "item3", "status", "active")
                    )
            );

        Object result = processor.process(input);
        assertNotNull(result);
        assertTrue(result instanceof Map);

        Map<String, Object> resultMap = (Map<String, Object>) result;
        List<Map<String, Object>> items = (List<Map<String, Object>>) resultMap.get("items");
        assertEquals(3, items.size());

        assertEquals("high", items.get(0).get("priority"));
        assertEquals("low", items.get(1).get("priority"));
        assertEquals("high", items.get(2).get("priority"));
    }

    @Test
    public void testProcess_ComplexRealWorldExample() {
        // This tests the actual use case: removing toolUse and adding missing type fields
        Map<String, Object> config = Map
            .of(
                "path",
                "$.messages[*].content[*]",
                "processors",
                List
                    .of(
                        Map
                            .of(
                                "type",
                                "conditional",
                                "path",
                                "$.type",
                                "routes",
                                List
                                    .of(
                                        Map
                                            .of(
                                                "not_exists",
                                                List
                                                    .of(
                                                        Map
                                                            .of(
                                                                "type",
                                                                "conditional",
                                                                "path",
                                                                "$.text",
                                                                "routes",
                                                                List
                                                                    .of(
                                                                        Map
                                                                            .of(
                                                                                "exists",
                                                                                List
                                                                                    .of(
                                                                                        Map
                                                                                            .of(
                                                                                                "type",
                                                                                                "set_field",
                                                                                                "path",
                                                                                                "$.type",
                                                                                                "value",
                                                                                                "text"
                                                                                            )
                                                                                    )
                                                                            )
                                                                    )
                                                            )
                                                    )
                                            )
                                    )
                            )
                    )
            );

        MLForEachProcessor processor = new MLForEachProcessor(config);

        Map<String, Object> input = Map
            .of(
                "system",
                "You are a helpful assistant",
                "messages",
                List
                    .of(
                        Map
                            .of(
                                "role",
                                "assistant",
                                "content",
                                List
                                    .of(
                                        Map.of("text", "I'll help you find information about flights from Beijing."),
                                        Map
                                            .of(
                                                "toolUse",
                                                Map
                                                    .of(
                                                        "input",
                                                        Map.of("question", "How many total flights from Beijing?"),
                                                        "name",
                                                        "get_flights_data",
                                                        "toolUseId",
                                                        "tooluse_D5Y3jJ0oSMmNH7RT9sa12w"
                                                    )
                                            )
                                    )
                            ),
                        Map
                            .of(
                                "role",
                                "user",
                                "content",
                                List.of(Map.of("text", "Please extract information from our conversation so far", "type", "text"))
                            )
                    )
            );

        Object result = processor.process(input);
        assertNotNull(result);
        assertTrue(result instanceof Map);

        Map<String, Object> resultMap = (Map<String, Object>) result;
        List<Map<String, Object>> messages = (List<Map<String, Object>>) resultMap.get("messages");

        // First message, first content should now have type
        List<Map<String, Object>> content1 = (List<Map<String, Object>>) messages.get(0).get("content");
        assertEquals("text", content1.get(0).get("type"));

        // First message, second content (toolUse) should not have type added
        Map<String, Object> content2 = content1.get(1);
        assertTrue(!content2.containsKey("type"));
        assertTrue(content2.containsKey("toolUse"));

        // Second message, first content already had type
        List<Map<String, Object>> content3 = (List<Map<String, Object>>) messages.get(1).get("content");
        assertEquals("text", content3.get(0).get("type"));
    }

    @Test
    public void testProcess_StringInput() {
        MLForEachProcessor processor = new MLForEachProcessor(baseConfig);

        String input = "{\"items\": [{\"name\": \"item1\"}, {\"name\": \"item2\"}]}";

        Object result = processor.process(input);
        assertNotNull(result);
        assertTrue(result instanceof Map);

        Map<String, Object> resultMap = (Map<String, Object>) result;
        List<Map<String, Object>> items = (List<Map<String, Object>>) resultMap.get("items");
        assertEquals(2, items.size());

        for (Map<String, Object> item : items) {
            assertEquals(true, item.get("processed"));
        }
    }

    @Test
    public void testProcess_SingleElementArray() {
        MLForEachProcessor processor = new MLForEachProcessor(baseConfig);

        Map<String, Object> input = Map.of("items", List.of(Map.of("name", "single")));

        Object result = processor.process(input);
        assertNotNull(result);
        assertTrue(result instanceof Map);

        Map<String, Object> resultMap = (Map<String, Object>) result;
        List<Map<String, Object>> items = (List<Map<String, Object>>) resultMap.get("items");
        assertEquals(1, items.size());
        assertEquals(true, items.get(0).get("processed"));
        assertEquals("single", items.get(0).get("name"));
    }
}
