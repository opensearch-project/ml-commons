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
package org.opensearch.ml.conversational.action.memory.interaction;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.Map;

import org.mockito.ArgumentCaptor;
import org.opensearch.client.node.NodeClient;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.ml.common.conversational.ActionConstants;
import org.opensearch.rest.RestChannel;
import org.opensearch.rest.RestHandler.Route;
import org.opensearch.rest.RestRequest;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.test.rest.FakeRestRequest;

public class CreateInteractionRestActionTests extends OpenSearchTestCase {

    public void testBasics() {
        CreateInteractionRestAction action = new CreateInteractionRestAction();
        assert (action.getName().equals("conversational_create_interaction"));
        List<Route> routes = action.routes();
        assert (routes.size() == 1);
        assert (routes.get(0).equals(new Route(RestRequest.Method.POST, ActionConstants.CREATE_INTERACTION_PATH)));
    }

    public void testPrepareRequest() throws Exception {
        Map<String, String> params = Map
            .of(
                ActionConstants.CONVERSATION_ID_FIELD,
                "cid",
                ActionConstants.INPUT_FIELD,
                "input",
                ActionConstants.PROMPT_FIELD,
                "prompt",
                ActionConstants.AI_RESPONSE_FIELD,
                "response",
                ActionConstants.AI_AGENT_FIELD,
                "agent",
                ActionConstants.INTER_ATTRIBUTES_FIELD,
                "attributes"
            );
        CreateInteractionRestAction action = new CreateInteractionRestAction();
        RestRequest request = new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY).withParams(params).build();

        NodeClient client = mock(NodeClient.class);
        RestChannel channel = mock(RestChannel.class);
        action.handleRequest(request, channel, client);

        ArgumentCaptor<CreateInteractionRequest> argCaptor = ArgumentCaptor.forClass(CreateInteractionRequest.class);
        verify(client, times(1)).execute(eq(CreateInteractionAction.INSTANCE), argCaptor.capture(), any());
        CreateInteractionRequest req = argCaptor.getValue();
        assert (req.getConversationId().equals("cid"));
        assert (req.getInput().equals("input"));
        assert (req.getPrompt().equals("prompt"));
        assert (req.getResponse().equals("response"));
        assert (req.getAgent().equals("agent"));
        assert (req.getAttributes().equals("attributes"));
    }
}
