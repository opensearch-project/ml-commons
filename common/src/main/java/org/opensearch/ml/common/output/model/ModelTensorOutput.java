/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.output.model;

import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.opensearch.common.xcontent.json.JsonXContent;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.annotation.MLAlgoOutput;
import org.opensearch.ml.common.output.MLOutput;
import org.opensearch.ml.common.output.MLOutputType;

import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = false)
@MLAlgoOutput(MLOutputType.MODEL_TENSOR)
public class ModelTensorOutput extends MLOutput {
    private static final MLOutputType OUTPUT_TYPE = MLOutputType.MODEL_TENSOR;
    public static final String INFERENCE_RESULT_FIELD = "inference_results";

    private List<ModelTensors> mlModelOutputs;

    @Builder(toBuilder = true)
    public ModelTensorOutput(List<ModelTensors> mlModelOutputs) {
        super(OUTPUT_TYPE);
        this.mlModelOutputs = mlModelOutputs;
    }

    public ModelTensorOutput(StreamInput in) throws IOException {
        super(OUTPUT_TYPE);
        if (in.readBoolean()) {
            mlModelOutputs = new ArrayList<>();
            int size = in.readInt();
            for (int i = 0; i < size; i++) {
                mlModelOutputs.add(new ModelTensors(in));
            }
        }
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        if (mlModelOutputs != null && mlModelOutputs.size() > 0) {
            out.writeBoolean(true);
            out.writeInt(mlModelOutputs.size());
            for (ModelTensors output : mlModelOutputs) {
                output.writeTo(out);
            }
        } else {
            out.writeBoolean(false);
        }
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        if (mlModelOutputs != null && mlModelOutputs.size() > 0) {
            builder.startArray(INFERENCE_RESULT_FIELD);
            for (ModelTensors output : mlModelOutputs) {
                output.toXContent(builder, params);
            }
            builder.endArray();
        }
        builder.endObject();
        return builder;
    }

    @Override
    protected MLOutputType getType() {
        return OUTPUT_TYPE;
    }

    public static ModelTensorOutput parse(XContentParser parser) throws IOException {
        List<ModelTensors> mlModelOutputs = new ArrayList<>();

        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.currentToken(), parser);
        while (parser.nextToken() != XContentParser.Token.END_OBJECT) {
            String fieldName = parser.currentName();
            parser.nextToken();

            if (fieldName.equals(INFERENCE_RESULT_FIELD)) {
                ensureExpectedToken(XContentParser.Token.START_ARRAY, parser.currentToken(), parser);
                while (parser.nextToken() != XContentParser.Token.END_ARRAY) {
                    mlModelOutputs.add(ModelTensors.parse(parser));
                }
            } else {
                parser.skipChildren();
            }
        }

        return ModelTensorOutput.builder().mlModelOutputs(mlModelOutputs).build();
    }

    @Override
    public String toString() {
        try {
            return this.toXContent(JsonXContent.contentBuilder(), null).toString();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
