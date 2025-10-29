/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.memorycontainer;

import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;
import static org.opensearch.ml.common.CommonValue.ERROR_FIELD;
import static org.opensearch.ml.common.CommonValue.TENANT_ID_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.CREATED_TIME_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.MEMORY_ACTION_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.MEMORY_AFTER_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.MEMORY_BEFORE_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.MEMORY_CONTAINER_ID_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.MEMORY_ID_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.NAMESPACE_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.NAMESPACE_SIZE_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.OWNER_ID_FIELD;
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
import org.opensearch.ml.common.transport.memorycontainer.memory.MemoryEvent;
import org.opensearch.ml.common.utils.StringUtils;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

/**
 * Represents a history record of memory operations (create, update, delete) in the memory container.
 * Tracks changes to memories with before/after states and timestamps for auditing purposes.
 */
@Getter
@Setter
@Builder
public class MLMemoryHistory implements ToXContentObject, Writeable {

    // Required fields
    private String ownerId;
    private String memoryContainerId;
    private String memoryId;
    private MemoryEvent action;
    private Map<String, Object> before;
    private Map<String, Object> after;
    private Map<String, String> namespace;
    private Map<String, String> tags;
    private Instant createdTime;
    private String tenantId;
    private String error;

    public MLMemoryHistory(
        String ownerId,
        String memoryContainerId,
        String memoryId,
        MemoryEvent action,
        Map<String, Object> before,
        Map<String, Object> after,
        Map<String, String> namespace,
        Map<String, String> tags,
        Instant createdTime,
        String tenantId,
        String error
    ) {
        this.ownerId = ownerId;
        this.memoryContainerId = memoryContainerId;
        this.memoryId = memoryId;
        this.action = action;
        this.before = before;
        this.after = after;
        this.namespace = namespace;
        this.tags = tags;
        this.createdTime = createdTime;
        this.tenantId = tenantId;
        this.error = error;
    }

    public MLMemoryHistory(StreamInput in) throws IOException {
        this.ownerId = in.readOptionalString();
        this.memoryContainerId = in.readOptionalString();
        this.memoryId = in.readOptionalString();
        if (in.readBoolean()) {
            this.action = in.readEnum(MemoryEvent.class);
        }
        if (in.readBoolean()) {
            this.before = in.readMap();
        }
        if (in.readBoolean()) {
            this.after = in.readMap();
        }
        this.createdTime = in.readOptionalInstant();
        if (in.readBoolean()) {
            namespace = in.readMap(StreamInput::readString, StreamInput::readString);
        }
        if (in.readBoolean()) {
            this.tags = in.readMap(StreamInput::readString, StreamInput::readString);
        }
        this.tenantId = in.readOptionalString();
        this.error = in.readOptionalString();
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeOptionalString(ownerId);
        out.writeOptionalString(memoryContainerId);
        out.writeOptionalString(memoryId);
        if (action != null) {
            out.writeBoolean(true);
            out.writeEnum(action);
        } else {
            out.writeBoolean(false);
        }
        if (before != null) {
            out.writeBoolean(true);
            out.writeMap(before);
        } else {
            out.writeBoolean(false);
        }
        if (after != null) {
            out.writeBoolean(true);
            out.writeMap(after);
        } else {
            out.writeBoolean(false);
        }
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
        out.writeOptionalInstant(createdTime);
        out.writeOptionalString(tenantId);
        out.writeOptionalString(error);
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        if (ownerId != null) {
            builder.field(OWNER_ID_FIELD, ownerId);
        }
        if (memoryContainerId != null) {
            builder.field(MEMORY_CONTAINER_ID_FIELD, memoryContainerId);
        }
        if (memoryId != null) {
            builder.field(MEMORY_ID_FIELD, memoryId);
        }
        if (action != null) {
            builder.field(MEMORY_ACTION_FIELD, action);
        }
        if (before != null) {
            builder.field(MEMORY_BEFORE_FIELD, before);
        }
        if (after != null) {
            builder.field(MEMORY_AFTER_FIELD, after);
        }
        if (namespace != null && !namespace.isEmpty()) {
            builder.field(NAMESPACE_FIELD, namespace);
            builder.field(NAMESPACE_SIZE_FIELD, namespace.size());
        }
        if (tags != null && !tags.isEmpty()) {
            builder.field(TAGS_FIELD, tags);
        }
        if (createdTime != null) {
            builder.field(CREATED_TIME_FIELD, createdTime.toEpochMilli());
        }
        if (tenantId != null) {
            builder.field(TENANT_ID_FIELD, tenantId);
        }
        if (error != null) {
            builder.field(ERROR_FIELD, error);
        }
        builder.endObject();
        return builder;
    }

    public static MLMemoryHistory parse(XContentParser parser) throws IOException {
        String ownerId = null;
        String memoryContainerId = null;
        String memoryId = null;
        MemoryEvent action = null;
        Map<String, Object> before = null;
        Map<String, Object> after = null;
        Map<String, String> namespace = null;
        Map<String, String> tags = null;
        Instant createdTime = null;
        String tenantId = null;
        String error = null;

        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.currentToken(), parser);
        while (parser.nextToken() != XContentParser.Token.END_OBJECT) {
            String fieldName = parser.currentName();
            parser.nextToken();

            switch (fieldName) {
                case OWNER_ID_FIELD:
                    ownerId = parser.text();
                    break;
                case MEMORY_CONTAINER_ID_FIELD:
                    memoryContainerId = parser.text();
                    break;
                case MEMORY_ID_FIELD:
                    memoryId = parser.text();
                    break;
                case MEMORY_ACTION_FIELD:
                    action = MemoryEvent.fromString(parser.text());
                    break;
                case MEMORY_BEFORE_FIELD:
                    before = parser.map();
                    break;
                case MEMORY_AFTER_FIELD:
                    after = parser.map();
                    break;
                case NAMESPACE_FIELD:
                    namespace = StringUtils.getParameterMap(parser.map());
                    break;
                case TAGS_FIELD:
                    tags = StringUtils.getParameterMap(parser.map());
                    break;
                case CREATED_TIME_FIELD:
                    createdTime = Instant.ofEpochMilli(parser.longValue());
                    break;
                case TENANT_ID_FIELD:
                    tenantId = parser.text();
                    break;
                case ERROR_FIELD:
                    error = parser.text();
                    break;
                default:
                    parser.skipChildren();
                    break;
            }
        }

        return MLMemoryHistory
            .builder()
            .ownerId(ownerId)
            .memoryContainerId(memoryContainerId)
            .memoryId(memoryId)
            .action(action)
            .before(before)
            .after(after)
            .namespace(namespace)
            .tags(tags)
            .createdTime(createdTime)
            .tenantId(tenantId)
            .error(error)
            .build();
    }

}
