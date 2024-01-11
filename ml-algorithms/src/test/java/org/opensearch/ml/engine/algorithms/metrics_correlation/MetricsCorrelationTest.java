/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.metrics_correlation;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.opensearch.ml.common.CommonValue.ML_MODEL_GROUP_INDEX;
import static org.opensearch.ml.common.CommonValue.ML_MODEL_INDEX;
import static org.opensearch.ml.engine.algorithms.DLModel.ML_ENGINE;
import static org.opensearch.ml.engine.algorithms.DLModel.MODEL_HELPER;
import static org.opensearch.ml.engine.algorithms.DLModel.MODEL_ZIP_FILE;
import static org.opensearch.ml.engine.algorithms.metrics_correlation.MetricsCorrelation.MCORR_ML_VERSION;
import static org.opensearch.ml.engine.algorithms.metrics_correlation.MetricsCorrelation.MODEL_CONTENT_HASH;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.lucene.search.TotalHits;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.Version;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.action.search.ShardSearchFailure;
import org.opensearch.client.Client;
import org.opensearch.cluster.ClusterName;
import org.opensearch.cluster.ClusterState;
import org.opensearch.cluster.metadata.IndexMetadata;
import org.opensearch.cluster.metadata.Metadata;
import org.opensearch.cluster.node.DiscoveryNode;
import org.opensearch.cluster.node.DiscoveryNodeRole;
import org.opensearch.cluster.node.DiscoveryNodes;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.action.ActionFuture;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.commons.ConfigConstants;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.common.bytes.BytesReference;
import org.opensearch.core.common.transport.TransportAddress;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.index.IndexNotFoundException;
import org.opensearch.index.query.BoolQueryBuilder;
import org.opensearch.index.query.TermQueryBuilder;
import org.opensearch.ml.common.CommonValue;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.MLModel;
import org.opensearch.ml.common.MLTask;
import org.opensearch.ml.common.exception.ExecuteException;
import org.opensearch.ml.common.exception.MLException;
import org.opensearch.ml.common.input.execute.metricscorrelation.MetricsCorrelationInput;
import org.opensearch.ml.common.input.execute.samplecalculator.LocalSampleCalculatorInput;
import org.opensearch.ml.common.model.MLModelConfig;
import org.opensearch.ml.common.model.MLModelFormat;
import org.opensearch.ml.common.model.MLModelState;
import org.opensearch.ml.common.model.MetricsCorrelationModelConfig;
import org.opensearch.ml.common.output.Output;
import org.opensearch.ml.common.output.execute.metrics_correlation.MCorrModelTensors;
import org.opensearch.ml.common.output.execute.metrics_correlation.MetricsCorrelationOutput;
import org.opensearch.ml.common.transport.deploy.MLDeployModelAction;
import org.opensearch.ml.common.transport.deploy.MLDeployModelRequest;
import org.opensearch.ml.common.transport.deploy.MLDeployModelResponse;
import org.opensearch.ml.common.transport.model.MLModelGetAction;
import org.opensearch.ml.common.transport.model.MLModelGetRequest;
import org.opensearch.ml.common.transport.model.MLModelGetResponse;
import org.opensearch.ml.common.transport.model.MLModelSearchAction;
import org.opensearch.ml.common.transport.register.MLRegisterModelAction;
import org.opensearch.ml.common.transport.register.MLRegisterModelInput;
import org.opensearch.ml.common.transport.register.MLRegisterModelRequest;
import org.opensearch.ml.common.transport.register.MLRegisterModelResponse;
import org.opensearch.ml.common.transport.task.MLTaskGetAction;
import org.opensearch.ml.common.transport.task.MLTaskGetRequest;
import org.opensearch.ml.common.transport.task.MLTaskGetResponse;
import org.opensearch.ml.engine.MLEngine;
import org.opensearch.ml.engine.ModelHelper;
import org.opensearch.ml.engine.encryptor.Encryptor;
import org.opensearch.ml.engine.encryptor.EncryptorImpl;
import org.opensearch.search.SearchHit;
import org.opensearch.search.SearchHits;
import org.opensearch.search.aggregations.InternalAggregations;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.search.fetch.subphase.FetchSourceContext;
import org.opensearch.search.internal.InternalSearchResponse;
import org.opensearch.search.profile.SearchProfileShardResults;
import org.opensearch.search.suggest.Suggest;
import org.opensearch.threadpool.ThreadPool;

public class MetricsCorrelationTest {
    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();
    @Mock
    Client client;
    Settings settings;

    @Mock
    private ClusterService clusterService;

    @Mock
    ThreadPool threadPool;

    ThreadContext threadContext;

    @Mock
    SearchRequest searchRequest;
    SearchResponse searchResponse;

