/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.opensearch.ml.engine.algorithms.metrics_correlation.MetricsCorrelation.MCORR_ML_VERSION;
import static org.opensearch.ml.utils.TestHelper.*;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.lucene.search.TotalHits;
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
import org.opensearch.transport.client.node.NodeClient;
import org.opensearch.cluster.ClusterName;
import org.opensearch.cluster.ClusterState;
import org.opensearch.cluster.node.DiscoveryNode;
import org.opensearch.cluster.node.DiscoveryNodeRole;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.xcontent.XContentFactory;
import org.opensearch.commons.authuser.User;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.common.Strings;
import org.opensearch.core.common.bytes.BytesReference;
import org.opensearch.core.common.transport.TransportAddress;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.ml.action.profile.MLProfileAction;
import org.opensearch.ml.action.profile.MLProfileNodeResponse;
import org.opensearch.ml.action.profile.MLProfileRequest;
import org.opensearch.ml.action.profile.MLProfileResponse;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.MLModel;
import org.opensearch.ml.common.MLTask;
import org.opensearch.ml.common.MLTaskState;
import org.opensearch.ml.common.MLTaskType;
import org.opensearch.ml.common.dataset.MLInputDataType;
import org.opensearch.ml.common.model.MLModelState;
import org.opensearch.ml.profile.MLModelProfile;
import org.opensearch.ml.profile.MLPredictRequestStats;
import org.opensearch.ml.profile.MLProfileInput;
import org.opensearch.rest.BytesRestResponse;
import org.opensearch.rest.RestChannel;
import org.opensearch.rest.RestHandler;
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

public class RestMLProfileActionTests extends OpenSearchTestCase {
    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Mock
    private RestChannel channel;

    @Mock
    ClusterService clusterService;

    private static final AtomicInteger portGenerator = new AtomicInteger();
    private RestMLProfileAction profileAction;
    private ThreadPool threadPool;
    private NodeClient client;
    private DiscoveryNode node;
    private MLTask mlTask;
    private MLModelProfile mlModelProfile;
    private ClusterName clusterName;
    ClusterState testState;

    @Before
    public void setup() throws IOException {
        MockitoAnnotations.openMocks(this);
        profileAction = new RestMLProfileAction(clusterService);
        Settings settings = Settings.builder().build();
        threadPool = new TestThreadPool(this.getClass().getSimpleName() + "ThreadPool");
        client = spy(new NodeClient(settings, threadPool));
        Set<DiscoveryNodeRole> roleSet = new HashSet<>();
        roleSet.add(DiscoveryNodeRole.DATA_ROLE);
        node = new DiscoveryNode(
            "node",
            new TransportAddress(TransportAddress.META_ADDRESS, portGenerator.incrementAndGet()),
            new HashMap<>(),
            roleSet,
            Version.CURRENT
        );

        when(channel.newBuilder()).thenReturn(XContentFactory.jsonBuilder());
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
            .workerNodes(ImmutableList.of("test_node"))
            .createTime(Instant.ofEpochMilli(123))
            .lastUpdateTime(Instant.ofEpochMilli(123))
            .error("error")
            .user(new User())
            .async(false)
            .build();
        mlModelProfile = MLModelProfile
            .builder()
            .predictor("test_predictor")
            .workerNodes(new String[] { "node1", "node2" })
            .modelState(MLModelState.DEPLOYED)
            .modelInferenceStats(MLPredictRequestStats.builder().count(10L).average(11.0).max(20.0).min(5.0).build())
            .build();

        clusterName = new ClusterName("test cluster");
        testState = setupTestClusterState("node");
        when(clusterService.state()).thenReturn(testState);

        doAnswer(invocation -> {
            ActionListener<MLProfileResponse> actionListener = invocation.getArgument(2);
            Map<String, MLTask> nodeTasks = new HashMap<>();
            nodeTasks.put("test_id", mlTask);
            Map<String, MLModelProfile> nodeModels = new HashMap<>();
            nodeModels.put("test_id", mlModelProfile);
            MLProfileNodeResponse nodeResponse = new MLProfileNodeResponse(node, nodeTasks, nodeModels);
            MLProfileResponse profileResponse = new MLProfileResponse(clusterName, Arrays.asList(nodeResponse), new ArrayList<>());
            actionListener.onResponse(profileResponse);
            return null;
        }).when(client).execute(eq(MLProfileAction.INSTANCE), any(), any());
    }

