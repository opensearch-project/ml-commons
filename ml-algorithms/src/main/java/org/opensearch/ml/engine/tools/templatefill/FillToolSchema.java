/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.tools.templatefill;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.opensearch.ml.common.agenticsearch.AgenticSearchTemplate;

import lombok.Getter;

/**
 * Builds the forced-tool JSON Schema the model fills, from a template's stored param-schema.
 *
 * <p>A single template becomes one property per param, plus an optional {@code cannot_express} flag the
 * model sets to decline a question the template cannot express. Several candidate templates become one
 * merged tool: the flag, a required {@code template_id} choice (including {@code none}), and every
 * candidate's params namespaced by a per-template prefix and all optional. The chosen template's values are then
 * checked against its real schema by {@link FillValueValidator}, which restores the required-ness a
 * merged all-optional schema cannot express.
 *
 * <p>Property names are sanitized to the character set every supported provider accepts, and each built
 * schema keeps the map from emitted property back to the real Mustache param name.
 */
public final class FillToolSchema {

    public static final String CANNOT_EXPRESS_FIELD = "cannot_express";
    public static final String CHOICE_FIELD = "template_id";
    public static final String NONE_CHOICE = "none";

    // Upper bound on candidates in one merged call; the schema grows with each candidate.
    public static final int MAX_CANDIDATES = 8;

    // param-schema keys, as written at registration.
    static final String TYPE_KEY = "type";
    static final String REQUIRED_KEY = "required";
    static final String DESCRIPTION_KEY = "description";
    static final String ENUM_KEY = "enum";

    static final String TYPE_STRING = "string";
    static final String TYPE_INTEGER = "integer";
    static final String TYPE_NUMBER = "number";
    static final String TYPE_BOOLEAN = "boolean";
    static final String TYPE_ARRAY = "array";

    // Provider property-name grammar (the strictest, Bedrock's, is ^[a-zA-Z0-9_.-]{1,64}$).
    private static final int MAX_PROPERTY_LENGTH = 64;

    // Enumerated triggers are load-bearing; see TemplateFillPrompts.
    private static final String SINGLE_ABSTAIN_DESCRIPTION = "Set true when the question needs a capability NOT among these parameters. "
        + "Concretely set it true if the question: (a) restricts text matching to ONE "
        + "specific field (e.g. 'in the title', 'in the name') and no parameter isolates that "
        + "field; (b) demands an EXACT contiguous phrase / literal wording in a field and no "
        + "phrase parameter exists; (c) asks to RANK or BOOST by a signal (most popular, "
        + "trending, boost recent/newer, custom relevance) and no parameter or sort option "
        + "expresses that ranking; (d) asks for a COUNT-only answer, aggregation, faceting, or "
        + "grouping; (e) references a field, similarity ('products like X'), or predicate that "
        + "has no matching parameter. Otherwise leave it false and fill the parameters.";

    private static final String MULTI_ABSTAIN_DESCRIPTION = "Set true when no candidate template can express the question — a "
        + "field, filter, projection, aggregation, count-only answer, exact "
        + "phrase, prefix/wildcard/fuzzy match, custom ranking, or similarity "
        + "that none of the parameters below cover. Leave false otherwise.";

    private static final String ARRAY_HINT = "JSON array literal, e.g. [\"a\",\"b\"].";

    private FillToolSchema() {}

    /** A built tool schema plus the lookups needed to read the model's fill back. */
    @Getter
    public static final class Built {
        private final Map<String, Object> schema;
        // template id -> (emitted property -> real param name)
        private final Map<String, Map<String, String>> propertyToParam;
        // Whether the schema carries the abstain flag (a single template with a real param of that name does not).
        private final boolean abstainEnabled;
        // template id -> the prefix its params carry in a merged schema; empty for a single template
        private final Map<String, String> prefixes;

