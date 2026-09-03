/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.model_group;

import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;
import static org.opensearch.ml.common.CommonValue.TENANT_ID_FIELD;
import static org.opensearch.ml.common.CommonValue.VERSION_2_19_0;
import static org.opensearch.ml.common.CommonValue.VERSION_3_9_0;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import org.opensearch.Version;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.common.io.stream.Writeable;
import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.AccessMode;

import lombok.Builder;
import lombok.Data;

@Data
public class MLRegisterModelGroupInput implements ToXContentObject, Writeable {

    public static final String NAME_FIELD = "name"; // mandatory
    public static final String DESCRIPTION_FIELD = "description"; // optional
    public static final String BACKEND_ROLES_FIELD = "backend_roles"; // optional
    public static final String MODEL_ACCESS_MODE = "access_mode"; // optional
    public static final String ADD_ALL_BACKEND_ROLES = "add_all_backend_roles"; // optional

    public static final Version MINIMAL_SUPPORTED_VERSION_FOR_CUSTOM_MODEL_GROUP_ID = VERSION_3_9_0;

    private String name;
    private String description;
    private List<String> backendRoles;
    private AccessMode modelAccessMode;
    private Boolean isAddAllBackendRoles;
    private String tenantId;
    private String modelGroupId;

    @Builder(toBuilder = true)
    public MLRegisterModelGroupInput(
        String name,
        String description,
        List<String> backendRoles,
        AccessMode modelAccessMode,
        Boolean isAddAllBackendRoles,
        String tenantId,
        String modelGroupId
    ) {
        this.name = Objects.requireNonNull(name, "model group name must not be null");
        this.description = description;
        this.backendRoles = backendRoles;
        this.modelAccessMode = modelAccessMode;
        this.isAddAllBackendRoles = isAddAllBackendRoles;
        this.tenantId = tenantId;
        this.modelGroupId = modelGroupId;
    }

    public MLRegisterModelGroupInput(
        String name,
        String description,
        List<String> backendRoles,
        AccessMode modelAccessMode,
        Boolean isAddAllBackendRoles,
        String tenantId
    ) {
        this(name, description, backendRoles, modelAccessMode, isAddAllBackendRoles, tenantId, null);
    }

    public MLRegisterModelGroupInput(StreamInput in) throws IOException {
        Version streamInputVersion = in.getVersion();
        this.name = in.readString();
        this.description = in.readOptionalString();
        this.backendRoles = in.readOptionalStringList();
        if (in.readBoolean()) {
            modelAccessMode = in.readEnum(AccessMode.class);
        }
        this.isAddAllBackendRoles = in.readOptionalBoolean();
        this.tenantId = streamInputVersion.onOrAfter(VERSION_2_19_0) ? in.readOptionalString() : null;
        this.modelGroupId = streamInputVersion.onOrAfter(MINIMAL_SUPPORTED_VERSION_FOR_CUSTOM_MODEL_GROUP_ID)
            ? in.readOptionalString()
            : null;
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        Version streamOutputVersion = out.getVersion();
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
        if (streamOutputVersion.onOrAfter(VERSION_2_19_0)) {
            out.writeOptionalString(tenantId);
        }
        if (streamOutputVersion.onOrAfter(MINIMAL_SUPPORTED_VERSION_FOR_CUSTOM_MODEL_GROUP_ID)) {
            out.writeOptionalString(modelGroupId);
        }
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
            builder.field(TENANT_ID_FIELD, tenantId);
        }
        if (modelGroupId != null) {
            builder.field(MLRegisterModelGroupResponse.MODEL_GROUP_ID_FIELD, modelGroupId);
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
        String modelGroupId = null;

        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.currentToken(), parser);
        while (parser.nextToken() != XContentParser.Token.END_OBJECT) {
            String fieldName = parser.currentName();
            parser.nextToken();
            switch (fieldName) {
                case MLRegisterModelGroupResponse.MODEL_GROUP_ID_FIELD:
                    modelGroupId = parser.text();
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
                case ADD_ALL_BACKEND_ROLES:
                    isAddAllBackendRoles = parser.booleanValue();
                    break;
                case TENANT_ID_FIELD:
                    tenantId = parser.textOrNull();
                    break;
                default:
                    parser.skipChildren();
                    break;
            }
        }
        return new MLRegisterModelGroupInput(
            name,
            description,
            backendRoles,
            modelAccessMode,
            isAddAllBackendRoles,
            tenantId,
            modelGroupId
        );
    }
}
