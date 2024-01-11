/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.task;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;

import org.junit.Before;
import org.junit.Ignore;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.Version;
import org.opensearch.client.Client;
import org.opensearch.cluster.node.DiscoveryNode;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.Settings;
import org.opensearch.core.action.ActionListener;
import org.opensearch.index.query.MatchAllQueryBuilder;
import org.opensearch.ml.breaker.MLCircuitBreakerService;
import org.opensearch.ml.cluster.DiscoveryNodeHelper;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.MLTask;
import org.opensearch.ml.common.dataframe.DataFrame;
import org.opensearch.ml.common.dataset.DataFrameInputDataset;
import org.opensearch.ml.common.dataset.MLInputDataset;
import org.opensearch.ml.common.dataset.SearchQueryInputDataset;
import org.opensearch.ml.common.input.MLInput;
import org.opensearch.ml.common.input.parameter.rcf.BatchRCFParams;
import org.opensearch.ml.common.transport.MLTaskResponse;
import org.opensearch.ml.common.transport.training.MLTrainingTaskRequest;
import org.opensearch.ml.common.transport.trainpredict.MLTrainAndPredictionTaskAction;
import org.opensearch.ml.engine.MLEngine;
import org.opensearch.ml.engine.encryptor.Encryptor;
import org.opensearch.ml.engine.encryptor.EncryptorImpl;
import org.opensearch.ml.engine.indices.MLInputDatasetHandler;
import org.opensearch.ml.stats.MLNodeLevelStat;
import org.opensearch.ml.stats.MLStat;
import org.opensearch.ml.stats.MLStats;
import org.opensearch.ml.stats.suppliers.CounterSupplier;
import org.opensearch.ml.utils.TestData;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.TransportService;

public class MLTrainAndPredictTaskRunnerTests extends OpenSearchTestCase {

    @Mock
    ThreadPool threadPool;
    @Mock
    ClusterService clusterService;
    @Mock
    Client client;
    @Mock
    MLTaskManager mlTaskManager;
    @Mock
    MLTaskDispatcher mlTaskDispatcher;
    @Mock
    MLCircuitBreakerService mlCircuitBreakerService;
    @Mock
    TransportService transportService;
    @Mock
    ActionListener<MLTaskResponse> listener;
    @Mock
    ExecutorService executorService;
    @Mock
    DiscoveryNodeHelper nodeHelper;

    MLStats mlStats;
    DataFrame dataFrame;
    DiscoveryNode localNode;
    DiscoveryNode remoteNode;
    MLInputDatasetHandler mlInputDatasetHandler;
    MLTrainAndPredictTaskRunner taskRunner;
    MLTrainingTaskRequest requestWithDataFrame;
    MLTrainingTaskRequest requestWithQuery;
    String indexName = "testIndex";
    String errorMessage = "test error";
    Settings settings;
    MLEngine mlEngine;
    private Encryptor encryptor;

    @Before
    public void setup() {
        encryptor = new EncryptorImpl("m+dWmfmnNRiNlOdej/QelEkvMTyH//frS2TBeS2BP4w=");
        mlEngine = new MLEngine(Path.of("/tmp/test" + randomAlphaOfLength(10)), encryptor);
        settings = Settings.builder().build();
        MockitoAnnotations.openMocks(this);
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
            new MLTrainAndPredictTaskRunner(
                threadPool,
                clusterService,
                client,
                mlTaskManager,
                mlStats,
                mlInputDatasetHandler,
                mlTaskDispatcher,
                mlCircuitBreakerService,
                nodeHelper,
                mlEngine
            )
        );
        dataFrame = TestData.constructTestDataFrame(100);

