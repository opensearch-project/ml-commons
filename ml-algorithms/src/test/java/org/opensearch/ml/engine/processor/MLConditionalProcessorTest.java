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
import java.util.Map;

import org.junit.Test;

/**
 * Unit tests for {@link MLConditionalProcessor}
 */
public class MLConditionalProcessorTest {

    @Test
    public void testExactStringMatch() {
        Map<String, Object> toStringConfig = new HashMap<>();
        toStringConfig.put("type", "to_string");

        Map<String, Object> adminRoute = new HashMap<>();
        adminRoute.put("admin", Arrays.asList(toStringConfig));

        Map<String, Object> config = new HashMap<>();
        config.put("path", "$.role");
        config.put("routes", Arrays.asList(adminRoute));

        MLConditionalProcessor processor = new MLConditionalProcessor(config);

        Map<String, Object> input = new HashMap<>();
        input.put("role", "admin");
        input.put("data", "test");

        Object result = processor.process(input);

        assertNotNull(result);
        assertTrue(result instanceof String);
    }

    @Test
    public void testExistsCondition() {
        Map<String, Object> toStringConfig = new HashMap<>();
        toStringConfig.put("type", "to_string");

        Map<String, Object> existsRoute = new HashMap<>();
        existsRoute.put("exists", Arrays.asList(toStringConfig));

        Map<String, Object> config = new HashMap<>();
        config.put("path", "$.optional");
        config.put("routes", Arrays.asList(existsRoute));

        MLConditionalProcessor processor = new MLConditionalProcessor(config);

        Map<String, Object> input = new HashMap<>();
        input.put("optional", "value");

        Object result = processor.process(input);

        assertNotNull(result);
        assertTrue(result instanceof String);
    }

    @Test
    public void testNullCondition() {
        Map<String, Object> setConfig = new HashMap<>();
        setConfig.put("type", "set_field");
        setConfig.put("path", "$.default");
        setConfig.put("value", "set");

        Map<String, Object> nullRoute = new HashMap<>();
        nullRoute.put("null", Arrays.asList(setConfig));

        Map<String, Object> config = new HashMap<>();
        config.put("path", "$.missing");
        config.put("routes", Arrays.asList(nullRoute));

        MLConditionalProcessor processor = new MLConditionalProcessor(config);

        Map<String, Object> input = new HashMap<>();
        input.put("data", "test");

        Object result = processor.process(input);

        assertNotNull(result);
        assertTrue(result instanceof Map);
        Map<String, Object> resultMap = (Map<String, Object>) result;
        assertEquals("set", resultMap.get("default"));
    }

    @Test
    public void testNotExistsCondition() {
        Map<String, Object> setConfig = new HashMap<>();
        setConfig.put("type", "set_field");
        setConfig.put("path", "$.created");
        setConfig.put("value", true);

        Map<String, Object> notExistsRoute = new HashMap<>();
        notExistsRoute.put("not_exists", Arrays.asList(setConfig));

        Map<String, Object> config = new HashMap<>();
        config.put("path", "$.optional");
        config.put("routes", Arrays.asList(notExistsRoute));

        MLConditionalProcessor processor = new MLConditionalProcessor(config);

        Map<String, Object> input = new HashMap<>();
        input.put("data", "test");

        Object result = processor.process(input);

        assertNotNull(result);
        assertTrue(result instanceof Map);
        Map<String, Object> resultMap = (Map<String, Object>) result;
        assertEquals(true, resultMap.get("created"));
    }

    @Test
    public void testGreaterThanCondition() {
        Map<String, Object> setConfig = new HashMap<>();
        setConfig.put("type", "set_field");
        setConfig.put("path", "$.category");
        setConfig.put("value", "adult");

        Map<String, Object> gtRoute = new HashMap<>();
        gtRoute.put(">18", Arrays.asList(setConfig));

        Map<String, Object> config = new HashMap<>();
        config.put("path", "$.age");
        config.put("routes", Arrays.asList(gtRoute));

        MLConditionalProcessor processor = new MLConditionalProcessor(config);

        Map<String, Object> input = new HashMap<>();
        input.put("age", 25);

        Object result = processor.process(input);

        assertNotNull(result);
        assertTrue(result instanceof Map);
        Map<String, Object> resultMap = (Map<String, Object>) result;
        assertEquals("adult", resultMap.get("category"));
    }

