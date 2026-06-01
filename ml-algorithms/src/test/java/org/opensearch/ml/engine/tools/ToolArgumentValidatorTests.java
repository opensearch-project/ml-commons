/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.tools;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Map;

import org.junit.Before;
import org.junit.Test;

import lombok.SneakyThrows;

public class ToolArgumentValidatorTests {

    private ToolArgumentValidator validator;
    private String testSchema;

    @Before
    public void setup() {
        validator = new ToolArgumentValidator();
        testSchema = """
            {"type":"object",\
            "properties":{\
            "index":{"type":"string","description":"OpenSearch index name"},\
            "query":{"type":"object","description":"OpenSearch Query DSL"}},\
            "required":["index","query"],\
            "additionalProperties":false}""";
    }

    // ========== Valid Input Tests ==========

    @Test
    @SneakyThrows
    public void testValidateAndNormalize_withValidJson() {
        String validInput = "{\"index\":\"test-index\",\"query\":{\"match_all\":{}}}";
        
        Map<String, Object> result = validator.validateAndNormalize(validInput, testSchema);
        
        assertNotNull("Result should not be null", result);
        assertEquals("test-index", result.get("index"));
        assertTrue("Query should be a Map", result.get("query") instanceof Map);
    }

    @Test
    @SneakyThrows
    public void testValidateAndNormalize_withComplexQuery() {
        String complexInput = "{\"index\":\"test-index\",\"query\":{\"bool\":{\"must\":[{\"match\":{\"title\":\"test\"}}]}}}";
        
        Map<String, Object> result = validator.validateAndNormalize(complexInput, testSchema);
        
        assertNotNull("Result should not be null", result);
        assertEquals("test-index", result.get("index"));
        Map<String, Object> query = (Map<String, Object>) result.get("query");
        assertTrue("Query should contain bool", query.containsKey("bool"));
    }

    // ========== Stringified JSON Normalization Tests ==========

    @Test
    @SneakyThrows
    public void testValidateAndNormalize_withStringifiedQuery() {
        // Common LLM output pattern: query field is stringified JSON
        String stringifiedInput = "{\"index\":\"test-index\",\"query\":\"{\\\"match_all\\\":{}}\"}";
        
        Map<String, Object> result = validator.validateAndNormalize(stringifiedInput, testSchema);
        
        assertNotNull("Result should not be null", result);
        assertEquals("test-index", result.get("index"));
        assertTrue("Query should be normalized to Map", result.get("query") instanceof Map);
        
        Map<String, Object> query = (Map<String, Object>) result.get("query");
        assertTrue("Query should contain match_all", query.containsKey("match_all"));
    }

    @Test
    @SneakyThrows
    public void testValidateAndNormalize_withComplexStringifiedQuery() {
        String complexStringified = "{\"index\":\"test-index\",\"query\":\"{\\\"bool\\\":{\\\"must\\\":[{\\\"match\\\":{\\\"title\\\":\\\"test\\\"}}]}}\"}";
        
        Map<String, Object> result = validator.validateAndNormalize(complexStringified, testSchema);
        
        assertNotNull("Result should not be null", result);
        Map<String, Object> query = (Map<String, Object>) result.get("query");
        assertTrue("Query should contain bool", query.containsKey("bool"));
        
        Map<String, Object> bool = (Map<String, Object>) query.get("bool");
        assertTrue("Bool should contain must", bool.containsKey("must"));
    }

    @Test
    @SneakyThrows
    public void testValidateAndNormalize_withNestedStringifiedJson() {
        // Multiple levels of stringification
        String nestedStringified = "{\"index\":\"test-index\",\"query\":\"{\\\"query\\\":{\\\"match_all\\\":{}},\\\"size\\\":10}\"}";
        
        Map<String, Object> result = validator.validateAndNormalize(nestedStringified, testSchema);
        
        assertNotNull("Result should not be null", result);
        Map<String, Object> query = (Map<String, Object>) result.get("query");
        assertTrue("Query should contain query field", query.containsKey("query"));
        assertTrue("Query should contain size field", query.containsKey("size"));
        assertEquals(10, ((Number) query.get("size")).intValue());
    }

    // ========== Conservative Normalization Tests ==========

    @Test
    @SneakyThrows
    public void testValidateAndNormalize_withWrappedJson() {
        // Entire JSON wrapped in quotes (common LLM mistake)
        String wrappedInput = "\"{\\\"index\\\":\\\"test-index\\\",\\\"query\\\":{\\\"match_all\\\":{}}}\"";
        
        Map<String, Object> result = validator.validateAndNormalize(wrappedInput, testSchema);
        
        assertNotNull("Result should not be null", result);
        assertEquals("test-index", result.get("index"));
        assertTrue("Query should be a Map", result.get("query") instanceof Map);
    }

