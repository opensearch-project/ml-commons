/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.connector.auth;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.opensearch.ml.common.connector.auth.AuthTestFixtures.SECRET_LOOKING;
import static org.opensearch.ml.common.connector.auth.AuthTestFixtures.fullOauth2;
import static org.opensearch.ml.common.connector.auth.AuthTestFixtures.http;
import static org.opensearch.ml.common.connector.auth.AuthTestFixtures.oauth2;
import static org.opensearch.ml.common.connector.auth.AuthTestFixtures.oauth2Builder;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.junit.Test;
import org.opensearch.ml.common.connector.Connector;
import org.opensearch.ml.common.connector.ConnectorProtocols;
import org.opensearch.ml.common.connector.HttpConnector;
import org.opensearch.ml.common.connector.McpConnector;

/** Write-time validation of the {@code auth_config} a connector holds ({@link AuthConfigValidator}). */
public class AuthConfigValidatorTests {

    /** References the keys {@code a}, {@code s} and {@code u}. */
    private static final OAuth2AuthConfig THREE_KEYS = oauth2Builder().tokenUrl("${credential.u}").audience("${credential.a}").build();

    private static String fails(Connector connector, Set<String> keys) {
        String message = assertThrows(IllegalArgumentException.class, () -> AuthConfigValidator.validate(connector, keys)).getMessage();
        assertFalse(message, message.contains(SECRET_LOOKING));
        return message;
    }

    @Test
    public void referencedCredentialKeys_mustBeInTheGivenKeyView() {
        AuthConfigValidator.validate(http(oauth2()), Set.of("s"));
        AuthConfigValidator.validate(http(fullOauth2()), Set.of("s", "unused"));
        assertEquals(AuthConfigValidator.missingCredentialKeyMessage("s"), fails(http(oauth2()), Set.of("other")));
        // The connector's own credential holds s; the given view does not, and it is the view that counts.
        AuthConfigValidator
            .validate(HttpConnector.builder().name("c").protocol(ConnectorProtocols.HTTP).authConfig(oauth2()).build(), Set.of("s"));
    }

    @Test
    public void referencedCredentialKeys_theFirstMissingKeyInSortedOrderIsReported() {
        assertEquals(AuthConfigValidator.missingCredentialKeyMessage("a"), fails(http(THREE_KEYS), Set.of()));
        assertEquals(AuthConfigValidator.missingCredentialKeyMessage("a"), fails(http(THREE_KEYS), Set.of("s", "u")));
        assertEquals(AuthConfigValidator.missingCredentialKeyMessage("u"), fails(http(THREE_KEYS), Set.of("a", "s")));
        AuthConfigValidator.validate(http(THREE_KEYS), Set.of("a", "s", "u"));
    }

    @Test
    public void nothingToValidate_passesWithAnyKeySet() {
        // No configuration, a protocol that never holds one, or a configuration that references no key.
        AuthConfigValidator.validate(http(null), Set.of());
        AuthConfigValidator
            .validate(
                McpConnector.builder().name("m").protocol(ConnectorProtocols.MCP_SSE).url("https://api.openai.com/mcp").build(),
                Set.of()
            );
        AuthConfigValidator.validate(http(OAuth2AuthConfig.NONE), Set.of());
        AuthConfigValidator.validate(http(oauth2Builder().clientSecret(null).clientAuthMethod("none").build()), Set.of());
    }

    @Test
    public void validateOrGrandfather_passesWithoutAWarning_whenTheConfigurationIsValid() {
        HttpConnector valid = http(oauth2());
        assertFalse(AuthConfigValidator.validateOrGrandfather(valid, Set.of("s"), false, false));
        assertFalse(AuthConfigValidator.validateOrGrandfather(valid, Set.of("s"), true, true));
    }

    @Test
    public void validateOrGrandfather_anUntouchedUpdateOverAStoredFailure_proceedsWithAWarning() {
        assertTrue(AuthConfigValidator.validateOrGrandfather(http(THREE_KEYS), Set.of("s"), false, false));
    }

    @Test
    public void validateOrGrandfather_rejects_whenAuthOrTheProtocolIsTouchedOrTheStoredConfigurationPassed() {
        HttpConnector failing = http(THREE_KEYS);
        for (boolean[] touchedAndStoredPassed : new boolean[][] { { true, false }, { false, true }, { true, true } }) {
            boolean touched = touchedAndStoredPassed[0];
            boolean storedPassed = touchedAndStoredPassed[1];
            IllegalArgumentException e = assertThrows(
                IllegalArgumentException.class,
                () -> AuthConfigValidator.validateOrGrandfather(failing, Set.of("s"), touched, storedPassed)
            );
            assertEquals(AuthConfigValidator.missingCredentialKeyMessage("a"), e.getMessage());
        }
    }

    @Test
    public void passes_isTrueExactlyWhenValidateDoesNotThrow() {
        assertTrue(AuthConfigValidator.passes(http(null), Set.of()));
        assertTrue(AuthConfigValidator.passes(http(oauth2()), Set.of("s")));
        assertFalse(AuthConfigValidator.passes(http(oauth2()), Set.of()));
    }

    @Test
    public void keysOf_isANullSafeCopy() {
        assertEquals(Set.of(), AuthConfigValidator.keysOf(null));
        Map<String, String> credential = new HashMap<>();
        credential.put("a", SECRET_LOOKING);
        Set<String> keys = AuthConfigValidator.keysOf(credential);
        assertEquals(Set.of("a"), keys);
        credential.put("b", "y");
        assertEquals("a copy, not a view", Set.of("a"), keys);
        assertThrows(UnsupportedOperationException.class, () -> keys.add("c"));
        assertEquals(Collections.emptySet(), AuthConfigValidator.keysOf(Map.of()));
    }
}
