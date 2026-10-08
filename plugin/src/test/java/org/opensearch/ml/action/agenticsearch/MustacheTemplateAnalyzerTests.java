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
import java.util.Set;

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

    // ---- 3.3: quote state from literal text only ----------------------------

    @Test
    public void derive_quoteInsideComment_doesNotFlipLaterScalars() {
        // One-line (minified) body: the " inside the comment is not JSON, so q stays a
        // string and n stays a number.
        Map<String, Object> schema = MustacheTemplateAnalyzer
            .derive("{{! dont use a \" quote here }}{\"query\":{\"match\":{\"title\":\"{{q}}\"}},\"size\":{{n}}}");
        assertEquals("string", spec(schema, "q").get("type"));
        assertEquals("number", spec(schema, "n").get("type"));
    }

    @Test
    public void derive_quoteInsideCustomDelimiterTag_doesNotCount() {
        Map<String, Object> schema = MustacheTemplateAnalyzer.derive("{{=<% %>=}}<%! a \" here %>{\"q\":\"<%q%>\",\"size\":<%n%>}");
        assertEquals("string", spec(schema, "q").get("type"));
        assertEquals("number", spec(schema, "n").get("type"));
    }

    // ---- 3.2: quoted triple-stache is a string ------------------------------

    @Test
    public void derive_quotedTriple_isString() {
        Map<String, Object> schema = MustacheTemplateAnalyzer.derive("{\"query\":{\"match\":{\"title\":\"{{{q}}}\"}}}");
        assertEquals("string", spec(schema, "q").get("type"));
        assertEquals(Boolean.TRUE, spec(schema, "q").get("required"));
    }

    @Test
    public void derive_quotedAmpersand_isString() {
        Map<String, Object> schema = MustacheTemplateAnalyzer.derive("{\"query\":{\"match\":{\"title\":\"{{&q}}\"}}}");
        assertEquals("string", spec(schema, "q").get("type"));
    }

    @Test
    public void derive_unquotedAmpersand_isArray() {
        Map<String, Object> schema = MustacheTemplateAnalyzer.derive("{ \"fields\": {{&lex_fields}} }");
        assertEquals("array", spec(schema, "lex_fields").get("type"));
    }

    @Test
    public void derive_tripleQuotedAndUnquoted_staysArray() {
        // Conflict rule: the unquoted use injects raw JSON, so array wins (a string value
        // would break that slot).
        Map<String, Object> schema = MustacheTemplateAnalyzer.derive("{\"a\":\"{{{x}}}\",\"b\":{{{x}}}}");
        assertEquals("array", spec(schema, "x").get("type"));
    }

    // ---- 3.5: list iteration and lang-mustache built-ins fail loud ----------

    private static final String ITERATOR_BODY = "{\"query\":{\"terms\":{\"brand\":[{{#brands}}\"{{.}}\",{{/brands}}\"zzz\"]}}}";

    @Test
    public void derive_implicitIteratorInSection_throwsNamingSection() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> MustacheTemplateAnalyzer.derive(ITERATOR_BODY));
        assertTrue(e.getMessage().contains("param 'brands' is iterated as a list ({{.}})"));
        assertTrue(e.getMessage().contains("supply param_schema explicitly"));
    }

    @Test
    public void paramNames_implicitIteratorInSection_returnsSectionName() {
        assertEquals(Set.of("brands"), MustacheTemplateAnalyzer.paramNames(ITERATOR_BODY));
    }

    @Test
    public void derive_implicitIteratorAtRootOrInInvertedSection_isIgnored() {
        Map<String, Object> schema = MustacheTemplateAnalyzer.derive("{ {{.}} {{^skip}}\"a\":\"{{.}}\"{{/skip}} }");
        assertEquals(Set.of("skip"), schema.keySet());
        assertEquals("boolean", spec(schema, "skip").get("type"));
    }

    @Test
    public void derive_toJsonSection_throws_paramNamesReturnsInnerName() {
        String body = "{\"query\":{\"terms\":{\"brand\":{{#toJson}}brands{{/toJson}}}}}";
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> MustacheTemplateAnalyzer.derive(body));
        assertTrue(e.getMessage().contains("param 'brands' is rendered through {{#toJson}}"));
        assertEquals(Set.of("brands"), MustacheTemplateAnalyzer.paramNames(body));
    }

    @Test
    public void derive_joinSections_throw_paramNamesReturnsInnerName() {
        String join = "{\"query\":{\"query_string\":{\"query\":\"{{#join}}terms{{/join}}\"}}}";
        assertThrows(IllegalArgumentException.class, () -> MustacheTemplateAnalyzer.derive(join));
        assertEquals(Set.of("terms"), MustacheTemplateAnalyzer.paramNames(join));

        String joinDelim = "{\"query\":{\"query_string\":{\"query\":\"{{#join delimiter=' OR '}}terms{{/join delimiter=' OR '}}\"}}}";
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> MustacheTemplateAnalyzer.derive(joinDelim));
        assertTrue(e.getMessage().contains("{{#join}}"));
        assertEquals(Set.of("terms"), MustacheTemplateAnalyzer.paramNames(joinDelim));
    }

    @Test
    public void derive_urlSection_isNotAParam_innerTagsStillScanned() {
        Map<String, Object> schema = MustacheTemplateAnalyzer.derive("{\"q\":\"{{#url}}{{term}}{{/url}}\"}");
        assertEquals(Set.of("term"), schema.keySet());
        assertEquals("string", spec(schema, "term").get("type"));
    }

    // ---- 3.4: malformed delimiter change -----------------------------------

    @Test
    public void derive_delimiterChangeWithoutTrailingEquals_throws() {
        IllegalArgumentException e = assertThrows(
            IllegalArgumentException.class,
            () -> MustacheTemplateAnalyzer.derive("{{=<% %>}}{\"size\":<%n%>}")
        );
        assertTrue(e.getMessage().contains("Malformed delimiter change"));
        // Lenient name collection still walks the body.
        assertEquals(Set.of("n"), MustacheTemplateAnalyzer.paramNames("{{=<% %>}}{\"size\":<%n%>}"));
    }

    @Test
    public void derive_wellFormedDelimiterChange_derives() {
        Map<String, Object> schema = MustacheTemplateAnalyzer.derive("{{=<% %>=}}{\"q\":\"<%q%>\",\"size\":<%n%>}");
        assertEquals("string", spec(schema, "q").get("type"));
        assertEquals("number", spec(schema, "n").get("type"));
    }

    @Test
    public void paramNames_unbalancedOrEmpty_throws() {
        assertThrows(IllegalArgumentException.class, () -> MustacheTemplateAnalyzer.paramNames("{ {{#a}} x }"));
        assertThrows(IllegalArgumentException.class, () -> MustacheTemplateAnalyzer.paramNames(""));
    }

    @Test
    public void paramNames_matchesDeriveKeysForDerivableBody() {
        assertEquals(MustacheTemplateAnalyzer.derive(PRODUCT_BODY).keySet(), MustacheTemplateAnalyzer.paramNames(PRODUCT_BODY));
    }

    @Test
    public void derive_realDefaultSearchTemplate_typesUnchanged() {
        // Regression: the shipped template's three triple-staches sit in unquoted slots,
        // so they stay arrays; the quote-state rewrite must not shift any scalar's type.
        Map<String, Object> schema = MustacheTemplateAnalyzer.derive(QueryPlanningPromptTemplate.DEFAULT_SEARCH_TEMPLATE);
        assertEquals("string", spec(schema, "lex_query").get("type"));
        assertEquals("string", spec(schema, "lex_type").get("type"));
        assertEquals("string", spec(schema, "sem_field").get("type"));
        assertEquals("number", spec(schema, "from").get("type"));
        assertEquals("number", spec(schema, "lex_boost").get("type"));
        assertEquals("array", spec(schema, "lex_fields").get("type"));
        assertEquals("array", spec(schema, "filters").get("type"));
        assertEquals("array", spec(schema, "sort").get("type"));
        assertEquals("boolean", spec(schema, "sem_enabled").get("type"));
    }
}
