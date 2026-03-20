/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.httpclient;

import static org.junit.Assert.assertNotNull;

import java.time.Duration;

import javax.net.ssl.KeyManager;
import javax.net.ssl.TrustManager;

import org.junit.Test;

import software.amazon.awssdk.http.async.SdkAsyncHttpClient;

public class MLHttpClientFactoryTests {

    @Test
    public void test_getSdkAsyncHttpClient_success() {
        SdkAsyncHttpClient client = MLHttpClientFactory
            .getAsyncHttpClient(Duration.ofSeconds(100), Duration.ofSeconds(100), 100, false, false);
        assertNotNull(client);
        client = MLHttpClientFactory.getAsyncHttpClient(Duration.ofSeconds(100), Duration.ofSeconds(100), 100, false, true);
        assertNotNull(client);
    }

    @Test
    public void test_getAsyncHttpClient_withoutSkipSslVerificationValue_success() {
        SdkAsyncHttpClient client = MLHttpClientFactory.getAsyncHttpClient(Duration.ofSeconds(100), Duration.ofSeconds(100), 100, false);
        assertNotNull(client);
    }

    @Test
    public void test_getAsyncHttpClient_withSSLContext_success() {
        SdkAsyncHttpClient client = MLHttpClientFactory
            .getAsyncHttpClient(Duration.ofSeconds(100), Duration.ofSeconds(100), 100, false, false, null);
        assertNotNull(client);
    }

    @Test
    public void test_getAsyncHttpClient_withSSLContext_skipSslVerification_success() {
        SdkAsyncHttpClient client = MLHttpClientFactory
            .getAsyncHttpClient(Duration.ofSeconds(100), Duration.ofSeconds(100), 100, false, true, null);
        assertNotNull(client);
    }

    @Test
    public void test_getAsyncHttpClient_withSSLContextAndDescription_success() {
        SdkAsyncHttpClient client = MLHttpClientFactory
            .getAsyncHttpClient(Duration.ofSeconds(100), Duration.ofSeconds(100), 100, false, false, null, "test-client");
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
                false,
                null,
                "test-client",
                keyManagers,
                trustManagers
            );
        assertNotNull(client);
    }

    @Test
    public void test_getAsyncHttpClient_withManagers_nullManagers_success() {
        SdkAsyncHttpClient client = MLHttpClientFactory
            .getAsyncHttpClient(Duration.ofSeconds(100), Duration.ofSeconds(100), 100, false, false, null, null, null, null);
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
                true,
                null,
                "test-client",
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
                false,
                null,
                "test-client",
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
                false,
                null,
                "test-client",
                null,
                trustManagers
            );
        assertNotNull(client);
    }
}