    @Mock
    MLRegisterModelResponse mlRegisterModelResponse;
    @Mock
    MLDeployModelRequest mlDeployModelRequest;
    @Mock
    MLDeployModelResponse mlDeployModelResponse;

    @Mock
    ActionListener<Map<String, Object>> searchListener;
    @Mock
    ActionListener<MLRegisterModelResponse> mlRegisterModelResponseActionListener;
    @Mock
    ActionListener<MLDeployModelResponse> mlDeployModelResponseActionListener;
    private MetricsCorrelation metricsCorrelation;
    private MetricsCorrelationInput input, extendedInput;
    private Path mlCachePath;
    private Path mlConfigPath;
    private MLModel model;

    private MetricsCorrelationModelConfig modelConfig;
    private MLEngine mlEngine;
    private ModelHelper modelHelper;

    private MetricsCorrelationOutput expectedOutput;

    private final String modelId = "modelId";
    private final String modelGroupId = "modelGroupId";

    final String USER_STRING = "myuser|role1,role2|myTenant";

    MLTask mlTask;

    Map<String, Object> params = new HashMap<>();

    private Encryptor encryptor;

    public MetricsCorrelationTest() {}

    @Before
    public void setUp() throws IOException, URISyntaxException {

        System.setProperty("testMode", "true");

        mlCachePath = Path.of("/tmp/djl_cache_" + UUID.randomUUID());
        encryptor = new EncryptorImpl("m+dWmfmnNRiNlOdej/QelEkvMTyH//frS2TBeS2BP4w=");
        mlEngine = new MLEngine(mlCachePath, encryptor);
        modelConfig = MetricsCorrelationModelConfig.builder().modelType(MetricsCorrelation.MODEL_TYPE).allConfig(null).build();

        model = MLModel
            .builder()
            .modelFormat(MLModelFormat.TORCH_SCRIPT)
            .name(FunctionName.METRICS_CORRELATION.name())
            .modelId(modelId)
            .modelGroupId(modelGroupId)
            .algorithm(FunctionName.METRICS_CORRELATION)
            .version(MCORR_ML_VERSION)
            .modelConfig(modelConfig)
            .modelState(MLModelState.UNDEPLOYED)
            .build();
        modelHelper = new ModelHelper(mlEngine);

        mlTask = MLTask.builder().taskId("task_id").modelId(modelId).build();
        params.put(MODEL_ZIP_FILE, new File(getClass().getResource("mcorr.zip").toURI()));
        params.put(MODEL_HELPER, modelHelper);
        params.put(ML_ENGINE, mlEngine);

        MockitoAnnotations.openMocks(this);
        metricsCorrelation = spy(new MetricsCorrelation(client, settings, clusterService));

        settings = Settings.builder().build();
        ClusterState testClusterState = setupTestClusterState();
        when(clusterService.state()).thenReturn(testClusterState);

        threadContext = new ThreadContext(settings);
        threadContext.putTransient(ConfigConstants.OPENSEARCH_SECURITY_USER_INFO_THREAD_CONTEXT, USER_STRING);
        when(client.threadPool()).thenReturn(threadPool);
        when(threadPool.getThreadContext()).thenReturn(threadContext);

        List<float[]> inputData = new ArrayList<>();
        inputData.add(new float[] { -1.0f, 2.0f, 3.0f });
        inputData.add(new float[] { -1.0f, 2.0f, 3.0f });
        input = MetricsCorrelationInput.builder().inputData(inputData).build();

        List<float[]> extendedInputData = new ArrayList<>();
        extendedInputData
            .add(
                new float[] {
                    -1.1635416f,
                    -1.5003631f,
                    0.46138194f,
                    0.5308311f,
                    -0.83149344f,
                    -3.7009873f,
                    -3.5463789f,
                    0.22571462f,
                    -5.0380244f,
                    0.76588845f,
                    1.236113f,
                    1.8460795f,
                    1.7576948f,
                    0.44893077f,
                    0.7363948f,
                    0.70440894f,
                    0.89451003f,
                    4.2006273f,
                    0.3697659f,
                    2.2458954f }
            );
        extendedInputData
            .add(
                new float[] {
                    1.3037996f,
                    2.7976995f,
                    -0.12042701f,
                    1.3688855f,
                    1.6955005f,
                    -2.2575269f,
                    0.080582514f,
                    3.011721f,
                    -0.4320283f,
                    3.2440786f,
                    -1.0321085f,
                    1.2346085f,
                    -2.3152106f,
                    -0.9783513f,
                    0.6837618f,
                    1.5320586f,
                    -1.6148578f,
                    -0.94538075f,
                    0.55978125f,
                    -4.7430468f }
            );
        extendedInputData
            .add(
                new float[] {
                    1.8792984f,
                    -3.1561708f,
                    -0.8443318f,
                    -1.998743f,
                    -0.6319316f,
                    2.4614046f,
                    -0.44511616f,
                    0.82785237f,
                    1.7911717f,
                    -1.8172283f,
                    0.46574894f,
                    -1.8691323f,
                    3.9586513f,
                    0.8078605f,
                    0.9049874f,
                    5.4086914f,
                    -0.7425967f,
                    -0.20115769f,
                    -1.197923f,
                    2.741789f }
            );
        extendedInput = MetricsCorrelationInput.builder().inputData(extendedInputData).build();
    }

