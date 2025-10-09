/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.opensearch.OpenSearchParseException;

/**
 * Unit tests for LlmResultPathGenerator.
 */
public class LlmResultPathGeneratorTest {

    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    /**
     * Test schema with explicit x-llm-output marker.
     */
    @Test
    public void testGenerate_WithExplicitMarker() throws IOException {
        String schema = "{\n"
            + "  \"properties\": {\n"
            + "    \"inference_results\": {\n"
            + "      \"items\": {\n"
            + "        \"properties\": {\n"
            + "          \"output\": {\n"
            + "            \"items\": {\n"
            + "              \"properties\": {\n"
            + "                \"dataAsMap\": {\n"
            + "                  \"type\": \"object\",\n"
            + "                  \"properties\": {\n"
            + "                    \"choices\": {\n"
            + "                      \"type\": \"array\",\n"
            + "                      \"items\": {\n"
            + "                        \"type\": \"object\",\n"
            + "                        \"properties\": {\n"
            + "                          \"message\": {\n"
            + "                            \"type\": \"object\",\n"
            + "                            \"properties\": {\n"
            + "                              \"content\": {\n"
            + "                                \"type\": \"string\",\n"
            + "                                \"x-llm-output\": true\n"
            + "                              }\n"
            + "                            }\n"
            + "                          }\n"
            + "                        }\n"
            + "                      }\n"
            + "                    }\n"
            + "                  }\n"
            + "                }\n"
            + "              }\n"
            + "            }\n"
            + "          }\n"
            + "        }\n"
            + "      }\n"
            + "    }\n"
            + "  }\n"
            + "}";

        String result = LlmResultPathGenerator.generate(schema);

        assertNotNull(result);
        assertEquals("$.choices[0].message.content", result);
    }

    /**
     * Test schema without x-llm-output marker returns null.
     */
    @Test
    public void testGenerate_WithHeuristicMatching_Content() throws IOException {
        String schema = "{\n"
            + "  \"properties\": {\n"
            + "    \"inference_results\": {\n"
            + "      \"items\": {\n"
            + "        \"properties\": {\n"
            + "          \"output\": {\n"
            + "            \"items\": {\n"
            + "              \"properties\": {\n"
            + "                \"dataAsMap\": {\n"
            + "                  \"type\": \"object\",\n"
            + "                  \"properties\": {\n"
            + "                    \"content\": {\n"
            + "                      \"type\": \"array\",\n"
            + "                      \"items\": {\n"
            + "                        \"type\": \"object\",\n"
            + "                        \"properties\": {\n"
            + "                          \"text\": {\n"
            + "                            \"type\": \"string\"\n"
            + "                          }\n"
            + "                        }\n"
            + "                      }\n"
            + "                    }\n"
            + "                  }\n"
            + "                }\n"
            + "              }\n"
            + "            }\n"
            + "          }\n"
            + "        }\n"
            + "      }\n"
            + "    }\n"
            + "  }\n"
            + "}";

        String result = LlmResultPathGenerator.generate(schema);

        // Without x-llm-output marker, should return null
        assertNull(result);
    }

    /**
     * Test Bedrock Claude v3 style response structure.
     */
    @Test
    public void testGenerate_BedrockClaudeV3Style() throws IOException {
        String schema = "{\n"
            + "  \"properties\": {\n"
            + "    \"inference_results\": {\n"
            + "      \"items\": {\n"
            + "        \"properties\": {\n"
            + "          \"output\": {\n"
            + "            \"items\": {\n"
            + "              \"properties\": {\n"
            + "                \"dataAsMap\": {\n"
            + "                  \"type\": \"object\",\n"
            + "                  \"properties\": {\n"
            + "                    \"content\": {\n"
            + "                      \"type\": \"array\",\n"
            + "                      \"items\": {\n"
            + "                        \"type\": \"object\",\n"
            + "                        \"properties\": {\n"
            + "                          \"text\": {\n"
            + "                            \"type\": \"string\",\n"
            + "                            \"x-llm-output\": true\n"
            + "                          }\n"
            + "                        }\n"
            + "                      }\n"
            + "                    }\n"
            + "                  }\n"
            + "                }\n"
            + "              }\n"
            + "            }\n"
            + "          }\n"
            + "        }\n"
            + "      }\n"
            + "    }\n"
            + "  }\n"
            + "}";

        String result = LlmResultPathGenerator.generate(schema);

        assertNotNull(result);
        assertEquals("$.content[0].text", result);
    }

