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

    @Getter
    private String name;
    @Getter
    private String description;

    @Builder(toBuilder = true)
    public ToolMetadata(String name, String description) {
        this.name = name;
        this.description = description;
    }

    public ToolMetadata(StreamInput input) throws IOException {
        name = input.readString();
        description = input.readString();
    }

    public void writeTo(StreamOutput output) throws IOException {
        output.writeString(name);
        output.writeString(description);
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
        builder.endObject();
        return builder;
    }

    public static ToolMetadata parse(XContentParser parser) throws IOException {
        String name = null;
        String description = null;

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
                default:
                    parser.skipChildren();
                    break;
            }
        }
        return ToolMetadata.builder()
                .name(name)
                .description(description)
                .build();
    }

    public static ToolMetadata fromStream(StreamInput in) throws IOException {
        ToolMetadata toolMetadata = new ToolMetadata(in);
        return toolMetadata;
    }
}
