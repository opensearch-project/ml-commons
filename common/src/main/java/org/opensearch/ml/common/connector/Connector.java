/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.connector;

import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;
import static org.opensearch.ml.common.utils.StringUtils.gson;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.AccessController;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
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
import org.opensearch.ml.common.connector.auth.OAuth2AuthConfig;
import org.opensearch.ml.common.output.model.ModelTensor;
import org.opensearch.ml.common.transport.connector.MLCreateConnectorInput;

/**
 * Connector defines how to connect to a remote service.
 */
public interface Connector extends ToXContentObject, Writeable {

    String getName();

    String getTenantId();

    void setTenantId(String tenantId);

    String getProvisionedBy();

    void setProvisionedBy(String provisionedBy);

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

    /** The connector's {@code auth_config}; null when it has none, which only an HTTP-family connector can carry. */
    default OAuth2AuthConfig getAuthConfig() {
        return null;
    }

    BatchJobStatusMapping getBatchJobStatus();

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

    static Connector createConnector(XContentParser parser) throws IOException {
        return createConnector(parser.map());
    }

    /**
     * Parses the inline connector of a model register request: the strict {@code auth_config} checks of a request run
     * first, then the stored-document parser.
     *
     * @throws IllegalArgumentException when the {@code auth_config} content of the request is invalid
     */
    @SuppressWarnings("unchecked")
    static Connector createConnectorFromRequest(XContentParser parser) throws IOException {
        Map<String, Object> connectorMap = parser.mapOrdered();
        if (connectorMap.containsKey(AbstractConnector.AUTH_CONFIG_FIELD)) {
            Object authConfig = connectorMap.get(AbstractConnector.AUTH_CONFIG_FIELD);
            if (!(authConfig instanceof Map)) {
                throw new IllegalArgumentException(OAuth2AuthConfig.NOT_AN_OBJECT_MESSAGE);
            }
            OAuth2AuthConfig.parse((Map<String, Object>) authConfig);
        }
        return createConnector(connectorMap);
    }

    @SuppressWarnings("removal")
    private static Connector createConnector(Map<String, Object> connectorMap) throws IOException {
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

    /**
     * Create-, update- and register-time check. Parameters supplied only at predict time may remain in the path
     * and query; the scheme and host must already be resolved. Executors re-check the fully resolved URL.
     */
    default void validateConnectorURL(List<String> urlRegexes) {
        if (getActions() == null) {
            throw new IllegalArgumentException("No actions configured for this connector");
        }
        Map<String, String> parameters = getParameters();
        for (ConnectorAction action : getActions()) {
            StringSubstitutor substitutor = new StringSubstitutor(parameters, "${parameters.", "}");
            String url = substitutor.replace(action.getUrl());
            validateResolvedEndpoint(fillPathPlaceholders(url), urlRegexes);
        }
    }

    /**
     * Replaces {@code ${parameters.*}} placeholders after the authority with "0" so a URL whose path or query is
     * completed at predict time can be checked at create time. Predict time fills in nothing else, so any other
     * placeholder, and any placeholder in the scheme or authority, is left in place and rejected.
     */
    static String fillPathPlaceholders(String url) {
        if (url == null) {
            return null;
        }
        int schemeEnd = url.indexOf("://");
        if (schemeEnd < 0) {
            return url;
        }
        int authorityEnd = url.length();
        for (int i = schemeEnd + 3; i < url.length(); i++) {
            char c = url.charAt(i);
            if (c == '/' || c == '?' || c == '#') {
                authorityEnd = i;
                break;
            }
        }
        return url.substring(0, authorityEnd) + url.substring(authorityEnd).replaceAll("\\$\\{parameters\\.[^}]*\\}", "0");
    }

    /**
     * The string endpoint allowlists are matched against: scheme and host lowercased (RFC 3986 section 6.2.2.1),
     * then port, raw path and raw query as parsed. Callers must have rejected URIs without a scheme or host.
     */
    static String canonicalUrl(URI uri) {
        return uri.getScheme().toLowerCase(Locale.ROOT)
            + "://"
            + uri.getHost().toLowerCase(Locale.ROOT)
            + (uri.getPort() == -1 ? "" : ":" + uri.getPort())
            + (uri.getRawPath() == null ? "" : uri.getRawPath())
            + (uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery());
    }

    default void validateResolvedEndpoint(String resolvedUrl, List<String> urlRegexes) {
        if (urlRegexes == null || urlRegexes.isEmpty()) {
            throw new IllegalArgumentException(
                "Trusted connector endpoints regex is not configured. Please set plugins.ml_commons.trusted_connector_endpoints_regex."
            );
        }
        if (resolvedUrl == null) {
            throw new IllegalArgumentException("Resolved connector URL is null");
        }
        if (resolvedUrl.contains("${")) {
            logRejectedUrl(resolvedUrl);
            throw new IllegalArgumentException(
                "Connector URL contains an unresolved parameter placeholder; define it in the connector parameters"
            );
        }
        URI uri;
        try {
            uri = new URI(resolvedUrl);
        } catch (URISyntaxException e) {
            logRejectedUrl(resolvedUrl);
            throw new IllegalArgumentException("Connector URL is not a valid URI", e);
        }
        String scheme = uri.getScheme();
        if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
            logRejectedUrl(resolvedUrl);
            throw new IllegalArgumentException("Connector URL scheme must be http or https");
        }
        if (uri.getRawUserInfo() != null) {
            logRejectedUrl(resolvedUrl);
            throw new IllegalArgumentException("Connector URL must not contain user information");
        }
        if (uri.getRawFragment() != null) {
            logRejectedUrl(resolvedUrl);
            throw new IllegalArgumentException("Connector URL must not contain a fragment");
        }
        String host = uri.getHost();
        if (host == null || host.isEmpty()) {
            logRejectedUrl(resolvedUrl);
            throw new IllegalArgumentException("Connector URL has no host");
        }
        String canonical = canonicalUrl(uri);
        for (String urlRegex : urlRegexes) {
            Pattern pattern = Pattern.compile(urlRegex);
            Matcher matcher = pattern.matcher(canonical);
            if (matcher.matches()) {
                return;
            }
        }
        logRejectedUrl(resolvedUrl);
        throw new IllegalArgumentException("Connector URL is not matching the trusted connector endpoint regex");
    }

    private static void logRejectedUrl(String resolvedUrl) {
        RejectedConnectorUrlLog.warn(resolvedUrl);
    }

    Map<String, String> getDecryptedHeaders();

    Map<String, String> getDecryptedCredential();

    /** The raw credential map, which may still be encrypted. Needed to validate config before encryption. */
    Map<String, String> getCredential();

    default Map<String, String> getHeadersWithRuntimeParameters(Map<String, String> runtimeParameters) {
        return getDecryptedHeaders();
    }
}
