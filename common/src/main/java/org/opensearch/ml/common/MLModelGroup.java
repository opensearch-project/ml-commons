/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common;

import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.opensearch.commons.authuser.User;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.common.util.CollectionUtils;
import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
public class MLModelGroup implements ToXContentObject {
    public static final String MODEL_GROUP_NAME_FIELD = "name"; // name of the model group
    public static final String DESCRIPTION_FIELD = "description"; // description of the model group
    public static final String LATEST_VERSION_FIELD = "latest_version"; // latest model version added to the model group
    public static final String BACKEND_ROLES_FIELD = "backend_roles"; // back_end roles as specified by the owner/admin
    public static final String OWNER = "owner"; // user who creates/owns the model group

    public static final String ACCESS = "access"; // assigned to public, private, or null when model group created
    public static final String MODEL_GROUP_ID_FIELD = "model_group_id"; // unique ID assigned to each model group
    public static final String CREATED_TIME_FIELD = "created_time"; // model group created time stamp
    public static final String LAST_UPDATED_TIME_FIELD = "last_updated_time"; // updated whenever a new model version is created

    @Setter
    private String name;
    private String description;
    private int latestVersion;
    private List<String> backendRoles;
    private User owner;

    private String access;

    private String modelGroupId;

    private Instant createdTime;
    private Instant lastUpdatedTime;

    @Builder(toBuilder = true)
    public MLModelGroup(
        String name,
        String description,
        int latestVersion,
        List<String> backendRoles,
        User owner,
        String access,
        String modelGroupId,
        Instant createdTime,
        Instant lastUpdatedTime
    ) {
        this.name = Objects.requireNonNull(name, "model group name must not be null");
        this.description = description;
        this.latestVersion = latestVersion;
        this.backendRoles = backendRoles;
        this.owner = owner;
        this.access = access;
        this.modelGroupId = modelGroupId;
        this.createdTime = createdTime;
        this.lastUpdatedTime = lastUpdatedTime;
    }

    public MLModelGroup(StreamInput input) throws IOException {
        name = input.readString();
        description = input.readOptionalString();
        latestVersion = input.readInt();
        if (input.readBoolean()) {
            backendRoles = input.readStringList();
        }
        if (input.readBoolean()) {
            this.owner = new User(input);
        } else {
            this.owner = null;
        }
        access = input.readOptionalString();
        modelGroupId = input.readOptionalString();
        createdTime = input.readOptionalInstant();
        lastUpdatedTime = input.readOptionalInstant();
    }

    public void writeTo(StreamOutput out) throws IOException {
        out.writeString(name);
        out.writeOptionalString(description);
        out.writeInt(latestVersion);
        if (!CollectionUtils.isEmpty(backendRoles)) {
            out.writeBoolean(true);
            out.writeStringCollection(backendRoles);
        } else {
            out.writeBoolean(false);
        }
        if (owner != null) {
            out.writeBoolean(true);
            owner.writeTo(out);
        } else {
            out.writeBoolean(false);
        }
        out.writeOptionalString(access);
        out.writeOptionalString(modelGroupId);
        out.writeOptionalInstant(createdTime);
        out.writeOptionalInstant(lastUpdatedTime);
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        builder.field(MODEL_GROUP_NAME_FIELD, name);
        builder.field(LATEST_VERSION_FIELD, latestVersion);
        if (description != null) {
            builder.field(DESCRIPTION_FIELD, description);
        }
        if (!CollectionUtils.isEmpty(backendRoles)) {
            builder.field(BACKEND_ROLES_FIELD, backendRoles);
        }
        if (owner != null) {
            builder.field(OWNER, owner);
        }
        if (access != null) {
            builder.field(ACCESS, access);
        }
        if (modelGroupId != null) {
            builder.field(MODEL_GROUP_ID_FIELD, modelGroupId);
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

    public static MLModelGroup parse(XContentParser parser) throws IOException {
        String name = null;
        String description = null;
        List<String> backendRoles = null;
        int latestVersion = 0;
        User owner = null;
        String access = null;
        String modelGroupId = null;
        Instant createdTime = null;
        Instant lastUpdateTime = null;

        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.currentToken(), parser);
        while (parser.nextToken() != XContentParser.Token.END_OBJECT) {
            String fieldName = parser.currentName();
            parser.nextToken();

            switch (fieldName) {
                case MODEL_GROUP_NAME_FIELD:
                    name = parser.text();
                    break;
                case DESCRIPTION_FIELD:
                    description = parser.text();
                    break;
                case BACKEND_ROLES_FIELD:
                    backendRoles = new ArrayList<>();
                    ensureExpectedToken(XContentParser.Token.START_ARRAY, parser.currentToken(), parser);
                    while (parser.nextToken() != XContentParser.Token.END_ARRAY) {
                        backendRoles.add(parser.text());
                    }
                    break;
                case LATEST_VERSION_FIELD:
                    latestVersion = parser.intValue();
                    break;
                case OWNER:
                    owner = User.parse(parser);
                    break;
                case ACCESS:
                    access = parser.text();
                    break;
                case MODEL_GROUP_ID_FIELD:
                    modelGroupId = parser.text();
                    break;
                case CREATED_TIME_FIELD:
                    createdTime = Instant.ofEpochMilli(parser.longValue());
                    break;
                case LAST_UPDATED_TIME_FIELD:
                    lastUpdateTime = Instant.ofEpochMilli(parser.longValue());
                    break;
                default:
                    parser.skipChildren();
                    break;
            }
        }
        return MLModelGroup
            .builder()
            .name(name)
            .description(description)
            .backendRoles(backendRoles)
            .latestVersion(latestVersion)
            .owner(owner)
            .access(access)
            .modelGroupId(modelGroupId)
            .createdTime(createdTime)
            .lastUpdatedTime(lastUpdateTime)
            .build();
    }

    public static MLModelGroup fromStream(StreamInput in) throws IOException {
        MLModelGroup mlModel = new MLModelGroup(in);
        return mlModel;
    }
}
