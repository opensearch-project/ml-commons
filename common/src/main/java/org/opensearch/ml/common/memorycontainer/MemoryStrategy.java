/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.memorycontainer;

import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.NAMESPACE_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.STRATEGY_ENABLED_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.STRATEGY_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.STRATEGY_ID_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.STRATEGY_TYPE_FIELD;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
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
    private MemoryStrategyType strategy;
    private List<String> namespace;

    public MemoryStrategy(String id, boolean enabled, MemoryStrategyType strategy, List<String> namespace) {
        this.id = id;
        this.enabled = enabled;
        this.strategy = strategy;
        this.namespace = namespace;
    }

    public MemoryStrategy(StreamInput input) throws IOException {
        this.id = input.readString();
        this.enabled = input.readBoolean();
        this.strategy = input.readEnum(MemoryStrategyType.class);
        this.namespace = input.readStringList();
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeString(id);
        out.writeBoolean(enabled);
        out.writeEnum(strategy);
        out.writeStringCollection(namespace);
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();

        builder.field(STRATEGY_ID_FIELD, id);
        builder.field(STRATEGY_ENABLED_FIELD, enabled);
        builder.field(STRATEGY_FIELD, strategy.getValue());
        builder.field(NAMESPACE_FIELD, namespace);

        builder.endObject();
        return builder;
    }

    public static MemoryStrategy parse(XContentParser parser) throws IOException {
        String id = null;
        boolean enabled = true;
        MemoryStrategyType strategy = null;
        List<String> namespace = null;

        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.currentToken(), parser);
        while (parser.nextToken() != XContentParser.Token.END_OBJECT) {
            String fieldName = parser.currentName();
            parser.nextToken();

            switch (fieldName) {
                case STRATEGY_ID_FIELD:
                    id = parser.text();
                    break;
                case STRATEGY_ENABLED_FIELD:
                    enabled = parser.booleanValue();
                    break;
                case STRATEGY_FIELD:
                    strategy = MemoryStrategyType.fromString(parser.text());
                    break;
                case STRATEGY_TYPE_FIELD:
                    // Backward compatibility
                    strategy = MemoryStrategyType.fromString(parser.text());
                    break;
                case NAMESPACE_FIELD:
                    namespace = new ArrayList<>();
                    ensureExpectedToken(XContentParser.Token.START_ARRAY, parser.currentToken(), parser);
                    while (parser.nextToken() != XContentParser.Token.END_ARRAY) {
                        namespace.add(parser.text());
                    }
                    break;
                default:
                    parser.skipChildren();
                    break;
            }
        }

        // Generate ID if not provided: strategy_name + UUID
        if (id == null && strategy != null) {
            id = strategy.getValue().toLowerCase() + "_" + UUID.randomUUID().toString();
        } else if (id == null) {
            id = UUID.randomUUID().toString();
        }

        return MemoryStrategy.builder().id(id).enabled(enabled).strategy(strategy).namespace(namespace).build();
    }

}
