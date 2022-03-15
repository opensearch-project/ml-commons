/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.task;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.spy;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;

import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.ExpectedException;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.Version;
import org.opensearch.action.ActionListener;
import org.opensearch.action.get.GetResponse;
import org.opensearch.client.Client;
import org.opensearch.cluster.node.DiscoveryNode;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.index.get.GetResult;
import org.opensearch.index.query.MatchAllQueryBuilder;
import org.opensearch.ml.common.breaker.MLCircuitBreakerService;
import org.opensearch.ml.common.dataframe.DataFrame;
import org.opensearch.ml.common.dataset.DataFrameInputDataset;
import org.opensearch.ml.common.dataset.MLInputDataset;
import org.opensearch.ml.common.dataset.SearchQueryInputDataset;
import org.opensearch.ml.common.parameter.*;
import org.opensearch.ml.common.transport.MLTaskResponse;
import org.opensearch.ml.common.transport.prediction.MLPredictionTaskAction;
import org.opensearch.ml.common.transport.prediction.MLPredictionTaskRequest;
import org.opensearch.ml.indices.MLInputDatasetHandler;
import org.opensearch.ml.stats.MLStat;
import org.opensearch.ml.stats.MLStats;
import org.opensearch.ml.stats.StatNames;
import org.opensearch.ml.stats.suppliers.CounterSupplier;
import org.opensearch.ml.utils.TestData;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.TransportService;

import com.google.common.collect.ImmutableList;

public class MLPredictTaskRunnerTests extends OpenSearchTestCase {

    @Mock
    ThreadPool threadPool;

    @Mock
    ClusterService clusterService;

    @Mock
    Client client;

    @Mock
    MLTaskManager mlTaskManager;

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

    @Before
    public void setup() {
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

        Map<String, MLStat<?>> stats = new ConcurrentHashMap<>();
        stats.put(StatNames.ML_EXECUTING_TASK_COUNT, new MLStat<>(false, new CounterSupplier()));
        stats.put(StatNames.ML_TOTAL_REQUEST_COUNT, new MLStat<>(false, new CounterSupplier()));
        stats.put(StatNames.ML_TOTAL_FAILURE_COUNT, new MLStat<>(false, new CounterSupplier()));
        stats.put(StatNames.ML_TOTAL_MODEL_COUNT, new MLStat<>(false, new CounterSupplier()));
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
                mlCircuitBreakerService
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
        when(client.threadPool()).thenReturn(threadPool);

        GetResult getResult = new GetResult(indexName, "type", "111", 111l, 111l, 111l, true, null, null, null);
        getResponse = new GetResponse(getResult);
        doAnswer(invocation -> {
            ActionListener<GetResponse> actionListener = invocation.getArgument(1);
            actionListener.onResponse(getResponse);
            return null;
        }).when(client).get(any(), any());
        when(threadPool.getThreadContext()).thenReturn(threadContext);
    }

    public void testExecuteTask_OnLocalNode() {
        setupMocks(true, false);

        taskRunner.executeTask(requestWithDataFrame, transportService, listener);
        verify(mlInputDatasetHandler, never()).parseSearchQueryInput(any(), any());
        verify(mlInputDatasetHandler).parseDataFrameInput(requestWithDataFrame.getMlInput().getInputDataset());
        verify(mlTaskManager).add(any(MLTask.class));
        verify(client).get(any(), any());
        verify(mlTaskManager).remove(anyString());
    }

    public void testExecuteTask_OnLocalNode_QueryInput() {
        setupMocks(true, false);

        taskRunner.executeTask(requestWithQuery, transportService, listener);
        verify(mlInputDatasetHandler).parseSearchQueryInput(any(), any());
        verify(mlInputDatasetHandler, never()).parseDataFrameInput(requestWithDataFrame.getMlInput().getInputDataset());
        verify(mlTaskManager).add(any(MLTask.class));
        verify(client).get(any(), any());
        verify(mlTaskManager).remove(anyString());
    }

    public void testExecuteTask_OnLocalNode_QueryInput_Failure() {
        setupMocks(true, true);

        taskRunner.executeTask(requestWithQuery, transportService, listener);
        verify(mlInputDatasetHandler).parseSearchQueryInput(any(), any());
        verify(mlInputDatasetHandler, never()).parseDataFrameInput(requestWithDataFrame.getMlInput().getInputDataset());
        verify(mlTaskManager, never()).add(any(MLTask.class));
        verify(client, never()).get(any(), any());
    }

    public void testExecuteTask_OnRemoteNode() {
        setupMocks(false, false);
        taskRunner.executeTask(requestWithDataFrame, transportService, listener);
        verify(transportService).sendRequest(eq(remoteNode), eq(MLPredictionTaskAction.NAME), eq(requestWithDataFrame), any());
    }

    private void setupMocks(boolean runOnLocalNode, boolean failedToParseQueryInput) {
        doAnswer(invocation -> {
            ActionListener<DiscoveryNode> actionListener = invocation.getArgument(0);
            if (runOnLocalNode) {
                actionListener.onResponse(localNode);
            } else {
                actionListener.onResponse(remoteNode);
            }
            return null;
        }).when(mlTaskDispatcher).dispatchTask(any());

        if (failedToParseQueryInput) {
            doAnswer(invocation -> {
                ActionListener<DataFrame> actionListener = invocation.getArgument(1);
                actionListener.onFailure(new RuntimeException(errorMessage));
                return null;
            }).when(mlInputDatasetHandler).parseSearchQueryInput(any(), any());
        } else {
            doAnswer(invocation -> {
                ActionListener<DataFrame> actionListener = invocation.getArgument(1);
                actionListener.onResponse(dataFrame);
                return null;
            }).when(mlInputDatasetHandler).parseSearchQueryInput(any(), any());
        }
    }
}
