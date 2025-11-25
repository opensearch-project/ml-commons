/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.opensearch.ml.common.CommonValue.TENANT_ID_FIELD;
import static org.opensearch.ml.common.utils.ToolUtils.TOOL_REQUIRED_PARAMS;
import static org.opensearch.ml.common.utils.ToolUtils.filterToolOutput;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;
import org.opensearch.common.settings.Settings;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.agent.MLToolSpec;
import org.opensearch.ml.common.input.Constants;
import org.opensearch.ml.common.output.model.ModelTensor;
import org.opensearch.rest.RestRequest;
import org.opensearch.search.SearchModule;
import org.opensearch.test.rest.FakeRestRequest;

public class ToolUtilsTest {

    @Test
    public void testExtractRequiredParameters_WithRequiredParameters() {
        Map<String, String> parameters = new HashMap<>();
        parameters.put("param1", "value1");
        parameters.put("param2", "value2");
        parameters.put("param3", "value3");

        Map<String, Object> attributes = new HashMap<>();
        attributes.put(TOOL_REQUIRED_PARAMS, "[param1, param2]");

        Map<String, String> result = ToolUtils.extractRequiredParameters(parameters, attributes);

        assertEquals(2, result.size());
        assertEquals("value1", result.get("param1"));
        assertEquals("value2", result.get("param2"));
        assertFalse(result.containsKey("param3"));
    }

    @Test
    public void testExtractRequiredParameters_NoRequiredParameters() {
        Map<String, String> parameters = new HashMap<>();
        parameters.put("param1", "value1");
        parameters.put("param2", "value2");

        Map<String, Object> attributes = new HashMap<>();

        Map<String, String> result = ToolUtils.extractRequiredParameters(parameters, attributes);

        assertEquals(2, result.size());
        assertEquals("value1", result.get("param1"));
        assertEquals("value2", result.get("param2"));
    }

    @Test
    public void testExtractRequiredParameters_WithNullAttributes() {
        Map<String, String> parameters = new HashMap<>();
        parameters.put("param1", "value1");
        parameters.put("param2", "value2");

        Map<String, String> result = ToolUtils.extractRequiredParameters(parameters, null);

        assertEquals(2, result.size());
        assertEquals("value1", result.get("param1"));
        assertEquals("value2", result.get("param2"));
    }

    @Test
    public void testExtractRequiredParameters_WithInputSubstitution() {
        Map<String, String> parameters = new HashMap<>();
        parameters.put("param1", "value1");
        parameters.put("param2", "value2");
        parameters.put("input", "Use ${parameters.param1} and ${parameters.param2}");

        Map<String, Object> attributes = new HashMap<>();

        Map<String, String> result = ToolUtils.extractRequiredParameters(parameters, attributes);

        assertEquals(3, result.size());
        assertEquals("value1", result.get("param1"));
        assertEquals("value2", result.get("param2"));
        assertEquals("Use ${parameters.param1} and ${parameters.param2}", result.get("input"));
    }

    @Test
    public void testExtractRequiredParameters_WithMissingSubstitutionParameter() {
        Map<String, String> parameters = new HashMap<>();
        parameters.put("param1", "value1");
        parameters.put("input", "Use ${parameters.param1} and ${parameters.param2}");

        Map<String, Object> attributes = new HashMap<>();

        Map<String, String> result = ToolUtils.extractRequiredParameters(parameters, attributes);

        assertEquals("Use ${parameters.param1} and ${parameters.param2}", result.get("input"));
    }

    @Test
    public void testBuildToolParameters_Basic() {
        Map<String, String> parameters = new HashMap<>();
        parameters.put("param1", "value1");

        MLToolSpec toolSpec = MLToolSpec.builder().type("test_tool").build();

        Map<String, String> result = ToolUtils.buildToolParameters(parameters, toolSpec, "test_tenant");

        assertEquals("value1", result.get("param1"));
        assertEquals("test_tenant", result.get(TENANT_ID_FIELD));
    }

    @Test
    public void testBuildToolParameters_WithToolSpecParameters() {
        Map<String, String> parameters = new HashMap<>();
        parameters.put("param1", "value1");

        MLToolSpec toolSpec = MLToolSpec.builder().type("test_tool").parameters(Map.of("param2", "value2", "param3", "value3")).build();

        Map<String, String> result = ToolUtils.buildToolParameters(parameters, toolSpec, "test_tenant");

        assertEquals(4, result.size());
        assertEquals("value1", result.get("param1"));
        assertEquals("value2", result.get("param2"));
        assertEquals("value3", result.get("param3"));
        assertEquals("test_tenant", result.get(TENANT_ID_FIELD));
    }

