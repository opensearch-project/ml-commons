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
import static org.opensearch.ml.settings.MLCommonsSettings.ML_COMMONS_ALLOW_CUSTOM_DEPLOYMENT_PLAN;
import static org.opensearch.ml.utils.TestHelper.clusterSetting;
import static org.opensearch.ml.utils.TestHelper.setupTestClusterState;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.action.ActionListener;
import org.opensearch.client.node.NodeClient;
import org.opensearch.cluster.ClusterState;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.Strings;
import org.opensearch.common.bytes.BytesArray;
import org.opensearch.common.settings.ClusterSettings;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.ml.common.transport.model.MLModelGetResponse;
import org.opensearch.ml.common.transport.undeploy.MLUndeployModelsAction;
import org.opensearch.ml.common.transport.undeploy.MLUndeployModelsRequest;
import org.opensearch.rest.RestChannel;
import org.opensearch.rest.RestHandler;
import org.opensearch.rest.RestRequest;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.test.rest.FakeRestRequest;
import org.opensearch.threadpool.TestThreadPool;
import org.opensearch.threadpool.ThreadPool;

import com.google.gson.Gson;

public class RestMLUndeployModelActionTests extends OpenSearchTestCase {

    private RestMLUndeployModelAction restMLUndeployModelAction;
    private NodeClient client;
    private ThreadPool threadPool;
    @Mock
    private ClusterService clusterService;
    @Mock
    ClusterState testState;

    @Mock
    RestChannel channel;

    private final Settings settings = Settings.builder().put(ML_COMMONS_ALLOW_CUSTOM_DEPLOYMENT_PLAN.getKey(), true).build();

