/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.tools.templatefill;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.opensearch.ml.engine.tools.templatefill.FillToolSchema.CANNOT_EXPRESS_FIELD;
import static org.opensearch.ml.engine.tools.templatefill.FillToolSchema.CHOICE_FIELD;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;
import org.opensearch.ml.common.agenticsearch.AgenticSearchTemplate;

@SuppressWarnings("unchecked")
public class FillToolSchemaTests {

    public static Map<String, Object> spec(String type, boolean required, String description, List<?> enumValues) {
        Map<String, Object> spec = new LinkedHashMap<>();
        spec.put("type", type);
        spec.put("required", required);
        spec.put("description", description);
        if (enumValues != null) {
            spec.put("enum", enumValues);
        }
        return spec;
    }

    public static AgenticSearchTemplate template(String id, Map<String, Object> paramSchema) {
        return AgenticSearchTemplate.builder().templateId(id).indexBinding("products").paramSchema(paramSchema).build();
    }

    public static Map<String, Object> productSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("query_text", spec("string", true, "Full-text query.", null));
        schema.put("category", spec("string", false, "", null));
        schema.put("sort_by", spec("string", false, "Field to sort by.", List.of("price", "title.keyword")));
        schema.put("size", spec("number", true, "Number of results.", null));
        schema.put("tags", spec("array", false, "Tags.", null));
        return schema;
    }

    private static Map<String, Object> properties(Map<String, Object> schema) {
        return (Map<String, Object>) schema.get("properties");
    }

    @Test
    public void single_mapsEachParamAndAddsAbstainFlag() {
        FillToolSchema.Built built = FillToolSchema.single(template("product_search", productSchema()));
        Map<String, Object> props = properties(built.getSchema());

        assertEquals("object", built.getSchema().get("type"));
        assertEquals(List.of("query_text", "size"), built.getSchema().get("required"));
        assertEquals(Map.of("type", "string", "description", "Full-text query."), props.get("query_text"));
        // An empty description is omitted.
        assertEquals(Map.of("type", "string"), props.get("category"));
        assertEquals(List.of("price", "title.keyword"), ((Map<String, Object>) props.get("sort_by")).get("enum"));
        assertEquals("number", ((Map<String, Object>) props.get("size")).get("type"));
        // An array slot is a JSON literal carried as a string.
        Map<String, Object> tags = (Map<String, Object>) props.get("tags");
        assertEquals("string", tags.get("type"));
        assertTrue(((String) tags.get("description")).contains("JSON array literal"));

        assertTrue(built.isAbstainEnabled());
        assertEquals("boolean", ((Map<String, Object>) props.get(CANNOT_EXPRESS_FIELD)).get("type"));
        assertEquals("query_text", built.getPropertyToParam().get("product_search").get("query_text"));
    }

    @Test
    public void single_realParamNamedLikeTheFlag_disablesAbstain() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put(CANNOT_EXPRESS_FIELD, spec("boolean", false, "", null));
        FillToolSchema.Built built = FillToolSchema.single(template("t", schema));
        assertFalse(built.isAbstainEnabled());
        assertEquals(CANNOT_EXPRESS_FIELD, built.getPropertyToParam().get("t").get(CANNOT_EXPRESS_FIELD));
    }

    @Test
    public void single_sanitizesIllegalPropertyNames() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("a b", spec("string", false, "", null));
        schema.put("a_b", spec("string", false, "", null));
        FillToolSchema.Built built = FillToolSchema.single(template("t", schema));
        Map<String, String> names = built.getPropertyToParam().get("t");
        assertEquals("a b", names.get("a_b"));
        assertEquals("a_b", names.get("a_b_1"));
    }

    @Test
    public void numericEnum_keepsDeclaredType() {
        Map<String, Object> out = FillToolSchema.paramJsonSchema(spec("number", false, "", List.of(1, 5, 10)));
        assertEquals("number", out.get("type"));
        // Non-string members under a string type: the enum alone constrains the value.
        out = FillToolSchema.paramJsonSchema(spec("string", false, "", List.of(1, "a")));
        assertFalse(out.containsKey("type"));
    }

    @Test
    public void multi_namespacesParamsAndRequiresOnlyTheChoice() {
        Map<String, Object> other = new LinkedHashMap<>();
        other.put("query_text", spec("string", true, "Semantic query.", null));
        FillToolSchema.Built built = FillToolSchema
            .multi(List.of(template("product_search", productSchema()), template("semantic-search", other)));
        Map<String, Object> props = properties(built.getSchema());

        assertEquals(List.of(CHOICE_FIELD), built.getSchema().get("required"));
        List<String> keys = List.copyOf(props.keySet());
        assertEquals(CANNOT_EXPRESS_FIELD, keys.get(0));
        assertEquals(CHOICE_FIELD, keys.get(1));
        assertEquals(List.of("product_search", "semantic-search", "none"), ((Map<String, Object>) props.get(CHOICE_FIELD)).get("enum"));
        assertEquals(Map.of("product_search", "t0__", "semantic-search", "t1__"), built.getPrefixes());
        assertTrue(props.containsKey("t0__query_text"));
        // Each namespaced property names its template, since the prefix alone does not.
        assertEquals("[semantic-search] Semantic query.", ((Map<String, Object>) props.get("t1__query_text")).get("description"));
        assertEquals("[product_search]", ((Map<String, Object>) props.get("t0__category")).get("description"));
        assertEquals("query_text", built.getPropertyToParam().get("semantic-search").get("t1__query_text"));
        // Every candidate's params are optional in the merged schema; the validator restores required-ness.
        assertFalse(built.getSchema().get("required").toString().contains("query_text"));
    }

    @Test
    public void multi_longTemplateIds_keepParamNamesDistinct() {
        String longA = "a".repeat(200);
        String longB = "a".repeat(199) + "b";
        FillToolSchema.Built built = FillToolSchema.multi(List.of(template(longA, productSchema()), template(longB, productSchema())));
        Map<String, String> a = built.getPropertyToParam().get(longA);
        Map<String, String> b = built.getPropertyToParam().get(longB);
        assertTrue(a.containsKey("t0__query_text") && a.containsKey("t0__size"));
        assertTrue(b.containsKey("t1__query_text") && b.containsKey("t1__size"));
    }

    @Test
    public void multi_candidateNamedNone_doesNotDuplicateSentinel() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("q", spec("string", true, "", null));
        FillToolSchema.Built built = FillToolSchema.multi(List.of(template("none", schema), template("b", schema)));
        assertEquals(List.of("none", "b"), ((Map<String, Object>) properties(built.getSchema()).get(CHOICE_FIELD)).get("enum"));
    }
}
