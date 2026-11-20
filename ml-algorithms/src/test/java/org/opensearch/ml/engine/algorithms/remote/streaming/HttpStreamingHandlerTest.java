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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.opensearch.ml.engine.algorithms.agent.AgentUtils.LLM_INTERFACE_OPENAI_V1_CHAT_COMPLETIONS;

import java.net.InetAddress;
import java.net.Proxy;
import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.junit.Before;
import org.junit.Test;
import org.opensearch.ml.common.connector.Connector;
import org.opensearch.ml.common.connector.ConnectorClientConfig;
import org.opensearch.ml.common.httpclient.ConnectorAddressValidator;
import org.opensearch.ml.common.transport.MLTaskResponse;
import org.opensearch.ml.engine.MLStaticMockBase;

import okhttp3.OkHttpClient;
import okhttp3.sse.EventSource;

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
            validator
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

        HttpStreamingHandler.ResolvedAddresses first = new HttpStreamingHandler.ResolvedAddresses(new InetAddress[] { a, b });
        HttpStreamingHandler.ResolvedAddresses same = new HttpStreamingHandler.ResolvedAddresses(new InetAddress[] { a, b });
        HttpStreamingHandler.ResolvedAddresses other = new HttpStreamingHandler.ResolvedAddresses(new InetAddress[] { b });

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
}
