/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.memorycontainer;

import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.ENABLED_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.ID_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.NAMESPACE_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.STRATEGY_CONFIG_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.STRATEGY_TYPE_FIELD;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.common.io.stream.Writeable;
import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
@EqualsAndHashCode
public class MemoryStrategy implements ToXContentObject, Writeable {

    private String id;
    private Boolean enabled;
    private MemoryStrategyType type;
    private List<String> namespace;
    private Map<String, Object> strategyConfig;

    public MemoryStrategy(String id, Boolean enabled, MemoryStrategyType type, List<String> namespace, Map<String, Object> strategyConfig) {
        // Do not auto-generate ID in constructor - let StrategyMergeHelper control ID generation
        // This allows distinguishing between updates (ID provided) and additions (ID null/empty)
        this.id = id;
        this.enabled = enabled;
        this.type = type;
        this.namespace = namespace;
        this.strategyConfig = (strategyConfig != null) ? strategyConfig : new HashMap<>();
    }

    public MemoryStrategy(StreamInput input) throws IOException {
        this.id = input.readString();
        this.enabled = input.readOptionalBoolean();

        if (input.readBoolean()) {
            this.type = input.readEnum(MemoryStrategyType.class);
        } else {
            this.type = null;
        }

        if (input.readBoolean()) {
            this.namespace = input.readStringList();
        } else {
            this.namespace = null;
        }

        if (input.readBoolean()) {
            this.strategyConfig = input.readMap();
        } else {
            this.strategyConfig = new HashMap<>();
        }
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeString(id);
        out.writeOptionalBoolean(enabled);

        if (type != null) {
            out.writeBoolean(true);
            out.writeEnum(type);
        } else {
            out.writeBoolean(false);
        }

        if (namespace != null) {
            out.writeBoolean(true);
            out.writeStringCollection(namespace);
        } else {
            out.writeBoolean(false);
        }

        if (strategyConfig != null && !strategyConfig.isEmpty()) {
            out.writeBoolean(true);
            out.writeMap(strategyConfig);
        } else {
            out.writeBoolean(false);
        }
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        builder.field(ID_FIELD, id);
        builder.field(ENABLED_FIELD, enabled);

        if (type != null) {
            builder.field(STRATEGY_TYPE_FIELD, type.getValue());
        }

        if (namespace != null) {
            builder.field(NAMESPACE_FIELD, namespace);
        }

        if (strategyConfig != null && !strategyConfig.isEmpty()) {
            builder.field(STRATEGY_CONFIG_FIELD, strategyConfig);
        }
        builder.endObject();
        return builder;
    }

    public static MemoryStrategy parse(XContentParser parser) throws IOException {
        String id = null;
        Boolean enabled = null;
        MemoryStrategyType type = null;
        List<String> namespace = null;
        Map<String, Object> strategyConfig = null;

        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.currentToken(), parser);
        while (parser.nextToken() != XContentParser.Token.END_OBJECT) {
            String fieldName = parser.currentName();
            parser.nextToken();

            switch (fieldName) {
                case ID_FIELD:
                    id = parser.text();
                    break;
                case ENABLED_FIELD:
                    enabled = parser.booleanValue();
                    break;
                case STRATEGY_TYPE_FIELD:
                    type = MemoryStrategyType.fromString(parser.text());
                    break;
                case NAMESPACE_FIELD:
                    namespace = new ArrayList<>();
                    ensureExpectedToken(XContentParser.Token.START_ARRAY, parser.currentToken(), parser);
                    while (parser.nextToken() != XContentParser.Token.END_ARRAY) {
                        namespace.add(parser.text());
                    }
                    break;
                case STRATEGY_CONFIG_FIELD:
                    strategyConfig = parser.map();
                    break;
                default:
                    parser.skipChildren();
                    break;
            }
        }

        return MemoryStrategy.builder().id(id).enabled(enabled).type(type).namespace(namespace).strategyConfig(strategyConfig).build();
    }

    /**
     * Returns whether this strategy is enabled. If enabled is null, defaults to true.
     *
     * @return true if enabled or null, false otherwise
     */
    public boolean isEnabled() {
        return enabled == null || enabled;
    }

    /**
     * Validates a memory strategy for required fields.
     * Since type is now @NonNull and an enum, no type validation needed.
     * Only validates namespace is not empty.
     *
     * @param strategy The memory strategy to validate
     * @throws IllegalArgumentException if validation fails
     */
    public static void validate(MemoryStrategy strategy) {
        if (strategy == null) {
            throw new IllegalArgumentException("Strategy cannot be null");
        }
        if (strategy.getNamespace() == null || strategy.getNamespace().isEmpty()) {
            throw new IllegalArgumentException("Strategy namespace is required. Please provide a non-empty namespace array.");
        }
    }

    /**
     * Generate a unique strategy ID with format: type_XXXXXXXX (8 char UUID)
     *
     * @param type The strategy type enum (required)
     * @return A unique strategy ID
     */
    public static String generateStrategyId(MemoryStrategyType type) {
        return type.getValue().toLowerCase() + "_" + UUID.randomUUID().toString().substring(0, 8);
    }

}
