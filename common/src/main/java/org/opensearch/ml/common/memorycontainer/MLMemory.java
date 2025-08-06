/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.memorycontainer;

import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.AGENT_ID_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.CREATED_TIME_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.LAST_UPDATED_TIME_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.MEMORY_EMBEDDING_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.MEMORY_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.MEMORY_TYPE_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.ROLE_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.SESSION_ID_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.TAGS_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.USER_ID_FIELD;

import java.io.IOException;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.common.io.stream.Writeable;
import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

/**
 * Represents a memory entry in a memory container
 */
@Getter
@Setter
@Builder
public class MLMemory implements ToXContentObject, Writeable {

    // Core fields
    private String sessionId;
    private String memory;
    private MemoryType memoryType;

    // Optional fields
    private String userId;
    private String agentId;
    private String role;
    private Map<String, String> tags;

    // System fields
    private Instant createdTime;
    private Instant lastUpdatedTime;

    // Vector/embedding field (optional, for semantic storage)
    private Object memoryEmbedding;

    @Builder
    public MLMemory(
        String sessionId,
        String memory,
        MemoryType memoryType,
        String userId,
        String agentId,
        String role,
        Map<String, String> tags,
        Instant createdTime,
        Instant lastUpdatedTime,
        Object memoryEmbedding
    ) {
        this.sessionId = sessionId;
        this.memory = memory;
        this.memoryType = memoryType;
        this.userId = userId;
        this.agentId = agentId;
        this.role = role;
        this.tags = tags;
        this.createdTime = createdTime;
        this.lastUpdatedTime = lastUpdatedTime;
        this.memoryEmbedding = memoryEmbedding;
    }

    public MLMemory(StreamInput in) throws IOException {
        this.sessionId = in.readString();
        this.memory = in.readString();
        this.memoryType = in.readEnum(MemoryType.class);
        this.userId = in.readOptionalString();
        this.agentId = in.readOptionalString();
        this.role = in.readOptionalString();
        if (in.readBoolean()) {
            this.tags = in.readMap(StreamInput::readString, StreamInput::readString);
        }
        this.createdTime = in.readInstant();
        this.lastUpdatedTime = in.readInstant();
        // Note: memoryEmbedding is not serialized in StreamInput/Output as it's typically handled separately
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeString(sessionId);
        out.writeString(memory);
        out.writeEnum(memoryType);
        out.writeOptionalString(userId);
        out.writeOptionalString(agentId);
        out.writeOptionalString(role);
        if (tags != null && !tags.isEmpty()) {
            out.writeBoolean(true);
            out.writeMap(tags, StreamOutput::writeString, StreamOutput::writeString);
        } else {
            out.writeBoolean(false);
        }
        out.writeInstant(createdTime);
        out.writeInstant(lastUpdatedTime);
        // Note: memoryEmbedding is not serialized in StreamInput/Output as it's typically handled separately
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        builder.field(SESSION_ID_FIELD, sessionId);
        builder.field(MEMORY_FIELD, memory);
        builder.field(MEMORY_TYPE_FIELD, memoryType.getValue());

        if (userId != null) {
            builder.field(USER_ID_FIELD, userId);
        }
        if (agentId != null) {
            builder.field(AGENT_ID_FIELD, agentId);
        }
        if (role != null) {
            builder.field(ROLE_FIELD, role);
        }
        if (tags != null && !tags.isEmpty()) {
            builder.field(TAGS_FIELD, tags);
        }

        builder.field(CREATED_TIME_FIELD, createdTime.toEpochMilli());
        builder.field(LAST_UPDATED_TIME_FIELD, lastUpdatedTime.toEpochMilli());

        if (memoryEmbedding != null) {
            builder.field(MEMORY_EMBEDDING_FIELD, memoryEmbedding);
        }

        builder.endObject();
        return builder;
    }

    public static MLMemory parse(XContentParser parser) throws IOException {
        String sessionId = null;
        String memory = null;
        MemoryType memoryType = null;
        String userId = null;
        String agentId = null;
        String role = null;
        Map<String, String> tags = null;
        Instant createdTime = null;
        Instant lastUpdatedTime = null;
        Object memoryEmbedding = null;

        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.currentToken(), parser);
        while (parser.nextToken() != XContentParser.Token.END_OBJECT) {
            String fieldName = parser.currentName();
            parser.nextToken();

            switch (fieldName) {
                case SESSION_ID_FIELD:
                    sessionId = parser.text();
                    break;
                case MEMORY_FIELD:
                    memory = parser.text();
                    break;
                case MEMORY_TYPE_FIELD:
                    memoryType = MemoryType.fromString(parser.text());
                    break;
                case USER_ID_FIELD:
                    userId = parser.text();
                    break;
                case AGENT_ID_FIELD:
                    agentId = parser.text();
                    break;
                case ROLE_FIELD:
                    role = parser.text();
                    break;
                case TAGS_FIELD:
                    Map<String, Object> tagsMap = parser.map();
                    if (tagsMap != null) {
                        tags = new HashMap<>();
                        for (Map.Entry<String, Object> entry : tagsMap.entrySet()) {
                            if (entry.getValue() != null) {
                                tags.put(entry.getKey(), entry.getValue().toString());
                            }
                        }
                    }
                    break;
                case CREATED_TIME_FIELD:
                    createdTime = Instant.ofEpochMilli(parser.longValue());
                    break;
                case LAST_UPDATED_TIME_FIELD:
                    lastUpdatedTime = Instant.ofEpochMilli(parser.longValue());
                    break;
                case MEMORY_EMBEDDING_FIELD:
                    // Parse embedding as generic object (could be array or sparse map)
                    memoryEmbedding = parser.map();
                    break;
                default:
                    parser.skipChildren();
                    break;
            }
        }

        return MLMemory
            .builder()
            .sessionId(sessionId)
            .memory(memory)
            .memoryType(memoryType)
            .userId(userId)
            .agentId(agentId)
            .role(role)
            .tags(tags)
            .createdTime(createdTime)
            .lastUpdatedTime(lastUpdatedTime)
            .memoryEmbedding(memoryEmbedding)
            .build();
    }

    /**
     * Convert to a Map for indexing
     */
    public Map<String, Object> toIndexMap() {
        Map<String, Object> map = Map
            .of(
                SESSION_ID_FIELD,
                sessionId,
                MEMORY_FIELD,
                memory,
                MEMORY_TYPE_FIELD,
                memoryType.getValue(),
                CREATED_TIME_FIELD,
                createdTime.toEpochMilli(),
                LAST_UPDATED_TIME_FIELD,
                lastUpdatedTime.toEpochMilli()
            );

        // Use mutable map for optional fields
        Map<String, Object> result = new java.util.HashMap<>(map);

        if (userId != null) {
            result.put(USER_ID_FIELD, userId);
        }
        if (agentId != null) {
            result.put(AGENT_ID_FIELD, agentId);
        }
        if (role != null) {
            result.put(ROLE_FIELD, role);
        }
        if (tags != null && !tags.isEmpty()) {
            result.put(TAGS_FIELD, tags);
        }
        if (memoryEmbedding != null) {
            result.put(MEMORY_EMBEDDING_FIELD, memoryEmbedding);
        }

        return result;
    }
}
