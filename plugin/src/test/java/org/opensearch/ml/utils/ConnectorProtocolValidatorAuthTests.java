/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.utils;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_CONNECTOR_AUTH_DISABLED_MESSAGE;
import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_CONNECTOR_AUTH_ENABLED;
import static org.opensearch.ml.utils.AuthConfigTestFixtures.OLD_VERSION;
import static org.opensearch.ml.utils.AuthConfigTestFixtures.TOKEN_URL;
import static org.opensearch.ml.utils.AuthConfigTestFixtures.actions;
import static org.opensearch.ml.utils.AuthConfigTestFixtures.clusterState;
import static org.opensearch.ml.utils.AuthConfigTestFixtures.fullOauth2;
import static org.opensearch.ml.utils.AuthConfigTestFixtures.http;
import static org.opensearch.ml.utils.AuthConfigTestFixtures.mcp;
import static org.opensearch.ml.utils.AuthConfigTestFixtures.oauth2;
import static org.opensearch.ml.utils.AuthConfigTestFixtures.oauth2Builder;
import static org.opensearch.ml.utils.AuthConfigTestFixtures.oldNodeCluster;
import static org.opensearch.ml.utils.AuthConfigTestFixtures.pairs;
import static org.opensearch.ml.utils.AuthConfigTestFixtures.ttl;
import static org.opensearch.ml.utils.ConnectorProtocolValidator.REFERENCED_KEYS;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

import org.junit.Before;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.OpenSearchStatusException;
import org.opensearch.Version;
import org.opensearch.cluster.ClusterState;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.ml.common.CommonValue;
import org.opensearch.ml.common.connector.AwsConnector;
import org.opensearch.ml.common.connector.Connector;
import org.opensearch.ml.common.connector.ConnectorProtocols;
import org.opensearch.ml.common.connector.HttpConnector;
import org.opensearch.ml.common.connector.auth.ConnectorAuthType;
import org.opensearch.ml.common.connector.auth.OAuth2AuthConfig;
import org.opensearch.ml.common.connector.auth.OAuth2AuthConfig.OAuth2AuthConfigBuilder;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.test.OpenSearchTestCase;

/**
 * Write-time checks of connector {@code auth_config}: {@link ConnectorProtocolValidator#validateAuth},
 * {@link ConnectorProtocolValidator#validateAuthAfterUpdate}, {@link ConnectorProtocolValidator#validateSuppliedConnector}
 * and the protected-field comparator behind the credential requirement.
 */
public class ConnectorProtocolValidatorAuthTests extends OpenSearchTestCase {

    private static final OAuth2AuthConfig NONE = OAuth2AuthConfig.NONE;
    private static final Map<String, String> CREDENTIAL = Map.of("k", "v");
    private static final String[] PROTOCOLS_WITHOUT_AUTH_CONFIG = {
        ConnectorProtocols.AWS_SIGV4,
        ConnectorProtocols.MCP_SSE,
        ConnectorProtocols.MCP_STREAMABLE_HTTP,
        ConnectorProtocols.GOOGLE_CLOUD };

    @Mock
    private MLFeatureEnabledSetting setting;

    private Supplier<ClusterState> state;

    @Before
    @SuppressWarnings("unchecked")
    public void setup() {
        MockitoAnnotations.openMocks(this);
        state = mock(Supplier.class);
        when(state.get()).thenReturn(clusterState(Version.CURRENT, CommonValue.VERSION_3_10_0));
    }

    private void mixedCluster() {
        when(state.get()).thenReturn(oldNodeCluster());
    }

    private void verifyNothingRead() {
        verify(setting, never()).isConnectorAuthEnabled();
        verify(state, never()).get();
    }

    private void validateAuth(String protocol, OAuth2AuthConfig authConfig) {
        ConnectorProtocolValidator.validateAuth(protocol, authConfig, setting, state);
    }

    private String validateAuthFails(String protocol, OAuth2AuthConfig authConfig) {
        return expectThrows(IllegalArgumentException.class, () -> validateAuth(protocol, authConfig)).getMessage();
    }

