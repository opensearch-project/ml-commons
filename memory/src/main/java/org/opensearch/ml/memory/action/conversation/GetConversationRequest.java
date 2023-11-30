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
package org.opensearch.ml.memory.action.conversation;

import static org.opensearch.action.ValidateActions.addValidationError;

import java.io.IOException;

import org.opensearch.action.ActionRequest;
import org.opensearch.action.ActionRequestValidationException;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.ml.common.conversation.ActionConstants;
import org.opensearch.rest.RestRequest;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Action Request object for GetConversation (singular)
 */
@AllArgsConstructor
public class GetConversationRequest extends ActionRequest {
    @Getter
    private String conversationId;

    /**
     * Stream Constructor
     * @param in input stream to read this from
     * @throws IOException if something goes wrong reading from stream
     */
    public GetConversationRequest(StreamInput in) throws IOException {
        super(in);
        this.conversationId = in.readString();
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        out.writeString(this.conversationId);
    }

    @Override
    public ActionRequestValidationException validate() {
        ActionRequestValidationException exception = null;
        if (this.conversationId == null) {
            exception = addValidationError("GetConversation Request must have a conversation id", exception);
        }
        return exception;
    }

    /**
     * Creates a GetConversationRequest from a rest request
     * @param request Rest Request representing a GetConversationRequest
     * @return the new GetConversationRequest
     * @throws IOException if something goes wrong in translation
     */
    public static GetConversationRequest fromRestRequest(RestRequest request) throws IOException {
        String conversationId = request.param(ActionConstants.CONVERSATION_ID_FIELD);
        return new GetConversationRequest(conversationId);
    }
}
