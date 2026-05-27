/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.task;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.spy;
import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_MODEL_AUTO_DEPLOY_ENABLE;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.ExpectedException;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.opensearch.OpenSearchStatusException;
import org.opensearch.Version;
import org.opensearch.action.get.GetResponse;
import org.opensearch.action.index.IndexResponse;
import org.opensearch.cluster.ClusterName;
import org.opensearch.cluster.ClusterState;
import org.opensearch.cluster.metadata.IndexMetadata;
import org.opensearch.cluster.metadata.Metadata;
import org.opensearch.cluster.node.DiscoveryNode;
import org.opensearch.cluster.node.DiscoveryNodeRole;
import org.opensearch.cluster.node.DiscoveryNodes;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.ClusterSettings;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.common.xcontent.XContentFactory;
import org.opensearch.commons.ConfigConstants;
import org.opensearch.commons.authuser.User;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.common.bytes.BytesReference;
import org.opensearch.core.common.transport.TransportAddress;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.index.get.GetResult;
import org.opensearch.index.query.MatchAllQueryBuilder;
import org.opensearch.ml.breaker.MLCircuitBreakerService;
import org.opensearch.ml.cluster.DiscoveryNodeHelper;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.MLModel;
import org.opensearch.ml.common.MLTask;
import org.opensearch.ml.common.connector.ConnectorAction;
import org.opensearch.ml.common.dataframe.DataFrame;
import org.opensearch.ml.common.dataset.DataFrameInputDataset;
import org.opensearch.ml.common.dataset.MLInputDataset;
import org.opensearch.ml.common.dataset.SearchQueryInputDataset;
import org.opensearch.ml.common.dataset.TextDocsInputDataSet;
import org.opensearch.ml.common.dataset.remote.RemoteInferenceInputDataSet;
import org.opensearch.ml.common.input.MLInput;
import org.opensearch.ml.common.input.parameter.rcf.BatchRCFParams;
import org.opensearch.ml.common.output.MLPredictionOutput;
import org.opensearch.ml.common.output.model.ModelTensor;
import org.opensearch.ml.common.output.model.ModelTensorOutput;
import org.opensearch.ml.common.output.model.ModelTensors;
import org.opensearch.ml.common.transport.MLTaskResponse;
import org.opensearch.ml.common.transport.prediction.MLPredictionTaskAction;
import org.opensearch.ml.common.transport.prediction.MLPredictionTaskRequest;
import org.opensearch.ml.engine.MLEngine;
import org.opensearch.ml.engine.Predictable;
import org.opensearch.ml.engine.encryptor.Encryptor;
import org.opensearch.ml.engine.encryptor.EncryptorImpl;
import org.opensearch.ml.engine.indices.MLInputDatasetHandler;
import org.opensearch.ml.model.MLModelManager;
import org.opensearch.ml.stats.MLNodeLevelStat;
import org.opensearch.ml.stats.MLStat;
import org.opensearch.ml.stats.MLStats;
import org.opensearch.ml.stats.suppliers.CounterSupplier;
import org.opensearch.ml.utils.TestData;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.TransportChannel;
import org.opensearch.transport.TransportService;
import org.opensearch.transport.client.Client;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

public class MLPredictTaskRunnerTests extends OpenSearchTestCase {

    public static final String USER_STRING = "myuser|role1,role2|myTenant";
    @Mock
    ThreadPool threadPool;

    @Mock
    ClusterService clusterService;

    @Mock
    Client client;

    @Mock
    MLTaskManager mlTaskManager;

    @Mock
    MLModelManager mlModelManager;

    @Mock
    DiscoveryNodeHelper nodeHelper;

    @Mock
    ExecutorService executorService;

    @Mock
    MLTaskDispatcher mlTaskDispatcher;

    @Mock
    MLCircuitBreakerService mlCircuitBreakerService;

    @Mock
    TransportService transportService;

    @Mock
    ActionListener<MLTaskResponse> listener;

    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    MLStats mlStats;
    DataFrame dataFrame;
    DiscoveryNode localNode;
    DiscoveryNode remoteNode;
    MLInputDatasetHandler mlInputDatasetHandler;
    MLPredictTaskRunner taskRunner;
    MLPredictionTaskRequest requestWithDataFrame;
    MLPredictionTaskRequest requestWithQuery;
    ThreadContext threadContext;
    String indexName = "testIndex";
    String errorMessage = "test error";
    GetResponse getResponse;
    MLInput mlInputWithDataFrame;
    MLEngine mlEngine;
    Encryptor encryptor;
    MLModel mlModel;

