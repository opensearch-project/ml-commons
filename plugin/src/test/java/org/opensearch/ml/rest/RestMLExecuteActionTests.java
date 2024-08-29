/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.opensearch.ml.utils.TestHelper.getExecuteAgentRestRequest;
import static org.opensearch.ml.utils.TestHelper.getLocalSampleCalculatorRestRequest;
import static org.opensearch.ml.utils.TestHelper.getMetricsCorrelationRestRequest;

import java.io.IOException;
import java.util.List;

import org.junit.Before;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.client.node.NodeClient;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.xcontent.XContentFactory;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.common.Strings;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.input.Input;
import org.opensearch.ml.common.transport.execute.MLExecuteTaskAction;
import org.opensearch.ml.common.transport.execute.MLExecuteTaskRequest;
import org.opensearch.ml.common.transport.execute.MLExecuteTaskResponse;
import org.opensearch.ml.settings.MLFeatureEnabledSetting;
import org.opensearch.rest.BytesRestResponse;
import org.opensearch.rest.RestChannel;
import org.opensearch.rest.RestHandler;
import org.opensearch.rest.RestRequest;
import org.opensearch.rest.RestResponse;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.threadpool.TestThreadPool;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.RemoteTransportException;

public class RestMLExecuteActionTests extends OpenSearchTestCase {

    private RestMLExecuteAction restMLExecuteAction;

    NodeClient client;
    private ThreadPool threadPool;

    @Mock
    RestChannel channel;

