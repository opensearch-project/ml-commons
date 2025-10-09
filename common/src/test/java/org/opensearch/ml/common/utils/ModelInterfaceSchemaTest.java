/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.utils;

import static org.junit.Assert.*;

import java.util.Map;

import org.junit.Test;
import org.opensearch.ml.common.utils.ModelInterfaceUtils.ModelInterfaceSchema;

/**
 * Unit tests for ModelInterfaceSchema enum.
 * Tests validation, schema loading, and backward compatibility.
 */
public class ModelInterfaceSchemaTest {

    @Test
    public void testFromString_ValidNames() {
        // Test case-insensitive lookup
        ModelInterfaceSchema schema1 = ModelInterfaceSchema.fromString("bedrock_ai21_labs_jurassic2_mid_v1");
        assertEquals(ModelInterfaceSchema.BEDROCK_AI21_LABS_JURASSIC2_MID_V1, schema1);

        ModelInterfaceSchema schema2 = ModelInterfaceSchema.fromString("BEDROCK_AI21_LABS_JURASSIC2_MID_V1");
        assertEquals(ModelInterfaceSchema.BEDROCK_AI21_LABS_JURASSIC2_MID_V1, schema2);

        ModelInterfaceSchema schema3 = ModelInterfaceSchema.fromString("bedrock_anthropic_claude_v2");
        assertEquals(ModelInterfaceSchema.BEDROCK_ANTHROPIC_CLAUDE_V2, schema3);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testFromString_InvalidName() {
        ModelInterfaceSchema.fromString("invalid_schema_name");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testFromString_NullName() {
        ModelInterfaceSchema.fromString(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testFromString_BlankName() {
        ModelInterfaceSchema.fromString("   ");
    }

    @Test
    public void testGetInterface_ReturnsValidMap() {
        Map<String, String> interface1 = ModelInterfaceSchema.BEDROCK_AI21_LABS_JURASSIC2_MID_V1.getInterface();

        assertNotNull("Interface should not be null", interface1);
        assertTrue("Interface should contain 'input' key", interface1.containsKey("input"));
        assertTrue("Interface should contain 'output' key", interface1.containsKey("output"));
        assertFalse("Input schema should not be empty", interface1.get("input").isEmpty());
        assertFalse("Output schema should not be empty", interface1.get("output").isEmpty());
    }

    @Test
    public void testGetInterface_ConsistentResults() {
        // Multiple calls should return equivalent maps (file I/O is cached at loadSchemaFromFile level)
        Map<String, String> interface1 = ModelInterfaceSchema.BEDROCK_ANTHROPIC_CLAUDE_V2.getInterface();
        Map<String, String> interface2 = ModelInterfaceSchema.BEDROCK_ANTHROPIC_CLAUDE_V2.getInterface();

        assertEquals("Results should be equal", interface1, interface2);
        assertEquals("Input schemas should match", interface1.get("input"), interface2.get("input"));
        assertEquals("Output schemas should match", interface1.get("output"), interface2.get("output"));
    }

    @Test
    public void testAllSchemas_CanLoadSuccessfully() {
        // Verify all enum values can load their schemas without errors
        for (ModelInterfaceSchema schema : ModelInterfaceSchema.values()) {
            Map<String, String> schemaInterface = schema.getInterface();
            assertNotNull("Schema should not be null: " + schema.name(), schemaInterface);
            assertEquals("Schema should have exactly 2 keys", 2, schemaInterface.size());
        }
    }

    @Test
    public void testGetName() {
        String name = ModelInterfaceSchema.BEDROCK_TITAN_EMBED_TEXT_V1.getName();
        assertEquals("bedrock_titan_embed_text_v1", name);
    }

    @Test
    public void testEnumValues_AllPresent() {
        ModelInterfaceSchema[] values = ModelInterfaceSchema.values();

        // Verify we have all expected schemas
        assertEquals("Should have 16 schema variants", 16, values.length);

        // Verify specific schemas exist
        assertNotNull(ModelInterfaceSchema.valueOf("BEDROCK_AI21_LABS_JURASSIC2_MID_V1"));
        assertNotNull(ModelInterfaceSchema.valueOf("AMAZON_COMPREHEND_DETECTDOMAINANTLANGUAGE"));
        assertNotNull(ModelInterfaceSchema.valueOf("AMAZON_TEXTRACT_DETECTDOCUMENTTEXT"));
        assertNotNull(ModelInterfaceSchema.valueOf("BEDROCK_ANTHROPIC_CLAUDE_USE_SYSTEM_PROMPT"));
        assertNotNull(ModelInterfaceSchema.valueOf("OPENAI_CHAT_COMPLETIONS"));
    }
}
