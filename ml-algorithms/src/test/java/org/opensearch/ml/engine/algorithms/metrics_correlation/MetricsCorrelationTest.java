/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.metrics_correlation;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.lucene.search.TotalHits;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.action.search.ShardSearchFailure;
import org.opensearch.client.Client;
import org.opensearch.common.action.ActionFuture;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.common.bytes.BytesReference;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.index.IndexNotFoundException;
import org.opensearch.index.query.BoolQueryBuilder;
import org.opensearch.index.query.TermQueryBuilder;
import org.opensearch.ml.common.CommonValue;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.MLModel;
import org.opensearch.ml.common.MLTask;
import org.opensearch.ml.common.exception.MLException;
import org.opensearch.ml.common.input.MLInput;
import org.opensearch.ml.common.input.execute.metricscorrelation.MetricsCorrelationInput;
import org.opensearch.ml.common.input.execute.samplecalculator.LocalSampleCalculatorInput;
import org.opensearch.ml.common.model.*;
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
import org.opensearch.ml.common.exception.ExecuteException;
import org.opensearch.ml.engine.MLEngine;
import org.opensearch.ml.engine.ModelHelper;
import org.opensearch.search.SearchHit;
import org.opensearch.search.SearchHits;
import org.opensearch.search.aggregations.InternalAggregations;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.search.fetch.subphase.FetchSourceContext;
import org.opensearch.search.internal.InternalSearchResponse;
import org.opensearch.search.profile.SearchProfileShardResults;
import org.opensearch.search.suggest.Suggest;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.*;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.*;
import static org.opensearch.ml.engine.algorithms.DLModel.*;
import static org.opensearch.ml.engine.algorithms.metrics_correlation.MetricsCorrelation.MCORR_ML_VERSION;


public class MetricsCorrelationTest {
    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();
    @Mock
    Client client;
    @Mock
    Settings settings;
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
    private Path djlCachePath;
    private MLModel model;

    private MetricsCorrelationModelConfig modelConfig;
    private MLEngine mlEngine;
    private ModelHelper modelHelper;

    private MetricsCorrelationOutput expectedOutput;

    private final String modelId = "modelId";

    MLTask mlTask;

    Map<String, Object> params = new HashMap<>();

    public MetricsCorrelationTest() {
    }

    @Before
    public void setUp() throws IOException, URISyntaxException {

        System.setProperty("testMode", "true");

        djlCachePath = Path.of("/tmp/djl_cache_" + UUID.randomUUID());
        mlEngine = new MLEngine(djlCachePath);
        modelConfig = MetricsCorrelationModelConfig.builder()
                .modelType(MetricsCorrelation.MODEL_TYPE)
                .allConfig(null)
                .build();

        model = MLModel.builder()
                .modelFormat(MLModelFormat.TORCH_SCRIPT)
                .name(FunctionName.METRICS_CORRELATION.name())
                .modelId(modelId)
                .algorithm(FunctionName.METRICS_CORRELATION)
                .version(MCORR_ML_VERSION)
                .modelConfig(modelConfig)
                .modelState(MLModelState.UNDEPLOYED)
                .build();
        modelHelper = new ModelHelper(mlEngine);

        mlTask = MLTask.builder()
                .taskId("task_id")
                .modelId(modelId)
                .build();
        params.put(MODEL_ZIP_FILE, new File(getClass().getResource("mcorr.zip").toURI()));
        params.put(MODEL_HELPER, modelHelper);
        params.put(ML_ENGINE, mlEngine);

        MockitoAnnotations.openMocks(this);
        metricsCorrelation = spy(new MetricsCorrelation(client, settings));
        List<float[]> inputData = new ArrayList<>();
        inputData.add(new float[]{-1.0f, 2.0f, 3.0f});
        inputData.add(new float[]{-1.0f, 2.0f, 3.0f});
        input = MetricsCorrelationInput.builder().inputData(inputData).build();

        List<float[]> extendedInputData = new ArrayList<>();
        extendedInputData.add(new float[]{-1.1635416f, -1.5003631f, 0.46138194f, 0.5308311f, -0.83149344f, -3.7009873f, -3.5463789f, 0.22571462f, -5.0380244f, 0.76588845f, 1.236113f, 1.8460795f, 1.7576948f, 0.44893077f, 0.7363948f, 0.70440894f, 0.89451003f, 4.2006273f, 0.3697659f, 2.2458954f});
        extendedInputData.add(new float[]{1.3037996f, 2.7976995f, -0.12042701f, 1.3688855f, 1.6955005f, -2.2575269f, 0.080582514f, 3.011721f, -0.4320283f, 3.2440786f, -1.0321085f, 1.2346085f, -2.3152106f, -0.9783513f, 0.6837618f, 1.5320586f, -1.6148578f, -0.94538075f, 0.55978125f, -4.7430468f});
        extendedInputData.add(new float[]{1.8792984f, -3.1561708f, -0.8443318f, -1.998743f, -0.6319316f, 2.4614046f, -0.44511616f, 0.82785237f, 1.7911717f, -1.8172283f, 0.46574894f, -1.8691323f, 3.9586513f, 0.8078605f, 0.9049874f, 5.4086914f, -0.7425967f, -0.20115769f, -1.197923f, 2.741789f});
        extendedInput = MetricsCorrelationInput.builder().inputData(extendedInputData).build();
    }

