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

import static org.opensearch.ml.common.conversation.ConversationalIndexConstants.APPLICATION_TYPE_FIELD;
import static org.opensearch.ml.common.conversation.ConversationalIndexConstants.META_ADDITIONAL_INFO_FIELD;

import java.io.IOException;
import java.util.Map;

import org.opensearch.OpenSearchParseException;
import org.opensearch.Version;
import org.opensearch.action.ActionRequest;
import org.opensearch.action.ActionRequestValidationException;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.CommonValue;
import org.opensearch.ml.common.conversation.ActionConstants;
import org.opensearch.rest.RestRequest;

import lombok.Getter;

/**
 * Action Request for creating a conversation
 */
public class CreateConversationRequest extends ActionRequest {
    public static final Version MINIMAL_SUPPORTED_VERSION_FOR_ADDITIONAL_INFO = CommonValue.VERSION_2_17_0;

    @Getter
    private String name = null;
    @Getter
    private String applicationType = null;
    @Getter
    private Map<String, String> additionalInfos = null;

    /**
     * Constructor
     * @param in input stream to read from
     * @throws IOException if something breaks
     */
    public CreateConversationRequest(StreamInput in) throws IOException {
        super(in);
        this.name = in.readOptionalString();
        this.applicationType = in.readOptionalString();
        if (in.getVersion().onOrAfter(MINIMAL_SUPPORTED_VERSION_FOR_ADDITIONAL_INFO)) {
            if (in.readBoolean()) {
                this.additionalInfos = in.readMap(StreamInput::readString, StreamInput::readString);
            }
        }
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
     * @param name name of the conversation
     */
    public CreateConversationRequest(String name, String applicationType) {
        super();
        this.name = name;
        this.applicationType = applicationType;
    }

    /**
     * Constructor
     * @param name name of the conversation
     * @param applicationType of the conversation
     * @param additionalInfos information of the conversation
     */
    public CreateConversationRequest(String name, String applicationType, Map<String, String> additionalInfos) {
        super();
        this.name = name;
        this.applicationType = applicationType;
        this.additionalInfos = additionalInfos;
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
        out.writeOptionalString(applicationType);
        if (out.getVersion().onOrAfter(MINIMAL_SUPPORTED_VERSION_FOR_ADDITIONAL_INFO)) {
            if (additionalInfos == null) {
                out.writeBoolean(false);
            } else {
                out.writeBoolean(true);
                out.writeMap(additionalInfos, StreamOutput::writeString, StreamOutput::writeString);
            }
        }
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
        try (XContentParser parser = restRequest.contentParser()) {
            Map<String, Object> body = parser.map();
            String name = null;
            String applicationType = null;
            Map<String, String> additionalInfo = null;

            for (String key : body.keySet()) {
                switch (key) {
                    case ActionConstants.REQUEST_CONVERSATION_NAME_FIELD:
                        name = (String) body.get(ActionConstants.REQUEST_CONVERSATION_NAME_FIELD);
                        break;
                    case APPLICATION_TYPE_FIELD:
                        applicationType = (String) body.get(APPLICATION_TYPE_FIELD);
                        break;
                    case META_ADDITIONAL_INFO_FIELD:
                        additionalInfo = (Map<String, String>) body.get(META_ADDITIONAL_INFO_FIELD);
                        break;
                    default:
                        throw new IllegalArgumentException("Invalid field [" + key + "] found in request body");
                }
            }
            if (body.get(ActionConstants.REQUEST_CONVERSATION_NAME_FIELD) != null) {
                return new CreateConversationRequest(name, applicationType, additionalInfo);
            } else {
                return new CreateConversationRequest();
            }
        } catch (Exception exception) {
            throw new OpenSearchParseException(exception.getMessage());
        }
    }
}
