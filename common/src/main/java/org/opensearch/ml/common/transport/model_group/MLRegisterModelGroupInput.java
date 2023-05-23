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
import org.opensearch.ml.common.ModelAccessIdentifier;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.opensearch.common.xcontent.XContentParserUtils.ensureExpectedToken;

@Data
public class MLRegisterModelGroupInput implements ToXContentObject, Writeable{

    public static final String NAME_FIELD = "name"; //mandatory
    public static final String DESCRIPTION_FIELD = "description"; //optional
    public static final String TAGS_FIELD = "tags"; //optional
    public static final String BACKEND_ROLES_FIELD = "backend_roles"; //optional
    public static final String MODEL_ACCESS_IDENTIFIER = "model_access_identifier"; //mandatory when security and model access control enabled.
    public static final String ADD_ALL_BACKEND_ROLES = "add_all_backend_roles"; //optional

    private String name;
    private String description;
    private Map<String, Object> tags;
    private List<String> backendRoles;
    private ModelAccessIdentifier modelAccessIdentifier;
    private Boolean isAddAllBackendRoles;

    @Builder(toBuilder = true)
    public MLRegisterModelGroupInput(String name, String description, Map<String, Object> tags, List<String> backendRoles, ModelAccessIdentifier modelAccessIdentifier, Boolean isAddAllBackendRoles) {
        this.name = name;
        this.description = description;
        this.tags = tags;
        this.backendRoles = backendRoles;
        this.modelAccessIdentifier = modelAccessIdentifier;
        this.isAddAllBackendRoles = isAddAllBackendRoles;
    }

    public MLRegisterModelGroupInput(StreamInput in) throws IOException{
        this.name = in.readString();
        this.description = in.readOptionalString();
        if (in.readBoolean()) {
            tags = in.readMap();
        }
        this.backendRoles = in.readOptionalStringList();
        this.modelAccessIdentifier = in.readEnum(ModelAccessIdentifier.class);
        this.isAddAllBackendRoles = in.readOptionalBoolean();
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeString(name);
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
        out.writeEnum(modelAccessIdentifier);
        out.writeOptionalBoolean(isAddAllBackendRoles);
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        builder.field(NAME_FIELD, name);
        if (description != null) {
            builder.field(DESCRIPTION_FIELD, description);
        }
        if (tags != null && tags.size() > 0) {
            builder.field(TAGS_FIELD, tags);
        }
        if (backendRoles != null && backendRoles.size() > 0) {
            builder.field(BACKEND_ROLES_FIELD, backendRoles);
        }
        builder.field(MODEL_ACCESS_IDENTIFIER, modelAccessIdentifier);
        if (isAddAllBackendRoles != null) {
            builder.field(ADD_ALL_BACKEND_ROLES, isAddAllBackendRoles);
        }
        builder.endObject();
        return builder;
    }

    public static MLRegisterModelGroupInput parse(XContentParser parser) throws IOException {
        String name = null;
        String description = null;
        Map<String, Object> tags = null;
        List<String> backendRoles = null;
        ModelAccessIdentifier modelAccessIdentifier = null;
        Boolean isAddAllBackendRoles = null;

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
                case MODEL_ACCESS_IDENTIFIER:
                    modelAccessIdentifier = ModelAccessIdentifier.from(parser.text());
                    break;
                case ADD_ALL_BACKEND_ROLES:
                    isAddAllBackendRoles = parser.booleanValue();
                    break;
                default:
                    parser.skipChildren();
                    break;
            }
        }
        return new MLRegisterModelGroupInput(name, description, tags, backendRoles, modelAccessIdentifier, isAddAllBackendRoles);
    }

}
