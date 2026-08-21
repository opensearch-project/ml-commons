/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.agenticsearch;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Recovers each param's role from where it lands in the rendered query, so the derived
 * schema can carry a description and a fixed-value enum instead of a bare name.
 *
 * <p>{@link MustacheTemplateAnalyzer} sees only the {@code {{tags}}} and cannot tell what
 * a param is used for. This analyzer derives the role without an LLM: each param is assigned
 * a unique marker value ({@link #buildMarkers}), the cluster renders the body, and each
 * marker is located in the parsed JSON ({@link #locate}). A marker's key-path names the
 * enclosing clause and the field it targets, so {@code {{query}}} landing in
 * {@code match.title} becomes "Full-text query matched against the title field"
 * ({@link #classify}, {@link #describe}), and a slot with a closed OpenSearch vocabulary
 * (a sort order, a match operator) becomes an {@code enum} ({@link #vocabEnum}).
 *
 * <p>The clause table below encodes OpenSearch DSL grammar and is shared by every template
 * rather than being per-template logic. A param whose marker cannot be located, or whose
 * clause is not in the table, yields no role and keeps the base derivation, so an
 * unrecognized body falls back to the base schema rather than failing. The class is pure
 * (no cluster I/O) so the caller owns rendering and the tables are unit-testable on
 * hand-built JSON.
 */
public final class TemplateStructureAnalyzer {

    // Marker tokens are valid JSON strings unlikely to occur in a real template body. A
    // string param renders its token into a quoted slot; a number param renders a large
    // distinct integer into an unquoted slot; both stay locatable after a round-trip render.
    static final String MARKER_PREFIX = "__mk_sentinel_";
    static final String MARKER_SUFFIX = "__";
    static final long MARKER_NUMBER_BASE = 990_000_000L;

    // Recovered role for a param, keyed off its rendered clause.
    static final String ROLE_FULL_TEXT = "full_text_query";
    static final String ROLE_PHRASE = "phrase";
    static final String ROLE_FILTER_TERM = "filter_term";
    static final String ROLE_FILTER_TERMS = "filter_terms";
    static final String ROLE_PATTERN = "pattern_match";
    static final String ROLE_FUZZY = "fuzzy_match";
    static final String ROLE_RANGE_BOUND = "range_bound";
    static final String ROLE_SORT_ORDER = "sort_order";
    static final String ROLE_SORT_FIELD = "sort_field";
    static final String ROLE_FIELD_SELECTOR = "field_selector";
    static final String ROLE_RESULT_COUNT = "result_count";
    static final String ROLE_RESULT_OFFSET = "result_offset";
    static final String ROLE_MATCH_OPERATOR = "match_operator";
    static final String ROLE_ZERO_TERMS = "zero_terms";
    static final String ROLE_SCORE_MODE = "score_mode";
    static final String ROLE_BOOST_MODE = "boost_mode";
    static final String ROLE_BOOST = "boost";

    private TemplateStructureAnalyzer() {}

    /** Marker values to render with, plus the reverse lookups {@link #locate} needs. */
    static final class MarkerSet {
        // param -> value to substitute when rendering the body.
        private final Map<String, Object> renderParams = new LinkedHashMap<>();
        // marker string -> param, for a param rendered into a quoted slot or used as a key.
        private final Map<String, String> stringMarkers = new LinkedHashMap<>();
        // marker integer -> param, for a param rendered into an unquoted numeric slot.
        private final Map<Long, String> numberMarkers = new LinkedHashMap<>();

        Map<String, Object> renderParams() {
            return renderParams;
        }
    }

    /** Where a param's marker was found in the rendered JSON. */
    static final class Located {
        final List<Object> path; // object keys (String) and array indices (Integer) from the root
        final boolean asKey;     // the marker was a JSON object key (a field-selector param) not a value

        Located(List<Object> path, boolean asKey) {
            this.path = path;
            this.asKey = asKey;
        }

        /** A path with no array index is stable across renders, so a default can be read at it. */
        boolean isStablePath() {
            for (Object step : path) {
                if (step instanceof Integer) {
                    return false;
                }
            }
            return true;
        }
    }

    /** A param's recovered clause role and the field(s) it targets. */
    static final class Facts {
        final String role;
        final List<String> fields; // target field name(s); may be empty when not recoverable
        final String bound;        // range bound key (gte/gt/lte/lt) for ROLE_RANGE_BOUND, else null

        Facts(String role, List<String> fields, String bound) {
            this.role = role;
            this.fields = fields;
            this.bound = bound;
        }
    }

    /**
     * Build a unique marker value per param, typed so the rendered body stays legal JSON:
     * a string param renders a token into its quoted slot, a number param a large distinct
     * integer, a boolean {@code true} (so its section renders), and an array an empty array
     * (kept legal but not located, since a raw-JSON slot has no single field to describe).
     */
    static MarkerSet buildMarkers(Map<String, Object> schema) {
        MarkerSet markers = new MarkerSet();
        int index = 0;
        for (Map.Entry<String, Object> entry : schema.entrySet()) {
            String param = entry.getKey();
            String type = typeOf(entry.getValue());
            switch (type) {
                case MustacheTemplateAnalyzer.TYPE_NUMBER: {
                    long token = MARKER_NUMBER_BASE + index;
                    markers.numberMarkers.put(token, param);
                    markers.renderParams.put(param, token);
                    break;
                }
                case MustacheTemplateAnalyzer.TYPE_BOOLEAN:
                    // A guard toggle: render true so its section opens and inner params render.
                    markers.renderParams.put(param, Boolean.TRUE);
                    break;
                case MustacheTemplateAnalyzer.TYPE_ARRAY:
                    // A triple-stache raw-JSON slot: an empty array renders legally (matching
                    // pre-flight) but carries no single field, so it is not tracked for a role.
                    markers.renderParams.put(param, "[]");
                    break;
                default: {
                    String token = MARKER_PREFIX + index + MARKER_SUFFIX;
                    markers.stringMarkers.put(token, param);
                    markers.renderParams.put(param, token);
                    break;
                }
            }
            index++;
        }
        return markers;
    }

    /**
     * Walk the rendered JSON and record where each param's marker landed. A marker found as
     * an object key marks a field-selector param; found as a scalar value it marks the slot
     * that param fills. The first occurrence wins.
     */
    static Map<String, Located> locate(Object renderedRoot, MarkerSet markers) {
        Map<String, Located> found = new LinkedHashMap<>();
        walk(renderedRoot, new ArrayList<>(), markers, found);
        return found;
    }

    private static void walk(Object node, List<Object> path, MarkerSet markers, Map<String, Located> found) {
        if (node instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) node;
            for (Map.Entry<String, Object> entry : map.entrySet()) {
                String key = entry.getKey();
                String keyParam = markers.stringMarkers.get(key);
                List<Object> childPath = append(path, key);
                if (keyParam != null) {
                    found.putIfAbsent(keyParam, new Located(childPath, true));
                }
                walk(entry.getValue(), childPath, markers, found);
            }
        } else if (node instanceof List) {
            List<?> list = (List<?>) node;
            for (int i = 0; i < list.size(); i++) {
                walk(list.get(i), append(path, i), markers, found);
            }
        } else if (node instanceof String) {
            String param = markers.stringMarkers.get(node);
            if (param != null) {
                found.putIfAbsent(param, new Located(path, false));
            }
        } else if (node instanceof Number) {
            String param = markers.numberMarkers.get(((Number) node).longValue());
            if (param != null) {
                found.putIfAbsent(param, new Located(path, false));
            }
        }
    }

    /**
     * Map a located marker to its clause role and target field(s) by matching the tail of its
     * key-path against the DSL clause table. Array indices are ignored: only the object keys
     * carry clause meaning.
     */
    static Facts classify(Located located, Object renderedRoot) {
        List<String> keys = keysOnly(located.path);
        int n = keys.size();

        if (located.asKey) {
            // The param supplies a field name (e.g. the sort key), not a value.
            String role = keys.contains("sort") ? ROLE_SORT_FIELD : ROLE_FIELD_SELECTOR;
            return new Facts(role, List.of(), null);
        }

        String last = n >= 1 ? keys.get(n - 1) : null;
        String prev = n >= 2 ? keys.get(n - 2) : null;
        String prev2 = n >= 3 ? keys.get(n - 3) : null;

        // Top-level paging controls.
        if (n == 1) {
            if ("size".equals(last)) {
                return new Facts(ROLE_RESULT_COUNT, List.of(), null);
            }
            if ("from".equals(last)) {
                return new Facts(ROLE_RESULT_OFFSET, List.of(), null);
            }
        }

        // range.<field>.<bound>
        if (isRangeBound(last) && "range".equals(prev2)) {
            return new Facts(ROLE_RANGE_BOUND, fieldList(prev), last);
        }

        // sort[].<field>.order (field may be a literal name or a field-selector marker)
        if ("order".equals(last) && keys.contains("sort")) {
            return new Facts(ROLE_SORT_ORDER, fieldList(prev), null);
        }

        // Closed-vocabulary option slots.
        if ("operator".equals(last)) {
            return new Facts(ROLE_MATCH_OPERATOR, List.of(), null);
        }
        if ("zero_terms_query".equals(last)) {
            return new Facts(ROLE_ZERO_TERMS, List.of(), null);
        }
        if ("score_mode".equals(last)) {
            return new Facts(ROLE_SCORE_MODE, List.of(), null);
        }
        if ("boost_mode".equals(last)) {
            return new Facts(ROLE_BOOST_MODE, List.of(), null);
        }
        if ("boost".equals(last)) {
            return new Facts(ROLE_BOOST, List.of(), null);
        }

        // multi_match: the query slot names its fields in a sibling array.
        if ("query".equals(last) && "multi_match".equals(prev)) {
            return new Facts(ROLE_FULL_TEXT, multiMatchFields(located.path, renderedRoot), null);
        }
        // A match clause with an options sub-object: {"match":{"<field>":{"query":...}}}.
        if ("query".equals(last) && isFullTextClause(prev2)) {
            return new Facts(fullTextRole(prev2), fieldList(prev), null);
        }

        // <clause>.<field> for the single-field query and filter clauses.
        if (isFullTextClause(prev)) {
            return new Facts(fullTextRole(prev), fieldList(last), null);
        }
        if ("term".equals(prev)) {
            return new Facts(ROLE_FILTER_TERM, fieldList(last), null);
        }
        if ("terms".equals(prev)) {
            return new Facts(ROLE_FILTER_TERMS, fieldList(last), null);
        }
        if ("prefix".equals(prev) || "wildcard".equals(prev) || "regexp".equals(prev)) {
            return new Facts(ROLE_PATTERN, fieldList(last), null);
        }
        if ("fuzzy".equals(prev)) {
            return new Facts(ROLE_FUZZY, fieldList(last), null);
        }

        return new Facts(null, List.of(), null); // unrecognized clause -> no role, keeps base schema
    }

    /** The closed vocabulary for an enum-valued slot, or null when the role has no fixed set. */
    static List<String> vocabEnum(Facts facts) {
        if (facts.role == null) {
            return null;
        }
        switch (facts.role) {
            case ROLE_SORT_ORDER:
                return List.of("asc", "desc");
            case ROLE_MATCH_OPERATOR:
                return List.of("and", "or");
            case ROLE_ZERO_TERMS:
                return List.of("none", "all");
            case ROLE_SCORE_MODE:
                return List.of("multiply", "sum", "avg", "first", "max", "min");
            case ROLE_BOOST_MODE:
                return List.of("multiply", "replace", "sum", "avg", "max", "min");
            default:
                return null;
        }
    }

    /**
     * Compose a one-clause description from a param's role and target field(s), appending the
     * effective default when one was found. Returns null for an unrecognized role so the base
     * (empty) description stands.
     */
    static String describe(Facts facts, Object defaultValue) {
        if (facts == null || facts.role == null) {
            return null;
        }
        String field = singleField(facts.fields);
        String base;
        switch (facts.role) {
            case ROLE_FULL_TEXT:
                base = facts.fields.isEmpty()
                    ? "Full-text query terms."
                    : "Full-text query matched against the " + joinFields(facts.fields) + fieldWord(facts.fields) + ".";
                break;
            case ROLE_PHRASE:
                base = field == null ? "Exact phrase to match." : "Exact phrase to match in the " + field + " field.";
                break;
            case ROLE_FILTER_TERM:
                base = field == null ? "Exact-match filter value." : "Filter by exact " + field + " value.";
                break;
            case ROLE_FILTER_TERMS:
                base = field == null ? "One or more exact-match filter values." : "Filter by one or more " + field + " values.";
                break;
            case ROLE_PATTERN:
                base = field == null ? "Pattern to match." : "Pattern to match against the " + field + " field.";
                break;
            case ROLE_FUZZY:
                base = field == null ? "Fuzzy match term." : "Fuzzy match term for the " + field + " field.";
                break;
            case ROLE_RANGE_BOUND:
                base = rangeSentence(facts.bound, field);
                break;
            case ROLE_SORT_ORDER:
                base = field == null ? "Sort direction." : "Sort direction for the " + field + " sort.";
                break;
            case ROLE_SORT_FIELD:
                base = "Field to sort results by.";
                break;
            case ROLE_FIELD_SELECTOR:
                base = "Field name to target.";
                break;
            case ROLE_RESULT_COUNT:
                base = "Number of results to return.";
                break;
            case ROLE_RESULT_OFFSET:
                base = "Offset of the first result to return.";
                break;
            case ROLE_MATCH_OPERATOR:
                base = "Whether all query terms must match (and) or any may match (or).";
                break;
            case ROLE_ZERO_TERMS:
                base = "Behavior when the analyzed query has no terms.";
                break;
            case ROLE_SCORE_MODE:
                base = "How to combine the scores of matching clauses.";
                break;
            case ROLE_BOOST_MODE:
                base = "How to combine the query score with the function score.";
                break;
            case ROLE_BOOST:
                base = "Relevance boost weight for this clause.";
                break;
            default:
                return null;
        }
        if (defaultValue != null) {
            base = base + " Defaults to " + renderDefault(defaultValue) + " if unset.";
        }
        return base;
    }

    /**
     * Assemble a one-line, template-level description from the params' recovered roles, for
     * multi-template selection. Capabilities are grouped by clause (full-text fields, filters,
     * ranges, sort, paging) and joined with semicolons into a fixed set of fragments. Returns
     * null when no role is recovered, so an unrecognized body leaves the description unset
     * rather than carrying an incorrect one.
     */
    static String describeTemplate(Map<String, Object> schema, MarkerSet markers, Object renderedRoot) {
        Map<String, Located> located = locate(renderedRoot, markers);
        Set<String> fullText = new LinkedHashSet<>();
        Set<String> pattern = new LinkedHashSet<>();
        Set<String> fuzzy = new LinkedHashSet<>();
        Set<String> filters = new LinkedHashSet<>();
        Set<String> ranges = new LinkedHashSet<>();
        boolean fullTextNoField = false;
        boolean sortable = false;
        boolean paged = false;

        for (String param : schema.keySet()) {
            Located loc = located.get(param);
            if (loc == null) {
                continue;
            }
            Facts facts = classify(loc, renderedRoot);
            if (facts.role == null) {
                continue;
            }
            switch (facts.role) {
                case ROLE_FULL_TEXT:
                case ROLE_PHRASE:
                    if (facts.fields.isEmpty()) {
                        fullTextNoField = true;
                    } else {
                        fullText.addAll(facts.fields);
                    }
                    break;
                case ROLE_PATTERN:
                    pattern.addAll(facts.fields);
                    break;
                case ROLE_FUZZY:
                    fuzzy.addAll(facts.fields);
                    break;
                case ROLE_FILTER_TERM:
                case ROLE_FILTER_TERMS:
                    filters.addAll(facts.fields);
                    break;
                case ROLE_RANGE_BOUND:
                    ranges.addAll(facts.fields);
                    break;
                case ROLE_SORT_ORDER:
                case ROLE_SORT_FIELD:
                    sortable = true;
                    break;
                case ROLE_RESULT_OFFSET:
                    paged = true;
                    break;
                default:
                    break;
            }
        }

        List<String> clauses = new ArrayList<>();
        if (!fullText.isEmpty()) {
            clauses.add("full-text search over " + joinFields(new ArrayList<>(fullText)));
        } else if (fullTextNoField) {
            clauses.add("full-text search");
        }
        if (!pattern.isEmpty()) {
            clauses.add("pattern matching on " + joinFields(new ArrayList<>(pattern)));
        }
        if (!fuzzy.isEmpty()) {
            clauses.add("fuzzy matching on " + joinFields(new ArrayList<>(fuzzy)));
        }
        if (!filters.isEmpty()) {
            clauses.add("filters by " + joinFields(new ArrayList<>(filters)));
        }
        if (!ranges.isEmpty()) {
            clauses.add("range filters on " + joinFields(new ArrayList<>(ranges)));
        }
        if (sortable) {
            clauses.add("sortable");
        }
        if (paged) {
            clauses.add("paginated");
        }
        if (clauses.isEmpty()) {
            return null;
        }
        String joined = String.join("; ", clauses);
        return Character.toUpperCase(joined.charAt(0)) + joined.substring(1) + ".";
    }

    /** Resolve a scalar value at a key-path in a parsed JSON tree, or null if absent. */
    static Object valueAt(Object root, List<Object> path) {
        Object node = root;
        for (Object step : path) {
            if (step instanceof String && node instanceof Map) {
                node = ((Map<?, ?>) node).get(step);
            } else if (step instanceof Integer && node instanceof List) {
                List<?> list = (List<?>) node;
                int i = (Integer) step;
                node = i >= 0 && i < list.size() ? list.get(i) : null;
            } else {
                return null;
            }
            if (node == null) {
                return null;
            }
        }
        return node instanceof Map || node instanceof List ? null : node;
    }

    // ---- internals ---------------------------------------------------------

    private static boolean isRangeBound(String key) {
        return "gte".equals(key) || "gt".equals(key) || "lte".equals(key) || "lt".equals(key);
    }

    private static boolean isFullTextClause(String clause) {
        return "match".equals(clause)
            || "match_bool_prefix".equals(clause)
            || "match_phrase".equals(clause)
            || "match_phrase_prefix".equals(clause);
    }

    private static String fullTextRole(String clause) {
        return ("match_phrase".equals(clause) || "match_phrase_prefix".equals(clause)) ? ROLE_PHRASE : ROLE_FULL_TEXT;
    }

    /** A field name, unless it is a field-selector marker (a dynamic key), in which case none. */
    private static List<String> fieldList(String name) {
        if (name == null || name.startsWith(MARKER_PREFIX)) {
            return List.of();
        }
        return List.of(stripBoost(name));
    }

    /** Read a multi_match clause's declared fields (a sibling array of the located query slot). */
    private static List<String> multiMatchFields(List<Object> queryPath, Object root) {
        List<Object> clausePath = queryPath.subList(0, queryPath.size() - 1);
        Object clause = navigate(root, clausePath);
        if (!(clause instanceof Map)) {
            return List.of();
        }
        Object fields = ((Map<?, ?>) clause).get("fields");
        if (!(fields instanceof List)) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (Object f : (List<?>) fields) {
            // Skip a field that is itself a marker: a template whose multi_match fields are
            // parameterized ("fields": ["{{f1}}"]) renders the marker there, and it must not
            // leak into the description as a field name.
            if (f instanceof String && !((String) f).startsWith(MARKER_PREFIX)) {
                out.add(stripBoost((String) f));
            }
        }
        return out;
    }

    /** Navigate to a (possibly container) node at a key-path, for reading a clause's siblings. */
    private static Object navigate(Object root, List<Object> path) {
        Object node = root;
        for (Object step : path) {
            if (step instanceof String && node instanceof Map) {
                node = ((Map<?, ?>) node).get(step);
            } else if (step instanceof Integer && node instanceof List) {
                List<?> list = (List<?>) node;
                int i = (Integer) step;
                node = i >= 0 && i < list.size() ? list.get(i) : null;
            } else {
                return null;
            }
            if (node == null) {
                return null;
            }
        }
        return node;
    }

    /** Drop a field's boost suffix ({@code title^2} -> {@code title}) from the description. */
    private static String stripBoost(String field) {
        int caret = field.indexOf('^');
        return caret >= 0 ? field.substring(0, caret) : field;
    }

    private static List<String> keysOnly(List<Object> path) {
        List<String> keys = new ArrayList<>();
        for (Object step : path) {
            if (step instanceof String) {
                keys.add((String) step);
            }
        }
        return keys;
    }

    private static String singleField(List<String> fields) {
        return fields.size() == 1 ? fields.get(0) : null;
    }

    private static String joinFields(List<String> fields) {
        if (fields.size() == 1) {
            return fields.get(0);
        }
        if (fields.size() == 2) {
            return fields.get(0) + " and " + fields.get(1);
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < fields.size(); i++) {
            if (i == fields.size() - 1) {
                sb.append("and ").append(fields.get(i));
            } else {
                sb.append(fields.get(i)).append(", ");
            }
        }
        return sb.toString();
    }

    private static String fieldWord(List<String> fields) {
        return fields.size() == 1 ? " field" : " fields";
    }

    private static String rangeSentence(String bound, String field) {
        String target = field == null ? "this field" : field;
        switch (bound == null ? "" : bound.toLowerCase(Locale.ROOT)) {
            case "gte":
                return "Lower bound (>=) on " + target + ".";
            case "gt":
                return "Lower bound (>) on " + target + ".";
            case "lte":
                return "Upper bound (<=) on " + target + ".";
            case "lt":
                return "Upper bound (<) on " + target + ".";
            default:
                return "Range bound on " + target + ".";
        }
    }

    private static String renderDefault(Object value) {
        if (value instanceof String) {
            return "\"" + value + "\"";
        }
        return String.valueOf(value);
    }

    private static String typeOf(Object spec) {
        if (spec instanceof Map) {
            Object type = ((Map<?, ?>) spec).get(MustacheTemplateAnalyzer.TYPE_KEY);
            if (type instanceof String) {
                return (String) type;
            }
        }
        return MustacheTemplateAnalyzer.TYPE_STRING;
    }

    private static List<Object> append(List<Object> path, Object step) {
        List<Object> next = new ArrayList<>(path.size() + 1);
        next.addAll(path);
        next.add(step);
        return next;
    }
}
