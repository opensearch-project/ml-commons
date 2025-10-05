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
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.DELETE_MEMORIES_BY_QUERY_PATH;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.PARAMETER_MEMORY_CONTAINER_ID;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.PARAMETER_MEMORY_TYPE;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.common.bytes.BytesArray;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.index.reindex.BulkByScrollResponse;
import org.opensearch.ml.common.memorycontainer.MemoryType;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.transport.memorycontainer.memory.MLDeleteMemoriesByQueryAction;
import org.opensearch.ml.common.transport.memorycontainer.memory.MLDeleteMemoriesByQueryRequest;
import org.opensearch.ml.common.transport.memorycontainer.memory.MLDeleteMemoriesByQueryResponse;
import org.opensearch.rest.RestChannel;
import org.opensearch.rest.RestHandler;
import org.opensearch.rest.RestRequest;
import org.opensearch.search.SearchModule;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.test.rest.FakeRestRequest;
import org.opensearch.threadpool.TestThreadPool;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.client.node.NodeClient;

/**
 * Unit tests for RestMLDeleteMemoriesByQueryAction
 */
public class RestMLDeleteMemoriesByQueryActionTests extends OpenSearchTestCase {
    @Rule
    public ExpectedException thrown = ExpectedException.none();

    private RestMLDeleteMemoriesByQueryAction restAction;
    private NodeClient client;
    private ThreadPool threadPool;
    private NamedXContentRegistry xContentRegistry;

    @Mock
    private MLFeatureEnabledSetting mlFeatureEnabledSetting;