    @Override
    public void tearDown() throws Exception {
        super.tearDown();
        threadPool.shutdown();
        client.close();
    }

    public void testConstructor() {
        RestMLProfileAction action = new RestMLProfileAction(clusterService);
        assertNotNull(action);
    }

    public void testGetName() {
        String actionName = profileAction.getName();
        assertFalse(Strings.isNullOrEmpty(actionName));
        assertEquals("profile_ml", actionName);
    }

    public void testRoutes() {
        List<RestHandler.Route> routes = profileAction.routes();
        assertNotNull(routes);
        assertFalse(routes.isEmpty());
        RestHandler.Route modelRoute = routes.get(0);
        RestHandler.Route modelsRoute = routes.get(1);
        RestHandler.Route taskRoute = routes.get(2);
        RestHandler.Route tasksRoute = routes.get(3);
        RestHandler.Route route = routes.get(4);
        assertEquals(RestRequest.Method.GET, modelRoute.getMethod());
        assertEquals("/_plugins/_ml/profile/models/{model_id}", modelRoute.getPath());
        assertEquals("/_plugins/_ml/profile/models", modelsRoute.getPath());
        assertEquals("/_plugins/_ml/profile/tasks/{task_id}", taskRoute.getPath());
        assertEquals("/_plugins/_ml/profile/tasks", tasksRoute.getPath());
        assertEquals("/_plugins/_ml/profile", route.getPath());
    }

