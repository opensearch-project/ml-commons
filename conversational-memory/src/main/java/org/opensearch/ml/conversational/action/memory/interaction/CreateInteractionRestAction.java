/*
 * Copyright Aryn, Inc 2023
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

import java.io.IOException;
import java.util.List;

import org.opensearch.client.node.NodeClient;
import org.opensearch.ml.conversational.action.ActionConstants;
import org.opensearch.rest.BaseRestHandler;
import org.opensearch.rest.RestRequest;
import org.opensearch.rest.action.RestToXContentListener;

/**
 * Rest action for adding a new interaction to a conversation
 */
public class CreateInteractionRestAction extends BaseRestHandler {
    private final static String PUT_INTERACTION_NAME = "conversational_create_interaction";

    @Override
    public List<Route> routes() {
        return List.of(
            new Route(RestRequest.Method.POST, ActionConstants.CREATE_INTERACTION_PATH)
        );
    }

    @Override
    public String getName() {
        return PUT_INTERACTION_NAME;
    }

    @Override
    public RestChannelConsumer prepareRequest(RestRequest request, NodeClient client)  throws IOException {
        CreateInteractionRequest piRequest = CreateInteractionRequest.fromRestRequest(request);
        return channel -> client.execute(CreateInteractionAction.INSTANCE, piRequest, new RestToXContentListener<>(channel));
    }



}