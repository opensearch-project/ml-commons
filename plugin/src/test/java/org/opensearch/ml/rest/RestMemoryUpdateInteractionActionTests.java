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
import static org.opensearch.ml.common.conversation.ActionConstants.RESPONSE_INTERACTION_ID_FIELD;
import static org.opensearch.ml.common.conversation.ConversationalIndexConstants.INTERACTIONS_ADDITIONAL_INFO_FIELD;

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
import org.opensearch.ml.memory.action.conversation.UpdateInteractionAction;
import org.opensearch.ml.memory.action.conversation.UpdateInteractionRequest;
import org.opensearch.rest.RestChannel;
import org.opensearch.rest.RestHandler;
import org.opensearch.rest.RestRequest;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.test.rest.FakeRestRequest;
import org.opensearch.threadpool.TestThreadPool;
import org.opensearch.threadpool.ThreadPool;

import com.google.gson.Gson;

public class RestMemoryUpdateInteractionActionTests extends OpenSearchTestCase {
    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    private RestMemoryUpdateInteractionAction restMemoryUpdateInteractionAction;
    private NodeClient client;
    private ThreadPool threadPool;

    @Mock
    RestChannel channel;

    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);
        threadPool = new TestThreadPool(this.getClass().getSimpleName() + "ThreadPool");
        client = spy(new NodeClient(Settings.EMPTY, threadPool));

        restMemoryUpdateInteractionAction = new RestMemoryUpdateInteractionAction();

        doAnswer(invocation -> {
            ActionListener<UpdateResponse> actionListener = invocation.getArgument(2);
            return null;
        }).when(client).execute(eq(UpdateInteractionAction.INSTANCE), any(), any());
    }

    @Override
    public void tearDown() throws Exception {
        super.tearDown();
        threadPool.shutdown();
        client.close();
    }

    public void testConstructor() {
        RestMemoryUpdateInteractionAction restMemoryUpdateInteractionAction = new RestMemoryUpdateInteractionAction();
        assertNotNull(restMemoryUpdateInteractionAction);
    }

    public void testGetName() {
        String actionName = restMemoryUpdateInteractionAction.getName();
        assertFalse(Strings.isNullOrEmpty(actionName));
        assertEquals("ml_update_interaction_action", actionName);
    }

    public void testRoutes() {
        List<RestHandler.Route> routes = restMemoryUpdateInteractionAction.routes();
        assertNotNull(routes);
        assertFalse(routes.isEmpty());
        RestHandler.Route route = routes.get(0);
        assertEquals(RestRequest.Method.PUT, route.getMethod());
        assertEquals("/_plugins/_ml/memory/message/{message_id}", route.getPath());
    }

    public void testUpdateInteractionRequest() throws Exception {
        RestRequest request = getRestRequest();
        restMemoryUpdateInteractionAction.handleRequest(request, channel, client);
        ArgumentCaptor<UpdateInteractionRequest> argumentCaptor = ArgumentCaptor.forClass(UpdateInteractionRequest.class);
        verify(client, times(1)).execute(eq(UpdateInteractionAction.INSTANCE), argumentCaptor.capture(), any());
        UpdateInteractionRequest updateInteractionRequest = argumentCaptor.getValue();
        assertEquals("test_interactionId", updateInteractionRequest.getInteractionId());
        assertEquals(Map.of("feedback", "thumbs up!"), updateInteractionRequest.getUpdateContent().get(INTERACTIONS_ADDITIONAL_INFO_FIELD));
    }

    public void testUpdateInteractionRequestWithEmptyContent() throws Exception {
        exceptionRule.expect(OpenSearchParseException.class);
        exceptionRule.expectMessage("Failed to update interaction: Request body is empty");
        RestRequest request = getRestRequestWithEmptyContent();
        restMemoryUpdateInteractionAction.handleRequest(request, channel, client);
    }

    public void testUpdateInteractionRequestWithNullInteractionId() throws Exception {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("Request should contain message_id");
        RestRequest request = getRestRequestWithNullInteractionId();
        restMemoryUpdateInteractionAction.handleRequest(request, channel, client);
    }

    private RestRequest getRestRequest() {
        RestRequest.Method method = RestRequest.Method.POST;
        final Map<String, Object> updateContent = Map.of(INTERACTIONS_ADDITIONAL_INFO_FIELD, Map.of("feedback", "thumbs up!"));
        String requestContent = new Gson().toJson(updateContent);
        Map<String, String> params = new HashMap<>();
        params.put(RESPONSE_INTERACTION_ID_FIELD, "test_interactionId");
        RestRequest request = new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY)
            .withMethod(method)
            .withPath("/_plugins/_ml/memory/message/{message_id}")
            .withParams(params)
            .withContent(new BytesArray(requestContent), XContentType.JSON)
            .build();
        return request;
    }

    private RestRequest getRestRequestWithEmptyContent() {
        RestRequest.Method method = RestRequest.Method.POST;
        Map<String, String> params = new HashMap<>();
        params.put(RESPONSE_INTERACTION_ID_FIELD, "test_interactionId");
        RestRequest request = new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY)
            .withMethod(method)
            .withPath("/_plugins/_ml/memory/message/{message_id}")
            .withParams(params)
            .withContent(new BytesArray(""), XContentType.JSON)
            .build();
        return request;
    }

    private RestRequest getRestRequestWithNullInteractionId() {
        RestRequest.Method method = RestRequest.Method.POST;
        final Map<String, Object> updateContent = Map.of(INTERACTIONS_ADDITIONAL_INFO_FIELD, Map.of("feedback", "thumbs up!"));
        String requestContent = new Gson().toJson(updateContent);
        Map<String, String> params = new HashMap<>();
        RestRequest request = new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY)
            .withMethod(method)
            .withPath("/_plugins/_ml/memory/message/{message_id}")
            .withParams(params)
            .withContent(new BytesArray(requestContent), XContentType.JSON)
            .build();
        return request;
    }
}
