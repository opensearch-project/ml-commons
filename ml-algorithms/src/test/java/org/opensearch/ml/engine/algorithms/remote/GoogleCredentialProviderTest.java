/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.remote;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import java.io.IOException;
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
}
