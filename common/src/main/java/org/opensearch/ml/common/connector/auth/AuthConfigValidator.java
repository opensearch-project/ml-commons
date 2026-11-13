/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.connector.auth;

import java.net.URI;
import java.util.Map;
import java.util.Set;

import org.opensearch.ml.common.connector.Connector;
import org.opensearch.ml.common.connector.ConnectorAction;
import org.opensearch.ml.common.connector.ConnectorClientConfig;

/**
 * Write-time checks of the {@code auth_config} a connector holds: every credential key it references must be a key of
 * the connector credential, and a configuration whose type attaches credentials also has its client authentication,
 * token endpoint, action headers, action URLs and client config checked. Every write path runs these checks on the
 * connector it is about to store, after the request-level checks of {@code ConnectorProtocolValidator}.
 */
public final class AuthConfigValidator {

    public static final String GRANT_TYPE_NOT_SUPPORTED_MESSAGE = "auth_config.grant_type is not supported in this version; use "
        + OAuth2AuthConfig.GRANT_TYPE_CLIENT_CREDENTIALS;
    public static final String CLIENT_AUTH_METHOD_NONE_NOT_SUPPORTED_MESSAGE = "auth_config.client_auth_method ["
        + OAuth2AuthConfig.CLIENT_AUTH_METHOD_NONE
        + "] is not supported in this version";
    public static final String AUTHORIZATION_HEADER_MESSAGE =
        "A connector with an active auth_config must not define an Authorization header; the token is injected at request time";
    public static final String CLEARTEXT_ACTION_URL_MESSAGE = "A connector with an active auth_config must use https for every action URL";
    public static final String SKIP_SSL_VERIFICATION_MESSAGE = "A connector with an active auth_config must not set client_config."
        + ConnectorClientConfig.SKIP_SSL_VERIFICATION_FIELD;
    public static final String TOKEN_URL_DOT_SEGMENT_MESSAGE = "auth_config.token_url must not contain a dot segment (. or ..) in its path";
    public static final String TOKEN_URL_PATH_MUST_BE_ASCII_MESSAGE =
        "auth_config.token_url path must contain only ASCII characters; percent-encode other characters";

    private AuthConfigValidator() {}

    /** The message for a reference to credential {@code key}, which the connector credential does not contain. */
    public static String missingCredentialKeyMessage(String key) {
        return "auth_config references credential key [" + key + "], which the connector credential does not contain";
    }

    /** The message for a configuration without {@code client_id} whose client authentication method needs one. */
    public static String clientIdRequiredMessage(String method) {
        return "auth_config.client_id is required for client_auth_method " + method;
    }

    /** The message for a configuration without {@code client_secret} whose client authentication method needs one. */
    public static String clientSecretRequiredMessage(String method) {
        return "auth_config.client_secret is required for client_auth_method " + method;
    }

    /**
     * Validates the {@code auth_config} {@code connector} holds; a connector without one passes.
     *
     * @param connector the connector as it will be stored
     * @param credentialKeys the credential keys the stored connector will have; references must name one of them
     * @param allowlist the trusted token endpoint patterns a literal {@code token_url} must match
     * @return the configuration; null when the connector holds none
     * @throws IllegalArgumentException when the configuration is invalid
     */
    public static OAuth2AuthConfig validate(Connector connector, Set<String> credentialKeys, TokenEndpointAllowlist allowlist) {
        OAuth2AuthConfig config = connector.getAuthConfig();
        if (config == null) {
            return null;
        }
        // Sorted, so the first missing key is reported deterministically.
        for (String key : config.referencedCredentialKeys()) {
            if (!credentialKeys.contains(key)) {
                throw new IllegalArgumentException(missingCredentialKeyMessage(key));
            }
        }
        if (config.getType().isActive()) {
            validateActive(connector, config, allowlist);
        }
        return config;
    }

