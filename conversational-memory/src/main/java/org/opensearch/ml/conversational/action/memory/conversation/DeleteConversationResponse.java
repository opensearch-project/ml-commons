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
import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.ml.common.conversational.ActionConstants;

/**
 * Action Response for Delete Conversation Action
 */
public class DeleteConversationResponse extends ActionResponse implements ToXContentObject {
    private boolean success;

    /**
     * Constructor
     * @param in stream input. Assumes there was one of these written to the stream
     * @throws IOException if something breaks
     */
    public DeleteConversationResponse(StreamInput in) throws IOException {
        super(in);
        success = in.readBoolean();
    }

    /**
     * Constructor
     * @param success whether the deletion was successful
     */
    public DeleteConversationResponse(boolean success) {
        super();
        this.success = success;
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeBoolean(success);
    }

    /**
     * Gets whether this delete action succeeded
     * @return whether this deletion was successful
     */
    public boolean wasSuccessful() {
        return success;
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, ToXContentObject.Params params) throws IOException {
        builder.startObject();
        builder.field(ActionConstants.SUCCESS_FIELD, success);
        builder.endObject();
        return builder;
    }


}