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
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.PARAMETER_MEMORY_CONTAINER_ID;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.PARAMETER_MEMORY_ID;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.UPDATE_MEMORY_PATH;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.ExpectedException;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.action.update.UpdateResponse;
import org.opensearch.common.settings.Settings;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.common.bytes.BytesArray;
import org.opensearch.core.xcontent.MediaType;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.ml.common.transport.memorycontainer.memory.MLUpdateMemoryAction;
import org.opensearch.ml.common.transport.memorycontainer.memory.MLUpdateMemoryRequest;
import org.opensearch.rest.RestChannel;
import org.opensearch.rest.RestHandler;
import org.opensearch.rest.RestRequest;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.test.rest.FakeRestRequest;
import org.opensearch.threadpool.TestThreadPool;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.client.node.NodeClient;

public class RestMLUpdateMemoryActionTests extends OpenSearchTestCase {
    @Rule
    public ExpectedException thrown = ExpectedException.none();

    private RestMLUpdateMemoryAction restMLUpdateMemoryAction;
    private NodeClient client;
    private ThreadPool threadPool;

    @Mock
    RestChannel channel;

    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);
        restMLUpdateMemoryAction = new RestMLUpdateMemoryAction();

        threadPool = new TestThreadPool(this.getClass().getSimpleName() + "ThreadPool");
        client = spy(new NodeClient(Settings.EMPTY, threadPool));

        doAnswer(invocation -> {
            ActionListener<UpdateResponse> actionListener = invocation.getArgument(2);
            return null;
        }).when(client).execute(eq(MLUpdateMemoryAction.INSTANCE), any(), any());
    }

    @Override
    public void tearDown() throws Exception {
        super.tearDown();
        threadPool.shutdown();
        client.close();
    }

    public void testGetName() {
        assertEquals("ml_update_memory_action", restMLUpdateMemoryAction.getName());
    }

    public void testRoutes() {
        List<RestHandler.Route> routes = restMLUpdateMemoryAction.routes();
        assertNotNull(routes);
        assertEquals(1, routes.size());
        assertEquals(RestRequest.Method.PUT, routes.get(0).getMethod());
        assertEquals(UPDATE_MEMORY_PATH, routes.get(0).getPath());
    }

    public void testPrepareRequest() throws Exception {
        RestRequest request = getRestRequest();
        restMLUpdateMemoryAction.handleRequest(request, channel, client);

        ArgumentCaptor<MLUpdateMemoryRequest> argumentCaptor = ArgumentCaptor.forClass(MLUpdateMemoryRequest.class);
        verify(client, times(1)).execute(eq(MLUpdateMemoryAction.INSTANCE), argumentCaptor.capture(), any());

        MLUpdateMemoryRequest capturedRequest = argumentCaptor.getValue();
        assertNotNull(capturedRequest);
        assertEquals("test-container-id", capturedRequest.getMemoryContainerId());
        assertEquals("test-memory-id", capturedRequest.getMemoryId());
    }

    public void testPrepareRequestWithoutContent() throws Exception {
        Map<String, String> params = new HashMap<>();
        params.put(PARAMETER_MEMORY_CONTAINER_ID, "test-container-id");
        params.put(PARAMETER_MEMORY_ID, "test-memory-id");

        RestRequest request = new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY)
            .withMethod(RestRequest.Method.PUT)
            .withPath(UPDATE_MEMORY_PATH)
            .withParams(params)
            .build();

        Exception exception = expectThrows(Exception.class, () -> { restMLUpdateMemoryAction.handleRequest(request, channel, client); });

        assertTrue(exception.getMessage().contains("empty body"));
    }

    public void testPrepareRequestWithMissingContainerId() throws Exception {
        String requestContent = "{\"text\":\"new text\"}";
        Map<String, String> params = new HashMap<>();
        params.put(PARAMETER_MEMORY_ID, "test-memory-id");

        RestRequest request = new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY)
            .withMethod(RestRequest.Method.PUT)
            .withPath(UPDATE_MEMORY_PATH)
            .withParams(params)
            .withContent(new BytesArray(requestContent), MediaType.fromMediaType("application/json"))
            .build();

        Exception exception = expectThrows(IllegalArgumentException.class, () -> {
            restMLUpdateMemoryAction.handleRequest(request, channel, client);
        });

        assertNotNull(exception);
    }

    public void testPrepareRequestWithMissingMemoryId() throws Exception {
        String requestContent = "{\"text\":\"new text\"}";
        Map<String, String> params = new HashMap<>();
        params.put(PARAMETER_MEMORY_CONTAINER_ID, "test-container-id");

        RestRequest request = new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY)
            .withMethod(RestRequest.Method.PUT)
            .withPath(UPDATE_MEMORY_PATH)
            .withParams(params)
            .withContent(new BytesArray(requestContent), MediaType.fromMediaType("application/json"))
            .build();

        Exception exception = expectThrows(IllegalArgumentException.class, () -> {
            restMLUpdateMemoryAction.handleRequest(request, channel, client);
        });

        assertNotNull(exception);
    }

    public void testPrepareRequestWithEmptyText() throws Exception {
        String requestContent = "{\"text\":\"\"}";
        Map<String, String> params = new HashMap<>();
        params.put(PARAMETER_MEMORY_CONTAINER_ID, "test-container-id");
        params.put(PARAMETER_MEMORY_ID, "test-memory-id");

        RestRequest request = new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY)
            .withMethod(RestRequest.Method.PUT)
            .withPath(UPDATE_MEMORY_PATH)
            .withParams(params)
            .withContent(new BytesArray(requestContent), MediaType.fromMediaType("application/json"))
            .build();

        // Empty text is not allowed
        Exception exception = expectThrows(IllegalArgumentException.class, () -> {
            restMLUpdateMemoryAction.handleRequest(request, channel, client);
        });

        assertNotNull(exception);
        assertTrue(exception.getMessage().contains("empty") || exception.getMessage().contains("null"));
    }

    public void testPrepareRequestWithLongText() throws Exception {
        // Test with a very long text content
        StringBuilder longText = new StringBuilder();
        for (int i = 0; i < 100; i++) {
            longText.append("This is line ").append(i).append(" of a long text. ");
        }
        String requestContent = "{\"text\":\"" + longText.toString().replace("\"", "\\\"") + "\"}";
        Map<String, String> params = new HashMap<>();
        params.put(PARAMETER_MEMORY_CONTAINER_ID, "test-container-id");
        params.put(PARAMETER_MEMORY_ID, "test-memory-id");

        RestRequest request = new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY)
            .withMethod(RestRequest.Method.PUT)
            .withPath(UPDATE_MEMORY_PATH)
            .withParams(params)
            .withContent(new BytesArray(requestContent), MediaType.fromMediaType("application/json"))
            .build();

        restMLUpdateMemoryAction.handleRequest(request, channel, client);

        ArgumentCaptor<MLUpdateMemoryRequest> argumentCaptor = ArgumentCaptor.forClass(MLUpdateMemoryRequest.class);
        verify(client, times(1)).execute(eq(MLUpdateMemoryAction.INSTANCE), argumentCaptor.capture(), any());

        MLUpdateMemoryRequest capturedRequest = argumentCaptor.getValue();
        assertNotNull(capturedRequest);
    }

    private RestRequest getRestRequest() {
        String requestContent = "{\"text\":\"updated memory content\"}";
        Map<String, String> params = new HashMap<>();
        params.put(PARAMETER_MEMORY_CONTAINER_ID, "test-container-id");
        params.put(PARAMETER_MEMORY_ID, "test-memory-id");

        return new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY)
            .withMethod(RestRequest.Method.PUT)
            .withPath(UPDATE_MEMORY_PATH)
            .withParams(params)
            .withContent(new BytesArray(requestContent), MediaType.fromMediaType("application/json"))
            .build();
    }
}
