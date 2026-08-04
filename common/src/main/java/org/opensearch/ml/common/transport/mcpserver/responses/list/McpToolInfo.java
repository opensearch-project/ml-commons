/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.mcpserver.responses.list;

import static org.opensearch.ml.common.CommonValue.MCP_TOOL_DESCRIPTION_FIELD;
import static org.opensearch.ml.common.CommonValue.MCP_TOOL_NAME_FIELD;
import static org.opensearch.ml.common.CommonValue.MCP_TOOL_TYPE_FIELD;
import static org.opensearch.ml.common.CommonValue.TOOL_INPUT_SCHEMA_FIELD;

import java.io.IOException;
import java.util.Objects;

import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.common.io.stream.Writeable;
import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.core.xcontent.XContentBuilder;

import lombok.Builder;
import lombok.Getter;

/**
 * Represents a tool exposed by an MCP connector for REST API listing.
 * Contains name, type, description, and inputSchema.
 */
@Getter
public class McpToolInfo implements ToXContentObject, Writeable {

    private final String name;
    private final String type;
    private final String description;
    private final String inputSchema;

    @Builder
    public McpToolInfo(String name, String type, String description, String inputSchema) {
        this.name = Objects.requireNonNull(name, "name cannot be null");
        this.type = Objects.requireNonNull(type, "type cannot be null");
        this.description = description;
        this.inputSchema = inputSchema;
    }

    public McpToolInfo(StreamInput in) throws IOException {
        this.name = in.readString();
        this.type = in.readString();
        this.description = in.readOptionalString();
        this.inputSchema = in.readOptionalString();
    }

    public void writeTo(StreamOutput out) throws IOException {
        out.writeString(name);
        out.writeString(type);
        out.writeOptionalString(description);
        out.writeOptionalString(inputSchema);
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        builder.field(MCP_TOOL_NAME_FIELD, name);
        builder.field(MCP_TOOL_TYPE_FIELD, type);
        if (description != null) {
            builder.field(MCP_TOOL_DESCRIPTION_FIELD, description);
        }
        if (inputSchema != null) {
            builder.field(TOOL_INPUT_SCHEMA_FIELD, inputSchema);
        }
        builder.endObject();
        return builder;
    }
}
