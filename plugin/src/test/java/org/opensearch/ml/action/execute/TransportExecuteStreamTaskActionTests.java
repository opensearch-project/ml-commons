/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.execute;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.input.Input;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.transport.execute.MLExecuteStreamTaskAction;
import org.opensearch.ml.common.transport.execute.MLExecuteTaskRequest;
import org.opensearch.ml.common.transport.execute.MLExecuteTaskResponse;
import org.opensearch.ml.task.MLExecuteTaskRunner;
import org.opensearch.tasks.Task;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.StreamTransportService;
import org.opensearch.transport.TransportChannel;
import org.opensearch.transport.TransportException;
import org.opensearch.transport.TransportResponseHandler;
import org.opensearch.transport.TransportService;
import org.opensearch.transport.client.Client;

public class TransportExecuteStreamTaskActionTests extends OpenSearchTestCase {

    @Mock
    private MLExecuteTaskRunner mlExecuteTaskRunner;

    @Mock
    private TransportService transportService;

    @Mock
    private Client client;

    @Mock
    private ClusterService clusterService;

    @Mock
    private ActionListener<MLExecuteTaskResponse> actionListener;

    @Mock
    private ActionFilters actionFilters;

    @Mock
    private MLFeatureEnabledSetting mlFeatureEnabledSetting;

    @Mock
    private StreamTransportService streamTransportService;

    @Mock
    private TransportChannel transportChannel;

    @Mock
    private ThreadPool threadPool;

    private MLExecuteTaskRequest mlExecuteTaskRequest;
    private TransportExecuteStreamTaskAction transportExecuteStreamTaskAction;
    private ThreadContext threadContext;

    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);

        Settings settings = Settings.builder().build();
        threadContext = new ThreadContext(settings);

        when(client.threadPool()).thenReturn(threadPool);
        when(threadPool.getThreadContext()).thenReturn(threadContext);
        when(clusterService.getSettings()).thenReturn(settings);
        when(mlFeatureEnabledSetting.isAgentFrameworkEnabled()).thenReturn(true);

        mlExecuteTaskRequest = MLExecuteTaskRequest.builder().functionName(FunctionName.AGENT).input(mock(Input.class)).build();

        transportExecuteStreamTaskAction = spy(
            new TransportExecuteStreamTaskAction(transportService, actionFilters, mlExecuteTaskRunner, streamTransportService)
        );
    }

    @Test
    public void testGetStreamTransportService() {
        StreamTransportService result = TransportExecuteStreamTaskAction.getStreamTransportService();
        assertNotNull(result);
    }

    @Test
    public void testMessageReceived() {
        Task task = mock(Task.class);
        transportExecuteStreamTaskAction.messageReceived(mlExecuteTaskRequest, transportChannel, task);

        assertEquals(transportChannel, mlExecuteTaskRequest.getStreamingChannel());
        verify(transportService)
            .sendRequest(
                eq(transportService.getLocalNode()),
                eq(MLExecuteStreamTaskAction.NAME),
                eq(mlExecuteTaskRequest),
                any(TransportResponseHandler.class)
            );
    }

    @Test
    public void testMessageReceivedHandlerMethods() throws Exception {
        Task task = mock(Task.class);

        doAnswer(invocation -> {
            TransportResponseHandler<MLExecuteTaskResponse> handler = invocation.getArgument(3);

            // Test read method
            StreamInput streamInput = mock(StreamInput.class);
            MLExecuteTaskResponse response = handler.read(streamInput);
            assertNotNull(response);

            // Test executor method
            assertEquals(ThreadPool.Names.SAME, handler.executor());

            // Test handleResponse method
            MLExecuteTaskResponse mockResponse = mock(MLExecuteTaskResponse.class);
            handler.handleResponse(mockResponse);

            // Test handleException method
            TransportException transportException = new TransportException("test exception");
            handler.handleException(transportException);

            return null;
        }).when(transportService).sendRequest(any(), any(), any(), any(TransportResponseHandler.class));

        transportExecuteStreamTaskAction.messageReceived(mlExecuteTaskRequest, transportChannel, task);

        verify(transportChannel).sendResponse(any(TransportException.class));
    }

    @Test
    public void testMessageReceivedHandlerException() throws Exception {
        Task task = mock(Task.class);
        doThrow(new IOException("channel error")).when(transportChannel).sendResponse(any(Exception.class));

        doAnswer(invocation -> {
            TransportResponseHandler<MLExecuteTaskResponse> handler = invocation.getArgument(3);

            TransportException transportException = new TransportException("test exception");
            handler.handleException(transportException);

            return null;
        }).when(transportService).sendRequest(any(), any(), any(), any(TransportResponseHandler.class));

        transportExecuteStreamTaskAction.messageReceived(mlExecuteTaskRequest, transportChannel, task);
        verify(transportChannel).sendResponse(any(TransportException.class));
    }

    @Test
    public void testDoExecuteWithoutChannel() {
        transportExecuteStreamTaskAction.doExecute(null, mlExecuteTaskRequest, actionListener);

        ArgumentCaptor<Exception> captor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(captor.capture());
        assertTrue(captor.getValue() instanceof UnsupportedOperationException);
        assertEquals("Use doExecute with TransportChannel for streaming requests", captor.getValue().getMessage());
    }

    @Test
    public void testDoExecuteWithChannel() {
        MLExecuteTaskRequest requestWithChannel = MLExecuteTaskRequest
            .builder()
            .functionName(FunctionName.AGENT)
            .input(mock(Input.class))
            .build();
        requestWithChannel.setStreamingChannel(transportChannel);

        Task task = mock(Task.class);
        ActionListener<MLExecuteTaskResponse> listener = mock(ActionListener.class);
        transportExecuteStreamTaskAction.doExecute(task, requestWithChannel, listener);

        verify(mlExecuteTaskRunner)
            .run(eq(FunctionName.AGENT), any(MLExecuteTaskRequest.class), any(StreamTransportService.class), eq(listener));
    }
}