    private void afterUpdate(Connector stored, String protocol, OAuth2AuthConfig updated, Map<String, String> credential) {
        ConnectorProtocolValidator.validateAuthAfterUpdate(stored, protocol, updated, credential, setting, state);
    }

    private String afterUpdateFails(Connector stored, String protocol, OAuth2AuthConfig updated, Map<String, String> credential) {
        return expectThrows(IllegalArgumentException.class, () -> afterUpdate(stored, protocol, updated, credential)).getMessage();
    }

    private void assertForbidden(Runnable call) {
        OpenSearchStatusException e = expectThrows(OpenSearchStatusException.class, call::run);
        assertEquals(RestStatus.FORBIDDEN, e.status());
        assertEquals(ML_COMMONS_CONNECTOR_AUTH_DISABLED_MESSAGE, e.getMessage());
        assertTrue(e.getMessage().contains(ML_COMMONS_CONNECTOR_AUTH_ENABLED.getKey()));
    }

    private static String notSupported(String protocol) {
        return ConnectorProtocolValidator.protocolNotSupportedMessage(protocol);
    }

    private static String guardMessage() {
        return ConnectorProtocolValidator.versionGuardMessage(OLD_VERSION);
    }

    private static String typeNotSupported() {
        return ConnectorProtocolValidator.typeNotSupportedMessage(ConnectorAuthType.OAUTH2);
    }

    private static String credentialRequired(String field) {
        return ConnectorProtocolValidator.credentialRequiredMessage(field);
    }

    private static Optional<String> changed(OAuth2AuthConfig stored, OAuth2AuthConfig resulting) {
        return ConnectorProtocolValidator.firstChangedProtectedField(stored, resulting);
    }

    /** An oauth2 configuration with one member set, beside the members every oauth2 configuration holds. */
    private static OAuth2AuthConfig oauth2With(UnaryOperator<OAuth2AuthConfigBuilder> member) {
        return member.apply(oauth2Builder()).build();
    }

    private static OAuth2AuthConfig scoped(String scope) {
        return oauth2With(b -> b.scope(scope));
    }

    private static OAuth2AuthConfig url(String tokenUrl) {
        return oauth2With(b -> b.tokenUrl(tokenUrl));
    }

    private static OAuth2AuthConfig secret(String key) {
        return oauth2With(b -> b.clientSecret("${credential." + key + "}"));
    }

    public void testValidateAuth_nothingDeclared_readsNeitherTheFlagNorTheClusterState() {
        validateAuth(ConnectorProtocols.HTTP, null);
        validateAuth(null, null);
        for (String protocol : PROTOCOLS_WITHOUT_AUTH_CONFIG) {
            validateAuth(protocol, null);
        }
        verifyNothingRead();
    }

    public void testValidateAuth_noneOnHttp_passesWithTheFlagOff() {
        when(setting.isConnectorAuthEnabled()).thenReturn(false);
        validateAuth(ConnectorProtocols.HTTP, NONE);
        verify(setting, never()).isConnectorAuthEnabled();
        verify(state).get();
    }

    public void testValidateAuth_activeType_isForbiddenWithTheFlagOffAndUnsupportedWithItOn() {
        when(setting.isConnectorAuthEnabled()).thenReturn(false);
        assertForbidden(() -> validateAuth(ConnectorProtocols.HTTP, oauth2()));

        when(setting.isConnectorAuthEnabled()).thenReturn(true);
        assertEquals(typeNotSupported(), validateAuthFails(ConnectorProtocols.HTTP, oauth2()));
    }

    public void testValidateAuth_configurationOnAProtocolWithoutAuthConfig_isRejected() {
        for (String protocol : PROTOCOLS_WITHOUT_AUTH_CONFIG) {
            assertEquals(notSupported(protocol), validateAuthFails(protocol, NONE));
        }
        assertEquals(notSupported("bogus"), validateAuthFails("bogus", NONE));
        assertEquals(notSupported(ConnectorProtocols.AWS_SIGV4), validateAuthFails(ConnectorProtocols.AWS_SIGV4, oauth2()));
        verifyNothingRead();
    }

