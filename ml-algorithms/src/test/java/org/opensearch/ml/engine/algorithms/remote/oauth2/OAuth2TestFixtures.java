/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.remote.oauth2;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.opensearch.ml.common.connector.auth.OAuth2AuthConfig.CLIENT_AUTH_METHOD_CLIENT_SECRET_POST;
import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_CONNECTOR_AUTH_ENABLED;
import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_TRUSTED_OAUTH2_TOKEN_ENDPOINTS_REGEX;
import static org.opensearch.ml.engine.algorithms.remote.RemoteConnectorExecutor.RETRY_EXECUTOR;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;
import java.util.regex.Pattern;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.opensearch.common.CheckedRunnable;
import org.opensearch.common.settings.ClusterSettings;
import org.opensearch.common.settings.Settings;
import org.opensearch.ml.common.connector.ConnectorAction;
import org.opensearch.ml.common.connector.ConnectorProtocols;
import org.opensearch.ml.common.connector.HttpConnector;
import org.opensearch.ml.common.connector.auth.OAuth2AuthConfig;
import org.opensearch.ml.common.connector.auth.TokenEndpointAllowlist;
import org.opensearch.ml.common.httpclient.ConnectorAddressValidator;
import org.opensearch.ml.common.httpclient.TokenEndpointAddressValidator;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.utils.StringUtils;
import org.opensearch.test.MockLogAppender;
import org.opensearch.threadpool.FixedExecutorBuilder;
import org.opensearch.threadpool.TestThreadPool;
import org.opensearch.threadpool.ThreadPool;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/** Fixtures shared by the OAuth2 tests of this module. No fixture resolves a name or opens a connection. */
public final class OAuth2TestFixtures {

    private OAuth2TestFixtures() {}

    /** A literal documentation address, so that no test resolves a name. */
    public static final String PUBLIC_TOKEN_HOST = "203.0.113.10";
    public static final String PUBLIC_TOKEN_URL = "https://" + PUBLIC_TOKEN_HOST + "/token";
    /** A trusted token endpoint pattern that matches {@link #PUBLIC_TOKEN_URL} only. */
    public static final String PUBLIC_TOKEN_URL_PATTERN = "^https://203\\.0\\.113\\.10/token$";
    public static final String CLIENT_ID = "app";
    /** Looks like a secret; no message or log line may echo it. */
    public static final String CLIENT_SECRET = "s3cr3t-value-never-echoed";
    /** A token endpoint response with a token and a lifetime. */
    public static final String TOKEN_RESPONSE = "{\"access_token\":\"tok-1\",\"token_type\":\"Bearer\",\"expires_in\":3599}";

    /** Writes JSON with single quotes for readability. */
    public static String json(String singleQuoted) {
        return singleQuoted.replace('\'', '"');
    }

    /**
     * An oauth2 configuration with the given token URL, method and extra members (single-quoted JSON), the client id
     * {@link #CLIENT_ID} and the secret referenced as {@code ${credential.client_secret}}, as JSON.
     */
    public static String block(String tokenUrl, String method, String members) {
        return json(
            "{'type':'oauth2','token_url':'"
                + tokenUrl
                + "','client_auth_method':'"
                + method
                + "','client_id':'"
                + CLIENT_ID
                + "','client_secret':'${credential.client_secret}'"
                + (members.isEmpty() ? "" : "," + members)
                + "}"
        );
    }

    /** The parsed {@link #block}. */
    public static OAuth2AuthConfig config(String tokenUrl, String method, String members) {
        return parse(block(tokenUrl, method, members));
    }

    /** The configuration the JSON object {@code json} denotes, parsed as a request is; single quotes are accepted. */
    @SuppressWarnings("unchecked")
    public static OAuth2AuthConfig parse(String json) {
        return OAuth2AuthConfig.parse(StringUtils.MAPPER.readValue(json(json), LinkedHashMap.class));
    }

    /** {@link #config(String, String, String)} for {@link #PUBLIC_TOKEN_URL}. */
    public static OAuth2AuthConfig config(String method, String members) {
        return config(PUBLIC_TOKEN_URL, method, members);
    }

    public static OAuth2AuthConfig config(String method) {
        return config(method, "");
    }

    /** A snapshot of every key of {@code credential}. */
    public static CredentialSnapshot snapshot(Map<String, String> credential) {
        return CredentialSnapshot.of(credential, credential.keySet());
    }

    /** The credential {@link #config} references. */
    public static CredentialSnapshot clientSecret() {
        return snapshot(Map.of("client_secret", CLIENT_SECRET));
    }

    /**
     * An http connector holding {@code authConfig}, with one predict action to {@code actionUrl} and the decrypted
     * credential {@link #clientSecret} reads.
     */
    public static HttpConnector connector(OAuth2AuthConfig authConfig, String actionUrl) {
        HttpConnector connector = HttpConnector
            .builder()
            .name("c")
            .version("1")
            .protocol(ConnectorProtocols.HTTP)
            .parameters(new HashMap<>())
            .authConfig(authConfig)
            .actions(
                List
                    .of(
                        ConnectorAction
                            .builder()
                            .actionType(ConnectorAction.ActionType.PREDICT)
                            .method("POST")
                            .url(actionUrl)
                            .requestBody("{}")
                            .build()
                    )
            )
            .build();
        connector.setDecryptedCredential(new HashMap<>(Map.of("client_secret", CLIENT_SECRET)));
        return connector;
    }

