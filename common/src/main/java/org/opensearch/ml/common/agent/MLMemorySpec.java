/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.agent;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;

import java.io.IOException;

import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;


@Getter
public class MLMemorySpec implements ToXContentObject {
    public static final String MEMORY_TYPE_FIELD = "type";
    public static final String WINDOW_SIZE_FIELD = "window_size";
    public static final String SESSION_ID_FIELD = "session_id";

    private String type;
    @Setter
    private String sessionId;
    private Integer windowSize;


    @Builder(toBuilder = true)
    public MLMemorySpec(String type,
                        String sessionId,
                        Integer windowSize) {
        if (type == null) {
            throw new IllegalArgumentException("agent name is null");
        }
        this.type = type;
        this.sessionId = sessionId;
        this.windowSize = windowSize;
    }

    public MLMemorySpec(StreamInput input) throws IOException{
        type = input.readString();
        sessionId = input.readOptionalString();
        windowSize = input.readOptionalInt();
    }

    public void writeTo(StreamOutput out) throws IOException {
        out.writeString(type);
        out.writeOptionalString(sessionId);
        out.writeOptionalInt(windowSize);
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
        builder.endObject();
        return builder;
    }

    public static MLMemorySpec parse(XContentParser parser) throws IOException {
        String type = null;
        String sessionId = null;
        Integer windowSize = null;

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
                default:
                    parser.skipChildren();
                    break;
            }
        }
        return MLMemorySpec.builder()
                .type(type)
                .sessionId(sessionId)
                .windowSize(windowSize)
                .build();
    }

    public static MLMemorySpec fromStream(StreamInput in) throws IOException {
        MLMemorySpec toolSpec = new MLMemorySpec(in);
        return toolSpec;
    }
}
