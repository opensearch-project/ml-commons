/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.HYBRID_SEARCH_MEMORIES_PATH;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.PARAMETER_MEMORY_CONTAINER_ID;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.mockito.ArgumentCaptor;
import org.opensearch.OpenSearchStatusException;
import org.opensearch.core.common.bytes.BytesArray;
import org.opensearch.core.xcontent.MediaType;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.transport.memorycontainer.memory.MLHybridSearchMemoriesAction;
import org.opensearch.ml.common.transport.memorycontainer.memory.MLHybridSearchMemoriesRequest;
import org.opensearch.rest.RestChannel;
import org.opensearch.rest.RestHandler;
import org.opensearch.rest.RestRequest;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.test.rest.FakeRestRequest;
import org.opensearch.transport.client.node.NodeClient;

public class RestMLHybridSearchMemoriesActionTests extends OpenSearchTestCase {

    private RestMLHybridSearchMemoriesAction action;
    private NodeClient client;
    private RestChannel channel;
    private MLFeatureEnabledSetting mlFeatureEnabledSetting;

    @Before
    public void setup() {
        mlFeatureEnabledSetting = mock(MLFeatureEnabledSetting.class);
        when(mlFeatureEnabledSetting.isMultiTenancyEnabled()).thenReturn(false);
        when(mlFeatureEnabledSetting.isAgenticMemoryEnabled()).thenReturn(true);
        action = new RestMLHybridSearchMemoriesAction(mlFeatureEnabledSetting);
        client = mock(NodeClient.class);
        channel = mock(RestChannel.class);
    }

    public void testGetName() {
        assertEquals("ml_hybrid_search_memories_action", action.getName());
    }

    public void testRoutes() {
        List<RestHandler.Route> routes = action.routes();
        assertNotNull(routes);
        assertEquals(2, routes.size());
        assertTrue(routes.stream().anyMatch(r -> r.getMethod() == RestRequest.Method.POST));
        assertTrue(routes.stream().anyMatch(r -> r.getMethod() == RestRequest.Method.GET));
    }

    public void testPrepareRequest() throws Exception {
        RestRequest request = buildRequest("{\"query\": \"test search\", \"k\": 5}");
        action.handleRequest(request, channel, client);

        ArgumentCaptor<MLHybridSearchMemoriesRequest> captor = ArgumentCaptor.forClass(MLHybridSearchMemoriesRequest.class);
        verify(client, times(1)).execute(eq(MLHybridSearchMemoriesAction.INSTANCE), captor.capture(), any());

        var captured = captor.getValue();
        assertNotNull(captured);
        assertEquals("test search", captured.getMlHybridSearchMemoriesInput().getQuery());
        assertEquals(5, captured.getMlHybridSearchMemoriesInput().getK());
        assertEquals("test-container-id", captured.getMlHybridSearchMemoriesInput().getMemoryContainerId());
    }

    public void testPrepareRequestWithAllFields() throws Exception {
        String body = "{\"query\": \"test\", \"k\": 3, \"namespace\": {\"user_id\": \"bob\"}, \"tags\": {\"topic\": \"devops\"}}";
        RestRequest request = buildRequest(body);
        action.handleRequest(request, channel, client);

        ArgumentCaptor<MLHybridSearchMemoriesRequest> captor = ArgumentCaptor.forClass(MLHybridSearchMemoriesRequest.class);
        verify(client, times(1)).execute(eq(MLHybridSearchMemoriesAction.INSTANCE), captor.capture(), any());

        var input = captor.getValue().getMlHybridSearchMemoriesInput();
        assertEquals("test", input.getQuery());
        assertEquals(3, input.getK());
        assertEquals("bob", input.getNamespace().get("user_id"));
        assertEquals("devops", input.getTags().get("topic"));
    }

    public void testPrepareRequestDefaultK() throws Exception {
        RestRequest request = buildRequest("{\"query\": \"test\"}");
        action.handleRequest(request, channel, client);

        ArgumentCaptor<MLHybridSearchMemoriesRequest> captor = ArgumentCaptor.forClass(MLHybridSearchMemoriesRequest.class);
        verify(client, times(1)).execute(eq(MLHybridSearchMemoriesAction.INSTANCE), captor.capture(), any());
        assertEquals(10, captor.getValue().getMlHybridSearchMemoriesInput().getK());
    }

