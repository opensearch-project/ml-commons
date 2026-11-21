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
import org.opensearch.ml.common.connector.GoogleCloudConnector;

/**
 * Write-time checks of the {@code auth_config} a connector holds: every credential key it references must be a key of
 * the connector credential, and a configuration whose type attaches credentials also has its client authentication,
 * token endpoint, action headers, action URLs and client config checked. Every write path runs these checks on the
 * connector it is about to store, after the request-level checks of {@code ConnectorProtocolValidator}.
 */
public final class AuthConfigValidator {

    public static final String GRANT_TYPE_NOT_SUPPORTED_MESSAGE = "auth_config.grant_type is not supported in this version; use "
        + OAuth2AuthConfig.GRANT_TYPE_CLIENT_CREDENTIALS
        + " or "
        + OAuth2AuthConfig.GRANT_TYPE_JWT_BEARER;
    public static final String CLIENT_AUTH_METHOD_NONE_NOT_SUPPORTED_MESSAGE = "auth_config.client_auth_method ["
        + OAuth2AuthConfig.CLIENT_AUTH_METHOD_NONE
        + "] is supported only with the jwt-bearer grant in this version";
    public static final String AUTHORIZATION_HEADER_MESSAGE =
        "A connector with an active auth_config must not define an Authorization header; the token is injected at request time";
    public static final String CLEARTEXT_ACTION_URL_MESSAGE = "A connector with an active auth_config must use https for every action URL";
    public static final String SKIP_SSL_VERIFICATION_MESSAGE = "A connector with an active auth_config must not set client_config."
        + ConnectorClientConfig.SKIP_SSL_VERIFICATION_FIELD;
    public static final String GOOGLE_CLOUD_AUTHORIZATION_HEADER_MESSAGE =
        "A google_cloud connector must not define an Authorization header; its access token is sent in that header. Remove the header";
    public static final String GOOGLE_CLOUD_CLEARTEXT_ACTION_URL_MESSAGE =
        "A google_cloud connector must use https for every action URL: its access token is sent with every request";
    public static final String GOOGLE_CLOUD_SKIP_SSL_VERIFICATION_MESSAGE = "A google_cloud connector must not set client_config."
        + ConnectorClientConfig.SKIP_SSL_VERIFICATION_FIELD
        + ": its access token is sent with every request";
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
     * Validates {@code config}, the {@code auth_config} of {@code connector}; a connector without one passes, except a
     * {@code google_cloud} connector that defines an Authorization header, which its access token would replace.
     * <p>
     * A {@code google_cloud} connector without a declaration is judged twice: the write paths pass {@code null} and get
     * the header check, so a new connector cannot take such a header; the executor passes the configuration the stored
     * identity stands for, which is judged as an active configuration without the header check, so a connector written
     * before this check keeps working, its header replaced and logged where its provider is built. Its messages name
     * the protocol rather than a declaration it does not hold.
     *
     * @param connector the connector as it will be stored; its actions and client config are judged for an active type
     * @param config the configuration to judge, as the caller reads it from the connector; null when it holds none
     * @param credentialKeys the credential keys the stored connector will have; references must name one of them
     * @param allowlist the trusted token endpoint patterns a literal {@code token_url} must match
     * @return {@code config}
     * @throws IllegalArgumentException when the configuration is invalid
     */
    public static OAuth2AuthConfig validate(
        Connector connector,
        OAuth2AuthConfig config,
        Set<String> credentialKeys,
        TokenEndpointAllowlist allowlist
    ) {
        if (config == null) {
            if (isLegacyGoogleCloud(connector)) {
                requireNoAuthorizationHeader(connector, GOOGLE_CLOUD_AUTHORIZATION_HEADER_MESSAGE);
            }
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

    /**
     * The checks of a configuration that attaches a token: the grant and what it authenticates with, then the token
     * endpoint, the actions and the client config, which every grant shares. A {@code google_adc} configuration makes
     * no token request of its own, so only the connector is examined.
     */
    private static void validateActive(Connector connector, OAuth2AuthConfig config, TokenEndpointAllowlist allowlist) {
        if (config.getType() != ConnectorAuthType.GOOGLE_ADC) {
            validateTokenRequest(config, allowlist);
        }
        validateConnector(connector, isLegacyGoogleCloud(connector));
    }

    /** The grant, what it authenticates with and the literal token endpoint. */
    private static void validateTokenRequest(OAuth2AuthConfig config, TokenEndpointAllowlist allowlist) {
        String grantType = config.getGrantType();
        if (OAuth2AuthConfig.GRANT_TYPE_CLIENT_CREDENTIALS.equals(grantType)) {
            validateClientCredentials(config);
        } else if (!OAuth2AuthConfig.GRANT_TYPE_JWT_BEARER.equals(grantType)) {
            // The JWT bearer grant authenticates with its assertion alone: the configuration already holds the jwt block,
            // names no method and has no secret, and the key the block references is parsed where the credential is
            // plaintext (validatePrivateKey) or when an assertion is signed.
            throw new IllegalArgumentException(GRANT_TYPE_NOT_SUPPORTED_MESSAGE);
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
    }

    /** No action writes an {@code Authorization} header, every action URL is https and TLS is verified. */
    /**
     * The actions and the client config of a connector whose requests carry a token. A {@code google_cloud} connector
     * without a declaration ({@code legacyGoogleCloud}) is not refused for an Authorization header, which the token
     * replaces, and gets messages that name the protocol.
     */
    private static void validateConnector(Connector connector, boolean legacyGoogleCloud) {
        if (!legacyGoogleCloud) {
            requireNoAuthorizationHeader(connector, AUTHORIZATION_HEADER_MESSAGE);
        }
        // A scheme that is still a placeholder after substitution passes here: it is known only once a request resolves it.
        if (ConnectorAction.firstCleartextUrl(connector.getActions(), connector.getParameters()) != null) {
            throw new IllegalArgumentException(
                legacyGoogleCloud ? GOOGLE_CLOUD_CLEARTEXT_ACTION_URL_MESSAGE : CLEARTEXT_ACTION_URL_MESSAGE
            );
        }
        ConnectorClientConfig clientConfig = connector.getConnectorClientConfig();
        if (clientConfig != null && Boolean.TRUE.equals(clientConfig.getSkipSslVerification())) {
            throw new IllegalArgumentException(
                legacyGoogleCloud ? GOOGLE_CLOUD_SKIP_SSL_VERIFICATION_MESSAGE : SKIP_SSL_VERIFICATION_MESSAGE
            );
        }
    }

    /** Whether {@code connector} is a {@code google_cloud} connector without a declaration, so that its stored identity stands for one. */
    private static boolean isLegacyGoogleCloud(Connector connector) {
        return connector instanceof GoogleCloudConnector && connector.getAuthConfig() == null;
    }

    /** Whether an action of {@code connector} defines an Authorization header, in any case. */
    public static boolean definesAuthorizationHeader(Connector connector) {
        if (connector.getActions() == null) {
            return false;
        }
        for (ConnectorAction action : connector.getActions()) {
            if (action.getHeaders() == null) {
                continue;
            }
            for (String header : action.getHeaders().keySet()) {
                if (OAuth2Support.AUTHORIZATION_HEADER.equalsIgnoreCase(header)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void requireNoAuthorizationHeader(Connector connector, String message) {
        if (definesAuthorizationHeader(connector)) {
            throw new IllegalArgumentException(message);
        }
    }

    /**
     * Whether an update of {@code stored} that supplies actions ({@code suppliesActions}) is judged as a create: a
     * {@code google_cloud} connector without a declaration may keep a stored Authorization header, but not take one.
     */
    public static boolean actionsAreJudgedAsNew(Connector stored, boolean suppliesActions) {
        return suppliesActions && isLegacyGoogleCloud(stored);
    }

    /** The client credentials grant authenticates with a client id and secret, which the method must send. */
    private static void validateClientCredentials(OAuth2AuthConfig config) {
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
     * Parses the private key that the {@code jwt.private_key} reference of {@code config} names, when
     * {@code plaintextCredential} holds it, for the block's {@code signing_alg}, so that a key the signer could not use
     * is refused where the plaintext is still at hand instead of on every request the connector serves. Nothing is read
     * for a configuration that is absent, attaches nothing or has no {@code jwt} block, or when the map lacks the key
     * ({@link #validate} reports a missing key). The key material is parsed and dropped; it is never logged or echoed.
     *
     * @param config the configuration the write leaves; may be null
     * @param plaintextCredential the credential the request carries, before encryption; may be null
     * @throws IllegalArgumentException with a message of {@link PrivateKeys#parsePkcs8} when the key is not an
     *     unencrypted PKCS#8 key the algorithm signs with
     */
    public static void validatePrivateKey(OAuth2AuthConfig config, Map<String, String> plaintextCredential) {
        if (config == null || !config.getType().isActive() || config.getJwt() == null || plaintextCredential == null) {
            return;
        }
        JwtSpec jwt = config.getJwt();
        String pem = plaintextCredential.get(CredentialReference.keyOf(jwt.getPrivateKey()));
        if (pem != null) {
            PrivateKeys.parsePkcs8(pem, jwt.getSigningAlg());
        }
    }

    /**
     * Whether {@link #validate} would accept {@code config} on {@code connector}. Never throws: any exception counts
     * as a failure. Used to tell whether a stored configuration already failed before an update.
     */
    public static boolean passes(
        Connector connector,
        OAuth2AuthConfig config,
        Set<String> credentialKeys,
        TokenEndpointAllowlist allowlist
    ) {
        try {
            validate(connector, config, credentialKeys, allowlist);
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
        OAuth2AuthConfig config,
        Set<String> credentialKeys,
        TokenEndpointAllowlist allowlist,
        boolean authOrProtocolTouched,
        boolean storedPassed
    ) {
        try {
            validate(connector, config, credentialKeys, allowlist);
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

    /** {@link #requireHttps(URI)} for a request of {@code connector}, with the message of a {@code google_cloud} connector without a declaration. */
    public static void requireHttps(URI endpoint, Connector connector) {
        if (!OAuth2AuthConfig.HTTPS.equalsIgnoreCase(endpoint.getScheme())) {
            throw new IllegalArgumentException(
                isLegacyGoogleCloud(connector) ? GOOGLE_CLOUD_CLEARTEXT_ACTION_URL_MESSAGE : CLEARTEXT_ACTION_URL_MESSAGE
            );
        }
    }

    /** A copy of the keys of {@code credential}; empty for a null map. */
    public static Set<String> keysOf(Map<String, String> credential) {
        return credential == null ? Set.of() : Set.copyOf(credential.keySet());
    }
}
