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
import org.junit.rules.ExpectedException;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.action.delete.DeleteResponse;
import org.opensearch.client.node.NodeClient;
import org.opensearch.common.settings.Settings;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.common.Strings;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.ml.common.transport.controller.MLControllerDeleteAction;
import org.opensearch.ml.common.transport.controller.MLControllerDeleteRequest;
import org.opensearch.ml.settings.MLFeatureEnabledSetting;
import org.opensearch.rest.RestChannel;
import org.opensearch.rest.RestHandler;
import org.opensearch.rest.RestRequest;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.test.rest.FakeRestRequest;
import org.opensearch.threadpool.TestThreadPool;
import org.opensearch.threadpool.ThreadPool;

public class RestMLDeleteControllerActionTests extends OpenSearchTestCase {

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    private RestMLDeleteControllerAction restMLDeleteControllerAction;

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
        restMLDeleteControllerAction = new RestMLDeleteControllerAction(mlFeatureEnabledSetting);

        threadPool = new TestThreadPool(this.getClass().getSimpleName() + "ThreadPool");
        client = spy(new NodeClient(Settings.EMPTY, threadPool));

        doAnswer(invocation -> {
            ActionListener<DeleteResponse> actionListener = invocation.getArgument(2);
            return null;
        }).when(client).execute(eq(MLControllerDeleteAction.INSTANCE), any(), any());

    }

    @Override
    public void tearDown() throws Exception {
        super.tearDown();
        threadPool.shutdown();
        client.close();
    }

    public void testConstructor() {
        RestMLDeleteControllerAction mlDeleteControllerAction = new RestMLDeleteControllerAction(mlFeatureEnabledSetting);
        assertNotNull(mlDeleteControllerAction);
    }

    public void testGetName() {
        String actionName = restMLDeleteControllerAction.getName();
        assertFalse(Strings.isNullOrEmpty(actionName));
        assertEquals("ml_delete_controller_action", actionName);
    }

    public void testRoutes() {
        List<RestHandler.Route> routes = restMLDeleteControllerAction.routes();
        assertNotNull(routes);
        assertFalse(routes.isEmpty());
        RestHandler.Route route = routes.get(0);
        assertEquals(RestRequest.Method.DELETE, route.getMethod());
        assertEquals("/_plugins/_ml/controllers/{model_id}", route.getPath());
    }

    public void testDeleteControllerRequestWithControllerDisabled() throws Exception {
        thrown.expect(IllegalStateException.class);
        thrown
            .expectMessage(
                "Controller is currently disabled. To enable it, update the setting \"plugins.ml_commons.controller_enabled\" to true."
            );
        when(mlFeatureEnabledSetting.isControllerEnabled()).thenReturn(false);
        RestRequest request = getRestRequest();
        restMLDeleteControllerAction.handleRequest(request, channel, client);
    }

    public void test_PrepareRequest() throws Exception {
        RestRequest request = getRestRequest();
        restMLDeleteControllerAction.handleRequest(request, channel, client);

        ArgumentCaptor<MLControllerDeleteRequest> argumentCaptor = ArgumentCaptor.forClass(MLControllerDeleteRequest.class);
        verify(client, times(1)).execute(eq(MLControllerDeleteAction.INSTANCE), argumentCaptor.capture(), any());
        String taskId = argumentCaptor.getValue().getModelId();
        assertEquals(taskId, "testModelId");
    }

    private RestRequest getRestRequest() {
        Map<String, String> params = new HashMap<>();
        params.put(PARAMETER_MODEL_ID, "testModelId");
        return new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY).withParams(params).build();
    }
}
