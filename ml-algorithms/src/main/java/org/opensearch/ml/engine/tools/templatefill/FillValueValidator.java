/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.tools.templatefill;

import static org.opensearch.ml.engine.tools.templatefill.FillToolSchema.ENUM_KEY;
import static org.opensearch.ml.engine.tools.templatefill.FillToolSchema.REQUIRED_KEY;
import static org.opensearch.ml.engine.tools.templatefill.FillToolSchema.TYPE_ARRAY;
import static org.opensearch.ml.engine.tools.templatefill.FillToolSchema.TYPE_BOOLEAN;
import static org.opensearch.ml.engine.tools.templatefill.FillToolSchema.TYPE_INTEGER;
import static org.opensearch.ml.engine.tools.templatefill.FillToolSchema.TYPE_NUMBER;
import static org.opensearch.ml.engine.tools.templatefill.FillToolSchema.asMap;
import static org.opensearch.ml.engine.tools.templatefill.FillToolSchema.jsonType;
import static org.opensearch.ml.engine.tools.templatefill.FillToolSchema.rawType;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.opensearch.common.xcontent.json.JsonXContent;
import org.opensearch.core.xcontent.DeprecationHandler;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.utils.StringUtils;

/**
 * Checks a model's fill against a template's param-schema and returns the values to render.
 *
 * <p>Only the params the model set are kept, so the body's optional sections and inverted-section
 * defaults behave as authored; an unset optional must not render as an empty slot. Keys the schema does
 * not declare are dropped rather than failing the fill. Any violation (a missing required param, a value
 * outside its enum, a value that does not fit its type) throws, and the caller falls back to writing the
 * query directly.
 */
public final class FillValueValidator {

    // Largest magnitude a double represents every integer up to exactly.
    private static final double MAX_EXACT_INTEGER = 9_007_199_254_740_992d;

    private FillValueValidator() {}