    public void testValidateAuth_nullProtocol_isRejectedLikeAnUnsupportedProtocol() {
        assertEquals(notSupported(null), validateAuthFails(null, NONE));
        assertEquals(notSupported(null), validateAuthFails(null, oauth2()));
        verifyNothingRead();
    }

    public void testValidateAuth_versionGuard() {
        mixedCluster();
        assertEquals(guardMessage(), validateAuthFails(ConnectorProtocols.HTTP, NONE));

        when(state.get()).thenReturn(clusterState(CommonValue.VERSION_3_10_0, Version.fromString("3.11.0"), Version.CURRENT));
        validateAuth(ConnectorProtocols.HTTP, NONE);
        assertEquals(CommonValue.VERSION_3_10_0, HttpConnector.MINIMAL_SUPPORTED_VERSION_FOR_CONNECTOR_AUTH);
    }

    public void testValidateAuth_checkOrder() {
        mixedCluster();
        when(setting.isConnectorAuthEnabled()).thenReturn(false);
        // The protocol check before the version guard.
        assertEquals(notSupported(ConnectorProtocols.AWS_SIGV4), validateAuthFails(ConnectorProtocols.AWS_SIGV4, oauth2()));
        // The version guard before the flag.
        assertEquals(guardMessage(), validateAuthFails(ConnectorProtocols.HTTP, oauth2()));
        verify(setting, never()).isConnectorAuthEnabled();
        // The flag before the type check.
        when(state.get()).thenReturn(clusterState(Version.CURRENT));
        assertForbidden(() -> validateAuth(ConnectorProtocols.HTTP, oauth2()));
    }

    public void testAfterUpdate_untouchedUpdate_readsNothing() {
        mixedCluster();
        afterUpdate(http(NONE), null, null, new HashMap<>());
        afterUpdate(http(NONE), ConnectorProtocols.HTTP, null, null);
        // A protocol change over a connector that holds no configuration leaves nothing to judge.
        afterUpdate(http(null), ConnectorProtocols.AWS_SIGV4, null, null);
        afterUpdate(mcp(ConnectorProtocols.MCP_SSE), ConnectorProtocols.MCP_STREAMABLE_HTTP, null, null);
        verifyNothingRead();
    }

    public void testAfterUpdate_protocolLock() {
        assertEquals(notSupported(ConnectorProtocols.AWS_SIGV4), afterUpdateFails(http(NONE), ConnectorProtocols.AWS_SIGV4, null, null));
        assertEquals(
            notSupported(ConnectorProtocols.GOOGLE_CLOUD),
            afterUpdateFails(http(fullOauth2()), ConnectorProtocols.GOOGLE_CLOUD, null, CREDENTIAL)
        );
        // Switching to none in the same request does not help: the resulting protocol is judged first.
        assertEquals(
            notSupported(ConnectorProtocols.AWS_SIGV4),
            afterUpdateFails(http(fullOauth2()), ConnectorProtocols.AWS_SIGV4, NONE, null)
        );
        verifyNothingRead();
    }

    public void testAfterUpdate_noneToOauth2() {
        when(setting.isConnectorAuthEnabled()).thenReturn(true);
        assertEquals(credentialRequired("token_url"), afterUpdateFails(http(NONE), null, oauth2(), null));
        // A connector without a configuration is the same baseline.
        assertEquals(credentialRequired("token_url"), afterUpdateFails(http(null), null, oauth2(), null));
        assertEquals(typeNotSupported(), afterUpdateFails(http(NONE), null, oauth2(), CREDENTIAL));
        // The flag comes before the credential requirement.
        when(setting.isConnectorAuthEnabled()).thenReturn(false);
        assertForbidden(() -> afterUpdate(http(NONE), null, oauth2(), null));
    }

    public void testAfterUpdate_oauth2ToNone_withoutCredential_passes() {
        afterUpdate(http(fullOauth2()), null, NONE, null);
        verify(setting, never()).isConnectorAuthEnabled();
        verify(state).get();
    }

