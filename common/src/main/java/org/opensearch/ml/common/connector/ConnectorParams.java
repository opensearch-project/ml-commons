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
import java.util.Locale;

import static org.opensearch.common.xcontent.XContentParserUtils.ensureExpectedToken;

public class ConnectorParams implements ToXContentObject, NamedWriteable {
    public static final String PARSE_FIELD_NAME = "Open_AI_Parameters";

    public static final String CONNECTOR_NAME_FIELD = "connector_name";
    public static final String VERSION_FIELD = "version";
    public static final String DESCRIPTION_FIELD = "description";
    public static final String ENDPOINT_FIELD = "endpoint";
    public static final String PROTOCOL_FIELD = "protocol";
    public static final String AUTHENTICATION_FIELD = "auth";
    public static final String CONTENT_TYPE_FIELD = "content_type";

    private String connectorName;
    private String version;
    private String description;
    private String endpoint;
    private Protocol protocol;
    private Auth auth;
    private String contentType;

    @Builder(toBuilder = true)
    public ConnectorParams(String connectorName,
                           String version,
                           String description,
                           String endpoint,
                           Protocol protocol,
                           Auth auth,
                           String contentType) {
        this.connectorName = connectorName;
        this.version = version;
        this.description = description;
        this.endpoint = endpoint;
        this.protocol = protocol;
        this.auth = auth;
        this.contentType = contentType;
    }

    public static ConnectorParams parse(XContentParser parser) throws IOException {
        String connectorName = null;
        String version = null;
        String description = null;
        String endpoint = null;
        Protocol protocol = null;
        Auth auth = null;
        String contentType = null;

        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.currentToken(), parser);
        while (parser.nextToken() != XContentParser.Token.END_OBJECT) {
            String fieldName = parser.currentName();
            parser.nextToken();

            switch (fieldName) {
                case CONNECTOR_NAME_FIELD:
                    connectorName = parser.text();
                    break;
                case VERSION_FIELD:
                    version = parser.text();
                    break;
                case DESCRIPTION_FIELD:
                    description = parser.text();
                    break;
                case ENDPOINT_FIELD:
                    endpoint = parser.text();
                    break;
                case PROTOCOL_FIELD:
                    protocol = Protocol.from(parser.text().toUpperCase(Locale.ROOT));
                    break;
                case AUTHENTICATION_FIELD:
                    auth = Auth.from(parser.text().toUpperCase(Locale.ROOT));
                    break;
                case CONTENT_TYPE_FIELD:
                    contentType = parser.text();
                default:
                    parser.skipChildren();
                    break;
            }
        }
        return new ConnectorParams(connectorName, version, description, endpoint, protocol, auth, contentType);
    }

    @Override
    public String getWriteableName() {
        return PARSE_FIELD_NAME;
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        if (connectorName != null) {
            builder.field(CONNECTOR_NAME_FIELD, connectorName);
        }
        if (version != null) {
            builder.field(VERSION_FIELD, version);
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
        if (contentType != null) {
            builder.field(CONTENT_TYPE_FIELD, contentType);
        }
        builder.endObject();
        return builder;
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeOptionalString(connectorName);
        out.writeOptionalString(version);
        out.writeOptionalString(description);
        out.writeOptionalString(endpoint);
        out.writeEnum(protocol);
        out.writeEnum(auth);
        out.writeOptionalString(contentType);
    }

    public ConnectorParams(StreamInput input) throws IOException {
        connectorName = input.readOptionalString();
        version = input.readOptionalString();
        description = input.readOptionalString();
        endpoint = input.readOptionalString();
        protocol = input.readEnum(Protocol.class);
        auth = input.readEnum(Auth.class);
        contentType = input.readOptionalString();
    }
}