    public void testPrepareRequestFeatureDisabled() {
        when(mlFeatureEnabledSetting.isAgenticMemoryEnabled()).thenReturn(false);
        RestRequest request = buildRequest("{\"query\": \"test\"}");
        expectThrows(OpenSearchStatusException.class, () -> action.handleRequest(request, channel, client));
    }

    public void testPrepareRequestNullQuery() {
        RestRequest request = buildRequest("{\"k\": 5}");
        expectThrows(IllegalArgumentException.class, () -> action.handleRequest(request, channel, client));
    }

    public void testPrepareRequestEmptyBody() {
        Map<String, String> params = new HashMap<>();
        params.put(PARAMETER_MEMORY_CONTAINER_ID, "test-container-id");
        RestRequest request = new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY)
            .withMethod(RestRequest.Method.POST)
            .withPath(HYBRID_SEARCH_MEMORIES_PATH)
            .withParams(params)
            .build();
        expectThrows(IllegalArgumentException.class, () -> action.handleRequest(request, channel, client));
    }

    public void testPrepareRequestWithMinScore() throws Exception {
        RestRequest request = buildRequest("{\"query\": \"test\", \"min_score\": 0.5}");
        action.handleRequest(request, channel, client);

        ArgumentCaptor<MLHybridSearchMemoriesRequest> captor = ArgumentCaptor.forClass(MLHybridSearchMemoriesRequest.class);
        verify(client, times(1)).execute(eq(MLHybridSearchMemoriesAction.INSTANCE), captor.capture(), any());
        assertEquals(0.5f, captor.getValue().getMlHybridSearchMemoriesInput().getMinScore(), 0.001);
    }

    public void testPrepareRequestWithFilter() throws Exception {
        // Filter parsing requires SearchModule-registered NamedXContentRegistry
        // Covered via transport action tests; here we just verify the field is parsed
        Map<String, String> params = new HashMap<>();
        params.put(PARAMETER_MEMORY_CONTAINER_ID, "test-container-id");
        RestRequest request = new FakeRestRequest.Builder(
            new org.opensearch.core.xcontent.NamedXContentRegistry(
                new org.opensearch.search.SearchModule(org.opensearch.common.settings.Settings.EMPTY, java.util.Collections.emptyList())
                    .getNamedXContents()
            )
        )
            .withMethod(RestRequest.Method.POST)
            .withPath(HYBRID_SEARCH_MEMORIES_PATH)
            .withParams(params)
            .withContent(
                new BytesArray("{\"query\": \"test\", \"filter\": {\"term\": {\"strategy_type\": \"SEMANTIC\"}}}"),
                MediaType.fromMediaType("application/json")
            )
            .build();
        action.handleRequest(request, channel, client);

        ArgumentCaptor<MLHybridSearchMemoriesRequest> captor = ArgumentCaptor.forClass(MLHybridSearchMemoriesRequest.class);
        verify(client, times(1)).execute(eq(MLHybridSearchMemoriesAction.INSTANCE), captor.capture(), any());
        assertNotNull(captor.getValue().getMlHybridSearchMemoriesInput().getFilter());
    }

    public void testPrepareRequestWithGetMethod() throws Exception {
        Map<String, String> params = new HashMap<>();
        params.put(PARAMETER_MEMORY_CONTAINER_ID, "test-container-id");
        RestRequest request = new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY)
            .withMethod(RestRequest.Method.GET)
            .withPath(HYBRID_SEARCH_MEMORIES_PATH)
            .withParams(params)
            .withContent(new BytesArray("{\"query\": \"test\"}"), MediaType.fromMediaType("application/json"))
            .build();
        action.handleRequest(request, channel, client);
        verify(client, times(1)).execute(eq(MLHybridSearchMemoriesAction.INSTANCE), any(), any());
    }

    private RestRequest buildRequest(String body) {
        Map<String, String> params = new HashMap<>();
        params.put(PARAMETER_MEMORY_CONTAINER_ID, "test-container-id");

        return new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY)
            .withMethod(RestRequest.Method.POST)
            .withPath(HYBRID_SEARCH_MEMORIES_PATH)
            .withParams(params)
            .withContent(new BytesArray(body), MediaType.fromMediaType("application/json"))
            .build();
    }
}