    @Test
    public void testBuildToolParameters_WithToolNamePrefix() {
        Map<String, String> parameters = new HashMap<>();
        parameters.put("param1", "value1");
        parameters.put("TestTool.param2", "value2");
        parameters.put("OtherTool.param3", "value3");

        MLToolSpec toolSpec = MLToolSpec.builder().type("test_tool").name("TestTool").build();

        Map<String, String> result = ToolUtils.buildToolParameters(parameters, toolSpec, "test_tenant");

        assertEquals(4, result.size());
        assertEquals("value1", result.get("param1"));
        assertEquals("value2", result.get("param2"));
        assertEquals("value3", result.get("OtherTool.param3"));
        assertEquals("test_tenant", result.get(TENANT_ID_FIELD));
    }

    @Test
    public void testBuildToolParameters_WithEmptyParameters() {
        Map<String, String> parameters = new HashMap<>();

        MLToolSpec toolSpec = MLToolSpec.builder().type("test_tool").build();

        Map<String, String> result = ToolUtils.buildToolParameters(parameters, toolSpec, "test_tenant");

        assertEquals(1, result.size());
        assertEquals("test_tenant", result.get(TENANT_ID_FIELD));
    }

    @Test
    public void testBuildToolParameters_WithNullToolSpecParameters() {
        Map<String, String> parameters = new HashMap<>();
        parameters.put("param1", "value1");

        MLToolSpec toolSpec = MLToolSpec.builder().type("test_tool").parameters(null).build();

        Map<String, String> result = ToolUtils.buildToolParameters(parameters, toolSpec, "test_tenant");

        assertEquals(2, result.size());
        assertEquals("value1", result.get("param1"));
        assertEquals("test_tenant", result.get(TENANT_ID_FIELD));
    }

    @Test
    public void testFilterToolOutput_NoFiltering() {
        // Create a simple object
        Map<String, String> originalOutput = Map.of("key1", "value1", "key2", "value2");
        Map<String, String> toolParams = new HashMap<>();

        String result = ToolUtils.parseResponse(filterToolOutput(toolParams, originalOutput));

        // The result should contain all the original data
        assertTrue(result.contains("key1"));
        assertTrue(result.contains("value1"));
        assertTrue(result.contains("key2"));
        assertTrue(result.contains("value2"));
    }

    @Test
    public void testFilterToolOutput_WithFilter() {
        // Create a nested object
        Map<String, Object> originalOutput = new HashMap<>();
        originalOutput.put("key1", "value1");
        originalOutput.put("nested", Map.of("innerKey", "innerValue"));

        Map<String, String> toolParams = new HashMap<>();
        toolParams.put(ToolUtils.TOOL_OUTPUT_FILTERS_FIELD, "$.nested");

        String result = ToolUtils.parseResponse(filterToolOutput(toolParams, originalOutput));

        // The result should contain only the filtered data
        assertTrue(result.contains("innerKey"));
        assertTrue(result.contains("innerValue"));
        assertFalse(result.contains("key1"));
        assertFalse(result.contains("value1"));
    }

    @Test
    public void testFilterToolOutput_ArrayFiltering() {
        // Create an object with an array
        Map<String, Object> originalOutput = new HashMap<>();
        List<Map<String, Object>> items = new ArrayList<>();
        items.add(Map.of("id", 1, "name", "first"));
        items.add(Map.of("id", 2, "name", "second"));
        originalOutput.put("items", items);

        Map<String, String> toolParams = new HashMap<>();
        toolParams.put(ToolUtils.TOOL_OUTPUT_FILTERS_FIELD, "$.items[1]");

        String result = ToolUtils.parseResponse(filterToolOutput(toolParams, originalOutput));

        // Should contain second but not first
        assertTrue(result.contains("second"));
        assertFalse(result.contains("first"));
    }

    @Test
    public void testFilterToolOutput_NonexistentPath() {
        // Create an object
        Map<String, String> originalOutput = Map.of("key1", "value1");

        Map<String, String> toolParams = new HashMap<>();
        toolParams.put(ToolUtils.TOOL_OUTPUT_FILTERS_FIELD, "$.nonexistent");

        String result = ToolUtils.parseResponse(filterToolOutput(toolParams, originalOutput));

        // Original data should be preserved when path not found
        assertTrue(result.contains("key1"));
        assertTrue(result.contains("value1"));
    }

