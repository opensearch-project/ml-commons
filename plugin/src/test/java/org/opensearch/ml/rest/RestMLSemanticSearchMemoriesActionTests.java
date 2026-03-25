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
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.PARAMETER_MEMORY_CONTAINER_ID;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.SEMANTIC_SEARCH_MEMORIES_PATH;

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
import org.opensearch.ml.common.transport.memorycontainer.memory.MLSemanticSearchMemoriesAction;
import org.opensearch.ml.common.transport.memorycontainer.memory.MLSemanticSearchMemoriesRequest;
import org.opensearch.rest.RestChannel;
import org.opensearch.rest.RestHandler;
import org.opensearch.rest.RestRequest;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.test.rest.FakeRestRequest;
import org.opensearch.transport.client.node.NodeClient;

public class RestMLSemanticSearchMemoriesActionTests extends OpenSearchTestCase {

    private RestMLSemanticSearchMemoriesAction action;
    private NodeClient client;
    private RestChannel channel;
    private MLFeatureEnabledSetting mlFeatureEnabledSetting;

    @Before
    public void setup() {
        mlFeatureEnabledSetting = mock(MLFeatureEnabledSetting.class);
        when(mlFeatureEnabledSetting.isMultiTenancyEnabled()).thenReturn(false);
        when(mlFeatureEnabledSetting.isAgenticMemoryEnabled()).thenReturn(true);
        action = new RestMLSemanticSearchMemoriesAction(mlFeatureEnabledSetting);
        client = mock(NodeClient.class);
        channel = mock(RestChannel.class);
    }

    public void testGetName() {
        assertEquals("ml_semantic_search_memories_action", action.getName());
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

        ArgumentCaptor<MLSemanticSearchMemoriesRequest> captor = ArgumentCaptor.forClass(MLSemanticSearchMemoriesRequest.class);
        verify(client, times(1)).execute(eq(MLSemanticSearchMemoriesAction.INSTANCE), captor.capture(), any());

        MLSemanticSearchMemoriesRequest captured = captor.getValue();
        assertNotNull(captured);
        assertEquals("test search", captured.getMlSemanticSearchMemoriesInput().getQuery());
        assertEquals(5, captured.getMlSemanticSearchMemoriesInput().getK());
        assertEquals("test-container-id", captured.getMlSemanticSearchMemoriesInput().getMemoryContainerId());
    }

    public void testPrepareRequestWithAllFields() throws Exception {
        String body =
            "{\"query\": \"test\", \"k\": 3, \"namespace\": {\"user_id\": \"alice\"}, \"tags\": {\"topic\": \"food\"}, \"min_score\": 0.7}";
        RestRequest request = buildRequest(body);
        action.handleRequest(request, channel, client);

        ArgumentCaptor<MLSemanticSearchMemoriesRequest> captor = ArgumentCaptor.forClass(MLSemanticSearchMemoriesRequest.class);
        verify(client, times(1)).execute(eq(MLSemanticSearchMemoriesAction.INSTANCE), captor.capture(), any());

        var input = captor.getValue().getMlSemanticSearchMemoriesInput();
        assertEquals("test", input.getQuery());
        assertEquals(3, input.getK());
        assertEquals("alice", input.getNamespace().get("user_id"));
        assertEquals("food", input.getTags().get("topic"));
        assertEquals(0.7f, input.getMinScore(), 0.001);
    }

    public void testPrepareRequestDefaultK() throws Exception {
        RestRequest request = buildRequest("{\"query\": \"test\"}");
        action.handleRequest(request, channel, client);

        ArgumentCaptor<MLSemanticSearchMemoriesRequest> captor = ArgumentCaptor.forClass(MLSemanticSearchMemoriesRequest.class);
        verify(client, times(1)).execute(eq(MLSemanticSearchMemoriesAction.INSTANCE), captor.capture(), any());
        assertEquals(10, captor.getValue().getMlSemanticSearchMemoriesInput().getK());
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

    private RestRequest buildRequest(String body) {
        Map<String, String> params = new HashMap<>();
        params.put(PARAMETER_MEMORY_CONTAINER_ID, "test-container-id");

        return new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY)
            .withMethod(RestRequest.Method.POST)
            .withPath(SEMANTIC_SEARCH_MEMORIES_PATH)
            .withParams(params)
            .withContent(new BytesArray(body), MediaType.fromMediaType("application/json"))
            .build();
    }
}
