/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.opensearch.ml.engine.algorithms.metrics_correlation.MetricsCorrelation.MCORR_ML_VERSION;
import static org.opensearch.ml.utils.RestActionUtils.splitCommaSeparatedParam;
import static org.opensearch.ml.utils.TestHelper.builder;
import static org.opensearch.ml.utils.TestHelper.getStatsRestRequest;
import static org.opensearch.ml.utils.TestHelper.setupTestClusterState;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.lucene.search.TotalHits;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.ExpectedException;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.Version;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.action.search.ShardSearchFailure;
import org.opensearch.client.node.NodeClient;
import org.opensearch.cluster.ClusterName;
import org.opensearch.cluster.ClusterState;
import org.opensearch.cluster.node.DiscoveryNode;
import org.opensearch.cluster.node.DiscoveryNodeRole;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.xcontent.XContentFactory;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.common.bytes.BytesReference;
import org.opensearch.core.common.transport.TransportAddress;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.ml.action.stats.MLStatsNodeResponse;
import org.opensearch.ml.action.stats.MLStatsNodesAction;
import org.opensearch.ml.action.stats.MLStatsNodesRequest;
import org.opensearch.ml.action.stats.MLStatsNodesResponse;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.MLModel;
import org.opensearch.ml.plugin.MachineLearningPlugin;
import org.opensearch.ml.stats.ActionName;
import org.opensearch.ml.stats.MLActionLevelStat;
import org.opensearch.ml.stats.MLActionStats;
import org.opensearch.ml.stats.MLAlgoStats;
import org.opensearch.ml.stats.MLClusterLevelStat;
import org.opensearch.ml.stats.MLModelStats;
import org.opensearch.ml.stats.MLNodeLevelStat;
import org.opensearch.ml.stats.MLStat;
import org.opensearch.ml.stats.MLStatLevel;
import org.opensearch.ml.stats.MLStats;
import org.opensearch.ml.stats.MLStatsInput;
import org.opensearch.ml.stats.suppliers.CounterSupplier;
import org.opensearch.ml.utils.IndexUtils;
import org.opensearch.rest.BytesRestResponse;
import org.opensearch.rest.RestChannel;
import org.opensearch.rest.RestRequest;
import org.opensearch.search.SearchHit;
import org.opensearch.search.SearchHits;
import org.opensearch.search.aggregations.InternalAggregations;
import org.opensearch.search.internal.InternalSearchResponse;
import org.opensearch.search.profile.SearchProfileShardResults;
import org.opensearch.search.suggest.Suggest;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.test.rest.FakeRestRequest;
import org.opensearch.threadpool.TestThreadPool;
import org.opensearch.threadpool.ThreadPool;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

public class RestMLStatsActionTests extends OpenSearchTestCase {
    @Rule
    public ExpectedException expectedEx = ExpectedException.none();

    RestMLStatsAction restAction;
    MLStats mlStats;
    @Mock
    ClusterService clusterService;
    @Mock
    IndexUtils indexUtils;
    @Mock
    NamedXContentRegistry xContentRegistry;

    @Mock
    RestChannel channel;
    ThreadPool threadPool;
    NodeClient client;
    DiscoveryNode node;

    String clusterNameStr = "test cluster";
    ClusterName clusterName;
    private static final AtomicInteger portGenerator = new AtomicInteger();
    ClusterState testState;

    long mlModelCount = 10;
    long mlConnectorCount = 2;
    long nodeTotalRequestCount = 100;
    long kmeansTrainRequestCount = 20;

    String modelId = "model_id";