    @Test
    public void testLessThanCondition() {
        Map<String, Object> setConfig = new HashMap<>();
        setConfig.put("type", "set_field");
        setConfig.put("path", "$.category");
        setConfig.put("value", "minor");

        Map<String, Object> ltRoute = new HashMap<>();
        ltRoute.put("<18", Arrays.asList(setConfig));

        Map<String, Object> config = new HashMap<>();
        config.put("path", "$.age");
        config.put("routes", Arrays.asList(ltRoute));

        MLConditionalProcessor processor = new MLConditionalProcessor(config);

        Map<String, Object> input = new HashMap<>();
        input.put("age", 15);

        Object result = processor.process(input);

        assertNotNull(result);
        assertTrue(result instanceof Map);
        Map<String, Object> resultMap = (Map<String, Object>) result;
        assertEquals("minor", resultMap.get("category"));
    }

    @Test
    public void testGreaterThanOrEqualCondition() {
        Map<String, Object> setConfig = new HashMap<>();
        setConfig.put("type", "set_field");
        setConfig.put("path", "$.eligible");
        setConfig.put("value", true);

        Map<String, Object> gteRoute = new HashMap<>();
        gteRoute.put(">=18", Arrays.asList(setConfig));

        Map<String, Object> config = new HashMap<>();
        config.put("path", "$.age");
        config.put("routes", Arrays.asList(gteRoute));

        MLConditionalProcessor processor = new MLConditionalProcessor(config);

        Map<String, Object> input = new HashMap<>();
        input.put("age", 18);

        Object result = processor.process(input);

        assertNotNull(result);
        assertTrue(result instanceof Map);
        Map<String, Object> resultMap = (Map<String, Object>) result;
        assertEquals(true, resultMap.get("eligible"));
    }

    @Test
    public void testLessThanOrEqualCondition() {
        Map<String, Object> setConfig = new HashMap<>();
        setConfig.put("type", "set_field");
        setConfig.put("path", "$.discount");
        setConfig.put("value", 10);

        Map<String, Object> lteRoute = new HashMap<>();
        lteRoute.put("<=100", Arrays.asList(setConfig));

        Map<String, Object> config = new HashMap<>();
        config.put("path", "$.price");
        config.put("routes", Arrays.asList(lteRoute));

        MLConditionalProcessor processor = new MLConditionalProcessor(config);

        Map<String, Object> input = new HashMap<>();
        input.put("price", 100);

        Object result = processor.process(input);

        assertNotNull(result);
        assertTrue(result instanceof Map);
        Map<String, Object> resultMap = (Map<String, Object>) result;
        assertEquals(10, resultMap.get("discount"));
    }

    @Test
    public void testEqualToCondition() {
        Map<String, Object> setConfig = new HashMap<>();
        setConfig.put("type", "set_field");
        setConfig.put("path", "$.status");
        setConfig.put("value", "exact");

        Map<String, Object> eqRoute = new HashMap<>();
        eqRoute.put("==42", Arrays.asList(setConfig));

        Map<String, Object> config = new HashMap<>();
        config.put("path", "$.value");
        config.put("routes", Arrays.asList(eqRoute));

        MLConditionalProcessor processor = new MLConditionalProcessor(config);

        Map<String, Object> input = new HashMap<>();
        input.put("value", 42);

        Object result = processor.process(input);

        assertNotNull(result);
        assertTrue(result instanceof Map);
        Map<String, Object> resultMap = (Map<String, Object>) result;
        assertEquals("exact", resultMap.get("status"));
    }

    @Test
    public void testRegexCondition() {
        Map<String, Object> setConfig = new HashMap<>();
        setConfig.put("type", "set_field");
        setConfig.put("path", "$.isInternal");
        setConfig.put("value", true);

        Map<String, Object> regexRoute = new HashMap<>();
        regexRoute.put("regex:.*@company\\.com$", Arrays.asList(setConfig));

        Map<String, Object> config = new HashMap<>();
        config.put("path", "$.email");
        config.put("routes", Arrays.asList(regexRoute));

        MLConditionalProcessor processor = new MLConditionalProcessor(config);

        Map<String, Object> input = new HashMap<>();
        input.put("email", "user@company.com");

        Object result = processor.process(input);

        assertNotNull(result);
        assertTrue(result instanceof Map);
        Map<String, Object> resultMap = (Map<String, Object>) result;
        assertEquals(true, resultMap.get("isInternal"));
    }

