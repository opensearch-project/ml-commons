/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
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
import org.opensearch.common.settings.Settings;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.common.Strings;
import org.opensearch.ml.common.memorycontainer.MLWorkingMemory;
import org.opensearch.ml.common.memorycontainer.WorkingMemoryType;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.transport.memorycontainer.memory.MLGetWorkingMemoryAction;
import org.opensearch.ml.common.transport.memorycontainer.memory.MLGetWorkingMemoryRequest;
import org.opensearch.ml.common.transport.memorycontainer.memory.MLGetWorkingMemoryResponse;
import org.opensearch.rest.RestChannel;
import org.opensearch.rest.RestHandler;
import org.opensearch.rest.RestRequest;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.test.rest.FakeRestRequest;
import org.opensearch.threadpool.TestThreadPool;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.client.node.NodeClient;

public class RestMLGetWorkingMemoryActionTests extends OpenSearchTestCase {

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    private RestMLGetWorkingMemoryAction restMLGetWorkingMemoryAction;
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
        restMLGetWorkingMemoryAction = new RestMLGetWorkingMemoryAction(mlFeatureEnabledSetting);

        threadPool = new TestThreadPool(this.getClass().getSimpleName() + "ThreadPool");
        client = spy(new NodeClient(Settings.EMPTY, threadPool));

        doAnswer(invocation -> {
            ActionListener<MLGetWorkingMemoryResponse> actionListener = invocation.getArgument(2);
            MLWorkingMemory memory = MLWorkingMemory
                .builder()
                .memoryContainerId("test-container-id")
                .memoryType(WorkingMemoryType.CONVERSATIONAL)
                .build();
            MLGetWorkingMemoryResponse response = new MLGetWorkingMemoryResponse(memory);
            actionListener.onResponse(response);
            return null;
        }).when(client).execute(eq(MLGetWorkingMemoryAction.INSTANCE), any(), any());
    }

    @Override
    public void tearDown() throws Exception {
        super.tearDown();
        threadPool.shutdown();
        client.close();
    }

    public void testConstructor() {
        RestMLGetWorkingMemoryAction action = new RestMLGetWorkingMemoryAction(mlFeatureEnabledSetting);
        assertNotNull(action);
    }

    public void testGetName() {
        String actionName = restMLGetWorkingMemoryAction.getName();
        assertFalse(Strings.isNullOrEmpty(actionName));
        assertEquals("ml_get_working_memory_action", actionName);
    }

    public void testRoutes() {
        List<RestHandler.Route> routes = restMLGetWorkingMemoryAction.routes();
        assertNotNull(routes);
        assertFalse(routes.isEmpty());
        assertEquals(1, routes.size());

        RestHandler.Route route = routes.get(0);
        assertEquals(RestRequest.Method.GET, route.getMethod());
    }

    public void testGetRequestWithValidParameters() throws IOException {
        Map<String, String> params = new HashMap<>();
        params.put("memory_container_id", "test-container-id");
        params.put("working_memory_id", "test-memory-id");

        RestRequest request = new FakeRestRequest.Builder(xContentRegistry()).withParams(params).build();

        MLGetWorkingMemoryRequest mlGetWorkingMemoryRequest = restMLGetWorkingMemoryAction.getRequest(request);

        assertNotNull(mlGetWorkingMemoryRequest);
        assertEquals("test-container-id", mlGetWorkingMemoryRequest.getMemoryContainerId());
        assertEquals("test-memory-id", mlGetWorkingMemoryRequest.getWorkingMemoryId());
    }

    public void testPrepareRequestWhenFeatureEnabled() throws Exception {
        Map<String, String> params = new HashMap<>();
        params.put("memory_container_id", "test-container-id");
        params.put("working_memory_id", "test-memory-id");

        RestRequest request = new FakeRestRequest.Builder(xContentRegistry()).withParams(params).build();

        restMLGetWorkingMemoryAction.prepareRequest(request, client);
    }

    public void testPrepareRequestWhenFeatureDisabled() throws IOException {
        when(mlFeatureEnabledSetting.isAgenticMemoryEnabled()).thenReturn(false);

        Map<String, String> params = new HashMap<>();
        params.put("memory_container_id", "test-container-id");
        params.put("working_memory_id", "test-memory-id");

        RestRequest request = new FakeRestRequest.Builder(xContentRegistry()).withParams(params).build();

        thrown.expect(OpenSearchStatusException.class);
        thrown.expectMessage("The Agentic Memory APIs are not enabled");

        restMLGetWorkingMemoryAction.prepareRequest(request, client);
    }
}
