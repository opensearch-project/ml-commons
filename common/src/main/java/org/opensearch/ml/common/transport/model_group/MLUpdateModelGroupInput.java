/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.model_group;

import lombok.Builder;
import lombok.Data;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.common.io.stream.Writeable;
import org.opensearch.core.common.util.CollectionUtils;
import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.AccessMode;
import org.opensearch.ml.common.model.ModelGroupTag;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;

@Data
public class MLUpdateModelGroupInput implements ToXContentObject, Writeable {

    public static final String MODEL_GROUP_ID_FIELD = "model_group_id"; //mandatory
    public static final String NAME_FIELD = "name"; //optional
    public static final String DESCRIPTION_FIELD = "description"; //optional
    public static final String BACKEND_ROLES_FIELD = "backend_roles"; //optional
    public static final String MODEL_ACCESS_MODE = "access_mode"; //optional
    public static final String ADD_ALL_BACKEND_ROLES_FIELD = "add_all_backend_roles"; //optional
    public static final String TAGS_FIELD = "tags"; //optional

    private String modelGroupID;
    private String name;
    private String description;
    private List<String> backendRoles;
    private AccessMode modelAccessMode;
    private Boolean isAddAllBackendRoles;
    private List<ModelGroupTag> tags;

    @Builder(toBuilder = true)
    public MLUpdateModelGroupInput(String modelGroupID, String name, String description, List<String> backendRoles, AccessMode modelAccessMode, Boolean isAddAllBackendRoles,List<ModelGroupTag> tags) {
        this.modelGroupID = modelGroupID;
        this.name = name;
        this.description = description;
        this.backendRoles = backendRoles;
        this.modelAccessMode = modelAccessMode;
        this.isAddAllBackendRoles = isAddAllBackendRoles;
        this.tags = tags;
    }

    public MLUpdateModelGroupInput(StreamInput in) throws IOException {
        this.modelGroupID = in.readString();
        this.name = in.readOptionalString();
        this.description = in.readOptionalString();
        this.backendRoles = in.readOptionalStringList();
        if (in.readBoolean()) {
            modelAccessMode = in.readEnum(AccessMode.class);
        }
        this.isAddAllBackendRoles = in.readOptionalBoolean();
        this.tags=in.readList(ModelGroupTag::new);
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
        if (backendRoles != null && backendRoles.size() > 0) {
            builder.field(BACKEND_ROLES_FIELD, backendRoles);
        }
        if (modelAccessMode != null) {
            builder.field(MODEL_ACCESS_MODE, modelAccessMode);
        }
        if (isAddAllBackendRoles != null) {
            builder.field(ADD_ALL_BACKEND_ROLES_FIELD, isAddAllBackendRoles);
        }
        if(!CollectionUtils.isEmpty(tags)){
            builder.field(TAGS_FIELD, tags);
        }
        builder.endObject();
        return builder;
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeString(modelGroupID);
        out.writeOptionalString(name);
        out.writeOptionalString(description);
        if (backendRoles != null) {
            out.writeBoolean(true);
            out.writeStringCollection(backendRoles);
        } else {
            out.writeBoolean(false);
        }
        if (modelAccessMode != null) {
            out.writeBoolean(true);
            out.writeEnum(modelAccessMode);
        } else {
            out.writeBoolean(false);
        }
        out.writeOptionalBoolean(isAddAllBackendRoles);
        if(!CollectionUtils.isEmpty(tags)){
            out.writeList(tags);
        }
    }

    public static MLUpdateModelGroupInput parse(XContentParser parser) throws IOException {
        String modelGroupID = null;
        String name = null;
        String description = null;
        List<String> backendRoles = null;
        AccessMode modelAccessMode = null;
        Boolean isAddAllBackendRoles = null;
        List<ModelGroupTag> tags=null;

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
                case BACKEND_ROLES_FIELD:
                    backendRoles = new ArrayList<>();
                    ensureExpectedToken(XContentParser.Token.START_ARRAY, parser.currentToken(), parser);
                    while (parser.nextToken() != XContentParser.Token.END_ARRAY) {
                        backendRoles.add(parser.text());
                    }
                    break;
                case MODEL_ACCESS_MODE:
                    modelAccessMode = AccessMode.from(parser.text().toLowerCase(Locale.ROOT));
                    break;
                case ADD_ALL_BACKEND_ROLES_FIELD:
                    isAddAllBackendRoles = parser.booleanValue();
                    break;
                case TAGS_FIELD:
                    tags = new ArrayList<>();
                    ensureExpectedToken(XContentParser.Token.START_ARRAY, parser.currentToken(), parser);
                    while (parser.nextToken() != XContentParser.Token.END_ARRAY) {
                            tags.add(ModelGroupTag.parse(parser));
                    }
                    break;
                default:
                    parser.skipChildren();
                    break;
            }
        }
        return new MLUpdateModelGroupInput(modelGroupID, name, description, backendRoles, modelAccessMode, isAddAllBackendRoles,tags);
    }
}
