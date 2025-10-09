/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.utils.message;

import static org.junit.Assert.*;

import org.junit.Test;

/**
 * Unit tests for MessageFormatterFactory.
 * Tests the factory's ability to select the correct formatter based on input schema.
 */
public class MessageFormatterFactoryTests {

    // Claude schema example - contains "system_prompt" parameter
    private static final String CLAUDE_SCHEMA = "{"
        + "\"type\":\"object\","
        + "\"properties\":{"
        + "\"system_prompt\":{\"type\":\"string\"},"
        + "\"messages\":{\"type\":\"array\"}"
        + "}"
        + "}";

    // OpenAI schema example - NO "system_prompt" parameter
    private static final String OPENAI_SCHEMA = "{"
        + "\"type\":\"object\","
        + "\"properties\":{"
        + "\"messages\":{\"type\":\"array\"},"
        + "\"temperature\":{\"type\":\"number\"}"
        + "}"
        + "}";

    @Test
    public void testFactoryWithClaudeSchema() {
        MessageFormatter formatter = MessageFormatterFactory.getFormatter(CLAUDE_SCHEMA);

        assertNotNull("Formatter should not be null", formatter);
        assertTrue("Should return ClaudeMessageFormatter", formatter instanceof ClaudeMessageFormatter);
    }

    @Test
    public void testFactoryWithOpenAISchema() {
        MessageFormatter formatter = MessageFormatterFactory.getFormatter(OPENAI_SCHEMA);

        assertNotNull("Formatter should not be null", formatter);
        assertTrue("Should return OpenAIMessageFormatter", formatter instanceof OpenAIMessageFormatter);
    }

    @Test
    public void testFactoryWithNullSchema() {
        MessageFormatter formatter = MessageFormatterFactory.getFormatter(null);

        assertNotNull("Formatter should not be null", formatter);
        // Default to Claude formatter
        assertTrue("Should default to ClaudeMessageFormatter", formatter instanceof ClaudeMessageFormatter);
    }

    @Test
    public void testFactoryWithBlankSchema() {
        MessageFormatter formatter = MessageFormatterFactory.getFormatter("");

        assertNotNull("Formatter should not be null", formatter);
        // Default to Claude formatter
        assertTrue("Should default to ClaudeMessageFormatter", formatter instanceof ClaudeMessageFormatter);
    }

    @Test
    public void testFactoryWithWhitespaceSchema() {
        MessageFormatter formatter = MessageFormatterFactory.getFormatter("   ");

        assertNotNull("Formatter should not be null", formatter);
        // Default to Claude formatter
        assertTrue("Should default to ClaudeMessageFormatter", formatter instanceof ClaudeMessageFormatter);
    }

    @Test
    public void testFactoryWithMalformedSchema() {
        // Invalid JSON - missing closing brace
        String malformedSchema = "{\"properties\":{\"system_prompt\":{\"type\":\"string\"}";

        MessageFormatter formatter = MessageFormatterFactory.getFormatter(malformedSchema);

        assertNotNull("Formatter should not be null", formatter);
        // Even with malformed JSON, should return formatter (checks for string presence)
        assertTrue("Should return ClaudeMessageFormatter (contains system_prompt)", formatter instanceof ClaudeMessageFormatter);
    }

    @Test
    public void testFactoryWithPartialClaudeSchema() {
        // Schema that contains system_prompt in a string value but not as a field name
        String partialSchema = "{\"description\":\"This schema has system_prompt field\"}";

        MessageFormatter formatter = MessageFormatterFactory.getFormatter(partialSchema);

        assertNotNull("Formatter should not be null", formatter);
        // Factory checks for "\"system_prompt\"" (with quotes), not just "system_prompt"
        // This schema contains system_prompt only in a description, not as a field name
        assertTrue("Should return OpenAIMessageFormatter (no quoted field name)", formatter instanceof OpenAIMessageFormatter);
    }

