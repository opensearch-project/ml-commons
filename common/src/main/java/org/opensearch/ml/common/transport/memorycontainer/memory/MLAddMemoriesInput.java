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
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.MESSAGES_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.MESSAGE_ID_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.METADATA_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.NAMESPACE_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.NAMESPACE_SIZE_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.OWNER_ID_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.PARAMETERS_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.SESSION_ID_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.STRUCTURED_DATA_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.TAGS_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.WORKING_MEMORY_TYPE_FIELD;

import java.io.IOException;
import java.time.Instant;
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
import org.opensearch.ml.common.memorycontainer.WorkingMemoryType;
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
public class MLAddMemoriesInput implements ToXContentObject, Writeable {

    // Required fields
    private String memoryContainerId;
    private WorkingMemoryType memoryType;
    private List<MessageInput> messages;
    private Integer messageId;
    private String binaryData;
    private Map<String, Object> structuredData;

    // Optional fields
    private Map<String, String> namespace;
    private boolean infer;
    private Map<String, String> metadata;
    private Map<String, String> tags;
    private Map<String, Object> parameters;
    private String ownerId;

    public MLAddMemoriesInput(
        String memoryContainerId,
        WorkingMemoryType memoryType,
        List<MessageInput> messages,
        Integer messageId,
        String binaryData,
        Map<String, Object> structuredData,
        Map<String, String> namespace,
        boolean infer,
        Map<String, String> metadata,
        Map<String, String> tags,
        Map<String, Object> parameters,
        String ownerId
    ) {
        // MAX_MESSAGES_PER_REQUEST limit removed for performance testing

        this.memoryContainerId = memoryContainerId;
        this.memoryType = memoryType == null ? WorkingMemoryType.CONVERSATIONAL : memoryType;
        this.messages = messages;
        this.messageId = messageId;
        this.binaryData = binaryData;
        this.structuredData = structuredData;
        this.namespace = namespace;
        this.infer = infer; // default infer is false
        this.metadata = metadata;
        this.tags = tags;
        this.parameters = new HashMap<>();
        if (parameters != null && !parameters.isEmpty()) {
            this.parameters.putAll(parameters);
        }
        this.ownerId = ownerId;
        validate();
    }

    public void validate() {
        if (messages == null || messages.isEmpty()) {
            if (infer) {
                throw new IllegalArgumentException("No messages provided when inferring memory");
            }
        }

        if (memoryContainerId == null) {
            throw new IllegalArgumentException("No memory container id provided");
        }
    }

    public MLAddMemoriesInput(StreamInput in) throws IOException {
        this.memoryContainerId = in.readOptionalString();
        this.memoryType = in.readEnum(WorkingMemoryType.class);
        if (in.readBoolean()) {
            int messagesSize = in.readVInt();
            this.messages = new ArrayList<>(messagesSize);
            for (int i = 0; i < messagesSize; i++) {
                this.messages.add(new MessageInput(in));
            }
        }
        this.messageId = in.readOptionalInt();
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
        if (in.readBoolean()) {
            this.parameters = in.readMap();
        }
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
        out.writeOptionalInt(messageId);
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
        if (parameters != null) {
            out.writeBoolean(true);
            out.writeMap(parameters);
        } else {
            out.writeBoolean(false);
        }
        out.writeOptionalString(ownerId);
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, ToXContent.Params params) throws IOException {
        return toXContent(builder, params, false);
    }

    public XContentBuilder toXContent(XContentBuilder builder, ToXContent.Params params, boolean withTimeStamp) throws IOException {
        builder.startObject();
        builder.field(WORKING_MEMORY_TYPE_FIELD, memoryType);
        if (messages != null && messages.size() > 0) {
            builder.startArray(MESSAGES_FIELD);
            for (MessageInput message : messages) {
                message.toXContent(builder, params);
            }
            builder.endArray();
        }
        if (messageId != null) {
            builder.field(MESSAGE_ID_FIELD, messageId);
        }
        if (binaryData != null) {
            builder.field(BINARY_DATA_FIELD, binaryData);
        }
        if (structuredData != null) {
            builder.field(STRUCTURED_DATA_FIELD, structuredData);
        }
        if (namespace != null && !namespace.isEmpty()) {
            builder.field(NAMESPACE_FIELD, namespace);
            builder.field(NAMESPACE_SIZE_FIELD, namespace.size());
        }
        builder.field(INFER_FIELD, infer);
        if (metadata != null && !metadata.isEmpty()) {
            builder.field(METADATA_FIELD, metadata);
        }
        if (tags != null && !tags.isEmpty()) {
            builder.field(TAGS_FIELD, tags);
        }
        if (parameters != null && !parameters.isEmpty()) {
            builder.field(PARAMETERS_FIELD, parameters);
        }
        if (ownerId != null) {
            builder.field(OWNER_ID_FIELD, ownerId);
        }
        if (withTimeStamp) {
            Instant now = Instant.now();
            builder.field(CREATED_TIME_FIELD, now.toEpochMilli());
            builder.field(LAST_UPDATED_TIME_FIELD, now.toEpochMilli());
        }
        builder.endObject();
        return builder;
    }

    public static MLAddMemoriesInput parse(XContentParser parser, String memoryContainerId) throws IOException {
        String memoryType = null;
        List<MessageInput> messages = null;
        Integer messageId = null;
        String binaryData = null;
        Map<String, Object> structuredData = null;
        Map<String, String> namespace = null;
        boolean infer = false;
        Map<String, String> metadata = null;
        Map<String, String> tags = null;
        Map<String, Object> parameters = null;
        String ownerId = null;

        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.currentToken(), parser);
        while (parser.nextToken() != XContentParser.Token.END_OBJECT) {
            String fieldName = parser.currentName();
            parser.nextToken();

            switch (fieldName) {
                case WORKING_MEMORY_TYPE_FIELD:
                    memoryType = parser.text();
                    break;
                case MESSAGES_FIELD:
                    messages = new ArrayList<>();
                    ensureExpectedToken(XContentParser.Token.START_ARRAY, parser.currentToken(), parser);
                    while (parser.nextToken() != XContentParser.Token.END_ARRAY) {
                        messages.add(MessageInput.parse(parser));
                    }
                    break;
                case MESSAGE_ID_FIELD:
                    messageId = parser.intValue();
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
                case PARAMETERS_FIELD:
                    parameters = parser.map();
                    break;
                case OWNER_ID_FIELD:
                    ownerId = parser.text();
                    break;
                default:
                    parser.skipChildren();
                    break;
            }
        }

        return MLAddMemoriesInput
            .builder()
            .memoryContainerId(memoryContainerId)
            .memoryType(memoryType == null ? WorkingMemoryType.CONVERSATIONAL : WorkingMemoryType.fromString(memoryType))
            .messages(messages)
            .messageId(messageId)
            .binaryData(binaryData)
            .structuredData(structuredData)
            .namespace(namespace)
            .infer(infer)
            .metadata(metadata)
            .tags(tags)
            .parameters(parameters)
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
