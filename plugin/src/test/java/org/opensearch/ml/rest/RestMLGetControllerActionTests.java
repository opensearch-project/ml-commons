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
import static org.opensearch.ml.utils.RestActionUtils.PARAMETER_MODEL_ID;

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
import org.opensearch.client.node.NodeClient;
import org.opensearch.common.settings.Settings;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.common.Strings;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.transport.controller.MLControllerGetAction;
import org.opensearch.ml.common.transport.controller.MLControllerGetRequest;
import org.opensearch.ml.common.transport.controller.MLControllerGetResponse;
import org.opensearch.rest.RestChannel;
import org.opensearch.rest.RestHandler;
import org.opensearch.rest.RestRequest;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.test.rest.FakeRestRequest;
import org.opensearch.threadpool.TestThreadPool;
import org.opensearch.threadpool.ThreadPool;

public class RestMLGetControllerActionTests extends OpenSearchTestCase {

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    private RestMLGetControllerAction restMLGetControllerAction;

    NodeClient client;
    private ThreadPool threadPool;

    @Mock
    MLFeatureEnabledSetting mlFeatureEnabledSetting;

    @Mock
    RestChannel channel;

    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);
        when(mlFeatureEnabledSetting.isControllerEnabled()).thenReturn(true);
        restMLGetControllerAction = new RestMLGetControllerAction(mlFeatureEnabledSetting);

        threadPool = new TestThreadPool(this.getClass().getSimpleName() + "ThreadPool");
        client = spy(new NodeClient(Settings.EMPTY, threadPool));

        doAnswer(invocation -> {
            ActionListener<MLControllerGetResponse> actionListener = invocation.getArgument(2);
            return null;
        }).when(client).execute(eq(MLControllerGetAction.INSTANCE), any(), any());

    }

    @Override
    public void tearDown() throws Exception {
        super.tearDown();
        threadPool.shutdown();
        client.close();
    }

    public void testConstructor() {
        RestMLGetControllerAction mlGetControllerAction = new RestMLGetControllerAction(mlFeatureEnabledSetting);
        assertNotNull(mlGetControllerAction);
    }

    public void testGetName() {
        String actionName = restMLGetControllerAction.getName();
        assertFalse(Strings.isNullOrEmpty(actionName));
        assertEquals("ml_get_controller_action", actionName);
    }

    public void testRoutes() {
        List<RestHandler.Route> routes = restMLGetControllerAction.routes();
        assertNotNull(routes);
        assertFalse(routes.isEmpty());
        RestHandler.Route route = routes.get(0);
        assertEquals(RestRequest.Method.GET, route.getMethod());
        assertEquals("/_plugins/_ml/controllers/{model_id}", route.getPath());
    }

    @Test
    public void testGetControllerRequestWithControllerDisabled() throws Exception {
        thrown.expect(IllegalStateException.class);
        thrown
            .expectMessage(
                "Controller is currently disabled. To enable it, update the setting \"plugins.ml_commons.controller_enabled\" to true."
            );
        when(mlFeatureEnabledSetting.isControllerEnabled()).thenReturn(false);
        RestRequest request = getRestRequest();
        restMLGetControllerAction.handleRequest(request, channel, client);
    }

    public void test_PrepareRequest() throws Exception {
        RestRequest request = getRestRequest();
        restMLGetControllerAction.handleRequest(request, channel, client);

        ArgumentCaptor<MLControllerGetRequest> argumentCaptor = ArgumentCaptor.forClass(MLControllerGetRequest.class);
        verify(client, times(1)).execute(eq(MLControllerGetAction.INSTANCE), argumentCaptor.capture(), any());
        String taskId = argumentCaptor.getValue().getModelId();
        assertEquals(taskId, "testModelId");
    }

    private RestRequest getRestRequest() {
        Map<String, String> params = new HashMap<>();
        params.put(PARAMETER_MODEL_ID, "testModelId");
        return new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY).withParams(params).build();
    }
}
