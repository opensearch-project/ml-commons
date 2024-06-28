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
import static org.opensearch.ml.settings.MLCommonsSettings.ML_COMMONS_MULTI_TENANCY_ENABLED;
import static org.opensearch.ml.utils.RestActionUtils.PARAMETER_CONNECTOR_ID;

import java.io.IOException;
import java.util.Collections;
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
import org.opensearch.ml.common.input.Constants;
import org.opensearch.ml.common.transport.connector.MLConnectorGetAction;
import org.opensearch.ml.common.transport.connector.MLConnectorGetRequest;
import org.opensearch.ml.common.transport.connector.MLConnectorGetResponse;
import org.opensearch.ml.settings.MLFeatureEnabledSetting;
import org.opensearch.rest.RestChannel;
import org.opensearch.rest.RestHandler;
import org.opensearch.rest.RestRequest;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.test.rest.FakeRestRequest;
import org.opensearch.threadpool.TestThreadPool;
import org.opensearch.threadpool.ThreadPool;

public class RestMLGetConnectorActionTests extends OpenSearchTestCase {
    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Mock
    private ClusterService clusterService;

    private RestMLGetConnectorAction restMLGetConnectorAction;

    @Mock
    private MLFeatureEnabledSetting mlFeatureEnabledSetting;

    NodeClient client;
    private ThreadPool threadPool;

    Settings settings;

    @Mock
    RestChannel channel;

    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);
        settings = Settings.builder().put(ML_COMMONS_MULTI_TENANCY_ENABLED.getKey(), false).build();
        when(clusterService.getSettings()).thenReturn(settings);
        when(clusterService.getClusterSettings()).thenReturn(new ClusterSettings(settings, Set.of(ML_COMMONS_MULTI_TENANCY_ENABLED)));
        when(mlFeatureEnabledSetting.isMultiTenancyEnabled()).thenReturn(false);
        restMLGetConnectorAction = new RestMLGetConnectorAction(clusterService, settings, mlFeatureEnabledSetting);

        threadPool = new TestThreadPool(this.getClass().getSimpleName() + "ThreadPool");
        client = spy(new NodeClient(Settings.EMPTY, threadPool));

        doAnswer(invocation -> {
            ActionListener<MLConnectorGetResponse> actionListener = invocation.getArgument(2);
            return null;
        }).when(client).execute(eq(MLConnectorGetAction.INSTANCE), any(), any());

    }

    @Override
    public void tearDown() throws Exception {
        super.tearDown();
        threadPool.shutdown();
        client.close();
    }

    public void testConstructor() {
        RestMLGetConnectorAction mlGetConnectorAction = new RestMLGetConnectorAction(clusterService, settings, mlFeatureEnabledSetting);
        assertNotNull(mlGetConnectorAction);
    }

    public void testGetName() {
        String actionName = restMLGetConnectorAction.getName();
        assertFalse(Strings.isNullOrEmpty(actionName));
        assertEquals("ml_get_connector_action", actionName);
    }

    public void testRoutes() {
        List<RestHandler.Route> routes = restMLGetConnectorAction.routes();
        assertNotNull(routes);
        assertFalse(routes.isEmpty());
        RestHandler.Route route = routes.get(0);
        assertEquals(RestRequest.Method.GET, route.getMethod());
        assertEquals("/_plugins/_ml/connectors/{connector_id}", route.getPath());
    }

    public void test_PrepareRequest() throws Exception {
        RestRequest request = getRestRequest("connector_id", null, true);
        restMLGetConnectorAction.handleRequest(request, channel, client);

        ArgumentCaptor<MLConnectorGetRequest> argumentCaptor = ArgumentCaptor.forClass(MLConnectorGetRequest.class);
        verify(client, times(1)).execute(eq(MLConnectorGetAction.INSTANCE), argumentCaptor.capture(), any());
        String taskId = argumentCaptor.getValue().getConnectorId();
        assertEquals(taskId, "connector_id");
    }

    public void testGetRequest_MultiTenancyEnabled() throws IOException {
        when(mlFeatureEnabledSetting.isMultiTenancyEnabled()).thenReturn(true);
        RestRequest request = getRestRequest("connector_id", "tenant_id", true);
        MLConnectorGetRequest mlConnectorGetRequest = restMLGetConnectorAction.getRequest(request);

        assertEquals("connector_id", mlConnectorGetRequest.getConnectorId());
        assertEquals("tenant_id", mlConnectorGetRequest.getTenantId());
    }

    public void testGetRequest_MissingConnectorId() throws IOException {
        thrown.expect(IllegalArgumentException.class);
        thrown.expectMessage("Request should contain connector_id");

        RestRequest request = new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY).build();
        restMLGetConnectorAction.getRequest(request);
    }

    public void testGetRequest_ReturnContent() throws IOException {
        RestRequest request = getRestRequest("connector_id", null, true);
        MLConnectorGetRequest mlConnectorGetRequest = restMLGetConnectorAction.getRequest(request);

        assertEquals("connector_id", mlConnectorGetRequest.getConnectorId());
        assertTrue(mlConnectorGetRequest.isReturnContent());
    }

    private RestRequest getRestRequest(String connectorId, String tenantId, boolean returnContent) {
        Map<String, String> params = new HashMap<>();
        params.put(PARAMETER_CONNECTOR_ID, connectorId);
        if (returnContent) {
            params.put("return_content", "true");
        }

        Map<String, List<String>> headers = new HashMap<>();
        if (tenantId != null) {
            headers.put(Constants.TENANT_ID_HEADER, Collections.singletonList(tenantId));
        }

        return new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY).withParams(params).withHeaders(headers).build();
    }

}
