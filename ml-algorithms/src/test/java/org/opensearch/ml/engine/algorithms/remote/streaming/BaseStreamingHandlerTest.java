package org.opensearch.ml.engine.algorithms.remote.streaming;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.opensearch.ml.engine.algorithms.agent.AgentUtils.LLM_INTERFACE_OPENAI_V1_CHAT_COMPLETIONS;

import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.ml.common.connector.Connector;
import org.opensearch.ml.common.connector.ConnectorClientConfig;
import org.opensearch.ml.common.output.model.ModelTensor;
import org.opensearch.ml.common.output.model.ModelTensorOutput;
import org.opensearch.ml.common.output.model.ModelTensors;
import org.opensearch.ml.common.transport.MLTaskResponse;

public class BaseStreamingHandlerTest {

    @Mock
    private StreamPredictActionListener<MLTaskResponse, ?> mockActionListener;

    @Mock
    private Connector mockConnector;

    private BaseStreamingHandler streamingHandler;
    private ConnectorClientConfig connectorClientConfig;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        connectorClientConfig = new ConnectorClientConfig();

        streamingHandler = new HttpStreamingHandler(LLM_INTERFACE_OPENAI_V1_CHAT_COMPLETIONS, mockConnector, connectorClientConfig);

    }

    @Test
    public void testSendContentResponse() {
        String content = "test content";
        boolean isLast = false;

        streamingHandler.sendContentResponse(content, isLast, mockActionListener);

        ArgumentCaptor<MLTaskResponse> responseCaptor = ArgumentCaptor.forClass(MLTaskResponse.class);
        ArgumentCaptor<Boolean> isLastCaptor = ArgumentCaptor.forClass(Boolean.class);

        verify(mockActionListener).onStreamResponse(responseCaptor.capture(), isLastCaptor.capture());

        MLTaskResponse response = responseCaptor.getValue();
        ModelTensorOutput output = (ModelTensorOutput) response.getOutput();
        ModelTensors tensors = output.getMlModelOutputs().get(0);
        ModelTensor tensor = tensors.getMlModelTensors().get(0);

        assertEquals("response", tensor.getName());
        assertEquals(content, tensor.getDataAsMap().get("content"));
        assertEquals(isLast, tensor.getDataAsMap().get("is_last"));
        assertEquals(isLast, isLastCaptor.getValue());
    }

    @Test
    public void testSendContentResponseWithLastFlag() {
        String content = "final content";
        boolean isLast = true;

        streamingHandler.sendContentResponse(content, isLast, mockActionListener);

        ArgumentCaptor<MLTaskResponse> responseCaptor = ArgumentCaptor.forClass(MLTaskResponse.class);
        ArgumentCaptor<Boolean> isLastCaptor = ArgumentCaptor.forClass(Boolean.class);

        verify(mockActionListener).onStreamResponse(responseCaptor.capture(), isLastCaptor.capture());

        assertTrue(isLastCaptor.getValue());
    }

    @Test
    public void testSendCompletionResponseAlreadyClosed() {
        AtomicBoolean isStreamClosed = new AtomicBoolean(true);
        streamingHandler.sendCompletionResponse(isStreamClosed, mockActionListener);
        verify(mockActionListener, never()).onStreamResponse(any(), anyBoolean());
    }
}
