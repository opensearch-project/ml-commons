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
import static org.opensearch.ml.common.input.Constants.TENANT_ID_HEADER;
import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_MULTI_TENANCY_ENABLED;
import static org.opensearch.ml.utils.RestActionUtils.PARAMETER_MODEL_GROUP_ID;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.Before;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.action.delete.DeleteResponse;
import org.opensearch.client.node.NodeClient;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.ClusterSettings;
import org.opensearch.common.settings.Settings;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.common.Strings;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.transport.model_group.MLModelGroupDeleteAction;
import org.opensearch.ml.common.transport.model_group.MLModelGroupDeleteRequest;
import org.opensearch.rest.RestChannel;
import org.opensearch.rest.RestHandler;
import org.opensearch.rest.RestRequest;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.test.rest.FakeRestRequest;
import org.opensearch.threadpool.TestThreadPool;
import org.opensearch.threadpool.ThreadPool;

public class RestMLDeleteModelGroupActionTests extends OpenSearchTestCase {

    private RestMLDeleteModelGroupAction restMLDeleteModelGroupAction;

    NodeClient client;
    private ThreadPool threadPool;

    @Mock
    RestChannel channel;

    @Mock
    private MLFeatureEnabledSetting mlFeatureEnabledSetting;

    @Mock
    private ClusterService clusterService;

    Settings settings;

    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);
        MockitoAnnotations.openMocks(this);
        when(clusterService.getSettings()).thenReturn(settings);
        when(clusterService.getClusterSettings()).thenReturn(new ClusterSettings(settings, Set.of(ML_COMMONS_MULTI_TENANCY_ENABLED)));
        when(mlFeatureEnabledSetting.isMultiTenancyEnabled()).thenReturn(false);
        restMLDeleteModelGroupAction = new RestMLDeleteModelGroupAction(mlFeatureEnabledSetting);

        threadPool = new TestThreadPool(this.getClass().getSimpleName() + "ThreadPool");
        client = spy(new NodeClient(Settings.EMPTY, threadPool));

        doAnswer(invocation -> {
            ActionListener<DeleteResponse> actionListener = invocation.getArgument(2);
            return null;
        }).when(client).execute(eq(MLModelGroupDeleteAction.INSTANCE), any(), any());

    }

    @Override
    public void tearDown() throws Exception {
        super.tearDown();
        threadPool.shutdown();
        client.close();
    }

    public void testConstructor() {
        RestMLDeleteModelGroupAction mlDeleteModelGroupAction = new RestMLDeleteModelGroupAction(mlFeatureEnabledSetting);
        assertNotNull(mlDeleteModelGroupAction);
    }

    public void testGetName() {
        String actionName = restMLDeleteModelGroupAction.getName();
        assertFalse(Strings.isNullOrEmpty(actionName));
        assertEquals("ml_delete_model_group_action", actionName);
    }

    public void testRoutes() {
        List<RestHandler.Route> routes = restMLDeleteModelGroupAction.routes();
        assertNotNull(routes);
        assertFalse(routes.isEmpty());
        RestHandler.Route route = routes.get(0);
        assertEquals(RestRequest.Method.DELETE, route.getMethod());
        assertEquals("/_plugins/_ml/model_groups/{model_group_id}", route.getPath());
    }

    public void test_PrepareRequest() throws Exception {
        RestRequest request = getRestRequest();
        restMLDeleteModelGroupAction.handleRequest(request, channel, client);

        ArgumentCaptor<MLModelGroupDeleteRequest> argumentCaptor = ArgumentCaptor.forClass(MLModelGroupDeleteRequest.class);
        verify(client, times(1)).execute(eq(MLModelGroupDeleteAction.INSTANCE), argumentCaptor.capture(), any());
        String taskId = argumentCaptor.getValue().getModelGroupId();
        assertEquals(taskId, "test_id");
    }

    public void test_PrepareRequest_WithTenantId() throws Exception {
        // Enable multi-tenancy
        when(mlFeatureEnabledSetting.isMultiTenancyEnabled()).thenReturn(true);

        // Create RestRequest with tenantId in header
        RestRequest request = getRestRequestWithTenantId("test_tenant");
        restMLDeleteModelGroupAction.handleRequest(request, channel, client);

        // Capture request sent to client
        ArgumentCaptor<MLModelGroupDeleteRequest> argumentCaptor = ArgumentCaptor.forClass(MLModelGroupDeleteRequest.class);
        verify(client, times(1)).execute(eq(MLModelGroupDeleteAction.INSTANCE), argumentCaptor.capture(), any());

        // Verify modelGroupId and tenantId
        MLModelGroupDeleteRequest capturedRequest = argumentCaptor.getValue();
        assertEquals("test_id", capturedRequest.getModelGroupId());
        assertEquals("test_tenant", capturedRequest.getTenantId());
    }

    private RestRequest getRestRequest() {
        Map<String, String> params = new HashMap<>();
        params.put(PARAMETER_MODEL_GROUP_ID, "test_id");
        return new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY).withParams(params).build();
    }

    private RestRequest getRestRequestWithTenantId(String tenantId) {
        Map<String, String> params = new HashMap<>();
        params.put(PARAMETER_MODEL_GROUP_ID, "test_id");

        Map<String, List<String>> headers = new HashMap<>();
        headers.put(TENANT_ID_HEADER, List.of(tenantId)); // Add tenant ID to headers

        return new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY).withParams(params).withHeaders(headers).build();
    }
}
