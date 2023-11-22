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
    private Instant createTime;
    @Getter
    private String conversationId;
    @Getter
    private String input;
    @Getter
    private String promptTemplate;
    @Getter
    private String response;
    @Getter
    private String origin;
    @Getter
    private String additionalInfo;

    /**
     * Creates an Interaction object from a map of fields in the OS index
     * @param id the Interaction id
     * @param fields the field mapping from the OS document
     * @return a new Interaction object representing the OS document
     */
    public static Interaction fromMap(String id, Map<String, Object> fields) {
        Instant createTime = Instant.parse((String) fields.get(ConversationalIndexConstants.INTERACTIONS_CREATE_TIME_FIELD));
        String conversationId = (String) fields.get(ConversationalIndexConstants.INTERACTIONS_CONVERSATION_ID_FIELD);
        String input = (String) fields.get(ConversationalIndexConstants.INTERACTIONS_INPUT_FIELD);
        String promptTemplate = (String) fields.get(ConversationalIndexConstants.INTERACTIONS_PROMPT_TEMPLATE_FIELD);
        String response = (String) fields.get(ConversationalIndexConstants.INTERACTIONS_RESPONSE_FIELD);
        String origin = (String) fields.get(ConversationalIndexConstants.INTERACTIONS_ORIGIN_FIELD);
        String additionalInfo = (String) fields.get(ConversationalIndexConstants.INTERACTIONS_ADDITIONAL_INFO_FIELD);
        return new Interaction(id, createTime, conversationId, input, promptTemplate, response, origin, additionalInfo);
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
        Instant createTime = in.readInstant();
        String conversationId = in.readString();
        String input = in.readString();
        String promptTemplate = in.readString();
        String response = in.readString();
        String origin = in.readString();
        String additionalInfo = in.readOptionalString();
        return new Interaction(id, createTime, conversationId, input, promptTemplate, response, origin, additionalInfo);
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeString(id);
        out.writeInstant(createTime);
        out.writeString(conversationId);
        out.writeString(input);
        out.writeString(promptTemplate);
        out.writeString(response);
        out.writeString(origin);
        out.writeOptionalString(additionalInfo);
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, ToXContentObject.Params params) throws IOException {
        builder.startObject();
        builder.field(ActionConstants.CONVERSATION_ID_FIELD, conversationId);
        builder.field(ActionConstants.RESPONSE_INTERACTION_ID_FIELD, id);
        builder.field(ConversationalIndexConstants.INTERACTIONS_CREATE_TIME_FIELD, createTime);
        builder.field(ConversationalIndexConstants.INTERACTIONS_INPUT_FIELD, input);
        builder.field(ConversationalIndexConstants.INTERACTIONS_PROMPT_TEMPLATE_FIELD, promptTemplate);
        builder.field(ConversationalIndexConstants.INTERACTIONS_RESPONSE_FIELD, response);
        builder.field(ConversationalIndexConstants.INTERACTIONS_ORIGIN_FIELD, origin);
        if (additionalInfo != null) {
            builder.field(ConversationalIndexConstants.INTERACTIONS_ADDITIONAL_INFO_FIELD, additionalInfo);
        }
        builder.endObject();
        return builder;
    }

    @Override
    public boolean equals(Object other) {
        return (other instanceof Interaction
            && ((Interaction) other).id.equals(this.id)
            && ((Interaction) other).conversationId.equals(this.conversationId)
            && ((Interaction) other).createTime.equals(this.createTime)
            && ((Interaction) other).input.equals(this.input)
            && ((Interaction) other).promptTemplate.equals(this.promptTemplate)
            && ((Interaction) other).response.equals(this.response)
            && ((Interaction) other).origin.equals(this.origin)
            && ((((Interaction) other).additionalInfo == null && this.additionalInfo == null)
                || ((Interaction) other).additionalInfo.equals(this.additionalInfo)));
    }

    @Override
    public String toString() {
        return "Interaction{"
            + "id="
            + id
            + ",cid="
            + conversationId
            + ",create_time="
            + createTime
            + ",origin="
            + origin
            + ",input="
            + input
            + ",promt_template="
            + promptTemplate
            + ",response="
            + response
            + ",additional_info="
            + additionalInfo
            + "}";
    }

}
