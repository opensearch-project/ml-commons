/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.connector.template;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.text.StringSubstitutor;
import org.json.JSONObject;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.common.io.stream.StreamInput;
import org.opensearch.common.io.stream.StreamOutput;
import org.opensearch.commons.authuser.User;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.AccessMode;
import org.opensearch.ml.common.connector.AbstractConnector;
import org.opensearch.ml.common.connector.Connector;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.opensearch.common.xcontent.XContentParserUtils.ensureExpectedToken;
import static org.opensearch.ml.common.connector.template.APISchema.HEADERS_FIELD;
import static org.opensearch.ml.common.connector.template.APISchema.METHOD_FIELD;
import static org.opensearch.ml.common.connector.template.APISchema.REQUEST_BODY_FIELD;
import static org.opensearch.ml.common.connector.template.APISchema.URL_FIELD;
import static org.opensearch.ml.common.utils.StringUtils.fromJson;
import static org.opensearch.ml.common.utils.StringUtils.isJson;
import static org.opensearch.ml.common.utils.StringUtils.toJson;

public class DetachedConnector extends AbstractConnector {
    public static final String CONNECTOR_NAME_FIELD = "name";
    public static final String CONNECTOR_VERSION_FIELD = "version";
    public static final String CONNECTOR_DESCRIPTION_FIELD = "description";
    public static final String CONNECTOR_PROTOCOL_FIELD = "protocol";
    public static final String CONNECTOR_PARAMETERS_FIELD = "parameters";
    public static final String CONNECTOR_CREDENTIAL_FIELD = "credential";
    public static final String PREDICT_API_SCHEMA_FIELD = "predict";
    public static final String METADATA_API_SCHEMA_FIELD = "action";
    public static final String BACKEND_ROLES_FIELD = "backend_roles";
    public static final String OWNER_FIELD = "owner";
    public static final String ACCESS_FIELD = "access";
    public static final String CREATED_TIME_FIELD = "created_time";
    public static final String LAST_UPDATED_TIME_FIELD = "last_updated_time";

    private String name;
    private String version;
    private String description;
    private String protocol;
    private String parameterStr;
    private String credentialStr;
    @Getter
    private String predictAPI;
    @Getter
    private String metadataAPI;
    @Setter
    @Getter
    private List<String> backendRoles;
    @Setter
    @Getter
    private User owner;
    @Setter
    @Getter
    private AccessMode access;
    private Instant createdTime;
    private Instant lastUpdateTime;

    private Map<String, String> predictMap;

    @Builder(toBuilder = true)
    public DetachedConnector(String name,
                     String version,
                     String description,
                     String protocol,
                     String parameterStr,
                     String credentialStr,
                     String predictAPI,
                     String metadataAPI,
                     List<String> backendRoles,
                     User owner,
                     AccessMode access,
                     Instant createdTime,
                     Instant lastUpdateTime
    ) {
        this.name = name;
        this.version = version;
        this.description = description;
        this.protocol = protocol;
        this.parameterStr = parameterStr;
        this.credentialStr = credentialStr;
        this.predictAPI = predictAPI;
        this.metadataAPI = metadataAPI;
        this.backendRoles = backendRoles;
        this.owner = owner;
        this.access = access;
        this.createdTime = createdTime;
        this.lastUpdateTime = lastUpdateTime;
    }

    public DetachedConnector(StreamInput input) throws IOException {
        name = input.readString();
        version = input.readString();
        description = input.readString();
        protocol = input.readString();
        parameterStr = input.readOptionalString();
        credentialStr = input.readString();
        predictAPI = input.readOptionalString();
        metadataAPI = input.readOptionalString();
        backendRoles = input.readOptionalStringList();
        if (input.readBoolean()) {
            this.owner = new User(input);
        } else {
            this.owner = null;
        }
        if (input.readBoolean()) {
            this.access = input.readEnum(AccessMode.class);
        }
        createdTime = input.readOptionalInstant();
        lastUpdateTime = input.readOptionalInstant();
    }

