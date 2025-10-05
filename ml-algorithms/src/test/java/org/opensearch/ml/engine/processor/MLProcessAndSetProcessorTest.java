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

import com.jayway.jsonpath.JsonPath;

/**
 * Unit tests for {@link MLProcessAndSetProcessor}
 */
public class MLProcessAndSetProcessorTest {

    @Test
    public void testProcessAndSetWithSingleProcessor() {
        Map<String, Object> toStringConfig = new HashMap<>();
        toStringConfig.put("type", "to_string");

        Map<String, Object> config = new HashMap<>();
        config.put("path", "$.result");
        config.put("processors", Arrays.asList(toStringConfig));

        MLProcessAndSetProcessor processor = new MLProcessAndSetProcessor(config);

        Map<String, Object> input = new HashMap<>();
        input.put("data", "test");

        Object result = processor.process(input);

        assertNotNull(result);
        String resultValue = JsonPath.read(result, "$.result");
        assertEquals("{\"data\":\"test\"}", resultValue);
    }

    @Test
    public void testProcessAndSetWithMultipleProcessors() {
        Map<String, Object> regexConfig = new HashMap<>();
        regexConfig.put("type", "regex_replace");
        regexConfig.put("pattern", "[^a-zA-Z0-9]");
        regexConfig.put("replacement", "_");

        Map<String, Object> toStringConfig = new HashMap<>();
        toStringConfig.put("type", "to_string");

        Map<String, Object> config = new HashMap<>();
        config.put("path", "$.processed");
        config.put("processors", Arrays.asList(toStringConfig, regexConfig));

        MLProcessAndSetProcessor processor = new MLProcessAndSetProcessor(config);

        Map<String, Object> input = new HashMap<>();
        input.put("name", "test-value");

        Object result = processor.process(input);

        assertNotNull(result);
        String processed = JsonPath.read(result, "$.processed");
        assertTrue(processed.contains("_"));
    }

    @Test
    public void testProcessAndSetCreatesNewField() {
        Map<String, Object> toStringConfig = new HashMap<>();
        toStringConfig.put("type", "to_string");

        Map<String, Object> config = new HashMap<>();
        config.put("path", "$.newField");
        config.put("processors", Arrays.asList(toStringConfig));

        MLProcessAndSetProcessor processor = new MLProcessAndSetProcessor(config);

        Map<String, Object> input = new HashMap<>();
        input.put("existing", "value");

        Object result = processor.process(input);

        assertNotNull(result);
        String existing = JsonPath.read(result, "$.existing");
        String newField = JsonPath.read(result, "$.newField");
        assertEquals("value", existing);
        assertNotNull(newField);
    }

    @Test
    public void testProcessAndSetUpdatesExistingField() {
        Map<String, Object> toStringConfig = new HashMap<>();
        toStringConfig.put("type", "to_string");

        Map<String, Object> config = new HashMap<>();
        config.put("path", "$.status");
        config.put("processors", Arrays.asList(toStringConfig));

        MLProcessAndSetProcessor processor = new MLProcessAndSetProcessor(config);

        Map<String, Object> input = new HashMap<>();
        input.put("status", "original");

        Object result = processor.process(input);

        assertNotNull(result);
        String status = JsonPath.read(result, "$.status");
        assertEquals("{\"status\":\"original\"}", status);
    }

    @Test
    public void testProcessAndSetWithNestedPath() {
        Map<String, Object> toStringConfig = new HashMap<>();
        toStringConfig.put("type", "to_string");

        Map<String, Object> config = new HashMap<>();
        config.put("path", "$.metadata.processed");
        config.put("processors", Arrays.asList(toStringConfig));

        MLProcessAndSetProcessor processor = new MLProcessAndSetProcessor(config);

        Map<String, Object> metadata = new HashMap<>();
        Map<String, Object> input = new HashMap<>();
        input.put("metadata", metadata);
        input.put("data", "test");

        Object result = processor.process(input);

        assertNotNull(result);
        String processed = JsonPath.read(result, "$.metadata.processed");
        assertNotNull(processed);
    }

