/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.PARAMETER_MEMORY_CONTAINER_ID;

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
import org.opensearch.common.settings.Settings;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.common.Strings;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.ml.common.input.Constants;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.transport.memorycontainer.MLMemoryContainerDeleteAction;
import org.opensearch.ml.common.transport.memorycontainer.MLMemoryContainerDeleteRequest;
import org.opensearch.rest.RestChannel;
import org.opensearch.rest.RestHandler;
import org.opensearch.rest.RestRequest;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.test.rest.FakeRestRequest;
import org.opensearch.threadpool.TestThreadPool;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.client.node.NodeClient;

public class RestMLDeleteMemoryContainerActionTests extends OpenSearchTestCase {
    @Rule
    public ExpectedException thrown = ExpectedException.none();

    private RestMLDeleteMemoryContainerAction restMLDeleteMemoryContainerAction;

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
        restMLDeleteMemoryContainerAction = new RestMLDeleteMemoryContainerAction(mlFeatureEnabledSetting);

        threadPool = new TestThreadPool(this.getClass().getSimpleName() + "ThreadPool");
        client = spy(new NodeClient(Settings.EMPTY, threadPool));

        doAnswer(invocation -> {
            ActionListener<DeleteResponse> actionListener = invocation.getArgument(2);
            return null;
        }).when(client).execute(eq(MLMemoryContainerDeleteAction.INSTANCE), any(), any());

    }

    @Override
    public void tearDown() throws Exception {
        super.tearDown();
        threadPool.shutdown();
        client.close();
    }

    public void testConstructor() {
        RestMLDeleteMemoryContainerAction mlDeleteMemoryContainerAction = new RestMLDeleteMemoryContainerAction(mlFeatureEnabledSetting);
        assertNotNull(mlDeleteMemoryContainerAction);
    }

    public void testGetName() {
        String actionName = restMLDeleteMemoryContainerAction.getName();
        assertFalse(Strings.isNullOrEmpty(actionName));
        assertEquals("ml_delete_memory_container_action", actionName);
    }

    public void testRoutes() {
        List<RestHandler.Route> routes = restMLDeleteMemoryContainerAction.routes();
        assertNotNull(routes);
        assertFalse(routes.isEmpty());
        RestHandler.Route route = routes.get(0);
        assertEquals(RestRequest.Method.DELETE, route.getMethod());
        assertEquals("/_plugins/_ml/memory_container/{memory_container_id}", route.getPath());
    }

    public void test_PrepareRequest() throws Exception {
        RestRequest request = getRestRequest("memory_container_id", null);
        restMLDeleteMemoryContainerAction.handleRequest(request, channel, client);

        ArgumentCaptor<MLMemoryContainerDeleteRequest> argumentCaptor = ArgumentCaptor.forClass(MLMemoryContainerDeleteRequest.class);
        verify(client, times(1)).execute(eq(MLMemoryContainerDeleteAction.INSTANCE), argumentCaptor.capture(), any());
        String memoryContainerId = argumentCaptor.getValue().getMemoryContainerId();
        assertEquals(memoryContainerId, "memory_container_id");
    }

    public void testPrepareRequest_MultiTenancyEnabled() throws Exception {
        when(mlFeatureEnabledSetting.isMultiTenancyEnabled()).thenReturn(true);
        RestRequest request = getRestRequest("memory_container_id", "_tenant_id");
        restMLDeleteMemoryContainerAction.handleRequest(request, channel, client);

        ArgumentCaptor<MLMemoryContainerDeleteRequest> argumentCaptor = ArgumentCaptor.forClass(MLMemoryContainerDeleteRequest.class);
        verify(client, times(1)).execute(eq(MLMemoryContainerDeleteAction.INSTANCE), argumentCaptor.capture(), any());
        MLMemoryContainerDeleteRequest mlMemoryContainerDeleteRequest = argumentCaptor.getValue();
        assertEquals("memory_container_id", mlMemoryContainerDeleteRequest.getMemoryContainerId());
        assertEquals("_tenant_id", mlMemoryContainerDeleteRequest.getTenantId());
    }

    private RestRequest getRestRequest(String memoryContainerId, String tenantId) {
        Map<String, String> params = new HashMap<>();
        params.put(PARAMETER_MEMORY_CONTAINER_ID, memoryContainerId);

        Map<String, List<String>> headers = new HashMap<>();
        if (tenantId != null) {
            headers.put(Constants.TENANT_ID_HEADER, Collections.singletonList(tenantId));
        }

        return new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY).withParams(params).withHeaders(headers).build();
    }
}