    @Ignore
    @Test
    public void testWhenModelIdNotNullButModelIsNotDeployed() throws ExecuteException {
        MLModelGetResponse response = new MLModelGetResponse(model);
        ActionFuture<MLModelGetResponse> mockedFuture = mock(ActionFuture.class);
        when(client.execute(any(MLModelGetAction.class), any(MLModelGetRequest.class))).thenReturn(mockedFuture);
        when(mockedFuture.actionGet(anyLong())).thenReturn(response);

        doAnswer(invocation -> {

            MLModel smallModel = MLModel
                .builder()
                .modelFormat(MLModelFormat.TORCH_SCRIPT)
                .name(FunctionName.METRICS_CORRELATION.name())
                .modelId(modelId)
                .modelGroupId(modelGroupId)
                .algorithm(FunctionName.METRICS_CORRELATION)
                .version(MCORR_ML_VERSION)
                .modelConfig(modelConfig)
                .modelState(MLModelState.UNDEPLOYED)
                .build();
            MLModelGetResponse responseTemp = new MLModelGetResponse(smallModel);
            ActionFuture<MLModelGetResponse> mockedFutureTemp = mock(ActionFuture.class);
            MLTaskGetResponse taskResponse = new MLTaskGetResponse(mlTask);
            ActionFuture<MLTaskGetResponse> mockedFutureResponse = mock(ActionFuture.class);
            when(client.execute(any(MLTaskGetAction.class), any(MLTaskGetRequest.class))).thenReturn(mockedFutureResponse);
            when(mockedFutureResponse.actionGet(anyLong())).thenReturn(taskResponse);
            when(mockedFutureTemp.actionGet(anyLong())).thenReturn(responseTemp);
            metricsCorrelation.initModel(smallModel, params);
            smallModel.toBuilder().modelState(MLModelState.DEPLOYED).build();
            return null;
        }).when(client).execute(any(MLDeployModelAction.class), any(MLDeployModelRequest.class), isA(ActionListener.class));

        ActionListener<Output> actionListener = ActionListener.wrap(o -> {
            MetricsCorrelationOutput output = (MetricsCorrelationOutput) o;
            List<MCorrModelTensors> mlModelOutputs = output.getModelOutput();
            assert mlModelOutputs.size() == 1;
            assertNull(mlModelOutputs.get(0).getMCorrModelTensors());
        }, e -> { fail("Test failed: " + e.getMessage()); });
        metricsCorrelation.execute(input, actionListener);
    }

    @Ignore
    @Test
    public void testExecuteWithModelInIndexAndEmptyOutput() throws ExecuteException, URISyntaxException {
        Map<String, Object> params = new HashMap<>();
        params.put(MODEL_HELPER, modelHelper);
        params.put(MODEL_ZIP_FILE, new File(getClass().getResource("mcorr.zip").toURI()));
        params.put(ML_ENGINE, mlEngine);

        MLModel smallModel = model.toBuilder().modelConfig(modelConfig).build();
        MLModelGetResponse response = new MLModelGetResponse(smallModel);

        doAnswer(invocation -> {
            ActionListener<SearchResponse> searchListener = invocation.getArgument(2);
            searchResponse = createSearchModelResponse();
            searchListener.onResponse(searchResponse);
            return searchListener;
        }).when(client).execute(any(MLModelSearchAction.class), any(SearchRequest.class), isA(ActionListener.class));

        ActionFuture<MLModelGetResponse> mockedFuture = mock(ActionFuture.class);
        when(client.execute(any(MLModelGetAction.class), any(MLModelGetRequest.class))).thenReturn(mockedFuture);
        when(mockedFuture.actionGet(anyLong())).thenReturn(response);
        doAnswer(invocation -> {
            metricsCorrelation.initModel(smallModel, params);
            return null;
        }).when(client).execute(any(MLDeployModelAction.class), any(MLDeployModelRequest.class), isA(ActionListener.class));

        ActionListener<Output> actionListener = ActionListener.wrap(o -> {
            MetricsCorrelationOutput output = (MetricsCorrelationOutput) o;
            List<MCorrModelTensors> mlModelOutputs = output.getModelOutput();
            assert mlModelOutputs.size() == 1;
            assertNull(mlModelOutputs.get(0).getMCorrModelTensors());
        }, e -> { fail("Test failed: " + e.getMessage()); });
        metricsCorrelation.execute(input, actionListener);
    }

