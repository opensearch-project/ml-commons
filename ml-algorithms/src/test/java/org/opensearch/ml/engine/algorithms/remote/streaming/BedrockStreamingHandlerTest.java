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

import java.util.HashMap;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.ml.common.connector.AwsConnector;
import org.opensearch.ml.common.exception.MLException;
import org.opensearch.ml.common.transport.MLTaskResponse;

import software.amazon.awssdk.http.async.SdkAsyncHttpClient;

public class BedrockStreamingHandlerTest {

    @Mock
    private SdkAsyncHttpClient httpClient;
    @Mock
    private AwsConnector connector;
    @Mock
    private StreamPredictActionListener<MLTaskResponse, ?> actionListener;

    private BedrockStreamingHandler bedrockStreamingHandler;

    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);

        when(connector.getRegion()).thenReturn("us-east-1");
        when(connector.getAccessKey()).thenReturn("test-access-key");
        when(connector.getSecretKey()).thenReturn("test-secret-key");

        bedrockStreamingHandler = new BedrockStreamingHandler(httpClient, connector);
    }

    @Test
    public void testConstructor() {
        assertNotNull(bedrockStreamingHandler);
    }

    @Test
    public void testHandleError() {
        Exception testException = new RuntimeException("Test error");

        doAnswer(invocation -> {
            MLException exception = invocation.getArgument(0);
            assertNotNull(exception);
            return null;
        }).when(actionListener).onFailure(any(MLException.class));

        bedrockStreamingHandler.handleError(testException, actionListener);
        verify(actionListener).onFailure(any(MLException.class));
    }

    @Test
    public void testStartStreamInvalidPayload() {
        Map<String, String> parameters = new HashMap<>();
        parameters.put("model", "test-model");
        parameters.put("agent_type", "test");

        String invalidPayload = "invalid json";

        bedrockStreamingHandler.startStream("test_action", parameters, invalidPayload, actionListener);

        verify(actionListener).onFailure(any(MLException.class));
    }
}
