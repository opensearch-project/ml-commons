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

public class RequestBody implements ToXContentObject, NamedWriteable {
    public static final String PARSE_FIELD_NAME = "Request_Body_Schema";

    public static final String MODEL_FIELD = "model";
    public static final String MESSAGE_FIELD = "messages";

    private String model;
    private String messages;

    @Builder(toBuilder = true)
    public RequestBody(String model, String messages) {
        this.model = model;
        this.messages = messages;
    }

    public static RequestBody parse(XContentParser parser) throws IOException {
        String model = null;
        String messages = null;

        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.currentToken(), parser);
        while (parser.nextToken() != XContentParser.Token.END_OBJECT) {
            String fieldName = parser.currentName();
            parser.nextToken();

            switch (fieldName) {
                case MODEL_FIELD:
                    model = parser.text();
                    break;
                case MESSAGE_FIELD:
                    messages = parser.text();
                    break;
                default:
                    parser.skipChildren();
                    break;
            }
        }
        return new RequestBody(model, messages);
    }

    @Override
    public String getWriteableName() {
        return PARSE_FIELD_NAME;
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        if (model != null) {
            builder.field(MODEL_FIELD, model);
        }
        if (messages != null) {
            builder.field(MESSAGE_FIELD, messages);
        }
        builder.endObject();
        return builder;
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeOptionalString(model);
        out.writeOptionalString(messages);
    }

    public RequestBody(StreamInput input) throws IOException {
        model = input.readOptionalString();
        messages = input.readOptionalString();
    }
}