    @Test
    public void testExecuteWithModelInIndexAndOneEvent() throws ExecuteException, URISyntaxException {
        Map<String, Object> params = new HashMap<>();
        params.put(MODEL_HELPER, modelHelper);
        params.put(MODEL_ZIP_FILE, new File(getClass().getResource("mcorr.zip").toURI()));
        params.put(ML_ENGINE, mlEngine);

        MLModel smallModel = model.toBuilder().modelConfig(modelConfig).modelState(MLModelState.DEPLOYED).build();
        MLModelGetResponse response = new MLModelGetResponse(smallModel);
        MLTaskGetResponse taskResponse = new MLTaskGetResponse(mlTask);

        metricsCorrelation.initModel(smallModel, params);

        ActionFuture<MLModelGetResponse> mockedFuture = mock(ActionFuture.class);
        when(client.execute(any(MLModelGetAction.class), any(MLModelGetRequest.class))).thenReturn(mockedFuture);
        when(mockedFuture.actionGet(anyLong())).thenReturn(response);

        ActionFuture<MLTaskGetResponse> mockedFutureResponse = mock(ActionFuture.class);
        when(client.execute(any(MLTaskGetAction.class), any(MLTaskGetRequest.class))).thenReturn(mockedFutureResponse);
        when(mockedFutureResponse.actionGet(anyLong())).thenReturn(taskResponse);

        ActionListener<Output> actionListener = ActionListener.wrap(o -> {
            MetricsCorrelationOutput output = (MetricsCorrelationOutput) o;
            List<MCorrModelTensors> mlModelOutputs = output.getModelOutput();
            assert mlModelOutputs.size() == 1;
            assertNotNull(mlModelOutputs.get(0).getMCorrModelTensors().get(0).getEvent_window());
            assertNotNull(mlModelOutputs.get(0).getMCorrModelTensors().get(0).getEvent_pattern());
            assertNotNull(mlModelOutputs.get(0).getMCorrModelTensors().get(0).getSuspected_metrics());
        }, e -> { fail("Test failed: " + e.getMessage()); });
        metricsCorrelation.execute(extendedInput, actionListener);
    }

    @Ignore
    @Test
    public void testExecuteWithNoModelIndexAndOneEvent() throws ExecuteException, URISyntaxException {
        Map<String, Object> params = new HashMap<>();
        params.put(MODEL_HELPER, modelHelper);
        params.put(MODEL_ZIP_FILE, new File(getClass().getResource("mcorr.zip").toURI()));
        params.put(ML_ENGINE, mlEngine);

        MLModel smallModel = model.toBuilder().modelConfig(modelConfig).modelState(MLModelState.DEPLOYED).build();
        MLModelGetResponse response = new MLModelGetResponse(smallModel);
        MLTaskGetResponse taskResponse = new MLTaskGetResponse(mlTask);

        ActionFuture<MLModelGetResponse> mockedFuture = mock(ActionFuture.class);
        when(client.execute(any(MLModelGetAction.class), any(MLModelGetRequest.class))).thenReturn(mockedFuture);
        when(mockedFuture.actionGet(anyLong())).thenReturn(response);

        ActionFuture<MLTaskGetResponse> mockedFutureResponse = mock(ActionFuture.class);
        when(client.execute(any(MLTaskGetAction.class), any(MLTaskGetRequest.class))).thenReturn(mockedFutureResponse);
        when(mockedFutureResponse.actionGet(anyLong())).thenReturn(taskResponse);

        doAnswer(invocation -> {
            ActionListener<SearchResponse> searchListener = invocation.getArgument(2);
            searchListener.onFailure(new IndexNotFoundException("no such index []"));
            return searchListener;
        }).when(client).execute(any(MLModelSearchAction.class), any(SearchRequest.class), isA(ActionListener.class));

        doAnswer(invocation -> {
            ActionListener<MLRegisterModelResponse> registerModelResponseListener = invocation.getArgument(2);
            registerModelResponseListener.onResponse(mlRegisterModelResponse);
            metricsCorrelation.initModel(smallModel, params);
            return mlRegisterModelResponse;
        }).when(client).execute(any(MLRegisterModelAction.class), any(MLRegisterModelRequest.class), isA(ActionListener.class));

        ActionListener<Output> actionListener = ActionListener.wrap(o -> {
            MetricsCorrelationOutput output = (MetricsCorrelationOutput) o;
            List<MCorrModelTensors> mlModelOutputs = output.getModelOutput();
            assert mlModelOutputs.size() == 1;
            assertNotNull(mlModelOutputs.get(0).getMCorrModelTensors().get(0).getEvent_window());
            assertNotNull(mlModelOutputs.get(0).getMCorrModelTensors().get(0).getEvent_pattern());
            assertNotNull(mlModelOutputs.get(0).getMCorrModelTensors().get(0).getSuspected_metrics());
        }, e -> { fail("Test failed: " + e.getMessage()); });
        metricsCorrelation.execute(extendedInput, actionListener);
    }

