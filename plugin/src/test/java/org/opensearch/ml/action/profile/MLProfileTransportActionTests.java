/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.profile;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.verify;
import static org.opensearch.cluster.node.DiscoveryNodeRole.CLUSTER_MANAGER_ROLE;
import static org.opensearch.ml.engine.algorithms.metrics_correlation.MetricsCorrelation.MCORR_ML_VERSION;
import static org.opensearch.ml.settings.MLCommonsSettings.ML_COMMONS_ALLOW_CUSTOM_DEPLOYMENT_PLAN;
import static org.opensearch.ml.utils.TestHelper.builder;

import java.io.IOException;
import java.net.InetAddress;
import java.time.Instant;
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
import org.opensearch.cluster.ClusterName;
import org.opensearch.cluster.node.DiscoveryNode;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.common.settings.ClusterSettings;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.commons.authuser.User;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.common.bytes.BytesReference;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.transport.TransportAddress;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.env.Environment;
import org.opensearch.ml.common.*;
import org.opensearch.ml.common.dataset.MLInputDataType;
import org.opensearch.ml.common.model.MLModelState;
import org.opensearch.ml.model.MLModelManager;
import org.opensearch.ml.profile.MLModelProfile;
import org.opensearch.ml.profile.MLPredictRequestStats;
import org.opensearch.ml.profile.MLProfileInput;
import org.opensearch.ml.task.MLTaskCache;
import org.opensearch.ml.task.MLTaskManager;
import org.opensearch.search.SearchHit;
import org.opensearch.search.SearchHits;
import org.opensearch.search.aggregations.InternalAggregations;
import org.opensearch.search.internal.InternalSearchResponse;
import org.opensearch.search.profile.SearchProfileShardResults;
import org.opensearch.search.suggest.Suggest;
import org.opensearch.test.OpenSearchIntegTestCase;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.TransportService;

public class MLProfileTransportActionTests extends OpenSearchIntegTestCase {
    private MLProfileTransportAction action;
    private Environment environment;
    private MLTaskManager mlTaskManager;
    private MLModelManager mlModelManager;
    private MLTask mlTask;
    private MLModelProfile mlModelProfile;
    private String testTaskId;
    private String testModelId;

    @Mock
    private Client client;

    private DiscoveryNode localNode;

    private ClusterSettings clusterSettings;

    @Mock
    private ClusterService clusterService;

    @Mock
    private ThreadPool threadPool;

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
        MockitoAnnotations.openMocks(this);

        environment = mock(Environment.class);
        Settings settings = Settings.builder().build();
        when(environment.settings()).thenReturn(settings);
        when(client.threadPool()).thenReturn(threadPool);

        ThreadContext threadContext = new ThreadContext(Settings.EMPTY);
        when(threadPool.getThreadContext()).thenReturn(threadContext);

        mlTask = MLTask
            .builder()
            .taskId("test_id")
            .modelId("model_id")
            .taskType(MLTaskType.TRAINING)
            .functionName(FunctionName.AD_LIBSVM)
            .state(MLTaskState.CREATED)
            .inputType(MLInputDataType.DATA_FRAME)
            .progress(0.4f)
            .outputIndex("test_index")
            .workerNodes(Arrays.asList("test_node"))
            .createTime(Instant.ofEpochMilli(123))
            .lastUpdateTime(Instant.ofEpochMilli(123))
            .error("error")
            .user(new User())
            .async(false)
            .build();
        Map<String, MLTaskCache> taskCacheMap = new HashMap<>();
        taskCacheMap.put("test_id", new MLTaskCache(mlTask));
        mlTaskManager = mock(MLTaskManager.class);
        testTaskId = "test_task_id";
        when(mlTaskManager.getAllTaskIds()).thenReturn(new String[] { testTaskId });
        when(mlTaskManager.getMLTask(testTaskId)).thenReturn(mlTask);

