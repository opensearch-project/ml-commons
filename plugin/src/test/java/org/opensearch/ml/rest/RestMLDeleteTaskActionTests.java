/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.times;
import static org.opensearch.ml.settings.MLCommonsSettings.ML_COMMONS_MULTI_TENANCY_ENABLED;
import static org.opensearch.ml.utils.RestActionUtils.PARAMETER_TASK_ID;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.Before;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.action.delete.DeleteResponse;
import org.opensearch.client.node.NodeClient;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.ClusterSettings;
import org.opensearch.common.settings.Settings;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.common.Strings;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.ml.common.transport.task.MLTaskDeleteAction;
import org.opensearch.ml.common.transport.task.MLTaskDeleteRequest;
import org.opensearch.ml.settings.MLFeatureEnabledSetting;
import org.opensearch.rest.RestChannel;
import org.opensearch.rest.RestHandler;
import org.opensearch.rest.RestRequest;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.test.rest.FakeRestRequest;
import org.opensearch.threadpool.TestThreadPool;
import org.opensearch.threadpool.ThreadPool;

public class RestMLDeleteTaskActionTests extends OpenSearchTestCase {

    private RestMLDeleteTaskAction restMLDeleteTaskAction;

    NodeClient client;
    private ThreadPool threadPool;

    @Mock
    private MLFeatureEnabledSetting mlFeatureEnabledSetting;

    @Mock
    RestChannel channel;

    Settings settings;

    @Mock
    private ClusterService clusterService;

    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);
        settings = Settings.builder().put(ML_COMMONS_MULTI_TENANCY_ENABLED.getKey(), false).build();
        when(clusterService.getSettings()).thenReturn(settings);
        when(clusterService.getClusterSettings()).thenReturn(new ClusterSettings(settings, Set.of(ML_COMMONS_MULTI_TENANCY_ENABLED)));
        when(mlFeatureEnabledSetting.isMultiTenancyEnabled()).thenReturn(false);
        restMLDeleteTaskAction = new RestMLDeleteTaskAction(mlFeatureEnabledSetting);

        threadPool = new TestThreadPool(this.getClass().getSimpleName() + "ThreadPool");
        client = spy(new NodeClient(Settings.EMPTY, threadPool));

        doAnswer(invocation -> {
            ActionListener<DeleteResponse> actionListener = invocation.getArgument(2);
            return null;
        }).when(client).execute(eq(MLTaskDeleteAction.INSTANCE), any(), any());
    }

    @Override
    public void tearDown() throws Exception {
        super.tearDown();
        threadPool.shutdown();
        client.close();
    }

    public void testConstructor() {
        RestMLDeleteTaskAction mlDeleteTaskAction = new RestMLDeleteTaskAction(mlFeatureEnabledSetting);
        assertNotNull(mlDeleteTaskAction);
    }

    public void testGetName() {
        String actionName = restMLDeleteTaskAction.getName();
        assertFalse(Strings.isNullOrEmpty(actionName));
        assertEquals("ml_delete_task_action", actionName);
    }

    public void testRoutes() {
        List<RestHandler.Route> routes = restMLDeleteTaskAction.routes();
        assertNotNull(routes);
        assertFalse(routes.isEmpty());
        RestHandler.Route route = routes.get(0);
        assertEquals(RestRequest.Method.DELETE, route.getMethod());
        assertEquals("/_plugins/_ml/tasks/{task_id}", route.getPath());
    }

    public void test_PrepareRequest() throws Exception {
        RestRequest request = getRestRequest();
        restMLDeleteTaskAction.handleRequest(request, channel, client);

        ArgumentCaptor<MLTaskDeleteRequest> argumentCaptor = ArgumentCaptor.forClass(MLTaskDeleteRequest.class);
        verify(client, times(1)).execute(eq(MLTaskDeleteAction.INSTANCE), argumentCaptor.capture(), any());
        String taskId = argumentCaptor.getValue().getTaskId();
        assertEquals(taskId, "test_id");
    }

    private RestRequest getRestRequest() {
        Map<String, String> params = new HashMap<>();
        params.put(PARAMETER_TASK_ID, "test_id");
        RestRequest request = new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY).withParams(params).build();
        return request;
    }
}
