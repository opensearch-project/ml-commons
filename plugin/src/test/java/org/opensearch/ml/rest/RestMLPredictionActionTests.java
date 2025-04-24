/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.opensearch.ml.utils.MLExceptionUtils.LOCAL_MODEL_DISABLED_ERR_MSG;
import static org.opensearch.ml.utils.MLExceptionUtils.REMOTE_INFERENCE_DISABLED_ERR_MSG;
import static org.opensearch.ml.utils.RestActionUtils.PARAMETER_MODEL_ID;
import static org.opensearch.ml.utils.TestHelper.getBatchRestRequest;
import static org.opensearch.ml.utils.TestHelper.getBatchRestRequest_WrongActionType;
import static org.opensearch.ml.utils.TestHelper.getKMeansRestRequest;
import static org.opensearch.ml.utils.TestHelper.verifyParsedBatchMLInput;
import static org.opensearch.ml.utils.TestHelper.verifyParsedKMeansMLInput;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.client.node.NodeClient;
import org.opensearch.common.settings.Settings;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.common.Strings;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.MLModel;
import org.opensearch.ml.common.input.MLInput;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.transport.MLTaskResponse;
import org.opensearch.ml.common.transport.model.MLModelGetAction;
import org.opensearch.ml.common.transport.model.MLModelGetResponse;
import org.opensearch.ml.common.transport.prediction.MLPredictionTaskAction;
import org.opensearch.ml.common.transport.prediction.MLPredictionTaskRequest;
import org.opensearch.ml.model.MLModelManager;
import org.opensearch.rest.RestChannel;
import org.opensearch.rest.RestHandler;
import org.opensearch.rest.RestRequest;
import org.opensearch.rest.action.RestToXContentListener;
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
    @Mock
    MLModelManager modelManager;
    @Mock
    MLFeatureEnabledSetting mlFeatureEnabledSetting;

    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);
        when(modelManager.getOptionalModelFunctionName(anyString())).thenReturn(Optional.of(FunctionName.REMOTE));
        when(mlFeatureEnabledSetting.isRemoteInferenceEnabled()).thenReturn(true);
        when(mlFeatureEnabledSetting.isLocalModelEnabled()).thenReturn(true);
        restMLPredictionAction = new RestMLPredictionAction(modelManager, mlFeatureEnabledSetting);

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

    @Test
    public void testConstructor() {
        RestMLPredictionAction mlPredictionAction = new RestMLPredictionAction(modelManager, mlFeatureEnabledSetting);
        assertNotNull(mlPredictionAction);
    }

    @Test
    public void testGetName() {
        String actionName = restMLPredictionAction.getName();
        assertFalse(Strings.isNullOrEmpty(actionName));
        assertEquals("ml_prediction_action", actionName);
    }

    @Test
    public void testRoutes() {
        List<RestHandler.Route> routes = restMLPredictionAction.routes();
        assertNotNull(routes);
        assertFalse(routes.isEmpty());
        RestHandler.Route route = routes.get(0);
        assertEquals(RestRequest.Method.POST, route.getMethod());
        assertEquals("/_plugins/_ml/_predict/{algorithm}/{model_id}", route.getPath());
    }

    @Test
    public void testRoutes_Batch() {
        List<RestHandler.Route> routes = restMLPredictionAction.routes();
        assertNotNull(routes);
        assertFalse(routes.isEmpty());
        RestHandler.Route route = routes.get(2);
        assertEquals(RestRequest.Method.POST, route.getMethod());
        assertEquals("/_plugins/_ml/models/{model_id}/_batch_predict", route.getPath());
    }

    @Test
    public void testGetRequest() throws IOException {
        RestRequest request = getRestRequest_PredictModel();
        MLPredictionTaskRequest mlPredictionTaskRequest = restMLPredictionAction
            .getRequest("modelId", FunctionName.KMEANS.name(), FunctionName.KMEANS.name(), request);

        MLInput mlInput = mlPredictionTaskRequest.getMlInput();
        verifyParsedKMeansMLInput(mlInput);
    }

    @Test
    public void testGetRequest_RemoteInferenceDisabled() throws IOException {
        thrown.expect(IllegalStateException.class);
        thrown.expectMessage(REMOTE_INFERENCE_DISABLED_ERR_MSG);

        when(mlFeatureEnabledSetting.isRemoteInferenceEnabled()).thenReturn(false);
        RestRequest request = getRestRequest_PredictModel();
        MLPredictionTaskRequest mlPredictionTaskRequest = restMLPredictionAction
            .getRequest("modelId", FunctionName.REMOTE.name(), "text_embedding", request);
    }

    @Test
    public void testGetRequest_LocalModelInferenceDisabled() throws IOException {
        thrown.expect(IllegalStateException.class);
        thrown.expectMessage(LOCAL_MODEL_DISABLED_ERR_MSG);

        when(mlFeatureEnabledSetting.isLocalModelEnabled()).thenReturn(false);
        RestRequest request = getRestRequest_PredictModel();
        MLPredictionTaskRequest mlPredictionTaskRequest = restMLPredictionAction
            .getRequest("modelId", FunctionName.TEXT_EMBEDDING.name(), "text_embedding", request);
    }

    @Test
    public void testPrepareRequest() throws Exception {
        RestRequest request = getRestRequest_PredictModel();
        restMLPredictionAction.handleRequest(request, channel, client);
        ArgumentCaptor<MLPredictionTaskRequest> argumentCaptor = ArgumentCaptor.forClass(MLPredictionTaskRequest.class);
        verify(client, times(1)).execute(eq(MLPredictionTaskAction.INSTANCE), argumentCaptor.capture(), any());
        MLInput mlInput = argumentCaptor.getValue().getMlInput();
        verifyParsedKMeansMLInput(mlInput);
    }

    @Test
    public void testPrepareBatchRequest() throws Exception {
        RestRequest request = getBatchRestRequest();
        when(mlFeatureEnabledSetting.isOfflineBatchInferenceEnabled()).thenReturn(true);
        restMLPredictionAction.handleRequest(request, channel, client);
        ArgumentCaptor<MLPredictionTaskRequest> argumentCaptor = ArgumentCaptor.forClass(MLPredictionTaskRequest.class);
        verify(client, times(1)).execute(eq(MLPredictionTaskAction.INSTANCE), argumentCaptor.capture(), any());
        MLInput mlInput = argumentCaptor.getValue().getMlInput();
        verifyParsedBatchMLInput(mlInput);
    }

    @Test
    public void testPrepareBatchRequest_FeatureFlagDisabled() throws Exception {
        thrown.expect(IllegalStateException.class);
        thrown
            .expectMessage(
                "Offline Batch Inference is currently disabled. To enable it, update the setting \"plugins.ml_commons.offline_batch_inference_enabled\" to true."
            );

        RestRequest request = getBatchRestRequest();
        when(mlFeatureEnabledSetting.isOfflineBatchInferenceEnabled()).thenReturn(false);
        restMLPredictionAction.handleRequest(request, channel, client);
    }

    @Test
    public void testPrepareBatchRequest_WrongActionType() throws Exception {
        thrown.expect(IllegalArgumentException.class);
        thrown.expectMessage("Wrong Action Type");

        RestRequest request = getBatchRestRequest_WrongActionType();
        restMLPredictionAction.getRequest("model id", "remote", "text_embedding", request);
    }

    @Ignore
    public void testPrepareRequest_EmptyAlgorithm() throws Exception {
        MLModel model = MLModel.builder().algorithm(FunctionName.BATCH_RCF).build();

        doAnswer(invocation -> {
            ActionListener<MLModelGetResponse> actionListener = invocation.getArgument(2);
            MLModelGetResponse response = new MLModelGetResponse(model);
            actionListener.onResponse(response);
            return null;
        }).when(client).execute(eq(MLModelGetAction.INSTANCE), any(), any());

        doAnswer(invocation -> {
            ActionListener<RestToXContentListener> actionListener = invocation.getArgument(2);
            RestToXContentListener<MLModelGetResponse> listener = new RestToXContentListener<>(channel);
            actionListener.onResponse(listener);
            return null;
        }).when(client).execute(eq(MLPredictionTaskAction.INSTANCE), any(), any());

        RestRequest request = getKMeansRestRequest();
        request.params().clear();
        request.params().put(PARAMETER_MODEL_ID, "model_id");
        restMLPredictionAction.handleRequest(request, channel, client);
        ArgumentCaptor<MLPredictionTaskRequest> argumentCaptor = ArgumentCaptor.forClass(MLPredictionTaskRequest.class);
        verify(client, times(1)).execute(eq(MLPredictionTaskAction.INSTANCE), argumentCaptor.capture(), any());
        MLInput mlInput = argumentCaptor.getValue().getMlInput();
        verifyParsedKMeansMLInput(mlInput);
    }

    @Test
    public void testGetRequest_InvalidActionType() throws IOException {
        // Test with an invalid action type
        thrown.expect(IllegalArgumentException.class);
        thrown.expectMessage("Wrong Action Type of models");

        RestRequest request = getBatchRestRequest_WrongActionType();
        restMLPredictionAction.getRequest("model_id", FunctionName.REMOTE.name(), "text_embedding", request);
    }

    @Test
    public void testGetRequest_UnsupportedAlgorithm() throws IOException {
        thrown.expect(IllegalArgumentException.class);
        thrown.expectMessage("Wrong function name");

        // Create a RestRequest with an unsupported algorithm
        RestRequest request = getRestRequest_PredictModel();
        restMLPredictionAction.getRequest("model_id", "INVALID_ALGO", "text_embedding", request);
    }

    private RestRequest getRestRequest_PredictModel() {
        RestRequest request = getKMeansRestRequest();
        request.params().put(PARAMETER_MODEL_ID, "model_id");
        return request;
    }
}