    /**
     * Test Bedrock Claude v2 style (simple completion field).
     */
    @Test
    public void testGenerate_BedrockClaudeV2Style() throws IOException {
        String schema = "{\n"
            + "  \"properties\": {\n"
            + "    \"inference_results\": {\n"
            + "      \"items\": {\n"
            + "        \"properties\": {\n"
            + "          \"output\": {\n"
            + "            \"items\": {\n"
            + "              \"properties\": {\n"
            + "                \"dataAsMap\": {\n"
            + "                  \"type\": \"object\",\n"
            + "                  \"properties\": {\n"
            + "                    \"completion\": {\n"
            + "                      \"type\": \"string\",\n"
            + "                      \"x-llm-output\": true\n"
            + "                    }\n"
            + "                  }\n"
            + "                }\n"
            + "              }\n"
            + "            }\n"
            + "          }\n"
            + "        }\n"
            + "      }\n"
            + "    }\n"
            + "  }\n"
            + "}";

        String result = LlmResultPathGenerator.generate(schema);

        assertNotNull(result);
        assertEquals("$.completion", result);
    }

    /**
     * Test with null schema input.
     */
    @Test
    public void testGenerate_NullSchema() throws IOException {
        String result = LlmResultPathGenerator.generate(null);
        assertNull(result);
    }

    /**
     * Test with empty schema input.
     */
    @Test
    public void testGenerate_EmptySchema() throws IOException {
        String result = LlmResultPathGenerator.generate("");
        assertNull(result);
    }

