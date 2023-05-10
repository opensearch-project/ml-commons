/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.connector.template;

import lombok.Data;
import org.opensearch.common.io.stream.NamedWriteable;
import org.opensearch.common.io.stream.StreamInput;
import org.opensearch.common.io.stream.StreamOutput;
import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;

import java.io.IOException;

import static org.opensearch.common.xcontent.XContentParserUtils.ensureExpectedToken;

@Data
public class APISchema implements ToXContentObject, NamedWriteable {
    public static final String PARSE_FIELD_NAME = "API_Schema";

    public static final String METHOD_FIELD = "Method";
    public static final String URL_FIELD = "URL";
    public static final String HEADERS_FIELD = "headers";
    public static final String REQUEST_BODY_FIELD = "request_body";

    private String method;
    private String url;
    private String headers;
    private String requestBody;

    public APISchema(String method, String url, String headers, String requestBody) {
        this.method = method;
        this.url = url;
        this.headers = headers;
        this.requestBody = requestBody;
    }

    public static APISchema parse(XContentParser parser) throws IOException {
        String method = null;
        String url = null;
        String headers = null;
        String requestBody = null;

        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.currentToken(), parser);
        while (parser.nextToken() != XContentParser.Token.END_OBJECT) { // Method
            String fieldName = parser.currentName();
            parser.nextToken(); // Post

            switch (fieldName) {
                case METHOD_FIELD:
                    method = parser.text();
                    break;
                case URL_FIELD:
                    url = parser.text();
                    break;
                case HEADERS_FIELD:
                    headers = parser.text();
                    break;
                case REQUEST_BODY_FIELD:
                    requestBody = parser.text();
                    break;
                default:
                    parser.skipChildren();
                    break;
            }
        }
        return new APISchema(method, url, headers, requestBody);
    }

    @Override
    public String getWriteableName() {
        return PARSE_FIELD_NAME;
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        if (method != null) {
            builder.field(METHOD_FIELD, method);
        }
        if (url != null) {
            builder.field(URL_FIELD, url);
        }
        if (headers != null) {
            builder.field(HEADERS_FIELD, headers);
        }
        if (requestBody != null) {
            builder.field(REQUEST_BODY_FIELD, requestBody);
        }
        builder.endObject();
        return builder;
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeOptionalString(method);
        out.writeOptionalString(url);
        out.writeOptionalString(headers);
        out.writeOptionalString(requestBody);
    }

    public APISchema(StreamInput input) throws IOException {
        method = input.readOptionalString();
        url = input.readOptionalString();
        headers = input.readOptionalString();
        requestBody = input.readOptionalString();
    }
}
