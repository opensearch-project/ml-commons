/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.rest;

import java.io.IOException;
import java.util.List;

import org.opensearch.client.node.NodeClient;
import org.opensearch.ml.common.conversation.ActionConstants;
import org.opensearch.ml.memory.action.conversation.GetTracesAction;
import org.opensearch.ml.memory.action.conversation.GetTracesRequest;
import org.opensearch.rest.BaseRestHandler;
import org.opensearch.rest.RestRequest;
import org.opensearch.rest.action.RestToXContentListener;

public class RestMemoryGetTracesAction extends BaseRestHandler {
    private final static String GET_TRACES_NAME = "conversational_get_traces";

    @Override
    public List<Route> routes() {
        return List.of(new Route(RestRequest.Method.GET, ActionConstants.GET_TRACES_REST_PATH));
    }

    @Override
    public String getName() {
        return GET_TRACES_NAME;
    }

    @Override
    protected RestChannelConsumer prepareRequest(RestRequest request, NodeClient client) throws IOException {
        GetTracesRequest gtRequest = GetTracesRequest.fromRestRequest(request);
        return channel -> client.execute(GetTracesAction.INSTANCE, gtRequest, new RestToXContentListener<>(channel));
    }
}
