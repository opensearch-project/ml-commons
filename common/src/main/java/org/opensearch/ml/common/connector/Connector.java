/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.connector;

import lombok.Builder;
import lombok.Getter;
import org.opensearch.common.io.stream.StreamInput;
import org.opensearch.common.io.stream.StreamOutput;
import org.opensearch.common.io.stream.Writeable;
import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.opensearch.common.xcontent.XContentParserUtils.ensureExpectedToken;

@Getter
public class Connector implements ToXContentObject, Writeable {

    public static final String CONNECTOR_ID_FIELD = "connector_id";
    public static final String CONNECTOR_NAME_FIELD = "name";
    public static final String CONNECTOR_VERSION_FIELD = "version";
    public static final String DESCRIPTION_FIELD = "description";

    public static final String ENDPOINT_FIELD = "endpoint";
    public static final String PROTOCOL_FIELD = "protocol";
    public static final String AUTHENTICATION_FIELD = "authentication";
    public static final String CONNECTOR_STATE_FIELD = "connector_state";

    public static final String CREATED_TIME_FIELD = "created_time";
    public static final String LAST_UPDATED_TIME_FIELD = "last_updated_time";
    public static final String CREDENTIAL_ID_FIELD = "credential_id";

    private String connectorId;
    private String name;
    private String version;
    private String description;

    private String endpoint;
    private Protocol protocol;
    private Auth auth;
    private ConnectorState connectorState;

    private Instant createdTime;
    private Instant lastUpdateTime;
    private String credentialId;

    @Builder(toBuilder = true)
    public Connector(String connectorId,
                     String name,
                     String version,
                     String description,
                     String endpoint,
                     Protocol protocol,
                     Auth auth,
                     ConnectorState connectorState,
                     Instant createdTime,
                     Instant lastUpdateTime,
                     String credentialId) {
        this.connectorId = connectorId;
        this.name = name;
        this.version = version;
        this.description = description;
        this.endpoint = endpoint;
        this.protocol = protocol;
        this.auth = auth;
        this.connectorState = connectorState;
        this.createdTime = createdTime;
        this.lastUpdateTime = lastUpdateTime;
        this.credentialId = credentialId;
    }

    public Connector(StreamInput input) throws IOException {
        connectorId = input.readOptionalString();
        name = input.readOptionalString();
        version = input.readString();
        description = input.readOptionalString();
        endpoint = input.readOptionalString();

        if (input.readBoolean()) {
            protocol = input.readEnum(Protocol.class);
        }
        if (input.readBoolean()) {
            auth = input.readEnum(Auth.class);
        }
        if (input.readBoolean()) {
            connectorState = input.readEnum(ConnectorState.class);
        }
        createdTime = input.readOptionalInstant();
        lastUpdateTime = input.readOptionalInstant();
        credentialId = input.readOptionalString();
    }

    public void writeTo(StreamOutput out) throws IOException {
        out.writeOptionalString(connectorId);
        out.writeOptionalString(name);
        out.writeString(version);
        out.writeOptionalString(description);
        out.writeOptionalString(endpoint);

        if (protocol != null) {
            out.writeBoolean(true);
            out.writeEnum(protocol);
        } else {
            out.writeBoolean(false);
        }
        if (auth != null) {
            out.writeBoolean(true);
            out.writeEnum(auth);
        } else {
            out.writeBoolean(false);
        }
        if (connectorState != null) {
            out.writeBoolean(true);
            out.writeEnum(connectorState);
        } else {
            out.writeBoolean(false);
        }
        out.writeOptionalInstant(createdTime);
        out.writeOptionalInstant(lastUpdateTime);
        out.writeOptionalString(credentialId);
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        if (connectorId != null) {
            builder.field(CONNECTOR_ID_FIELD, connectorId);
        }
        if (name != null) {
            builder.field(CONNECTOR_NAME_FIELD, name);
        }
        if (version != null) {
            builder.field(CONNECTOR_VERSION_FIELD, version);
        }
        if (description != null) {
            builder.field(DESCRIPTION_FIELD, description);
        }
        if (endpoint != null) {
            builder.field(ENDPOINT_FIELD, endpoint);
        }
        if (protocol != null) {
            builder.field(PROTOCOL_FIELD, protocol);
        }
        if (auth != null) {
            builder.field(AUTHENTICATION_FIELD, auth);
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
        if (credentialId != null) {
            builder.field(CREDENTIAL_ID_FIELD, credentialId);
        }
        builder.endObject();
        return builder;
    }

    public static Connector parse(XContentParser parser) throws IOException {
        String connectorId = null;
        String name = null;
        String version = null;
        String description = null;
        String endpoint = null;

        Protocol protocol = null;
        Auth auth = null;
        ConnectorState connectorState = null;
        Instant createdTime = null;
        Instant lastUpdateTime = null;
        List<String> models = new ArrayList<>();
        String credentialId = null;

        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.currentToken(), parser);
        while (parser.nextToken() != XContentParser.Token.END_OBJECT) {
            String fieldName = parser.currentName();
            parser.nextToken();

            switch (fieldName) {
                case CONNECTOR_ID_FIELD:
                    connectorId = parser.text();
                    break;
                case CONNECTOR_NAME_FIELD:
                    name = parser.text();
                    break;
                case CONNECTOR_VERSION_FIELD:
                    version = parser.text();
                    break;
                case DESCRIPTION_FIELD:
                    description = parser.text();
                    break;
                case ENDPOINT_FIELD:
                    endpoint = parser.text();
                    break;
                case PROTOCOL_FIELD:
                    protocol = Protocol.from(parser.text());
                    break;
                case AUTHENTICATION_FIELD:
                    auth = Auth.from(parser.text());
                    break;
                case CONNECTOR_STATE_FIELD:
                    connectorState = ConnectorState.from(parser.text());
                case CREATED_TIME_FIELD:
                    createdTime = Instant.ofEpochMilli(parser.longValue());
                    break;
                case LAST_UPDATED_TIME_FIELD:
                    lastUpdateTime = Instant.ofEpochMilli(parser.longValue());
                    break;
                case CREDENTIAL_ID_FIELD:
                    credentialId = parser.text();
                    break;
                default:
                    parser.skipChildren();
                    break;
            }
        }

        return Connector.builder()
                .connectorId(connectorId)
                .name(name)
                .version(version)
                .description(description)
                .endpoint(endpoint)
                .protocol(protocol)
                .auth(auth)
                .connectorState(connectorState)
                .createdTime(createdTime)
                .lastUpdateTime(lastUpdateTime)
                .credentialId(credentialId)
                .build();
    }
}
