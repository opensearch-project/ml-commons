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
    private static final String NAME_FIELD = "name";
    private static final String DESCRIPTION_FIELD = "description";
    private static final String PARAMS_FIELD = "params";
    private static final String SCHEMA_FIELD = "schema";
    private final String name;
    private final String description;
    private Map<String, Object> params;
    private Map<String, Object> schema;

    public McpTool(StreamInput streamInput) throws IOException {
        name = streamInput.readString();
        description = streamInput.readOptionalString();
        if (streamInput.readBoolean()) {
            params = streamInput.readMap(StreamInput::readString, StreamInput::readGenericValue);
        }
        if (streamInput.readBoolean()) {
            schema = streamInput.readMap(StreamInput::readString, StreamInput::readGenericValue);
        }
    }

    public McpTool(String name, String description, Map<String, Object> params, Map<String, Object> schema) {
        this.name = name;
        this.description = description;
        this.params = params;
        this.schema = schema;
    }

    public static McpTool parse(XContentParser parser) throws IOException {
        String name = null;
        String description = null;
        Map<String, Object> params = null;
        Map<String, Object> schema = null;
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
        return new McpTool(name, description, params, schema);
    }

    @Override
    public void writeTo(StreamOutput streamOutput) throws IOException {
        streamOutput.writeString(name);
        streamOutput.writeOptionalString(description);
        if (params != null) {
            streamOutput.writeBoolean(true);
            streamOutput.writeMap(params, StreamOutput::writeString, StreamOutput::writeGenericValue);
        } else {
            streamOutput.writeBoolean(false);
        }

        if (schema != null) {
            streamOutput.writeBoolean(true);
            streamOutput.writeMap(schema, StreamOutput::writeString, StreamOutput::writeGenericValue);
        } else {
            streamOutput.writeBoolean(false);
        }
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, ToXContent.Params xcontentParams) throws IOException {
        builder.startObject();
        builder.field(NAME_FIELD, name);
        if (description != null) {
            builder.field(DESCRIPTION_FIELD, description);
        }
        if (params != null && !params.isEmpty()) {
            builder.field(PARAMS_FIELD, params);
        }
        if (schema != null && !schema.isEmpty()) {
            builder.field(SCHEMA_FIELD, schema);
        }
        builder.endObject();
        return builder;
    }
}
