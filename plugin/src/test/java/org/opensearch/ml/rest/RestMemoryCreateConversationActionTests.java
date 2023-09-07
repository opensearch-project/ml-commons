/*
 * Copyright 2023 Aryn
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.opensearch.ml.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.mockito.ArgumentCaptor;
import org.opensearch.client.node.NodeClient;
import org.opensearch.core.common.bytes.BytesArray;
import org.opensearch.core.xcontent.MediaTypeRegistry;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.ml.common.conversation.ActionConstants;
import org.opensearch.ml.memory.action.conversation.CreateConversationAction;
import org.opensearch.ml.memory.action.conversation.CreateConversationRequest;
import org.opensearch.rest.RestChannel;
import org.opensearch.rest.RestHandler.Route;
import org.opensearch.rest.RestRequest;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.test.rest.FakeRestRequest;

import com.google.gson.Gson;

public class RestMemoryCreateConversationActionTests extends OpenSearchTestCase {

    Gson gson;

    @Before
    public void setup() {
        gson = new Gson();
    }

    public void testBasics() {
        RestMemoryCreateConversationAction action = new RestMemoryCreateConversationAction();
        assert (action.getName().equals("conversational_create_conversation"));
        List<Route> routes = action.routes();
        assert (routes.size() == 1);
        assert (routes.get(0).equals(new Route(RestRequest.Method.POST, ActionConstants.CREATE_CONVERSATION_REST_PATH)));
    }

    public void testPrepareRequest() throws Exception {
        RestMemoryCreateConversationAction action = new RestMemoryCreateConversationAction();
        RestRequest request = new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY)
            .withContent(
                new BytesArray(gson.toJson(Map.of(ActionConstants.REQUEST_CONVERSATION_NAME_FIELD, "test-name"))),
                MediaTypeRegistry.JSON
            )
            .build();

        NodeClient client = mock(NodeClient.class);
        RestChannel channel = mock(RestChannel.class);
        action.handleRequest(request, channel, client);

        ArgumentCaptor<CreateConversationRequest> argumentCaptor = ArgumentCaptor.forClass(CreateConversationRequest.class);
        verify(client, times(1)).execute(eq(CreateConversationAction.INSTANCE), argumentCaptor.capture(), any());
        assert (argumentCaptor.getValue().getName().equals("test-name"));
    }
}
