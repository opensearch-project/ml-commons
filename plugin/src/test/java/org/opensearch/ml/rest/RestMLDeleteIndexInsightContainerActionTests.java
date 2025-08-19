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

import java.util.List;

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
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.transport.indexInsight.MLIndexInsightContainerDeleteAction;
import org.opensearch.ml.common.transport.indexInsight.MLIndexInsightContainerDeleteRequest;
import org.opensearch.rest.RestChannel;
import org.opensearch.rest.RestHandler;
import org.opensearch.rest.RestRequest;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.test.rest.FakeRestRequest;
import org.opensearch.threadpool.TestThreadPool;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.client.node.NodeClient;

public class RestMLDeleteIndexInsightContainerActionTests extends OpenSearchTestCase {
    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    private RestMLDeleteIndexInsightContainerAction restMLDeleteIndexInsightContainerAction;
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
        restMLDeleteIndexInsightContainerAction = new RestMLDeleteIndexInsightContainerAction(mlFeatureEnabledSetting);
        doAnswer(invocation -> {
            invocation.getArgument(2);
            return null;
        }).when(client).execute(eq(MLIndexInsightContainerDeleteAction.INSTANCE), any(), any());
    }

    @Override
    public void tearDown() throws Exception {
        super.tearDown();
        threadPool.shutdown();
        client.close();
    }

    @Test
    public void testConstructor() {
        RestMLDeleteIndexInsightContainerAction deleteIndexInsightContainerAction = new RestMLDeleteIndexInsightContainerAction(
            mlFeatureEnabledSetting
        );
        assertNotNull(deleteIndexInsightContainerAction);
    }

    @Test
    public void testGetName() {
        String actionName = restMLDeleteIndexInsightContainerAction.getName();
        assertFalse(Strings.isNullOrEmpty(actionName));
        assertEquals("ml_delete_index_insight_container_action", actionName);
    }

    @Test
    public void testRoutes() {
        List<RestHandler.Route> routes = restMLDeleteIndexInsightContainerAction.routes();
        assertNotNull(routes);
        assertFalse(routes.isEmpty());
        RestHandler.Route route = routes.get(0);
        assertEquals(RestRequest.Method.DELETE, route.getMethod());
        assertEquals("/_plugins/_ml/index_insight_container/", route.getPath());
    }

    @Test
    public void testDeleteIndexInsightContainerRequest() throws Exception {
        RestRequest request = getRestRequest();
        restMLDeleteIndexInsightContainerAction.handleRequest(request, channel, client);
        ArgumentCaptor<MLIndexInsightContainerDeleteRequest> argumentCaptor = ArgumentCaptor
            .forClass(MLIndexInsightContainerDeleteRequest.class);
        verify(client, times(1)).execute(eq(MLIndexInsightContainerDeleteAction.INSTANCE), argumentCaptor.capture(), any());
        MLIndexInsightContainerDeleteRequest indexInsightContainerDeleteRequest = argumentCaptor.getValue();
        assertNotNull(indexInsightContainerDeleteRequest);
    }

    @Test
    public void testDeleteIndexInsightContainerRequestWithoutEnableAgentFramework() throws Exception {
        RestRequest request = getRestRequest();
        when(mlFeatureEnabledSetting.isAgentFrameworkEnabled()).thenReturn(false);
        IllegalStateException e = assertThrows(
            IllegalStateException.class,
            () -> restMLDeleteIndexInsightContainerAction.handleRequest(request, channel, client)
        );
        assertEquals(e.getMessage(), AGENT_FRAMEWORK_DISABLED_ERR_MSG);
    }

    private RestRequest getRestRequest() {
        RestRequest.Method method = RestRequest.Method.DELETE;
        RestRequest request = new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY)
            .withMethod(method)
            .withPath("/_plugins/_ml/index_insight_container")
            .build();
        return request;
    }

}