    @Test
    public void testContainsCondition() {
        Map<String, Object> setConfig = new HashMap<>();
        setConfig.put("type", "set_field");
        setConfig.put("path", "$.hasKeyword");
        setConfig.put("value", true);

        Map<String, Object> containsRoute = new HashMap<>();
        containsRoute.put("contains:urgent", Arrays.asList(setConfig));

        Map<String, Object> config = new HashMap<>();
        config.put("path", "$.message");
        config.put("routes", Arrays.asList(containsRoute));

        MLConditionalProcessor processor = new MLConditionalProcessor(config);

        Map<String, Object> input = new HashMap<>();
        input.put("message", "This is urgent!");

        Object result = processor.process(input);

        assertNotNull(result);
        assertTrue(result instanceof Map);
        Map<String, Object> resultMap = (Map<String, Object>) result;
        assertEquals(true, resultMap.get("hasKeyword"));
    }

    @Test
    public void testMultipleRoutesFirstMatch() {
        Map<String, Object> setConfig1 = new HashMap<>();
        setConfig1.put("type", "set_field");
        setConfig1.put("path", "$.level");
        setConfig1.put("value", "admin");

        Map<String, Object> setConfig2 = new HashMap<>();
        setConfig2.put("type", "set_field");
        setConfig2.put("path", "$.level");
        setConfig2.put("value", "user");

        Map<String, Object> adminRoute = new HashMap<>();
        adminRoute.put("admin", Arrays.asList(setConfig1));

        Map<String, Object> userRoute = new HashMap<>();
        userRoute.put("user", Arrays.asList(setConfig2));

        Map<String, Object> config = new HashMap<>();
        config.put("path", "$.role");
        config.put("routes", Arrays.asList(adminRoute, userRoute));

        MLConditionalProcessor processor = new MLConditionalProcessor(config);

        Map<String, Object> input = new HashMap<>();
        input.put("role", "admin");

        Object result = processor.process(input);

        assertNotNull(result);
        assertTrue(result instanceof Map);
        Map<String, Object> resultMap = (Map<String, Object>) result;
        assertEquals("admin", resultMap.get("level"));
    }

    @Test
    public void testDefaultRoute() {
        Map<String, Object> setConfig = new HashMap<>();
        setConfig.put("type", "set_field");
        setConfig.put("path", "$.level");
        setConfig.put("value", "guest");

        Map<String, Object> adminRoute = new HashMap<>();
        adminRoute.put("admin", Arrays.asList(setConfig));

        Map<String, Object> defaultConfig = new HashMap<>();
        defaultConfig.put("type", "set_field");
        defaultConfig.put("path", "$.level");
        defaultConfig.put("value", "default");

        Map<String, Object> config = new HashMap<>();
        config.put("path", "$.role");
        config.put("routes", Arrays.asList(adminRoute));
        config.put("default", Arrays.asList(defaultConfig));

        MLConditionalProcessor processor = new MLConditionalProcessor(config);

        Map<String, Object> input = new HashMap<>();
        input.put("role", "unknown");

        Object result = processor.process(input);

        assertNotNull(result);
        assertTrue(result instanceof Map);
        Map<String, Object> resultMap = (Map<String, Object>) result;
        assertEquals("default", resultMap.get("level"));
    }

    @Test
    public void testNoMatchNoDefault() {
        Map<String, Object> setConfig = new HashMap<>();
        setConfig.put("type", "set_field");
        setConfig.put("path", "$.processed");
        setConfig.put("value", true);

        Map<String, Object> adminRoute = new HashMap<>();
        adminRoute.put("admin", Arrays.asList(setConfig));

        Map<String, Object> config = new HashMap<>();
        config.put("path", "$.role");
        config.put("routes", Arrays.asList(adminRoute));

        MLConditionalProcessor processor = new MLConditionalProcessor(config);

        Map<String, Object> input = new HashMap<>();
        input.put("role", "user");
        input.put("data", "test");

        Object result = processor.process(input);

        assertNotNull(result);
        assertTrue(result instanceof Map);
        Map<String, Object> resultMap = (Map<String, Object>) result;
        assertEquals("user", resultMap.get("role"));
        assertEquals("test", resultMap.get("data"));
    }

