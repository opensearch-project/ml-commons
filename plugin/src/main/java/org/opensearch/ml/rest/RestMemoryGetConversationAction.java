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

import java.io.IOException;
import java.util.List;

import org.opensearch.client.node.NodeClient;
import org.opensearch.ml.common.conversation.ActionConstants;
import org.opensearch.ml.memory.action.conversation.GetConversationAction;
import org.opensearch.ml.memory.action.conversation.GetConversationRequest;
import org.opensearch.rest.BaseRestHandler;
import org.opensearch.rest.RestRequest;
import org.opensearch.rest.action.RestToXContentListener;

public class RestMemoryGetConversationAction extends BaseRestHandler {
    private final static String GET_CONVERSATION_NAME = "conversational_get_conversation";

    @Override
    public List<Route> routes() {
        return List.of(new Route(RestRequest.Method.GET, ActionConstants.GET_CONVERSATION_REST_PATH));
    }

    @Override
    public String getName() {
        return GET_CONVERSATION_NAME;
    }

    @Override
    public RestChannelConsumer prepareRequest(RestRequest request, NodeClient client) throws IOException {
        GetConversationRequest gcRequest = GetConversationRequest.fromRestRequest(request);
        return channel -> client.execute(GetConversationAction.INSTANCE, gcRequest, new RestToXContentListener<>(channel));
    }
}
