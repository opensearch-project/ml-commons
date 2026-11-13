/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.connector.auth;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.opensearch.ml.common.connector.auth.AuthTestFixtures.ACTION_URL;
import static org.opensearch.ml.common.connector.auth.AuthTestFixtures.DEFAULT_TOKEN_ALLOWLIST;
import static org.opensearch.ml.common.connector.auth.AuthTestFixtures.SECRET_LOOKING;
import static org.opensearch.ml.common.connector.auth.AuthTestFixtures.TOKEN_URL;
import static org.opensearch.ml.common.connector.auth.AuthTestFixtures.fullOauth2;
import static org.opensearch.ml.common.connector.auth.AuthTestFixtures.http;
import static org.opensearch.ml.common.connector.auth.AuthTestFixtures.oauth2;
import static org.opensearch.ml.common.connector.auth.AuthTestFixtures.oauth2Builder;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.Test;
import org.opensearch.ml.common.connector.Connector;
import org.opensearch.ml.common.connector.ConnectorAction;
import org.opensearch.ml.common.connector.ConnectorClientConfig;
import org.opensearch.ml.common.connector.ConnectorProtocols;
import org.opensearch.ml.common.connector.HttpConnector;
import org.opensearch.ml.common.connector.McpConnector;

/** Write-time validation of the {@code auth_config} a connector holds ({@link AuthConfigValidator}). */
public class AuthConfigValidatorTests {

    /** References the keys {@code a}, {@code s} and {@code u}. */
    private static final OAuth2AuthConfig THREE_KEYS = oauth2Builder().tokenUrl("${credential.u}").audience("${credential.a}").build();
    private static final TokenEndpointAllowlist NOTHING_ALLOWED = new TokenEndpointAllowlist(List.of());
    private static final TokenEndpointAllowlist EVERYTHING_ALLOWED = new TokenEndpointAllowlist(List.of(".*"));

    private static OAuth2AuthConfig validate(Connector connector) {
        return AuthConfigValidator.validate(connector, Set.of("s"), DEFAULT_TOKEN_ALLOWLIST);
    }

    private static String fails(Connector connector, Set<String> keys, TokenEndpointAllowlist allowlist) {
        IllegalArgumentException e = assertThrows(
            IllegalArgumentException.class,
            () -> AuthConfigValidator.validate(connector, keys, allowlist)
        );
        assertFalse(e.getMessage(), e.getMessage().contains(SECRET_LOOKING));
        assertNull(e.getCause());
        return e.getMessage();
    }

    private static String fails(Connector connector, Set<String> keys) {
        return fails(connector, keys, DEFAULT_TOKEN_ALLOWLIST);
    }

    private static String fails(Connector connector) {
        return fails(connector, Set.of("s"));
    }

    /** {@link AuthTestFixtures#oauth2()} with its token URL replaced. */
    private static OAuth2AuthConfig tokenUrl(String tokenUrl) {
        return oauth2Builder().tokenUrl(tokenUrl).build();
    }

    private static Map<String, String> parameters() {
        return new LinkedHashMap<>(Map.of("model", "x"));
    }

    /** One predict action per URL, each with {@code headers}. */
    private static List<ConnectorAction> actions(Map<String, String> headers, String... urls) {
        List<ConnectorAction> actions = new ArrayList<>();
        for (String url : urls) {
            actions
                .add(
                    ConnectorAction
                        .builder()
                        .actionType(ConnectorAction.ActionType.PREDICT)
                        .method("POST")
                        .url(url)
                        .headers(headers)
                        .requestBody("{}")
                        .build()
                );
        }
        return actions;
    }

    @Test
    public void referencedCredentialKeys_mustBeInTheGivenKeyView() {
        assertEquals(oauth2(), validate(http(oauth2())));
        AuthConfigValidator.validate(http(fullOauth2()), Set.of("s", "unused"), DEFAULT_TOKEN_ALLOWLIST);
        assertEquals(AuthConfigValidator.missingCredentialKeyMessage("s"), fails(http(oauth2()), Set.of("other")));
        // The connector's own credential holds s; the given view does not, and it is the view that counts.
        validate(HttpConnector.builder().name("c").protocol(ConnectorProtocols.HTTP).authConfig(oauth2()).build());
    }

