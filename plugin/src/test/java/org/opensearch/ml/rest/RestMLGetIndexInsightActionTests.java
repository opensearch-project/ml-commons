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
import static org.opensearch.ml.utils.MLExceptionUtils.AGENT_FRAMEWORK_DISABLED_ERR_MSG;
import static org.opensearch.ml.utils.RestActionUtils.PARAMETER_INDEX_ID;

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
import org.opensearch.core.common.Strings;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.ml.common.indexInsight.MLIndexInsightType;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.transport.indexInsight.MLIndexInsightGetAction;
import org.opensearch.ml.common.transport.indexInsight.MLIndexInsightGetRequest;
import org.opensearch.rest.RestChannel;
import org.opensearch.rest.RestHandler;
import org.opensearch.rest.RestRequest;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.test.rest.FakeRestRequest;
import org.opensearch.threadpool.TestThreadPool;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.client.node.NodeClient;

public class RestMLGetIndexInsightActionTests extends OpenSearchTestCase {
    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    private RestMLGetIndexInsightAction restMLGetIndexInsightAction;
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
        restMLGetIndexInsightAction = new RestMLGetIndexInsightAction(mlFeatureEnabledSetting);
        doAnswer(invocation -> {
            invocation.getArgument(2);
            return null;
        }).when(client).execute(eq(MLIndexInsightGetAction.INSTANCE), any(), any());
    }

    @Override
    public void tearDown() throws Exception {
        super.tearDown();
        threadPool.shutdown();
        client.close();
    }

    @Test
    public void testConstructor() {
        RestMLGetIndexInsightAction getIndexInsightAction = new RestMLGetIndexInsightAction(mlFeatureEnabledSetting);
        assertNotNull(getIndexInsightAction);
    }

    @Test
    public void testGetName() {
        String actionName = restMLGetIndexInsightAction.getName();
        assertFalse(Strings.isNullOrEmpty(actionName));
        assertEquals("ml_get_index_insight_action", actionName);
    }

    @Test
    public void testRoutes() {
        List<RestHandler.Route> routes = restMLGetIndexInsightAction.routes();
        assertNotNull(routes);
        assertFalse(routes.isEmpty());
        RestHandler.Route route = routes.get(0);
        assertEquals(RestRequest.Method.GET, route.getMethod());
        assertEquals("/_plugins/_ml/insights/{index_id}/{insight_type}", route.getPath());
    }

    @Test
    public void testGetIndexInsightRequest() throws Exception {
        RestRequest request = getRestRequest();
        restMLGetIndexInsightAction.handleRequest(request, channel, client);
        ArgumentCaptor<MLIndexInsightGetRequest> argumentCaptor = ArgumentCaptor.forClass(MLIndexInsightGetRequest.class);
        verify(client, times(1)).execute(eq(MLIndexInsightGetAction.INSTANCE), argumentCaptor.capture(), any());
        MLIndexInsightGetRequest indexInsightGetRequest = argumentCaptor.getValue();
        assertNotNull(indexInsightGetRequest);
        assertEquals(indexInsightGetRequest.getIndexName(), "test_index");
        assertEquals(indexInsightGetRequest.getTargetIndexInsight(), MLIndexInsightType.FIELD_DESCRIPTION);
    }

    @Test
    public void testGetIndexInsightRequestWithoutTaskType() throws Exception {
        RestRequest request = getRestRequestWithoutTaskType();
        restMLGetIndexInsightAction.handleRequest(request, channel, client);
        ArgumentCaptor<MLIndexInsightGetRequest> argumentCaptor = ArgumentCaptor.forClass(MLIndexInsightGetRequest.class);
        verify(client, times(1)).execute(eq(MLIndexInsightGetAction.INSTANCE), argumentCaptor.capture(), any());
        MLIndexInsightGetRequest indexInsightGetRequest = argumentCaptor.getValue();
        assertNotNull(indexInsightGetRequest);
        assertEquals(indexInsightGetRequest.getIndexName(), "test_index");
        assertEquals(indexInsightGetRequest.getTargetIndexInsight(), MLIndexInsightType.STATISTICAL_DATA);
    }

    @Test
    public void testGetIndexInsightRequestWithoutEnableAgentFramework() throws Exception {
        RestRequest request = getRestRequest();
        when(mlFeatureEnabledSetting.isAgentFrameworkEnabled()).thenReturn(false);
        IllegalStateException e = assertThrows(
            IllegalStateException.class,
            () -> restMLGetIndexInsightAction.handleRequest(request, channel, client)
        );
        assertEquals(e.getMessage(), AGENT_FRAMEWORK_DISABLED_ERR_MSG);
    }

    private RestRequest getRestRequest() {
        RestRequest.Method method = RestRequest.Method.GET;
        Map<String, String> params = new HashMap<>();
        params.put(PARAMETER_INDEX_ID, "test_index");
        params.put("insight_type", "field_description");
        RestRequest request = new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY)
            .withMethod(method)
            .withPath("/_plugins/_ml/insights/{index_id}/{insight_type}")
            .withParams(params)
            .build();

        return request;
    }

    private RestRequest getRestRequestWithoutTaskType() {
        RestRequest.Method method = RestRequest.Method.GET;
        Map<String, String> params = new HashMap<>();
        params.put(PARAMETER_INDEX_ID, "test_index");
        RestRequest request = new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY)
            .withMethod(method)
            .withPath("/_plugins/_ml/insights/{index_id}")
            .withParams(params)
            .build();

        return request;
    }

}
