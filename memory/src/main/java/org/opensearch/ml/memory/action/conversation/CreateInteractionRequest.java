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
import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.opensearch.action.ActionRequest;
import org.opensearch.action.ActionRequestValidationException;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.conversation.ActionConstants;
import org.opensearch.rest.RestRequest;

import lombok.AllArgsConstructor;
import lombok.Getter;

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
    private String promptTemplate;
    @Getter
    private String response;
    @Getter
    private String origin;
    @Getter
    private Map<String, String> additionalInfo;
    @Getter
    private String parentIid;
    @Getter
    private Integer traceNumber;

    public CreateInteractionRequest(
        String conversationId,
        String input,
        String promptTemplate,
        String response,
        String origin,
        Map<String, String> additionalInfo
    ) {
        this.conversationId = conversationId;
        this.input = input;
        this.promptTemplate = promptTemplate;
        this.response = response;
        this.origin = origin;
        this.additionalInfo = additionalInfo;
    }

    /**
     * Constructor
     * @param in stream to read this request from
     * @throws IOException if something breaks or there's no p.i.request in the stream
     */
    public CreateInteractionRequest(StreamInput in) throws IOException {
        super(in);
        this.conversationId = in.readString();
        this.input = in.readString();
        this.promptTemplate = in.readString();
        this.response = in.readString();
        this.origin = in.readOptionalString();
        if (in.readBoolean()) {
            this.additionalInfo = in.readMap(s -> s.readString(), s -> s.readString());
        }
        this.parentIid = in.readOptionalString();
        this.traceNumber = in.readOptionalInt();
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        out.writeString(conversationId);
        out.writeString(input);
        out.writeString(promptTemplate);
        out.writeString(response);
        out.writeOptionalString(origin);
        if (additionalInfo != null) {
            out.writeBoolean(true);
            out.writeMap(additionalInfo, StreamOutput::writeString, StreamOutput::writeString);
        } else {
            out.writeBoolean(false);
        }
        out.writeOptionalString(parentIid);
        out.writeOptionalInt(traceNumber);
    }

    @Override
    public ActionRequestValidationException validate() {
        ActionRequestValidationException exception = null;
        if (this.conversationId == null) {
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
        String cid = request.param(ActionConstants.MEMORY_ID);
        XContentParser parser = request.contentParser();
        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.nextToken(), parser);

        String input = null;
        String prompt = null;
        String response = null;
        String origin = null;
        Map<String, String> addinf = new HashMap<>();
        String parintid = null;
        Integer tracenum = null;

        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.currentToken(), parser);
        while (parser.nextToken() != XContentParser.Token.END_OBJECT) {
            String fieldName = parser.currentName();
            parser.nextToken();

            switch (fieldName) {
                case ActionConstants.INPUT_FIELD:
                    input = parser.text();
                    break;
                case ActionConstants.PROMPT_TEMPLATE_FIELD:
                    prompt = parser.text();
                    break;
                case ActionConstants.AI_RESPONSE_FIELD:
                    response = parser.text();
                    break;
                case ActionConstants.RESPONSE_ORIGIN_FIELD:
                    origin = parser.text();
                    break;
                case ActionConstants.ADDITIONAL_INFO_FIELD:
                    addinf = parser.mapStrings();
                    break;
                case ActionConstants.PARENT_INTERACTION_ID_FIELD:
                    parintid = parser.text();
                    break;
                case ActionConstants.TRACE_NUMBER_FIELD:
                    tracenum = parser.intValue(false);
                    break;
                default:
                    throw new IllegalArgumentException("Invalid field [" + fieldName + "] found in request body");
            }
        }

        boolean allFieldsEmpty = (input == null || input.trim().isEmpty())
            && (prompt == null || prompt.trim().isEmpty())
            && (response == null || response.trim().isEmpty())
            && (origin == null || origin.trim().isEmpty())
            && (addinf == null || addinf.isEmpty());
        if (allFieldsEmpty) {
            throw new IllegalArgumentException(
                "At least one of the following parameters must be non-empty: " + "input, prompt_template, response, origin, additional_info"
            );
        }

        return new CreateInteractionRequest(cid, input, prompt, response, origin, addinf, parintid, tracenum);
    }

}
