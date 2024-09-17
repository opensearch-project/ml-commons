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
import static org.opensearch.ml.utils.RestActionUtils.PARAMETER_TASK_ID;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.opensearch.client.node.NodeClient;
import org.opensearch.common.settings.Settings;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.common.Strings;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.ml.common.transport.task.MLCancelBatchJobAction;
import org.opensearch.ml.common.transport.task.MLCancelBatchJobRequest;
import org.opensearch.ml.common.transport.task.MLCancelBatchJobResponse;
import org.opensearch.rest.RestChannel;
import org.opensearch.rest.RestHandler;
import org.opensearch.rest.RestRequest;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.test.rest.FakeRestRequest;
import org.opensearch.threadpool.TestThreadPool;
import org.opensearch.threadpool.ThreadPool;

public class RestMLCancelBatchJobActionTests extends OpenSearchTestCase {

    private RestMLCancelBatchJobAction restMLCancelBatchJobAction;

    NodeClient client;
    private ThreadPool threadPool;

    @Mock
    RestChannel channel;

    @Before
    public void setup() {
        restMLCancelBatchJobAction = new RestMLCancelBatchJobAction();

        threadPool = new TestThreadPool(this.getClass().getSimpleName() + "ThreadPool");
        client = spy(new NodeClient(Settings.EMPTY, threadPool));

        doAnswer(invocation -> {
            ActionListener<MLCancelBatchJobResponse> actionListener = invocation.getArgument(2);
            return null;
        }).when(client).execute(eq(MLCancelBatchJobAction.INSTANCE), any(), any());
    }

    @Override
    public void tearDown() throws Exception {
        super.tearDown();
        threadPool.shutdown();
        client.close();
    }

    public void testConstructor() {
        RestMLCancelBatchJobAction mlCancelBatchJobAction = new RestMLCancelBatchJobAction();
        assertNotNull(mlCancelBatchJobAction);
    }

    public void testGetName() {
        String actionName = restMLCancelBatchJobAction.getName();
        assertFalse(Strings.isNullOrEmpty(actionName));
        assertEquals("ml_cancel_task_action", actionName);
    }

    public void testRoutes() {
        List<RestHandler.Route> routes = restMLCancelBatchJobAction.routes();
        assertNotNull(routes);
        assertFalse(routes.isEmpty());
        RestHandler.Route route = routes.get(0);
        assertEquals(RestRequest.Method.POST, route.getMethod());
        assertEquals("/_plugins/_ml/tasks/{task_id}/_cancel", route.getPath());
    }

    public void test_PrepareRequest() throws Exception {
        RestRequest request = getRestRequest();
        restMLCancelBatchJobAction.handleRequest(request, channel, client);

        ArgumentCaptor<MLCancelBatchJobRequest> argumentCaptor = ArgumentCaptor.forClass(MLCancelBatchJobRequest.class);
        verify(client, times(1)).execute(eq(MLCancelBatchJobAction.INSTANCE), argumentCaptor.capture(), any());
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