    @Test
    public void testProcessAndSetCreatesNestedPath() {
        Map<String, Object> toStringConfig = new HashMap<>();
        toStringConfig.put("type", "to_string");

        Map<String, Object> config = new HashMap<>();
        config.put("path", "$.summary.result");
        config.put("processors", Arrays.asList(toStringConfig));

        MLProcessAndSetProcessor processor = new MLProcessAndSetProcessor(config);

        Map<String, Object> summary = new HashMap<>();
        Map<String, Object> input = new HashMap<>();
        input.put("summary", summary);
        input.put("data", "test");

        Object result = processor.process(input);

        assertNotNull(result);
        String summaryResult = JsonPath.read(result, "$.summary.result");
        assertNotNull(summaryResult);
    }

    @Test
    public void testProcessAndSetPreservesOtherFields() {
        Map<String, Object> toStringConfig = new HashMap<>();
        toStringConfig.put("type", "to_string");

        Map<String, Object> config = new HashMap<>();
        config.put("path", "$.processed");
        config.put("processors", Arrays.asList(toStringConfig));

        MLProcessAndSetProcessor processor = new MLProcessAndSetProcessor(config);

        Map<String, Object> input = new HashMap<>();
        input.put("field1", "value1");
        input.put("field2", "value2");
        input.put("field3", 123);

        Object result = processor.process(input);

        assertNotNull(result);
        String field1 = JsonPath.read(result, "$.field1");
        String field2 = JsonPath.read(result, "$.field2");
        Integer field3 = JsonPath.read(result, "$.field3");
        assertEquals("value1", field1);
        assertEquals("value2", field2);
        assertEquals(Integer.valueOf(123), field3);
    }

    @Test
    public void testProcessAndSetWithRegexCapture() {
        Map<String, Object> captureConfig = new HashMap<>();
        captureConfig.put("type", "regex_capture");
        captureConfig.put("pattern", "([a-zA-Z]+)@([a-zA-Z]+)");
        captureConfig.put("groups", Arrays.asList(1, 2));

        Map<String, Object> config = new HashMap<>();
        config.put("path", "$.captured");
        config.put("processors", Arrays.asList(captureConfig));

        MLProcessAndSetProcessor processor = new MLProcessAndSetProcessor(config);

        Map<String, Object> input = new HashMap<>();
        input.put("email", "john@example");

        Object result = processor.process(input);

        assertNotNull(result);
        List<?> captured = JsonPath.read(result, "$.captured");
        assertEquals(2, captured.size());
    }

    @Test
    public void testProcessAndSetWithJsonPathFilter() {
        Map<String, Object> filterConfig = new HashMap<>();
        filterConfig.put("type", "jsonpath_filter");
        filterConfig.put("path", "$.user.name");

        Map<String, Object> config = new HashMap<>();
        config.put("path", "$.userName");
        config.put("processors", Arrays.asList(filterConfig));

        MLProcessAndSetProcessor processor = new MLProcessAndSetProcessor(config);

        Map<String, Object> user = new HashMap<>();
        user.put("name", "John");
        user.put("age", 30);

        Map<String, Object> input = new HashMap<>();
        input.put("user", user);

        Object result = processor.process(input);

        assertNotNull(result);
        String userName = JsonPath.read(result, "$.userName");
        assertEquals("John", userName);
    }

    @Test
    public void testProcessAndSetWithRemoveJsonPath() {
        Map<String, Object> removeConfig = new HashMap<>();
        removeConfig.put("type", "remove_jsonpath");
        removeConfig.put("path", "$.password");

        Map<String, Object> toStringConfig = new HashMap<>();
        toStringConfig.put("type", "to_string");

        Map<String, Object> config = new HashMap<>();
        config.put("path", "$.sanitized");
        config.put("processors", Arrays.asList(removeConfig, toStringConfig));

        MLProcessAndSetProcessor processor = new MLProcessAndSetProcessor(config);

        Map<String, Object> input = new HashMap<>();
        input.put("username", "john");
        input.put("password", "secret");

        Object result = processor.process(input);

        assertNotNull(result);
        String sanitized = JsonPath.read(result, "$.sanitized");
        assertTrue(!sanitized.contains("password"));
        assertTrue(sanitized.contains("username"));
    }

