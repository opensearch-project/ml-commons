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
                    .setTokenServerUri(URI.create(connector.getTokenUri()))
                    .setScopes(scopes)
                    .build();
            }
            return new GoogleCredentialProvider(creds);
        } catch (IOException e) {
            String mode = connector.useAdc() ? "ADC/Workload Identity" : "service-account key";
            throw new MLException("Failed to initialize Google credentials (" + mode + "): " + e.getMessage(), e);
        }
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
