/*
 *
 *  * Copyright OpenSearch Contributors
 *  * SPDX-License-Identifier: Apache-2.0
 *
 */

package org.opensearch.ml.common.transport.mcpserver.requests.register;

import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;

import java.io.IOException;
import java.util.Map;

import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.common.io.stream.Writeable;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;

import lombok.Data;
import lombok.extern.log4j.Log4j2;

/**
 *  This class represents a tool that can be registered with OpenSearch. It contains information about the tool's name,
 * description, parameters, and schema.
 */
@Log4j2
@Data
public class McpTool implements ToXContentObject, Writeable {
    private static final String TYPE_FIELD = "type";
    private static final String DESCRIPTION_FIELD = "description";
    private static final String PARAMS_FIELD = "parameters";
    private static final String ATTRIBUTES_FIELD = "attributes";
    public static final String SCHEMA_FIELD = "input_schema";
    private final String type;
    private final String description;
    private Map<String, Object> parameters;
    private Map<String, Object> attributes;
    private static final String TYPE_NOT_SHOWN_EXCEPTION_MESSAGE = "type field required";

    public McpTool(StreamInput streamInput) throws IOException {
        type = streamInput.readString();
        if (type == null) {
            throw new IllegalArgumentException(TYPE_NOT_SHOWN_EXCEPTION_MESSAGE);
        }
        description = streamInput.readOptionalString();
        if (streamInput.readBoolean()) {
            parameters = streamInput.readMap(StreamInput::readString, StreamInput::readGenericValue);
        }
        if (streamInput.readBoolean()) {
            attributes = streamInput.readMap(StreamInput::readString, StreamInput::readGenericValue);
        }
    }

    public McpTool(String type, String description, Map<String, Object> parameters, Map<String, Object> attributes) {
        if (type == null) {
            throw new IllegalArgumentException(TYPE_NOT_SHOWN_EXCEPTION_MESSAGE);
        }
        this.type = type;
        this.description = description;
        this.parameters = parameters;
        this.attributes = attributes;
    }

    public static McpTool parse(XContentParser parser) throws IOException {
        String type = null;
        String description = null;
        Map<String, Object> params = null;
        Map<String, Object> schema = null;
        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.currentToken(), parser);
        while (parser.nextToken() != XContentParser.Token.END_OBJECT) {
            String fieldName = parser.currentName();
            parser.nextToken();

            switch (fieldName) {
                case TYPE_FIELD:
                    type = parser.text();
                    break;
                case DESCRIPTION_FIELD:
                    description = parser.text();
                    break;
                case PARAMS_FIELD:
                    params = parser.map();
                    break;
                case SCHEMA_FIELD:
                    schema = parser.map();
                    break;
                default:
                    parser.skipChildren();
                    break;
            }
        }
        if (type == null) {
            throw new IllegalArgumentException(TYPE_NOT_SHOWN_EXCEPTION_MESSAGE);
        }
        return new McpTool(type, description, params, schema);
    }

    @Override
    public void writeTo(StreamOutput streamOutput) throws IOException {
        streamOutput.writeString(type);
        streamOutput.writeOptionalString(description);
        if (parameters != null) {
            streamOutput.writeBoolean(true);
            streamOutput.writeMap(parameters, StreamOutput::writeString, StreamOutput::writeGenericValue);
        } else {
            streamOutput.writeBoolean(false);
        }

        if (attributes != null) {
            streamOutput.writeBoolean(true);
            streamOutput.writeMap(attributes, StreamOutput::writeString, StreamOutput::writeGenericValue);
        } else {
            streamOutput.writeBoolean(false);
        }
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, ToXContent.Params xcontentParams) throws IOException {
        builder.startObject();
        builder.field(TYPE_FIELD, type);
        if (description != null) {
            builder.field(DESCRIPTION_FIELD, description);
        }
        if (parameters != null && !parameters.isEmpty()) {
            builder.field(PARAMS_FIELD, parameters);
        }
        if (attributes != null && !attributes.isEmpty()) {
            builder.field(SCHEMA_FIELD, attributes);
        }
        builder.endObject();
        return builder;
    }
}
