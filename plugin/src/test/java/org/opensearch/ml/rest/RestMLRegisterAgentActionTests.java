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
import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_MULTI_TENANCY_ENABLED;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.ExpectedException;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.ClusterSettings;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.common.bytes.BytesArray;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.ml.common.agent.MLAgent;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.transport.agent.MLRegisterAgentAction;
import org.opensearch.ml.common.transport.agent.MLRegisterAgentRequest;
import org.opensearch.rest.RestChannel;
import org.opensearch.rest.RestHandler;
import org.opensearch.rest.RestRequest;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.test.rest.FakeRestRequest;
import org.opensearch.threadpool.TestThreadPool;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.client.node.NodeClient;

import com.google.gson.Gson;

public class RestMLRegisterAgentActionTests extends OpenSearchTestCase {
    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    private RestMLRegisterAgentAction restMLRegisterAgentAction;
    private NodeClient client;
    private ThreadPool threadPool;

    @Mock
    private MLFeatureEnabledSetting mlFeatureEnabledSetting;
    Settings settings;

    @Mock
    private ClusterService clusterService;

    @Mock
    RestChannel channel;

    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);
        threadPool = new TestThreadPool(this.getClass().getSimpleName() + "ThreadPool");
        client = spy(new NodeClient(Settings.EMPTY, threadPool));
        settings = Settings.builder().put(ML_COMMONS_MULTI_TENANCY_ENABLED.getKey(), false).build();
        when(clusterService.getSettings()).thenReturn(settings);
        when(clusterService.getClusterSettings()).thenReturn(new ClusterSettings(settings, Set.of(ML_COMMONS_MULTI_TENANCY_ENABLED)));
        when(mlFeatureEnabledSetting.isMultiTenancyEnabled()).thenReturn(false);
        when(mlFeatureEnabledSetting.isAgentFrameworkEnabled()).thenReturn(true);
        restMLRegisterAgentAction = new RestMLRegisterAgentAction(mlFeatureEnabledSetting);
        doAnswer(invocation -> null).when(client).execute(eq(MLRegisterAgentAction.INSTANCE), any(), any());
    }

    @Override
    public void tearDown() throws Exception {
        super.tearDown();
        threadPool.shutdown();
        client.close();
    }

    public void testConstructor() {
        RestMLRegisterAgentAction registerAgentAction = new RestMLRegisterAgentAction(mlFeatureEnabledSetting);
        assertNotNull(registerAgentAction);
    }

    public void testGetName() {
        String actionName = restMLRegisterAgentAction.getName();
        assertFalse(actionName.isEmpty());
        assertEquals("ml_register_agent_action", actionName);
    }

    public void testRoutes() {
        List<RestHandler.Route> routes = restMLRegisterAgentAction.routes();
        assertNotNull(routes);
        assertFalse(routes.isEmpty());
        RestHandler.Route route = routes.get(0);
        assertEquals(RestRequest.Method.POST, route.getMethod());
        assertEquals("/_plugins/_ml/agents/_register", route.getPath());
    }

    public void testRegisterAgentRequest() throws Exception {
        RestRequest request = getRestRequest();
        restMLRegisterAgentAction.handleRequest(request, channel, client);
        ArgumentCaptor<MLRegisterAgentRequest> argumentCaptor = ArgumentCaptor.forClass(MLRegisterAgentRequest.class);
        verify(client, times(1)).execute(eq(MLRegisterAgentAction.INSTANCE), argumentCaptor.capture(), any());
        MLAgent mlAgent = argumentCaptor.getValue().getMlAgent();
        assertEquals("testAgentName", mlAgent.getName());
        assertEquals("This is a test agent description", mlAgent.getDescription());
    }

    public void testRegisterAgentRequestWhenFrameworkDisabled() throws Exception {
        when(mlFeatureEnabledSetting.isAgentFrameworkEnabled()).thenReturn(false);
        exceptionRule.expect(IllegalStateException.class);
        exceptionRule.expectMessage("Agent Framework is currently disabled. To enable it, update the setting \"plugins.ml_commons.agent_framework_enabled\" to true.");
        RestRequest request = getRestRequest();
        restMLRegisterAgentAction.handleRequest(request, channel, client);
    }

    public void testRegisterAgentWithModelWhenUnifiedAgentApiDisabled() throws Exception {
        when(mlFeatureEnabledSetting.isUnifiedAgentApiEnabled()).thenReturn(false);
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("Unified agent API is not enabled. To enable, please update the setting plugins.ml_commons.unified_agent_api_enabled");
        RestRequest request = getRestRequestWithModel();
        restMLRegisterAgentAction.handleRequest(request, channel, client);
    }

    public void testRegisterAgentWithModelWhenUnifiedAgentApiEnabled() throws Exception {
        when(mlFeatureEnabledSetting.isUnifiedAgentApiEnabled()).thenReturn(true);
        RestRequest request = getRestRequestWithModel();
        restMLRegisterAgentAction.handleRequest(request, channel, client);
        ArgumentCaptor<MLRegisterAgentRequest> argumentCaptor = ArgumentCaptor.forClass(MLRegisterAgentRequest.class);
        verify(client, times(1)).execute(eq(MLRegisterAgentAction.INSTANCE), argumentCaptor.capture(), any());
        MLAgent mlAgent = argumentCaptor.getValue().getMlAgent();
        assertEquals("testAgentWithModel", mlAgent.getName());
        assertNotNull(mlAgent.getModel());
    }

    private RestRequest getRestRequest() {
        RestRequest.Method method = RestRequest.Method.POST;
        final Map<String, Object> agentData = Map
            .of("name", "testAgentName", "description", "This is a test agent description", "type", "FLOW");
        String requestContent = new Gson().toJson(agentData);
        return new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY)
            .withMethod(method)
            .withPath("/_plugins/_ml/agents/_register")
            .withContent(new BytesArray(requestContent), XContentType.JSON)
            .build();
    }

    private RestRequest getRestRequestWithModel() {
        RestRequest.Method method = RestRequest.Method.POST;
        final Map<String, Object> modelData = Map.of("model_provider", "bedrock", "model_id", "anthropic.claude-v2");
        final Map<String, Object> agentData = Map
            .of("name", "testAgentWithModel", "description", "Agent with model", "type", "CONVERSATIONAL", "model", modelData);
        String requestContent = new Gson().toJson(agentData);
        return new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY)
            .withMethod(method)
            .withPath("/_plugins/_ml/agents/_register")
            .withContent(new BytesArray(requestContent), XContentType.JSON)
            .build();
    }
}