        localNode = new DiscoveryNode(
            "foo0",
            "foo0",
            new TransportAddress(InetAddress.getLoopbackAddress(), 9300),
            Collections.emptyMap(),
            Collections.singleton(CLUSTER_MANAGER_ROLE),
            Version.CURRENT
        );
        when(clusterService.localNode()).thenReturn(localNode);

        settings = Settings.builder().put(ML_COMMONS_ALLOW_CUSTOM_DEPLOYMENT_PLAN.getKey(), true).build();
        clusterSettings = new ClusterSettings(settings, new HashSet<>(Arrays.asList(ML_COMMONS_ALLOW_CUSTOM_DEPLOYMENT_PLAN)));
        when(clusterService.getClusterSettings()).thenReturn(clusterSettings);
        when(clusterService.getSettings()).thenReturn(settings);

        mlModelProfile = Mockito
            .spy(
                MLModelProfile
                    .builder()
                    .predictor("test_predictor")
                    .workerNodes(new String[] { "node1", "node2" })
                    .modelState(MLModelState.DEPLOYED)
                    .modelInferenceStats(MLPredictRequestStats.builder().count(10L).average(11.0).max(20.0).min(5.0).build())
                    .build()
            );
        testModelId = "test_model_id";
        mlModelManager = mock(MLModelManager.class);
        when(mlModelManager.getAllModelIds()).thenReturn(new String[] { testModelId });
        when(mlModelManager.getModelProfile(testModelId)).thenReturn(mlModelProfile);
        when(clusterService.getClusterName()).thenReturn(new ClusterName("Local Cluster"));

