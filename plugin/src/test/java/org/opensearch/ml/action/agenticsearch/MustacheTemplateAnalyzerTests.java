/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.agenticsearch;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.util.Map;

import org.junit.Test;
import org.opensearch.ml.engine.tools.QueryPlanningPromptTemplate;

public class MustacheTemplateAnalyzerTests {

    @SuppressWarnings("unchecked")
    private static Map<String, Object> spec(Map<String, Object> schema, String name) {
        return (Map<String, Object>) schema.get(name);
    }

    /** The worked example from the design doc (§4.3), abbreviated. */
    private static final String PRODUCT_BODY = "{ \"size\": {{size}}{{^size}}10{{/size}},"
        + "  \"query\": { \"bool\": {"
        + "    \"must\": [ { \"multi_match\": { \"query\": \"{{lex_query}}\", \"fields\": [\"title\"] } } ],"
        + "    \"filter\": [ { \"match_all\": {} }"
        + "      {{#color}},{ \"term\": { \"color\": \"{{color}}\" } }{{/color}}"
        + "      {{#price_max}},{ \"range\": { \"price\": { \"lte\": {{price_max}} } } }{{/price_max}}"
        + "    ]{{#sort_by}}, \"sort\": [ { \"{{sort_by}}\": { \"order\": \"{{sort_order}}\" } } ]{{/sort_by}} } } }";

    @Test
    public void derive_workedExample_paramsAndTypes() {
        Map<String, Object> schema = MustacheTemplateAnalyzer.derive(PRODUCT_BODY);

        // All the template's params are discovered.
        assertTrue(schema.keySet().containsAll(java.util.List.of("size", "lex_query", "color", "price_max", "sort_by", "sort_order")));

        // lex_query sits inside quotes -> string, and is required (plain value, no default).
        assertEquals("string", spec(schema, "lex_query").get("type"));
        assertEquals(Boolean.TRUE, spec(schema, "lex_query").get("required"));

        // size has an inverted-section default {{^size}}10{{/size}} -> optional number.
        assertEquals("number", spec(schema, "size").get("type"));
        assertEquals(Boolean.FALSE, spec(schema, "size").get("required"));

        // price_max is used both as a section guard AND a value -> optional (guarded).
        assertEquals(Boolean.FALSE, spec(schema, "price_max").get("required"));

        // color/sort_by/sort_order are optional (guarded / defaulted), never required.
        assertFalse((Boolean) spec(schema, "color").get("required"));
        assertFalse((Boolean) spec(schema, "sort_order").get("required"));
    }

    @Test
    public void derive_sectionGuardOnly_isBoolean() {
        // {{#flag}}...{{/flag}} where flag is never substituted -> boolean guard.
        Map<String, Object> schema = MustacheTemplateAnalyzer.derive("{ {{#flag}}\"a\":1{{/flag}} }");
        assertEquals("boolean", spec(schema, "flag").get("type"));
        assertEquals(Boolean.FALSE, spec(schema, "flag").get("required"));
    }

    @Test
    public void derive_tripleStache_isArray() {
        // {{{x}}} injects raw JSON (an array/object), not an escaped scalar.
        Map<String, Object> schema = MustacheTemplateAnalyzer.derive("{ \"fields\": {{{lex_fields}}} }");
        assertEquals("array", spec(schema, "lex_fields").get("type"));
    }

    @Test
    public void derive_unquotedScalar_isNumber() {
        Map<String, Object> schema = MustacheTemplateAnalyzer.derive("{ \"from\": {{from}} }");
        assertEquals("number", spec(schema, "from").get("type"));
        assertEquals(Boolean.TRUE, spec(schema, "from").get("required"));
    }

    @Test
    public void derive_ignoresComments() {
        Map<String, Object> schema = MustacheTemplateAnalyzer.derive("{ {{! a comment }} \"q\": \"{{lex}}\" }");
        assertEquals(1, schema.size());
        assertTrue(schema.containsKey("lex"));
    }

    @Test
    public void derive_escapedBackslashBeforeQuote_scalarStaysNumber() {
        // The closing quote of "C:\\" is preceded by an escaped backslash, NOT an
        // escaped quote, so the string literal DOES close. {{from}} then sits outside
        // any string and must classify as number, not string.
        Map<String, Object> schema = MustacheTemplateAnalyzer.derive("{ \"path\": \"C:\\\\\", \"from\": {{from}} }");
        assertEquals("number", spec(schema, "from").get("type"));
    }

    @Test
    public void derive_escapedQuoteInString_scalarStaysString() {
        // An escaped quote (\") inside a string does NOT close it, so {{lex}} is still
        // inside the string literal -> string.
        Map<String, Object> schema = MustacheTemplateAnalyzer.derive("{ \"q\": \"say \\\"hi\\\" {{lex}}\" }");
        assertEquals("string", spec(schema, "lex").get("type"));
    }

    @Test
    public void derive_quoteScanResetsPerLine_laterScalarUnaffected() {
        // A lone quote on an earlier line must not leak "inside string" state onto a
        // later line's scalar. {{from}} is unquoted on its own line -> number.
        Map<String, Object> schema = MustacheTemplateAnalyzer.derive("{ \"note\": \"unterminated\n, \"from\": {{from}} }");
        assertEquals("number", spec(schema, "from").get("type"));
    }

    @Test
    public void derive_unbalancedSection_throws() {
        assertThrows(IllegalArgumentException.class, () -> MustacheTemplateAnalyzer.derive("{ {{#a}} x {{/b}} }"));
        assertThrows(IllegalArgumentException.class, () -> MustacheTemplateAnalyzer.derive("{ {{#a}} x }"));
    }

    @Test
    public void derive_emptyBody_throws() {
        assertThrows(IllegalArgumentException.class, () -> MustacheTemplateAnalyzer.derive(""));
        assertThrows(IllegalArgumentException.class, () -> MustacheTemplateAnalyzer.derive(null));
    }

    @Test
    public void derive_realDefaultSearchTemplate_findsExpectedParams() {
        // The production DEFAULT_SEARCH_TEMPLATE exercises triples, inverted defaults,
        // and section guards; the analyzer should walk it without error.
        Map<String, Object> schema = MustacheTemplateAnalyzer.derive(QueryPlanningPromptTemplate.DEFAULT_SEARCH_TEMPLATE);
        assertTrue(schema.containsKey("lex_query"));
        assertTrue(schema.containsKey("from"));
        assertTrue(schema.containsKey("sem_enabled"));
        // sem_enabled guards a section but is never substituted -> boolean.
        assertEquals("boolean", spec(schema, "sem_enabled").get("type"));
        // lex_fields is a triple-stache -> array.
        assertEquals("array", spec(schema, "lex_fields").get("type"));
    }
}
