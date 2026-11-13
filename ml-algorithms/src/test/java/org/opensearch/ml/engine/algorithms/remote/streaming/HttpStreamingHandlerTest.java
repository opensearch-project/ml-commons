/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.remote.streaming;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.opensearch.ml.engine.algorithms.agent.AgentUtils.LLM_INTERFACE_OPENAI_V1_CHAT_COMPLETIONS;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.opensearch.OpenSearchStatusException;
import org.opensearch.ml.common.CommonValue;
import org.opensearch.ml.common.connector.Connector;
import org.opensearch.ml.common.connector.ConnectorClientConfig;
import org.opensearch.ml.common.httpclient.ConnectorAddressValidator;
import org.opensearch.ml.common.transport.MLTaskResponse;
import org.opensearch.ml.engine.MLStaticMockBase;
import org.opensearch.ml.engine.algorithms.remote.ResolvedAddresses;

import com.sun.net.httpserver.HttpServer;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okhttp3.sse.EventSource;
import okio.Buffer;
import okio.BufferedSource;
import okio.Okio;
import okio.Source;
import okio.Timeout;

@SuppressWarnings({ "rawtypes", "unchecked" })
public class HttpStreamingHandlerTest extends MLStaticMockBase {

    private HttpStreamingHandler handler;
    private Connector mockConnector;
    private ConnectorClientConfig mockConfig;

    @Before
    public void setUp() {
        mockConnector = mock(Connector.class);
        mockConfig = mock(ConnectorClientConfig.class);
        when(mockConfig.getConnectionTimeout()).thenReturn(30);
        when(mockConfig.getReadTimeout()).thenReturn(30);

        handler = new HttpStreamingHandler(LLM_INTERFACE_OPENAI_V1_CHAT_COMPLETIONS, mockConnector, mockConfig);
    }

    /**
     * The streaming client carries no mutual-TLS material, so accepting a connector that asked for it would
     * stream without presenting the client certificate. Fail instead of downgrading silently.
     */
    @Test
    public void testConstructor_rejectsMutualTlsEnabledConnector() {
        ConnectorClientConfig mtlsConfig = mock(ConnectorClientConfig.class);
        when(mtlsConfig.getMutualTlsEnabled()).thenReturn(true);

        IllegalArgumentException e = assertThrows(
            IllegalArgumentException.class,
            () -> new HttpStreamingHandler(LLM_INTERFACE_OPENAI_V1_CHAT_COMPLETIONS, mockConnector, mtlsConfig)
        );
        assertTrue(e.getMessage().contains("Mutual TLS is not supported on the streaming path"));
    }

    @Test
    public void testConstructor_allowsMutualTlsOffOrUnset() {
        for (Boolean value : new Boolean[] { null, false }) {
            ConnectorClientConfig config = mock(ConnectorClientConfig.class);
            when(config.getConnectionTimeout()).thenReturn(30);
            when(config.getReadTimeout()).thenReturn(30);
            when(config.getMutualTlsEnabled()).thenReturn(value);
            new HttpStreamingHandler(LLM_INTERFACE_OPENAI_V1_CHAT_COMPLETIONS, mockConnector, config);
        }
    }

    @Test
    public void testFirstTokenReceived_coversLogTimeToFirstToken() {
        StreamPredictActionListener<MLTaskResponse, ?> mockStreamListener = mock(StreamPredictActionListener.class);
        Map<String, String> parameters = new HashMap<>();
        parameters.put("model", "gpt-4");

        HttpStreamingHandler.HTTPEventSourceListener listener = handler.new HTTPEventSourceListener(
            mockStreamListener, LLM_INTERFACE_OPENAI_V1_CHAT_COMPLETIONS, parameters
        );

        // JSON with choices[0].delta.content so that line 310 AgentUtils.logTimeToFirstToken fires
        String jsonData = "{\"choices\":[{\"delta\":{\"content\":\"hello\"},\"finish_reason\":null}]}";
        EventSource mockEventSource = mock(EventSource.class);

        listener.onEvent(mockEventSource, null, null, jsonData);
    }

    @Test
    public void testClient_connectsDirectlyWithoutRedirects() {
        okhttp3.OkHttpClient client = handler.okHttpClient();
        org.junit.Assert.assertEquals(java.net.Proxy.NO_PROXY, client.proxy());
        org.junit.Assert.assertFalse(client.followRedirects());
        org.junit.Assert.assertFalse(client.followSslRedirects());
    }

