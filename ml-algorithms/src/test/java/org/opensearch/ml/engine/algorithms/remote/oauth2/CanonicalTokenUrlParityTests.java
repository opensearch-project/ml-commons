/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.remote.oauth2;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.opensearch.ml.common.connector.auth.OAuth2AuthConfig;

import okhttp3.HttpUrl;

/**
 * {@link OAuth2AuthConfig#canonicalTokenUrl} must print a token URL as the OkHttp client that fetches the token does,
 * so that the string matched against the trusted token endpoints names the endpoint the client connects to.
 */
public class CanonicalTokenUrlParityTests {

    @Test
    public void canonicalTokenUrlMatchesOkHttp() {
        String[] urls = {
            "https://[2001:db8:0:0:0:0:0:1]/token",
            "https://[2001:DB8::1]/token",
            "https://[::1]/token",
            "https://[2001:db8:0:1:0:0:0:1]/token",
            "https://[2001:db8:1:0:1:0:1:1]/token",
            "https://[1:0:0:2:0:0:3:0]/token",
            "https://[0:0:0:0:0:0:0:0]/token",
            "https://[2001:db8::1]:8443/token",
            "https://[::ffff:192.0.2.1]/token",
            "https://[::FFFF:192.0.2.1]:8443/token",
            "https://LOGIN.Example.COM/Token",
            "HTTPS://login.example.com:443/token",
            "https://login.example.com",
            "https://login.example.com/a%20b/t%2Fc" };
        for (String url : urls) {
            assertEquals(url, HttpUrl.get(url).toString(), OAuth2AuthConfig.canonicalTokenUrl(url));
        }
    }
}
