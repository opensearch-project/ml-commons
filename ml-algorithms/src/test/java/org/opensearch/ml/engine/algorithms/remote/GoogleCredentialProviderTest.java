/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.remote;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import java.io.IOException;
import java.net.URI;
import java.util.Date;

import org.junit.Test;
import org.opensearch.ml.common.exception.MLException;

import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;

public class GoogleCredentialProviderTest {

    @Test
    public void getAccessToken_ReturnsTokenValue() {
        // Real credentials backed by a non-expiring static token: refreshIfExpired() is a
        // no-op (no network), and getAccessToken() (a final method, so not mockable) returns
        // the token value.
        AccessToken token = new AccessToken("ya29.test-token", new Date(Long.MAX_VALUE));
        GoogleCredentials credentials = GoogleCredentials.create(token);

        GoogleCredentialProvider provider = new GoogleCredentialProvider(credentials);

        assertEquals("ya29.test-token", provider.getAccessToken());
    }

    @Test(expected = MLException.class)
    public void getAccessToken_RefreshFailure_WrapsInMLException() throws IOException {
        // refreshIfExpired() is non-final, so it can be stubbed to throw. The failure occurs
        // before the final getAccessToken() accessor is reached.
        GoogleCredentials credentials = mock(GoogleCredentials.class);
        doThrow(new IOException("boom")).when(credentials).refreshIfExpired();

        GoogleCredentialProvider provider = new GoogleCredentialProvider(credentials);
        provider.getAccessToken();
    }

    @Test
    public void validateTokenUri_acceptsGoogleEndpoints() {
        assertEquals(
            URI.create("https://oauth2.googleapis.com/token"),
            GoogleCredentialProvider.validateTokenUri("https://oauth2.googleapis.com/token")
        );
        assertEquals(
            URI.create("https://us-central1-aiplatform.googleapis.com/token"),
            GoogleCredentialProvider.validateTokenUri("https://us-central1-aiplatform.googleapis.com/token")
        );
    }

    @Test(expected = IllegalArgumentException.class)
    public void validateTokenUri_rejectsNonGoogleHost() {
        GoogleCredentialProvider.validateTokenUri("https://evil.example.com/token");
    }

    @Test(expected = IllegalArgumentException.class)
    public void validateTokenUri_rejectsInternalMetadataAddress() {
        GoogleCredentialProvider.validateTokenUri("http://169.254.169.254/token");
    }

    @Test(expected = IllegalArgumentException.class)
    public void validateTokenUri_rejectsNonHttpsScheme() {
        GoogleCredentialProvider.validateTokenUri("http://oauth2.googleapis.com/token");
    }

    @Test(expected = IllegalArgumentException.class)
    public void validateTokenUri_rejectsHostSuffixSpoofing() {
        // Ensure endsWith(".googleapis.com") cannot be bypassed by a lookalike domain.
        GoogleCredentialProvider.validateTokenUri("https://googleapis.com.evil.example/token");
    }
}
