/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.remote;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.net.URI;

import org.junit.Test;

import okhttp3.HttpUrl;

public class AllowlistParserParityTest {

    @Test
    public void acceptedUrlsHaveTheSameHostInUriAndOkHttp() {
        String[] urls = new String[] {
            "https://bedrock-runtime.us-east-1.amazonaws.com/model/m/invoke",
            "https://textract.us-east-1.amazonaws.com",
            "https://example.openai.azure.com/openai/deployments/d/chat/completions?api-version=2024-10-21",
            "https://[2001:db8::1]/v1" };
        for (String url : urls) {
            URI uri = URI.create(url);
            HttpUrl httpUrl = HttpUrl.parse(url);
            assertNotNull(httpUrl);
            String uriHost = uri.getHost();
            if (uriHost.startsWith("[") && uriHost.endsWith("]")) {
                uriHost = uriHost.substring(1, uriHost.length() - 1);
            }
            assertEquals(uriHost, httpUrl.host());
        }
    }
}
