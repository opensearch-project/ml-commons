/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.agenticsearch;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
 *   <tr><td>{@code {{{x}}}} or {@code {{&x}}} in an unquoted slot</td><td>array (raw JSON injected as-is)</td></tr>
 *   <tr><td>{@code {{{x}}}} or {@code {{&x}}} inside {@code "..."}</td><td>string (unescaped)</td></tr>
 * </table>
 *
 * <p>Some legal bodies cannot be typed from the body alone: list iteration
 * ({@code {{#xs}}{{.}}{{/xs}}}), the {@code lang-mustache} {@code toJson}/{@code join}
 * built-ins, and a malformed delimiter change. {@link #derive} rejects these and asks the
 * caller to supply {@code param_schema}; {@link #paramNames} still reports the names such
 * a body references, so a supplied schema can be checked against it.
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

    /**
     * {@code lang-mustache} built-in section functions ({@code CustomMustacheFactory}). Their
     * section name is a function, not a param: {@code {{#toJson}}brands{{/toJson}}} renders the
     * param named by the literal inner text. {@code url} wraps an ordinary sub-template.
     */
    private static final String FN_TO_JSON = "toJson";
    private static final String FN_JOIN = "join";
    private static final String FN_JOIN_DELIMITER_PREFIX = "join delimiter=";
    private static final String FN_URL = "url";

    /** Per-parameter facts accumulated while scanning; folded into a schema at the end. */
    private static final class ParamFacts {
        boolean usedAsSection;      // appeared as {{#x}} or {{^x}} (a guard/default -> optional)
        boolean usedAsValue;        // appeared as {{x}} or {{{x}}} (a substituted value)
        boolean usedAsValueAtRoot;  // a value use OUTSIDE any section -> unconditionally rendered
        boolean tripleUnquoted;     // a {{{x}}}/{{&x}} OUTSIDE "..." -> raw JSON array/object
        boolean quotedScalar;       // a value that sat inside "..." -> string, else number
    }

    /** An open section on the scan stack. */
    private static final class OpenSection {
        final String name;
        final boolean inverted;     // {{^x}} rather than {{#x}}
        final boolean function;     // a lang-mustache built-in (toJson/join/url), not a param
        final int contentStart;     // body offset just past the open tag

        OpenSection(String name, boolean inverted, boolean function, int contentStart) {
            this.name = name;
            this.inverted = inverted;
            this.function = function;
            this.contentStart = contentStart;
        }
    }

    /** What one pass over the body found: per-param facts plus anything that defeats derivation. */
    private static final class Scan {
        final Map<String, ParamFacts> facts = new LinkedHashMap<>();
        // Params only reachable through a toJson/join built-in: named, but not typeable.
        final Set<String> functionParams = new LinkedHashSet<>();
        // Constructs that make a derived schema wrong; derive() rejects on any of them.
        final List<String> issues = new ArrayList<>();
    }

    /**
     * Analyze a Mustache body and return the derived {@code param_schema} map
     * (ordered by first appearance), suitable for {@link AgenticSearchTemplate}.
     *
     * @throws IllegalArgumentException if the body has an unbalanced section, or uses a
     *     construct whose params cannot be derived (list iteration, a {@code toJson}/{@code
     *     join} built-in, a malformed delimiter change); the caller must then supply
     *     {@code param_schema} explicitly.
     */
    public static Map<String, Object> derive(String body) {
        Scan scan = scan(body);
        if (!scan.issues.isEmpty()) {
            throw new IllegalArgumentException(String.join("; ", scan.issues));
        }
        return toSchema(scan.facts);
    }

    /**
     * The names of every param the body references, without deriving types. Lenient: it
     * tolerates the constructs {@link #derive} rejects, so a caller-supplied
     * {@code param_schema} for such a body can still be checked against it.
     *
     * @throws IllegalArgumentException if the body is empty or has an unbalanced section.
     */
    public static Set<String> paramNames(String body) {
        Scan scan = scan(body);
        Set<String> names = new LinkedHashSet<>(scan.facts.keySet());
        names.addAll(scan.functionParams);
        return Collections.unmodifiableSet(names);
    }

    private static Scan scan(String body) {
        if (body == null || body.isEmpty()) {
            throw new IllegalArgumentException("Template body is empty");
        }

        Scan scan = new Scan();
        Deque<OpenSection> openSections = new ArrayDeque<>();

        String open = "{{";
        String close = "}}";
        // Quote state is tracked only over the literal text BETWEEN tags, advanced
        // incrementally, so a quote inside {{! ... }} or any other tag never counts.
        boolean inQuote = false;
        int literalStart = 0;
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
            inQuote = advanceQuoteState(body, literalStart, start, inQuote);

            // A triple-stache {{{x}}} is an open of "{{" immediately followed by "{".
            boolean triple = "{{".equals(open) && start + 2 < n && body.charAt(start + 2) == '{';
            int contentStart = start + open.length() + (triple ? 1 : 0);
            int contentEnd = end;
            String raw = body.substring(contentStart, contentEnd).trim();
            int afterTag = end + close.length();
            if (triple && afterTag < n && body.charAt(afterTag) == '}') {
                afterTag += 1; // consume the extra closing brace of }}}
            }
            literalStart = afterTag;

            if (raw.isEmpty()) {
                i = afterTag;
                continue;
            }

            char sigil = raw.charAt(0);
            switch (sigil) {
                case '!': // comment
                    break;
                case '=': { // delimiter change, e.g. {{=<% %>=}}
                    boolean closed = raw.length() > 1 && raw.endsWith("=");
                    String spec = raw.substring(1, closed ? raw.length() - 1 : raw.length()).trim();
                    String[] delims = spec.split("\\s+");
                    boolean wellFormed = delims.length == 2 && !delims[0].isEmpty() && !delims[1].isEmpty();
                    if (!closed || !wellFormed) {
                        // The engine does not apply this as a delimiter change, so the
                        // params scanned under the new delimiters would not exist.
                        scan.issues
                            .add(
                                "Malformed delimiter change '"
                                    + open
                                    + raw
                                    + close
                                    + "': expected the form {{=<open> <close>=}}, with the trailing '='"
                            );
                    }
                    if (wellFormed) {
                        open = delims[0];
                        close = delims[1];
                    }
                    break;
                }
                case '#': // section open
                case '^': { // inverted section open
                    String name = raw.substring(1).trim();
                    boolean inverted = sigil == '^';
                    boolean function = !inverted && isBuiltinFunction(name);
                    openSections.push(new OpenSection(name, inverted, function, afterTag));
                    if (!function) {
                        scan.facts.computeIfAbsent(name, k -> new ParamFacts()).usedAsSection = true;
                    }
                    break;
                }
                case '/': { // section close
                    String name = raw.substring(1).trim();
                    if (openSections.isEmpty() || !openSections.peek().name.equals(name)) {
                        throw new IllegalArgumentException("Unbalanced Mustache section near '" + name + "'");
                    }
                    OpenSection section = openSections.pop();
                    if (section.function && !FN_URL.equals(section.name)) {
                        recordFunctionParam(scan, section, body.substring(section.contentStart, start).trim(), open);
                    }
                    break;
                }
                case '>': // partial — not a fillable param
                    break;
                case '&': // {{&x}} unescaped, same as triple
                    recordValue(scan, openSections, raw.substring(1).trim(), true, inQuote);
                    break;
                default: // a plain value {{x}} or {{{x}}}
                    recordValue(scan, openSections, raw, triple, inQuote);
                    break;
            }
            i = afterTag;
        }

        if (!openSections.isEmpty()) {
            throw new IllegalArgumentException("Unclosed Mustache section '" + openSections.peek().name + "'");
        }
        return scan;
    }

    private static boolean isBuiltinFunction(String name) {
        return FN_TO_JSON.equals(name) || FN_JOIN.equals(name) || FN_URL.equals(name) || name.startsWith(FN_JOIN_DELIMITER_PREFIX);
    }

    /**
     * A {@code toJson}/{@code join} section renders the param its literal inner text names.
     * That name is a real param, but its shape (list or object) is invisible to the scanner.
     */
    private static void recordFunctionParam(Scan scan, OpenSection section, String inner, String open) {
        String fn = section.name.startsWith(FN_JOIN_DELIMITER_PREFIX) ? FN_JOIN : section.name;
        if (inner.isEmpty() || inner.contains(open)) {
            scan.issues.add("{{#" + fn + "}} section must name a single param; supply param_schema explicitly");
            return;
        }
        scan.functionParams.add(inner);
        scan.issues
            .add("param '" + inner + "' is rendered through {{#" + fn + "}}; its type cannot be derived — supply param_schema explicitly");
    }

    private static void recordValue(Scan scan, Deque<OpenSection> openSections, String name, boolean triple, boolean quoted) {
        if (name.isEmpty()) {
            return;
        }
        if (".".equals(name)) {
            // The implicit iterator: inside {{#xs}}, xs is a list the body iterates. The
            // schema has no list type, and deriving xs as a boolean guard would make a
            // filled value silently render a wrong query, so this is a derive failure.
            OpenSection section = openSections.peek();
            if (section != null && !section.inverted && !section.function) {
                scan.issues
                    .add(
                        "param '"
                            + section.name
                            + "' is iterated as a list ({{.}}); list params cannot be derived — supply param_schema explicitly"
                    );
            }
            return;
        }
        ParamFacts f = scan.facts.computeIfAbsent(name, k -> new ParamFacts());
        f.usedAsValue = true;
        if (openSections.isEmpty()) {
            f.usedAsValueAtRoot = true;
        }
        if (triple && !quoted) {
            f.tripleUnquoted = true;
        }
        if (quoted) {
            f.quotedScalar = true;
        }
    }

    /**
     * Advance the "inside a JSON string literal" state over the literal text
     * {@code [from, to)} between two tags. A scalar tag that follows while the state is
     * set sits inside a string (so it is a string, otherwise a number).
     *
     * <p>A quote is a real delimiter only when it is not escaped, and it is escaped
     * only when preceded by an <em>odd</em> run of backslashes — {@code \"} is escaped,
     * but {@code \\"} is a literal backslash followed by a closing quote. Counting the
     * run (rather than testing the single previous char) is what tells those apart.
     * The state resets at each newline: JSON string literals don't span raw newlines, so
     * a stray/miscounted quote stays contained to its line instead of flipping the
     * classification of every later parameter.
     */
    private static boolean advanceQuoteState(String body, int from, int to, boolean inQuote) {
        for (int j = from; j < to; j++) {
            char c = body.charAt(j);
            if (c == '\n') {
                inQuote = false; // strings don't span raw newlines; contain any miscount
            } else if (c == '"' && !isEscaped(body, from, j)) {
                inQuote = !inQuote;
            }
        }
        return inQuote;
    }

    /**
     * True if the char at {@code pos} is escaped, i.e. preceded by an odd run of
     * backslashes. The run stops at {@code from}, the start of the literal text, since a
     * backslash inside a preceding tag is not part of the JSON.
     */
    private static boolean isEscaped(String body, int from, int pos) {
        int backslashes = 0;
        for (int k = pos - 1; k >= from && body.charAt(k) == '\\'; k--) {
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
            // an inverted-section default (optional). A triple-stache in an unquoted slot
            // is an injected JSON array/object; one inside "..." is just an unescaped
            // string. A param used both ways keeps array, so its unquoted use still fits.
            if (f.tripleUnquoted) {
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