    @Ignore
    @Test
    public void testExecuteWithModelInIndexAndInvokeDeployAndOneEvent() throws ExecuteException, URISyntaxException {
        Map<String, Object> params = new HashMap<>();
        params.put(MODEL_HELPER, modelHelper);
        params.put(MODEL_ZIP_FILE, new File(getClass().getResource("mcorr.zip").toURI()));
        params.put(ML_ENGINE, mlEngine);

        MLModel smallModel = model.toBuilder().modelConfig(modelConfig).modelState(MLModelState.PARTIALLY_DEPLOYED).build();
        MLModelGetResponse responseBeforeDeployed = new MLModelGetResponse(model);
        MLModelGetResponse responseAfterDeployed = new MLModelGetResponse(smallModel);
        MLTaskGetResponse taskResponse = new MLTaskGetResponse(mlTask);

        ActionFuture<MLModelGetResponse> mockedFuture = mock(ActionFuture.class);
        when(client.execute(any(MLModelGetAction.class), any(MLModelGetRequest.class))).thenReturn(mockedFuture);
        when(mockedFuture.actionGet(anyLong())).thenReturn(responseBeforeDeployed);

        ActionFuture<MLTaskGetResponse> mockedFutureResponse = mock(ActionFuture.class);
        when(client.execute(any(MLTaskGetAction.class), any(MLTaskGetRequest.class))).thenReturn(mockedFutureResponse);
        when(mockedFutureResponse.actionGet(anyLong())).thenReturn(taskResponse);

        doAnswer(invocation -> {
            ActionListener<SearchResponse> searchListener = invocation.getArgument(2);
            searchResponse = createSearchModelResponse();
            searchListener.onResponse(searchResponse);
            return searchListener;
        }).when(client).execute(any(MLModelSearchAction.class), any(SearchRequest.class), isA(ActionListener.class));

        doAnswer(invocation -> {
            ActionListener<MLDeployModelResponse> deployModelResponseListener = invocation.getArgument(2);
            metricsCorrelation.initModel(smallModel, params);
            ActionFuture<MLModelGetResponse> mockedFutureNext = mock(ActionFuture.class);
            when(client.execute(any(MLModelGetAction.class), any(MLModelGetRequest.class))).thenReturn(mockedFutureNext);
            when(mockedFutureNext.actionGet(anyLong())).thenReturn(responseAfterDeployed);

            deployModelResponseListener.onResponse(mlDeployModelResponse);
            return mlDeployModelResponse;
        }).when(client).execute(any(MLDeployModelAction.class), any(MLDeployModelRequest.class), isA(ActionListener.class));

        ActionListener<Output> actionListener = ActionListener.wrap(o -> {
            MetricsCorrelationOutput output = (MetricsCorrelationOutput) o;
            List<MCorrModelTensors> mlModelOutputs = output.getModelOutput();
            assert mlModelOutputs.size() == 1;
            assertNotNull(mlModelOutputs.get(0).getMCorrModelTensors().get(0).getEvent_window());
            assertNotNull(mlModelOutputs.get(0).getMCorrModelTensors().get(0).getEvent_pattern());
            assertNotNull(mlModelOutputs.get(0).getMCorrModelTensors().get(0).getSuspected_metrics());
        }, e -> { fail("Test failed: " + e.getMessage()); });
        metricsCorrelation.execute(extendedInput, actionListener);
    }

