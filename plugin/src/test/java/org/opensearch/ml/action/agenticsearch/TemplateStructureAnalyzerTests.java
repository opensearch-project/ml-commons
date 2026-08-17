/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.agenticsearch;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.junit.Test;

/** Clause-role recovery, description assembly, and enum tables (no cluster; hand-built JSON). */
public class TemplateStructureAnalyzerTests {

    private static Map<String, Object> map(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], kv[i + 1]);
        }
        return m;
    }

    private static List<Object> list(Object... items) {
        List<Object> l = new ArrayList<>();
        for (Object item : items) {
            l.add(item);
        }
        return l;
    }

    private static Map<String, Object> spec(String type, boolean required) {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put(MustacheTemplateAnalyzer.TYPE_KEY, type);
        s.put(MustacheTemplateAnalyzer.REQUIRED_KEY, required);
        s.put(MustacheTemplateAnalyzer.DESCRIPTION_KEY, "");
        return s;
    }

    /** The schema the PRODUCT_BODY fixture derives, in body order. */
    private static Map<String, Object> productSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("size", spec("number", false));
        schema.put("lex_query", spec("string", true));
        schema.put("color", spec("string", false));
        schema.put("price_max", spec("number", false));
        schema.put("sort_by", spec("string", false));
        schema.put("sort_order", spec("string", false));
        return schema;
    }

    /** A rendered tree that places every param's marker where PRODUCT_BODY would render it. */
    private static Map<String, Object> productRendered(Map<String, Object> rp) {
        return map(
            "size",
            rp.get("size"),
            "query",
            map(
                "bool",
                map(
                    "must",
                    list(map("multi_match", map("query", rp.get("lex_query"), "fields", list("title")))),
                    "filter",
                    list(
                        map("match_all", map()),
                        map("term", map("color", rp.get("color"))),
                        map("range", map("price", map("lte", rp.get("price_max"))))
                    ),
                    "sort",
                    list(map((String) rp.get("sort_by"), map("order", rp.get("sort_order"))))
                )
            )
        );
    }

    @Test
    public void buildMarkers_typesToRenderValues() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("s", spec("string", true));
        schema.put("n", spec("number", true));
        schema.put("b", spec("boolean", false));
        schema.put("a", spec("array", false));

        Map<String, Object> rp = TemplateStructureAnalyzer.buildMarkers(schema).renderParams();

        assertTrue(rp.get("s") instanceof String);
        assertTrue(rp.get("n") instanceof Long);
        assertEquals(Boolean.TRUE, rp.get("b"));
        assertEquals("[]", rp.get("a"));
    }

    @Test
    public void locate_findsValuesAndKeys_withStablePathFlag() {
        Map<String, Object> schema = productSchema();
        TemplateStructureAnalyzer.MarkerSet markers = TemplateStructureAnalyzer.buildMarkers(schema);
        Map<String, Object> rendered = productRendered(markers.renderParams());

        Map<String, TemplateStructureAnalyzer.Located> located = TemplateStructureAnalyzer.locate(rendered, markers);

        assertTrue(located.containsKey("lex_query"));
        assertTrue(located.get("sort_by").asKey); // used as a JSON key -> field selector
        assertFalse(located.get("lex_query").asKey);
        // A top-level scalar path is stable; one nested in an array is not.
        assertTrue(located.get("size").isStablePath());
        assertFalse(located.get("color").isStablePath());
    }

    @Test
    public void classifyAndDescribe_productBodyRoles() {
        Map<String, Object> schema = productSchema();
        TemplateStructureAnalyzer.MarkerSet markers = TemplateStructureAnalyzer.buildMarkers(schema);
        Map<String, Object> rendered = productRendered(markers.renderParams());
        Map<String, TemplateStructureAnalyzer.Located> located = TemplateStructureAnalyzer.locate(rendered, markers);

        // Full-text: field read from the multi_match sibling "fields".
        TemplateStructureAnalyzer.Facts lex = TemplateStructureAnalyzer.classify(located.get("lex_query"), rendered);
        assertEquals(TemplateStructureAnalyzer.ROLE_FULL_TEXT, lex.role);
        assertEquals("Full-text query matched against the title field.", TemplateStructureAnalyzer.describe(lex, null));

        // Term filter.
        TemplateStructureAnalyzer.Facts color = TemplateStructureAnalyzer.classify(located.get("color"), rendered);
        assertEquals(TemplateStructureAnalyzer.ROLE_FILTER_TERM, color.role);
        assertEquals("Filter by exact color value.", TemplateStructureAnalyzer.describe(color, null));

        // Range upper bound.
        TemplateStructureAnalyzer.Facts price = TemplateStructureAnalyzer.classify(located.get("price_max"), rendered);
        assertEquals(TemplateStructureAnalyzer.ROLE_RANGE_BOUND, price.role);
        assertEquals("lte", price.bound);
        assertEquals("Upper bound (<=) on price.", TemplateStructureAnalyzer.describe(price, null));

        // Result count, with and without a discovered default.
        TemplateStructureAnalyzer.Facts size = TemplateStructureAnalyzer.classify(located.get("size"), rendered);
        assertEquals(TemplateStructureAnalyzer.ROLE_RESULT_COUNT, size.role);
        assertEquals("Number of results to return.", TemplateStructureAnalyzer.describe(size, null));
        assertEquals("Number of results to return. Defaults to 10 if unset.", TemplateStructureAnalyzer.describe(size, 10L));

        // Field selector used as the sort key.
        TemplateStructureAnalyzer.Facts sortBy = TemplateStructureAnalyzer.classify(located.get("sort_by"), rendered);
        assertEquals(TemplateStructureAnalyzer.ROLE_SORT_FIELD, sortBy.role);
        assertEquals("Field to sort results by.", TemplateStructureAnalyzer.describe(sortBy, null));

        // Sort order: the sort field is a dynamic marker key, so no field is named.
        TemplateStructureAnalyzer.Facts sortOrder = TemplateStructureAnalyzer.classify(located.get("sort_order"), rendered);
        assertEquals(TemplateStructureAnalyzer.ROLE_SORT_ORDER, sortOrder.role);
        assertEquals("Sort direction.", TemplateStructureAnalyzer.describe(sortOrder, null));
        assertEquals(List.of("asc", "desc"), TemplateStructureAnalyzer.vocabEnum(sortOrder));
    }

    @Test
    public void classify_multiMatchWithMultipleFields() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("q", spec("string", true));
        TemplateStructureAnalyzer.MarkerSet markers = TemplateStructureAnalyzer.buildMarkers(schema);
        Map<String, Object> rp = markers.renderParams();
        Map<String, Object> rendered = map("query", map("multi_match", map("query", rp.get("q"), "fields", list("title", "body"))));

        TemplateStructureAnalyzer.Facts q = TemplateStructureAnalyzer
            .classify(TemplateStructureAnalyzer.locate(rendered, markers).get("q"), rendered);
        assertEquals(TemplateStructureAnalyzer.ROLE_FULL_TEXT, q.role);
        assertEquals("Full-text query matched against the title and body fields.", TemplateStructureAnalyzer.describe(q, null));
    }

    @Test
    public void classify_rangeLowerBound() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("min_price", spec("number", false));
        TemplateStructureAnalyzer.MarkerSet markers = TemplateStructureAnalyzer.buildMarkers(schema);
        Map<String, Object> rp = markers.renderParams();
        Map<String, Object> rendered = map("range", map("price", map("gte", rp.get("min_price"))));

        TemplateStructureAnalyzer.Facts f = TemplateStructureAnalyzer
            .classify(TemplateStructureAnalyzer.locate(rendered, markers).get("min_price"), rendered);
        assertEquals("gte", f.bound);
        assertEquals("Lower bound (>=) on price.", TemplateStructureAnalyzer.describe(f, null));
    }

    @Test
    public void classify_matchOperatorIsEnum() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("op", spec("string", false));
        TemplateStructureAnalyzer.MarkerSet markers = TemplateStructureAnalyzer.buildMarkers(schema);
        Map<String, Object> rp = markers.renderParams();
        Map<String, Object> rendered = map("match", map("title", map("query", "shoes", "operator", rp.get("op"))));

        TemplateStructureAnalyzer.Facts f = TemplateStructureAnalyzer
            .classify(TemplateStructureAnalyzer.locate(rendered, markers).get("op"), rendered);
        assertEquals(TemplateStructureAnalyzer.ROLE_MATCH_OPERATOR, f.role);
        assertEquals(List.of("and", "or"), TemplateStructureAnalyzer.vocabEnum(f));
    }

    @Test
    public void classify_unrecognizedClause_yieldsNoRoleAndNoDescription() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("x", spec("string", true));
        TemplateStructureAnalyzer.MarkerSet markers = TemplateStructureAnalyzer.buildMarkers(schema);
        Map<String, Object> rp = markers.renderParams();
        Map<String, Object> rendered = map("custom_clause", map("field", rp.get("x")));

        TemplateStructureAnalyzer.Facts f = TemplateStructureAnalyzer
            .classify(TemplateStructureAnalyzer.locate(rendered, markers).get("x"), rendered);
        assertNull(f.role);
        assertNull(TemplateStructureAnalyzer.describe(f, null));
        assertNull(TemplateStructureAnalyzer.vocabEnum(f));
    }

    @Test
    public void valueAt_navigatesNestedMapsAndArrays() {
        Map<String, Object> rendered = map("query", map("bool", map("filter", list(map("term", map("color", "red"))))));

        assertEquals("red", TemplateStructureAnalyzer.valueAt(rendered, list("query", "bool", "filter", 0, "term", "color")));
        assertNull(TemplateStructureAnalyzer.valueAt(rendered, list("query", "missing")));
        // A container node (not a scalar) resolves to null.
        assertNull(TemplateStructureAnalyzer.valueAt(rendered, list("query", "bool")));
    }

    @Test
    public void classify_multiMatchParameterizedFields_doesNotLeakMarker() {
        // When the multi_match fields themselves are params, their markers must not surface
        // as field names in the description.
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("q", spec("string", true));
        schema.put("f1", spec("string", false));
        TemplateStructureAnalyzer.MarkerSet markers = TemplateStructureAnalyzer.buildMarkers(schema);
        Map<String, Object> rp = markers.renderParams();
        Map<String, Object> rendered = map("query", map("multi_match", map("query", rp.get("q"), "fields", list(rp.get("f1")))));

        TemplateStructureAnalyzer.Facts q = TemplateStructureAnalyzer
            .classify(TemplateStructureAnalyzer.locate(rendered, markers).get("q"), rendered);
        assertEquals(TemplateStructureAnalyzer.ROLE_FULL_TEXT, q.role);
        assertEquals("Full-text query terms.", TemplateStructureAnalyzer.describe(q, null));
    }

    @Test
    public void classify_fromIsResultOffsetWithDefault() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("from", spec("number", false));
        TemplateStructureAnalyzer.MarkerSet markers = TemplateStructureAnalyzer.buildMarkers(schema);
        Map<String, Object> rendered = map("from", markers.renderParams().get("from"));

        TemplateStructureAnalyzer.Facts f = TemplateStructureAnalyzer
            .classify(TemplateStructureAnalyzer.locate(rendered, markers).get("from"), rendered);
        assertEquals(TemplateStructureAnalyzer.ROLE_RESULT_OFFSET, f.role);
        assertEquals("Offset of the first result to return. Defaults to 0 if unset.", TemplateStructureAnalyzer.describe(f, 0L));
    }

    @Test
    public void classify_matchPhrase() {
        checkSingle(
            "string",
            mk -> map("match_phrase", map("title", mk)),
            TemplateStructureAnalyzer.ROLE_PHRASE,
            "Exact phrase to match in the title field."
        );
    }

    @Test
    public void classify_termsIsFilterTerms() {
        checkSingle(
            "string",
            mk -> map("terms", map("tags", list(mk))),
            TemplateStructureAnalyzer.ROLE_FILTER_TERMS,
            "Filter by one or more tags values."
        );
    }

    @Test
    public void classify_prefixIsPattern() {
        checkSingle(
            "string",
            mk -> map("prefix", map("code", mk)),
            TemplateStructureAnalyzer.ROLE_PATTERN,
            "Pattern to match against the code field."
        );
    }

    @Test
    public void classify_fuzzyIsFuzzyMatch() {
        checkSingle(
            "string",
            mk -> map("fuzzy", map("name", mk)),
            TemplateStructureAnalyzer.ROLE_FUZZY,
            "Fuzzy match term for the name field."
        );
    }

    @Test
    public void classify_functionScoreBoost() {
        checkSingle(
            "number",
            mk -> map("function_score", map("boost", mk)),
            TemplateStructureAnalyzer.ROLE_BOOST,
            "Relevance boost weight for this clause."
        );
    }

    @Test
    public void classify_sortOrderWithStaticField_namesTheField() {
        checkSingle(
            "string",
            mk -> map("sort", list(map("price", map("order", mk)))),
            TemplateStructureAnalyzer.ROLE_SORT_ORDER,
            "Sort direction for the price sort."
        );
    }

    @Test
    public void classify_zeroTermsQueryIsEnum() {
        checkSingle(
            "string",
            mk -> map("match", map("t", map("query", "x", "zero_terms_query", mk))),
            TemplateStructureAnalyzer.ROLE_ZERO_TERMS,
            "Behavior when the analyzed query has no terms."
        );
    }

    @Test
    public void vocabEnum_closedVocabularyTables() {
        assertEquals(List.of("asc", "desc"), TemplateStructureAnalyzer.vocabEnum(roleFacts(TemplateStructureAnalyzer.ROLE_SORT_ORDER)));
        assertEquals(List.of("and", "or"), TemplateStructureAnalyzer.vocabEnum(roleFacts(TemplateStructureAnalyzer.ROLE_MATCH_OPERATOR)));
        assertEquals(List.of("none", "all"), TemplateStructureAnalyzer.vocabEnum(roleFacts(TemplateStructureAnalyzer.ROLE_ZERO_TERMS)));
        assertEquals(6, TemplateStructureAnalyzer.vocabEnum(roleFacts(TemplateStructureAnalyzer.ROLE_SCORE_MODE)).size());
        assertEquals(6, TemplateStructureAnalyzer.vocabEnum(roleFacts(TemplateStructureAnalyzer.ROLE_BOOST_MODE)).size());
        assertNull(TemplateStructureAnalyzer.vocabEnum(roleFacts(TemplateStructureAnalyzer.ROLE_FILTER_TERM)));
    }

    @Test
    public void describeTemplate_summarizesCapabilitiesGroupedByClause() {
        Map<String, Object> schema = productSchema();
        TemplateStructureAnalyzer.MarkerSet markers = TemplateStructureAnalyzer.buildMarkers(schema);
        Map<String, Object> rendered = productRendered(markers.renderParams());

        assertEquals(
            "Full-text search over title; filters by color; range filters on price; sortable.",
            TemplateStructureAnalyzer.describeTemplate(schema, markers, rendered)
        );
    }

    @Test
    public void describeTemplate_noRecognizedRole_returnsNull() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("x", spec("string", true));
        TemplateStructureAnalyzer.MarkerSet markers = TemplateStructureAnalyzer.buildMarkers(schema);
        Map<String, Object> rendered = map("custom_clause", map("field", markers.renderParams().get("x")));

        assertNull(TemplateStructureAnalyzer.describeTemplate(schema, markers, rendered));
    }

    /** Builds a one-param schema, renders it via {@code renderWith}, then classifies and describes it. */
    private static void checkSingle(
        String type,
        Function<Object, Map<String, Object>> renderWith,
        String expectedRole,
        String expectedDesc
    ) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("p", spec(type, false));
        TemplateStructureAnalyzer.MarkerSet markers = TemplateStructureAnalyzer.buildMarkers(schema);
        Map<String, Object> rendered = renderWith.apply(markers.renderParams().get("p"));
        TemplateStructureAnalyzer.Located loc = TemplateStructureAnalyzer.locate(rendered, markers).get("p");
        TemplateStructureAnalyzer.Facts facts = TemplateStructureAnalyzer.classify(loc, rendered);
        assertEquals(expectedRole, facts.role);
        if (expectedDesc != null) {
            assertEquals(expectedDesc, TemplateStructureAnalyzer.describe(facts, null));
        }
    }

    private static TemplateStructureAnalyzer.Facts roleFacts(String role) {
        return new TemplateStructureAnalyzer.Facts(role, List.of(), null);
    }
}
