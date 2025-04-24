/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_MULTI_TENANCY_ENABLED;

import java.util.*;

import org.junit.Before;
import org.junit.Ignore;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.client.node.NodeClient;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.ClusterSettings;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.common.Strings;
import org.opensearch.core.common.bytes.BytesArray;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.transport.deploy.MLDeployModelAction;
import org.opensearch.ml.common.transport.deploy.MLDeployModelRequest;
import org.opensearch.ml.common.transport.model.MLModelGetResponse;
import org.opensearch.rest.RestChannel;
import org.opensearch.rest.RestHandler;
import org.opensearch.rest.RestRequest;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.test.rest.FakeRestRequest;
import org.opensearch.threadpool.TestThreadPool;
import org.opensearch.threadpool.ThreadPool;

import com.google.gson.Gson;

public class RestMLDeployModelActionTests extends OpenSearchTestCase {

    private RestMLDeployModelAction restMLDeployModelAction;
    private NodeClient client;
    private ThreadPool threadPool;

    Settings settings;

    @Mock
    private ClusterService clusterService;

    @Mock
    private MLFeatureEnabledSetting mlFeatureEnabledSetting;

    @Mock
    RestChannel channel;

    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);
        settings = Settings.builder().put(ML_COMMONS_MULTI_TENANCY_ENABLED.getKey(), false).build();
        when(clusterService.getSettings()).thenReturn(settings);
        when(clusterService.getClusterSettings()).thenReturn(new ClusterSettings(settings, Set.of(ML_COMMONS_MULTI_TENANCY_ENABLED)));
        when(mlFeatureEnabledSetting.isMultiTenancyEnabled()).thenReturn(false);
        restMLDeployModelAction = new RestMLDeployModelAction(mlFeatureEnabledSetting);
        threadPool = new TestThreadPool(this.getClass().getSimpleName() + "ThreadPool");
        client = spy(new NodeClient(Settings.EMPTY, threadPool));
        doAnswer(invocation -> {
            ActionListener<MLModelGetResponse> actionListener = invocation.getArgument(2);
            return null;
        }).when(client).execute(eq(MLDeployModelAction.INSTANCE), any(), any());
    }

    @Override
    public void tearDown() throws Exception {
        super.tearDown();
        threadPool.shutdown();
        client.close();
    }

    public void testConstructor() {
        RestMLDeployModelAction mlDeployModel = new RestMLDeployModelAction(mlFeatureEnabledSetting);
        assertNotNull(mlDeployModel);
    }

    public void testGetName() {
        String actionName = restMLDeployModelAction.getName();
        assertFalse(Strings.isNullOrEmpty(actionName));
        assertEquals("ml_deploy_model_action", actionName);
    }

    @Ignore
    public void testRoutes() {
        List<RestHandler.Route> routes = restMLDeployModelAction.routes();
        assertNotNull(routes);
        assertFalse(routes.isEmpty());
        RestHandler.Route route = routes.get(0);
        assertEquals(RestRequest.Method.POST, route.getMethod());
        assertEquals("/_plugins/_ml/models/{model_id}/_deploy", route.getPath());
    }

    public void testReplacedRoutes() {
        List<RestHandler.ReplacedRoute> replacedRoutes = restMLDeployModelAction.replacedRoutes();
        assertNotNull(replacedRoutes);
        assertFalse(replacedRoutes.isEmpty());
        RestHandler.Route route = replacedRoutes.get(0);
        assertEquals(RestRequest.Method.POST, route.getMethod());
        assertEquals("/_plugins/_ml/models/{model_id}/_deploy", route.getPath());
    }

    public void testDeployModelRequest() throws Exception {
        RestRequest request = getRestRequest();
        restMLDeployModelAction.handleRequest(request, channel, client);
        ArgumentCaptor<MLDeployModelRequest> argumentCaptor = ArgumentCaptor.forClass(MLDeployModelRequest.class);
        verify(client, times(1)).execute(eq(MLDeployModelAction.INSTANCE), argumentCaptor.capture(), any());
        String[] modelNodeIds = argumentCaptor.getValue().getModelNodeIds();
        String modelId = argumentCaptor.getValue().getModelId();
        assertArrayEquals(new String[] { "id1", "id2", "id3" }, modelNodeIds);
        assertEquals("test_model", modelId);
    }

    public void testDeployModelRequest_NoContent() throws Exception {
        RestRequest.Method method = RestRequest.Method.POST;
        Map<String, String> params = new HashMap<>();
        params.put("model_id", "test_model");
        RestRequest request = new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY).withMethod(method).withParams(params).build();
        restMLDeployModelAction.handleRequest(request, channel, client);
        ArgumentCaptor<MLDeployModelRequest> argumentCaptor = ArgumentCaptor.forClass(MLDeployModelRequest.class);
        verify(client, times(1)).execute(eq(MLDeployModelAction.INSTANCE), argumentCaptor.capture(), any());
        String[] modelNodeIds = argumentCaptor.getValue().getModelNodeIds();
        String modelId = argumentCaptor.getValue().getModelId();
        assertNull(modelNodeIds);
        assertEquals("test_model", modelId);
    }

    private RestRequest getRestRequest() {
        RestRequest.Method method = RestRequest.Method.POST;
        final String[] modelNodeIds = { "id1", "id2", "id3" };
        final Map<String, Object> model = Map.of("node_ids", modelNodeIds);
        String requestContent = new Gson().toJson(model);
        Map<String, String> params = new HashMap<>();
        params.put("model_id", "test_model");

        return new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY)
            .withMethod(method)
            .withParams(params)
            .withContent(new BytesArray(requestContent), XContentType.JSON)
            .build();
    }
}