    public void testAfterUpdate_unchangedResend_isGuarded() {
        afterUpdate(http(NONE), null, NONE, null);
        mixedCluster();
        assertEquals(guardMessage(), afterUpdateFails(http(NONE), null, NONE, null));
        assertEquals(guardMessage(), afterUpdateFails(http(fullOauth2()), null, fullOauth2(), null));
    }

    public void testAfterUpdate_emptyCredentialCountsAsNoCredential() {
        when(setting.isConnectorAuthEnabled()).thenReturn(true);
        HttpConnector stored = http(scoped("a"));
        OAuth2AuthConfig changeScope = scoped("b");
        assertEquals(credentialRequired("scope"), afterUpdateFails(stored, null, changeScope, new HashMap<>()));
        assertEquals(credentialRequired("scope"), afterUpdateFails(stored, null, changeScope, null));
        assertEquals(typeNotSupported(), afterUpdateFails(stored, null, changeScope, CREDENTIAL));
    }

    public void testAfterUpdate_protectedChange_namesTheField() {
        when(setting.isConnectorAuthEnabled()).thenReturn(true);
        HttpConnector stored = http(fullOauth2());
        assertEquals(credentialRequired("scope"), afterUpdateFails(stored, null, fullOauth2().toBuilder().scope(null).build(), null));
        assertEquals(
            ConnectorProtocolValidator.REFERENCED_KEYS_CREDENTIAL_REQUIRED_MESSAGE,
            afterUpdateFails(stored, null, fullOauth2().toBuilder().clientSecret("${credential.other}").build(), null)
        );
        // token_response is not protected, so the request gets as far as the type check.
        assertEquals(typeNotSupported(), afterUpdateFails(stored, null, fullOauth2().toBuilder().tokenResponse(ttl(1200)).build(), null));
    }

    public void testAfterUpdate_checkOrderInAMixedCluster() {
        mixedCluster();
        when(setting.isConnectorAuthEnabled()).thenReturn(false);
        // The protocol check before the version guard.
        assertEquals(notSupported(ConnectorProtocols.AWS_SIGV4), afterUpdateFails(http(NONE), ConnectorProtocols.AWS_SIGV4, null, null));
        // The version guard before the flag, and before the credential requirement.
        assertEquals(guardMessage(), afterUpdateFails(http(NONE), null, oauth2(), null));
        assertEquals(guardMessage(), afterUpdateFails(http(scoped("a")), null, scoped("b"), CREDENTIAL));
        verify(setting, never()).isConnectorAuthEnabled();
    }

    public void testComparator_resolutionTable() {
        OAuth2AuthConfig withUrl = url(TOKEN_URL);
        assertEquals(Optional.of("token_url"), changed(NONE, withUrl));
        assertEquals(Optional.of("scope"), changed(scoped("x"), scoped("y")));
        assertEquals(Optional.empty(), changed(withUrl, withUrl.toBuilder().tokenResponse(ttl(600)).build()));
        assertEquals(Optional.of("scope"), changed(scoped("a"), oauth2Builder().build()));
        assertEquals(Optional.of(REFERENCED_KEYS), changed(secret("a"), secret("b")));
        // client_secret is the one referencing member that is not protected itself, so a new reference surfaces here too.
        assertEquals(Optional.of(REFERENCED_KEYS), changed(oauth2Builder().build(), secret("s")));
        assertEquals(Optional.of("token_url"), changed(NONE, withUrl.toBuilder().clientSecret("${credential.s}").build()));
        assertEquals(Optional.empty(), changed(fullOauth2(), fullOauth2()));
    }

