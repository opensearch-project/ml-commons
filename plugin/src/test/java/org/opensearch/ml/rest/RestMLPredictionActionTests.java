/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.opensearch.ml.utils.RestActionUtils.PARAMETER_MODEL_ID;
import static org.opensearch.ml.utils.TestHelper.getKMeansRestRequest;
import static org.opensearch.ml.utils.TestHelper.verifyParsedKMeansMLInput;

import java.io.IOException;
import java.util.List;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.rules.ExpectedException;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.opensearch.action.ActionListener;
import org.opensearch.client.node.NodeClient;
import org.opensearch.common.Strings;
import org.opensearch.common.settings.Settings;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.input.MLInput;
import org.opensearch.ml.common.transport.MLTaskResponse;
import org.opensearch.ml.common.transport.prediction.MLPredictionTaskAction;
import org.opensearch.ml.common.transport.prediction.MLPredictionTaskRequest;
import org.opensearch.rest.RestChannel;
import org.opensearch.rest.RestHandler;
import org.opensearch.rest.RestRequest;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.threadpool.TestThreadPool;
import org.opensearch.threadpool.ThreadPool;

public class RestMLPredictionActionTests extends OpenSearchTestCase {
    @Rule
    public ExpectedException thrown = ExpectedException.none();

    private RestMLPredictionAction restMLPredictionAction;

    NodeClient client;
    private ThreadPool threadPool;

    @Mock
    RestChannel channel;

    @Before
    public void setup() {
        restMLPredictionAction = new RestMLPredictionAction();

        threadPool = new TestThreadPool(this.getClass().getSimpleName() + "ThreadPool");
        client = spy(new NodeClient(Settings.EMPTY, threadPool));

        doAnswer(invocation -> {
            ActionListener<MLTaskResponse> actionListener = invocation.getArgument(2);
            return null;
        }).when(client).execute(eq(MLPredictionTaskAction.INSTANCE), any(), any());
    }

    @Override
    public void tearDown() throws Exception {
        super.tearDown();
        threadPool.shutdown();
        client.close();
    }

    public void testConstructor() {
        RestMLPredictionAction mlPredictionAction = new RestMLPredictionAction();
        assertNotNull(mlPredictionAction);
    }

    public void testGetName() {
        String actionName = restMLPredictionAction.getName();
        assertFalse(Strings.isNullOrEmpty(actionName));
        assertEquals("ml_prediction_action", actionName);
    }

    public void testRoutes() {
        List<RestHandler.Route> routes = restMLPredictionAction.routes();
        assertNotNull(routes);
        assertFalse(routes.isEmpty());
        RestHandler.Route route = routes.get(0);
        assertEquals(RestRequest.Method.POST, route.getMethod());
        assertEquals("/_plugins/_ml/_predict/{algorithm}/{model_id}", route.getPath());
    }

    public void testGetRequest() throws IOException {
        RestRequest request = getRestRequest_PredictModel();
        MLPredictionTaskRequest mlPredictionTaskRequest = restMLPredictionAction.getRequest("modelId", FunctionName.KMEANS.name(), request);

        MLInput mlInput = mlPredictionTaskRequest.getMlInput();
        verifyParsedKMeansMLInput(mlInput);
    }

    @Ignore
    public void testPrepareRequest() throws Exception {
        RestRequest request = getRestRequest_PredictModel();
        restMLPredictionAction.handleRequest(request, channel, client);

        ArgumentCaptor<MLPredictionTaskRequest> argumentCaptor = ArgumentCaptor.forClass(MLPredictionTaskRequest.class);
        verify(client, times(1)).execute(eq(MLPredictionTaskAction.INSTANCE), argumentCaptor.capture(), any());
        MLInput mlInput = argumentCaptor.getValue().getMlInput();
        verifyParsedKMeansMLInput(mlInput);
    }

    private RestRequest getRestRequest_PredictModel() {
        RestRequest request = getKMeansRestRequest();
        request.params().put(PARAMETER_MODEL_ID, "model_id");
        return request;
    }
}
