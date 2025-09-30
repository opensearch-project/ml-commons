/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.tools.parser;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.junit.Test;
import org.opensearch.ml.common.spi.tools.Parser;

public class ToolParserTests {

    @Test
    public void testCreateProcessingParserWithBaseParser() {
        Parser baseParser = input -> "base_" + input;
        List<Map<String, Object>> processorConfigs = Collections.emptyList();

        Parser parser = ToolParser.createProcessingParser(baseParser, processorConfigs);

        assertNotNull(parser);
        Object result = parser.parse("input");
        assertEquals("base_input", result);
    }

    @Test
    public void testCreateProcessingParserWithoutBaseParser() {
        List<Map<String, Object>> processorConfigs = Collections.emptyList();

        Parser parser = ToolParser.createProcessingParser(null, processorConfigs);

        assertNotNull(parser);
        Object result = parser.parse("input");
        assertEquals("input", result);
    }

    @Test
    public void testCreateProcessingParserWithEmptyProcessors() {
        Parser baseParser = input -> "base_" + input;
        List<Map<String, Object>> processorConfigs = Collections.emptyList();

        Parser parser = ToolParser.createProcessingParser(baseParser, processorConfigs);

        assertNotNull(parser);
        Object result = parser.parse("input");
        assertEquals("base_input", result);
    }

    @Test
    public void testCreateFromToolParamsWithBaseParser() {
        Parser baseParser = input -> "base_" + input;
        Map<String, Object> params = Collections.emptyMap();

        Parser parser = ToolParser.createFromToolParams(params, baseParser);

        assertNotNull(parser);
        Object result = parser.parse("input");
        assertEquals("base_input", result);
    }

    @Test
    public void testCreateFromToolParamsWithoutBaseParser() {
        Map<String, Object> params = Collections.emptyMap();

        Parser parser = ToolParser.createFromToolParams(params);

        assertNotNull(parser);
        Object result = parser.parse("input");
        assertEquals("input", result);
    }

    @Test
    public void testCreateFromToolParamsWithEmptyParams() {
        Map<String, Object> params = Collections.emptyMap();

        Parser parser = ToolParser.createFromToolParams(params);

        assertNotNull(parser);
        Object result = parser.parse("input");
        assertEquals("input", result);
    }

    @Test
    public void testCreateFromToolParamsWithNullParams() {
        Parser parser = ToolParser.createFromToolParams(null);

        assertNotNull(parser);
        Object result = parser.parse("input");
        assertEquals("input", result);
    }

    @Test
    public void testCreateFromToolParamsWithNullParamsAndNullBaseParser() {
        Parser parser = ToolParser.createFromToolParams(null, null);

        assertNotNull(parser);
        Object result = parser.parse("input");
        assertEquals("input", result);
    }

    @Test
    public void testCreateFromToolParamsWithNullParamsAndBaseParser() {
        Parser baseParser = input -> "base_" + input;
        Parser parser = ToolParser.createFromToolParams(null, baseParser);

        assertNotNull(parser);
        Object result = parser.parse("input");
        assertEquals("base_input", result);
    }

    @Test
    public void testCreateFromToolParamsParseNullInput() {
        Parser parser = ToolParser.createFromToolParams(Collections.emptyMap());

        assertNotNull(parser);
        Object result = parser.parse(null);
        assertEquals(null, result);
    }

    @Test
    public void testCreateFromToolParamsWithBaseParserParseNullInput() {
        Parser baseParser = input -> input == null ? "null_handled" : "base_" + input;
        Parser parser = ToolParser.createFromToolParams(Collections.emptyMap(), baseParser);

        assertNotNull(parser);
        Object result = parser.parse(null);
        assertEquals("null_handled", result);
    }
}