        action = Mockito
            .spy(
                new MLProfileTransportAction(
                    client.threadPool(),
                    clusterService,
                    mock(TransportService.class),
                    mock(ActionFilters.class),
                    mlTaskManager,
                    environment,
                    mlModelManager,
                    client
                )
            );
    }

    @Test
    public void testNewResponse() {
        String nodeId = "nodeId1";
        MLProfileRequest request = new MLProfileRequest(new String[] { nodeId }, new MLProfileInput());

        MLProfileResponse mlProfileResponse = action.newResponse(request, new ArrayList<>(), new ArrayList<>());
        assertNotNull(mlProfileResponse.getNodes());
    }

    public void testNewNodeRequest() {
        String nodeId = "nodeId1";
        MLProfileRequest mlTaskProfileRequest = new MLProfileRequest(new String[] { nodeId }, new MLProfileInput());

        MLProfileNodeRequest mlStatsNodeRequest1 = new MLProfileNodeRequest(mlTaskProfileRequest);
        MLProfileNodeRequest mlStatsNodeRequest2 = action.newNodeRequest(mlTaskProfileRequest);

        assertEquals(
            mlStatsNodeRequest1.getMlProfileRequest().getMlProfileInput(),
            mlStatsNodeRequest2.getMlProfileRequest().getMlProfileInput()
        );
    }

    public void testNewNodeResponse() throws IOException {
        Map<String, MLTask> taskProfileValues = new HashMap<>();
        DiscoveryNode localNode = new DiscoveryNode("node0", buildNewFakeTransportAddress(), Version.CURRENT);
        Map<String, MLModelProfile> modelProfile = new HashMap<>();
        MLProfileNodeResponse mlProfileNodeResponse = new MLProfileNodeResponse(localNode, taskProfileValues, modelProfile);
        BytesStreamOutput out = new BytesStreamOutput();
        mlProfileNodeResponse.writeTo(out);
        StreamInput in = out.bytes().streamInput();
        MLProfileNodeResponse newProfileNodeResponse = action.newNodeResponse(in);
        Assert.assertEquals(mlProfileNodeResponse.getNodeTasksSize(), newProfileNodeResponse.getNodeTasksSize());
    }

    public void testNodeOperation() {
        String nodeId = clusterService().localNode().getId();
        MLProfileInput mlProfileInput1 = new MLProfileInput(
            new HashSet<>(),
            new HashSet<>(Arrays.asList(testTaskId)),
            new HashSet<>(),
            false,
            false
        );
        MLProfileInput mlProfileInput2 = new MLProfileInput(
            new HashSet<>(Arrays.asList(testModelId)),
            new HashSet<>(),
            new HashSet<>(),
            false,
            false
        );

        doAnswer(invocation -> {
            ActionListener<Set<String>> listener = invocation.getArgument(0);
            Set<String> result = new HashSet<>();
            result.add("modelId");
            listener.onResponse(result);
            CountDownLatch latch = invocation.getArgument(1);
            latch.countDown(); // Ensure the latch is counted down after the listener is notified
            return null;
        }).when(action).searchHiddenModels(isA(ActionListener.class), isA(CountDownLatch.class));

        MLProfileRequest mlTaskProfileRequest1 = new MLProfileRequest(new String[] { nodeId }, mlProfileInput1);
        MLProfileRequest mlTaskProfileRequest2 = new MLProfileRequest(new String[] { nodeId }, mlProfileInput2);

        MLProfileNodeResponse response1 = action.nodeOperation(new MLProfileNodeRequest(mlTaskProfileRequest1));
        MLProfileNodeResponse response2 = action.nodeOperation(new MLProfileNodeRequest(mlTaskProfileRequest2));

        Assert.assertEquals(1, response1.getNodeTasksSize());
        assertNotNull(response1.getMlNodeTasks().get(testTaskId));
        Assert.assertEquals(1, response2.getNodeModelsSize());
    }

    public void testNodeOperation_emptyInputs() {
        String nodeId = clusterService().localNode().getId();
        doAnswer(invocation -> {
            ActionListener<Set<String>> listener = invocation.getArgument(0);
            Set<String> result = new HashSet<>();
            result.add("modelId");
            listener.onResponse(result);
            CountDownLatch latch = invocation.getArgument(1);
            latch.countDown(); // Ensure the latch is counted down after the listener is notified
            return null;
        }).when(action).searchHiddenModels(isA(ActionListener.class), isA(CountDownLatch.class));
        MLProfileInput mlProfileInput = new MLProfileInput(new HashSet<>(), new HashSet<>(), new HashSet<>(), false, false);
        MLProfileRequest mlTaskProfileRequest = new MLProfileRequest(new String[] { nodeId }, mlProfileInput);

        MLProfileNodeResponse response = action.nodeOperation(new MLProfileNodeRequest(mlTaskProfileRequest));
        Assert.assertEquals(0, response.getNodeTasksSize());
        assertNull(response.getMlNodeTasks().get(testTaskId));
    }

    public void testNodeOperation_emptyResponses() {
        String nodeId = clusterService().localNode().getId();
        doAnswer(invocation -> {
            ActionListener<Set<String>> listener = invocation.getArgument(0);
            Set<String> result = new HashSet<>();
            result.add("modelId");
            listener.onResponse(result);
            CountDownLatch latch = invocation.getArgument(1);
            latch.countDown(); // Ensure the latch is counted down after the listener is notified
            return null;
        }).when(action).searchHiddenModels(isA(ActionListener.class), isA(CountDownLatch.class));
        MLProfileInput mlProfileInput1 = new MLProfileInput(
            new HashSet<>(),
            new HashSet<>(Arrays.asList("newtest_id")),
            new HashSet<>(),
            false,
            false
        );
        MLProfileInput mlProfileInput2 = new MLProfileInput(
            new HashSet<>(Arrays.asList("newmodel_id")),
            new HashSet<>(),
            new HashSet<>(),
            false,
            false
        );
        MLProfileRequest mlTaskProfileRequest1 = new MLProfileRequest(new String[] { nodeId }, mlProfileInput1);
        MLProfileRequest mlTaskProfileRequest2 = new MLProfileRequest(new String[] { nodeId }, mlProfileInput2);

        MLProfileNodeResponse response1 = action.nodeOperation(new MLProfileNodeRequest(mlTaskProfileRequest1));
        MLProfileNodeResponse response2 = action.nodeOperation(new MLProfileNodeRequest(mlTaskProfileRequest2));

        Assert.assertEquals(0, response1.getNodeTasksSize());
        assertNull(response1.getMlNodeTasks().get(testTaskId));
        Assert.assertEquals(0, response2.getNodeTasksSize());
    }

    public void testNodeOperation_NoResponseIdNotMatch() {
        String nodeId = clusterService().localNode().getId();
        doAnswer(invocation -> {
            ActionListener<Set<String>> listener = invocation.getArgument(0);
            Set<String> result = new HashSet<>();
            result.add("modelId");
            listener.onResponse(result);
            CountDownLatch latch = invocation.getArgument(1);
            latch.countDown(); // Ensure the latch is counted down after the listener is notified
            return null;
        }).when(action).searchHiddenModels(isA(ActionListener.class), isA(CountDownLatch.class));
        MLProfileRequest mlTaskProfileRequest = new MLProfileRequest(new String[] { nodeId }, new MLProfileInput());
        MLProfileNodeResponse response = action.nodeOperation(new MLProfileNodeRequest(mlTaskProfileRequest));

        assertEquals(0, response.getNodeTasksSize());
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
        assertTrue("Expected set to contain modelId", capturedSet.contains("modelId"));
        verify(client).search(any(SearchRequest.class), any(ActionListener.class));
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

    @Test
    public void testNodeOperation_EmptyIds() {
        MLProfileInput mlProfileInput = new MLProfileInput(
            new HashSet<>(), // Empty model IDs
            new HashSet<>(), // Empty task IDs
            new HashSet<>(),
            false,
            false
        );
        doAnswer(invocation -> {
            ActionListener<Set<String>> listener = invocation.getArgument(0);
            Set<String> result = new HashSet<>();
            result.add("modelId");
            listener.onResponse(result);
            CountDownLatch latch = invocation.getArgument(1);
            latch.countDown(); // Ensure the latch is counted down after the listener is notified
            return null;
        }).when(action).searchHiddenModels(isA(ActionListener.class), isA(CountDownLatch.class));
        MLProfileRequest request = new MLProfileRequest(new String[] { clusterService().localNode().getId() }, mlProfileInput);

        MLProfileNodeResponse response = action.nodeOperation(new MLProfileNodeRequest(request));
        assertTrue("Expecting no tasks or models", response.getMlNodeTasks().isEmpty() && response.getMlNodeModels().isEmpty());
    }

    @Test
    public void testNodeOperation_SuperAdminVsRegularUser() {
        MLProfileInput mlProfileInput = new MLProfileInput(
            new HashSet<>(Arrays.asList(testModelId)),
            new HashSet<>(),
            new HashSet<>(),
            false,
            false
        );

        doAnswer(invocation -> {
            ActionListener<Set<String>> listener = invocation.getArgument(0);
            Set<String> result = new HashSet<>();
            result.add(testModelId);
            listener.onResponse(result);
            CountDownLatch latch = invocation.getArgument(1);
            latch.countDown(); // Ensure the latch is counted down after the listener is notified
            return null;
        }).when(action).searchHiddenModels(isA(ActionListener.class), isA(CountDownLatch.class));

        MLProfileRequest request = new MLProfileRequest(new String[] { localNode.getId() }, mlProfileInput);

        when(action.isSuperAdminUserWrapper(clusterService, client)).thenReturn(true); // Super admin test

        MLProfileNodeResponse superAdminResponse = action.nodeOperation(new MLProfileNodeRequest(request));
        assertFalse("Super admin may see more items", superAdminResponse.getMlNodeModels().isEmpty());

        when(action.isSuperAdminUserWrapper(clusterService, client)).thenReturn(false); // Regular user test

        MLProfileNodeResponse regularUserResponse = action.nodeOperation(new MLProfileNodeRequest(request));
        assertTrue("Regular user may see fewer items", regularUserResponse.getMlNodeModels().isEmpty());
    }

}
