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
package org.opensearch.ml.common.conversation;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

import org.opensearch.action.index.IndexRequest;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.common.io.stream.Writeable;
import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.search.SearchHit;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Class for holding conversational metadata
 */
@AllArgsConstructor
public class ConversationMeta implements Writeable, ToXContentObject {

    @Getter
    private String id;
    @Getter
    private Instant createdTime;
    @Getter
    private String name;
    @Getter
    private String user;

    /**
     * Creates a conversationMeta object from a SearchHit object
     * @param hit the search hit to transform into a conversationMeta object
     * @return a new conversationMeta object representing the search hit
     */
    public static ConversationMeta fromSearchHit(SearchHit hit) {
        String id = hit.getId();
        return ConversationMeta.fromMap(id, hit.getSourceAsMap());
    }

    /**
     * Creates a conversationMeta object from a Map of fields in the OS index
     * @param id the conversation's id
     * @param docFields the map of source fields
     * @return a new conversationMeta object representing the map
     */
    public static ConversationMeta fromMap(String id, Map<String, Object> docFields) {
        Instant created = Instant.parse((String) docFields.get(ConversationalIndexConstants.META_CREATED_FIELD));
        String name = (String) docFields.get(ConversationalIndexConstants.META_NAME_FIELD);
        String user = (String) docFields.get(ConversationalIndexConstants.USER_FIELD);
        return new ConversationMeta(id, created, name, user);
    }

    /**
     * Creates a conversationMeta from a stream, given the stream was written to by 
     * conversationMeta.writeTo
     * @param in stream to read from
     * @return new conversationMeta object
     * @throws IOException if you're reading from a stream without a conversationMeta in it
     */
    public static ConversationMeta fromStream(StreamInput in) throws IOException {
        String id = in.readString();
        Instant created = in.readInstant();
        String name = in.readString();
        String user = in.readOptionalString();
        return new ConversationMeta(id, created, name, user);
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeString(id);
        out.writeInstant(createdTime);
        out.writeString(name);
        out.writeOptionalString(user);
    }

    /**
     * Convert this conversationMeta object into an IndexRequest so it can be indexed
     * @param index the index to send this conversation to. Should usually be .conversational-meta
     * @return the IndexRequest for the client to send
     */
    public IndexRequest toIndexRequest(String index) {
        IndexRequest request = new IndexRequest(index);
        return request
            .id(this.id)
            .source(
                ConversationalIndexConstants.META_CREATED_FIELD,
                this.createdTime,
                ConversationalIndexConstants.META_NAME_FIELD,
                this.name
            );
    }

    @Override
    public String toString() {
        return "{id=" + id + ", name=" + name + ", created=" + createdTime.toString() + ", user=" + user + "}";
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, ToXContentObject.Params params) throws IOException {
        builder.startObject();
        builder.field(ActionConstants.CONVERSATION_ID_FIELD, this.id);
        builder.field(ConversationalIndexConstants.META_CREATED_FIELD, this.createdTime);
        builder.field(ConversationalIndexConstants.META_NAME_FIELD, this.name);
        if (this.user != null) {
            builder.field(ConversationalIndexConstants.USER_FIELD, this.user);
        }
        builder.endObject();
        return builder;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof ConversationMeta)) {
            return false;
        }
        ConversationMeta otherConversation = (ConversationMeta) other;
        return Objects.equals(this.id, otherConversation.id)
            && Objects.equals(this.user, otherConversation.user)
            && Objects.equals(this.createdTime, otherConversation.createdTime)
            && Objects.equals(this.name, otherConversation.name);
    }

}
