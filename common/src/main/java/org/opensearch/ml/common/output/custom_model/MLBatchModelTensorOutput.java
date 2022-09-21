/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.output.custom_model;

import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.opensearch.common.io.stream.StreamInput;
import org.opensearch.common.io.stream.StreamOutput;
import org.opensearch.common.xcontent.XContentBuilder;
import org.opensearch.ml.common.annotation.MLAlgoOutput;
import org.opensearch.ml.common.output.MLOutput;
import org.opensearch.ml.common.output.MLOutputType;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Data
@EqualsAndHashCode(callSuper=false)
@MLAlgoOutput(MLOutputType.BATCH_MODEL_TENSOR)
public class MLBatchModelTensorOutput extends MLOutput {
    private static final MLOutputType OUTPUT_TYPE = MLOutputType.BATCH_MODEL_TENSOR;
    public static final String INFERENCE_RESULT_FIELD = "inference_result";

    private List<MLModelTensorOutput> mlModelOutputs;

    @Builder(toBuilder = true)
    public MLBatchModelTensorOutput(List<MLModelTensorOutput> mlModelOutputs) {
        super(OUTPUT_TYPE);
        this.mlModelOutputs = mlModelOutputs;
    }


    public MLBatchModelTensorOutput(StreamInput in) throws IOException {
        super(OUTPUT_TYPE);
        if (in.readBoolean()) {
            mlModelOutputs = new ArrayList<>();
            int size = in.readInt();
            for (int i=0; i<size; i++) {
                mlModelOutputs.add(new MLModelTensorOutput(in));
            }
        }
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        if (mlModelOutputs != null && mlModelOutputs.size() > 0) {
            out.writeBoolean(true);
            out.writeInt(mlModelOutputs.size());
            for (MLModelTensorOutput output : mlModelOutputs) {
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
            builder.startArray("text_embins");
            for (MLModelTensorOutput output : mlModelOutputs) {
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

}
