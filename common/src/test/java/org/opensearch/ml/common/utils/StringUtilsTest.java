/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.utils;

import static org.junit.Assert.assertEquals;
import static org.opensearch.ml.common.utils.StringUtils.TO_STRING_FUNCTION_NAME;
import static org.opensearch.ml.common.utils.StringUtils.collectToStringPrefixes;
import static org.opensearch.ml.common.utils.StringUtils.parseParameters;
import static org.opensearch.ml.common.utils.StringUtils.toJson;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.text.StringSubstitutor;
import org.junit.Assert;
import org.junit.Test;

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
        Map<String, Object> response = StringUtils
            .fromJson("{\"key\": {\"nested_key\": \"nested_value\", \"nested_array\": [1, \"a\"]}}", "response");
        assertEquals(1, response.size());
        Assert.assertTrue(response.get("key") instanceof Map);
        Map nestedMap = (Map) response.get("key");
        assertEquals("nested_value", nestedMap.get("nested_key"));
        List list = (List) nestedMap.get("nested_array");
        assertEquals(2, list.size());
        assertEquals(1.0, list.get(0));
        assertEquals("a", list.get(1));
    }

    @Test
    public void fromJson_SimpleList() {
        Map<String, Object> response = StringUtils.fromJson("[1, \"a\"]", "response");
        assertEquals(1, response.size());
        Assert.assertTrue(response.get("response") instanceof List);
        List list = (List) response.get("response");
        assertEquals(1.0, list.get(0));
        assertEquals("a", list.get(1));
    }

    @Test
    public void fromJson_NestedList() {
        Map<String, Object> response = StringUtils.fromJson("[1, \"a\", [2, 3], {\"key\": \"value\"}]", "response");
        assertEquals(1, response.size());
        Assert.assertTrue(response.get("response") instanceof List);
        List list = (List) response.get("response");
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
        parameters.put("key4", new int[] { 10, 20 });
        parameters.put("key5", new Object[] { 1.01, "abc" });
        Map<String, String> parameterMap = StringUtils.getParameterMap(parameters);
        assertEquals(5, parameterMap.size());
        assertEquals("value1", parameterMap.get("key1"));
        assertEquals("2", parameterMap.get("key2"));
        assertEquals("2.1", parameterMap.get("key3"));
        assertEquals("[10,20]", parameterMap.get("key4"));
        assertEquals("[1.01,\"abc\"]", parameterMap.get("key5"));
    }

    @Test
    public void getInterfaceMap() {
        final Set<String> allowedInterfaceFieldNameList = new HashSet<>(Arrays.asList("input", "output"));
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("input", "value1");
        parameters.put("output", 2);
        parameters.put("key3", 2.1);
        parameters.put("key4", new int[] { 10, 20 });
        parameters.put("key5", new Object[] { 1.01, "abc" });
        Map<String, String> interfaceMap = StringUtils.filteredParameterMap(parameters, allowedInterfaceFieldNameList);
        Assert.assertEquals(2, interfaceMap.size());
        Assert.assertEquals("value1", interfaceMap.get("input"));
        Assert.assertEquals("2", interfaceMap.get("output"));
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

    @Test
    public void testGetErrorMessageWhenHiddenNull() {
        // Arrange
        String errorMessage = "An error occurred.";
        String modelId = "12345";

        String expected = "An error occurred. Model ID: 12345";

        // Act
        String result = StringUtils.getErrorMessage(errorMessage, modelId, null);

        // Assert
        assertEquals(expected, result);
    }

    /**
     * Tests the collectToStringPrefixes method with a map containing toString() method calls
     * in the values. Verifies that the method correctly extracts the prefixes of the toString()
     * method calls.
     */
    @Test
    public void testGetToStringPrefix() {
        Map<String, String> parameters = new HashMap<>();
        parameters
            .put(
                "prompt",
                "answer question based on context: ${parameters.context.toString()} and conversation history based on history: ${parameters.history.toString()}"
            );
        parameters.put("context", "${parameters.text.toString()}");

        List<String> prefixes = collectToStringPrefixes(parameters);
        List<String> expectPrefixes = new ArrayList<>();
        expectPrefixes.add("text");
        expectPrefixes.add("context");
        expectPrefixes.add("history");
        assertEquals(prefixes, expectPrefixes);
    }

    /**
     * Tests the parseParameters method with a map containing a list of strings as the value
     * for the "context" key. Verifies that the method correctly processes the list and adds
     * the processed value to the map with the expected key. Also tests the string substitution
     * using the processed values.
     */
    @Test
    public void testParseParametersListToString() {
        Map<String, String> parameters = new HashMap<>();
        parameters.put("prompt", "answer question based on context: ${parameters.context.toString()}");
        ArrayList<String> listOfDocuments = new ArrayList<>();
        listOfDocuments.add("document1");
        parameters.put("context", toJson(listOfDocuments));

        parseParameters(parameters);
        assertEquals(parameters.get("context" + TO_STRING_FUNCTION_NAME), "[\\\"document1\\\"]");

        String requestBody = "{\"prompt\": \"${parameters.prompt}\"}";
        StringSubstitutor substitutor = new StringSubstitutor(parameters, "${parameters.", "}");
        requestBody = substitutor.replace(requestBody);
        assertEquals(requestBody, "{\"prompt\": \"answer question based on context: [\\\"document1\\\"]\"}");
    }

    /**
     * Tests the parseParameters method with a map containing a list of strings as the value
     * for the "context" key, and the "prompt" value containing escaped characters. Verifies
     * that the method correctly processes the list and adds the processed value to the map
     * with the expected key. Also tests the string substitution using the processed values.
     */
    @Test
    public void testParseParametersListToStringWithEscapedPrompt() {
        Map<String, String> parameters = new HashMap<>();
        parameters
            .put(
                "prompt",
                "\\n\\nHuman: You are a professional data analyst. You will always answer question based on the given context first. If the answer is not directly shown in the context, you will analyze the data and find the answer. If you don't know the answer, just say I don't know. Context: ${parameters.context.toString()}. \\n\\n Human: please summarize the documents \\n\\n Assistant:"
            );
        ArrayList<String> listOfDocuments = new ArrayList<>();
        listOfDocuments.add("document1");
        parameters.put("context", toJson(listOfDocuments));

        parseParameters(parameters);
        assertEquals(parameters.get("context" + TO_STRING_FUNCTION_NAME), "[\\\"document1\\\"]");

        String requestBody = "{\"prompt\": \"${parameters.prompt}\"}";
        StringSubstitutor substitutor = new StringSubstitutor(parameters, "${parameters.", "}");
        requestBody = substitutor.replace(requestBody);
        assertEquals(
            requestBody,
            "{\"prompt\": \"\\n\\nHuman: You are a professional data analyst. You will always answer question based on the given context first. If the answer is not directly shown in the context, you will analyze the data and find the answer. If you don't know the answer, just say I don't know. Context: [\\\"document1\\\"]. \\n\\n Human: please summarize the documents \\n\\n Assistant:\"}"
        );
    }

    /**
     * Tests the parseParameters method with a map containing a list of strings as the value
     * for the "context" key, and the "prompt" value containing escaped characters. Verifies
     * that the method correctly processes the list and adds the processed value to the map
     * with the expected key. Also tests the string substitution using the processed values.
     */
    @Test
    public void testParseParametersListToStringModelConfig() {
        Map<String, String> parameters = new HashMap<>();
        parameters
            .put(
                "prompt",
                "\\n\\nHuman: You are a professional data analyst. You will always answer question based on the given context first. If the answer is not directly shown in the context, you will analyze the data and find the answer. If you don't know the answer, just say I don't know. Context: ${parameters.model_config.context.toString()}. \\n\\n Human: please summarize the documents \\n\\n Assistant:"
            );
        ArrayList<String> listOfDocuments = new ArrayList<>();
        listOfDocuments.add("document1");
        parameters.put("model_config.context", toJson(listOfDocuments));

        parseParameters(parameters);
        assertEquals(parameters.get("model_config.context" + TO_STRING_FUNCTION_NAME), "[\\\"document1\\\"]");

        String requestBody = "{\"prompt\": \"${parameters.prompt}\"}";
        StringSubstitutor substitutor = new StringSubstitutor(parameters, "${parameters.", "}");
        requestBody = substitutor.replace(requestBody);
        assertEquals(
            requestBody,
            "{\"prompt\": \"\\n\\nHuman: You are a professional data analyst. You will always answer question based on the given context first. If the answer is not directly shown in the context, you will analyze the data and find the answer. If you don't know the answer, just say I don't know. Context: [\\\"document1\\\"]. \\n\\n Human: please summarize the documents \\n\\n Assistant:\"}"
        );
    }

    /**
     * Tests the parseParameters method with a map containing a nested list of strings as the
     * value for the "context" key. Verifies that the method correctly processes the nested
     * list and adds the processed value to the map with the expected key. Also tests the
     * string substitution using the processed values.
     */
    @Test
    public void testParseParametersNestedListToString() {
        Map<String, String> parameters = new HashMap<>();
        parameters.put("prompt", "answer question based on context: ${parameters.context.toString()}");
        ArrayList<String> listOfDocuments = new ArrayList<>();
        listOfDocuments.add("document1");
        ArrayList<String> NestedListOfDocuments = new ArrayList<>();
        NestedListOfDocuments.add("document2");
        listOfDocuments.add(toJson(NestedListOfDocuments));
        parameters.put("context", toJson(listOfDocuments));

        parseParameters(parameters);
        assertEquals(parameters.get("context" + TO_STRING_FUNCTION_NAME), "[\\\"document1\\\",\\\"[\\\\\\\"document2\\\\\\\"]\\\"]");

        String requestBody = "{\"prompt\": \"${parameters.prompt}\"}";
        StringSubstitutor substitutor = new StringSubstitutor(parameters, "${parameters.", "}");
        requestBody = substitutor.replace(requestBody);
        assertEquals(
            requestBody,
            "{\"prompt\": \"answer question based on context: [\\\"document1\\\",\\\"[\\\\\\\"document2\\\\\\\"]\\\"]\"}"
        );
    }

    /**
     * Tests the parseParameters method with a map containing a map of strings as the value
     * for the "context" key. Verifies that the method correctly processes the map and adds
     * the processed value to the map with the expected key. Also tests the string substitution
     * using the processed values.
     */
    @Test
    public void testParseParametersMapToString() {
        Map<String, String> parameters = new HashMap<>();
        parameters
            .put(
                "prompt",
                "answer question based on context: ${parameters.context.toString()} and conversation history based on history: ${parameters.history.toString()}"
            );
        Map<String, String> mapOfDocuments = new HashMap<>();
        mapOfDocuments.put("name", "John");
        parameters.put("context", toJson(mapOfDocuments));
        parameters.put("history", "hello\n");
        parseParameters(parameters);
        assertEquals(parameters.get("context" + TO_STRING_FUNCTION_NAME), "{\\\"name\\\":\\\"John\\\"}");
        String requestBody = "{\"prompt\": \"${parameters.prompt}\"}";
        StringSubstitutor substitutor = new StringSubstitutor(parameters, "${parameters.", "}");
        requestBody = substitutor.replace(requestBody);
        assertEquals(
            requestBody,
            "{\"prompt\": \"answer question based on context: {\\\"name\\\":\\\"John\\\"} and conversation history based on history: hello\\n\"}"
        );
    }

    /**
     * Tests the parseParameters method with a map containing a nested map of strings as the
     * value for the "context" key. Verifies that the method correctly processes the nested
     * map and adds the processed value to the map with the expected key. Also tests the
     * string substitution using the processed values.
     */
    @Test
    public void testParseParametersNestedMapToString() {
        Map<String, String> parameters = new HashMap<>();
        parameters
            .put(
                "prompt",
                "answer question based on context: ${parameters.context.toString()} and conversation history based on history: ${parameters.history.toString()}"
            );
        Map<String, String> mapOfDocuments = new HashMap<>();
        mapOfDocuments.put("name", "John");
        Map<String, String> nestedMapOfDocuments = new HashMap<>();
        nestedMapOfDocuments.put("city", "New York");
        mapOfDocuments.put("hometown", toJson(nestedMapOfDocuments));
        parameters.put("context", toJson(mapOfDocuments));
        parameters.put("history", "hello\n");
        parseParameters(parameters);
        assertEquals(
            parameters.get("context" + TO_STRING_FUNCTION_NAME),
            "{\\\"hometown\\\":\\\"{\\\\\\\"city\\\\\\\":\\\\\\\"New York\\\\\\\"}\\\",\\\"name\\\":\\\"John\\\"}"
        );
        String requestBody = "{\"prompt\": \"${parameters.prompt}\"}";
        StringSubstitutor substitutor = new StringSubstitutor(parameters, "${parameters.", "}");
        requestBody = substitutor.replace(requestBody);
        assertEquals(
            requestBody,
            "{\"prompt\": \"answer question based on context: {\\\"hometown\\\":\\\"{\\\\\\\"city\\\\\\\":\\\\\\\"New York\\\\\\\"}\\\",\\\"name\\\":\\\"John\\\"} and conversation history based on history: hello\\n\"}"
        );
    }
}
