/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.connector.auth;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.opensearch.ml.common.connector.auth.AuthTestFixtures.ACTION_URL;
import static org.opensearch.ml.common.connector.auth.AuthTestFixtures.DEFAULT_TOKEN_ALLOWLIST;
import static org.opensearch.ml.common.connector.auth.AuthTestFixtures.GOOGLE_TOKEN_URL;
import static org.opensearch.ml.common.connector.auth.AuthTestFixtures.PRIVATE_KEY_KEY;
import static org.opensearch.ml.common.connector.auth.AuthTestFixtures.SECRET_LOOKING;
import static org.opensearch.ml.common.connector.auth.AuthTestFixtures.TOKEN_URL;
import static org.opensearch.ml.common.connector.auth.AuthTestFixtures.fullJwtBearer;
import static org.opensearch.ml.common.connector.auth.AuthTestFixtures.fullOauth2;
import static org.opensearch.ml.common.connector.auth.AuthTestFixtures.http;
import static org.opensearch.ml.common.connector.auth.AuthTestFixtures.jwtBearer;
import static org.opensearch.ml.common.connector.auth.AuthTestFixtures.jwtBearerBuilder;
import static org.opensearch.ml.common.connector.auth.AuthTestFixtures.oauth2;
import static org.opensearch.ml.common.connector.auth.AuthTestFixtures.oauth2Builder;
import static org.opensearch.ml.common.connector.auth.AuthTestFixtures.pem;
import static org.opensearch.ml.common.connector.auth.AuthTestFixtures.rsaPkcs8;
import static org.opensearch.ml.common.connector.auth.AuthTestFixtures.rsaPkcs8Pem;

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
import org.opensearch.ml.common.connector.GoogleCloudConnector;
import org.opensearch.ml.common.connector.HttpConnector;
import org.opensearch.ml.common.connector.McpConnector;

/** Write-time validation of the {@code auth_config} a connector holds ({@link AuthConfigValidator}). */
public class AuthConfigValidatorTests {

    /** References the keys {@code a}, {@code s} and {@code u}. */
    private static final OAuth2AuthConfig THREE_KEYS = oauth2Builder().tokenUrl("${credential.u}").audience("${credential.a}").build();
    private static final TokenEndpointAllowlist NOTHING_ALLOWED = new TokenEndpointAllowlist(List.of());
    private static final TokenEndpointAllowlist EVERYTHING_ALLOWED = new TokenEndpointAllowlist(List.of(".*"));

    /** {@link AuthConfigValidator#validate} of the configuration {@code connector} holds. */
    private static OAuth2AuthConfig validate(Connector connector, Set<String> keys, TokenEndpointAllowlist allowlist) {
        return AuthConfigValidator.validate(connector, connector.getAuthConfig(), keys, allowlist);
    }

    private static OAuth2AuthConfig validate(Connector connector) {
        return validate(connector, Set.of("s"), DEFAULT_TOKEN_ALLOWLIST);
    }

    private static boolean passes(Connector connector, Set<String> keys, TokenEndpointAllowlist allowlist) {
        return AuthConfigValidator.passes(connector, connector.getAuthConfig(), keys, allowlist);
    }

    private static boolean validateOrGrandfather(
        Connector connector,
        Set<String> keys,
        TokenEndpointAllowlist allowlist,
        boolean authOrProtocolTouched,
        boolean storedPassed
    ) {
        return AuthConfigValidator
            .validateOrGrandfather(connector, connector.getAuthConfig(), keys, allowlist, authOrProtocolTouched, storedPassed);
    }

