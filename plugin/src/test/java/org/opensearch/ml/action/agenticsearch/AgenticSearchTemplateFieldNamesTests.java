/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.agenticsearch;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;

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
        AgenticSearchTemplateService.collectFieldNames(properties, "", out);
        return out;
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
    }

    // A body with a field-selector param (sort_by), a *_field param, and a value param
    // whose name ends in _by (created_by). Only the field selectors should get the enum.
    private static final String BODY = "{\"query\":{\"bool\":{\"filter\":["
        + "{{#created_by}}{\"term\":{\"created_by\":\"{{created_by}}\"}}{{/created_by}}"
        + "{{#group_field}},{\"term\":{\"{{group_field}}\":\"x\"}}{{/group_field}}]}},"
        + "\"sort\":[{{#sort_by}}{\"{{sort_by}}\":\"desc\"}{{/sort_by}}]}";

    @Test
    public void deriveSchema_scopesFieldSelectorsToMappingFields() {
        AgenticSearchTemplateService service = new AgenticSearchTemplateService(null, null, null, null, null);
        Map<String, Object> schema = service.deriveSchema(BODY, List.of("price", "brand", "created_at"));

        assertEquals(List.of("price", "brand", "created_at"), enumOf(schema, "sort_by"));
        assertEquals(List.of("price", "brand", "created_at"), enumOf(schema, "group_field"));
    }

    @Test
    public void deriveSchema_leavesValueParamEndingInByUnscoped() {
        AgenticSearchTemplateService service = new AgenticSearchTemplateService(null, null, null, null, null);
        Map<String, Object> schema = service.deriveSchema(BODY, List.of("price", "brand", "created_at"));

        // created_by is a filter value, not a field selector; it must not be given the
        // field-name enum (which would force the model to fill it with a field name).
        assertFalse(specOf(schema, "created_by").containsKey("enum"));
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
