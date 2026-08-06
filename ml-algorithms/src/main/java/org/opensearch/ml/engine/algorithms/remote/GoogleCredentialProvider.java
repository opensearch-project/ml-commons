/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.remote;

import java.io.IOException;
import java.net.URI;
import java.util.Collections;
import java.util.List;

import org.opensearch.ml.common.connector.GoogleCloudConnector;
import org.opensearch.ml.common.exception.MLException;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;

import lombok.extern.log4j.Log4j2;

/**
 * Mints and refreshes GCP OAuth2 access tokens for the google_cloud connector protocol.
 * Wraps google-auth-library; token caching/refresh is handled by the underlying
 * {@link GoogleCredentials} instance.
 */
@Log4j2
public class GoogleCredentialProvider {

    private final GoogleCredentials credentials;

    public GoogleCredentialProvider(GoogleCredentials credentials) {
        this.credentials = credentials;
    }

    /**
     * Build a provider from connector config. Service-account key mode reconstructs
     * {@link ServiceAccountCredentials} from the decrypted credential fields; ADC mode uses
     * {@link GoogleCredentials#getApplicationDefault()}.
     */
    public static GoogleCredentialProvider fromConnector(GoogleCloudConnector connector) {
        try {
            List<String> scopes = Collections.singletonList(connector.getScopes());
            GoogleCredentials creds;
            if (connector.useAdc()) {
                creds = GoogleCredentials.getApplicationDefault().createScoped(scopes);
            } else {
                creds = ServiceAccountCredentials
                    .newBuilder()
                    .setClientEmail(connector.getClientEmail())
                    .setPrivateKeyString(connector.getPrivateKey())
                    .setTokenServerUri(validateTokenUri(connector.getTokenUri()))
                    .setScopes(scopes)
                    .build();
            }
            return new GoogleCredentialProvider(creds);
        } catch (IOException e) {
            String mode = connector.useAdc() ? "ADC/Workload Identity" : "service-account key";
            throw new MLException("Failed to initialize Google credentials (" + mode + "): " + e.getMessage(), e);
        }
    }

    /**
     * The single valid host for the OAuth2 token-mint endpoint. This is the only Google surface
     * that legitimately receives the signed JWT during a service-account token exchange.
     */
    private static final String OAUTH2_TOKEN_HOST = "oauth2.googleapis.com";

    /**
     * Validate the OAuth2 token endpoint before it is used to refresh tokens. token_uri is a
     * user-controlled connector credential; without this guard an administrator could point it at
     * an internal address and have the node POST the signed JWT there (SSRF). Service-account key
     * exchange only ever targets {@value #OAUTH2_TOKEN_HOST}, so restrict token_uri to exactly that
     * host over HTTPS rather than any *.googleapis.com surface. (ADC/Workload Identity does not use
     * this path -- it resolves credentials via {@link GoogleCredentials#getApplicationDefault()}.)
     */
    static URI validateTokenUri(String tokenUri) {
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
        if (!OAUTH2_TOKEN_HOST.equals(host.toLowerCase(java.util.Locale.ROOT))) {
            throw new IllegalArgumentException("token_uri host must be " + OAUTH2_TOKEN_HOST + ", got: " + host);
        }
        return uri;
    }

    public String getAccessToken() {
        try {
            credentials.refreshIfExpired();
            return credentials.getAccessToken().getTokenValue();
        } catch (IOException e) {
            throw new MLException("Failed to obtain Google OAuth2 access token: " + e.getMessage(), e);
        }
    }
}