    @Mock
    private RestChannel channel;

    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);
        when(mlFeatureEnabledSetting.isAgenticMemoryEnabled()).thenReturn(true);
        restAction = new RestMLDeleteMemoriesByQueryAction(mlFeatureEnabledSetting);

        threadPool = new TestThreadPool(this.getClass().getSimpleName() + "ThreadPool");
        client = spy(new NodeClient(Settings.EMPTY, threadPool));

        // Create XContent registry with query builders support
        SearchModule searchModule = new SearchModule(Settings.EMPTY, Collections.emptyList());
        xContentRegistry = new NamedXContentRegistry(searchModule.getNamedXContents());

        doAnswer(invocation -> {
            ActionListener<MLDeleteMemoriesByQueryResponse> actionListener = invocation.getArgument(2);
            // Create a mock BulkByScrollResponse
            BulkByScrollResponse bulkResponse = new BulkByScrollResponse(Collections.emptyList(), null);
            MLDeleteMemoriesByQueryResponse response = new MLDeleteMemoriesByQueryResponse(bulkResponse);
            actionListener.onResponse(response);
            return null;
        }).when(client).execute(eq(MLDeleteMemoriesByQueryAction.INSTANCE), any(), any());
    }

    @Override
    public void tearDown() throws Exception {
        super.tearDown();
        threadPool.shutdown();
        client.close();
    }

    @Test
    public void testGetName() {
        assertEquals("ml_delete_memories_by_query_action", restAction.getName());
    }

    @Test
    public void testRoutes() {
        List<RestHandler.Route> routes = restAction.routes();
        assertNotNull(routes);
        assertEquals(1, routes.size());
        assertEquals(RestRequest.Method.POST, routes.get(0).getMethod());
        assertEquals(DELETE_MEMORIES_BY_QUERY_PATH, routes.get(0).getPath());
    }

    @Test
    public void testPrepareRequest_WithQuery() throws Exception {
        // Create request with query
        String queryJson = "{\"query\":{\"match_all\":{}}}";
        RestRequest request = createRestRequest(queryJson);

        // Execute the request
        restAction.handleRequest(request, channel, client);

        // Capture the request sent to client
        ArgumentCaptor<MLDeleteMemoriesByQueryRequest> argumentCaptor = ArgumentCaptor.forClass(MLDeleteMemoriesByQueryRequest.class);
        verify(client, times(1)).execute(eq(MLDeleteMemoriesByQueryAction.INSTANCE), argumentCaptor.capture(), any());

        // Verify the captured request
        MLDeleteMemoriesByQueryRequest capturedRequest = argumentCaptor.getValue();
        assertNotNull(capturedRequest);
        assertEquals("test-container-id", capturedRequest.getMemoryContainerId());
        assertEquals(MemoryType.SESSIONS, capturedRequest.getMemoryType());
        assertNotNull(capturedRequest.getQuery());
    }

    @Test
    public void testPrepareRequest_WithoutQuery() throws Exception {
        // Create request without query (empty body)
        RestRequest request = createRestRequest(null);

        // Execute the request
        restAction.handleRequest(request, channel, client);

        // Capture the request sent to client
        ArgumentCaptor<MLDeleteMemoriesByQueryRequest> argumentCaptor = ArgumentCaptor.forClass(MLDeleteMemoriesByQueryRequest.class);
        verify(client, times(1)).execute(eq(MLDeleteMemoriesByQueryAction.INSTANCE), argumentCaptor.capture(), any());

        // Verify the captured request
        MLDeleteMemoriesByQueryRequest capturedRequest = argumentCaptor.getValue();
        assertNotNull(capturedRequest);
        assertEquals("test-container-id", capturedRequest.getMemoryContainerId());
        assertEquals(MemoryType.SESSIONS, capturedRequest.getMemoryType());
        // Query should be null, transport layer will handle default
        assertNull(capturedRequest.getQuery());
    }

    @Test
    public void testPrepareRequest_WithComplexQuery() throws Exception {
        // Create request with complex query
        String queryJson = "{\"query\":{\"bool\":{\"must\":[{\"term\":{\"field\":\"value\"}}]}}}";
        RestRequest request = createRestRequest(queryJson);

        // Execute the request
        restAction.handleRequest(request, channel, client);

        // Capture the request sent to client
        ArgumentCaptor<MLDeleteMemoriesByQueryRequest> argumentCaptor = ArgumentCaptor.forClass(MLDeleteMemoriesByQueryRequest.class);
        verify(client, times(1)).execute(eq(MLDeleteMemoriesByQueryAction.INSTANCE), argumentCaptor.capture(), any());

        // Verify the captured request
        MLDeleteMemoriesByQueryRequest capturedRequest = argumentCaptor.getValue();
        assertNotNull(capturedRequest);
        assertEquals("test-container-id", capturedRequest.getMemoryContainerId());
        assertEquals(MemoryType.SESSIONS, capturedRequest.getMemoryType());
        assertNotNull(capturedRequest.getQuery());
        assertTrue(capturedRequest.getQuery().toString().contains("bool"));
    }

    @Test
    public void testPrepareRequest_DifferentMemoryTypes() throws Exception {
        String[] memoryTypes = MemoryType.getAllValues().toArray(new String[0]);
        List<MLDeleteMemoriesByQueryRequest> capturedRequests = new ArrayList<>();

        // Set up client mock to capture all requests
        doAnswer(invocation -> {
            MLDeleteMemoriesByQueryRequest request = invocation.getArgument(1);
            capturedRequests.add(request);
            ActionListener<MLDeleteMemoriesByQueryResponse> actionListener = invocation.getArgument(2);
            BulkByScrollResponse bulkResponse = new BulkByScrollResponse(Collections.emptyList(), null);
            actionListener.onResponse(new MLDeleteMemoriesByQueryResponse(bulkResponse));
            return null;
        }).when(client).execute(eq(MLDeleteMemoriesByQueryAction.INSTANCE), any(), any());

        // Execute requests for each memory type
        for (String memoryType : memoryTypes) {
            Map<String, String> params = new HashMap<>();
            params.put(PARAMETER_MEMORY_CONTAINER_ID, "container-" + memoryType);
            params.put(PARAMETER_MEMORY_TYPE, memoryType);

            RestRequest request = new FakeRestRequest.Builder(xContentRegistry)
                .withMethod(RestRequest.Method.POST)
                .withPath(DELETE_MEMORIES_BY_QUERY_PATH)
                .withParams(params)
                .build();

            restAction.handleRequest(request, channel, client);
        }

        // Verify all requests were captured correctly
        assertEquals(memoryTypes.length, capturedRequests.size());
        for (int i = 0; i < memoryTypes.length; i++) {
            MLDeleteMemoriesByQueryRequest capturedRequest = capturedRequests.get(i);
            assertEquals("container-" + memoryTypes[i], capturedRequest.getMemoryContainerId());
            assertEquals(MemoryType.fromString(memoryTypes[i]), capturedRequest.getMemoryType());
        }

        // Verify client was called the right number of times
        verify(client, times(memoryTypes.length)).execute(eq(MLDeleteMemoriesByQueryAction.INSTANCE), any(), any());
    }

    @Test
    public void testPrepareRequest_MissingContainerId() {
        Map<String, String> params = new HashMap<>();
        params.put(PARAMETER_MEMORY_TYPE, "sessions");

        RestRequest request = new FakeRestRequest.Builder(xContentRegistry)
            .withMethod(RestRequest.Method.POST)
            .withPath(DELETE_MEMORIES_BY_QUERY_PATH)
            .withParams(params)
            .build();

        Exception exception = expectThrows(IllegalArgumentException.class, () -> { restAction.handleRequest(request, channel, client); });

        assertNotNull(exception);
    }

    @Test
    public void testPrepareRequest_MissingMemoryType() {
        Map<String, String> params = new HashMap<>();
        params.put(PARAMETER_MEMORY_CONTAINER_ID, "test-container");

        RestRequest request = new FakeRestRequest.Builder(xContentRegistry)
            .withMethod(RestRequest.Method.POST)
            .withPath(DELETE_MEMORIES_BY_QUERY_PATH)
            .withParams(params)
            .build();

        Exception exception = expectThrows(IllegalArgumentException.class, () -> { restAction.handleRequest(request, channel, client); });

        assertNotNull(exception);
    }

    private RestRequest createRestRequest(String queryJson) {
        Map<String, String> params = new HashMap<>();
        params.put(PARAMETER_MEMORY_CONTAINER_ID, "test-container-id");
        params.put(PARAMETER_MEMORY_TYPE, "sessions");

        FakeRestRequest.Builder requestBuilder = new FakeRestRequest.Builder(xContentRegistry)
            .withMethod(RestRequest.Method.POST)
            .withPath(DELETE_MEMORIES_BY_QUERY_PATH)
            .withParams(params);

        if (queryJson != null) {
            requestBuilder.withContent(new BytesArray(queryJson), XContentType.JSON);
        }

        return requestBuilder.build();
    }
}
