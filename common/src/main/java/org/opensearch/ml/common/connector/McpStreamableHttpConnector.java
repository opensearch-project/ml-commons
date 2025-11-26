/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.connector;

import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;
import static org.opensearch.ml.common.CommonValue.ACCESS_FIELD;
import static org.opensearch.ml.common.CommonValue.BACKEND_ROLES_FIELD;
import static org.opensearch.ml.common.CommonValue.CLIENT_CONFIG_FIELD;
import static org.opensearch.ml.common.CommonValue.CREATED_TIME_FIELD;
import static org.opensearch.ml.common.CommonValue.CREDENTIAL_FIELD;
import static org.opensearch.ml.common.CommonValue.DESCRIPTION_FIELD;
import static org.opensearch.ml.common.CommonValue.HEADERS_FIELD;
import static org.opensearch.ml.common.CommonValue.LAST_UPDATED_TIME_FIELD;
import static org.opensearch.ml.common.CommonValue.NAME_FIELD;
import static org.opensearch.ml.common.CommonValue.OWNER_FIELD;
import static org.opensearch.ml.common.CommonValue.PARAMETERS_FIELD;
import static org.opensearch.ml.common.CommonValue.PROTOCOL_FIELD;
import static org.opensearch.ml.common.CommonValue.TENANT_ID_FIELD;
import static org.opensearch.ml.common.CommonValue.URL_FIELD;
import static org.opensearch.ml.common.CommonValue.VERSION_FIELD;
import static org.opensearch.ml.common.connector.ConnectorProtocols.MCP_STREAMABLE_HTTP;
import static org.opensearch.ml.common.connector.ConnectorProtocols.validateProtocol;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.text.StringSubstitutor;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.commons.authuser.User;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.AccessMode;
import org.opensearch.ml.common.output.model.ModelTensor;
import org.opensearch.ml.common.transport.connector.MLCreateConnectorInput;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;

@Log4j2
@NoArgsConstructor
@EqualsAndHashCode
@Getter
@org.opensearch.ml.common.annotation.Connector(MCP_STREAMABLE_HTTP)
public class McpStreamableHttpConnector extends AbstractConnector {

    protected String name;
    protected String description;
    protected String version;
    protected String protocol;

    protected Map<String, String> credential;
    protected Map<String, String> decryptedHeaders;
    protected Map<String, String> parameters;
    @Setter
    protected Map<String, String> decryptedCredential;
    @Setter
    protected List<String> backendRoles;
    @Setter
    protected User owner;
    @Setter
    protected AccessMode access;
    @Setter
    protected Instant createdTime;
    @Setter
    protected Instant lastUpdateTime;
    @Setter
    protected ConnectorClientConfig connectorClientConfig;
    @Setter
    protected String tenantId;
    @Setter
    @Getter
    protected String url;
    @Setter
    protected Map<String, String> headers;

    @Builder
    public McpStreamableHttpConnector(
        String name,
        String description,
        String version,
        String protocol,
        Map<String, String> credential,
        List<String> backendRoles,
        AccessMode accessMode,
        User owner,
        ConnectorClientConfig connectorClientConfig,
        String tenantId,
        String url,
        Map<String, String> headers,
        Map<String, String> parameters
    ) {
        validateProtocol(protocol);
        this.name = name;
        this.description = description;
        this.version = version;
        this.protocol = protocol;
        this.credential = credential;
        this.backendRoles = backendRoles;
        this.access = accessMode;
        this.owner = owner;
        this.connectorClientConfig = connectorClientConfig;
        this.tenantId = tenantId;
        this.url = url;
        this.headers = headers;
        this.parameters = parameters;
    }

