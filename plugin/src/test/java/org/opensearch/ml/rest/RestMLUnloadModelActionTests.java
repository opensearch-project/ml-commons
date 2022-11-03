/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
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
import org.opensearch.common.settings.Settings;
import org.opensearch.common.xcontent.NamedXContentRegistry;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.ml.common.transport.model.MLModelGetResponse;
import org.opensearch.ml.common.transport.unload.MLUnloadModelAction;
import org.opensearch.ml.common.transport.unload.UnloadModelNodesRequest;
import org.opensearch.rest.RestChannel;
import org.opensearch.rest.RestHandler;
import org.opensearch.rest.RestRequest;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.test.rest.FakeRestRequest;
import org.opensearch.threadpool.TestThreadPool;
import org.opensearch.threadpool.ThreadPool;

import com.google.gson.Gson;

public class RestMLUnloadModelActionTests extends OpenSearchTestCase {

    private RestMLUnloadModelAction restMLUnloadModelAction;
    private NodeClient client;
    private ThreadPool threadPool;
    @Mock
    private ClusterService clusterService;
    @Mock
    ClusterState testState;

    @Mock
    RestChannel channel;

    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);
        restMLUnloadModelAction = new RestMLUnloadModelAction(clusterService);
        threadPool = new TestThreadPool(this.getClass().getSimpleName() + "ThreadPool");
        client = spy(new NodeClient(Settings.EMPTY, threadPool));
        doAnswer(invocation -> {
            ActionListener<MLModelGetResponse> actionListener = invocation.getArgument(2);
            return null;
        }).when(client).execute(eq(MLUnloadModelAction.INSTANCE), any(), any());
        testState = setupTestClusterState();
        when(clusterService.state()).thenReturn(testState);
    }

    @Override
    public void tearDown() throws Exception {
        super.tearDown();
        threadPool.shutdown();
        client.close();
    }

    public void testConstructor() {
        RestMLUnloadModelAction mlUnloadModel = new RestMLUnloadModelAction(clusterService);
        assertNotNull(mlUnloadModel);
    }

    public void testGetName() {
        String actionName = restMLUnloadModelAction.getName();
        assertFalse(Strings.isNullOrEmpty(actionName));
        assertEquals("ml_unload_model_action", actionName);
    }

    public void testRoutes() {
        List<RestHandler.Route> routes = restMLUnloadModelAction.routes();
        assertNotNull(routes);
        assertFalse(routes.isEmpty());
        RestHandler.Route route1 = routes.get(0);
        RestHandler.Route route2 = routes.get(1);
        assertEquals(RestRequest.Method.POST, route1.getMethod());
        assertEquals(RestRequest.Method.POST, route2.getMethod());
        assertEquals("/_plugins/_ml/models/{model_id}/_unload", route1.getPath());
        assertEquals("/_plugins/_ml/models/_unload", route2.getPath());

    }

    public void testUnloadModelRequest() throws Exception {
        RestRequest request = getRestRequest();
        restMLUnloadModelAction.handleRequest(request, channel, client);
        ArgumentCaptor<UnloadModelNodesRequest> argumentCaptor = ArgumentCaptor.forClass(UnloadModelNodesRequest.class);
        verify(client, times(1)).execute(eq(MLUnloadModelAction.INSTANCE), argumentCaptor.capture(), any());
        String[] targetModelIds = argumentCaptor.getValue().getModelIds();
        String[] targetNodeIds = argumentCaptor.getValue().nodesIds();
        assertNotNull(targetModelIds);
        assertArrayEquals(new String[] { "testTargetModel" }, targetModelIds);
        assertEquals(3, targetNodeIds.length);
        assertArrayEquals(new String[] { "nodeId1", "nodeId2", "nodeId3" }, targetNodeIds);
    }

    public void testUnloadModelRequest_NullModelId() throws Exception {
        RestRequest request = getRestRequest_NullModelId();
        restMLUnloadModelAction.handleRequest(request, channel, client);
        ArgumentCaptor<UnloadModelNodesRequest> argumentCaptor = ArgumentCaptor.forClass(UnloadModelNodesRequest.class);
        verify(client, times(1)).execute(eq(MLUnloadModelAction.INSTANCE), argumentCaptor.capture(), any());
        String[] targetModelIds = argumentCaptor.getValue().getModelIds();
        String[] targetNodeIds = argumentCaptor.getValue().nodesIds();
        assertNotNull(targetModelIds);
        assertEquals(3, targetNodeIds.length);
        assertArrayEquals(new String[] { "modelId1", "modelId2", "modelId3" }, targetModelIds);
        assertArrayEquals(new String[] { "nodeId1", "nodeId2", "nodeId3" }, targetNodeIds);
    }

    public void testUnloadModelRequest_EmptyRequest() throws Exception {
        RestRequest.Method method = RestRequest.Method.POST;
        Map<String, String> params = new HashMap<>();
        params.put("model_id", "testTargetModel");
        RestRequest request = new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY).withMethod(method).withParams(params).build();
        restMLUnloadModelAction.handleRequest(request, channel, client);
        ArgumentCaptor<UnloadModelNodesRequest> argumentCaptor = ArgumentCaptor.forClass(UnloadModelNodesRequest.class);
        verify(client, times(1)).execute(eq(MLUnloadModelAction.INSTANCE), argumentCaptor.capture(), any());
        String[] targetModelIds = argumentCaptor.getValue().getModelIds();
        String[] targetNodeIds = argumentCaptor.getValue().nodesIds();
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
