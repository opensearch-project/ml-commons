/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.connector;

import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;
import static org.opensearch.ml.common.CommonValue.TENANT_ID_FIELD;
import static org.opensearch.ml.common.CommonValue.VERSION_2_19_0;
import static org.opensearch.ml.common.CommonValue.VERSION_3_0_0;
import static org.opensearch.ml.common.connector.ConnectorProtocols.MCP_SSE;
import static org.opensearch.ml.common.connector.ConnectorProtocols.MCP_STREAMABLE_HTTP;
import static org.opensearch.ml.common.utils.StringUtils.getParameterMap;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.opensearch.Version;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.common.io.stream.Writeable;
import org.opensearch.core.common.util.CollectionUtils;
import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.AccessMode;
import org.opensearch.ml.common.CommonValue;
import org.opensearch.ml.common.connector.ConnectorAction;
import org.opensearch.ml.common.connector.ConnectorClientConfig;

import lombok.Builder;
import lombok.Data;
import lombok.Setter;

@Data
public class MLCreateConnectorInput implements ToXContentObject, Writeable {
    public static final String CONNECTOR_NAME_FIELD = "name";
    public static final String CONNECTOR_DESCRIPTION_FIELD = "description";
    public static final String CONNECTOR_VERSION_FIELD = "version";
    public static final String CONNECTOR_PROTOCOL_FIELD = "protocol";

    public static final String CONNECTOR_PARAMETERS_FIELD = "parameters";
    public static final String CONNECTOR_CREDENTIAL_FIELD = "credential";
    public static final String CONNECTOR_ACTIONS_FIELD = "actions";

    public static final String BACKEND_ROLES_FIELD = "backend_roles";
    public static final String ADD_ALL_BACKEND_ROLES_FIELD = "add_all_backend_roles";
    public static final String OWNER_FIELD = "owner";
    public static final String ACCESS_MODE_FIELD = "access_mode";
    public static final String DRY_RUN_FIELD = "dry_run";

    public static final String CONNECTOR_URL_FIELD = "url";
    public static final String CONNECTOR_HEADER_FIELD = "headers";

    private static final Version MINIMAL_SUPPORTED_VERSION_FOR_CLIENT_CONFIG = CommonValue.VERSION_2_13_0;

    public static final String DRY_RUN_CONNECTOR_NAME = "dryRunConnector";

    private String name;
    private String description;
    private String version;
    private String protocol;
    @Setter
    private String tenantId;
    private Map<String, String> parameters;
    private Map<String, String> credential;
    private List<ConnectorAction> actions;
    private List<String> backendRoles;
    private Boolean addAllBackendRoles;
    private AccessMode access;
    private boolean dryRun;
    private boolean updateConnector;
    private ConnectorClientConfig connectorClientConfig;

    private String url;
    private Map<String, String> headers;

    @Builder(toBuilder = true)
    public MLCreateConnectorInput(
        String name,
        String description,
        String version,
        String protocol,
        Map<String, String> parameters,
        Map<String, String> credential,
        List<ConnectorAction> actions,
        List<String> backendRoles,
        Boolean addAllBackendRoles,
        AccessMode access,
        boolean dryRun,
        boolean updateConnector,
        ConnectorClientConfig connectorClientConfig,
        String tenantId,
        String url,
        Map<String, String> headers
    ) {
        if (!dryRun && !updateConnector) {

            if (name == null) {
                throw new IllegalArgumentException("Connector name is null");
            }
            if (version == null) {
                throw new IllegalArgumentException("Connector version is null");
            }
            if (protocol == null) {
                throw new IllegalArgumentException("Connector protocol is null");
            }
            boolean isMcpConnector = (protocol.equals(MCP_SSE) || protocol.equals(MCP_STREAMABLE_HTTP));
            if ((credential == null || credential.isEmpty()) && !isMcpConnector) {
                throw new IllegalArgumentException("Connector credential is null or empty list");
            }
            if (actions != null) {
                for (ConnectorAction action : actions) {
                    action.validatePrePostProcessFunctions(parameters);
                }
            }
            if ((url == null || url.isBlank()) && isMcpConnector) {
                throw new IllegalArgumentException("MCP Connector url is null or blank");
            }
        }
        this.name = name;
        this.description = description;
        this.version = version;
        this.protocol = protocol;
        this.parameters = parameters;
        this.credential = credential;
        this.actions = actions;
        this.backendRoles = backendRoles;
        this.addAllBackendRoles = addAllBackendRoles;
        this.access = access;
        this.dryRun = dryRun;
        this.updateConnector = updateConnector;
        this.connectorClientConfig = connectorClientConfig;
        this.tenantId = tenantId;
        this.url = url;
        this.headers = headers;
    }