    @Test
    public void testNoPathEvaluatesEntireInput() {
        Map<String, Object> toStringConfig = new HashMap<>();
        toStringConfig.put("type", "to_string");

        Map<String, Object> existsRoute = new HashMap<>();
        existsRoute.put("exists", Arrays.asList(toStringConfig));

        Map<String, Object> config = new HashMap<>();
        config.put("routes", Arrays.asList(existsRoute));

        MLConditionalProcessor processor = new MLConditionalProcessor(config);

        Map<String, Object> input = new HashMap<>();
        input.put("data", "test");

        Object result = processor.process(input);

        assertNotNull(result);
        assertTrue(result instanceof String);
    }

    @Test
    public void testNumericStringComparison() {
        Map<String, Object> setConfig = new HashMap<>();
        setConfig.put("type", "set_field");
        setConfig.put("path", "$.valid");
        setConfig.put("value", true);

        Map<String, Object> gtRoute = new HashMap<>();
        gtRoute.put(">10", Arrays.asList(setConfig));

        Map<String, Object> config = new HashMap<>();
        config.put("path", "$.count");
        config.put("routes", Arrays.asList(gtRoute));

        MLConditionalProcessor processor = new MLConditionalProcessor(config);

        Map<String, Object> input = new HashMap<>();
        input.put("count", "15");

        Object result = processor.process(input);

        assertNotNull(result);
        assertTrue(result instanceof Map);
        Map<String, Object> resultMap = (Map<String, Object>) result;
        assertEquals(true, resultMap.get("valid"));
    }

    @Test
    public void testNestedPathExtraction() {
        Map<String, Object> setConfig = new HashMap<>();
        setConfig.put("type", "set_field");
        setConfig.put("path", "$.isAdmin");
        setConfig.put("value", true);

        Map<String, Object> adminRoute = new HashMap<>();
        adminRoute.put("admin", Arrays.asList(setConfig));

        Map<String, Object> config = new HashMap<>();
        config.put("path", "$.user.role");
        config.put("routes", Arrays.asList(adminRoute));

        MLConditionalProcessor processor = new MLConditionalProcessor(config);

        Map<String, Object> user = new HashMap<>();
        user.put("role", "admin");
        user.put("name", "John");

        Map<String, Object> input = new HashMap<>();
        input.put("user", user);

        Object result = processor.process(input);

        assertNotNull(result);
        assertTrue(result instanceof Map);
        Map<String, Object> resultMap = (Map<String, Object>) result;
        assertEquals(true, resultMap.get("isAdmin"));
    }

    @Test
    public void testChainedProcessors() {
        Map<String, Object> removeConfig = new HashMap<>();
        removeConfig.put("type", "remove_jsonpath");
        removeConfig.put("path", "$.password");

        Map<String, Object> toStringConfig = new HashMap<>();
        toStringConfig.put("type", "to_string");

        Map<String, Object> adminRoute = new HashMap<>();
        adminRoute.put("admin", Arrays.asList(removeConfig, toStringConfig));

        Map<String, Object> config = new HashMap<>();
        config.put("path", "$.role");
        config.put("routes", Arrays.asList(adminRoute));

        MLConditionalProcessor processor = new MLConditionalProcessor(config);

        Map<String, Object> input = new HashMap<>();
        input.put("role", "admin");
        input.put("username", "john");
        input.put("password", "secret");

        Object result = processor.process(input);

        assertNotNull(result);
        assertTrue(result instanceof String);
        String resultStr = (String) result;
        assertTrue(resultStr.contains("username"));
        assertTrue(!resultStr.contains("password"));
    }

    @Test
    public void testRegexNoMatch() {
        Map<String, Object> setConfig = new HashMap<>();
        setConfig.put("type", "set_field");
        setConfig.put("path", "$.matched");
        setConfig.put("value", true);

        Map<String, Object> regexRoute = new HashMap<>();
        regexRoute.put("regex:^\\d+$", Arrays.asList(setConfig));

        Map<String, Object> config = new HashMap<>();
        config.put("path", "$.value");
        config.put("routes", Arrays.asList(regexRoute));

        MLConditionalProcessor processor = new MLConditionalProcessor(config);

        Map<String, Object> input = new HashMap<>();
        input.put("value", "abc123");

        Object result = processor.process(input);

        assertNotNull(result);
        assertTrue(result instanceof Map);
        Map<String, Object> resultMap = (Map<String, Object>) result;
        assertEquals("abc123", resultMap.get("value"));
    }

