/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.output.execute.metrics_correlation;

import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.opensearch.common.io.stream.StreamInput;
import org.opensearch.common.io.stream.StreamOutput;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.ml.common.annotation.MLAlgoOutput;
import org.opensearch.ml.common.output.MLOutput;
import org.opensearch.ml.common.output.MLOutputType;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Output of metrics correlation algorithm results
 */
@Data
@EqualsAndHashCode(callSuper=false)
@MLAlgoOutput(MLOutputType.MODEL_TENSOR)
public class MetricsCorrelationOutput extends MLOutput {

    private static final MLOutputType OUTPUT_TYPE = MLOutputType.MODEL_TENSOR;
    private List<MCorrModelTensors> modelOutput;

    public static final String INFERENCE_RESULT_FIELD = "inference_results";

    @Builder
    public MetricsCorrelationOutput(List<MCorrModelTensors> modelOutput) {
        super(OUTPUT_TYPE);
        this.modelOutput = modelOutput;
    }

    public MetricsCorrelationOutput(StreamInput in) throws IOException {
        super(OUTPUT_TYPE);
        if (in.readBoolean()) {
            modelOutput = new ArrayList<>();
            int size = in.readInt();
            for (int i=0; i<size; i++) {
                modelOutput.add(new MCorrModelTensors(in));
            }
        }
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        if (modelOutput != null && modelOutput.size() > 0) {
            out.writeBoolean(true);
            out.writeInt(modelOutput.size());
            for (MCorrModelTensors output : modelOutput) {
                output.writeTo(out);
            }
        } else {
            out.writeBoolean(false);
        }
    }

    @Override
    protected MLOutputType getType() {
        return OUTPUT_TYPE;
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
//        builder.startObject();  --> This line is causing : com.fasterxml.jackson.core.JsonGenerationException: Can not start an object, expecting field name (context: Object)
        if (modelOutput != null && modelOutput.size() > 0) {
            builder.startArray(INFERENCE_RESULT_FIELD);
            for (MCorrModelTensors output : modelOutput) {
                output.toXContent(builder, params);
            }
            builder.endArray();
        }
        builder.endObject();
        return builder;
    }
}
