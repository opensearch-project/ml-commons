/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.processor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.opensearch.ml.engine.processor.ProcessorChain.EXTRACT_JSON;
import static org.opensearch.ml.engine.processor.ProcessorChain.JSONPATH_FILTER;
import static org.opensearch.ml.engine.processor.ProcessorChain.REGEX_CAPTURE;
import static org.opensearch.ml.engine.processor.ProcessorChain.REGEX_REPLACE;
import static org.opensearch.ml.engine.processor.ProcessorChain.TO_STRING;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;
import org.opensearch.ml.common.output.model.ModelTensor;
import org.opensearch.ml.common.output.model.ModelTensorOutput;
import org.opensearch.ml.common.output.model.ModelTensors;
import org.opensearch.ml.common.utils.StringUtils;
import org.opensearch.ml.engine.processor.ProcessorChain.OutputProcessor;
import org.opensearch.ml.engine.processor.ProcessorChain.ProcessorRegistry;

public class ProcessorChainTests {

    @Test
    public void testToString() {
        // First test with replace_all=true
        Map<String, Object> configMap = new HashMap<>();

        OutputProcessor processorReplaceAll = ProcessorRegistry.createProcessor(TO_STRING, configMap);
        String result = (String) processorReplaceAll.process(Map.of("key1", "value1"));
        assertEquals("{\"key1\":\"value1\"}", result);

        result = (String) processorReplaceAll.process(List.of("value1", "value2"));
        assertEquals("[\"value1\",\"value2\"]", result);
    }

    @Test
    public void testToString_ModelTensor() {
        Map<String, Object> configMap = new HashMap<>();

        OutputProcessor processorReplaceAll = ProcessorRegistry.createProcessor(TO_STRING, configMap);
        ModelTensor modelTensor = ModelTensor.builder().name("test").dataAsMap(Map.of("key1", "value1")).build();
        String result = (String) processorReplaceAll.process(modelTensor);
        assertEquals("{\"name\":\"test\",\"dataAsMap\":{\"key1\":\"value1\"}}", result);

        result = (String) processorReplaceAll.process(Collections.singletonList(modelTensor));
        assertEquals("[{\"name\":\"test\",\"dataAsMap\":{\"key1\":\"value1\"}}]", result);
    }

    @Test
    public void testToString_ModelTensors() {
        Map<String, Object> configMap = new HashMap<>();

        OutputProcessor processorReplaceAll = ProcessorRegistry.createProcessor(TO_STRING, configMap);
        ModelTensor modelTensor = ModelTensor.builder().name("test").dataAsMap(Map.of("key1", "value1")).build();
        ModelTensors modelTensors = ModelTensors.builder().mlModelTensors(Collections.singletonList(modelTensor)).build();
        String result = (String) processorReplaceAll.process(modelTensors);
        assertEquals("{\"output\":[{\"name\":\"test\",\"dataAsMap\":{\"key1\":\"value1\"}}]}", result);
    }

    @Test
    public void testToString_ModelTensorOutput() {
        Map<String, Object> configMap = new HashMap<>();

        OutputProcessor processorReplaceAll = ProcessorRegistry.createProcessor(TO_STRING, configMap);
        ModelTensor modelTensor = ModelTensor.builder().name("test").dataAsMap(Map.of("key1", "value1")).build();
        ModelTensors modelTensors = ModelTensors.builder().mlModelTensors(Collections.singletonList(modelTensor)).build();
        ModelTensorOutput modelTensorOutput = ModelTensorOutput.builder().mlModelOutputs(Collections.singletonList(modelTensors)).build();
        String result = (String) processorReplaceAll.process(modelTensorOutput);
        assertEquals("{\"inference_results\":[{\"output\":[{\"name\":\"test\",\"dataAsMap\":{\"key1\":\"value1\"}}]}]}", result);
    }

    @Test
    public void testToString_EscapeJson() {
        Map<String, Object> configMap = new HashMap<>();
        configMap.put("escape_json", true);

        OutputProcessor processorReplaceAll = ProcessorRegistry.createProcessor(TO_STRING, configMap);

        String result = (String) processorReplaceAll.process("hello \"world\" opensearch");
        assertEquals("hello \\\"world\\\" opensearch", result);
    }

    @Test
    public void testRegexProcessor() {
        // First test with replace_all=true
        Map<String, Object> configReplace = new HashMap<>();
        configReplace.put("pattern", "test(\\d+)");
        configReplace.put("replacement", "replaced$1");
        configReplace.put("replace_all", true);

        OutputProcessor processorReplaceAll = ProcessorRegistry.createProcessor(REGEX_REPLACE, configReplace);
        String resultReplaceAll = (String) processorReplaceAll.process("test123 test456");
        assertEquals("replaced123 replaced456", resultReplaceAll);

        // Second test with replace_all=false - using a completely fresh config
        configReplace.put("replace_all", false);

        OutputProcessor processorReplaceFirst = ProcessorRegistry.createProcessor(REGEX_REPLACE, configReplace);
        String resultReplaceFirst = (String) processorReplaceFirst.process("test123 test456");
        assertEquals("replaced123 test456", resultReplaceFirst);
    }

