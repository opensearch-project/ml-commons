/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.connector;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.text.StringSubstitutor;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.common.io.stream.StreamInput;
import org.opensearch.common.io.stream.StreamOutput;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import static org.opensearch.common.xcontent.XContentParserUtils.ensureExpectedToken;
import static org.opensearch.ml.common.connector.ConnectorNames.HTTP;
import static org.opensearch.ml.common.utils.StringUtils.getParameterMap;
import static org.opensearch.ml.common.utils.StringUtils.isJson;

@Log4j2
@NoArgsConstructor
@org.opensearch.ml.common.annotation.Connector(HTTP)
public class HttpConnector extends AbstractConnector {
    public static final String HTTP_METHOD_FIELD = "http_method";
    public static final String ENDPOINT_FIELD = "endpoint";
    public static final String CREDENTIAL_FIELD = "credential";
    public static final String HEADERS_FIELD = "headers";
    public static final String BODY_TEMPLATE_FIELD = "body_template";
    public static final String RESPONSE_FILTER_FIELD = "response_filter";
    public static final String PARAMETERS_FIELD = "parameters";
    public static final String PRE_PROCESS_FUNCTION_FIELD = "pre_process_function";
    public static final String POST_PROCESS_FUNCTION_FIELD = "post_process_function";
    public static final String SERVICE_NAME_FIELD = "service_name";
    public static final String REGION_FIELD = "region";

    @Getter
    protected String name;
    @Getter
    protected String endpoint;

    protected Map<String, String> headers;

    @Getter
    protected String bodyTemplate;

    //TODO: add RequestConfig like request time out,

    public HttpConnector(String name, XContentParser parser) throws IOException {
        this.name = name;
        headers = new HashMap<>();

        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.currentToken(), parser);
        while (parser.nextToken() != XContentParser.Token.END_OBJECT) {
            String fieldName = parser.currentName();
            parser.nextToken();

            switch (fieldName) {
                case HTTP_METHOD_FIELD:
                    httpMethod = parser.text();
                    break;
                case ENDPOINT_FIELD:
                    endpoint = parser.text();
                    break;
                case PARAMETERS_FIELD:
                    Map<String, Object> map = parser.map();
                    parameters = getParameterMap(map);
                    break;
                case CREDENTIAL_FIELD:
                    credential = new HashMap<>();
                    credential.putAll(parser.mapStrings());
                    break;
                case HEADERS_FIELD:
                    headers.putAll(parser.mapStrings());
                    break;
                case BODY_TEMPLATE_FIELD:
                    bodyTemplate = parser.text();
                    break;
                default:
                    parser.skipChildren();
                    break;
            }
        }
        if (endpoint == null) {
            throw new IllegalArgumentException("wrong input");
        }
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        builder.field(this.name);
        builder.startObject();
        if (httpMethod != null) {
            builder.field(HTTP_METHOD_FIELD, httpMethod);
        }
        if (endpoint != null) {
            builder.field(ENDPOINT_FIELD, endpoint);
        }
        if (parameters != null) {
            builder.field(PARAMETERS_FIELD, parameters);
        }
        if (credential != null) {
            builder.field(CREDENTIAL_FIELD, credential);
        }
        if (headers != null) {
            builder.field(HEADERS_FIELD, headers);
        }
        if (bodyTemplate != null) {
            builder.field(BODY_TEMPLATE_FIELD, bodyTemplate);
        }
        builder.endObject();
        builder.endObject();
        return builder;
    }

    public HttpConnector(StreamInput input) throws IOException {
        this.name = input.readString();
        endpoint = input.readOptionalString();
        httpMethod = input.readOptionalString();
        if (input.readBoolean()) {
            parameters = input.readMap(StreamInput::readString, StreamInput::readString);
        }
        if (input.readBoolean()) {
            credential = input.readMap(StreamInput::readString, StreamInput::readString);
        }
        if (input.readBoolean()) {
            headers = input.readMap(StreamInput::readString, StreamInput::readString);
        }
        bodyTemplate = input.readOptionalString();
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeString(getName());
        out.writeOptionalString(getEndpoint());
        out.writeOptionalString(httpMethod);
        if (parameters != null) {
            out.writeBoolean(true);
            out.writeMap(parameters, StreamOutput::writeString, StreamOutput::writeString);
        } else {
            out.writeBoolean(false);
        }
        if (credential != null) {
            out.writeBoolean(true);
            out.writeMap(credential, StreamOutput::writeString, StreamOutput::writeString);
        } else {
            out.writeBoolean(false);
        }
        if (headers != null) {
            out.writeBoolean(true);
            out.writeMap(headers, StreamOutput::writeString, StreamOutput::writeString);
        } else {
            out.writeBoolean(false);
        }
        out.writeOptionalString(bodyTemplate);
    }

    @Override
    public  <T> T createPredictPayload(Map<String, String> parameters) {
        if (bodyTemplate != null) {
            String payload = bodyTemplate;
            StringSubstitutor substitutor = new StringSubstitutor(parameters, "${parameters.", "}");
            payload = substitutor.replace(payload);

            if (!isJson(payload)) {
                throw new IllegalArgumentException("Invalid JSON in payload");
            }
            return (T) payload;
        }
        return (T) parameters.get("http_body");
    }

    @Override
    public void decrypt(Function<String, String> function) {
        Map<String, String> decrypted = new HashMap<>();
        for (String key : credential.keySet()) {
            decrypted.put(key, function.apply(credential.get(key)));
        }
        this.decryptedCredential = decrypted;
        this.decryptedHeaders = createPredictDecryptedHeaders(headers);
    }

    @Override
    public Connector cloneConnector() {
        try (BytesStreamOutput bytesStreamOutput = new BytesStreamOutput()){
            this.writeTo(bytesStreamOutput);
            StreamInput streamInput = bytesStreamOutput.bytes().streamInput();
            return new HttpConnector(streamInput);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void encrypt(Function<String, String> function) {
        for (String key : credential.keySet()) {
            String encrypted = function.apply(credential.get(key));
            credential.put(key, encrypted);
        }
    }

    public void removeCredential() {
        this.credential = null;
        this.decryptedCredential = null;
    }

    public String getPredictHttpMethod() {
        return httpMethod;
    }

    public String getPredictEndpoint() {
        return getEndpoint();
    }
}
