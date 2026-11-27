/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.remote.oauth2;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.opensearch.ml.common.connector.auth.OAuth2AuthConfig.CLIENT_AUTH_METHOD_CLIENT_SECRET_POST;
import static org.opensearch.ml.common.connector.auth.OAuth2AuthConfig.CLIENT_AUTH_METHOD_NONE;
import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_TRUSTED_OAUTH2_TOKEN_ENDPOINTS_REGEX;
import static org.opensearch.ml.engine.algorithms.remote.oauth2.OAuth2TestFixtures.CLIENT_SECRET;
import static org.opensearch.ml.engine.algorithms.remote.oauth2.OAuth2TestFixtures.PUBLIC_TOKEN_URL;
import static org.opensearch.ml.engine.algorithms.remote.oauth2.OAuth2TestFixtures.TOKEN_RESPONSE;
import static org.opensearch.ml.engine.algorithms.remote.oauth2.OAuth2TestFixtures.clusterSettings;
import static org.opensearch.ml.engine.algorithms.remote.oauth2.OAuth2TestFixtures.config;
import static org.opensearch.ml.engine.algorithms.remote.oauth2.OAuth2TestFixtures.connector;
import static org.opensearch.ml.engine.algorithms.remote.oauth2.OAuth2TestFixtures.featureSettings;
import static org.opensearch.ml.engine.algorithms.remote.oauth2.OAuth2TestFixtures.oauth2Connector;
import static org.opensearch.ml.engine.algorithms.remote.oauth2.OAuth2TestFixtures.reply;
import static org.opensearch.ml.engine.algorithms.remote.oauth2.OAuth2TestFixtures.scriptedTransport;
import static org.opensearch.ml.engine.algorithms.remote.oauth2.OAuth2TestFixtures.testThreadPool;
import static org.opensearch.ml.engine.algorithms.remote.oauth2.OAuth2TestFixtures.tokenClient;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.opensearch.common.settings.ClusterSettings;
import org.opensearch.common.settings.Settings;
import org.opensearch.core.action.ActionListener;
import org.opensearch.ml.common.connector.HttpConnector;
import org.opensearch.ml.common.connector.auth.AuthConfigValidator;
import org.opensearch.ml.common.connector.auth.TokenEndpointAllowlist;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.threadpool.ThreadPool;

import okhttp3.Request;
import okio.Buffer;

public class AuthorizationProviderFactoryTests {

    private static final String ACTION_URL = "https://203.0.113.20/v1/chat/completions";

    private final List<Request> recorded = new CopyOnWriteArrayList<>();
    private ThreadPool threadPool;
    private ClusterSettings clusterSettings;
    private AuthorizationProviderFactory factory;

    @Before
    public void setUp() {
        threadPool = testThreadPool();
        clusterSettings = clusterSettings();
        MLFeatureEnabledSetting settings = featureSettings(clusterSettings);
        OAuth2TokenCache cache = new OAuth2TokenCache(clusterSettings, settings, stat -> {}, System::nanoTime);
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

    private static String token(AuthorizationProvider provider) throws Exception {
        CompletableFuture<TokenHandle> result = new CompletableFuture<>();
        provider.authorizationValue(ActionListener.wrap(result::complete, result::completeExceptionally));
        return result.get(10, TimeUnit.SECONDS).value();
    }

    private void assertRefused(HttpConnector connector, String message) {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> factory.providerFor(connector));
        assertEquals(message, e.getMessage());
        assertTrue(recorded.isEmpty());
    }

    @Test
    public void theConfigurationIsCheckedAgainUnderTheCurrentSettings() {
        assertRefused(
            connector(config(PUBLIC_TOKEN_URL, CLIENT_AUTH_METHOD_NONE, ""), ACTION_URL),
            AuthConfigValidator.CLIENT_AUTH_METHOD_NONE_NOT_SUPPORTED_MESSAGE
        );

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
