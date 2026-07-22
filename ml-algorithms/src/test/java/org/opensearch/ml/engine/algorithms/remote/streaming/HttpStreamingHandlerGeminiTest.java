/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.remote.streaming;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.opensearch.ml.engine.algorithms.agent.AgentUtils.LLM_INTERFACE_GEMINI_V1BETA_GENERATE_CONTENT;

import java.util.HashMap;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.opensearch.ml.common.connector.ConnectorClientConfig;
import org.opensearch.ml.common.output.model.ModelTensorOutput;
import org.opensearch.ml.common.transport.MLTaskResponse;

/**
 * Unit tests for the Gemini (Vertex AI streamGenerateContent) SSE parsing branch of
 * HttpStreamingHandler. The event payloads below are real frames captured from Vertex AI
 * gemini-2.5-flash :streamGenerateContent?alt=sse (with the "data: " SSE prefix stripped,
 * as the event source does before onEvent()).
 */
public class HttpStreamingHandlerGeminiTest {

    // A mid-stream chunk: text present, no finishReason.
    private static final String GEMINI_TEXT_CHUNK =
        "{\"candidates\":[{\"content\":{\"role\":\"model\",\"parts\":[{\"text\":\"Hello world\"}]}}]}";

    // A terminal chunk: text plus finishReason STOP.
    private static final String GEMINI_FINAL_CHUNK =
        "{\"candidates\":[{\"content\":{\"role\":\"model\",\"parts\":[{\"text\":\"1\\n2\\n3\"}]},\"finishReason\":\"STOP\"}],"
            + "\"modelVersion\":\"gemini-2.5-flash\"}";

    private HttpStreamingHandler.HTTPEventSourceListener listener;
    private StreamPredictActionListener<MLTaskResponse, ?> streamListener;

    @Before
    public void setUp() {
        ConnectorClientConfig config = new ConnectorClientConfig();
        HttpStreamingHandler handler = new HttpStreamingHandler(LLM_INTERFACE_GEMINI_V1BETA_GENERATE_CONTENT, null, config);
        streamListener = mock(StreamPredictActionListener.class);
        Map<String, String> parameters = new HashMap<>();
        listener = handler.new HTTPEventSourceListener(streamListener, LLM_INTERFACE_GEMINI_V1BETA_GENERATE_CONTENT, parameters);
    }

    @Test
    public void onEvent_textChunk_emitsContentNotLast() {
        listener.onEvent(null, null, null, GEMINI_TEXT_CHUNK);

        ArgumentCaptor<MLTaskResponse> responseCaptor = ArgumentCaptor.forClass(MLTaskResponse.class);
        ArgumentCaptor<Boolean> isLastCaptor = ArgumentCaptor.forClass(Boolean.class);
        org.mockito.Mockito.verify(streamListener).onStreamResponse(responseCaptor.capture(), isLastCaptor.capture());

        assertFalse(isLastCaptor.getValue());
        Map<String, ?> dataMap = extractDataMap(responseCaptor.getValue());
        assertEquals("Hello world", dataMap.get("content"));
        assertEquals(false, dataMap.get("is_last"));
    }

    @Test
    public void onEvent_finalChunk_emitsContentThenCompletion() {
        listener.onEvent(null, null, null, GEMINI_FINAL_CHUNK);

        ArgumentCaptor<MLTaskResponse> responseCaptor = ArgumentCaptor.forClass(MLTaskResponse.class);
        ArgumentCaptor<Boolean> isLastCaptor = ArgumentCaptor.forClass(Boolean.class);
        // First the content chunk (is_last=false), then the completion sentinel (is_last=true).
        org.mockito.Mockito
            .verify(streamListener, org.mockito.Mockito.times(2))
            .onStreamResponse(responseCaptor.capture(), isLastCaptor.capture());

        assertEquals("1\n2\n3", extractDataMap(responseCaptor.getAllValues().get(0)).get("content"));
        assertFalse(isLastCaptor.getAllValues().get(0));

        assertEquals("", extractDataMap(responseCaptor.getAllValues().get(1)).get("content"));
        assertTrue(isLastCaptor.getAllValues().get(1));
    }

    @Test
    public void onEvent_malformedChunk_isIgnored() {
        listener.onEvent(null, null, null, "not json");
        org.mockito.Mockito.verifyNoInteractions(streamListener);
    }

    @SuppressWarnings("unchecked")
    private Map<String, ?> extractDataMap(MLTaskResponse response) {
        ModelTensorOutput output = (ModelTensorOutput) response.getOutput();
        return output.getMlModelOutputs().get(0).getMlModelTensors().get(0).getDataAsMap();
    }
}
