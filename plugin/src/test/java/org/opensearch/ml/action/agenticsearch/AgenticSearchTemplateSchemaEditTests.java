/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.agenticsearch;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;

/** The checks {@code updateTemplate} runs before persisting a partial param-schema edit. */
public class AgenticSearchTemplateSchemaEditTests {

    private static Map<String, Object> spec(Object... keyValues) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            m.put((String) keyValues[i], keyValues[i + 1]);
        }
        return m;
    }

    private static Map<String, Object> storedSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("size", spec("type", "number", "required", false, "description", ""));
        schema.put("lex_query", spec("type", "string", "required", true, "description", ""));
        schema.put("sort_by", spec("type", "string", "required", false, "enum", List.of("price", "rating"), "source", "mapping"));
        return schema;
    }

    // ---- mergeParamSchema --------------------------------------------------

    @Test
    public void merge_editsOneKey_keepsDerivedFactsAndOtherParams() {
        Map<String, Object> patch = Map.of("size", spec("description", "How many results to return."));

        Map<String, Object> merged = AgenticSearchTemplateService.mergeParamSchema(storedSchema(), patch);

        // Edited key applied.
        @SuppressWarnings("unchecked")
        Map<String, Object> size = (Map<String, Object>) merged.get("size");
        assertEquals("How many results to return.", size.get("description"));
        // Derived facts for that param survive; the spec is not replaced wholesale.
        assertEquals("number", size.get("type"));
        assertEquals(false, size.get("required"));
        // Unmentioned params untouched.
        assertEquals(3, merged.size());
        assertTrue(merged.containsKey("lex_query"));
        assertTrue(merged.containsKey("sort_by"));
    }

    @Test
    public void merge_canFlipRequired() {
        Map<String, Object> patch = Map.of("size", spec("required", true));

        Map<String, Object> merged = AgenticSearchTemplateService.mergeParamSchema(storedSchema(), patch);

        @SuppressWarnings("unchecked")
        Map<String, Object> size = (Map<String, Object>) merged.get("size");
        assertEquals(true, size.get("required"));
    }

    @Test
    public void merge_unknownParam_rejected() {
        // A param the body has no placeholder for can never be filled.
        Map<String, Object> patch = Map.of("nonexistent", spec("type", "string"));

        IllegalArgumentException e = assertThrows(
            IllegalArgumentException.class,
            () -> AgenticSearchTemplateService.mergeParamSchema(storedSchema(), patch)
        );
        assertTrue(e.getMessage().contains("nonexistent"));
    }

    @Test
    public void merge_nonObjectSpec_rejected() {
        Map<String, Object> patch = Map.of("size", "not-an-object");

        assertThrows(IllegalArgumentException.class, () -> AgenticSearchTemplateService.mergeParamSchema(storedSchema(), patch));
    }

    @Test
    public void merge_nullStored_treatedAsEmptySoAnyParamIsUnknown() {
        Map<String, Object> patch = Map.of("size", spec("description", "x"));

        assertThrows(IllegalArgumentException.class, () -> AgenticSearchTemplateService.mergeParamSchema(null, patch));
    }

    // ---- validateParamSchema ----------------------------------------------

    @Test
    public void validate_derivedSchema_passes() {
        AgenticSearchTemplateService.validateParamSchema(storedSchema());
    }

    @Test
    public void validate_emptyEnum_rejected() {
        // An empty enum becomes an empty Literal, which the agent server rejects later.
        Map<String, Object> schema = storedSchema();
        schema.put("sort_by", spec("type", "string", "enum", List.of()));

        IllegalArgumentException e = assertThrows(
            IllegalArgumentException.class,
            () -> AgenticSearchTemplateService.validateParamSchema(schema)
        );
        assertTrue(e.getMessage().contains("enum"));
    }

    @Test
    public void validate_enumValueNotFittingType_rejected() {
        // A string enum on a number param renders an unquoted word into the body.
        Map<String, Object> schema = storedSchema();
        schema.put("size", spec("type", "number", "enum", List.of("ten")));

        IllegalArgumentException e = assertThrows(
            IllegalArgumentException.class,
            () -> AgenticSearchTemplateService.validateParamSchema(schema)
        );
        assertTrue(e.getMessage().contains("does not fit declared type"));
    }

    @Test
    public void validate_numericEnumOnNumberParam_passes() {
        Map<String, Object> schema = storedSchema();
        schema.put("size", spec("type", "number", "enum", List.of(10, 25, 50)));

        AgenticSearchTemplateService.validateParamSchema(schema);
    }

    @Test
    public void validate_missingOrBlankType_rejected() {
        Map<String, Object> noType = storedSchema();
        noType.put("size", spec("required", false));
        assertThrows(IllegalArgumentException.class, () -> AgenticSearchTemplateService.validateParamSchema(noType));

        Map<String, Object> blankType = storedSchema();
        blankType.put("size", spec("type", ""));
        assertThrows(IllegalArgumentException.class, () -> AgenticSearchTemplateService.validateParamSchema(blankType));
    }

    @Test
    public void validate_nonBooleanRequired_rejected() {
        Map<String, Object> schema = storedSchema();
        schema.put("size", spec("type", "number", "required", "yes"));

        IllegalArgumentException e = assertThrows(
            IllegalArgumentException.class,
            () -> AgenticSearchTemplateService.validateParamSchema(schema)
        );
        assertTrue(e.getMessage().contains("required"));
    }

    @Test
    public void validate_arrayParamRequiresRawJsonString() {
        // A triple-stache slot takes raw JSON as a string, substituted verbatim.
        Map<String, Object> asString = storedSchema();
        asString.put("size", spec("type", "array", "enum", List.of("[\"a\",\"b\"]")));
        AgenticSearchTemplateService.validateParamSchema(asString);
    }

    @Test
    public void validate_arrayParamRejectsJsonArray() {
        // The engine stringifies a List instead of emitting JSON, and a multi-element
        // list makes the guarding section iterate. Both render invalid JSON.
        Map<String, Object> asList = storedSchema();
        asList.put("size", spec("type", "array", "enum", List.of(List.of("a", "b"))));

        IllegalArgumentException e = assertThrows(
            IllegalArgumentException.class,
            () -> AgenticSearchTemplateService.validateParamSchema(asList)
        );
        assertTrue(e.getMessage().contains("does not fit declared type"));
    }

    @Test
    public void validate_nullEnumValue_rejected() {
        Map<String, Object> schema = storedSchema();
        Map<String, Object> withNull = new LinkedHashMap<>();
        withNull.put("type", "string");
        withNull.put("enum", java.util.Arrays.asList("price", null));
        schema.put("sort_by", withNull);

        assertThrows(IllegalArgumentException.class, () -> AgenticSearchTemplateService.validateParamSchema(schema));
    }

    // ---- merge + validate, as updateTemplate runs them ---------------------

    @Test
    public void mergeThenValidate_editThatBreaksTypeIsCaught() {
        // Narrowing sort_by to an enum, but with numbers for a string param.
        Map<String, Object> patch = Map.of("sort_by", spec("enum", List.of(1, 2)));

        Map<String, Object> merged = AgenticSearchTemplateService.mergeParamSchema(storedSchema(), patch);
        // The merge succeeds (the param exists); validation rejects the values.
        assertFalse(merged.isEmpty());
        assertThrows(IllegalArgumentException.class, () -> AgenticSearchTemplateService.validateParamSchema(merged));
    }

    @Test
    public void mergeThenValidate_descriptionOnlyEdit_passes() {
        Map<String, Object> patch = Map.of("lex_query", spec("description", "The shopper's search words."));

        Map<String, Object> merged = AgenticSearchTemplateService.mergeParamSchema(storedSchema(), patch);
        AgenticSearchTemplateService.validateParamSchema(merged);

        @SuppressWarnings("unchecked")
        Map<String, Object> lex = (Map<String, Object>) merged.get("lex_query");
        assertEquals("The shopper's search words.", lex.get("description"));
        assertEquals(true, lex.get("required"));
    }
}
