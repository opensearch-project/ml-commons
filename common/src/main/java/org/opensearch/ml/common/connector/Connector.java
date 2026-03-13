/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.connector;

import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;
import static org.opensearch.ml.common.utils.StringUtils.gson;

import java.io.IOException;
import java.security.AccessController;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.text.StringSubstitutor;
import org.opensearch.common.TriConsumer;
import org.opensearch.common.xcontent.LoggingDeprecationHandler;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.commons.authuser.User;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.common.io.stream.Writeable;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.AccessMode;
import org.opensearch.ml.common.MLCommonsClassLoader;
import org.opensearch.ml.common.output.model.ModelTensor;
import org.opensearch.ml.common.transport.connector.MLCreateConnectorInput;

/**
 * Connector defines how to connect to a remote service.
 */
public interface Connector extends ToXContentObject, Writeable {

    String getName();

    String getTenantId();

    void setTenantId(String tenantId);

    String getProtocol();

    void setCreatedTime(Instant createdTime);

    void setLastUpdateTime(Instant lastUpdateTime);

    User getOwner();

    void setOwner(User user);

    AccessMode getAccess();

    void setAccess(AccessMode access);

    List<String> getBackendRoles();

    void setBackendRoles(List<String> backendRoles);

    Map<String, String> getParameters();

    List<ConnectorAction> getActions();

    void addAction(ConnectorAction action);

    ConnectorClientConfig getConnectorClientConfig();

    String getActionEndpoint(String action, Map<String, String> parameters);

    String getActionHttpMethod(String action);

    <T> T createPayload(String action, Map<String, String> parameters);

    void decrypt(
        String action,
        TriConsumer<List<String>, String, ActionListener<List<String>>> function,
        String tenantId,
        ActionListener<Boolean> listener
    );

    void encrypt(
        TriConsumer<List<String>, String, ActionListener<List<String>>> function,
        String tenantId,
        ActionListener<Boolean> listener
    );

    Connector cloneConnector();

    Optional<ConnectorAction> findAction(String action);

    void removeCredential();

    void writeTo(StreamOutput out) throws IOException;

    void update(MLCreateConnectorInput updateContent);

    <T> void parseResponse(T orElse, List<ModelTensor> modelTensors, boolean b) throws IOException;

    default void validatePayload(String payload) {
        if (payload != null && payload.contains("${parameters")) {
            Pattern pattern = Pattern.compile("\\$\\{parameters\\.([^}]+)}");
            Matcher matcher = pattern.matcher(payload);

            StringBuilder errorBuilder = new StringBuilder();
            while (matcher.find()) {
                String parameter = matcher.group(1);
                errorBuilder.append(parameter).append(", ");
            }
            String error = errorBuilder.substring(0, errorBuilder.length() - 2).toString();
            throw new IllegalArgumentException("Some parameter placeholder not filled in payload: " + error);
        }
    }

    static Connector fromStream(StreamInput in) throws IOException {
        try {
            String connectorProtocol = in.readString();
            return MLCommonsClassLoader
                .initConnector(connectorProtocol, new Object[] { connectorProtocol, in }, String.class, StreamInput.class);
        } catch (IllegalArgumentException illegalArgumentException) {
            throw illegalArgumentException;
        }
    }

    static Connector createConnector(XContentBuilder builder, String connectorProtocol) throws IOException {
        try {
            String jsonStr = builder.toString();
            return createConnector(jsonStr, connectorProtocol);
        } catch (IllegalArgumentException illegalArgumentException) {
            throw illegalArgumentException;
        }
    }

    @SuppressWarnings("removal")
    static Connector createConnector(XContentParser parser) throws IOException {
        Map<String, Object> connectorMap = parser.map();
        String jsonStr;
        try {
            jsonStr = AccessController.doPrivileged((PrivilegedExceptionAction<String>) () -> gson.toJson(connectorMap));
        } catch (PrivilegedActionException e) {
            throw new IllegalArgumentException("wrong connector");
        }
        String connectorProtocol = (String) connectorMap.get("protocol");

        return createConnector(jsonStr, connectorProtocol);
    }

    private static Connector createConnector(String jsonStr, String connectorProtocol) throws IOException {
        try (
            XContentParser connectorParser = XContentType.JSON
                .xContent()
                .createParser(NamedXContentRegistry.EMPTY, LoggingDeprecationHandler.INSTANCE, jsonStr)
        ) {
            ensureExpectedToken(XContentParser.Token.START_OBJECT, connectorParser.nextToken(), connectorParser);

            if (connectorProtocol == null) {
                throw new IllegalArgumentException("connector protocol is null");
            }
            return MLCommonsClassLoader
                .initConnector(connectorProtocol, new Object[] { connectorProtocol, connectorParser }, String.class, XContentParser.class);
        } catch (Exception ex) {
            if (ex instanceof IllegalArgumentException) {
                throw ex;
            }
            return null;
        }
    }

    default void validateConnectorURL(List<String> urlRegexes) {
        if (getActions() == null) {
            throw new IllegalArgumentException("No actions configured for this connector");
        }
        Map<String, String> parameters = getParameters();
        for (ConnectorAction action : getActions()) {
            StringSubstitutor substitutor = new StringSubstitutor(parameters, "${parameters.", "}");
            String url = substitutor.replace(action.getUrl());
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
    }

    Map<String, String> getDecryptedHeaders();

    Map<String, String> getDecryptedCredential();
}