    @Test
    public void referencedCredentialKeys_theFirstMissingKeyInSortedOrderIsReported() {
        assertEquals(AuthConfigValidator.missingCredentialKeyMessage("a"), fails(http(THREE_KEYS), Set.of()));
        assertEquals(AuthConfigValidator.missingCredentialKeyMessage("a"), fails(http(THREE_KEYS), Set.of("s", "u")));
        assertEquals(AuthConfigValidator.missingCredentialKeyMessage("u"), fails(http(THREE_KEYS), Set.of("a", "s")));
        AuthConfigValidator.validate(http(THREE_KEYS), Set.of("a", "s", "u"), DEFAULT_TOKEN_ALLOWLIST);
    }

    @Test
    public void nothingToValidate_passesWithAnyKeySet() {
        // No configuration, a protocol that never holds one, or a configuration that references no key.
        assertNull(AuthConfigValidator.validate(http(null), Set.of(), NOTHING_ALLOWED));
        assertNull(
            AuthConfigValidator
                .validate(
                    McpConnector.builder().name("m").protocol(ConnectorProtocols.MCP_SSE).url("https://api.openai.com/mcp").build(),
                    Set.of(),
                    NOTHING_ALLOWED
                )
        );
        assertEquals(OAuth2AuthConfig.NONE, AuthConfigValidator.validate(http(OAuth2AuthConfig.NONE), Set.of(), NOTHING_ALLOWED));
    }

    @Test
    public void grantAndClientAuthMethod_onlyClientCredentialsWithASecret() {
        validate(http(oauth2Builder().grantType(OAuth2AuthConfig.GRANT_TYPE_CLIENT_CREDENTIALS).build()));
        for (String grant : new String[] {
            "urn:ietf:params:oauth:grant-type:jwt-bearer",
            "urn:ibm:params:oauth:grant-type:apikey",
            "Client_Credentials" }) {
            assertEquals(
                grant,
                AuthConfigValidator.GRANT_TYPE_NOT_SUPPORTED_MESSAGE,
                fails(http(oauth2Builder().grantType(grant).build()))
            );
        }
        assertEquals(
            AuthConfigValidator.CLIENT_AUTH_METHOD_NONE_NOT_SUPPORTED_MESSAGE,
            fails(http(oauth2Builder().clientAuthMethod(OAuth2AuthConfig.CLIENT_AUTH_METHOD_NONE).clientSecret(null).build()))
        );
    }

    @Test
    public void clientIdAndSecret_areRequiredByBothMethods() {
        for (String method : new String[] {
            OAuth2AuthConfig.CLIENT_AUTH_METHOD_CLIENT_SECRET_BASIC,
            OAuth2AuthConfig.CLIENT_AUTH_METHOD_CLIENT_SECRET_POST }) {
            OAuth2AuthConfig.OAuth2AuthConfigBuilder builder = oauth2Builder().clientAuthMethod(method);
            validate(http(builder.build()));
            assertEquals(AuthConfigValidator.clientIdRequiredMessage(method), fails(http(builder.clientId(null).build())));
            assertEquals(
                AuthConfigValidator.clientSecretRequiredMessage(method),
                fails(http(builder.clientId("app").clientSecret(null).build()))
            );
        }
    }

    @Test
    public void literalTokenUrl_mustMatchTheAllowlist() {
        validate(http(oauth2()));
        assertEquals(TokenEndpointAllowlist.NOT_ALLOWED_MESSAGE, fails(http(tokenUrl("https://login.example.com/t"))));
        validate(http(tokenUrl(TOKEN_URL.replace("acme.okta.com", "acme.okta.com:443"))));
        assertEquals(
            TokenEndpointAllowlist.NOT_ALLOWED_MESSAGE,
            fails(http(tokenUrl(TOKEN_URL.replace("acme.okta.com", "acme.okta.com:8443"))))
        );
        String malformed = TOKEN_URL.replace("default", "def ault");
        String canonicalFormMessage = assertThrows(IllegalArgumentException.class, () -> OAuth2AuthConfig.canonicalTokenUrl(malformed))
            .getMessage();
        // A malformed literal never builds, so no stored configuration carries one.
        assertEquals(canonicalFormMessage, assertThrows(IllegalArgumentException.class, () -> tokenUrl(malformed)).getMessage());
        // A reference is checked once its value is known.
        AuthConfigValidator.validate(http(tokenUrl("${credential.u}")), Set.of("s", "u"), NOTHING_ALLOWED);
        assertEquals(TokenEndpointAllowlist.NOT_ALLOWED_MESSAGE, fails(http(oauth2()), Set.of("s"), NOTHING_ALLOWED));
    }

