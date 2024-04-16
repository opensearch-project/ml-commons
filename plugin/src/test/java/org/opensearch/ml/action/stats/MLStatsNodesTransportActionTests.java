/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.stats;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.*;
import static org.opensearch.ml.engine.algorithms.metrics_correlation.MetricsCorrelation.MCORR_ML_VERSION;
import static org.opensearch.ml.stats.MLNodeLevelStat.ML_JVM_HEAP_USAGE;
import static org.opensearch.ml.utils.TestHelper.builder;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.CountDownLatch;

import org.apache.lucene.search.TotalHits;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.opensearch.Version;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.action.search.ShardSearchFailure;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.client.Client;
import org.opensearch.cluster.node.DiscoveryNode;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.common.bytes.BytesReference;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.env.Environment;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.MLModel;
import org.opensearch.ml.model.MLModelManager;
import org.opensearch.ml.stats.*;
import org.opensearch.ml.stats.suppliers.CounterSupplier;
import org.opensearch.ml.stats.suppliers.SettableSupplier;
import org.opensearch.search.SearchHit;
import org.opensearch.search.SearchHits;
import org.opensearch.search.aggregations.InternalAggregations;
import org.opensearch.search.internal.InternalSearchResponse;
import org.opensearch.search.profile.SearchProfileShardResults;
import org.opensearch.search.suggest.Suggest;
import org.opensearch.test.OpenSearchIntegTestCase;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.TransportService;

import com.google.common.collect.ImmutableSet;

public class MLStatsNodesTransportActionTests extends OpenSearchIntegTestCase {
    private MLStatsNodesTransportAction action;
    private MLStats mlStats;
    private Map<Enum, MLStat<?>> statsMap;
    private MLClusterLevelStat clusterStatName1;
    private MLNodeLevelStat nodeStatName1;
    private Environment environment;

    @Mock
    private MLModelManager mlModelManager;

    @Mock
    private Client client;

    @Mock
    private ThreadPool threadPool;

    private final String modelId = "model_id";

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
        MockitoAnnotations.openMocks(this);

        clusterStatName1 = MLClusterLevelStat.ML_MODEL_COUNT;
        nodeStatName1 = MLNodeLevelStat.ML_EXECUTING_TASK_COUNT;

        statsMap = new HashMap<>() {
            {
                put(nodeStatName1, new MLStat<>(false, new CounterSupplier()));
                put(clusterStatName1, new MLStat<>(true, new CounterSupplier()));
                put(ML_JVM_HEAP_USAGE, new MLStat<>(true, new SettableSupplier()));
            }
        };

        mlStats = new MLStats(statsMap);
        environment = mock(Environment.class);
        Settings settings = Settings.builder().build();
        when(environment.settings()).thenReturn(settings);

        when(client.threadPool()).thenReturn(threadPool);

        ThreadContext threadContext = new ThreadContext(Settings.EMPTY);
        when(threadPool.getThreadContext()).thenReturn(threadContext);

