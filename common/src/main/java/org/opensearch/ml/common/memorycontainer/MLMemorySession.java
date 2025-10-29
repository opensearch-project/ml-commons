/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.memorycontainer;

import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;
import static org.opensearch.ml.common.CommonValue.TENANT_ID_FIELD;
import static org.opensearch.ml.common.conversation.ActionConstants.ADDITIONAL_INFO_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.AGENTS_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.CREATED_TIME_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.LAST_UPDATED_TIME_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.MEMORY_CONTAINER_ID_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.METADATA_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.NAMESPACE_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.OWNER_ID_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.SUMMARY_FIELD;

import java.io.IOException;
import java.time.Instant;
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
 * Represents a session record in the memory container system.
 * Contains session metadata including owner, summary, creation time, and associated namespace.
 */
@Getter
@Setter
@Builder
public class MLMemorySession implements ToXContentObject, Writeable {

    // Required fields
    private String ownerId;
    private String memoryContainerId;
    private String summary;
    private Instant createdTime;
    private Instant lastUpdateTime;
    private Map<String, Object> metadata;
    private Map<String, Object> agents;
    private Map<String, Object> additionalInfo;
    private Map<String, String> namespace;
    private String tenantId;

    public MLMemorySession(
        String ownerId,
        String memoryContainerId,
        String summary,
        Instant createdTime,
        Instant lastUpdateTime,
        Map<String, Object> metadata,
        Map<String, Object> agents,
        Map<String, Object> additionalInfo,
        Map<String, String> namespace,
        String tenantId
    ) {
        this.ownerId = ownerId;
        this.memoryContainerId = memoryContainerId;
        this.summary = summary;
        this.createdTime = createdTime;
        this.lastUpdateTime = lastUpdateTime;
        this.metadata = metadata;
        this.agents = agents;
        this.additionalInfo = additionalInfo;
        this.namespace = namespace;
        this.tenantId = tenantId;
    }

    public MLMemorySession(StreamInput in) throws IOException {
        this.ownerId = in.readOptionalString();
        this.memoryContainerId = in.readOptionalString();
        this.summary = in.readOptionalString();
        this.createdTime = in.readOptionalInstant();
        this.lastUpdateTime = in.readOptionalInstant();
        if (in.readBoolean()) {
            this.metadata = in.readMap();
        }
        if (in.readBoolean()) {
            this.agents = in.readMap();
        }
        if (in.readBoolean()) {
            this.additionalInfo = in.readMap();
        }
        if (in.readBoolean()) {
            this.namespace = in.readMap(StreamInput::readString, StreamInput::readString);
        }
        this.tenantId = in.readOptionalString();
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeOptionalString(ownerId);
        out.writeOptionalString(memoryContainerId);
        out.writeOptionalString(summary);
        out.writeOptionalInstant(createdTime);
        out.writeOptionalInstant(lastUpdateTime);

        if (metadata != null) {
            out.writeBoolean(true);
            out.writeMap(metadata);
        } else {
            out.writeBoolean(false);
        }
        if (agents != null) {
            out.writeBoolean(true);
            out.writeMap(agents);
        } else {
            out.writeBoolean(false);
        }
        if (additionalInfo != null) {
            out.writeBoolean(true);
            out.writeMap(additionalInfo);
        } else {
            out.writeBoolean(false);
        }
        if (namespace != null) {
            out.writeBoolean(true);
            out.writeMap(namespace, StreamOutput::writeString, StreamOutput::writeOptionalString);
        } else {
            out.writeBoolean(false);
        }
        out.writeOptionalString(tenantId);
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
        if (summary != null) {
            builder.field(SUMMARY_FIELD, summary);
        }
        if (createdTime != null) {
            builder.field(CREATED_TIME_FIELD, createdTime.toEpochMilli());
        }
        if (lastUpdateTime != null) {
            builder.field(LAST_UPDATED_TIME_FIELD, lastUpdateTime.toEpochMilli());
        }
        if (metadata != null) {
            builder.field(METADATA_FIELD, metadata);
        }
        if (agents != null) {
            builder.field(AGENTS_FIELD, agents);
        }
        if (additionalInfo != null) {
            builder.field(ADDITIONAL_INFO_FIELD, additionalInfo);
        }
        if (namespace != null) {
            builder.field(NAMESPACE_FIELD, namespace);
        }
        if (tenantId != null) {
            builder.field(TENANT_ID_FIELD, tenantId);
        }
        builder.endObject();
        return builder;
    }

    public static MLMemorySession parse(XContentParser parser) throws IOException {
        String ownerId = null;
        String memoryContainerId = null;
        String summary = null;
        Instant createdTime = null;
        Instant lastUpdateTime = null;
        Map<String, Object> metadata = null;
        Map<String, Object> agents = null;
        Map<String, Object> additionalInfo = null;
        Map<String, String> namespace = null;
        String tenantId = null;

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
                case SUMMARY_FIELD:
                    summary = parser.text();
                    break;
                case CREATED_TIME_FIELD:
                    createdTime = Instant.ofEpochMilli(parser.longValue());
                    break;
                case LAST_UPDATED_TIME_FIELD:
                    lastUpdateTime = Instant.ofEpochMilli(parser.longValue());
                    break;
                case METADATA_FIELD:
                    metadata = parser.map();
                    break;
                case AGENTS_FIELD:
                    agents = parser.map();
                    break;
                case ADDITIONAL_INFO_FIELD:
                    additionalInfo = parser.map();
                    break;
                case NAMESPACE_FIELD:
                    namespace = parser.mapStrings();
                    break;
                case TENANT_ID_FIELD:
                    tenantId = parser.text();
                    break;
                default:
                    parser.skipChildren();
                    break;
            }
        }

        return MLMemorySession
            .builder()
            .ownerId(ownerId)
            .memoryContainerId(memoryContainerId)
            .summary(summary)
            .createdTime(createdTime)
            .lastUpdateTime(lastUpdateTime)
            .metadata(metadata)
            .agents(agents)
            .additionalInfo(additionalInfo)
            .namespace(namespace)
            .tenantId(tenantId)
            .build();
    }

}
