/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.opensearch.ml.utils.MLExceptionUtils.AGENT_FRAMEWORK_DISABLED_ERR_MSG;

import java.util.List;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.common.Strings;
import org.opensearch.core.common.bytes.BytesArray;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.transport.indexInsight.MLIndexInsightConfigPutAction;
import org.opensearch.ml.common.transport.indexInsight.MLIndexInsightConfigPutRequest;
import org.opensearch.rest.RestChannel;
import org.opensearch.rest.RestHandler;
import org.opensearch.rest.RestRequest;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.test.rest.FakeRestRequest;
import org.opensearch.threadpool.TestThreadPool;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.client.node.NodeClient;

public class RestMLPutIndexInsightConfigActionTests extends OpenSearchTestCase {
    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    private RestMLPutIndexInsightConfigAction restMLPutIndexInsightConfigAction;
    private NodeClient client;
    private ThreadPool threadPool;

    @Mock
    MLFeatureEnabledSetting mlFeatureEnabledSetting;

    @Mock
    RestChannel channel;

    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);
        threadPool = new TestThreadPool(this.getClass().getSimpleName() + "ThreadPool");
        client = spy(new NodeClient(Settings.EMPTY, threadPool));
        when(mlFeatureEnabledSetting.isMultiTenancyEnabled()).thenReturn(false);
        when(mlFeatureEnabledSetting.isAgentFrameworkEnabled()).thenReturn(true);
        restMLPutIndexInsightConfigAction = new RestMLPutIndexInsightConfigAction(mlFeatureEnabledSetting);
        doAnswer(invocation -> {
            invocation.getArgument(2);
            return null;
        }).when(client).execute(eq(MLIndexInsightConfigPutAction.INSTANCE), any(), any());
    }

    @Override
    public void tearDown() throws Exception {
        super.tearDown();
        threadPool.shutdown();
        client.close();
    }

    @Test
    public void testConstructor() {
        RestMLPutIndexInsightConfigAction putIndexInsightConfigAction = new RestMLPutIndexInsightConfigAction(mlFeatureEnabledSetting);
        assertNotNull(putIndexInsightConfigAction);
    }

    @Test
    public void testGetName() {
        String actionName = restMLPutIndexInsightConfigAction.getName();
        assertFalse(Strings.isNullOrEmpty(actionName));
        assertEquals("ml_put_index_insight_config_action", actionName);
    }

    @Test
    public void testRoutes() {
        List<RestHandler.Route> routes = restMLPutIndexInsightConfigAction.routes();
        assertNotNull(routes);
        assertFalse(routes.isEmpty());
        RestHandler.Route route = routes.get(0);
        assertEquals(RestRequest.Method.PUT, route.getMethod());
        assertEquals("/_plugins/_ml/index_insight_config/", route.getPath());
    }

    @Test
    public void testPutIndexInsightConfigRequest() throws Exception {
        RestRequest request = getRestRequest();
        restMLPutIndexInsightConfigAction.handleRequest(request, channel, client);
        ArgumentCaptor<MLIndexInsightConfigPutRequest> argumentCaptor = ArgumentCaptor.forClass(MLIndexInsightConfigPutRequest.class);
        verify(client, times(1)).execute(eq(MLIndexInsightConfigPutAction.INSTANCE), argumentCaptor.capture(), any());
        Boolean isEnable = argumentCaptor.getValue().getIsEnable();
        assertEquals(true, isEnable);
    }

    @Test
    public void testPutIndexInsightConfigRequestWithoutEnableAgentFramework() throws Exception {
        RestRequest request = getRestRequest();
        when(mlFeatureEnabledSetting.isAgentFrameworkEnabled()).thenReturn(false);
        IllegalStateException e = assertThrows(
            IllegalStateException.class,
            () -> restMLPutIndexInsightConfigAction.handleRequest(request, channel, client)
        );
        assertEquals(e.getMessage(), AGENT_FRAMEWORK_DISABLED_ERR_MSG);
    }

    @Test
    public void testPutIndexInsightConfigRequest_WithAgentId() throws Exception {
        RestRequest request = getRestRequest("{\"is_enable\":true,\"agent_id\":\"agent-1\"}");
        restMLPutIndexInsightConfigAction.handleRequest(request, channel, client);
        ArgumentCaptor<MLIndexInsightConfigPutRequest> argumentCaptor = ArgumentCaptor.forClass(MLIndexInsightConfigPutRequest.class);
        verify(client, times(1)).execute(eq(MLIndexInsightConfigPutAction.INSTANCE), argumentCaptor.capture(), any());
        assertEquals(true, argumentCaptor.getValue().getIsEnable());
        assertEquals("agent-1", argumentCaptor.getValue().getAgentId());
    }

    @Test
    public void testPutIndexInsightConfigRequest_UnknownField() {
        RestRequest request = getRestRequest("{\"is_enable\":true,\"agentId\":\"agent-1\"}");
        IllegalArgumentException e = assertThrows(
            IllegalArgumentException.class,
            () -> restMLPutIndexInsightConfigAction.handleRequest(request, channel, client)
        );
        assertEquals("Unknown field [agentId] in index insight config", e.getMessage());
        verify(client, never()).execute(eq(MLIndexInsightConfigPutAction.INSTANCE), any(), any());
    }

    private RestRequest getRestRequest() {
        return getRestRequest("{\"is_enable\":true}");
    }

    private RestRequest getRestRequest(String requestContent) {
        RestRequest.Method method = RestRequest.Method.PUT;
        RestRequest request = new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY)
            .withMethod(method)
            .withPath("/_plugins/_ml/index_insight_config")
            .withContent(new BytesArray(requestContent), XContentType.JSON)
            .build();
        return request;
    }

}