    private static void validateActive(Connector connector, OAuth2AuthConfig config, TokenEndpointAllowlist allowlist) {
        if (!OAuth2AuthConfig.GRANT_TYPE_CLIENT_CREDENTIALS.equals(config.getGrantType())) {
            throw new IllegalArgumentException(GRANT_TYPE_NOT_SUPPORTED_MESSAGE);
        }
        // The parse admits only client_secret_basic, client_secret_post and none, so after this check the method sends
        // a client id and secret.
        String method = config.getClientAuthMethod();
        if (OAuth2AuthConfig.CLIENT_AUTH_METHOD_NONE.equals(method)) {
            throw new IllegalArgumentException(CLIENT_AUTH_METHOD_NONE_NOT_SUPPORTED_MESSAGE);
        }
        if (config.getClientId() == null) {
            throw new IllegalArgumentException(clientIdRequiredMessage(method));
        }
        if (config.getClientSecret() == null) {
            throw new IllegalArgumentException(clientSecretRequiredMessage(method));
        }
        // A reference is checked when a token is requested, once its value is known.
        String tokenUrl = config.getTokenUrl();
        if (!CredentialReference.isReference(tokenUrl)) {
            String canonical = OAuth2AuthConfig.canonicalTokenUrl(tokenUrl);
            // The token client sends only a URL that OkHttp leaves unchanged, and OkHttp resolves dot segments.
            if (hasDotSegment(canonical)) {
                throw new IllegalArgumentException(TOKEN_URL_DOT_SEGMENT_MESSAGE);
            }
            // OkHttp also percent-encodes a path character outside ASCII, which URI accepts raw, so the URL sent would
            // never equal the stored canonical form.
            if (hasNonAsciiPath(canonical)) {
                throw new IllegalArgumentException(TOKEN_URL_PATH_MUST_BE_ASCII_MESSAGE);
            }
            if (!allowlist.matches(canonical)) {
                throw new IllegalArgumentException(TokenEndpointAllowlist.NOT_ALLOWED_MESSAGE);
            }
        }
        if (connector.getActions() != null) {
            for (ConnectorAction action : connector.getActions()) {
                if (action.getHeaders() == null) {
                    continue;
                }
                for (String header : action.getHeaders().keySet()) {
                    if (OAuth2Support.AUTHORIZATION_HEADER.equalsIgnoreCase(header)) {
                        throw new IllegalArgumentException(AUTHORIZATION_HEADER_MESSAGE);
                    }
                }
            }
        }
        // A scheme that is still a placeholder after substitution passes here: it is known only once a request resolves it.
        if (ConnectorAction.firstCleartextUrl(connector.getActions(), connector.getParameters()) != null) {
            throw new IllegalArgumentException(CLEARTEXT_ACTION_URL_MESSAGE);
        }
        ConnectorClientConfig clientConfig = connector.getConnectorClientConfig();
        if (clientConfig != null && Boolean.TRUE.equals(clientConfig.getSkipSslVerification())) {
            throw new IllegalArgumentException(SKIP_SSL_VERIFICATION_MESSAGE);
        }
    }

    /** Whether the decoded path of {@code canonicalTokenUrl} has a segment that is {@code .} or {@code ..}. */
    private static boolean hasDotSegment(String canonicalTokenUrl) {
        for (String segment : URI.create(canonicalTokenUrl).getPath().split("/")) {
            if (".".equals(segment) || "..".equals(segment)) {
                return true;
            }
        }
        return false;
    }

    /** Whether the raw path of {@code canonicalTokenUrl} has a character outside ASCII. */
    private static boolean hasNonAsciiPath(String canonicalTokenUrl) {
        return URI.create(canonicalTokenUrl).getRawPath().chars().anyMatch(c -> c > 127);
    }

    /**
     * Whether {@link #validate} would accept {@code connector}. Never throws: any exception counts as a failure. Used
     * to tell whether a stored configuration already failed before an update.
     */
    public static boolean passes(Connector connector, Set<String> credentialKeys, TokenEndpointAllowlist allowlist) {
        try {
            validate(connector, credentialKeys, allowlist);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * {@link #validate} for the connector an update is about to write. An invalid configuration is let through, for
     * the caller to write unchanged and warn about, when the request touches neither {@code auth_config} nor the
     * protocol ({@code authOrProtocolTouched}) and the stored configuration already failed ({@code storedPassed}).
     *
     * @return true when the configuration fails validation but the write may proceed; false when it passes
     * @throws IllegalArgumentException when the configuration is invalid and the update is not of that kind
     */
    public static boolean validateOrGrandfather(
        Connector connector,
        Set<String> credentialKeys,
        TokenEndpointAllowlist allowlist,
        boolean authOrProtocolTouched,
        boolean storedPassed
    ) {
        try {
            validate(connector, credentialKeys, allowlist);
            return false;
        } catch (IllegalArgumentException e) {
            if (!authOrProtocolTouched && !storedPassed) {
                return true;
            }
            throw e;
        }
    }

    /** @throws IllegalArgumentException with {@link #CLEARTEXT_ACTION_URL_MESSAGE} unless {@code endpoint} uses https */
    public static void requireHttps(URI endpoint) {
        if (!OAuth2AuthConfig.HTTPS.equalsIgnoreCase(endpoint.getScheme())) {
            throw new IllegalArgumentException(CLEARTEXT_ACTION_URL_MESSAGE);
        }
    }

    /** A copy of the keys of {@code credential}; empty for a null map. */
    public static Set<String> keysOf(Map<String, String> credential) {
        return credential == null ? Set.of() : Set.copyOf(credential.keySet());
    }
}
