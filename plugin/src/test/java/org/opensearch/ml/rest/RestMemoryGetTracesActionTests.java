/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.rest;

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
import org.opensearch.ml.common.conversation.ActionConstants;
import org.opensearch.ml.memory.action.conversation.GetTracesAction;
import org.opensearch.ml.memory.action.conversation.GetTracesRequest;
import org.opensearch.rest.RestChannel;
import org.opensearch.rest.RestHandler;
import org.opensearch.rest.RestRequest;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.test.rest.FakeRestRequest;

public class RestMemoryGetTracesActionTests extends OpenSearchTestCase {

    public void testBasics() {
        RestMemoryGetTracesAction action = new RestMemoryGetTracesAction();
        assert (action.getName().equals("conversational_get_traces"));
        List<RestHandler.Route> routes = action.routes();
        assert (routes.size() == 1);
        assert (routes.get(0).equals(new RestHandler.Route(RestRequest.Method.GET, ActionConstants.GET_TRACES_REST_PATH)));
    }

    public void testPrepareRequest() throws Exception {
        RestMemoryGetTracesAction action = new RestMemoryGetTracesAction();
        Map<String, String> params = Map
            .of(
                ActionConstants.RESPONSE_INTERACTION_ID_FIELD,
                "iid",
                ActionConstants.REQUEST_MAX_RESULTS_FIELD,
                "2",
                ActionConstants.NEXT_TOKEN_FIELD,
                "7"
            );
        RestRequest request = new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY).withParams(params).build();

        NodeClient client = mock(NodeClient.class);
        RestChannel channel = mock(RestChannel.class);
        action.handleRequest(request, channel, client);

        ArgumentCaptor<GetTracesRequest> argCaptor = ArgumentCaptor.forClass(GetTracesRequest.class);
        verify(client, times(1)).execute(eq(GetTracesAction.INSTANCE), argCaptor.capture(), any());
        GetTracesRequest req = argCaptor.getValue();
        assert (req.getInteractionId().equals("iid"));
        assert (req.getFrom() == 7);
        assert (req.getMaxResults() == 2);
    }

}