    @Test
    public void testContainsNoMatch() {
        Map<String, Object> setConfig = new HashMap<>();
        setConfig.put("type", "set_field");
        setConfig.put("path", "$.found");
        setConfig.put("value", true);

        Map<String, Object> containsRoute = new HashMap<>();
        containsRoute.put("contains:error", Arrays.asList(setConfig));

        Map<String, Object> config = new HashMap<>();
        config.put("path", "$.message");
        config.put("routes", Arrays.asList(containsRoute));

        MLConditionalProcessor processor = new MLConditionalProcessor(config);

        Map<String, Object> input = new HashMap<>();
        input.put("message", "success");

        Object result = processor.process(input);

        assertNotNull(result);
        assertTrue(result instanceof Map);
        Map<String, Object> resultMap = (Map<String, Object>) result;
        assertEquals("success", resultMap.get("message"));
    }

    @Test
    public void testNumericComparisonWithNonNumeric() {
        Map<String, Object> setConfig = new HashMap<>();
        setConfig.put("type", "set_field");
        setConfig.put("path", "$.matched");
        setConfig.put("value", true);

        Map<String, Object> gtRoute = new HashMap<>();
        gtRoute.put(">10", Arrays.asList(setConfig));

        Map<String, Object> config = new HashMap<>();
        config.put("path", "$.value");
        config.put("routes", Arrays.asList(gtRoute));

        MLConditionalProcessor processor = new MLConditionalProcessor(config);

        Map<String, Object> input = new HashMap<>();
        input.put("value", "not a number");

        Object result = processor.process(input);

        assertNotNull(result);
        assertTrue(result instanceof Map);
        Map<String, Object> resultMap = (Map<String, Object>) result;
        assertEquals("not a number", resultMap.get("value"));
    }

    @Test
    public void testEmptyArrayTreatedAsNull() {
        Map<String, Object> setConfig = new HashMap<>();
        setConfig.put("type", "set_field");
        setConfig.put("path", "$.isEmpty");
        setConfig.put("value", true);

        Map<String, Object> nullRoute = new HashMap<>();
        nullRoute.put("null", Arrays.asList(setConfig));

        Map<String, Object> config = new HashMap<>();
        config.put("path", "$.items");
        config.put("routes", Arrays.asList(nullRoute));

        MLConditionalProcessor processor = new MLConditionalProcessor(config);

        Map<String, Object> input = new HashMap<>();
        input.put("items", Arrays.asList());

        Object result = processor.process(input);

        assertNotNull(result);
        assertTrue(result instanceof Map);
        Map<String, Object> resultMap = (Map<String, Object>) result;
        assertEquals(true, resultMap.get("isEmpty"));
    }

    @Test
    public void testPreservesOriginalInputStructure() {
        Map<String, Object> setConfig = new HashMap<>();
        setConfig.put("type", "set_field");
        setConfig.put("path", "$.processed");
        setConfig.put("value", true);

        Map<String, Object> adminRoute = new HashMap<>();
        adminRoute.put("admin", Arrays.asList(setConfig));

        Map<String, Object> config = new HashMap<>();
        config.put("path", "$.role");
        config.put("routes", Arrays.asList(adminRoute));

        MLConditionalProcessor processor = new MLConditionalProcessor(config);

        Map<String, Object> nested = new HashMap<>();
        nested.put("key", "value");

        Map<String, Object> input = new HashMap<>();
        input.put("role", "admin");
        input.put("id", 123);
        input.put("nested", nested);

        Object result = processor.process(input);

        assertNotNull(result);
        assertTrue(result instanceof Map);
        Map<String, Object> resultMap = (Map<String, Object>) result;
        assertEquals("admin", resultMap.get("role"));
        assertEquals(123, resultMap.get("id"));
        assertTrue(resultMap.containsKey("nested"));
        assertEquals(true, resultMap.get("processed"));
    }

    @Test
    public void testWithNullInput() {
        Map<String, Object> setConfig = new HashMap<>();
        setConfig.put("type", "set_field");
        setConfig.put("path", "$.processed");
        setConfig.put("value", true);

        Map<String, Object> existsRoute = new HashMap<>();
        existsRoute.put("exists", Arrays.asList(setConfig));

        Map<String, Object> config = new HashMap<>();
        config.put("routes", Arrays.asList(existsRoute));

        MLConditionalProcessor processor = new MLConditionalProcessor(config);

        Object result = processor.process(null);

        assertEquals(null, result);
    }

