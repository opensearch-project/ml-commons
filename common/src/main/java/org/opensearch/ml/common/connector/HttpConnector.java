/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.connector;

import lombok.NoArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.text.StringSubstitutor;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.common.io.stream.StreamInput;
import org.opensearch.common.io.stream.StreamOutput;
import org.opensearch.commons.authuser.User;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.AccessMode;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import static org.opensearch.common.xcontent.XContentParserUtils.ensureExpectedToken;
import static org.opensearch.ml.common.connector.ConnectorProtocols.HTTP;
import static org.opensearch.ml.common.utils.StringUtils.getParameterMap;
import static org.opensearch.ml.common.utils.StringUtils.isJson;

@Log4j2
@NoArgsConstructor
@org.opensearch.ml.common.annotation.Connector(HTTP)
public class HttpConnector extends AbstractConnector {
    public static final String CREDENTIAL_FIELD = "credential";
    public static final String RESPONSE_FILTER_FIELD = "response_filter";
    public static final String PARAMETERS_FIELD = "parameters";
    public static final String SERVICE_NAME_FIELD = "service_name";
    public static final String REGION_FIELD = "region";

    //TODO: add RequestConfig like request time out,

    public HttpConnector(String protocol, XContentParser parser) throws IOException {
        this.protocol = protocol;

        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.currentToken(), parser);
        while (parser.nextToken() != XContentParser.Token.END_OBJECT) {
            String fieldName = parser.currentName();
            parser.nextToken();

            switch (fieldName) {
                case NAME_FIELD:
                    name = parser.text();
                    break;
                case VERSION_FIELD:
                    version = parser.text();
                    break;
                case DESCRIPTION_FIELD:
                    description = parser.text();
                    break;
                case PROTOCOL_FIELD:
                    protocol = parser.text();
                    break;
                case PARAMETERS_FIELD:
                    Map<String, Object> map = parser.map();
                    parameters = getParameterMap(map);
                    break;
                case CREDENTIAL_FIELD:
                    credential = new HashMap<>();
                    credential.putAll(parser.mapStrings());
                    break;
                case ACTIONS_FIELD:
                    actions = new ArrayList<>();
                    ensureExpectedToken(XContentParser.Token.START_ARRAY, parser.currentToken(), parser);
                    while (parser.nextToken() != XContentParser.Token.END_ARRAY) {
                        actions.add(ConnectorAction.parse(parser));
                    }
                    break;
                case BACKEND_ROLES_FIELD:
                    ensureExpectedToken(XContentParser.Token.START_ARRAY, parser.currentToken(), parser);
                    backendRoles = new ArrayList<>();
                    while (parser.nextToken() != XContentParser.Token.END_ARRAY) {
                        backendRoles.add(parser.text());
                    }
                    break;
                case OWNER_FIELD:
                    owner = User.parse(parser);
                    break;
                case ACCESS_FIELD:
                    access = AccessMode.from(parser.text());
                    break;
                case CREATED_TIME_FIELD:
                    createdTime = Instant.ofEpochMilli(parser.longValue());
                    break;
                case LAST_UPDATED_TIME_FIELD:
                    lastUpdateTime = Instant.ofEpochMilli(parser.longValue());
                    break;
                default:
                    parser.skipChildren();
                    break;
            }
        }
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        if (name != null) {
            builder.field(NAME_FIELD, name);
        }
        if (version != null) {
            builder.field(VERSION_FIELD, version);
        }
        if (description != null) {
            builder.field(DESCRIPTION_FIELD, description);
        }
        if (protocol != null) {
            builder.field(PROTOCOL_FIELD, protocol);
        }
        if (parameters != null) {
            builder.field(PARAMETERS_FIELD, parameters);
        }
        if (credential != null) {
            builder.field(CREDENTIAL_FIELD, credential);
        }
        if (actions != null) {
            builder.field(ACTIONS_FIELD, actions);
        }
        if (backendRoles != null) {
            builder.field(BACKEND_ROLES_FIELD, backendRoles);
        }
        if (owner != null) {
            builder.field(OWNER_FIELD, owner);
        }
        if (access != null) {
            builder.field(ACCESS_FIELD, access.getValue());
        }
        if (createdTime != null) {
            builder.field(CREATED_TIME_FIELD, createdTime.toEpochMilli());
        }
        if (lastUpdateTime != null) {
            builder.field(LAST_UPDATED_TIME_FIELD, lastUpdateTime.toEpochMilli());
        }
        builder.endObject();
        return builder;
    }

    public HttpConnector(StreamInput input) throws IOException {
        this.protocol = input.readString();
        this.name = input.readOptionalString();
        this.version = input.readOptionalString();
        this.description = input.readOptionalString();
        if (input.readBoolean()) {
            parameters = input.readMap(StreamInput::readString, StreamInput::readString);
        }
        if (input.readBoolean()) {
            credential = input.readMap(StreamInput::readString, StreamInput::readString);
        }
        if (input.readBoolean()) {
            actions = new ArrayList<>();
            int size = input.readInt();
            for (int i = 0; i < size; i++) {
                actions.add(new ConnectorAction(input));
            }
        }
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeString(protocol);
        out.writeOptionalString(name);
        out.writeOptionalString(version);
        out.writeOptionalString(description);
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
        if (actions != null) {
            out.writeBoolean(true);
            out.writeInt(actions.size());
            for (ConnectorAction action : actions) {
                action.writeTo(out);
            }
        } else {
            out.writeBoolean(false);
        }
    }

    @Override
    public  <T> T createPredictPayload(Map<String, String> parameters) {
        Optional<ConnectorAction> predictAction = findPredictAction();
        if (predictAction.isPresent() && predictAction.get().getRequestBody() != null) {
            String payload = predictAction.get().getRequestBody();
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
        Optional<ConnectorAction> predictAction = findPredictAction();
        Map<String, String> headers = predictAction.isPresent() ? predictAction.get().getHeaders() : null;
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
        return findPredictAction().get().getMethod();
    }

    public String getPredictEndpoint() {
        return findPredictAction().get().getUrl();
    }
}
