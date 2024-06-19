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
import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.AccessMode;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;
import static org.opensearch.ml.common.CommonValue.TENANT_ID;

@Data
public class MLRegisterModelGroupInput implements ToXContentObject, Writeable{

    public static final String NAME_FIELD = "name"; //mandatory
    public static final String DESCRIPTION_FIELD = "description"; //optional
    public static final String BACKEND_ROLES_FIELD = "backend_roles"; //optional
    public static final String MODEL_ACCESS_MODE = "access_mode"; //optional
    public static final String ADD_ALL_BACKEND_ROLES = "add_all_backend_roles"; //optional

    private String name;
    private String description;
    private List<String> backendRoles;
    private AccessMode modelAccessMode;
    private Boolean isAddAllBackendRoles;
    private String tenantId;

    @Builder(toBuilder = true)
    public MLRegisterModelGroupInput(String name, String description, List<String> backendRoles, AccessMode modelAccessMode, Boolean isAddAllBackendRoles, String tenantId) {
        this.name = Objects.requireNonNull(name, "model group name must not be null");
        this.description = description;
        this.backendRoles = backendRoles;
        this.modelAccessMode = modelAccessMode;
        this.isAddAllBackendRoles = isAddAllBackendRoles;
        this.tenantId = tenantId;
    }

    public MLRegisterModelGroupInput(StreamInput in) throws IOException{
        this.name = in.readString();
        this.description = in.readOptionalString();
        this.backendRoles = in.readOptionalStringList();
        if (in.readBoolean()) {
            modelAccessMode = in.readEnum(AccessMode.class);
        }
        this.isAddAllBackendRoles = in.readOptionalBoolean();
        // TODO add BWC later
        this.tenantId = in.readOptionalString();
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeString(name);
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
        // TODO add BWC later
        out.writeOptionalString(tenantId);
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        builder.field(NAME_FIELD, name);
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
            builder.field(ADD_ALL_BACKEND_ROLES, isAddAllBackendRoles);
        }
        if (tenantId != null) {
            builder.field(TENANT_ID, tenantId);
        }
        builder.endObject();
        return builder;
    }

    public static MLRegisterModelGroupInput parse(XContentParser parser) throws IOException {
        String name = null;
        String description = null;
        List<String> backendRoles = null;
        AccessMode modelAccessMode = null;
        Boolean isAddAllBackendRoles = null;
        String tenantId = null;

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
                case ADD_ALL_BACKEND_ROLES:
                    isAddAllBackendRoles = parser.booleanValue();
                    break;
                case TENANT_ID:
                    tenantId = parser.textOrNull();
                default:
                    parser.skipChildren();
                    break;
            }
        }
        return new MLRegisterModelGroupInput(name, description, backendRoles, modelAccessMode, isAddAllBackendRoles, tenantId);
    }

}