    /**
     * Test with malformed JSON schema.
     */
    @Test
    public void testGenerate_MalformedJson() {
        exceptionRule.expect(OpenSearchParseException.class);
        exceptionRule.expectMessage("Schema parsing error");

        try {
            LlmResultPathGenerator.generate("{invalid json}");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Test schema without dataAsMap structure (should attempt fallback).
     */
    @Test
    public void testGenerate_NoDataAsMapStructure() throws IOException {
        String schema = "{\n"
            + "  \"type\": \"object\",\n"
            + "  \"properties\": {\n"
            + "    \"response\": {\n"
            + "      \"type\": \"string\",\n"
            + "      \"x-llm-output\": true\n"
            + "    }\n"
            + "  }\n"
            + "}";

        String result = LlmResultPathGenerator.generate(schema);

        assertNotNull(result);
        assertEquals("$.response", result);
    }

    /**
     * Test schema with no LLM text field markers or common names.
     */
    @Test
    public void testGenerate_NoLlmTextField() throws IOException {
        String schema = "{\n"
            + "  \"properties\": {\n"
            + "    \"inference_results\": {\n"
            + "      \"items\": {\n"
            + "        \"properties\": {\n"
            + "          \"output\": {\n"
            + "            \"items\": {\n"
            + "              \"properties\": {\n"
            + "                \"dataAsMap\": {\n"
            + "                  \"type\": \"object\",\n"
            + "                  \"properties\": {\n"
            + "                    \"unknownField\": {\n"
            + "                      \"type\": \"number\"\n"
            + "                    }\n"
            + "                  }\n"
            + "                }\n"
            + "              }\n"
            + "            }\n"
            + "          }\n"
            + "        }\n"
            + "      }\n"
            + "    }\n"
            + "  }\n"
            + "}";

        String result = LlmResultPathGenerator.generate(schema);

        assertNull(result);
    }

    /**
     * Test deeply nested structure with marker.
     */
    @Test
    public void testGenerate_DeeplyNested() throws IOException {
        String schema = "{\n"
            + "  \"properties\": {\n"
            + "    \"inference_results\": {\n"
            + "      \"items\": {\n"
            + "        \"properties\": {\n"
            + "          \"output\": {\n"
            + "            \"items\": {\n"
            + "              \"properties\": {\n"
            + "                \"dataAsMap\": {\n"
            + "                  \"type\": \"object\",\n"
            + "                  \"properties\": {\n"
            + "                    \"data\": {\n"
            + "                      \"type\": \"object\",\n"
            + "                      \"properties\": {\n"
            + "                        \"results\": {\n"
            + "                          \"type\": \"array\",\n"
            + "                          \"items\": {\n"
            + "                            \"type\": \"object\",\n"
            + "                            \"properties\": {\n"
            + "                              \"output\": {\n"
            + "                                \"type\": \"object\",\n"
            + "                                \"properties\": {\n"
            + "                                  \"text\": {\n"
            + "                                    \"type\": \"string\",\n"
            + "                                    \"x-llm-output\": true\n"
            + "                                  }\n"
            + "                                }\n"
            + "                              }\n"
            + "                            }\n"
            + "                          }\n"
            + "                        }\n"
            + "                      }\n"
            + "                    }\n"
            + "                  }\n"
            + "                }\n"
            + "              }\n"
            + "            }\n"
            + "          }\n"
            + "        }\n"
            + "      }\n"
            + "    }\n"
            + "  }\n"
            + "}";

        String result = LlmResultPathGenerator.generate(schema);

        assertNotNull(result);
        assertEquals("$.data.results[0].output.text", result);
    }

    /**
     * Test isValidJsonPath with valid paths.
     */
    @Test
    public void testIsValidJsonPath_ValidPaths() {
        assertTrue(LlmResultPathGenerator.isValidJsonPath("$.content"));
        assertTrue(LlmResultPathGenerator.isValidJsonPath("$.choices[0].message.content"));
        assertTrue(LlmResultPathGenerator.isValidJsonPath("$.data.results[0].output.text"));
        assertTrue(LlmResultPathGenerator.isValidJsonPath("$"));
    }

    /**
     * Test isValidJsonPath with invalid paths.
     */
    @Test
    public void testIsValidJsonPath_InvalidPaths() {
        assertFalse(LlmResultPathGenerator.isValidJsonPath(null));
        assertFalse(LlmResultPathGenerator.isValidJsonPath(""));
        assertFalse(LlmResultPathGenerator.isValidJsonPath("content")); // Missing $
        assertFalse(LlmResultPathGenerator.isValidJsonPath("$.choices[0.message.content")); // Unbalanced brackets
        assertFalse(LlmResultPathGenerator.isValidJsonPath("$.choices]0[.message")); // Unbalanced brackets
    }

    /**
     * Test schema with multiple string fields, marker takes precedence.
     */
    @Test
    public void testGenerate_MarkerTakesPrecedence() throws IOException {
        String schema = "{\n"
            + "  \"properties\": {\n"
            + "    \"inference_results\": {\n"
            + "      \"items\": {\n"
            + "        \"properties\": {\n"
            + "          \"output\": {\n"
            + "            \"items\": {\n"
            + "              \"properties\": {\n"
            + "                \"dataAsMap\": {\n"
            + "                  \"type\": \"object\",\n"
            + "                  \"properties\": {\n"
            + "                    \"text\": {\n"
            + "                      \"type\": \"string\"\n"
            + "                    },\n"
            + "                    \"content\": {\n"
            + "                      \"type\": \"string\"\n"
            + "                    },\n"
            + "                    \"actualOutput\": {\n"
            + "                      \"type\": \"string\",\n"
            + "                      \"x-llm-output\": true\n"
            + "                    }\n"
            + "                  }\n"
            + "                }\n"
            + "              }\n"
            + "            }\n"
            + "          }\n"
            + "        }\n"
            + "      }\n"
            + "    }\n"
            + "  }\n"
            + "}";

        String result = LlmResultPathGenerator.generate(schema);

        assertNotNull(result);
        // Should return actualOutput since it has explicit marker
        assertEquals("$.actualOutput", result);
    }

    /**
     * Test schema with array at root level of dataAsMap.
     */
    @Test
    public void testGenerate_ArrayAtRootLevel() throws IOException {
        String schema = "{\n"
            + "  \"properties\": {\n"
            + "    \"inference_results\": {\n"
            + "      \"items\": {\n"
            + "        \"properties\": {\n"
            + "          \"output\": {\n"
            + "            \"items\": {\n"
            + "              \"properties\": {\n"
            + "                \"dataAsMap\": {\n"
            + "                  \"type\": \"array\",\n"
            + "                  \"items\": {\n"
            + "                    \"type\": \"object\",\n"
            + "                    \"properties\": {\n"
            + "                      \"message\": {\n"
            + "                        \"type\": \"string\",\n"
            + "                        \"x-llm-output\": true\n"
            + "                      }\n"
            + "                    }\n"
            + "                  }\n"
            + "                }\n"
            + "              }\n"
            + "            }\n"
            + "          }\n"
            + "        }\n"
            + "      }\n"
            + "    }\n"
            + "  }\n"
            + "}";

        String result = LlmResultPathGenerator.generate(schema);

        assertNotNull(result);
        assertEquals("$[0].message", result);
    }

    /**
     * Test schema without x-llm-output marker returns null.
     */
    @Test
    public void testGenerate_HeuristicResponseField() throws IOException {
        String schema = "{\n"
            + "  \"properties\": {\n"
            + "    \"inference_results\": {\n"
            + "      \"items\": {\n"
            + "        \"properties\": {\n"
            + "          \"output\": {\n"
            + "            \"items\": {\n"
            + "              \"properties\": {\n"
            + "                \"dataAsMap\": {\n"
            + "                  \"type\": \"object\",\n"
            + "                  \"properties\": {\n"
            + "                    \"response\": {\n"
            + "                      \"type\": \"string\"\n"
            + "                    },\n"
            + "                    \"otherField\": {\n"
            + "                      \"type\": \"number\"\n"
            + "                    }\n"
            + "                  }\n"
            + "                }\n"
            + "              }\n"
            + "            }\n"
            + "          }\n"
            + "        }\n"
            + "      }\n"
            + "    }\n"
            + "  }\n"
            + "}";

        String result = LlmResultPathGenerator.generate(schema);

        // Without x-llm-output marker, should return null
        assertNull(result);
    }

    /**
     * Test schema with x-llm-output set to false (should be ignored and return null).
     */
    @Test
    public void testGenerate_MarkerSetToFalse() throws IOException {
        String schema = "{\n"
            + "  \"properties\": {\n"
            + "    \"inference_results\": {\n"
            + "      \"items\": {\n"
            + "        \"properties\": {\n"
            + "          \"output\": {\n"
            + "            \"items\": {\n"
            + "              \"properties\": {\n"
            + "                \"dataAsMap\": {\n"
            + "                  \"type\": \"object\",\n"
            + "                  \"properties\": {\n"
            + "                    \"wrongField\": {\n"
            + "                      \"type\": \"string\",\n"
            + "                      \"x-llm-output\": false\n"
            + "                    },\n"
            + "                    \"content\": {\n"
            + "                      \"type\": \"string\"\n"
            + "                    }\n"
            + "                  }\n"
            + "                }\n"
            + "              }\n"
            + "            }\n"
            + "          }\n"
            + "        }\n"
            + "      }\n"
            + "    }\n"
            + "  }\n"
            + "}";

        String result = LlmResultPathGenerator.generate(schema);

        // Without x-llm-output marker set to true, should return null
        assertNull(result);
    }

    /**
     * Test minimal valid schema structure without marker returns null.
     */
    @Test
    public void testGenerate_MinimalValidSchema() throws IOException {
        String schema = "{\n"
            + "  \"type\": \"object\",\n"
            + "  \"properties\": {\n"
            + "    \"text\": {\n"
            + "      \"type\": \"string\"\n"
            + "    }\n"
            + "  }\n"
            + "}";

        String result = LlmResultPathGenerator.generate(schema);

        // Without x-llm-output marker, should return null
        assertNull(result);
    }
}
