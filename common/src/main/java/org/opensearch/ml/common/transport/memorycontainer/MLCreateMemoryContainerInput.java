/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.memorycontainer;

import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;
import static org.opensearch.ml.common.CommonValue.TENANT_ID_FIELD;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.common.io.stream.Writeable;
import org.opensearch.core.common.util.CollectionUtils;
import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.memorycontainer.MemoryConfiguration;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MLCreateMemoryContainerInput implements ToXContentObject, Writeable {

    public static final String NAME_FIELD = "name";
    public static final String DESCRIPTION_FIELD = "description";
    public static final String MEMORY_CONFIG_FIELD = "configuration";
    public static final String BACKEND_ROLES_FIELD = "backend_roles";

    private String name;
    private String description;
    private MemoryConfiguration configuration;
    private String tenantId;
    private List<String> backendRoles;

    @Builder(toBuilder = true)
    public MLCreateMemoryContainerInput(
        String name,
        String description,
        MemoryConfiguration configuration,
        String tenantId,
        List<String> backendRoles
    ) {
        if (name == null) {
            throw new IllegalArgumentException("name is null");
        }
        this.name = name;
        this.description = description;
        if (configuration == null) {
            this.configuration = MemoryConfiguration.builder().build();
        } else {
            this.configuration = configuration;
        }
        this.tenantId = tenantId;
        this.backendRoles = backendRoles;
    }

    public MLCreateMemoryContainerInput(StreamInput in) throws IOException {
        this.name = in.readString();
        this.description = in.readOptionalString();
        if (in.readBoolean()) {
            this.configuration = new MemoryConfiguration(in);
        } else {
            this.configuration = null;
        }
        this.tenantId = in.readOptionalString();
        if (in.readBoolean()) {
            backendRoles = in.readStringList();
        }
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeString(name);
        out.writeOptionalString(description);
        if (configuration != null) {
            out.writeBoolean(true);
            configuration.writeTo(out);
        } else {
            out.writeBoolean(false);
        }
        out.writeOptionalString(tenantId);
        if (!CollectionUtils.isEmpty(backendRoles)) {
            out.writeBoolean(true);
            out.writeStringCollection(backendRoles);
        } else {
            out.writeBoolean(false);
        }
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        builder.field(NAME_FIELD, name);
        if (description != null) {
            builder.field(DESCRIPTION_FIELD, description);
        }
        if (configuration != null) {
            builder.field(MEMORY_CONFIG_FIELD, configuration);
        }
        if (tenantId != null) {
            builder.field(TENANT_ID_FIELD, tenantId);
        }
        if (!CollectionUtils.isEmpty(backendRoles)) {
            builder.field(BACKEND_ROLES_FIELD, backendRoles);
        }
        builder.endObject();
        return builder;
    }

    public static MLCreateMemoryContainerInput parse(XContentParser parser) throws IOException {
        String name = null;
        String description = null;
        MemoryConfiguration configuration = null;
        String tenantId = null;
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
                case MEMORY_CONFIG_FIELD:
                    configuration = MemoryConfiguration.parse(parser);
                    break;
                case TENANT_ID_FIELD:
                    tenantId = parser.text();
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

        return MLCreateMemoryContainerInput
            .builder()
            .name(name)
            .description(description)
            .configuration(configuration)
            .tenantId(tenantId)
            .backendRoles(backendRoles)
            .build();
    }
}
