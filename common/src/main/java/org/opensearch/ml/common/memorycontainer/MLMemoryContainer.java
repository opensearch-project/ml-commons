/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.memorycontainer;

import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.CONTAINER_ID_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.CONTAINER_NAME_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.CREATED_TIME_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.DESCRIPTION_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.INDEX_NAME_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.LAST_UPDATED_TIME_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.OWNER_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.SEMANTIC_STORAGE_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.TENANT_ID_FIELD;

import java.io.IOException;
import java.time.Instant;

import org.opensearch.commons.authuser.User;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.common.io.stream.Writeable;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

/**
 * ML Memory Container data model that stores metadata about memory-related objects
 */
@Getter
@Setter
@Builder
@EqualsAndHashCode
public class MLMemoryContainer implements ToXContentObject, Writeable {

    private String containerId;
    private String containerName;
    private String description;
    private User owner;
    private String tenantId;
    private Instant createdTime;
    private Instant lastUpdatedTime;
    private String indexName;
    private SemanticStorageConfig semanticStorage;

    public MLMemoryContainer(
        String containerId,
        String containerName,
        String description,
        User owner,
        String tenantId,
        Instant createdTime,
        Instant lastUpdatedTime,
        String indexName,
        SemanticStorageConfig semanticStorage
    ) {
        this.containerId = containerId;
        this.containerName = containerName;
        this.description = description;
        this.owner = owner;
        this.tenantId = tenantId;
        this.createdTime = createdTime;
        this.lastUpdatedTime = lastUpdatedTime;
        this.indexName = indexName;
        this.semanticStorage = semanticStorage;
    }

    public MLMemoryContainer(StreamInput input) throws IOException {
        this.containerId = input.readOptionalString();
        this.containerName = input.readOptionalString();
        this.description = input.readOptionalString();
        if (input.readBoolean()) {
            this.owner = new User(input);
        }
        this.tenantId = input.readOptionalString();
        this.createdTime = input.readOptionalInstant();
        this.lastUpdatedTime = input.readOptionalInstant();
        this.indexName = input.readOptionalString();
        if (input.readBoolean()) {
            this.semanticStorage = new SemanticStorageConfig(input);
        }
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeOptionalString(containerId);
        out.writeOptionalString(containerName);
        out.writeOptionalString(description);
        if (owner != null) {
            out.writeBoolean(true);
            owner.writeTo(out);
        } else {
            out.writeBoolean(false);
        }
        out.writeOptionalString(tenantId);
        out.writeOptionalInstant(createdTime);
        out.writeOptionalInstant(lastUpdatedTime);
        out.writeOptionalString(indexName);
        if (semanticStorage != null) {
            out.writeBoolean(true);
            semanticStorage.writeTo(out);
        } else {
            out.writeBoolean(false);
        }
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, ToXContent.Params params) throws IOException {
        builder.startObject();
        if (containerId != null) {
            builder.field(CONTAINER_ID_FIELD, containerId);
        }
        if (containerName != null) {
            builder.field(CONTAINER_NAME_FIELD, containerName);
        }
        if (description != null) {
            builder.field(DESCRIPTION_FIELD, description);
        }
        if (owner != null) {
            builder.field(OWNER_FIELD, owner);
        }
        if (tenantId != null) {
            builder.field(TENANT_ID_FIELD, tenantId);
        }
        if (createdTime != null) {
            builder.field(CREATED_TIME_FIELD, createdTime.toEpochMilli());
        }
        if (lastUpdatedTime != null) {
            builder.field(LAST_UPDATED_TIME_FIELD, lastUpdatedTime.toEpochMilli());
        }
        if (indexName != null) {
            builder.field(INDEX_NAME_FIELD, indexName);
        }
        if (semanticStorage != null) {
            builder.field(SEMANTIC_STORAGE_FIELD, semanticStorage);
        }
        builder.endObject();
        return builder;
    }

    public static MLMemoryContainer parse(XContentParser parser) throws IOException {
        String containerId = null;
        String containerName = null;
        String description = null;
        User owner = null;
        String tenantId = null;
        Instant createdTime = null;
        Instant lastUpdatedTime = null;
        String indexName = null;
        SemanticStorageConfig semanticStorage = null;

        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.currentToken(), parser);
        while (parser.nextToken() != XContentParser.Token.END_OBJECT) {
            String fieldName = parser.currentName();
            parser.nextToken();

            switch (fieldName) {
                case CONTAINER_ID_FIELD:
                    containerId = parser.text();
                    break;
                case CONTAINER_NAME_FIELD:
                    containerName = parser.text();
                    break;
                case DESCRIPTION_FIELD:
                    description = parser.text();
                    break;
                case OWNER_FIELD:
                    owner = User.parse(parser);
                    break;
                case TENANT_ID_FIELD:
                    tenantId = parser.text();
                    break;
                case CREATED_TIME_FIELD:
                    createdTime = Instant.ofEpochMilli(parser.longValue());
                    break;
                case LAST_UPDATED_TIME_FIELD:
                    lastUpdatedTime = Instant.ofEpochMilli(parser.longValue());
                    break;
                case INDEX_NAME_FIELD:
                    indexName = parser.text();
                    break;
                case SEMANTIC_STORAGE_FIELD:
                    semanticStorage = SemanticStorageConfig.parse(parser);
                    break;
                default:
                    parser.skipChildren();
                    break;
            }
        }

        return MLMemoryContainer
            .builder()
            .containerId(containerId)
            .containerName(containerName)
            .description(description)
            .owner(owner)
            .tenantId(tenantId)
            .createdTime(createdTime)
            .lastUpdatedTime(lastUpdatedTime)
            .indexName(indexName)
            .semanticStorage(semanticStorage)
            .build();
    }
}
