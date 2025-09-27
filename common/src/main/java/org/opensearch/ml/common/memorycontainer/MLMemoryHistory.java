/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.memorycontainer;

import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;
import static org.opensearch.ml.common.CommonValue.TENANT_ID_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.CREATED_TIME_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.MEMORY_ACTION_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.MEMORY_AFTER_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.MEMORY_BEFORE_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.MEMORY_ID_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.OWNER_ID_FIELD;

import java.io.IOException;
import java.time.Instant;

import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.common.io.stream.Writeable;
import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.transport.memorycontainer.memory.MemoryEvent;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

/**
 * Input data for adding memory to a memory container
 */
@Getter
@Setter
@Builder
public class MLMemoryHistory implements ToXContentObject, Writeable {

    // Required fields
    private String ownerId;
    private String memoryId;
    private MemoryEvent action;
    private MLMemory before;
    private MLMemory after;
    private Instant createdTime;
    private String tenantId;

    public MLMemoryHistory(
        String ownerId,
        String memoryId,
        MemoryEvent action,
        MLMemory before,
        MLMemory after,
        Instant createdTime,
        String tenantId
    ) {
        this.ownerId = ownerId;
        this.memoryId = memoryId;
        this.action = action;
        this.before = before;
        this.after = after;
        this.createdTime = createdTime;
        this.tenantId = tenantId;
    }

    public MLMemoryHistory(StreamInput in) throws IOException {
        this.ownerId = in.readOptionalString();
        this.memoryId = in.readOptionalString();
        this.action = in.readEnum(MemoryEvent.class);
        if (in.readBoolean()) {
            this.before = new MLMemory(in);
        }
        if (in.readBoolean()) {
            this.after = new MLMemory(in);
        }
        this.createdTime = in.readOptionalInstant();
        this.tenantId = in.readOptionalString();
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeOptionalString(ownerId);
        out.writeOptionalString(memoryId);
        out.writeEnum(action);
        if (before != null) {
            out.writeBoolean(true);
            before.writeTo(out);
        } else {
            out.writeBoolean(false);
        }
        if (after != null) {
            out.writeBoolean(true);
            after.writeTo(out);
        } else {
            out.writeBoolean(false);
        }
        out.writeOptionalInstant(createdTime);
        out.writeOptionalString(tenantId);
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        if (ownerId != null) {
            builder.field(OWNER_ID_FIELD, ownerId);
        }
        if (memoryId != null) {
            builder.field(MEMORY_ID_FIELD, memoryId);
        }
        builder.field(MEMORY_ACTION_FIELD, action);
        if (before != null) {
            builder.field(MEMORY_BEFORE_FIELD, before);
        }
        if (after != null) {
            builder.field(MEMORY_AFTER_FIELD, after);
        }
        if (createdTime != null) {
            builder.field(CREATED_TIME_FIELD, createdTime.toEpochMilli());
        }
        if (tenantId != null) {
            builder.field(TENANT_ID_FIELD, tenantId);
        }
        builder.endObject();
        return builder;
    }

    public static MLMemoryHistory parse(XContentParser parser) throws IOException {
        String ownerId = null;
        String memoryId = null;
        MemoryEvent action = null;
        MLMemory before = null;
        MLMemory after = null;
        Instant createdTime = null;
        String tenantId = null;

        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.currentToken(), parser);
        while (parser.nextToken() != XContentParser.Token.END_OBJECT) {
            String fieldName = parser.currentName();
            parser.nextToken();

            switch (fieldName) {
                case OWNER_ID_FIELD:
                    ownerId = parser.text();
                    break;
                case MEMORY_ID_FIELD:
                    memoryId = parser.text();
                    break;
                case MEMORY_ACTION_FIELD:
                    action = MemoryEvent.fromString(parser.text());
                    break;
                case MEMORY_BEFORE_FIELD:
                    before = MLMemory.parse(parser);
                    break;
                case MEMORY_AFTER_FIELD:
                    after = MLMemory.parse(parser);
                    break;
                case CREATED_TIME_FIELD:
                    createdTime = Instant.ofEpochMilli(parser.longValue());
                    break;
                case TENANT_ID_FIELD:
                    tenantId = parser.text();
                    break;
                default:
                    parser.skipChildren();
                    break;
            }
        }

        return MLMemoryHistory
            .builder()
            .ownerId(ownerId)
            .memoryId(memoryId)
            .action(action)
            .before(before)
            .after(after)
            .createdTime(createdTime)
            .tenantId(tenantId)
            .build();
    }

}
