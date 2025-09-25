/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.memorycontainer.memory;

import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.CONTENT_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.CONTENT_TEXT_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.ROLE_FIELD;

import java.io.IOException;
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

    private String role;    // Optional when infer=true
    private String contentText; // Required

    private Map<String, Object> content;

    public MessageInput(String role, String contentText, Map<String, Object> content) {
        this.role = role;
        this.contentText = contentText;
        this.content = content;

        if (role == null || (content == null && contentText == null)) {
            throw new IllegalArgumentException("Message must have role and content");
        }
    }

    public Object getContent() {
        if (content != null) {
            return content;
        } else {
            return contentText;
        }
    }

    public MessageInput(StreamInput in) throws IOException {
        this.role = in.readOptionalString();
        this.contentText = in.readOptionalString();
        if (in.readBoolean()) {
            this.content = in.readMap();
        }
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeOptionalString(role);
        out.writeOptionalString(contentText);
        if (contentText != null) {
            out.writeBoolean(true);
            out.writeMap(content);
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
        if (contentText != null) {
            builder.field(CONTENT_TEXT_FIELD, contentText);
        }
        if (content != null) {
            builder.field(CONTENT_FIELD, content);
        }

        builder.endObject();
        return builder;
    }

    public static MessageInput parse(XContentParser parser) throws IOException {
        String role = null;
        String contentText = null;
        Map<String, Object> content = null;

        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.currentToken(), parser);
        while (parser.nextToken() != XContentParser.Token.END_OBJECT) {
            String fieldName = parser.currentName();
            parser.nextToken();

            switch (fieldName) {
                case ROLE_FIELD:
                    role = parser.text();
                    break;
                case CONTENT_TEXT_FIELD:
                    contentText = parser.text();
                    break;
                case CONTENT_FIELD:
                    if (!parser.currentToken().equals(XContentParser.Token.START_OBJECT)) {
                        contentText = parser.text();
                    } else {
                        content = parser.map();
                    }
                    break;
                default:
                    parser.skipChildren();
                    break;
            }
        }

        return MessageInput.builder().role(role).contentText(contentText).content(content).build();
    }
}
