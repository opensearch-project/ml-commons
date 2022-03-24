/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.opensearch.ml.utils.TestHelper.getKMeansRestRequest;
import static org.opensearch.ml.utils.TestHelper.verifyParsedKMeansMLInput;

import java.io.IOException;
import java.util.List;

import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.ExpectedException;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.action.ActionListener;
import org.opensearch.client.node.NodeClient;
import org.opensearch.common.Strings;
import org.opensearch.common.settings.Settings;
import org.opensearch.ml.common.MLTaskState;
import org.opensearch.ml.common.input.MLInput;
import org.opensearch.ml.common.output.MLTrainingOutput;
import org.opensearch.ml.common.transport.MLTaskResponse;
import org.opensearch.ml.common.transport.training.MLTrainingTaskRequest;
import org.opensearch.ml.common.transport.trainpredict.MLTrainAndPredictionTaskAction;
import org.opensearch.rest.RestChannel;
import org.opensearch.rest.RestHandler;
import org.opensearch.rest.RestRequest;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.threadpool.TestThreadPool;
import org.opensearch.threadpool.ThreadPool;

public class RestMLTrainAndPredictActionTests extends OpenSearchTestCase {

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    private RestMLTrainAndPredictAction restMLTrainAndPredictAction;

    NodeClient client;
    private ThreadPool threadPool;

    @Mock
    RestChannel channel;

    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);
        restMLTrainAndPredictAction = new RestMLTrainAndPredictAction();

        threadPool = new TestThreadPool(this.getClass().getSimpleName() + "ThreadPool");
        client = spy(new NodeClient(Settings.EMPTY, threadPool));

        doAnswer(invocation -> {
            ActionListener<MLTaskResponse> actionListener = invocation.getArgument(2);
            MLTrainingOutput mlTaskResponse = new MLTrainingOutput(null, "taskId", MLTaskState.CREATED.name());
            actionListener.onResponse(MLTaskResponse.builder().output(mlTaskResponse).build());
            return null;
        }).when(client).execute(eq(MLTrainAndPredictionTaskAction.INSTANCE), any(), any());
    }

    @Override
    public void tearDown() throws Exception {
        super.tearDown();
        threadPool.shutdown();
        client.close();
    }

    public void testConstructor() {
        RestMLTrainAndPredictAction restMLTrainAndPredictAction = new RestMLTrainAndPredictAction();
        assertNotNull(restMLTrainAndPredictAction);
    }

    public void testGetName() {
        String actionName = restMLTrainAndPredictAction.getName();
        assertFalse(Strings.isNullOrEmpty(actionName));
        assertEquals("ml_train_and_predict_action", actionName);
    }

    public void testRoutes() {
        List<RestHandler.Route> routes = restMLTrainAndPredictAction.routes();
        assertNotNull(routes);
        assertFalse(routes.isEmpty());
        RestHandler.Route route = routes.get(0);
        assertEquals(RestRequest.Method.POST, route.getMethod());
        assertEquals("/_plugins/_ml/_train_predict/{algorithm}", route.getPath());
    }

    public void testGetRequest() throws IOException {
        RestRequest request = getKMeansRestRequest();
        MLTrainingTaskRequest trainingTaskRequest = restMLTrainAndPredictAction.getRequest(request);

        MLInput mlInput = trainingTaskRequest.getMlInput();
        verifyParsedKMeansMLInput(mlInput);
    }

    public void testPrepareRequest() throws Exception {
        RestRequest request = getKMeansRestRequest();
        restMLTrainAndPredictAction.handleRequest(request, channel, client);

        ArgumentCaptor<MLTrainingTaskRequest> argumentCaptor = ArgumentCaptor.forClass(MLTrainingTaskRequest.class);
        verify(client, times(1)).execute(eq(MLTrainAndPredictionTaskAction.INSTANCE), argumentCaptor.capture(), any());
        MLInput mlInput = argumentCaptor.getValue().getMlInput();
        verifyParsedKMeansMLInput(mlInput);
    }
}
