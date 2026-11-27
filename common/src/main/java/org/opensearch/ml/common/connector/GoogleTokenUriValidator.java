/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.connector;

import java.net.URI;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * The {@code token_uri} rule for {@code google_cloud} connectors that authenticate with a service-account key.
 * <p>
 * The same rule runs in two places: when credentials are built for a request, and when a connector is created or
 * changed (connector create and update, inline connector on model register and update). The second place sees
 * the credential only while it is still plaintext, before it is encrypted, so it is not part of
 * {@link GoogleCloudConnector}'s own validation, which also runs on stored, encrypted connectors.
 */
public final class GoogleTokenUriValidator {

    /**
     * The single valid host for the OAuth2 token-mint endpoint. This is the only Google surface that legitimately
     * receives the signed JWT during a service-account token exchange.
     */
    public static final String OAUTH2_TOKEN_HOST = "oauth2.googleapis.com";

    private GoogleTokenUriValidator() {}

    /**
     * Validate the OAuth2 token endpoint before it is used to refresh tokens. token_uri is a user-controlled
     * connector credential; without this guard an administrator could point it at an internal address and have the
     * node POST the signed JWT there (SSRF). Service-account key exchange only ever targets {@value #OAUTH2_TOKEN_HOST},
     * so restrict token_uri to exactly that host over HTTPS rather than any *.googleapis.com surface. (ADC/Workload
     * Identity does not use this path.)
     *
     * <p>
     * Same rule and messages as {@code GoogleCredentialProvider.validateTokenUri} in ml-algorithms, which checks the
     * decrypted value when a token is requested. That copy is left as it is so this change does not touch code that
     * the move of google_cloud onto the shared OAuth engine (#5244) deletes.
     *
     * @throws IllegalArgumentException if the URI is not https on {@value #OAUTH2_TOKEN_HOST} and the default port
     */
    public static URI validateTokenUri(String tokenUri) {
        URI uri;
        try {
            uri = URI.create(tokenUri);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid token_uri: " + tokenUri);
        }
        String scheme = uri.getScheme();
        String host = uri.getHost();
        if (!"https".equalsIgnoreCase(scheme) || host == null) {
            throw new IllegalArgumentException("token_uri must be an https Google endpoint, got: " + tokenUri);
        }
        if (!OAUTH2_TOKEN_HOST.equals(host.toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("token_uri host must be " + OAUTH2_TOKEN_HOST + ", got: " + host);
        }
        // Pinning only the host still allows an arbitrary port on it, which would send the signed JWT
        // somewhere other than the real token endpoint. Accept only the default https port.
        int port = uri.getPort();
        if (port != -1 && port != 443) {
            throw new IllegalArgumentException("token_uri must use the default https port, got port: " + port);
        }
        return uri;
    }

    /**
     * Checks the {@code token_uri} of a plaintext credential a request supplies, so a value the request path would
     * reject is refused when the connector is saved rather than at its first predict.
     * <p>
     * Applies only where the request path uses {@code token_uri}: the {@code google_cloud} protocol in
     * service-account key mode. A credential without {@code token_uri} uses the default endpoint.
     *
     * @param protocol the connector protocol after the request is applied
     * @param parameters the connector parameters after the request is applied; they decide the auth mode
     * @param credential the plaintext credential the request supplies; {@code null} or empty is ignored
     * @throws IllegalArgumentException if {@code token_uri} is not allowed
     */
    public static void validateCredential(String protocol, Map<String, String> parameters, Map<String, String> credential) {
        if (!ConnectorProtocols.GOOGLE_CLOUD.equals(protocol) || credential == null || isAdcMode(parameters)) {
            return;
        }
        String tokenUri = credential.get(GoogleCloudConnector.TOKEN_URI_FIELD);
        if (tokenUri != null) {
            validateTokenUri(tokenUri);
        }
    }

    /**
     * Update variant of {@link #validateCredential(String, Map, Map)}. Only the credential the update supplies is
     * checked: a stored credential is encrypted, and an update without a credential keeps it unchanged. Protocol and
     * parameters are resolved the way {@code HttpConnector#update} applies them: the protocol is replaced when the
     * update sets one, and parameters are merged.
     *
     * @param storedProtocol the protocol the connector has now
     * @param storedParameters the parameters the connector has now
     * @param updatedProtocol the protocol the update sets, or {@code null}
     * @param updatedParameters the parameters the update sets, or {@code null}
     * @param updatedCredential the plaintext credential the update supplies, or {@code null}
     */
    public static void validateCredentialAfterUpdate(
        String storedProtocol,
        Map<String, String> storedParameters,
        String updatedProtocol,
        Map<String, String> updatedParameters,
        Map<String, String> updatedCredential
    ) {
        if (updatedCredential == null || updatedCredential.isEmpty()) {
            return;
        }
        Map<String, String> parameters = new HashMap<>();
        if (storedParameters != null) {
            parameters.putAll(storedParameters);
        }
        if (updatedParameters != null) {
            parameters.putAll(updatedParameters);
        }
        validateCredential(updatedProtocol != null ? updatedProtocol : storedProtocol, parameters, updatedCredential);
    }

    private static boolean isAdcMode(Map<String, String> parameters) {
        return parameters != null
            && GoogleCloudConnector.AUTH_MODE_ADC.equalsIgnoreCase(parameters.get(GoogleCloudConnector.AUTH_MODE_FIELD));
    }
}
