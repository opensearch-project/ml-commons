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

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.ExpectedException;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.action.ActionListener;
import org.opensearch.action.get.GetResponse;
import org.opensearch.client.node.NodeClient;
import org.opensearch.common.Strings;
import org.opensearch.common.bytes.BytesArray;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.ml.common.transport.model_group.MLRegisterModelGroupAction;
import org.opensearch.ml.common.transport.model_group.MLRegisterModelGroupInput;
import org.opensearch.ml.common.transport.model_group.MLRegisterModelGroupRequest;
import org.opensearch.rest.RestChannel;
import org.opensearch.rest.RestHandler;
import org.opensearch.rest.RestRequest;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.test.rest.FakeRestRequest;
import org.opensearch.threadpool.TestThreadPool;
import org.opensearch.threadpool.ThreadPool;

import com.google.gson.Gson;

public class RestMLRegisterModelGroupActionTests extends OpenSearchTestCase {
    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    private RestMLRegisterModelGroupAction restMLRegisterModelGroupAction;
    private NodeClient client;
    private ThreadPool threadPool;

    @Mock
    RestChannel channel;

    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);
        threadPool = new TestThreadPool(this.getClass().getSimpleName() + "ThreadPool");
        client = spy(new NodeClient(Settings.EMPTY, threadPool));
        restMLRegisterModelGroupAction = new RestMLRegisterModelGroupAction();
        doAnswer(invocation -> {
            ActionListener<GetResponse> actionListener = invocation.getArgument(2);
            return null;
        }).when(client).execute(eq(MLRegisterModelGroupAction.INSTANCE), any(), any());
    }

    @Override
    public void tearDown() throws Exception {
        super.tearDown();
        threadPool.shutdown();
        client.close();
    }

    public void testConstructor() {
        RestMLRegisterModelGroupAction registerModelGroupAction = new RestMLRegisterModelGroupAction();
        assertNotNull(registerModelGroupAction);
    }

    public void testGetName() {
        String actionName = restMLRegisterModelGroupAction.getName();
        assertFalse(Strings.isNullOrEmpty(actionName));
        assertEquals("ml_register_model_group_action", actionName);
    }

    public void testRoutes() {
        List<RestHandler.Route> routes = restMLRegisterModelGroupAction.routes();
        assertNotNull(routes);
        assertFalse(routes.isEmpty());
        RestHandler.Route route = routes.get(0);
        assertEquals(RestRequest.Method.POST, route.getMethod());
        assertEquals("/_plugins/_ml/model_groups/_register", route.getPath());
    }

    public void testRegisterModelGroupRequest() throws Exception {
        RestRequest request = getRestRequest();
        restMLRegisterModelGroupAction.handleRequest(request, channel, client);
        ArgumentCaptor<MLRegisterModelGroupRequest> argumentCaptor = ArgumentCaptor.forClass(MLRegisterModelGroupRequest.class);
        verify(client, times(1)).execute(eq(MLRegisterModelGroupAction.INSTANCE), argumentCaptor.capture(), any());
        MLRegisterModelGroupInput registerModelGroupInput = argumentCaptor.getValue().getRegisterModelGroupInput();
        assertEquals("testModelGroupName", registerModelGroupInput.getName());
        assertEquals("This is test description", registerModelGroupInput.getDescription());
    }

    public void testRegisterModelGroupRequestWithEmptyContent() throws Exception {
        exceptionRule.expect(IOException.class);
        exceptionRule.expectMessage("Model group request has empty body");
        RestRequest request = getRestRequestWithEmptyContent();
        restMLRegisterModelGroupAction.handleRequest(request, channel, client);
    }

    private RestRequest getRestRequest() {
        RestRequest.Method method = RestRequest.Method.POST;
        final Map<String, Object> modelGroup = Map.of("name", "testModelGroupName", "description", "This is test description");
        String requestContent = new Gson().toJson(modelGroup).toString();
        RestRequest request = new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY)
            .withMethod(method)
            .withPath("/_plugins/_ml/model_groups/_register")
            .withContent(new BytesArray(requestContent), XContentType.JSON)
            .build();
        return request;
    }

    private RestRequest getRestRequestWithEmptyContent() {
        RestRequest.Method method = RestRequest.Method.POST;
        RestRequest request = new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY)
            .withMethod(method)
            .withPath("/_plugins/_ml/model_groups/_register")
            .withContent(new BytesArray(""), XContentType.JSON)
            .build();
        return request;
    }
}
