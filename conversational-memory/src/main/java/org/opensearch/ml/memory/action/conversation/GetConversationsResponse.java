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

import java.io.IOException;
import java.util.List;

import org.opensearch.core.action.ActionResponse;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.ml.common.conversation.ActionConstants;
import org.opensearch.ml.common.conversation.ConversationMeta;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Action Response for CreateConversation
 */
@AllArgsConstructor
public class GetConversationsResponse extends ActionResponse implements ToXContentObject {
    @Getter
    private List<ConversationMeta> conversations;
    @Getter
    private int nextToken;
    private boolean hasMoreTokens;

    /**
     * Constructor
     * @param in input stream to create this from
     * @throws IOException if something breaks
     */
    public GetConversationsResponse(StreamInput in) throws IOException {
        super(in);
        this.conversations = in.readList(ConversationMeta::fromStream);
        this.nextToken = in.readInt();
        this.hasMoreTokens = in.readBoolean();
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeList(conversations);
        out.writeInt(nextToken);
        out.writeBoolean(hasMoreTokens);
    }

    /**
     * are there more pages of results in this search
     * @return whether there are more pages of results in this search
     */
    public boolean hasMorePages() {
        return hasMoreTokens;
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, ToXContent.Params params) throws IOException {
        builder.startObject();
        builder.startArray(ActionConstants.RESPONSE_CONVERSATION_LIST_FIELD);
        for (ConversationMeta conversation : conversations) {
            conversation.toXContent(builder, params);
        }
        builder.endArray();
        if (hasMoreTokens) {
            builder.field(ActionConstants.NEXT_TOKEN_FIELD, nextToken);
        }
        builder.endObject();
        return builder;
    }

}
