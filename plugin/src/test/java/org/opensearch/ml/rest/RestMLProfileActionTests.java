/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.opensearch.ml.utils.TestHelper.getProfileRestRequest;
import static org.opensearch.ml.utils.TestHelper.setupTestClusterState;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
import org.opensearch.action.ActionListener;
import org.opensearch.action.get.GetResponse;
import org.opensearch.action.get.MultiGetItemResponse;
import org.opensearch.action.get.MultiGetRequest;
import org.opensearch.action.get.MultiGetRequestBuilder;
import org.opensearch.action.get.MultiGetResponse;
import org.opensearch.action.search.SearchRequestBuilder;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.client.Client;
import org.opensearch.client.node.NodeClient;
import org.opensearch.cluster.ClusterName;
import org.opensearch.cluster.ClusterState;
import org.opensearch.cluster.node.DiscoveryNode;
import org.opensearch.cluster.node.DiscoveryNodeRole;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.Strings;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.common.io.stream.StreamInput;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.transport.TransportAddress;
import org.opensearch.common.xcontent.LoggingDeprecationHandler;
import org.opensearch.common.xcontent.NamedXContentRegistry;
import org.opensearch.common.xcontent.XContentFactory;
import org.opensearch.common.xcontent.XContentParser;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.commons.authuser.User;
import org.opensearch.index.IndexNotFoundException;
import org.opensearch.index.query.QueryBuilder;
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
import org.opensearch.ml.factory.MultiGetRequestBuilderFactory;
import org.opensearch.ml.profile.MLDeploymentProfile;
import org.opensearch.ml.profile.MLModelProfile;
import org.opensearch.ml.profile.MLPredictRequestStats;
import org.opensearch.ml.profile.MLProfileInput;
import org.opensearch.rest.RestChannel;
import org.opensearch.rest.RestHandler;
import org.opensearch.rest.RestRequest;
import org.opensearch.rest.RestResponse;
import org.opensearch.search.SearchHits;
import org.opensearch.search.fetch.subphase.FetchSourceContext;
import org.opensearch.search.sort.SortBuilder;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.test.rest.FakeRestRequest;
import org.opensearch.threadpool.TestThreadPool;
import org.opensearch.threadpool.ThreadPool;