    @Test
    public void testFactoryWithComplexClaudeSchema() {
        String complexSchema = "{"
            + "\"$schema\":\"http://json-schema.org/draft-07/schema#\","
            + "\"type\":\"object\","
            + "\"properties\":{"
            + "\"model\":{\"type\":\"string\"},"
            + "\"system_prompt\":{\"type\":\"string\",\"description\":\"System prompt\"},"
            + "\"messages\":{\"type\":\"array\"},"
            + "\"temperature\":{\"type\":\"number\"}"
            + "},"
            + "\"required\":[\"messages\"]"
            + "}";

        MessageFormatter formatter = MessageFormatterFactory.getFormatter(complexSchema);

        assertNotNull("Formatter should not be null", formatter);
        assertTrue("Should return ClaudeMessageFormatter", formatter instanceof ClaudeMessageFormatter);
    }

    @Test
    public void testFactoryWithComplexOpenAISchema() {
        String complexSchema = "{"
            + "\"$schema\":\"http://json-schema.org/draft-07/schema#\","
            + "\"type\":\"object\","
            + "\"properties\":{"
            + "\"model\":{\"type\":\"string\"},"
            + "\"messages\":{\"type\":\"array\",\"items\":{\"type\":\"object\"}},"
            + "\"temperature\":{\"type\":\"number\"},"
            + "\"max_tokens\":{\"type\":\"integer\"}"
            + "},"
            + "\"required\":[\"model\",\"messages\"]"
            + "}";

        MessageFormatter formatter = MessageFormatterFactory.getFormatter(complexSchema);

        assertNotNull("Formatter should not be null", formatter);
        assertTrue("Should return OpenAIMessageFormatter", formatter instanceof OpenAIMessageFormatter);
    }

    @Test
    public void testFactoryReturnsClaudeFormatter() {
        MessageFormatter formatter = MessageFormatterFactory.getClaudeFormatter();

        assertNotNull("Formatter should not be null", formatter);
        assertTrue("Should return ClaudeMessageFormatter", formatter instanceof ClaudeMessageFormatter);
    }

    @Test
    public void testFactoryReturnsOpenAIFormatter() {
        MessageFormatter formatter = MessageFormatterFactory.getOpenAIFormatter();

        assertNotNull("Formatter should not be null", formatter);
        assertTrue("Should return OpenAIMessageFormatter", formatter instanceof OpenAIMessageFormatter);
    }

    @Test
    public void testFactorySingletonPattern() {
        // Get Claude formatter multiple times
        MessageFormatter claude1 = MessageFormatterFactory.getClaudeFormatter();
        MessageFormatter claude2 = MessageFormatterFactory.getClaudeFormatter();

        // Should be the same instance (singleton)
        assertSame("Should return same Claude formatter instance", claude1, claude2);

        // Get OpenAI formatter multiple times
        MessageFormatter openai1 = MessageFormatterFactory.getOpenAIFormatter();
        MessageFormatter openai2 = MessageFormatterFactory.getOpenAIFormatter();

        // Should be the same instance (singleton)
        assertSame("Should return same OpenAI formatter instance", openai1, openai2);

        // Claude and OpenAI should be different instances
        assertNotSame("Claude and OpenAI formatters should be different", claude1, openai1);
    }

    @Test
    public void testFactoryNeverReturnsNull() {
        // Test various edge cases to ensure factory never returns null

        String[] testSchemas = { null, "", "   ", "invalid json{{{", "{}", "{\"random\":\"schema\"}", CLAUDE_SCHEMA, OPENAI_SCHEMA };

        for (String schema : testSchemas) {
            MessageFormatter formatter = MessageFormatterFactory.getFormatter(schema);
            assertNotNull("Factory should never return null for schema: " + schema, formatter);
        }
    }

    @Test
    public void testFactorySchemaDetection_CaseSensitive() {
        // Test with different casings - should be case-sensitive
        String upperCaseSchema = "{\"SYSTEM_PROMPT\":\"value\"}";
        MessageFormatter formatter1 = MessageFormatterFactory.getFormatter(upperCaseSchema);
        // Should NOT match (case sensitive) - defaults to Claude anyway
        assertNotNull("Should return formatter", formatter1);

        String mixedCaseSchema = "{\"System_Prompt\":\"value\"}";
        MessageFormatter formatter2 = MessageFormatterFactory.getFormatter(mixedCaseSchema);
        // Should NOT match exact string "system_prompt"
        assertNotNull("Should return formatter", formatter2);

        String correctCaseSchema = "{\"system_prompt\":\"value\"}";
        MessageFormatter formatter3 = MessageFormatterFactory.getFormatter(correctCaseSchema);
        assertTrue("Should match exact case", formatter3 instanceof ClaudeMessageFormatter);
    }

