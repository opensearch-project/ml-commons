/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.mcpserver.responses.list;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.common.io.stream.Writeable;
import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.core.xcontent.XContentBuilder;

import lombok.Builder;
import lombok.Getter;

/**
 * Represents a tool exposed by an MCP connector for REST API listing.
 * Contains name, type, description, and argument schema (argument name -> type string).
 */
@Getter
public class McpToolInfo implements ToXContentObject, Writeable {

    private static final String NAME_FIELD = "name";
    private static final String TYPE_FIELD = "type";
    private static final String DESCRIPTION_FIELD = "description";
    private static final String ARGUMENTS_FIELD = "arguments";

    private final String name;
    private final String type;
    private final String description;
    private final Map<String, String> arguments;

    @Builder
    public McpToolInfo(String name, String type, String description, Map<String, String> arguments) {
        this.name = name;
        this.type = type;
        this.description = description;
        this.arguments = arguments == null ? Collections.emptyMap() : new HashMap<>(arguments);
    }

    public McpToolInfo(StreamInput in) throws IOException {
        this.name = in.readString();
        this.type = in.readString();
        this.description = in.readOptionalString();
        this.arguments = in.readMap(StreamInput::readString, StreamInput::readString);
    }

    public void writeTo(StreamOutput out) throws IOException {
        out.writeString(name);
        out.writeString(type);
        out.writeOptionalString(description);
        out.writeMap(arguments, StreamOutput::writeString, StreamOutput::writeString);
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        builder.field(NAME_FIELD, name);
        builder.field(TYPE_FIELD, type);
        if (description != null) {
            builder.field(DESCRIPTION_FIELD, description);
        }
        builder.field(ARGUMENTS_FIELD, arguments);
        builder.endObject();
        return builder;
    }
}