import com.google.common.collect.ImmutableList;

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

    @Mock
    private MultiGetRequestBuilderFactory multiGetRequestBuilderFactory;

    @Mock
    private MultiGetRequestBuilder multiGetRequestBuilder;

    @Before
    public void setup() throws IOException {
        MockitoAnnotations.openMocks(this);
        profileAction = new RestMLProfileAction(clusterService, multiGetRequestBuilderFactory);

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
            .workerNode("test_node")
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
            .modelState(MLModelState.LOADED)
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
        when(multiGetRequestBuilderFactory.createMultiGetRequestBuilder(any(Client.class))).thenReturn(multiGetRequestBuilder);
    }

    @Override
    public void tearDown() throws Exception {
        super.tearDown();
        threadPool.shutdown();
        client.close();
    }

    public void testConstructor() {
        RestMLProfileAction action = new RestMLProfileAction(clusterService, multiGetRequestBuilderFactory);
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

    public void testPrepareRequest_whenPathParameterValueIsAll_returnBothNodesAndModels() throws Exception {
        MLProfileInput mlProfileInput = new MLProfileInput();
        mlProfileInput.setProfileAndDeployment("all");
        mlProfileInput.setReturnAllTasks(true);
        mlProfileInput.setReturnAllModels(true);
        testPrepareRequest_whenPathParameterValueIsAllOrDeployment_success(mlProfileInput);
    }

    public void testPrepareRequest_whenPathParameterValueIsDeployment_returnDeploymentOnly() throws Exception {
        MLProfileInput mlProfileInput = new MLProfileInput();
        mlProfileInput.setProfileAndDeployment("deployment");
        mlProfileInput.setReturnAllTasks(true);
        mlProfileInput.setReturnAllModels(true);
        testPrepareRequest_whenPathParameterValueIsAllOrDeployment_success(mlProfileInput);
    }

    private void testPrepareRequest_whenPathParameterValueIsAllOrDeployment_success(MLProfileInput mlProfileInput) throws Exception {
        RestRequest restRequest = getProfileRestRequest(mlProfileInput);
        MLProfileNodeResponse mlProfileNodeResponse = mock(MLProfileNodeResponse.class);
        Map<String, MLModelProfile> mlNodeModels = new HashMap<>();
        MLModelProfile mlModelProfile = mock(MLModelProfile.class);
        when(mlModelProfile.getWorkerNodes()).thenReturn(new String[] { "l5c_UZGaQFiA2qrE64JbRA", "kbmgTYi_R7qbTmksRzziqA" });
        mlNodeModels.put("hWVxL4UB7My4S9Dk2wW8", mlModelProfile);
        when(mlProfileNodeResponse.getMlNodeModels()).thenReturn(mlNodeModels);
        List<MLProfileNodeResponse> mlProfileNodeResponses = new ArrayList<>();
        mlProfileNodeResponses.add(mlProfileNodeResponse);
        MLProfileResponse mlProfileResponse = mock(MLProfileResponse.class);
        when(mlProfileResponse.getNodes()).thenReturn(mlProfileNodeResponses);

        MultiGetResponse multiGetResponse = mock(MultiGetResponse.class);
        MultiGetItemResponse multiGetItemResponse = mock((MultiGetItemResponse.class));
        GetResponse getResponse = mock(GetResponse.class);
        Map<String, Object> source = new HashMap<>();
        source.put("name", "model_name_1");
        when(getResponse.getSourceAsMap()).thenReturn(source);
        when(getResponse.getId()).thenReturn("hWVxL4UB7My4S9Dk2wW8");
        when(multiGetItemResponse.getResponse()).thenReturn(getResponse);

        MultiGetItemResponse[] multiGetItemResponses = new MultiGetItemResponse[] { multiGetItemResponse };
        when(multiGetResponse.getResponses()).thenReturn(multiGetItemResponses);

        MultiGetRequest multiGetRequest = mock(MultiGetRequest.class);
        when(multiGetRequestBuilder.request()).thenReturn(multiGetRequest);
        when(
            multiGetRequest
                .add(anyString(), any(String[].class), any(FetchSourceContext.class), anyString(), any(XContentParser.class), anyBoolean())
        ).thenReturn(multiGetRequest);
        doAnswer(invocation -> {
            ActionListener<MultiGetResponse> listener = invocation.getArgument(0);
            listener.onResponse(multiGetResponse);
            return null;
        }).when(multiGetRequestBuilder).execute(isA(ActionListener.class));

        SearchRequestBuilder searchRequestBuilder = mock(SearchRequestBuilder.class);
        when(client.prepareSearch(anyString())).thenReturn(searchRequestBuilder);
        when(searchRequestBuilder.setQuery(any(QueryBuilder.class))).thenReturn(searchRequestBuilder);
        when(searchRequestBuilder.setSize(anyInt())).thenReturn(searchRequestBuilder);
        when(searchRequestBuilder.addSort(any(SortBuilder.class))).thenReturn(searchRequestBuilder);

        SearchHits searchHits = null;
        String responseBody = Files
            .readString(
                Path.of(this.getClass().getClassLoader().getResource("org/opensearch/ml/rest/MockMLTaskIndexQueryResult.json").toURI())
            );
        try (
            XContentParser parser = XContentFactory
                .xContent(XContentType.JSON)
                .createParser(NamedXContentRegistry.EMPTY, LoggingDeprecationHandler.INSTANCE, responseBody)
        ) {
            searchHits = SearchHits.fromXContent(parser);
        } catch (IOException e) {
            fail(e.getMessage());
        }
        SearchResponse searchResponse = mock(SearchResponse.class);
        when(searchResponse.getHits()).thenReturn(searchHits);
        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(0);
            listener.onResponse(searchResponse);
            return null;
        }).when(searchRequestBuilder).execute(isA(ActionListener.class));

        doAnswer(invocation -> {
            ActionListener<MLProfileResponse> listener = invocation.getArgument(2);
            listener.onResponse(mlProfileResponse);
            return null;
        }).when(client).execute(eq(MLProfileAction.INSTANCE), any(MLProfileRequest.class), isA(ActionListener.class));
        profileAction.handleRequest(restRequest, channel, client);
        verify(channel, times(1)).sendResponse(any(RestResponse.class));
    }

    public void testPrepareRequest_whenMLModelIndexQueryFailedForAll_stopProcessingDeploymentInfo() throws Exception {
        MLProfileInput mlProfileInput = new MLProfileInput();
        mlProfileInput.setProfileAndDeployment("all");
        mlProfileInput.setReturnAllTasks(true);
        mlProfileInput.setReturnAllModels(true);
        testPrepareRequest_whenMLModelIndexQueryFailed_stopProcessingDeploymentInfo(mlProfileInput);
    }

    public void testPrepareRequest_whenMLModelIndexQueryFailedForDeployment_stopProcessingDeploymentInfo() throws Exception {
        MLProfileInput mlProfileInput = new MLProfileInput();
        mlProfileInput.setProfileAndDeployment("deployment");
        mlProfileInput.setReturnAllTasks(true);
        mlProfileInput.setReturnAllModels(true);
        testPrepareRequest_whenMLModelIndexQueryFailed_stopProcessingDeploymentInfo(mlProfileInput);
    }

    private void testPrepareRequest_whenMLModelIndexQueryFailed_stopProcessingDeploymentInfo(MLProfileInput mlProfileInput)
        throws Exception {
        RestRequest restRequest = getProfileRestRequest(mlProfileInput);
        MLProfileNodeResponse mlProfileNodeResponse = mock(MLProfileNodeResponse.class);
        Map<String, MLModelProfile> mlNodeModels = new HashMap<>();
        MLModelProfile mlModelProfile = mock(MLModelProfile.class);
        when(mlModelProfile.getWorkerNodes()).thenReturn(new String[] { "l5c_UZGaQFiA2qrE64JbRA", "kbmgTYi_R7qbTmksRzziqA" });
        mlNodeModels.put("hWVxL4UB7My4S9Dk2wW8", mlModelProfile);
        when(mlProfileNodeResponse.getMlNodeModels()).thenReturn(mlNodeModels);
        List<MLProfileNodeResponse> mlProfileNodeResponses = new ArrayList<>();
        mlProfileNodeResponses.add(mlProfileNodeResponse);
        MLProfileResponse mlProfileResponse = mock(MLProfileResponse.class);
        when(mlProfileResponse.getNodes()).thenReturn(mlProfileNodeResponses);

        MultiGetResponse multiGetResponse = mock(MultiGetResponse.class);
        MultiGetItemResponse multiGetItemResponse = mock((MultiGetItemResponse.class));
        GetResponse getResponse = mock(GetResponse.class);
        when(getResponse.getSourceAsMap()).thenReturn(null);
        when(getResponse.getId()).thenReturn("hWVxL4UB7My4S9Dk2wW8");
        when(multiGetItemResponse.getResponse()).thenReturn(getResponse);

        MultiGetItemResponse[] multiGetItemResponses = new MultiGetItemResponse[] { multiGetItemResponse };
        when(multiGetResponse.getResponses()).thenReturn(multiGetItemResponses);

        MultiGetRequest multiGetRequest = mock(MultiGetRequest.class);
        when(multiGetRequestBuilder.request()).thenReturn(multiGetRequest);
        when(
            multiGetRequest
                .add(anyString(), any(String[].class), any(FetchSourceContext.class), anyString(), any(XContentParser.class), anyBoolean())
        ).thenReturn(multiGetRequest);
        doAnswer(invocation -> {
            ActionListener<MultiGetResponse> listener = invocation.getArgument(0);
            listener.onResponse(multiGetResponse);
            return null;
        }).when(multiGetRequestBuilder).execute(isA(ActionListener.class));

        doAnswer(invocation -> {
            ActionListener<MLProfileResponse> listener = invocation.getArgument(2);
            listener.onResponse(mlProfileResponse);
            return null;
        }).when(client).execute(eq(MLProfileAction.INSTANCE), any(MLProfileRequest.class), isA(ActionListener.class));
        profileAction.handleRequest(restRequest, channel, client);
        verify(channel, times(1)).sendResponse(any(RestResponse.class));
    }

    public void testPrepareRequest_whenTaskIndexQueryFailedForAll_stopProcessingDeploymentInfo() throws Exception {
        MLProfileInput mlProfileInput = new MLProfileInput();
        mlProfileInput.setProfileAndDeployment("all");
        mlProfileInput.setReturnAllTasks(true);
        mlProfileInput.setReturnAllModels(true);
        testPrepareRequest_whenTaskIndexQueryFailed_stopProcessingDeploymentInfo(mlProfileInput);
    }

    public void testPrepareRequest_whenTaskIndexQueryFailedForDeployment_stopProcessingDeploymentInfo() throws Exception {
        MLProfileInput mlProfileInput = new MLProfileInput();
        mlProfileInput.setProfileAndDeployment("deployment");
        mlProfileInput.setReturnAllTasks(true);
        mlProfileInput.setReturnAllModels(true);
        testPrepareRequest_whenTaskIndexQueryFailed_stopProcessingDeploymentInfo(mlProfileInput);
    }

    private void testPrepareRequest_whenTaskIndexQueryFailed_stopProcessingDeploymentInfo(MLProfileInput mlProfileInput) throws Exception {
        RestRequest restRequest = getProfileRestRequest(mlProfileInput);
        MLProfileNodeResponse mlProfileNodeResponse = mock(MLProfileNodeResponse.class);
        Map<String, MLModelProfile> mlNodeModels = new HashMap<>();
        MLModelProfile mlModelProfile = mock(MLModelProfile.class);
        when(mlModelProfile.getWorkerNodes()).thenReturn(new String[] { "l5c_UZGaQFiA2qrE64JbRA", "kbmgTYi_R7qbTmksRzziqA" });
        mlNodeModels.put("hWVxL4UB7My4S9Dk2wW8", mlModelProfile);
        when(mlProfileNodeResponse.getMlNodeModels()).thenReturn(mlNodeModels);
        List<MLProfileNodeResponse> mlProfileNodeResponses = new ArrayList<>();
        mlProfileNodeResponses.add(mlProfileNodeResponse);
        MLProfileResponse mlProfileResponse = mock(MLProfileResponse.class);
        when(mlProfileResponse.getNodes()).thenReturn(mlProfileNodeResponses);

        MultiGetResponse multiGetResponse = mock(MultiGetResponse.class);
        MultiGetItemResponse multiGetItemResponse = mock((MultiGetItemResponse.class));
        GetResponse getResponse = mock(GetResponse.class);
        Map<String, Object> source = new HashMap<>();
        source.put("name", "model_name_1");
        when(getResponse.getSourceAsMap()).thenReturn(source);
        when(getResponse.getId()).thenReturn("hWVxL4UB7My4S9Dk2wW8");
        when(multiGetItemResponse.getResponse()).thenReturn(getResponse);

        MultiGetItemResponse[] multiGetItemResponses = new MultiGetItemResponse[] { multiGetItemResponse };
        when(multiGetResponse.getResponses()).thenReturn(multiGetItemResponses);

        MultiGetRequest multiGetRequest = mock(MultiGetRequest.class);
        when(multiGetRequestBuilder.request()).thenReturn(multiGetRequest);
        when(
            multiGetRequest
                .add(anyString(), any(String[].class), any(FetchSourceContext.class), anyString(), any(XContentParser.class), anyBoolean())
        ).thenReturn(multiGetRequest);
        doAnswer(invocation -> {
            ActionListener<MultiGetResponse> listener = invocation.getArgument(0);
            listener.onResponse(multiGetResponse);
            return null;
        }).when(multiGetRequestBuilder).execute(isA(ActionListener.class));

        SearchRequestBuilder searchRequestBuilder = mock(SearchRequestBuilder.class);
        when(client.prepareSearch(anyString())).thenReturn(searchRequestBuilder);
        when(searchRequestBuilder.setQuery(any(QueryBuilder.class))).thenReturn(searchRequestBuilder);
        when(searchRequestBuilder.setSize(anyInt())).thenReturn(searchRequestBuilder);
        when(searchRequestBuilder.addSort(any(SortBuilder.class))).thenReturn(searchRequestBuilder);

        SearchResponse searchResponse = mock(SearchResponse.class);
        when(searchResponse.getHits()).thenReturn(null);
        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(0);
            listener.onResponse(searchResponse);
            return null;
        }).when(searchRequestBuilder).execute(isA(ActionListener.class));

        doAnswer(invocation -> {
            ActionListener<MLProfileResponse> listener = invocation.getArgument(2);
            listener.onResponse(mlProfileResponse);
            return null;
        }).when(client).execute(eq(MLProfileAction.INSTANCE), any(MLProfileRequest.class), isA(ActionListener.class));
        profileAction.handleRequest(restRequest, channel, client);
        verify(channel, times(1)).sendResponse(any(RestResponse.class));
    }

    public void testPrepareRequest_whenMLModelIndexQueryExceptionForAll_returnDefaultProfileResponse() throws Exception {
        MLProfileInput mlProfileInput = new MLProfileInput();
        mlProfileInput.setProfileAndDeployment("all");
        mlProfileInput.setReturnAllTasks(true);
        mlProfileInput.setReturnAllModels(true);
        testPrepareRequest_whenMLModelIndexQueryException_returnDefaultProfileResponse(mlProfileInput);
    }

    public void testPrepareRequest_whenMLModelIndexQueryExceptionForDeployment_returnEmpty() throws Exception {
        MLProfileInput mlProfileInput = new MLProfileInput();
        mlProfileInput.setProfileAndDeployment("deployment");
        mlProfileInput.setReturnAllTasks(true);
        mlProfileInput.setReturnAllModels(true);
        testPrepareRequest_whenMLModelIndexQueryException_returnDefaultProfileResponse(mlProfileInput);
    }

    private void testPrepareRequest_whenMLModelIndexQueryException_returnDefaultProfileResponse(MLProfileInput mlProfileInput)
        throws Exception {
        RestRequest restRequest = getProfileRestRequest(mlProfileInput);
        MLProfileNodeResponse mlProfileNodeResponse = mock(MLProfileNodeResponse.class);
        Map<String, MLModelProfile> mlNodeModels = new HashMap<>();
        MLModelProfile mlModelProfile = mock(MLModelProfile.class);
        when(mlModelProfile.getWorkerNodes()).thenReturn(new String[] { "l5c_UZGaQFiA2qrE64JbRA", "kbmgTYi_R7qbTmksRzziqA" });
        mlNodeModels.put("hWVxL4UB7My4S9Dk2wW8", mlModelProfile);
        when(mlProfileNodeResponse.getMlNodeModels()).thenReturn(mlNodeModels);
        List<MLProfileNodeResponse> mlProfileNodeResponses = new ArrayList<>();
        mlProfileNodeResponses.add(mlProfileNodeResponse);
        MLProfileResponse mlProfileResponse = mock(MLProfileResponse.class);
        when(mlProfileResponse.getNodes()).thenReturn(mlProfileNodeResponses);

        MultiGetRequest multiGetRequest = mock(MultiGetRequest.class);
        when(multiGetRequestBuilder.request()).thenReturn(multiGetRequest);
        when(
            multiGetRequest
                .add(anyString(), any(String[].class), any(FetchSourceContext.class), anyString(), any(XContentParser.class), anyBoolean())
        ).thenReturn(multiGetRequest);
        doAnswer(invocation -> {
            ActionListener<MultiGetResponse> listener = invocation.getArgument(0);
            listener.onFailure(new IndexNotFoundException("index not found"));
            return null;
        }).when(multiGetRequestBuilder).execute(isA(ActionListener.class));

        profileAction.handleRequest(restRequest, channel, client);
        verify(channel, times(1)).sendResponse(any(RestResponse.class));
    }

    public void testPrepareRequest_whenMLTaskIndexDataQueryFailForAll_returnProfileOnly() throws Exception {
        MLProfileInput mlProfileInput = new MLProfileInput();
        mlProfileInput.setProfileAndDeployment("all");
        mlProfileInput.setReturnAllTasks(true);
        mlProfileInput.setReturnAllModels(true);
        testPrepareRequest_whenMLTaskIndexDataQueryFail_returnProfileOnly(mlProfileInput);
    }

    public void testPrepareRequest_whenMLTaskIndexDataQueryFailForDeployment_returnEmpty() throws Exception {
        MLProfileInput mlProfileInput = new MLProfileInput();
        mlProfileInput.setProfileAndDeployment("deployment");
        mlProfileInput.setReturnAllTasks(true);
        mlProfileInput.setReturnAllModels(true);
        testPrepareRequest_whenMLTaskIndexDataQueryFail_returnProfileOnly(mlProfileInput);
    }

    private void testPrepareRequest_whenMLTaskIndexDataQueryFail_returnProfileOnly(MLProfileInput mlProfileInput) throws Exception {
        RestRequest restRequest = getProfileRestRequest(mlProfileInput);
        MLProfileNodeResponse mlProfileNodeResponse = mock(MLProfileNodeResponse.class);
        Map<String, MLModelProfile> mlNodeModels = new HashMap<>();
        MLModelProfile mlModelProfile = mock(MLModelProfile.class);
        when(mlModelProfile.getWorkerNodes()).thenReturn(new String[] { "l5c_UZGaQFiA2qrE64JbRA", "kbmgTYi_R7qbTmksRzziqA" });
        mlNodeModels.put("hWVxL4UB7My4S9Dk2wW8", mlModelProfile);
        when(mlProfileNodeResponse.getMlNodeModels()).thenReturn(mlNodeModels);
        List<MLProfileNodeResponse> mlProfileNodeResponses = new ArrayList<>();
        mlProfileNodeResponses.add(mlProfileNodeResponse);
        MLProfileResponse mlProfileResponse = mock(MLProfileResponse.class);
        when(mlProfileResponse.getNodes()).thenReturn(mlProfileNodeResponses);

        MultiGetResponse multiGetResponse = mock(MultiGetResponse.class);
        MultiGetItemResponse multiGetItemResponse = mock((MultiGetItemResponse.class));
        GetResponse getResponse = mock(GetResponse.class);
        Map<String, Object> source = new HashMap<>();
        source.put("name", "model_name_1");
        when(getResponse.getSourceAsMap()).thenReturn(source);
        when(getResponse.getId()).thenReturn("hWVxL4UB7My4S9Dk2wW8");
        when(multiGetItemResponse.getResponse()).thenReturn(getResponse);

        MultiGetItemResponse[] multiGetItemResponses = new MultiGetItemResponse[] { multiGetItemResponse };
        when(multiGetResponse.getResponses()).thenReturn(multiGetItemResponses);

        MultiGetRequest multiGetRequest = mock(MultiGetRequest.class);
        when(multiGetRequestBuilder.request()).thenReturn(multiGetRequest);
        when(
            multiGetRequest
                .add(anyString(), any(String[].class), any(FetchSourceContext.class), anyString(), any(XContentParser.class), anyBoolean())
        ).thenReturn(multiGetRequest);
        doAnswer(invocation -> {
            ActionListener<MultiGetResponse> listener = invocation.getArgument(0);
            listener.onResponse(multiGetResponse);
            return null;
        }).when(multiGetRequestBuilder).execute(isA(ActionListener.class));

        SearchRequestBuilder searchRequestBuilder = mock(SearchRequestBuilder.class);
        when(client.prepareSearch(anyString())).thenReturn(searchRequestBuilder);
        when(searchRequestBuilder.setQuery(any(QueryBuilder.class))).thenReturn(searchRequestBuilder);
        when(searchRequestBuilder.setSize(anyInt())).thenReturn(searchRequestBuilder);
        when(searchRequestBuilder.addSort(any(SortBuilder.class))).thenReturn(searchRequestBuilder);

        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(0);
            listener.onFailure(new IndexNotFoundException("ml task index not found"));
            return null;
        }).when(searchRequestBuilder).execute(isA(ActionListener.class));

        doAnswer(invocation -> {
            ActionListener<MLProfileResponse> listener = invocation.getArgument(2);
            listener.onResponse(mlProfileResponse);
            return null;
        }).when(client).execute(eq(MLProfileAction.INSTANCE), any(MLProfileRequest.class), isA(ActionListener.class));
        profileAction.handleRequest(restRequest, channel, client);
        verify(channel, times(1)).sendResponse(any(RestResponse.class));
    }

    public void testPrepareRequest_whenExpectedDeployNodesMapEmptyForAll_returnProfileOnly() throws Exception {
        MLProfileInput mlProfileInput = new MLProfileInput();
        mlProfileInput.setProfileAndDeployment("all");
        mlProfileInput.setReturnAllTasks(true);
        mlProfileInput.setReturnAllModels(true);
        testPrepareRequest_whenExpectedDeployNodesMapEmpty_returnProfileOnly(mlProfileInput);
    }

    public void testPrepareRequest_whenExpectedDeployNodesMapEmptyForDeployment_returnEmpty() throws Exception {
        MLProfileInput mlProfileInput = new MLProfileInput();
        mlProfileInput.setProfileAndDeployment("deployment");
        mlProfileInput.setReturnAllTasks(true);
        mlProfileInput.setReturnAllModels(true);
        testPrepareRequest_whenExpectedDeployNodesMapEmpty_returnProfileOnly(mlProfileInput);
    }

    private void testPrepareRequest_whenExpectedDeployNodesMapEmpty_returnProfileOnly(MLProfileInput mlProfileInput) throws Exception {
        RestRequest restRequest = getProfileRestRequest(mlProfileInput);
        MLProfileNodeResponse mlProfileNodeResponse = mock(MLProfileNodeResponse.class);
        Map<String, MLModelProfile> mlNodeModels = new HashMap<>();
        MLModelProfile mlModelProfile = mock(MLModelProfile.class);
        when(mlModelProfile.getWorkerNodes()).thenReturn(new String[] { "l5c_UZGaQFiA2qrE64JbRA", "kbmgTYi_R7qbTmksRzziqA" });
        mlNodeModels.put("hWVxL4UB7My4S9Dk2wW8", mlModelProfile);
        when(mlProfileNodeResponse.getMlNodeModels()).thenReturn(mlNodeModels);
        List<MLProfileNodeResponse> mlProfileNodeResponses = new ArrayList<>();
        mlProfileNodeResponses.add(mlProfileNodeResponse);
        MLProfileResponse mlProfileResponse = mock(MLProfileResponse.class);
        when(mlProfileResponse.getNodes()).thenReturn(mlProfileNodeResponses);

        MultiGetResponse multiGetResponse = mock(MultiGetResponse.class);
        MultiGetItemResponse multiGetItemResponse = mock((MultiGetItemResponse.class));
        GetResponse getResponse = mock(GetResponse.class);
        Map<String, Object> source = new HashMap<>();
        source.put("name", "model_name_1");
        when(getResponse.getSourceAsMap()).thenReturn(source);
        when(getResponse.getId()).thenReturn("hWVxL4UB7My4S9Dk2wW8");
        when(multiGetItemResponse.getResponse()).thenReturn(getResponse);

        MultiGetItemResponse[] multiGetItemResponses = new MultiGetItemResponse[] { multiGetItemResponse };
        when(multiGetResponse.getResponses()).thenReturn(multiGetItemResponses);

        MultiGetRequest multiGetRequest = mock(MultiGetRequest.class);
        when(multiGetRequestBuilder.request()).thenReturn(multiGetRequest);
        when(
            multiGetRequest
                .add(anyString(), any(String[].class), any(FetchSourceContext.class), anyString(), any(XContentParser.class), anyBoolean())
        ).thenReturn(multiGetRequest);
        doAnswer(invocation -> {
            ActionListener<MultiGetResponse> listener = invocation.getArgument(0);
            listener.onResponse(multiGetResponse);
            return null;
        }).when(multiGetRequestBuilder).execute(isA(ActionListener.class));

        SearchRequestBuilder searchRequestBuilder = mock(SearchRequestBuilder.class);
        when(client.prepareSearch(anyString())).thenReturn(searchRequestBuilder);
        when(searchRequestBuilder.setQuery(any(QueryBuilder.class))).thenReturn(searchRequestBuilder);
        when(searchRequestBuilder.setSize(anyInt())).thenReturn(searchRequestBuilder);
        when(searchRequestBuilder.addSort(any(SortBuilder.class))).thenReturn(searchRequestBuilder);

        SearchHits searchHits = null;
        String responseBody = Files
            .readString(
                Path.of(this.getClass().getClassLoader().getResource("org/opensearch/ml/rest/EmptyMLTaskIndexQueryResult.json").toURI())
            );
        try (
            XContentParser parser = XContentFactory
                .xContent(XContentType.JSON)
                .createParser(NamedXContentRegistry.EMPTY, LoggingDeprecationHandler.INSTANCE, responseBody)
        ) {
            searchHits = SearchHits.fromXContent(parser);
        } catch (IOException e) {
            fail(e.getMessage());
        }
        SearchResponse searchResponse = mock(SearchResponse.class);
        when(searchResponse.getHits()).thenReturn(searchHits);
        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(0);
            listener.onResponse(searchResponse);
            return null;
        }).when(searchRequestBuilder).execute(isA(ActionListener.class));

        doAnswer(invocation -> {
            ActionListener<MLProfileResponse> listener = invocation.getArgument(2);
            listener.onResponse(mlProfileResponse);
            return null;
        }).when(client).execute(eq(MLProfileAction.INSTANCE), any(MLProfileRequest.class), isA(ActionListener.class));
        profileAction.handleRequest(restRequest, channel, client);
        verify(channel, times(1)).sendResponse(any(RestResponse.class));
    }

    public void testPrepareRequest_whenMGetModelIdsCreationException_returnProfileData() throws Exception {
        MLProfileInput mlProfileInput = new MLProfileInput();
        mlProfileInput.setProfileAndDeployment("all");
        mlProfileInput.setReturnAllTasks(true);
        mlProfileInput.setReturnAllModels(true);
        testPrepareRequest_whenMGetModelIdsCreationException_returnProfileData(mlProfileInput);
    }

    private void testPrepareRequest_whenMGetModelIdsCreationException_returnProfileData(MLProfileInput mlProfileInput) throws Exception {
        RestRequest restRequest = getProfileRestRequest(mlProfileInput);
        MLProfileNodeResponse mlProfileNodeResponse = mock(MLProfileNodeResponse.class);
        Map<String, MLModelProfile> mlNodeModels = new HashMap<>();
        MLModelProfile mlModelProfile = mock(MLModelProfile.class);
        when(mlModelProfile.getWorkerNodes()).thenReturn(new String[] { "l5c_UZGaQFiA2qrE64JbRA", "kbmgTYi_R7qbTmksRzziqA" });
        mlNodeModels.put("hWVxL4UB7My4S9Dk2wW8", mlModelProfile);
        when(mlProfileNodeResponse.getMlNodeModels()).thenReturn(mlNodeModels);
        List<MLProfileNodeResponse> mlProfileNodeResponses = new ArrayList<>();
        mlProfileNodeResponses.add(mlProfileNodeResponse);
        MLProfileResponse mlProfileResponse = mock(MLProfileResponse.class);
        when(mlProfileResponse.getNodes()).thenReturn(mlProfileNodeResponses);

        profileAction.handleRequest(restRequest, channel, client);
        verify(channel, times(1)).sendResponse(any(RestResponse.class));
    }

    public void testPrepareRequest_whenMGetModelIdsExecuteException_returnProfileData() throws Exception {
        MLProfileInput mlProfileInput = new MLProfileInput();
        mlProfileInput.setProfileAndDeployment("all");
        mlProfileInput.setReturnAllTasks(true);
        mlProfileInput.setReturnAllModels(true);
        testPrepareRequest_whenMGetModelIdsExecuteException_returnProfileData(mlProfileInput);
    }

    private void testPrepareRequest_whenMGetModelIdsExecuteException_returnProfileData(MLProfileInput mlProfileInput) throws Exception {
        RestRequest restRequest = getProfileRestRequest(mlProfileInput);
        MLProfileNodeResponse mlProfileNodeResponse = mock(MLProfileNodeResponse.class);
        Map<String, MLModelProfile> mlNodeModels = new HashMap<>();
        MLModelProfile mlModelProfile = mock(MLModelProfile.class);
        when(mlModelProfile.getWorkerNodes()).thenReturn(new String[] { "l5c_UZGaQFiA2qrE64JbRA", "kbmgTYi_R7qbTmksRzziqA" });
        mlNodeModels.put("hWVxL4UB7My4S9Dk2wW8", mlModelProfile);
        when(mlProfileNodeResponse.getMlNodeModels()).thenReturn(mlNodeModels);
        List<MLProfileNodeResponse> mlProfileNodeResponses = new ArrayList<>();
        mlProfileNodeResponses.add(mlProfileNodeResponse);
        MLProfileResponse mlProfileResponse = mock(MLProfileResponse.class);
        when(mlProfileResponse.getNodes()).thenReturn(mlProfileNodeResponses);

        MultiGetRequest multiGetRequest = mock(MultiGetRequest.class);
        when(multiGetRequestBuilder.request()).thenReturn(multiGetRequest);
        when(
            multiGetRequest
                .add(anyString(), any(String[].class), any(FetchSourceContext.class), anyString(), any(XContentParser.class), anyBoolean())
        ).thenReturn(multiGetRequest);
        doThrow(new RuntimeException()).when(multiGetRequestBuilder).execute(isA(ActionListener.class));

        profileAction.handleRequest(restRequest, channel, client);
        verify(channel, times(1)).sendResponse(any(RestResponse.class));
    }

    public void testPrepareRequest_whenMLTaskIndexDataQueryFailedForAll_thenReturnProfileOnly() throws Exception {
        MLProfileInput mlProfileInput = new MLProfileInput();
        mlProfileInput.setProfileAndDeployment("all");
        mlProfileInput.setReturnAllTasks(true);
        mlProfileInput.setReturnAllModels(true);
        testPrepareRequest_whenMLTaskIndexDataQueryFailed_thenReturnProfileOnly(mlProfileInput);
    }

    public void testPrepareRequest_whenMLTaskIndexDataQueryFailedForDeployment_thenReturnProfileOnly() throws Exception {
        MLProfileInput mlProfileInput = new MLProfileInput();
        mlProfileInput.setProfileAndDeployment("deployment");
        mlProfileInput.setReturnAllTasks(true);
        mlProfileInput.setReturnAllModels(true);
        testPrepareRequest_whenMLTaskIndexDataQueryFailed_thenReturnProfileOnly(mlProfileInput);
    }

    private void testPrepareRequest_whenMLTaskIndexDataQueryFailed_thenReturnProfileOnly(MLProfileInput mlProfileInput) throws Exception {
        RestRequest restRequest = getProfileRestRequest(mlProfileInput);
        MLProfileNodeResponse mlProfileNodeResponse = mock(MLProfileNodeResponse.class);
        Map<String, MLModelProfile> mlNodeModels = new HashMap<>();
        MLModelProfile mlModelProfile = mock(MLModelProfile.class);
        when(mlModelProfile.getWorkerNodes()).thenReturn(new String[] { "l5c_UZGaQFiA2qrE64JbRA", "kbmgTYi_R7qbTmksRzziqA" });
        mlNodeModels.put("hWVxL4UB7My4S9Dk2wW8", mlModelProfile);
        when(mlProfileNodeResponse.getMlNodeModels()).thenReturn(mlNodeModels);
        List<MLProfileNodeResponse> mlProfileNodeResponses = new ArrayList<>();
        mlProfileNodeResponses.add(mlProfileNodeResponse);
        MLProfileResponse mlProfileResponse = mock(MLProfileResponse.class);
        when(mlProfileResponse.getNodes()).thenReturn(mlProfileNodeResponses);

        MultiGetResponse multiGetResponse = mock(MultiGetResponse.class);
        MultiGetItemResponse multiGetItemResponse = mock((MultiGetItemResponse.class));
        GetResponse getResponse = mock(GetResponse.class);
        Map<String, Object> source = new HashMap<>();
        source.put("name", "model_name_1");
        when(getResponse.getSourceAsMap()).thenReturn(source);
        when(getResponse.getId()).thenReturn("hWVxL4UB7My4S9Dk2wW8");
        when(multiGetItemResponse.getResponse()).thenReturn(getResponse);

        MultiGetItemResponse[] multiGetItemResponses = new MultiGetItemResponse[] { multiGetItemResponse };
        when(multiGetResponse.getResponses()).thenReturn(multiGetItemResponses);

        MultiGetRequest multiGetRequest = mock(MultiGetRequest.class);
        when(multiGetRequestBuilder.request()).thenReturn(multiGetRequest);
        when(
            multiGetRequest
                .add(anyString(), any(String[].class), any(FetchSourceContext.class), anyString(), any(XContentParser.class), anyBoolean())
        ).thenReturn(multiGetRequest);
        doAnswer(invocation -> {
            ActionListener<MultiGetResponse> listener = invocation.getArgument(0);
            listener.onResponse(multiGetResponse);
            return null;
        }).when(multiGetRequestBuilder).execute(isA(ActionListener.class));

        SearchRequestBuilder searchRequestBuilder = mock(SearchRequestBuilder.class);
        when(client.prepareSearch(anyString())).thenReturn(searchRequestBuilder);
        when(searchRequestBuilder.setQuery(any(QueryBuilder.class))).thenReturn(searchRequestBuilder);
        when(searchRequestBuilder.setSize(anyInt())).thenReturn(searchRequestBuilder);
        when(searchRequestBuilder.addSort(any(SortBuilder.class))).thenReturn(searchRequestBuilder);

        doThrow(new RuntimeException()).when(searchRequestBuilder).execute(isA(ActionListener.class));

        doAnswer(invocation -> {
            ActionListener<MLProfileResponse> listener = invocation.getArgument(2);
            listener.onResponse(mlProfileResponse);
            return null;
        }).when(client).execute(eq(MLProfileAction.INSTANCE), any(MLProfileRequest.class), isA(ActionListener.class));
        profileAction.handleRequest(restRequest, channel, client);
        verify(channel, times(1)).sendResponse(any(RestResponse.class));
    }

    public void testMLDeploymentProfile_Constructor_withParameters() {
        MLDeploymentProfile mlDeploymentProfile = new MLDeploymentProfile(
            "mock_model_name",
            "mock_model_id",
            new ArrayList<>(),
            new ArrayList<>()
        );
        assert mlDeploymentProfile.getModelName() != null;
    }

    public void testMLDeploymentProfile_Constructor_withStreamInput() throws IOException {
        new MLDeploymentProfile(mock(StreamInput.class));
    }

    public void testMLDeploymentProfile_setters_getters() {
        MLDeploymentProfile mlDeploymentProfile = new MLDeploymentProfile("mock_model_name", "mock_model_id");
        mlDeploymentProfile.setNotDeployedNodeIds(ImmutableList.of("worker1"));
        mlDeploymentProfile.setTargetNodeIds(ImmutableList.of("worker1"));
        assert mlDeploymentProfile.getNotDeployedNodeIds() != null;
        assert mlDeploymentProfile.getTargetNodeIds() != null;
        assert mlDeploymentProfile.getModelName() != null;
        assert mlDeploymentProfile.getModelId() != null;
    }

    public void testMLDeploymentProfile_writeTo() throws IOException {
        MLDeploymentProfile mlDeploymentProfile = new MLDeploymentProfile(
            "mock_model_name",
            "mock_model_id",
            ImmutableList.of("worker1"),
            ImmutableList.of("worker2")
        );
        mlDeploymentProfile.writeTo(new BytesStreamOutput());
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