    @Test
    public void testRegexReplaceProcessorMultipleGroups() {
        // The regex matches parts like "test123 abcDEF"
        Map<String, Object> config = new HashMap<>();
        // Replacement uses $1-$2-$3 to combine captured groups with -
        config.put("pattern", "test(\\d+) (abc)(\\w+)");
        config.put("replacement", "replaced$1-$2-$3");
        config.put("replace_all", true);

        OutputProcessor processor = ProcessorRegistry.createProcessor(REGEX_REPLACE, config);

        String input = "test123 abcDEF test456 abcXYZ";
        Object result = processor.process(input);

        // Expected to replace all matches, combining groups with '-' separator
        String expected = "replaced123-abc-DEF replaced456-abc-XYZ";

        assertEquals(expected, result);
    }

    @Test
    public void testRegexProcessorWithNonStringInput() {
        Map<String, Object> config = new HashMap<>();
        config.put("pattern", "\"key\"\\s*:\\s*\"(.+?)\"");
        config.put("replacement", "\"key\":\"modified-$1\"");

        OutputProcessor processor = ProcessorRegistry.createProcessor(REGEX_REPLACE, config);

        // Test with Map input (should be converted to JSON string)
        Map<String, String> mapInput = new HashMap<>();
        mapInput.put("key", "value");

        String result = (String) processor.process(mapInput);
        assertTrue(result.contains("modified-value"));
    }

    @Test
    public void testJsonPathProcessor() {
        Map<String, Object> config = new HashMap<>();
        config.put("path", "$.person.name");

        OutputProcessor processor = ProcessorRegistry.createProcessor(JSONPATH_FILTER, config);

        String input = "{\"person\": {\"name\": \"John\", \"age\": 30}}";
        Object result = processor.process(input);
        assertEquals("John", result);

        // Test with default value for missing path
        config.put("default", "Default Name");
        config.put("path", "$.person.missing");
        processor = ProcessorRegistry.createProcessor(JSONPATH_FILTER, config);
        result = processor.process(input);
        assertEquals("Default Name", result);
    }

    @Test
    public void testJsonPathProcessorWithError() {
        Map<String, Object> config = new HashMap<>();
        config.put("path", "$.invalid..path"); // Invalid path syntax

        OutputProcessor processor = ProcessorRegistry.createProcessor(JSONPATH_FILTER, config);

        String input = "{\"person\": {\"name\": \"John\"}}";
        Object result = processor.process(input);
        // Should return original input when error occurs
        assertEquals(input, result);
    }

    @Test
    public void testExtractJsonProcessor() {
        Map<String, Object> config = new HashMap<>();

        OutputProcessor processor = ProcessorRegistry.createProcessor(EXTRACT_JSON, config);

        // Test with JSON embedded in text
        String input = "Some text before {\"key\": \"value\"} and after";
        Object result = processor.process(input);
        assertTrue(result instanceof Map);
        assertEquals("value", ((Map<?, ?>) result).get("key"));

        // Test with non-JSON input
        result = processor.process("No JSON here");
        assertEquals("No JSON here", result);
    }

    @Test
    public void testExtractJsonProcessorArray() {
        Map<String, Object> config = new HashMap<>();

        OutputProcessor processor = ProcessorRegistry.createProcessor(EXTRACT_JSON, config);

        // Test with JSON array embedded in text
        String input = "Some text before [{\"key1\": \"value1\"}, {\"key2\": \"value2\"}] and after";
        Object result = processor.process(input);

        assertTrue(result instanceof List);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> list = (List<Map<String, Object>>) result;

        assertEquals(2, list.size());
        assertEquals("value1", list.get(0).get("key1"));
        assertEquals("value2", list.get(1).get("key2"));

        // Test with non-JSON input returns unchanged
        result = processor.process("No JSON array here");
        assertEquals("No JSON array here", result);
    }

    @Test
    public void testExtractJsonProcessorWithInvalidJson() {
        Map<String, Object> config = new HashMap<>();
        OutputProcessor processor = ProcessorRegistry.createProcessor(EXTRACT_JSON, config);

        // Invalid JSON that starts with a brace
        String input = "{not valid json}";
        Object result = processor.process(input);
        // Should return original input on error
        assertEquals(input, result);
    }

