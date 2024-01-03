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

import org.opensearch.core.action.ActionResponse;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.ml.common.conversation.ConversationMeta;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * ActionResponse object for GetConversation (singular)
 */
@AllArgsConstructor
public class GetConversationResponse extends ActionResponse implements ToXContentObject {

    @Getter
    private ConversationMeta conversation;

    /**
     * Stream Constructor
     * @param in input stream to read this from
     * @throws IOException if soething goes wrong in reading
     */
    public GetConversationResponse(StreamInput in) throws IOException {
        super(in);
        this.conversation = ConversationMeta.fromStream(in);
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        this.conversation.writeTo(out);
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        return this.conversation.toXContent(builder, params);
    }
}
