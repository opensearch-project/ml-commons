/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.opensearch.ml.common.utils.StringUtils.*;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.apache.commons.text.StringSubstitutor;
import org.junit.Assert;
import org.junit.Test;
import org.opensearch.OpenSearchParseException;
import org.opensearch.action.ActionRequestValidationException;
import org.opensearch.ml.common.output.model.MLResultDataType;
import org.opensearch.ml.common.output.model.ModelTensor;
import org.opensearch.ml.common.output.model.ModelTensorOutput;
import org.opensearch.ml.common.output.model.ModelTensors;

import com.google.gson.JsonElement;
import com.google.gson.TypeAdapter;
import com.google.gson.reflect.TypeToken;
import com.jayway.jsonpath.JsonPath;

public class StringUtilsTest {

    @Test
    public void isJson_True() {
        assertTrue(StringUtils.isJson("{}"));
        assertTrue(StringUtils.isJson("[]"));
        assertTrue(StringUtils.isJson("{\"key\": \"value\"}"));
        assertTrue(StringUtils.isJson("{\"key\": 123}"));
        assertTrue(StringUtils.isJson("[1, 2, 3]"));
        assertTrue(StringUtils.isJson("[\"a\", \"b\"]"));
        assertTrue(StringUtils.isJson("[1, \"a\"]"));
        assertTrue(StringUtils.isJson("{\"key1\": \"value\", \"key2\": 123}"));
        assertTrue(StringUtils.isJson("{}"));
        assertTrue(StringUtils.isJson("[]"));
        assertTrue(StringUtils.isJson("[ ]"));
        assertTrue(StringUtils.isJson("[,]"));
        assertTrue(StringUtils.isJson("[abc]"));
        assertTrue(StringUtils.isJson("[\"abc\", 123]"));
    }

    @Test
    public void isJson_False() {
        assertFalse(StringUtils.isJson("{"));
        assertFalse(StringUtils.isJson("["));
        assertFalse(StringUtils.isJson("{\"key\": \"value}"));
        assertFalse(StringUtils.isJson("{\"key\": \"value\", \"key\": 123}"));
        assertFalse(StringUtils.isJson("[1, \"a]"));
        assertFalse(StringUtils.isJson("[]\""));
        assertFalse(StringUtils.isJson("[ ]\""));
        assertFalse(StringUtils.isJson("[,]\""));
        assertFalse(StringUtils.isJson("[,\"]"));
        assertFalse(StringUtils.isJson("[]\"123\""));
        assertFalse(StringUtils.isJson("[abc\"]"));
        assertFalse(StringUtils.isJson("[abc\n123]"));
    }

    @Test
    public void toUTF8() {
        String rawString = "\uD83D\uDE00\uD83D\uDE0D\uD83D\uDE1C";
        String utf8 = StringUtils.toUTF8(rawString);
        assertNotNull(utf8);
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
        assertTrue(response.get("key") instanceof Map);
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
        assertTrue(response.get("response") instanceof List);
        List list = (List) response.get("response");
        assertEquals(1.0, list.get(0));
        assertEquals("a", list.get(1));
    }

    @Test
    public void fromJson_NestedList() {
        Map<String, Object> response = StringUtils.fromJson("[1, \"a\", [2, 3], {\"key\": \"value\"}]", "response");
        assertEquals(1, response.size());
        assertTrue(response.get("response") instanceof List);
        List list = (List) response.get("response");
        assertEquals(1.0, list.get(0));
        assertEquals("a", list.get(1));
        assertTrue(list.get(2) instanceof List);
        assertTrue(list.get(3) instanceof Map);
    }

    @Test
    public void fromJsonWithWrappingKey_SimpleMap() {
        Map<String, Object> response = StringUtils.fromJsonWithWrappingKey("{\"key\": \"value\"}", "wrapper");
        assertEquals(1, response.size());
        assertTrue(response.get("wrapper") instanceof Map);
        Map wrappedMap = (Map) response.get("wrapper");
        assertEquals("value", wrappedMap.get("key"));
    }

    @Test
    public void fromJsonWithWrappingKey_NestedMap() {
        Map<String, Object> response = StringUtils
            .fromJsonWithWrappingKey("{\"key\": {\"nested_key\": \"nested_value\", \"nested_array\": [1, \"a\"]}}", "wrapper");
        assertEquals(1, response.size());
        assertTrue(response.get("wrapper") instanceof Map);
        Map wrappedMap = (Map) response.get("wrapper");
        assertTrue(wrappedMap.get("key") instanceof Map);
        Map nestedMap = (Map) wrappedMap.get("key");
        assertEquals("nested_value", nestedMap.get("nested_key"));
        List list = (List) nestedMap.get("nested_array");
        assertEquals(2, list.size());
        assertEquals(1.0, list.get(0));
        assertEquals("a", list.get(1));
    }

