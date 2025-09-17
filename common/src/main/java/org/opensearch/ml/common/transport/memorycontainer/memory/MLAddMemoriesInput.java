/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.memorycontainer.memory;

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
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import org.opensearch.ml.common.memorycontainer.ShortTermMemoryType;
import org.opensearch.ml.common.utils.StringUtils;

/**
 * Input data for adding memory to a memory container
 */
@Getter
@Setter
@Builder
public class MLAddMemoriesInput implements ToXContentObject, Writeable {

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

    public MLAddMemoriesInput(
        String memoryContainerId,
        ShortTermMemoryType memoryType,
        List<MessageInput> messages,
        String binaryData,
        Map<String, Object> structuredData,
        Map<String, String> namespace,
        boolean infer,
        Map<String, String> metadata,
        Map<String, String> tags
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
        validate();
    }

    public void validate() {
        if (messages == null || messages.isEmpty()) {
            if (infer) {
                throw new IllegalArgumentException("No messages provided when inferring memory");
            }
        }

        if (infer && memoryType != ShortTermMemoryType.CONVERSATION) {
            throw new IllegalArgumentException("Infer is only supported for conversation memory");
        }

        if (memoryContainerId == null) {
            throw new IllegalArgumentException("No memory container id provided");
        }
    }

    public MLAddMemoriesInput(StreamInput in) throws IOException {
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
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, ToXContent.Params params) throws IOException {
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
        builder.endObject();
        return builder;
    }

    public XContentBuilder toXContentWithTimeStamp(XContentBuilder builder, ToXContent.Params params) throws IOException {
        Instant now = Instant.now();
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
        builder.field(CREATED_TIME_FIELD, now);
        builder.field(LAST_UPDATED_TIME_FIELD, now);
        builder.endObject();
        return builder;
    }

    public static MLAddMemoriesInput parse(XContentParser parser, String memoryContainerId) throws IOException {
        String memoryType = null;
        List<MessageInput> messages = null;
        String binaryData = null;
        Map<String, Object> structuredData = null;
        Map<String, String> namespace = null;
        Map<String, String> longTermMemoryNamespace = null;
        boolean infer = false;
        Map<String, String> metadata = null;
        Map<String, String> tags = null;

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
                default:
                    parser.skipChildren();
                    break;
            }
        }

        return MLAddMemoriesInput
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
            .build();
    }

    public String getSessionId() {
        return namespace.get(SESSION_ID_FIELD);
    }

    public String getAgentId() {
        return namespace.get(AGENT_ID_FIELD);
    }
}
