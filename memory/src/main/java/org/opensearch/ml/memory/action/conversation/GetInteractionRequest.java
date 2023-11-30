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
 * Action Request for GetInteraction
 */
@AllArgsConstructor
public class GetInteractionRequest extends ActionRequest {
    @Getter
    private String conversationId;
    @Getter
    private String interactionId;

    /**
     * Stream Constructor
     * @param in input stream to read this request from
     * @throws IOException if somthing goes wrong reading
     */
    public GetInteractionRequest(StreamInput in) throws IOException {
        super(in);
        this.conversationId = in.readString();
        this.interactionId = in.readString();
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        out.writeString(this.conversationId);
        out.writeString(this.interactionId);
    }

    @Override
    public ActionRequestValidationException validate() {
        ActionRequestValidationException exception = null;
        if (conversationId == null) {
            exception = addValidationError("Get Interaction Request must have a conversation id", exception);
        }
        if (interactionId == null) {
            exception = addValidationError("Get Interaction Request must have an interaction id", exception);
        }
        return exception;
    }

    /**
     * Creates a GetInteractionRequest from a Rest Request
     * @param request Rest Request representing a GetInteractionRequest
     * @return new GetInteractionRequest built from the rest request
     * @throws IOException if something goes wrong reading from the rest request
     */
    public static GetInteractionRequest fromRestRequest(RestRequest request) throws IOException {
        String conversationId = request.param(ActionConstants.CONVERSATION_ID_FIELD);
        String interactionId = request.param(ActionConstants.RESPONSE_INTERACTION_ID_FIELD);
        return new GetInteractionRequest(conversationId, interactionId);
    }
}
