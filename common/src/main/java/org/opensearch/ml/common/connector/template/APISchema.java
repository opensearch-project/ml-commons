/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.connector.template;

import lombok.Data;
import lombok.Getter;
import org.json.JSONObject;
import org.opensearch.common.io.stream.NamedWriteable;
import org.opensearch.common.io.stream.StreamInput;
import org.opensearch.common.io.stream.StreamOutput;
import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static org.opensearch.common.xcontent.XContentParserUtils.ensureExpectedToken;

@Data
public class APISchema implements ToXContentObject, NamedWriteable {
    public static final String PARSE_FIELD_NAME = "API_Schema";

    public static final String METHOD_FIELD = "method";
    public static final String URL_FIELD = "url";
    public static final String HEADERS_FIELD = "headers";
    public static final String REQUEST_BODY_FIELD = "request_body";
    public static final String PRE_PROCESS_FUNCTION_FIELD = "pre_process_function";
    public static final String POST_PROCESS_FUNCTION_FIELD = "post_process_function";
    @Getter
    protected Map<String, String> decryptedHeaders;

    private String method;
    private String url;
    private Map<String, String> headers;
    private String requestBody;
    @Getter
    private String preProcessFunction;
    @Getter
    private String postProcessFunction;

    public APISchema(String method, String url, Map<String, String> headers, String requestBody, String preProcessFunction, String postProcessFunction) {
        this.method = method;
        this.url = url;
        this.headers = headers;
        this.requestBody = requestBody;
        this.preProcessFunction = preProcessFunction;
        this.postProcessFunction = postProcessFunction;
    }

    public static APISchema parse(XContentParser parser) throws IOException {
        String method = null;
        String url = null;
        Map<String, String> headers = new HashMap<>();
        String requestBody = null;
        String preProcessFunction = null;
        String postProcessFunction = null;

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
                    headers = parser.mapStrings();
                    break;
                case REQUEST_BODY_FIELD:
                    requestBody = parser.text();
                    break;
                case PRE_PROCESS_FUNCTION_FIELD:
                    preProcessFunction = parser.text();
                    break;
                case POST_PROCESS_FUNCTION_FIELD:
                    postProcessFunction = parser.text();
                    break;
                default:
                    parser.skipChildren();
                    break;
            }
        }
        return new APISchema(method, url, headers, requestBody, preProcessFunction, postProcessFunction);
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
        if (preProcessFunction != null) {
            builder.field(PRE_PROCESS_FUNCTION_FIELD, preProcessFunction);
        }
        if (postProcessFunction != null) {
            builder.field(POST_PROCESS_FUNCTION_FIELD, postProcessFunction);
        }
        builder.endObject();
        return builder;
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeOptionalString(method);
        out.writeOptionalString(url);
        if (headers != null) {
            out.writeBoolean(true);
            out.writeMap(headers, StreamOutput::writeString, StreamOutput::writeString);
        } else {
            out.writeBoolean(false);
        }
        out.writeOptionalString(requestBody);
        out.writeOptionalString(preProcessFunction);
        out.writeOptionalString(postProcessFunction);
    }

    public APISchema(StreamInput input) throws IOException {
        method = input.readOptionalString();
        url = input.readOptionalString();
        if (headers != null) {
            headers = input.readMap(s -> s.readString(), s-> s.readString());
        }
        requestBody = input.readOptionalString();
        preProcessFunction = input.readOptionalString();
        postProcessFunction = input.readOptionalString();
    }

    public String toString() {
        JSONObject api = new JSONObject();
        api.put(METHOD_FIELD, this.method);
        api.put(URL_FIELD, this.url);
        api.put(HEADERS_FIELD, new JSONObject(this.headers).toString());
        api.put(REQUEST_BODY_FIELD, this.requestBody);
        api.put(PRE_PROCESS_FUNCTION_FIELD, this.preProcessFunction);
        api.put(POST_PROCESS_FUNCTION_FIELD, this.postProcessFunction);
        return api.toString();
    }
}