        action = new MLStatsNodesTransportAction(
            client().threadPool(),
            clusterService(),
            mock(TransportService.class),
            mock(ActionFilters.class),
            mlStats,
            environment,
            client,
            mlModelManager
        );
    }

    @Test
    public void testNewNodeRequest() {
        String nodeId = "nodeId1";
        MLStatsNodesRequest mlStatsNodesRequest = new MLStatsNodesRequest(new String[] { nodeId }, new MLStatsInput());

        MLStatsNodeRequest mlStatsNodeRequest1 = new MLStatsNodeRequest(mlStatsNodesRequest);
        MLStatsNodeRequest mlStatsNodeRequest2 = action.newNodeRequest(mlStatsNodesRequest);

        assertEquals(mlStatsNodeRequest1.getMlStatsNodesRequest(), mlStatsNodeRequest2.getMlStatsNodesRequest());
    }

    @Test
    public void testNewNodeResponse() throws IOException {
        Map<MLNodeLevelStat, Object> statValues = new HashMap<>();
        DiscoveryNode localNode = new DiscoveryNode("node0", buildNewFakeTransportAddress(), Version.CURRENT);
        MLStatsNodeResponse statsNodeResponse = new MLStatsNodeResponse(localNode, statValues);
        BytesStreamOutput out = new BytesStreamOutput();
        statsNodeResponse.writeTo(out);
        StreamInput in = out.bytes().streamInput();
        MLStatsNodeResponse newStatsNodeResponse = action.newNodeResponse(in);
        Assert.assertEquals(statsNodeResponse.getNodeLevelStatSize(), newStatsNodeResponse.getAlgorithmStatSize());
        Assert.assertEquals(statsNodeResponse.getNodeLevelStatSize(), newStatsNodeResponse.getModelStatSize());
    }

    @Test
    public void testNodeOperation() {
        String nodeId = clusterService().localNode().getId();
        MLStatsNodesRequest mlStatsNodesRequest = new MLStatsNodesRequest(new String[] { nodeId }, new MLStatsInput());

        ImmutableSet<MLNodeLevelStat> statsToBeRetrieved = ImmutableSet.of(nodeStatName1);
        mlStatsNodesRequest.addNodeLevelStats(statsToBeRetrieved);

        MLStatsNodeResponse response = action.nodeOperation(new MLStatsNodeRequest(mlStatsNodesRequest));

        Assert.assertEquals(1, response.getNodeLevelStatSize());
        assertNotNull(response.getNodeLevelStat(nodeStatName1));
    }

    @Test
    public void testNodeOperationWithJvmHeapUsage() {
        String nodeId = clusterService().localNode().getId();
        MLStatsNodesRequest mlStatsNodesRequest = new MLStatsNodesRequest(new String[] { nodeId }, new MLStatsInput());

        Set<MLNodeLevelStat> statsToBeRetrieved = ImmutableSet.of(ML_JVM_HEAP_USAGE);

        mlStatsNodesRequest.addNodeLevelStats(statsToBeRetrieved);

        MLStatsNodeResponse response = action.nodeOperation(new MLStatsNodeRequest(mlStatsNodesRequest));

        Assert.assertEquals(statsToBeRetrieved.size(), response.getNodeLevelStatSize());
        assertNotNull(response.getNodeLevelStat(ML_JVM_HEAP_USAGE));
    }

    @Test
    public void testNodeOperation_NoNodeLevelStat() {
        String nodeId = clusterService().localNode().getId();
        MLStatsInput mlStatsInput = MLStatsInput.builder().targetStatLevels(EnumSet.of(MLStatLevel.ALGORITHM)).build();
        MLStatsNodesRequest mlStatsNodesRequest = new MLStatsNodesRequest(new String[] { nodeId }, mlStatsInput);

        MLStatsNodeResponse response = action.nodeOperation(new MLStatsNodeRequest(mlStatsNodesRequest));

        assertEquals(0, response.getNodeLevelStatSize());
    }

    @Test
    public void testNodeOperation_NoNodeLevelStat_AlgoStatWithoutHiddenModel() {
        MLStats mlStats = new MLStats(statsMap);
        mlStats.createCounterStatIfAbsent(FunctionName.KMEANS, ActionName.TRAIN, MLActionLevelStat.ML_ACTION_REQUEST_COUNT).increment();
        mlStats.createModelCounterStatIfAbsent(modelId, ActionName.PREDICT, MLActionLevelStat.ML_ACTION_REQUEST_COUNT).increment();

        MLStatsNodesTransportAction action = Mockito
            .spy(
                new MLStatsNodesTransportAction(
                    client().threadPool(),
                    clusterService(),
                    mock(TransportService.class),
                    mock(ActionFilters.class),
                    mlStats,
                    environment,
                    client,
                    mlModelManager
                )
            );

        doAnswer(invocation -> {
            ActionListener<Set<String>> listener = invocation.getArgument(0);
            Set<String> result = new HashSet<>();
            listener.onResponse(result);
            CountDownLatch latch = invocation.getArgument(1);
            latch.countDown(); // Ensure the latch is counted down after the listener is notified
            return null;
        }).when(action).searchHiddenModels(isA(ActionListener.class), isA(CountDownLatch.class));

        String nodeId = clusterService().localNode().getId();
        MLStatsInput mlStatsInput = MLStatsInput.builder().targetStatLevels(EnumSet.of(MLStatLevel.ALGORITHM, MLStatLevel.MODEL)).build();
        MLStatsNodesRequest mlStatsNodesRequest = new MLStatsNodesRequest(new String[] { nodeId }, mlStatsInput);

        MLStatsNodeResponse response = action.nodeOperation(new MLStatsNodeRequest(mlStatsNodesRequest));

        assertEquals(0, response.getNodeLevelStatSize());
        assertEquals(1, response.getAlgorithmStatSize());
        assertEquals(1, response.getModelStatSize());
        MLAlgoStats algorithmStats = response.getAlgorithmStats(FunctionName.KMEANS);
        assertNotNull(algorithmStats);
        MLActionStats actionStats = algorithmStats.getActionStats(ActionName.TRAIN);
        assertNotNull(actionStats);
        assertEquals(1l, actionStats.getActionStat(MLActionLevelStat.ML_ACTION_REQUEST_COUNT));

        MLModelStats modelStats = response.getModelStats(modelId);
        assertNotNull(modelStats);
        actionStats = modelStats.getActionStats(ActionName.PREDICT);
        assertNotNull(actionStats);
        assertEquals(1l, actionStats.getActionStat(MLActionLevelStat.ML_ACTION_REQUEST_COUNT));
    }

    @Test
    public void testNodeOperation_NoNodeLevelStat_AlgoStat_hiddenModel() {
        MLStats mlStats = new MLStats(statsMap);
        mlStats.createCounterStatIfAbsent(FunctionName.KMEANS, ActionName.TRAIN, MLActionLevelStat.ML_ACTION_REQUEST_COUNT).increment();
        mlStats.createModelCounterStatIfAbsent(modelId, ActionName.PREDICT, MLActionLevelStat.ML_ACTION_REQUEST_COUNT).increment();

        MLStatsNodesTransportAction action = Mockito
            .spy(
                new MLStatsNodesTransportAction(
                    client().threadPool(),
                    clusterService(),
                    mock(TransportService.class),
                    mock(ActionFilters.class),
                    mlStats,
                    environment,
                    client,
                    mlModelManager
                )
            );

        doAnswer(invocation -> {
            ActionListener<Set<String>> listener = invocation.getArgument(0);
            Set<String> result = new HashSet<>();
            result.add(modelId);
            listener.onResponse(result);
            CountDownLatch latch = invocation.getArgument(1);
            latch.countDown(); // Ensure the latch is counted down after the listener is notified
            return null;
        }).when(action).searchHiddenModels(isA(ActionListener.class), isA(CountDownLatch.class));

        String nodeId = clusterService().localNode().getId();
        MLStatsInput mlStatsInput = MLStatsInput.builder().targetStatLevels(EnumSet.of(MLStatLevel.ALGORITHM, MLStatLevel.MODEL)).build();
        MLStatsNodesRequest mlStatsNodesRequest = new MLStatsNodesRequest(new String[] { nodeId }, mlStatsInput);

        MLStatsNodeResponse response = action.nodeOperation(new MLStatsNodeRequest(mlStatsNodesRequest));

        assertEquals(0, response.getNodeLevelStatSize());
        assertEquals(1, response.getAlgorithmStatSize());
        assertEquals(0, response.getModelStatSize());
        MLAlgoStats algorithmStats = response.getAlgorithmStats(FunctionName.KMEANS);
        assertNotNull(algorithmStats);
        MLActionStats actionStats = algorithmStats.getActionStats(ActionName.TRAIN);
        assertNotNull(actionStats);
        assertEquals(1l, actionStats.getActionStat(MLActionLevelStat.ML_ACTION_REQUEST_COUNT));

        MLModelStats modelStats = response.getModelStats(modelId);
        assertNull(modelStats);
    }

    @Test
    public void testSearchHiddenModels_successfulSearch() throws IOException {

        SearchResponse response = createSearchModelResponse();

        ActionListener<Set<String>> mockListener = mock(ActionListener.class);
        CountDownLatch latch = mock(CountDownLatch.class);
        ArgumentCaptor<SearchRequest> captor = ArgumentCaptor.forClass(SearchRequest.class);

        doAnswer(invocation -> {
            ActionListener<SearchResponse> al = invocation.getArgument(1);
            al.onResponse(response);
            return null;
        }).when(client).search(captor.capture(), any());

        action.searchHiddenModels(mockListener, latch);

        ArgumentCaptor<Set<String>> argumentCaptor = ArgumentCaptor.forClass(Set.class);

        verify(mockListener).onResponse(argumentCaptor.capture());
        Set<String> capturedSet = argumentCaptor.getValue();
        assertEquals(argumentCaptor.getValue().size(), 1);
        assertTrue("Expected set to contain modelId", capturedSet.contains(modelId));
        verify(client).search(any(SearchRequest.class), any(ActionListener.class));
    }

    private SearchResponse createSearchModelResponse() throws IOException {
        XContentBuilder content = builder();
        content.startObject();
        content.field(MLModel.MODEL_NAME_FIELD, FunctionName.METRICS_CORRELATION.name());
        content.field(MLModel.MODEL_VERSION_FIELD, MCORR_ML_VERSION);
        content.field(MLModel.MODEL_ID_FIELD, modelId);
        content.endObject();

        SearchHit[] hits = new SearchHit[1];
        hits[0] = new SearchHit(0, modelId, null, null).sourceRef(BytesReference.bytes(content));

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

}
