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

import java.io.IOException;

import org.opensearch.action.ActionRequest;
import org.opensearch.action.ActionRequestValidationException;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.ml.conversational.action.ActionConstants;
import org.opensearch.rest.RestRequest;

import static org.opensearch.action.ValidateActions.addValidationError;

/**
 * ActionRequest for get interactions
 */
public class GetInteractionsRequest extends ActionRequest {

    private int maxResults = ActionConstants.DEFAULT_MAX_RESULTS;
    private int from = 0;
    private String conversationId;

    /**
     * Constructor
     * @param conversationId UID of the conversation to get interactions from
     * @param maxResults number of interactions to retrieve
     */
    public GetInteractionsRequest(String conversationId, int maxResults) {
        this.conversationId = conversationId;
        this.maxResults = maxResults;
    }

    /**
     * Constructor
     * @param conversationId UID of the conversation to get interactions from
     * @param maxResults number of interactions to retrieve
     * @param from position of first interaction to retrieve
     */
    public GetInteractionsRequest(String conversationId, int maxResults, int from) {
        this.conversationId = conversationId;
        this.maxResults = maxResults;
        this.from = from;
    }

    /**
     * Constructor
     * @param conversationId the UID of the conversation to get interactions from
     */
    public GetInteractionsRequest(String conversationId) {
        this.conversationId = conversationId;
    }

    /**
     * Constructor
     * @param in streaminput to read this from. assumes there was a GetInteractionsRequest.writeTo 
     * @throws IOException if there wasn't a GIR in the stream
     */
    public GetInteractionsRequest(StreamInput in) throws IOException {
        super(in);
        this.conversationId = in.readString();
        this.maxResults = in.readInt();
        this.from = in.readInt();
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        out.writeString(conversationId);
        out.writeInt(maxResults);
        out.writeInt(from);
    }

    @Override
    public ActionRequestValidationException validate() {
        ActionRequestValidationException exception = null;
        if(conversationId == null) {
            exception = addValidationError("must get interactions from a conversation", exception);
        }
        if(maxResults <= 0) {
            exception = addValidationError("must retrieve positive interactions", exception);
        }
        if(from < 0) {
            exception = addValidationError("must start at nonnegative position", exception);
        }
        return exception;
    }

    /**
     * Get the UID of the conversation to retrieve from
     * @return the UID of the conversation this request is trying to retrieve from
     */
    public String getConversationId() {
        return this.conversationId;
    }

    /**
     * Get the maximum number of results to return
     * @return the max number of interactions to return from this action
     */
    public int getMaxResults() {
        return this.maxResults;
    }

    /**
     * what position to start at in retrieving interactions
     * @return the position
     */
    public int getFrom() {
        return from;
    }

    /**
     * Makes a GetInteractionsRequest out of a RestRequest
     * @param request Rest Request representing a get interactions request
     * @return a new GetInteractionsRequest
     * @throws IOException if something goes wrong
     */
    public static GetInteractionsRequest fromRestRequest(RestRequest request) throws IOException {
        String cid = request.param(ActionConstants.CONVERSATION_ID_FIELD);
        if(request.hasParam(ActionConstants.NEXT_TOKEN_FIELD)) {
            int from = Integer.parseInt(request.param(ActionConstants.NEXT_TOKEN_FIELD));
            if(request.hasParam(ActionConstants.REQUEST_MAX_RESULTS_FIELD)) {
                int maxResults = Integer.parseInt(request.param(ActionConstants.REQUEST_MAX_RESULTS_FIELD));
                return new GetInteractionsRequest(cid, maxResults, from);
            } else {
                return new GetInteractionsRequest(cid, ActionConstants.DEFAULT_MAX_RESULTS, from);
            }
        } else {
            if(request.hasParam(ActionConstants.REQUEST_MAX_RESULTS_FIELD)) {
                int maxResults = Integer.parseInt(request.param(ActionConstants.REQUEST_MAX_RESULTS_FIELD));
                return new GetInteractionsRequest(cid, maxResults);
            } else {
                return new GetInteractionsRequest(cid);
            }
        }
    }

}