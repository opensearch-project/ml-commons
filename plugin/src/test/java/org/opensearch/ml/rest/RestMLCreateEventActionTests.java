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
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.CREATE_EVENT_PATH;
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
import org.opensearch.ml.common.transport.memorycontainer.memory.MLCreateEventAction;
import org.opensearch.ml.common.transport.memorycontainer.memory.MLCreateEventRequest;
import org.opensearch.ml.common.transport.memorycontainer.memory.MLCreateEventResponse;
import org.opensearch.rest.RestChannel;
import org.opensearch.rest.RestHandler;
import org.opensearch.rest.RestRequest;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.test.rest.FakeRestRequest;
import org.opensearch.threadpool.TestThreadPool;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.client.node.NodeClient;

public class RestMLCreateEventActionTests extends OpenSearchTestCase {
    @Rule
    public ExpectedException thrown = ExpectedException.none();

    private RestMLCreateEventAction restMLCreateEventAction;
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
        restMLCreateEventAction = new RestMLCreateEventAction(mlFeatureEnabledSetting);

        threadPool = new TestThreadPool(this.getClass().getSimpleName() + "ThreadPool");
        client = spy(new NodeClient(Settings.EMPTY, threadPool));

        doAnswer(invocation -> {
            ActionListener<MLCreateEventResponse> actionListener = invocation.getArgument(2);
            return null;
        }).when(client).execute(eq(MLCreateEventAction.INSTANCE), any(), any());
    }

    @Override
    public void tearDown() throws Exception {
        super.tearDown();
        threadPool.shutdown();
        client.close();
    }

    public void testGetName() {
        assertEquals("ml_create_event_action", restMLCreateEventAction.getName());
    }

    public void testRoutes() {
        List<RestHandler.Route> routes = restMLCreateEventAction.routes();
        assertNotNull(routes);
        assertEquals(1, routes.size());
        assertEquals(RestRequest.Method.POST, routes.get(0).getMethod());
        assertEquals(CREATE_EVENT_PATH, routes.get(0).getPath());
    }

    public void testPrepareRequest() throws Exception {
        RestRequest request = getRestRequest();
        restMLCreateEventAction.handleRequest(request, channel, client);

        ArgumentCaptor<MLCreateEventRequest> argumentCaptor = ArgumentCaptor.forClass(MLCreateEventRequest.class);
        verify(client, times(1)).execute(eq(MLCreateEventAction.INSTANCE), argumentCaptor.capture(), any());

        MLCreateEventRequest capturedRequest = argumentCaptor.getValue();
        assertNotNull(capturedRequest);
    }

    public void testPrepareRequestWithoutContent() throws Exception {
        Map<String, String> params = new HashMap<>();
        params.put(PARAMETER_MEMORY_CONTAINER_ID, "test-container-id");

        RestRequest request = new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY)
            .withMethod(RestRequest.Method.POST)
            .withPath(CREATE_EVENT_PATH)
            .withParams(params)
            .build();

        Exception exception = expectThrows(Exception.class, () -> { restMLCreateEventAction.handleRequest(request, channel, client); });

        assertTrue(exception.getMessage().contains("empty body"));
    }

    public void testPrepareRequestWithMissingContainerId() throws Exception {
        String requestContent = "{\"messages\":[{\"role\":\"user\",\"content\":\"test message\"}]}";

        RestRequest request = new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY)
            .withMethod(RestRequest.Method.POST)
            .withPath(CREATE_EVENT_PATH)
            .withContent(new BytesArray(requestContent), MediaType.fromMediaType("application/json"))
            .build();

        Exception exception = expectThrows(IllegalArgumentException.class, () -> {
            restMLCreateEventAction.handleRequest(request, channel, client);
        });

        assertNotNull(exception);
    }

    private RestRequest getRestRequest() {
        String requestContent = "{\"messages\":[{\"role\":\"user\",\"content\":\"test message\"}]}";
        Map<String, String> params = new HashMap<>();
        params.put(PARAMETER_MEMORY_CONTAINER_ID, "test-container-id");

        return new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY)
            .withMethod(RestRequest.Method.POST)
            .withPath(CREATE_EVENT_PATH)
            .withParams(params)
            .withContent(new BytesArray(requestContent), MediaType.fromMediaType("application/json"))
            .build();
    }
}