    @Test
    public void literalTokenUrl_mustNotHaveASingleDotSegment() {
        assertEquals(
            AuthConfigValidator.TOKEN_URL_DOT_SEGMENT_MESSAGE,
            fails(http(tokenUrl(TOKEN_URL.replace("/default/", "/default/./"))))
        );
    }

    @Test
    public void literalTokenUrl_mustNotHaveADoubleDotSegment() {
        assertEquals(
            AuthConfigValidator.TOKEN_URL_DOT_SEGMENT_MESSAGE,
            fails(http(tokenUrl(TOKEN_URL.replace("/default/", "/default/../"))))
        );
        assertEquals(
            AuthConfigValidator.TOKEN_URL_DOT_SEGMENT_MESSAGE,
            fails(http(tokenUrl(TOKEN_URL.replace("/default/", "/default/%2E%2E/"))))
        );
    }

    @Test
    public void literalTokenUrl_mayHaveADotInsideASegment() {
        AuthConfigValidator.validate(http(tokenUrl("https://acme.okta.com/a/b.c/")), Set.of("s"), EVERYTHING_ALLOWED);
    }

    @Test
    public void literalTokenUrl_pathMustBeAscii() {
        assertEquals(
            AuthConfigValidator.TOKEN_URL_PATH_MUST_BE_ASCII_MESSAGE,
            fails(http(tokenUrl("https://acme.okta.com/café/token")), Set.of("s"), EVERYTHING_ALLOWED)
        );
    }

    @Test
    public void literalTokenUrl_mayHaveAPercentEncodedCharacterInItsPath() {
        AuthConfigValidator.validate(http(tokenUrl("https://acme.okta.com/caf%C3%A9/token")), Set.of("s"), EVERYTHING_ALLOWED);
    }

    @Test
    public void authorizationHeader_isRefusedInAnyCase() {
        for (String name : new String[] { "authorization", "AUTHORIZATION" }) {
            HttpConnector connector = http(parameters(), oauth2(), actions(Map.of(name, "Bearer ${credential.s}"), ACTION_URL), null);
            assertEquals(name, AuthConfigValidator.AUTHORIZATION_HEADER_MESSAGE, fails(connector));
        }
        validate(http(parameters(), oauth2(), actions(Map.of("Content-Type", "application/json"), ACTION_URL), null));
        validate(http(parameters(), oauth2(), null, null));
        validate(http(parameters(), oauth2(), actions(null, ACTION_URL), null));
    }

    @Test
    public void actionUrls_mustUseHttpsOnceTheConnectorParametersAreSubstituted() {
        for (String url : new String[] { "http://api.openai.com/v1", " HTTP://api.openai.com/v1", "${parameters.endpoint}/v1" }) {
            Map<String, String> parameters = parameters();
            parameters.put("endpoint", "http://api.openai.com");
            assertEquals(
                url,
                AuthConfigValidator.CLEARTEXT_ACTION_URL_MESSAGE,
                fails(http(parameters, oauth2(), actions(null, ACTION_URL, url), null))
            );
        }
        // Known only once a request resolves it.
        validate(http(parameters(), oauth2(), actions(null, "${parameters.scheme}://api.openai.com/v1"), null));

        // An update can bring in parameters that refer to each other; building a connector already refuses them.
        HttpConnector cycle = http(parameters(), oauth2(), actions(null, ACTION_URL + "/${parameters.a}"), null);
        cycle.getParameters().put("a", "${parameters.b}");
        cycle.getParameters().put("b", "${parameters.a}");
        assertEquals(Connector.URL_PARAMETER_CYCLE_MESSAGE, fails(cycle));

        IllegalArgumentException e = assertThrows(
            IllegalArgumentException.class,
            () -> AuthConfigValidator.requireHttps(URI.create("http://api.openai.com/v1"))
        );
        assertEquals(AuthConfigValidator.CLEARTEXT_ACTION_URL_MESSAGE, e.getMessage());
        AuthConfigValidator.requireHttps(URI.create(ACTION_URL));
    }