    @Test
    @SneakyThrows
    public void testValidateAndNormalize_withEscapedQuotes() {
        // This input is actually invalid JSON - the backslashes aren't properly escaped
        String escapedInput = "{\\\"index\\\":\\\"test-index\\\",\\\"query\\\":{\\\"match_all\\\":{}}}";
        
        try {
            validator.validateAndNormalize(escapedInput, testSchema);
            fail("Should throw IllegalArgumentException for invalid JSON with unescaped backslashes");
        } catch (IllegalArgumentException e) {
            assertTrue("Should mention malformed JSON", e.getMessage().contains("malformed"));
        }
    }

    @Test
    @SneakyThrows
    public void testValidateAndNormalize_preservesNonJsonStrings() {
        // String values that aren't JSON should remain as strings
        String inputWithString = "{\"index\":\"test-index\",\"description\":\"This is just a string\",\"query\":{\"match_all\":{}}}";
        
        Map<String, Object> result = validator.validateAndNormalize(inputWithString, testSchema);
        
        assertNotNull("Result should not be null", result);
        assertEquals("test-index", result.get("index"));
        assertEquals("This is just a string", result.get("description"));
        assertTrue("Query should be a Map", result.get("query") instanceof Map);
    }

    // ========== Error Handling Tests ==========

    @Test
    @SneakyThrows
    public void testValidateAndNormalize_withNullInput() {
        try {
            validator.validateAndNormalize(null, testSchema);
            fail("Should throw IllegalArgumentException for null input");
        } catch (IllegalArgumentException e) {
            assertTrue("Should mention null input", e.getMessage().contains("null or empty"));
        }
    }

    @Test
    @SneakyThrows
    public void testValidateAndNormalize_withEmptyInput() {
        try {
            validator.validateAndNormalize("", testSchema);
            fail("Should throw IllegalArgumentException for empty input");
        } catch (IllegalArgumentException e) {
            assertTrue("Should mention null or empty", e.getMessage().contains("null or empty"));
        }
    }

    @Test
    @SneakyThrows
    public void testValidateAndNormalize_withCompletelyInvalidJson() {
        String invalidInput = "this is not json at all {{{";
        
        try {
            validator.validateAndNormalize(invalidInput, testSchema);
            fail("Should throw IllegalArgumentException for invalid JSON");
        } catch (IllegalArgumentException e) {
            assertTrue("Should mention malformed JSON", e.getMessage().contains("malformed"));
        }
    }

    @Test
    @SneakyThrows
    public void testValidateAndNormalize_withPartiallyValidJson() {
        // JSON that can't be safely normalized - this is actually valid JSON with a string value
        String partiallyValid = "{\"index\":\"test-index\",\"query\":\"not-json-but-looks-like{it\"}";
        
        // This should actually succeed because it's valid JSON, just with a string query value
        Map<String, Object> result = validator.validateAndNormalize(partiallyValid, testSchema);
        
        assertNotNull("Result should not be null", result);
        assertEquals("test-index", result.get("index"));
        assertEquals("not-json-but-looks-like{it", result.get("query"));
    }

    // ========== Edge Cases ==========

    @Test
    @SneakyThrows
    public void testValidateAndNormalize_withNumberValues() {
        String inputWithNumbers = "{\"index\":\"test-index\",\"query\":{\"range\":{\"age\":{\"gte\":18}}},\"size\":10}";
        
        Map<String, Object> result = validator.validateAndNormalize(inputWithNumbers, testSchema);
        
        assertNotNull("Result should not be null", result);
        assertEquals("test-index", result.get("index"));
        assertEquals(10, ((Number) result.get("size")).intValue());
        
        Map<String, Object> query = (Map<String, Object>) result.get("query");
        Map<String, Object> range = (Map<String, Object>) query.get("range");
        Map<String, Object> age = (Map<String, Object>) range.get("age");
        assertEquals(18, ((Number) age.get("gte")).intValue());
    }

    @Test
    @SneakyThrows
    public void testValidateAndNormalize_withBooleanValues() {
        String inputWithBooleans = "{\"index\":\"test-index\",\"query\":{\"match_all\":{}},\"explain\":true}";
        
        Map<String, Object> result = validator.validateAndNormalize(inputWithBooleans, testSchema);
        
        assertNotNull("Result should not be null", result);
        assertEquals("test-index", result.get("index"));
        assertEquals(true, result.get("explain"));
    }

    @Test
    @SneakyThrows
    public void testValidateAndNormalize_withArrayValues() {
        String inputWithArrays = "{\"index\":\"test-index\",\"query\":{\"terms\":{\"status\":[\"published\",\"draft\"]}}}";
        
        Map<String, Object> result = validator.validateAndNormalize(inputWithArrays, testSchema);
        
        assertNotNull("Result should not be null", result);
        assertEquals("test-index", result.get("index"));
        
        Map<String, Object> query = (Map<String, Object>) result.get("query");
        Map<String, Object> terms = (Map<String, Object>) query.get("terms");
        assertTrue("Status should be an array", terms.get("status") instanceof java.util.List);
    }