    @Test
    public void testExtractJsonProcessorWithExtractTypeObject() {
        Map<String, Object> config = new HashMap<>();
        config.put("extract_type", "object");

        OutputProcessor processor = ProcessorRegistry.createProcessor(EXTRACT_JSON, config);

        String input = "prefix {\"foo\":\"bar\"} suffix";
        Object result = processor.process(input);

        assertTrue(result instanceof Map);
        assertEquals("bar", ((Map<?, ?>) result).get("foo"));

        // First item of JSON array will be extracted when forcing object type
        input = "prefix [{\"foo\":\"bar\"}] suffix";
        result = processor.process(input);
        assertTrue(result instanceof Map);
        assertEquals("bar", ((Map<?, ?>) result).get("foo"));
    }

    @Test
    public void testExtractJsonProcessorWithExtractTypeArray() {
        Map<String, Object> config = new HashMap<>();
        config.put("extract_type", "array");

        OutputProcessor processor = ProcessorRegistry.createProcessor(EXTRACT_JSON, config);

        String input = "prefix [{\"foo\":\"bar\"}, {\"baz\":\"qux\"}] suffix";
        Object result = processor.process(input);

        assertTrue(result instanceof List);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> list = (List<Map<String, Object>>) result;
        assertEquals(2, list.size());
        assertEquals("bar", list.get(0).get("foo"));
        assertEquals("qux", list.get(1).get("baz"));

        // JSON object should NOT be extracted when forcing array type, fallback to input
        input = "prefix {\"foo\":\"bar\"} suffix";
        result = processor.process(input);
        assertEquals(input, result);
    }

    @Test
    public void testExtractJsonProcessorWithDefaultValue() {
        Map<String, Object> config = new HashMap<>();
        config.put("extract_type", "array");
        List<String> defaultVal = List.of("default");
        config.put("default", defaultVal);

        OutputProcessor processor = ProcessorRegistry.createProcessor(EXTRACT_JSON, config);

        // No JSON array found, should return default value
        String input = "no json array here";
        Object result = processor.process(input);
        assertSame(defaultVal, result);

        // Invalid JSON should also return default
        input = "[invalid json]";
        result = processor.process(input);
        assertSame(defaultVal, result);
    }

    @Test
    public void testExtractJsonProcessorWithNoJsonStart() {
        Map<String, Object> config = new HashMap<>();

        OutputProcessor processor = ProcessorRegistry.createProcessor(EXTRACT_JSON, config);

        // Input string with no '{' or '['
        String input = "no braces or brackets here";
        Object result = processor.process(input);
        assertEquals(input, result);
    }

    @Test
    public void testExtractJsonProcessorWithNonStringInput() {
        Map<String, Object> config = new HashMap<>();

        OutputProcessor processor = ProcessorRegistry.createProcessor(EXTRACT_JSON, config);

        // Pass in non-string input (e.g. Integer)
        Integer input = 12345;
        Object result = processor.process(input);
        assertSame(input, result);
    }

    @Test
    public void testRegexCaptureProcessor() {
        Map<String, Object> config = new HashMap<>();
        config.put("pattern", "value: (\\d+)");
        config.put("groups", 1);

        OutputProcessor processor = ProcessorRegistry.createProcessor(REGEX_CAPTURE, config);

        // Test successful capture
        String input = "The value: 123 is captured";
        Object result = processor.process(input);
        assertEquals("123", result);

        // Test no match
        result = processor.process("No match here");
        assertEquals("No match here", result);

        config.put("groups", "[1]");

        result = processor.process(input);
        assertEquals("123", result);
    }

    @Test
    public void testRegexCaptureProcessor_MultipleGroups() {
        Map<String, Object> config = new HashMap<>();
        config.put("pattern", "value: (\\d+), name: (\\w+), status: (\\w+)");
        config.put("groups", "[1, 3]");  // multiple groups

        OutputProcessor processor = ProcessorRegistry.createProcessor(REGEX_CAPTURE, config);

        // Input string with all three groups
        String input = "value: 123, name: Alice, status: active";

        Object result = processor.process(input);

        // Expect a List<String> with three captured groups
        assertTrue(result instanceof List);

        @SuppressWarnings("unchecked")
        List<String> capturedGroups = (List<String>) result;

        assertEquals(2, capturedGroups.size());
        assertEquals("123", capturedGroups.get(0));
        assertEquals("active", capturedGroups.get(1));

        // Test with a single group (should return String, not List)
        config.put("groups", "2");
        processor = ProcessorRegistry.createProcessor(REGEX_CAPTURE, config);
        result = processor.process(input);
        assertTrue(result instanceof String);
        assertEquals("Alice", result);

        // Test no match returns original input
        result = processor.process("no matching text here");
        assertEquals("no matching text here", result);
    }

