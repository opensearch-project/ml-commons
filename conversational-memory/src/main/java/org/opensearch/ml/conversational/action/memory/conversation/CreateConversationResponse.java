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

import org.opensearch.action.ActionResponse;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.ml.common.conversational.ActionConstants;

/**
 * Action Response for CreateConversation
 */
public class CreateConversationResponse extends ActionResponse implements ToXContentObject {

    String conversationId;

    /**
     * Convtructor
     * @param in input stream to create this from
     * @throws IOException if something breaks
     */
    public CreateConversationResponse(StreamInput in) throws IOException {
        super(in);
        this.conversationId = in.readString();
    }

    /**
     * Constructor
     * @param conversationId unique id of the newly-created conversation
     */
    public CreateConversationResponse(String conversationId) {
        super();
        this.conversationId = conversationId;
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeString(this.conversationId);
    }

    /**
     * @return the unique id of the newly created conversation
     */
    public String getId() {
        return conversationId;
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, ToXContent.Params params) throws IOException {
        builder.startObject();
        builder.field(ActionConstants.CONVERSATION_ID_FIELD, this.conversationId);
        builder.endObject();
        return builder;
    }

}