    @Ignore
    @Test
    public void testExecuteWithNoModelInIndexAndOneEvent() throws ExecuteException, URISyntaxException {
        Map<String, Object> params = new HashMap<>();
        params.put(MODEL_HELPER, modelHelper);
        params.put(MODEL_ZIP_FILE, new File(getClass().getResource("mcorr.zip").toURI()));
        params.put(ML_ENGINE, mlEngine);

        MLModel smallModel = model.toBuilder().modelConfig(modelConfig).modelState(MLModelState.DEPLOYED).build();
        MLModelGetResponse response = new MLModelGetResponse(smallModel);
        MLTaskGetResponse taskResponse = new MLTaskGetResponse(mlTask);

        ActionFuture<MLModelGetResponse> mockedFuture = mock(ActionFuture.class);
        when(client.execute(any(MLModelGetAction.class), any(MLModelGetRequest.class))).thenReturn(mockedFuture);
        when(mockedFuture.actionGet(anyLong())).thenReturn(response);

        ActionFuture<MLTaskGetResponse> mockedFutureResponse = mock(ActionFuture.class);
        when(client.execute(any(MLTaskGetAction.class), any(MLTaskGetRequest.class))).thenReturn(mockedFutureResponse);
        when(mockedFutureResponse.actionGet(anyLong())).thenReturn(taskResponse);

        doAnswer(invocation -> {
            ActionListener<SearchResponse> searchListener = invocation.getArgument(2);
            searchResponse = createEmptySearchModelResponse();
            searchListener.onResponse(searchResponse);
            return searchListener;
        }).when(client).execute(any(MLModelSearchAction.class), any(SearchRequest.class), isA(ActionListener.class));

        doAnswer(invocation -> {
            ActionListener<MLRegisterModelResponse> registerModelResponseListener = invocation.getArgument(2);
            registerModelResponseListener.onResponse(mlRegisterModelResponse);
            metricsCorrelation.initModel(smallModel, params);
            return mlRegisterModelResponse;
        }).when(client).execute(any(MLRegisterModelAction.class), any(MLRegisterModelRequest.class), isA(ActionListener.class));

        ActionListener<Output> actionListener = ActionListener.wrap(o -> {
            MetricsCorrelationOutput output = (MetricsCorrelationOutput) o;
            List<MCorrModelTensors> mlModelOutputs = output.getModelOutput();
            assert mlModelOutputs.size() == 1;
            assertNotNull(mlModelOutputs.get(0).getMCorrModelTensors().get(0).getEvent_window());
            assertNotNull(mlModelOutputs.get(0).getMCorrModelTensors().get(0).getEvent_pattern());
            assertNotNull(mlModelOutputs.get(0).getMCorrModelTensors().get(0).getSuspected_metrics());
        }, e -> { fail("Test failed: " + e.getMessage()); });
        metricsCorrelation.execute(extendedInput, actionListener);
    }

    // working
    @Test
    public void testGetModel() {
        ActionFuture<MLModelGetResponse> mockedFuture = mock(ActionFuture.class);
        MLModel smallModel = model.toBuilder().modelConfig(modelConfig).build();
        MLModelGetResponse response = new MLModelGetResponse(smallModel);
        when(client.execute(any(MLModelGetAction.class), any(MLModelGetRequest.class))).thenReturn(mockedFuture);
        when(mockedFuture.actionGet(anyLong())).thenReturn(response);
        MLModel mlModel = metricsCorrelation.getModel(modelId);
        model = MLModel
            .builder()
            .modelFormat(MLModelFormat.TORCH_SCRIPT)
            .name(FunctionName.METRICS_CORRELATION.name())
            .modelId(modelId)
            .algorithm(FunctionName.METRICS_CORRELATION)
            .version(MCORR_ML_VERSION)
            .modelConfig(modelConfig)
            .modelState(MLModelState.DEPLOYED)
            .build();
        assert MLModelFormat.TORCH_SCRIPT.equals(mlModel.getModelFormat());
        assert FunctionName.METRICS_CORRELATION.name().equals(model.getName());
        assert modelId.equals(model.getModelId());
        assert FunctionName.METRICS_CORRELATION.equals(mlModel.getAlgorithm());
        assert MCORR_ML_VERSION.equals(mlModel.getVersion());
        MetricsCorrelationModelConfig modelConfig1 = (MetricsCorrelationModelConfig) model.getModelConfig();
        assert MetricsCorrelation.MODEL_TYPE.equals(modelConfig1.getModelType());
        assertNull(modelConfig1.getAllConfig());
    }

    public static XContentBuilder builder() throws IOException {
        return XContentBuilder.builder(XContentType.JSON.xContent());
    }

