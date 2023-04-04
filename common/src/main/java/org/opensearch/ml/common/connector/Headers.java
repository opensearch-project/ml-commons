/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.connector;

import lombok.Builder;
import org.opensearch.common.io.stream.NamedWriteable;
import org.opensearch.common.io.stream.StreamInput;
import org.opensearch.common.io.stream.StreamOutput;
import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;

import java.io.IOException;

import static org.opensearch.common.xcontent.XContentParserUtils.ensureExpectedToken;

public class Headers implements ToXContentObject, NamedWriteable {
    public static final String PARSE_FIELD_NAME = "Header_Schema";

    public static final String CONTENT_TYPE_FIELD = "Content-Type";
    public static final String AUTHORIZATION_FIELD = "Authorization";

    private String contentType;
    private String authorization;

    @Builder(toBuilder = true)
    public Headers(String contentType, String authorization){
        this.contentType = contentType;
        this.authorization = authorization;
    }

    public static Headers parse(XContentParser parser) throws IOException {
        String contentType = null;
        String authorization = null;

        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.currentToken(), parser);
        while (parser.nextToken() != XContentParser.Token.END_OBJECT) {
            String fieldName = parser.currentName();
            parser.nextToken();

            switch (fieldName) {
                case CONTENT_TYPE_FIELD:
                    contentType = parser.text();
                    break;
                case AUTHORIZATION_FIELD:
                    authorization = parser.text();
                    break;
                default:
                    parser.skipChildren();
                    break;
            }
        }
        return new Headers(contentType, authorization);
    }

    @Override
    public String getWriteableName() {
        return PARSE_FIELD_NAME;
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        if (contentType != null) {
            builder.field(CONTENT_TYPE_FIELD, contentType);
        }
        if (authorization != null) {
            builder.field(AUTHORIZATION_FIELD, authorization);
        }
        builder.endObject();
        return builder;
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeOptionalString(contentType);
        out.writeOptionalString(authorization);
    }

    public Headers(StreamInput input) throws IOException {
        contentType = input.readOptionalString();
        authorization = input.readOptionalString();
    }
}