    private final ClusterSettings clusterSettings = clusterSetting(settings, ML_COMMONS_ALLOW_CUSTOM_DEPLOYMENT_PLAN);

    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);
        testState = setupTestClusterState();
        when(clusterService.state()).thenReturn(testState);
        when(clusterService.getClusterSettings()).thenReturn(clusterSettings);
        restMLUndeployModelAction = new RestMLUndeployModelAction(clusterService, settings);
        threadPool = new TestThreadPool(this.getClass().getSimpleName() + "ThreadPool");
        client = spy(new NodeClient(Settings.EMPTY, threadPool));
        doAnswer(invocation -> {
            ActionListener<MLModelGetResponse> actionListener = invocation.getArgument(2);
            return null;
        }).when(client).execute(eq(MLUndeployModelsAction.INSTANCE), any(), any());

    }

    @Override
    public void tearDown() throws Exception {
        super.tearDown();
        threadPool.shutdown();
        client.close();
    }

    public void testConstructor() {
        RestMLUndeployModelAction undeployModel = new RestMLUndeployModelAction(clusterService, settings);
        assertNotNull(undeployModel);
    }

    public void testGetName() {
        String actionName = restMLUndeployModelAction.getName();
        assertFalse(Strings.isNullOrEmpty(actionName));
        assertEquals("ml_undeploy_model_action", actionName);
    }

    public void testReplacedRoutes() {
        List<RestHandler.ReplacedRoute> replacedRoutes = restMLUndeployModelAction.replacedRoutes();
        assertNotNull(replacedRoutes);
        assertFalse(replacedRoutes.isEmpty());
        RestHandler.Route route1 = replacedRoutes.get(0);
        RestHandler.Route route2 = replacedRoutes.get(1);
        assertEquals(RestRequest.Method.POST, route1.getMethod());
        assertEquals(RestRequest.Method.POST, route2.getMethod());
        assertEquals("/_plugins/_ml/models/{model_id}/_undeploy", route1.getPath());
        assertEquals("/_plugins/_ml/models/_undeploy", route2.getPath());

    }

    public void testUndeployModelRequest() throws Exception {
        RestRequest request = getRestRequest();
        restMLUndeployModelAction.handleRequest(request, channel, client);
        ArgumentCaptor<MLUndeployModelsRequest> argumentCaptor = ArgumentCaptor.forClass(MLUndeployModelsRequest.class);
        verify(client, times(1)).execute(eq(MLUndeployModelsAction.INSTANCE), argumentCaptor.capture(), any());
        String[] targetModelIds = argumentCaptor.getValue().getModelIds();
        String[] targetNodeIds = argumentCaptor.getValue().getNodeIds();
        assertNotNull(targetModelIds);
        assertArrayEquals(new String[] { "testTargetModel" }, targetModelIds);
        assertEquals(3, targetNodeIds.length);
        assertArrayEquals(new String[] { "nodeId1", "nodeId2", "nodeId3" }, targetNodeIds);
    }

    public void testUndeployModelRequest_NullModelId() throws Exception {
        RestRequest request = getRestRequest_NullModelId();
        restMLUndeployModelAction.handleRequest(request, channel, client);
        ArgumentCaptor<MLUndeployModelsRequest> argumentCaptor = ArgumentCaptor.forClass(MLUndeployModelsRequest.class);
        verify(client, times(1)).execute(eq(MLUndeployModelsAction.INSTANCE), argumentCaptor.capture(), any());
        String[] targetModelIds = argumentCaptor.getValue().getModelIds();
        String[] targetNodeIds = argumentCaptor.getValue().getNodeIds();
        assertNotNull(targetModelIds);
        assertEquals(3, targetNodeIds.length);
        assertArrayEquals(new String[] { "modelId1", "modelId2", "modelId3" }, targetModelIds);
        assertArrayEquals(new String[] { "nodeId1", "nodeId2", "nodeId3" }, targetNodeIds);
    }

    public void testUndeployModelRequest_EmptyRequest() throws Exception {
        RestRequest.Method method = RestRequest.Method.POST;
        Map<String, String> params = new HashMap<>();
        params.put("model_id", "testTargetModel");
        RestRequest request = new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY).withMethod(method).withParams(params).build();
        restMLUndeployModelAction.handleRequest(request, channel, client);
        ArgumentCaptor<MLUndeployModelsRequest> argumentCaptor = ArgumentCaptor.forClass(MLUndeployModelsRequest.class);
        verify(client, times(1)).execute(eq(MLUndeployModelsAction.INSTANCE), argumentCaptor.capture(), any());
        String[] targetModelIds = argumentCaptor.getValue().getModelIds();
        String[] targetNodeIds = argumentCaptor.getValue().getNodeIds();
        assertArrayEquals(new String[] { "testTargetModel" }, targetModelIds);
        assertNotNull(targetNodeIds);
    }

    private RestRequest getRestRequest() {
        RestRequest.Method method = RestRequest.Method.POST;
        String[] nodeIds = { "nodeId1", "nodeId2", "nodeId3" };
        final Map<String, Object> model = Map.of("node_ids", nodeIds);
        String requestContent = new Gson().toJson(model).toString();
        Map<String, String> params = new HashMap<>();
        params.put("model_id", "testTargetModel");
        RestRequest request = new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY)
            .withMethod(method)
            .withParams(params)
            .withContent(new BytesArray(requestContent), XContentType.JSON)
            .build();
        return request;
    }

    private RestRequest getRestRequest_NullModelId() {
        RestRequest.Method method = RestRequest.Method.POST;
        String[] modelIds = { "modelId1", "modelId2", "modelId3" };
        String[] nodeIds = { "nodeId1", "nodeId2", "nodeId3" };
        final Map<String, Object> model = Map.of("model_ids", modelIds, "node_ids", nodeIds);
        String requestContent = new Gson().toJson(model).toString();
        Map<String, String> params = new HashMap<>();
        RestRequest request = new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY)
            .withMethod(method)
            .withParams(params)
            .withContent(new BytesArray(requestContent), XContentType.JSON)
            .build();
        return request;
    }

}
