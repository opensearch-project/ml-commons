/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.utils;

import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class StringUtilsTest {

    @Test
    public void isJson_True() {
        Assert.assertTrue(StringUtils.isJson("{}"));
        Assert.assertTrue(StringUtils.isJson("[]"));
        Assert.assertTrue(StringUtils.isJson("{\"key\": \"value\"}"));
        Assert.assertTrue(StringUtils.isJson("{\"key\": 123}"));
        Assert.assertTrue(StringUtils.isJson("[1, 2, 3]"));
        Assert.assertTrue(StringUtils.isJson("[\"a\", \"b\"]"));
        Assert.assertTrue(StringUtils.isJson("[1, \"a\"]"));
        Assert.assertTrue(StringUtils.isJson("{\"key1\": \"value\", \"key2\": 123}"));
        Assert.assertTrue(StringUtils.isJson("{}"));
        Assert.assertTrue(StringUtils.isJson("[]"));
        Assert.assertTrue(StringUtils.isJson("[ ]"));
        Assert.assertTrue(StringUtils.isJson("[,]"));
        Assert.assertTrue(StringUtils.isJson("[abc]"));
        Assert.assertTrue(StringUtils.isJson("[\"abc\", 123]"));
    }

    @Test
    public void isJson_False() {
        Assert.assertFalse(StringUtils.isJson("{"));
        Assert.assertFalse(StringUtils.isJson("["));
        Assert.assertFalse(StringUtils.isJson("{\"key\": \"value}"));
        Assert.assertFalse(StringUtils.isJson("{\"key\": \"value\", \"key\": 123}"));
        Assert.assertFalse(StringUtils.isJson("[1, \"a]"));
        Assert.assertFalse(StringUtils.isJson("[]\""));
        Assert.assertFalse(StringUtils.isJson("[ ]\""));
        Assert.assertFalse(StringUtils.isJson("[,]\""));
        Assert.assertFalse(StringUtils.isJson("[,\"]"));
        Assert.assertFalse(StringUtils.isJson("[]\"123\""));
        Assert.assertFalse(StringUtils.isJson("[abc\"]"));
        Assert.assertFalse(StringUtils.isJson("[abc\n123]"));
    }

    @Test
    public void toUTF8() {
        String rawString = "\uD83D\uDE00\uD83D\uDE0D\uD83D\uDE1C";
        String utf8 = StringUtils.toUTF8(rawString);
        Assert.assertNotNull(utf8);
    }

    @Test
    public void fromJson_SimpleMap() {
        Map<String, Object> response = StringUtils.fromJson("{\"key\": \"value\"}", "response");
        assertEquals(1, response.size());
        assertEquals("value", response.get("key"));
    }

    @Test
    public void fromJson_NestedMap() {
        Map<String, Object> response = StringUtils.fromJson("{\"key\": {\"nested_key\": \"nested_value\", \"nested_array\": [1, \"a\"]}}", "response");
        assertEquals(1, response.size());
        Assert.assertTrue(response.get("key") instanceof Map);
        Map nestedMap = (Map)response.get("key");
        assertEquals("nested_value", nestedMap.get("nested_key"));
        List list = (List)nestedMap.get("nested_array");
        assertEquals(2, list.size());
        assertEquals(1.0, list.get(0));
        assertEquals("a", list.get(1));
    }

    @Test
    public void fromJson_SimpleList() {
        Map<String, Object> response = StringUtils.fromJson("[1, \"a\"]", "response");
        assertEquals(1, response.size());
        Assert.assertTrue(response.get("response") instanceof List);
        List list = (List)response.get("response");
        assertEquals(1.0, list.get(0));
        assertEquals("a", list.get(1));
    }

    @Test
    public void fromJson_NestedList() {
        Map<String, Object> response = StringUtils.fromJson("[1, \"a\", [2, 3], {\"key\": \"value\"}]", "response");
        assertEquals(1, response.size());
        Assert.assertTrue(response.get("response") instanceof List);
        List list = (List)response.get("response");
        assertEquals(1.0, list.get(0));
        assertEquals("a", list.get(1));
        Assert.assertTrue(list.get(2) instanceof List);
        Assert.assertTrue(list.get(3) instanceof Map);
    }

    @Test
    public void getParameterMap() {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("key1", "value1");
        parameters.put("key2", 2);
        parameters.put("key3", 2.1);
        parameters.put("key4", new int[]{10, 20});
        parameters.put("key5", new Object[]{1.01, "abc"});
        Map<String, String> parameterMap = StringUtils.getParameterMap(parameters);
        assertEquals(5, parameterMap.size());
        assertEquals("value1", parameterMap.get("key1"));
        assertEquals("2", parameterMap.get("key2"));
        assertEquals("2.1", parameterMap.get("key3"));
        assertEquals("[10,20]", parameterMap.get("key4"));
        assertEquals("[1.01,\"abc\"]", parameterMap.get("key5"));
    }

    @Test
    public void processTextDocs() {
        List<String> processedDocs = StringUtils.processTextDocs(Arrays.asList("abc \n\n123\"4", null, "[1.01,\"abc\"]"));
        assertEquals(3, processedDocs.size());
        assertEquals("abc \\n\\n123\\\"4", processedDocs.get(0));
        Assert.assertNull(processedDocs.get(1));
        assertEquals("[1.01,\\\"abc\\\"]", processedDocs.get(2));
    }

    @Test
    public void isEscapeUsed() {
        Assert.assertFalse(StringUtils.isEscapeUsed("String escape"));
        Assert.assertTrue(StringUtils.isEscapeUsed(" escape(\"abc\n123\")"));
    }

    @Test
    public void containsEscapeMethod() {
        Assert.assertFalse(StringUtils.containsEscapeMethod("String escape"));
        Assert.assertFalse(StringUtils.containsEscapeMethod("String escape()"));
        Assert.assertFalse(StringUtils.containsEscapeMethod(" escape(\"abc\n123\")"));
        Assert.assertTrue(StringUtils.containsEscapeMethod("String escape(def abc)"));
        Assert.assertTrue(StringUtils.containsEscapeMethod("String escape(String input)"));
    }

    @Test
    public void addDefaultMethod_NoEscape() {
        String input = "return 123;";
        String result = StringUtils.addDefaultMethod(input);
        assertEquals(input, result);
    }

    @Test
    public void addDefaultMethod_Escape() {
        String input = "return escape(\"abc\n123\");";
        String result = StringUtils.addDefaultMethod(input);
        Assert.assertNotEquals(input, result);
        Assert.assertTrue(result.startsWith(StringUtils.DEFAULT_ESCAPE_FUNCTION));
    }

    @Test
    public void testGetErrorMessage() {
        // Arrange
        String errorMessage = "An error occurred.";
        String modelId = "12345";
        boolean isHidden = false;
        String expected = "An error occurred. Model ID: 12345";

        // Act
        String result = StringUtils.getErrorMessage(errorMessage, modelId, isHidden);

        // Assert
        assertEquals(expected, result);
    }

    @Test
    public void testGetErrorMessageWhenHidden() {
        // Arrange
        String errorMessage = "An error occurred.";
        String modelId = "12345";
        boolean isHidden = true;
        String expected = "An error occurred.";

        // Act
        String result = StringUtils.getErrorMessage(errorMessage, modelId, isHidden);

        // Assert
        assertEquals(expected, result);
    }
}