    @Test
    public void fromJsonWithWrappingKey_SimpleList() {
        Map<String, Object> response = StringUtils.fromJsonWithWrappingKey("[1, \"a\"]", "wrapper");
        assertEquals(1, response.size());
        assertTrue(response.get("wrapper") instanceof List);
        List list = (List) response.get("wrapper");
        assertEquals(1.0, list.get(0));
        assertEquals("a", list.get(1));
    }

    @Test
    public void fromJsonWithWrappingKey_NestedList() {
        Map<String, Object> response = StringUtils.fromJsonWithWrappingKey("[1, \"a\", [2, 3], {\"key\": \"value\"}]", "wrapper");
        assertEquals(1, response.size());
        assertTrue(response.get("wrapper") instanceof List);
        List list = (List) response.get("wrapper");
        assertEquals(1.0, list.get(0));
        assertEquals("a", list.get(1));
        assertTrue(list.get(2) instanceof List);
        assertTrue(list.get(3) instanceof Map);
    }

    @Test
    public void fromJsonWithWrappingKey_EmptyObject() {
        Map<String, Object> response = StringUtils.fromJsonWithWrappingKey("{}", "wrapper");
        assertEquals(1, response.size());
        assertTrue(response.get("wrapper") instanceof Map);
        Map wrappedMap = (Map) response.get("wrapper");
        assertTrue(wrappedMap.isEmpty());
    }

    @Test
    public void fromJsonWithWrappingKey_EmptyArray() {
        Map<String, Object> response = StringUtils.fromJsonWithWrappingKey("[]", "wrapper");
        assertEquals(1, response.size());
        assertTrue(response.get("wrapper") instanceof List);
        List list = (List) response.get("wrapper");
        assertTrue(list.isEmpty());
    }

    @Test
    public void fromJsonWithWrappingKey_UnsupportedType() {
        assertThrows(IllegalArgumentException.class, () -> { StringUtils.fromJsonWithWrappingKey("\"simple string\"", "wrapper"); });
    }

    @Test
    public void fromJsonWithWrappingKey_UnsupportedNumber() {
        assertThrows(IllegalArgumentException.class, () -> { StringUtils.fromJsonWithWrappingKey("42", "wrapper"); });
    }

    @Test
    public void fromJsonWithWrappingKey_UnsupportedBoolean() {
        assertThrows(IllegalArgumentException.class, () -> { StringUtils.fromJsonWithWrappingKey("true", "wrapper"); });
    }

