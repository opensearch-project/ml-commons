/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.times;
import static org.opensearch.ml.common.input.Constants.TENANT_ID_HEADER;
import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_MULTI_TENANCY_ENABLED;
import static org.opensearch.ml.utils.RestActionUtils.PARAMETER_MODEL_ID;

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
import org.opensearch.client.node.NodeClient;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.ClusterSettings;
import org.opensearch.common.settings.Settings;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.common.Strings;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.transport.model.MLModelGetAction;
import org.opensearch.ml.common.transport.model.MLModelGetRequest;
import org.opensearch.ml.common.transport.model.MLModelGetResponse;
import org.opensearch.rest.RestChannel;
import org.opensearch.rest.RestHandler;
import org.opensearch.rest.RestRequest;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.test.rest.FakeRestRequest;
import org.opensearch.threadpool.TestThreadPool;
import org.opensearch.threadpool.ThreadPool;

public class RestMLGetModelActionTests extends OpenSearchTestCase {
    @Rule
    public ExpectedException thrown = ExpectedException.none();

    private RestMLGetModelAction restMLGetModelAction;

    NodeClient client;
    private ThreadPool threadPool;

    @Mock
    RestChannel channel;

    @Mock
    private MLFeatureEnabledSetting mlFeatureEnabledSetting;

    Settings settings;

    @Mock
    private ClusterService clusterService;

    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);
        settings = Settings.builder().put(ML_COMMONS_MULTI_TENANCY_ENABLED.getKey(), false).build();
        when(clusterService.getSettings()).thenReturn(settings);
        when(clusterService.getClusterSettings()).thenReturn(new ClusterSettings(settings, Set.of(ML_COMMONS_MULTI_TENANCY_ENABLED)));
        when(mlFeatureEnabledSetting.isMultiTenancyEnabled()).thenReturn(false);
        restMLGetModelAction = new RestMLGetModelAction(mlFeatureEnabledSetting);

        threadPool = new TestThreadPool(this.getClass().getSimpleName() + "ThreadPool");
        client = spy(new NodeClient(Settings.EMPTY, threadPool));

        doAnswer(invocation -> {
            ActionListener<MLModelGetResponse> actionListener = invocation.getArgument(2);
            return null;
        }).when(client).execute(eq(MLModelGetAction.INSTANCE), any(), any());

    }

    @Override
    public void tearDown() throws Exception {
        super.tearDown();
        threadPool.shutdown();
        client.close();
    }

    public void testConstructor() {
        RestMLGetModelAction mlGetModelAction = new RestMLGetModelAction(mlFeatureEnabledSetting);
        assertNotNull(mlGetModelAction);
    }

    public void testGetName() {
        String actionName = restMLGetModelAction.getName();
        assertFalse(Strings.isNullOrEmpty(actionName));
        assertEquals("ml_get_model_action", actionName);
    }

    public void testRoutes() {
        List<RestHandler.Route> routes = restMLGetModelAction.routes();
        assertNotNull(routes);
        assertFalse(routes.isEmpty());
        RestHandler.Route route = routes.get(0);
        assertEquals(RestRequest.Method.GET, route.getMethod());
        assertEquals("/_plugins/_ml/models/{model_id}", route.getPath());
    }

    public void test_PrepareRequest_WithTenantId() throws Exception {
        // Enable multi-tenancy
        when(mlFeatureEnabledSetting.isMultiTenancyEnabled()).thenReturn(true);

        // Create RestRequest with tenantId in the header
        RestRequest request = getRestRequestWithTenantId("test_tenant");
        restMLGetModelAction.handleRequest(request, channel, client);

        // Capture request sent to client
        ArgumentCaptor<MLModelGetRequest> argumentCaptor = ArgumentCaptor.forClass(MLModelGetRequest.class);
        verify(client, times(1)).execute(eq(MLModelGetAction.INSTANCE), argumentCaptor.capture(), any());

        // Verify modelId and tenantId
        MLModelGetRequest capturedRequest = argumentCaptor.getValue();
        assertEquals("test_id", capturedRequest.getModelId());
        assertEquals("test_tenant", capturedRequest.getTenantId());
    }

    public void test_PrepareRequest() throws Exception {
        RestRequest request = getRestRequest();
        restMLGetModelAction.handleRequest(request, channel, client);

        ArgumentCaptor<MLModelGetRequest> argumentCaptor = ArgumentCaptor.forClass(MLModelGetRequest.class);
        verify(client, times(1)).execute(eq(MLModelGetAction.INSTANCE), argumentCaptor.capture(), any());
        String taskId = argumentCaptor.getValue().getModelId();
        assertEquals(taskId, "test_id");
    }

    private RestRequest getRestRequestWithTenantId(String tenantId) {
        Map<String, String> params = new HashMap<>();
        params.put(PARAMETER_MODEL_ID, "test_id");

        Map<String, List<String>> headers = new HashMap<>();
        headers.put(TENANT_ID_HEADER, List.of(tenantId)); // Add tenant ID to headers

        return new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY).withParams(params).withHeaders(headers).build();
    }

    private RestRequest getRestRequest() {
        Map<String, String> params = new HashMap<>();
        params.put(PARAMETER_MODEL_ID, "test_id");
        RestRequest request = new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY).withParams(params).build();
        return request;
    }
}