    public void testComparator_eachProtectedFieldChangedAloneIsNamed() {
        Map<String, List<UnaryOperator<OAuth2AuthConfigBuilder>>> fields = new LinkedHashMap<>();
        fields.put("token_url", List.of(b -> b.tokenUrl(TOKEN_URL), b -> b.tokenUrl("https://login.example.com/other")));
        fields
            .put(
                "grant_type",
                List.of(b -> b.grantType("client_credentials"), b -> b.grantType("urn:ietf:params:oauth:grant-type:jwt-bearer"))
            );
        fields
            .put(
                "client_auth_method",
                List.of(b -> b.clientAuthMethod("client_secret_basic"), b -> b.clientAuthMethod("client_secret_post"))
            );
        fields.put("client_id", List.of(b -> b.clientId("a"), b -> b.clientId("b")));
        fields.put("scope", List.of(b -> b.scope("a"), b -> b.scope("b")));
        fields.put("audience", List.of(b -> b.audience("a"), b -> b.audience("b")));
        fields
            .put(
                "token_request_params",
                List.of(b -> b.tokenRequestParams(Map.of("resource", "a")), b -> b.tokenRequestParams(Map.of("resource", "b")))
            );
        OAuth2AuthConfig smallest = oauth2Builder().build();
        for (Map.Entry<String, List<UnaryOperator<OAuth2AuthConfigBuilder>>> field : fields.entrySet()) {
            OAuth2AuthConfig before = oauth2With(field.getValue().get(0));
            OAuth2AuthConfig after = oauth2With(field.getValue().get(1));
            assertEquals(field.getKey(), Optional.of(field.getKey()), changed(before, after));
            assertEquals("smallest against changed: " + field.getKey(), Optional.of(field.getKey()), changed(smallest, after));
            assertEquals("changed against smallest: " + field.getKey(), Optional.of(field.getKey()), changed(after, smallest));
        }
    }

    public void testComparator_aNoneResultIsEmptyAndTheNoneBaselineReportsTheTokenUrlFirst() {
        assertEquals(Optional.empty(), changed(NONE, NONE));
        assertEquals(Optional.empty(), changed(oauth2(), NONE));
        // From the none baseline, the token_url every oauth2 configuration holds is the first member that differs.
        assertEquals(Optional.of("token_url"), changed(NONE, oauth2Builder().build()));
    }

    public void testComparator_fieldsAreComparedInSchemaOrder() {
        OAuth2AuthConfig stored = fullOauth2();
        OAuth2AuthConfigBuilder update = fullOauth2()
            .toBuilder()
            .clientAuthMethod(OAuth2AuthConfig.CLIENT_AUTH_METHOD_CLIENT_SECRET_POST)
            .clientId("other")
            .scope("s2")
            .audience("a2")
            .tokenRequestParams(pairs("resource", "r2"));
        assertEquals(Optional.of("client_auth_method"), changed(stored, update.build()));
        assertEquals(Optional.of("client_id"), changed(stored, update.clientAuthMethod(stored.getClientAuthMethod()).build()));
        assertEquals(Optional.of("scope"), changed(stored, update.clientId(stored.getClientId()).build()));
        assertEquals(Optional.of("audience"), changed(stored, update.scope(stored.getScope()).build()));
        assertEquals(Optional.of("token_request_params"), changed(stored, update.audience(stored.getAudience()).build()));
        assertEquals(Optional.empty(), changed(stored, update.tokenRequestParams(stored.getTokenRequestParams()).build()));
    }

    public void testComparator_literalTokenUrlsCompareCanonicallyAndReferencesAsWritten() {
        assertEquals(Optional.empty(), changed(url("https://acme.okta.com:443/t"), url("https://ACME.okta.com/t")));
        assertEquals(Optional.empty(), changed(url("https://H"), url("https://h/")));
        assertEquals(Optional.of("token_url"), changed(url("https://h:8443/t"), url("https://h/t")));
        assertEquals(Optional.empty(), changed(url("${credential.url}"), url("${credential.url}")));
        assertEquals(Optional.of("token_url"), changed(url("${credential.url}"), url("${credential.other}")));
        assertEquals(Optional.of("token_url"), changed(url("${credential.url}"), url(TOKEN_URL)));
    }

    public void testComparator_baselineIsNone() {
        for (OAuth2AuthConfig resulting : new OAuth2AuthConfig[] { NONE, oauth2(), scoped("s"), secret("s"), fullOauth2() }) {
            assertEquals(changed(NONE, resulting), changed(null, resulting));
        }
    }

    private static String suppliedFails(Connector stored, Connector supplied) {
        return expectThrows(IllegalArgumentException.class, () -> ConnectorProtocolValidator.validateSuppliedConnector(stored, supplied))
            .getMessage();
    }

