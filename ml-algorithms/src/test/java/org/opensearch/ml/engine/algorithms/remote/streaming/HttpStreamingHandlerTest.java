/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.remote.streaming;

import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.opensearch.ml.engine.algorithms.agent.AgentUtils.LLM_INTERFACE_OPENAI_V1_CHAT_COMPLETIONS;

import java.util.HashMap;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.ml.common.connector.Connector;
import org.opensearch.ml.common.connector.ConnectorClientConfig;
import org.opensearch.ml.common.exception.MLException;
import org.opensearch.ml.common.transport.MLTaskResponse;

public class HttpStreamingHandlerTest {

    @Mock
    private Connector connector;
    @Mock
    private ConnectorClientConfig connectorClientConfig;
    @Mock
    private StreamPredictActionListener<MLTaskResponse, ?> actionListener;

    private HttpStreamingHandler httpStreamingHandler;

    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);

        when(connectorClientConfig.getConnectionTimeout()).thenReturn(30);
        when(connectorClientConfig.getReadTimeout()).thenReturn(30);

        httpStreamingHandler = new HttpStreamingHandler(LLM_INTERFACE_OPENAI_V1_CHAT_COMPLETIONS, connector, connectorClientConfig);
    }

    @Test
    public void testConstructor() {
        assertNotNull(httpStreamingHandler);
    }

    @Test
    public void testHandleError() {
        Exception testException = new RuntimeException("Test error");

        doAnswer(invocation -> {
            MLException exception = invocation.getArgument(0);
            assertNotNull(exception);
            return null;
        }).when(actionListener).onFailure(any(MLException.class));

        httpStreamingHandler.handleError(testException, actionListener);
        verify(actionListener).onFailure(any(MLException.class));
    }

    @Test
    public void testStartStreamWithException() {
        Map<String, String> parameters = new HashMap<>();
        parameters.put("agent_type", "test");

        when(connector.getActions()).thenReturn(null);
        httpStreamingHandler.startStream("test_action", parameters, "test_payload", actionListener);

        verify(actionListener).onFailure(any(MLException.class));
    }

    @Test
    public void testHTTPEventSourceListenerConstructor() {
        HttpStreamingHandler.HTTPEventSourceListener listener = httpStreamingHandler.new HTTPEventSourceListener(
            actionListener, LLM_INTERFACE_OPENAI_V1_CHAT_COMPLETIONS, "test_agent"
        );
        assertNotNull(listener);
    }

    @Test
    public void testHTTPEventSourceListenerOnFailureWithThrowable() {
        HttpStreamingHandler.HTTPEventSourceListener listener = httpStreamingHandler.new HTTPEventSourceListener(
            actionListener, LLM_INTERFACE_OPENAI_V1_CHAT_COMPLETIONS, "test_agent"
        );

        RuntimeException testException = new RuntimeException("Test error");
        listener.onFailure(null, testException, null);

        verify(actionListener).onFailure(any(MLException.class));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testUnsupportedLLMInterface() {
        HttpStreamingHandler.HTTPEventSourceListener listener = httpStreamingHandler.new HTTPEventSourceListener(
            actionListener, "unsupported_interface", "test_agent"
        );
        listener.onEvent(null, null, null, "test data");
    }
}
