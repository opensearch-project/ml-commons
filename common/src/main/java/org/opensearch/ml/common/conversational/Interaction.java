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
package org.opensearch.ml.common.conversational;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;

import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.common.io.stream.Writeable;
import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.search.SearchHit;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * Class for dealing with Interactions
 */
@Builder
@AllArgsConstructor
public class Interaction implements Writeable, ToXContentObject {

    @Getter
    private String id;
    @Getter
    private Instant timestamp;
    @Getter
    private String conversationId;
    @Getter
    private String input;
    @Getter
    private String response;
    @Getter
    private String origin;

    /**
     * Creates an Interaction object from a map of fields in the OS index
     * @param id the Interaction id
     * @param fields the field mapping from the OS document
     * @return a new Interaction object representing the OS document
     */
    public static Interaction fromMap(String id, Map<String, Object> fields) {
        Instant timestamp = Instant.parse((String) fields.get(ConversationalIndexConstants.INTERACTIONS_TIMESTAMP_FIELD));
        String conversationId   = (String) fields.get(ConversationalIndexConstants.INTERACTIONS_CONVERSATION_ID_FIELD);
        String input     = (String) fields.get(ConversationalIndexConstants.INTERACTIONS_INPUT_FIELD);
        String response  = (String) fields.get(ConversationalIndexConstants.INTERACTIONS_RESPONSE_FIELD);
        String agent     = (String) fields.get(ConversationalIndexConstants.INTERACTIONS_ORIGIN_FIELD);
        return new Interaction(id, timestamp, conversationId, input, response, agent);
    }

    /**
     * Creates an Interaction object from a search hit
     * @param hit the search hit from the interactions index
     * @return a new Interaction object representing the search hit
     */
    public static Interaction fromSearchHit(SearchHit hit) {
        String id = hit.getId();
        return fromMap(id, hit.getSourceAsMap());
    }

    /**
     * Creates a new Interaction object from a stream
     * @param in stream to read from; assumes Interactions.writeTo was called on it
     * @return a new Interaction
     * @throws IOException if can't read or the stream isn't pointing to an intraction or something
     */
    public static Interaction fromStream(StreamInput in) throws IOException {
        String id = in.readString();
        Instant timestamp = in.readInstant();
        String conversationId = in.readString();
        String input = in.readString();
        String response = in.readString();
        String origin = in.readString();
        return new Interaction(id, timestamp, conversationId, input, response, origin);
    }


    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeString(id);
        out.writeInstant(timestamp);
        out.writeString(conversationId);
        out.writeString(input);
        out.writeString(response);
        out.writeString(origin);
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, ToXContentObject.Params params) throws IOException {
        builder.startObject();
        builder.field(ActionConstants.CONVERSATION_ID_FIELD, conversationId);
        builder.field(ActionConstants.RESPONSE_INTERACTION_ID_FIELD, id);
        builder.field(ConversationalIndexConstants.INTERACTIONS_TIMESTAMP_FIELD, timestamp);
        builder.field(ConversationalIndexConstants.INTERACTIONS_INPUT_FIELD, input);
        builder.field(ConversationalIndexConstants.INTERACTIONS_RESPONSE_FIELD, response);
        builder.field(ConversationalIndexConstants.INTERACTIONS_ORIGIN_FIELD, origin);
        builder.endObject();
        return builder;
    }

    @Override
    public boolean equals(Object other) {
        return (
            other instanceof Interaction &&
            ((Interaction) other).id.equals(this.id) &&
            ((Interaction) other).conversationId.equals(this.conversationId) &&
            ((Interaction) other).timestamp.equals(this.timestamp) &&
            ((Interaction) other).input.equals(this.input) &&
            ((Interaction) other).response.equals(this.response) &&
            ((Interaction) other).origin.equals(this.origin)
        );
    }

    @Override
    public String toString() {
        return "Interaction{"
            + "id=" + id
            + ",cid=" + conversationId
            + ",timestamp=" + timestamp
            + ",origin=" + origin
            + ",input=" + input
            + ",response=" + response
            + "}";
    }
    

}