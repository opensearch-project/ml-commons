/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.input.execute.od;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.opensearch.common.ParseField;
import org.opensearch.common.io.stream.StreamInput;
import org.opensearch.common.io.stream.StreamOutput;
import org.opensearch.common.xcontent.NamedXContentRegistry;
import org.opensearch.common.xcontent.XContentBuilder;
import org.opensearch.common.xcontent.XContentParser;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.annotation.ExecuteInput;
import org.opensearch.ml.common.input.Input;

import java.io.IOException;

import static org.opensearch.common.xcontent.XContentParserUtils.ensureExpectedToken;

@ExecuteInput(algorithms={FunctionName.OBJECT_DETECTION})
@Data
@AllArgsConstructor
public class ObjectDetectionInput implements Input {

    public static final String IMAGE_URL_FIELD = "url";
    public static final NamedXContentRegistry.Entry XCONTENT_REGISTRY_ENTRY = new NamedXContentRegistry.Entry(
            Input.class,
            new ParseField(FunctionName.OBJECT_DETECTION.name()),
            parser -> parse(parser)
    );

    public static ObjectDetectionInput parse(XContentParser parser) throws IOException {
        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.currentToken(), parser);
        String imageUrl = null;

        while (parser.nextToken() != XContentParser.Token.END_OBJECT) {
            String fieldName = parser.currentName();
            parser.nextToken();
            switch (fieldName) {
                case IMAGE_URL_FIELD:
                    imageUrl = parser.text();
                    break;
                default:
                    parser.skipChildren();
                    break;
            }
        }
        return new ObjectDetectionInput(imageUrl);
    }

    private final String imageUrl;

    public ObjectDetectionInput(StreamInput in) throws IOException {
        this.imageUrl = in.readString();
    }

    @Override
    public FunctionName getFunctionName() {
        return FunctionName.OBJECT_DETECTION;
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        builder.field(IMAGE_URL_FIELD, imageUrl);
        builder.endObject();
        return builder;
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeString(imageUrl);
    }
}