    public McpStreamableHttpConnector(String protocol, XContentParser parser) throws IOException {
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
                    this.protocol = parser.text();
                    break;
                case CREDENTIAL_FIELD:
                    credential = new HashMap<>();
                    credential.putAll(parser.mapStrings());
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
                case CLIENT_CONFIG_FIELD:
                    connectorClientConfig = ConnectorClientConfig.parse(parser);
                    break;
                case TENANT_ID_FIELD:
                    tenantId = parser.textOrNull();
                    break;
                case URL_FIELD:
                    url = parser.textOrNull();
                    break;
                case HEADERS_FIELD:
                    headers = new HashMap<>();
                    headers.putAll(parser.mapStrings());
                    break;
                case PARAMETERS_FIELD:
                    parameters = new HashMap<>();
                    parameters.putAll(parser.mapStrings());
                    break;
                default:
                    parser.skipChildren();
                    break;
            }
        }
    }

    protected Map<String, String> createDecryptedHeaders(Map<String, String> headers) {
        // TODO: Change this to return empty MAP in all createDecryptedHeaders functions across connectors
        if (headers == null) {
            return null;
        }
        Map<String, String> decryptedHeaders = new HashMap<>();
        StringSubstitutor substitutor = new StringSubstitutor(getDecryptedCredential(), "${credential.", "}");
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            decryptedHeaders.put(entry.getKey(), substitutor.replace(entry.getValue()));
        }
        return decryptedHeaders;
    }

    @Override
    public Connector cloneConnector() {
        try (BytesStreamOutput bytesStreamOutput = new BytesStreamOutput()) {
            this.writeTo(bytesStreamOutput);
            StreamInput streamInput = bytesStreamOutput.bytes().streamInput();
            return new McpStreamableHttpConnector(streamInput);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public McpStreamableHttpConnector(StreamInput input) throws IOException {
        this.protocol = input.readString();
        parseFromStream(input);
    }

    private void parseFromStream(StreamInput input) throws IOException {
        this.name = input.readOptionalString();
        this.version = input.readOptionalString();
        this.description = input.readOptionalString();
        if (input.readBoolean()) {
            credential = input.readMap(StreamInput::readString, StreamInput::readString);
        }
        backendRoles = input.readOptionalStringList();
        if (input.readBoolean()) {
            this.access = input.readEnum(AccessMode.class);
        }
        if (input.readBoolean()) {
            this.owner = new User(input);
        }
        this.createdTime = input.readOptionalInstant();
        this.lastUpdateTime = input.readOptionalInstant();
        if (input.readBoolean()) {
            this.connectorClientConfig = new ConnectorClientConfig(input);
        }
        this.tenantId = input.readOptionalString();
        this.url = input.readString();
        if (input.readBoolean()) {
            this.headers = input.readMap(s -> s.readString(), s -> s.readString());
        }
        if (input.readBoolean()) {
            this.parameters = input.readMap(s -> s.readString(), s -> s.readString());
        }
    }

    @Override
    public void removeCredential() {
        this.credential = null;
        this.decryptedCredential = null;
        this.decryptedHeaders = null;
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeString(protocol);
        out.writeOptionalString(name);
        out.writeOptionalString(version);
        out.writeOptionalString(description);
        if (credential != null) {
            out.writeBoolean(true);
            out.writeMap(credential, StreamOutput::writeString, StreamOutput::writeString);
        } else {
            out.writeBoolean(false);
        }
        out.writeOptionalStringCollection(backendRoles);
        if (access != null) {
            out.writeBoolean(true);
            out.writeEnum(access);
        } else {
            out.writeBoolean(false);
        }
        if (owner != null) {
            out.writeBoolean(true);
            owner.writeTo(out);
        } else {
            out.writeBoolean(false);
        }
        out.writeOptionalInstant(createdTime);
        out.writeOptionalInstant(lastUpdateTime);
        if (connectorClientConfig != null) {
            out.writeBoolean(true);
            connectorClientConfig.writeTo(out);
        } else {
            out.writeBoolean(false);
        }
        out.writeOptionalString(tenantId);

        out.writeString(url);

        if (headers != null) {
            out.writeBoolean(true);
            out.writeMap(headers, StreamOutput::writeString, StreamOutput::writeString);
        } else {
            out.writeBoolean(false);
        }
        if (parameters != null) {
            out.writeBoolean(true);
            out.writeMap(parameters, StreamOutput::writeString, StreamOutput::writeString);
        } else {
            out.writeBoolean(false);
        }
    }

    @Override
    public void update(MLCreateConnectorInput updateContent) {
        if (updateContent.getName() != null) {
            this.name = updateContent.getName();
        }
        if (updateContent.getDescription() != null) {
            this.description = updateContent.getDescription();
        }
        if (updateContent.getVersion() != null) {
            this.version = updateContent.getVersion();
        }
        if (updateContent.getProtocol() != null) {
            this.protocol = updateContent.getProtocol();
        }
        if (updateContent.getCredential() != null && !updateContent.getCredential().isEmpty()) {
            this.credential = updateContent.getCredential();
        }
        if (updateContent.getBackendRoles() != null) {
            this.backendRoles = updateContent.getBackendRoles();
        }
        if (updateContent.getAccess() != null) {
            this.access = updateContent.getAccess();
        }
        if (updateContent.getConnectorClientConfig() != null) {
            this.connectorClientConfig = updateContent.getConnectorClientConfig();
        }
        if (updateContent.getUrl() != null && updateContent.getUrl().isBlank()) {
            throw new IllegalArgumentException("MCP Connector url is blank");
        }
        if (updateContent.getUrl() != null) {
            this.url = updateContent.getUrl();
        }
        if (updateContent.getHeaders() != null) {
            this.headers = updateContent.getHeaders();
        }
        if (updateContent.getParameters() != null) {
            this.parameters = updateContent.getParameters();
        }
    }

    @Override
    public <T> void parseResponse(T orElse, List<ModelTensor> modelTensors, boolean b) throws IOException {
        throw new UnsupportedOperationException("Not implemented.");
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
        if (credential != null) {
            builder.field(CREDENTIAL_FIELD, credential);
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
        if (connectorClientConfig != null) {
            builder.field(CLIENT_CONFIG_FIELD, connectorClientConfig);
        }
        if (tenantId != null) {
            builder.field(TENANT_ID_FIELD, tenantId);
        }
        if (url != null) {
            builder.field(URL_FIELD, url);
        }
        if (headers != null) {
            builder.field(HEADERS_FIELD, headers);
        }
        if (parameters != null) {
            builder.field(PARAMETERS_FIELD, parameters);
        }
        builder.endObject();
        return builder;
    }

    @Override
    public void validateConnectorURL(List<String> urlRegexes) {
        boolean hasMatchedUrl = false;
        for (String urlRegex : urlRegexes) {
            Pattern pattern = Pattern.compile(urlRegex);
            Matcher matcher = pattern.matcher(url);
            if (matcher.matches()) {
                hasMatchedUrl = true;
                break;
            }
        }
        if (!hasMatchedUrl) {
            throw new IllegalArgumentException("Connector URL is not matching the trusted connector endpoint regex");
        }
    }

    @Override
    public Map<String, String> getParameters() {
        return parameters;
    }

    @Override
    public List<ConnectorAction> getActions() {
        throw new UnsupportedOperationException("Not implemented.");
    }

    @Override
    public void addAction(ConnectorAction action) {
        throw new UnsupportedOperationException("Not implemented.");
    }

    @Override
    public String getActionEndpoint(String action, Map<String, String> parameters) {
        throw new UnsupportedOperationException("Not implemented.");
    }

    @Override
    protected Map<String, String> getAllHeaders(String action) {
        return headers;
    }

    @Override
    public String getActionHttpMethod(String action) {
        throw new UnsupportedOperationException("Not implemented.");
    }

    @Override
    public <T> T createPayload(String action, Map<String, String> parameters) {
        throw new UnsupportedOperationException("Not implemented.");
    }

    @Override
    public Optional<ConnectorAction> findAction(String action) {
        throw new UnsupportedOperationException("Not implemented.");
    }
}