    @Before
    public void setup() throws IOException {
        MockitoAnnotations.openMocks(this);
        Map<Enum, MLStat<?>> statMap = ImmutableMap
            .<Enum, MLStat<?>>builder()
            .put(MLNodeLevelStat.ML_EXECUTING_TASK_COUNT, new MLStat<>(false, new CounterSupplier()))
            .build();
        mlStats = new MLStats(statMap);
        threadPool = new TestThreadPool(this.getClass().getSimpleName() + "ThreadPool");
        client = spy(new NodeClient(Settings.EMPTY, threadPool));
        restAction = new RestMLStatsAction(mlStats, clusterService, indexUtils, xContentRegistry);
        Set<DiscoveryNodeRole> roleSet = new HashSet<>();
        roleSet.add(DiscoveryNodeRole.DATA_ROLE);
        node = new DiscoveryNode(
            "node",
            new TransportAddress(TransportAddress.META_ADDRESS, new AtomicInteger().incrementAndGet()),
            new HashMap<>(),
            roleSet,
            Version.CURRENT
        );
        testState = setupTestClusterState("node");
        when(clusterService.state()).thenReturn(testState);

        clusterName = new ClusterName(clusterNameStr);

        doAnswer(invocation -> {
            ActionListener<Long> actionListener = invocation.getArgument(3);
            actionListener.onResponse(mlModelCount);
            return null;
        }).when(indexUtils).getNumberOfDocumentsInIndex(anyString(), anyString(), any(), any());

        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(1);
            SearchResponse response = createSearchModelResponse(); // Prepare your mocked response here
            listener.onResponse(response);
            return null;
        }).when(client).search(any(SearchRequest.class), any());

        doAnswer(invocation -> {
            ActionListener<Long> actionListener = invocation.getArgument(1);
            actionListener.onResponse(mlConnectorCount);
            return null;
        }).when(indexUtils).getNumberOfDocumentsInIndex(anyString(), any());

        when(channel.newBuilder()).thenReturn(XContentFactory.jsonBuilder());
    }

    @Override
    public void tearDown() throws Exception {
        super.tearDown();
        threadPool.shutdown();
        client.close();
    }

    public void testPrepareRequest_AllStateLevels() throws Exception {
        MLStatsInput mlStatsInput = MLStatsInput.builder().targetStatLevels(EnumSet.allOf(MLStatLevel.class)).build();
        RestRequest request = getStatsRestRequest(mlStatsInput);
        restAction.handleRequest(request, channel, client);

        ArgumentCaptor<MLStatsNodesRequest> argumentCaptor = ArgumentCaptor.forClass(MLStatsNodesRequest.class);
        verify(client, times(1)).execute(eq(MLStatsNodesAction.INSTANCE), argumentCaptor.capture(), any());
        MLStatsInput input = argumentCaptor.getValue().getMlStatsInput();
        assertEquals(mlStatsInput.getTargetStatLevels().size(), input.getTargetStatLevels().size());
        for (MLStatLevel statLevel : mlStatsInput.getTargetStatLevels()) {
            assertTrue(input.getTargetStatLevels().contains(statLevel));
        }
    }

    public void testPrepareRequest_ClusterLevelStates() throws Exception {
        MLStatsInput mlStatsInput = MLStatsInput.builder().targetStatLevels(EnumSet.of(MLStatLevel.CLUSTER)).build();
        RestRequest request = getStatsRestRequest(mlStatsInput);
        restAction.handleRequest(request, channel, client);

        ArgumentCaptor<BytesRestResponse> argumentCaptor = ArgumentCaptor.forClass(BytesRestResponse.class);
        verify(channel, times(1)).sendResponse(argumentCaptor.capture());
        BytesRestResponse restResponse = argumentCaptor.getValue();
        assertEquals(RestStatus.OK, restResponse.status());
        BytesReference content = restResponse.content();
        assertTrue(content.utf8ToString().contains("\"ml_connector_count\":2"));
        assertTrue(content.utf8ToString().contains("\"ml_model_count\":10"));
    }

    public void testNodeStatsWithNoNodes() throws Exception {
        when(clusterService.state()).thenReturn(ClusterState.builder(new ClusterName("emptyCluster")).build()); // Mock to return an empty cluster state

        doAnswer(invocation -> {
            ActionListener<MLStatsNodesResponse> actionListener = invocation.getArgument(2);
            actionListener.onResponse(mock(MLStatsNodesResponse.class));
            return null;
        }).when(client).execute(eq(MLStatsNodesAction.INSTANCE), any(), any());

        MLStatsInput mlStatsInput = MLStatsInput.builder().targetStatLevels(EnumSet.of(MLStatLevel.NODE)).build();
        RestRequest request = getStatsRestRequest(mlStatsInput);

        restAction.handleRequest(request, channel, client);

        ArgumentCaptor<BytesRestResponse> argumentCaptor = ArgumentCaptor.forClass(BytesRestResponse.class);
        verify(channel).sendResponse(argumentCaptor.capture());
        BytesRestResponse response = argumentCaptor.getValue();
        assertEquals(RestStatus.OK, response.status());
        String test = response.content().utf8ToString();
        assertTrue(response.content().utf8ToString().contains("{}")); // Expecting an empty node response
    }

    public void testPrepareRequest_ClusterAndNodeLevelStates() throws Exception {
        prepareResponse();

        MLStatsInput mlStatsInput = MLStatsInput.builder().targetStatLevels(EnumSet.of(MLStatLevel.CLUSTER, MLStatLevel.NODE)).build();
        RestRequest request = getStatsRestRequest(mlStatsInput);
        restAction.handleRequest(request, channel, client);

        ArgumentCaptor<MLStatsNodesRequest> inputArgumentCaptor = ArgumentCaptor.forClass(MLStatsNodesRequest.class);
        verify(client, times(1)).execute(eq(MLStatsNodesAction.INSTANCE), inputArgumentCaptor.capture(), any());
        MLStatsInput input = inputArgumentCaptor.getValue().getMlStatsInput();
        assertEquals(mlStatsInput.getTargetStatLevels().size(), input.getTargetStatLevels().size());
        for (MLStatLevel statLevel : mlStatsInput.getTargetStatLevels()) {
            assertTrue(input.getTargetStatLevels().contains(statLevel));
        }

        ArgumentCaptor<BytesRestResponse> argumentCaptor = ArgumentCaptor.forClass(BytesRestResponse.class);
        verify(channel, times(1)).sendResponse(argumentCaptor.capture());
        BytesRestResponse restResponse = argumentCaptor.getValue();
        assertEquals(RestStatus.OK, restResponse.status());
        BytesReference content = restResponse.content();
        final String stat = content.utf8ToString();
        assertTrue(stat.contains("\"ml_connector_count\":2"));
        assertTrue(stat.contains("\"ml_model_count\":10"));
        assertTrue(
            stat
                .contains(
                    "\"nodes\":{\"node\":{\"ml_request_count\":100,\"algorithms\":{\"kmeans\":{\"train\":{\"ml_action_request_count\":20}}},\"models\":{\"model_id\":{\"train\":{\"ml_action_request_count\":20},\"is_hidden\":true}}}}"
                )
        );
    }

    private void prepareResponse() {
        doAnswer(invocation -> {
            ActionListener<MLStatsNodesResponse> actionListener = invocation.getArgument(2);
            List<MLStatsNodeResponse> nodes = new ArrayList<>();
            Map<MLNodeLevelStat, Object> nodeStats = ImmutableMap.of(MLNodeLevelStat.ML_REQUEST_COUNT, nodeTotalRequestCount);
            Map<FunctionName, MLAlgoStats> algoStats = new HashMap<>();
            Map<ActionName, MLActionStats> actionStats = ImmutableMap
                .of(
                    ActionName.TRAIN,
                    new MLActionStats(ImmutableMap.of(MLActionLevelStat.ML_ACTION_REQUEST_COUNT, kmeansTrainRequestCount))
                );
            algoStats.put(FunctionName.KMEANS, new MLAlgoStats(actionStats));

            Map<String, MLModelStats> modelStats = new HashMap<>();

            modelStats.put(modelId, new MLModelStats(actionStats, true));

            MLStatsNodeResponse nodeResponse = new MLStatsNodeResponse(node, nodeStats, algoStats, modelStats);
            nodes.add(nodeResponse);
            MLStatsNodesResponse statsResponse = new MLStatsNodesResponse(clusterName, nodes, ImmutableList.of());
            actionListener.onResponse(statsResponse);
            return null;
        }).when(client).execute(eq(MLStatsNodesAction.INSTANCE), any(), any());
    }

    public void testPrepareRequest_ClusterAndNodeLevelStates_Failure() throws Exception {
        doAnswer(invocation -> {
            ActionListener<MLStatsNodesResponse> actionListener = invocation.getArgument(2);
            actionListener.onFailure(new RuntimeException("test failure"));
            return null;
        }).when(client).execute(eq(MLStatsNodesAction.INSTANCE), any(), any());

        MLStatsInput mlStatsInput = MLStatsInput.builder().targetStatLevels(EnumSet.of(MLStatLevel.CLUSTER, MLStatLevel.NODE)).build();
        RestRequest request = getStatsRestRequest(mlStatsInput);
        restAction.handleRequest(request, channel, client);

        ArgumentCaptor<MLStatsNodesRequest> inputArgumentCaptor = ArgumentCaptor.forClass(MLStatsNodesRequest.class);
        verify(client, times(1)).execute(eq(MLStatsNodesAction.INSTANCE), inputArgumentCaptor.capture(), any());
        MLStatsInput input = inputArgumentCaptor.getValue().getMlStatsInput();
        assertEquals(mlStatsInput.getTargetStatLevels().size(), input.getTargetStatLevels().size());
        for (MLStatLevel statLevel : mlStatsInput.getTargetStatLevels()) {
            assertTrue(input.getTargetStatLevels().contains(statLevel));
        }

        ArgumentCaptor<BytesRestResponse> argumentCaptor = ArgumentCaptor.forClass(BytesRestResponse.class);
        verify(channel, times(1)).sendResponse(argumentCaptor.capture());
        BytesRestResponse restResponse = argumentCaptor.getValue();
        assertEquals(RestStatus.INTERNAL_SERVER_ERROR, restResponse.status());
        BytesReference content = restResponse.content();
        // Error happened when generate failure response, then will return general error message
        assertEquals("Failed to get ML node level stats", content.utf8ToString());

        when(channel.request()).thenReturn(request);
        when(channel.newErrorBuilder()).thenReturn(XContentFactory.jsonBuilder());
        when(channel.detailedErrorsEnabled()).thenReturn(false);
        restAction.handleRequest(request, channel, client);
        argumentCaptor = ArgumentCaptor.forClass(BytesRestResponse.class);
        verify(channel, times(2)).sendResponse(argumentCaptor.capture());
        restResponse = argumentCaptor.getValue();
        assertEquals(RestStatus.INTERNAL_SERVER_ERROR, restResponse.status());
        content = restResponse.content();
        // Return exception directly in normal case
        assertEquals("{\"error\":\"Internal failure\",\"status\":500}", content.utf8ToString());
    }

    public void testPrepareRequest_ClusterAndNodeLevelStates_NoRequestContent() throws Exception {
        prepareResponse();

        RestRequest request = getStatsRestRequest();
        restAction.handleRequest(request, channel, client);

        ArgumentCaptor<MLStatsNodesRequest> inputArgumentCaptor = ArgumentCaptor.forClass(MLStatsNodesRequest.class);
        verify(client, times(1)).execute(eq(MLStatsNodesAction.INSTANCE), inputArgumentCaptor.capture(), any());
        MLStatsInput input = inputArgumentCaptor.getValue().getMlStatsInput();
        assertEquals(MLStatLevel.values().length, input.getTargetStatLevels().size());
        for (MLStatLevel statLevel : MLStatLevel.values()) {
            assertTrue(input.getTargetStatLevels().contains(statLevel));
        }

        ArgumentCaptor<BytesRestResponse> argumentCaptor = ArgumentCaptor.forClass(BytesRestResponse.class);
        verify(channel, times(1)).sendResponse(argumentCaptor.capture());
        BytesRestResponse restResponse = argumentCaptor.getValue();
        assertEquals(RestStatus.OK, restResponse.status());
        BytesReference content = restResponse.content();
        final String stat = content.utf8ToString();
        assertTrue(stat.contains("\"ml_connector_count\":2"));
        assertTrue(stat.contains("\"ml_model_count\":10"));
        assertTrue(
            stat
                .contains(
                    "\"nodes\":{\"node\":{\"ml_request_count\":100,\"algorithms\":{\"kmeans\":{\"train\":{\"ml_action_request_count\":20}}},\"models\":{\"model_id\":{\"train\":{\"ml_action_request_count\":20},\"is_hidden\":true}}}}"
                )
        );
    }

    public void testPrepareRequest_ClusterAndNodeLevelStates_RequestParams() throws Exception {
        prepareResponse();

        RestRequest request = getStatsRestRequest(
            node.getId(),
            MLClusterLevelStat.ML_MODEL_COUNT + "," + MLNodeLevelStat.ML_DEPLOYED_MODEL_COUNT
        );
        restAction.handleRequest(request, channel, client);

        ArgumentCaptor<MLStatsNodesRequest> inputArgumentCaptor = ArgumentCaptor.forClass(MLStatsNodesRequest.class);
        verify(client, times(1)).execute(eq(MLStatsNodesAction.INSTANCE), inputArgumentCaptor.capture(), any());
        MLStatsInput input = inputArgumentCaptor.getValue().getMlStatsInput();
        assertEquals(2, input.getTargetStatLevels().size());
        assertTrue(input.getTargetStatLevels().contains(MLStatLevel.CLUSTER));
        assertTrue(input.getTargetStatLevels().contains(MLStatLevel.NODE));
        assertEquals(1, input.getClusterLevelStats().size());
        assertTrue(input.getClusterLevelStats().contains(MLClusterLevelStat.ML_MODEL_COUNT));
        assertTrue(input.getNodeLevelStats().contains(MLNodeLevelStat.ML_DEPLOYED_MODEL_COUNT));

        ArgumentCaptor<BytesRestResponse> argumentCaptor = ArgumentCaptor.forClass(BytesRestResponse.class);
        verify(channel, times(1)).sendResponse(argumentCaptor.capture());
        BytesRestResponse restResponse = argumentCaptor.getValue();
        assertEquals(RestStatus.OK, restResponse.status());
        BytesReference content = restResponse.content();
        final String stat = content.utf8ToString();
        assertTrue(stat.contains("\"ml_connector_count\":2"));
        assertTrue(stat.contains("\"ml_model_count\":10"));
        assertTrue(
            stat
                .contains(
                    "\"nodes\":{\"node\":{\"ml_request_count\":100,\"algorithms\":{\"kmeans\":{\"train\":{\"ml_action_request_count\":20}}},\"models\":{\"model_id\":{\"train\":{\"ml_action_request_count\":20},\"is_hidden\":true}}}}"
                )
        );
    }

    public void testPrepareRequest_ClusterAndNodeLevelStates_RequestParams_NodeLevelStat() throws Exception {
        prepareResponse();

        RestRequest request = getStatsRestRequest(node.getId(), MLNodeLevelStat.ML_DEPLOYED_MODEL_COUNT.name());
        restAction.handleRequest(request, channel, client);

        ArgumentCaptor<MLStatsNodesRequest> inputArgumentCaptor = ArgumentCaptor.forClass(MLStatsNodesRequest.class);
        verify(client, times(1)).execute(eq(MLStatsNodesAction.INSTANCE), inputArgumentCaptor.capture(), any());
        MLStatsInput input = inputArgumentCaptor.getValue().getMlStatsInput();
        assertEquals(1, input.getTargetStatLevels().size());
        assertTrue(input.getTargetStatLevels().contains(MLStatLevel.NODE));
        assertEquals(0, input.getClusterLevelStats().size());
        assertEquals(1, input.getNodeLevelStats().size());
        assertTrue(input.getNodeLevelStats().contains(MLNodeLevelStat.ML_DEPLOYED_MODEL_COUNT));

        ArgumentCaptor<BytesRestResponse> argumentCaptor = ArgumentCaptor.forClass(BytesRestResponse.class);
        verify(channel, times(1)).sendResponse(argumentCaptor.capture());
        BytesRestResponse restResponse = argumentCaptor.getValue();
        assertEquals(RestStatus.OK, restResponse.status());
        BytesReference content = restResponse.content();
        String stat = content.utf8ToString();
        assertEquals(
            "{\"nodes\":{\"node\":{\"ml_request_count\":100,\"algorithms\":{\"kmeans\":{\"train\":{\"ml_action_request_count\":20}}},\"models\":{\"model_id\":{\"train\":{\"ml_action_request_count\":20},\"is_hidden\":true}}}}}",
            stat
        );
    }

    public void testCreateMlStatsInputFromRequestParams_NodeStat() {
        RestRequest request = getStatsRestRequest(node.getId(), MLNodeLevelStat.ML_DEPLOYED_MODEL_COUNT.name().toLowerCase(Locale.ROOT));
        MLStatsInput input = restAction.createMlStatsInputFromRequestParams(request);
        assertEquals(1, input.getTargetStatLevels().size());
        assertTrue(input.getTargetStatLevels().contains(MLStatLevel.NODE));
        assertTrue(input.getNodeLevelStats().contains(MLNodeLevelStat.ML_DEPLOYED_MODEL_COUNT));
        assertEquals(0, input.getClusterLevelStats().size());
    }

    public void testCreateMlStatsInputFromRequestParams_ClusterStat() {
        RestRequest request = getStatsRestRequest(node.getId(), MLClusterLevelStat.ML_MODEL_COUNT.name().toLowerCase(Locale.ROOT));
        MLStatsInput input = restAction.createMlStatsInputFromRequestParams(request);
        assertEquals(1, input.getTargetStatLevels().size());
        assertTrue(input.getTargetStatLevels().contains(MLStatLevel.CLUSTER));
        assertTrue(input.getClusterLevelStats().contains(MLClusterLevelStat.ML_MODEL_COUNT));
        assertEquals(0, input.getNodeLevelStats().size());
    }

    public void testSplitCommaSeparatedParam() {
        Map<String, String> param = ImmutableMap.<String, String>builder().put("nodeId", "111,222").build();
        FakeRestRequest fakeRestRequest = new FakeRestRequest.Builder(xContentRegistry())
            .withMethod(RestRequest.Method.GET)
            .withPath(MachineLearningPlugin.ML_BASE_URI + "/{nodeId}/stats/")
            .withParams(param)
            .build();
        Optional<String[]> nodeId = splitCommaSeparatedParam(fakeRestRequest, "nodeId");
        String[] array = nodeId.get();
        Assert.assertEquals(array[0], "111");
        Assert.assertEquals(array[1], "222");
    }

    public void testIOExceptionDuringNodeStatsRetrieval() throws Exception {
        // Configure the client to simulate an error response through the ActionListener
        doAnswer(invocation -> {
            ActionListener<?> listener = invocation.getArgument(2);
            listener.onFailure(new IOException("Failed to get ML node level stats"));  // Simulate an error being reported asynchronously
            return null;
        }).when(client).execute(any(), any(), any());

        MLStatsInput mlStatsInput = MLStatsInput.builder().targetStatLevels(EnumSet.of(MLStatLevel.NODE)).build();
        RestRequest request = getStatsRestRequest(mlStatsInput);

        // Execute the request
        restAction.handleRequest(request, channel, client);

        // Capture and verify the response
        ArgumentCaptor<BytesRestResponse> argumentCaptor = ArgumentCaptor.forClass(BytesRestResponse.class);
        verify(channel).sendResponse(argumentCaptor.capture());
        BytesRestResponse response = argumentCaptor.getValue();

        // Assert the expected results
        assertEquals(RestStatus.INTERNAL_SERVER_ERROR, response.status());
        assertTrue(response.content().utf8ToString().contains("Failed to get ML node level stats"));
    }

    public void testClusterStatsUnavailable() throws Exception {
        // Mock the asynchronous method to simulate an error condition

        doAnswer(invocation -> {
            ActionListener<Long> listener = invocation.getArgument(3);
            listener.onFailure(new RuntimeException("Failed to get ML model count"));
            return null; // Void method requires null return in doAnswer
        }).when(indexUtils).getNumberOfDocumentsInIndex(anyString(), anyString(), any(), any());

        // Build the stats input and the REST request to trigger the operation
        MLStatsInput mlStatsInput = MLStatsInput.builder().targetStatLevels(EnumSet.of(MLStatLevel.CLUSTER)).build();
        RestRequest request = getStatsRestRequest(mlStatsInput);

        // Execute the action which should invoke the mocked method
        restAction.handleRequest(request, channel, client);

        // Capture and verify the response sent back via the channel
        ArgumentCaptor<BytesRestResponse> argumentCaptor = ArgumentCaptor.forClass(BytesRestResponse.class);
        verify(channel).sendResponse(argumentCaptor.capture());
        BytesRestResponse response = argumentCaptor.getValue();

        // Assert the response status and content to confirm proper error handling
        assertEquals(RestStatus.INTERNAL_SERVER_ERROR, response.status());
        String test = response.content().utf8ToString();
        assertTrue(response.content().utf8ToString().contains("Failed to get ML model count"));
    }

    private SearchResponse createSearchModelResponse() throws IOException {
        XContentBuilder content = builder();
        content.startObject();
        content.field(MLModel.MODEL_NAME_FIELD, FunctionName.METRICS_CORRELATION.name());
        content.field(MLModel.MODEL_VERSION_FIELD, MCORR_ML_VERSION);
        content.field(MLModel.MODEL_ID_FIELD, "modelId");
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

}
