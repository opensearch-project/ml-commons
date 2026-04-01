/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.agent;

import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;

import java.io.IOException;

import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

@EqualsAndHashCode
@Getter
public class MLMemorySpec implements ToXContentObject {
    public static final String MEMORY_TYPE_FIELD = "type";
    public static final String WINDOW_SIZE_FIELD = "window_size";
    public static final String SESSION_ID_FIELD = "session_id";
    public static final String MEMORY_CONTAINER_ID_FIELD = "memory_container_id";

    private String type;
    @Setter
    private String sessionId;
    private Integer windowSize;
    private String memoryContainerId;

    @Builder(toBuilder = true)
    public MLMemorySpec(String type, String sessionId, Integer windowSize, String memoryContainerId) {
        if (type == null) {
            throw new IllegalArgumentException("agent name is null");
        }
        this.type = type;
        this.sessionId = sessionId;
        this.windowSize = windowSize;
        this.memoryContainerId = memoryContainerId;
    }

    public MLMemorySpec(StreamInput input) throws IOException {
        type = input.readString();
        sessionId = input.readOptionalString();
        windowSize = input.readOptionalInt();
        memoryContainerId = input.readOptionalString();
    }

    public void writeTo(StreamOutput out) throws IOException {
        out.writeString(type);
        out.writeOptionalString(sessionId);
        out.writeOptionalInt(windowSize);
        out.writeOptionalString(memoryContainerId);
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        builder.field(MEMORY_TYPE_FIELD, type);
        if (windowSize != null) {
            builder.field(WINDOW_SIZE_FIELD, windowSize);
        }
        if (sessionId != null) {
            builder.field(SESSION_ID_FIELD, sessionId);
        }
        if (memoryContainerId != null) {
            builder.field(MEMORY_CONTAINER_ID_FIELD, memoryContainerId);
        }
        builder.endObject();
        return builder;
    }

    public static MLMemorySpec parse(XContentParser parser) throws IOException {
        String type = null;
        String sessionId = null;
        Integer windowSize = null;
        String memoryContainerId = null;

        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.currentToken(), parser);
        while (parser.nextToken() != XContentParser.Token.END_OBJECT) {
            String fieldName = parser.currentName();
            parser.nextToken();

            switch (fieldName) {
                case MEMORY_TYPE_FIELD:
                    type = parser.text();
                    break;
                case SESSION_ID_FIELD:
                    sessionId = parser.text();
                    break;
                case WINDOW_SIZE_FIELD:
                    windowSize = parser.intValue();
                    break;
                case MEMORY_CONTAINER_ID_FIELD:
                    memoryContainerId = parser.text();
                    break;
                default:
                    parser.skipChildren();
                    break;
            }
        }
        return MLMemorySpec.builder().type(type).sessionId(sessionId).windowSize(windowSize).memoryContainerId(memoryContainerId).build();
    }

    public static MLMemorySpec fromStream(StreamInput in) throws IOException {
        MLMemorySpec toolSpec = new MLMemorySpec(in);
        return toolSpec;
    }
}