    public void test_PrepareRequest_TaskRequest() throws Exception {
        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(1);
            SearchResponse response = createSearchModelResponse(); // Prepare your mocked response here
            listener.onResponse(response);
            return null;
        }).when(client).search(any(SearchRequest.class), any());

        RestRequest request = getRestRequest();
        profileAction.handleRequest(request, channel, client);

        ArgumentCaptor<MLProfileRequest> argumentCaptor = ArgumentCaptor.forClass(MLProfileRequest.class);
        verify(client, times(1)).execute(eq(MLProfileAction.INSTANCE), argumentCaptor.capture(), any());
        Set<String> taskIds = argumentCaptor.getValue().getMlProfileInput().getTaskIds();
        assertEquals(taskIds.size(), 1);
        assertTrue(taskIds.contains("test_id"));
    }

    public void test_PrepareRequest_TaskRequestWithNoTaskIds() throws Exception {
        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(1);
            SearchResponse response = createSearchModelResponse(); // Prepare your mocked response here
            listener.onResponse(response);
            return null;
        }).when(client).search(any(SearchRequest.class), any());

        RestRequest request = new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY).withPath("/_plugins/_ml/profile/tasks").build();
        profileAction.handleRequest(request, channel, client);

        ArgumentCaptor<MLProfileRequest> argumentCaptor = ArgumentCaptor.forClass(MLProfileRequest.class);
        verify(client, times(1)).execute(eq(MLProfileAction.INSTANCE), argumentCaptor.capture(), any());
        Boolean returnAllTasks = argumentCaptor.getValue().getMlProfileInput().isReturnAllTasks();
        assertTrue(returnAllTasks);
    }

    public void test_PrepareRequest_ModelRequest() throws Exception {
        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(1);
            SearchResponse response = createSearchModelResponse(); // Prepare your mocked response here
            listener.onResponse(response);
            return null;
        }).when(client).search(any(SearchRequest.class), any());

        RestRequest request = getModelRestRequest();
        profileAction.handleRequest(request, channel, client);

        ArgumentCaptor<MLProfileRequest> argumentCaptor = ArgumentCaptor.forClass(MLProfileRequest.class);
        verify(client, times(1)).execute(eq(MLProfileAction.INSTANCE), argumentCaptor.capture(), any());
        Set<String> modelIds = argumentCaptor.getValue().getMlProfileInput().getModelIds();
        assertEquals(modelIds.size(), 1);
        assertTrue(modelIds.contains("test_id"));
    }

    public void test_PrepareRequest_TaskRequestWithNoModelIds() throws Exception {
        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(1);
            SearchResponse response = createSearchModelResponse(); // Prepare your mocked response here
            listener.onResponse(response);
            return null;
        }).when(client).search(any(SearchRequest.class), any());

        RestRequest request = new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY).withPath("/_plugins/_ml/profile/models").build();
        profileAction.handleRequest(request, channel, client);

        ArgumentCaptor<MLProfileRequest> argumentCaptor = ArgumentCaptor.forClass(MLProfileRequest.class);
        verify(client, times(1)).execute(eq(MLProfileAction.INSTANCE), argumentCaptor.capture(), any());
        Boolean returnAllModels = argumentCaptor.getValue().getMlProfileInput().isReturnAllModels();
        assertTrue(returnAllModels);
    }

    public void test_PrepareRequest_EmptyNodeProfile() throws Exception {
        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(1);
            SearchResponse response = createSearchModelResponse(); // Prepare your mocked response here
            listener.onResponse(response);
            return null;
        }).when(client).search(any(SearchRequest.class), any());
        doAnswer(invocation -> {
            ActionListener<MLProfileResponse> actionListener = invocation.getArgument(2);
            MLProfileResponse profileResponse = new MLProfileResponse(clusterName, new ArrayList<>(), new ArrayList<>());
            actionListener.onResponse(profileResponse);
            return null;
        }).when(client).execute(eq(MLProfileAction.INSTANCE), any(), any());

        RestRequest request = getRestRequest();
        profileAction.handleRequest(request, channel, client);

        ArgumentCaptor<MLProfileRequest> argumentCaptor = ArgumentCaptor.forClass(MLProfileRequest.class);
        verify(client, times(1)).execute(eq(MLProfileAction.INSTANCE), argumentCaptor.capture(), any());
        Set<String> taskIds = argumentCaptor.getValue().getMlProfileInput().getTaskIds();
        assertEquals(taskIds.size(), 1);
        assertTrue(taskIds.contains("test_id"));
    }

    public void test_PrepareRequest_EmptyNodeTasksSize() throws Exception {
        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(1);
            SearchResponse response = createSearchModelResponse(); // Prepare your mocked response here
            listener.onResponse(response);
            return null;
        }).when(client).search(any(SearchRequest.class), any());

        doAnswer(invocation -> {
            ActionListener<MLProfileResponse> actionListener = invocation.getArgument(2);
            Map<String, MLTask> nodeTasks = new HashMap<>();
            Map<String, MLModelProfile> nodeModels = new HashMap<>();
            MLProfileNodeResponse nodeResponse = new MLProfileNodeResponse(node, nodeTasks, nodeModels);
            MLProfileResponse profileResponse = new MLProfileResponse(clusterName, Arrays.asList(nodeResponse), new ArrayList<>());
            actionListener.onResponse(profileResponse);
            return null;
        }).when(client).execute(eq(MLProfileAction.INSTANCE), any(), any());

        RestRequest request = getRestRequest();
        profileAction.handleRequest(request, channel, client);

        ArgumentCaptor<MLProfileRequest> argumentCaptor = ArgumentCaptor.forClass(MLProfileRequest.class);
        verify(client, times(1)).execute(eq(MLProfileAction.INSTANCE), argumentCaptor.capture(), any());
        Set<String> taskIds = argumentCaptor.getValue().getMlProfileInput().getTaskIds();
        assertEquals(taskIds.size(), 1);
        assertTrue(taskIds.contains("test_id"));
    }

    public void test_PrepareRequest_WithRequestContent() throws Exception {
        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(1);
            SearchResponse response = createSearchModelResponse(); // Prepare your mocked response here
            listener.onResponse(response);
            return null;
        }).when(client).search(any(SearchRequest.class), any());

        MLProfileInput mlProfileInput = new MLProfileInput();
        RestRequest request = getProfileRestRequest(mlProfileInput);
        profileAction.handleRequest(request, channel, client);
        ArgumentCaptor<MLProfileRequest> argumentCaptor = ArgumentCaptor.forClass(MLProfileRequest.class);
        verify(client, times(1)).execute(eq(MLProfileAction.INSTANCE), argumentCaptor.capture(), any());
    }

    public void test_PrepareRequest_Failure() throws Exception {
        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(1);
            SearchResponse response = createSearchModelResponse(); // Prepare your mocked response here
            listener.onResponse(response);
            return null;
        }).when(client).search(any(SearchRequest.class), any());

        doAnswer(invocation -> {
            ActionListener<MLProfileResponse> actionListener = invocation.getArgument(2);
            actionListener.onFailure(new RuntimeException("test failure"));
            return null;
        }).when(client).execute(eq(MLProfileAction.INSTANCE), any(), any());

        RestRequest request = getRestRequest();
        profileAction.handleRequest(request, channel, client);
        ArgumentCaptor<MLProfileRequest> argumentCaptor = ArgumentCaptor.forClass(MLProfileRequest.class);
        verify(client, times(1)).execute(eq(MLProfileAction.INSTANCE), argumentCaptor.capture(), any());
    }

    public void test_Search_Failure() throws Exception {
        // Setup to simulate a search failure
        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(1);
            listener.onFailure(new Exception("Mocking Exception")); // Trigger failure
            return null;
        }).when(client).search(any(SearchRequest.class), any(ActionListener.class));

        // Create a RestRequest instance for testing
        RestRequest request = getRestRequest(); // Ensure this method correctly initializes a RestRequest

        // Handle the request with the expectation of handling a failure
        profileAction.handleRequest(request, channel, client);

        // Verification that the search method was called exactly once
        verify(client, times(1)).search(any(SearchRequest.class), any(ActionListener.class));

        // Capturing the response sent to the channel
        ArgumentCaptor<BytesRestResponse> responseCaptor = ArgumentCaptor.forClass(BytesRestResponse.class);
        verify(channel).sendResponse(responseCaptor.capture());

        // Check the response status code to see if it correctly reflects the error
        BytesRestResponse response = responseCaptor.getValue();
        assertEquals(RestStatus.OK, response.status());
        assertTrue(response.content().utf8ToString().contains("{}"));
    }

    public void test_WhenViewIsModel_ReturnModelViewResult() throws Exception {
        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(1);
            SearchResponse response = createSearchModelResponse(); // Prepare your mocked response here
            listener.onResponse(response);
            return null;
        }).when(client).search(any(SearchRequest.class), any());
        MLProfileInput mlProfileInput = new MLProfileInput();
        RestRequest request = getProfileRestRequestWithQueryParams(mlProfileInput, ImmutableMap.of("view", "model"));
        profileAction.handleRequest(request, channel, client);
        ArgumentCaptor<MLProfileRequest> argumentCaptor = ArgumentCaptor.forClass(MLProfileRequest.class);
        verify(client, times(1)).execute(eq(MLProfileAction.INSTANCE), argumentCaptor.capture(), any());
    }

    // public void testNodeViewOutput() throws Exception {
    // // Assuming setup for non-empty node responses as done in the initial setup
    // MLProfileInput mlProfileInput = new MLProfileInput();
    // RestRequest request = getProfileRestRequestWithQueryParams(mlProfileInput, ImmutableMap.of("view", "node"));
    // profileAction.handleRequest(request, channel, client);
    //
    // ArgumentCaptor<MLProfileRequest> argumentCaptor = ArgumentCaptor.forClass(MLProfileRequest.class);
    // verify(client, times(1)).execute(eq(MLProfileAction.INSTANCE), argumentCaptor.capture(), any());
    //
    // // Verify that the response is correctly formed for the node view
    // verify(channel).sendResponse(argThat(response -> {
    // // Ensure the response content matches expected node view structure
    // String content = response.content().utf8ToString();
    // return content.contains("\"node\":") && !content.contains("\"models\":");
    // }));
    // }

    public void testBackendFailureHandling() throws Exception {
        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(1);
            SearchResponse response = createSearchModelResponse(); // Prepare your mocked response here
            listener.onResponse(response);
            return null;
        }).when(client).search(any(SearchRequest.class), any());

        doAnswer(invocation -> {
            ActionListener<MLProfileResponse> listener = invocation.getArgument(2);
            listener.onFailure(new RuntimeException("Simulated backend failure"));
            return null;
        }).when(client).execute(eq(MLProfileAction.INSTANCE), any(MLProfileRequest.class), any(ActionListener.class));

        RestRequest request = getRestRequest();
        profileAction.handleRequest(request, channel, client);

        verify(channel).sendResponse(argThat(response -> response.status() == RestStatus.INTERNAL_SERVER_ERROR));
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

    private RestRequest getRestRequest() {
        Map<String, String> params = new HashMap<>();
        params.put("task_id", "test_id");
        RestRequest request = new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY).withParams(params).build();
        return request;
    }

    private RestRequest getModelRestRequest() {
        Map<String, String> params = new HashMap<>();
        params.put("model_id", "test_id");
        RestRequest request = new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY).withParams(params).build();
        return request;
    }
}