    @Test
    public void testWhenModelIdNotNullButModelIsNotDeployed() throws ExecuteException {
        metricsCorrelation.initModel(model, params);
        MLModelGetResponse response = new MLModelGetResponse(model);
        ActionFuture<MLModelGetResponse> mockedFuture = mock(ActionFuture.class);
        when(client.execute(any(MLModelGetAction.class), any(MLModelGetRequest.class))).thenReturn(mockedFuture);
        when(mockedFuture.actionGet(anyLong())).thenReturn(response);

        doAnswer(invocation -> {
            MLModel smallModel = model.toBuilder().modelConfig(modelConfig).modelState(MLModelState.DEPLOYED).build();
            MLModelGetResponse responseTemp = new MLModelGetResponse(smallModel);
            ActionFuture<MLModelGetResponse> mockedFutureTemp = mock(ActionFuture.class);
            MLTaskGetResponse taskResponse = new MLTaskGetResponse(mlTask);
            ActionFuture<MLTaskGetResponse> mockedFutureResponse = mock(ActionFuture.class);
            when(client.execute(any(MLTaskGetAction.class), any(MLTaskGetRequest.class))).thenReturn(mockedFutureResponse);
            when(mockedFutureResponse.actionGet(anyLong())).thenReturn(taskResponse);
            when(mockedFutureTemp.actionGet(anyLong())).thenReturn(responseTemp);

            metricsCorrelation.initModel(smallModel, params);
            return null;
        }).when(client).execute(any(MLDeployModelAction.class), any(MLDeployModelRequest.class), isA(ActionListener.class));

        MetricsCorrelationOutput output = metricsCorrelation.execute(input);
        List<MCorrModelTensors> mlModelOutputs = output.getModelOutput();
        assert mlModelOutputs.size() == 1;
        assertNull(mlModelOutputs.get(0).getMCorrModelTensors());
    }

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

        MetricsCorrelationOutput output = metricsCorrelation.execute(input);
        List<MCorrModelTensors> mlModelOutputs = output.getModelOutput();
        assert mlModelOutputs.size() == 1;
        assertNull(mlModelOutputs.get(0).getMCorrModelTensors());
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


