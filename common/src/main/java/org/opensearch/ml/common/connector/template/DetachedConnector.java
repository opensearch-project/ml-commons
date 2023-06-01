/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.connector.template;

import com.google.gson.reflect.TypeToken;
import lombok.Builder;
import lombok.Getter;
import org.json.JSONObject;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.common.io.stream.StreamInput;
import org.opensearch.common.io.stream.StreamOutput;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.connector.AbstractConnector;
import org.opensearch.ml.common.connector.Connector;

import java.io.IOException;
import java.lang.reflect.Type;
import java.time.Instant;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.function.Function;

import static org.opensearch.common.xcontent.XContentParserUtils.ensureExpectedToken;
import static org.opensearch.ml.common.utils.StringUtils.gson;
import static org.opensearch.ml.common.utils.StringUtils.toJson;

public class DetachedConnector extends AbstractConnector {
    public static final String CONNECTOR_NAME_FIELD = "name";
    public static final String CONNECTOR_VERSION_FIELD = "version";
    public static final String CONNECTOR_DESCRIPTION_FIELD = "description";
    public static final String CONNECTOR_PROTOCOL_FIELD = "protocol";
    public static final String CONNECTOR_PARAMETERS_FIELD = "parameters";
    public static final String CONNECTOR_CREDENTIAL_FIELD = "credential";
    public static final String PREDICT_API_SCHEMA_FIELD = "predict_API";
    public static final String METADATA_API_SCHEMA_FIELD = "metadata_API";
    public static final String CONNECTOR_STATE_FIELD = "connector_state";
    public static final String CREATED_TIME_FIELD = "created_time";
    public static final String LAST_UPDATED_TIME_FIELD = "last_updated_time";

    protected Map<String, String> decryptedCredential;

    private String name;
    private String version;
    private String description;
    private String protocol;
    private String parameters;
    private String credential;
    @Getter
    private String predictAPI;
    @Getter
    private String metadataAPI;
    private ConnectorState connectorState;
    private Instant createdTime;
    private Instant lastUpdateTime;

    @Builder(toBuilder = true)
    public DetachedConnector(String name,
                     String version,
                     String description,
                     String protocol,
                     String parameters,
                     String credential,
                     String predictAPI,
                     String metadataAPI,
                     ConnectorState connectorState,
                     Instant createdTime,
                     Instant lastUpdateTime
    ) {
        this.name = name;
        this.version = version;
        this.description = description;
        this.protocol = protocol;
        this.parameters = parameters;
        this.credential = credential;
        this.predictAPI = predictAPI;
        this.metadataAPI = metadataAPI;
        this.connectorState = connectorState;
        this.createdTime = createdTime;
        this.lastUpdateTime = lastUpdateTime;
    }

    public DetachedConnector(StreamInput input) throws IOException {
        name = input.readString();
        version = input.readString();
        description = input.readString();
        protocol = input.readString();
        parameters = input.readOptionalString();
        credential = input.readString();
        predictAPI = input.readOptionalString();
        metadataAPI = input.readOptionalString();
        if (input.readBoolean()) {
            connectorState = input.readEnum(ConnectorState.class);
        }
        createdTime = input.readOptionalInstant();
        lastUpdateTime = input.readOptionalInstant();
    }

    public void writeTo(StreamOutput out) throws IOException {
        out.writeString(name);
        out.writeString(version);
        out.writeString(description);
        out.writeString(protocol);
        out.writeOptionalString(parameters);
        out.writeString(credential);
        out.writeOptionalString(predictAPI);
        out.writeOptionalString(metadataAPI);
        if (connectorState != null) {
            out.writeBoolean(true);
            out.writeEnum(connectorState);
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
        if (parameters != null) {
            builder.field(CONNECTOR_PARAMETERS_FIELD, parameters);
        }
        if (credential != null) {
            builder.field(CONNECTOR_CREDENTIAL_FIELD, credential);
        }
        if (predictAPI != null) {
            builder.field(PREDICT_API_SCHEMA_FIELD, predictAPI);
        }
        if (metadataAPI != null) {
            builder.field(METADATA_API_SCHEMA_FIELD, metadataAPI);
        }
        if (connectorState != null) {
            builder.field(CONNECTOR_STATE_FIELD, connectorState);
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
        String parameters = null;
        String credential = null;
        String predictAPI = null;
        String metadataAPI = null;
        ConnectorState connectorState = null;
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
                    parameters = parser.text();
                    break;
                case CONNECTOR_CREDENTIAL_FIELD:
                    credential = parser.text();
                    break;
                case PREDICT_API_SCHEMA_FIELD:
                    predictAPI = parser.text();
                    break;
                case METADATA_API_SCHEMA_FIELD:
                    metadataAPI = parser.text();
                    break;
                case CONNECTOR_STATE_FIELD:
                    connectorState = ConnectorState.from(parser.text());
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
                .parameters(parameters)
                .credential(credential)
                .predictAPI(predictAPI)
                .metadataAPI(metadataAPI)
                .connectorState(connectorState)
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
        JSONObject jObject = new JSONObject(credential);
        Iterator<?> keys = jObject.keys();

        while( keys.hasNext() ){
            String key = (String)keys.next();
            String value = function.apply(jObject.getString(key));
            credentialMap.put(key, value);
        }

        credential = toJson(credentialMap);
    }

    @Override
    public String getEndpoint() {
        return null;
    }

    @Override
    public void decrypt(Function<String, String> function) {
        Type type = new TypeToken<HashMap<String, String>>() {}.getType();
        HashMap<String, String> credentialMap = gson.fromJson(credential, type);

        Map<String, String> decrypted = new HashMap<>();
        for (String key : credentialMap.keySet()) {
            decrypted.put(key, function.apply(credentialMap.get(key)));
        }
        this.decryptedCredential = decrypted;
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

    @Override
    public <T> T createPayload(Map<String, String> parameters) {
        return null;
    }
}
