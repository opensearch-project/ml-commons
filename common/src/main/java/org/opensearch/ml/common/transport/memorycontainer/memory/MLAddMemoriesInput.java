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
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.MESSAGES_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.METADATA_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.NAMESPACE_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.OWNER_ID_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.SESSION_ID_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.STRUCTURED_DATA_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.TAGS_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.WORKING_MEMORY_TYPE_FIELD;

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
@Builder(builderClassName = "Builder", buildMethodName = "internalBuild")
public class MLAddMemoriesInput implements ToXContentObject, Writeable {

    // Required fields
    private String memoryContainerId;
    private WorkingMemoryType memoryType;
    private List<MessageInput> messages;
    private String binaryData;
    private Map<String, Object> structuredData;

    // Optional fields
    private Map<String, String> namespace;
    private boolean infer;
    private Map<String, String> metadata;
    private Map<String, String> tags;
    private String ownerId;

    public MLAddMemoriesInput(
        String memoryContainerId,
        WorkingMemoryType memoryType,
        List<MessageInput> messages,
        String binaryData,
        Map<String, Object> structuredData,
        Map<String, String> namespace,
        boolean infer,
        Map<String, String> metadata,
        Map<String, String> tags,
        String ownerId
    ) {
        // Constructor used internally - the builder handles auto-determination logic
        this.memoryContainerId = memoryContainerId;
        this.memoryType = memoryType;
        this.messages = messages;
        this.binaryData = binaryData;
        this.structuredData = structuredData;
        this.namespace = namespace;
        this.infer = infer;
        this.metadata = metadata;
        this.tags = tags;
        this.ownerId = ownerId;
        validate();
    }

    public void validate() {
        // Check that at least one data field is provided
        boolean hasMessages = messages != null && !messages.isEmpty();
        boolean hasBinaryData = binaryData != null && !binaryData.isEmpty();
        boolean hasStructuredData = structuredData != null && !structuredData.isEmpty();

        if (!hasMessages && !hasBinaryData && !hasStructuredData) {
            throw new IllegalArgumentException("At least one of 'messages', 'binary_data', or 'structured_data' must be provided");
        }

        // Validate memoryType consistency
        if (memoryType == WorkingMemoryType.CONVERSATIONAL && !hasMessages) {
            throw new IllegalArgumentException("No messages provided for conversational memory");
        }

        // Validate infer requirements
        if (infer && !hasMessages) {
            throw new IllegalArgumentException("Inference requires messages to be provided");
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
        return toXContent(builder, params, false);
    }

    public XContentBuilder toXContent(XContentBuilder builder, ToXContent.Params params, boolean withTimeStamp) throws IOException {
        builder.startObject();
        if (memoryContainerId != null) {
            builder.field(MEMORY_CONTAINER_ID_FIELD, memoryContainerId);
        }
        builder.field(WORKING_MEMORY_TYPE_FIELD, memoryType);
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
        String binaryData = null;
        Map<String, Object> structuredData = null;
        Map<String, String> namespace = null;
        Boolean infer = null;  // Use Boolean to track if it was explicitly set
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

        // Build with auto-determination logic
        Builder builder = MLAddMemoriesInput
            .builder()
            .memoryContainerId(memoryContainerId)
            .messages(messages)
            .binaryData(binaryData)
            .structuredData(structuredData)
            .namespace(namespace)
            .metadata(metadata)
            .tags(tags);

        // Set memoryType if explicitly provided
        if (memoryType != null) {
            builder.memoryType(WorkingMemoryType.fromString(memoryType));
        }

        // Set infer if explicitly provided
        if (infer != null) {
            builder.infer(infer);
        }

        return builder.build();
    }

    public String getSessionId() {
        return namespace == null ? null : namespace.get(SESSION_ID_FIELD);
    }

    public String getAgentId() {
        return namespace == null ? null : namespace.get(AGENT_ID_FIELD);
    }

    // Custom builder class to handle auto-determination logic
    public static class Builder {
        private Boolean inferProvided = false;
        private boolean inferValue = false;

        public Builder infer(boolean infer) {
            this.inferValue = infer;
            this.inferProvided = true;
            return this;
        }

        public MLAddMemoriesInput build() {
            // Auto-determine memoryType if not specified
            if (memoryType == null) {
                if (binaryData != null || structuredData != null) {
                    memoryType = WorkingMemoryType.DATA;
                } else if (messages != null && !messages.isEmpty()) {
                    memoryType = WorkingMemoryType.CONVERSATIONAL;
                }
                // If still null, validation will catch it
            }

            // Set infer default based on memoryType if not explicitly provided
            if (!inferProvided && memoryType != null) {
                inferValue = (memoryType == WorkingMemoryType.CONVERSATIONAL);
            }

            // Use the renamed internal build method
            this.infer = inferValue;
            return internalBuild();
        }
    }
}