    @Test
    public void testProcessAndSetWithSetField() {
        Map<String, Object> setConfig = new HashMap<>();
        setConfig.put("type", "set_field");
        setConfig.put("path", "$.status");
        setConfig.put("value", "processed");

        Map<String, Object> config = new HashMap<>();
        config.put("path", "$.modified");
        config.put("processors", Arrays.asList(setConfig));

        MLProcessAndSetProcessor processor = new MLProcessAndSetProcessor(config);

        Map<String, Object> input = new HashMap<>();
        input.put("data", "test");

        Object result = processor.process(input);

        assertNotNull(result);
        Map<String, Object> modified = JsonPath.read(result, "$.modified");
        assertEquals("processed", modified.get("status"));
    }

    @Test
    public void testProcessAndSetWithDeepNestedPath() {
        Map<String, Object> toStringConfig = new HashMap<>();
        toStringConfig.put("type", "to_string");

        Map<String, Object> config = new HashMap<>();
        config.put("path", "$.level1.level2.level3.result");
        config.put("processors", Arrays.asList(toStringConfig));

        MLProcessAndSetProcessor processor = new MLProcessAndSetProcessor(config);

        Map<String, Object> level3 = new HashMap<>();
        Map<String, Object> level2 = new HashMap<>();
        level2.put("level3", level3);
        Map<String, Object> level1 = new HashMap<>();
        level1.put("level2", level2);
        Map<String, Object> input = new HashMap<>();
        input.put("level1", level1);
        input.put("data", "test");

        Object result = processor.process(input);

        assertNotNull(result);
        String deepResult = JsonPath.read(result, "$.level1.level2.level3.result");
        assertNotNull(deepResult);
    }

    @Test
    public void testProcessAndSetWithComplexTransformation() {
        Map<String, Object> extractConfig = new HashMap<>();
        extractConfig.put("type", "extract_json");
        extractConfig.put("extract_type", "object");

        Map<String, Object> removeConfig = new HashMap<>();
        removeConfig.put("type", "remove_jsonpath");
        removeConfig.put("path", "$.sensitive");

        Map<String, Object> toStringConfig = new HashMap<>();
        toStringConfig.put("type", "to_string");

        Map<String, Object> config = new HashMap<>();
        config.put("path", "$.transformed");
        config.put("processors", Arrays.asList(extractConfig, removeConfig, toStringConfig));

        MLProcessAndSetProcessor processor = new MLProcessAndSetProcessor(config);

        String input = "Data: {\"name\": \"John\", \"sensitive\": \"secret\"}";
        Object result = processor.process(input);

        assertNotNull(result);
    }

