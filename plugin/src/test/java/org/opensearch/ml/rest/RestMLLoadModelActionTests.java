/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.util.*;

import org.junit.Before;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.opensearch.action.ActionListener;
import org.opensearch.client.node.NodeClient;
import org.opensearch.common.Strings;
import org.opensearch.common.bytes.BytesArray;
import org.opensearch.common.settings.Settings;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.ml.common.transport.load.MLLoadModelAction;
import org.opensearch.ml.common.transport.load.MLLoadModelRequest;
import org.opensearch.ml.common.transport.model.MLModelGetResponse;
import org.opensearch.rest.RestChannel;
import org.opensearch.rest.RestHandler;
import org.opensearch.rest.RestRequest;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.test.rest.FakeRestRequest;
import org.opensearch.threadpool.TestThreadPool;
import org.opensearch.threadpool.ThreadPool;

import com.google.gson.Gson;

public class RestMLLoadModelActionTests extends OpenSearchTestCase {

    private RestMLLoadModelAction restMLLoadModelAction;
    private NodeClient client;
    private ThreadPool threadPool;

    @Mock
    RestChannel channel;

    @Before
    public void setup() {
        restMLLoadModelAction = new RestMLLoadModelAction();
        threadPool = new TestThreadPool(this.getClass().getSimpleName() + "ThreadPool");
        client = spy(new NodeClient(Settings.EMPTY, threadPool));
        doAnswer(invocation -> {
            ActionListener<MLModelGetResponse> actionListener = invocation.getArgument(2);
            return null;
        }).when(client).execute(eq(MLLoadModelAction.INSTANCE), any(), any());
    }

    @Override
    public void tearDown() throws Exception {
        super.tearDown();
        threadPool.shutdown();
        client.close();
    }

    public void testConstructor() {
        RestMLLoadModelAction mlLoadModel = new RestMLLoadModelAction();
        assertNotNull(mlLoadModel);
    }

    public void testGetName() {
        String actionName = restMLLoadModelAction.getName();
        assertFalse(Strings.isNullOrEmpty(actionName));
        assertEquals("ml_load_model_action", actionName);
    }

    public void testRoutes() {
        List<RestHandler.Route> routes = restMLLoadModelAction.routes();
        assertNotNull(routes);
        assertFalse(routes.isEmpty());
        RestHandler.Route route = routes.get(0);
        assertEquals(RestRequest.Method.POST, route.getMethod());
        assertEquals("/_plugins/_ml/models/{model_id}/_load", route.getPath());
    }

    public void testLoadModelRequest() throws Exception {
        RestRequest request = getRestRequest();
        restMLLoadModelAction.handleRequest(request, channel, client);
        ArgumentCaptor<MLLoadModelRequest> argumentCaptor = ArgumentCaptor.forClass(MLLoadModelRequest.class);
        verify(client, times(1)).execute(eq(MLLoadModelAction.INSTANCE), argumentCaptor.capture(), any());
        String[] modelNodeIds = argumentCaptor.getValue().getModelNodeIds();
        String modelId = argumentCaptor.getValue().getModelId();
        assertArrayEquals(new String[] { "id1", "id2", "id3" }, modelNodeIds);
        assertEquals("test_model", modelId);
    }

    public void testLoadModelRequest_NoContent() throws Exception {
        RestRequest.Method method = RestRequest.Method.POST;
        Map<String, String> params = new HashMap<>();
        params.put("model_id", "test_model");
        RestRequest request = new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY).withMethod(method).withParams(params).build();
        restMLLoadModelAction.handleRequest(request, channel, client);
        ArgumentCaptor<MLLoadModelRequest> argumentCaptor = ArgumentCaptor.forClass(MLLoadModelRequest.class);
        verify(client, times(1)).execute(eq(MLLoadModelAction.INSTANCE), argumentCaptor.capture(), any());
        String[] modelNodeIds = argumentCaptor.getValue().getModelNodeIds();
        String modelId = argumentCaptor.getValue().getModelId();
        assertNull(modelNodeIds);
        assertEquals("test_model", modelId);
    }

    private RestRequest getRestRequest() {
        RestRequest.Method method = RestRequest.Method.POST;
        final String[] modelNodeIds = { "id1", "id2", "id3" };
        final Map<String, Object> model = Map.of("node_ids", modelNodeIds);
        String requestContent = new Gson().toJson(model).toString();
        Map<String, String> params = new HashMap<>();
        params.put("model_id", "test_model");
        RestRequest request = new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY)
            .withMethod(method)
            .withParams(params)
            .withContent(new BytesArray(requestContent), XContentType.JSON)
            .build();
        return request;
    }
}
