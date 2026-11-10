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
import javax.net.ssl.X509TrustManager;

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
        TrustManager[] trustManagers = new TrustManager[] { mock(X509TrustManager.class) };

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
                    trustManagers // and a populated truststore: the rejection must not depend on it being absent
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

    @org.junit.Test
    public void validateConnectorEndpoint_appliesPrivateIpPolicy() {
        IllegalArgumentException e = org.junit.Assert
            .assertThrows(
                IllegalArgumentException.class,
                () -> MLHttpClientFactory.validateConnectorEndpoint("http://10.0.0.1/v1", "http", "10.0.0.1", 80, false, null, null)
            );
        org.junit.Assert.assertEquals("Remote inference host name has private ip address: 10.0.0.1", e.getMessage());
        MLHttpClientFactory.validateConnectorEndpoint("http://10.0.0.1/v1", "http", "10.0.0.1", 80, true, null, null);
        MLHttpClientFactory.validateConnectorEndpoint("https://203.0.113.1/v1", "https", "203.0.113.1", 443, false, null, null);
    }

    @org.junit.Test
    public void validateConnectorEndpoint_appliesRestrictedAndTrustedPrivatePatterns() {
        java.util.List<java.util.regex.Pattern> restricted = java.util.List.of(java.util.regex.Pattern.compile("10\\.0\\.0\\.9"));
        org.junit.Assert
            .assertThrows(
                IllegalArgumentException.class,
                () -> MLHttpClientFactory.validateConnectorEndpoint("http://10.0.0.9/v1", "http", "10.0.0.9", 80, true, null, restricted)
            );
        java.util.List<java.util.regex.Pattern> trusted = java.util.List.of(java.util.regex.Pattern.compile("^http://10\\.0\\.0\\.1/.*$"));
        MLHttpClientFactory.validateConnectorEndpoint("http://10.0.0.1/v1", "http", "10.0.0.1", 80, true, trusted, null);
        org.junit.Assert
            .assertThrows(
                IllegalArgumentException.class,
                () -> MLHttpClientFactory.validateConnectorEndpoint("http://10.0.0.2/v1", "http", "10.0.0.2", 80, true, trusted, null)
            );
    }

    @org.junit.Test
    public void validateConnectorEndpoint_rejectsNonHttpScheme() {
        org.junit.Assert
            .assertThrows(
                IllegalArgumentException.class,
                () -> MLHttpClientFactory.validateConnectorEndpoint("ftp://203.0.113.1/x", "ftp", "203.0.113.1", 21, false, null, null)
            );
    }
}