    @Test
    public void skipSslVerification_isRefusedWhenSet() {
        ConnectorClientConfig skip = ConnectorClientConfig.builder().skipSslVerification(true).build();
        assertEquals(
            AuthConfigValidator.SKIP_SSL_VERIFICATION_MESSAGE,
            fails(http(parameters(), oauth2(), actions(null, ACTION_URL), skip))
        );
        validate(
            http(parameters(), oauth2(), actions(null, ACTION_URL), ConnectorClientConfig.builder().skipSslVerification(false).build())
        );
        validate(http(parameters(), oauth2(), actions(null, ACTION_URL), ConnectorClientConfig.builder().build()));
    }

    @Test
    public void noneConfiguration_attachesNothing_soActionsAndClientConfigAreNotExamined() {
        ConnectorClientConfig skip = ConnectorClientConfig.builder().skipSslVerification(true).build();
        List<ConnectorAction> actions = actions(Map.of("Authorization", "Bearer ${credential.s}"), "http://plain.example.com/x");
        AuthConfigValidator.validate(http(parameters(), OAuth2AuthConfig.NONE, actions, skip), Set.of(), NOTHING_ALLOWED);
    }

    @Test
    public void passesAndValidateOrGrandfather_useTheGivenAllowlist() {
        HttpConnector connector = http(oauth2());
        assertTrue(AuthConfigValidator.passes(connector, Set.of("s"), DEFAULT_TOKEN_ALLOWLIST));
        assertFalse(AuthConfigValidator.passes(connector, Set.of("s"), NOTHING_ALLOWED));
        assertTrue(AuthConfigValidator.validateOrGrandfather(connector, Set.of("s"), NOTHING_ALLOWED, false, false));
        IllegalArgumentException e = assertThrows(
            IllegalArgumentException.class,
            () -> AuthConfigValidator.validateOrGrandfather(connector, Set.of("s"), NOTHING_ALLOWED, true, false)
        );
        assertEquals(TokenEndpointAllowlist.NOT_ALLOWED_MESSAGE, e.getMessage());
    }

    @Test
    public void validateOrGrandfather_passesWithoutAWarning_whenTheConfigurationIsValid() {
        HttpConnector valid = http(oauth2());
        assertFalse(AuthConfigValidator.validateOrGrandfather(valid, Set.of("s"), DEFAULT_TOKEN_ALLOWLIST, false, false));
        assertFalse(AuthConfigValidator.validateOrGrandfather(valid, Set.of("s"), DEFAULT_TOKEN_ALLOWLIST, true, true));
    }

    @Test
    public void validateOrGrandfather_anUntouchedUpdateOverAStoredFailure_proceedsWithAWarning() {
        assertTrue(AuthConfigValidator.validateOrGrandfather(http(THREE_KEYS), Set.of("s"), DEFAULT_TOKEN_ALLOWLIST, false, false));
    }

    @Test
    public void validateOrGrandfather_rejects_whenAuthOrTheProtocolIsTouchedOrTheStoredConfigurationPassed() {
        HttpConnector failing = http(THREE_KEYS);
        for (boolean[] touchedAndStoredPassed : new boolean[][] { { true, false }, { false, true }, { true, true } }) {
            boolean touched = touchedAndStoredPassed[0];
            boolean storedPassed = touchedAndStoredPassed[1];
            IllegalArgumentException e = assertThrows(
                IllegalArgumentException.class,
                () -> AuthConfigValidator.validateOrGrandfather(failing, Set.of("s"), DEFAULT_TOKEN_ALLOWLIST, touched, storedPassed)
            );
            assertEquals(AuthConfigValidator.missingCredentialKeyMessage("a"), e.getMessage());
        }
    }

    @Test
    public void passes_isTrueExactlyWhenValidateDoesNotThrow() {
        assertTrue(AuthConfigValidator.passes(http(null), Set.of(), DEFAULT_TOKEN_ALLOWLIST));
        assertTrue(AuthConfigValidator.passes(http(oauth2()), Set.of("s"), DEFAULT_TOKEN_ALLOWLIST));
        assertFalse(AuthConfigValidator.passes(http(oauth2()), Set.of(), DEFAULT_TOKEN_ALLOWLIST));
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
