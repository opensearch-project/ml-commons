/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.connector.template;

import lombok.Data;
import lombok.Getter;
import org.apache.commons.text.StringSubstitutor;
import org.json.JSONObject;
import org.opensearch.common.io.stream.NamedWriteable;
import org.opensearch.common.io.stream.StreamInput;
import org.opensearch.common.io.stream.StreamOutput;
import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;

import java.io.IOException;
import java.security.AccessController;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.HashMap;
import java.util.Map;

import static org.opensearch.common.xcontent.XContentParserUtils.ensureExpectedToken;
import static org.opensearch.ml.common.utils.StringUtils.gson;

@Data
public class APISchema implements ToXContentObject, NamedWriteable {
    public static final String PARSE_FIELD_NAME = "API_Schema";

    public static final String METHOD_FIELD = "method";
    public static final String URL_FIELD = "url";
    public static final String HEADERS_FIELD = "headers";
    public static final String REQUEST_BODY_FIELD = "request_body";
    @Getter
    protected Map<String, String> decryptedHeaders;

    private String method;
    private String url;
    private Map<String, String> headers;
    private String requestBody;

    public APISchema(String method, String url, Map<String, String> headers, String requestBody) {
        this.method = method;
        this.url = url;
        this.headers = headers;
        this.requestBody = requestBody;
    }

    public static APISchema parse(XContentParser parser) throws IOException {
        String method = null;
        String url = null;
        Map<String, ?> headerObjs = new HashMap<>();
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
                    headerObjs = parser.map();
                    break;
                case REQUEST_BODY_FIELD:
                    requestBody = parser.text();
                    break;
                default:
                    parser.skipChildren();
                    break;
            }
        }
        Map<String, String> headers = new HashMap<>();
        for (String key : headerObjs.keySet()) {
            Object value = headerObjs.get(key);
            try {
                AccessController.doPrivileged((PrivilegedExceptionAction<Void>) () -> {
                    if (value instanceof String) {
                        headers.put(key, (String)value);
                    } else {
                        headers.put(key, gson.toJson(value));
                    }
                    return null;
                });
            } catch (PrivilegedActionException e) {
                throw new RuntimeException(e);
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
        if (headers != null) {
            out.writeBoolean(true);
            out.writeMap(headers, StreamOutput::writeString, StreamOutput::writeString);
        } else {
            out.writeBoolean(false);
        }
        out.writeOptionalString(requestBody);
    }

    public APISchema(StreamInput input) throws IOException {
        method = input.readOptionalString();
        url = input.readOptionalString();
        if (headers != null) {
            headers = input.readMap(s -> s.readString(), s-> s.readString());
        }
        requestBody = input.readOptionalString();
    }

    public void decrypt(Map<String, String> decryptedCredential, Map<String, String> parameters) {
        Map<String, String> decryptedHeaders = new HashMap<>();
        StringSubstitutor substitutor = new StringSubstitutor(decryptedCredential, "${credential.", "}");
        for (String key : headers.keySet()) {
            decryptedHeaders.put(key, substitutor.replace(headers.get(key)));
        }
        if (parameters != null && parameters.size() > 0) {
            substitutor = new StringSubstitutor(parameters, "${parameters.", "}");
            for (String key : decryptedHeaders.keySet()) {
                decryptedHeaders.put(key, substitutor.replace(decryptedHeaders.get(key)));
            }
        }
        this.decryptedHeaders = decryptedHeaders;
    }

    public String toString() {
        JSONObject api = new JSONObject();
        api.put(METHOD_FIELD, this.method);
        api.put(URL_FIELD, this.url);
        api.put(HEADERS_FIELD, this.headers.toString());
        api.put(REQUEST_BODY_FIELD, this.requestBody);
        return api.toString();
    }
}
