/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.task;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.spy;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;

import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.ExpectedException;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.Version;
import org.opensearch.action.get.GetResponse;
import org.opensearch.client.Client;
import org.opensearch.cluster.node.DiscoveryNode;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.common.xcontent.XContentFactory;
import org.opensearch.commons.ConfigConstants;
import org.opensearch.commons.authuser.User;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.common.bytes.BytesReference;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.index.get.GetResult;
import org.opensearch.index.query.MatchAllQueryBuilder;
import org.opensearch.ml.breaker.MLCircuitBreakerService;
import org.opensearch.ml.cluster.DiscoveryNodeHelper;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.MLModel;
import org.opensearch.ml.common.MLTask;
import org.opensearch.ml.common.dataframe.DataFrame;
import org.opensearch.ml.common.dataset.DataFrameInputDataset;
import org.opensearch.ml.common.dataset.MLInputDataset;
import org.opensearch.ml.common.dataset.SearchQueryInputDataset;
import org.opensearch.ml.common.dataset.TextDocsInputDataSet;
import org.opensearch.ml.common.input.MLInput;
import org.opensearch.ml.common.input.parameter.rcf.BatchRCFParams;
import org.opensearch.ml.common.output.MLPredictionOutput;
import org.opensearch.ml.common.output.model.ModelTensorOutput;
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
import org.opensearch.transport.TransportService;

import com.google.common.collect.ImmutableList;

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

    @Before
    public void setup() throws IOException {
        MockitoAnnotations.openMocks(this);
        encryptor = new EncryptorImpl("m+dWmfmnNRiNlOdej/QelEkvMTyH//frS2TBeS2BP4w=");
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
                mlEngine
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
        Settings settings = Settings.builder().build();
        threadContext = new ThreadContext(settings);
        threadContext.putTransient(ConfigConstants.OPENSEARCH_SECURITY_USER_INFO_THREAD_CONTEXT, USER_STRING);
        when(client.threadPool()).thenReturn(threadPool);
        when(threadPool.getThreadContext()).thenReturn(threadContext);

        MLModel mlModel = MLModel
            .builder()
            .user(User.parse(USER_STRING))
            .version("1.1.1")
            .name("test")
            .algorithm(FunctionName.BATCH_RCF)
            .content("content")
            .build();
        XContentBuilder content = mlModel.toXContent(XContentFactory.jsonBuilder(), ToXContent.EMPTY_PARAMS);
        BytesReference bytesReference = BytesReference.bytes(content);

        GetResult getResult = new GetResult(indexName, "1.1.1", 111l, 111l, 111l, true, bytesReference, null, null);
        getResponse = new GetResponse(getResult);
    }

    public void testExecuteTask_OnLocalNode() {
        setupMocks(true, false, false, false);

        taskRunner.dispatchTask(FunctionName.BATCH_RCF, requestWithDataFrame, transportService, listener);
        verify(mlInputDatasetHandler, never()).parseSearchQueryInput(any(), any());
        verify(mlTaskManager).add(any(MLTask.class));
        verify(client).get(any(), any());
        verify(mlTaskManager).remove(anyString());
    }

    public void testExecuteTask_OnLocalNode_RemoteModel() {
        setupMocks(true, false, false, false);

        taskRunner.dispatchTask(FunctionName.REMOTE, requestWithDataFrame, transportService, listener);
        verify(mlInputDatasetHandler, never()).parseSearchQueryInput(any(), any());
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(listener).onFailure(argumentCaptor.capture());
        assertTrue(argumentCaptor.getValue().getMessage().contains("Model not ready yet."));
        verify(mlTaskManager, never()).add(any(MLTask.class));
        verify(client, never()).get(any(), any());
    }

    public void testExecuteTask_OnLocalNode_QueryInput() {
        setupMocks(true, false, false, false);

        taskRunner.dispatchTask(FunctionName.BATCH_RCF, requestWithQuery, transportService, listener);
        verify(mlInputDatasetHandler).parseSearchQueryInput(any(), any());
        verify(mlTaskManager).add(any(MLTask.class));
        verify(client).get(any(), any());
        verify(mlTaskManager).remove(anyString());
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
        }).when(predictor).asyncPredict(any(), any());
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
}
