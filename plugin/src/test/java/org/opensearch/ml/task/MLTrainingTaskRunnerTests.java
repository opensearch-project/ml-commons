/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.task;

import static org.mockito.ArgumentMatchers.any;
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
import org.opensearch.action.index.IndexResponse;
import org.opensearch.client.Client;
import org.opensearch.cluster.node.DiscoveryNode;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.index.Index;
import org.opensearch.core.index.shard.ShardId;
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
import org.opensearch.ml.common.transport.training.MLTrainingTaskAction;
import org.opensearch.ml.common.transport.training.MLTrainingTaskRequest;
import org.opensearch.ml.engine.MLEngine;
import org.opensearch.ml.engine.encryptor.Encryptor;
import org.opensearch.ml.engine.encryptor.EncryptorImpl;
import org.opensearch.ml.engine.indices.MLIndicesHandler;
import org.opensearch.ml.engine.indices.MLInputDatasetHandler;
import org.opensearch.ml.stats.MLNodeLevelStat;
import org.opensearch.ml.stats.MLStat;
import org.opensearch.ml.stats.MLStats;
import org.opensearch.ml.stats.suppliers.CounterSupplier;
import org.opensearch.ml.utils.TestData;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.NodeNotConnectedException;
import org.opensearch.transport.TransportService;

public class MLTrainingTaskRunnerTests extends OpenSearchTestCase {
    @Mock
    ThreadPool threadPool;
    @Mock
    ClusterService clusterService;
    @Mock
    Client client;
    @Mock
    MLTaskManager mlTaskManager;
    @Mock
    MLIndicesHandler mlIndicesHandler;
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
    MLTrainingTaskRunner taskRunner;
    MLTrainingTaskRequest requestWithDataFrame;
    MLTrainingTaskRequest asyncRequestWithDataFrame;
    MLTrainingTaskRequest requestWithQuery;
    MLTrainingTaskRequest asyncRequestWithQuery;
    String indexName = "testIndex";
    String errorMessage = "test error";
    ThreadContext threadContext;