    @Test
    public void testFilterToolOutput_NullParams() {
        Map<String, String> originalOutput = Map.of("key1", "value1");

        String result = ToolUtils.parseResponse(filterToolOutput(null, originalOutput));

        assertTrue(result.contains("key1"));
        assertTrue(result.contains("value1"));
    }

    @Test
    public void testFilterToolOutput_NullOutput() {
        Map<String, String> toolParams = new HashMap<>();
        toolParams.put(ToolUtils.TOOL_OUTPUT_FILTERS_FIELD, "$.field");

        String result = ToolUtils.parseResponse(filterToolOutput(toolParams, null));

        assertEquals("null", result);
    }

    @Test
    public void testFilterToolOutput_ComplexNestedPath() {
        // Create deeply nested object
        Map<String, Object> deeplyNested = new HashMap<>();
        deeplyNested.put("targetField", "targetValue");

        Map<String, Object> nested = new HashMap<>();
        nested.put("level2", deeplyNested);

        Map<String, Object> originalOutput = new HashMap<>();
        originalOutput.put("level1", nested);
        originalOutput.put("otherField", "otherValue");

        Map<String, String> toolParams = new HashMap<>();
        toolParams.put(ToolUtils.TOOL_OUTPUT_FILTERS_FIELD, "$.level1.level2.targetField");

        String result = ToolUtils.parseResponse(filterToolOutput(toolParams, originalOutput));

        // Should contain only the targeted deep value
        assertEquals("targetValue", result);
    }

    @Test
    public void testExtractInputParameters_WithJsonInput() {
        Map<String, String> parameters = new HashMap<>();
        parameters.put("param1", "value1");
        parameters.put("input", "{\"key1\": \"jsonValue1\", \"key2\": \"jsonValue2\"}");

        Map<String, Object> attributes = new HashMap<>();

        Map<String, String> result = ToolUtils.extractInputParameters(parameters, attributes);

        assertEquals(4, result.size());
        assertEquals("value1", result.get("param1"));
        assertEquals("{\"key1\": \"jsonValue1\", \"key2\": \"jsonValue2\"}", result.get("input"));
        assertEquals("jsonValue1", result.get("key1"));
        assertEquals("jsonValue2", result.get("key2"));
    }

    @Test
    public void testExtractInputParameters_WithParameterSubstitution() {
        Map<String, String> parameters = new HashMap<>();
        parameters.put("param1", "substitutedValue");
        parameters.put("input", "{\"message\": \"Hello ${parameters.param1}\"}");

        Map<String, Object> attributes = new HashMap<>();

        Map<String, String> result = ToolUtils.extractInputParameters(parameters, attributes);

        assertEquals(3, result.size());
        assertEquals("substitutedValue", result.get("param1"));
        assertEquals("{\"message\": \"Hello substitutedValue\"}", result.get("input"));
        assertEquals("Hello substitutedValue", result.get("message"));
    }

    @Test
    public void testExtractInputParameters_WithInvalidJson() {
        Map<String, String> parameters = new HashMap<>();
        parameters.put("param1", "value1");
        parameters.put("input", "invalid json string");

        Map<String, Object> attributes = new HashMap<>();

        Map<String, String> result = ToolUtils.extractInputParameters(parameters, attributes);

        assertEquals(2, result.size());
        assertEquals("value1", result.get("param1"));
        assertEquals("invalid json string", result.get("input"));
    }

    @Test
    public void testExtractInputParameters_NoInputParameter() {
        Map<String, String> parameters = new HashMap<>();
        parameters.put("param1", "value1");
        parameters.put("param2", "value2");

        Map<String, Object> attributes = new HashMap<>();

        Map<String, String> result = ToolUtils.extractInputParameters(parameters, attributes);

        assertEquals(2, result.size());
        assertEquals("value1", result.get("param1"));
        assertEquals("value2", result.get("param2"));
    }

    @Test
    public void testConvertOutputToModelTensor_WithMap() {
        Map<String, Object> mapOutput = Map.of("key1", "value1", "key2", "value2");
        String outputKey = "test_output";

        ModelTensor result = ToolUtils.convertOutputToModelTensor(mapOutput, outputKey);

        assertEquals(outputKey, result.getName());
        // Map should now be wrapped with "output" key
        Map<String, Object> expectedMap = Map.of(ToolUtils.TOOL_OUTPUT_KEY, mapOutput);
        assertEquals(expectedMap, result.getDataAsMap());
        assertEquals(mapOutput, result.getDataAsMap().get(ToolUtils.TOOL_OUTPUT_KEY));
    }