    @Before
    public void setup() throws IOException {
        MockitoAnnotations.openMocks(this);
        encryptor = new EncryptorImpl(null, "m+dWmfmnNRiNlOdej/QelEkvMTyH//frS2TBeS2BP4w=");
        mlEngine = new MLEngine(Path.of("/tmp/test" + randomAlphaOfLength(10)), encryptor);
        localNode = new DiscoveryNode("localNodeId", buildNewFakeTransportAddress(), Version.CURRENT);
        remoteNode = new DiscoveryNode("remoteNodeId", buildNewFakeTransportAddress(), Version.CURRENT);
        when(clusterService.localNode()).thenReturn(localNode);

        when(threadPool.executor(anyString())).thenReturn(executorService);
        doAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(0);
            runnable.run();
            return null;
        }).when(executorService).execute(any(Runnable.class));

        Map<Enum, MLStat<?>> stats = new ConcurrentHashMap<>();
        stats.put(MLNodeLevelStat.ML_EXECUTING_TASK_COUNT, new MLStat<>(false, new CounterSupplier()));
        stats.put(MLNodeLevelStat.ML_REQUEST_COUNT, new MLStat<>(false, new CounterSupplier()));
        stats.put(MLNodeLevelStat.ML_FAILURE_COUNT, new MLStat<>(false, new CounterSupplier()));
        stats.put(MLNodeLevelStat.ML_DEPLOYED_MODEL_COUNT, new MLStat<>(false, new CounterSupplier()));

        Settings settings = Settings.builder().put(ML_COMMONS_MODEL_AUTO_DEPLOY_ENABLE.getKey(), true).build();
        ClusterSettings clusterSettings = new ClusterSettings(settings, new HashSet<>(Arrays.asList(ML_COMMONS_MODEL_AUTO_DEPLOY_ENABLE)));
        when(clusterService.getClusterSettings()).thenReturn(clusterSettings);
        this.mlStats = new MLStats(stats);
        mlInputDatasetHandler = spy(new MLInputDatasetHandler(client));
        taskRunner = spy(
            new MLPredictTaskRunner(
                threadPool,
                clusterService,
                client,
                mlTaskManager,
                mlStats,
                mlInputDatasetHandler,
                mlTaskDispatcher,
                mlCircuitBreakerService,
                xContentRegistry(),
                mlModelManager,
                nodeHelper,
                mlEngine,
                settings
            )
        );

        dataFrame = TestData.constructTestDataFrame(100);

        MLInputDataset dataFrameInputDataSet = new DataFrameInputDataset(dataFrame);
        BatchRCFParams batchRCFParams = BatchRCFParams.builder().build();
        mlInputWithDataFrame = MLInput
            .builder()
            .algorithm(FunctionName.BATCH_RCF)
            .parameters(batchRCFParams)
            .inputDataset(dataFrameInputDataSet)
            .build();
        requestWithDataFrame = MLPredictionTaskRequest.builder().modelId("111").mlInput(mlInputWithDataFrame).build();

        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        searchSourceBuilder.query(new MatchAllQueryBuilder());
        MLInputDataset queryInputDataSet = new SearchQueryInputDataset(ImmutableList.of(indexName), searchSourceBuilder);
        MLInput mlInputWithQuery = MLInput
            .builder()
            .algorithm(FunctionName.BATCH_RCF)
            .parameters(batchRCFParams)
            .inputDataset(queryInputDataSet)
            .build();
        requestWithQuery = MLPredictionTaskRequest.builder().modelId("111").mlInput(mlInputWithQuery).build();

        when(client.threadPool()).thenReturn(threadPool);
        threadContext = new ThreadContext(settings);
        threadContext.putTransient(ConfigConstants.OPENSEARCH_SECURITY_USER_INFO_THREAD_CONTEXT, USER_STRING);
        when(client.threadPool()).thenReturn(threadPool);
        when(threadPool.getThreadContext()).thenReturn(threadContext);

        mlModel = MLModel
            .builder()
            .user(User.parse(USER_STRING))
            .version("1.1.1")
            .name("test")
            .algorithm(FunctionName.BATCH_RCF)
            .content("content")
            .isHidden(true)
            .build();
        XContentBuilder content = mlModel.toXContent(XContentFactory.jsonBuilder(), ToXContent.EMPTY_PARAMS);
        BytesReference bytesReference = BytesReference.bytes(content);

        GetResult getResult = new GetResult(indexName, "1.1.1", 111l, 111l, 111l, true, bytesReference, null, null);
        getResponse = new GetResponse(getResult);
        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(1);
            listener.onResponse(false);
            return null;
        }).when(mlModelManager).checkMaxBatchJobTask(any(MLTask.class), isA(ActionListener.class));
    }

    public void testExecuteTask_OnLocalNode() {
        setupMocks(true, false, false, false);

        taskRunner.dispatchTask(FunctionName.BATCH_RCF, requestWithDataFrame, transportService, listener);
        verify(mlInputDatasetHandler, never()).parseSearchQueryInput(any(), any());
        verify(mlTaskManager).add(any(MLTask.class));
        verify(client).get(any(), any());
        verify(mlTaskManager).remove(anyString());
    }

    public void testExecuteTask_OnLocalNode_QueryInput() {
        setupMocks(true, false, false, false);

        taskRunner.dispatchTask(FunctionName.BATCH_RCF, requestWithQuery, transportService, listener);
        verify(mlInputDatasetHandler).parseSearchQueryInput(any(), any());
        verify(mlTaskManager).add(any(MLTask.class));
        verify(client).get(any(), any());
        verify(mlTaskManager).remove(anyString());
    }

    public void testExecuteTask_OnLocalNode_RemoteModelAutoDeploy() {
        setupMocks(true, false, false, false);
        doAnswer(invocation -> {
            ActionListener<MLModel> actionListener = invocation.getArgument(2);
            actionListener.onResponse(mlModel);
            return null;
        }).when(mlModelManager).getModel(any(), any(), any());
        when(mlModelManager.addModelToAutoDeployCache("111", mlModel)).thenReturn(mlModel);
        taskRunner.dispatchTask(FunctionName.REMOTE, requestWithDataFrame, transportService, listener);
        verify(client).execute(any(), any(), any());
        verify(mlTaskDispatcher).dispatchPredictTask(any(), any());
    }

    public void testExecuteTask_OnLocalNode_QueryInput_Failure() {
        setupMocks(true, true, false, false);

        taskRunner.dispatchTask(FunctionName.BATCH_RCF, requestWithQuery, transportService, listener);
        verify(mlInputDatasetHandler).parseSearchQueryInput(any(), any());
        verify(mlTaskManager, never()).add(any(MLTask.class));
        verify(client, never()).get(any(), any());
    }

    public void testExecuteTask_NoPermission() {
        setupMocks(true, true, false, false);
        threadContext.stashContext();
        threadContext.putTransient(ConfigConstants.OPENSEARCH_SECURITY_USER_INFO_THREAD_CONTEXT, "test_user|test_role|test_tenant");
        taskRunner.dispatchTask(FunctionName.BATCH_RCF, requestWithDataFrame, transportService, listener);
        verify(mlTaskManager).add(any(MLTask.class));
        verify(mlTaskManager).remove(anyString());
        verify(client).get(any(), any());
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(listener).onFailure(argumentCaptor.capture());
        assertEquals("User: test_user does not have permissions to run predict by model: 111", argumentCaptor.getValue().getMessage());
    }

    public void testExecuteTask_OnRemoteNode() {
        setupMocks(false, false, false, false);
        taskRunner.dispatchTask(FunctionName.BATCH_RCF, requestWithDataFrame, transportService, listener);
        verify(transportService).sendRequest(eq(remoteNode), eq(MLPredictionTaskAction.NAME), eq(requestWithDataFrame), any());
    }

    public void testExecuteTask_OnLocalNode_GetModelFail() {
        setupMocks(true, false, true, false);

        taskRunner.dispatchTask(FunctionName.BATCH_RCF, requestWithDataFrame, transportService, listener);
        verify(mlInputDatasetHandler, never()).parseSearchQueryInput(any(), any());
        verify(mlTaskManager).add(any(MLTask.class));
        verify(client).get(any(), any());
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(listener).onFailure(argumentCaptor.capture());
        assertEquals(errorMessage, argumentCaptor.getValue().getMessage());
    }

    public void testExecuteTask_OnLocalNode_NullModelIdException() {
        setupMocks(true, false, false, false);
        requestWithDataFrame = MLPredictionTaskRequest.builder().mlInput(mlInputWithDataFrame).build();

        taskRunner.dispatchTask(FunctionName.BATCH_RCF, requestWithDataFrame, transportService, listener);
        verify(mlInputDatasetHandler, never()).parseSearchQueryInput(any(), any());
        verify(mlTaskManager).add(any(MLTask.class));
        verify(client, never()).get(any(), any());
        verify(mlTaskManager).remove(anyString());
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(IllegalArgumentException.class);
        verify(listener).onFailure(argumentCaptor.capture());
        assertEquals("ModelId is invalid", argumentCaptor.getValue().getMessage());
    }

    public void testExecuteTask_OnLocalNode_remoteModel_success() {
        setupMocks(true, false, false, false);
        TextDocsInputDataSet textDocsInputDataSet = new TextDocsInputDataSet(List.of("hello", "world"), null);
        MLPredictionTaskRequest textDocsInputRequest = MLPredictionTaskRequest
            .builder()
            .modelId("test_model")
            .mlInput(MLInput.builder().algorithm(FunctionName.REMOTE).inputDataset(textDocsInputDataSet).build())
            .build();
        Predictable predictor = mock(Predictable.class);
        when(predictor.isModelReady()).thenReturn(true);
        doAnswer(invocation -> {
            ActionListener<MLTaskResponse> actionListener = invocation.getArgument(1);
            actionListener
                .onResponse(MLTaskResponse.builder().output(ModelTensorOutput.builder().mlModelOutputs(List.of()).build()).build());
            return null;
        }).when(predictor).asyncPredict(any(), any(), any());
        when(mlModelManager.getPredictor(anyString())).thenReturn(predictor);
        when(mlModelManager.getWorkerNodes(anyString(), eq(FunctionName.REMOTE), eq(true))).thenReturn(new String[] { "node1" });
        taskRunner.dispatchTask(FunctionName.REMOTE, textDocsInputRequest, transportService, listener);
        verify(client, never()).get(any(), any());
        ArgumentCaptor<MLTaskResponse> argumentCaptor = ArgumentCaptor.forClass(MLTaskResponse.class);
        verify(listener).onResponse(argumentCaptor.capture());
        assert argumentCaptor.getValue().getOutput() instanceof ModelTensorOutput;
    }

    public void testExecuteTask_OnLocalNode_localModel_success() {
        setupMocks(true, false, false, false);
        TextDocsInputDataSet textDocsInputDataSet = new TextDocsInputDataSet(List.of("hello", "world"), null);
        MLPredictionTaskRequest textDocsInputRequest = MLPredictionTaskRequest
            .builder()
            .modelId("test_model")
            .mlInput(MLInput.builder().algorithm(FunctionName.TEXT_EMBEDDING).inputDataset(textDocsInputDataSet).build())
            .build();
        Predictable predictor = mock(Predictable.class);
        when(predictor.isModelReady()).thenReturn(true);
        when(mlModelManager.getPredictor(anyString())).thenReturn(predictor);
        when(mlModelManager.getWorkerNodes(anyString(), eq(FunctionName.TEXT_EMBEDDING), eq(true))).thenReturn(new String[] { "node1" });
        when(mlModelManager.trackPredictDuration(anyString(), any())).thenReturn(mock(MLPredictionOutput.class));
        taskRunner.dispatchTask(FunctionName.TEXT_EMBEDDING, textDocsInputRequest, transportService, listener);
        verify(client, never()).get(any(), any());
        ArgumentCaptor<MLTaskResponse> argumentCaptor = ArgumentCaptor.forClass(MLTaskResponse.class);
        verify(listener).onResponse(argumentCaptor.capture());
        assert argumentCaptor.getValue().getOutput() instanceof MLPredictionOutput;
    }

    public void testExecuteTask_OnLocalNode_prediction_exception() {
        setupMocks(true, false, false, false);
        TextDocsInputDataSet textDocsInputDataSet = new TextDocsInputDataSet(List.of("hello", "world"), null);
        MLPredictionTaskRequest textDocsInputRequest = MLPredictionTaskRequest
            .builder()
            .modelId("test_model")
            .mlInput(MLInput.builder().algorithm(FunctionName.TEXT_EMBEDDING).inputDataset(textDocsInputDataSet).build())
            .build();
        Predictable predictable = mock(Predictable.class);
        when(mlModelManager.getPredictor(anyString())).thenReturn(predictable);
        when(predictable.isModelReady()).thenThrow(new RuntimeException("runtime exception"));
        when(mlModelManager.getWorkerNodes(anyString(), eq(FunctionName.TEXT_EMBEDDING), eq(true))).thenReturn(new String[] { "node1" });
        when(mlModelManager.trackPredictDuration(anyString(), any())).thenReturn(mock(MLPredictionOutput.class));
        taskRunner.dispatchTask(FunctionName.TEXT_EMBEDDING, textDocsInputRequest, transportService, listener);
        verify(client, never()).get(any(), any());
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(listener).onFailure(argumentCaptor.capture());
        assert argumentCaptor.getValue() instanceof RuntimeException;
        assertEquals("runtime exception", argumentCaptor.getValue().getMessage());
    }

    public void testExecuteTask_OnLocalNode_NullGetResponse() {
        setupMocks(true, false, false, true);

        taskRunner.dispatchTask(FunctionName.BATCH_RCF, requestWithDataFrame, transportService, listener);
        verify(mlInputDatasetHandler, never()).parseSearchQueryInput(any(), any());
        verify(mlTaskManager).add(any(MLTask.class));
        verify(client).get(any(), any());
        verify(mlTaskManager).remove(anyString());
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(listener).onFailure(argumentCaptor.capture());
        assertEquals("No model found, please check the modelId.", argumentCaptor.getValue().getMessage());
    }

    public void testValidateModelTensorOutputSuccess() {
        ModelTensor modelTensor = ModelTensor
            .builder()
            .name("response")
            .dataAsMap(Map.of("id", "chatcmpl-9JUSY2myXUjGBUrG0GO5niEAY5NKm"))
            .build();
        Map<String, String> modelInterface = Map
            .of(
                "output",
                "{\"properties\":{\"inference_results\":{\"description\":\"This is a test description field\"," + "\"type\":\"array\"}}}"
            );
        ModelTensorOutput modelTensorOutput = ModelTensorOutput
            .builder()
            .mlModelOutputs(List.of(ModelTensors.builder().mlModelTensors(List.of(modelTensor)).build()))
            .build();
        when(mlModelManager.getModelInterface(any())).thenReturn(modelInterface);
        taskRunner.validateOutputSchema("testId", modelTensorOutput);
    }

    public void testValidateBatchPredictionSuccess() throws IOException {
        setupMocks(true, false, false, false);
        RemoteInferenceInputDataSet remoteInferenceInputDataSet = RemoteInferenceInputDataSet
            .builder()
            .parameters(
                Map
                    .of(
                        "messages",
                        "[{\\\"role\\\":\\\"system\\\",\\\"content\\\":\\\"You are a helpful assistant.\\\"},"
                            + "{\\\"role\\\":\\\"user\\\",\\\"content\\\":\\\"Hello!\\\"}]"
                    )
            )
            .actionType(ConnectorAction.ActionType.BATCH_PREDICT)
            .build();
        MLPredictionTaskRequest remoteInputRequest = MLPredictionTaskRequest
            .builder()
            .modelId("test_model")
            .mlInput(MLInput.builder().algorithm(FunctionName.REMOTE).inputDataset(remoteInferenceInputDataSet).build())
            .build();
        Predictable predictor = mock(Predictable.class);
        when(predictor.isModelReady()).thenReturn(true);
        ModelTensor modelTensor = ModelTensor
            .builder()
            .name("response")
            .dataAsMap(Map.of("TransformJobArn", "arn:aws:sagemaker:us-east-1:802041417063:transform-job/batch-transform-01"))
            .build();
        Map<String, String> modelInterface = Map
            .of(
                "output",
                "{\"properties\":{\"inference_results\":{\"description\":\"This is a test description field\"," + "\"type\":\"array\"}}}"
            );
        ModelTensors modelTensors = ModelTensors.builder().statusCode(200).mlModelTensors(List.of(modelTensor)).statusCode(200).build();
        modelTensors.setStatusCode(200);
        ModelTensorOutput modelTensorOutput = ModelTensorOutput.builder().mlModelOutputs(List.of(modelTensors)).build();
        doAnswer(invocation -> {
            ActionListener<MLTaskResponse> actionListener = invocation.getArgument(1);
            actionListener.onResponse(MLTaskResponse.builder().output(modelTensorOutput).build());
            return null;
        }).when(predictor).asyncPredict(any(), any(), any());

        IndexResponse indexResponse = mock(IndexResponse.class);
        when(indexResponse.getId()).thenReturn("mockTaskId");
        doAnswer(invocation -> {
            ActionListener<IndexResponse> listener = invocation.getArgument(1);
            listener.onResponse(indexResponse);
            return null;
        }).when(mlTaskManager).createMLTask(any(MLTask.class), Mockito.isA(ActionListener.class));

        when(mlModelManager.getModelInterface(any())).thenReturn(modelInterface);

        when(mlModelManager.getPredictor(anyString())).thenReturn(predictor);
        when(mlModelManager.getWorkerNodes(anyString(), eq(FunctionName.REMOTE), eq(true))).thenReturn(new String[] { "node1" });

        Settings indexSettings = Settings
            .builder()
            .put(IndexMetadata.SETTING_NUMBER_OF_SHARDS, 1)
            .put(IndexMetadata.SETTING_NUMBER_OF_REPLICAS, 1)
            .put(IndexMetadata.SETTING_VERSION_CREATED, Version.CURRENT)
            .build();
        final Settings.Builder existingSettings = Settings.builder().put(indexSettings).put(IndexMetadata.SETTING_INDEX_UUID, "test2UUID");

        IndexMetadata indexMetaData = IndexMetadata.builder(".ml_commons_task_polling_job").settings(existingSettings).build();

        final Map<String, IndexMetadata> indices = Map.of(indexName, indexMetaData);
        Metadata metadata = new Metadata.Builder().indices(indices).build();
        DiscoveryNode node = new DiscoveryNode(
            "node",
            new TransportAddress(TransportAddress.META_ADDRESS, new AtomicInteger().incrementAndGet()),
            new HashMap<>(),
            ImmutableSet.of(DiscoveryNodeRole.DATA_ROLE),
            Version.CURRENT
        );
        ClusterState state = new ClusterState(
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
        ;
        when(clusterService.state()).thenReturn(state);
        taskRunner.dispatchTask(FunctionName.REMOTE, remoteInputRequest, transportService, listener);
        verify(client, never()).get(any(), any());
        ArgumentCaptor<MLTaskResponse> argumentCaptor = ArgumentCaptor.forClass(MLTaskResponse.class);
        verify(listener).onResponse(argumentCaptor.capture());
    }

    public void testValidateBatchPredictionFailure() throws IOException {
        setupMocks(true, false, false, false);
        RemoteInferenceInputDataSet remoteInferenceInputDataSet = RemoteInferenceInputDataSet
            .builder()
            .parameters(
                Map
                    .of(
                        "messages",
                        "[{\\\"role\\\":\\\"system\\\",\\\"content\\\":\\\"You are a helpful assistant.\\\"},"
                            + "{\\\"role\\\":\\\"user\\\",\\\"content\\\":\\\"Hello!\\\"}]"
                    )
            )
            .actionType(ConnectorAction.ActionType.BATCH_PREDICT)
            .build();
        MLPredictionTaskRequest remoteInputRequest = MLPredictionTaskRequest
            .builder()
            .modelId("test_model")
            .mlInput(MLInput.builder().algorithm(FunctionName.REMOTE).inputDataset(remoteInferenceInputDataSet).build())
            .build();
        Predictable predictor = mock(Predictable.class);
        when(predictor.isModelReady()).thenReturn(true);
        doAnswer(invocation -> {
            ActionListener<MLTaskResponse> actionListener = invocation.getArgument(1);
            actionListener
                .onResponse(MLTaskResponse.builder().output(ModelTensorOutput.builder().mlModelOutputs(List.of()).build()).build());
            return null;
        }).when(predictor).asyncPredict(any(), any(), any());

        IndexResponse indexResponse = mock(IndexResponse.class);
        when(indexResponse.getId()).thenReturn("mockTaskId");
        doAnswer(invocation -> {
            ActionListener<IndexResponse> listener = invocation.getArgument(1);
            listener.onResponse(indexResponse);
            return null;
        }).when(mlTaskManager).createMLTask(any(MLTask.class), Mockito.isA(ActionListener.class));

        when(mlModelManager.getPredictor(anyString())).thenReturn(predictor);
        when(mlModelManager.getWorkerNodes(anyString(), eq(FunctionName.REMOTE), eq(true))).thenReturn(new String[] { "node1" });
        taskRunner.dispatchTask(FunctionName.REMOTE, remoteInputRequest, transportService, listener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(listener).onFailure(argumentCaptor.capture());
        assertEquals("Unable to create batch transform job", argumentCaptor.getValue().getMessage());
    }

    public void testValidateModelTensorOutputFailed() {
        exceptionRule.expect(OpenSearchStatusException.class);
        ModelTensor modelTensor = ModelTensor
            .builder()
            .name("response")
            .dataAsMap(Map.of("id", "chatcmpl-9JUSY2myXUjGBUrG0GO5niEAY5NKm"))
            .build();
        Map<String, String> modelInterface = Map
            .of(
                "output",
                "{\"properties\":{\"inference_results\":{\"description\":\"This is a test description field\"," + "\"type\":\"string\"}}}"
            );
        ModelTensorOutput modelTensorOutput = ModelTensorOutput
            .builder()
            .mlModelOutputs(List.of(ModelTensors.builder().mlModelTensors(List.of(modelTensor)).build()))
            .build();
        when(mlModelManager.getModelInterface(any())).thenReturn(modelInterface);
        taskRunner.validateOutputSchema("testId", modelTensorOutput);
    }

    public void testIsStreamingRequest() {
        MLInput mlInput = MLInput
            .builder()
            .algorithm(FunctionName.REMOTE)
            .inputDataset(new TextDocsInputDataSet(List.of("test"), null))
            .build();
        MLPredictionTaskRequest request = MLPredictionTaskRequest.builder().modelId("test").mlInput(mlInput).build();

        try {
            java.lang.reflect.Method method = MLPredictTaskRunner.class
                .getDeclaredMethod("isStreamingRequest", MLPredictionTaskRequest.class);
            method.setAccessible(true);
            boolean result = (boolean) method.invoke(taskRunner, request);
            assertFalse(result);
        } catch (Exception e) {
            fail("Failed to test isStreamingRequest: " + e.getMessage());
        }
    }

    public void testIsStreamingRequestWithChannel() {
        MLInput mlInput = MLInput
            .builder()
            .algorithm(FunctionName.REMOTE)
            .inputDataset(new TextDocsInputDataSet(List.of("test"), null))
            .build();
        MLPredictionTaskRequest request = MLPredictionTaskRequest.builder().modelId("test").mlInput(mlInput).build();
        request.setStreamingChannel(mock(TransportChannel.class));

        try {
            java.lang.reflect.Method method = MLPredictTaskRunner.class
                .getDeclaredMethod("isStreamingRequest", MLPredictionTaskRequest.class);
            method.setAccessible(true);
            boolean result = (boolean) method.invoke(taskRunner, request);
            assertTrue(result);
        } catch (Exception e) {
            fail("Failed to test isStreamingRequest: " + e.getMessage());
        }
    }

    public void testValidateBatchPredictionSuccess_InitPollingJob() throws IOException {
        setupMocks(true, false, false, false);
        RemoteInferenceInputDataSet remoteInferenceInputDataSet = RemoteInferenceInputDataSet
            .builder()
            .parameters(
                Map
                    .of(
                        "messages",
                        "[{\\\"role\\\":\\\"system\\\",\\\"content\\\":\\\"You are a helpful assistant.\\\"},"
                            + "{\\\"role\\\":\\\"user\\\",\\\"content\\\":\\\"Hello!\\\"}]"
                    )
            )
            .actionType(ConnectorAction.ActionType.BATCH_PREDICT)
            .build();
        MLPredictionTaskRequest remoteInputRequest = MLPredictionTaskRequest
            .builder()
            .modelId("test_model")
            .mlInput(MLInput.builder().algorithm(FunctionName.REMOTE).inputDataset(remoteInferenceInputDataSet).build())
            .build();
        Predictable predictor = mock(Predictable.class);
        when(predictor.isModelReady()).thenReturn(true);
        ModelTensor modelTensor = ModelTensor
            .builder()
            .name("response")
            .dataAsMap(Map.of("TransformJobArn", "arn:aws:sagemaker:us-east-1:802041417063:transform-job/batch-transform-01"))
            .build();
        Map<String, String> modelInterface = Map
            .of(
                "output",
                "{\"properties\":{\"inference_results\":{\"description\":\"This is a test description field\"," + "\"type\":\"array\"}}}"
            );
        ModelTensors modelTensors = ModelTensors.builder().statusCode(200).mlModelTensors(List.of(modelTensor)).statusCode(200).build();
        modelTensors.setStatusCode(200);
        ModelTensorOutput modelTensorOutput = ModelTensorOutput.builder().mlModelOutputs(List.of(modelTensors)).build();
        doAnswer(invocation -> {
            ActionListener<MLTaskResponse> actionListener = invocation.getArgument(1);
            actionListener.onResponse(MLTaskResponse.builder().output(modelTensorOutput).build());
            return null;
        }).when(predictor).asyncPredict(any(), any(), any());

        IndexResponse indexResponse = mock(IndexResponse.class);
        when(indexResponse.getId()).thenReturn("mockTaskId");
        doAnswer(invocation -> {
            ActionListener<IndexResponse> listener = invocation.getArgument(1);
            listener.onResponse(indexResponse);
            return null;
        }).when(mlTaskManager).createMLTask(any(MLTask.class), Mockito.isA(ActionListener.class));

        when(mlModelManager.getModelInterface(any())).thenReturn(modelInterface);

        when(mlModelManager.getPredictor(anyString())).thenReturn(predictor);
        when(mlModelManager.getWorkerNodes(anyString(), eq(FunctionName.REMOTE), eq(true))).thenReturn(new String[] { "node1" });

        // Mocking clusterService to simulate missing TASK_POLLING_JOB_INDEX
        Metadata metadata = new Metadata.Builder().indices(Map.of()).build();
        DiscoveryNode node = new DiscoveryNode(
            "node",
            new TransportAddress(TransportAddress.META_ADDRESS, new AtomicInteger().incrementAndGet()),
            new HashMap<>(),
            ImmutableSet.of(DiscoveryNodeRole.DATA_ROLE),
            Version.CURRENT
        );
        ClusterState state = new ClusterState(
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
        ;
        when(clusterService.state()).thenReturn(state);

        taskRunner.dispatchTask(FunctionName.REMOTE, remoteInputRequest, transportService, listener);
        verify(mlTaskManager).startTaskPollingJob();
    }

    private void setupMocks(boolean runOnLocalNode, boolean failedToParseQueryInput, boolean failedToGetModel, boolean nullGetResponse) {
        doAnswer(invocation -> {
            ActionListener<DiscoveryNode> actionListener = invocation.getArgument(1);
            if (runOnLocalNode) {
                actionListener.onResponse(localNode);
            } else {
                actionListener.onResponse(remoteNode);
            }
            return null;
        }).when(mlTaskDispatcher).dispatch(any(), any());

        doAnswer(invocation -> {
            ActionListener<DiscoveryNode> actionListener = invocation.getArgument(1);
            if (runOnLocalNode) {
                actionListener.onResponse(localNode);
            } else {
                actionListener.onResponse(remoteNode);
            }
            return null;
        }).when(mlTaskDispatcher).dispatchPredictTask(any(), any());

        if (failedToParseQueryInput) {
            doAnswer(invocation -> {
                ActionListener<MLInputDataset> actionListener = invocation.getArgument(1);
                actionListener.onFailure(new RuntimeException(errorMessage));
                return null;
            }).when(mlInputDatasetHandler).parseSearchQueryInput(any(), any());
        } else {
            doAnswer(invocation -> {
                ActionListener<MLInputDataset> actionListener = invocation.getArgument(1);
                actionListener.onResponse(new DataFrameInputDataset(dataFrame));
                return null;
            }).when(mlInputDatasetHandler).parseSearchQueryInput(any(), any());
        }

        if (nullGetResponse) {
            getResponse = null;
        }

        if (failedToGetModel) {
            doAnswer(invocation -> {
                ActionListener<GetResponse> actionListener = invocation.getArgument(1);
                actionListener.onFailure(new RuntimeException(errorMessage));
                return null;
            }).when(client).get(any(), any());
        } else {
            doAnswer(invocation -> {
                ActionListener<GetResponse> actionListener = invocation.getArgument(1);
                actionListener.onResponse(getResponse);
                return null;
            }).when(client).get(any(), any());
        }
    }

    public void testShouldTrackRemoteFailure() {
        // Test IllegalArgumentException - should not track
        assertFalse(taskRunner.shouldTrackRemoteFailure(new IllegalArgumentException("Invalid argument")));

        // Test OpenSearchStatusException with 4xx status codes - should not track
        assertFalse(taskRunner.shouldTrackRemoteFailure(new OpenSearchStatusException("Bad request", RestStatus.BAD_REQUEST)));
        assertFalse(taskRunner.shouldTrackRemoteFailure(new OpenSearchStatusException("Unauthorized", RestStatus.UNAUTHORIZED)));
        assertFalse(taskRunner.shouldTrackRemoteFailure(new OpenSearchStatusException("Forbidden", RestStatus.FORBIDDEN)));
        assertFalse(taskRunner.shouldTrackRemoteFailure(new OpenSearchStatusException("Not found", RestStatus.NOT_FOUND)));

        // Test OpenSearchStatusException with 5xx status codes - should track
        assertTrue(taskRunner.shouldTrackRemoteFailure(new OpenSearchStatusException("Server error", RestStatus.INTERNAL_SERVER_ERROR)));
        assertTrue(
            taskRunner.shouldTrackRemoteFailure(new OpenSearchStatusException("Service unavailable", RestStatus.SERVICE_UNAVAILABLE))
        );

        // Test other exceptions - should track
        assertTrue(taskRunner.shouldTrackRemoteFailure(new RuntimeException("Runtime error")));
        assertTrue(taskRunner.shouldTrackRemoteFailure(new IOException("IO error")));
    }
}
