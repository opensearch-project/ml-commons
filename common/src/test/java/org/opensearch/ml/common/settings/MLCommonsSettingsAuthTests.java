/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_CONNECTOR_AUTH_DISABLED_MESSAGE;
import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_CONNECTOR_AUTH_ENABLED;
import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_TRUSTED_OAUTH2_TOKEN_ENDPOINTS_REGEX;

import java.util.List;
import java.util.regex.Pattern;

import org.junit.Test;
import org.opensearch.common.settings.Setting;
import org.opensearch.common.settings.Settings;

public class MLCommonsSettingsAuthTests {

    private static List<String> tokenEndpoints(String... patterns) {
        return ML_COMMONS_TRUSTED_OAUTH2_TOKEN_ENDPOINTS_REGEX
            .get(Settings.builder().putList(ML_COMMONS_TRUSTED_OAUTH2_TOKEN_ENDPOINTS_REGEX.getKey(), patterns).build());
    }

    /** Whether a shipped default pattern matches {@code tokenUrl}. */
    private static boolean defaultsMatch(String tokenUrl) {
        return ML_COMMONS_TRUSTED_OAUTH2_TOKEN_ENDPOINTS_REGEX
            .get(Settings.EMPTY)
            .stream()
            .anyMatch(regex -> Pattern.compile(regex).matcher(tokenUrl).matches());
    }

    private static void assertDynamicNodeScope(Setting<?> setting) {
        assertTrue(setting.getKey(), setting.isDynamic());
        assertTrue(setting.getKey(), setting.hasNodeScope());
    }

    @Test
    public void connectorAuthEnabled() {
        assertEquals("plugins.ml_commons.connector.auth_enabled", ML_COMMONS_CONNECTOR_AUTH_ENABLED.getKey());
        assertFalse(ML_COMMONS_CONNECTOR_AUTH_ENABLED.get(Settings.EMPTY));
        assertDynamicNodeScope(ML_COMMONS_CONNECTOR_AUTH_ENABLED);
        assertTrue(ML_COMMONS_CONNECTOR_AUTH_DISABLED_MESSAGE.contains("plugins.ml_commons.connector.auth_enabled"));
        assertTrue(ML_COMMONS_CONNECTOR_AUTH_DISABLED_MESSAGE.endsWith(ML_COMMONS_CONNECTOR_AUTH_ENABLED.getKey()));
    }

    @Test
    public void tokenEndpoints_keyAndScope() {
        assertEquals("plugins.ml_commons.trusted_oauth2_token_endpoints_regex", ML_COMMONS_TRUSTED_OAUTH2_TOKEN_ENDPOINTS_REGEX.getKey());
        assertDynamicNodeScope(ML_COMMONS_TRUSTED_OAUTH2_TOKEN_ENDPOINTS_REGEX);
    }

    @Test
    public void tokenEndpoints_defaultsPassTheValidator() {
        // Setting.get validates the default, so a default the validator rejects would stop node startup.
        List<String> defaults = ML_COMMONS_TRUSTED_OAUTH2_TOKEN_ENDPOINTS_REGEX.get(Settings.EMPTY);
        assertEquals(
            List
                .of(
                    "^https://login\\.microsoftonline\\.com/[A-Za-z0-9][A-Za-z0-9.-]*/oauth2/v2\\.0/token$",
                    "^https://oauth2\\.googleapis\\.com/token$",
                    "^https://iam\\.cloud\\.ibm\\.com/identity/token$",
                    "^https://[a-z0-9-]+\\.okta\\.com/oauth2/[A-Za-z0-9]+/v1/token$",
                    "^https://login\\.salesforce\\.com/services/oauth2/token$",
                    "^https://[a-z0-9-]+\\.my\\.salesforce\\.com/services/oauth2/token$",
                    "^https://accounts\\.cloud\\.databricks\\.com/oidc/accounts/[a-z0-9-]+/v1/token$"
                ),
            defaults
        );
        assertEquals(defaults, tokenEndpoints(defaults.toArray(new String[0])));
    }

    @Test
    public void tokenEndpoints_defaultsHaveNoAuthorityWildcard() {
        for (String regex : ML_COMMONS_TRUSTED_OAUTH2_TOKEN_ENDPOINTS_REGEX.get(Settings.EMPTY)) {
            assertFalse(regex, MLCommonsSettings.authorityContainsWildcard(MLCommonsSettings.authorityPart(regex)));
            assertTrue(regex, regex.startsWith("^https://"));
            assertTrue(regex, regex.endsWith("$"));
        }
    }

