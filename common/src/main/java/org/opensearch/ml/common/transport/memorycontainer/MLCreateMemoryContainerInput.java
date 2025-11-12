/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.memorycontainer;

import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;
import static org.opensearch.ml.common.CommonValue.BACKEND_ROLES_FIELD;
import static org.opensearch.ml.common.CommonValue.TENANT_ID_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.BACKEND_ROLE_EMPTY_ERROR;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.BACKEND_ROLE_INVALID_CHARACTERS_ERROR;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.BACKEND_ROLE_INVALID_LENGTH_ERROR;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.opensearch.OpenSearchParseException;
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
        validateBackendRoles(backendRoles);
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

    /**
     * Validates backend roles according to security constraints.
     * Each role must:
     * - Be at most 128 Unicode characters long
     * - Contain only alphanumeric characters and special symbols: :+=,.@-_/
     * - Not be empty or blank
     *
     * @param backendRoles List of backend role strings to validate (null/empty list is allowed)
     * @throws OpenSearchParseException if any role violates constraints
     */
    public static void validateBackendRoles(List<String> backendRoles) {
        if (backendRoles == null || backendRoles.isEmpty()) {
            return; // null or empty list is allowed
        }

        // Regex pattern: Unicode letters/digits + allowed special chars: :+=,.@-_/
        Pattern validPattern = Pattern.compile("^[\\p{L}\\p{N}:+=,.@\\-_/]+$");

        for (String role : backendRoles) {
            // Check for null or empty
            if (role == null || role.isEmpty() || role.isBlank()) {
                throw new OpenSearchParseException(BACKEND_ROLE_EMPTY_ERROR);
            }

            // Check length (Unicode character count)
            if (role.length() > 128) {
                throw new OpenSearchParseException(String.format(BACKEND_ROLE_INVALID_LENGTH_ERROR, role));
            }

            // Check allowed characters
            if (!validPattern.matcher(role).matches()) {
                throw new OpenSearchParseException(String.format(BACKEND_ROLE_INVALID_CHARACTERS_ERROR, role));
            }
        }
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
