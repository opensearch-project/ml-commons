/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.memorycontainer;

import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.AGENT_ID_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.BINARY_DATA_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.CREATED_TIME_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.INFER_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.LAST_UPDATED_TIME_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.MEMORY_CONTAINER_ID_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.MEMORY_TYPE_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.MESSAGES_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.METADATA_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.NAMESPACE_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.OWNER_ID_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.SESSION_ID_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.STRUCTURED_DATA_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.TAGS_FIELD;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.common.io.stream.Writeable;
import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.transport.memorycontainer.memory.MessageInput;
import org.opensearch.ml.common.utils.StringUtils;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

/**
 * Input data for adding memory to a memory container
 */
@Getter
@Setter
@Builder
public class MLWorkingMemory implements ToXContentObject, Writeable {

    // Required fields
    private String memoryContainerId;
    private ShortTermMemoryType memoryType;
    private List<MessageInput> messages;
    private String binaryData;
    private Map<String, Object> structuredData;

    // Optional fields
    private Map<String, String> namespace;
    private boolean infer;
    private Map<String, String> metadata;
    private Map<String, String> tags;
    private Instant createdTime;
    private Instant lastUpdateTime;
    private String ownerId;

    public MLWorkingMemory(
        String memoryContainerId,
        ShortTermMemoryType memoryType,
        List<MessageInput> messages,
        String binaryData,
        Map<String, Object> structuredData,
        Map<String, String> namespace,
        boolean infer,
        Map<String, String> metadata,
        Map<String, String> tags,
        Instant createdTime,
        Instant lastUpdateTime,
        String ownerId
    ) {
        // MAX_MESSAGES_PER_REQUEST limit removed for performance testing

        this.memoryContainerId = memoryContainerId;
        this.memoryType = memoryType == null ? ShortTermMemoryType.CONVERSATION : memoryType;
        this.messages = messages;
        this.binaryData = binaryData;
        this.structuredData = structuredData;
        this.namespace = namespace;
        this.infer = infer; // default infer is false
        this.metadata = metadata;
        this.tags = tags;
        this.createdTime = createdTime;
        this.lastUpdateTime = lastUpdateTime;
        this.ownerId = ownerId;
    }

    public MLWorkingMemory(StreamInput in) throws IOException {
        this.memoryContainerId = in.readOptionalString();
        this.memoryType = in.readEnum(ShortTermMemoryType.class);
        if (in.readBoolean()) {
            int messagesSize = in.readVInt();
            this.messages = new ArrayList<>(messagesSize);
            for (int i = 0; i < messagesSize; i++) {
                this.messages.add(new MessageInput(in));
            }
        }
        this.binaryData = in.readOptionalString();
        if (in.readBoolean()) {
            this.structuredData = in.readMap();
        }
        if (in.readBoolean()) {
            this.namespace = in.readMap(StreamInput::readString, StreamInput::readString);
        }
        this.infer = in.readBoolean();
        if (in.readBoolean()) {
            this.metadata = in.readMap(StreamInput::readString, StreamInput::readString);
        }
        if (in.readBoolean()) {
            this.tags = in.readMap(StreamInput::readString, StreamInput::readString);
        }
        this.createdTime = in.readOptionalInstant();
        this.lastUpdateTime = in.readOptionalInstant();
        this.ownerId = in.readOptionalString();
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeOptionalString(memoryContainerId);
        out.writeEnum(memoryType);
        if (messages != null) {
            out.writeBoolean(true);
            out.writeVInt(messages.size());
            for (MessageInput message : messages) {
                message.writeTo(out);
            }
        } else {
            out.writeBoolean(false);
        }

        out.writeOptionalString(binaryData);
        if (structuredData != null) {
            out.writeBoolean(true);
            out.writeMap(structuredData);
        } else {
            out.writeBoolean(false);
        }
        if (namespace != null && !namespace.isEmpty()) {
            out.writeBoolean(true);
            out.writeMap(namespace, StreamOutput::writeString, StreamOutput::writeString);
        } else {
            out.writeBoolean(false);
        }
        out.writeBoolean(infer);
        if (metadata != null && !metadata.isEmpty()) {
            out.writeBoolean(true);
            out.writeMap(metadata, StreamOutput::writeString, StreamOutput::writeString);
        } else {
            out.writeBoolean(false);
        }
        if (tags != null && !tags.isEmpty()) {
            out.writeBoolean(true);
            out.writeMap(tags, StreamOutput::writeString, StreamOutput::writeString);
        } else {
            out.writeBoolean(false);
        }
        out.writeOptionalInstant(createdTime);
        out.writeOptionalInstant(lastUpdateTime);
        out.writeOptionalString(ownerId);
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        if (memoryContainerId != null) {
            builder.field(MEMORY_CONTAINER_ID_FIELD, memoryContainerId);
        }
        builder.field(MEMORY_TYPE_FIELD, memoryType);
        if (messages != null && messages.size() > 0) {
            builder.startArray(MESSAGES_FIELD);
            for (MessageInput message : messages) {
                message.toXContent(builder, params);
            }
            builder.endArray();
        }

        if (binaryData != null) {
            builder.field(BINARY_DATA_FIELD, binaryData);
        }
        if (structuredData != null) {
            builder.field(STRUCTURED_DATA_FIELD, structuredData);
        }
        if (namespace != null && !namespace.isEmpty()) {
            builder.field(NAMESPACE_FIELD, namespace);
        }
        builder.field(INFER_FIELD, infer);
        if (metadata != null && !metadata.isEmpty()) {
            builder.field(METADATA_FIELD, metadata);
        }
        if (tags != null && !tags.isEmpty()) {
            builder.field(TAGS_FIELD, tags);
        }
        if (createdTime != null) {
            builder.field(CREATED_TIME_FIELD, createdTime.toEpochMilli());
        }
        if (lastUpdateTime != null) {
            builder.field(LAST_UPDATED_TIME_FIELD, lastUpdateTime.toEpochMilli());
        }
        if (ownerId != null) {
            builder.field(OWNER_ID_FIELD, ownerId);
        }
        builder.endObject();
        return builder;
    }

