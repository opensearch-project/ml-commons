/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.remote.oauth2;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.opensearch.ml.common.connector.auth.OAuth2AuthConfig.CLIENT_AUTH_METHOD_CLIENT_SECRET_POST;
import static org.opensearch.ml.common.connector.auth.OAuth2AuthConfig.CLIENT_AUTH_METHOD_NONE;
import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_CONNECTOR_AUTH_DISABLED_MESSAGE;
import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_CONNECTOR_AUTH_ENABLED;
import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_CONNECTOR_OAUTH2_EXTENSION_GRANTS;
import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_TRUSTED_OAUTH2_TOKEN_ENDPOINTS_REGEX;
import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_VERTEXAI_CONNECTOR_DISABLED_MESSAGE;
import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_VERTEXAI_CONNECTOR_ENABLED;
import static org.opensearch.ml.engine.algorithms.remote.oauth2.OAuth2TestFixtures.CLIENT_SECRET;
import static org.opensearch.ml.engine.algorithms.remote.oauth2.OAuth2TestFixtures.PKCS1_PEM;
import static org.opensearch.ml.engine.algorithms.remote.oauth2.OAuth2TestFixtures.PUBLIC_TOKEN_HOST;
import static org.opensearch.ml.engine.algorithms.remote.oauth2.OAuth2TestFixtures.PUBLIC_TOKEN_URL;
import static org.opensearch.ml.engine.algorithms.remote.oauth2.OAuth2TestFixtures.TOKEN_RESPONSE;
import static org.opensearch.ml.engine.algorithms.remote.oauth2.OAuth2TestFixtures.clusterSettings;
import static org.opensearch.ml.engine.algorithms.remote.oauth2.OAuth2TestFixtures.config;
import static org.opensearch.ml.engine.algorithms.remote.oauth2.OAuth2TestFixtures.connector;
import static org.opensearch.ml.engine.algorithms.remote.oauth2.OAuth2TestFixtures.featureSettings;
import static org.opensearch.ml.engine.algorithms.remote.oauth2.OAuth2TestFixtures.googleAdcConnector;
import static org.opensearch.ml.engine.algorithms.remote.oauth2.OAuth2TestFixtures.googleConnector;
import static org.opensearch.ml.engine.algorithms.remote.oauth2.OAuth2TestFixtures.googleServiceAccountConnector;
import static org.opensearch.ml.engine.algorithms.remote.oauth2.OAuth2TestFixtures.jwtBearerConfig;
import static org.opensearch.ml.engine.algorithms.remote.oauth2.OAuth2TestFixtures.jwtBearerConnector;
import static org.opensearch.ml.engine.algorithms.remote.oauth2.OAuth2TestFixtures.oauth2Connector;
import static org.opensearch.ml.engine.algorithms.remote.oauth2.OAuth2TestFixtures.parse;
import static org.opensearch.ml.engine.algorithms.remote.oauth2.OAuth2TestFixtures.privateKeyCredential;
import static org.opensearch.ml.engine.algorithms.remote.oauth2.OAuth2TestFixtures.reply;
import static org.opensearch.ml.engine.algorithms.remote.oauth2.OAuth2TestFixtures.rsaPkcs8Pem;
import static org.opensearch.ml.engine.algorithms.remote.oauth2.OAuth2TestFixtures.scriptedTransport;
import static org.opensearch.ml.engine.algorithms.remote.oauth2.OAuth2TestFixtures.serviceAccountCredential;
import static org.opensearch.ml.engine.algorithms.remote.oauth2.OAuth2TestFixtures.testThreadPool;
import static org.opensearch.ml.engine.algorithms.remote.oauth2.OAuth2TestFixtures.tokenClient;
import static org.opensearch.ml.engine.algorithms.remote.oauth2.OAuth2TokenCache.Stat.TOKEN_FAILURE;
import static org.opensearch.ml.engine.algorithms.remote.oauth2.OAuth2TokenCache.Stat.TOKEN_REQUEST;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.opensearch.OpenSearchStatusException;
import org.opensearch.common.settings.ClusterSettings;
import org.opensearch.common.settings.Settings;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.ml.common.connector.ConnectorClientConfig;
import org.opensearch.ml.common.connector.GoogleCloudConnector;
import org.opensearch.ml.common.connector.HttpConnector;
import org.opensearch.ml.common.connector.auth.AuthConfigValidator;
import org.opensearch.ml.common.connector.auth.ExtensionGrantAllowlist;
import org.opensearch.ml.common.connector.auth.OAuth2AuthConfig;
import org.opensearch.ml.common.connector.auth.TokenEndpointAllowlist;
import org.opensearch.ml.common.exception.MLException;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.threadpool.ThreadPool;

