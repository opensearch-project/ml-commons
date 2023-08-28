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
package org.opensearch.ml.conversational.action.memory.conversation;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.List;

import org.mockito.ArgumentCaptor;
import org.opensearch.client.node.NodeClient;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.ml.common.conversational.ActionConstants;
import org.opensearch.rest.RestChannel;
import org.opensearch.rest.RestHandler.Route;
import org.opensearch.rest.RestRequest;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.test.rest.FakeRestRequest;

public class GetConversationsRestActionTests extends OpenSearchTestCase {
    public void testBasics() {
        GetConversationsRestAction action = new GetConversationsRestAction();
        assert (action.getName().equals("conversational_get_conversations"));
        List<Route> routes = action.routes();
        assert (routes.size() == 1);
        assert (routes.get(0).equals(new Route(RestRequest.Method.GET, ActionConstants.GET_CONVERSATIONS_REST_PATH)));
    }

    public void testPrepareRequest() throws Exception {
        GetConversationsRestAction action = new GetConversationsRestAction();
        RestRequest request = new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY).build();

        NodeClient client = mock(NodeClient.class);
        RestChannel channel = mock(RestChannel.class);
        action.handleRequest(request, channel, client);

        ArgumentCaptor<GetConversationsRequest> argCaptor = ArgumentCaptor.forClass(GetConversationsRequest.class);
        verify(client, times(1)).execute(eq(GetConversationsAction.INSTANCE), argCaptor.capture(), any());
        GetConversationsRequest gcreq = argCaptor.getValue();
        assert (gcreq.getFrom() == 0);
        assert (gcreq.getMaxResults() == ActionConstants.DEFAULT_MAX_RESULTS);
    }
}
