/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.memorycontainer.memory;

import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.*;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;

import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.common.io.stream.Writeable;
import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.ml.common.memorycontainer.MemoryType;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

/**
 * Represents a single memory search result with relevance score
 */
@Getter
@ToString
@Builder
public class MemorySearchResult implements ToXContentObject, Writeable {

    private final String memoryId;
    private final String memory;
    private final float score;
    private final String sessionId;
    private final String agentId;
    private final String userId;
    private final MemoryType memoryType;
    private final String role;
    private final Map<String, String> tags;
    private final Instant createdTime;
    private final Instant lastUpdatedTime;

    public MemorySearchResult(
        String memoryId,
        String memory,
        float score,
        String sessionId,
        String agentId,
        String userId,
        MemoryType memoryType,
        String role,
        Map<String, String> tags,
        Instant createdTime,
        Instant lastUpdatedTime
    ) {
        this.memoryId = memoryId;
        this.memory = memory;
        this.score = score;
        this.sessionId = sessionId;
        this.agentId = agentId;
        this.userId = userId;
        this.memoryType = memoryType;
        this.role = role;
        this.tags = tags;
        this.createdTime = createdTime;
        this.lastUpdatedTime = lastUpdatedTime;
    }

    public MemorySearchResult(StreamInput in) throws IOException {
        this.memoryId = in.readString();
        this.memory = in.readString();
        this.score = in.readFloat();
        this.sessionId = in.readOptionalString();
        this.agentId = in.readOptionalString();
        this.userId = in.readOptionalString();
        String memoryTypeStr = in.readOptionalString();
        this.memoryType = memoryTypeStr != null ? MemoryType.fromString(memoryTypeStr) : null;
        this.role = in.readOptionalString();
        if (in.readBoolean()) {
            this.tags = in.readMap(StreamInput::readString, StreamInput::readString);
        } else {
            this.tags = null;
        }
        this.createdTime = in.readOptionalInstant();
        this.lastUpdatedTime = in.readOptionalInstant();
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeString(memoryId);
        out.writeString(memory);
        out.writeFloat(score);
        out.writeOptionalString(sessionId);
        out.writeOptionalString(agentId);
        out.writeOptionalString(userId);
        out.writeOptionalString(memoryType != null ? memoryType.toString() : null);
        out.writeOptionalString(role);
        if (tags != null && !tags.isEmpty()) {
            out.writeBoolean(true);
            out.writeMap(tags, StreamOutput::writeString, StreamOutput::writeString);
        } else {
            out.writeBoolean(false);
        }
        out.writeOptionalInstant(createdTime);
        out.writeOptionalInstant(lastUpdatedTime);
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        builder.field(MEMORY_ID_FIELD, memoryId);
        builder.field(MEMORY_FIELD, memory);
        builder.field("_score", score);
        if (sessionId != null) {
            builder.field(SESSION_ID_FIELD, sessionId);
        }
        if (agentId != null) {
            builder.field(AGENT_ID_FIELD, agentId);
        }
        if (userId != null) {
            builder.field(USER_ID_FIELD, userId);
        }
        if (memoryType != null) {
            builder.field(MEMORY_TYPE_FIELD, memoryType.toString());
        }
        if (role != null) {
            builder.field(ROLE_FIELD, role);
        }
        if (tags != null && !tags.isEmpty()) {
            builder.field(TAGS_FIELD, tags);
        }
        if (createdTime != null) {
            builder.field(CREATED_TIME_FIELD, createdTime.toEpochMilli());
        }
        if (lastUpdatedTime != null) {
            builder.field(LAST_UPDATED_TIME_FIELD, lastUpdatedTime.toEpochMilli());
        }
        builder.endObject();
        return builder;
    }
}
