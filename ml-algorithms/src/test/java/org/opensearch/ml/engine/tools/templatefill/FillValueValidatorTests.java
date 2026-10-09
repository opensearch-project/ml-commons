/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.tools.templatefill;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.opensearch.ml.engine.tools.templatefill.FillToolSchemaTests.spec;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;

public class FillValueValidatorTests {

    private static Map<String, Object> filled(Object... kv) {
        Map<String, Object> m = new HashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], kv[i + 1]);
        }
        return m;
    }

    @Test
    public void keepsOnlyFilledParams_andDropsUnknownKeys() {
        Map<String, Object> clean = FillValueValidator
            .validate(FillToolSchemaTests.productSchema(), filled("query_text", "shoes", "size", 5.0, "made_up", "x"));
        assertEquals(Map.of("query_text", "shoes", "size", 5L), clean);
        assertFalse(clean.containsKey("category"));
    }

    @Test
    public void missingRequired_throws() {
        assertThrows(
            IllegalArgumentException.class,
            () -> FillValueValidator.validate(FillToolSchemaTests.productSchema(), filled("size", 5))
        );
    }

    @Test
    public void enum_rejectsValueOutsideTheSet() {
        Map<String, Object> schema = FillToolSchemaTests.productSchema();
        assertThrows(
            IllegalArgumentException.class,
            () -> FillValueValidator.validate(schema, filled("query_text", "a", "size", 1, "sort_by", "title"))
        );
        assertEquals("price", FillValueValidator.validate(schema, filled("query_text", "a", "size", 1, "sort_by", "price")).get("sort_by"));
    }

    @Test
    public void numericEnum_matchesAcrossNumberForms() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("size", spec("number", true, "", List.of(1, 5, 10)));
        assertEquals(5L, FillValueValidator.validate(schema, filled("size", 5.0)).get("size"));
        assertEquals(5L, FillValueValidator.validate(schema, filled("size", "5")).get("size"));
        assertThrows(IllegalArgumentException.class, () -> FillValueValidator.validate(schema, filled("size", 7)));
    }

    @Test
    public void number_prefersIntegers() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("n", spec("number", true, "", null));
        assertEquals(5L, FillValueValidator.validate(schema, filled("n", 5.0)).get("n"));
        assertEquals(1.5, FillValueValidator.validate(schema, filled("n", 1.5)).get("n"));
        assertEquals(20L, FillValueValidator.validate(schema, filled("n", "20")).get("n"));
        assertThrows(IllegalArgumentException.class, () -> FillValueValidator.validate(schema, filled("n", "many")));
    }

    @Test
    public void integer_rejectsFractions() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("n", spec("integer", true, "", null));
        assertEquals(3L, FillValueValidator.validate(schema, filled("n", 3)).get("n"));
        assertThrows(IllegalArgumentException.class, () -> FillValueValidator.validate(schema, filled("n", 2.5)));
    }

    @Test
    public void boolean_acceptsBooleanSpellings() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("b", spec("boolean", true, "", null));
        assertEquals(true, FillValueValidator.validate(schema, filled("b", true)).get("b"));
        assertEquals(false, FillValueValidator.validate(schema, filled("b", "False")).get("b"));
        assertThrows(IllegalArgumentException.class, () -> FillValueValidator.validate(schema, filled("b", "maybe")));
    }

    @Test
    public void array_isReserializedAsOneJsonValue() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("tags", spec("array", true, "", null));
        assertEquals("[\"a\"]", FillValueValidator.validate(schema, filled("tags", "[\"a\"]")).get("tags"));
        assertEquals("[\"a\",\"b\"]", FillValueValidator.validate(schema, filled("tags", " [ \"a\", \"b\" ] ")).get("tags"));
        assertEquals("[\"a\",\"b\"]", FillValueValidator.validate(schema, filled("tags", List.of("a", "b"))).get("tags"));
    }

    @Test
    public void array_rejectsObjects() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("tags", spec("array", true, "", null));
        assertThrows(
            IllegalArgumentException.class,
            () -> FillValueValidator.validate(schema, filled("tags", "{\"index\":\"other\",\"id\":\"1\",\"path\":\"ids\"}"))
        );
        assertThrows(IllegalArgumentException.class, () -> FillValueValidator.validate(schema, filled("tags", Map.of("a", 1))));
    }

    @Test
    public void enum_matchesLargeIntegersExactly() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("id", spec("integer", true, "", List.of(9007199254740993L, 9007199254740992L)));
        assertEquals(9007199254740992L, FillValueValidator.validate(schema, filled("id", 9007199254740992L)).get("id"));
        assertEquals(9007199254740993L, FillValueValidator.validate(schema, filled("id", 9007199254740993L)).get("id"));
        // An integral double still matches its integer member.
        Map<String, Object> small = new LinkedHashMap<>();
        small.put("size", spec("integer", true, "", List.of(5L, 10L)));
        assertEquals(5L, FillValueValidator.validate(small, filled("size", 5.0)).get("size"));
    }

    @Test
    public void array_wrapsScalars() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("tags", spec("array", true, "", null));
        assertEquals("[\"red\"]", FillValueValidator.validate(schema, filled("tags", "red")).get("tags"));
        assertEquals("[\"red\"]", FillValueValidator.validate(schema, filled("tags", "\"red\"")).get("tags"));
        assertEquals("[5]", FillValueValidator.validate(schema, filled("tags", 5)).get("tags"));
    }

    @Test
    public void array_cannotBreakOutOfItsSlot() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("tags", spec("array", true, "", null));
        // Trailing content after a JSON value is not a single value, so it is carried as one string element.
        String injected = "[\"red\"]}}],\"must_not\":[{\"match_all\":{}}";
        Object clean = FillValueValidator.validate(schema, filled("tags", injected)).get("tags");
        assertEquals(List.of(injected), org.opensearch.ml.common.utils.StringUtils.gson.fromJson((String) clean, List.class));
    }

    @Test
    public void string_rejectsObjects() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("s", spec("string", true, "", null));
        assertEquals("5", FillValueValidator.validate(schema, filled("s", 5)).get("s"));
        assertThrows(IllegalArgumentException.class, () -> FillValueValidator.validate(schema, filled("s", Map.of("a", 1))));
    }
}
