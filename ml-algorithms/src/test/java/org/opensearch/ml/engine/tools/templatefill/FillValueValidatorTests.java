/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.tools.templatefill;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.opensearch.ml.engine.tools.templatefill.FillToolSchemaTests.spec;

import java.math.BigDecimal;
import java.math.BigInteger;
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
    public void bigIntegers_keepExactPrecision() {
        // The largest unsigned_long, beyond both a long and a double's exact range.
        BigInteger max = new BigInteger("18446744073709551615");
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("id", spec("integer", true, "", List.of(max, BigInteger.ONE)));
        assertEquals(max, FillValueValidator.validate(schema, filled("id", max)).get("id"));
        assertEquals(max, FillValueValidator.validate(schema, filled("id", "18446744073709551615")).get("id"));

        Map<String, Object> open = new LinkedHashMap<>();
        open.put("id", spec("integer", true, "", null));
        assertEquals(max, FillValueValidator.validate(open, filled("id", max)).get("id"));
        assertEquals(max, FillValueValidator.validate(open, filled("id", "18446744073709551615")).get("id"));
        assertEquals(max, FillValueValidator.validate(open, filled("id", new BigDecimal("18446744073709551615.0"))).get("id"));
        // A BigInteger within a long's range narrows to a Long.
        assertEquals(7L, FillValueValidator.validate(open, filled("id", BigInteger.valueOf(7))).get("id"));
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

    @Test
    public void list_isARealListOfStrings() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("terms", spec("list", true, "", null));
        assertEquals(List.of("a", "b"), FillValueValidator.validate(schema, filled("terms", List.of("a", "b"))).get("terms"));
        assertEquals(List.of("a", "5"), FillValueValidator.validate(schema, filled("terms", List.of("a", 5))).get("terms"));
        // A model that sends the array as a JSON string still yields a list.
        assertEquals(List.of("a", "b"), FillValueValidator.validate(schema, filled("terms", "[\"a\", \"b\"]")).get("terms"));
    }

    @Test
    public void list_wrapsScalars_andDoesNotSplitPlainText() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("terms", spec("list", true, "", null));
        assertEquals(List.of("red"), FillValueValidator.validate(schema, filled("terms", "red")).get("terms"));
        assertEquals(List.of("5"), FillValueValidator.validate(schema, filled("terms", 5)).get("terms"));
        // A comma-joined string is one element by design; splitting would guess at the model's intent.
        assertEquals(List.of("red, blue"), FillValueValidator.validate(schema, filled("terms", "red, blue")).get("terms"));
    }

    @Test
    public void list_enumConstrainsEachElement() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("colors", spec("list", true, "", List.of("red", "blue")));
        assertEquals(List.of("red", "blue"), FillValueValidator.validate(schema, filled("colors", List.of("red", "blue"))).get("colors"));
        assertEquals(List.of("red"), FillValueValidator.validate(schema, filled("colors", "red")).get("colors"));
        assertEquals(List.of("red"), FillValueValidator.validate(schema, filled("colors", "[\"red\"]")).get("colors"));
        assertThrows(IllegalArgumentException.class, () -> FillValueValidator.validate(schema, filled("colors", List.of("red", "green"))));
    }

    @Test
    public void list_rejectsNestedAndNullElements() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("terms", spec("list", true, "", null));
        assertEquals(List.of("true"), FillValueValidator.validate(schema, filled("terms", List.of(true))).get("terms"));
        assertThrows(
            IllegalArgumentException.class,
            () -> FillValueValidator.validate(schema, filled("terms", List.of("a", List.of("b"))))
        );
        assertThrows(IllegalArgumentException.class, () -> FillValueValidator.validate(schema, filled("terms", "[[\"a\"]]")));
        assertThrows(IllegalArgumentException.class, () -> FillValueValidator.validate(schema, filled("terms", "[\"a\", null]")));
    }

    @Test
    public void list_rejectsObjects() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("terms", spec("list", true, "", null));
        assertThrows(IllegalArgumentException.class, () -> FillValueValidator.validate(schema, filled("terms", Map.of("a", 1))));
        assertThrows(IllegalArgumentException.class, () -> FillValueValidator.validate(schema, filled("terms", "{\"a\":1}")));
        assertThrows(
            IllegalArgumentException.class,
            () -> FillValueValidator.validate(schema, filled("terms", List.of("a", Map.of("b", 2))))
        );
    }
}