    // working
    @Test
    public void testSearchRequest() {
        String expectedIndex = CommonValue.ML_MODEL_INDEX;
        String[] expectedIncludes = {
            MLModel.MODEL_ID_FIELD,
            MLModel.MODEL_NAME_FIELD,
            MLModel.MODEL_STATE_FIELD,
            MLModel.MODEL_VERSION_FIELD,
            MLModel.MODEL_CONTENT_FIELD };
        String[] expectedExcludes = { MLModel.MODEL_CONTENT_FIELD };
        String expectedNameQuery = FunctionName.METRICS_CORRELATION.name();
        String expectedVersionQuery = MCORR_ML_VERSION;
        SearchRequest searchRequest = metricsCorrelation.getSearchRequest();
        assertEquals(expectedIndex, searchRequest.indices()[0]);
        SearchSourceBuilder generatedSearchSource = searchRequest.source();
        FetchSourceContext fetchSourceContext = generatedSearchSource.fetchSource();
        assertNotNull(fetchSourceContext);
        assertArrayEquals(expectedIncludes, fetchSourceContext.includes());
        assertArrayEquals(expectedExcludes, fetchSourceContext.excludes());

        assertNotNull(generatedSearchSource.query());
        assertTrue(generatedSearchSource.query() instanceof BoolQueryBuilder);
        BoolQueryBuilder boolQueryBuilder = (BoolQueryBuilder) generatedSearchSource.query();
        assertEquals(2, boolQueryBuilder.should().size());

        // Verify name query
        assertTrue(boolQueryBuilder.should().get(0) instanceof TermQueryBuilder);
        TermQueryBuilder nameQueryBuilder = (TermQueryBuilder) boolQueryBuilder.should().get(0);
        assertEquals(expectedNameQuery, nameQueryBuilder.value());
        assertEquals(MLModel.MODEL_NAME_FIELD, nameQueryBuilder.fieldName());

        // Verify version query
        assertTrue(boolQueryBuilder.should().get(1) instanceof TermQueryBuilder);
        TermQueryBuilder versionQueryBuilder = (TermQueryBuilder) boolQueryBuilder.should().get(1);
        assertEquals(expectedVersionQuery, versionQueryBuilder.value());
        assertEquals(MLModel.MODEL_VERSION_FIELD, versionQueryBuilder.fieldName());
    }

    @Ignore
    @Test
    public void testRegisterModel() throws InterruptedException {
        doAnswer(invocation -> {
            ActionListener<MLRegisterModelResponse> registerModelResponseListener = invocation.getArgument(2);
            registerModelResponseListener.onResponse(mlRegisterModelResponse);

            MLRegisterModelRequest mlRegisterModelRequestTemp = invocation.getArgument(1);
            MLRegisterModelInput mlRegisterModelInput = mlRegisterModelRequestTemp.getRegisterModelInput();
            assert mlRegisterModelInput.isDeployModel();
            assert mlRegisterModelInput.getModelFormat() == MLModelFormat.TORCH_SCRIPT;
            assert FunctionName.METRICS_CORRELATION.name().equals(mlRegisterModelInput.getModelName());
            assert MCORR_ML_VERSION.equals(mlRegisterModelInput.getVersion());
            assert MODEL_CONTENT_HASH.equals(mlRegisterModelInput.getHashValue());
            MLModelConfig modelConfig = mlRegisterModelInput.getModelConfig();
            assert MetricsCorrelation.MODEL_TYPE.equals(modelConfig.getModelType());
            assertNull(modelConfig.getAllConfig());
            assert MetricsCorrelation.MCORR_MODEL_URL.equals(mlRegisterModelInput.getUrl());
            return mlRegisterModelResponse;
        }).when(client).execute(any(MLRegisterModelAction.class), any(MLRegisterModelRequest.class), isA(ActionListener.class));
        metricsCorrelation.registerModel(mlRegisterModelResponseActionListener);
        verify(mlRegisterModelResponseActionListener).onResponse(mlRegisterModelResponse);
    }

    @Test
    public void testDeployModel() {
        doAnswer(invocation -> {
            ActionListener<MLDeployModelResponse> deployModelResponseListener = invocation.getArgument(2);
            deployModelResponseListener.onResponse(mlDeployModelResponse);
            MLDeployModelRequest mlDeployModelRequestTemp = invocation.getArgument(1);
            assert !mlDeployModelRequestTemp.isAsync();
            assert !mlDeployModelRequestTemp.isDispatchTask();
            return mlDeployModelResponse;
        }).when(client).execute(any(MLDeployModelAction.class), any(MLDeployModelRequest.class), isA(ActionListener.class));
        metricsCorrelation.deployModel(modelId, mlDeployModelResponseActionListener);
        verify(mlDeployModelResponseActionListener).onResponse(mlDeployModelResponse);
    }

    @Test
    public void testDeployModelFail() {
        Exception ex = new ExecuteException("Testing");
        doAnswer(invocation -> {
            ActionListener<MLDeployModelResponse> deployModelResponseListener = invocation.getArgument(2);
            deployModelResponseListener.onFailure(ex);
            return null;
        }).when(client).execute(any(MLDeployModelAction.class), any(MLDeployModelRequest.class), isA(ActionListener.class));
        metricsCorrelation.deployModel(modelId, mlDeployModelResponseActionListener);
        verify(mlDeployModelResponseActionListener).onFailure(ex);
    }

    @Test
    public void testWrongInput() throws ExecuteException {
        exceptionRule.expect(ExecuteException.class);
        metricsCorrelation.execute(mock(LocalSampleCalculatorInput.class), mock(ActionListener.class));
    }

    @Test
    public void parseModelTensorOutput_NullOutput() {
        exceptionRule.expect(MLException.class);
        exceptionRule.expectMessage("No output generated");
        metricsCorrelation.parseModelTensorOutput(null, null);
    }