    public static MLCreateConnectorInput parse(XContentParser parser) throws IOException {
        return parse(parser, false);
    }

    public static MLCreateConnectorInput parse(XContentParser parser, boolean updateConnector) throws IOException {
        String name = null;
        String description = null;
        String version = null;
        String protocol = null;
        Map<String, String> parameters = new HashMap<>();
        Map<String, String> credential = new HashMap<>();
        List<ConnectorAction> actions = null;
        List<String> backendRoles = null;
        Boolean addAllBackendRoles = null;
        AccessMode access = null;
        boolean dryRun = false;
        ConnectorClientConfig connectorClientConfig = null;
        String tenantId = null;
        String url = null;
        Map<String, String> headers = null;

        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.currentToken(), parser);
        while (parser.nextToken() != XContentParser.Token.END_OBJECT) {
            String fieldName = parser.currentName();
            parser.nextToken();

            switch (fieldName) {
                case CONNECTOR_NAME_FIELD:
                    name = parser.text();
                    break;
                case CONNECTOR_DESCRIPTION_FIELD:
                    description = parser.text();
                    break;
                case CONNECTOR_VERSION_FIELD:
                    version = parser.text();
                    break;
                case CONNECTOR_PROTOCOL_FIELD:
                    protocol = parser.text();
                    break;
                case CONNECTOR_PARAMETERS_FIELD:
                    parameters = getParameterMap(parser.map());
                    break;
                case CONNECTOR_CREDENTIAL_FIELD:
                    credential = parser.mapStrings();
                    break;
                case CONNECTOR_ACTIONS_FIELD:
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
                case ADD_ALL_BACKEND_ROLES_FIELD:
                    addAllBackendRoles = parser.booleanValue();
                    break;
                case ACCESS_MODE_FIELD:
                    access = AccessMode.from(parser.text());
                    break;
                case DRY_RUN_FIELD:
                    dryRun = parser.booleanValue();
                    break;
                case CommonValue.CLIENT_CONFIG_FIELD:
                    connectorClientConfig = ConnectorClientConfig.parse(parser);
                    break;
                case TENANT_ID_FIELD:
                    tenantId = parser.textOrNull();
                    break;
                case CONNECTOR_URL_FIELD:
                    url = parser.textOrNull();
                    break;
                case CONNECTOR_HEADER_FIELD:
                    headers = parser.mapStrings();
                    break;
                default:
                    parser.skipChildren();
                    break;
            }
        }
        return new MLCreateConnectorInput(
            name,
            description,
            version,
            protocol,
            parameters,
            credential,
            actions,
            backendRoles,
            addAllBackendRoles,
            access,
            dryRun,
            updateConnector,
            connectorClientConfig,
            tenantId,
            url,
            headers
        );
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        if (name != null) {
            builder.field(CONNECTOR_NAME_FIELD, name);
        }
        if (description != null) {
            builder.field(CONNECTOR_DESCRIPTION_FIELD, description);
        }
        if (version != null) {
            builder.field(CONNECTOR_VERSION_FIELD, version);
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
        if (actions != null) {
            builder.field(CONNECTOR_ACTIONS_FIELD, actions);
        }
        if (!CollectionUtils.isEmpty(backendRoles)) {
            builder.field(BACKEND_ROLES_FIELD, backendRoles);
        }
        if (addAllBackendRoles != null) {
            builder.field(ADD_ALL_BACKEND_ROLES_FIELD, addAllBackendRoles);
        }
        if (access != null) {
            builder.field(ACCESS_MODE_FIELD, access);
        }
        if (connectorClientConfig != null) {
            builder.field(CommonValue.CLIENT_CONFIG_FIELD, connectorClientConfig);
        }
        if (tenantId != null) {
            builder.field(TENANT_ID_FIELD, tenantId);
        }
        if (url != null) {
            builder.field(CONNECTOR_URL_FIELD, url);
        }
        if (headers != null) {
            builder.field(CONNECTOR_HEADER_FIELD, headers);
        }
        builder.endObject();
        return builder;
    }

