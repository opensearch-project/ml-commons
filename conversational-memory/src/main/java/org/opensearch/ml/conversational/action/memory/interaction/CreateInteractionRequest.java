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
import java.util.Map;

import org.opensearch.action.ActionRequest;
import org.opensearch.action.ActionRequestValidationException;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.ml.common.conversational.ActionConstants;
import org.opensearch.rest.RestRequest;

import lombok.AllArgsConstructor;
import lombok.Getter;

import static org.opensearch.action.ValidateActions.addValidationError;

/**
 * Action Request for create interaction
 */
@AllArgsConstructor
public class CreateInteractionRequest extends ActionRequest {
    @Getter
    private String conversationId;
    @Getter
    private String input;
    @Getter
    private String prompt;
    @Getter
    private String response;
    @Getter
    private String agent;
    @Getter
    private String attributes;

    /**
     * Constructor
     * @param in stream to read this request from
     * @throws IOException if something breaks or there's no p.i.request in the stream
     */
    public CreateInteractionRequest(StreamInput in) throws IOException {
        super(in);
        this.conversationId = in.readString();
        this.input = in.readString();
        this.response = in.readString();
        this.prompt = in.readOptionalString();
        this.agent = in.readOptionalString();
        this.attributes = in.readOptionalString();
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        out.writeString(conversationId);
        out.writeString(input);
        out.writeString(response);
        out.writeString(prompt);
        out.writeString(agent);
        out.writeString(attributes);
    }
    
    @Override
    public ActionRequestValidationException validate() {
        ActionRequestValidationException exception = null;
        if(this.conversationId == null) {
            exception = addValidationError("Interaction MUST belong to a conversation ID", exception);
        }
        return exception;
    }

    /**
     * Create a PutInteractionRequest from a RestRequest
     * @param request a RestRequest for a put interaction op
     * @return new PutInteractionRequest object
     * @throws IOException if something goes wrong reading from request
     */
    public static CreateInteractionRequest fromRestRequest(RestRequest request) throws IOException {
        if(!request.hasContent()) {
            throw new IOException("Put interaction request must have body");
        }
        Map<String, String> bodyMap = request.contentParser().mapStrings();
        String cid = request.param(ActionConstants.CONVERSATION_ID_FIELD);
        String inp = bodyMap.get(ActionConstants.INPUT_FIELD);
        String prp = bodyMap.get(ActionConstants.PROMPT_FIELD);
        String rsp = bodyMap.get(ActionConstants.AI_RESPONSE_FIELD);
        String agt = bodyMap.get(ActionConstants.AI_AGENT_FIELD);
        String att = bodyMap.get(ActionConstants.INTER_ATTRIBUTES_FIELD);
        return new CreateInteractionRequest(cid, inp, prp, rsp, agt, att);
    }

}