    @Test
    public void testConvertOutputToModelTensor_WithList() {
        List<String> listOutput = List.of("item1", "item2", "item3");
        String outputKey = "test_output";

        ModelTensor result = ToolUtils.convertOutputToModelTensor(listOutput, outputKey);

        assertEquals(outputKey, result.getName());
        Map<String, Object> expectedMap = Map.of(ToolUtils.TOOL_OUTPUT_KEY, listOutput);
        assertEquals(expectedMap, result.getDataAsMap());
        assertEquals(listOutput, result.getDataAsMap().get(ToolUtils.TOOL_OUTPUT_KEY));
    }

    @Test
    public void testConvertOutputToModelTensor_WithJsonObjectString() {
        String jsonOutput = "{\"key1\":\"value1\",\"key2\":\"value2\"}";
        String outputKey = "test_output";

        ModelTensor result = ToolUtils.convertOutputToModelTensor(jsonOutput, outputKey);

        assertEquals(outputKey, result.getName());
        // JSON object should be wrapped with "output" key using fromJsonWithWrappingKey
        assertTrue(result.getDataAsMap().containsKey(ToolUtils.TOOL_OUTPUT_KEY));
        Map<String, Object> wrappedOutput = (Map<String, Object>) result.getDataAsMap().get(ToolUtils.TOOL_OUTPUT_KEY);
        assertEquals("value1", wrappedOutput.get("key1"));
        assertEquals("value2", wrappedOutput.get("key2"));
    }

    @Test
    public void testConvertOutputToModelTensor_WithJsonArrayString() {
        String jsonOutput = "[\"item1\", \"item2\", \"item3\"]";
        String outputKey = "test_output";

        ModelTensor result = ToolUtils.convertOutputToModelTensor(jsonOutput, outputKey);

        assertEquals(outputKey, result.getName());
        // JSON array should be wrapped with "output" key using fromJsonWithWrappingKey
        assertTrue(result.getDataAsMap().containsKey(ToolUtils.TOOL_OUTPUT_KEY));
        List<String> wrappedOutput = (List<String>) result.getDataAsMap().get(ToolUtils.TOOL_OUTPUT_KEY);
        assertEquals(3, wrappedOutput.size());
        assertEquals("item1", wrappedOutput.get(0));
        assertEquals("item2", wrappedOutput.get(1));
        assertEquals("item3", wrappedOutput.get(2));
    }

    @Test
    public void testConvertOutputToModelTensor_WithNonJsonString() {
        String stringOutput = "simple string output";
        String outputKey = "test_output";

        ModelTensor result = ToolUtils.convertOutputToModelTensor(stringOutput, outputKey);

        assertEquals(outputKey, result.getName());
        Map<String, Object> expectedMap = Map.of(ToolUtils.TOOL_OUTPUT_KEY, stringOutput);
        assertEquals(expectedMap, result.getDataAsMap());
        assertEquals(stringOutput, result.getDataAsMap().get(ToolUtils.TOOL_OUTPUT_KEY));
    }

    @Test
    public void testConvertOutputToModelTensor_WithEmptyMap() {
        Map<String, Object> emptyMap = new HashMap<>();
        String outputKey = "test_output";

        ModelTensor result = ToolUtils.convertOutputToModelTensor(emptyMap, outputKey);

        assertEquals(outputKey, result.getName());
        Map<String, Object> expectedMap = Map.of(ToolUtils.TOOL_OUTPUT_KEY, emptyMap);
        assertEquals(expectedMap, result.getDataAsMap());
        assertTrue(((Map<?, ?>) result.getDataAsMap().get(ToolUtils.TOOL_OUTPUT_KEY)).isEmpty());
    }

    @Test
    public void testConvertOutputToModelTensor_WithEmptyList() {
        List<String> emptyList = new ArrayList<>();
        String outputKey = "test_output";

        ModelTensor result = ToolUtils.convertOutputToModelTensor(emptyList, outputKey);

        assertEquals(outputKey, result.getName());
        Map<String, Object> expectedMap = Map.of(ToolUtils.TOOL_OUTPUT_KEY, emptyList);
        assertEquals(expectedMap, result.getDataAsMap());
        assertTrue(((List<?>) result.getDataAsMap().get(ToolUtils.TOOL_OUTPUT_KEY)).isEmpty());
    }

