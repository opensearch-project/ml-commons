/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.PARAMETER_MEMORY_CONTAINER_ID;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.PARAMETER_MEMORY_ID;
import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_AGENTIC_MEMORY_DISABLED_MESSAGE;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.OpenSearchStatusException;
import org.opensearch.common.settings.Settings;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.common.Strings;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.transport.memorycontainer.memory.MLGetMemoryAction;
import org.opensearch.ml.common.transport.memorycontainer.memory.MLGetMemoryRequest;
import org.opensearch.rest.RestChannel;
import org.opensearch.rest.RestHandler;
import org.opensearch.rest.RestRequest;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.test.rest.FakeRestRequest;
import org.opensearch.threadpool.TestThreadPool;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.client.node.NodeClient;

public class RestMLGetMemoryActionTests extends OpenSearchTestCase {

    private RestMLGetMemoryAction restMLGetMemoryAction;

    NodeClient client;
    private ThreadPool threadPool;

    @Mock
    RestChannel channel;

    @Mock
    private MLFeatureEnabledSetting mlFeatureEnabledSetting;

    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);
        when(mlFeatureEnabledSetting.isAgenticMemoryEnabled()).thenReturn(true); // Enable by default for tests
        restMLGetMemoryAction = new RestMLGetMemoryAction(mlFeatureEnabledSetting);

        threadPool = new TestThreadPool(this.getClass().getSimpleName() + "ThreadPool");
        client = spy(new NodeClient(Settings.EMPTY, threadPool));

        doAnswer(invocation -> {
            ActionListener<Object> actionListener = invocation.getArgument(2);
            return null;
        }).when(client).execute(eq(MLGetMemoryAction.INSTANCE), any(), any());
    }

    @Override
    public void tearDown() throws Exception {
        super.tearDown();
        threadPool.shutdown();
        client.close();
    }

    @Test
    public void testConstructor() {
        RestMLGetMemoryAction mlGetMemoryAction = new RestMLGetMemoryAction(mlFeatureEnabledSetting);
        assertNotNull(mlGetMemoryAction);
    }

    @Test
    public void testGetName() {
        String actionName = restMLGetMemoryAction.getName();
        assertFalse(Strings.isNullOrEmpty(actionName));
        assertEquals("ml_get_memory_action", actionName);
    }

    @Test
    public void testRoutes() {
        List<RestHandler.Route> routes = restMLGetMemoryAction.routes();
        assertNotNull(routes);
        assertFalse(routes.isEmpty());
        RestHandler.Route route = routes.get(0);
        assertEquals(RestRequest.Method.GET, route.getMethod());
        assertEquals("/_plugins/_ml/memory_containers/{memory_container_id}/memories/{memory_id}", route.getPath());
    }

    @Test
    public void testRoutePathConstant() {
        List<RestHandler.Route> routes = restMLGetMemoryAction.routes();
        RestHandler.Route route = routes.get(0);

        // Verify the route path matches the expected pattern
        assertTrue(route.getPath().contains("/_plugins/_ml/memory_containers/"));
        assertTrue(route.getPath().contains("/memories/"));
        assertTrue(route.getPath().contains("{memory_container_id}"));
        assertTrue(route.getPath().contains("{memory_id}"));
        assertEquals(RestRequest.Method.GET, route.getMethod());
    }

    @Test
    public void testPrepareRequest() throws Exception {
        RestRequest request = getRestRequest("memory_container_id", "memory_id");
        restMLGetMemoryAction.handleRequest(request, channel, client);

        ArgumentCaptor<MLGetMemoryRequest> argumentCaptor = ArgumentCaptor.forClass(MLGetMemoryRequest.class);
        verify(client, times(1)).execute(eq(MLGetMemoryAction.INSTANCE), argumentCaptor.capture(), any());
        MLGetMemoryRequest mlGetMemoryRequest = argumentCaptor.getValue();
        assertNotNull(mlGetMemoryRequest);
        assertEquals("memory_container_id", mlGetMemoryRequest.getMemoryContainerId());
        assertEquals("memory_id", mlGetMemoryRequest.getMemoryId());
    }

    @Test
    public void testGetRequestMethod() throws Exception {
        RestRequest request = getRestRequest("container-123", "memory-456");
        MLGetMemoryRequest mlGetMemoryRequest = restMLGetMemoryAction.getRequest(request);

        assertNotNull(mlGetMemoryRequest);
        assertEquals("container-123", mlGetMemoryRequest.getMemoryContainerId());
        assertEquals("memory-456", mlGetMemoryRequest.getMemoryId());
    }

    private RestRequest getRestRequest(String memoryContainerId, String memoryId) {
        Map<String, String> params = new HashMap<>();
        params.put(PARAMETER_MEMORY_CONTAINER_ID, memoryContainerId);
        params.put(PARAMETER_MEMORY_ID, memoryId);

        return new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY)
            .withMethod(RestRequest.Method.GET)
            .withPath("/_plugins/_ml/memory_containers/" + memoryContainerId + "/memory/" + memoryId)
            .withParams(params)
            .build();
    }

    @Test
    public void testPrepareRequestWithFeatureDisabled() throws Exception {
        // Setup feature flag to be disabled
        when(mlFeatureEnabledSetting.isAgenticMemoryEnabled()).thenReturn(false);
        restMLGetMemoryAction = new RestMLGetMemoryAction(mlFeatureEnabledSetting);
        
        RestRequest request = getRestRequest("memory_container_id", "memory_id");
        
        // Expect OpenSearchStatusException to be thrown
        OpenSearchStatusException exception = expectThrows(
            OpenSearchStatusException.class, 
            () -> restMLGetMemoryAction.handleRequest(request, channel, client)
        );
        
        assertEquals(ML_COMMONS_AGENTIC_MEMORY_DISABLED_MESSAGE, exception.getMessage());
        assertEquals(RestStatus.FORBIDDEN, exception.status());
        
        // Verify that client.execute was never called
        verify(client, never()).execute(any(), any(), any());
    }
}
