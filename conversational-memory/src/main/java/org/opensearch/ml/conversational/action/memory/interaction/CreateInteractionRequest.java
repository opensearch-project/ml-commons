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
import java.util.Map;
import java.util.MissingResourceException;

import org.opensearch.action.ActionRequest;
import org.opensearch.action.ActionRequestValidationException;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.ml.conversational.action.ActionConstants;
import org.opensearch.rest.RestRequest;

import static org.opensearch.action.ValidateActions.addValidationError;

/**
 * Action Request for create interaction
 */
public class CreateInteractionRequest extends ActionRequest {
    private String conversationId;
    private String input;
    private String prompt;
    private String response;
    private String agent;
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

    /**
     * Constructor. Should probably turn this into a builder idiom
     * @param conversationId id of the conversation to add to
     * @param input human input for this interaction
     * @param prompt prompt template for this interaction
     * @param response genAI response for this interaction
     * @param agent AI agent used for this interaction
     * @param attributes any extra stuff attached to this interaction
     */
    public CreateInteractionRequest(
        String conversationId,
        String input,
        String prompt,
        String response,
        String agent,
        String attributes
    ) {
        this.conversationId = conversationId;
        this.input = input;
        this.prompt = prompt;
        this.response = response;
        this.agent = agent;
        this.attributes = attributes;
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
     * conversation this interaction belongs to
     * @return the id of the conversation this interaction belongs to
     */
    public String getConversationId() { return conversationId; }
    /**
     * input in this interaction
     * @return the human input that sparked this interaction
     */
    public String getInput() { return input; }
    /**
     * prompt used in this interaction
     * @return the prompt template used in this interaction
     */
    public String getPrompt() { return prompt; }
    /**
     * AI response from this interaction
     * @return the AI-Generated response in this interaction
     */
    public String getResponse() { return response; }
    /**
     * Agent in this interaction
     * @return the agent (some identifier) that was interacted with
     */
    public String getAgent() { return agent; }
    /**
     * Arbitrary JSON object of extra data
     * @return arbitrary string for extra data the agent might have generated
     */
    public String getAttributes() { return attributes; }

    /**
     * Create a PutInteractionRequest from a RestRequest
     * @param request a RestRequest for a put interaction op
     * @return new PutInteractionRequest object
     * @throws MissingResourceException if request has no body
     * @throws IOException if something goes wrong reading from request
     */
    public static CreateInteractionRequest fromRestRequest(RestRequest request) throws MissingResourceException, IOException {
        if(!request.hasContent()) {
            throw new MissingResourceException("Put interaction request must have body", "RestRequest", "content");
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