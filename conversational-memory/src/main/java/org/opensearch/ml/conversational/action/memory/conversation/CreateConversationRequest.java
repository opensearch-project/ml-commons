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
import java.util.Map;

import org.opensearch.action.ActionRequest;
import org.opensearch.action.ActionRequestValidationException;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.ml.common.conversational.ActionConstants;
import org.opensearch.rest.RestRequest;

import lombok.Getter;

/**
 * Action Request for creating a conversation
 */
public class CreateConversationRequest extends ActionRequest {
    @Getter
    private String name = null;

    /**
     * Constructor
     * @param in input stream to read from
     * @throws IOException if something breaks
     */
    public CreateConversationRequest(StreamInput in) throws IOException {
        super(in);
        this.name = in.readOptionalString();
    }

    /**
     * Constructor
     * @param name name of the conversation
     */
    public CreateConversationRequest(String name) {
        super();
        this.name = name;
    }

    /**
     * Constructor
     * name will be null
     */
    public CreateConversationRequest() {}

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        out.writeOptionalString(name);
    }

    @Override
    public ActionRequestValidationException validate() {
        ActionRequestValidationException exception = null;

        return exception;
    }

    /**
     * Creates a CreateConversationRequest from a RestRequest
     * @param restRequest a RestRequest for a CreateConversation
     * @return a new CreateConversationRequest
     * @throws IOException if something breaks
     */
    public static CreateConversationRequest fromRestRequest(RestRequest restRequest) throws IOException {
        if (!restRequest.hasContent()) {
            return new CreateConversationRequest();
        }
        Map<String, String> body = restRequest.contentParser().mapStrings();
        if (body.containsKey(ActionConstants.REQUEST_CONVERSATION_NAME_FIELD)) {
            return new CreateConversationRequest(body.get(ActionConstants.REQUEST_CONVERSATION_NAME_FIELD));
        } else {
            return new CreateConversationRequest();
        }
    }

}
