/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.rest.mcpserver;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_MULTI_TENANCY_ENABLED;
import static org.opensearch.ml.utils.RestActionUtils.PARAMETER_CONNECTOR_ID;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.ClusterSettings;
import org.opensearch.common.settings.Settings;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.ml.common.input.Constants;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.transport.mcpserver.action.MLMcpConnectorListToolsAction;
import org.opensearch.ml.common.transport.mcpserver.requests.list.MLMcpConnectorListToolsRequest;
import org.opensearch.rest.RestChannel;
import org.opensearch.rest.RestHandler;
import org.opensearch.rest.RestRequest;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.test.rest.FakeRestRequest;
import org.opensearch.transport.client.node.NodeClient;

public class RestMLMcpConnectorListToolsActionTests extends OpenSearchTestCase {

    @Mock
    private ClusterService clusterService;
    @Mock
    private MLFeatureEnabledSetting mlFeatureEnabledSetting;
    @Mock
    private RestChannel channel;
    @Mock
    NodeClient client;

    private RestMLMcpConnectorListToolsAction restAction;

    @Before
    public void setUp() throws Exception {
        super.setUp();
        MockitoAnnotations.openMocks(this);
        Settings settings = Settings.builder().put(ML_COMMONS_MULTI_TENANCY_ENABLED.getKey(), false).build();
        when(clusterService.getSettings()).thenReturn(settings);
        when(clusterService.getClusterSettings()).thenReturn(new ClusterSettings(settings, Set.of(ML_COMMONS_MULTI_TENANCY_ENABLED)));
        when(mlFeatureEnabledSetting.isMultiTenancyEnabled()).thenReturn(false);
        restAction = new RestMLMcpConnectorListToolsAction(mlFeatureEnabledSetting);
    }

    @Override
    public void tearDown() throws Exception {
        super.tearDown();
    }

    @Test
    public void testConstructor() {
        assertNotNull(restAction);
    }

    @Test
    public void testGetName() {
        assertEquals("ml_mcp_connector_list_tools_action", restAction.getName());
    }

    @Test
    public void testRoutes() {
        List<RestHandler.Route> routes = restAction.routes();
        assertNotNull(routes);
        assertFalse(routes.isEmpty());
        RestHandler.Route route = routes.get(0);
        assertEquals(RestRequest.Method.GET, route.getMethod());
        assertEquals("/_plugins/_ml/connectors/{connector_id}/tools", route.getPath());
    }

    @Test
    public void testPrepareRequest_BuildsCorrectRequest() throws Exception {
        Map<String, String> params = new HashMap<>();
        params.put(PARAMETER_CONNECTOR_ID, "123456");
        RestRequest request = new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY).withParams(params).build();
        restAction.handleRequest(request, channel, client);

        ArgumentCaptor<MLMcpConnectorListToolsRequest> captor = ArgumentCaptor.forClass(MLMcpConnectorListToolsRequest.class);
        verify(client, times(1)).execute(eq(MLMcpConnectorListToolsAction.INSTANCE), captor.capture(), any());
        MLMcpConnectorListToolsRequest listRequest = captor.getValue();
        assertEquals("123456", listRequest.getConnectorId());
    }

    @Test
    public void testGetRequest_WithConnectorId() throws Exception {
        Map<String, String> params = new HashMap<>();
        params.put(PARAMETER_CONNECTOR_ID, "123456");
        RestRequest request = new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY).withParams(params).build();
        MLMcpConnectorListToolsRequest listRequest = restAction.getRequest(request);
        assertNotNull(listRequest);
        assertEquals("123456", listRequest.getConnectorId());
    }

    @Test
    public void testPrepareRequest_WithMultiTenancyEnabled_AndTenantHeader_BuildsRequestWithTenantId() throws Exception {
        when(mlFeatureEnabledSetting.isMultiTenancyEnabled()).thenReturn(true);
        Map<String, String> params = new HashMap<>();
        params.put(PARAMETER_CONNECTOR_ID, "conn-789");
        Map<String, List<String>> headers = new HashMap<>();
        headers.put(Constants.TENANT_ID_HEADER, Collections.singletonList("tenant-abc"));
        RestRequest request = new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY).withParams(params).withHeaders(headers).build();
        restAction.handleRequest(request, channel, client);

        ArgumentCaptor<MLMcpConnectorListToolsRequest> captor = ArgumentCaptor.forClass(MLMcpConnectorListToolsRequest.class);
        verify(client, times(1)).execute(eq(MLMcpConnectorListToolsAction.INSTANCE), captor.capture(), any());
        MLMcpConnectorListToolsRequest listRequest = captor.getValue();
        assertEquals("conn-789", listRequest.getConnectorId());
        assertEquals("tenant-abc", listRequest.getTenantId());
    }

    @Test
    public void testGetRequest_WithMultiTenancyEnabled_AndTenantHeader_ReturnsTenantId() throws Exception {
        when(mlFeatureEnabledSetting.isMultiTenancyEnabled()).thenReturn(true);
        Map<String, String> params = new HashMap<>();
        params.put(PARAMETER_CONNECTOR_ID, "conn-xyz");
        Map<String, List<String>> headers = new HashMap<>();
        headers.put(Constants.TENANT_ID_HEADER, Collections.singletonList("my-tenant-id"));
        RestRequest request = new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY).withParams(params).withHeaders(headers).build();
        MLMcpConnectorListToolsRequest listRequest = restAction.getRequest(request);
        assertNotNull(listRequest);
        assertEquals("conn-xyz", listRequest.getConnectorId());
        assertEquals("my-tenant-id", listRequest.getTenantId());
    }
}
