/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.tools;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.opensearch.ml.common.CommonValue.TENANT_ID_FIELD;
import static org.opensearch.ml.engine.tools.ToolUtils.TOOL_REQUIRED_PARAMS;
import static org.opensearch.ml.engine.tools.ToolUtils.filterToolOutput;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;
import org.opensearch.ml.common.agent.MLToolSpec;
import org.opensearch.ml.common.spi.tools.Tool;

public class ToolUtilsTest {

    @Test
    public void testCreateTool_Success() {
        Map<String, Tool.Factory> toolFactories = new HashMap<>();
        Tool.Factory factory = mock(Tool.Factory.class);
        Tool mockTool = mock(Tool.class);
        when(factory.create(any())).thenReturn(mockTool);
        toolFactories.put("test_tool", factory);

        MLToolSpec toolSpec = MLToolSpec
            .builder()
            .type("test_tool")
            .name("TestTool")
            .description("Original description")
            .parameters(Map.of("param1", "value1"))
            .runtimeResources(Map.of("resource1", "value2"))
            .build();

        Map<String, String> params = new HashMap<>();
        params.put("TestTool.param2", "value3");
        params.put("TestTool.description", "Custom description");

        Map<String, String> toolParameters = ToolUtils.buildToolParameters(params, toolSpec, "test_tenant");
        ToolUtils.createTool(toolFactories, toolParameters, toolSpec);

        verify(factory).create(argThat(toolParamsMap -> {
            Map<String, Object> toolParams = (Map<String, Object>) toolParamsMap;
            return toolParams.get("param1").equals("value1")
                && toolParams.get("param2").equals("value3")
                && toolParams.get("resource1").equals("value2")
                && toolParams.get(TENANT_ID_FIELD).equals("test_tenant");
        }));

        verify(mockTool).setName("TestTool");
        verify(mockTool).setDescription("Custom description");
    }

    @Test
    public void testCreateTool_ToolNotFound() {
        Map<String, Tool.Factory> toolFactories = new HashMap<>();
        MLToolSpec toolSpec = MLToolSpec.builder().type("non_existent_tool").name("TestTool").build();

        assertThrows(IllegalArgumentException.class, () -> ToolUtils.createTool(toolFactories, new HashMap<>(), toolSpec));
    }

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
    public void testCreateTool_WithDescription() {
        Map<String, Tool.Factory> toolFactories = new HashMap<>();
        Tool.Factory factory = mock(Tool.Factory.class);
        Tool mockTool = mock(Tool.class);
        when(factory.create(any())).thenReturn(mockTool);
        toolFactories.put("test_tool", factory);

        MLToolSpec toolSpec = MLToolSpec.builder().type("test_tool").name("TestTool").description("Tool description").build();

        Map<String, String> params = new HashMap<>();

        Tool result = ToolUtils.createTool(toolFactories, params, toolSpec);

        verify(mockTool).setName("TestTool");
        verify(mockTool).setDescription("Tool description");
        assertEquals(mockTool, result);
    }

    @Test
    public void testCreateTool_WithRuntimeResources() {
        Map<String, Tool.Factory> toolFactories = new HashMap<>();
        Tool.Factory factory = mock(Tool.Factory.class);
        Tool mockTool = mock(Tool.class);
        when(factory.create(any())).thenReturn(mockTool);
        toolFactories.put("test_tool", factory);

        Map<String, Object> runtimeResources = new HashMap<>();
        runtimeResources.put("resource1", "value1");
        runtimeResources.put("resource2", 42);

        MLToolSpec toolSpec = MLToolSpec.builder().type("test_tool").name("TestTool").runtimeResources(runtimeResources).build();

        Map<String, String> params = new HashMap<>();
        params.put("param1", "value1");

        ToolUtils.createTool(toolFactories, params, toolSpec);

        verify(factory).create(argThat(toolParamsMap -> {
            Map<String, Object> toolParams = (Map<String, Object>) toolParamsMap;
            return toolParams.get("param1").equals("value1")
                && toolParams.get("resource1").equals("value1")
                && toolParams.get("resource2").equals(42);
        }));
    }

    @Test
    public void testCreateTool_WithNullRuntimeResources() {
        Map<String, Tool.Factory> toolFactories = new HashMap<>();
        Tool.Factory factory = mock(Tool.Factory.class);
        Tool mockTool = mock(Tool.class);
        when(factory.create(any())).thenReturn(mockTool);
        toolFactories.put("test_tool", factory);

        MLToolSpec toolSpec = MLToolSpec.builder().type("test_tool").name("TestTool").runtimeResources(null).build();

        Map<String, String> params = new HashMap<>();
        params.put("param1", "value1");

        ToolUtils.createTool(toolFactories, params, toolSpec);

        verify(factory).create(argThat(toolParamsMap -> ((Map<String, Object>) toolParamsMap).get("param1").equals("value1")));
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
}