        Built(
            Map<String, Object> schema,
            Map<String, Map<String, String>> propertyToParam,
            boolean abstainEnabled,
            Map<String, String> prefixes
        ) {
            this.schema = schema;
            this.propertyToParam = propertyToParam;
            this.abstainEnabled = abstainEnabled;
            this.prefixes = prefixes;
        }
    }

    /** Build the fill tool for one template. */
    public static Built single(AgenticSearchTemplate template) {
        Map<String, Object> paramSchema = template.getParamSchema();
        Map<String, Object> properties = new LinkedHashMap<>();
        List<String> required = new ArrayList<>();
        Set<String> used = new HashSet<>();
        // A template that declares a real param of the abstain name keeps it and forgoes the flag.
        boolean abstainEnabled = !paramSchema.containsKey(CANNOT_EXPRESS_FIELD);
        if (abstainEnabled) {
            used.add(CANNOT_EXPRESS_FIELD);
        }

        Map<String, String> names = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : paramSchema.entrySet()) {
            if (!(entry.getValue() instanceof Map)) {
                continue;
            }
            Map<String, Object> spec = asMap(entry.getValue());
            String property = uniqueProperty(entry.getKey(), "", used);
            properties.put(property, paramJsonSchema(spec));
            names.put(property, entry.getKey());
            if (Boolean.TRUE.equals(spec.get(REQUIRED_KEY))) {
                required.add(property);
            }
        }
        if (abstainEnabled) {
            properties.put(CANNOT_EXPRESS_FIELD, Map.of(TYPE_KEY, TYPE_BOOLEAN, DESCRIPTION_KEY, SINGLE_ABSTAIN_DESCRIPTION));
        }