import okhttp3.Request;
import okio.Buffer;

public class AuthorizationProviderFactoryTests {

    private static final String ACTION_URL = "https://203.0.113.20/v1/chat/completions";

    private final List<Request> recorded = new CopyOnWriteArrayList<>();
    private final List<OAuth2TokenCache.Stat> counted = new CopyOnWriteArrayList<>();
    /** The cache's clock, which stands still unless a test moves it. */
    private final AtomicLong clock = new AtomicLong();
    private ThreadPool threadPool;
    private ClusterSettings clusterSettings;
    private AuthorizationProviderFactory factory;

    @Before
    public void setUp() {
        threadPool = testThreadPool();
        clusterSettings = clusterSettings();
        MLFeatureEnabledSetting settings = featureSettings(clusterSettings);
        OAuth2TokenCache cache = new OAuth2TokenCache(clusterSettings, settings, counted::add, clock::get);
        OAuth2TokenClient client = tokenClient(
            scriptedTransport(recorded, reply(200, TOKEN_RESPONSE), reply(200, TOKEN_RESPONSE.replace("tok-1", "tok-2"))),
            threadPool
        );
        factory = new AuthorizationProviderFactory(cache, client, settings);
    }

    @After
    public void tearDown() {
        ThreadPool.terminate(threadPool, 10, TimeUnit.SECONDS);
    }

    private static CompletableFuture<TokenHandle> authorizationValue(AuthorizationProvider provider) {
        CompletableFuture<TokenHandle> result = new CompletableFuture<>();
        provider.authorizationValue(ActionListener.wrap(result::complete, result::completeExceptionally));
        return result;
    }

    private static String token(AuthorizationProvider provider) throws Exception {
        return authorizationValue(provider).get(10, TimeUnit.SECONDS).value();
    }

    private static Exception failure(AuthorizationProvider provider) {
        ExecutionException e = assertThrows(ExecutionException.class, () -> authorizationValue(provider).get(10, TimeUnit.SECONDS));
        return (Exception) e.getCause();
    }