        MetricsCorrelationOutput output = metricsCorrelation.execute(extendedInput);
        List<MCorrModelTensors> mlModelOutputs = output.getModelOutput();
        assert mlModelOutputs.size() == 1;
        assertNotNull(mlModelOutputs.get(0).getMCorrModelTensors().get(0).getEvent_window());
        assertNotNull(mlModelOutputs.get(0).getMCorrModelTensors().get(0).getEvent_pattern());
        assertNotNull(mlModelOutputs.get(0).getMCorrModelTensors().get(0).getSuspected_metrics());
    }

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

        MetricsCorrelationOutput output = metricsCorrelation.execute(extendedInput);
        List<MCorrModelTensors> mlModelOutputs = output.getModelOutput();
        assert mlModelOutputs.size() == 1;
        assertNotNull(mlModelOutputs.get(0).getMCorrModelTensors().get(0).getEvent_window());
        assertNotNull(mlModelOutputs.get(0).getMCorrModelTensors().get(0).getEvent_pattern());
        assertNotNull(mlModelOutputs.get(0).getMCorrModelTensors().get(0).getSuspected_metrics());
    }

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

        MetricsCorrelationOutput output = metricsCorrelation.execute(extendedInput);
        List<MCorrModelTensors> mlModelOutputs = output.getModelOutput();
        assert mlModelOutputs.size() == 1;
        assertNotNull(mlModelOutputs.get(0).getMCorrModelTensors().get(0).getEvent_window());
        assertNotNull(mlModelOutputs.get(0).getMCorrModelTensors().get(0).getEvent_pattern());
        assertNotNull(mlModelOutputs.get(0).getMCorrModelTensors().get(0).getSuspected_metrics());
    }


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

        MetricsCorrelationOutput output = metricsCorrelation.execute(extendedInput);
        List<MCorrModelTensors> mlModelOutputs = output.getModelOutput();
        assert mlModelOutputs.size() == 1;
        assertNotNull(mlModelOutputs.get(0).getMCorrModelTensors().get(0).getEvent_window());
        assertNotNull(mlModelOutputs.get(0).getMCorrModelTensors().get(0).getEvent_pattern());
        assertNotNull(mlModelOutputs.get(0).getMCorrModelTensors().get(0).getSuspected_metrics());
    }

    @Test
    public void testGetModel() {
        ActionFuture<MLModelGetResponse> mockedFuture = mock(ActionFuture.class);
        MLModel smallModel = model.toBuilder().modelConfig(modelConfig).build();
        MLModelGetResponse response = new MLModelGetResponse(smallModel);
        when(client.execute(any(MLModelGetAction.class), any(MLModelGetRequest.class))).thenReturn(mockedFuture);
        when(mockedFuture.actionGet(anyLong())).thenReturn(response);
        MLModel mlModel = metricsCorrelation.getModel(modelId);
        model = MLModel.builder()
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

    @Test
    public void testSearchModel() {
        Map<String, Object> modelInfo = new HashMap<>();
        modelInfo.put(MLModel.MODEL_VERSION_FIELD, MCORR_ML_VERSION);
        modelInfo.put(MLModel.MODEL_NAME_FIELD, FunctionName.METRICS_CORRELATION.name());
        modelInfo.put(MLModel.MODEL_ID_FIELD, modelId);
       doAnswer(invocation -> {
           ActionListener<SearchResponse> searchListener = invocation.getArgument(2);
           searchResponse = createSearchModelResponse();
           searchListener.onResponse(searchResponse);
           return searchListener;
       }).when(client).execute(any(MLModelSearchAction.class), any(SearchRequest.class), isA(ActionListener.class));
       metricsCorrelation.searchModel(searchRequest, searchListener);
       verify(searchListener).onResponse(modelInfo);
    }

    @Test
    public void testSearchRequest() {
        String expectedIndex = CommonValue.ML_MODEL_INDEX;
        String[] expectedIncludes = {MLModel.MODEL_ID_FIELD, MLModel.MODEL_NAME_FIELD, MLModel.MODEL_STATE_FIELD, MLModel.MODEL_VERSION_FIELD, MLModel.MODEL_CONTENT_FIELD};
        String[] expectedExcludes = {MLModel.MODEL_CONTENT_FIELD};
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
        metricsCorrelation.execute(mock(LocalSampleCalculatorInput.class));
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

    private SearchResponse createSearchModelResponse(
    ) throws IOException {
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

    private SearchResponse createEmptySearchModelResponse(
    ) throws IOException {
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
}
