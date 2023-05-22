/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.model_group;

import lombok.Builder;
import lombok.Data;
import org.opensearch.common.io.stream.StreamInput;
import org.opensearch.common.io.stream.StreamOutput;
import org.opensearch.common.io.stream.Writeable;
import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.opensearch.common.xcontent.XContentParserUtils.ensureExpectedToken;

@Data
public class MLUpdateModelGroupInput implements ToXContentObject, Writeable {

    public static final String MODEL_GROUP_ID_FIELD = "model_group_id"; //mandatory
    public static final String NAME_FIELD = "name"; //optional
    public static final String DESCRIPTION_FIELD = "description"; //optional
    public static final String TAGS_FIELD = "tags"; //optional
    public static final String BACKEND_ROLES_FIELD = "backend_roles"; //optional
    public static final String IS_PUBLIC_FIELD = "is_public"; //optional
    public static final String ADD_ALL_BACKEND_ROLES_FIELD = "add_all_backend_roles"; //optional


    private String modelGroupID;
    private String name;
    private String description;
    private Map<String, Object> tags;
    private List<String> backendRoles;
    private Boolean isPublic;
    private Boolean isAddAllBackendRoles;

    @Builder(toBuilder = true)
    public MLUpdateModelGroupInput(String modelGroupID, String name, String description, Map<String, Object> tags, List<String> backendRoles, Boolean isPublic, Boolean isAddAllBackendRoles) {
        this.modelGroupID = modelGroupID;
        this.name = name;
        this.description = description;
        this.tags = tags;
        this.backendRoles = backendRoles;
        this.isPublic = isPublic;
        this.isAddAllBackendRoles = isAddAllBackendRoles;
    }

    public MLUpdateModelGroupInput(StreamInput in) throws IOException {
        this.modelGroupID = in.readString();
        this.name = in.readOptionalString();
        this.description = in.readOptionalString();
        if (in.readBoolean()) {
            tags = in.readMap();
        }
        this.backendRoles = in.readOptionalStringList();
        this.isPublic = in.readOptionalBoolean();
        this.isAddAllBackendRoles = in.readOptionalBoolean();
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        builder.field(MODEL_GROUP_ID_FIELD, modelGroupID);
        if (name != null) {
            builder.field(NAME_FIELD, name);
        }
        if (description != null) {
            builder.field(DESCRIPTION_FIELD, description);
        }
        if (tags != null && tags.size() > 0) {
            builder.field(TAGS_FIELD, tags);
        }
        if (backendRoles != null && backendRoles.size() > 0) {
            builder.field(BACKEND_ROLES_FIELD, backendRoles);
        }
        if (isPublic != null) {
            builder.field(IS_PUBLIC_FIELD, isPublic);
        }
        if (isAddAllBackendRoles != null) {
            builder.field(ADD_ALL_BACKEND_ROLES_FIELD, isAddAllBackendRoles);
        }
        builder.endObject();
        return builder;
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeString(modelGroupID);
        out.writeOptionalString(name);
        out.writeOptionalString(description);
        if (tags != null) {
            out.writeBoolean(true);
            out.writeMap(tags);
        } else {
            out.writeBoolean(false);
        }
        if (backendRoles != null) {
            out.writeBoolean(true);
            out.writeStringCollection(backendRoles);
        } else {
            out.writeBoolean(false);
        }
        out.writeOptionalBoolean(isPublic);
        out.writeOptionalBoolean(isAddAllBackendRoles);
    }

    public static MLUpdateModelGroupInput parse(XContentParser parser) throws IOException {
        String modelGroupID = null;
        String name = null;
        String description = null;
        Map<String, Object> tags = null;
        List<String> backendRoles = null;
        Boolean isPublic = null;
        Boolean isAddAllBackendRoles = null;

        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.currentToken(), parser);
        while (parser.nextToken() != XContentParser.Token.END_OBJECT) {
            String fieldName = parser.currentName();
            parser.nextToken();
            switch (fieldName) {
                case MODEL_GROUP_ID_FIELD:
                    modelGroupID = parser.text();
                    break;
                case NAME_FIELD:
                    name = parser.text();
                    break;
                case DESCRIPTION_FIELD:
                    description = parser.text();
                    break;
                case TAGS_FIELD:
                    tags = parser.map();
                    break;
                case BACKEND_ROLES_FIELD:
                    backendRoles = new ArrayList<>();
                    ensureExpectedToken(XContentParser.Token.START_ARRAY, parser.currentToken(), parser);
                    while (parser.nextToken() != XContentParser.Token.END_ARRAY) {
                        backendRoles.add(parser.text());
                    }
                    break;
                case IS_PUBLIC_FIELD:
                    isPublic = parser.booleanValue();
                    break;
                case ADD_ALL_BACKEND_ROLES_FIELD:
                    isAddAllBackendRoles = parser.booleanValue();
                    break;
                default:
                    parser.skipChildren();
                    break;
            }
        }
        return new MLUpdateModelGroupInput(modelGroupID, name, description, tags, backendRoles, isPublic, isAddAllBackendRoles);
    }
}