    private void assertRefused(HttpConnector connector, String message) {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> factory.providerFor(connector));
        assertEquals(message, e.getMessage());
        assertTrue(recorded.isEmpty());
    }

    private void setConnectorAuthEnabled(boolean enabled) {
        clusterSettings.applySettings(Settings.builder().put(ML_COMMONS_CONNECTOR_AUTH_ENABLED.getKey(), enabled).build());
    }

    private void setVertexAiEnabled(boolean enabled) {
        clusterSettings.applySettings(Settings.builder().put(ML_COMMONS_VERTEXAI_CONNECTOR_ENABLED.getKey(), enabled).build());
    }

    /** The 403 the provider answers with, and its message. */
    private static String forbidden(AuthorizationProvider provider) {
        OpenSearchStatusException e = (OpenSearchStatusException) failure(provider);
        assertEquals(RestStatus.FORBIDDEN, e.status());
        return e.getMessage();
    }

    private static String form(Request request) throws Exception {
        Buffer body = new Buffer();
        request.body().writeTo(body);
        return body.readUtf8();
    }

    @Test
    public void theConfigurationIsCheckedAgainUnderTheCurrentSettings() {
        // A client that authenticates with no method has no secret to reference.
        OAuth2AuthConfig none = parse(
            "{'type':'oauth2','token_url':'"
                + PUBLIC_TOKEN_URL
                + "','client_auth_method':'"
                + CLIENT_AUTH_METHOD_NONE
                + "','client_id':'app'}"
        );
        assertRefused(connector(none, ACTION_URL), AuthConfigValidator.CLIENT_AUTH_METHOD_NONE_MESSAGE);

        clusterSettings
            .applySettings(Settings.builder().putList(ML_COMMONS_TRUSTED_OAUTH2_TOKEN_ENDPOINTS_REGEX.getKey(), List.of()).build());
        assertRefused(oauth2Connector(ACTION_URL), TokenEndpointAllowlist.NOT_ALLOWED_MESSAGE);
    }

    @Test
    public void aValidConfiguration_obtainsItsTokenThroughTheCache() throws Exception {
        AuthorizationProvider provider = factory.providerFor(oauth2Connector(ACTION_URL));

        assertEquals("tok-1", token(provider));
        assertEquals("tok-1", token(provider));

        assertEquals(1, recorded.size());
        assertEquals(PUBLIC_TOKEN_URL, recorded.get(0).url().toString());
    }

    @Test
    public void connectorsWithTheSameConfigurationShareOneToken() throws Exception {
        assertEquals("tok-1", token(factory.providerFor(oauth2Connector(ACTION_URL))));
        assertEquals("tok-1", token(factory.providerFor(oauth2Connector("https://203.0.113.21/v1/other"))));
        assertEquals(1, recorded.size());

        HttpConnector otherScope = connector(config(CLIENT_AUTH_METHOD_CLIENT_SECRET_POST, "'scope':'other'"), ACTION_URL);
        assertEquals("tok-2", token(factory.providerFor(otherScope)));
        assertEquals(2, recorded.size());
    }

    @Test
    public void connectorsWhoseTokenRequestParamsAreGivenInAnotherOrderShareOneToken() throws Exception {
        String resourceFirst = "'token_request_params':{'resource':'r','tenant':'t'}";
        String tenantFirst = "'token_request_params':{'tenant':'t','resource':'r'}";

        assertEquals(
            "tok-1",
            token(factory.providerFor(connector(config(CLIENT_AUTH_METHOD_CLIENT_SECRET_POST, resourceFirst), ACTION_URL)))
        );
        assertEquals(
            "tok-1",
            token(factory.providerFor(connector(config(CLIENT_AUTH_METHOD_CLIENT_SECRET_POST, tenantFirst), ACTION_URL)))
        );

        assertEquals(1, recorded.size());
    }

    @Test
    public void aJwtBearerConfiguration_obtainsItsTokenWithASignedAssertion() throws Exception {
        AuthorizationProvider provider = factory.providerFor(jwtBearerConnector(ACTION_URL));

        assertEquals("tok-1", token(provider));
        assertEquals("tok-1", token(provider));

        assertEquals(1, recorded.size());
        assertNull(recorded.get(0).header("Authorization"));
        Buffer body = new Buffer();
        recorded.get(0).body().writeTo(body);
        assertTrue(body.readUtf8().contains("&assertion="));
    }

    @Test
    public void aKeyTheSignerCannotUse_failsTheRequest_andHoldsTheNextOnesBackLikeAnEndpointFailure() {
        AuthorizationProvider provider = factory.providerFor(connector(jwtBearerConfig(), ACTION_URL, privateKeyCredential(PKCS1_PEM)));
        String message = String
            .format(Locale.ROOT, OAuth2TokenClient.FAILURE_MESSAGE, PUBLIC_TOKEN_HOST, OAuth2TokenClient.INVALID_PRIVATE_KEY);

        Exception first = failure(provider);
        assertSame(MLException.class, first.getClass());
        assertEquals(message, first.getMessage());
        // Within the backoff the next request fails at once; the key is not parsed again.
        assertEquals(message, failure(provider).getMessage());

        assertEquals(List.of(TOKEN_REQUEST, TOKEN_FAILURE), counted);
        assertTrue(recorded.isEmpty());
    }

    @Test
    public void aLegacyServiceAccountConnector_runsOnTheTokenEngineUnderTheVertexAiSettingAlone() throws Exception {
        AuthorizationProvider provider = factory.providerFor(googleServiceAccountConnector(ACTION_URL));

        assertEquals("tok-1", token(provider));
        assertEquals(PUBLIC_TOKEN_URL, recorded.get(0).url().toString());
        assertTrue(form(recorded.get(0)).contains("&assertion="));
        // connector.auth_enabled plays no part: turning it off empties the cache, and the next request is served again.
        setConnectorAuthEnabled(false);
        assertEquals("tok-2", token(provider));
        setVertexAiEnabled(false);
        assertEquals(ML_COMMONS_VERTEXAI_CONNECTOR_DISABLED_MESSAGE, forbidden(provider));

        assertEquals(2, recorded.size());
        // Counted as any connector on the engine.
        assertEquals(List.of(TOKEN_REQUEST, TOKEN_REQUEST), counted);
    }

    @Test
    public void aLegacyServiceAccountConnector_whoseTokenUriNoPatternMatches_failsEveryTokenRequest_andNothingIsSent() {
        GoogleCloudConnector connector = googleConnector(null, serviceAccountCredential("https://idp.example/token"), null, ACTION_URL);
        AuthorizationProvider provider = factory.providerFor(connector);

        Exception e = failure(provider);

        assertSame(IllegalArgumentException.class, e.getClass());
        assertEquals(
            String.format(Locale.ROOT, OAuth2TokenClient.FAILURE_MESSAGE, "idp.example", TokenEndpointAllowlist.NOT_ALLOWED_MESSAGE),
            e.getMessage()
        );
        assertTrue(recorded.isEmpty());
    }

    @Test
    public void aLegacyAdcConnector_isServedByTheLibraryBackedProvider_whichTheEngineNeitherCachesNorCounts() {
        AuthorizationProvider provider = factory.providerFor(googleAdcConnector(ACTION_URL));

        assertTrue(provider instanceof GoogleAdcAuthorizationProvider);
        setVertexAiEnabled(false);
        assertEquals(ML_COMMONS_VERTEXAI_CONNECTOR_DISABLED_MESSAGE, forbidden(provider));
        assertTrue(recorded.isEmpty());
        assertTrue(counted.isEmpty());
    }

    @Test
    public void aLegacyAdcConnector_isHeldToTheConnectorLevelChecksOfAnActiveConfiguration() {
        ConnectorClientConfig skip = ConnectorClientConfig.builder().skipSslVerification(true).build();
        Map<String, String> adc = Map.of(GoogleCloudConnector.AUTH_MODE_FIELD, GoogleCloudConnector.AUTH_MODE_ADC);

        assertRefused(googleConnector(adc, null, null, ACTION_URL, skip), AuthConfigValidator.GOOGLE_CLOUD_SKIP_SSL_VERIFICATION_MESSAGE);
    }

    /** Written before such a header was refused; the token takes its place on every request, so the connector is served. */
    @Test
    public void aLegacyConnectorWithAStoredAuthorizationHeader_isServed() throws Exception {
        Map<String, String> header = Map.of("authorization", "Bearer stale");
        Map<String, String> adc = Map.of(GoogleCloudConnector.AUTH_MODE_FIELD, GoogleCloudConnector.AUTH_MODE_ADC);

        AuthorizationProvider serviceAccount = factory
            .providerFor(googleConnector(null, serviceAccountCredential(PUBLIC_TOKEN_URL), null, ACTION_URL, null, header));
        assertEquals("tok-1", token(serviceAccount));

        assertTrue(
            factory.providerFor(googleConnector(adc, null, null, ACTION_URL, null, header)) instanceof GoogleAdcAuthorizationProvider
        );
    }

    /** The admitted extension grants are read at every hand-out, so a grant the operator removes stops at once, cached token or not. */
    @Test
    public void anExtensionGrantTheSettingNoLongerAdmits_failsTheNextHandOut_andNothingIsSent() throws Exception {
        OAuth2AuthConfig apikey = parse(
            "{'type':'oauth2','token_url':'"
                + PUBLIC_TOKEN_URL
                + "','grant_type':'urn:ibm:params:oauth:grant-type:apikey','client_auth_method':'none',"
                + "'token_request_params':{'apikey':'${credential.apikey}'}}"
        );
        AuthorizationProvider provider = factory.providerFor(connector(apikey, ACTION_URL, Map.of("apikey", "k-1")));
        assertEquals("tok-1", token(provider));
        assertEquals(1, recorded.size());

        clusterSettings.applySettings(Settings.builder().putList(ML_COMMONS_CONNECTOR_OAUTH2_EXTENSION_GRANTS.getKey()).build());
        Exception e = failure(provider);
        assertSame(IllegalArgumentException.class, e.getClass());
        assertEquals(ExtensionGrantAllowlist.NOT_ADMITTED_MESSAGE, e.getMessage());
        assertEquals(1, recorded.size());
        // A connector built while the grant is not admitted fails the write check the factory re-runs.
        IllegalArgumentException refused = assertThrows(
            IllegalArgumentException.class,
            () -> factory.providerFor(connector(apikey, ACTION_URL, Map.of("apikey", "k-1")))
        );
        assertEquals(ExtensionGrantAllowlist.NOT_ADMITTED_MESSAGE, refused.getMessage());

        clusterSettings
            .applySettings(
                Settings
                    .builder()
                    .putList(ML_COMMONS_CONNECTOR_OAUTH2_EXTENSION_GRANTS.getKey(), "urn:ibm:params:oauth:grant-type:apikey")
                    .build()
            );
        assertEquals("tok-1", token(provider));
        assertEquals(1, recorded.size());
    }

    @Test
    public void anExplicitBlockOnGoogleCloud_runsUnderBothSettings() throws Exception {
        GoogleCloudConnector connector = googleConnector(null, privateKeyCredential(rsaPkcs8Pem()), jwtBearerConfig(), ACTION_URL);
        AuthorizationProvider provider = factory.providerFor(connector);
        assertEquals("tok-1", token(provider));

        setConnectorAuthEnabled(false);
        assertEquals(ML_COMMONS_CONNECTOR_AUTH_DISABLED_MESSAGE, forbidden(provider));
        setConnectorAuthEnabled(true);
        setVertexAiEnabled(false);
        assertEquals(ML_COMMONS_VERTEXAI_CONNECTOR_DISABLED_MESSAGE, forbidden(provider));
        // Both off, in one update: the Vertex AI setting is named, as the first of the two.
        clusterSettings
            .applySettings(
                Settings
                    .builder()
                    .put(ML_COMMONS_VERTEXAI_CONNECTOR_ENABLED.getKey(), false)
                    .put(ML_COMMONS_CONNECTOR_AUTH_ENABLED.getKey(), false)
                    .build()
            );
        assertEquals(ML_COMMONS_VERTEXAI_CONNECTOR_DISABLED_MESSAGE, forbidden(provider));
        assertEquals(1, recorded.size());
    }

    @Test
    public void theCredentialValuesAreTakenWhenTheProviderIsBuilt() throws Exception {
        HttpConnector connector = oauth2Connector(ACTION_URL);
        AuthorizationProvider provider = factory.providerFor(connector);
        connector.removeCredential();

        assertEquals("tok-1", token(provider));

        Buffer body = new Buffer();
        recorded.get(0).body().writeTo(body);
        assertTrue(body.readUtf8().contains("client_secret=" + CLIENT_SECRET));
    }
}
