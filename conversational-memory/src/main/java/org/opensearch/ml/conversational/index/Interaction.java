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
package org.opensearch.ml.conversational.index;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;

import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.common.io.stream.Writeable;
import org.opensearch.ml.conversational.action.ActionConstants;
import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.search.SearchHit;

import lombok.Builder;

/**
 * Class for dealing with Interactions
 */
@Builder
public class Interaction implements Writeable, ToXContentObject {

    private String id;
    private Instant timestamp;
    private String conversationId;
    private String input;
    private String prompt;
    private String response;
    private String agent;
    private String metadata;

    /**
     * Constructor
     * @param id id of THIS interaction object in the index
     * @param timestamp when this interaction happened
     * @param conversationId id of the conversation this interaction belongs to
     * @param input human input in this interaction
     * @param prompt prompt template used in this interaction
     * @param response GenAI response from this interaction
     * @param agent name of the agent used in this interaction
     * @param metadata arbitrary metadata
     */
    public Interaction(
        String id,
        Instant timestamp,
        String conversationId,
        String input,
        String prompt,
        String response,
        String agent,
        String metadata
    ) {
        this.id = id;
        this.timestamp = timestamp;
        this.conversationId = conversationId;
        this.input = input;
        this.prompt = prompt;
        this.response = response;
        this.agent = agent;
        this.metadata = metadata;
    }

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
        String prompt    = (String) fields.get(ConversationalIndexConstants.INTERACTIONS_PROMPT_FIELD);
        String response  = (String) fields.get(ConversationalIndexConstants.INTERACTIONS_RESPONSE_FIELD);
        String agent     = (String) fields.get(ConversationalIndexConstants.INTERACTIONS_AGENT_FIELD);
        String metadata  = (String) fields.get(ConversationalIndexConstants.INTERACTIONS_METADATA_FIELD);
        return new Interaction(id, timestamp, conversationId, input, prompt, response, agent, metadata);
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
     * Creates a new Interaction onject from a stream
     * @param in stream to read from; assumes Interactions.writeTo was called on it
     * @return a new Interaction
     * @throws IOException if can't read or the stream isn't pointing to an intraction or something
     */
    public static Interaction fromStream(StreamInput in) throws IOException {
        String id = in.readString();
        Instant timestamp = in.readInstant();
        String conversationId = in.readString();
        String input = in.readString();
        String prompt = in.readString();
        String response = in.readString();
        String agent = in.readString();
        String metadata = in.readOptionalString();
        return new Interaction(id, timestamp, conversationId, input, prompt, response, agent, metadata);
    }


    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeString(id);
        out.writeInstant(timestamp);
        out.writeString(conversationId);
        out.writeString(input);
        out.writeString(prompt);
        out.writeString(response);
        out.writeString(agent);
        out.writeString(metadata);
    }

    /**
     * @return this interaction's unique ID
     */
    public String getId() {
        return id;
    }
    /**
     * @return this interaction's timestamp
     */
    public Instant getTimestamp() {
        return timestamp;
    }
    /**
     * @return the id of the conversation this interaction belongs to
     */
    public String getConversationId() {
        return conversationId;
    }
    /**
     * @return the humna input from this interaction
     */
    public String getInput() {
        return input;
    }
    /**
     * @return the prompt template used in this interaction
     */
    public String getPrompt() {
        return prompt;
    }
    /**
     * @return the GenAI response from this interaction
     */
    public String getResponse() {
        return response;
    }
    /**
     * @return the name of the agent used in this interaction
     */
    public String getAgent() {
        return agent;
    }
    /**
     * @return an arbitrary JSON blob stored as part of this interaction
     */
    public String getMetadata() {
        return metadata;
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, ToXContentObject.Params params) throws IOException {
        builder.startObject();
        builder.field(ActionConstants.CONVERSATION_ID_FIELD, conversationId);
        builder.field(ActionConstants.RESPONSE_INTER_ID_FIELD, id);
        builder.field(ConversationalIndexConstants.INTERACTIONS_TIMESTAMP_FIELD, timestamp);
        builder.field(ConversationalIndexConstants.INTERACTIONS_INPUT_FIELD, input);
        builder.field(ConversationalIndexConstants.INTERACTIONS_PROMPT_FIELD, prompt);
        builder.field(ConversationalIndexConstants.INTERACTIONS_RESPONSE_FIELD, response);
        builder.field(ConversationalIndexConstants.INTERACTIONS_AGENT_FIELD, agent);
        builder.field(ActionConstants.INTER_ATTRIBUTES_FIELD, metadata);
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
            ((Interaction) other).prompt.equals(this.prompt) &&
            ((Interaction) other).response.equals(this.response) &&
            ((Interaction) other).agent.equals(this.agent) && 
            ((Interaction) other).metadata.equals(this.metadata)
        );
    }

    @Override
    public String toString() {
        return "Interaction{"
            + "id=" + id
            + ",cid=" + conversationId
            + ",timestamp=" + timestamp
            + ",agent=" + agent
            + "}";
    }
    

}