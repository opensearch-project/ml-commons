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
    private Map<String, String> strategyConfig;

    public MemoryStrategy(String id, boolean enabled, String type, List<String> namespace, Map<String, String> strategyConfig) {
        this.id = id;
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
            this.strategyConfig = input.readMap(StreamInput::readString, StreamInput::readString);
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
            out.writeMap(strategyConfig, StreamOutput::writeString, StreamOutput::writeString);
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
        Map<String, String> strategyConfig = new HashMap<>();

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
                    strategyConfig.putAll(parser.mapStrings());
                    break;
                default:
                    parser.skipChildren();
                    break;
            }
        }

        // Generate ID with type prefix if not provided
        if (id == null && type != null) {
            id = type.toLowerCase() + "_" + UUID.randomUUID().toString();
        } else if (id == null) {
            id = UUID.randomUUID().toString();
        }

        return MemoryStrategy.builder().id(id).enabled(enabled).type(type).namespace(namespace).strategyConfig(strategyConfig).build();
    }

}