    @Test
    public void testComplexConditionalChain() {
        Map<String, Object> removeConfig = new HashMap<>();
        removeConfig.put("type", "remove_jsonpath");
        removeConfig.put("path", "$.sensitive");

        Map<String, Object> setConfig = new HashMap<>();
        setConfig.put("type", "set_field");
        setConfig.put("path", "$.sanitized");
        setConfig.put("value", true);

        Map<String, Object> adminRoute = new HashMap<>();
        adminRoute.put(">=18", Arrays.asList(removeConfig, setConfig));

        Map<String, Object> defaultConfig = new HashMap<>();
        defaultConfig.put("type", "set_field");
        defaultConfig.put("path", "$.restricted");
        defaultConfig.put("value", true);

        Map<String, Object> config = new HashMap<>();
        config.put("path", "$.age");
        config.put("routes", Arrays.asList(adminRoute));
        config.put("default", Arrays.asList(defaultConfig));

        MLConditionalProcessor processor = new MLConditionalProcessor(config);

        Map<String, Object> input = new HashMap<>();
        input.put("age", 25);
        input.put("name", "John");
        input.put("sensitive", "data");

        Object result = processor.process(input);

        assertNotNull(result);
        assertTrue(result instanceof Map);
        Map<String, Object> resultMap = (Map<String, Object>) result;
        assertEquals("John", resultMap.get("name"));
        assertEquals(true, resultMap.get("sanitized"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testMissingRoutesConfig() {
        Map<String, Object> config = new HashMap<>();
        config.put("path", "$.role");

        new MLConditionalProcessor(config);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testNullRoutesConfig() {
        Map<String, Object> config = new HashMap<>();
        config.put("path", "$.role");
        config.put("routes", null);

        new MLConditionalProcessor(config);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testEmptyRoutesList() {
        Map<String, Object> config = new HashMap<>();
        config.put("path", "$.role");
        config.put("routes", Arrays.asList());

        new MLConditionalProcessor(config);
    }

    @Test
    public void testInvalidPathReturnsOriginal() {
        Map<String, Object> setConfig = new HashMap<>();
        setConfig.put("type", "set_field");
        setConfig.put("path", "$.processed");
        setConfig.put("value", true);

        Map<String, Object> existsRoute = new HashMap<>();
        existsRoute.put("exists", Arrays.asList(setConfig));

        Map<String, Object> config = new HashMap<>();
        config.put("path", "$.invalid..path");
        config.put("routes", Arrays.asList(existsRoute));

        MLConditionalProcessor processor = new MLConditionalProcessor(config);

        Map<String, Object> input = new HashMap<>();
        input.put("data", "test");

        Object result = processor.process(input);

        assertNotNull(result);
    }

    @Test
    public void testMultipleConditionTypes() {
        Map<String, Object> setConfig1 = new HashMap<>();
        setConfig1.put("type", "set_field");
        setConfig1.put("path", "$.type");
        setConfig1.put("value", "exact");

        Map<String, Object> setConfig2 = new HashMap<>();
        setConfig2.put("type", "set_field");
        setConfig2.put("path", "$.type");
        setConfig2.put("value", "range");

        Map<String, Object> setConfig3 = new HashMap<>();
        setConfig3.put("type", "set_field");
        setConfig3.put("path", "$.type");
        setConfig3.put("value", "pattern");

        Map<String, Object> exactRoute = new HashMap<>();
        exactRoute.put("admin", Arrays.asList(setConfig1));

        Map<String, Object> rangeRoute = new HashMap<>();
        rangeRoute.put(">10", Arrays.asList(setConfig2));

        Map<String, Object> patternRoute = new HashMap<>();
        patternRoute.put("regex:^test.*", Arrays.asList(setConfig3));

        Map<String, Object> config = new HashMap<>();
        config.put("path", "$.value");
        config.put("routes", Arrays.asList(exactRoute, rangeRoute, patternRoute));

        MLConditionalProcessor processor = new MLConditionalProcessor(config);

        Map<String, Object> input = new HashMap<>();
        input.put("value", "testing");

        Object result = processor.process(input);

        assertNotNull(result);
        assertTrue(result instanceof Map);
        Map<String, Object> resultMap = (Map<String, Object>) result;
        assertEquals("pattern", resultMap.get("type"));
    }
}
