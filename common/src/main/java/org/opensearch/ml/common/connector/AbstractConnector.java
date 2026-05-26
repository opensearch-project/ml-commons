/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.connector;

import static org.opensearch.ml.common.CommonValue.ML_MAP_RESPONSE_KEY;
import static org.opensearch.ml.common.utils.StringUtils.isJson;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.text.StringSubstitutor;
import org.opensearch.common.TriConsumer;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.commons.authuser.User;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.AccessMode;
import org.opensearch.ml.common.output.model.ModelTensor;
import org.opensearch.ml.common.utils.StringUtils;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;

@Log4j2
@Getter
public abstract class AbstractConnector implements Connector {
    private static final Pattern PARAMETER_PLACEHOLDER_PATTERN = Pattern.compile("\\$\\{parameters\\.[^}]+\\}");
    private static final Set<String> MCP_PROTOCOLS = Set.of(ConnectorProtocols.MCP_SSE, ConnectorProtocols.MCP_STREAMABLE_HTTP);
    private static final Set<String> BLOCKED_DYNAMIC_HEADERS = Set
        .of(
            "authorization",
            "proxy-authorization",
            "cookie",
            "x-api-key",
            "x-auth-token",
            "x-auth-header",
            "x-forwarded-for",
            "x-real-ip",
            "x-client-ip",
            "cf-connecting-ip",
            "true-client-ip",
            "x-originating-ip",
            "host",
            "x-forwarded-host",
            "x-forwarded-server",
            "forwarded"
        );

    public static final String ACCESS_KEY_FIELD = "access_key";
    public static final String SECRET_KEY_FIELD = "secret_key";
    public static final String SESSION_TOKEN_FIELD = "session_token";
    public static final String NAME_FIELD = "name";
    public static final String VERSION_FIELD = "version";
    public static final String DESCRIPTION_FIELD = "description";
    public static final String PROTOCOL_FIELD = "protocol";
    public static final String ACTIONS_FIELD = "actions";
    public static final String PARAMETERS_FIELD = "parameters";
    public static final String CREATED_TIME_FIELD = "created_time";
    public static final String LAST_UPDATED_TIME_FIELD = "last_updated_time";
    public static final String OWNER_FIELD = "owner";
    public static final String ACCESS_FIELD = "access";
    public static final String CLIENT_CONFIG_FIELD = "client_config";

    protected String name;
    protected String description;
    protected String version;
    protected String protocol;

    protected Map<String, String> parameters;
    protected Map<String, String> credential;
    protected Map<String, String> decryptedHeaders;
    @Setter
    protected Map<String, String> decryptedCredential;

    protected List<ConnectorAction> actions;

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
    protected String provisionedBy;

    protected Map<String, String> createDecryptedHeaders(Map<String, String> headers) {
        if (headers == null) {
            return null;
        }
        Map<String, String> decryptedHeaders = new HashMap<>();
        StringSubstitutor substitutor = new StringSubstitutor(getDecryptedCredential(), "${credential.", "}");
        for (String key : headers.keySet()) {
            decryptedHeaders.put(key, substitutor.replace(headers.get(key)));
        }
        return decryptedHeaders;
    }

    public Map<String, String> substituteHeadersWithRuntimeParameters(Map<String, String> headers, Map<String, String> runtimeParameters) {
        if (headers == null || headers.isEmpty()) {
            return headers;
        }

        Map<String, String> substitutedHeaders = new HashMap<>();
        StringSubstitutor substitutor = new StringSubstitutor(runtimeParameters, "${parameters.", "}");

        for (Map.Entry<String, String> entry : headers.entrySet()) {
            String value = entry.getValue();
            if (value != null && value.contains("${parameters.")) {
                String substituted = substitutor.replace(value);
                if (substituted.contains("${parameters.")) {
                    throw new IllegalArgumentException(
                        String.format("Header '%s' contains unresolved placeholder. Required parameter is missing.", entry.getKey())
                    );
                }
                substitutedHeaders.put(entry.getKey(), substituted);
            } else {
                substitutedHeaders.put(entry.getKey(), value);
            }
        }

        return substitutedHeaders;
    }

    @SuppressWarnings("unchecked")
    public <T> void parseResponse(T response, List<ModelTensor> modelTensors, boolean modelTensorJson) throws IOException {
        if (modelTensorJson) {
            String modelTensorJsonContent = (String) response;
            XContentParser parser = XContentType.JSON.xContent().createParser(NamedXContentRegistry.EMPTY, null, modelTensorJsonContent);
            parser.nextToken();
            if (XContentParser.Token.START_ARRAY == parser.currentToken()) {
                while (parser.nextToken() != XContentParser.Token.END_ARRAY) {
                    ModelTensor modelTensor = ModelTensor.parser(parser);
                    modelTensors.add(modelTensor);
                }
            } else {
                ModelTensor modelTensor = ModelTensor.parser(parser);
                modelTensors.add(modelTensor);
            }
            return;
        }
        if (response instanceof String && isJson((String) response)) {
            Map<String, Object> data = StringUtils.fromJson((String) response, ML_MAP_RESPONSE_KEY);
            modelTensors.add(ModelTensor.builder().name("response").dataAsMap(data).build());
        } else if (response instanceof Map) {
            modelTensors.add(ModelTensor.builder().name("response").dataAsMap((Map<String, ?>) response).build());
        } else {
            Map<String, Object> map = new HashMap<>();
            map.put("response", response);
            modelTensors.add(ModelTensor.builder().name("response").dataAsMap(map).build());
        }
    }

