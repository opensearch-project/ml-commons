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

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.ExpectedException;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.opensearch.client.node.NodeClient;
import org.opensearch.common.settings.Settings;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.common.Strings;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.ml.common.spi.tools.Tool;
import org.opensearch.ml.common.transport.tools.MLListToolsAction;
import org.opensearch.ml.common.transport.tools.MLToolsListRequest;
import org.opensearch.ml.common.transport.tools.MLToolsListResponse;
import org.opensearch.ml.engine.tools.CatIndexTool;
import org.opensearch.rest.RestChannel;
import org.opensearch.rest.RestHandler;
import org.opensearch.rest.RestRequest;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.test.rest.FakeRestRequest;
import org.opensearch.threadpool.TestThreadPool;
import org.opensearch.threadpool.ThreadPool;

public class RestMLListToolsActionTests extends OpenSearchTestCase {
    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Mock
    RestChannel channel;

    private RestMLListToolsAction restMLListToolsAction;

    NodeClient nodeClient;
    private ThreadPool threadPool;

    private Map<String, Tool.Factory> toolFactories = new HashMap<>();
    private Tool.Factory mockFactory = Mockito.mock(Tool.Factory.class);

    @Before
    public void setup() {
        Mockito.when(mockFactory.getDefaultDescription()).thenReturn("Mocked Description");

        Tool tool = CatIndexTool.Factory.getInstance().create(Collections.emptyMap());
        Mockito.when(mockFactory.create(Mockito.any())).thenReturn(tool);
        toolFactories.put("mockTool", mockFactory);
        restMLListToolsAction = new RestMLListToolsAction(toolFactories);

        threadPool = new TestThreadPool(this.getClass().getSimpleName() + "ThreadPool");
        nodeClient = spy(new NodeClient(Settings.EMPTY, threadPool));

        doAnswer(invocation -> {
            ActionListener<MLToolsListResponse> actionListener = invocation.getArgument(2);
            return null;
        }).when(nodeClient).execute(eq(MLListToolsAction.INSTANCE), any(), any());
    }

    @Override
    public void tearDown() throws Exception {
        super.tearDown();
        threadPool.shutdown();
        nodeClient.close();
    }

    public void testGetName() {
        String actionName = restMLListToolsAction.getName();
        assertFalse(Strings.isNullOrEmpty(actionName));
        assertEquals("ml_list_tools_action", actionName);
    }

    public void testRoutes() {
        List<RestHandler.Route> routes = restMLListToolsAction.routes();
        assertNotNull(routes);
        assertFalse(routes.isEmpty());
        RestHandler.Route route = routes.get(0);
        assertEquals(RestRequest.Method.GET, route.getMethod());
        assertEquals("/_plugins/_ml/tools", route.getPath());
    }

    public void test_PrepareRequest() throws Exception {
        RestRequest request = getRestRequest();
        restMLListToolsAction.handleRequest(request, channel, nodeClient);

        ArgumentCaptor<MLToolsListRequest> argumentCaptor = ArgumentCaptor.forClass(MLToolsListRequest.class);
        verify(nodeClient, times(1)).execute(eq(MLListToolsAction.INSTANCE), argumentCaptor.capture(), any());
        String name = argumentCaptor.getValue().getToolMetadataList().get(0).getName();
        assertEquals(name, "mockTool");
    }

    private RestRequest getRestRequest() {
        Map<String, String> params = new HashMap<>();
        RestRequest request = new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY).build();
        return request;
    }
}