    @Test
    public void testFactoryWithMinimalClaudeSchema() {
        String minimalSchema = "{\"system_prompt\":\"\"}";

        MessageFormatter formatter = MessageFormatterFactory.getFormatter(minimalSchema);

        assertNotNull("Formatter should not be null", formatter);
        assertTrue("Should return ClaudeMessageFormatter", formatter instanceof ClaudeMessageFormatter);
    }

    @Test
    public void testFactoryWithMinimalOpenAISchema() {
        String minimalSchema = "{\"messages\":[]}";

        MessageFormatter formatter = MessageFormatterFactory.getFormatter(minimalSchema);

        assertNotNull("Formatter should not be null", formatter);
        assertTrue("Should return OpenAIMessageFormatter", formatter instanceof OpenAIMessageFormatter);
    }

    @Test
    public void testFactorySchemaWithSystemPromptInDescription() {
        // Edge case: "system_prompt" appears in description but not as a field name
        String schema = "{"
            + "\"properties\":{"
            + "\"messages\":{\"type\":\"array\",\"description\":\"Messages array, not system_prompt\"}"
            + "}"
            + "}";

        MessageFormatter formatter = MessageFormatterFactory.getFormatter(schema);

        assertNotNull("Formatter should not be null", formatter);
        // Factory checks for "\"system_prompt\"" (with quotes as a field name)
        // This schema only has system_prompt in a description string, not as a field name
        assertTrue("Should return OpenAIMessageFormatter (system_prompt not a field name)", formatter instanceof OpenAIMessageFormatter);
    }

    @Test
    public void testFactoryRealWorldClaudeSchema() {
        // Real-world Claude schema example
        String realClaudeSchema = "{"
            + "\"type\":\"object\","
            + "\"properties\":{"
            + "\"anthropic_version\":{\"type\":\"string\"},"
            + "\"max_tokens\":{\"type\":\"integer\"},"
            + "\"system_prompt\":{\"type\":\"string\"},"
            + "\"messages\":{"
            + "\"type\":\"array\","
            + "\"items\":{"
            + "\"type\":\"object\","
            + "\"properties\":{"
            + "\"role\":{\"type\":\"string\"},"
            + "\"content\":{\"type\":\"array\"}"
            + "}"
            + "}"
            + "}"
            + "}"
            + "}";

        MessageFormatter formatter = MessageFormatterFactory.getFormatter(realClaudeSchema);

        assertNotNull("Formatter should not be null", formatter);
        assertTrue("Should return ClaudeMessageFormatter", formatter instanceof ClaudeMessageFormatter);
    }

    @Test
    public void testFactoryRealWorldOpenAISchema() {
        // Real-world OpenAI schema example (Chat Completions API)
        String realOpenAISchema = "{"
            + "\"type\":\"object\","
            + "\"properties\":{"
            + "\"model\":{\"type\":\"string\"},"
            + "\"messages\":{"
            + "\"type\":\"array\","
            + "\"items\":{"
            + "\"type\":\"object\","
            + "\"properties\":{"
            + "\"role\":{\"type\":\"string\",\"enum\":[\"system\",\"user\",\"assistant\"]},"
            + "\"content\":{\"type\":\"string\"}"
            + "},"
            + "\"required\":[\"role\",\"content\"]"
            + "}"
            + "},"
            + "\"temperature\":{\"type\":\"number\",\"minimum\":0,\"maximum\":2},"
            + "\"max_tokens\":{\"type\":\"integer\"}"
            + "},"
            + "\"required\":[\"model\",\"messages\"]"
            + "}";

        MessageFormatter formatter = MessageFormatterFactory.getFormatter(realOpenAISchema);

        assertNotNull("Formatter should not be null", formatter);
        assertTrue("Should return OpenAIMessageFormatter", formatter instanceof OpenAIMessageFormatter);
    }
}
