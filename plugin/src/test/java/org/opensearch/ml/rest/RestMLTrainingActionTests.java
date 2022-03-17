/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.rest;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.ExpectedException;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.action.ActionListener;
import org.opensearch.client.node.NodeClient;
import org.opensearch.common.Strings;
import org.opensearch.common.bytes.BytesArray;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.xcontent.NamedXContentRegistry;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.ml.common.dataset.MLInputDataType;
import org.opensearch.ml.common.dataset.SearchQueryInputDataset;
import org.opensearch.ml.common.parameter.FunctionName;
import org.opensearch.ml.common.parameter.KMeansParams;
import org.opensearch.ml.common.parameter.MLInput;
import org.opensearch.ml.common.parameter.MLPredictionOutput;
import org.opensearch.ml.common.parameter.MLTaskState;
import org.opensearch.ml.common.parameter.MLTrainingOutput;
import org.opensearch.ml.common.transport.MLTaskResponse;
import org.opensearch.ml.common.transport.prediction.MLPredictionTaskAction;
import org.opensearch.ml.common.transport.training.MLTrainingTaskAction;
import org.opensearch.ml.common.transport.training.MLTrainingTaskRequest;
import org.opensearch.ml.engine.algorithms.clustering.KMeans;
import org.opensearch.rest.BaseRestHandler;
import org.opensearch.rest.RestChannel;
import org.opensearch.rest.RestHandler;
import org.opensearch.rest.RestRequest;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.test.rest.FakeRestRequest;
import org.opensearch.threadpool.TestThreadPool;
import org.opensearch.threadpool.ThreadPool;

import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.opensearch.ml.utils.RestActionUtils.PARAMETER_ALGORITHM;

public class RestMLTrainingActionTests extends OpenSearchTestCase {
    @Rule
    public ExpectedException thrown = ExpectedException.none();

    private RestMLTrainingAction restMLTrainingAction;

    NodeClient client;
    private ThreadPool threadPool;

    @Mock
    RestChannel channel;

    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);
        restMLTrainingAction = new RestMLTrainingAction();

        threadPool = new TestThreadPool(this.getClass().getSimpleName() + "ThreadPool");
        client = spy(new NodeClient(Settings.EMPTY, threadPool));

        doAnswer(invocation -> {
            ActionListener<MLTaskResponse> actionListener = invocation.getArgument(2);
            MLTrainingOutput mlTaskResponse = new MLTrainingOutput(null, "taskId", MLTaskState.CREATED.name());
            actionListener.onResponse(MLTaskResponse.builder()
                    .output(mlTaskResponse)
                    .build());
            return null;
        }).when(client).execute(eq(MLTrainingTaskAction.INSTANCE), any(), any());
    }

    @Override
    public void tearDown() throws Exception {
        super.tearDown();
        threadPool.shutdown();
        client.close();
    }

    public void testConstructor() {
        RestMLTrainingAction mlTrainingAction = new RestMLTrainingAction();
        assertNotNull(mlTrainingAction);
    }

    public void testGetName() {
        String actionName = restMLTrainingAction.getName();
        assertFalse(Strings.isNullOrEmpty(actionName));
        assertEquals("ml_training_action", actionName);
    }

    public void testRoutes() {
        List<RestHandler.Route> routes = restMLTrainingAction.routes();
        assertNotNull(routes);
        assertFalse(routes.isEmpty());
        RestHandler.Route route = routes.get(0);
        assertEquals(RestRequest.Method.POST, route.getMethod());
        assertEquals("/_plugins/_ml/_train/{algorithm}", route.getPath());
    }

    public void testGetRequest() throws IOException {
        RestRequest request = getRestRequest();
        MLTrainingTaskRequest trainingTaskRequest = restMLTrainingAction.getRequest(request);

        MLInput mlInput = trainingTaskRequest.getMlInput();
        verifyParsedMLInput(mlInput);
    }

    public void testPrepareRequest() throws Exception {
        RestRequest request = getRestRequest();
        restMLTrainingAction.handleRequest(request, channel, client);

        ArgumentCaptor<MLTrainingTaskRequest> argumentCaptor = ArgumentCaptor.forClass(MLTrainingTaskRequest.class);
        verify(client, times(1)).execute(eq(MLTrainingTaskAction.INSTANCE), argumentCaptor.capture(), any());
        MLInput mlInput = argumentCaptor.getValue().getMlInput();
        verifyParsedMLInput(mlInput);
    }

    private RestRequest getRestRequest() {
        Map<String, String> params = new HashMap<>();
        params.put(PARAMETER_ALGORITHM, FunctionName.KMEANS.name());
        final String requestContent = "{\"parameters\":{\"centroids\":3,\"iterations\":10,\"distance_type\":" +
                "\"COSINE\"},\"input_query\":{\"_source\":[\"petal_length_in_cm\",\"petal_width_in_cm\"]," +
                "\"size\":10000},\"input_index\":[\"iris_data\"]}";
        RestRequest request = new FakeRestRequest.Builder(getXContentRegistry()).withParams(params)
                .withContent(new BytesArray(requestContent), XContentType.JSON).build();
        return request;
    }

    private void verifyParsedMLInput(MLInput mlInput) {
        assertEquals(FunctionName.KMEANS, mlInput.getAlgorithm());
        assertEquals(MLInputDataType.SEARCH_QUERY, mlInput.getInputDataset().getInputDataType());
        SearchQueryInputDataset inputDataset = (SearchQueryInputDataset)mlInput.getInputDataset();
        assertEquals(1, inputDataset.getIndices().size());
        assertEquals("iris_data", inputDataset.getIndices().get(0));
        KMeansParams kMeansParams = (KMeansParams) mlInput.getParameters();
        assertEquals(3, kMeansParams.getCentroids().intValue());
    }

    private NamedXContentRegistry getXContentRegistry() {
        return new NamedXContentRegistry(Collections.singletonList(KMeansParams.XCONTENT_REGISTRY));
    }
}
