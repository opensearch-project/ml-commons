/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.rest;

import static org.mockito.Mockito.*;
import static org.opensearch.ml.utils.RestActionUtils.PARAMETER_AGENT_ID;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.common.bytes.BytesArray;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.ml.common.agent.MLAgent;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.transport.agent.MLAgentUpdateAction;
import org.opensearch.ml.common.transport.agent.MLAgentUpdateRequest;
import org.opensearch.rest.RestChannel;
import org.opensearch.rest.RestHandler;
import org.opensearch.rest.RestRequest;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.test.rest.FakeRestRequest;
import org.opensearch.threadpool.TestThreadPool;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.client.node.NodeClient;

import com.google.gson.Gson;

import joptsimple.internal.Strings;

public class RestMLUpdateAgentActionTests extends OpenSearchTestCase {

    private RestMLUpdateAgentAction restMLUpdateAgentAction;
    NodeClient client;
    private ThreadPool threadPool;

    @Mock
    RestChannel channel;

    @Mock
    private MLFeatureEnabledSetting mlFeatureEnabledSetting;
    Settings settings;

    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);
        when(mlFeatureEnabledSetting.isAgentFrameworkEnabled()).thenReturn(true);
        restMLUpdateAgentAction = new RestMLUpdateAgentAction(mlFeatureEnabledSetting);

        threadPool = new TestThreadPool(this.getClass().getSimpleName() + "ThreadPool");
        client = spy(new NodeClient(Settings.EMPTY, threadPool));
        settings = Settings.builder().build();

        doAnswer(invocation -> null).when(client).execute(eq(MLAgentUpdateAction.INSTANCE), any(), any());
    }

    @Override
    public void tearDown() throws Exception {
        super.tearDown();
        threadPool.shutdown();
        client.close();
    }

    public void testConstructor() {
        RestMLUpdateAgentAction mlUpdateAgentAction = new RestMLUpdateAgentAction(mlFeatureEnabledSetting);
        assertNotNull(mlUpdateAgentAction);
    }

    public void testGetName() {
        String actionName = restMLUpdateAgentAction.getName();
        assertFalse(Strings.isNullOrEmpty(actionName));
        assertEquals("ml_update_agent_action", actionName);
    }

    public void testRoutes() {
        List<RestHandler.Route> routes = restMLUpdateAgentAction.routes();
        assertNotNull(routes);
        assertFalse(routes.isEmpty());
        RestHandler.Route route = routes.getFirst();
        assertEquals(RestRequest.Method.PUT, route.getMethod());
        assertEquals("/_plugins/_ml/agents/{agent_id}", route.getPath());
    }

    public void test_PrepareRequest() throws Exception {
        RestRequest request = getRestRequest();
        restMLUpdateAgentAction.handleRequest(request, channel, client);

        ArgumentCaptor<MLAgentUpdateRequest> argumentCaptor = ArgumentCaptor.forClass(MLAgentUpdateRequest.class);
        verify(client, times(1)).execute(eq(MLAgentUpdateAction.INSTANCE), argumentCaptor.capture(), any());
        String agentId = argumentCaptor.getValue().getAgentId();
        MLAgent mlAgent = argumentCaptor.getValue().getMlAgent();
        assertEquals("agent_id", agentId);
        assertEquals("testAgentName", mlAgent.getName());
        assertEquals("This is a test agent description", mlAgent.getDescription());
        assertEquals("FLOW", mlAgent.getType());
    }

    public void test_PrepareRequest_disabled() {
        RestRequest request = getRestRequest();

        when(mlFeatureEnabledSetting.isAgentFrameworkEnabled()).thenReturn(false);
        assertThrows(IllegalStateException.class, () -> restMLUpdateAgentAction.handleRequest(request, channel, client));
    }

    private RestRequest getRestRequest() {
        RestRequest.Method method = RestRequest.Method.PUT;
        Map<String, String> params = new HashMap<>();
        params.put(PARAMETER_AGENT_ID, "agent_id");
        final Map<String, Object> agentData = Map
            .of("name", "testAgentName", "description", "This is a test agent description", "type", "FLOW");
        String requestContent = new Gson().toJson(agentData);
        return new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY)
            .withMethod(method)
            .withParams(params)
            .withContent(new BytesArray(requestContent), XContentType.JSON)
            .build();
    }
}