    MLEngine mlEngine;
    Encryptor encryptor;

    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);
        encryptor = new EncryptorImpl("m+dWmfmnNRiNlOdej/QelEkvMTyH//frS2TBeS2BP4w=");
        mlEngine = new MLEngine(Path.of("/tmp/djl-cache_" + randomAlphaOfLength(10)), encryptor);
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
            new MLTrainingTaskRunner(
                threadPool,
                clusterService,
                client,
                mlTaskManager,
                mlStats,
                mlIndicesHandler,
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
        asyncRequestWithDataFrame = MLTrainingTaskRequest.builder().async(true).mlInput(mlInputWithDataFrame).build();

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
        asyncRequestWithQuery = MLTrainingTaskRequest.builder().async(true).mlInput(mlInputWithQuery).build();

        when(client.threadPool()).thenReturn(threadPool);
        Settings settings = Settings.builder().build();
        threadContext = new ThreadContext(settings);
        when(client.threadPool()).thenReturn(threadPool);
        when(threadPool.getThreadContext()).thenReturn(threadContext);
    }

    @Ignore
    public void testExecuteTask_OnLocalNode_SyncRequest() {
        setupMocks(true, false, false, false);
        taskRunner.dispatchTask(FunctionName.REMOTE, requestWithDataFrame, transportService, listener);
        verify(listener).onResponse(any());
        verify(mlTaskManager, never()).createMLTask(any(MLTask.class), any());
        verify(mlTaskManager).add(any(MLTask.class));
        verify(mlTaskManager).remove(anyString());
        verify(mlIndicesHandler).initModelIndexIfAbsent(any());
        verify(client).index(any(), any());
    }

    @Ignore
    public void testExecuteTask_OnLocalNode_SyncRequest_QueryInput() {
        setupMocks(true, false, false, false);
        taskRunner.dispatchTask(FunctionName.REMOTE, requestWithQuery, transportService, listener);
        verify(listener).onResponse(any());
        verify(mlTaskManager, never()).createMLTask(any(MLTask.class), any());
        verify(mlTaskManager).add(any(MLTask.class));
        verify(mlTaskManager).remove(anyString());
        verify(mlIndicesHandler).initModelIndexIfAbsent(any());
        verify(client).index(any(), any());
    }

    @Ignore
    public void testExecuteTask_OnLocalNode_AsyncRequest_QueryInput_Failure() {
        setupMocks(true, false, false, true);
        taskRunner.dispatchTask(FunctionName.REMOTE, asyncRequestWithQuery, transportService, listener);
        verify(listener).onResponse(any());
        verify(mlTaskManager).createMLTask(any(MLTask.class), any());
        verify(mlTaskManager).add(any(MLTask.class));
        verify(mlTaskManager).remove(anyString());
        verify(mlIndicesHandler, never()).initModelIndexIfAbsent(any());
        verify(client, never()).index(any(), any());
        verify(taskRunner).handleAsyncMLTaskFailure(any(MLTask.class), any(Exception.class));
    }

    @Ignore
    public void testExecuteTask_OnLocalNode_AsyncRequest() {
        setupMocks(true, false, false, false);
        taskRunner.dispatchTask(FunctionName.REMOTE, asyncRequestWithDataFrame, transportService, listener);
        verify(listener).onResponse(any());
        verify(mlTaskManager).createMLTask(any(MLTask.class), any());
        verify(mlTaskManager).add(any(MLTask.class));
        verify(mlTaskManager).remove(anyString());
        verify(mlIndicesHandler).initModelIndexIfAbsent(any());
        verify(client).index(any(), any());
    }

    @Ignore
    public void testExecuteTask_OnLocalNode_AsyncRequest_FailToCreateTask() {
        setupMocks(true, true, false, false);
        taskRunner.dispatchTask(FunctionName.REMOTE, asyncRequestWithDataFrame, transportService, listener);
        verify(listener, never()).onResponse(any());
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(listener).onFailure(argumentCaptor.capture());
        assertEquals(errorMessage, argumentCaptor.getValue().getMessage());
        verify(mlTaskManager).createMLTask(any(MLTask.class), any());
        verify(mlTaskManager, never()).add(any(MLTask.class));
        verify(mlTaskManager, never()).remove(anyString());
        verify(mlIndicesHandler, never()).initModelIndexIfAbsent(any());
        verify(client, never()).index(any(), any());
        // don't need to update async task if ML task not created
        verify(taskRunner, never()).handleAsyncMLTaskFailure(any(MLTask.class), any(Exception.class));
    }

    @Ignore
    public void testExecuteTask_OnLocalNode_AsyncRequest_FailToCreateTaskWithException() {
        setupMocks(true, true, true, false);
        taskRunner.dispatchTask(FunctionName.REMOTE, asyncRequestWithDataFrame, transportService, listener);
        verify(listener, never()).onResponse(any());
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(listener).onFailure(argumentCaptor.capture());
        assertEquals(errorMessage, argumentCaptor.getValue().getMessage());
        verify(mlTaskManager).createMLTask(any(MLTask.class), any());
        verify(mlTaskManager, never()).add(any(MLTask.class));
        verify(mlTaskManager, never()).remove(anyString());
        verify(mlIndicesHandler, never()).initModelIndexIfAbsent(any());
        verify(client, never()).index(any(), any());
        // don't need to update async task if ML task not created
        verify(taskRunner, never()).handleAsyncMLTaskFailure(any(MLTask.class), any(Exception.class));
    }

    @Ignore
    public void testExecuteTask_OnRemoteNode_SyncRequest() {
        setupMocks(false, false, false, false);
        taskRunner.dispatchTask(FunctionName.REMOTE, requestWithDataFrame, transportService, listener);
        verify(transportService).sendRequest(eq(remoteNode), eq(MLTrainingTaskAction.NAME), eq(requestWithDataFrame), any());
    }

    @Ignore
    public void testExecuteTask_OnRemoteNode_SyncRequest_FailToSendRequest() {
        setupMocks(false, false, false, false);
        doThrow(new NodeNotConnectedException(remoteNode, errorMessage))
            .when(transportService)
            .sendRequest(eq(remoteNode), eq(MLTrainingTaskAction.NAME), any(), any());
        taskRunner.dispatchTask(FunctionName.REMOTE, requestWithDataFrame, transportService, listener);
        verify(transportService).sendRequest(eq(remoteNode), eq(MLTrainingTaskAction.NAME), eq(requestWithDataFrame), any());
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(listener).onFailure(argumentCaptor.capture());
        assertTrue(argumentCaptor.getValue().getMessage().contains(errorMessage));
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

    private void setupMocks(
        boolean runOnLocalNode,
        boolean failedToCreateTask,
        boolean throwExceptionWhenCreateMLTask,
        boolean failedToParseQueryInput
    ) {
        doAnswer(invocation -> {
            ActionListener<DiscoveryNode> actionListener = invocation.getArgument(1);
            if (runOnLocalNode) {
                actionListener.onResponse(localNode);
            } else {
                actionListener.onResponse(remoteNode);
            }
            return null;
        }).when(mlTaskDispatcher).dispatch(any(), any());

        if (throwExceptionWhenCreateMLTask) {
            doThrow(new RuntimeException(errorMessage)).when(mlTaskManager).createMLTask(any(), any());
        } else {
            doAnswer(invocation -> {
                ActionListener<IndexResponse> actionListener = invocation.getArgument(1);
                ShardId shardId = new ShardId(new Index("indexName", "uuid"), 1);
                IndexResponse indexResponse = new IndexResponse(shardId, "taskId", 1, 1, 1, true);
                if (failedToCreateTask) {
                    actionListener.onFailure(new RuntimeException(errorMessage));
                } else {
                    actionListener.onResponse(indexResponse);
                }
                return null;
            }).when(mlTaskManager).createMLTask(any(), any());
        }

        doAnswer(invocation -> {
            ActionListener<Boolean> actionListener = invocation.getArgument(0);
            actionListener.onResponse(true);
            return null;
        }).when(mlIndicesHandler).initModelIndexIfAbsent(any());

        doAnswer(invocation -> {
            ActionListener<IndexResponse> actionListener = invocation.getArgument(1);
            ShardId shardId = new ShardId(new Index("indexName", "uuid"), 1);
            IndexResponse indexResponse = new IndexResponse(shardId, "modelId", 1, 1, 1, true);
            actionListener.onResponse(indexResponse);
            return null;
        }).when(client).index(any(), any());

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
    }
}
