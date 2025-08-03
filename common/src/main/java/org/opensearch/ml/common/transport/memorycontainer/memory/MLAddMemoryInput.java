/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.memorycontainer.memory;

import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.*;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.common.io.stream.Writeable;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.memorycontainer.MemoryType;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

/**
 * Input data for adding memory to a memory container
 */
@Getter
@Setter
@Builder
public class MLAddMemoryInput implements ToXContentObject, Writeable {

    // Required fields
    private String memoryContainerId;
    private String memory; // Internally stored as 'memory', but exposed as 'message' in API

    // Optional fields
    private MemoryType memoryType;
    private String memoryId;
    private String sessionId;
    private String agentId;
    private String role;
    private Boolean infer;
    private Map<String, String> tags;

    public MLAddMemoryInput(
        String memoryContainerId,
        String memory,
        MemoryType memoryType,
        String memoryId,
        String sessionId,
        String agentId,
        String role,
        Boolean infer,
        Map<String, String> tags
    ) {
        // Note: memoryContainerId validation is removed here since it may come from URL path
        if (memory == null || memory.isEmpty()) {
            throw new IllegalArgumentException("Message is required");
        }

        this.memoryContainerId = memoryContainerId;
        this.memory = memory;
        this.memoryType = memoryType;
        this.memoryId = memoryId;
        this.sessionId = sessionId;
        this.agentId = agentId;
        this.role = role;
        this.infer = infer;
        this.tags = tags;
    }

    public MLAddMemoryInput(StreamInput in) throws IOException {
        this.memoryContainerId = in.readOptionalString();
        this.memory = in.readString();
        String memoryTypeStr = in.readOptionalString();
        this.memoryType = memoryTypeStr != null ? MemoryType.fromString(memoryTypeStr) : null;
        this.memoryId = in.readOptionalString();
        this.sessionId = in.readOptionalString();
        this.agentId = in.readOptionalString();
        this.role = in.readOptionalString();
        this.infer = in.readOptionalBoolean();
        if (in.readBoolean()) {
            this.tags = in.readMap(StreamInput::readString, StreamInput::readString);
        }
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeOptionalString(memoryContainerId);
        out.writeString(memory);
        out.writeOptionalString(memoryType != null ? memoryType.getValue() : null);
        out.writeOptionalString(memoryId);
        out.writeOptionalString(sessionId);
        out.writeOptionalString(agentId);
        out.writeOptionalString(role);
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
        builder.field(MESSAGE_FIELD, memory);
        if (memoryType != null) {
            builder.field(MEMORY_TYPE_FIELD, memoryType.getValue());
        }
        if (memoryId != null) {
            builder.field(MEMORY_ID_FIELD, memoryId);
        }
        if (sessionId != null) {
            builder.field(SESSION_ID_FIELD, sessionId);
        }
        if (agentId != null) {
            builder.field(AGENT_ID_FIELD, agentId);
        }
        if (role != null) {
            builder.field(ROLE_FIELD, role);
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

    public static MLAddMemoryInput parse(XContentParser parser) throws IOException {
        String memoryContainerId = null;
        String memory = null;
        MemoryType memoryType = null;
        String memoryId = null;
        String sessionId = null;
        String agentId = null;
        String role = null;
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
                case MESSAGE_FIELD:
                    memory = parser.text();
                    break;
                case MEMORY_TYPE_FIELD:
                    memoryType = MemoryType.fromString(parser.text());
                    break;
                case MEMORY_ID_FIELD:
                    memoryId = parser.text();
                    break;
                case SESSION_ID_FIELD:
                    sessionId = parser.text();
                    break;
                case AGENT_ID_FIELD:
                    agentId = parser.text();
                    break;
                case ROLE_FIELD:
                    role = parser.text();
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

        return MLAddMemoryInput
            .builder()
            .memoryContainerId(memoryContainerId)
            .memory(memory)
            .memoryType(memoryType)
            .memoryId(memoryId)
            .sessionId(sessionId)
            .agentId(agentId)
            .role(role)
            .infer(infer)
            .tags(tags)
            .build();
    }
}
