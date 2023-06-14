/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.connector;

import lombok.Builder;
import lombok.Data;
import org.opensearch.common.io.stream.StreamInput;
import org.opensearch.common.io.stream.StreamOutput;
import org.opensearch.common.io.stream.Writeable;
import org.opensearch.commons.authuser.User;
import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.AccessMode;
import org.opensearch.ml.common.connector.template.ConnectorTemplate;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.opensearch.common.xcontent.XContentParserUtils.ensureExpectedToken;

@Data
public class MLCreateConnectorInput implements ToXContentObject, Writeable {
    public static final String CONNECTOR_META_DATA_FIELD = "metadata";
    public static final String CONNECTOR_PARAMETERS_FIELD = "parameters";
    public static final String CONNECTOR_CREDENTIAL_FIELD = "credential";
    public static final String CONNECTOR_TEMPLATE_FIELD = "template";

    public static final String BACKEND_ROLES_FIELD = "backend_roles";
    public static final String ADD_ALL_BACKEND_ROLES_FIELD = "add_all_backend_roles";
    public static final String OWNER_FIELD = "owner";
    public static final String ACCESS_MODE_FIELD = "access_mode";

    private Map<String, String> metadata;
    private Map<String, String> parameters;
    private Map<String, String> credential;
    private ConnectorTemplate connectorTemplate;
    private List<String> backendRoles;
    private Boolean addAllBackendRoles;
    private AccessMode access;

    @Builder(toBuilder = true)
    public MLCreateConnectorInput(Map<String, String> metadata,
                                  Map<String, String> parameters,
                                  Map<String, String> credential,
                                  ConnectorTemplate connectorTemplate,
                                  List<String> backendRoles,
                                  Boolean addAllBackendRoles,
                                  AccessMode access
                                  ) {
        this.metadata = metadata;
        this.parameters = parameters;
        this.credential = credential;
        this.connectorTemplate = connectorTemplate;
        this.backendRoles = backendRoles;
        this.addAllBackendRoles = addAllBackendRoles;
        this.access = access;
    }

    public static MLCreateConnectorInput parse(XContentParser parser) throws IOException {
        Map<String, String> metadata = new HashMap<>();
        Map<String, String> parameters = new HashMap<>();
        Map<String, String> credential = new HashMap<>();
        ConnectorTemplate connectorTemplate = null;
        List<String> backendRoles = new ArrayList<>();
        Boolean addAllBackendRoles = null;
        AccessMode access = null;

        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.currentToken(), parser);
        while (parser.nextToken() != XContentParser.Token.END_OBJECT) {
            String fieldName = parser.currentName();
            parser.nextToken();

            switch (fieldName) {
                case CONNECTOR_META_DATA_FIELD:
                    metadata = parser.mapStrings();
                    break;
                case CONNECTOR_PARAMETERS_FIELD:
                    parameters = parser.mapStrings();
                    break;
                case CONNECTOR_CREDENTIAL_FIELD:
                    credential = parser.mapStrings();
                    break;
                case CONNECTOR_TEMPLATE_FIELD:
                    connectorTemplate = connectorTemplate.parse(parser);
                    break;
                case BACKEND_ROLES_FIELD:
                    ensureExpectedToken(XContentParser.Token.START_ARRAY, parser.currentToken(), parser);
                    while (parser.nextToken() != XContentParser.Token.END_ARRAY) {
                        backendRoles.add(parser.text());
                    }
                    break;
                case ADD_ALL_BACKEND_ROLES_FIELD:
                    addAllBackendRoles = parser.booleanValue();
                    break;
                case ACCESS_MODE_FIELD:
                    access = AccessMode.from(parser.text());
                    break;
                default:
                    parser.skipChildren();
                    break;
            }
        }
        return new MLCreateConnectorInput(metadata, parameters, credential, connectorTemplate, backendRoles, addAllBackendRoles, access);
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        if (metadata != null) {
            builder.field(CONNECTOR_META_DATA_FIELD, metadata);
        }
        if (parameters != null) {
            builder.field(CONNECTOR_PARAMETERS_FIELD, parameters);
        }
        if (credential != null) {
            builder.field(CONNECTOR_CREDENTIAL_FIELD, credential);
        }
        if (connectorTemplate != null) {
            builder.field(CONNECTOR_TEMPLATE_FIELD, connectorTemplate);
        }
        if (backendRoles != null) {
            builder.field(BACKEND_ROLES_FIELD, backendRoles);
        }
        if (addAllBackendRoles != null) {
            builder.field(ADD_ALL_BACKEND_ROLES_FIELD, addAllBackendRoles);
        }
        if (access != null) {
            builder.field(ACCESS_MODE_FIELD, access);
        }
        builder.endObject();
        return builder;
    }

    @Override
    public void writeTo(StreamOutput output) throws IOException {
        if (metadata != null) {
            output.writeBoolean(true);
            output.writeMap(metadata, StreamOutput::writeString, StreamOutput::writeString);
        }
        else {
            output.writeBoolean(false);
        }
        if (parameters != null) {
            output.writeBoolean(true);
            output.writeMap(parameters, StreamOutput::writeString, StreamOutput::writeString);
        } else {
            output.writeBoolean(false);
        }
        if (credential != null) {
            output.writeBoolean(true);
            output.writeMap(credential, StreamOutput::writeString, StreamOutput::writeString);
        } else {
            output.writeBoolean(false);
        }
        if (connectorTemplate != null) {
            output.writeBoolean(true);
            connectorTemplate.writeTo(output);
        } else {
            output.writeBoolean(false);
        }
        if (backendRoles != null) {
            output.writeBoolean(true);
            output.writeOptionalStringCollection(backendRoles);
        } else {
            output.writeBoolean(false);
        }
        if (addAllBackendRoles != null) {
            output.writeBoolean(addAllBackendRoles);
        }
        if (access != null) {
            output.writeBoolean(true);
            output.writeEnum(access);
        } else {
            output.writeBoolean(false);
        }
    }

    public MLCreateConnectorInput(StreamInput input) throws IOException {
        if (input.readBoolean()) {
            metadata = input.readMap(s -> s.readString(), s-> s.readString());
        }
        if (input.readBoolean()) {
            parameters = input.readMap(s -> s.readString(), s -> s.readString());
        }
        if (input.readBoolean()) {
            credential = input.readMap(s -> s.readString(), s-> s.readString());
        }
        if (input.readBoolean()) {
            this.connectorTemplate = new ConnectorTemplate(input);
        }
        if (input.readBoolean()) {
            this.backendRoles = input.readList(StreamInput::readString);
        }
        this.addAllBackendRoles = input.readOptionalBoolean();
        if (input.readBoolean()) {
            this.access = input.readEnum(AccessMode.class);
        }
    }
}
