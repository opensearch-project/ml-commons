/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.memorycontainer.memory;

import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.CONTENT_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.ROLE_FIELD;

import java.io.IOException;

import org.apache.commons.lang3.StringUtils;
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

    private String role;    // Optional when infer=true
    private String content; // Required

    public MessageInput(String role, String content) {
        if (StringUtils.isBlank(content)) {
            throw new IllegalArgumentException("Content is required");
        }
        this.role = role;
        this.content = content;
    }

    public MessageInput(StreamInput in) throws IOException {
        this.role = in.readOptionalString();
        this.content = in.readString();
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeOptionalString(role);
        out.writeString(content);
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        if (role != null) {
            builder.field(ROLE_FIELD, role);
        }
        builder.field(CONTENT_FIELD, content);
        builder.endObject();
        return builder;
    }

    public static MessageInput parse(XContentParser parser) throws IOException {
        String role = null;
        String content = null;

        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.currentToken(), parser);
        while (parser.nextToken() != XContentParser.Token.END_OBJECT) {
            String fieldName = parser.currentName();
            parser.nextToken();

            switch (fieldName) {
                case ROLE_FIELD:
                    role = parser.text();
                    break;
                case CONTENT_FIELD:
                    content = parser.text();
                    break;
                default:
                    parser.skipChildren();
                    break;
            }
        }

        return new MessageInput(role, content);
    }
}