    @Test
    public void testRegexCaptureProcessorWithInvalidPattern() {
        Map<String, Object> config = new HashMap<>();
        config.put("pattern", "(unclosed"); // Invalid regex pattern
        config.put("group", 1);

        OutputProcessor processor = ProcessorRegistry.createProcessor(REGEX_CAPTURE, config);

        String input = "test input";
        Object result = processor.process(input);
        // Should return original input on error
        assertEquals(input, result);
    }

    @Test
    public void testSimpleProcessorChain() {
        // Create a chain of processors
        List<Map<String, Object>> configs = new ArrayList<>();

        Map<String, Object> regexConfig = new HashMap<>();
        regexConfig.put("type", REGEX_REPLACE);
        regexConfig.put("pattern", "<reasoning>.*?</reasoning>");
        regexConfig.put("replacement", "");
        configs.add(regexConfig);

        Map<String, Object> extractConfig = new HashMap<>();
        extractConfig.put("type", EXTRACT_JSON);
        configs.add(extractConfig);

        ProcessorChain chain = new ProcessorChain(configs);

        // Test the chain
        String input = "<reasoning>This is reasoning</reasoning>{\"query\":{\"match_all\":{}}}";
        Object result = chain.process(input);

        assertTrue(result instanceof Map);
        assertNotNull(((Map<?, ?>) result).get("query"));
    }

    @Test
    public void testComplexProcessorChain() {
        // Create a complex chain that processes in sequence
        OutputProcessor first = input -> ((String) input).replace("first", "1st");
        OutputProcessor second = input -> ((String) input).replace("second", "2nd");
        OutputProcessor third = input -> ((String) input).replace("third", "3rd");

        ProcessorChain chain = new ProcessorChain(first, second, third);

        String input = "first second third";
        Object result = chain.process(input);

        assertEquals("1st 2nd 3rd", result);
    }

    @Test
    public void testEmptyChain() {
        ProcessorChain chain = new ProcessorChain(Collections.emptyList());
        assertFalse(chain.hasProcessors());

        String input = "test";
        Object result = chain.process(input);
        assertEquals(input, result);
    }

    @Test
    public void testExtractProcessorConfigsWithList() {
        // Test with List input
        Map<String, Object> params = new HashMap<>();
        List<Map<String, Object>> configs = new ArrayList<>();

        Map<String, Object> config1 = new HashMap<>();
        config1.put("type", REGEX_REPLACE);
        config1.put("pattern", "test");
        configs.add(config1);

        params.put(ProcessorChain.OUTPUT_PROCESSORS, configs);

        List<Map<String, Object>> result = ProcessorChain.extractProcessorConfigs(params);
        assertEquals(1, result.size());
        assertEquals(REGEX_REPLACE, result.get(0).get("type"));
    }

    @Test
    public void testExtractProcessorConfigsWithString() {
        // Test with String input
        Map<String, Object> params = new HashMap<>();
        String configStr = "[{\"type\":\"regex_replace\",\"pattern\":\"test\",\"replacement\":\"\"}]";

        params.put(ProcessorChain.OUTPUT_PROCESSORS, configStr);

        List<Map<String, Object>> result = ProcessorChain.extractProcessorConfigs(params);
        assertEquals(1, result.size());
        assertEquals(REGEX_REPLACE, result.get(0).get("type"));
    }

    @Test
    public void testExtractProcessorConfigsWithInvalidString() {
        // Test with invalid String input
        Map<String, Object> params = new HashMap<>();
        String configStr = "not a json";

        params.put(ProcessorChain.OUTPUT_PROCESSORS, configStr);

        List<Map<String, Object>> result = ProcessorChain.extractProcessorConfigs(params);
        assertTrue(result.isEmpty());
    }

    @Test
    public void testExtractProcessorConfigsWithNull() {
        // Test with null params
        List<Map<String, Object>> result = ProcessorChain.extractProcessorConfigs(null);
        assertTrue(result.isEmpty());

        // Test with empty params
        result = ProcessorChain.extractProcessorConfigs(Collections.emptyMap());
        assertTrue(result.isEmpty());
    }

    @Test
    public void testExtractProcessorConfigsWithEscapedJson() {
        // Test with escaped JSON string (common in configuration)
        Map<String, Object> params = new HashMap<>();
        String configStr =
            "[{\"pattern\":\"\\u003creasoning\\u003e.*?\\u003c/reasoning\\u003e\",\"type\":\"regex_replace\",\"replacement\":\"\"},{\"type\":\"extract_json\"}]";

        params.put(ProcessorChain.OUTPUT_PROCESSORS, configStr);

        List<Map<String, Object>> result = ProcessorChain.extractProcessorConfigs(params);
        assertEquals(2, result.size());
        assertEquals(REGEX_REPLACE, result.get(0).get("type"));
        assertEquals(EXTRACT_JSON, result.get(1).get("type"));
        assertEquals("<reasoning>.*?</reasoning>", result.get(0).get("pattern").toString().replace("\\", ""));
    }