    @Test
    public void testStartStream_privateDestinationRejectedByDefault() {
        @SuppressWarnings("unchecked")
        StreamPredictActionListener<org.opensearch.ml.common.transport.MLTaskResponse, ?> listener = mock(
            StreamPredictActionListener.class
        );

        handler.startStream(URI.create("http://10.0.0.1/v1/chat/completions"), Map.of(), new HashMap<>(), "{\"messages\":[]}", listener);

        org.mockito.ArgumentCaptor<Exception> failure = org.mockito.ArgumentCaptor.forClass(Exception.class);
        org.mockito.Mockito.verify(listener).onFailure(failure.capture());
        Throwable cause = failure.getValue().getCause();
        assertTrue(String.valueOf(cause), cause instanceof IllegalArgumentException && cause.getMessage().contains("private ip address"));
    }

    @Test
    public void testStartStream_restrictedPatternAppliesWithPrivateIpEnabled() {
        ConnectorAddressValidator validator = new ConnectorAddressValidator(true, null, List.of(Pattern.compile("10\\.0\\.0\\.9")));
        HttpStreamingHandler restricted = new HttpStreamingHandler(
            LLM_INTERFACE_OPENAI_V1_CHAT_COMPLETIONS,
            mockConnector,
            mockConfig,
            null,
            validator,
            null
        );
        @SuppressWarnings("unchecked")
        StreamPredictActionListener<org.opensearch.ml.common.transport.MLTaskResponse, ?> listener = mock(
            StreamPredictActionListener.class
        );

        restricted.startStream(URI.create("http://10.0.0.9/v1/chat/completions"), Map.of(), new HashMap<>(), "{\"messages\":[]}", listener);

        org.mockito.ArgumentCaptor<Exception> failure = org.mockito.ArgumentCaptor.forClass(Exception.class);
        org.mockito.Mockito.verify(listener).onFailure(failure.capture());
        assertTrue(
            String.valueOf(failure.getValue().getCause()),
            failure.getValue().getCause().getMessage().contains("restricted ip address")
        );
    }

    @Test
    public void testClientFor_connectsOnlyToTheValidatedAddresses() throws Exception {
        InetAddress validated = InetAddress.getByName("203.0.113.1");

        OkHttpClient client = handler.clientFor(new InetAddress[] { validated });

        assertEquals(List.of(validated), client.dns().lookup("api.example"));
        // everything else is the handler's client: direct, no redirects, its timeouts, its connection pool
        assertEquals(Proxy.NO_PROXY, client.proxy());
        assertFalse(client.followRedirects());
        assertFalse(client.followSslRedirects());
        assertEquals(30_000, client.connectTimeoutMillis());
        assertEquals(30_000, client.readTimeoutMillis());
        assertSame(handler.okHttpClient().connectionPool(), client.connectionPool());
    }

    @Test
    public void testResolvedAddresses_equalOnlyForTheSameAddresses() throws Exception {
        InetAddress a = InetAddress.getByName("203.0.113.1");
        InetAddress b = InetAddress.getByName("203.0.113.2");

        ResolvedAddresses first = new ResolvedAddresses(new InetAddress[] { a, b });
        ResolvedAddresses same = new ResolvedAddresses(new InetAddress[] { a, b });
        ResolvedAddresses other = new ResolvedAddresses(new InetAddress[] { b });

        assertEquals(first, same);
        assertEquals(first.hashCode(), same.hashCode());
        assertNotEquals(first, other);
    }

    @Test
    public void testStartStream_buildsTheRequestFromTheGivenUriAndHeaders() {
        URI endpoint = URI.create("http://10.0.0.1/v1/chat/completions");
        Map<String, String> headers = Map.of("Authorization", "Bearer token-1");
        String payload = "{\"messages\":[]}";
        @SuppressWarnings("unchecked")
        StreamPredictActionListener<org.opensearch.ml.common.transport.MLTaskResponse, ?> listener = mock(
            StreamPredictActionListener.class
        );
        try (
            org.mockito.MockedStatic<org.opensearch.ml.engine.algorithms.remote.ConnectorUtils> utils = mockStatic(
                org.opensearch.ml.engine.algorithms.remote.ConnectorUtils.class,
                org.mockito.Mockito.withSettings().defaultAnswer(org.mockito.Mockito.CALLS_REAL_METHODS)
            )
        ) {
            handler.startStream(endpoint, headers, new HashMap<>(), payload, listener);

            utils
                .verify(
                    () -> org.opensearch.ml.engine.algorithms.remote.ConnectorUtils.buildOKHttpStreamingRequest(endpoint, headers, payload)
                );
        }
    }

    private static final Map<String, String> BEARER_HEADERS = Map.of("Content-Type", "application/json", "Authorization", "Bearer tok-1");
    private static final String PAYLOAD = "{\"messages\":[]}";