    @Override
    public void writeTo(StreamOutput output) throws IOException {
        Version streamOutputVersion = output.getVersion();
        output.writeOptionalString(name);
        output.writeOptionalString(description);
        output.writeOptionalString(version);
        output.writeOptionalString(protocol);
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
        if (actions != null) {
            output.writeBoolean(true);
            output.writeInt(actions.size());
            for (ConnectorAction action : actions) {
                action.writeTo(output);
            }
        } else {
            output.writeBoolean(false);
        }
        if (!CollectionUtils.isEmpty(backendRoles)) {
            output.writeBoolean(true);
            output.writeStringCollection(backendRoles);
        } else {
            output.writeBoolean(false);
        }
        output.writeOptionalBoolean(addAllBackendRoles);
        if (access != null) {
            output.writeBoolean(true);
            output.writeEnum(access);
        } else {
            output.writeBoolean(false);
        }
        output.writeBoolean(dryRun);
        output.writeBoolean(updateConnector);
        if (streamOutputVersion.onOrAfter(MINIMAL_SUPPORTED_VERSION_FOR_CLIENT_CONFIG)) {
            if (connectorClientConfig != null) {
                output.writeBoolean(true);
                connectorClientConfig.writeTo(output);
            } else {
                output.writeBoolean(false);
            }
        }
        if (streamOutputVersion.onOrAfter(VERSION_2_19_0)) {
            output.writeOptionalString(tenantId);
        }
        if (streamOutputVersion.onOrAfter(VERSION_3_0_0)) {
            output.writeOptionalString(url);
        }
        if (streamOutputVersion.onOrAfter(VERSION_3_0_0)) {
            if (headers != null) {
                output.writeBoolean(true);
                output.writeMap(headers, StreamOutput::writeString, StreamOutput::writeString);
            } else {
                output.writeBoolean(false);
            }
        }
    }

    public MLCreateConnectorInput(StreamInput input) throws IOException {
        Version streamInputVersion = input.getVersion();
        name = input.readOptionalString();
        description = input.readOptionalString();
        version = input.readOptionalString();
        protocol = input.readOptionalString();
        if (input.readBoolean()) {
            parameters = input.readMap(s -> s.readString(), s -> s.readString());
        }
        if (input.readBoolean()) {
            credential = input.readMap(s -> s.readString(), s -> s.readString());
        }
        if (input.readBoolean()) {
            actions = new ArrayList<>();
            int size = input.readInt();
            for (int i = 0; i < size; i++) {
                actions.add(new ConnectorAction(input));
            }
        }
        if (input.readBoolean()) {
            this.backendRoles = input.readList(StreamInput::readString);
        }
        this.addAllBackendRoles = input.readOptionalBoolean();
        if (input.readBoolean()) {
            this.access = input.readEnum(AccessMode.class);
        }
        dryRun = input.readBoolean();
        updateConnector = input.readBoolean();
        if (streamInputVersion.onOrAfter(MINIMAL_SUPPORTED_VERSION_FOR_CLIENT_CONFIG)) {
            if (input.readBoolean()) {
                this.connectorClientConfig = new ConnectorClientConfig(input);
            }
        }
        this.tenantId = streamInputVersion.onOrAfter(VERSION_2_19_0) ? input.readOptionalString() : null;
        this.url = streamInputVersion.onOrAfter(VERSION_3_0_0) ? input.readOptionalString() : null;
        if (streamInputVersion.onOrAfter(VERSION_3_0_0)) {
            if (input.readBoolean()) {
                this.headers = input.readMap(s -> s.readString(), s -> s.readString());
            }
        }
    }
}