    @Test
    public void testConvertOutputToModelTensor_WithNestedJsonObject() {
        String nestedJsonOutput = "{\"user\":{\"name\":\"John\",\"age\":30},\"items\":[\"a\",\"b\"]}";
        String outputKey = "test_output";

        ModelTensor result = ToolUtils.convertOutputToModelTensor(nestedJsonOutput, outputKey);

        assertEquals(outputKey, result.getName());
        assertTrue(result.getDataAsMap().containsKey(ToolUtils.TOOL_OUTPUT_KEY));
        Map<String, Object> wrappedOutput = (Map<String, Object>) result.getDataAsMap().get(ToolUtils.TOOL_OUTPUT_KEY);
        assertTrue(wrappedOutput.containsKey("user"));
        assertTrue(wrappedOutput.containsKey("items"));

        Map<String, Object> user = (Map<String, Object>) wrappedOutput.get("user");
        assertEquals("John", user.get("name"));
        assertEquals(30.0, user.get("age")); // Gson parses numbers as Double

        List<String> items = (List<String>) wrappedOutput.get("items");
        assertEquals(2, items.size());
        assertEquals("a", items.get(0));
        assertEquals("b", items.get(1));
    }

    @Test
    public void testConvertOutputToModelTensor_WithNumber() {
        Integer numberOutput = 42;
        String outputKey = "test_output";

        ModelTensor result = ToolUtils.convertOutputToModelTensor(numberOutput, outputKey);

        assertEquals(outputKey, result.getName());
        Map<String, Object> expectedMap = Map.of(ToolUtils.TOOL_OUTPUT_KEY, numberOutput);
        assertEquals(expectedMap, result.getDataAsMap());
        assertEquals(numberOutput, result.getDataAsMap().get(ToolUtils.TOOL_OUTPUT_KEY));
    }

    @Test
    public void testConvertOutputToModelTensor_WithBoolean() {
        Boolean booleanOutput = true;
        String outputKey = "test_output";

        ModelTensor result = ToolUtils.convertOutputToModelTensor(booleanOutput, outputKey);

        assertEquals(outputKey, result.getName());
        Map<String, Object> expectedMap = Map.of(ToolUtils.TOOL_OUTPUT_KEY, booleanOutput);
        assertEquals(expectedMap, result.getDataAsMap());
        assertEquals(booleanOutput, result.getDataAsMap().get(ToolUtils.TOOL_OUTPUT_KEY));
    }

    @Test
    public void testConvertOutputToModelTensor_WithNull() {
        String outputKey = "test_output";

        ModelTensor result = ToolUtils.convertOutputToModelTensor(null, outputKey);

        assertEquals(outputKey, result.getName());
        // Map.of() doesn't accept null values, so we need to create a HashMap
        Map<String, Object> expectedMap = new HashMap<>();
        expectedMap.put(ToolUtils.TOOL_OUTPUT_KEY, null);
        assertEquals(expectedMap, result.getDataAsMap());
        assertEquals(null, result.getDataAsMap().get(ToolUtils.TOOL_OUTPUT_KEY));
    }

    @Test
    public void testGetAttributeFromHeader() {
        String cmkRoleArn = "test-cmk-role";
        String cmkAssumeRoleArn = "test-assume-cmk-role";

        Map<String, List<String>> headers = new HashMap<>();
        SearchModule searchModule = new SearchModule(Settings.EMPTY, Collections.emptyList());
        NamedXContentRegistry xContentRegistry = new NamedXContentRegistry(searchModule.getNamedXContents());
        headers.put(Constants.CMK_ROLE_FIELD, Collections.singletonList(cmkRoleArn));
        headers.put(Constants.CMK_ASSUME_ROLE_FIELD, Collections.singletonList(cmkAssumeRoleArn));
        RestRequest restRequest = new FakeRestRequest.Builder(xContentRegistry).withHeaders(headers).build();

        String resultCMKRoleArn = ToolUtils.getAttributeFromHeader(Constants.CMK_ROLE_FIELD, restRequest);
        String resultCMKAssumeRoleArn = ToolUtils.getAttributeFromHeader(Constants.CMK_ASSUME_ROLE_FIELD, restRequest);
        assertEquals(cmkRoleArn, resultCMKRoleArn);
        assertEquals(cmkAssumeRoleArn, resultCMKAssumeRoleArn);

    }
}
