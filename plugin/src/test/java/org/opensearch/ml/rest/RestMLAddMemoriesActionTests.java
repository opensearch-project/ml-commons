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
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.MEMORIES_PATH;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.PARAMETER_MEMORY_CONTAINER_ID;

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
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.common.bytes.BytesArray;
import org.opensearch.core.xcontent.MediaType;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.transport.memorycontainer.memory.MLAddMemoriesAction;
import org.opensearch.ml.common.transport.memorycontainer.memory.MLAddMemoriesRequest;
import org.opensearch.ml.common.transport.memorycontainer.memory.MLAddMemoriesResponse;
import org.opensearch.rest.RestChannel;
import org.opensearch.rest.RestHandler;
import org.opensearch.rest.RestRequest;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.test.rest.FakeRestRequest;
import org.opensearch.threadpool.TestThreadPool;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.client.node.NodeClient;

public class RestMLAddMemoriesActionTests extends OpenSearchTestCase {
    @Rule
    public ExpectedException thrown = ExpectedException.none();

    private RestMLAddMemoriesAction restMLAddMemoriesAction;
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
        restMLAddMemoriesAction = new RestMLAddMemoriesAction(mlFeatureEnabledSetting);

        threadPool = new TestThreadPool(this.getClass().getSimpleName() + "ThreadPool");
        client = spy(new NodeClient(Settings.EMPTY, threadPool));

        doAnswer(invocation -> {
            ActionListener<MLAddMemoriesResponse> actionListener = invocation.getArgument(2);
            return null;
        }).when(client).execute(eq(MLAddMemoriesAction.INSTANCE), any(), any());
    }

    @Override
    public void tearDown() throws Exception {
        super.tearDown();
        threadPool.shutdown();
        client.close();
    }

    public void testGetName() {
        assertEquals("ml_add_memories_action", restMLAddMemoriesAction.getName());
    }

    public void testRoutes() {
        List<RestHandler.Route> routes = restMLAddMemoriesAction.routes();
        assertNotNull(routes);
        assertEquals(1, routes.size());
        assertEquals(RestRequest.Method.POST, routes.get(0).getMethod());
        assertEquals(MEMORIES_PATH, routes.get(0).getPath());
    }

    public void testPrepareRequest() throws Exception {
        RestRequest request = getRestRequest();
        restMLAddMemoriesAction.handleRequest(request, channel, client);

        ArgumentCaptor<MLAddMemoriesRequest> argumentCaptor = ArgumentCaptor.forClass(MLAddMemoriesRequest.class);
        verify(client, times(1)).execute(eq(MLAddMemoriesAction.INSTANCE), argumentCaptor.capture(), any());

        MLAddMemoriesRequest capturedRequest = argumentCaptor.getValue();
        assertNotNull(capturedRequest);
    }

    public void testPrepareRequestWithoutContent() throws Exception {
        Map<String, String> params = new HashMap<>();
        params.put(PARAMETER_MEMORY_CONTAINER_ID, "test-container-id");

        RestRequest request = new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY)
            .withMethod(RestRequest.Method.POST)
            .withPath(MEMORIES_PATH)
            .withParams(params)
            .build();

        Exception exception = expectThrows(Exception.class, () -> { restMLAddMemoriesAction.handleRequest(request, channel, client); });

        assertTrue(exception.getMessage().contains("empty body"));
    }

    public void testPrepareRequestWithMissingContainerId() throws Exception {
        String requestContent = "{\"messages\":[{\"role\":\"user\",\"content\":\"test message\"}]}";

        RestRequest request = new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY)
            .withMethod(RestRequest.Method.POST)
            .withPath(MEMORIES_PATH)
            .withContent(new BytesArray(requestContent), MediaType.fromMediaType("application/json"))
            .build();

        Exception exception = expectThrows(IllegalArgumentException.class, () -> {
            restMLAddMemoriesAction.handleRequest(request, channel, client);
        });

        assertNotNull(exception);
    }

    private RestRequest getRestRequest() {
        String requestContent = "{\"messages\":[{\"role\":\"user\",\"content\":\"test message\"}]}";
        Map<String, String> params = new HashMap<>();
        params.put(PARAMETER_MEMORY_CONTAINER_ID, "test-container-id");

        return new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY)
            .withMethod(RestRequest.Method.POST)
            .withPath(MEMORIES_PATH)
            .withParams(params)
            .withContent(new BytesArray(requestContent), MediaType.fromMediaType("application/json"))
            .build();
    }
}
