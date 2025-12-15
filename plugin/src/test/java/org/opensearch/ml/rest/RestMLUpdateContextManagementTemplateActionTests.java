/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;
import static org.opensearch.ml.utils.RestActionUtils.PARAMETER_TEMPLATE_NAME;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.common.settings.Settings;
import org.opensearch.core.common.bytes.BytesArray;
import org.opensearch.core.xcontent.MediaTypeRegistry;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.rest.RestChannel;
import org.opensearch.rest.RestHandler;
import org.opensearch.rest.RestRequest;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.test.rest.FakeRestRequest;
import org.opensearch.threadpool.TestThreadPool;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.client.node.NodeClient;

public class RestMLUpdateContextManagementTemplateActionTests extends OpenSearchTestCase {

    private RestMLUpdateContextManagementTemplateAction restAction;

    @Mock
    private NodeClient client;
    private ThreadPool threadPool;

    @Mock
    private RestChannel channel;

    @Mock
    private MLFeatureEnabledSetting mlFeatureEnabledSetting;

    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);
        when(mlFeatureEnabledSetting.isAgentFrameworkEnabled()).thenReturn(true);
        restAction = new RestMLUpdateContextManagementTemplateAction(mlFeatureEnabledSetting);

        threadPool = new TestThreadPool(this.getClass().getSimpleName() + "ThreadPool");
        client = spy(new NodeClient(Settings.EMPTY, threadPool));

        doAnswer(invocation -> { return null; }).when(client).execute(any(), any(), any());
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
        threadPool.shutdown();
    }

    @Test
    public void testRoutes() {
        List<RestHandler.Route> routes = restAction.routes();
        assertNotNull(routes);
        assertFalse(routes.isEmpty());
        RestHandler.Route route = routes.get(0);
        assertEquals(RestRequest.Method.PUT, route.getMethod());
        assertEquals("/_plugins/_ml/context_management/{template_name}", route.getPath());
    }

    @Test
    public void testGetName() {
        String actionName = restAction.getName();
        assertFalse(actionName.isEmpty());
        assertEquals("ml_update_context_management_template_action", actionName);
    }

    @Test
    public void testPrepareRequest() throws Exception {
        Map<String, String> params = new HashMap<>();
        params.put(PARAMETER_TEMPLATE_NAME, "test_template");

        String requestContent = "{"
            + "\"name\": \"test_template\","
            + "\"description\": \"Test template\","
            + "\"context_managers\": []"
            + "}";

        RestRequest request = new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY)
            .withMethod(RestRequest.Method.PUT)
            .withPath("/_plugins/_ml/context_management/test_template")
            .withParams(params)
            .withContent(new BytesArray(requestContent), MediaTypeRegistry.JSON)
            .build();

        restAction.prepareRequest(request, client);
    }
}
