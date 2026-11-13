/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.remote.oauth2;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;
import static org.opensearch.ml.common.connector.auth.OAuth2AuthConfig.CLIENT_AUTH_METHOD_CLIENT_SECRET_POST;
import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_CONNECTOR_AUTH_DISABLED_MESSAGE;
import static org.opensearch.ml.engine.algorithms.remote.RemoteConnectorExecutor.RETRY_EXECUTOR;
import static org.opensearch.ml.engine.algorithms.remote.oauth2.OAuth2TestFixtures.CLIENT_SECRET;
import static org.opensearch.ml.engine.algorithms.remote.oauth2.OAuth2TestFixtures.PUBLIC_TOKEN_HOST;
import static org.opensearch.ml.engine.algorithms.remote.oauth2.OAuth2TestFixtures.PUBLIC_TOKEN_URL;
import static org.opensearch.ml.engine.algorithms.remote.oauth2.OAuth2TestFixtures.TOKEN_RESPONSE;
import static org.opensearch.ml.engine.algorithms.remote.oauth2.OAuth2TestFixtures.allowAll;
import static org.opensearch.ml.engine.algorithms.remote.oauth2.OAuth2TestFixtures.allowNone;
import static org.opensearch.ml.engine.algorithms.remote.oauth2.OAuth2TestFixtures.clientSecret;
import static org.opensearch.ml.engine.algorithms.remote.oauth2.OAuth2TestFixtures.config;
import static org.opensearch.ml.engine.algorithms.remote.oauth2.OAuth2TestFixtures.fail;
import static org.opensearch.ml.engine.algorithms.remote.oauth2.OAuth2TestFixtures.json;
import static org.opensearch.ml.engine.algorithms.remote.oauth2.OAuth2TestFixtures.logged;
import static org.opensearch.ml.engine.algorithms.remote.oauth2.OAuth2TestFixtures.reply;
import static org.opensearch.ml.engine.algorithms.remote.oauth2.OAuth2TestFixtures.scriptedTransport;
import static org.opensearch.ml.engine.algorithms.remote.oauth2.OAuth2TestFixtures.snapshot;
import static org.opensearch.ml.engine.algorithms.remote.oauth2.OAuth2TestFixtures.testThreadPool;
import static org.opensearch.ml.engine.algorithms.remote.oauth2.OAuth2TestFixtures.tokenClient;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockMakers;
import org.opensearch.OpenSearchStatusException;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.concurrency.OpenSearchRejectedExecutionException;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.ml.common.connector.auth.OAuth2AuthConfig;
import org.opensearch.ml.common.connector.auth.TokenEndpointAllowlist;
import org.opensearch.ml.common.exception.MLException;
import org.opensearch.ml.common.httpclient.ConnectorAddressValidator;
import org.opensearch.ml.common.httpclient.TokenEndpointAddressValidator;
import org.opensearch.threadpool.ThreadPool;

