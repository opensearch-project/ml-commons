/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.agent;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;

import java.io.IOException;

import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;

@EqualsAndHashCode
@Getter
public class MLToolSelectionSpec implements ToXContentObject {
    public static final String TOOL_SELECTION_TYPE_FIELD = "type";
    public static final String TOOL_SELECTION_MODEL_ID_FIELD = "model_id";

    private String type;
    private String model_id;

    @Builder(toBuilder = true)
    public MLToolSelectionSpec(String type,
                      String model_id) {
        if (type == null) {
            type = "original";
        }
        else {
            this.type = type;
        }
        this.model_id = model_id;
    }


    public MLToolSelectionSpec(StreamInput input) throws IOException{
        type = input.readString();
        model_id = input.readOptionalString();
    }

    public void writeTo(StreamOutput out) throws IOException {
        out.writeString(type);
        out.writeOptionalString(model_id);
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        if (type != null) {
            builder.field(TOOL_SELECTION_MODEL_ID_FIELD, type);
        }
        if (model_id != null) {
            builder.field(TOOL_SELECTION_MODEL_ID_FIELD, model_id);
        }
        builder.endObject();
        return builder;
    }

    public static MLToolSelectionSpec parse(XContentParser parser) throws IOException {
        String type = null;
        String model_id = null;

        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.currentToken(), parser);
        while (parser.nextToken() != XContentParser.Token.END_OBJECT) {
            String fieldName = parser.currentName();
            parser.nextToken();

            switch (fieldName) {
                case TOOL_SELECTION_TYPE_FIELD:
                    type = parser.text();
                    break;
                case TOOL_SELECTION_MODEL_ID_FIELD:
                    model_id = parser.text();
                    break;
                default:
                    parser.skipChildren();
                    break;
            }
        }
        return MLToolSelectionSpec.builder()
                .type(type)
                .model_id(model_id)
                .build();
    }

    public static MLToolSelectionSpec fromStream(StreamInput in) throws IOException {
        MLToolSelectionSpec toolSelectionSpec = new MLToolSelectionSpec(in);
        return toolSelectionSpec;
    }
}
