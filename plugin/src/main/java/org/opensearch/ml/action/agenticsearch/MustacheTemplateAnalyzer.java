/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.agenticsearch;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;

import org.opensearch.ml.common.agenticsearch.AgenticSearchTemplate;

/**
 * Derives a template's parameter structure from its Mustache body (design D6 / the
 * Appendix rule table). A single scope-aware pass over the body's {@code {{...}}}
 * tags yields, per parameter, a structural type and whether it is required.
 *
 * <p>This is deliberately a focused tag scanner, not a full Mustache engine: it
 * pairs sections, tracks nesting/scope, and distinguishes {@code {{{triple}}}} from
 * {@code {{escaped}}}, {@code {{#section}}}, {@code {{^inverted}}}, {@code
 * {{!comments}}}, and delimiter changes ({@code {{=<% %>=}}}). It cannot recover
 * string-vs-number (Mustache is untyped about the surrounding JSON), so a scalar is
 * inferred as string when it sits in a quoted position and number otherwise — a
 * sound heuristic the customer can override, and the render-parse pre-flight
 * backstops any miss. The rule table:
 *
 * <table>
 *   <caption>Mustache tag to derived param type and required-ness</caption>
 *   <tr><td>bare {@code {{x}}} at root</td><td>required scalar</td></tr>
 *   <tr><td>{@code {{#x}}..{{/x}}} where x is never a plain value</td><td>boolean (section guard)</td></tr>
 *   <tr><td>{@code {{x}}{{^x}}default{{/x}}}</td><td>optional, with default</td></tr>
 *   <tr><td>{@code {{{x}}}} (triple)</td><td>unescaped scalar (often an array/object injected as JSON)</td></tr>
 * </table>
 *
 * The output is the {@code param_schema} map the customer then enriches (value
 * enums, descriptions) and the field-name enums the index mapping supplies.
 */
public class MustacheTemplateAnalyzer {

    // param-schema entry keys, shared with AgenticSearchTemplateService.
    static final String TYPE_KEY = "type";
    static final String REQUIRED_KEY = "required";
    static final String DESCRIPTION_KEY = "description";
    static final String ENUM_KEY = "enum";
    static final String SOURCE_KEY = "source";

    // Structural types we can infer from the body alone.
    static final String TYPE_STRING = "string";
    static final String TYPE_NUMBER = "number";
    static final String TYPE_BOOLEAN = "boolean";
    static final String TYPE_ARRAY = "array";

    // Value of SOURCE_KEY for an enum derived from the index mapping.
    static final String SOURCE_MAPPING = "mapping";

    private MustacheTemplateAnalyzer() {}

    /** Per-parameter facts accumulated while scanning; folded into a schema at the end. */
    private static final class ParamFacts {
        boolean usedAsSection;      // appeared as {{#x}} or {{^x}} (a guard/default -> optional)
        boolean usedAsValue;        // appeared as {{x}} or {{{x}}} (a substituted value)
        boolean usedAsValueAtRoot;  // a value use OUTSIDE any section -> unconditionally rendered
        boolean triple;             // appeared as {{{x}}} -> unescaped (JSON array/object)
        boolean quotedScalar;       // a {{x}} that sat inside "..." -> string, else number
    }

    /**
     * Analyze a Mustache body and return the derived {@code param_schema} map
     * (ordered by first appearance), suitable for {@link AgenticSearchTemplate}.
     *
     * @throws IllegalArgumentException if the body has an unbalanced section.
     */
    public static Map<String, Object> derive(String body) {
        if (body == null || body.isEmpty()) {
            throw new IllegalArgumentException("Template body is empty");
        }

        Map<String, ParamFacts> facts = new LinkedHashMap<>();
        Deque<String> openSections = new ArrayDeque<>();

        String open = "{{";
        String close = "}}";
        int i = 0;
        int n = body.length();
        while (i < n) {
            int start = body.indexOf(open, i);
            if (start < 0) {
                break;
            }
            int end = body.indexOf(close, start + open.length());
            if (end < 0) {
                break; // trailing unclosed tag — ignore
            }

            // A triple-stache {{{x}}} is an open of "{{" immediately followed by "{".
            boolean triple = "{{".equals(open) && start + 2 < n && body.charAt(start + 2) == '{';
            int contentStart = start + open.length() + (triple ? 1 : 0);
            int contentEnd = end;
            String raw = body.substring(contentStart, contentEnd).trim();
            int afterTag = end + close.length();
            if (triple && afterTag < n && body.charAt(afterTag) == '}') {
                afterTag += 1; // consume the extra closing brace of }}}
            }

            if (raw.isEmpty()) {
                i = afterTag;
                continue;
            }

            char sigil = raw.charAt(0);
            switch (sigil) {
                case '!': // comment
                    break;
                case '=': // delimiter change, e.g. {{=<% %>=}}
                    String spec = raw.substring(1, raw.endsWith("=") ? raw.length() - 1 : raw.length()).trim();
                    String[] delims = spec.split("\\s+");
                    if (delims.length == 2 && !delims[0].isEmpty() && !delims[1].isEmpty()) {
                        open = delims[0];
                        close = delims[1];
                    }
                    break;
                case '#': // section open
                case '^': { // inverted section open
                    String name = raw.substring(1).trim();
                    openSections.push(name);
                    facts.computeIfAbsent(name, k -> new ParamFacts()).usedAsSection = true;
                    break;
                }
                case '/': { // section close
                    String name = raw.substring(1).trim();
                    if (openSections.isEmpty() || !openSections.peek().equals(name)) {
                        throw new IllegalArgumentException("Unbalanced Mustache section near '" + name + "'");
                    }
                    openSections.pop();
                    break;
                }
                case '>': // partial — not a fillable param
                case '&': { // {{&x}} unescaped, same as triple
                    if (sigil == '&') {
                        recordValue(facts, raw.substring(1).trim(), true, isQuotedPosition(body, start), openSections.isEmpty());
                    }
                    break;
                }
                default: { // a plain value {{x}} or {{{x}}}
                    recordValue(facts, raw, triple, isQuotedPosition(body, start), openSections.isEmpty());
                    break;
                }
            }
            i = afterTag;
        }

        if (!openSections.isEmpty()) {
            throw new IllegalArgumentException("Unclosed Mustache section '" + openSections.peek() + "'");
        }

        return toSchema(facts);
    }

