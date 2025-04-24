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
import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_MULTI_TENANCY_ENABLED;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.ExpectedException;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.action.get.GetResponse;
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
import org.opensearch.ml.common.transport.model_group.MLUpdateModelGroupAction;
import org.opensearch.ml.common.transport.model_group.MLUpdateModelGroupInput;
import org.opensearch.ml.common.transport.model_group.MLUpdateModelGroupRequest;
import org.opensearch.rest.RestChannel;
import org.opensearch.rest.RestHandler;
import org.opensearch.rest.RestRequest;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.test.rest.FakeRestRequest;
import org.opensearch.threadpool.TestThreadPool;
import org.opensearch.threadpool.ThreadPool;

import com.google.gson.Gson;

public class RestMLUpdateModelGroupActionTests extends OpenSearchTestCase {
    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    private RestMLUpdateModelGroupAction restMLUpdateModelGroupAction;
    private NodeClient client;
    private ThreadPool threadPool;

    @Mock
    private MLFeatureEnabledSetting mlFeatureEnabledSetting;

    @Mock
    private ClusterService clusterService;
    Settings settings;

    @Mock
    RestChannel channel;

    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);
        threadPool = new TestThreadPool(this.getClass().getSimpleName() + "ThreadPool");
        client = spy(new NodeClient(Settings.EMPTY, threadPool));
        settings = Settings.builder().put(ML_COMMONS_MULTI_TENANCY_ENABLED.getKey(), false).build();
        when(clusterService.getSettings()).thenReturn(settings);
        when(clusterService.getClusterSettings()).thenReturn(new ClusterSettings(settings, Set.of(ML_COMMONS_MULTI_TENANCY_ENABLED)));
        when(mlFeatureEnabledSetting.isMultiTenancyEnabled()).thenReturn(false);
        restMLUpdateModelGroupAction = new RestMLUpdateModelGroupAction(mlFeatureEnabledSetting);
        doAnswer(invocation -> {
            ActionListener<GetResponse> actionListener = invocation.getArgument(2);
            return null;
        }).when(client).execute(eq(MLUpdateModelGroupAction.INSTANCE), any(), any());
    }

    @Override
    public void tearDown() throws Exception {
        super.tearDown();
        threadPool.shutdown();
        client.close();
    }

    public void testConstructor() {
        RestMLUpdateModelGroupAction UpdateModelGroupAction = new RestMLUpdateModelGroupAction(mlFeatureEnabledSetting);
        assertNotNull(UpdateModelGroupAction);
    }

    public void testGetName() {
        String actionName = restMLUpdateModelGroupAction.getName();
        assertFalse(Strings.isNullOrEmpty(actionName));
        assertEquals("ml_update_model_group_action", actionName);
    }

    public void testRoutes() {
        List<RestHandler.Route> routes = restMLUpdateModelGroupAction.routes();
        assertNotNull(routes);
        assertFalse(routes.isEmpty());
        RestHandler.Route route = routes.get(0);
        assertEquals(RestRequest.Method.PUT, route.getMethod());
        assertEquals("/_plugins/_ml/model_groups/{model_group_id}", route.getPath());
    }

    public void testUpdateModelGroupRequest() throws Exception {
        RestRequest request = getRestRequest();
        restMLUpdateModelGroupAction.handleRequest(request, channel, client);
        ArgumentCaptor<MLUpdateModelGroupRequest> argumentCaptor = ArgumentCaptor.forClass(MLUpdateModelGroupRequest.class);
        verify(client, times(1)).execute(eq(MLUpdateModelGroupAction.INSTANCE), argumentCaptor.capture(), any());
        MLUpdateModelGroupInput UpdateModelGroupInput = argumentCaptor.getValue().getUpdateModelGroupInput();
        assertEquals("testModelGroupName", UpdateModelGroupInput.getName());
        assertEquals("This is test description", UpdateModelGroupInput.getDescription());
    }

    public void testUpdateModelGroupRequestWithEmptyContent() throws Exception {
        exceptionRule.expect(IOException.class);
        exceptionRule.expectMessage("Model group request has empty body");
        RestRequest request = getRestRequestWithEmptyContent();
        restMLUpdateModelGroupAction.handleRequest(request, channel, client);
    }

    private RestRequest getRestRequest() {
        RestRequest.Method method = RestRequest.Method.POST;
        final Map<String, Object> modelGroup = Map.of("name", "testModelGroupName", "description", "This is test description");
        String requestContent = new Gson().toJson(modelGroup).toString();
        Map<String, String> params = new HashMap<>();
        params.put("model_group_id", "test_modelGroupId");
        RestRequest request = new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY)
            .withMethod(method)
            .withPath("/_plugins/_ml/model_groups/{model_group_id}")
            .withParams(params)
            .withContent(new BytesArray(requestContent), XContentType.JSON)
            .build();
        return request;
    }

    private RestRequest getRestRequestWithEmptyContent() {
        RestRequest.Method method = RestRequest.Method.POST;
        Map<String, String> params = new HashMap<>();
        params.put("model_group_id", "test_modelGroupId");
        RestRequest request = new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY)
            .withMethod(method)
            .withPath("/_plugins/_ml/model_groups/{model_group_id}")
            .withParams(params)
            .withContent(new BytesArray(""), XContentType.JSON)
            .build();
        return request;
    }
}
