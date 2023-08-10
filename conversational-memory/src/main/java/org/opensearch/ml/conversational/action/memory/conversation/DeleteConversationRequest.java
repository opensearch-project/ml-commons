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

import java.io.IOException;

import org.opensearch.action.ActionRequest;
import org.opensearch.action.ActionRequestValidationException;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.ml.conversational.action.ActionConstants;
import org.opensearch.rest.RestRequest;

import static org.opensearch.action.ValidateActions.addValidationError;

/**
 * Action Request for Delete Conversation
 */
public class DeleteConversationRequest extends ActionRequest {
    private String conversationId;

    /**
     * Constructor
     * @param in input stream, assumes one of these requests was written to it
     * @throws IOException if something breaks
     */
    public DeleteConversationRequest(StreamInput in) throws IOException {
        super(in);
        this.conversationId = in.readString();
    }

    /**
     * Constructor
     * @param conversationId id of the conversation to be deleted
     */
    public DeleteConversationRequest(String conversationId) {
        this.conversationId = conversationId;
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeString(conversationId);
    }

    /**
     * Get the conversation id of the conversation to be deleted
     * @return the id of the conversation to be deleted
     */
    public String getId() {
        return conversationId;
    }

    @Override
    public ActionRequestValidationException validate() {
        ActionRequestValidationException exception = null;
        if (conversationId == null) {
            exception = addValidationError("conversation id must not be null", exception);
        }
        return exception;
    }

    /**
     * Create a new DeleteConversationRequest from a RestRequest
     * @param request RestRequest representing a DeleteConversationRequest
     * @return a new DeleteConversationRequest
     * @throws IOException if something breaks
     */
    public static DeleteConversationRequest fromRestRequest(RestRequest request) throws IOException {
        String cid = request.param(ActionConstants.CONVERSATION_ID_FIELD);
        return new DeleteConversationRequest(cid);
    }

}