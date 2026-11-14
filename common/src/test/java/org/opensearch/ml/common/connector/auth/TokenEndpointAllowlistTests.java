/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.connector.auth;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Test;

public class TokenEndpointAllowlistTests {

    private static final TokenEndpointAllowlist ALLOWLIST = new TokenEndpointAllowlist(
        List.of("^https://idp\\.example\\.com/token$", "https://[a-z0-9-]+\\.corp\\.example/oauth2/token")
    );

    @Test
    public void matches_onlyAFullMatch() {
        assertTrue(ALLOWLIST.matches("https://idp.example.com/token"));
        assertTrue(ALLOWLIST.matches("https://eu-1.corp.example/oauth2/token"));

        // Without anchors a pattern still has to match the whole URL.
        assertFalse(ALLOWLIST.matches("https://eu-1.corp.example/oauth2/token/extra"));
        assertFalse(ALLOWLIST.matches("https://evil.example/https://eu-1.corp.example/oauth2/token"));
        assertFalse(ALLOWLIST.matches("https://idp.example.com/token2"));
        assertFalse(ALLOWLIST.matches("https://idp.example.com"));
    }

    @Test
    public void matches_nothingWhenEmpty() {
        assertFalse(new TokenEndpointAllowlist(List.of()).matches("https://idp.example.com/token"));
    }

    @Test
    public void notAllowedMessage_namesTheSetting() {
        assertEquals(
            "auth_config.token_url does not match plugins.ml_commons.trusted_oauth2_token_endpoints_regex",
            TokenEndpointAllowlist.NOT_ALLOWED_MESSAGE
        );
    }
}
