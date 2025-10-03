/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.PARAMETER_MEMORY_CONTAINER_ID;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.PARAMETER_MEMORY_TYPE;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.SEARCH_MEMORIES_PATH;

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
import org.opensearch.common.settings.Settings;
import org.opensearch.core.common.bytes.BytesArray;
import org.opensearch.core.xcontent.MediaType;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.transport.memorycontainer.memory.MLSearchMemoriesAction;
import org.opensearch.ml.common.transport.memorycontainer.memory.MLSearchMemoriesRequest;
import org.opensearch.rest.RestChannel;
import org.opensearch.rest.RestHandler;
import org.opensearch.rest.RestRequest;
import org.opensearch.search.SearchModule;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.test.rest.FakeRestRequest;
import org.opensearch.threadpool.TestThreadPool;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.client.node.NodeClient;

public class RestMLSearchMemoriesActionTests extends OpenSearchTestCase {
    @Rule
    public ExpectedException thrown = ExpectedException.none();

    private RestMLSearchMemoriesAction restMLSearchMemoriesAction;
    private NodeClient client;
    private ThreadPool threadPool;

    @Mock
    RestChannel channel;

    @Mock
    MLFeatureEnabledSetting mlFeatureEnabledSetting;

    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);
        when(mlFeatureEnabledSetting.isMultiTenancyEnabled()).thenReturn(false);
        when(mlFeatureEnabledSetting.isAgenticMemoryEnabled()).thenReturn(true);
        restMLSearchMemoriesAction = new RestMLSearchMemoriesAction(mlFeatureEnabledSetting);

        threadPool = new TestThreadPool(this.getClass().getSimpleName() + "ThreadPool");
        client = spy(new NodeClient(Settings.EMPTY, threadPool));

    }

    @Override
    public void tearDown() throws Exception {
        super.tearDown();
        threadPool.shutdown();
        client.close();
    }

    public void testGetName() {
        assertEquals("ml_search_memories_action", restMLSearchMemoriesAction.getName());
    }

    public void testRoutes() {
        List<RestHandler.Route> routes = restMLSearchMemoriesAction.routes();
        assertNotNull(routes);
        assertEquals(2, routes.size());

        // Check both POST and GET routes are registered
        boolean hasPost = false;
        boolean hasGet = false;
        for (RestHandler.Route route : routes) {
            if (route.getMethod() == RestRequest.Method.POST) {
                hasPost = true;
                assertEquals(SEARCH_MEMORIES_PATH, route.getPath());
            }
            if (route.getMethod() == RestRequest.Method.GET) {
                hasGet = true;
                assertEquals(SEARCH_MEMORIES_PATH, route.getPath());
            }
        }
        assertTrue(hasPost);
        assertTrue(hasGet);
    }

    public void testPrepareRequestWithPost() throws Exception {
        RestRequest request = getRestRequest(RestRequest.Method.POST);
        restMLSearchMemoriesAction.handleRequest(request, channel, client);

        ArgumentCaptor<MLSearchMemoriesRequest> argumentCaptor = ArgumentCaptor.forClass(MLSearchMemoriesRequest.class);
        verify(client, times(1)).execute(eq(MLSearchMemoriesAction.INSTANCE), argumentCaptor.capture(), any());

        MLSearchMemoriesRequest capturedRequest = argumentCaptor.getValue();
        assertNotNull(capturedRequest);
    }

    public void testPrepareRequestWithGet() throws Exception {
        RestRequest request = getRestRequest(RestRequest.Method.GET);
        restMLSearchMemoriesAction.handleRequest(request, channel, client);

        ArgumentCaptor<MLSearchMemoriesRequest> argumentCaptor = ArgumentCaptor.forClass(MLSearchMemoriesRequest.class);
        verify(client, times(1)).execute(eq(MLSearchMemoriesAction.INSTANCE), argumentCaptor.capture(), any());

        MLSearchMemoriesRequest capturedRequest = argumentCaptor.getValue();
        assertNotNull(capturedRequest);
    }

    public void testPrepareRequestWithoutContent() throws Exception {
        Map<String, String> params = new HashMap<>();
        params.put(PARAMETER_MEMORY_CONTAINER_ID, "test-container-id");

        RestRequest request = new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY)
            .withMethod(RestRequest.Method.POST)
            .withPath(SEARCH_MEMORIES_PATH)
            .withParams(params)
            .build();

        Exception exception = expectThrows(IllegalArgumentException.class, () -> {
            restMLSearchMemoriesAction.handleRequest(request, channel, client);
        });

        assertTrue(exception.getMessage().contains("empty body"));
    }

    public void testPrepareRequestWithMissingContainerId() throws Exception {
        String requestContent = "{\"query\":\"test search query\"}";

        RestRequest request = new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY)
            .withMethod(RestRequest.Method.POST)
            .withPath(SEARCH_MEMORIES_PATH)
            .withContent(new BytesArray(requestContent), MediaType.fromMediaType("application/json"))
            .build();

        Exception exception = expectThrows(IllegalArgumentException.class, () -> {
            restMLSearchMemoriesAction.handleRequest(request, channel, client);
        });

        assertNotNull(exception);
    }

    public void testPrepareRequestWithMultiTenancy() throws Exception {
        // Test that multi-tenancy setting is checked
        when(mlFeatureEnabledSetting.isMultiTenancyEnabled()).thenReturn(false);
        
        RestRequest request = getRestRequest(RestRequest.Method.POST);
        restMLSearchMemoriesAction.handleRequest(request, channel, client);

        ArgumentCaptor<MLSearchMemoriesRequest> argumentCaptor = ArgumentCaptor.forClass(MLSearchMemoriesRequest.class);
        verify(client, times(1)).execute(eq(MLSearchMemoriesAction.INSTANCE), argumentCaptor.capture(), any());
        
        MLSearchMemoriesRequest capturedRequest = argumentCaptor.getValue();
        assertNotNull(capturedRequest);
        
        // Verify that multi-tenancy setting was checked
        verify(mlFeatureEnabledSetting, times(1)).isMultiTenancyEnabled();
    }

    private RestRequest getRestRequest(RestRequest.Method method) {
        String requestContent = "{\"query\": {\"match_all\": {}} }";
        Map<String, String> params = new HashMap<>();
        params.put(PARAMETER_MEMORY_CONTAINER_ID, "test-container-id");
        params.put(PARAMETER_MEMORY_TYPE, "working");

        return new FakeRestRequest.Builder(
            new NamedXContentRegistry(new SearchModule(Settings.EMPTY, Collections.emptyList()).getNamedXContents())
        )
            .withMethod(method)
            .withPath(SEARCH_MEMORIES_PATH)
            .withParams(params)
            .withContent(new BytesArray(requestContent), MediaType.fromMediaType("application/json"))
            .build();
    }
}
