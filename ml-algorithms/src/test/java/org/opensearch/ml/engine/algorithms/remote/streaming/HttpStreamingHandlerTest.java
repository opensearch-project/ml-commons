/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.remote.streaming;

import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.opensearch.ml.engine.algorithms.agent.AgentUtils.LLM_INTERFACE_OPENAI_V1_CHAT_COMPLETIONS;

import java.util.HashMap;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.opensearch.ml.common.connector.Connector;
import org.opensearch.ml.common.connector.ConnectorClientConfig;
import org.opensearch.ml.common.transport.MLTaskResponse;

import okhttp3.sse.EventSource;

@SuppressWarnings({ "rawtypes", "unchecked" })
public class HttpStreamingHandlerTest {

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
}
