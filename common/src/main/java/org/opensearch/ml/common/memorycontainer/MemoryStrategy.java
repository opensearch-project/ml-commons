/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.memorycontainer;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.common.io.stream.Writeable;
import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;

@Getter
@Setter
@Builder
@EqualsAndHashCode
public class MemoryStrategy implements ToXContentObject, Writeable {

    private String id;
    private boolean enabled;
    private String type;
    private List<String> namespace;

    public MemoryStrategy(String id, boolean enabled, String type, List<String> namespace) {
        this.id = id;
        this.enabled = enabled;
        this.type = type;
        this.namespace = namespace;
    }

    public MemoryStrategy(StreamInput input) throws IOException {
        this.id = input.readString();
        this.enabled = input.readBoolean();
        this.type = input.readString();
        this.namespace = input.readStringList();
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeString(id);
        out.writeBoolean(enabled);
        out.writeString(type);
        out.writeStringCollection(namespace);
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();

        builder.field("id", id);
        builder.field("enabled", enabled);
        builder.field("type", type);
        builder.field("namespace", namespace);

        builder.endObject();
        return builder;
    }

    public static MemoryStrategy parse(XContentParser parser) throws IOException {
        String id = UUID.randomUUID().toString();
        boolean enabled = false;
        String type = null;
        List<String> namespace = null;

        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.currentToken(), parser);
        while (parser.nextToken() != XContentParser.Token.END_OBJECT) {
            String fieldName = parser.currentName();
            parser.nextToken();

            switch (fieldName) {
                case "id":
                    id = parser.text();
                    break;
                case "enabled":
                    // Skip this field - it's now auto-determined
                    enabled = parser.booleanValue();
                    break;
                case "type":
                    type = parser.text();
                    break;
                case "namespace":
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

        return MemoryStrategy
            .builder()
            .id(id)
            .enabled(enabled)
            .type(type)
            .namespace(namespace)
            .build();
    }

}