    // ========== Real-World LLM Patterns ==========

    @Test
    @SneakyThrows
    public void testValidateAndNormalize_realWorldOpenAIPattern() {
        // Actual pattern from OpenAI function calling that was failing
        String openAIPattern = "{\"index\":\"opensearch-release\",\"query\":\"{\\\"query\\\":{\\\"match_all\\\":{}}}\"}";
        
        Map<String, Object> result = validator.validateAndNormalize(openAIPattern, testSchema);
        
        assertNotNull("Result should not be null", result);
        assertEquals("opensearch-release", result.get("index"));
        
        Map<String, Object> query = (Map<String, Object>) result.get("query");
        assertTrue("Query should contain query field", query.containsKey("query"));
        
        Map<String, Object> innerQuery = (Map<String, Object>) query.get("query");
        assertTrue("Inner query should contain match_all", innerQuery.containsKey("match_all"));
    }

    @Test
    @SneakyThrows
    public void testValidateAndNormalize_realWorldBedrockPattern() {
        // Pattern commonly seen from Bedrock models
        String bedrockPattern = "{\"index\":\"logs-2024\",\"query\":\"{\\\"bool\\\":{\\\"filter\\\":[{\\\"range\\\":{\\\"@timestamp\\\":{\\\"gte\\\":\\\"2024-01-01\\\"}}}]}}\"}";
        
        Map<String, Object> result = validator.validateAndNormalize(bedrockPattern, testSchema);
        
        assertNotNull("Result should not be null", result);
        assertEquals("logs-2024", result.get("index"));
        
        Map<String, Object> query = (Map<String, Object>) result.get("query");
        assertTrue("Query should contain bool", query.containsKey("bool"));
        
        Map<String, Object> bool = (Map<String, Object>) query.get("bool");
        assertTrue("Bool should contain filter", bool.containsKey("filter"));
    }

    // ========== Performance and Safety Tests ==========

    @Test
    @SneakyThrows
    public void testValidateAndNormalize_doesNotModifyValidInput() {
        String validInput = "{\"index\":\"test-index\",\"query\":{\"match_all\":{}}}";
        
        Map<String, Object> result = validator.validateAndNormalize(validInput, testSchema);
        
        // Should parse successfully without any normalization
        assertNotNull("Result should not be null", result);
        assertEquals("test-index", result.get("index"));
        assertTrue("Query should be a Map", result.get("query") instanceof Map);
    }

    @Test
    @SneakyThrows
    public void testValidateAndNormalize_failsOnDangerousInput() {
        // Input that is completely malformed JSON (missing closing brace)
        String dangerousInput = "{\"index\":\"test-index\",\"query\":\"value\"";
        
        try {
            validator.validateAndNormalize(dangerousInput, testSchema);
            fail("Should fail on malformed JSON input");
        } catch (IllegalArgumentException e) {
            assertTrue("Should mention malformed JSON",
                e.getMessage().contains("malformed") || e.getMessage().contains("safely normalized"));
        }
    }

    @Test
    @SneakyThrows
    public void testValidateAndNormalize_handlesLargeInput() {
        // Test with reasonably large input to ensure performance
        StringBuilder largeQuery = new StringBuilder("{\"bool\":{\"must\":[");
        for (int i = 0; i < 100; i++) {
            if (i > 0) largeQuery.append(",");
            largeQuery.append("{\"match\":{\"field").append(i).append("\":\"value").append(i).append("\"}}");
        }
        largeQuery.append("]}}");
        
        String largeInput = "{\"index\":\"test-index\",\"query\":\"" + 
            largeQuery.toString().replace("\"", "\\\"") + "\"}";
        
        Map<String, Object> result = validator.validateAndNormalize(largeInput, testSchema);
        
        assertNotNull("Result should not be null", result);
        assertEquals("test-index", result.get("index"));
        assertTrue("Query should be normalized to Map", result.get("query") instanceof Map);
    }

    // ========== ValidationResult Tests ==========

    @Test
    public void testValidationResult_success() {
        Map<String, Object> testData = Map.of("key", "value");
        ToolArgumentValidator.ValidationResult result = ToolArgumentValidator.ValidationResult.success(testData);
        
        assertTrue("Should be successful", result.isSuccess());
        assertEquals(testData, result.getNormalizedInput());
        assertEquals(null, result.getErrorMessage());
    }

    @Test
    public void testValidationResult_failure() {
        String errorMsg = "Test error message";
        ToolArgumentValidator.ValidationResult result = ToolArgumentValidator.ValidationResult.failure(errorMsg);
        
        assertFalse("Should not be successful", result.isSuccess());
        assertEquals(null, result.getNormalizedInput());
        assertEquals(errorMsg, result.getErrorMessage());
    }
}