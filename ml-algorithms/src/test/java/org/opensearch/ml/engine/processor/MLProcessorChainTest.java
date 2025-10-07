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

/**
 * Unit tests for {@link ProcessorChain}
 */
public class MLProcessorChainTest {

    @Test
    public void testCreateProcessingChainFromConfigs() {
        Map<String, Object> config1 = new HashMap<>();
        config1.put("type", "to_string");

        Map<String, Object> config2 = new HashMap<>();
        config2.put("type", "regex_replace");
        config2.put("pattern", "test");
        config2.put("replacement", "replaced");

        List<Map<String, Object>> configs = Arrays.asList(config1, config2);
        List<MLProcessor> processors = ProcessorChain.createProcessingChain(configs);

        assertNotNull(processors);
        assertEquals(2, processors.size());
        assertTrue(processors.get(0) instanceof MLToStringProcessor);
        assertTrue(processors.get(1) instanceof MLRegexReplaceProcessor);
    }

    @Test
    public void testCreateProcessingChainEmpty() {
        List<MLProcessor> processors = ProcessorChain.createProcessingChain(null);

        assertNotNull(processors);
        assertEquals(0, processors.size());
    }

    @Test
    public void testParseProcessorConfigsFromList() {
        Map<String, Object> config1 = new HashMap<>();
        config1.put("type", "to_string");

        Map<String, Object> config2 = new HashMap<>();
        config2.put("type", "to_string");

        List<Map<String, Object>> configs = Arrays.asList(config1, config2);
        List<MLProcessor> processors = ProcessorChain.parseProcessorConfigs(configs);

        assertNotNull(processors);
        assertEquals(2, processors.size());
    }

    @Test
    public void testParseProcessorConfigsFromSingleMap() {
        Map<String, Object> config = new HashMap<>();
        config.put("type", "to_string");

        List<MLProcessor> processors = ProcessorChain.parseProcessorConfigs(config);

        assertNotNull(processors);
        assertEquals(1, processors.size());
        assertTrue(processors.get(0) instanceof MLToStringProcessor);
    }

    @Test
    public void testParseProcessorConfigsNull() {
        List<MLProcessor> processors = ProcessorChain.parseProcessorConfigs(null);

        assertNotNull(processors);
        assertEquals(0, processors.size());
    }

    @Test
    public void testApplyProcessorsSequentially() {
        Map<String, Object> config1 = new HashMap<>();
        config1.put("type", "regex_replace");
        config1.put("pattern", "hello");
        config1.put("replacement", "hi");

        Map<String, Object> config2 = new HashMap<>();
        config2.put("type", "regex_replace");
        config2.put("pattern", "world");
        config2.put("replacement", "universe");

        List<MLProcessor> processors = ProcessorChain.createProcessingChain(Arrays.asList(config1, config2));

        String input = "hello world";
        Object result = ProcessorChain.applyProcessors(input, processors);

        assertEquals("hi universe", result);
    }

    @Test
    public void testApplyProcessorsEmptyList() {
        String input = "test";
        Object result = ProcessorChain.applyProcessors(input, Arrays.asList());

        assertEquals("test", result);
    }

    @Test
    public void testProcessorChainConstructorWithConfigs() {
        Map<String, Object> config = new HashMap<>();
        config.put("type", "to_string");

        ProcessorChain chain = new ProcessorChain(Arrays.asList(config));

        assertTrue(chain.hasProcessors());
    }

    @Test
    public void testProcessorChainConstructorWithProcessors() {
        MLProcessor processor1 = new MLToStringProcessor(new HashMap<>());
        MLProcessor processor2 = new MLToStringProcessor(new HashMap<>());

        ProcessorChain chain = new ProcessorChain(processor1, processor2);

        assertTrue(chain.hasProcessors());
    }

    @Test
    public void testProcessorChainProcess() {
        Map<String, Object> config = new HashMap<>();
        config.put("type", "regex_replace");
        config.put("pattern", "test");
        config.put("replacement", "result");

        ProcessorChain chain = new ProcessorChain(Arrays.asList(config));

        String input = "test string";
        Object result = chain.process(input);

        assertEquals("result string", result);
    }

    @Test
    public void testProcessorChainHasProcessors() {
        Map<String, Object> config = new HashMap<>();
        config.put("type", "to_string");

        ProcessorChain chain = new ProcessorChain(Arrays.asList(config));

        assertTrue(chain.hasProcessors());
    }

    @Test
    public void testProcessorChainHasNoProcessors() {
        ProcessorChain chain = new ProcessorChain(Arrays.asList());

        assertFalse(chain.hasProcessors());
    }

    @Test
    public void testExtractProcessorConfigsFromParams() {
        Map<String, Object> params = new HashMap<>();
        params
            .put(
                "output_processors",
                Arrays.asList(createProcessorConfig("to_string"), createProcessorConfig("regex_replace", "pattern", "test"))
            );

        List<Map<String, Object>> configs = ProcessorChain.extractProcessorConfigs(params);

        assertNotNull(configs);
        assertEquals(2, configs.size());
    }

