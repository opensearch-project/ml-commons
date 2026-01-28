/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.httpclient;

import static org.junit.Assert.assertNotNull;

import java.time.Duration;
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
}
