/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.agent;

import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.common.io.stream.Writeable;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;

/**
 * Per-agent tool search configuration. Experimental and opt-in: applies only when {@code enabled}
 * is true here and the cluster-level switch is on.
 *
 * Every field is nullable; null means the built-in default (see ToolSelectionSettings).
 * search_timeout is deliberately absent so it stays admin-only.
 */
@Getter
@EqualsAndHashCode
public class MLToolSelectionConfig implements ToXContentObject, Writeable {

    public static final String ENABLED_FIELD = "enabled";
    public static final String PINNED_TOOLS_FIELD = "pinned_tools";
    public static final String TOP_K_FIELD = "top_k";
    public static final String MIN_POOL_SIZE_FIELD = "min_pool_size";
    public static final String MAX_DYNAMIC_TOOLS_FIELD = "max_dynamic_tools";
    public static final String EMBEDDING_MODEL_ID_FIELD = "embedding_model_id";
    public static final Set<String> FIELDS = Set
        .of(ENABLED_FIELD, PINNED_TOOLS_FIELD, TOP_K_FIELD, MIN_POOL_SIZE_FIELD, MAX_DYNAMIC_TOOLS_FIELD, EMBEDDING_MODEL_ID_FIELD);

    static final int MAX_TOP_K = 50;
    static final int MAX_MIN_POOL_SIZE = 10000;
    /** Matches the number of hidden tool names search_tools spells out in its description. */
    static final int MAX_MAX_DYNAMIC_TOOLS = 100;

    private final Boolean enabled;
    private final List<String> pinnedTools;
    private final Integer topK;
    private final Integer minPoolSize;
    /** Upper bound for max_dynamic_tools. */
    private final Integer maxDynamicTools;
    private final String embeddingModelId;

    @Builder(toBuilder = true)
    public MLToolSelectionConfig(
        Boolean enabled,
        List<String> pinnedTools,
        Integer topK,
        Integer minPoolSize,
        Integer maxDynamicTools,
        String embeddingModelId
    ) {
        this.enabled = enabled;
        this.pinnedTools = validatedCopy(pinnedTools);
        this.topK = topK;
        this.minPoolSize = minPoolSize;
        this.maxDynamicTools = maxDynamicTools;
        this.embeddingModelId = embeddingModelId;
        validate();
    }

    /** Validated here so {@link #parse} and stream deserialization are covered too. */
    private void validate() {
        checkRange(TOP_K_FIELD, topK, MAX_TOP_K);
        checkRange(MIN_POOL_SIZE_FIELD, minPoolSize, MAX_MIN_POOL_SIZE);
        checkRange(MAX_DYNAMIC_TOOLS_FIELD, maxDynamicTools, MAX_MAX_DYNAMIC_TOOLS);
        if (embeddingModelId != null && embeddingModelId.isBlank()) {
            throw new IllegalArgumentException("tool_selection.embedding_model_id must not be blank; omit it to use the cluster default");
        }
    }

    private static void checkRange(String field, Integer value, int max) {
        if (value != null && (value < 1 || value > max)) {
            throw new IllegalArgumentException("tool_selection." + field + " must be between 1 and " + max);
        }
    }

