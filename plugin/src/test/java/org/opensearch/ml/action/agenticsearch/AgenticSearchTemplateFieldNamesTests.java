/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.agenticsearch;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.Test;
import org.opensearch.action.admin.indices.get.GetIndexResponse;
import org.opensearch.cluster.metadata.MappingMetadata;

/** Mapping flattening that backs the field-selector enums. */
public class AgenticSearchTemplateFieldNamesTests {

    private static Map<String, Object> map(Object... keyValues) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            m.put((String) keyValues[i], keyValues[i + 1]);
        }
        return m;
    }

    private static List<String> collect(Map<String, Object> properties) {
        List<String> out = new ArrayList<>();
        AgenticSearchTemplateService.collectFieldNames(properties, "", out, new LinkedHashSet<>());
        return out;
    }

    private static Set<String> sortable(Map<String, Object> properties) {
        Set<String> sortable = new LinkedHashSet<>();
        AgenticSearchTemplateService.collectFieldNames(properties, "", new ArrayList<>(), sortable);
        return sortable;
    }

    private static AgenticSearchTemplateService.MappingFields fields(List<String> all, Set<String> sortable) {
        return new AgenticSearchTemplateService.MappingFields(all, sortable);
    }

    @Test
    public void flatFields_allCollected() {
        List<String> fields = collect(map("price", map("type", "double"), "brand", map("type", "keyword")));

        assertEquals(List.of("price", "brand"), fields);
    }

    @Test
    public void objectContainer_excludedButLeavesCollected() {
        // `spec` cannot be sorted on or matched against, so offering it to the model
        // would let it pick a field that fails at query time.
        Map<String, Object> props = map("spec", map("properties", map("os", map("type", "keyword"), "vendor", map("type", "keyword"))));

        List<String> fields = collect(props);

        assertFalse(fields.contains("spec"));
        assertTrue(fields.containsAll(List.of("spec.os", "spec.vendor")));
        assertEquals(2, fields.size());
        assertEquals(Set.of("spec.os", "spec.vendor"), sortable(props));
    }

    @Test
    public void nestedContainers_recurseToLeavesOnly() {
        Map<String, Object> props = map(
            "recall",
            map("properties", map("date", map("type", "date"), "detail", map("properties", map("note", map("type", "text")))))
        );

        List<String> fields = collect(props);

        assertEquals(List.of("recall.date", "recall.detail.note"), fields);
    }

    @Test
    public void textField_exposesKeywordSubField() {
        Map<String, Object> props = map("title", map("type", "text", "fields", map("keyword", map("type", "keyword"))));

        List<String> fields = collect(props);

        assertEquals(List.of("title", "title.keyword"), fields);
        // The text field itself has no doc_values; its keyword sub-field is judged by its own type.
        assertEquals(Set.of("title.keyword"), sortable(props));
    }

    @Test
    public void textFieldWithFielddata_isSortable() {
        Map<String, Object> props = map("title", map("type", "text", "fielddata", true), "body", map("type", "text"));

        assertEquals(List.of("title", "body"), collect(props));
        assertEquals(Set.of("title"), sortable(props));
    }

    @Test
    public void sortableTypes_numericFamilyDateBooleanIp() {
        Map<String, Object> props = map(
            "a",
            map("type", "scaled_float", "scaling_factor", 100),
            "b",
            map("type", "unsigned_long"),
            "c",
            map("type", "half_float"),
            "d",
            map("type", "date_nanos"),
            "e",
            map("type", "boolean"),
            "f",
            map("type", "ip"),
            "g",
            map("type", "integer")
        );

        assertEquals(Set.of("a", "b", "c", "d", "e", "f", "g"), sortable(props));
    }

    @Test
    public void flatObjectAndVector_collectedButNotSortable() {
        Map<String, Object> props = map("attrs", map("type", "flat_object"), "emb", map("type", "knn_vector", "dimension", 4));

        assertEquals(List.of("attrs", "emb"), collect(props));
        assertTrue(sortable(props).isEmpty());
    }

    @Test
    public void docValuesFalse_notSortable() {
        Map<String, Object> props = map("sku", map("type", "keyword", "doc_values", false), "code", map("type", "keyword"));

        assertEquals(List.of("sku", "code"), collect(props));
        assertEquals(Set.of("code"), sortable(props));
    }

    @Test
    public void disabledObject_skippedEntirely() {
        // enabled:false is not indexed, so neither the object nor its children can be queried.
        Map<String, Object> props = map(
            "raw",
            map("type", "object", "enabled", false),
            "blob",
            map("enabled", "false", "properties", map("inner", map("type", "keyword"))),
            "brand",
            map("type", "keyword")
        );

        assertEquals(List.of("brand"), collect(props));
        assertEquals(Set.of("brand"), sortable(props));
    }

    @Test
    public void nestedContainer_leavesCollectedButNotSortable() {
        // A root-level sort cannot reach into a nested container.
        Map<String, Object> props = map(
            "variants",
            map(
                "type",
                "nested",
                "properties",
                map("sku", map("type", "keyword"), "spec", map("properties", map("size", map("type", "integer"))))
            ),
            "price",
            map("type", "double")
        );

        assertEquals(List.of("variants.sku", "variants.spec.size", "price"), collect(props));
        assertEquals(Set.of("price"), sortable(props));
    }

    @Test
    public void containerWithSubFields_keepsSubFieldsWithoutContainer() {
        // A container carrying `fields` still must not offer itself as a target.
        Map<String, Object> props = map(
            "meta",
            map("properties", map("code", map("type", "keyword")), "fields", map("raw", map("type", "keyword")))
        );

        List<String> fields = collect(props);

        assertFalse(fields.contains("meta"));
        assertTrue(fields.contains("meta.code"));
        assertTrue(fields.contains("meta.raw"));
    }

    @Test
    public void nonMapProperty_collectedAsLeaf() {
        Map<String, Object> props = map("weird", "not-a-map");

        assertEquals(List.of("weird"), collect(props));
        assertTrue(sortable(props).isEmpty());
    }

    // ---- multi-index targets -----------------------------------------------

    private static GetIndexResponse indexResponse(Map<String, Map<String, Object>> propertiesByIndex) {
        Map<String, MappingMetadata> mappings = new LinkedHashMap<>();
        for (Map.Entry<String, Map<String, Object>> e : propertiesByIndex.entrySet()) {
            MappingMetadata metadata = mock(MappingMetadata.class);
            when(metadata.getSourceAsMap()).thenReturn(map("properties", e.getValue()));
            mappings.put(e.getKey(), metadata);
        }
        GetIndexResponse response = mock(GetIndexResponse.class);
        when(response.mappings()).thenReturn(mappings);
        return response;
    }

    @Test
    public void extractFieldNames_unionsAllIndicesInNameOrder() {
        // Given in reverse order: the union still follows sorted index names, so the enum is stable.
        Map<String, Map<String, Object>> byIndex = new LinkedHashMap<>();
        byIndex.put("products-b", map("price", map("type", "double"), "rating", map("type", "float")));
        byIndex.put("products-a", map("brand", map("type", "keyword"), "price", map("type", "double")));

        AgenticSearchTemplateService.MappingFields result = AgenticSearchTemplateService.extractFieldNames(indexResponse(byIndex));

        assertEquals(List.of("brand", "price", "rating"), result.all);
    }

    @Test
    public void extractFieldNames_sortableOnlyWhenSortableInEveryIndex() {
        // brand is only in one index and title is text in one: a sort on either fails the search.
        Map<String, Map<String, Object>> byIndex = new LinkedHashMap<>();
        byIndex.put("a", map("brand", map("type", "keyword"), "price", map("type", "double"), "title", map("type", "keyword")));
        byIndex.put("b", map("price", map("type", "long"), "title", map("type", "text")));

        AgenticSearchTemplateService.MappingFields result = AgenticSearchTemplateService.extractFieldNames(indexResponse(byIndex));

        assertEquals(List.of("brand", "price", "title"), result.all);
        assertEquals(List.of("price"), new ArrayList<>(result.sortable));
    }

    @Test
    public void extractFieldNames_noMappings_isEmpty() {
        GetIndexResponse response = mock(GetIndexResponse.class);
        when(response.mappings()).thenReturn(Map.of());

        AgenticSearchTemplateService.MappingFields result = AgenticSearchTemplateService.extractFieldNames(response);

        assertTrue(result.all.isEmpty());
        assertTrue(result.sortable.isEmpty());
    }

    // ---- deriveSchema ------------------------------------------------------

    // A body with a field-selector param (sort_by), a *_field param, and a value param
    // whose name ends in _by (created_by). Only the field selectors should get the enum.
    private static final String BODY = "{\"query\":{\"bool\":{\"filter\":["
        + "{{#created_by}}{\"term\":{\"created_by\":\"{{created_by}}\"}}{{/created_by}}"
        + "{{#group_field}},{\"term\":{\"{{group_field}}\":\"x\"}}{{/group_field}}]}},"
        + "\"sort\":[{{#sort_by}}{\"{{sort_by}}\":\"desc\"}{{/sort_by}}]}";

    private static final List<String> ALL = List.of("price", "brand", "created_at", "title");
    private static final Set<String> SORTABLE = new LinkedHashSet<>(List.of("price", "brand", "created_at"));

    @Test
    public void deriveSchema_scopesFieldSelectorsToMappingFields() {
        AgenticSearchTemplateService service = new AgenticSearchTemplateService(null, null, null, null, null);
        Map<String, Object> schema = service.deriveSchema(BODY, fields(ALL, SORTABLE));

        // sort_by can only take a sortable field; other selectors may target any field.
        assertEquals(List.of("price", "brand", "created_at"), enumOf(schema, "sort_by"));
        assertEquals(ALL, enumOf(schema, "group_field"));
        assertEquals("mapping", specOf(schema, "sort_by").get("source"));
    }

    @Test
    public void deriveSchema_noSortableFields_omitsSortEnum() {
        // An empty enum would fail validateParamSchema, so it is omitted rather than written.
        AgenticSearchTemplateService service = new AgenticSearchTemplateService(null, null, null, null, null);
        Map<String, Object> schema = service.deriveSchema(BODY, fields(List.of("title"), Set.of()));

        assertFalse(specOf(schema, "sort_by").containsKey("enum"));
        assertFalse(specOf(schema, "sort_by").containsKey("source"));
        assertEquals(List.of("title"), enumOf(schema, "group_field"));
    }

    @Test
    public void deriveSchema_leavesValueParamEndingInByUnscoped() {
        AgenticSearchTemplateService service = new AgenticSearchTemplateService(null, null, null, null, null);
        Map<String, Object> schema = service.deriveSchema(BODY, fields(ALL, SORTABLE));

        // created_by is a filter value, not a field selector; it must not be given the
        // field-name enum (which would force the model to fill it with a field name).
        assertFalse(specOf(schema, "created_by").containsKey("enum"));
    }

    // ---- structural enrichment vs. the name heuristic ----------------------

    // text_field is search text despite its name; sem_field is a neural (knn_vector) target;
    // group_field keys a term filter; order_field and sort_by pick the sort key.
    private static final String ROLE_BODY = "{\"query\":{\"bool\":{\"must\":["
        + "{\"match\":{\"title\":\"{{text_field}}\"}},"
        + "{\"neural\":{\"{{sem_field}}\":{\"query_text\":\"x\",\"k\":5}}}],"
        + "\"filter\":[{\"term\":{\"{{group_field}}\":\"x\"}}]}},"
        + "\"sort\":[{\"{{sort_by}}\":\"desc\"},\"{{order_field}}\"]}";

    /** Derive from ROLE_BODY, then enrich against a hand-built marker render of the same body. */
    private static Map<String, Object> deriveAndEnrich(List<String> all, Set<String> sortable) {
        AgenticSearchTemplateService service = new AgenticSearchTemplateService(null, null, null, null, null);
        Map<String, Object> schema = service.deriveSchema(ROLE_BODY, fields(all, sortable));
        TemplateStructureAnalyzer.MarkerSet markers = TemplateStructureAnalyzer.buildMarkers(schema);
        Map<String, Object> rp = markers.renderParams();
        Map<String, Object> rendered = map(
            "query",
            map(
                "bool",
                map(
                    "must",
                    List
                        .of(
                            map("match", map("title", rp.get("text_field"))),
                            map("neural", map((String) rp.get("sem_field"), map("k", 5)))
                        ),
                    "filter",
                    List.of(map("term", map((String) rp.get("group_field"), "x")))
                )
            ),
            "sort",
            List.of(map((String) rp.get("sort_by"), "desc"), rp.get("order_field"))
        );
        service.applyStructuralEnrichment(schema, markers, rendered, null, sortable);
        return schema;
    }

    @Test
    public void enrichment_valueRoleDropsNameGuessedEnum() {
        Map<String, Object> schema = deriveAndEnrich(ALL, SORTABLE);

        // Used as match text: a field-name enum would make real search text unrepresentable.
        assertFalse(specOf(schema, "text_field").containsKey("enum"));
        assertFalse(specOf(schema, "text_field").containsKey("source"));
        assertEquals("Full-text query matched against the title field.", specOf(schema, "text_field").get("description"));
    }

    @Test
    public void enrichment_nonSortSelectorsKeepFullEnum() {
        Map<String, Object> schema = deriveAndEnrich(ALL, SORTABLE);

        // A neural target and a term key are not sorts, so unsortable fields stay legal.
        assertEquals(ALL, enumOf(schema, "sem_field"));
        assertEquals(ALL, enumOf(schema, "group_field"));
    }

    @Test
    public void enrichment_sortRoleNarrowsToSortable() {
        Map<String, Object> schema = deriveAndEnrich(ALL, SORTABLE);

        // order_field got the full enum from its name; its sort slot narrows it.
        assertEquals(List.of("price", "brand", "created_at"), enumOf(schema, "order_field"));
        assertEquals(List.of("price", "brand", "created_at"), enumOf(schema, "sort_by"));
        assertEquals("mapping", specOf(schema, "order_field").get("source"));
    }

    @Test
    public void enrichment_sortRoleWithNoSortableFields_dropsEnum() {
        Map<String, Object> schema = deriveAndEnrich(List.of("title"), Set.of());

        assertFalse(specOf(schema, "order_field").containsKey("enum"));
        assertFalse(specOf(schema, "order_field").containsKey("source"));
        assertEquals(List.of("title"), enumOf(schema, "sem_field"));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> specOf(Map<String, Object> schema, String param) {
        return (Map<String, Object>) schema.get(param);
    }

    @SuppressWarnings("unchecked")
    private static List<String> enumOf(Map<String, Object> schema, String param) {
        return (List<String>) specOf(schema, param).get("enum");
    }
}
