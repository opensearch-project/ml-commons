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
package org.opensearch.ml.conversational.action.memory.conversation;

import java.io.IOException;

import org.opensearch.action.ActionRequest;
import org.opensearch.action.ActionRequestValidationException;
import org.opensearch.common.io.stream.StreamInput;
import org.opensearch.common.io.stream.StreamOutput;
import org.opensearch.ml.conversational.action.ActionConstants;
import org.opensearch.rest.RestRequest;

import static org.opensearch.action.ValidateActions.addValidationError;

/**
 * ActionRequest for list conversations action
 */
public class GetConversationsRequest extends ActionRequest {

    private int maxResults = ActionConstants.DEFAULT_MAX_RESULTS;
    private int from = 0;

    /**
     * Constructor; returns from position 0
     * @param maxResults number of results to return
     */
    public GetConversationsRequest(int maxResults) {
        super();
        this.maxResults = maxResults;
    }

    /**
     * Constructor
     * @param maxResults number of results to return
     * @param from where to start from
     */
    public GetConversationsRequest(int maxResults, int from) {
        super();
        this.maxResults = maxResults;
        this.from = from;
    }

    /**
     * Constructor; defaults to 10 results returned from position 0
     */
    public GetConversationsRequest() {
        super();
    }

    /**
     * Constructor
     * @param in Input stream to read from. assumes there was a writeTo
     * @throws IOException if I can't read
     */
    public GetConversationsRequest(StreamInput in) throws IOException {
        super(in);
        this.maxResults = in.readInt();
        this.from = in.readInt();
    }

    /**
     * max results to be returned by this action
     * @return max results
     */
    public int getMaxResults() {
        return maxResults;
    }

    /**
     * what position to start at in retrieving conversations
     * @return the position
     */
    public int getFrom() {
        return from;
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        out.writeInt(maxResults);
        out.writeInt(from);
    }

    @Override
    public ActionRequestValidationException validate() {
        ActionRequestValidationException exception = null;
        if(this.maxResults == 0) {
            exception = addValidationError("Can't list 0 conversations", exception);
        }
        return exception;
    }

    /**
     * Creates a ListConversationsRequest from a RestRequest
     * @param request a RestRequest for a ListConversations
     * @return a new ListConversationsRequest
     * @throws IOException if something breaks
     */
    public static GetConversationsRequest fromRestRequest(RestRequest request) throws IOException {
        if(request.hasParam(ActionConstants.NEXT_TOKEN_FIELD)) {
            if(request.hasParam(ActionConstants.REQUEST_MAX_RESULTS_FIELD)) {
                return new GetConversationsRequest(Integer.parseInt(request.param(ActionConstants.REQUEST_MAX_RESULTS_FIELD)),
                                                    Integer.parseInt(request.param(ActionConstants.NEXT_TOKEN_FIELD)));
            } else {
                return new GetConversationsRequest(ActionConstants.DEFAULT_MAX_RESULTS,
                                                    Integer.parseInt(request.param(ActionConstants.NEXT_TOKEN_FIELD)));
            }
        } else {
            if(request.hasParam(ActionConstants.REQUEST_MAX_RESULTS_FIELD)) {
                return new GetConversationsRequest(Integer.parseInt(request.param(ActionConstants.REQUEST_MAX_RESULTS_FIELD)));
            } else {
                return new GetConversationsRequest();
            }
        }
    }
}