    @Mock
    MLFeatureEnabledSetting mlFeatureEnabledSetting;

    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);
        when(mlFeatureEnabledSetting.isAgentFrameworkEnabled()).thenReturn(true);
        restMLExecuteAction = new RestMLExecuteAction(mlFeatureEnabledSetting);

        threadPool = new TestThreadPool(this.getClass().getSimpleName() + "ThreadPool");
        client = spy(new NodeClient(Settings.EMPTY, threadPool));

        doAnswer(invocation -> {
            ActionListener<MLExecuteTaskResponse> actionListener = invocation.getArgument(2);
            return null;
        }).when(client).execute(eq(MLExecuteTaskAction.INSTANCE), any(), any());
    }

    @Override
    public void tearDown() throws Exception {
        super.tearDown();
        threadPool.shutdown();
        client.close();
    }

    public void testConstructor() {
        RestMLExecuteAction restMLExecuteAction = new RestMLExecuteAction(mlFeatureEnabledSetting);
        assertNotNull(restMLExecuteAction);
    }

    public void testGetName() {
        String actionName = restMLExecuteAction.getName();
        assertFalse(Strings.isNullOrEmpty(actionName));
        assertEquals("ml_execute_action", actionName);
    }

    public void testRoutes() {
        List<RestHandler.Route> routes = restMLExecuteAction.routes();
        assertNotNull(routes);
        assertFalse(routes.isEmpty());
        RestHandler.Route route = routes.get(0);
        assertEquals(RestRequest.Method.POST, route.getMethod());
        assertEquals("/_plugins/_ml/_execute/{algorithm}", route.getPath());
    }

    public void testGetRequest() throws IOException {
        RestRequest request = getLocalSampleCalculatorRestRequest();
        MLExecuteTaskRequest executeTaskRequest = restMLExecuteAction.getRequest(request);

        Input input = executeTaskRequest.getInput();
        assertNotNull(input);
        assertEquals(FunctionName.LOCAL_SAMPLE_CALCULATOR, input.getFunctionName());
    }

    public void testGetRequestMCorr() throws IOException {
        RestRequest request = getMetricsCorrelationRestRequest();
        MLExecuteTaskRequest executeTaskRequest = restMLExecuteAction.getRequest(request);

        Input input = executeTaskRequest.getInput();
        assertNotNull(input);
        assertEquals(FunctionName.METRICS_CORRELATION, input.getFunctionName());
    }

    public void testGetRequestAgent() throws IOException {
        RestRequest request = getExecuteAgentRestRequest();
        MLExecuteTaskRequest executeTaskRequest = restMLExecuteAction.getRequest(request);

        Input input = executeTaskRequest.getInput();
        assertNotNull(input);
        assertEquals(FunctionName.AGENT, input.getFunctionName());
    }

    public void testPrepareRequest() throws Exception {
        RestRequest request = getLocalSampleCalculatorRestRequest();
        restMLExecuteAction.handleRequest(request, channel, client);

        ArgumentCaptor<MLExecuteTaskRequest> argumentCaptor = ArgumentCaptor.forClass(MLExecuteTaskRequest.class);
        verify(client, times(1)).execute(eq(MLExecuteTaskAction.INSTANCE), argumentCaptor.capture(), any());
        Input input = argumentCaptor.getValue().getInput();
        assertEquals(FunctionName.LOCAL_SAMPLE_CALCULATOR, input.getFunctionName());
    }

    public void testPrepareRequest1() throws Exception {
        doNothing().when(channel).sendResponse(isA(RestResponse.class));
        doAnswer(invocation -> {
            ActionListener<MLExecuteTaskResponse> actionListener = invocation.getArgument(2);
            actionListener.onResponse(new MLExecuteTaskResponse(FunctionName.LOCAL_SAMPLE_CALCULATOR, null));
            return null;
        }).when(client).execute(eq(MLExecuteTaskAction.INSTANCE), any(), any());
        RestRequest request = getLocalSampleCalculatorRestRequest();
        restMLExecuteAction.handleRequest(request, channel, client);

        ArgumentCaptor<MLExecuteTaskRequest> argumentCaptor = ArgumentCaptor.forClass(MLExecuteTaskRequest.class);
        verify(client, times(1)).execute(eq(MLExecuteTaskAction.INSTANCE), argumentCaptor.capture(), any());
        Input input = argumentCaptor.getValue().getInput();
        assertEquals(FunctionName.LOCAL_SAMPLE_CALCULATOR, input.getFunctionName());
    }

    public void testPrepareRequest2() throws Exception {
        doThrow(new IllegalArgumentException("input error")).when(channel).sendResponse(isA(RestResponse.class));
        doNothing().when(channel).sendResponse(isA(BytesRestResponse.class));
        doAnswer(invocation -> {
            ActionListener<MLExecuteTaskResponse> actionListener = invocation.getArgument(2);
            actionListener.onResponse(new MLExecuteTaskResponse(FunctionName.LOCAL_SAMPLE_CALCULATOR, null));
            return null;
        }).when(client).execute(eq(MLExecuteTaskAction.INSTANCE), any(), any());
        RestRequest request = getLocalSampleCalculatorRestRequest();
        restMLExecuteAction.handleRequest(request, channel, client);

        ArgumentCaptor<MLExecuteTaskRequest> argumentCaptor = ArgumentCaptor.forClass(MLExecuteTaskRequest.class);
        verify(client, times(1)).execute(eq(MLExecuteTaskAction.INSTANCE), argumentCaptor.capture(), any());
        Input input = argumentCaptor.getValue().getInput();
        assertEquals(FunctionName.LOCAL_SAMPLE_CALCULATOR, input.getFunctionName());
    }

    public void testPrepareRequestClientError() throws Exception {
        doAnswer(invocation -> {
            ActionListener<MLExecuteTaskResponse> actionListener = invocation.getArgument(2);
            actionListener.onFailure(new IllegalArgumentException("input error"));
            return null;
        }).when(client).execute(eq(MLExecuteTaskAction.INSTANCE), any(), any());
        RestRequest request = getLocalSampleCalculatorRestRequest();
        restMLExecuteAction.handleRequest(request, channel, client);

        ArgumentCaptor<MLExecuteTaskRequest> argumentCaptor = ArgumentCaptor.forClass(MLExecuteTaskRequest.class);
        verify(client, times(1)).execute(eq(MLExecuteTaskAction.INSTANCE), argumentCaptor.capture(), any());
        Input input = argumentCaptor.getValue().getInput();
        assertEquals(FunctionName.LOCAL_SAMPLE_CALCULATOR, input.getFunctionName());
    }

    public void testPrepareRequestSystemError() throws Exception {
        doAnswer(invocation -> {
            ActionListener<MLExecuteTaskResponse> actionListener = invocation.getArgument(2);
            actionListener.onFailure(new RuntimeException("system error"));
            return null;
        }).when(client).execute(eq(MLExecuteTaskAction.INSTANCE), any(), any());
        RestRequest request = getLocalSampleCalculatorRestRequest();
        restMLExecuteAction.handleRequest(request, channel, client);

        ArgumentCaptor<MLExecuteTaskRequest> argumentCaptor = ArgumentCaptor.forClass(MLExecuteTaskRequest.class);
        verify(client, times(1)).execute(eq(MLExecuteTaskAction.INSTANCE), argumentCaptor.capture(), any());
        Input input = argumentCaptor.getValue().getInput();
        assertEquals(FunctionName.LOCAL_SAMPLE_CALCULATOR, input.getFunctionName());
    }

    public void testPrepareRequest_disabled() {
        RestRequest request = getExecuteAgentRestRequest();

        when(mlFeatureEnabledSetting.isAgentFrameworkEnabled()).thenReturn(false);
        assertThrows(IllegalStateException.class, () -> restMLExecuteAction.handleRequest(request, channel, client));
    }

    public void testPrepareRequestClientException() throws Exception {
        doAnswer(invocation -> {
            ActionListener<MLExecuteTaskResponse> actionListener = invocation.getArgument(2);
            actionListener.onFailure(new IllegalArgumentException("Illegal Argument Exception"));
            return null;
        }).when(client).execute(eq(MLExecuteTaskAction.INSTANCE), any(), any());
        doNothing().when(channel).sendResponse(any());
        RestRequest request = getLocalSampleCalculatorRestRequest();
        restMLExecuteAction.handleRequest(request, channel, client);

        ArgumentCaptor<MLExecuteTaskRequest> argumentCaptor = ArgumentCaptor.forClass(MLExecuteTaskRequest.class);
        verify(client, times(1)).execute(eq(MLExecuteTaskAction.INSTANCE), argumentCaptor.capture(), any());
        Input input = argumentCaptor.getValue().getInput();
        assertEquals(FunctionName.LOCAL_SAMPLE_CALCULATOR, input.getFunctionName());
        ArgumentCaptor<RestResponse> restResponseArgumentCaptor = ArgumentCaptor.forClass(RestResponse.class);
        verify(channel, times(1)).sendResponse(restResponseArgumentCaptor.capture());
        BytesRestResponse response = (BytesRestResponse) restResponseArgumentCaptor.getValue();
        assertEquals(RestStatus.BAD_REQUEST, response.status());
        String content = response.content().utf8ToString();
        String expectedError =
            "{\"error\":{\"reason\":\"Invalid Request\",\"details\":\"Illegal Argument Exception\",\"type\":\"IllegalArgumentException\"},\"status\":400}";
        assertEquals(expectedError, response.content().utf8ToString());
    }

    public void testPrepareRequestClientWrappedException() throws Exception {
        doAnswer(invocation -> {
            ActionListener<MLExecuteTaskResponse> actionListener = invocation.getArgument(2);
            actionListener
                .onFailure(
                    new RemoteTransportException("Remote Transport Exception", new IllegalArgumentException("Illegal Argument Exception"))
                );
            return null;
        }).when(client).execute(eq(MLExecuteTaskAction.INSTANCE), any(), any());
        doNothing().when(channel).sendResponse(any());
        RestRequest request = getLocalSampleCalculatorRestRequest();
        restMLExecuteAction.handleRequest(request, channel, client);

        ArgumentCaptor<MLExecuteTaskRequest> argumentCaptor = ArgumentCaptor.forClass(MLExecuteTaskRequest.class);
        verify(client, times(1)).execute(eq(MLExecuteTaskAction.INSTANCE), argumentCaptor.capture(), any());
        Input input = argumentCaptor.getValue().getInput();
        assertEquals(FunctionName.LOCAL_SAMPLE_CALCULATOR, input.getFunctionName());
        ArgumentCaptor<RestResponse> restResponseArgumentCaptor = ArgumentCaptor.forClass(RestResponse.class);
        verify(channel, times(1)).sendResponse(restResponseArgumentCaptor.capture());
        BytesRestResponse response = (BytesRestResponse) restResponseArgumentCaptor.getValue();
        assertEquals(RestStatus.BAD_REQUEST, response.status());
        String expectedError =
            "{\"error\":{\"reason\":\"Invalid Request\",\"details\":\"Illegal Argument Exception\",\"type\":\"IllegalArgumentException\"},\"status\":400}";
        assertEquals(expectedError, response.content().utf8ToString());
    }

    public void testPrepareRequestSystemException() throws Exception {
        doAnswer(invocation -> {
            ActionListener<MLExecuteTaskResponse> actionListener = invocation.getArgument(2);
            actionListener.onFailure(new RuntimeException("System Exception"));
            return null;
        }).when(client).execute(eq(MLExecuteTaskAction.INSTANCE), any(), any());
        doNothing().when(channel).sendResponse(any());
        RestRequest request = getLocalSampleCalculatorRestRequest();
        restMLExecuteAction.handleRequest(request, channel, client);

        ArgumentCaptor<MLExecuteTaskRequest> argumentCaptor = ArgumentCaptor.forClass(MLExecuteTaskRequest.class);
        verify(client, times(1)).execute(eq(MLExecuteTaskAction.INSTANCE), argumentCaptor.capture(), any());
        Input input = argumentCaptor.getValue().getInput();
        assertEquals(FunctionName.LOCAL_SAMPLE_CALCULATOR, input.getFunctionName());
        ArgumentCaptor<RestResponse> restResponseArgumentCaptor = ArgumentCaptor.forClass(RestResponse.class);
        verify(channel, times(1)).sendResponse(restResponseArgumentCaptor.capture());
        BytesRestResponse response = (BytesRestResponse) restResponseArgumentCaptor.getValue();
        assertEquals(RestStatus.INTERNAL_SERVER_ERROR, response.status());
        String expectedError =
            "{\"error\":{\"reason\":\"System Error\",\"details\":\"System Exception\",\"type\":\"RuntimeException\"},\"status\":500}";
        assertEquals(expectedError, response.content().utf8ToString());
    }

    public void testAgentExecutionResponseXContent() throws Exception {
        RestRequest request = getExecuteAgentRestRequest();
        doAnswer(invocation -> {
            ActionListener<MLExecuteTaskResponse> actionListener = invocation.getArgument(2);
            actionListener
                .onFailure(
                    new RemoteTransportException("Remote Transport Exception", new IllegalArgumentException("Illegal Argument Exception"))
                );
            return null;
        }).when(client).execute(eq(MLExecuteTaskAction.INSTANCE), any(), any());
        doNothing().when(channel).sendResponse(any());
        when(channel.newBuilder()).thenReturn(XContentFactory.jsonBuilder());
        restMLExecuteAction.handleRequest(request, channel, client);

        ArgumentCaptor<MLExecuteTaskRequest> argumentCaptor = ArgumentCaptor.forClass(MLExecuteTaskRequest.class);
        verify(client, times(1)).execute(eq(MLExecuteTaskAction.INSTANCE), argumentCaptor.capture(), any());
        Input input = argumentCaptor.getValue().getInput();
        assertEquals(FunctionName.AGENT, input.getFunctionName());
        ArgumentCaptor<RestResponse> restResponseArgumentCaptor = ArgumentCaptor.forClass(RestResponse.class);
        verify(channel, times(1)).sendResponse(restResponseArgumentCaptor.capture());
        BytesRestResponse response = (BytesRestResponse) restResponseArgumentCaptor.getValue();
        assertEquals(RestStatus.BAD_REQUEST, response.status());
        assertEquals("application/json; charset=UTF-8", response.contentType());
        String expectedError =
            "{\"status\":400,\"error\":{\"type\":\"IllegalArgumentException\",\"reason\":\"Invalid Request\",\"details\":\"Illegal Argument Exception\"}}";
        assertEquals(expectedError, response.content().utf8ToString());
    }

    public void testAgentExecutionResponsePlainText() throws Exception {
        RestRequest request = getExecuteAgentRestRequest();
        doAnswer(invocation -> {
            ActionListener<MLExecuteTaskResponse> actionListener = invocation.getArgument(2);
            actionListener
                .onFailure(
                    new RemoteTransportException("Remote Transport Exception", new IllegalArgumentException("Illegal Argument Exception"))
                );
            return null;
        }).when(client).execute(eq(MLExecuteTaskAction.INSTANCE), any(), any());
        doNothing().when(channel).sendResponse(any());
        restMLExecuteAction.handleRequest(request, channel, client);

        ArgumentCaptor<MLExecuteTaskRequest> argumentCaptor = ArgumentCaptor.forClass(MLExecuteTaskRequest.class);
        verify(client, times(1)).execute(eq(MLExecuteTaskAction.INSTANCE), argumentCaptor.capture(), any());
        Input input = argumentCaptor.getValue().getInput();
        assertEquals(FunctionName.AGENT, input.getFunctionName());
        ArgumentCaptor<RestResponse> restResponseArgumentCaptor = ArgumentCaptor.forClass(RestResponse.class);
        verify(channel, times(1)).sendResponse(restResponseArgumentCaptor.capture());
        BytesRestResponse response = (BytesRestResponse) restResponseArgumentCaptor.getValue();
        assertEquals(RestStatus.BAD_REQUEST, response.status());
        assertEquals("text/plain; charset=UTF-8", response.contentType());
        String expectedError =
            "{\"error\":{\"reason\":\"Invalid Request\",\"details\":\"Illegal Argument Exception\",\"type\":\"IllegalArgumentException\"},\"status\":400}";
        assertEquals(expectedError, response.content().utf8ToString());
    }
}