    @Test
    public void testProcessAndSetReturnsOriginalOnFailure() {
        Map<String, Object> toStringConfig = new HashMap<>();
        toStringConfig.put("type", "to_string");

        Map<String, Object> config = new HashMap<>();
        config.put("path", "$.invalid[0].path");
        config.put("processors", Arrays.asList(toStringConfig));

        MLProcessAndSetProcessor processor = new MLProcessAndSetProcessor(config);

        String input = "simple string";
        Object result = processor.process(input);

        // Should return original input on failure
        assertEquals(input, result);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testMissingPathConfig() {
        Map<String, Object> toStringConfig = new HashMap<>();
        toStringConfig.put("type", "to_string");

        Map<String, Object> config = new HashMap<>();
        config.put("processors", Arrays.asList(toStringConfig));

        new MLProcessAndSetProcessor(config);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testEmptyPathConfig() {
        Map<String, Object> toStringConfig = new HashMap<>();
        toStringConfig.put("type", "to_string");

        Map<String, Object> config = new HashMap<>();
        config.put("path", "");
        config.put("processors", Arrays.asList(toStringConfig));

        new MLProcessAndSetProcessor(config);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testWhitespaceOnlyPath() {
        Map<String, Object> toStringConfig = new HashMap<>();
        toStringConfig.put("type", "to_string");

        Map<String, Object> config = new HashMap<>();
        config.put("path", "   ");
        config.put("processors", Arrays.asList(toStringConfig));

        new MLProcessAndSetProcessor(config);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testMissingProcessorsConfig() {
        Map<String, Object> config = new HashMap<>();
        config.put("path", "$.result");

        new MLProcessAndSetProcessor(config);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testEmptyProcessorsList() {
        Map<String, Object> config = new HashMap<>();
        config.put("path", "$.result");
        config.put("processors", Arrays.asList());

        new MLProcessAndSetProcessor(config);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testNullPath() {
        Map<String, Object> toStringConfig = new HashMap<>();
        toStringConfig.put("type", "to_string");

        Map<String, Object> config = new HashMap<>();
        config.put("path", null);
        config.put("processors", Arrays.asList(toStringConfig));

        new MLProcessAndSetProcessor(config);
    }

    @Test
    public void testProcessAndSetWithEmptyInput() {
        Map<String, Object> toStringConfig = new HashMap<>();
        toStringConfig.put("type", "to_string");

        Map<String, Object> config = new HashMap<>();
        config.put("path", "$.result");
        config.put("processors", Arrays.asList(toStringConfig));

        MLProcessAndSetProcessor processor = new MLProcessAndSetProcessor(config);

        Map<String, Object> input = new HashMap<>();
        Object result = processor.process(input);

        assertNotNull(result);
        String resultValue = JsonPath.read(result, "$.result");
        assertEquals("{}", resultValue);
    }

    @Test
    public void testProcessAndSetWithNullInput() {
        Map<String, Object> toStringConfig = new HashMap<>();
        toStringConfig.put("type", "to_string");

        Map<String, Object> config = new HashMap<>();
        config.put("path", "$.result");
        config.put("processors", Arrays.asList(toStringConfig));

        MLProcessAndSetProcessor processor = new MLProcessAndSetProcessor(config);

        Object result = processor.process(null);

        // Should return null on failure
        assertEquals(null, result);
    }

    @Test
    public void testProcessAndSetChainedWithOtherProcessors() {
        Map<String, Object> toStringConfig = new HashMap<>();
        toStringConfig.put("type", "to_string");

        Map<String, Object> processAndSetConfig = new HashMap<>();
        processAndSetConfig.put("path", "$.intermediate");
        processAndSetConfig.put("processors", Arrays.asList(toStringConfig));

        Map<String, Object> setConfig = new HashMap<>();
        setConfig.put("type", "set_field");
        setConfig.put("path", "$.final");
        setConfig.put("value", "done");

        MLProcessAndSetProcessor processor1 = new MLProcessAndSetProcessor(processAndSetConfig);
        MLSetFieldProcessor processor2 = new MLSetFieldProcessor(setConfig);

        Map<String, Object> input = new HashMap<>();
        input.put("data", "test");

        Object result = processor1.process(input);
        result = processor2.process(result);

        assertNotNull(result);
        String intermediate = JsonPath.read(result, "$.intermediate");
        String finalValue = JsonPath.read(result, "$.final");
        assertNotNull(intermediate);
        assertEquals("done", finalValue);
    }

    @Test
    public void testProcessAndSetWithConditionalProcessor() {
        Map<String, Object> toStringConfig = new HashMap<>();
        toStringConfig.put("type", "to_string");

        Map<String, Object> adminRoute = new HashMap<>();
        adminRoute.put("admin", Arrays.asList(toStringConfig));

        Map<String, Object> conditionalConfig = new HashMap<>();
        conditionalConfig.put("type", "conditional");
        conditionalConfig.put("path", "$.role");
        conditionalConfig.put("routes", Arrays.asList(adminRoute));

        Map<String, Object> config = new HashMap<>();
        config.put("path", "$.processed");
        config.put("processors", Arrays.asList(conditionalConfig));

        MLProcessAndSetProcessor processor = new MLProcessAndSetProcessor(config);

        Map<String, Object> input = new HashMap<>();
        input.put("role", "admin");
        input.put("data", "test");

        Object result = processor.process(input);

        assertNotNull(result);
        String processed = JsonPath.read(result, "$.processed");
        assertNotNull(processed);
    }

    @Test
    public void testProcessAndSetWithExtractJson() {
        Map<String, Object> extractConfig = new HashMap<>();
        extractConfig.put("type", "extract_json");
        extractConfig.put("extract_type", "object");

        Map<String, Object> config = new HashMap<>();
        config.put("path", "$.extracted");
        config.put("processors", Arrays.asList(extractConfig));

        MLProcessAndSetProcessor processor = new MLProcessAndSetProcessor(config);

        String input = "Data: {\"name\": \"John\", \"age\": 30}";
        Object result = processor.process(input);

        assertNotNull(result);
    }

    @Test
    public void testProcessAndSetMultipleTimes() {
        Map<String, Object> toStringConfig = new HashMap<>();
        toStringConfig.put("type", "to_string");

        Map<String, Object> config1 = new HashMap<>();
        config1.put("path", "$.result1");
        config1.put("processors", Arrays.asList(toStringConfig));

        Map<String, Object> config2 = new HashMap<>();
        config2.put("path", "$.result2");
        config2.put("processors", Arrays.asList(toStringConfig));

        MLProcessAndSetProcessor processor1 = new MLProcessAndSetProcessor(config1);
        MLProcessAndSetProcessor processor2 = new MLProcessAndSetProcessor(config2);

        Map<String, Object> input = new HashMap<>();
        input.put("data", "test");

        Object result = processor1.process(input);
        result = processor2.process(result);

        assertNotNull(result);
        String result1 = JsonPath.read(result, "$.result1");
        String result2 = JsonPath.read(result, "$.result2");
        assertNotNull(result1);
        assertNotNull(result2);
    }

    @Test
    public void testProcessAndSetWithRegexReplace() {
        Map<String, Object> regexConfig = new HashMap<>();
        regexConfig.put("type", "regex_replace");
        regexConfig.put("pattern", "\\s+");
        regexConfig.put("replacement", "-");

        Map<String, Object> toStringConfig = new HashMap<>();
        toStringConfig.put("type", "to_string");

        Map<String, Object> config = new HashMap<>();
        config.put("path", "$.normalized");
        config.put("processors", Arrays.asList(toStringConfig, regexConfig));

        MLProcessAndSetProcessor processor = new MLProcessAndSetProcessor(config);

        Map<String, Object> input = new HashMap<>();
        input.put("text", "hello world test");

        Object result = processor.process(input);

        assertNotNull(result);
        String normalized = JsonPath.read(result, "$.normalized");
        assertTrue(normalized.contains("-"));
    }

    @Test
    public void testProcessAndSetPreservesInputStructure() {
        Map<String, Object> toStringConfig = new HashMap<>();
        toStringConfig.put("type", "to_string");

        Map<String, Object> config = new HashMap<>();
        config.put("path", "$.summary");
        config.put("processors", Arrays.asList(toStringConfig));

        MLProcessAndSetProcessor processor = new MLProcessAndSetProcessor(config);

        Map<String, Object> nested = new HashMap<>();
        nested.put("key", "value");

        Map<String, Object> input = new HashMap<>();
        input.put("id", 123);
        input.put("name", "test");
        input.put("nested", nested);

        Object result = processor.process(input);

        assertNotNull(result);
        Integer id = JsonPath.read(result, "$.id");
        String name = JsonPath.read(result, "$.name");
        Map<String, Object> nestedResult = JsonPath.read(result, "$.nested");
        String summary = JsonPath.read(result, "$.summary");

        assertEquals(Integer.valueOf(123), id);
        assertEquals("test", name);
        assertEquals("value", nestedResult.get("key"));
        assertNotNull(summary);
    }
}
