/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import org.opensearch.common.io.stream.StreamInput;
import org.opensearch.common.io.stream.StreamOutput;
import org.opensearch.commons.authuser.User;
import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.opensearch.common.xcontent.XContentParserUtils.ensureExpectedToken;

@Getter
public class MLModelGroup implements ToXContentObject {
    public static final String MODEL_GROUP_NAME_FIELD = "name"; //name of the model group
    // We use int type for version in first release 1.3. In 2.4, we changed to
    // use String type for version. Keep this old version field for old models.
    public static final String DESCRIPTION_FIELD = "description"; //description of the model group
    public static final String TAGS_FIELD = "tags"; //specified by the owner from pre-existing tags in the system
    public static final String LATEST_VERSION_FIELD = "latest_version"; //latest model version added to the model group
    public static final String BACKEND_ROLES_FIELD = "backend_roles"; //back_end roles as specified by the owner/admin
    public static final String OWNER = "owner"; //user who creates/owns the model group

    public static final String ACCESS = "access"; //assigned to public, private, or null when model group created
    public static final String PRIVATE = "private";
    public static final String PUBLIC = "public";
    public static final String MODEL_GROUP_ID_FIELD = "model_group_id"; //unique ID assigned to each model group
    public static final String CREATED_TIME_FIELD = "created_time"; //model group created time stamp
    public static final String LAST_UPDATED_TIME_FIELD = "last_updated_time"; //updated whenever a new model version is created
    //SHA256 hash value of model content.

    @Setter
    private String name;
    private String description;
    private Map<String, Object> tags;
    private int latestVersion = 0;
    private List<String> backendRoles;
    private User owner;

    private String access;

    private String modelGroupId;

    private Instant createdTime;
    private Instant lastUpdateTime;


    @Builder(toBuilder = true)
    public MLModelGroup(String name, String description, Map<String, Object> tags, int latestVersion,
                        List<String> backendRoles, User owner, String access,
                        String modelGroupId,
                        Instant createdTime,
                        Instant lastUpdateTime) {
        this.name = name;
        this.description = description;
        this.tags = tags;
        this.latestVersion = latestVersion;
        this.backendRoles = backendRoles;
        this.owner = owner;
        this.access = access;
        this.modelGroupId = modelGroupId;
        this.createdTime = createdTime;
        this.lastUpdateTime = lastUpdateTime;
    }


    public MLModelGroup(StreamInput input) throws IOException{
        name = input.readString();
        description = input.readOptionalString();
        if (input.readBoolean()) {
            tags = input.readMap();
        }
        latestVersion = input.readInt();
        backendRoles = input.readOptionalStringList();
        if (input.readBoolean()) {
            this.owner = new User(input);
        } else {
            this.owner = null;
        }
        access = input.readOptionalString();
        modelGroupId = input.readOptionalString();
        createdTime = input.readOptionalInstant();
        lastUpdateTime = input.readOptionalInstant();
    }

    public void writeTo(StreamOutput out) throws IOException {
        out.writeString(name);
        out.writeOptionalString(description);
        if (tags != null) {
            out.writeBoolean(true);
            out.writeMap(tags);
        } else {
            out.writeBoolean(false);
        }
        out.writeInt(latestVersion);
        out.writeStringCollection(backendRoles);
        if (owner != null) {
            owner.writeTo(out);
        } else {
            out.writeBoolean(false);
        }
        out.writeOptionalString(access);
        out.writeOptionalString(modelGroupId);
        out.writeOptionalInstant(createdTime);
        out.writeOptionalInstant(lastUpdateTime);
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        builder.field(MODEL_GROUP_NAME_FIELD, name);
        builder.field(LATEST_VERSION_FIELD, latestVersion);
        if (description != null) {
            builder.field(DESCRIPTION_FIELD, description);
        }
        if (tags != null && tags.size() > 0) {
            builder.field(TAGS_FIELD, tags);
        }
        if (backendRoles != null) {
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
        if (lastUpdateTime != null) {
            builder.field(LAST_UPDATED_TIME_FIELD, lastUpdateTime.toEpochMilli());
        }
        builder.endObject();
        return builder;
    }

    public static MLModelGroup parse(XContentParser parser) throws IOException {
        String name = null;
        String description = null;
        Map<String, Object> tags = new HashMap<>();
        List<String> backendRoles = new ArrayList<>();
        Integer latestVersion = null;
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
                case TAGS_FIELD:
                    tags = parser.map();
                    break;
                case BACKEND_ROLES_FIELD:
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
        return MLModelGroup.builder()
                .name(name)
                .description(description)
                .tags(tags)
                .backendRoles(backendRoles)
                .latestVersion(latestVersion)
                .owner(owner)
                .access(access)
                .modelGroupId(modelGroupId)
                .createdTime(createdTime)
                .lastUpdateTime(lastUpdateTime)
                .build();
    }


    public static MLModelGroup fromStream(StreamInput in) throws IOException {
        MLModelGroup mlModel = new MLModelGroup(in);
        return mlModel;
    }
}
