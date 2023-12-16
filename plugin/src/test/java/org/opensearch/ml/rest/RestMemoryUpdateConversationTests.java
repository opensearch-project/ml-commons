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
import static org.opensearch.ml.common.conversation.ConversationalIndexConstants.META_NAME_FIELD;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.ExpectedException;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.OpenSearchParseException;
import org.opensearch.action.update.UpdateResponse;
import org.opensearch.client.node.NodeClient;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.common.Strings;
import org.opensearch.core.common.bytes.BytesArray;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.ml.memory.action.conversation.UpdateConversationAction;
import org.opensearch.ml.memory.action.conversation.UpdateConversationRequest;
import org.opensearch.rest.RestChannel;
import org.opensearch.rest.RestHandler;
import org.opensearch.rest.RestRequest;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.test.rest.FakeRestRequest;
import org.opensearch.threadpool.TestThreadPool;
import org.opensearch.threadpool.ThreadPool;

import com.google.gson.Gson;

public class RestMemoryUpdateConversationTests extends OpenSearchTestCase {
    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    private RestMemoryUpdateConversationAction restMemoryUpdateConversationAction;
    private NodeClient client;
    private ThreadPool threadPool;

    @Mock
    RestChannel channel;

    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);
        threadPool = new TestThreadPool(this.getClass().getSimpleName() + "ThreadPool");
        client = spy(new NodeClient(Settings.EMPTY, threadPool));

        restMemoryUpdateConversationAction = new RestMemoryUpdateConversationAction();

        doAnswer(invocation -> {
            ActionListener<UpdateResponse> actionListener = invocation.getArgument(2);
            return null;
        }).when(client).execute(eq(UpdateConversationAction.INSTANCE), any(), any());
    }

    @Override
    public void tearDown() throws Exception {
        super.tearDown();
        threadPool.shutdown();
        client.close();
    }

    public void testConstructor() {
        RestMemoryUpdateConversationAction restMemoryUpdateConversationAction = new RestMemoryUpdateConversationAction();
        assertNotNull(restMemoryUpdateConversationAction);
    }

    public void testGetName() {
        String actionName = restMemoryUpdateConversationAction.getName();
        assertFalse(Strings.isNullOrEmpty(actionName));
        assertEquals("ml_update_conversation_action", actionName);
    }

    public void testRoutes() {
        List<RestHandler.Route> routes = restMemoryUpdateConversationAction.routes();
        assertNotNull(routes);
        assertFalse(routes.isEmpty());
        RestHandler.Route route = routes.get(0);
        assertEquals(RestRequest.Method.PUT, route.getMethod());
        assertEquals("/_plugins/_ml/memory/conversation/{conversation_id}/_update", route.getPath());
    }

    public void testUpdateConversationRequest() throws Exception {
        RestRequest request = getRestRequest();
        restMemoryUpdateConversationAction.handleRequest(request, channel, client);
        ArgumentCaptor<UpdateConversationRequest> argumentCaptor = ArgumentCaptor.forClass(UpdateConversationRequest.class);
        verify(client, times(1)).execute(eq(UpdateConversationAction.INSTANCE), argumentCaptor.capture(), any());
        UpdateConversationRequest updateConversationRequest = argumentCaptor.getValue();
        assertEquals("test_conversationId", updateConversationRequest.getConversationId());
        assertEquals("new name", updateConversationRequest.getUpdateContent().get(META_NAME_FIELD));
    }

    public void testUpdateConnectorRequestWithEmptyContent() throws Exception {
        exceptionRule.expect(OpenSearchParseException.class);
        exceptionRule.expectMessage("Failed to update conversation: Request body is empty");
        RestRequest request = getRestRequestWithEmptyContent();
        restMemoryUpdateConversationAction.handleRequest(request, channel, client);
    }

    public void testUpdateConnectorRequestWithNullConversationId() throws Exception {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("Request should contain conversation_id");
        RestRequest request = getRestRequestWithNullConversationId();
        restMemoryUpdateConversationAction.handleRequest(request, channel, client);
    }

    private RestRequest getRestRequest() {
        RestRequest.Method method = RestRequest.Method.POST;
        final Map<String, Object> updateContent = Map.of(META_NAME_FIELD, "new name");
        String requestContent = new Gson().toJson(updateContent);
        Map<String, String> params = new HashMap<>();
        params.put("conversation_id", "test_conversationId");
        RestRequest request = new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY)
            .withMethod(method)
            .withPath("/_plugins/_ml/memory/conversation/{conversation_id}/_update")
            .withParams(params)
            .withContent(new BytesArray(requestContent), XContentType.JSON)
            .build();
        return request;
    }

    private RestRequest getRestRequestWithEmptyContent() {
        RestRequest.Method method = RestRequest.Method.POST;
        Map<String, String> params = new HashMap<>();
        params.put("conversation_id", "test_conversationId");
        RestRequest request = new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY)
            .withMethod(method)
            .withPath("/_plugins/_ml/memory/conversation/{conversation_id}/_update")
            .withParams(params)
            .withContent(new BytesArray(""), XContentType.JSON)
            .build();
        return request;
    }

    private RestRequest getRestRequestWithNullConversationId() {
        RestRequest.Method method = RestRequest.Method.POST;
        final Map<String, Object> updateContent = Map.of(META_NAME_FIELD, "new name");
        String requestContent = new Gson().toJson(updateContent);
        Map<String, String> params = new HashMap<>();
        RestRequest request = new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY)
            .withMethod(method)
            .withPath("/_plugins/_ml/memory/conversation/{conversation_id}/_update")
            .withParams(params)
            .withContent(new BytesArray(requestContent), XContentType.JSON)
            .build();
        return request;
    }

}
