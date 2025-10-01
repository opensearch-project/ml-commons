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
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.DELETE_MEMORY_PATH;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.PARAMETER_MEMORY_CONTAINER_ID;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.PARAMETER_MEMORY_ID;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.PARAMETER_MEMORY_TYPE;

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
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.transport.memorycontainer.memory.MLDeleteMemoryAction;
import org.opensearch.ml.common.transport.memorycontainer.memory.MLDeleteMemoryRequest;
import org.opensearch.rest.RestChannel;
import org.opensearch.rest.RestHandler;
import org.opensearch.rest.RestRequest;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.test.rest.FakeRestRequest;
import org.opensearch.threadpool.TestThreadPool;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.client.node.NodeClient;

public class RestMLDeleteMemoryActionTests extends OpenSearchTestCase {
    @Rule
    public ExpectedException thrown = ExpectedException.none();

    private RestMLDeleteMemoryAction restMLDeleteMemoryAction;
    private NodeClient client;
    private ThreadPool threadPool;

    @Mock
    private MLFeatureEnabledSetting mlFeatureEnabledSetting;

    @Mock
    RestChannel channel;

    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);
        when(mlFeatureEnabledSetting.isAgenticMemoryEnabled()).thenReturn(true);
        restMLDeleteMemoryAction = new RestMLDeleteMemoryAction(mlFeatureEnabledSetting);

        threadPool = new TestThreadPool(this.getClass().getSimpleName() + "ThreadPool");
        client = spy(new NodeClient(Settings.EMPTY, threadPool));

        doAnswer(invocation -> {
            ActionListener<DeleteResponse> actionListener = invocation.getArgument(2);
            return null;
        }).when(client).execute(eq(MLDeleteMemoryAction.INSTANCE), any(), any());
    }

    @Override
    public void tearDown() throws Exception {
        super.tearDown();
        threadPool.shutdown();
        client.close();
    }

    public void testGetName() {
        assertEquals("ml_delete_memory_action", restMLDeleteMemoryAction.getName());
    }

    public void testRoutes() {
        List<RestHandler.Route> routes = restMLDeleteMemoryAction.routes();
        assertNotNull(routes);
        assertEquals(1, routes.size());
        assertEquals(RestRequest.Method.DELETE, routes.get(0).getMethod());
        assertEquals(DELETE_MEMORY_PATH, routes.get(0).getPath());
    }

    public void testPrepareRequest() throws Exception {
        RestRequest request = getRestRequest();
        restMLDeleteMemoryAction.handleRequest(request, channel, client);

        ArgumentCaptor<MLDeleteMemoryRequest> argumentCaptor = ArgumentCaptor.forClass(MLDeleteMemoryRequest.class);
        verify(client, times(1)).execute(eq(MLDeleteMemoryAction.INSTANCE), argumentCaptor.capture(), any());

        MLDeleteMemoryRequest capturedRequest = argumentCaptor.getValue();
        assertNotNull(capturedRequest);
        assertEquals("test-container-id", capturedRequest.getMemoryContainerId());
        assertEquals("test-memory-type", capturedRequest.getMemoryType());
        assertEquals("test-memory-id", capturedRequest.getMemoryId());
    }

    public void testPrepareRequestWithMissingContainerId() throws Exception {
        Map<String, String> params = new HashMap<>();
        params.put(PARAMETER_MEMORY_TYPE, "test-memory-type");
        params.put(PARAMETER_MEMORY_ID, "test-memory-id");

        RestRequest request = new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY)
            .withMethod(RestRequest.Method.DELETE)
            .withPath(DELETE_MEMORY_PATH)
            .withParams(params)
            .build();

        Exception exception = expectThrows(IllegalArgumentException.class, () -> {
            restMLDeleteMemoryAction.handleRequest(request, channel, client);
        });

        assertNotNull(exception);
    }

    public void testPrepareRequestWithMissingMemoryType() throws Exception {
        Map<String, String> params = new HashMap<>();
        params.put(PARAMETER_MEMORY_CONTAINER_ID, "test-container-id");
        params.put(PARAMETER_MEMORY_ID, "test-memory-id");

        RestRequest request = new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY)
            .withMethod(RestRequest.Method.DELETE)
            .withPath(DELETE_MEMORY_PATH)
            .withParams(params)
            .build();

        Exception exception = expectThrows(IllegalArgumentException.class, () -> {
            restMLDeleteMemoryAction.handleRequest(request, channel, client);
        });

        assertNotNull(exception);
    }

    public void testPrepareRequestWithMissingMemoryId() throws Exception {
        Map<String, String> params = new HashMap<>();
        params.put(PARAMETER_MEMORY_CONTAINER_ID, "test-container-id");
        params.put(PARAMETER_MEMORY_TYPE, "test-memory-type");

        RestRequest request = new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY)
            .withMethod(RestRequest.Method.DELETE)
            .withPath(DELETE_MEMORY_PATH)
            .withParams(params)
            .build();

        Exception exception = expectThrows(IllegalArgumentException.class, () -> {
            restMLDeleteMemoryAction.handleRequest(request, channel, client);
        });

        assertNotNull(exception);
    }

    public void testPrepareRequestWithEmptyIds() throws Exception {
        Map<String, String> params = new HashMap<>();
        params.put(PARAMETER_MEMORY_CONTAINER_ID, "");
        params.put(PARAMETER_MEMORY_TYPE, "");
        params.put(PARAMETER_MEMORY_ID, "");

        RestRequest request = new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY)
            .withMethod(RestRequest.Method.DELETE)
            .withPath(DELETE_MEMORY_PATH)
            .withParams(params)
            .build();

        Exception exception = expectThrows(IllegalArgumentException.class, () -> {
            restMLDeleteMemoryAction.handleRequest(request, channel, client);
        });

        assertNotNull(exception);
    }

    public void testPrepareRequestWithSpecialCharacters() throws Exception {
        Map<String, String> params = new HashMap<>();
        params.put(PARAMETER_MEMORY_CONTAINER_ID, "container-with-dashes-123");
        params.put(PARAMETER_MEMORY_TYPE, "type_with_underscores");
        params.put(PARAMETER_MEMORY_ID, "memory_with_underscores_456");

        RestRequest request = new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY)
            .withMethod(RestRequest.Method.DELETE)
            .withPath(DELETE_MEMORY_PATH)
            .withParams(params)
            .build();

        restMLDeleteMemoryAction.handleRequest(request, channel, client);

        ArgumentCaptor<MLDeleteMemoryRequest> argumentCaptor = ArgumentCaptor.forClass(MLDeleteMemoryRequest.class);
        verify(client, times(1)).execute(eq(MLDeleteMemoryAction.INSTANCE), argumentCaptor.capture(), any());

        MLDeleteMemoryRequest capturedRequest = argumentCaptor.getValue();
        assertNotNull(capturedRequest);
        assertEquals("container-with-dashes-123", capturedRequest.getMemoryContainerId());
        assertEquals("type_with_underscores", capturedRequest.getMemoryType());
        assertEquals("memory_with_underscores_456", capturedRequest.getMemoryId());
    }

    private RestRequest getRestRequest() {
        Map<String, String> params = new HashMap<>();
        params.put(PARAMETER_MEMORY_CONTAINER_ID, "test-container-id");
        params.put(PARAMETER_MEMORY_TYPE, "test-memory-type");
        params.put(PARAMETER_MEMORY_ID, "test-memory-id");

        return new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY)
            .withMethod(RestRequest.Method.DELETE)
            .withPath(DELETE_MEMORY_PATH)
            .withParams(params)
            .build();
    }
}
