package org.opensearch.ml.engine.algorithms.remote;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.ml.common.connector.AwsConnector;
import org.opensearch.ml.common.connector.ConnectorClientConfig;
import org.opensearch.ml.common.output.model.ModelTensor;
import org.opensearch.ml.common.output.model.ModelTensorOutput;
import org.opensearch.ml.common.output.model.ModelTensors;
import org.opensearch.ml.common.transport.MLTaskResponse;
import org.opensearch.ml.engine.algorithms.remote.streaming.StreamPredictActionListener;

public class AbstractConnectorExecutorTest {
    @Mock
    private AwsConnector mockConnector;

    @Mock
    private StreamPredictActionListener<MLTaskResponse, ?> mockActionListener;

    private ConnectorClientConfig connectorClientConfig;

    private AbstractConnectorExecutor executor;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        executor = new AwsConnectorExecutor(mockConnector);
        connectorClientConfig = new ConnectorClientConfig();
    }

    @Test
    public void testValidateWithNullConfig() {
        when(mockConnector.getConnectorClientConfig()).thenReturn(null);
        executor.initialize(mockConnector);
        assertEquals(ConnectorClientConfig.MAX_CONNECTION_DEFAULT_VALUE, executor.getConnectorClientConfig().getMaxConnections());
        assertEquals(ConnectorClientConfig.CONNECTION_TIMEOUT_DEFAULT_VALUE, executor.getConnectorClientConfig().getConnectionTimeout());
        assertEquals(ConnectorClientConfig.READ_TIMEOUT_DEFAULT_VALUE, executor.getConnectorClientConfig().getReadTimeout());
    }

    @Test
    public void testValidateWithNonNullConfigButNullValues() {
        when(mockConnector.getConnectorClientConfig()).thenReturn(connectorClientConfig);
        executor.initialize(mockConnector);
        assertEquals(ConnectorClientConfig.MAX_CONNECTION_DEFAULT_VALUE, executor.getConnectorClientConfig().getMaxConnections());
        assertEquals(ConnectorClientConfig.CONNECTION_TIMEOUT_DEFAULT_VALUE, executor.getConnectorClientConfig().getConnectionTimeout());
        assertEquals(ConnectorClientConfig.READ_TIMEOUT_DEFAULT_VALUE, executor.getConnectorClientConfig().getReadTimeout());
    }

    @Test
    public void testSendContentResponse() {
        String content = "test content";
        boolean isLast = false;

        executor.sendContentResponse(content, isLast, mockActionListener);

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

        executor.sendContentResponse(content, isLast, mockActionListener);

        ArgumentCaptor<MLTaskResponse> responseCaptor = ArgumentCaptor.forClass(MLTaskResponse.class);
        ArgumentCaptor<Boolean> isLastCaptor = ArgumentCaptor.forClass(Boolean.class);

        verify(mockActionListener).onStreamResponse(responseCaptor.capture(), isLastCaptor.capture());

        assertTrue(isLastCaptor.getValue());
    }

    @Test
    public void testSendCompletionResponseAlreadyClosed() {
        AtomicBoolean isStreamClosed = new AtomicBoolean(true);
        executor.sendCompletionResponse(isStreamClosed, mockActionListener);
        verify(mockActionListener, never()).onStreamResponse(any(), anyBoolean());
    }
}