    /** A handler whose 401 callback is {@code onUnauthorized}, spied so that a test can choose its connections. */
    private HttpStreamingHandler bearerHandler(Runnable onUnauthorized, ConnectorAddressValidator addressValidator) {
        return spy(
            new HttpStreamingHandler(
                LLM_INTERFACE_OPENAI_V1_CHAT_COMPLETIONS,
                mockConnector,
                mockConfig,
                null,
                addressValidator,
                onUnauthorized
            )
        );
    }

    /** Starts a bearer stream and returns the failure the listener reports. */
    private static Exception streamFailure(
        HttpStreamingHandler handler,
        URI endpoint,
        StreamPredictActionListener<MLTaskResponse, ?> listener
    ) {
        handler.startStream(endpoint, BEARER_HEADERS, new HashMap<>(), PAYLOAD, listener);
        ArgumentCaptor<Exception> failure = ArgumentCaptor.forClass(Exception.class);
        verify(listener, timeout(10_000)).onFailure(failure.capture());
        return failure.getValue();
    }

    /** A body whose every read fails. */
    private static ResponseBody unreadableBody() {
        return new ResponseBody() {
            @Override
            public MediaType contentType() {
                return null;
            }

            @Override
            public long contentLength() {
                return -1;
            }

            @Override
            public BufferedSource source() {
                return Okio.buffer(new Source() {
                    @Override
                    public long read(Buffer sink, long byteCount) throws IOException {
                        throw new IOException("connection reset");
                    }

                    @Override
                    public Timeout timeout() {
                        return Timeout.NONE;
                    }

                    @Override
                    public void close() {}
                });
            }
        };
    }

    @Test
    public void anErrorResponseKeepsItsStatusAndA401RunsTheCallbackFirst() {
        record Case(int status, ResponseBody body, String message, boolean unauthorized) {
        }
        String error = "{\"error\":\"invalid_token\"}";
        List<Case> cases = List
            .of(
                new Case(
                    401,
                    ResponseBody.create(error, MediaType.get("application/json")),
                    CommonValue.REMOTE_SERVICE_ERROR + error,
                    true
                ),
                new Case(401, unreadableBody(), "SSE failure - unable to read error details", true),
                new Case(
                    500,
                    ResponseBody.create(error, MediaType.get("application/json")),
                    CommonValue.REMOTE_SERVICE_ERROR + error,
                    false
                )
            );
        for (Case c : cases) {
            Runnable callback = mock(Runnable.class);
            HttpStreamingHandler bearer = bearerHandler(callback, ConnectorAddressValidator.DEFAULT);
            List<Request> requests = new CopyOnWriteArrayList<>();
            OkHttpClient answering = bearer.okHttpClient().newBuilder().addInterceptor(chain -> {
                requests.add(chain.request());
                return new Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(c.status())
                    .message("scripted")
                    .body(c.body())
                    .build();
            }).build();
            doReturn(answering).when(bearer).clientFor(any());
            StreamPredictActionListener<MLTaskResponse, ?> listener = mock(StreamPredictActionListener.class);

            OpenSearchStatusException e = (OpenSearchStatusException) streamFailure(
                bearer,
                URI.create("https://203.0.113.10/v1/chat/completions"),
                listener
            );

            assertEquals(c.status(), e.status().getStatus());
            assertEquals(c.message(), e.getMessage());
            assertEquals(1, requests.size());
            assertEquals(List.of("Bearer tok-1"), requests.get(0).headers("Authorization"));
            assertEquals("text/event-stream", requests.get(0).header("Accept"));
            if (c.unauthorized()) {
                InOrder order = inOrder(callback, listener);
                order.verify(callback).run();
                order.verify(listener).onFailure(any());
            } else {
                verify(callback, never()).run();
            }
        }
    }

    @Test
    public void aBearerStreamFollowsNoRedirect() throws Exception {
        List<String> paths = new CopyOnWriteArrayList<>();
        AtomicInteger redirect = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/", exchange -> {
            paths.add(exchange.getRequestURI().getPath());
            exchange.getRequestBody().readAllBytes();
            exchange.getResponseHeaders().add("Location", "/other");
            exchange.sendResponseHeaders(redirect.get(), -1);
            exchange.close();
        });
        server.start();
        try {
            for (int status : new int[] { 302, 307 }) {
                redirect.set(status);
                paths.clear();
                Runnable callback = mock(Runnable.class);
                HttpStreamingHandler bearer = bearerHandler(callback, new ConnectorAddressValidator(true, null, null));
                URI endpoint = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/v1/chat/completions");

                OpenSearchStatusException e = (OpenSearchStatusException) streamFailure(
                    bearer,
                    endpoint,
                    mock(StreamPredictActionListener.class)
                );

                assertEquals(status, e.status().getStatus());
                assertEquals(List.of("/v1/chat/completions"), paths);
                verify(callback, never()).run();
            }
        } finally {
            server.stop(0);
        }
    }
}
