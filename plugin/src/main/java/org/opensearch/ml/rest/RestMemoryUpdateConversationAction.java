/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.rest;

import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;
import static org.opensearch.ml.utils.RestActionUtils.getParameterId;

import java.io.IOException;
import java.util.List;

import org.opensearch.OpenSearchParseException;
import org.opensearch.client.node.NodeClient;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.conversation.ActionConstants;
import org.opensearch.ml.memory.action.conversation.UpdateConversationAction;
import org.opensearch.ml.memory.action.conversation.UpdateConversationRequest;
import org.opensearch.rest.BaseRestHandler;
import org.opensearch.rest.RestRequest;
import org.opensearch.rest.action.RestToXContentListener;

public class RestMemoryUpdateConversationAction extends BaseRestHandler {
    private static final String ML_UPDATE_CONVERSATION_ACTION = "ml_update_conversation_action";

    @Override
    public String getName() {
        return ML_UPDATE_CONVERSATION_ACTION;
    }

    @Override
    public List<Route> routes() {
        return List.of(new Route(RestRequest.Method.PUT, ActionConstants.UPDATE_CONVERSATIONS_REST_PATH));
    }

    @Override
    protected RestChannelConsumer prepareRequest(RestRequest request, NodeClient client) throws IOException {
        UpdateConversationRequest updateConversationRequest = getRequest(request);
        return restChannel -> client
            .execute(UpdateConversationAction.INSTANCE, updateConversationRequest, new RestToXContentListener<>(restChannel));
    }

    // VisibleForTesting
    private UpdateConversationRequest getRequest(RestRequest request) throws IOException {
        if (!request.hasContent()) {
            throw new OpenSearchParseException("Failed to update conversation: Request body is empty");
        }

        String conversationId = getParameterId(request, "conversation_id");

        XContentParser parser = request.contentParser();
        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.nextToken(), parser);

        return UpdateConversationRequest.parse(parser, conversationId);
    }
}