    @Test
    public void testExtractProcessorConfigsFromJsonString() {
        Map<String, Object> params = new HashMap<>();
        params.put("output_processors", "[{\"type\": \"to_string\"}]");

        List<Map<String, Object>> configs = ProcessorChain.extractProcessorConfigs(params);

        assertNotNull(configs);
        assertEquals(1, configs.size());
    }

    @Test
    public void testExtractProcessorConfigsNoKey() {
        Map<String, Object> params = new HashMap<>();

        List<Map<String, Object>> configs = ProcessorChain.extractProcessorConfigs(params);

        assertNotNull(configs);
        assertEquals(0, configs.size());
    }

    @Test
    public void testExtractProcessorConfigsNullParams() {
        List<Map<String, Object>> configs = ProcessorChain.extractProcessorConfigs(null);

        assertNotNull(configs);
        assertEquals(0, configs.size());
    }

    @Test
    public void testExtractProcessorConfigsInvalidJson() {
        Map<String, Object> params = new HashMap<>();
        params.put("output_processors", "{invalid json}");

        List<Map<String, Object>> configs = ProcessorChain.extractProcessorConfigs(params);

        assertNotNull(configs);
        assertEquals(0, configs.size());
    }

    @Test
    public void testComplexProcessorChain() {
        Map<String, Object> extractConfig = new HashMap<>();
        extractConfig.put("type", "extract_json");
        extractConfig.put("extract_type", "object");

        Map<String, Object> removeConfig = new HashMap<>();
        removeConfig.put("type", "remove_jsonpath");
        removeConfig.put("paths", Arrays.asList("$.password"));

        Map<String, Object> toStringConfig = new HashMap<>();
        toStringConfig.put("type", "to_string");

        ProcessorChain chain = new ProcessorChain(Arrays.asList(extractConfig, removeConfig, toStringConfig));

        String input = "Data: {\"name\": \"John\", \"age\": 30, \"password\": \"secret\"}";
        Object result = chain.process(input);

        assertNotNull(result);
        assertTrue(result instanceof String);
        String resultStr = (String) result;
        assertTrue(resultStr.contains("name"));
        assertTrue(resultStr.contains("John"));
        assertTrue(!resultStr.contains("password"));
    }

    @Test
    public void testProcessorChainWithRemoveFields() {
        // Remove password and ssn fields in a single processor
        Map<String, Object> removeConfig = new HashMap<>();
        removeConfig.put("type", "remove_jsonpath");
        removeConfig.put("paths", Arrays.asList("$.password", "$.ssn"));

        // Convert to string
        Map<String, Object> toStringConfig = new HashMap<>();
        toStringConfig.put("type", "to_string");

        ProcessorChain chain = new ProcessorChain(Arrays.asList(removeConfig, toStringConfig));

        Map<String, Object> input = new HashMap<>();
        input.put("username", "john");
        input.put("password", "secret");
        input.put("ssn", "123-45-6789");

        Object result = chain.process(input);

        assertNotNull(result);
        assertTrue(result instanceof String);
        String resultStr = (String) result;
        assertTrue(resultStr.contains("username"));
        assertTrue(!resultStr.contains("password"));
        assertTrue(!resultStr.contains("ssn"));
    }

    @Test
    public void testProcessorChainWithSetField() {
        Map<String, Object> setConfig = new HashMap<>();
        setConfig.put("type", "set_field");
        setConfig.put("path", "$.processed");
        setConfig.put("value", true);

        ProcessorChain chain = new ProcessorChain(Arrays.asList(setConfig));

        Map<String, Object> input = new HashMap<>();
        input.put("data", "test");

        Object result = chain.process(input);

        assertNotNull(result);
        assertTrue(result instanceof Map);
        Map<String, Object> resultMap = (Map<String, Object>) result;
        assertEquals(true, resultMap.get("processed"));
    }

    @Test
    public void testProcessorChainWithRegexCapture() {
        Map<String, Object> captureConfig = new HashMap<>();
        captureConfig.put("type", "regex_capture");
        captureConfig.put("pattern", "([a-zA-Z]+)@([a-zA-Z]+)");
        captureConfig.put("groups", Arrays.asList(1, 2));

        ProcessorChain chain = new ProcessorChain(Arrays.asList(captureConfig));

        String input = "Email: john@example";
        Object result = chain.process(input);

        assertNotNull(result);
        assertTrue(result instanceof List);
        List<?> resultList = (List<?>) result;
        assertEquals(2, resultList.size());
        assertEquals("john", resultList.get(0));
        assertEquals("example", resultList.get(1));
    }