    /**
     * @param paramSchema the template's stored param-schema
     * @param filled the model's values keyed by real param name
     * @return the values to render, keyed by param name
     * @throws IllegalArgumentException if the fill violates the schema
     */
    public static Map<String, Object> validate(Map<String, Object> paramSchema, Map<String, Object> filled) {
        Map<String, Object> clean = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : paramSchema.entrySet()) {
            if (!(entry.getValue() instanceof Map)) {
                continue;
            }
            String name = entry.getKey();
            Map<String, Object> spec = asMap(entry.getValue());
            Object value = filled.get(name);
            if (value == null) {
                if (Boolean.TRUE.equals(spec.get(REQUIRED_KEY))) {
                    throw new IllegalArgumentException("required param '" + name + "' was not filled");
                }
                continue;
            }
            clean.put(name, coerce(name, spec, value));
        }
        return clean;
    }

    private static Object coerce(String name, Map<String, Object> spec, Object value) {
        Object enumValues = spec.get(ENUM_KEY);
        if (enumValues instanceof List && !((List<?>) enumValues).isEmpty()) {
            return enumMember(name, (List<?>) enumValues, value);
        }
        String rawType = rawType(spec);
        switch (jsonType(rawType)) {
            case TYPE_INTEGER:
                return integer(name, value);
            case TYPE_NUMBER:
                return number(name, value);
            case TYPE_BOOLEAN:
                return bool(name, value);
            default:
                if (TYPE_ARRAY.equals(rawType)) {
                    return jsonLiteral(name, value);
                }
                return string(name, value);
        }
    }

    /** Resolve a value to the enum member it names, keeping the member's own type. */
    private static Object enumMember(String name, List<?> members, Object value) {
        Object normalized = value instanceof Number ? normalizeNumber((Number) value) : value;
        for (Object member : members) {
            if (member == null) {
                continue;
            }
            if (member instanceof Number && normalized instanceof Number) {
                if (numericallyEqual((Number) member, (Number) value)) {
                    return member instanceof Double || member instanceof Float ? member : normalizeNumber((Number) member);
                }
            } else if (member.equals(normalized) || String.valueOf(member).equals(String.valueOf(normalized))) {
                return member instanceof Number ? normalizeNumber((Number) member) : member;
            }
        }
        throw new IllegalArgumentException("param '" + name + "' value '" + value + "' is not one of " + members);
    }

    /**
     * Exact numeric equality, so integers beyond a double's exact range are not conflated (5 and 5.0 are
     * still equal).
     */
    private static boolean numericallyEqual(Number a, Number b) {
        BigDecimal x = toBigDecimal(a);
        BigDecimal y = toBigDecimal(b);
        return x != null && y != null ? x.compareTo(y) == 0 : a.doubleValue() == b.doubleValue();
    }

    /** Null for NaN or infinity, which have no decimal form. */
    private static BigDecimal toBigDecimal(Number n) {
        if (n instanceof BigDecimal) {
            return (BigDecimal) n;
        }
        if (n instanceof BigInteger) {
            return new BigDecimal((BigInteger) n);
        }
        if (n instanceof Double || n instanceof Float) {
            double d = n.doubleValue();
            return Double.isNaN(d) || Double.isInfinite(d) ? null : new BigDecimal(d);
        }
        return BigDecimal.valueOf(n.longValue());
    }

    private static Object integer(String name, Object value) {
        Object number = number(name, value);
        if (!(number instanceof Long)) {
            throw new IllegalArgumentException("param '" + name + "' expects an integer, got '" + value + "'");
        }
        return number;
    }

    /** A number, preferring an integer so {@code 5} renders as {@code 5} rather than {@code 5.0}. */
    private static Object number(String name, Object value) {
        if (value instanceof Number) {
            return normalizeNumber((Number) value);
        }
        if (value instanceof String) {
            try {
                return normalizeNumber(Double.parseDouble(((String) value).trim()));
            } catch (NumberFormatException e) {
                // fall through
            }
        }
        throw new IllegalArgumentException("param '" + name + "' expects a number, got '" + value + "'");
    }

    private static Object bool(String name, Object value) {
        if (value instanceof Boolean) {
            return value;
        }
        if (value instanceof String) {
            String s = ((String) value).trim().toLowerCase(Locale.ROOT);
            if ("true".equals(s) || "false".equals(s)) {
                return Boolean.parseBoolean(s);
            }
        }
        throw new IllegalArgumentException("param '" + name + "' expects a boolean, got '" + value + "'");
    }

    /**
     * An array slot renders raw through a triple brace, so the model's text would otherwise control the
     * query's structure. The value is parsed as exactly one JSON value and re-serialized, so it can only fill
     * its own slot; a scalar (or plain text) becomes a one-element array. An object is rejected, so the model
     * supplies values only and cannot insert a clause (a terms lookup, say) into the slot.
     */
    private static Object jsonLiteral(String name, Object value) {
        Object parsed = value;
        if (value instanceof String) {
            parsed = parseSingleJsonValue((String) value);
        }
        if (parsed instanceof List) {
            return StringUtils.toJson(parsed);
        }
        if (parsed instanceof String || parsed instanceof Number || parsed instanceof Boolean) {
            return StringUtils.toJson(List.of(parsed));
        }
        throw new IllegalArgumentException("param '" + name + "' expects a JSON array literal, got '" + value + "'");
    }

    /**
     * Parse text as exactly one JSON value. Text that is not a single well-formed JSON value (trailing
     * content, or not JSON at all) is taken as a plain string.
     */
    private static Object parseSingleJsonValue(String text) {
        try (
            XContentParser parser = JsonXContent.jsonXContent
                .createParser(NamedXContentRegistry.EMPTY, DeprecationHandler.IGNORE_DEPRECATIONS, text)
        ) {
            XContentParser.Token token = parser.nextToken();
            Object parsed;
            if (token == XContentParser.Token.START_ARRAY) {
                parsed = parser.list();
            } else if (token == XContentParser.Token.START_OBJECT) {
                parsed = parser.map();
            } else if (token != null && token.isValue()) {
                parsed = parser.objectText();
            } else {
                return text;
            }
            return parser.nextToken() == null ? parsed : text;
        } catch (Exception e) {
            return text;
        }
    }

    private static Object string(String name, Object value) {
        if (value instanceof String) {
            return value;
        }
        if (value instanceof Number) {
            return String.valueOf(normalizeNumber((Number) value));
        }
        if (value instanceof Boolean) {
            return String.valueOf(value);
        }
        throw new IllegalArgumentException("param '" + name + "' expects a string, got '" + value + "'");
    }

    /** An integral value becomes a Long; anything else a Double. */
    static Number normalizeNumber(Number value) {
        if (value instanceof Long || value instanceof Integer || value instanceof Short || value instanceof Byte) {
            return value.longValue();
        }
        double d = value.doubleValue();
        if (!Double.isInfinite(d) && !Double.isNaN(d) && d == Math.rint(d) && Math.abs(d) <= MAX_EXACT_INTEGER) {
            return (long) d;
        }
        return d;
    }
}
