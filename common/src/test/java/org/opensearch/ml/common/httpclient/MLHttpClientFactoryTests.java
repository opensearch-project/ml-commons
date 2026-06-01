/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.httpclient;

import static org.junit.Assert.assertNotNull;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

import org.junit.Test;

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
    public void test_getAsyncHttpClient_withSecurityFeatures_success() {
        List<Pattern> trustedEndpoints = Arrays.asList(Pattern.compile("http://trusted\\..*"));
        List<Pattern> restrictedPatterns = Arrays.asList(Pattern.compile("192\\.168\\..*"));

        SdkAsyncHttpClient client = MLHttpClientFactory
            .getAsyncHttpClient(Duration.ofSeconds(100), Duration.ofSeconds(100), 100, true, trustedEndpoints, restrictedPatterns, true);
        assertNotNull(client);
    }
}