    private static String fails(Connector connector, Set<String> keys, TokenEndpointAllowlist allowlist) {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> validate(connector, keys, allowlist));
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
        validate(http(fullOauth2()), Set.of("s", "unused"), DEFAULT_TOKEN_ALLOWLIST);
        assertEquals(AuthConfigValidator.missingCredentialKeyMessage("s"), fails(http(oauth2()), Set.of("other")));
        // The connector's own credential holds s; the given view does not, and it is the view that counts.
        validate(HttpConnector.builder().name("c").protocol(ConnectorProtocols.HTTP).authConfig(oauth2()).build());
    }

    @Test
    public void referencedCredentialKeys_theFirstMissingKeyInSortedOrderIsReported() {
        assertEquals(AuthConfigValidator.missingCredentialKeyMessage("a"), fails(http(THREE_KEYS), Set.of()));
        assertEquals(AuthConfigValidator.missingCredentialKeyMessage("a"), fails(http(THREE_KEYS), Set.of("s", "u")));
        assertEquals(AuthConfigValidator.missingCredentialKeyMessage("u"), fails(http(THREE_KEYS), Set.of("a", "s")));
        validate(http(THREE_KEYS), Set.of("a", "s", "u"), DEFAULT_TOKEN_ALLOWLIST);
    }

    @Test
    public void nothingToValidate_passesWithAnyKeySet() {
        // No configuration, a protocol that never holds one, or a configuration that references no key.
        assertNull(validate(http(null), Set.of(), NOTHING_ALLOWED));
        assertNull(
            validate(
                McpConnector.builder().name("m").protocol(ConnectorProtocols.MCP_SSE).url("https://api.openai.com/mcp").build(),
                Set.of(),
                NOTHING_ALLOWED
            )
        );
        assertEquals(OAuth2AuthConfig.NONE, validate(http(OAuth2AuthConfig.NONE), Set.of(), NOTHING_ALLOWED));
    }

    @Test
    public void grant_isClientCredentialsOrJwtBearer() {
        validate(http(oauth2Builder().grantType(OAuth2AuthConfig.GRANT_TYPE_CLIENT_CREDENTIALS).build()));
        assertEquals(jwtBearer(), validate(http(jwtBearer()), Set.of(PRIVATE_KEY_KEY), DEFAULT_TOKEN_ALLOWLIST));
        for (String grant : new String[] { "urn:ibm:params:oauth:grant-type:apikey", "Client_Credentials" }) {
            assertEquals(
                grant,
                AuthConfigValidator.GRANT_TYPE_NOT_SUPPORTED_MESSAGE,
                fails(http(oauth2Builder().grantType(grant).build()))
            );
        }
        assertEquals(
            "auth_config.grant_type is not supported in this version; use client_credentials or urn:ietf:params:oauth:grant-type:jwt-bearer",
            AuthConfigValidator.GRANT_TYPE_NOT_SUPPORTED_MESSAGE
        );
    }

    @Test
    public void clientAuthMethodNone_isAdmittedOnlyWithTheJwtBearerGrant() {
        assertEquals(
            AuthConfigValidator.CLIENT_AUTH_METHOD_NONE_NOT_SUPPORTED_MESSAGE,
            fails(http(oauth2Builder().clientAuthMethod(OAuth2AuthConfig.CLIENT_AUTH_METHOD_NONE).clientSecret(null).build()))
        );
        assertEquals(
            "auth_config.client_auth_method [none] is supported only with the jwt-bearer grant in this version",
            AuthConfigValidator.CLIENT_AUTH_METHOD_NONE_NOT_SUPPORTED_MESSAGE
        );
    }

    @Test
    public void jwtBearer_needsNoClientIdOrSecret_andSharesTheTokenUrlActionAndClientConfigChecks() {
        Set<String> keys = Set.of(PRIVATE_KEY_KEY);
        // The client id, when there is one, is a form parameter; the block's key must be a credential key.
        validate(http(jwtBearerBuilder().clientId("app").build()), keys, DEFAULT_TOKEN_ALLOWLIST);
        validate(http(fullJwtBearer()), keys, DEFAULT_TOKEN_ALLOWLIST);
        assertEquals(AuthConfigValidator.missingCredentialKeyMessage(PRIVATE_KEY_KEY), fails(http(jwtBearer()), Set.of("s")));

        assertEquals(TokenEndpointAllowlist.NOT_ALLOWED_MESSAGE, fails(http(jwtBearer()), keys, NOTHING_ALLOWED));
        assertEquals(
            AuthConfigValidator.TOKEN_URL_DOT_SEGMENT_MESSAGE,
            fails(http(jwtBearerBuilder().tokenUrl(GOOGLE_TOKEN_URL.replace("/token", "/./token")).build()), keys)
        );
        assertEquals(
            AuthConfigValidator.AUTHORIZATION_HEADER_MESSAGE,
            fails(http(parameters(), jwtBearer(), actions(Map.of("Authorization", "Bearer x"), ACTION_URL), null), keys)
        );
        assertEquals(
            AuthConfigValidator.CLEARTEXT_ACTION_URL_MESSAGE,
            fails(http(parameters(), jwtBearer(), actions(null, "http://api.openai.com/v1"), null), keys)
        );
        ConnectorClientConfig skip = ConnectorClientConfig.builder().skipSslVerification(true).build();
        assertEquals(
            AuthConfigValidator.SKIP_SSL_VERIFICATION_MESSAGE,
            fails(http(parameters(), jwtBearer(), actions(null, ACTION_URL), skip), keys)
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
        validate(http(tokenUrl("${credential.u}")), Set.of("s", "u"), NOTHING_ALLOWED);
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
        validate(http(tokenUrl("https://acme.okta.com/a/b.c/")), Set.of("s"), EVERYTHING_ALLOWED);
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
        validate(http(tokenUrl("https://acme.okta.com/caf%C3%A9/token")), Set.of("s"), EVERYTHING_ALLOWED);
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
    public void googleAdcConfiguration_makesNoTokenRequest_soOnlyTheConnectorIsExamined() {
        OAuth2AuthConfig adc = OAuth2AuthConfig.builder().type(ConnectorAuthType.GOOGLE_ADC).build();
        // No grant, no token URL and no key to check, so the allowlist and the key set play no part.
        assertEquals(
            adc,
            AuthConfigValidator.validate(http(parameters(), null, actions(null, ACTION_URL), null), adc, Set.of(), NOTHING_ALLOWED)
        );

        ConnectorClientConfig skip = ConnectorClientConfig.builder().skipSslVerification(true).build();
        assertEquals(
            AuthConfigValidator.AUTHORIZATION_HEADER_MESSAGE,
            failsWith(http(parameters(), null, actions(Map.of("Authorization", "Bearer x"), ACTION_URL), null), adc)
        );
        assertEquals(
            AuthConfigValidator.CLEARTEXT_ACTION_URL_MESSAGE,
            failsWith(http(parameters(), null, actions(null, "http://api.openai.com/v1"), null), adc)
        );
        assertEquals(
            AuthConfigValidator.SKIP_SSL_VERIFICATION_MESSAGE,
            failsWith(http(parameters(), null, actions(null, ACTION_URL), skip), adc)
        );
    }

    @Test
    public void aGoogleCloudConnectorWithoutADeclaration_isRefusedAnAuthorizationHeaderWhenWritten_notWhenRun() {
        Map<String, String> credential = Map
            .of(GoogleCloudConnector.PRIVATE_KEY_FIELD, "k", GoogleCloudConnector.CLIENT_EMAIL_FIELD, "sa@p.iam.gserviceaccount.com");
        Set<String> keys = credential.keySet();
        List<ConnectorAction> withHeader = actions(Map.of("authorization", "Bearer stale"), ACTION_URL);
        GoogleCloudConnector stored = google(credential, withHeader, null, null);
        GoogleCloudConnector clean = google(credential, actions(null, ACTION_URL), null, null);

        // Written: the write paths pass the declaration the connector holds, none here.
        assertEquals(
            AuthConfigValidator.GOOGLE_CLOUD_AUTHORIZATION_HEADER_MESSAGE,
            assertThrows(IllegalArgumentException.class, () -> AuthConfigValidator.validate(stored, null, keys, DEFAULT_TOKEN_ALLOWLIST))
                .getMessage()
        );
        assertNull(AuthConfigValidator.validate(clean, null, keys, DEFAULT_TOKEN_ALLOWLIST));
        assertTrue(AuthConfigValidator.definesAuthorizationHeader(stored));
        assertFalse(AuthConfigValidator.definesAuthorizationHeader(clean));

        // Run: the executor passes the configuration the stored identity stands for; the token replaces the header.
        OAuth2AuthConfig implied = stored.getEffectiveAuthConfig();
        assertSame(implied, AuthConfigValidator.validate(stored, implied, keys, DEFAULT_TOKEN_ALLOWLIST));

        // The other checks of an active configuration still apply, worded for the protocol.
        assertEquals(
            AuthConfigValidator.GOOGLE_CLOUD_CLEARTEXT_ACTION_URL_MESSAGE,
            failsWhenRun(google(credential, actions(null, "http://api.openai.com/v1"), null, null), keys)
        );
        ConnectorClientConfig skip = ConnectorClientConfig.builder().skipSslVerification(true).build();
        assertEquals(
            AuthConfigValidator.GOOGLE_CLOUD_SKIP_SSL_VERIFICATION_MESSAGE,
            failsWhenRun(google(credential, actions(null, ACTION_URL), null, skip), keys)
        );
        URI cleartext = URI.create("http://api.example/v1");
        assertEquals(
            AuthConfigValidator.GOOGLE_CLOUD_CLEARTEXT_ACTION_URL_MESSAGE,
            assertThrows(IllegalArgumentException.class, () -> AuthConfigValidator.requireHttps(cleartext, stored)).getMessage()
        );
        assertEquals(
            AuthConfigValidator.CLEARTEXT_ACTION_URL_MESSAGE,
            assertThrows(IllegalArgumentException.class, () -> AuthConfigValidator.requireHttps(cleartext, http(oauth2()))).getMessage()
        );

        // An explicit declaration on the protocol keeps the general rule.
        GoogleCloudConnector explicit = google(credential, withHeader, oauth2(), null);
        assertEquals(AuthConfigValidator.AUTHORIZATION_HEADER_MESSAGE, fails(explicit, Set.of("s")));

        // An update that supplies actions is judged as a create for such a connector alone.
        assertTrue(AuthConfigValidator.actionsAreJudgedAsNew(stored, true));
        assertFalse(AuthConfigValidator.actionsAreJudgedAsNew(stored, false));
        assertFalse(AuthConfigValidator.actionsAreJudgedAsNew(explicit, true));
        assertFalse(AuthConfigValidator.actionsAreJudgedAsNew(http(oauth2()), true));
    }

    private static GoogleCloudConnector google(
        Map<String, String> credential,
        List<ConnectorAction> actions,
        OAuth2AuthConfig authConfig,
        ConnectorClientConfig clientConfig
    ) {
        return GoogleCloudConnector
            .googleCloudConnectorBuilder()
            .name("g")
            .version("1")
            .protocol(ConnectorProtocols.GOOGLE_CLOUD)
            .credential(credential)
            .actions(actions)
            .authConfig(authConfig)
            .connectorClientConfig(clientConfig)
            .build();
    }

    /** The message the executor's check refuses {@code connector} with: its effective configuration, under the default allowlist. */
    private static String failsWhenRun(GoogleCloudConnector connector, Set<String> keys) {
        return assertThrows(
            IllegalArgumentException.class,
            () -> AuthConfigValidator.validate(connector, connector.getEffectiveAuthConfig(), keys, DEFAULT_TOKEN_ALLOWLIST)
        ).getMessage();
    }

    /** The message {@link AuthConfigValidator#validate} refuses {@code config} on {@code connector} with, no keys and nothing allowed. */
    private static String failsWith(Connector connector, OAuth2AuthConfig config) {
        return assertThrows(
            IllegalArgumentException.class,
            () -> AuthConfigValidator.validate(connector, config, Set.of(), NOTHING_ALLOWED)
        ).getMessage();
    }

    @Test
    public void noneConfiguration_attachesNothing_soActionsAndClientConfigAreNotExamined() {
        ConnectorClientConfig skip = ConnectorClientConfig.builder().skipSslVerification(true).build();
        List<ConnectorAction> actions = actions(Map.of("Authorization", "Bearer ${credential.s}"), "http://plain.example.com/x");
        validate(http(parameters(), OAuth2AuthConfig.NONE, actions, skip), Set.of(), NOTHING_ALLOWED);
    }

    @Test
    public void passesAndValidateOrGrandfather_useTheGivenAllowlist() {
        HttpConnector connector = http(oauth2());
        assertTrue(passes(connector, Set.of("s"), DEFAULT_TOKEN_ALLOWLIST));
        assertFalse(passes(connector, Set.of("s"), NOTHING_ALLOWED));
        assertTrue(validateOrGrandfather(connector, Set.of("s"), NOTHING_ALLOWED, false, false));
        IllegalArgumentException e = assertThrows(
            IllegalArgumentException.class,
            () -> validateOrGrandfather(connector, Set.of("s"), NOTHING_ALLOWED, true, false)
        );
        assertEquals(TokenEndpointAllowlist.NOT_ALLOWED_MESSAGE, e.getMessage());
    }

    @Test
    public void validateOrGrandfather_passesWithoutAWarning_whenTheConfigurationIsValid() {
        HttpConnector valid = http(oauth2());
        assertFalse(validateOrGrandfather(valid, Set.of("s"), DEFAULT_TOKEN_ALLOWLIST, false, false));
        assertFalse(validateOrGrandfather(valid, Set.of("s"), DEFAULT_TOKEN_ALLOWLIST, true, true));
    }

    @Test
    public void validateOrGrandfather_anUntouchedUpdateOverAStoredFailure_proceedsWithAWarning() {
        assertTrue(validateOrGrandfather(http(THREE_KEYS), Set.of("s"), DEFAULT_TOKEN_ALLOWLIST, false, false));
    }

    @Test
    public void validateOrGrandfather_rejects_whenAuthOrTheProtocolIsTouchedOrTheStoredConfigurationPassed() {
        HttpConnector failing = http(THREE_KEYS);
        for (boolean[] touchedAndStoredPassed : new boolean[][] { { true, false }, { false, true }, { true, true } }) {
            boolean touched = touchedAndStoredPassed[0];
            boolean storedPassed = touchedAndStoredPassed[1];
            IllegalArgumentException e = assertThrows(
                IllegalArgumentException.class,
                () -> validateOrGrandfather(failing, Set.of("s"), DEFAULT_TOKEN_ALLOWLIST, touched, storedPassed)
            );
            assertEquals(AuthConfigValidator.missingCredentialKeyMessage("a"), e.getMessage());
        }
    }

    @Test
    public void passes_isTrueExactlyWhenValidateDoesNotThrow() {
        assertTrue(passes(http(null), Set.of(), DEFAULT_TOKEN_ALLOWLIST));
        assertTrue(passes(http(oauth2()), Set.of("s"), DEFAULT_TOKEN_ALLOWLIST));
        assertFalse(passes(http(oauth2()), Set.of(), DEFAULT_TOKEN_ALLOWLIST));
    }

    @Test
    public void validate_judgesTheGivenConfiguration() {
        // The caller reads the configuration from the connector; what is judged is the one it passes.
        assertEquals(oauth2(), AuthConfigValidator.validate(http(null), oauth2(), Set.of("s"), DEFAULT_TOKEN_ALLOWLIST));
        assertNull(AuthConfigValidator.validate(http(oauth2()), null, Set.of(), NOTHING_ALLOWED));
        assertFalse(AuthConfigValidator.passes(http(null), oauth2(), Set.of(), DEFAULT_TOKEN_ALLOWLIST));
    }

    @Test
    public void validatePrivateKey_parsesTheReferencedKeyWhenThePlaintextCredentialHoldsIt() {
        AuthConfigValidator.validatePrivateKey(jwtBearer(), Map.of(PRIVATE_KEY_KEY, rsaPkcs8Pem()));
        IllegalArgumentException e = assertThrows(
            IllegalArgumentException.class,
            () -> AuthConfigValidator.validatePrivateKey(jwtBearer(), Map.of(PRIVATE_KEY_KEY, pem("RSA PRIVATE KEY", rsaPkcs8(), true)))
        );
        assertEquals(PrivateKeys.INVALID_MESSAGE, e.getMessage());
        assertNull(e.getCause());
    }

    @Test
    public void validatePrivateKey_readsNothingWithoutAJwtBlockOrWithoutTheKey() {
        Map<String, String> notAKey = Map.of(PRIVATE_KEY_KEY, SECRET_LOOKING, "s", SECRET_LOOKING);
        // The reference check of validate reports a key the map lacks.
        AuthConfigValidator.validatePrivateKey(jwtBearer(), Map.of("other", SECRET_LOOKING));
        AuthConfigValidator.validatePrivateKey(jwtBearer(), null);
        AuthConfigValidator.validatePrivateKey(oauth2(), notAKey);
        AuthConfigValidator.validatePrivateKey(OAuth2AuthConfig.NONE, notAKey);
        AuthConfigValidator.validatePrivateKey(null, notAKey);
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
