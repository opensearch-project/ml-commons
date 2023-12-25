/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
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
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.common.Strings;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.input.Input;
import org.opensearch.ml.common.transport.execute.MLExecuteTaskAction;
import org.opensearch.ml.common.transport.execute.MLExecuteTaskRequest;
import org.opensearch.ml.common.transport.execute.MLExecuteTaskResponse;
import org.opensearch.rest.RestChannel;
import org.opensearch.rest.RestHandler;
import org.opensearch.rest.RestRequest;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.threadpool.TestThreadPool;
import org.opensearch.threadpool.ThreadPool;

public class RestMLExecuteActionTests extends OpenSearchTestCase {

    private RestMLExecuteAction restMLExecuteAction;

    NodeClient client;
    private ThreadPool threadPool;

    @Mock
    RestChannel channel;

    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);
        restMLExecuteAction = new RestMLExecuteAction();

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
        RestMLExecuteAction restMLExecuteAction = new RestMLExecuteAction();
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
}