        Map<String, Map<String, String>> propertyToParam = new LinkedHashMap<>();
        propertyToParam.put(template.getTemplateId(), names);
        return new Built(objectSchema(properties, required), propertyToParam, abstainEnabled, Map.of());
    }

    /** Build the merged pick-and-fill tool for several candidate templates. */
    public static Built multi(List<AgenticSearchTemplate> candidates) {
        Map<String, Object> properties = new LinkedHashMap<>();
        Set<String> used = new HashSet<>(List.of(CANNOT_EXPRESS_FIELD, CHOICE_FIELD));

        // The abstain flag is declared before the choice so the model decides expressibility first.
        properties.put(CANNOT_EXPRESS_FIELD, Map.of(TYPE_KEY, TYPE_BOOLEAN, DESCRIPTION_KEY, MULTI_ABSTAIN_DESCRIPTION));
        List<String> choices = new ArrayList<>();
        for (AgenticSearchTemplate candidate : candidates) {
            choices.add(candidate.getTemplateId());
        }
        // A duplicated enum member is invalid JSON Schema, so the sentinel is only added when unused.
        if (!choices.contains(NONE_CHOICE)) {
            choices.add(NONE_CHOICE);
        }
        Map<String, Object> choice = new LinkedHashMap<>();
        choice.put(TYPE_KEY, TYPE_STRING);
        choice.put(ENUM_KEY, choices);
        choice
            .put(
                DESCRIPTION_KEY,
                "Id of the template you are filling, or 'none'. Fill only the parameters carrying that template's prefix."
            );
        properties.put(CHOICE_FIELD, choice);

        // Params are namespaced by a short positional prefix rather than the template id: an id can be up to 255
        // characters and sanitizing it is lossy, while a property name is capped at 64. The prompt's candidate
        // list and each property's description name the template the prefix stands for.
        Map<String, Map<String, String>> propertyToParam = new LinkedHashMap<>();
        Map<String, String> prefixes = new LinkedHashMap<>();
        int position = 0;
        for (AgenticSearchTemplate candidate : candidates) {
            String prefix = "t" + position++ + "__";
            prefixes.put(candidate.getTemplateId(), prefix);
            Map<String, String> names = new LinkedHashMap<>();
            for (Map.Entry<String, Object> entry : candidate.getParamSchema().entrySet()) {
                if (!(entry.getValue() instanceof Map)) {
                    continue;
                }
                String property = uniqueProperty(entry.getKey(), prefix, used);
                Map<String, Object> schema = paramJsonSchema(asMap(entry.getValue()));
                Object description = schema.get(DESCRIPTION_KEY);
                String tag = "[" + candidate.getTemplateId() + "]";
                schema.put(DESCRIPTION_KEY, description == null ? tag : tag + " " + description);
                properties.put(property, schema);
                names.put(property, entry.getKey());
            }
            propertyToParam.put(candidate.getTemplateId(), names);
        }
        return new Built(objectSchema(properties, List.of(CHOICE_FIELD)), propertyToParam, true, prefixes);
    }

    /** The JSON Schema for one param-schema entry. */
    static Map<String, Object> paramJsonSchema(Map<String, Object> spec) {
        Map<String, Object> out = new LinkedHashMap<>();
        String rawType = rawType(spec);
        String jsonType = jsonType(rawType);
        Object description = spec.get(DESCRIPTION_KEY);
        String text = description instanceof String ? (String) description : "";

        Object enumValues = spec.get(ENUM_KEY);
        if (enumValues instanceof List && !((List<?>) enumValues).isEmpty()) {
            List<?> members = (List<?>) enumValues;
            // Take the declared type rather than assuming string, so a numeric enum is emitted as numbers.
            boolean allStrings = members.stream().allMatch(v -> v instanceof String);
            if (!(TYPE_STRING.equals(jsonType) && !allStrings)) {
                out.put(TYPE_KEY, jsonType);
            }
            out.put(ENUM_KEY, new ArrayList<>(members));
        } else {
            out.put(TYPE_KEY, jsonType);
            if (TYPE_ARRAY.equals(rawType)) {
                // A multi-value slot is a raw JSON literal the body renders through a triple brace.
                text = (text + " " + ARRAY_HINT).trim();
            }
        }
        if (!text.isEmpty()) {
            out.put(DESCRIPTION_KEY, text);
        }
        return out;
    }

    /** The param-schema's declared type, lower-cased; missing means string. */
    static String rawType(Map<String, Object> spec) {
        Object type = spec.get(TYPE_KEY);
        return type == null ? TYPE_STRING : String.valueOf(type).toLowerCase(Locale.ROOT);
    }

    /** Map a param-schema type to its JSON Schema type; unknown types degrade to string. */
    static String jsonType(String rawType) {
        switch (rawType) {
            case "integer":
            case "int":
            case "long":
                return TYPE_INTEGER;
            case "number":
            case "float":
            case "double":
                return TYPE_NUMBER;
            case "boolean":
            case "bool":
                return TYPE_BOOLEAN;
            default:
                // string, text, keyword, and array (a JSON literal carried as a string)
                return TYPE_STRING;
        }
    }

    private static Map<String, Object> objectSchema(Map<String, Object> properties, List<String> required) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put(TYPE_KEY, "object");
        schema.put("properties", properties);
        if (!required.isEmpty()) {
            schema.put(REQUIRED_KEY, required);
        }
        return schema;
    }

    /** Sanitize a param name (with an optional namespace prefix) to a unique, provider-legal property name. */
    private static String uniqueProperty(String name, String prefix, Set<String> used) {
        String base = prefix + sanitize(name);
        if (base.length() > MAX_PROPERTY_LENGTH) {
            base = base.substring(0, MAX_PROPERTY_LENGTH);
        }
        String candidate = base;
        int n = 1;
        while (used.contains(candidate)) {
            String suffix = "_" + n++;
            candidate = base.substring(0, Math.min(base.length(), MAX_PROPERTY_LENGTH - suffix.length())) + suffix;
        }
        used.add(candidate);
        return candidate;
    }

    private static String sanitize(String name) {
        String safe = name.replaceAll("[^a-zA-Z0-9_.-]", "_");
        return safe.isEmpty() ? "p" : safe;
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> asMap(Object value) {
        return (Map<String, Object>) value;
    }
}
