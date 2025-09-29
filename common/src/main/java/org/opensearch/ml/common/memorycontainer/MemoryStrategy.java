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
    private boolean enabled;
    private String type;
    private List<String> namespace;
    private Map<String, Object> strategyConfig;

    public MemoryStrategy(String id, boolean enabled, String type, List<String> namespace, Map<String, Object> strategyConfig) {
        // Generate ID if not provided, using type prefix for better identification
        this.id = (id != null && !id.trim().isEmpty()) ? id : generateStrategyId(type);
        this.enabled = enabled;
        this.type = type;
        this.namespace = namespace;
        this.strategyConfig = strategyConfig;
    }

    public MemoryStrategy(StreamInput input) throws IOException {
        this.id = input.readString();
        this.enabled = input.readBoolean();
        this.type = input.readString();
        this.namespace = input.readStringList();
        if (input.readBoolean()) {
            this.strategyConfig = input.readMap();
        }
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeString(id);
        out.writeBoolean(enabled);
        out.writeString(type);
        out.writeStringCollection(namespace);
        if (!strategyConfig.isEmpty()) {
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
        builder.field(STRATEGY_TYPE_FIELD, type);
        builder.field(NAMESPACE_FIELD, namespace);
        if (!strategyConfig.isEmpty()) {
            builder.field(STRATEGY_CONFIG_FIELD, strategyConfig);
        }

        builder.endObject();
        return builder;
    }

    public static MemoryStrategy parse(XContentParser parser) throws IOException {
        String id = null;
        boolean enabled = true;  // Default to true
        String type = null;
        List<String> namespace = null;
        Map<String, Object> strategyConfig = new HashMap<>();

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
                    type = parser.text();
                    break;
                case NAMESPACE_FIELD:
                    namespace = new ArrayList<>();
                    ensureExpectedToken(XContentParser.Token.START_ARRAY, parser.currentToken(), parser);
                    while (parser.nextToken() != XContentParser.Token.END_ARRAY) {
                        namespace.add(parser.text());
                    }
                    break;
                case STRATEGY_CONFIG_FIELD:
                    strategyConfig.putAll(parser.map());
                    break;
                default:
                    parser.skipChildren();
                    break;
            }
        }

        // Generate ID with type prefix if not provided
        if (id == null) {
            id = generateStrategyId(type);
        }

        return MemoryStrategy.builder().id(id).enabled(enabled).type(type).namespace(namespace).strategyConfig(strategyConfig).build();
    }

    /**
     * Generate a unique strategy ID with format: type_XXXXXXXX (8 char UUID)
     * If type is null or empty, defaults to "strategy_XXXXXXXX"
     *
     * @param type The strategy type (e.g., "semantic", "user_preference")
     * @return A unique strategy ID
     */
    public static String generateStrategyId(String type) {
        String prefix = (type != null && !type.trim().isEmpty()) ? type.toLowerCase().replace(" ", "_") : "strategy";
        return prefix + "_" + UUID.randomUUID().toString().substring(0, 8);
    }

}
