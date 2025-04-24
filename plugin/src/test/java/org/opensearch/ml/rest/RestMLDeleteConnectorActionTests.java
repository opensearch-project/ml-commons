/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.opensearch.ml.utils.RestActionUtils.PARAMETER_CONNECTOR_ID;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.ExpectedException;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.action.delete.DeleteResponse;
import org.opensearch.client.node.NodeClient;
import org.opensearch.common.settings.Settings;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.common.Strings;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.ml.common.input.Constants;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.transport.connector.MLConnectorDeleteAction;
import org.opensearch.ml.common.transport.connector.MLConnectorDeleteRequest;
import org.opensearch.rest.RestChannel;
import org.opensearch.rest.RestHandler;
import org.opensearch.rest.RestRequest;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.test.rest.FakeRestRequest;
import org.opensearch.threadpool.TestThreadPool;
import org.opensearch.threadpool.ThreadPool;

public class RestMLDeleteConnectorActionTests extends OpenSearchTestCase {
    @Rule
    public ExpectedException thrown = ExpectedException.none();

    private RestMLDeleteConnectorAction restMLDeleteConnectorAction;

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
        restMLDeleteConnectorAction = new RestMLDeleteConnectorAction(mlFeatureEnabledSetting);

        threadPool = new TestThreadPool(this.getClass().getSimpleName() + "ThreadPool");
        client = spy(new NodeClient(Settings.EMPTY, threadPool));

        doAnswer(invocation -> {
            ActionListener<DeleteResponse> actionListener = invocation.getArgument(2);
            return null;
        }).when(client).execute(eq(MLConnectorDeleteAction.INSTANCE), any(), any());

    }

    @Override
    public void tearDown() throws Exception {
        super.tearDown();
        threadPool.shutdown();
        client.close();
    }

    public void testConstructor() {
        RestMLDeleteConnectorAction mlDeleteConnectorAction = new RestMLDeleteConnectorAction(mlFeatureEnabledSetting);
        assertNotNull(mlDeleteConnectorAction);
    }

    public void testGetName() {
        String actionName = restMLDeleteConnectorAction.getName();
        assertFalse(Strings.isNullOrEmpty(actionName));
        assertEquals("ml_delete_connector_action", actionName);
    }

    public void testRoutes() {
        List<RestHandler.Route> routes = restMLDeleteConnectorAction.routes();
        assertNotNull(routes);
        assertFalse(routes.isEmpty());
        RestHandler.Route route = routes.get(0);
        assertEquals(RestRequest.Method.DELETE, route.getMethod());
        assertEquals("/_plugins/_ml/connectors/{connector_id}", route.getPath());
    }

    public void test_PrepareRequest() throws Exception {
        RestRequest request = getRestRequest("connector_id", null);
        restMLDeleteConnectorAction.handleRequest(request, channel, client);

        ArgumentCaptor<MLConnectorDeleteRequest> argumentCaptor = ArgumentCaptor.forClass(MLConnectorDeleteRequest.class);
        verify(client, times(1)).execute(eq(MLConnectorDeleteAction.INSTANCE), argumentCaptor.capture(), any());
        String connectorId = argumentCaptor.getValue().getConnectorId();
        assertEquals(connectorId, "connector_id");
    }

    public void testPrepareRequest_MultiTenancyEnabled() throws Exception {
        when(mlFeatureEnabledSetting.isMultiTenancyEnabled()).thenReturn(true);
        RestRequest request = getRestRequest("connector_id", "_tenant_id");
        restMLDeleteConnectorAction.handleRequest(request, channel, client);

        ArgumentCaptor<MLConnectorDeleteRequest> argumentCaptor = ArgumentCaptor.forClass(MLConnectorDeleteRequest.class);
        verify(client, times(1)).execute(eq(MLConnectorDeleteAction.INSTANCE), argumentCaptor.capture(), any());
        MLConnectorDeleteRequest mlConnectorDeleteRequest = argumentCaptor.getValue();
        assertEquals("connector_id", mlConnectorDeleteRequest.getConnectorId());
        assertEquals("_tenant_id", mlConnectorDeleteRequest.getTenantId());
    }

    private RestRequest getRestRequest(String connectorId, String tenantId) {
        Map<String, String> params = new HashMap<>();
        params.put(PARAMETER_CONNECTOR_ID, connectorId);

        Map<String, List<String>> headers = new HashMap<>();
        if (tenantId != null) {
            headers.put(Constants.TENANT_ID_HEADER, Collections.singletonList(tenantId));
        }

        return new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY).withParams(params).withHeaders(headers).build();
    }
}