    public static MLWorkingMemory parse(XContentParser parser) throws IOException {
        String memoryContainerId = null;
        String memoryType = null;
        List<MessageInput> messages = null;
        String binaryData = null;
        Map<String, Object> structuredData = null;
        Map<String, String> namespace = null;
        boolean infer = false;
        Map<String, String> metadata = null;
        Map<String, String> tags = null;
        Instant createdTime = null;
        Instant lastUpdateTime = null;
        String ownerId = null;

        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.currentToken(), parser);
        while (parser.nextToken() != XContentParser.Token.END_OBJECT) {
            String fieldName = parser.currentName();
            parser.nextToken();

            switch (fieldName) {
                case MEMORY_CONTAINER_ID_FIELD:
                    memoryContainerId = parser.text();
                    break;
                case MEMORY_TYPE_FIELD:
                    memoryType = parser.text();
                    break;
                case MESSAGES_FIELD:
                    messages = new ArrayList<>();
                    ensureExpectedToken(XContentParser.Token.START_ARRAY, parser.currentToken(), parser);
                    while (parser.nextToken() != XContentParser.Token.END_ARRAY) {
                        messages.add(MessageInput.parse(parser));
                    }
                    break;
                case BINARY_DATA_FIELD:
                    binaryData = parser.text();
                    break;
                case STRUCTURED_DATA_FIELD:
                    structuredData = parser.map();
                    break;
                case NAMESPACE_FIELD:
                    namespace = StringUtils.getParameterMap(parser.map());
                    break;
                case INFER_FIELD:
                    infer = parser.booleanValue();
                    break;
                case METADATA_FIELD:
                    metadata = StringUtils.getParameterMap(parser.map());
                    break;
                case TAGS_FIELD:
                    tags = StringUtils.getParameterMap(parser.map());
                    break;
                case CREATED_TIME_FIELD:
                    createdTime = Instant.ofEpochMilli(parser.longValue());
                    break;
                case LAST_UPDATED_TIME_FIELD:
                    lastUpdateTime = Instant.ofEpochMilli(parser.longValue());
                    break;
                case OWNER_ID_FIELD:
                    ownerId = parser.text();
                    break;
                default:
                    parser.skipChildren();
                    break;
            }
        }

        return MLWorkingMemory
            .builder()
            .memoryContainerId(memoryContainerId)
            .memoryType(memoryType == null ? ShortTermMemoryType.CONVERSATION : ShortTermMemoryType.fromString(memoryType))
            .messages(messages)
            .binaryData(binaryData)
            .structuredData(structuredData)
            .namespace(namespace)
            .infer(infer)
            .metadata(metadata)
            .tags(tags)
            .createdTime(createdTime)
            .lastUpdateTime(lastUpdateTime)
            .ownerId(ownerId)
            .build();
    }

    public String getSessionId() {
        return namespace == null ? null : namespace.get(SESSION_ID_FIELD);
    }

    public String getAgentId() {
        return namespace == null ? null : namespace.get(AGENT_ID_FIELD);
    }
}