    public void testSupplied_storedAndSuppliedHoldAuthConfig() {
        HttpConnector stored = http(fullOauth2());
        ConnectorProtocolValidator.validateSuppliedConnector(stored, http(fullOauth2()));
        // Without a stored configuration, the supplied one compares with the none baseline.
        AwsConnector aws = AwsConnector
            .awsConnectorBuilder()
            .name("a")
            .version("1")
            .protocol(ConnectorProtocols.AWS_SIGV4)
            .parameters(Map.of("region", "us-east-1", "service_name", "sagemaker"))
            .credential(Map.of("access_key", "ak", "secret_key", "sk"))
            .actions(actions("https://runtime.sagemaker.us-east-1.amazonaws.com/endpoints/e/invocations"))
            .build();
        for (Connector withoutAuthConfig : new Connector[] { null, http(null), aws, mcp(ConnectorProtocols.MCP_SSE) }) {
            ConnectorProtocolValidator.validateSuppliedConnector(withoutAuthConfig, http(null));
            ConnectorProtocolValidator.validateSuppliedConnector(withoutAuthConfig, http(NONE));
            assertEquals(
                ConnectorProtocolValidator.suppliedConnectorChangesMessage("token_url"),
                suppliedFails(withoutAuthConfig, http(oauth2()))
            );
        }
    }

    public void testSupplied_droppedMemberOrMissingObject_isRejected() {
        HttpConnector stored = http(fullOauth2());
        String message = ConnectorProtocolValidator.SUPPLIED_CONNECTOR_REMOVES_FIELDS_MESSAGE;
        assertEquals(message, suppliedFails(stored, http(null)));
        assertEquals(message, suppliedFails(stored, http(NONE)));
        assertEquals(message, suppliedFails(stored, http(fullOauth2().toBuilder().scope(null).build())));
        // A nested member, and a whole object that falls back to its defaults.
        assertEquals(message, suppliedFails(stored, http(fullOauth2().toBuilder().tokenRequestParams(Map.of("resource", "r")).build())));
        assertEquals(message, suppliedFails(stored, http(fullOauth2().toBuilder().tokenResponse(null).build())));
    }

    public void testSupplied_protectedChange_isRejected() {
        HttpConnector stored = http(fullOauth2());
        Map<String, UnaryOperator<OAuth2AuthConfigBuilder>> changes = new LinkedHashMap<>();
        changes.put("token_url", b -> b.tokenUrl("https://login.example.com/other"));
        changes.put("grant_type", b -> b.grantType("urn:ietf:params:oauth:grant-type:jwt-bearer"));
        changes.put("client_auth_method", b -> b.clientAuthMethod("client_secret_post"));
        changes.put("client_id", b -> b.clientId("c"));
        changes.put("scope", b -> b.scope("s2"));
        changes.put("audience", b -> b.audience("a2"));
        changes.put("token_request_params", b -> b.tokenRequestParams(pairs("resource", "r", "x", "z")));
        for (Map.Entry<String, UnaryOperator<OAuth2AuthConfigBuilder>> change : changes.entrySet()) {
            assertEquals(
                change.getKey(),
                ConnectorProtocolValidator.suppliedConnectorChangesMessage(change.getKey()),
                suppliedFails(stored, http(change.getValue().apply(fullOauth2().toBuilder()).build()))
            );
        }
        assertEquals(
            ConnectorProtocolValidator.SUPPLIED_CONNECTOR_CHANGES_REFERENCED_KEYS_MESSAGE,
            suppliedFails(stored, http(fullOauth2().toBuilder().clientSecret("${credential.k}").build()))
        );
        assertEquals(ConnectorProtocolValidator.suppliedConnectorChangesMessage("token_url"), suppliedFails(http(NONE), http(oauth2())));
    }

    public void testSupplied_supersetChangingOnlyUnprotectedMembers_passes() {
        ConnectorProtocolValidator
            .validateSuppliedConnector(http(fullOauth2()), http(fullOauth2().toBuilder().tokenResponse(ttl(1200)).build()));
    }
}
