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

import org.opensearch.action.ActionResponse;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.ml.conversational.action.ActionConstants;
import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.core.xcontent.XContentBuilder;

/**
 * Action Response for create interaction
 */
public class CreateInteractionResponse extends ActionResponse implements ToXContentObject {
    private String interactionId;

    /**
     * Convtructor
     * @param in input stream to create this from
     * @throws IOException if something breaks
     */
    public CreateInteractionResponse(StreamInput in) throws IOException {
        super(in);
        this.interactionId = in.readString();
    }

    /**
     * Constructor
     * @param interactionId id of the newly created interaction
     */
    public CreateInteractionResponse(String interactionId) {
        this.interactionId = interactionId;
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeString(this.interactionId);
    }

    /**
     * @return the id of the newly created interaction
     */
    public String getId() {
        return this.interactionId;
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, ToXContentObject.Params params) throws IOException {
        builder.startObject();
        builder.field(ActionConstants.RESPONSE_INTER_ID_FIELD, this.interactionId);
        builder.endObject();
        return builder;
    }
}