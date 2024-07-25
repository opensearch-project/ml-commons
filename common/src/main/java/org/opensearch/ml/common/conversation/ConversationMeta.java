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

import org.opensearch.Version;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.common.io.stream.Writeable;
import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.ml.common.CommonValue;
import org.opensearch.search.SearchHit;

import lombok.AllArgsConstructor;
import lombok.Getter;

import static org.opensearch.ml.common.CommonValue.VERSION_2_17_0;

/**
 * Class for holding conversational metadata
 */
@AllArgsConstructor
public class ConversationMeta implements Writeable, ToXContentObject {
    public static final Version MINIMAL_SUPPORTED_VERSION_FOR_ADDITIONAL_INFO = CommonValue.VERSION_2_17_0;
    @Getter
    private String id;
    @Getter
    private Instant createdTime;
    @Getter
    private Instant updatedTime;
    @Getter
    private String name;
    @Getter
    private String user;
    @Getter
    private Map<String, String> additionalInfos;

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
        Instant created = Instant.parse((String) docFields.get(ConversationalIndexConstants.META_CREATED_TIME_FIELD));
        Instant updated = Instant.parse((String) docFields.get(ConversationalIndexConstants.META_UPDATED_TIME_FIELD));
        String name = (String) docFields.get(ConversationalIndexConstants.META_NAME_FIELD);
        String user = (String) docFields.get(ConversationalIndexConstants.USER_FIELD);
        Map<String, String> additionalInfos = (Map<String, String>)docFields.get(ConversationalIndexConstants.META_ADDITIONAL_INFO_FIELD);
        return new ConversationMeta(id, created, updated, name, user, additionalInfos);
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
        Instant updated = in.readInstant();
        String name = in.readString();
        String user = in.readOptionalString();
        Map<String, String> additionalInfos = null;
        if (in.getVersion().onOrAfter(MINIMAL_SUPPORTED_VERSION_FOR_ADDITIONAL_INFO)) {
            if (in.readBoolean()) {
                additionalInfos = in.readMap(StreamInput::readString, StreamInput::readString);
            }
        }
        return new ConversationMeta(id, created, updated, name, user, additionalInfos);
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeString(id);
        out.writeInstant(createdTime);
        out.writeInstant(updatedTime);
        out.writeString(name);
        out.writeOptionalString(user);
        if(out.getVersion().onOrAfter(MINIMAL_SUPPORTED_VERSION_FOR_ADDITIONAL_INFO)) {
            if (additionalInfos == null) {
                out.writeBoolean(false);
            } else {
                out.writeBoolean(true);
                out.writeMap(additionalInfos, StreamOutput::writeString, StreamOutput::writeString);
            }
        }
    }

    @Override
    public String toString() {
        return "{id=" + id
            + ", name=" + name
            + ", created=" + createdTime.toString()
            + ", updated=" + updatedTime.toString()
            + ", user=" + user
            + "}";
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, ToXContentObject.Params params) throws IOException {
        builder.startObject();
        builder.field(ActionConstants.CONVERSATION_ID_FIELD, this.id);
        builder.field(ConversationalIndexConstants.META_CREATED_TIME_FIELD, this.createdTime);
        builder.field(ConversationalIndexConstants.META_UPDATED_TIME_FIELD, this.updatedTime);
        builder.field(ConversationalIndexConstants.META_NAME_FIELD, this.name);
        if(this.user != null) {
            builder.field(ConversationalIndexConstants.USER_FIELD, this.user);
        }
        if (this.additionalInfos != null) {
            builder.field(ConversationalIndexConstants.META_ADDITIONAL_INFO_FIELD, this.additionalInfos);
        }
        builder.endObject();
        return builder;
    }

    @Override
    public boolean equals(Object other) {
        if(!(other instanceof ConversationMeta)) {
            return false;
        }
        ConversationMeta otherConversation = (ConversationMeta) other;
        return Objects.equals(this.id, otherConversation.id) &&
            Objects.equals(this.user, otherConversation.user) &&
            Objects.equals(this.createdTime, otherConversation.createdTime) &&
            Objects.equals(this.updatedTime, otherConversation.updatedTime) &&
            Objects.equals(this.name, otherConversation.name);
    }
    
}
