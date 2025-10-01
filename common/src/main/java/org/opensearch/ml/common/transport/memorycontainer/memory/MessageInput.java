/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.memorycontainer.memory;

import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.CONTENT_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.ROLE_FIELD;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.common.io.stream.Writeable;
import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

/**
 * Represents a single message with role and content
 */
@Getter
@Setter
@Builder
public class MessageInput implements ToXContentObject, Writeable {

    private String role;
    private List<Map<String, Object>> content;

    public MessageInput(String role, List<Map<String, Object>> content) {
        this.role = role;
        this.content = content;

        if (role == null || content == null) {
            throw new IllegalArgumentException("Message must have role and content");
        }
    }

    public MessageInput(StreamInput in) throws IOException {
        this.role = in.readOptionalString();
        if (in.readBoolean()) {
            this.content = in.readList(StreamInput::readMap);
        }
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeOptionalString(role);
        if (content != null) {
            out.writeBoolean(true);
            out.writeCollection(content, StreamOutput::writeMap);
        } else {
            out.writeBoolean(false);
        }
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        if (role != null) {
            builder.field(ROLE_FIELD, role);
        }
        if (content != null) {
            builder.field(CONTENT_FIELD, content);
        }
        builder.endObject();
        return builder;
    }

    public static MessageInput parse(XContentParser parser) throws IOException {
        String role = null;
        List<Map<String, Object>> content = null;

        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.currentToken(), parser);
        while (parser.nextToken() != XContentParser.Token.END_OBJECT) {
            String fieldName = parser.currentName();
            parser.nextToken();

            switch (fieldName) {
                case ROLE_FIELD:
                    role = parser.text();
                    break;
                case CONTENT_FIELD:
                    ensureExpectedToken(XContentParser.Token.START_ARRAY, parser.currentToken(), parser);
                    content = new ArrayList<>();
                    while (parser.nextToken() != XContentParser.Token.END_ARRAY) {
                        content.add(parser.map());
                    }
                    break;
                default:
                    parser.skipChildren();
                    break;
            }
        }

        return MessageInput.builder().role(role).content(content).build();
    }
}
