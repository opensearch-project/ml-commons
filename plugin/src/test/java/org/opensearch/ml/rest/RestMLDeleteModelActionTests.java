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
import static org.opensearch.ml.utils.RestActionUtils.PARAMETER_MODEL_ID;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.action.delete.DeleteResponse;
import org.opensearch.client.node.NodeClient;
import org.opensearch.common.settings.Settings;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.common.Strings;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.transport.model.MLModelDeleteAction;
import org.opensearch.ml.common.transport.model.MLModelDeleteRequest;
import org.opensearch.rest.RestChannel;
import org.opensearch.rest.RestHandler;
import org.opensearch.rest.RestRequest;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.test.rest.FakeRestRequest;
import org.opensearch.threadpool.TestThreadPool;
import org.opensearch.threadpool.ThreadPool;

public class RestMLDeleteModelActionTests extends OpenSearchTestCase {

    private RestMLDeleteModelAction restMLDeleteModelAction;

    NodeClient client;
    private ThreadPool threadPool;

    @Mock
    RestChannel channel;

    @Mock
    private MLFeatureEnabledSetting mlFeatureEnabledSetting;

    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);
        when(mlFeatureEnabledSetting.isRemoteInferenceEnabled()).thenReturn(false);
        restMLDeleteModelAction = new RestMLDeleteModelAction(mlFeatureEnabledSetting);

        threadPool = new TestThreadPool(this.getClass().getSimpleName() + "ThreadPool");
        client = spy(new NodeClient(Settings.EMPTY, threadPool));

        doAnswer(invocation -> {
            ActionListener<DeleteResponse> actionListener = invocation.getArgument(2);
            return null;
        }).when(client).execute(eq(MLModelDeleteAction.INSTANCE), any(), any());

    }

    @Override
    public void tearDown() throws Exception {
        super.tearDown();
        threadPool.shutdown();
        client.close();
    }

    @Test
    public void testConstructor() {
        RestMLDeleteModelAction mlDeleteModelAction = new RestMLDeleteModelAction(mlFeatureEnabledSetting);
        assertNotNull(mlDeleteModelAction);
    }

    @Test
    public void testGetName() {
        String actionName = restMLDeleteModelAction.getName();
        assertFalse(Strings.isNullOrEmpty(actionName));
        assertEquals("ml_delete_model_action", actionName);
    }

    @Test
    public void testRoutes() {
        List<RestHandler.Route> routes = restMLDeleteModelAction.routes();
        assertNotNull(routes);
        assertFalse(routes.isEmpty());
        RestHandler.Route route = routes.get(0);
        assertEquals(RestRequest.Method.DELETE, route.getMethod());
        assertEquals("/_plugins/_ml/models/{model_id}", route.getPath());
    }

    @Test
    public void test_PrepareRequest() throws Exception {
        RestRequest request = getRestRequest();
        restMLDeleteModelAction.handleRequest(request, channel, client);

        ArgumentCaptor<MLModelDeleteRequest> argumentCaptor = ArgumentCaptor.forClass(MLModelDeleteRequest.class);
        verify(client, times(1)).execute(eq(MLModelDeleteAction.INSTANCE), argumentCaptor.capture(), any());
        String taskId = argumentCaptor.getValue().getModelId();
        assertEquals(taskId, "test_id");
    }

    @Test
    public void testPrepareRequest_WithTenantIdInHeader() throws Exception {
        // Mock multi-tenancy enabled
        when(mlFeatureEnabledSetting.isMultiTenancyEnabled()).thenReturn(true);

        // Create a RestRequest with tenantId in the header
        RestRequest request = getRestRequestWithTenantId("test_tenant");
        restMLDeleteModelAction.handleRequest(request, channel, client);

        // Capture the request sent to the client
        ArgumentCaptor<MLModelDeleteRequest> argumentCaptor = ArgumentCaptor.forClass(MLModelDeleteRequest.class);
        verify(client, times(1)).execute(eq(MLModelDeleteAction.INSTANCE), argumentCaptor.capture(), any());

        // Verify modelId and tenantId
        MLModelDeleteRequest capturedRequest = argumentCaptor.getValue();
        assertEquals("test_id", capturedRequest.getModelId());
        assertEquals("test_tenant", capturedRequest.getTenantId());
    }

    public void testPrepareRequest_WithoutTenantId() throws Exception {
        // Mock multi-tenancy disabled
        when(mlFeatureEnabledSetting.isMultiTenancyEnabled()).thenReturn(false);

        // Create a RestRequest without tenantId
        RestRequest request = getRestRequest();
        restMLDeleteModelAction.handleRequest(request, channel, client);

        // Capture the request sent to the client
        ArgumentCaptor<MLModelDeleteRequest> argumentCaptor = ArgumentCaptor.forClass(MLModelDeleteRequest.class);
        verify(client, times(1)).execute(eq(MLModelDeleteAction.INSTANCE), argumentCaptor.capture(), any());

        // Verify modelId and ensure tenantId is null
        MLModelDeleteRequest capturedRequest = argumentCaptor.getValue();
        assertEquals("test_id", capturedRequest.getModelId());
        assertNull(capturedRequest.getTenantId());
    }

    private RestRequest getRestRequestWithTenantId(String tenantId) {
        Map<String, String> params = new HashMap<>();
        params.put(PARAMETER_MODEL_ID, "test_id"); // Model ID as a parameter

        Map<String, List<String>> headers = new HashMap<>();
        headers.put(TENANT_ID_HEADER, List.of(tenantId)); // Tenant ID as a header

        return new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY)
            .withParams(params)
            .withHeaders(headers) // Set headers
            .build();
    }

    private RestRequest getRestRequest() {
        Map<String, String> params = new HashMap<>();
        params.put(PARAMETER_MODEL_ID, "test_id");
        return new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY).withParams(params).build();
    }
}
