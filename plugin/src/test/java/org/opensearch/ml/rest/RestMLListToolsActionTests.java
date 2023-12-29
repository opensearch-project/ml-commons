/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.opensearch.ml.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.spy;

import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.ExpectedException;
import org.mockito.Mock;
import org.opensearch.client.node.NodeClient;
import org.opensearch.common.settings.Settings;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.common.Strings;
import org.opensearch.ml.common.spi.tools.Tool;
import org.opensearch.ml.common.transport.tools.MLGetToolAction;
import org.opensearch.ml.common.transport.tools.MLToolsListResponse;
import org.opensearch.rest.RestChannel;
import org.opensearch.rest.RestHandler;
import org.opensearch.rest.RestRequest;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.threadpool.TestThreadPool;
import org.opensearch.threadpool.ThreadPool;

public class RestMLListToolsActionTests extends OpenSearchTestCase {
    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Mock
    private Map<String, Tool.Factory> toolFactories;

    @Mock
    RestChannel channel;

    private RestMLListToolsAction restMLListToolsAction;

    NodeClient nodeClient;
    private ThreadPool threadPool;

    @Before
    public void setup() {
        restMLListToolsAction = new RestMLListToolsAction(toolFactories);

        threadPool = new TestThreadPool(this.getClass().getSimpleName() + "ThreadPool");
        nodeClient = spy(new NodeClient(Settings.EMPTY, threadPool));

        doAnswer(invocation -> {
            ActionListener<MLToolsListResponse> actionListener = invocation.getArgument(2);
            return null;
        }).when(nodeClient).execute(eq(MLGetToolAction.INSTANCE), any(), any());
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
}