    /** Checked before copying: List.copyOf would otherwise reject a null entry with an NPE, not a 400. */
    private static List<String> validatedCopy(List<String> pinnedTools) {
        if (pinnedTools == null) {
            return null;
        }
        for (String name : pinnedTools) {
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("tool_selection.pinned_tools must not contain blank names");
            }
        }
        return List.copyOf(pinnedTools);
    }

    public MLToolSelectionConfig(StreamInput in) throws IOException {
        this.enabled = in.readOptionalBoolean();
        this.pinnedTools = validatedCopy(in.readOptionalStringList());
        this.topK = in.readOptionalInt();
        this.minPoolSize = in.readOptionalInt();
        this.maxDynamicTools = in.readOptionalInt();
        this.embeddingModelId = in.readOptionalString();
        validate();
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeOptionalBoolean(enabled);
        out.writeOptionalStringCollection(pinnedTools);
        out.writeOptionalInt(topK);
        out.writeOptionalInt(minPoolSize);
        out.writeOptionalInt(maxDynamicTools);
        out.writeOptionalString(embeddingModelId);
    }

    /** Unset fields are omitted rather than written as null. */
    @Override
    public XContentBuilder toXContent(XContentBuilder builder, ToXContent.Params params) throws IOException {
        return builder.map(toMap());
    }

    /** The set fields, in a fixed order; unset fields are left out. */
    public Map<String, Object> toMap() {
        Map<String, Object> fields = new LinkedHashMap<>();
        putIfSet(fields, ENABLED_FIELD, enabled);
        putIfSet(fields, PINNED_TOOLS_FIELD, pinnedTools);
        putIfSet(fields, TOP_K_FIELD, topK);
        putIfSet(fields, MIN_POOL_SIZE_FIELD, minPoolSize);
        putIfSet(fields, MAX_DYNAMIC_TOOLS_FIELD, maxDynamicTools);
        putIfSet(fields, EMBEDDING_MODEL_ID_FIELD, embeddingModelId);
        return fields;
    }

    private static void putIfSet(Map<String, Object> fields, String name, Object value) {
        if (value != null) {
            fields.put(name, value);
        }
    }

    /** True when no field is set, which behaves exactly like having no configuration. */
    public boolean isEmpty() {
        return toMap().isEmpty();
    }

    public static MLToolSelectionConfig parse(XContentParser parser) throws IOException {
        return parse(parser, null);
    }

    /**
     * @param nullFields when not null, receives the known fields given as an explicit null, so an update
     *                   can tell "reset to the default" from "leave unchanged"
     */
    public static MLToolSelectionConfig parse(XContentParser parser, Set<String> nullFields) throws IOException {
        Boolean enabled = null;
        List<String> pinnedTools = null;
        Integer topK = null;
        Integer minPoolSize = null;
        Integer maxDynamicTools = null;
        String embeddingModelId = null;

        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.currentToken(), parser);
        while (parser.nextToken() != XContentParser.Token.END_OBJECT) {
            String fieldName = parser.currentName();
            parser.nextToken();
            // An explicit null means the same as leaving the field out: use the default.
            if (parser.currentToken() == XContentParser.Token.VALUE_NULL) {
                if (nullFields != null && FIELDS.contains(fieldName)) {
                    nullFields.add(fieldName);
                }
                continue;
            }
            switch (fieldName) {
                case ENABLED_FIELD:
                    enabled = parser.booleanValue();
                    break;
                case PINNED_TOOLS_FIELD:
                    pinnedTools = parseStringArray(parser);
                    break;
                case TOP_K_FIELD:
                    topK = parser.intValue();
                    break;
                case MIN_POOL_SIZE_FIELD:
                    minPoolSize = parser.intValue();
                    break;
                case MAX_DYNAMIC_TOOLS_FIELD:
                    maxDynamicTools = parser.intValue();
                    break;
                case EMBEDDING_MODEL_ID_FIELD:
                    embeddingModelId = parser.text();
                    break;
                default:
                    // skipChildren keeps the parser in sync when an unknown field is an object or array.
                    parser.skipChildren();
                    break;
            }
        }
        return MLToolSelectionConfig
            .builder()
            .enabled(enabled)
            .pinnedTools(pinnedTools)
            .topK(topK)
            .minPoolSize(minPoolSize)
            .maxDynamicTools(maxDynamicTools)
            .embeddingModelId(embeddingModelId)
            .build();
    }

    private static List<String> parseStringArray(XContentParser parser) throws IOException {
        ensureExpectedToken(XContentParser.Token.START_ARRAY, parser.currentToken(), parser);
        List<String> values = new ArrayList<>();
        while (parser.nextToken() != XContentParser.Token.END_ARRAY) {
            // text() throws on VALUE_NULL; keep the null so validation reports it as a bad value.
            values.add(parser.currentToken() == XContentParser.Token.VALUE_NULL ? null : parser.text());
        }
        return values;
    }
}
