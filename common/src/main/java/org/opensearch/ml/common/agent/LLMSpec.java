/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.agent;

import lombok.Builder;
import lombok.Getter;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;

import java.io.IOException;
import java.util.Map;

import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;
import static org.opensearch.ml.common.utils.StringUtils.getParameterMap;


@Getter
public class LLMSpec implements ToXContentObject {
    public static final String MODEL_ID_FIELD = "model_id";
    public static final String PARAMETERS_FIELD = "parameters";

    private String modelId;
    private Map<String, String> parameters;


    @Builder(toBuilder = true)
    public LLMSpec(String modelId, Map<String, String> parameters) {
        if (modelId == null) {
            throw new IllegalArgumentException("model id is null");
        }
        this.modelId = modelId;
        this.parameters = parameters;
    }

    public LLMSpec(StreamInput input) throws IOException{
        modelId = input.readString();
        if (input.readBoolean()) {
            parameters = input.readMap(StreamInput::readString, StreamInput::readOptionalString);
        }
    }

    public void writeTo(StreamOutput out) throws IOException {
        out.writeString(modelId);
        if (parameters != null && parameters.size() > 0) {
            out.writeBoolean(true);
            out.writeMap(parameters, StreamOutput::writeString, StreamOutput::writeOptionalString);
        } else {
            out.writeBoolean(false);
        }
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        if (modelId != null) {
            builder.field(MODEL_ID_FIELD, modelId);
        }
        if (parameters != null && parameters.size() > 0) {
            builder.field(PARAMETERS_FIELD, parameters);
        }
        builder.endObject();
        return builder;
    }

    public static LLMSpec parse(XContentParser parser) throws IOException {
        String modelId = null;
        Map<String, String> parameters = null;

        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.currentToken(), parser);
        while (parser.nextToken() != XContentParser.Token.END_OBJECT) {
            String fieldName = parser.currentName();
            parser.nextToken();

            switch (fieldName) {
                case MODEL_ID_FIELD:
                    modelId = parser.text();
                    break;
                case PARAMETERS_FIELD:
                    parameters = getParameterMap(parser.map());
                    break;
                default:
                    parser.skipChildren();
                    break;
            }
        }
        return LLMSpec.builder()
                .modelId(modelId)
                .parameters(parameters)
                .build();
    }

    public static LLMSpec fromStream(StreamInput in) throws IOException {
        LLMSpec toolSpec = new LLMSpec(in);
        return toolSpec;
    }
}