    @Test
    public void tokenEndpoints_rejectsHostPositionWildcards() {
        for (String loose : new String[] {
            "^https://[^/]+\\.okta\\.com/.*$",
            "^.*$",
            "^https://.+\\.okta\\.com/x$",
            "^https://\\S+\\.okta\\.com/x$" }) {
            IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> tokenEndpoints(loose));
            assertTrue(e.getMessage(), e.getMessage().contains("wildcard in the host position"));
            assertTrue(e.getMessage(), e.getMessage().contains(loose));
        }
        // One loose entry rejects the whole list.
        assertThrows(IllegalArgumentException.class, () -> tokenEndpoints("^https://oauth2\\.googleapis\\.com/token$", "^.*$"));
    }

    @Test
    public void tokenEndpoints_acceptsExplicitOperatorPatterns() {
        List<String> custom = List
            .of(
                "^https://keycloak\\.corp\\.example/realms/[a-z0-9-]+/protocol/openid-connect/token$",
                "^https://[a-z0-9-]+\\.authentication\\.[a-z0-9-]+\\.hana\\.ondemand\\.com/oauth/token$",
                "^https://idp\\.corp\\.example:8443/as/token\\.oauth2$"
            );
        assertEquals(custom, tokenEndpoints(custom.toArray(new String[0])));
        assertEquals(List.of(), tokenEndpoints());
    }

    @Test
    public void tokenEndpoints_defaultsMatchPublicIdentityProviders() {
        assertTrue(defaultsMatch("https://login.microsoftonline.com/72f988bf-86f1-41af-91ab-2d7cd011db47/oauth2/v2.0/token"));
        // Entra accepts the tenant as a GUID in either case or as a domain name.
        assertTrue(defaultsMatch("https://login.microsoftonline.com/72F988BF-86F1-41AF-91AB-2D7CD011DB47/oauth2/v2.0/token"));
        assertTrue(defaultsMatch("https://login.microsoftonline.com/contoso.onmicrosoft.com/oauth2/v2.0/token"));
        assertTrue(defaultsMatch("https://login.microsoftonline.com/contoso.com/oauth2/v2.0/token"));
        assertTrue(defaultsMatch("https://oauth2.googleapis.com/token"));
        assertTrue(defaultsMatch("https://acme.okta.com/oauth2/default/v1/token"));
        // Custom authorization server ids are mixed-case.
        assertTrue(defaultsMatch("https://acme.okta.com/oauth2/aus9o8wzkhckw9TLa0h7/v1/token"));
        assertTrue(defaultsMatch("https://iam.cloud.ibm.com/identity/token"));
        assertTrue(defaultsMatch("https://login.salesforce.com/services/oauth2/token"));
        assertTrue(defaultsMatch("https://acme.my.salesforce.com/services/oauth2/token"));
        assertTrue(defaultsMatch("https://accounts.cloud.databricks.com/oidc/accounts/0d26daa6-5e44-4c97-a497-ef015f91254a/v1/token"));
    }

    @Test
    public void tokenEndpoints_defaultsDoNotMatchLookalikes() {
        assertFalse(defaultsMatch("https://attacker.example/x.okta.com/oauth2/x/v1/token"));
        assertFalse(defaultsMatch("https://oauth2.googleapis.com.evil.example/token"));
        assertFalse(defaultsMatch("https://oauth2.googleapis.com:8443/token"));
        assertFalse(defaultsMatch("https://evil.example/oauth2.googleapis.com/token"));
        assertFalse(defaultsMatch("https://login.microsoftonline.com/tenant/oauth2/v2.0/token/extra"));
        assertFalse(defaultsMatch("https://login.microsoftonline.com/a/b/oauth2/v2.0/token"));
        assertFalse(defaultsMatch("https://login.microsoftonline.com/.contoso.com/oauth2/v2.0/token"));
        assertFalse(defaultsMatch("https://login.microsoftonline.com/tenant/oauth2/token"));
        assertFalse(defaultsMatch("https://acme.okta.com.evil.example/oauth2/default/v1/token"));
        assertFalse(defaultsMatch("https://a.b.my.salesforce.com/services/oauth2/token"));
        assertFalse(defaultsMatch("https://oauth2.googleapis.com/token/"));
    }
}
