/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.memorycontainer;

import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;
import static org.opensearch.ml.common.CommonValue.TENANT_ID_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.BACKEND_ROLES_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.CREATED_TIME_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.DESCRIPTION_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.LAST_UPDATED_TIME_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.MEMORY_STORAGE_CONFIG_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.NAME_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.OWNER_FIELD;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.opensearch.commons.authuser.User;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.common.io.stream.Writeable;
import org.opensearch.core.common.util.CollectionUtils;
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

    private String name;
    private String description;
    private User owner;
    private String tenantId;
    private Instant createdTime;
    private Instant lastUpdatedTime;
    private MemoryConfiguration configuration;
    private List<String> backendRoles;

    public MLMemoryContainer(
        String name,
        String description,
        User owner,
        String tenantId,
        Instant createdTime,
        Instant lastUpdatedTime,
        MemoryConfiguration configuration,
        List<String> backendRoles
    ) {
        this.name = name;
        this.description = description;
        this.owner = owner;
        this.tenantId = tenantId;
        this.createdTime = createdTime;
        this.lastUpdatedTime = lastUpdatedTime;
        this.configuration = configuration;
        this.backendRoles = backendRoles;
    }

    public MLMemoryContainer(StreamInput input) throws IOException {
        this.name = input.readOptionalString();
        this.description = input.readOptionalString();
        if (input.readBoolean()) {
            this.owner = new User(input);
        }
        this.tenantId = input.readOptionalString();
        this.createdTime = input.readOptionalInstant();
        this.lastUpdatedTime = input.readOptionalInstant();
        if (input.readBoolean()) {
            this.configuration = new MemoryConfiguration(input);
        }
        if (input.readBoolean()) {
            backendRoles = input.readStringList();
        }
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeOptionalString(name);
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
        if (configuration != null) {
            out.writeBoolean(true);
            configuration.writeTo(out);
        } else {
            out.writeBoolean(false);
        }
        if (!CollectionUtils.isEmpty(backendRoles)) {
            out.writeBoolean(true);
            out.writeStringCollection(backendRoles);
        } else {
            out.writeBoolean(false);
        }
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, ToXContent.Params params) throws IOException {
        builder.startObject();
        if (name != null) {
            builder.field(NAME_FIELD, name);
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
        if (configuration != null) {
            builder.field(MEMORY_STORAGE_CONFIG_FIELD, configuration);
        }
        if (!CollectionUtils.isEmpty(backendRoles)) {
            builder.field(BACKEND_ROLES_FIELD, backendRoles);
        }
        builder.endObject();
        return builder;
    }

    public static MLMemoryContainer parse(XContentParser parser) throws IOException {
        String name = null;
        String description = null;
        User owner = null;
        String tenantId = null;
        Instant createdTime = null;
        Instant lastUpdatedTime = null;
        MemoryConfiguration configuration = null;
        List<String> backendRoles = null;

        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.currentToken(), parser);
        while (parser.nextToken() != XContentParser.Token.END_OBJECT) {
            String fieldName = parser.currentName();
            parser.nextToken();

            switch (fieldName) {
                case NAME_FIELD:
                    name = parser.text();
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
                case MEMORY_STORAGE_CONFIG_FIELD:
                    configuration = MemoryConfiguration.parse(parser);
                    break;
                case BACKEND_ROLES_FIELD:
                    backendRoles = new ArrayList<>();
                    ensureExpectedToken(XContentParser.Token.START_ARRAY, parser.currentToken(), parser);
                    while (parser.nextToken() != XContentParser.Token.END_ARRAY) {
                        backendRoles.add(parser.text());
                    }
                    break;
                default:
                    parser.skipChildren();
                    break;
            }
        }

        return MLMemoryContainer
            .builder()
            .name(name)
            .description(description)
            .owner(owner)
            .tenantId(tenantId)
            .createdTime(createdTime)
            .lastUpdatedTime(lastUpdatedTime)
            .configuration(configuration)
            .backendRoles(backendRoles)
            .build();
    }
}
