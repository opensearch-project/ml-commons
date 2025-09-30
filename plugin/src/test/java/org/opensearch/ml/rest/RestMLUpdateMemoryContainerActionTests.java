/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.ExpectedException;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.OpenSearchStatusException;
import org.opensearch.action.update.UpdateResponse;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.common.Strings;
import org.opensearch.core.common.bytes.BytesArray;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.transport.memorycontainer.memory.MLUpdateMemoryContainerAction;
import org.opensearch.ml.common.transport.memorycontainer.memory.MLUpdateMemoryContainerRequest;
import org.opensearch.rest.RestChannel;
import org.opensearch.rest.RestHandler;
import org.opensearch.rest.RestRequest;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.test.rest.FakeRestRequest;
import org.opensearch.threadpool.TestThreadPool;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.client.node.NodeClient;

public class RestMLUpdateMemoryContainerActionTests extends OpenSearchTestCase {

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    private RestMLUpdateMemoryContainerAction restMLUpdateMemoryContainerAction;
    NodeClient client;
    private ThreadPool threadPool;

    @Mock
    RestChannel channel;

    @Mock
    private MLFeatureEnabledSetting mlFeatureEnabledSetting;

    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);
        when(mlFeatureEnabledSetting.isAgenticMemoryEnabled()).thenReturn(true);
        restMLUpdateMemoryContainerAction = new RestMLUpdateMemoryContainerAction(mlFeatureEnabledSetting);

        threadPool = new TestThreadPool(this.getClass().getSimpleName() + "ThreadPool");
        client = spy(new NodeClient(Settings.EMPTY, threadPool));

        doAnswer(invocation -> {
            ActionListener<UpdateResponse> actionListener = invocation.getArgument(2);
            UpdateResponse response = mock(UpdateResponse.class);
            actionListener.onResponse(response);
            return null;
        }).when(client).execute(eq(MLUpdateMemoryContainerAction.INSTANCE), any(), any());
    }

    @Override
    public void tearDown() throws Exception {
        super.tearDown();
        threadPool.shutdown();
        client.close();
    }

    public void testConstructor() {
        RestMLUpdateMemoryContainerAction action = new RestMLUpdateMemoryContainerAction(mlFeatureEnabledSetting);
        assertNotNull(action);
    }

    public void testGetName() {
        String actionName = restMLUpdateMemoryContainerAction.getName();
        assertFalse(Strings.isNullOrEmpty(actionName));
        assertEquals("ml_update_memory_container_action", actionName);
    }

    public void testRoutes() {
        List<RestHandler.Route> routes = restMLUpdateMemoryContainerAction.routes();
        assertNotNull(routes);
        assertFalse(routes.isEmpty());
        assertEquals(1, routes.size());

        RestHandler.Route route = routes.get(0);
        assertEquals(RestRequest.Method.PUT, route.getMethod());
    }

    public void testGetRequestWithValidInput() throws IOException {
        String requestBody = "{\n" + "  \"name\": \"updated-name\",\n" + "  \"description\": \"Updated description\"\n" + "}";

        Map<String, String> params = new HashMap<>();
        params.put("memory_container_id", "test-container-id");

        RestRequest request = new FakeRestRequest.Builder(xContentRegistry())
            .withParams(params)
            .withContent(new BytesArray(requestBody), XContentType.JSON)
            .build();

        MLUpdateMemoryContainerRequest mlUpdateMemoryContainerRequest = restMLUpdateMemoryContainerAction.getRequest(request);

        assertNotNull(mlUpdateMemoryContainerRequest);
        assertEquals("test-container-id", mlUpdateMemoryContainerRequest.getMemoryContainerId());
        assertNotNull(mlUpdateMemoryContainerRequest.getMlUpdateMemoryContainerInput());
    }

    public void testGetRequestWithEmptyBody() throws IOException {
        Map<String, String> params = new HashMap<>();
        params.put("memory_container_id", "test-container-id");

        RestRequest request = new FakeRestRequest.Builder(xContentRegistry()).withParams(params).build();

        thrown.expect(IllegalArgumentException.class);
        thrown.expectMessage("Update memory container request has empty body");

        restMLUpdateMemoryContainerAction.getRequest(request);
    }

    public void testPrepareRequestWhenFeatureEnabled() throws Exception {
        String requestBody = "{\n" + "  \"name\": \"updated-name\"\n" + "}";

        Map<String, String> params = new HashMap<>();
        params.put("memory_container_id", "test-container-id");

        RestRequest request = new FakeRestRequest.Builder(xContentRegistry())
            .withParams(params)
            .withContent(new BytesArray(requestBody), XContentType.JSON)
            .build();

        restMLUpdateMemoryContainerAction.prepareRequest(request, client);
    }

    public void testPrepareRequestWhenFeatureDisabled() throws IOException {
        when(mlFeatureEnabledSetting.isAgenticMemoryEnabled()).thenReturn(false);

        String requestBody = "{\n" + "  \"name\": \"updated-name\"\n" + "}";

        Map<String, String> params = new HashMap<>();
        params.put("memory_container_id", "test-container-id");

        RestRequest request = new FakeRestRequest.Builder(xContentRegistry())
            .withParams(params)
            .withContent(new BytesArray(requestBody), XContentType.JSON)
            .build();

        thrown.expect(OpenSearchStatusException.class);
        thrown.expectMessage("The Agentic Memory APIs are not enabled");

        restMLUpdateMemoryContainerAction.prepareRequest(request, client);
    }
}