    private static void recordValue(Map<String, ParamFacts> facts, String name, boolean triple, boolean quoted, boolean atRoot) {
        if (name.isEmpty() || ".".equals(name)) {
            return; // implicit iterator / empty
        }
        ParamFacts f = facts.computeIfAbsent(name, k -> new ParamFacts());
        f.usedAsValue = true;
        if (atRoot) {
            f.usedAsValueAtRoot = true;
        }
        if (triple) {
            f.triple = true;
        }
        if (quoted) {
            f.quotedScalar = true;
        }
    }

    /**
     * Heuristic: does the tag at {@code tagStart} sit inside a JSON string literal?
     * Walk the current line counting quote toggles; an odd count before the tag means
     * it sits inside a string (so the scalar is a string, otherwise a number).
     *
     * <p>A quote is a real delimiter only when it is not escaped, and it is escaped
     * only when preceded by an <em>odd</em> run of backslashes — {@code \"} is escaped,
     * but {@code \\"} is a literal backslash followed by a closing quote. Counting the
     * run (rather than testing the single previous char) is what tells those apart.
     * The scan resets at each newline: JSON string literals don't span raw newlines, so
     * a stray/miscounted quote stays contained to its line instead of flipping the
     * classification of every later parameter.
     */
    private static boolean isQuotedPosition(String body, int tagStart) {
        boolean inQuote = false;
        for (int j = 0; j < tagStart; j++) {
            char c = body.charAt(j);
            if (c == '\n') {
                inQuote = false; // strings don't span raw newlines; contain any miscount
            } else if (c == '"' && !isEscaped(body, j)) {
                inQuote = !inQuote;
            }
        }
        return inQuote;
    }

    /** True if the char at {@code pos} is escaped, i.e. preceded by an odd run of backslashes. */
    private static boolean isEscaped(String body, int pos) {
        int backslashes = 0;
        for (int k = pos - 1; k >= 0 && body.charAt(k) == '\\'; k--) {
            backslashes++;
        }
        return (backslashes & 1) == 1;
    }

    private static Map<String, Object> toSchema(Map<String, ParamFacts> facts) {
        Map<String, Object> schema = new LinkedHashMap<>();
        for (Map.Entry<String, ParamFacts> e : facts.entrySet()) {
            ParamFacts f = e.getValue();
            Map<String, Object> spec = new LinkedHashMap<>();

            // A name used ONLY as a section guard (never substituted as a value) is a
            // boolean flag. A name used as both a section and a value is a scalar with
            // an inverted-section default (optional). A triple-stache is an injected
            // JSON array/object.
            if (f.triple) {
                spec.put(TYPE_KEY, TYPE_ARRAY);
            } else if (f.usedAsSection && !f.usedAsValue) {
                spec.put(TYPE_KEY, TYPE_BOOLEAN);
            } else if (f.quotedScalar) {
                spec.put(TYPE_KEY, TYPE_STRING);
            } else {
                spec.put(TYPE_KEY, TYPE_NUMBER);
            }

            // Required only for a bare value at ROOT scope that is never wrapped in its
            // own section guard (the Appendix rule). A param used only inside a section
            // ({{#other}}..{{x}}..{{/other}}) renders conditionally, and one wrapped in
            // its own {{#x}}/{{^x}} disappears when absent — both are optional, so the
            // body still renders a legal query without them.
            boolean required = f.usedAsValueAtRoot && !f.usedAsSection;
            spec.put(REQUIRED_KEY, required);
            spec.put(DESCRIPTION_KEY, "");

            schema.put(e.getKey(), spec);
        }
        return schema;
    }
}
