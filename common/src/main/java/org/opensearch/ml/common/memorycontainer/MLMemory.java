/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.memorycontainer;

import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.CREATED_TIME_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.LAST_UPDATED_TIME_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.MEMORY_EMBEDDING_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.MEMORY_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.MEMORY_TYPE_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.NAMESPACE_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.NAMESPACE_SIZE_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.OWNER_ID_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.ROLE_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.STRATEGY_ID_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.TAGS_FIELD;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;

import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.common.io.stream.Writeable;
import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.utils.StringUtils;

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
    private String memory;
    private MemoryType memoryType;

    private Map<String, String> namespace;
    private Map<String, String> tags;

    // System fields
    private Instant createdTime;
    private Instant lastUpdatedTime;

    // Vector/embedding field (optional, for semantic storage)
    private Object memoryEmbedding;
    private String ownerId;
    private String strategyId;

    @Builder
    public MLMemory(
        String memory,
        MemoryType memoryType,
        Map<String, String> namespace,
        Map<String, String> tags,
        Instant createdTime,
        Instant lastUpdatedTime,
        Object memoryEmbedding,
        String ownerId,
        String strategyId
    ) {
        this.memory = memory;
        this.memoryType = memoryType;
        this.namespace = namespace;
        this.tags = tags;
        this.createdTime = createdTime;
        this.lastUpdatedTime = lastUpdatedTime;
        this.memoryEmbedding = memoryEmbedding;
        this.ownerId = ownerId;
        this.strategyId = strategyId;
    }

    public MLMemory(StreamInput in) throws IOException {
        this.memory = in.readString();
        this.memoryType = in.readEnum(MemoryType.class);
        if (in.readBoolean()) {
            this.namespace = in.readMap(StreamInput::readString, StreamInput::readString);
        }
        if (in.readBoolean()) {
            this.tags = in.readMap(StreamInput::readString, StreamInput::readString);
        }
        this.createdTime = in.readInstant();
        this.lastUpdatedTime = in.readInstant();
        this.ownerId = in.readOptionalString();
        this.strategyId = in.readOptionalString();
        // Note: memoryEmbedding is not serialized in StreamInput/Output as it's typically handled separately
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeString(memory);
        out.writeEnum(memoryType);
        if (namespace != null && !namespace.isEmpty()) {
            out.writeBoolean(true);
            out.writeMap(namespace, StreamOutput::writeString, StreamOutput::writeString);
        } else {
            out.writeBoolean(false);
        }
        if (tags != null && !tags.isEmpty()) {
            out.writeBoolean(true);
            out.writeMap(tags, StreamOutput::writeString, StreamOutput::writeString);
        } else {
            out.writeBoolean(false);
        }
        out.writeInstant(createdTime);
        out.writeInstant(lastUpdatedTime);
        out.writeOptionalString(ownerId);
        out.writeOptionalString(strategyId);
        // Note: memoryEmbedding is not serialized in StreamInput/Output as it's typically handled separately
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        builder.field(MEMORY_FIELD, memory);
        builder.field(MEMORY_TYPE_FIELD, memoryType.getValue());

        if (tags != null && !tags.isEmpty()) {
            builder.field(TAGS_FIELD, tags);
        }
        if (namespace != null && !namespace.isEmpty()) {
            builder.field(NAMESPACE_FIELD, namespace);
            builder.field(NAMESPACE_SIZE_FIELD, namespace.size());
        }

        builder.field(CREATED_TIME_FIELD, createdTime.toEpochMilli());
        builder.field(LAST_UPDATED_TIME_FIELD, lastUpdatedTime.toEpochMilli());

        if (memoryEmbedding != null) {
            builder.field(MEMORY_EMBEDDING_FIELD, memoryEmbedding);
        }
        if (ownerId != null) {
            builder.field(OWNER_ID_FIELD, ownerId);
        }
        if (strategyId != null) {
            builder.field(STRATEGY_ID_FIELD, strategyId);
        }

        builder.endObject();
        return builder;
    }

    public static MLMemory parse(XContentParser parser) throws IOException {
        String memory = null;
        MemoryType memoryType = null;
        Map<String, String> namespace = null;
        Map<String, String> tags = null;
        Instant createdTime = null;
        Instant lastUpdatedTime = null;
        Object memoryEmbedding = null;
        String ownerId = null;
        String strategyId = null;

        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.currentToken(), parser);
        while (parser.nextToken() != XContentParser.Token.END_OBJECT) {
            String fieldName = parser.currentName();
            parser.nextToken();

            switch (fieldName) {
                case MEMORY_FIELD:
                    memory = parser.text();
                    break;
                case MEMORY_TYPE_FIELD:
                    memoryType = MemoryType.fromString(parser.text());
                    break;
                case TAGS_FIELD:
                    tags = StringUtils.getParameterMap(parser.map());
                    break;
                case NAMESPACE_FIELD:
                    namespace = StringUtils.getParameterMap(parser.map());
                    break;
                case CREATED_TIME_FIELD:
                    createdTime = Instant.ofEpochMilli(parser.longValue());
                    break;
                case LAST_UPDATED_TIME_FIELD:
                    lastUpdatedTime = Instant.ofEpochMilli(parser.longValue());
                    break;
                case MEMORY_EMBEDDING_FIELD:
                    // Parse embedding as generic object (could be array or sparse map)
                    if (parser.currentToken() == XContentParser.Token.START_ARRAY) {
                        memoryEmbedding = parser.list(); // Simple list parsing like ModelTensor
                    } else if (parser.currentToken() == XContentParser.Token.START_OBJECT) {
                        memoryEmbedding = parser.map(); // For sparse embeddings
                    } else {
                        parser.skipChildren();
                    }
                    break;
                case OWNER_ID_FIELD:
                    ownerId = parser.text();
                    break;
                case STRATEGY_ID_FIELD:
                    strategyId = parser.text();
                    break;
                default:
                    parser.skipChildren();
                    break;
            }
        }

        return MLMemory
            .builder()
            .memory(memory)
            .memoryType(memoryType)
            .namespace(namespace)
            .tags(tags)
            .createdTime(createdTime)
            .lastUpdatedTime(lastUpdatedTime)
            .memoryEmbedding(memoryEmbedding)
            .ownerId(ownerId)
            .strategyId(strategyId)
            .build();
    }

    /**
     * Convert to a Map for indexing
     */
    public Map<String, Object> toIndexMap() {
        Map<String, Object> map = Map
            .of(
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

        if (namespace != null && !namespace.isEmpty()) {
            result.put(NAMESPACE_FIELD, namespace);
            result.put(NAMESPACE_SIZE_FIELD, namespace.size());
        }
        if (tags != null && !tags.isEmpty()) {
            result.put(TAGS_FIELD, tags);
        }
        if (memoryEmbedding != null) {
            result.put(MEMORY_EMBEDDING_FIELD, memoryEmbedding);
        }
        if (ownerId != null) {
            result.put(OWNER_ID_FIELD, ownerId);
        }
        if (strategyId != null) {
            result.put(STRATEGY_ID_FIELD, strategyId);
        }
        return result;
    }
}