    @Override
    public Optional<ConnectorAction> findAction(String action) {
        // Guard against null actions list or null action parameter
        if (actions != null && action != null) {
            if (ConnectorAction.ActionType.isValidAction(action)) {
                return actions.stream().filter(a -> a.getActionType().name().equalsIgnoreCase(action)).findFirst();
            }
            return actions.stream().filter(a -> action.equals(a.getName())).findFirst();
        }
        return Optional.empty();
    }

    @Override
    public void addAction(ConnectorAction action) {
        actions.add(action);
    }

    @Override
    public void removeCredential() {
        this.credential = null;
        this.decryptedCredential = null;
        this.decryptedHeaders = null;
    }

    @Override
    public String getActionEndpoint(String action, Map<String, String> parameters) {
        Optional<ConnectorAction> actionEndpoint = findAction(action);
        if (actionEndpoint.isEmpty()) {
            return null;
        }
        String predictEndpoint = actionEndpoint.get().getUrl();
        if (parameters != null && !parameters.isEmpty()) {
            StringSubstitutor substitutor = new StringSubstitutor(parameters, "${parameters.", "}");
            predictEndpoint = substitutor.replace(predictEndpoint);
        }
        return predictEndpoint;
    }

    @Override
    public void encrypt(
        TriConsumer<List<String>, String, ActionListener<List<String>>> function,
        String tenantId,
        ActionListener<Boolean> listener
    ) {
        if (credential == null || credential.isEmpty()) {
            listener.onResponse(true);
            return;
        }
        List<String> orderedEncryptKeys = new ArrayList<>();
        List<String> orderedToEncrypt = new ArrayList<>();
        for (String key : credential.keySet()) {
            orderedEncryptKeys.add(key);
            orderedToEncrypt.add(credential.get(key));
        }
        ActionListener<List<String>> updateEncryptedCredentialsListener = ActionListener.wrap(r -> {
            Map<String, String> encryptedCredentials = new HashMap<>();
            for (int i = 0; i < r.size(); i++) {
                encryptedCredentials.put(orderedEncryptKeys.get(i), r.get(i));
            }
            credential = encryptedCredentials;
            listener.onResponse(true);
        }, e -> {
            log.error("Failed to encrypt credentials in connector", e);
            listener.onFailure(e);
        });
        function.apply(orderedToEncrypt, tenantId, updateEncryptedCredentialsListener);
    }

    @Override
    public void decrypt(
        String action,
        TriConsumer<List<String>, String, ActionListener<List<String>>> function,
        String tenantId,
        ActionListener<Boolean> listener
    ) {
        if (credential == null || credential.isEmpty()) {
            this.decryptedHeaders = createDecryptedHeaders(getAllHeaders(action));
            listener.onResponse(true);
            return;
        }
        List<String> orderedDecryptKeys = new ArrayList<>();
        List<String> orderedToDecrypt = new ArrayList<>();
        for (Map.Entry<String, String> entry : credential.entrySet()) {
            orderedDecryptKeys.add(entry.getKey());
            orderedToDecrypt.add(entry.getValue());
        }
        ActionListener<List<String>> updateDecryptedCredentialsListener = ActionListener.wrap(r -> {
            decryptedCredential = new HashMap<>();
            for (int i = 0; i < r.size(); i++) {
                decryptedCredential.put(orderedDecryptKeys.get(i), r.get(i));
            }
            this.decryptedHeaders = createDecryptedHeaders(getAllHeaders(action));
            listener.onResponse(true);
        }, e -> {
            log.error("Failed to decrypt credentials in connector", e);
            listener.onFailure(e);
        });
        function.apply(orderedToDecrypt, tenantId, updateDecryptedCredentialsListener);
    }

    @Override
    public Map<String, String> getHeadersWithRuntimeParameters(Map<String, String> runtimeParameters) {
        Map<String, String> headers = getDecryptedHeaders();
        validateConnectorHeaders(headers, protocol);
        return substituteHeadersWithRuntimeParameters(headers, runtimeParameters);
    }

    /**
     * Validates connector headers for both security and protocol compatibility.
     * 
     * @param headers Connector headers to validate
     * @param connectorProtocol The connector protocol
     * @throws IllegalArgumentException if validation fails
     */
    public static void validateConnectorHeaders(Map<String, String> headers, String connectorProtocol) {
        if (headers == null || headers.isEmpty()) {
            return;
        }

        boolean isMcpProtocol = connectorProtocol != null && MCP_PROTOCOLS.contains(connectorProtocol.toLowerCase(Locale.ROOT));

        for (Map.Entry<String, String> entry : headers.entrySet()) {
            String headerValue = entry.getValue();
            if (headerValue == null) {
                continue;
            }

            Matcher matcher = PARAMETER_PLACEHOLDER_PATTERN.matcher(headerValue);
            if (!matcher.find()) {
                continue;
            }

            if (isMcpProtocol) {
                throw new IllegalArgumentException(
                    String.format("Header '%s' cannot use ${parameters.*} placeholders in MCP connectors.", entry.getKey())
                );
            }

            String headerName = entry.getKey().toLowerCase(Locale.ROOT);
            if (BLOCKED_DYNAMIC_HEADERS.contains(headerName)) {
                throw new IllegalArgumentException(
                    String
                        .format(
                            "Header '%s' cannot use ${parameters.*} placeholders for security reasons. Use ${credential.*} instead.",
                            entry.getKey()
                        )
                );
            }
        }
    }

    protected abstract Map<String, String> getAllHeaders(String action);

}
