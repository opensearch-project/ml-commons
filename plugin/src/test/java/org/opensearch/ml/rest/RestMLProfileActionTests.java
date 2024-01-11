/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.opensearch.ml.utils.TestHelper.*;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.ExpectedException;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.Version;
import org.opensearch.client.node.NodeClient;
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
import org.opensearch.core.common.transport.TransportAddress;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.ml.action.profile.MLProfileAction;
import org.opensearch.ml.action.profile.MLProfileNodeResponse;
import org.opensearch.ml.action.profile.MLProfileRequest;
import org.opensearch.ml.action.profile.MLProfileResponse;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.MLTask;
import org.opensearch.ml.common.MLTaskState;
import org.opensearch.ml.common.MLTaskType;
import org.opensearch.ml.common.dataset.MLInputDataType;
import org.opensearch.ml.common.model.MLModelState;
import org.opensearch.ml.profile.MLModelProfile;
import org.opensearch.ml.profile.MLPredictRequestStats;
import org.opensearch.ml.profile.MLProfileInput;
import org.opensearch.rest.RestChannel;
import org.opensearch.rest.RestHandler;
import org.opensearch.rest.RestRequest;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.test.rest.FakeRestRequest;
import org.opensearch.threadpool.TestThreadPool;
import org.opensearch.threadpool.ThreadPool;

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

        threadPool = new TestThreadPool(this.getClass().getSimpleName() + "ThreadPool");
        client = spy(new NodeClient(Settings.EMPTY, threadPool));
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
            .workerNodes(List.of("test_node"))
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
        testState = setupTestClusterState();
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
        RestRequest request = getRestRequest();
        profileAction.handleRequest(request, channel, client);

        ArgumentCaptor<MLProfileRequest> argumentCaptor = ArgumentCaptor.forClass(MLProfileRequest.class);
        verify(client, times(1)).execute(eq(MLProfileAction.INSTANCE), argumentCaptor.capture(), any());
        Set<String> taskIds = argumentCaptor.getValue().getMlProfileInput().getTaskIds();
        assertEquals(taskIds.size(), 1);
        assertTrue(taskIds.contains("test_id"));
    }

    public void test_PrepareRequest_TaskRequestWithNoTaskIds() throws Exception {
        RestRequest request = new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY).withPath("/_plugins/_ml/profile/tasks").build();
        profileAction.handleRequest(request, channel, client);

        ArgumentCaptor<MLProfileRequest> argumentCaptor = ArgumentCaptor.forClass(MLProfileRequest.class);
        verify(client, times(1)).execute(eq(MLProfileAction.INSTANCE), argumentCaptor.capture(), any());
        Boolean returnAllTasks = argumentCaptor.getValue().getMlProfileInput().isReturnAllTasks();
        assertTrue(returnAllTasks);
    }

    public void test_PrepareRequest_ModelRequest() throws Exception {
        RestRequest request = getModelRestRequest();
        profileAction.handleRequest(request, channel, client);

        ArgumentCaptor<MLProfileRequest> argumentCaptor = ArgumentCaptor.forClass(MLProfileRequest.class);
        verify(client, times(1)).execute(eq(MLProfileAction.INSTANCE), argumentCaptor.capture(), any());
        Set<String> modelIds = argumentCaptor.getValue().getMlProfileInput().getModelIds();
        assertEquals(modelIds.size(), 1);
        assertTrue(modelIds.contains("test_id"));
    }

    public void test_PrepareRequest_TaskRequestWithNoModelIds() throws Exception {
        RestRequest request = new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY).withPath("/_plugins/_ml/profile/models").build();
        profileAction.handleRequest(request, channel, client);

        ArgumentCaptor<MLProfileRequest> argumentCaptor = ArgumentCaptor.forClass(MLProfileRequest.class);
        verify(client, times(1)).execute(eq(MLProfileAction.INSTANCE), argumentCaptor.capture(), any());
        Boolean returnAllModels = argumentCaptor.getValue().getMlProfileInput().isReturnAllModels();
        assertTrue(returnAllModels);
    }

    public void test_PrepareRequest_EmptyNodeProfile() throws Exception {
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
        MLProfileInput mlProfileInput = new MLProfileInput();
        RestRequest request = getProfileRestRequest(mlProfileInput);
        profileAction.handleRequest(request, channel, client);
        ArgumentCaptor<MLProfileRequest> argumentCaptor = ArgumentCaptor.forClass(MLProfileRequest.class);
        verify(client, times(1)).execute(eq(MLProfileAction.INSTANCE), argumentCaptor.capture(), any());
    }

    public void test_PrepareRequest_Failure() throws Exception {
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

    public void test_WhenViewIsModel_ReturnModelViewResult() throws Exception {
        MLProfileInput mlProfileInput = new MLProfileInput();
        RestRequest request = getProfileRestRequestWithQueryParams(mlProfileInput, Map.of("view", "model"));
        profileAction.handleRequest(request, channel, client);
        ArgumentCaptor<MLProfileRequest> argumentCaptor = ArgumentCaptor.forClass(MLProfileRequest.class);
        verify(client, times(1)).execute(eq(MLProfileAction.INSTANCE), argumentCaptor.capture(), any());
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