    @Test
    public void fromJsonWithWrappingKey_UnsupportedNull() {
        assertThrows(IllegalArgumentException.class, () -> { StringUtils.fromJsonWithWrappingKey("null", "wrapper"); });
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
    public void getParameterMapWithNullInput() {
        Map<String, String> parameterMap = StringUtils.getParameterMap(null);
        Assert.assertTrue(parameterMap.isEmpty());
    }

    @Test
    public void getParameterMapWithEmptyInput() {
        Map<String, String> parameterMap = StringUtils.getParameterMap(Map.of());
        Assert.assertTrue(parameterMap.isEmpty());
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
        assertNull(processedDocs.get(1));
        assertEquals("[1.01,\\\"abc\\\"]", processedDocs.get(2));
    }

    @Test
    public void isEscapeUsed() {
        assertFalse(StringUtils.isEscapeUsed("String escape"));
        assertTrue(StringUtils.isEscapeUsed(" escape(\"abc\n123\")"));
    }

    @Test
    public void containsEscapeMethod() {
        assertFalse(StringUtils.containsEscapeMethod("String escape"));
        assertFalse(StringUtils.containsEscapeMethod("String escape()"));
        assertFalse(StringUtils.containsEscapeMethod(" escape(\"abc\n123\")"));
        assertTrue(StringUtils.containsEscapeMethod("String escape(def abc)"));
        assertTrue(StringUtils.containsEscapeMethod("String escape(String input)"));
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
        assertNotEquals(input, result);
        assertTrue(result.startsWith(StringUtils.DEFAULT_ESCAPE_FUNCTION));
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
    public void testCollectToStringPrefixes() {
        Map<String, String> map = new HashMap<>();
        map.put("key1", "${parameters.tensor.toString()}");
        map.put("key2", "${parameters.output.toString()}");
        map.put("key3", "normal value");

        List<String> prefixes = StringUtils.collectToStringPrefixes(map);

        assertEquals(2, prefixes.size());
        assertTrue(prefixes.contains("tensor"));
        assertTrue(prefixes.contains("output"));
    }

    @Test
    public void test_GsonTypeAdapters() {
        // Test ModelTensor serialization
        ModelTensor tensor = ModelTensor
            .builder()
            .name("test_tensor")
            .data(new Number[] { 1, 2, 3 })
            .dataType(MLResultDataType.INT32)
            .build();

        String tensorJson = StringUtils.gson.toJson(tensor);
        assertEquals(tensor.toString(), tensorJson);

        // Test ModelTensorOutput serialization
        List<ModelTensors> outputs = new ArrayList<>();
        outputs.add(ModelTensors.builder().mlModelTensors(Arrays.asList(tensor)).build());
        ModelTensorOutput output = ModelTensorOutput.builder().mlModelOutputs(outputs).build();

        String outputJson = StringUtils.gson.toJson(output);
        assertEquals(output.toString(), outputJson);

        // Test ModelTensors serialization
        ModelTensors tensors = ModelTensors.builder().mlModelTensors(Arrays.asList(tensor)).build();

        String tensorsJson = StringUtils.gson.toJson(tensors);
        assertEquals(tensors.toString(), tensorsJson);
    }

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

    @Test
    public void testObtainFieldNameFromJsonPath_ValidJsonPath() {
        // Test with a typical JSONPath
        String jsonPath = "$.response.body.data[*].embedding";
        String fieldName = obtainFieldNameFromJsonPath(jsonPath);
        assertEquals("embedding", fieldName);
    }

    @Test
    public void testObtainFieldNameFromJsonPath_WithPrefix() {
        // Test with JSONPath that has a prefix
        String jsonPath = "source[1].$.response.body.data[*].embedding";
        String fieldName = obtainFieldNameFromJsonPath(jsonPath);
        assertEquals("embedding", fieldName);
    }

    @Test
    public void testGetJsonPath_ValidJsonPathWithSource() {
        // Test with a JSONPath that includes a source prefix
        String input = "source[1].$.response.body.data[*].embedding";
        String result = getJsonPath(input);
        assertEquals("$.response.body.data[*].embedding", result);
    }

    @Test
    public void testGetJsonPath_ValidJsonPathWithoutSource() {
        // Test with a JSONPath that does not include a source prefix
        String input = "$.response.body.data[*].embedding";
        String result = getJsonPath(input);
        assertEquals("$.response.body.data[*].embedding", result);
    }

    @Test
    public void testisValidJSONPath_InvalidInputs() {
        assertFalse(isValidJSONPath("..bar"));
        assertFalse(isValidJSONPath("."));
        assertFalse(isValidJSONPath(".."));
        assertFalse(isValidJSONPath("foo.bar."));
        assertFalse(isValidJSONPath(".foo.bar."));
    }

    @Test
    public void testisValidJSONPath_NullInput() {
        assertFalse(isValidJSONPath(null));
    }

    @Test
    public void testisValidJSONPath_EmptyInput() {
        assertFalse(isValidJSONPath(""));
    }

    @Test
    public void testisValidJSONPath_ValidInputs() {
        assertTrue(isValidJSONPath("foo"));
        assertTrue(isValidJSONPath("foo.bar"));
        assertTrue(isValidJSONPath("foo.bar.baz"));
        assertTrue(isValidJSONPath("foo.bar.baz.qux"));
        assertTrue(isValidJSONPath(".foo"));
        assertTrue(isValidJSONPath("$.foo"));
        assertTrue(isValidJSONPath(".foo.bar"));
        assertTrue(isValidJSONPath("$.foo.bar"));
    }

    @Test
    public void testisValidJSONPath_WithFilter() {
        assertTrue(isValidJSONPath("$.store['book']"));
        assertTrue(isValidJSONPath("$['store']['book'][0]['title']"));
        assertTrue(isValidJSONPath("$.store.book[0]"));
        assertTrue(isValidJSONPath("$.store.book[1,2]"));
        assertTrue(isValidJSONPath("$.store.book[-1:] "));
        assertTrue(isValidJSONPath("$.store.book[0:2]"));
        assertTrue(isValidJSONPath("$.store.book[*]"));
        assertTrue(isValidJSONPath("$.store.book[?(@.price < 10)]"));
        assertTrue(isValidJSONPath("$.store.book[?(@.author == 'J.K. Rowling')]"));
        assertTrue(isValidJSONPath("$..author"));
        assertTrue(isValidJSONPath("$..book[?(@.price > 15)]"));
        assertTrue(isValidJSONPath("$.store.book[0,1]"));
        assertTrue(isValidJSONPath("$['store','warehouse']"));
        assertTrue(isValidJSONPath("$..book[?(@.price > 20)].title"));
    }

    @Test
    public void testPathExists_ExistingPath() {
        Object json = JsonPath.parse("{\"a\":{\"b\":42}}").json();
        assertTrue(StringUtils.pathExists(json, "$.a.b"));
    }

    @Test
    public void testPathExists_NonExistingPath() {
        Object json = JsonPath.parse("{\"a\":{\"b\":42}}").json();
        assertFalse(StringUtils.pathExists(json, "$.a.c"));
    }

    @Test
    public void testPathExists_EmptyObject() {
        Object json = JsonPath.parse("{}").json();
        assertFalse(StringUtils.pathExists(json, "$.a"));
    }

    @Test
    public void testPathExists_NullJson() {
        assertThrows(IllegalArgumentException.class, () -> StringUtils.pathExists(null, "$.a"));
    }

    @Test
    public void testPathExists_NullPath() {
        Object json = JsonPath.parse("{\"a\":42}").json();
        assertThrows(IllegalArgumentException.class, () -> StringUtils.pathExists(json, null));
    }

    @Test
    public void testPathExists_EmptyPath() {
        Object json = JsonPath.parse("{\"a\":42}").json();
        assertThrows(IllegalArgumentException.class, () -> StringUtils.pathExists(json, ""));
    }

    @Test
    public void testPathExists_InvalidPath() {
        Object json = JsonPath.parse("{\"a\":42}").json();
        assertThrows(IllegalArgumentException.class, () -> StringUtils.pathExists(json, "This is not a valid path"));
    }

    @Test
    public void testPathExists_ArrayElement() {
        Object json = JsonPath.parse("{\"a\":[1,2,3]}").json();
        assertTrue(StringUtils.pathExists(json, "$.a[1]"));
        assertFalse(StringUtils.pathExists(json, "$.a[3]"));
    }

    @Test
    public void testPathExists_NestedStructure() {
        Object json = JsonPath.parse("{\"a\":{\"b\":{\"c\":{\"d\":42}}}}").json();
        assertTrue(StringUtils.pathExists(json, "$.a.b.c.d"));
        assertFalse(StringUtils.pathExists(json, "$.a.b.c.e"));
    }

    @Test
    public void testPathExists_InvalidJson() {
        String invalidJson = "{invalid json}";
        assertFalse(StringUtils.pathExists(invalidJson, "$.a"));
    }

    @Test
    public void testPrepareNestedStructures_InvalidJsonPath() {
        Object jsonObject = new HashMap<>();
        String invalidFieldPath = "a.[.b";  // Invalid JSON path with single square bracket

        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> StringUtils.prepareNestedStructures(jsonObject, invalidFieldPath)
        );

        assertEquals("The field path is not a valid JSON path: " + invalidFieldPath, exception.getMessage());
    }

    @Test
    public void testPrepareNestedStructures_ExistingObject() {
        Map<String, Object> jsonObject = new HashMap<>();
        Map<String, Object> existingMap = new HashMap<>();
        jsonObject.put("a", existingMap);

        Object result = StringUtils.prepareNestedStructures(jsonObject, "a.b.c");

        assertTrue(jsonObject.get("a") instanceof Map);
        Map<String, Object> aMap = (Map<String, Object>) jsonObject.get("a");
        assertTrue(aMap.get("b") instanceof Map);
        Map<String, Object> bMap = (Map<String, Object>) aMap.get("b");
        assertTrue(bMap.containsKey("c"));
    }

    @Test
    public void testPrepareNestedStructures_ExistingArray() {
        Map<String, Object> jsonObject = new HashMap<>();
        List<Object> existingList = new ArrayList<>();
        existingList.add(new HashMap<>());
        jsonObject.put("a", existingList);

        Object result = StringUtils.prepareNestedStructures(jsonObject, "a[1].b");

        assertTrue(jsonObject.get("a") instanceof List);
        List<Object> aList = (List<Object>) jsonObject.get("a");
        assertEquals(2, aList.size());
        assertTrue(aList.get(1) instanceof Map);
        Map<String, Object> aMap = (Map<String, Object>) aList.get(1);
        assertTrue(aMap.containsKey("b"));
    }

    @Test
    public void testPrepareNestedStructures_NonMapInArray() {
        Map<String, Object> jsonObject = new HashMap<>();
        List<Object> existingList = new ArrayList<>();
        existingList.add("not a map");
        jsonObject.put("a", existingList);

        Object result = StringUtils.prepareNestedStructures(jsonObject, "a[0].b");

        assertEquals(jsonObject, result);
        assertTrue(jsonObject.get("a") instanceof List);
        List<Object> aList = (List<Object>) jsonObject.get("a");
        assertEquals("not a map", aList.get(0));
    }

    @Test
    public void testPrepareNestedStructures_NonListForArrayNotation() {
        Map<String, Object> jsonObject = new HashMap<>();
        jsonObject.put("a", "not a list");

        Object result = StringUtils.prepareNestedStructures(jsonObject, "a[0].b");

        assertEquals(jsonObject, result);
        assertEquals("not a list", jsonObject.get("a"));
    }

    @Test
    public void testPrepareNestedStructures_ArrayNotation() {
        Map<String, Object> jsonObject = new HashMap<>();
        Object result = StringUtils.prepareNestedStructures(jsonObject, "a[0].b[1].c");

        assertTrue(jsonObject.get("a") instanceof List);
        List<Object> aList = (List<Object>) jsonObject.get("a");
        assertTrue(aList.get(0) instanceof Map);
        Map<String, Object> aMap = (Map<String, Object>) aList.get(0);
        assertTrue(aMap.get("b") instanceof List);
        List<Object> bList = (List<Object>) aMap.get("b");
        assertTrue(bList.get(1) instanceof Map);
        Map<String, Object> bMap = (Map<String, Object>) bList.get(1);
        assertTrue(bMap.containsKey("c"));
    }

    @Test
    public void testPrepareNestedStructures_EmptyObject() {
        Object jsonObject = new HashMap<>();
        Object result = StringUtils.prepareNestedStructures(jsonObject, "a.b.c");
        assertTrue(JsonPath.read(result, "$.a.b") instanceof Map);
    }

    @Test
    public void testPrepareNestedStructures_ExistingStructure() {
        Object jsonObject = JsonPath.parse("{\"a\":{\"b\":{}}}").json();
        Object result = StringUtils.prepareNestedStructures(jsonObject, "a.b.c");
        assertTrue(JsonPath.read(result, "$.a.b") instanceof Map);
    }

    @Test
    public void testPrepareNestedStructures_PartiallyExistingStructure() {
        Object jsonObject = JsonPath.parse("{\"a\":{}}").json();
        Object result = StringUtils.prepareNestedStructures(jsonObject, "a.b.c.d");
        assertTrue(JsonPath.read(result, "$.a.b.c") instanceof Map);
    }

    @Test
    public void testPrepareNestedStructures_WithDollarSign() {
        Object jsonObject = new HashMap<>();
        Object result = StringUtils.prepareNestedStructures(jsonObject, "$.a.b.c");
        assertTrue(JsonPath.read(result, "$.a.b") instanceof Map);
    }

    @Test
    public void testPrepareNestedStructures_SingleLevel() {
        Object jsonObject = new HashMap<>();
        Object result = StringUtils.prepareNestedStructures(jsonObject, "a");
        assertEquals(jsonObject, result);
    }

    @Test
    public void testPrepareNestedStructures_ExistingValue() {
        Object jsonObject = JsonPath.parse("{\"a\":{\"b\":42}}").json();
        Object result = StringUtils.prepareNestedStructures(jsonObject, "a.b.c");
        assertEquals(Optional.ofNullable(42), Optional.ofNullable(JsonPath.read(result, "$.a.b")));
    }

    @Test
    public void testPrepareNestedStructures_NullInput() {
        assertThrows(IllegalArgumentException.class, () -> StringUtils.prepareNestedStructures(null, "a.b.c"));
    }

    @Test
    public void testPrepareNestedStructures_NullPath() {
        Object jsonObject = new HashMap<>();
        assertThrows(IllegalArgumentException.class, () -> StringUtils.prepareNestedStructures(jsonObject, null));
    }

    @Test
    public void testPrepareNestedStructures_ComplexPath() {
        Object jsonObject = new HashMap<>();
        Object result = StringUtils.prepareNestedStructures(jsonObject, "a.b.c.d.e.f");
        assertTrue(JsonPath.read(result, "$.a.b.c.d.e") instanceof Map);
    }

    @Test
    public void testPrepareNestedStructures_MixedExistingAndNew() {
        Object jsonObject = JsonPath.parse("{\"a\":{\"b\":42,\"c\":{}}}").json();
        Object result = StringUtils.prepareNestedStructures(jsonObject, "a.c.d.e");
        assertEquals(Optional.of(42), Optional.of(JsonPath.read(result, "$.a.b")));
        assertTrue(JsonPath.read(result, "$.a.c.d") instanceof Map);
    }

    @Test
    public void testValidateSchema() throws IOException {
        String schema = "{"
            + "\"type\": \"object\","
            + "\"properties\": {"
            + "    \"key1\": {\"type\": \"string\"},"
            + "    \"key2\": {\"type\": \"integer\"}"
            + "},"
            + "\"required\": [\"key1\", \"key2\"]"
            + "}";
        String json = "{\"key1\": \"foo\", \"key2\": 123}";
        StringUtils.validateSchema(schema, json);

        String json2 = "{\"key1\": \"foo\"}";
        assertThrows(OpenSearchParseException.class, () -> StringUtils.validateSchema(schema, json2));
    }

    @Test
    public void testIsSafeText_ValidInputs() {
        assertTrue(StringUtils.isSafeText("Model-Name_1.0"));
        assertTrue(StringUtils.isSafeText("This is a description:"));
        assertTrue(StringUtils.isSafeText("Name_with-dots.and:colons"));
    }

    @Test
    public void testValidateFields_AllValid() {
        Map<String, FieldDescriptor> fields = Map
            .of("Field1", new FieldDescriptor("Valid Name 1", true), "Field2", new FieldDescriptor("Another_Valid-Field.Name:Here", true));
        assertNull(StringUtils.validateFields(fields));
    }

    @Test
    public void testValidateFields_OptionalFieldsValidWhenBlank() {
        Map<String, FieldDescriptor> fields = Map
            .of(
                "OptionalField1",
                new FieldDescriptor("", false),
                "OptionalField2",
                new FieldDescriptor("   ", false),
                "OptionalField3",
                new FieldDescriptor(null, false)
            );
        assertNull(StringUtils.validateFields(fields));
    }

    @Test
    public void testValidateFields_OptionalFieldInvalidPattern() {
        Map<String, FieldDescriptor> fields = Map.of("OptionalField1", new FieldDescriptor("Bad@Value$", false));
        ActionRequestValidationException exception = StringUtils.validateFields(fields);
        assertNotNull(exception);
        assertTrue(exception.getMessage().contains("OptionalField1"));
    }

    @Test
    public void testIsSafeText_AdvancedValidInputs() {
        // Testing all allowed characters
        assertTrue(StringUtils.isSafeText("Hello World"));  // spaces
        assertTrue(StringUtils.isSafeText("Hello.World"));  // period
        assertTrue(StringUtils.isSafeText("Hello,World"));  // comma
        assertTrue(StringUtils.isSafeText("Hello!World"));  // exclamation
        assertTrue(StringUtils.isSafeText("Hello?World"));  // question mark
        assertTrue(StringUtils.isSafeText("Hello(World)")); // parentheses
        assertTrue(StringUtils.isSafeText("Hello:World"));  // colon
        assertTrue(StringUtils.isSafeText("Hello@World"));  // at sign
        assertTrue(StringUtils.isSafeText("Hello-World"));  // hyphen
        assertTrue(StringUtils.isSafeText("Hello_World"));  // underscore
        assertTrue(StringUtils.isSafeText("Hello'World")); // single quote
        assertTrue(StringUtils.isSafeText("Hello\"World")); // double quote
    }

    @Test
    public void testIsSafeText_AdvancedInvalidInputs() {
        // Testing specifically excluded characters
        assertFalse(StringUtils.isSafeText("Hello<World"));  // less than
        assertFalse(StringUtils.isSafeText("Hello>World"));  // greater than
        assertTrue(StringUtils.isSafeText("Hello/World"));  // forward slash
        assertFalse(StringUtils.isSafeText("Hello\\World")); // backslash
        assertFalse(StringUtils.isSafeText("Hello&World"));  // ampersand
        assertFalse(StringUtils.isSafeText("Hello+World"));  // plus
        assertFalse(StringUtils.isSafeText("Hello=World"));  // equals
        assertFalse(StringUtils.isSafeText("Hello;World"));  // semicolon
        assertFalse(StringUtils.isSafeText("Hello|World"));  // pipe
        assertFalse(StringUtils.isSafeText("Hello*World"));  // asterisk
    }

    @Test
    public void testValidateFields_RequiredFields_MissingOrInvalid() {
        Map<String, FieldDescriptor> fields = new HashMap<>();
        fields.put("RequiredField1", new FieldDescriptor("", true));
        fields.put("RequiredField2", new FieldDescriptor("   ", true));
        fields.put("RequiredField3", new FieldDescriptor("Bad@#Char$", true));
        fields.put("RequiredField4", new FieldDescriptor(null, true));

        ActionRequestValidationException exception = StringUtils.validateFields(fields);
        assertNotNull(exception);
        String message = exception.getMessage();
        assertTrue(message.contains("RequiredField1"));
        assertTrue(message.contains("RequiredField2"));
        assertTrue(message.contains("RequiredField3"));
        assertTrue(message.contains("RequiredField4"));
    }

    @Test
    public void testValidateFields_EmptyMap() {
        Map<String, FieldDescriptor> fields = new HashMap<>();
        assertNull(StringUtils.validateFields(fields));
    }

    @Test
    public void testValidateFields_UnicodeLettersAndNumbers() {
        Map<String, FieldDescriptor> fields = Map
            .of("field1", new FieldDescriptor("Hello世界123", true), "field2", new FieldDescriptor("Café42", true));
        assertNull(StringUtils.validateFields(fields));
    }

    @Test
    public void testValidateFields_InvalidCharacterSet() {
        Map<String, FieldDescriptor> fields = Map.of("Field1", new FieldDescriptor("Bad#Value$With^Weird*Chars", true));
        ActionRequestValidationException exception = StringUtils.validateFields(fields);
        assertNotNull(exception);
        assertTrue(exception.getMessage().contains("Field1"));
    }

    @Test
    public void prepareJsonValue_returnsRawIfJson() {
        String json = "{\"key\": 123}";
        String result = StringUtils.prepareJsonValue(json);
        assertSame(json, result);  // branch where isJson(input)==true
    }

    @Test
    public void prepareJsonValue_escapesBadCharsOtherwise() {
        String input = "Tom & Jerry \"<script>";
        String escaped = StringUtils.prepareJsonValue(input);
        assertNotEquals(input, escaped);
        assertFalse(StringUtils.isJson(escaped));
        assertEquals("Tom & Jerry \\\"<script>", escaped);
    }

    @Test
    public void testParseStringArrayToList_validJsonArray() {
        // Arrange
        String jsonArray = "[\"apple\", \"banana\", \"cherry\"]";

        // Act
        List<String> result = parseStringArrayToList(jsonArray);

        // Assert
        assertEquals(Arrays.asList("apple", "banana", "cherry"), result);
    }

    @Test
    public void testParseStringArrayToList_emptyArray() {
        // Arrange
        String jsonArray = "[]";

        // Act
        List<String> result = parseStringArrayToList(jsonArray);

        // Assert
        assertTrue(result.isEmpty());
    }

    @Test
    public void testParseStringArrayToList_withSpecialCharacters() {
        // Arrange
        String jsonArray = "[\"hello\", \"world!\", \"special: @#$%^&*()\"]";

        // Act
        List<String> result = parseStringArrayToList(jsonArray);

        // Assert
        assertEquals(Arrays.asList("hello", "world!", "special: @#$%^&*()"), result);
    }

    @Test
    public void testParseStringArrayToList_withNullElement() {
        // Arrange
        String jsonArray = "[\"first\", null, \"third\"]";

        // Act
        List<String> result = parseStringArrayToList(jsonArray);

        // Assert
        assertEquals(3, result.size());
        assertEquals("first", result.get(0));
        assertNull(result.get(1));
        assertEquals("third", result.get(2));
    }

    @Test
    public void testParseStringArrayToList_jsonWithTrailingComma() {
        // Arrange
        String jsonWithTrailingComma = "[\"apple\", \"banana\",]"; // Invalid trailing comma

        List<String> result = parseStringArrayToList(jsonWithTrailingComma);

        // Assert
        assertEquals(Arrays.asList("apple", "banana", null), result);
        assertEquals(3, result.size());
    }

    @Test
    public void testParseStringArrayToList_nonArrayJson() {
        // Arrange
        String nonArrayJson = "{\"key\": \"value\"}";

        // Act & Assert
        List<String> array = parseStringArrayToList(nonArrayJson);
        assertEquals(0, array.size());
    }

    @Test
    public void testParseStringArrayToList_Null() {
        List<String> array = parseStringArrayToList(null);
        assertEquals(0, array.size());
    }

    // reflect method for PlainDoubleAdapter
    private static TypeAdapter<Double> createPlainDoubleAdapter() {
        try {
            Class<?> clazz = Class.forName("org.opensearch.ml.common.utils.StringUtils$PlainDoubleAdapter");
            Constructor<?> constructor = clazz.getDeclaredConstructor();
            constructor.setAccessible(true);
            Object adapterInstance = constructor.newInstance();
            return (TypeAdapter<Double>) adapterInstance;
        } catch (Exception e) {
            throw new RuntimeException("Failed to create PlainDoubleAdapter via reflection", e);
        }
    }

    @Test
    public void testSerializeScientificNotation_RemovesExponent() {
        Map<String, Object> data = Map.of("test1", 1e30, "test2", 1.2e3, "test3", 9.5e-3, "test4", 1.56e-30);

        String json = StringUtils.PLAIN_NUMBER_GSON.toJson(data);

        assertTrue(json.contains("1000000000000000000000000000000"));
        assertTrue(json.contains("1200"));
        assertTrue(json.contains("0.0095"));
        assertTrue(json.contains("0.00000000000000000000000000000156"));

    }

    @Test
    public void testSerializeInteger_RemovesDecimalPoint() {
        Map<String, Object> data = Map.of("intLike", 42.0);

        String json = StringUtils.PLAIN_NUMBER_GSON.toJson(data);

        assertTrue(json.contains("42"));
        assertFalse(json.contains("42.0"));
    }

    @Test
    public void testSerializeNaNAndInfinity_BecomesNull() {
        Map<String, Double> data = new HashMap<>();
        data.put("nul", null);
        data.put("nan", Double.NaN);
        data.put("inf", Double.POSITIVE_INFINITY);
        data.put("ninf", Double.NEGATIVE_INFINITY);

        String json = StringUtils.PLAIN_NUMBER_GSON.toJson(data);

        assertTrue(json.contains("\"nan\":null"));
        assertTrue(json.contains("\"inf\":null"));
        assertTrue(json.contains("\"ninf\":null"));
        assertTrue(json.contains("\"nul\":null"));

        assertFalse(json.contains("NaN"));
        assertFalse(json.contains("Infinity"));
    }

    @Test
    public void testDeserializeBackToDouble() {
        String json = "{\"value\": 12345.6789}";

        Map<?, ?> result = StringUtils.PLAIN_NUMBER_GSON.fromJson(json, Map.class);

        Object value = result.get("value");
        assertTrue(value instanceof Double);
        assertEquals(12345.6789, (Double) value, 1e-7);
    }

    @Test
    public void testQuotedScientificNotation_RemainsString() {
        String json = "{\"code\":\"1e-6\"}";

        Map<?, ?> result = StringUtils.PLAIN_NUMBER_GSON.fromJson(json, Map.class);

        assertEquals("1e-6", result.get("code"));
    }

    @Test
    public void testSerializeFloatScientificNotation_RemovesExponent_InPojo() {
        java.util.Map<String, Float> data = new java.util.LinkedHashMap<>();
        data.put("fObj", 1.23e-5f);
        data.put("fPrim", 9.5e-3f);

        String json = StringUtils.PLAIN_NUMBER_GSON.toJson(data);

        assertTrue(json.contains("\"fObj\":0.0000123"));

        assertTrue(json.contains("\"fPrim\":0.0095") || json.contains("\"fPrim\":9.5E-3") || json.contains("\"fPrim\":9.5e-3"));
    }

    @Test
    public void testSerializeFloatNaNAndInfinity_BecomesNull_InPojo() {
        java.util.Map<String, Float> data = new java.util.LinkedHashMap<>();
        data.put("fObj", Float.NaN);
        data.put("fPrimBox", Float.POSITIVE_INFINITY);
        data.put("fNull", null);

        String json = StringUtils.PLAIN_NUMBER_GSON.toJson(data);

        assertTrue(json.contains("\"fObj\":null"));
        assertTrue(json.contains("\"fNull\":null"));
        assertTrue(json.contains("\"fPrimBox\":null") || !json.contains("\"fPrimBox\""));
    }

    @Test
    public void testDeserializeScientificNotation_ToFloatAndPrimitive() {
        String jsonObj = "{\"fObj\":1.23e-5}";
        Type mapType = new TypeToken<Map<String, Float>>() {
        }.getType();
        Map<String, Float> m = StringUtils.PLAIN_NUMBER_GSON.fromJson(jsonObj, mapType);
        assertEquals(1.23e-5f, m.get("fObj"), 1e-9f);

        String jsonArr = "[4.56e1]";
        float[] arr = StringUtils.PLAIN_NUMBER_GSON.fromJson(jsonArr, float[].class);
        assertEquals(45.6f, arr[0], 1e-6f);
    }

    @Test
    public void testDeserializeNullFloat_ToNull() {
        String json = "{\"fObj\":null,\"fPrim\":1.0}";

        Type mapType = new TypeToken<Map<String, JsonElement>>() {
        }.getType();
        Map<String, JsonElement> m = StringUtils.PLAIN_NUMBER_GSON.fromJson(json, mapType);

        assertTrue(m.containsKey("fObj"));
        assertTrue(m.get("fObj").isJsonNull());

        assertTrue(m.get("fPrim").isJsonPrimitive());
        assertEquals(1.0f, m.get("fPrim").getAsFloat(), 1e-9f);
    }
}