import com.sun.net.httpserver.HttpServer;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class OAuth2TokenClientTests {

    private static final OAuth2AuthConfig POST = config(CLIENT_AUTH_METHOD_CLIENT_SECRET_POST);

    private final List<Request> recorded = new CopyOnWriteArrayList<>();
    private ThreadPool threadPool;

    @Before
    public void setUp() {
        threadPool = testThreadPool();
    }

    @After
    public void tearDown() {
        ThreadPool.terminate(threadPool, 10, TimeUnit.SECONDS);
    }

    private OAuth2TokenClient client(OAuth2TestFixtures.Reply... replies) {
        return tokenClient(scriptedTransport(recorded, replies), threadPool);
    }

    private static CompletableFuture<OAuth2TokenClient.IssuedToken> request(
        OAuth2TokenClient client,
        OAuth2AuthConfig config,
        CredentialSnapshot credentials,
        TokenEndpointAllowlist allowlist
    ) {
        CompletableFuture<OAuth2TokenClient.IssuedToken> result = new CompletableFuture<>();
        client.requestToken(config, credentials, allowlist, ActionListener.wrap(result::complete, result::completeExceptionally));
        return result;
    }

    private static Exception failure(CompletableFuture<?> result) {
        ExecutionException e = assertThrows(ExecutionException.class, () -> result.get(10, TimeUnit.SECONDS));
        assertNull(e.getCause().getCause());
        return (Exception) e.getCause();
    }

    private static String failureMessage(String host, String detail) {
        return String.format(Locale.ROOT, OAuth2TokenClient.FAILURE_MESSAGE, host, detail);
    }

    /** A failed token request and the client's log lines, without their latency. */
    private record Failed(Exception exception, List<String> log) {
    }

    private static Failed failed(
        OAuth2TokenClient client,
        OAuth2AuthConfig config,
        CredentialSnapshot credentials,
        TokenEndpointAllowlist allowlist
    ) throws Exception {
        AtomicReference<Exception> failure = new AtomicReference<>();
        List<String> lines = logged(OAuth2TokenClient.class, () -> failure.set(failure(request(client, config, credentials, allowlist))));
        return new Failed(failure.get(), lines.stream().map(OAuth2TestFixtures::withoutLatency).toList());
    }

    /**
     * {@link #POST} with another token URL, a literal that builds or a credential reference: a URL the configuration
     * refuses reaches the client only when a reference resolves to it.
     */
    private static OAuth2AuthConfig tokenUrl(String tokenUrl) {
        return POST.toBuilder().tokenUrl(tokenUrl).build();
    }

    @Test
    public void requestToken_returnsTheIssuedTokenOnThePredictPool() throws Exception {
        OAuth2TokenClient client = client(reply(200, TOKEN_RESPONSE));
        AtomicReference<String> thread = new AtomicReference<>();
        CompletableFuture<OAuth2TokenClient.IssuedToken> result = new CompletableFuture<>();

        List<String> lines = logged(OAuth2TokenClient.class, () -> {
            client.requestToken(POST, clientSecret(), allowAll(), ActionListener.wrap(token -> {
                thread.set(Thread.currentThread().getName());
                result.complete(token);
            }, result::completeExceptionally));
            result.get(10, TimeUnit.SECONDS);
        });

        OAuth2TokenClient.IssuedToken token = result.get();
        assertArrayEquals("tok-1".getBytes(StandardCharsets.US_ASCII), token.token());
        assertEquals(3599, token.lifetimeSeconds());
        assertEquals(PUBLIC_TOKEN_URL, token.tokenUrl());
        assertEquals(1, recorded.size());
        assertEquals(PUBLIC_TOKEN_URL, recorded.get(0).url().toString());
        assertTrue(thread.get(), thread.get().contains("[" + RETRY_EXECUTOR + "]"));
        assertEquals(
            List.of("INFO OAuth2 token request: host=203.0.113.10 status=200 outcome=ok latency_ms=?"),
            lines.stream().map(OAuth2TestFixtures::withoutLatency).toList()
        );
    }

    @Test
    public void clientFor_connectsDirectlyToTheValidatedAddressesAndFollowsNoRedirect() throws Exception {
        List<String> paths = new CopyOnWriteArrayList<>();
        HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/", exchange -> {
            paths.add(exchange.getRequestURI().getPath());
            exchange.getResponseHeaders().add("Location", "/other");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        server.start();
        int closedPort;
        try (ServerSocket socket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            closedPort = socket.getLocalPort();
        }
        ProxySelector previous = ProxySelector.getDefault();
        try {
            // A client that used the JVM proxy settings would fail to connect.
            ProxySelector.setDefault(ProxySelector.of(new InetSocketAddress(InetAddress.getLoopbackAddress(), closedPort)));
            OAuth2TokenClient client = OAuth2TokenClient
                .create(
                    new TokenEndpointAddressValidator(() -> ConnectorAddressValidator.DEFAULT, Set.of()),
                    threadPool,
                    () -> true,
                    () -> 3600
                );
            // idp.test does not resolve, so reaching the server shows that the client used the given addresses.
            Request request = new Request.Builder().url("http://idp.test:" + server.getAddress().getPort() + "/token").build();
            OkHttpClient connecting = client.clientFor(new InetAddress[] { server.getAddress().getAddress() });

            try (Response response = connecting.newCall(request).execute()) {
                assertEquals(302, response.code());
            }
            assertEquals(List.of("/token"), paths);
        } finally {
            ProxySelector.setDefault(previous);
            server.stop(0);
        }
    }

    @Test
    public void requestToken_refusesBeforeSending() throws Exception {
        OAuth2TokenClient client = client();
        String httpMessage = assertThrows(
            IllegalArgumentException.class,
            () -> OAuth2AuthConfig.canonicalTokenUrl("http://203.0.113.10/token")
        ).getMessage();
        record Case(OAuth2AuthConfig config, CredentialSnapshot credentials, TokenEndpointAllowlist allowlist, String message, String log) {
        }
        List<Case> cases = List
            .of(
                new Case(
                    tokenUrl("https://127.0.0.1/token"),
                    clientSecret(),
                    allowAll(),
                    failureMessage("127.0.0.1", TokenEndpointAddressValidator.REFUSED_ADDRESS_MESSAGE),
                    "host=127.0.0.1 reason=refused_address"
                ),
                new Case(
                    POST,
                    clientSecret(),
                    allowNone(),
                    failureMessage(PUBLIC_TOKEN_HOST, TokenEndpointAllowlist.NOT_ALLOWED_MESSAGE),
                    "host=203.0.113.10 reason=not_allowed"
                ),
                new Case(
                    tokenUrl("${credential.token_url}"),
                    snapshot(Map.of("client_secret", CLIENT_SECRET, "token_url", "http://203.0.113.10/token")),
                    allowAll(),
                    "Failed to obtain OAuth2 token: " + httpMessage,
                    "reason=not_canonical"
                ),
                new Case(
                    tokenUrl("https://203.0.113.10/a/../token"),
                    clientSecret(),
                    allowAll(),
                    String
                        .format(
                            Locale.ROOT,
                            OAuth2TokenClient.FAILURE_MESSAGE_WITHOUT_HOST,
                            "auth_config.token_url is not in canonical form; write it with a path and without dot segments"
                        ),
                    "reason=not_canonical"
                ),
                new Case(
                    tokenUrl("https://203.0.113.10:9200/token"),
                    clientSecret(),
                    allowAll(),
                    failureMessage(PUBLIC_TOKEN_HOST, TokenEndpointAddressValidator.REFUSED_PORT_MESSAGE),
                    "host=203.0.113.10 reason=refused_port"
                ),
                new Case(
                    tokenUrl("https://169.254.169.254/token"),
                    clientSecret(),
                    allowAll(),
                    failureMessage("169.254.169.254", TokenEndpointAddressValidator.ADDRESS_NOT_ALLOWED_MESSAGE),
                    "host=169.254.169.254 reason=address_not_allowed"
                ),
                new Case(
                    tokenUrl("https://unresolvable.invalid/token"),
                    clientSecret(),
                    allowAll(),
                    failureMessage("unresolvable.invalid", "the token endpoint host could not be resolved"),
                    "host=unresolvable.invalid reason=not_resolved"
                ),
                new Case(
                    tokenUrl("${credential.token_url}"),
                    snapshot(Map.of("client_secret", CLIENT_SECRET, "token_url", "https://198.51.100.20/token")),
                    allowNone(),
                    failureMessage("198.51.100.20", TokenEndpointAllowlist.NOT_ALLOWED_MESSAGE),
                    "host=198.51.100.20 reason=not_allowed"
                )
            );

        for (Case refusal : cases) {
            Failed outcome = failed(client, refusal.config(), refusal.credentials(), refusal.allowlist());
            assertSame(refusal.message(), IllegalArgumentException.class, outcome.exception().getClass());
            assertEquals(refusal.message(), outcome.exception().getMessage());
            assertEquals(List.of("WARN OAuth2 token request refused before sending: " + refusal.log()), outcome.log());
        }
        assertTrue(recorded.isEmpty());
    }

    @Test
    public void requestToken_readsTheAuthSettingAgainBeforeSending() throws Exception {
        AtomicBoolean authEnabled = new AtomicBoolean(true);
        OAuth2TokenClient client = tokenClient(scriptedTransport(recorded, reply(200, TOKEN_RESPONSE)), threadPool, authEnabled::get);
        ExecutorService pool = threadPool.executor(RETRY_EXECUTOR);
        CountDownLatch release = new CountDownLatch(1);
        pool.execute(() -> {
            try {
                release.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        AtomicReference<Exception> failure = new AtomicReference<>();

        List<String> lines = logged(OAuth2TokenClient.class, () -> {
            // Queued behind the blocked thread, so the setting changes after requestToken and before send.
            CompletableFuture<OAuth2TokenClient.IssuedToken> result = request(client, POST, clientSecret(), allowAll());
            authEnabled.set(false);
            release.countDown();
            failure.set(failure(result));
        });

        assertSame(OpenSearchStatusException.class, failure.get().getClass());
        assertEquals(RestStatus.FORBIDDEN, ((OpenSearchStatusException) failure.get()).status());
        assertEquals(ML_COMMONS_CONNECTOR_AUTH_DISABLED_MESSAGE, failure.get().getMessage());
        assertEquals(List.of("WARN OAuth2 token request refused before sending: host=203.0.113.10 reason=auth_disabled"), lines);
        assertTrue(recorded.isEmpty());
    }

    @Test
    public void requestToken_sendsToTheTokenUrlAReferenceResolvesTo() throws Exception {
        OAuth2TokenClient client = client(reply(200, TOKEN_RESPONSE));

        OAuth2TokenClient.IssuedToken token = request(
            client,
            tokenUrl("${credential.token_url}"),
            snapshot(Map.of("client_secret", CLIENT_SECRET, "token_url", PUBLIC_TOKEN_URL)),
            allowAll()
        ).get(10, TimeUnit.SECONDS);

        assertEquals(PUBLIC_TOKEN_URL, token.tokenUrl());
        assertEquals(PUBLIC_TOKEN_URL, recorded.get(0).url().toString());
    }

    @Test
    public void requestToken_failsWhenTheInterfaceAddressesCannotBeRead() throws Exception {
        TokenEndpointAddressValidator addresses = mock(TokenEndpointAddressValidator.class, withSettings().mockMaker(MockMakers.INLINE));
        when(addresses.validate(any(), any(), anyInt()))
            .thenThrow(new IllegalStateException(TokenEndpointAddressValidator.INTERFACE_ADDRESSES_UNAVAILABLE_MESSAGE));
        OAuth2TokenClient client = new OAuth2TokenClient(scriptedTransport(recorded), addresses, threadPool, () -> true, () -> 3600);

        Failed outcome = failed(client, POST, clientSecret(), allowAll());

        assertSame(IllegalStateException.class, outcome.exception().getClass());
        assertEquals(
            failureMessage(PUBLIC_TOKEN_HOST, TokenEndpointAddressValidator.INTERFACE_ADDRESSES_UNAVAILABLE_MESSAGE),
            outcome.exception().getMessage()
        );
        assertEquals(
            List.of("WARN OAuth2 token request refused before sending: host=203.0.113.10 reason=interfaces_unavailable"),
            outcome.log()
        );
        assertTrue(recorded.isEmpty());
    }

    @Test
    public void requestToken_reportsTheProviderErrorCodeOnly() throws Exception {
        OAuth2TokenClient client = client(reply(401, json("{'error':'invalid_client','error_description':'MARKER'}")));

        Failed outcome = failed(client, POST, clientSecret(), allowAll());

        assertSame(MLException.class, outcome.exception().getClass());
        assertEquals("Failed to obtain OAuth2 token from 203.0.113.10: invalid_client (HTTP 401)", outcome.exception().getMessage());
        assertEquals(List.of("INFO OAuth2 token request: host=203.0.113.10 status=401 outcome=invalid_client latency_ms=?"), outcome.log());
        assertEquals(1, recorded.size());
    }

    @Test
    public void requestToken_failsOnAnyOtherStatusAndOnAnUnusableBody() throws Exception {
        String atTheLimit = "{\"access_token\":\"tok-1\",\"expires_in\":86400,\"pad\":\"";
        atTheLimit += "a".repeat(OAuth2TokenClient.MAX_RESPONSE_BYTES - atTheLimit.length() - 2) + "\"}";
        assertEquals(OAuth2TokenClient.MAX_RESPONSE_BYTES, atTheLimit.length());
        OAuth2TokenClient client = client(
            reply(503, "<html>"),
            reply(302, ""),
            reply(200, new byte[OAuth2TokenClient.MAX_RESPONSE_BYTES + 1]),
            reply(200, atTheLimit)
        );

        assertEquals(
            failureMessage(
                PUBLIC_TOKEN_HOST,
                String.format(Locale.ROOT, OAuth2TokenClient.IDP_ERROR_DETAIL, TokenResponses.UNKNOWN_ERROR_CODE, 503)
            ),
            failure(request(client, POST, clientSecret(), allowAll())).getMessage()
        );
        assertEquals(
            failureMessage(
                PUBLIC_TOKEN_HOST,
                String.format(Locale.ROOT, OAuth2TokenClient.IDP_ERROR_DETAIL, TokenResponses.UNKNOWN_ERROR_CODE, 302)
            ),
            failure(request(client, POST, clientSecret(), allowAll())).getMessage()
        );
        Failed tooLarge = failed(client, POST, clientSecret(), allowAll());
        assertEquals(
            failureMessage(
                PUBLIC_TOKEN_HOST,
                String.format(Locale.ROOT, TokenResponses.INVALID_RESPONSE_DETAIL, TokenResponses.RESPONSE_TOO_LARGE)
            ),
            tooLarge.exception().getMessage()
        );
        assertEquals(
            List.of("INFO OAuth2 token request: host=203.0.113.10 status=200 outcome=invalid_response latency_ms=?"),
            tooLarge.log()
        );
        // The token client applies the lifetime cap it reads.
        assertEquals(3600, request(client, POST, clientSecret(), allowAll()).get(10, TimeUnit.SECONDS).lifetimeSeconds());
    }

    @Test
    public void requestToken_reportsATransportFailureWithoutItsMessage() throws Exception {
        OAuth2TokenClient client = client(fail(new IOException("MARKER")));

        Failed outcome = failed(client, POST, clientSecret(), allowAll());

        assertSame(MLException.class, outcome.exception().getClass());
        assertEquals(failureMessage(PUBLIC_TOKEN_HOST, "transport_error"), outcome.exception().getMessage());
        assertEquals(
            List.of("INFO OAuth2 token request: host=203.0.113.10 outcome=transport_error latency_ms=? exception=IOException"),
            outcome.log()
        );
    }

    @Test
    public void requestToken_reportsAPoolRejection() throws Exception {
        OAuth2TokenClient client = client(reply(200, TOKEN_RESPONSE));
        ExecutorService pool = threadPool.executor(RETRY_EXECUTOR);
        CountDownLatch release = new CountDownLatch(1);
        pool.execute(() -> {
            try {
                release.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        // fills the queue of one
        pool.execute(() -> {});

        CompletableFuture<OAuth2TokenClient.IssuedToken> result = request(client, POST, clientSecret(), allowAll());
        release.countDown();

        assertTrue(failure(result) instanceof OpenSearchRejectedExecutionException);
        assertTrue(recorded.isEmpty());
    }
}