        MLInputDataset dataFrameInputDataSet = new DataFrameInputDataset(dataFrame);
        BatchRCFParams batchRCFParams = BatchRCFParams.builder().build();
        MLInput mlInputWithDataFrame = MLInput
            .builder()
            .algorithm(FunctionName.BATCH_RCF)
            .parameters(batchRCFParams)
            .inputDataset(dataFrameInputDataSet)
            .build();
        requestWithDataFrame = MLTrainingTaskRequest.builder().async(false).mlInput(mlInputWithDataFrame).build();

        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        searchSourceBuilder.query(new MatchAllQueryBuilder());
        MLInputDataset queryInputDataSet = new SearchQueryInputDataset(List.of(indexName), searchSourceBuilder);
        MLInput mlInputWithQuery = MLInput
            .builder()
            .algorithm(FunctionName.BATCH_RCF)
            .parameters(batchRCFParams)
            .inputDataset(queryInputDataSet)
            .build();
        requestWithQuery = MLTrainingTaskRequest.builder().async(false).mlInput(mlInputWithQuery).build();
    }

    @Ignore
    public void testExecuteTask_OnLocalNode() {
        doAnswer(invocation -> {
            ActionListener<DiscoveryNode> actionListener = invocation.getArgument(1);
            actionListener.onResponse(localNode);
            return null;
        }).when(mlTaskDispatcher).dispatch(any(), any());
        taskRunner.dispatchTask(FunctionName.REMOTE, requestWithDataFrame, transportService, listener);
        verify(listener).onResponse(any());
        verify(taskRunner).handleAsyncMLTaskComplete(any(MLTask.class));
    }

    @Ignore
    public void testExecuteTask_OnLocalNode_QueryInput() {
        doAnswer(invocation -> {
            ActionListener<DiscoveryNode> actionListener = invocation.getArgument(1);
            actionListener.onResponse(localNode);
            return null;
        }).when(mlTaskDispatcher).dispatch(any(), any());

        doAnswer(invocation -> {
            ActionListener<MLInputDataset> actionListener = invocation.getArgument(1);
            actionListener.onResponse(new DataFrameInputDataset(dataFrame));
            return null;
        }).when(mlInputDatasetHandler).parseSearchQueryInput(any(), any());

        taskRunner.dispatchTask(FunctionName.REMOTE, requestWithQuery, transportService, listener);
        verify(listener).onResponse(any());
        verify(taskRunner).handleAsyncMLTaskComplete(any(MLTask.class));
    }

    @Ignore
    public void testExecuteTask_OnLocalNode_QueryInput_Failure() {
        doAnswer(invocation -> {
            ActionListener<DiscoveryNode> actionListener = invocation.getArgument(1);
            actionListener.onResponse(localNode);
            return null;
        }).when(mlTaskDispatcher).dispatch(any(), any());

        doAnswer(invocation -> {
            ActionListener<MLInputDataset> actionListener = invocation.getArgument(1);
            actionListener.onFailure(new RuntimeException(errorMessage));
            return null;
        }).when(mlInputDatasetHandler).parseSearchQueryInput(any(), any());

        taskRunner.dispatchTask(FunctionName.REMOTE, requestWithQuery, transportService, listener);
        verify(listener, never()).onResponse(any());
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(listener).onFailure(argumentCaptor.capture());
        assertEquals(errorMessage, argumentCaptor.getValue().getMessage());
        verify(taskRunner).handleAsyncMLTaskFailure(any(MLTask.class), any(Exception.class));
    }

    @Ignore
    public void testExecuteTask_OnLocalNode_FailedToUpdateTask() {
        doAnswer(invocation -> {
            ActionListener<DiscoveryNode> actionListener = invocation.getArgument(1);
            actionListener.onResponse(localNode);
            return null;
        }).when(mlTaskDispatcher).dispatch(any(), any());
        doThrow(new RuntimeException(errorMessage)).when(mlTaskManager).updateTaskStateAsRunning(anyString(), anyBoolean());
        taskRunner.dispatchTask(FunctionName.REMOTE, requestWithDataFrame, transportService, listener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(listener).onFailure(argumentCaptor.capture());
        assertEquals(errorMessage, argumentCaptor.getValue().getMessage());
        verify(taskRunner).handleAsyncMLTaskFailure(any(MLTask.class), any(Exception.class));
    }

    @Ignore
    public void testExecuteTask_OnRemoteNode() {
        doAnswer(invocation -> {
            ActionListener<DiscoveryNode> actionListener = invocation.getArgument(1);
            actionListener.onResponse(remoteNode);
            return null;
        }).when(mlTaskDispatcher).dispatch(any(), any());
        taskRunner.dispatchTask(FunctionName.REMOTE, requestWithDataFrame, transportService, listener);
        verify(transportService).sendRequest(eq(remoteNode), eq(MLTrainAndPredictionTaskAction.NAME), eq(requestWithDataFrame), any());
    }

    public void testExecuteTask_FailedToDispatch() {
        doAnswer(invocation -> {
            ActionListener<DiscoveryNode> actionListener = invocation.getArgument(1);
            actionListener.onFailure(new RuntimeException(errorMessage));
            return null;
        }).when(mlTaskDispatcher).dispatch(any(), any());
        taskRunner.dispatchTask(FunctionName.REMOTE, requestWithDataFrame, transportService, listener);
        verify(listener, never()).onResponse(any());
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(listener).onFailure(argumentCaptor.capture());
        assertEquals(errorMessage, argumentCaptor.getValue().getMessage());
    }
}
