/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.opensearch.ml.common;

import lombok.Builder;
import lombok.Getter;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.common.io.stream.Writeable;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;

import java.io.IOException;

import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;


public class ToolMetadata implements ToXContentObject, Writeable {

    public static final String TOOL_NAME_FIELD = "name";
    public static final String TOOL_DESCRIPTION_FIELD = "description";
    public static final String TOOL_TYPE_FIELD = "type";
    public static final String TOOL_VERSION_FIELD = "version";


    @Getter
    private String name;
    @Getter
    private String description;
    @Getter
    private String type;
    @Getter
    private String version;

    @Builder(toBuilder = true)
    public ToolMetadata(String name, String description, String type, String version) {
        this.name = name;
        this.description = description;
        this.type = type;
        this.version = version;
    }

    public ToolMetadata(StreamInput input) throws IOException {
        name = input.readString();
        description = input.readString();
        type = input.readString();
        version = input.readOptionalString();
    }

    public void writeTo(StreamOutput output) throws IOException {
        output.writeString(name);
        output.writeString(description);
        output.writeString(type);
        output.writeOptionalString(version);
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, ToXContent.Params params) throws IOException {
        builder.startObject();
        if (name != null) {
            builder.field(TOOL_NAME_FIELD, name);
        }
        if (description != null) {
            builder.field(TOOL_DESCRIPTION_FIELD, description);
        }
        if (type != null) {
            builder.field(TOOL_TYPE_FIELD, type);
        }
        builder.field(TOOL_VERSION_FIELD, version != null ? version : "undefined");
        builder.endObject();
        return builder;
    }

    public static ToolMetadata parse(XContentParser parser) throws IOException {
        String name = null;
        String description = null;
        String type = null;
        String version = null;

        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.currentToken(), parser);
        while (parser.nextToken() != XContentParser.Token.END_OBJECT) {
            String fieldName = parser.currentName();
            parser.nextToken();

            switch (fieldName) {
                case TOOL_NAME_FIELD:
                    name = parser.text();
                    break;
                case TOOL_DESCRIPTION_FIELD:
                    description = parser.text();
                    break;
                case TOOL_TYPE_FIELD:
                    type = parser.text();
                    break;
                case TOOL_VERSION_FIELD:
                    version = parser.text();
                default:
                    parser.skipChildren();
                    break;
            }
        }
        return ToolMetadata.builder()
                .name(name)
                .description(description)
                .type(type)
                .version(version)
                .build();
    }

    public static ToolMetadata fromStream(StreamInput in) throws IOException {
        ToolMetadata toolMetadata = new ToolMetadata(in);
        return toolMetadata;
    }
}