    @Test
    public void testProcessorChainWithJsonPathFilter() {
        Map<String, Object> filterConfig = new HashMap<>();
        filterConfig.put("type", "jsonpath_filter");
        filterConfig.put("path", "$.user.name");

        ProcessorChain chain = new ProcessorChain(Arrays.asList(filterConfig));

        Map<String, Object> user = new HashMap<>();
        user.put("name", "John");
        user.put("age", 30);

        Map<String, Object> input = new HashMap<>();
        input.put("user", user);

        Object result = chain.process(input);

        assertEquals("John", result);
    }

    @Test
    public void testProcessorChainWithConditional() {
        Map<String, Object> adminRoute = new HashMap<>();
        adminRoute.put("admin", Arrays.asList(createProcessorConfig("to_string")));

        Map<String, Object> conditionalConfig = new HashMap<>();
        conditionalConfig.put("type", "conditional");
        conditionalConfig.put("path", "$.role");
        conditionalConfig.put("routes", Arrays.asList(adminRoute));

        ProcessorChain chain = new ProcessorChain(Arrays.asList(conditionalConfig));

        Map<String, Object> input = new HashMap<>();
        input.put("role", "admin");
        input.put("data", "test");

        Object result = chain.process(input);

        assertNotNull(result);
        assertTrue(result instanceof String);
    }

    @Test
    public void testProcessorChainWithProcessAndSet() {
        Map<String, Object> toStringConfig = new HashMap<>();
        toStringConfig.put("type", "to_string");

        Map<String, Object> processAndSetConfig = new HashMap<>();
        processAndSetConfig.put("type", "process_and_set");
        processAndSetConfig.put("path", "$.result");
        processAndSetConfig.put("processors", Arrays.asList(toStringConfig));

        ProcessorChain chain = new ProcessorChain(Arrays.asList(processAndSetConfig));

        Map<String, Object> input = new HashMap<>();
        input.put("data", "test");

        Object result = chain.process(input);

        assertNotNull(result);
        assertTrue(result instanceof Map);
        Map<String, Object> resultMap = (Map<String, Object>) result;
        assertTrue(resultMap.containsKey("result"));
    }

    @Test
    public void testProcessorChainPreservesDataThroughMultipleSteps() {
        Map<String, Object> setConfig = new HashMap<>();
        setConfig.put("type", "set_field");
        setConfig.put("path", "$.step1");
        setConfig.put("value", "completed");

        Map<String, Object> setConfig2 = new HashMap<>();
        setConfig2.put("type", "set_field");
        setConfig2.put("path", "$.step2");
        setConfig2.put("value", "completed");

        ProcessorChain chain = new ProcessorChain(Arrays.asList(setConfig, setConfig2));

        Map<String, Object> input = new HashMap<>();
        input.put("original", "data");

        Object result = chain.process(input);

        assertNotNull(result);
        assertTrue(result instanceof Map);
        Map<String, Object> resultMap = (Map<String, Object>) result;
        assertEquals("data", resultMap.get("original"));
        assertEquals("completed", resultMap.get("step1"));
        assertEquals("completed", resultMap.get("step2"));
    }

    @Test
    public void testProcessorChainWithEmptyInput() {
        Map<String, Object> config = new HashMap<>();
        config.put("type", "to_string");

        ProcessorChain chain = new ProcessorChain(Arrays.asList(config));

        Map<String, Object> input = new HashMap<>();
        Object result = chain.process(input);

        assertNotNull(result);
        assertEquals("{}", result);
    }

    @Test
    public void testProcessorChainWithNullInput() {
        Map<String, Object> config = new HashMap<>();
        config.put("type", "to_string");

        ProcessorChain chain = new ProcessorChain(Arrays.asList(config));

        Object result = chain.process(null);

        assertEquals("null", result);
    }

    @Test
    public void testMultipleProcessorChainsIndependent() {
        Map<String, Object> config1 = new HashMap<>();
        config1.put("type", "regex_replace");
        config1.put("pattern", "test");
        config1.put("replacement", "first");

        Map<String, Object> config2 = new HashMap<>();
        config2.put("type", "regex_replace");
        config2.put("pattern", "test");
        config2.put("replacement", "second");

        ProcessorChain chain1 = new ProcessorChain(Arrays.asList(config1));
        ProcessorChain chain2 = new ProcessorChain(Arrays.asList(config2));

        String input = "test";
        Object result1 = chain1.process(input);
        Object result2 = chain2.process(input);

        assertEquals("first", result1);
        assertEquals("second", result2);
    }

    // Helper methods
    private Map<String, Object> createProcessorConfig(String type) {
        Map<String, Object> config = new HashMap<>();
        config.put("type", type);
        return config;
    }

    private Map<String, Object> createProcessorConfig(String type, String key, Object value) {
        Map<String, Object> config = createProcessorConfig(type);
        config.put(key, value);
        return config;
    }
}
