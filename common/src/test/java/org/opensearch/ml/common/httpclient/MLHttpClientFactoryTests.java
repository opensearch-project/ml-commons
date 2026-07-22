/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.httpclient;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.mock;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

import javax.net.ssl.KeyManager;
import javax.net.ssl.TrustManager;

import org.junit.Test;
import org.opensearch.ml.common.exception.MLValidationException;

import software.amazon.awssdk.http.async.SdkAsyncHttpClient;

public class MLHttpClientFactoryTests {

    @Test
    public void test_getSdkAsyncHttpClient_success() {
        SdkAsyncHttpClient client = MLHttpClientFactory
            .getAsyncHttpClient(
                Duration.ofSeconds(100),
                Duration.ofSeconds(100),
                100,
                false,
                Collections.emptyList(),
                Collections.emptyList(),
                false
            );
        assertNotNull(client);
        client = MLHttpClientFactory
            .getAsyncHttpClient(
                Duration.ofSeconds(100),
                Duration.ofSeconds(100),
                100,
                false,
                Collections.emptyList(),
                Collections.emptyList(),
                true
            );
        assertNotNull(client);
    }

    @Test
    public void test_getAsyncHttpClient_withoutSkipSslVerificationValue_success() {
        SdkAsyncHttpClient client = MLHttpClientFactory.getAsyncHttpClient(Duration.ofSeconds(100), Duration.ofSeconds(100), 100, false);
        assertNotNull(client);
    }

    @Test
    public void test_getAsyncHttpClient_withManagers_success() {
        KeyManager[] keyManagers = new KeyManager[0];
        TrustManager[] trustManagers = new TrustManager[0];

        SdkAsyncHttpClient client = MLHttpClientFactory
            .getAsyncHttpClient(
                Duration.ofSeconds(100),
                Duration.ofSeconds(100),
                100,
                false,
                Collections.emptyList(),
                Collections.emptyList(),
                false,
                keyManagers,
                trustManagers
            );
        assertNotNull(client);
    }

    @Test
    public void test_getAsyncHttpClient_withManagers_nullManagers_success() {
        SdkAsyncHttpClient client = MLHttpClientFactory
            .getAsyncHttpClient(
                Duration.ofSeconds(100),
                Duration.ofSeconds(100),
                100,
                false,
                Collections.emptyList(),
                Collections.emptyList(),
                false,
                null,
                null
            );
        assertNotNull(client);
    }

    @Test
    public void test_getAsyncHttpClient_withManagers_skipSslVerification_success() {
        KeyManager[] keyManagers = new KeyManager[0];
        TrustManager[] trustManagers = new TrustManager[0];

        SdkAsyncHttpClient client = MLHttpClientFactory
            .getAsyncHttpClient(
                Duration.ofSeconds(100),
                Duration.ofSeconds(100),
                100,
                false,
                Collections.emptyList(),
                Collections.emptyList(),
                true,
                keyManagers,
                trustManagers
            );
        assertNotNull(client);
    }

    @Test
    public void test_getAsyncHttpClient_withOnlyKeyManagers_success() {
        KeyManager[] keyManagers = new KeyManager[0];

        SdkAsyncHttpClient client = MLHttpClientFactory
            .getAsyncHttpClient(
                Duration.ofSeconds(100),
                Duration.ofSeconds(100),
                100,
                false,
                Collections.emptyList(),
                Collections.emptyList(),
                false,
                keyManagers,
                null
            );
        assertNotNull(client);
    }

    @Test
    public void test_getAsyncHttpClient_withOnlyTrustManagers_success() {
        TrustManager[] trustManagers = new TrustManager[0];

        SdkAsyncHttpClient client = MLHttpClientFactory
            .getAsyncHttpClient(
                Duration.ofSeconds(100),
                Duration.ofSeconds(100),
                100,
                false,
                Collections.emptyList(),
                Collections.emptyList(),
                false,
                null,
                trustManagers
            );
        assertNotNull(client);
    }

    @Test
    public void test_getAsyncHttpClient_mTlsWithSkipSslVerification_success() {
        KeyManager[] keyManagers = new KeyManager[0];

        SdkAsyncHttpClient client = MLHttpClientFactory
            .getAsyncHttpClient(
                Duration.ofSeconds(100),
                Duration.ofSeconds(100),
                100,
                false,
                Collections.emptyList(),
                Collections.emptyList(),
                true, // skipSslVerification = true
                keyManagers, // empty array - no client cert actually presented
                null
            );
        assertNotNull(client);
    }

    @Test
    public void test_getAsyncHttpClient_mTlsWithSkipSslVerification_throwsException() {
        KeyManager[] keyManagers = new KeyManager[] { mock(KeyManager.class) };

        assertThrows(
            MLValidationException.class,
            () -> MLHttpClientFactory
                .getAsyncHttpClient(
                    Duration.ofSeconds(100),
                    Duration.ofSeconds(100),
                    100,
                    false,
                    Collections.emptyList(),
                    Collections.emptyList(),
                    true, // skipSslVerification = true
                    keyManagers, // a real client cert is present
                    null
                )
        );
    }

    @Test
    public void test_getAsyncHttpClient_withSecurityFeatures_success() {
        List<Pattern> trustedEndpoints = Arrays.asList(Pattern.compile("http://trusted\\..*"));
        List<Pattern> restrictedPatterns = Arrays.asList(Pattern.compile("192\\.168\\..*"));

        SdkAsyncHttpClient client = MLHttpClientFactory
            .getAsyncHttpClient(Duration.ofSeconds(100), Duration.ofSeconds(100), 100, true, trustedEndpoints, restrictedPatterns, true);
        assertNotNull(client);
    }
}