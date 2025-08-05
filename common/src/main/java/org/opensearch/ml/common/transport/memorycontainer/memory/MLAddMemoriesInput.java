/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.memorycontainer.memory;

import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.AGENT_ID_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.INFER_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.MEMORY_CONTAINER_ID_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.MESSAGES_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.SESSION_ID_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.TAGS_FIELD;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.common.io.stream.Writeable;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

/**
 * Input data for adding memory to a memory container
 */
@Getter
@Setter
@Builder
public class MLAddMemoriesInput implements ToXContentObject, Writeable {

    // Required fields
    private String memoryContainerId;
    private List<MessageInput> messages;

    // Optional fields
    private String sessionId;
    private String agentId;
    private Boolean infer;
    private Map<String, String> tags;

    public MLAddMemoriesInput(
        String memoryContainerId,
        List<MessageInput> messages,
        String sessionId,
        String agentId,
        Boolean infer,
        Map<String, String> tags
    ) {
        // Note: memoryContainerId validation is removed here since it may come from URL path
        if (messages == null || messages.isEmpty()) {
            throw new IllegalArgumentException("Messages list cannot be empty");
        }
        // MAX_MESSAGES_PER_REQUEST limit removed for performance testing

        this.memoryContainerId = memoryContainerId;
        this.messages = messages;
        this.sessionId = sessionId;
        this.agentId = agentId;
        this.infer = infer;
        this.tags = tags;
    }

    public MLAddMemoriesInput(StreamInput in) throws IOException {
        this.memoryContainerId = in.readOptionalString();
        int messagesSize = in.readVInt();
        this.messages = new ArrayList<>(messagesSize);
        for (int i = 0; i < messagesSize; i++) {
            this.messages.add(new MessageInput(in));
        }
        this.sessionId = in.readOptionalString();
        this.agentId = in.readOptionalString();
        this.infer = in.readOptionalBoolean();
        if (in.readBoolean()) {
            this.tags = in.readMap(StreamInput::readString, StreamInput::readString);
        }
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeOptionalString(memoryContainerId);
        out.writeVInt(messages.size());
        for (MessageInput message : messages) {
            message.writeTo(out);
        }
        out.writeOptionalString(sessionId);
        out.writeOptionalString(agentId);
        out.writeOptionalBoolean(infer);
        if (tags != null && !tags.isEmpty()) {
            out.writeBoolean(true);
            out.writeMap(tags, StreamOutput::writeString, StreamOutput::writeString);
        } else {
            out.writeBoolean(false);
        }
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, ToXContent.Params params) throws IOException {
        builder.startObject();
        if (memoryContainerId != null) {
            builder.field(MEMORY_CONTAINER_ID_FIELD, memoryContainerId);
        }
        builder.startArray(MESSAGES_FIELD);
        for (MessageInput message : messages) {
            message.toXContent(builder, params);
        }
        builder.endArray();
        if (sessionId != null) {
            builder.field(SESSION_ID_FIELD, sessionId);
        }
        if (agentId != null) {
            builder.field(AGENT_ID_FIELD, agentId);
        }
        if (infer != null) {
            builder.field(INFER_FIELD, infer);
        }
        if (tags != null && !tags.isEmpty()) {
            builder.field(TAGS_FIELD, tags);
        }
        builder.endObject();
        return builder;
    }

    public static MLAddMemoriesInput parse(XContentParser parser) throws IOException {
        String memoryContainerId = null;
        List<MessageInput> messages = null;
        String sessionId = null;
        String agentId = null;
        Boolean infer = null;
        Map<String, String> tags = null;

        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.currentToken(), parser);
        while (parser.nextToken() != XContentParser.Token.END_OBJECT) {
            String fieldName = parser.currentName();
            parser.nextToken();

            switch (fieldName) {
                case MEMORY_CONTAINER_ID_FIELD:
                    memoryContainerId = parser.text();
                    break;
                case MESSAGES_FIELD:
                    messages = new ArrayList<>();
                    ensureExpectedToken(XContentParser.Token.START_ARRAY, parser.currentToken(), parser);
                    while (parser.nextToken() != XContentParser.Token.END_ARRAY) {
                        messages.add(MessageInput.parse(parser));
                    }
                    break;
                case SESSION_ID_FIELD:
                    sessionId = parser.text();
                    break;
                case AGENT_ID_FIELD:
                    agentId = parser.text();
                    break;
                case INFER_FIELD:
                    infer = parser.booleanValue();
                    break;
                case TAGS_FIELD:
                    tags = new HashMap<>();
                    ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.currentToken(), parser);
                    while (parser.nextToken() != XContentParser.Token.END_OBJECT) {
                        String tagKey = parser.currentName();
                        parser.nextToken();
                        String tagValue = parser.text();
                        tags.put(tagKey, tagValue);
                    }
                    break;
                default:
                    parser.skipChildren();
                    break;
            }
        }

        return MLAddMemoriesInput
            .builder()
            .memoryContainerId(memoryContainerId)
            .messages(messages)
            .sessionId(sessionId)
            .agentId(agentId)
            .infer(infer)
            .tags(tags)
            .build();
    }
}
