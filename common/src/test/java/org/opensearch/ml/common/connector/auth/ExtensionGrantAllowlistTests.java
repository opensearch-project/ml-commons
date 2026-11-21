/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.connector.auth;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.opensearch.ml.common.connector.auth.AuthTestFixtures.APIKEY_GRANT;
import static org.opensearch.ml.common.connector.auth.AuthTestFixtures.DEFAULT_EXTENSION_GRANTS;

import java.util.List;

import org.junit.Test;

public class ExtensionGrantAllowlistTests {

    @Test
    public void theDefaultAdmitsTheIbmApiKeyGrantAlone() {
        assertTrue(DEFAULT_EXTENSION_GRANTS.admits(APIKEY_GRANT));
        assertFalse(DEFAULT_EXTENSION_GRANTS.admits("urn:ietf:params:oauth:grant-type:token-exchange"));
        assertFalse(DEFAULT_EXTENSION_GRANTS.admits("http://auth0.com/oauth/grant-type/mfa-otp"));
        assertFalse(DEFAULT_EXTENSION_GRANTS.admits("https://idp.example/grants/api-key"));
    }

    @Test
    public void theGrantsThePluginImplements_needNoEntry() {
        ExtensionGrantAllowlist nothing = new ExtensionGrantAllowlist(List.of());

        assertTrue(nothing.admits(OAuth2AuthConfig.GRANT_TYPE_CLIENT_CREDENTIALS));
        assertTrue(nothing.admits(OAuth2AuthConfig.GRANT_TYPE_JWT_BEARER));
        assertFalse(nothing.admits(APIKEY_GRANT));
    }

    @Test
    public void entriesAreComparedAsWritten() {
        ExtensionGrantAllowlist grants = new ExtensionGrantAllowlist(List.of("https://idp.example/grants/api-key"));

        assertTrue(grants.admits("https://idp.example/grants/api-key"));
        assertFalse(grants.admits("https://idp.example/grants/API-KEY"));
        assertFalse(grants.admits("https://idp.example/grants/api-key/"));
    }
}