    public void writeTo(StreamOutput out) throws IOException {
        out.writeString(name);
        out.writeString(version);
        out.writeString(description);
        out.writeString(protocol);
        out.writeOptionalString(parameterStr);
        out.writeString(credentialStr);
        out.writeOptionalString(predictAPI);
        out.writeOptionalString(metadataAPI);
        out.writeOptionalStringCollection(backendRoles);
        if (owner != null) {
            out.writeBoolean(true);
            owner.writeTo(out);
        } else {
            out.writeBoolean(false);
        }
        if (access != null) {
            out.writeBoolean(true);
            out.writeEnum(access);
        } else {
            out.writeBoolean(false);
        }
        out.writeOptionalInstant(createdTime);
        out.writeOptionalInstant(lastUpdateTime);
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        if (name != null) {
            builder.field(CONNECTOR_NAME_FIELD, name);
        }
        if (version != null) {
            builder.field(CONNECTOR_VERSION_FIELD, version);
        }
        if (description != null) {
            builder.field(CONNECTOR_DESCRIPTION_FIELD, description);
        }
        if (protocol != null) {
            builder.field(CONNECTOR_PROTOCOL_FIELD, protocol);
        }
        if (parameterStr != null) {
            builder.field(CONNECTOR_PARAMETERS_FIELD, parameterStr);
        }
        if (credentialStr != null) {
            builder.field(CONNECTOR_CREDENTIAL_FIELD, credentialStr);
        }
        if (predictAPI != null) {
            builder.field(PREDICT_API_SCHEMA_FIELD, predictAPI);
        }
        if (metadataAPI != null) {
            builder.field(METADATA_API_SCHEMA_FIELD, metadataAPI);
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

    public static DetachedConnector parse(XContentParser parser) throws IOException {
        String name = null;
        String version = null;
        String description = null;
        String protocol = null;
        String parameterStr = null;
        String credentialStr = null;
        String predictAPI = null;
        String metadataAPI = null;
        List<String> backendRoles = null;
        User owner = null;
        AccessMode access = null;
        Instant createdTime = null;
        Instant lastUpdateTime = null;

        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.currentToken(), parser);
        while (parser.nextToken() != XContentParser.Token.END_OBJECT) {
            String fieldName = parser.currentName();
            parser.nextToken();

            switch (fieldName) {
                case CONNECTOR_NAME_FIELD:
                    name = parser.text();
                    break;
                case CONNECTOR_VERSION_FIELD:
                    version = parser.text();
                    break;
                case CONNECTOR_DESCRIPTION_FIELD:
                    description = parser.text();
                    break;
                case CONNECTOR_PROTOCOL_FIELD:
                    protocol = parser.text();
                    break;
                case CONNECTOR_PARAMETERS_FIELD:
                    parameterStr = parser.text();
                    break;
                case CONNECTOR_CREDENTIAL_FIELD:
                    credentialStr = parser.text();
                    break;
                case PREDICT_API_SCHEMA_FIELD:
                    predictAPI = parser.text();
                    break;
                case METADATA_API_SCHEMA_FIELD:
                    metadataAPI = parser.text();
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

        return DetachedConnector.builder()
                .name(name)
                .version(version)
                .description(description)
                .protocol(protocol)
                .parameterStr(parameterStr)
                .credentialStr(credentialStr)
                .predictAPI(predictAPI)
                .metadataAPI(metadataAPI)
                .backendRoles(backendRoles)
                .owner(owner)
                .access(access)
                .createdTime(createdTime)
                .lastUpdateTime(lastUpdateTime)
                .build();
    }

    public static DetachedConnector fromStream(StreamInput in) throws IOException {
        DetachedConnector mlConnector = new DetachedConnector(in);
        return mlConnector;
    }

    @Override
    public void encrypt(Function<String, String> function) {
        HashMap<String, String> credentialMap = new HashMap<>();
        JSONObject jObject = new JSONObject(credentialStr);
        Iterator<?> keys = jObject.keys();

        while( keys.hasNext() ){
            String key = (String)keys.next();
            String value = function.apply(jObject.getString(key));
            credentialMap.put(key, value);
        }

        credentialStr = toJson(credentialMap);
    }

    @Override
    public String getPredictEndpoint() {
        Map<String, String> predictSchema = fromJson(predictAPI);
        return parseURL(predictSchema.get(URL_FIELD));
    }

    @Override
    public void decrypt(Function<String, String> function) {
        Map<String, String> credentialMap = fromJson(credentialStr);

        Map<String, String> decrypted = new HashMap<>();
        for (String key : credentialMap.keySet()) {
            decrypted.put(key, function.apply(credentialMap.get(key)));
        }
        setDecryptedCredential(decrypted);
        if (parameters == null) {
            parameters = fromJson(parameterStr);
        }
        Map<String, String> headers = fromJson(getPredictMap().get(HEADERS_FIELD));
        this.decryptedHeaders = createPredictDecryptedHeaders(headers);
    }

    @Override
    public Connector cloneConnector() {
        try (BytesStreamOutput bytesStreamOutput = new BytesStreamOutput()){
            this.writeTo(bytesStreamOutput);
            StreamInput streamInput = bytesStreamOutput.bytes().streamInput();
            return new DetachedConnector(streamInput);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }

    @Override
    public String getName() {
        return this.protocol;
    }

    public void removeCredential() {
        this.credentialStr = null;
    }

    @Override
    public <T> T createPredictPayload(Map<String, String> parameters) {
        if (predictAPI != null) {
            Map<String, String> predictSchema = getPredictMap();
            String payload = predictSchema.get(REQUEST_BODY_FIELD);
            StringSubstitutor substitutor = new StringSubstitutor(parameters, "${parameters.", "}");
            payload = substitutor.replace(payload);

            if (!isJson(payload)) {
                throw new IllegalArgumentException("Invalid JSON in payload!");
            }
            return (T) payload;
        }
        return (T) parameters.get("http_body");
    }

    public String getPredictHttpMethod() {
        return getPredictMap().get(METHOD_FIELD);
    }

    private Map<String, String> getPredictMap() {
        if (predictMap == null) {
            predictMap = fromJson(predictAPI);
        }

        return predictMap;
    }


}
