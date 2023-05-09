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

import static org.opensearch.common.xcontent.XContentParserUtils.ensureExpectedToken;

@Data
public class MLUpdateModelGroupInput implements ToXContentObject, Writeable {

    public static final String MODEL_GROUP_ID = "model_group_id";
    public static final String NAME = "name";
    public static final String DESCRIPTION = "description";
    public static final String BACKEND_ROLES = "backend_roles";


    private String modelGroupID;
    private String name;
    private String description;
    private List<String> backendRoles;

    @Builder(toBuilder = true)
    public MLUpdateModelGroupInput(String modelGroupID, String name, String description, List<String> backendRoles) {
        this.modelGroupID = modelGroupID;
        this.name = name;
        this.description = description;
        this.backendRoles = backendRoles;
    }

    public MLUpdateModelGroupInput(StreamInput in) throws IOException {
        this.modelGroupID = in.readString();
        this.name = in.readString();
        this.description = in.readOptionalString();
        if (in.readBoolean()) {
            backendRoles = in.readStringList();
        }
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        builder.field(MODEL_GROUP_ID, modelGroupID);
        builder.field(NAME, name);
        if (description != null) {
            builder.field(DESCRIPTION, description);
        }
        if (backendRoles != null && backendRoles.size() > 0) {
            builder.field(BACKEND_ROLES, backendRoles);
        }
        builder.endObject();
        return builder;
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeString(modelGroupID);
        out.writeString(name);
        out.writeOptionalString(description);
        if (backendRoles != null) {
            out.writeBoolean(true);
            out.writeStringCollection(backendRoles);
        } else {
            out.writeBoolean(false);
        }

    }

    public static MLUpdateModelGroupInput parse(XContentParser parser) throws IOException {
        String modelGroupID = null;
        String name = null;
        String description = null;
        List<String> backendRoles = null;

        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.currentToken(), parser);
        while (parser.nextToken() != XContentParser.Token.END_OBJECT) {
            String fieldName = parser.currentName();
            parser.nextToken();
            switch (fieldName) {
                case MODEL_GROUP_ID:
                    modelGroupID = parser.text();
                    break;
                case NAME:
                    name = parser.text();
                    break;
                case DESCRIPTION:
                    description = parser.text();
                    break;
                case BACKEND_ROLES:
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
        return new MLUpdateModelGroupInput(modelGroupID, name, description, backendRoles);
    }
}