    /** {@link #connector} with a client_secret_post configuration for {@link #PUBLIC_TOKEN_URL}. */
    public static HttpConnector oauth2Connector(String actionUrl) {
        return connector(config(CLIENT_AUTH_METHOD_CLIENT_SECRET_POST), actionUrl);
    }

    /**
     * Cluster settings with the settings the token cache follows: plugins.ml_commons.connector.auth_enabled, on, and
     * plugins.ml_commons.trusted_oauth2_token_endpoints_regex, trusting {@link #PUBLIC_TOKEN_URL}.
     */
    public static ClusterSettings clusterSettings() {
        Settings nodeSettings = Settings
            .builder()
            .put(ML_COMMONS_CONNECTOR_AUTH_ENABLED.getKey(), true)
            .putList(ML_COMMONS_TRUSTED_OAUTH2_TOKEN_ENDPOINTS_REGEX.getKey(), PUBLIC_TOKEN_URL_PATTERN)
            .build();
        return new ClusterSettings(
            nodeSettings,
            Set.of(ML_COMMONS_CONNECTOR_AUTH_ENABLED, ML_COMMONS_TRUSTED_OAUTH2_TOKEN_ENDPOINTS_REGEX)
        );
    }

    /** Feature settings whose connector auth flag and trusted token endpoints are those of {@code clusterSettings}. */
    public static MLFeatureEnabledSetting featureSettings(ClusterSettings clusterSettings) {
        MLFeatureEnabledSetting settings = mock(MLFeatureEnabledSetting.class);
        when(settings.isConnectorAuthEnabled()).thenAnswer(invocation -> clusterSettings.get(ML_COMMONS_CONNECTOR_AUTH_ENABLED));
        when(settings.getTrustedOAuth2TokenEndpoints())
            .thenAnswer(invocation -> new TokenEndpointAllowlist(clusterSettings.get(ML_COMMONS_TRUSTED_OAUTH2_TOKEN_ENDPOINTS_REGEX)));
        return settings;
    }

    public static TokenEndpointAllowlist allowAll() {
        return new TokenEndpointAllowlist(List.of(".*"));
    }

    public static TokenEndpointAllowlist allowNone() {
        return new TokenEndpointAllowlist(List.of());
    }

    /** What a scripted transport answers to one call. */
    @FunctionalInterface
    public interface Reply {
        Response answer(Request request) throws IOException;
    }

    public static Reply reply(int status, byte[] body) {
        return request -> new Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(status)
            .message("scripted")
            .body(ResponseBody.create(body, MediaType.get("application/json")))
            .build();
    }

    public static Reply reply(int status, String body) {
        return reply(status, body.getBytes(StandardCharsets.UTF_8));
    }

    public static Reply fail(IOException e) {
        return request -> { throw e; };
    }

    /**
     * A transport whose calls never leave the JVM: an application interceptor adds each request to {@code recorded} and
     * answers it with the next of {@code replies}.
     */
    public static OkHttpClient scriptedTransport(List<Request> recorded, Reply... replies) {
        Queue<Reply> queue = new ArrayDeque<>(List.of(replies));
        return new OkHttpClient.Builder().addInterceptor(chain -> {
            recorded.add(chain.request());
            Reply next;
            synchronized (queue) {
                next = queue.remove();
            }
            return next.answer(chain.request());
        }).build();
    }

    /**
     * A token client over {@code transport} whose address checks allow private addresses, refuse port 9200 and read this
     * host's interface addresses; {@code authEnabled} stands for the connector auth flag.
     */
    public static OAuth2TokenClient tokenClient(OkHttpClient transport, ThreadPool threadPool, BooleanSupplier authEnabled) {
        TokenEndpointAddressValidator addresses = new TokenEndpointAddressValidator(
            () -> new ConnectorAddressValidator(true, null, null),
            Set.of(9200)
        );
        return new OAuth2TokenClient(transport, addresses, threadPool, authEnabled, () -> 3600);
    }

    /** {@link #tokenClient(OkHttpClient, ThreadPool, BooleanSupplier)} with the flag on. */
    public static OAuth2TokenClient tokenClient(OkHttpClient transport, ThreadPool threadPool) {
        return tokenClient(transport, threadPool, () -> true);
    }

    /** A thread pool whose {@code opensearch_ml_predict_remote} pool has one thread and a queue of one. */
    public static ThreadPool testThreadPool() {
        return new TestThreadPool(
            "oauth2-tests",
            new FixedExecutorBuilder(Settings.EMPTY, RETRY_EXECUTOR, 1, 1, "thread_pool.ml_commons." + RETRY_EXECUTOR)
        );
    }

    /** The lines {@code logger} writes while {@code action} runs, each as its level, a space and the formatted message. */
    public static List<String> logged(Class<?> logger, CheckedRunnable<Exception> action) throws Exception {
        List<String> lines = new CopyOnWriteArrayList<>();
        try (MockLogAppender appender = MockLogAppender.createForLoggers(LogManager.getLogger(logger))) {
            appender.addExpectation(new MockLogAppender.LoggingExpectation() {
                @Override
                public void match(LogEvent event) {
                    lines.add(event.getLevel() + " " + event.getMessage().getFormattedMessage());
                }

                @Override
                public void assertMatched() {}
            });
            action.run();
        }
        return lines;
    }

    /** {@code line} without its latency, which varies, for comparing whole lines. */
    public static String withoutLatency(String line) {
        return LATENCY.matcher(line).replaceAll("latency_ms=?");
    }

    private static final Pattern LATENCY = Pattern.compile("latency_ms=\\d+");
}