    @Test
    public void initModel_NullModelZipFile() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("model file is null");
        Map<String, Object> params = new HashMap<>();
        params.put(MODEL_HELPER, modelHelper);
        metricsCorrelation.initModel(model, params);
    }

    @Test
    public void initModel_NullModelHelper() throws URISyntaxException {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("model helper is null");
        Map<String, Object> params = new HashMap<>();
        params.put(MODEL_ZIP_FILE, new File(getClass().getResource("mcorr.zip").toURI()));
        metricsCorrelation.initModel(model, params);
    }

    @Test
    public void initModel_NullMLEngine() throws URISyntaxException {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("ML engine is null");
        Map<String, Object> params = new HashMap<>();
        params.put(MODEL_ZIP_FILE, new File(getClass().getResource("mcorr.zip").toURI()));
        params.put(MODEL_HELPER, modelHelper);
        metricsCorrelation.initModel(model, params);
    }

    @Test
    public void initModel_NullModelId() throws URISyntaxException {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("model id is null");

        model.setModelId(null);
        metricsCorrelation.initModel(model, params);
    }

    @Test
    public void initModel_WrongFunctionName() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("wrong function name");
        MLModel mlModel = model.toBuilder().algorithm(FunctionName.KMEANS).build();
        metricsCorrelation.initModel(mlModel, params);
    }

    private SearchResponse createSearchModelResponse() throws IOException {
        XContentBuilder content = builder();
        content.startObject();
        content.field(MLModel.MODEL_NAME_FIELD, FunctionName.METRICS_CORRELATION.name());
        content.field(MLModel.MODEL_VERSION_FIELD, MCORR_ML_VERSION);
        content.field(MLModel.MODEL_ID_FIELD, modelId);
        content.endObject();

        SearchHit[] hits = new SearchHit[1];
        hits[0] = new SearchHit(0, "modelId", null, null).sourceRef(BytesReference.bytes(content));

        return new SearchResponse(
            new InternalSearchResponse(
                new SearchHits(hits, new TotalHits(1, TotalHits.Relation.EQUAL_TO), 1.0f),
                InternalAggregations.EMPTY,
                new Suggest(Collections.emptyList()),
                new SearchProfileShardResults(Collections.emptyMap()),
                false,
                false,
                1
            ),
            "",
            5,
            5,
            0,
            100,
            ShardSearchFailure.EMPTY_ARRAY,
            SearchResponse.Clusters.EMPTY
        );
    }

    private SearchResponse createEmptySearchModelResponse() throws IOException {
        XContentBuilder content = builder();
        content.startObject();
        content.endObject();

        SearchHit[] hits = new SearchHit[1];
        hits[0] = new SearchHit(0, "", null, null).sourceRef(BytesReference.bytes(content));

        return new SearchResponse(
            new InternalSearchResponse(
                new SearchHits(hits, new TotalHits(0, TotalHits.Relation.EQUAL_TO), 1.0f),
                InternalAggregations.EMPTY,
                new Suggest(Collections.emptyList()),
                new SearchProfileShardResults(Collections.emptyMap()),
                false,
                false,
                1
            ),
            "",
            5,
            5,
            0,
            100,
            ShardSearchFailure.EMPTY_ARRAY,
            SearchResponse.Clusters.EMPTY
        );
    }

    public static ClusterState setupTestClusterState() {
        Set<DiscoveryNodeRole> roleSet = new HashSet<>();
        roleSet.add(DiscoveryNodeRole.DATA_ROLE);
        DiscoveryNode node = new DiscoveryNode(
            "node",
            new TransportAddress(TransportAddress.META_ADDRESS, new AtomicInteger().incrementAndGet()),
            new HashMap<>(),
            roleSet,
            Version.CURRENT
        );
        Metadata metadata = new Metadata.Builder()
            .indices(
                Map
                    .of(
                        ML_MODEL_INDEX,
                        IndexMetadata
                            .builder("test")
                            .settings(
                                Settings
                                    .builder()
                                    .put("index.number_of_shards", 1)
                                    .put("index.number_of_replicas", 1)
                                    .put("index.version.created", Version.CURRENT.id)
                            )
                            .build(),
                        ML_MODEL_GROUP_INDEX,
                        IndexMetadata
                            .builder(ML_MODEL_GROUP_INDEX)
                            .settings(
                                Settings
                                    .builder()
                                    .put("index.number_of_shards", 1)
                                    .put("index.number_of_replicas", 1)
                                    .put("index.version.created", Version.CURRENT.id)
                            )
                            .build()
                    )
            )
            .build();
        return new ClusterState(
            new ClusterName("test cluster"),
            123l,
            "111111",
            metadata,
            null,
            DiscoveryNodes.builder().add(node).build(),
            null,
            Map.of(),
            0,
            false
        );
    }
}
