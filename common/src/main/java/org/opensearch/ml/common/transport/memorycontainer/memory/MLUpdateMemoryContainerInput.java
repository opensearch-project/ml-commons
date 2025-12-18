/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.memorycontainer.memory;

import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;
import static org.opensearch.ml.common.CommonValue.BACKEND_ROLES_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.DESCRIPTION_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.NAME_FIELD;
import static org.opensearch.ml.common.transport.memorycontainer.MLCreateMemoryContainerInput.validateBackendRoles;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.common.io.stream.Writeable;
import org.opensearch.core.common.util.CollectionUtils;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.memorycontainer.MemoryConfiguration;

import lombok.Builder;
import lombok.Getter;

@Getter
public class MLUpdateMemoryContainerInput implements ToXContentObject, Writeable {
    public static final String MEMORY_CONFIG_FIELD = "configuration";

    private String name;
    private String description;
    private List<String> backendRoles;
    private MemoryConfiguration configuration;

    @Builder
    public MLUpdateMemoryContainerInput(String name, String description, List<String> backendRoles, MemoryConfiguration configuration) {
        this.name = name;
        this.description = description;
        validateBackendRoles(backendRoles);
        this.backendRoles = backendRoles;
        this.configuration = configuration;
    }

    public MLUpdateMemoryContainerInput(StreamInput in) throws IOException {
        this.name = in.readOptionalString();
        this.description = in.readOptionalString();
        if (in.readBoolean()) {
            backendRoles = in.readStringList();
        }
        if (in.readBoolean()) {
            this.configuration = new MemoryConfiguration(in);
        }
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeOptionalString(name);
        out.writeOptionalString(description);

        if (!CollectionUtils.isEmpty(backendRoles)) {
            out.writeBoolean(true);
            out.writeStringCollection(backendRoles);
        } else {
            out.writeBoolean(false);
        }

        if (configuration != null) {
            out.writeBoolean(true);
            configuration.writeTo(out);
        } else {
            out.writeBoolean(false);
        }
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, ToXContent.Params params) throws IOException {
        builder.startObject();
        if (name != null) {
            builder.field(NAME_FIELD, name);
        }
        if (description != null) {
            builder.field(DESCRIPTION_FIELD, description);
        }
        if (!CollectionUtils.isEmpty(backendRoles)) {
            builder.field(BACKEND_ROLES_FIELD, backendRoles);
        }
        if (configuration != null) {
            builder.field(MEMORY_CONFIG_FIELD, configuration);
        }
        builder.endObject();
        return builder;
    }

    public static MLUpdateMemoryContainerInput parse(XContentParser parser) throws IOException {
        String name = null;
        String description = null;
        List<String> backendRoles = null;
        MemoryConfiguration configuration = null;

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
                case MEMORY_CONFIG_FIELD:
                    configuration = MemoryConfiguration.parse(parser);
                    break;
                default:
                    parser.skipChildren();
                    break;
            }
        }

        return MLUpdateMemoryContainerInput
            .builder()
            .name(name)
            .description(description)
            .backendRoles(backendRoles)
            .configuration(configuration)
            .build();
    }

}
