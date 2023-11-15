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
import org.junit.Ignore;
import org.mockito.ArgumentCaptor;
import org.opensearch.client.node.NodeClient;
import org.opensearch.core.common.bytes.BytesArray;
import org.opensearch.core.xcontent.MediaTypeRegistry;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.ml.common.conversation.ActionConstants;
import org.opensearch.ml.memory.action.conversation.CreateInteractionAction;
import org.opensearch.ml.memory.action.conversation.CreateInteractionRequest;
import org.opensearch.rest.RestChannel;
import org.opensearch.rest.RestHandler.Route;
import org.opensearch.rest.RestRequest;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.test.rest.FakeRestRequest;

import com.google.gson.Gson;

@Ignore
public class RestMemoryCreateInteractionActionTests extends OpenSearchTestCase {

    Gson gson;

    @Before
    public void setup() {
        gson = new Gson();
    }

    public void testBasics() {
        RestMemoryCreateInteractionAction action = new RestMemoryCreateInteractionAction();
        assert (action.getName().equals("conversational_create_interaction"));
        List<Route> routes = action.routes();
        assert (routes.size() == 1);
        assert (routes.get(0).equals(new Route(RestRequest.Method.POST, ActionConstants.CREATE_INTERACTION_REST_PATH)));
    }

    public void testPrepareRequest() throws Exception {
        Map<String, String> params = Map
            .of(
                ActionConstants.INPUT_FIELD,
                "input",
                ActionConstants.PROMPT_TEMPLATE_FIELD,
                "pt",
                ActionConstants.AI_RESPONSE_FIELD,
                "response",
                ActionConstants.RESPONSE_ORIGIN_FIELD,
                "origin",
                ActionConstants.ADDITIONAL_INFO_FIELD,
                "metadata"
            );
        RestMemoryCreateInteractionAction action = new RestMemoryCreateInteractionAction();
        RestRequest request = new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY)
            .withParams(Map.of(ActionConstants.CONVERSATION_ID_FIELD, "cid"))
            .withContent(new BytesArray(gson.toJson(params)), MediaTypeRegistry.JSON)
            .build();

        NodeClient client = mock(NodeClient.class);
        RestChannel channel = mock(RestChannel.class);
        action.handleRequest(request, channel, client);

        ArgumentCaptor<CreateInteractionRequest> argCaptor = ArgumentCaptor.forClass(CreateInteractionRequest.class);
        verify(client, times(1)).execute(eq(CreateInteractionAction.INSTANCE), argCaptor.capture(), any());
        CreateInteractionRequest req = argCaptor.getValue();
        assert (req.getConversationId().equals("cid"));
        assert (req.getInput().equals("input"));
        assert (req.getPromptTemplate().equals("pt"));
        assert (req.getResponse().equals("response"));
        assert (req.getOrigin().equals("origin"));
        assert (req.getAdditionalInfo().equals("metadata"));
    }
}