    @Test
    public void testRegisterCustomProcessor() {
        // Register a custom processor
        ProcessorRegistry.registerProcessor("custom", config -> {
            String prefix = (String) config.getOrDefault("prefix", "custom");
            return input -> prefix + ": " + input;
        });

        Map<String, Object> config = new HashMap<>();
        config.put("prefix", "PREFIX");

        OutputProcessor processor = ProcessorRegistry.createProcessor("custom", config);
        assertEquals("PREFIX: test", processor.process("test"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testCreateProcessorWithInvalidType() {
        ProcessorRegistry.createProcessor("invalid_type", Collections.emptyMap());
    }

    @Test
    public void testRealWorldScenario() {
        // This test simulates the real-world case from the issue
        String input =
            "<reasoning>We need count of flights from Seattle. Index mapping shows Origin field is string keyword. Need to filter where Origin contains Seattle? In sample mapping: origin values are names like \"Frankfurt am Main Airport\", \"Cape Town International Airport\". Probably Seattle? but not in sample. Anyway query: term or match? Use match? Could use term exact. Use keyword field. Probably `Origin:\"Seattle\"`. But location might be \"Seattle-Tacoma International Airport\". Use match? They ask \"total flights from Seattle\". Likely match on Origin contains \"Seattle\". Use match query with \"Seattle\". Then size 0 (only count). Add track_total_hits: true maybe. So produce query.</reasoning>{\"query\":{\"match\":{\"Origin\":\"Seattle\"}},\"size\":0,\"track_total_hits\":true}";

        // Create processors for the exact case in question
        List<Map<String, Object>> configs = new ArrayList<>();

        Map<String, Object> regexConfig = new HashMap<>();
        regexConfig.put("type", REGEX_REPLACE);
        regexConfig.put("pattern", "<reasoning>.*?</reasoning>");
        regexConfig.put("replacement", "");
        configs.add(regexConfig);

        Map<String, Object> extractConfig = new HashMap<>();
        extractConfig.put("type", EXTRACT_JSON);
        configs.add(extractConfig);

        ProcessorChain chain = new ProcessorChain(configs);

        Object result = chain.process(input);

        assertTrue(result instanceof Map);
        Map<String, Object> queryResult = (Map<String, Object>) result;

        // Verify the query parts are extracted correctly
        assertTrue(queryResult.containsKey("query"));
        assertTrue(queryResult.containsKey("size"));
        assertEquals(0, queryResult.get("size"));
        assertTrue(queryResult.containsKey("track_total_hits"));
        assertEquals(true, queryResult.get("track_total_hits"));

        Map<String, Object> queryMap = (Map<String, Object>) queryResult.get("query");
        assertTrue(queryMap.containsKey("match"));

        Map<String, Object> matchMap = (Map<String, Object>) queryMap.get("match");
        assertEquals("Seattle", matchMap.get("Origin"));
    }

    @Test
    public void testBasicStringConditions() {
        // Setup test input
        Map<String, Object> input = new HashMap<>();
        input.put("status", "success");

        // Create processor configs
        List<Map<String, Object>> processorConfigs = new ArrayList<>();

        Map<String, Object> conditionalConfig = new HashMap<>();
        conditionalConfig.put("type", "conditional");
        conditionalConfig.put("path", "$.status");

        // ====== Ordered routes list ======
        List<Object> routes = new ArrayList<>();

        // Success route
        List<Map<String, Object>> successRoute = new ArrayList<>();
        Map<String, Object> successToString = new HashMap<>();
        successToString.put("type", "to_string");
        successRoute.add(successToString);

        Map<String, Object> successRegexReplace = new HashMap<>();
        successRegexReplace.put("type", "regex_replace");
        successRegexReplace.put("pattern", "\\{.*\\}");
        successRegexReplace.put("replacement", "Operation was successful");
        successRoute.add(successRegexReplace);

        routes.add(Collections.singletonMap("success", successRoute));

        // Error route
        List<Map<String, Object>> errorRoute = new ArrayList<>();
        Map<String, Object> errorToString = new HashMap<>();
        errorToString.put("type", "to_string");
        errorRoute.add(errorToString);

        Map<String, Object> errorRegexReplace = new HashMap<>();
        errorRegexReplace.put("type", "regex_replace");
        errorRegexReplace.put("pattern", "\\{.*\\}");
        errorRegexReplace.put("replacement", "Operation failed");
        errorRoute.add(errorRegexReplace);

        routes.add(Collections.singletonMap("error", errorRoute));

        // Put ordered routes into config
        conditionalConfig.put("routes", routes);

        processorConfigs.add(conditionalConfig);

        // Create and run processor chain
        ProcessorChain chain = new ProcessorChain(processorConfigs);

        // Test success
        Object result = chain.process(input);
        assertEquals("Operation was successful", result);

        // Test error
        input.put("status", "error");
        result = chain.process(input);
        assertEquals("Operation failed", result);
    }

    @Test
    public void testNumericConditions() {
        Map<String, Object> input = new HashMap<>();
        input.put("count", 42);

        Map<String, Object> conditionalConfig = new HashMap<>();
        conditionalConfig.put("type", "conditional");
        conditionalConfig.put("path", "$.count");

        List<Map<String, Object>> routes = new ArrayList<>();

        // >50
        List<Map<String, Object>> gtRoute = new ArrayList<>();
        Map<String, Object> gtString = new HashMap<>();
        gtString.put("type", "to_string");
        gtRoute.add(gtString);
        Map<String, Object> gtReplace = new HashMap<>();
        gtReplace.put("type", "regex_replace");
        gtReplace.put("pattern", ".*");
        gtReplace.put("replacement", "Greater than 50");
        gtRoute.add(gtReplace);
        routes.add(Map.of(">50", gtRoute));

        // ==42
        List<Map<String, Object>> eqRoute = new ArrayList<>();
        Map<String, Object> eqString = new HashMap<>();
        eqString.put("type", "to_string");
        eqRoute.add(eqString);
        Map<String, Object> eqReplace = new HashMap<>();
        eqReplace.put("type", "regex_replace");
        eqReplace.put("pattern", "^.*$");
        eqReplace.put("replacement", "Exactly 42");
        eqRoute.add(eqReplace);
        routes.add(Map.of("==42", eqRoute));

        // <50
        List<Map<String, Object>> ltRoute = new ArrayList<>();
        Map<String, Object> ltString = new HashMap<>();
        ltString.put("type", "to_string");
        ltRoute.add(ltString);
        Map<String, Object> ltReplace = new HashMap<>();
        ltReplace.put("type", "regex_replace");
        ltReplace.put("pattern", "^.*$");
        ltReplace.put("replacement", "Less than 50");
        ltRoute.add(ltReplace);
        routes.add(Map.of("<50", ltRoute));

        conditionalConfig.put("routes", routes);

        // Default
        List<Map<String, Object>> defaultRoute = new ArrayList<>();
        Map<String, Object> defaultString = new HashMap<>();
        defaultString.put("type", "to_string");
        defaultRoute.add(defaultString);
        Map<String, Object> defaultReplace = new HashMap<>();
        defaultReplace.put("type", "regex_replace");
        defaultReplace.put("pattern", "^.*$");
        defaultReplace.put("replacement", "Default route");
        defaultRoute.add(defaultReplace);
        conditionalConfig.put("default", defaultRoute);

        OutputProcessor processor = ProcessorChain.ProcessorRegistry.createProcessor("conditional", conditionalConfig);
        assertEquals("Exactly 42", processor.process(input));

        input.put("count", 30);
        assertEquals("Less than 50", processor.process(input));

        input.put("count", 50);
        assertEquals("Default route", processor.process(input));
    }

    @Test
    public void testExistenceConditions() {
        Map<String, Object> input = new HashMap<>();
        input.put("required", "value");
        input.put("optional", null);

        Map<String, Object> conditionalConfig = new HashMap<>();
        conditionalConfig.put("type", "conditional");
        conditionalConfig.put("path", "$.missing");

        List<Map<String, Object>> routes = new ArrayList<>();

        // exists
        List<Map<String, Object>> existsRoute = new ArrayList<>();
        Map<String, Object> existsReplace = new HashMap<>();
        existsReplace.put("type", "regex_replace");
        existsReplace.put("pattern", "\\{.*\\}");
        existsReplace.put("replacement", "Field exists");
        existsRoute.add(existsReplace);
        routes.add(Map.of("exists", existsRoute));

        // not_exists
        List<Map<String, Object>> notExistsRoute = new ArrayList<>();
        Map<String, Object> notExistsReplace = new HashMap<>();
        notExistsReplace.put("type", "regex_replace");
        notExistsReplace.put("pattern", "\\{.*\\}");
        notExistsReplace.put("replacement", "Field does not exist");
        notExistsRoute.add(notExistsReplace);
        routes.add(Map.of("not_exists", notExistsRoute));

        conditionalConfig.put("routes", routes);

        OutputProcessor processor = ProcessorChain.ProcessorRegistry.createProcessor("conditional", conditionalConfig);
        assertEquals("Field does not exist", processor.process(input));

        conditionalConfig.put("path", "$.required");
        processor = ProcessorChain.ProcessorRegistry.createProcessor("conditional", conditionalConfig);
        assertEquals("Field exists", processor.process(input));

        conditionalConfig.put("path", "$.optional");
        processor = ProcessorChain.ProcessorRegistry.createProcessor("conditional", conditionalConfig);
        assertEquals("Field does not exist", processor.process(input));
    }

    @Test
    public void testNoMatchingConditionUsesDefault() {
        Map<String, Object> input = new HashMap<>();
        input.put("status", "unknown");

        Map<String, Object> conditionalConfig = new HashMap<>();
        conditionalConfig.put("type", "conditional");
        conditionalConfig.put("path", "$.status");

        List<Map<String, Object>> routes = new ArrayList<>();

        // success
        List<Map<String, Object>> successRoute = new ArrayList<>();
        Map<String, Object> successReplace = new HashMap<>();
        successReplace.put("type", "regex_replace");
        successReplace.put("pattern", "^.*$");
        successReplace.put("replacement", "Success route");
        successRoute.add(successReplace);
        routes.add(Map.of("success", successRoute));

        // error
        List<Map<String, Object>> errorRoute = new ArrayList<>();
        Map<String, Object> errorReplace = new HashMap<>();
        errorReplace.put("type", "regex_replace");
        errorReplace.put("pattern", "^.*$");
        errorReplace.put("replacement", "Error route");
        errorRoute.add(errorReplace);
        routes.add(Map.of("error", errorRoute));

        conditionalConfig.put("routes", routes);

        // default
        List<Map<String, Object>> defaultRoute = new ArrayList<>();
        Map<String, Object> defaultReplace = new HashMap<>();
        defaultReplace.put("type", "regex_replace");
        defaultReplace.put("pattern", "^.*$");
        defaultReplace.put("replacement", "Default route");
        defaultRoute.add(defaultReplace);
        conditionalConfig.put("default", defaultRoute);

        OutputProcessor processor = ProcessorChain.ProcessorRegistry.createProcessor("conditional", conditionalConfig);
        assertEquals("Default route", processor.process(input));
    }

    @Test
    public void testChainedProcessors() {
        Map<String, Object> input = new HashMap<>();
        input.put("status", "SUCCESS");

        List<Map<String, Object>> successChain = new ArrayList<>();
        Map<String, Object> step1 = new HashMap<>();
        step1.put("type", "to_string");
        successChain.add(step1);
        Map<String, Object> step1Replace = new HashMap<>();
        step1Replace.put("type", "regex_replace");
        step1Replace.put("pattern", "^.*$");
        step1Replace.put("replacement", "Step 1");
        successChain.add(step1Replace);
        Map<String, Object> step2Replace = new HashMap<>();
        step2Replace.put("type", "regex_replace");
        step2Replace.put("pattern", "^.*$");
        step2Replace.put("replacement", "Step 2");
        successChain.add(step2Replace);

        Map<String, Object> conditionalConfig = new HashMap<>();
        conditionalConfig.put("type", "conditional");
        conditionalConfig.put("path", "$.status");

        List<Map<String, Object>> routes = new ArrayList<>();
        routes.add(Map.of("SUCCESS", successChain));

        List<Map<String, Object>> errorRoute = new ArrayList<>();
        Map<String, Object> errorReplace = new HashMap<>();
        errorReplace.put("type", "regex_replace");
        errorReplace.put("pattern", "^.*$");
        errorReplace.put("replacement", "Error occurred");
        errorRoute.add(errorReplace);
        routes.add(Map.of("ERROR", errorRoute));

        conditionalConfig.put("routes", routes);

        OutputProcessor processor = ProcessorChain.ProcessorRegistry.createProcessor("conditional", conditionalConfig);
        assertEquals("Step 2", processor.process(input));
    }

    @Test
    public void testNoPathSpecified() {
        Map<String, Object> input = new HashMap<>();
        input.put("value", 42);

        Map<String, Object> conditionalConfig = new HashMap<>();
        conditionalConfig.put("type", "conditional");

        List<Map<String, Object>> routes = new ArrayList<>();
        List<Map<String, Object>> existsRoute = new ArrayList<>();
        Map<String, Object> existsReplace = new HashMap<>();
        existsReplace.put("type", "regex_replace");
        existsReplace.put("pattern", "^.*$");
        existsReplace.put("replacement", "Input exists");
        existsRoute.add(existsReplace);
        routes.add(Map.of("exists", existsRoute));

        conditionalConfig.put("routes", routes);

        OutputProcessor processor = ProcessorChain.ProcessorRegistry.createProcessor("conditional", conditionalConfig);
        assertEquals("Input exists", processor.process(input));

        assertNull(processor.process(null));
    }

    @Test
    public void testProcessorInProcessorChain() {
        Map<String, Object> input = new HashMap<>();
        input.put("value", 100);

        List<Map<String, Object>> chainConfig = new ArrayList<>();

        Map<String, Object> extractConfig = new HashMap<>();
        extractConfig.put("type", "jsonpath_filter");
        extractConfig.put("path", "$.value");
        chainConfig.add(extractConfig);

        Map<String, Object> conditionalConfig = new HashMap<>();
        conditionalConfig.put("type", "conditional");

        List<Map<String, Object>> routes = new ArrayList<>();
        List<Map<String, Object>> gtRoute = new ArrayList<>();
        Map<String, Object> gtReplace = new HashMap<>();
        gtReplace.put("type", "regex_replace");
        gtReplace.put("pattern", "^.*$");
        gtReplace.put("replacement", "Greater than 50");
        gtRoute.add(gtReplace);
        routes.add(Map.of(">50", gtRoute));

        List<Map<String, Object>> lteRoute = new ArrayList<>();
        Map<String, Object> lteReplace = new HashMap<>();
        lteReplace.put("type", "regex_replace");
        lteReplace.put("pattern", "^.*$");
        lteReplace.put("replacement", "Less than or equal to 50");
        lteRoute.add(lteReplace);
        routes.add(Map.of("<=50", lteRoute));

        conditionalConfig.put("routes", routes);

        chainConfig.add(conditionalConfig);

        ProcessorChain chain = new ProcessorChain(chainConfig);
        assertEquals("Greater than 50", chain.process(input));
    }

    private ProcessorChain.OutputProcessor createRemoveJsonPathProcessor(String path) {
        Map<String, Object> config = new HashMap<>();
        config.put("type", "remove_jsonpath");
        config.put("path", path);
        return ProcessorChain.ProcessorRegistry.createProcessor("remove_jsonpath", config);
    }

    @Test
    public void testRemoveSimpleField() {
        Map<String, Object> input = new HashMap<>();
        input.put("field1", "value1");
        input.put("field2", "value2");

        ProcessorChain.OutputProcessor processor = createRemoveJsonPathProcessor("$.field1");
        Object result = processor.process(input);

        Map<String, Object> resultMap = (Map<String, Object>) result;
        assertFalse(resultMap.containsKey("field1"));
        assertEquals("value2", resultMap.get("field2"));
    }

    @Test
    public void testRemoveArrayElement() {
        Map<String, Object> input = new HashMap<>();
        List<String> items = new ArrayList<>();
        items.add("item1");
        items.add("item2");
        items.add("item3");
        input.put("items", items);

        ProcessorChain.OutputProcessor processor = createRemoveJsonPathProcessor("$.items[1]");
        Object result = processor.process(input);

        List<String> resultItems = com.jayway.jsonpath.JsonPath.read(StringUtils.toJson(result), "$.items");
        assertEquals(2, resultItems.size());
        assertEquals("item1", resultItems.get(0));
        assertEquals("item3", resultItems.get(1));
    }

    @Test
    public void testRemoveNestedObject() {
        Map<String, Object> input = new HashMap<>();
        Map<String, Object> nested = new HashMap<>();
        nested.put("innerField", "value");
        input.put("outer", nested);

        ProcessorChain.OutputProcessor processor = createRemoveJsonPathProcessor("$.outer.innerField");
        Object result = processor.process(input);

        Map<String, Object> resultOuter = com.jayway.jsonpath.JsonPath.read(StringUtils.toJson(result), "$.outer");
        assertFalse(resultOuter.containsKey("innerField"));
    }

    @Test
    public void testRemoveFromNestedArray() {
        Map<String, Object> input = new HashMap<>();
        List<Map<String, Object>> items = new ArrayList<>();

        Map<String, Object> item1 = new HashMap<>();
        item1.put("id", "1");
        item1.put("value", "first");

        Map<String, Object> item2 = new HashMap<>();
        item2.put("id", "2");
        item2.put("value", "second");

        items.add(item1);
        items.add(item2);
        input.put("items", items);

        ProcessorChain.OutputProcessor processor = createRemoveJsonPathProcessor("$.items[0].value");
        Object result = processor.process(input);

        Map<String, Object> firstItem = com.jayway.jsonpath.JsonPath.read(StringUtils.toJson(result), "$.items[0]");
        assertEquals("1", firstItem.get("id"));
        assertFalse(firstItem.containsKey("value"));
    }

    @Test
    public void testRemoveNonExistentPath() {
        Map<String, Object> input = new HashMap<>();
        input.put("field", "value");

        ProcessorChain.OutputProcessor processor = createRemoveJsonPathProcessor("$.nonexistent.path");
        Object result = processor.process(input);

        assertEquals(input, result);
    }

    @Test
    public void testRemoveWithInvalidInput() {
        String input = "not a json object";

        ProcessorChain.OutputProcessor processor = createRemoveJsonPathProcessor("$.field");
        Object result = processor.process(input);

        assertEquals(input, result);
    }

}
