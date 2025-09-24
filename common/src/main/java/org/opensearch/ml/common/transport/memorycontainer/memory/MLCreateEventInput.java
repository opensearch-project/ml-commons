/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.memorycontainer.memory;

import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.AGENT_ID_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.DATA_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.INFER_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.MEMORY_CONTAINER_ID_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.MEMORY_TYPE_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.MESSAGES_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.METADATA_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.NAMESPACE_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.SESSION_ID_FIELD;

import java.io.IOException;
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
import org.opensearch.ml.common.memorycontainer.ShortTermMemoryType;
import org.opensearch.ml.common.utils.StringUtils;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

/**
 * Input payload for creating short-term events in a memory container
 */
@Getter
@Setter
@Builder
public class MLCreateEventInput implements ToXContentObject, Writeable {

    // Required fields
    private String memoryContainerId;
    private ShortTermMemoryType memoryType;  // Auto-detected from fields
    private List<MessageInput> messages;
    private Map<String, Object> data;

    // Optional fields
    private Map<String, String> namespace;
    private boolean infer;
    private Map<String, String> metadata;

    public MLCreateEventInput(
        String memoryContainerId,
        ShortTermMemoryType memoryType,
        List<MessageInput> messages,
        Map<String, Object> data,
        Map<String, String> namespace,
        boolean infer,
        Map<String, String> metadata
    ) {
        // MAX_MESSAGES_PER_REQUEST limit removed for performance testing

        this.memoryContainerId = memoryContainerId;
        this.messages = messages;
        this.data = data;
        this.namespace = namespace;
        this.metadata = metadata;

        // Auto-detect memory type and set infer defaults
        if (messages != null && !messages.isEmpty()) {
            this.memoryType = ShortTermMemoryType.CONVERSATIONAL;
            this.infer = infer; // User can override
        } else if (data != null) {
            this.memoryType = ShortTermMemoryType.DATA;
            this.infer = false; // Always false for data
        } else {
            this.memoryType = memoryType; // Use provided type if neither messages nor data
        }

        validate();
    }

    public void validate() {
        // Check mutual exclusion
        if (messages != null && !messages.isEmpty() && data != null) {
            throw new IllegalArgumentException("Cannot specify both 'messages' and 'data' fields in the same request");
        }

        // Check at least one is provided
        if ((messages == null || messages.isEmpty()) && data == null) {
            throw new IllegalArgumentException("Must specify either 'messages' or 'data' field");
        }

        // Validate infer field for data type
        if (data != null && infer) {
            throw new IllegalArgumentException("infer=true is not supported for data memory type");
        }

        // Validate infer with messages
        if (messages == null || messages.isEmpty()) {
            if (infer) {
                throw new IllegalArgumentException("No messages provided when inferring memory");
            }
        }

        if (infer && memoryType != ShortTermMemoryType.CONVERSATIONAL) {
            throw new IllegalArgumentException("Infer is only supported for conversational memory");
        }

        if (memoryContainerId == null) {
            throw new IllegalArgumentException("No memory container id provided");
        }
    }

    public MLCreateEventInput(StreamInput in) throws IOException {
        this.memoryContainerId = in.readOptionalString();
        this.memoryType = in.readEnum(ShortTermMemoryType.class);
        if (in.readBoolean()) {
            int messagesSize = in.readVInt();
            this.messages = new ArrayList<>(messagesSize);
            for (int i = 0; i < messagesSize; i++) {
                this.messages.add(new MessageInput(in));
            }
        }
        if (in.readBoolean()) {
            this.data = in.readMap();
        }
        if (in.readBoolean()) {
            this.namespace = in.readMap(StreamInput::readString, StreamInput::readString);
        }
        this.infer = in.readBoolean();
        if (in.readBoolean()) {
            this.metadata = in.readMap(StreamInput::readString, StreamInput::readString);
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

        if (data != null) {
            out.writeBoolean(true);
            out.writeMap(data);
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

        if (data != null) {
            builder.field(DATA_FIELD, data);
        }
        if (namespace != null && !namespace.isEmpty()) {
            builder.field(NAMESPACE_FIELD, namespace);
        }
        builder.field(INFER_FIELD, infer);
        if (metadata != null && !metadata.isEmpty()) {
            builder.field(METADATA_FIELD, metadata);
        }
        builder.endObject();
        return builder;
    }

    public static MLCreateEventInput parse(XContentParser parser, String memoryContainerId) throws IOException {
        List<MessageInput> messages = null;
        Map<String, Object> data = null;
        Map<String, String> namespace = null;
        Boolean inferSpecified = null; // Track if user explicitly set infer
        boolean infer = false;
        Map<String, String> metadata = null;

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
                case DATA_FIELD:
                    data = parser.map();
                    break;
                case NAMESPACE_FIELD:
                    namespace = StringUtils.getParameterMap(parser.map());
                    break;
                case INFER_FIELD:
                    inferSpecified = parser.booleanValue();
                    break;
                case METADATA_FIELD:
                    metadata = StringUtils.getParameterMap(parser.map());
                    break;
                default:
                    parser.skipChildren();
                    break;
            }
        }

        // Handle infer defaults based on type
        if (messages != null && !messages.isEmpty()) {
            // Conversational type: default infer to true if not specified
            infer = inferSpecified != null ? inferSpecified : true;
        } else if (data != null) {
            // Data type: always false, error if user tries to set to true
            if (inferSpecified != null && inferSpecified) {
                throw new IllegalArgumentException("infer=true is not supported for data memory type");
            }
            infer = false;
        } else {
            // Use provided value or false as default
            infer = inferSpecified != null ? inferSpecified : false;
        }

        return MLCreateEventInput
            .builder()
            .memoryContainerId(memoryContainerId)
            .memoryType(null) // Will be auto-detected in constructor
            .messages(messages)
            .data(data)
            .namespace(namespace)
            .infer(infer)
            .metadata(metadata)
            .build();
    }

    public String getSessionId() {
        return namespace.get(SESSION_ID_FIELD);
    }

    public String getAgentId() {
        return namespace.get(AGENT_ID_FIELD);
    }
}
